package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.*;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.SampleRebuildResponse;
import com.citics.glxt.contractchange.model.SampleImportRequest;
import com.citics.glxt.contractchange.contractcompare.service.SftpContractFileLoader;
import com.citics.glxt.contractchange.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import org.springframework.beans.BeanUtils;
import static com.citics.glxt.contractchange.service.ContractParagraphImportService.*;

/** 唯一对外导入入口；适用于几百条低频升级，所有步骤同步执行，UserId仅驻留本次请求。 */
@Slf4j
@Service
public class SampleLibraryImportService {
    private final ContractParagraphImportService importer;
    private final ContractParagraphMapper mapper;
    private final SampleLibraryStore store;
    private final ParagraphVectorIndexService index;
    private final ContractChangeProperties properties;
    private final SftpContractFileLoader fileLoader;

    public SampleLibraryImportService(ContractParagraphImportService importer,ContractParagraphMapper mapper,
            SampleLibraryStore store,ParagraphVectorIndexService index,ContractChangeProperties properties,SftpContractFileLoader fileLoader) {
        this.importer=importer; this.mapper=mapper; this.store=store; this.index=index; this.properties=properties; this.fileLoader=fileLoader;
    }

    public Object importExcel(MultipartFile file,String userId) {
        String operationId=UUID.randomUUID().toString();
        long started=System.currentTimeMillis();
        log.info("历史样本导入开始, operationId={}, source=upload, fileSizeBytes={}",
                operationId,file==null ? null : file.getSize());
        ContractParagraphImportService.ParsedExcel parsed;
        try {
            parsed=importer.readExcel(file);
        } catch (RuntimeException ex) {
            log.warn("历史样本导入请求失败, operationId={}, stage={}, exceptionType={}, elapsedMs={}",
                    operationId,"文件读取或解析",ex.getClass().getSimpleName(),System.currentTimeMillis()-started);
            throw ex;
        }
        log.info("历史样本Excel解析完成, operationId={}, totalRows={}, uniqueRows={}, skipped={}, errorCount={}",
                operationId,parsed.totalRows,parsed.rows.size(),parsed.skipped,parsed.errors.size());
        return importParsed(parsed,userId,operationId,started);
    }

    public Object importFromPath(SampleImportRequest request) {
        String operationId=UUID.randomUUID().toString();
        long started=System.currentTimeMillis();
        if (request == null || request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            log.warn("历史样本导入参数校验失败, operationId={}, reason=userId为空",operationId);
            throw new ContractChangeBusinessException("userId不能为空");
        }
        log.info("历史样本导入开始, operationId={}, source=serverPath",operationId);
        ContractParagraphImportService.ParsedExcel parsed;
        try {
            byte[] bytes=fileLoader.loadExcel(request.getFileGetPath());
            log.info("历史样本文件读取完成, operationId={}, fileSizeBytes={}",
                    operationId,bytes==null ? null : bytes.length);
            parsed=importer.readExcel(bytes);
        } catch (RuntimeException ex) {
            log.warn("历史样本导入请求失败, operationId={}, stage={}, exceptionType={}, elapsedMs={}",
                    operationId,"文件读取或解析",ex.getClass().getSimpleName(),System.currentTimeMillis()-started);
            throw ex;
        }
        log.info("历史样本Excel解析完成, operationId={}, totalRows={}, uniqueRows={}, skipped={}, errorCount={}",
                operationId,parsed.totalRows,parsed.rows.size(),parsed.skipped,parsed.errors.size());
        return importParsed(parsed,request.getUserId(),operationId,started);
    }

