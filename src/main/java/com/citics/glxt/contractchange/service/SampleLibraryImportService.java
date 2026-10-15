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
        return importParsed(importer.readExcel(file),userId);
    }

    public Object importFromPath(SampleImportRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId().trim().isEmpty())
            throw new ContractChangeBusinessException("userId不能为空");
        byte[] bytes=fileLoader.loadExcel(request.getFileGetPath());
        return importParsed(importer.readExcel(bytes),request.getUserId());
    }

    private Object importParsed(ParsedExcel parsed,String userId) {
        if (!parsed.errors.isEmpty()) return importer.failed(parsed);
        String id=UUID.randomUUID().toString();
        SampleLibraryControlDO source=store.claim(id,properties.getEmbedding());
        try {
            if (source.getModelVersion()==null || source.getModelVersion().equals(properties.getEmbedding().getModelVersion()))
                return importer.importPrepared(parsed,userId,source,store);
            List<ContractParagraphDO> active=mapper.selectAllActiveParagraphs();
            List<PreparedRow> merged=merge(active,parsed,source);
            ContractChangeProperties.Embedding target=new ContractChangeProperties.Embedding();
            BeanUtils.copyProperties(properties.getEmbedding(),target);
            String sourceVersion=source.getModelVersion();
            rebuild(source,target,merged,active.size(),userId);
            return new SampleRebuildResponse(id,sourceVersion,target.getModelVersion(),merged.size(),active.size());
        } finally {
            // 仅释放未提交的RUNNING；DB_COMMITTED由索引恢复成功后释放。
            store.release(id);
        }
    }

    List<PreparedRow> merge(List<ContractParagraphDO> active,ParsedExcel parsed,SampleLibraryControlDO source) {
        Map<String,PreparedRow> merged=new LinkedHashMap<>();
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
                continue;
            }
            merged.put(textHash,new PreparedRow(0,row.getOriginalText(),normalized,textHash,codes));
        }
        for (PreparedRow row:parsed.rows.values()) {
            PreparedRow old=merged.get(row.textHash);
            if (old!=null && !old.changeTypeCodes.equals(row.changeTypeCodes))
                throw new ContractChangeBusinessException(409,"历史样本与Excel标签冲突，行号="+row.rowNumber);
            merged.put(row.textHash,row); // 相同Hash同标签时Excel原文优先。
        }
        if (merged.size()>properties.getImportConfig().getMaxTotalSamples())
            throw new ContractChangeBusinessException(409,"合并后有效样本超过容量上限");
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
                importer.embed(rows.subList(start,end),userId);
            }
            assertFixedModel(target);
            List<ContractParagraphDO> values=new ArrayList<>();
            for (PreparedRow row:rows) values.add(importer.toDO(row));
            stage="数据库提交";
            store.commitRebuild(source,target,values,sourceCount);
            committed=true;
            // 此后失败保留DB_COMMITTED；重载/重启仅恢复索引，不再次生成或插入。
            stage="索引重载";
            index.reloadForOperation(source.getOperationId(),rows.size());
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
        if (!Objects.equals(target.getModelVersion(),properties.getEmbedding().getModelVersion())
                || !Objects.equals(target.getModelName(),properties.getEmbedding().getModelName())
                || target.getDimension()!=properties.getEmbedding().getDimension())
            throw new ContractChangeBusinessException(409,"不支持运行时热切换模型配置");
    }
}
