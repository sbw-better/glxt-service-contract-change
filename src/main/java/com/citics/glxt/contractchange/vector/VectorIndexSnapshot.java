package com.citics.glxt.contractchange.vector;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 内存索引不可变快照。
 *
 * <p>列表用于语义全量扫描，Hash Map 用于精确匹配；快照构建完成后整体原子替换。</p>
 */
@Getter
@AllArgsConstructor
public class VectorIndexSnapshot {
    /** 参与语义检索的样本列表。 */
    private final List<ParagraphVectorSample> samples;
    /** 文本 Hash 到样本的精确匹配索引。 */
    private final Map<String, ParagraphVectorSample> hashIndex;
    /** NOT_READY、READY、EMPTY、DEGRADED 或 LOAD_FAILED。 */
    private final String status;
    /** 构建时跳过的损坏记录数。 */
    private final int errorCount;
    /** 快照构建完成时间。 */
    private final Date loadedAt;

    /** 创建应用尚未完成首次Oracle加载时的初始快照。 */
    public static VectorIndexSnapshot notReady() {
        return new VectorIndexSnapshot(Collections.<ParagraphVectorSample>emptyList(),
                Collections.<String, ParagraphVectorSample>emptyMap(), "NOT_READY", 0, null);
    }

    /** 创建首次Oracle加载失败后的不可用快照。 */
    public static VectorIndexSnapshot loadFailed() {
        return new VectorIndexSnapshot(Collections.<ParagraphVectorSample>emptyList(),
                Collections.<String, ParagraphVectorSample>emptyMap(), "LOAD_FAILED", 0, new Date());
    }
}