    private Object importParsed(ParsedExcel parsed,String userId,String id,long started) {
        // Excel有行级错误时直接返回校验结果，避免占用样本库并触发任何模型调用。
        if (!parsed.errors.isEmpty()) {
            log.warn("历史样本导入校验失败, operationId={}, totalRows={}, uniqueRows={}, errorCount={}, elapsedMs={}",
                    id,parsed.totalRows,parsed.rows.size(),parsed.errors.size(),System.currentTimeMillis()-started);
            return importer.failed(parsed);
        }
        ContractChangeProperties.Embedding target=new ContractChangeProperties.Embedding();
        BeanUtils.copyProperties(properties.getEmbedding(),target);
        SampleLibraryControlDO source;
        try {
            source=store.claim(id,target);
            log.info("历史样本库写入占用成功, operationId={}, sourceVersion={}, targetVersion={}, revision={}",
                    id,source.getModelVersion(),properties.getEmbedding().getModelVersion(),source.getRevision());
        } catch (RuntimeException ex) {
            log.warn("历史样本库写入占用失败, operationId={}, exceptionType={}, elapsedMs={}",
                    id,ex.getClass().getSimpleName(),System.currentTimeMillis()-started);
            throw ex;
        }
        RuntimeException primaryFailure=null;
        String stage="准备样本";
        try {
            // 模型版本未变化时只向量化增量；版本变化时合并当前有效样本并全量重建，防止索引混用模型版本。
            if (source.getModelVersion()==null || source.getModelVersion().equals(properties.getEmbedding().getModelVersion())) {
                stage="增量导入";
                log.info("历史样本导入选择增量流程, operationId={}, sourceCount={}",id,parsed.rows.size());
                Object result=importer.importPrepared(parsed,userId,source,store,target);
                log.info("历史样本导入请求完成, operationId={}, flow=incremental, elapsedMs={}",
                        id,System.currentTimeMillis()-started);
                return result;
            }
            log.info("历史样本导入选择全量重建流程, operationId={}, sourceVersion={}, targetVersion={}",
                    id,source.getModelVersion(),properties.getEmbedding().getModelVersion());
            stage="读取历史样本";
            List<ContractParagraphDO> active=mapper.selectAllActiveParagraphs();
            log.info("全量重建读取有效样本完成, operationId={}, activeCount={}",id,active.size());
            stage="全量重建";
            List<PreparedRow> merged=merge(active,parsed,source);
            log.info("全量重建样本合并完成, operationId={}, sourceCount={}, importedUniqueCount={}, mergedCount={}",
                    id,active.size(),parsed.rows.size(),merged.size());
            String sourceVersion=source.getModelVersion();
            rebuild(source,target,merged,active.size(),userId);
            log.info("历史样本全量重建请求完成, operationId={}, sourceVersion={}, targetVersion={}, sampleCount={}, elapsedMs={}",
                    id,sourceVersion,target.getModelVersion(),merged.size(),System.currentTimeMillis()-started);
            return new SampleRebuildResponse(id,sourceVersion,target.getModelVersion(),merged.size(),active.size());
        } catch (RuntimeException ex) {
            primaryFailure=ex;
            log.error("历史样本导入处理失败, operationId={}, stage={}, exceptionType={}, detail={}, elapsedMs={}",
                    id,stage,ex.getClass().getSimpleName(),safeFailureDetail(ex),System.currentTimeMillis()-started);
            throw ex;
        } finally {
            // 仅释放未提交的RUNNING；DB_COMMITTED由索引恢复成功后释放。
            try {
                store.release(id);
            } catch (RuntimeException releaseFailure) {
                if (primaryFailure!=null) {
                    primaryFailure.addSuppressed(releaseFailure);
                    log.error("历史样本导入失败后释放写入占用也失败, operationId={}, primaryExceptionType={}, releaseExceptionType={}",
                            id,primaryFailure.getClass().getSimpleName(),releaseFailure.getClass().getSimpleName());
                } else {
                    log.error("历史样本导入结束后释放写入占用失败, operationId={}, exceptionType={}",
                            id,releaseFailure.getClass().getSimpleName());
                    throw releaseFailure;
                }
            }
        }
    }

    List<PreparedRow> merge(List<ContractParagraphDO> active,ParsedExcel parsed,SampleLibraryControlDO source) {
        Map<String,PreparedRow> merged=new LinkedHashMap<>();
        int duplicateHistory=0;
        int duplicateImport=0;
        for (ContractParagraphDO row:active) {
            String rowId=String.valueOf(row.getId());
            String normalized=ContractTextNormalizer.normalize(row.getOriginalText());
            try {
                importer.validateParagraph(normalized);
            } catch (ContractChangeBusinessException ex) {
                throw new ContractChangeBusinessException(409,"历史样本ID="+rowId+"的段落无效："+ex.getMessage());
            }
            String codes;
            try {
                codes=ChangeTypeCodes.canonicalize(row.getChangeTypeCodes());
            } catch (ContractChangeBusinessException ex) {
                throw new ContractChangeBusinessException(409,"历史样本ID="+rowId+"的BGLX_CODES无效："+ex.getMessage());
            }
            if (!Objects.equals(source.getModelVersion(),row.getModelVersion()))
                throw new ContractChangeBusinessException(409,"历史样本ID="+rowId+"的MODEL_VERSION与控制表不一致");
            if (!Objects.equals(source.getVectorDim(),row.getVectorDim()))
                throw new ContractChangeBusinessException(409,"历史样本ID="+rowId+"的VECTOR_DIM与控制表不一致");
            // GFBW和TEXT_HASH均由YWBW派生；升级重建时从原文重新计算，修复历史派生值不一致。
            String textHash=HashUtils.sha256(normalized);
            PreparedRow existing=merged.get(textHash);
            if (existing!=null) {
                if (!existing.changeTypeCodes.equals(codes))
                    throw new ContractChangeBusinessException(409,"历史样本ID="+rowId+"与其他相同段落的变更类型编码冲突");
                duplicateHistory++;
                continue;
            }
            merged.put(textHash,new PreparedRow(0,row.getOriginalText(),normalized,textHash,codes));
        }
        for (PreparedRow row:parsed.rows.values()) {
            PreparedRow old=merged.get(row.textHash);
            if (old!=null && !old.changeTypeCodes.equals(row.changeTypeCodes))
                throw new ContractChangeBusinessException(409,"历史样本与Excel标签冲突，行号="+row.rowNumber);
            if (old!=null) duplicateImport++;
            merged.put(row.textHash,row); // 相同Hash同标签时Excel原文优先。
        }
        if (merged.size()>properties.getImportConfig().getMaxTotalSamples())
            throw new ContractChangeBusinessException(409,"合并后有效样本超过容量上限");
        log.info("全量重建数据校验完成, sourceCount={}, importedUniqueCount={}, duplicateHistoryCount={}, duplicateImportCount={}, mergedCount={}",
                active.size(),parsed.rows.size(),duplicateHistory,duplicateImport,merged.size());
        return new ArrayList<>(merged.values());
    }

