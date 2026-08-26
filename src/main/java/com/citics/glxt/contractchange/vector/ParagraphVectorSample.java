package com.citics.glxt.contractchange.vector;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 加载到 JVM 内存中的一条只读历史向量样本。 */
@Data
@AllArgsConstructor
public class ParagraphVectorSample {
    /** Oracle 历史样本主键。 */
    private long sampleId;
    /** 用于返回参考证据的原始段落。 */
    private String originalText;
    /** 用于精确匹配的 SHA-256。 */
    private String textHash;
    /** 已拆分的多标签编码列表。 */
    private List<String> changeTypeCodes;
    /** 已 L2 归一化且维度与当前索引配置一致的向量。 */
    private float[] vector;
}
