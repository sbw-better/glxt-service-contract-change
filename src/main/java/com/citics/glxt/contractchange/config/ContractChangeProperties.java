package com.citics.glxt.contractchange.config;

import lombok.Data;
import lombok.ToString;
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
 * <p>配置按照“模型调用、Excel导入、段落检索”分成三组，名称和 application.yml 中的层级
 * 一一对应。把这些小配置类放在这里，是为了让所有合同识别参数集中在一个入口查看。</p>
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

    /**
     * 调用向量模型需要的参数。
     *
     * <p>模型会把合同段落转换成一组数字，后续通过比较这些数字判断段落含义是否接近。</p>
     */
    @Data
    public static class Embedding {
        /** 完整的 Embedding 接口地址；路径由模型平台提供，Java 不自行拼接。 */
        @NotBlank
        private String url;
        /** 模型网关 API Key，只允许通过外部安全配置注入。 */
        @NotBlank
        @ToString.Exclude
        private String apiKey;
        /** 网关请求体 model 字段使用的模型名称。 */
        @NotBlank
        private String modelName;
        /**
         * 本项目使用的模型版本标记。数据库只加载相同版本的历史向量，避免新旧模型的结果混在一起。
         */
        @NotBlank
        private String modelVersion;
        /**
         * 每条向量包含多少个数字。请求模型、保存数据库和加载内存时必须使用同一个值。
         */
        @Min(1)
        private int dimension;
        /** 单次发送给模型网关的最大文本数量；批量能力未确认前保持为 1。 */
        @Min(1)
        private int batchSize = 1;
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

    /** Excel导入数量限制，防止一次上传过多数据导致请求等待过久或占用过多内存。 */
    @Data
    public static class ImportConfig {
        /** 单份 Excel 允许的最大非空数据行数。 */
        @Min(1)
        private int maxRows = 1000;
        /** 第一版允许持久化的历史段落总数，防止内存精确检索规模失控。 */
        @Min(1)
        private int maxTotalSamples = 10000;
    }

    /**
     * 新段落与历史段落比较时使用的规则。
     *
     * <p>这些参数只决定取多少条历史记录、什么分数可以返回，不会改变数据库里的历史样本。</p>
     */
    @Data
    public static class Search {
        /** 与新段落最接近的历史记录最多保留多少条。 */
        @Min(1)
        private int retrieveTopK = 10;
        /** 从相似记录中最多取多少条，用它们已有的类型编码进行综合判断。 */
        @Min(1)
        private int voteTopK = 10;
        /** 接口最多向调用方展示多少条历史参考段落。 */
        @Min(1)
        private int evidenceTopK = 5;
        /**
         * 规范化后合同段落最大字符数。
         *
         * <p>该值是业务字符上限，不等同于模型 Token 上限。模型平台确认上下文长度后可通过
         * 外部配置调整；服务始终明确拒绝超长段落，不做静默截断。</p>
         */
        @Min(1)
        private int maxParagraphLength = 2000;
        /** 历史段落至少达到这个相似度，才参与类型判断。 */
        @DecimalMin("-1.0")
        @DecimalMax("1.0")
        private double minSimilarity = 0.60D;
        /** 一个类型的综合支持得分达到这个值，才可能标记为高可信。 */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double highThreshold = 0.80D;
        /** 一个类型至少达到这个得分，才会作为候选返回。 */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double candidateThreshold = 0.55D;
        /** 除了分数足够高，还需要至少这么多条历史段落共同支持，才能标记为高可信。 */
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
