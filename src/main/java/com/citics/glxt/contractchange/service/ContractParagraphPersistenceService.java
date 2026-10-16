package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.util.ContractTextNormalizer;
import com.citics.glxt.contractchange.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
                    int affected = mapper.insertParagraph(paragraph);
                    if (affected != 1) {
                        throw new IllegalStateException("历史样本新增记录数不正确");
                    }
                } else {
                    int affected = mapper.updateParagraph(paragraph);
                    if (affected != 1) {
                        throw new IllegalStateException("历史样本更新记录数不正确, sampleId=" + paragraph.getId());
                    }
                }
                executed++;
            }
            verifyPersistedText(paragraphs);
        } catch (RuntimeException ex) {
            // 必须继续抛出异常，确保 Spring 事务代理回滚本批次已经执行的全部 INSERT。
            log.error("历史样本批量入库失败并将回滚, expectedCount={}, executedCount={}, elapsedMs={}",
                    paragraphs.size(), executed, System.currentTimeMillis() - started, ex);
            throw ex;
        }
        log.info("历史样本批量保存完成, count={}, elapsedMs={}",
                paragraphs.size(), System.currentTimeMillis() - started);
    }

    /**
     * 写入与读回必须在同一事务内完成。若数据库字符集替换了输入字符，立即抛错使整批回滚，
     * 避免等到索引重载时才发现正文与Hash不一致。
     */
    private void verifyPersistedText(List<ContractParagraphDO> paragraphs) {
        if (paragraphs.isEmpty()) return;
        final int queryBatchSize = 500; // Oracle IN 子句上限为1000，留出余量。
        Map<Long, ContractParagraphDO> persistedById = new HashMap<Long, ContractParagraphDO>();
        for (int start = 0; start < paragraphs.size(); start += queryBatchSize) {
            int end = Math.min(start + queryBatchSize, paragraphs.size());
            List<Long> ids = new ArrayList<Long>(end - start);
            for (int i = start; i < end; i++) {
                Long id = paragraphs.get(i).getId();
                if (id == null) throw new IllegalStateException("历史样本写入后未回填主键，无法校验正文");
                ids.add(id);
            }
            List<ContractParagraphDO> loaded = mapper.selectTextForVerificationByIds(ids);
            if (loaded != null) {
                for (ContractParagraphDO row : loaded) persistedById.put(row.getId(), row);
            }
        }
        for (ContractParagraphDO expected : paragraphs) {
            ContractParagraphDO actual = persistedById.get(expected.getId());
            if (actual == null) {
                throw new IllegalStateException("历史样本ID=" + expected.getId() + "写入后未读回");
            }
            boolean originalChanged = !Objects.equals(expected.getOriginalText(), actual.getOriginalText());
            boolean normalizedChanged = !Objects.equals(expected.getNormalizedText(), actual.getNormalizedText());
            String actualHash = actual.getNormalizedText() == null ? null
                    : HashUtils.sha256(ContractTextNormalizer.normalize(actual.getNormalizedText()));
            boolean hashChanged = !Objects.equals(expected.getTextHash(), actual.getTextHash())
                    || !Objects.equals(expected.getTextHash(), actualHash);
            if (originalChanged || normalizedChanged || hashChanged) {
                log.error("历史样本写入读回校验失败, sampleId={}, originalChanged={}, normalizedChanged={}, "
                                + "storedHash={}, readBackHash={}, reason=数据库写入后正文或Hash发生变化，请检查字符集转换",
                        expected.getId(), originalChanged, normalizedChanged,
                        expected.getTextHash(), actualHash);
                throw new IllegalStateException("历史样本ID=" + expected.getId()
                        + "写入后正文发生变化，可能包含数据库字符集无法表示的字符；本批已回滚");
            }
        }
        log.info("历史样本写入读回校验完成, count={}", paragraphs.size());
    }
}