    private void rebuild(SampleLibraryControlDO source,ContractChangeProperties.Embedding target,
                         List<PreparedRow> rows,int sourceCount,String userId) {
        boolean committed=false;
        String stage="准备向量化";
        try {
            int batchSize=Math.min(16,Math.max(1,target.getBatchSize()));
            for (int start=0;start<rows.size();start+=batchSize) {
                assertFixedModel(target);
                int end=Math.min(start+batchSize,rows.size());
                stage="模型向量化（第"+(start/batchSize+1)+"批）";
                long batchStarted=System.currentTimeMillis();
                log.info("历史样本重建向量化批次开始, operationId={}, batch={}, batchCount={}, batchSize={}, totalSamples={}",
                        source.getOperationId(),(start/batchSize)+1,(rows.size()+batchSize-1)/batchSize,end-start,rows.size());
                importer.embed(rows.subList(start,end),userId,source.getOperationId(),target);
                log.info("历史样本重建向量化批次完成, operationId={}, batch={}, batchSize={}, elapsedMs={}",
                        source.getOperationId(),(start/batchSize)+1,end-start,System.currentTimeMillis()-batchStarted);
            }
            assertFixedModel(target);
            List<ContractParagraphDO> values=new ArrayList<>();
            for (PreparedRow row:rows) values.add(importer.toDO(row,target));
            stage="数据库提交";
            log.info("历史样本重建数据库提交开始, operationId={}, expectedActiveCount={}, newSampleCount={}",
                    source.getOperationId(),sourceCount,values.size());
            store.commitRebuild(source,target,values,sourceCount);
            committed=true;
            log.info("历史样本重建数据库提交完成, operationId={}, newSampleCount={}",source.getOperationId(),values.size());
            // 此后失败保留DB_COMMITTED；重载/重启仅恢复索引，不再次生成或插入。
            stage="索引重载";
            log.info("历史样本重建索引重载开始, operationId={}, expectedSampleCount={}",source.getOperationId(),rows.size());
            index.reloadForOperation(source.getOperationId(),rows.size());
            log.info("历史样本重建索引重载完成, operationId={}, sampleCount={}",source.getOperationId(),rows.size());
        } catch (RuntimeException ex) {
            String detail=safeFailureDetail(ex);
            // 只记录受控诊断信息，不记录底层SQL、请求正文、模型响应或凭据。
            log.error("历史样本重建失败, stage={}, operationId={}, exceptionType={}, detail={}",
                    stage,source.getOperationId(),ex.getClass().getSimpleName(),detail);
            String message=committed
                    ? "数据库已切换，索引待恢复；请重载索引，不要重复导入"
                    : "重建失败，旧有效版本未改变，失败阶段="+stage;
            if (!committed) message+="，原因="+detail;
            throw new ContractChangeBusinessException(503,message+"，operationId="+source.getOperationId());
        }
    }
    private String safeFailureDetail(RuntimeException ex) {
        if (ex instanceof ContractChangeBusinessException && ex.getMessage()!=null)
            return ex.getMessage();
        Throwable cause=ex;
        while (cause!=null) {
            if (cause instanceof java.sql.SQLException) {
                java.sql.SQLException sql=(java.sql.SQLException)cause;
                return "数据库错误码="+sql.getErrorCode()+", SQLState="+sql.getSQLState();
            }
            cause=cause.getCause();
        }
        return "异常类型="+ex.getClass().getSimpleName();
    }
    private void assertFixedModel(ContractChangeProperties.Embedding target) {
        if (!Objects.equals(target.getUrl(),properties.getEmbedding().getUrl())
                || !Objects.equals(target.getModelVersion(),properties.getEmbedding().getModelVersion())
                || !Objects.equals(target.getModelName(),properties.getEmbedding().getModelName())
                || target.getDimension()!=properties.getEmbedding().getDimension())
            throw new ContractChangeBusinessException(409,"不支持运行时热切换模型配置");
    }
}
