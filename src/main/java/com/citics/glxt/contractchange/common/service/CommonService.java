package com.citics.glxt.contractchange.common.service;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

public interface CommonService {
    /** 下载原始文件字节，不通过 DOCX 模型解析。 */
    byte[] downloadBytesFromTable(String fileGetPath);
    /** 通过文档库记录 ID 下载原始文件字节。 */
    byte[] downloadDocLibBytes(Long id);
    Boolean sftpUpload(String src, String dst, String encoding);
    Boolean sftpUpload(String src, String host, Integer port, String username, String password, String dst, String encoding);
    Object downloadFromTable(String fileGetPath, boolean checkDocx);
    WordprocessingMLPackage sftpDownload2(String src,String encoding,boolean checkDocx);
    WordprocessingMLPackage sftpDownload2(String host,Integer port,String username,String password,String src,String encoding,boolean checkDocx);
    /**
     * 从FTP文档库下载文件
     */
    String downloadFromDocLib(Long id, int type);

    Boolean sftpDownload(String src, String dst, String encoding);

    Boolean sftpDownload(String host, Integer port, String username, String password, String src, String dst, String encoding);


}
