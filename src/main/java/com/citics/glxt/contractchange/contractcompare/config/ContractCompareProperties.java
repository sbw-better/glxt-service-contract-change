package com.citics.glxt.contractchange.contractcompare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;

/** 合同双版本比较配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "contract-compare")
public class ContractCompareProperties {
    /** 单份合同下载后的最大字节数。 */
    @Min(1)
    private int maxFileSizeBytes = 50 * 1024 * 1024;
    /** 模糊条款匹配的最低综合分数。 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double minMatchScore = 0.80D;
    /** 第一和第二候选必须达到的最小分差。 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double ambiguityMargin = 0.10D;
    /** 段落间作为“修改”配对的最低字符相似度；低于该值时拆成删除和新增，避免跨段硬配。 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double paragraphMinMatchScore = 0.15D;
    /** 相同父级和编号仍需达到的最低内容分，避免重排后复用编号导致跨主题误配。 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double numberPathMinMatchScore = 0.20D;
}
