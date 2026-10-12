package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.domain.SampleLibraryControlDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.mapper.SampleLibraryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 网络调用不进入本服务；所有方法仅持有短数据库事务。 */
@Service
public class SampleLibraryStore {
    private final SampleLibraryMapper mapper;
    private final ContractParagraphMapper paragraphs;
    private final ContractParagraphPersistenceService persistence;

    public SampleLibraryStore(SampleLibraryMapper mapper, ContractParagraphMapper paragraphs,
                              ContractParagraphPersistenceService persistence) {
        this.mapper=mapper; this.paragraphs=paragraphs; this.persistence=persistence;
    }

    public SampleLibraryControlDO control() { return mapper.control(); }

    @Transactional
    public SampleLibraryControlDO claim(String id, ContractChangeProperties.Embedding target) {
        SampleLibraryControlDO value=mapper.lockControl();
        if (value==null || (value.getModelVersion()==null && paragraphs.countAllParagraphs()!=0))
            throw conflict("样本库控制信息未初始化，需要明确初始化模型身份");
        if (!validControl(value)) throw conflict("样本库控制状态或模型身份异常，请核对控制行");
        if (value.getOperationId()!=null) throw conflict("样本库正在写入，operationId="+value.getOperationId());
        if (value.getModelVersion()!=null && value.getModelVersion().equals(target.getModelVersion())) {
            if (!compatible(value,target)) throw conflict("模型名称或维度变化，请更新MODEL_VERSION");
        } else if (mapper.usedVersion(target.getModelVersion())>0) {
            throw conflict("目标MODEL_VERSION已使用，禁止复用历史版本");
        }
        require(mapper.claim(id));
        value.setOperationId(id);
        value.setOperationStatus("RUNNING");
        return value;
    }

    @Transactional
    public void release(String id) { mapper.release(id); }

    @Transactional
    public void commitIncremental(List<ContractParagraphDO> rows, SampleLibraryControlDO source,
                                   ContractChangeProperties.Embedding target) {
        SampleLibraryControlDO value=owned(source.getOperationId(),source.getRevision(),source.getModelVersion());
        persistence.saveAll(rows);
        value.setModelName(target.getModelName()); value.setModelVersion(target.getModelVersion());
        value.setVectorDim(target.getDimension());
        require(mapper.switchVersion(value));
    }

    @Transactional
    public void commitRebuild(SampleLibraryControlDO source,ContractChangeProperties.Embedding target,
                              List<ContractParagraphDO> rows,int sourceCount) {
        SampleLibraryControlDO value=owned(source.getOperationId(),source.getRevision(),source.getModelVersion());
        if (!"RUNNING".equals(value.getOperationStatus())) throw conflict("操作状态已改变");
        if (mapper.usedVersion(target.getModelVersion())!=0) throw conflict("目标版本已使用");
        if (paragraphs.selectAllActiveParagraphs().size()!=sourceCount) throw conflict("源有效样本数量已变化");
        persistence.saveAll(rows);
        int disabled=paragraphs.disableActiveParagraphs(source.getModelVersion());
        if (disabled!=sourceCount) throw conflict("源有效样本已变化");
        value.setModelName(target.getModelName()); value.setModelVersion(target.getModelVersion());
        value.setVectorDim(target.getDimension());
        require(mapper.switchVersion(value));
        require(mapper.committed(source.getOperationId(),rows.size()));
    }

    @Transactional
    public void complete(String id) {
        require(mapper.complete(id));
    }

    /** 仅在本项目单实例启动时调用；不抢占另一个运行实例。 */
    @Transactional
    public void recoverInterrupted() {
        SampleLibraryControlDO value=mapper.lockControl();
        if (value==null) return;
        if (!validControl(value)) throw conflict("样本库控制状态或模型身份异常，不能自动恢复");
        if ("RUNNING".equals(value.getOperationStatus())) require(mapper.release(value.getOperationId()));
    }

    private SampleLibraryControlDO owned(String id,long revision,String version) {
        SampleLibraryControlDO value=mapper.lockControl();
        if (!validControl(value) || !"RUNNING".equals(value.getOperationStatus())
                || !Objects.equals(id,value.getOperationId()) || value.getRevision()!=revision
                || !Objects.equals(version,value.getModelVersion())) throw conflict("样本库版本、修订号或写入占用已变化");
        return value;
    }
    public static boolean compatible(SampleLibraryControlDO value,ContractChangeProperties.Embedding target) {
        return value!=null && Objects.equals(value.getModelVersion(),target.getModelVersion())
            && Objects.equals(value.getModelName(),target.getModelName())
            && Objects.equals(value.getVectorDim(),target.getDimension());
    }
    /** 在Java侧也核对控制行，防止人工建表遗漏约束时误占用或误启用识别。 */
    public static boolean validControl(SampleLibraryControlDO value) {
        if (value==null || value.getRevision()<0) return false;
        boolean uninitialized=value.getModelName()==null && value.getModelVersion()==null && value.getVectorDim()==null;
        boolean initialized=hasText(value.getModelName()) && hasText(value.getModelVersion())
                && value.getVectorDim()!=null && value.getVectorDim()>0;
        if (!uninitialized && !initialized) return false;
        if ("IDLE".equals(value.getOperationStatus()))
            return value.getOperationId()==null && value.getExpectedSamples()==null;
        if ("RUNNING".equals(value.getOperationStatus()))
            return hasText(value.getOperationId()) && value.getExpectedSamples()==null;
        if ("DB_COMMITTED".equals(value.getOperationStatus()))
            return initialized && hasText(value.getOperationId()) && value.getExpectedSamples()!=null && value.getExpectedSamples()>0;
        return false;
    }
    private static boolean hasText(String value) { return value!=null && !value.trim().isEmpty(); }
    private static void require(int count) { if(count!=1) throw conflict("样本库操作状态冲突"); }
    private static ContractChangeBusinessException conflict(String message) { return new ContractChangeBusinessException(409,message); }
}
