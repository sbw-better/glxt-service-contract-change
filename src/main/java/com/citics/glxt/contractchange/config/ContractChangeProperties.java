package com.citics.glxt.contractchange.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 合同段落识别业务配置。
 *
 * <p>所有参数均以 {@code contract-change} 为前缀，可在不同环境通过外部配置覆盖，
 * 从而保证开发和生产使用同一份 Java 制品。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "contract-change")
public class ContractChangeProperties {
    /** 向量模型访问配置。 */
    @Valid
    private Embedding embedding = new Embedding();
    /** Excel 导入限制。 */
    @Valid
    private ImportConfig importConfig = new ImportConfig();
    /** 内存检索与投票阈值。 */
    @Valid
    private Search search = new Search();

    /** Hugging Face Embedding 容器调用参数。 */
    @Data
    public static class Embedding {
        /** 批量向量接口地址。 */
        @NotBlank
        private String url = "http://127.0.0.1:8081/embed";
        /** 容器健康检查地址。 */
        @NotBlank
        private String healthUrl = "http://127.0.0.1:8081/health";
        /** 写入数据库并用于隔离检索的模型版本标识。 */
        @NotBlank
        private String modelVersion = "bge-base-zh-768-v1";
        /** 模型固定输出维度。 */
        @Min(1)
        private int dimension = 768;
        /** 单次发送给模型容器的最大文本数量。 */
        @Min(1)
        private int batchSize = 4;
        /** HTTP 建连超时，单位毫秒。 */
        @Min(1)
        private int connectTimeoutMs = 3000;
        /** HTTP 响应读取超时，单位毫秒。 */
        @Min(1)
        private int readTimeoutMs = 30000;
        /** 连接异常和 5xx 的最大重试次数，不包含首次请求。 */
        @Min(0)
        private int maxRetries = 1;
    }

    /** 单次历史样本导入参数。 */
    @Data
    public static class ImportConfig {
        /** 单份 Excel 允许的最大非空数据行数。 */
        @Min(1)
        private int maxRows = 1000;
        /** 第一版允许持久化的历史段落总数，防止内存精确检索规模失控。 */
        @Min(1)
        private int maxTotalSamples = 10000;
    }

    /** 精确检索、证据返回和多标签投票参数。 */
    @Data
    public static class Search {
        /** 余弦相似度检索保留的最大候选数。 */
        @Min(1)
        private int retrieveTopK = 10;
        /** 候选结果中参与标签加权投票的最大样本数。 */
        @Min(1)
        private int voteTopK = 10;
        /** 返回给调用方的最大参考段落数。 */
        @Min(1)
        private int evidenceTopK = 5;
        /**
         * 规范化后合同段落最大字符数。
         *
         * <p>BGE Base 最大输入为 512 Token。字符数不能完全等同于 Token 数，因此保留余量，
         * 并要求模型容器关闭自动截断，避免只向量化段落前半部分。</p>
         */
        @Min(1)
        private int maxParagraphLength = 480;
        /** 历史样本进入召回结果的最低余弦相似度。 */
        @DecimalMin("-1.0")
        @DecimalMax("1.0")
        private double minSimilarity = 0.60D;
        /** 类型被判定为高可信的最低得分。 */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double highThreshold = 0.80D;
        /** 类型可以作为候选返回的最低得分。 */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double candidateThreshold = 0.55D;
        /** 类型被判定为高可信所需的最少支持样本数。 */
        @Min(1)
        private int minSupportCount = 2;
        /** 投票无结果时，启用强单条匹配兜底所需的第一名最低相似度。 */
        @DecimalMin("-1.0")
        @DecimalMax("1.0")
        private double strongMatchThreshold = 0.80D;
        /** 校验具有先后关系的检索参数，配置错误时直接阻止应用启动。 */
        @AssertTrue(message = "检索配置不合法：vote/evidence TopK不能大于retrieveTopK，候选阈值不能大于高可信阈值")
        public boolean isValidCombination() {
            return voteTopK <= retrieveTopK
                    && evidenceTopK <= retrieveTopK
                    && candidateThreshold <= highThreshold;
        }
    }
}
