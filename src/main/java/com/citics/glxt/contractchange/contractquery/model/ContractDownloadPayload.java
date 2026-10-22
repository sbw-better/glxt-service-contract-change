package com.citics.glxt.contractchange.contractquery.model;

/** Service 组装好的下载内容及响应所需的文件信息。 */
public class ContractDownloadPayload {
    private final String fileName;
    private final String fallbackFileName;
    private final String contentType;
    private final byte[] content;

    public ContractDownloadPayload(String fileName, String fallbackFileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.fallbackFileName = fallbackFileName;
        this.contentType = contentType;
        this.content = content;
    }

    public String getFileName() { return fileName; }
    public String getFallbackFileName() { return fallbackFileName; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
