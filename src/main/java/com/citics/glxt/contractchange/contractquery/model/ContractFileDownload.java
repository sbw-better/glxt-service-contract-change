package com.citics.glxt.contractchange.contractquery.model;

/** 接口二内部使用的文件名和原始文件内容。 */
public class ContractFileDownload {
    private final String fileName;
    private final byte[] content;

    public ContractFileDownload(String fileName, byte[] content) {
        this.fileName = fileName;
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }
}
