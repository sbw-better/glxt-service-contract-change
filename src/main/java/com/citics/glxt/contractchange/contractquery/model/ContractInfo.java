package com.citics.glxt.contractchange.contractquery.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/** 合同文档查询结果，仅包含接口一所需的文档和产品元数据。 */
public class ContractInfo {
    /** 文档库文件编号，供后续下载接口使用。 */
    private Long fileId;
    /** 产品代码及产品名称。 */
    private String productCode;
    private String productName;
    /** 合同版本号，按文档库版本号和子版本号拼接。 */
    private String contractVersion;
    /** 文档库记录中的文件名称。 */
    private String fileName;
    /** 文档分类编码，例如106或108。 */
    private Integer categoryCode;

    /** 生效日期；数据库值为空时响应中保留为空。 */
    @JsonFormat(pattern = "yyyyMMdd", timezone = "GMT+8")
    private Date effectiveDate;

    /** 文档库记录最后更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String contractVersion) { this.contractVersion = contractVersion; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getCategoryCode() { return categoryCode; }
    public void setCategoryCode(Integer categoryCode) { this.categoryCode = categoryCode; }
    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
