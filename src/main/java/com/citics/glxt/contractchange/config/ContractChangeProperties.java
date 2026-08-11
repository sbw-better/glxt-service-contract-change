package com.citics.glxt.contractchange.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 合同段落识别业务配置。
 *
 * <p>所有参数均以 {@code contract-change} 为前缀，可在不同环境通过外部配置覆盖，
 * 从而保证开发和生产使用同一份 Java 制品。</p>
 */
@Data
@ConfigurationProperties(prefix = "contract-change")
public class ContractChangeProperties {
    /** 向量模型访问配置。 */
    private Embedding embedding = new Embedding();
    /** Excel 导入限制。 */
    private ImportConfig importConfig = new ImportConfig();
    /** 内存检索与投票阈值。 */
    private Search search = new Search();

    /** Hugging Face Embedding 容器调用参数。 */
    @Data
    public static class Embedding {
        /** 批量向量接口地址。 */
        private String url = "http://127.0.0.1:8081/embed";
        /** 容器健康检查地址。 */
        private String healthUrl = "http://127.0.0.1:8081/health";
        /** 写入数据库并用于隔离检索的模型版本标识。 */
        private String modelVersion = "bge-base-zh-768-v1";
        /** 模型固定输出维度。 */
        private int dimension = 768;
        /** 单次发送给模型容器的最大文本数量。 */
        private int batchSize = 8;
        /** HTTP 建连超时，单位毫秒。 */
        private int connectTimeoutMs = 3000;
        /** HTTP 响应读取超时，单位毫秒。 */
        private int readTimeoutMs = 30000;
        /** 连接异常和 5xx 的最大重试次数，不包含首次请求。 */
        private int maxRetries = 1;
    }

    /** 单次历史样本导入参数。 */
    @Data
    public static class ImportConfig {
        /** 单份 Excel 允许的最大非空数据行数。 */
        private int maxRows = 1000;
    }

    /** 精确检索、证据返回和多标签投票参数。 */
    @Data
    public static class Search {
        /** 余弦相似度检索保留的最大候选数。 */
        private int retrieveTopK = 20;
        /** 候选结果中参与标签加权投票的最大样本数。 */
        private int voteTopK = 10;
        /** 返回给调用方的最大参考段落数。 */
        private int evidenceTopK = 5;
        /** 规范化后合同段落最大字符数。 */
        private int maxParagraphLength = 4000;
        /** 历史样本进入召回结果的最低余弦相似度。 */
        private double minSimilarity = 0.60D;
        /** 类型被判定为高可信的最低得分。 */
        private double highThreshold = 0.80D;
        /** 类型可以作为候选返回的最低得分。 */
        private double candidateThreshold = 0.55D;
        /** 类型被判定为高可信所需的最少支持样本数。 */
        private int minSupportCount = 2;
        /** 投票无结果时，启用强单条匹配兜底所需的第一名最低相似度。 */
        private double strongMatchThreshold = 0.80D;
        /** 强单条匹配兜底要求第一名至少领先第二名的相似度差值。 */
        private double strongMatchMinMargin = 0.05D;
    }
}
