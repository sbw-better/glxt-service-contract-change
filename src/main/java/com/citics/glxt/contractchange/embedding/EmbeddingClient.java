package com.citics.glxt.contractchange.embedding;

import com.citics.glxt.contractchange.model.EmbeddingBatchResult;

import java.util.List;

/** 向量模型访问抽象，生产环境由统一模型网关实现，测试环境可替换为 Mock Bean。 */
public interface EmbeddingClient {
    /**
     * 批量生成并返回已经 L2 归一化的向量。
     *
     * @param texts 非空文本列表
     * @param userId 当前实际操作人的用户标识，用于模型平台审计
     * @return 与输入顺序严格一致的批量向量结果
     */
    EmbeddingBatchResult embed(List<String> texts, String userId);
}
