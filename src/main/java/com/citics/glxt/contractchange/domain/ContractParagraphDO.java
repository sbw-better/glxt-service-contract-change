package com.citics.glxt.contractchange.domain;

import lombok.Data;

import java.util.Date;

/** Oracle 表 {@code TPIF_HTDLYB} 的数据对象。 */
@Data
public class ContractParagraphDO {
    /** 主键，由 {@code SEQ_TPIF_HTDLYB} 生成。 */
    private Long id;
    /** Excel 中的原始合同段落，对应 YWBW。 */
    private String originalText;
    /** 用于 Hash 和向量化的规范化段落，对应 GFBW。 */
    private String normalizedText;
    /** 规范化段落的 SHA-256 十六进制值。 */
    private String textHash;
    /** 排序去重后的英文分号分隔变更类型编码。 */
    private String changeTypeCodes;
    /** Float32 小端序编码的归一化向量。 */
    private byte[] vectorData;
    /** 向量维度，第一版固定为 768。 */
    private Integer vectorDim;
    /** 生成该向量的模型版本。 */
    private String modelVersion;
    /** 导入来源文件名，仅用于数据追溯。 */
    private String sourceFile;
    /** 是否参与检索：1 启用，0 停用。 */
    private Integer enabled;
    /** 创建时间。 */
    private Date createTime;
}
