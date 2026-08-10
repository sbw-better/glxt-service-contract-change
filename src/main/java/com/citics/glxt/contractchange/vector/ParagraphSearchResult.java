package com.citics.glxt.contractchange.vector;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 内存向量检索的一条候选结果。 */
@Data
@AllArgsConstructor
public class ParagraphSearchResult {
    /** 命中的历史样本。 */
    private ParagraphVectorSample sample;
    /** 查询段落与历史样本的余弦相似度。 */
    private double similarity;
}
