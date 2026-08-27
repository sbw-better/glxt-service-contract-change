package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 历史样本持久化事务边界。
 *
 * <p>这个类专门负责数据库写入，是为了让Spring事务能够真正生效。只要其中一条新增或更新失败，
 * 本批已经执行的其他操作也会一起撤销，不会出现Excel导入一半的情况。</p>
 */
@Slf4j
@Service
public class ContractParagraphPersistenceService {
    private final ContractParagraphMapper mapper;

    /** 注入仅包含本版核心 SQL 的历史样本 Mapper。 */
    public ContractParagraphPersistenceService(ContractParagraphMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在同一个事务中新增或更新全部历史样本。
     *
     * @param paragraphs 已完成向量化且通过校验的新增/更新样本
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(List<ContractParagraphDO> paragraphs) {
        long started = System.currentTimeMillis();
        log.info("历史样本批量保存开始, count={}", paragraphs.size());
        int executed = 0;
        try {
            for (ContractParagraphDO paragraph : paragraphs) {
                if (paragraph.getId() == null) {
                    mapper.insertParagraph(paragraph);
                } else {
                    int affected = mapper.updateParagraph(paragraph);
                    if (affected != 1) {
                        throw new IllegalStateException("历史样本更新记录数不正确, sampleId=" + paragraph.getId());
                    }
                }
                executed++;
            }
        } catch (RuntimeException ex) {
            // 必须继续抛出异常，确保 Spring 事务代理回滚本批次已经执行的全部 INSERT。
            log.error("历史样本批量入库失败并将回滚, expectedCount={}, executedCount={}, elapsedMs={}",
                    paragraphs.size(), executed, System.currentTimeMillis() - started, ex);
            throw ex;
        }
        log.info("历史样本批量保存完成, count={}, elapsedMs={}",
                paragraphs.size(), System.currentTimeMillis() - started);
    }
}
