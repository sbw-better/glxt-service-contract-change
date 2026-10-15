package com.citics.glxt.contractchange.common.service;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

public interface CommonService {
    Boolean sftpUpload(String src, String dst, String encoding);
    Boolean sftpUpload(String src, String host, Integer port, String username, String password, String dst, String encoding);
    Object downloadFromTable(String fileGetPath, boolean checkDocx);
    WordprocessingMLPackage sftpDownload2(String src,String encoding,boolean checkDocx);
    WordprocessingMLPackage sftpDownload2(String host,Integer port,String username,String password,String src,String encoding,boolean checkDocx);

}
