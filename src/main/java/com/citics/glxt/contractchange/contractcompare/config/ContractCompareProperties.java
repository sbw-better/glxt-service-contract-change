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
}
