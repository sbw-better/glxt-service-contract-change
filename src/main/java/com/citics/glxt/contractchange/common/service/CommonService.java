package com.citics.glxt.common.service;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

public interface CommonService {
    Object downloadFromTable(String fileGetPath, boolean checkDocx);
    WordprocessingMLPackage sftpDownload2(String src,String encoding,boolean checkDocx);
    WordprocessingMLPackage sftpDownload2(String host,Integer port,String username,String password,String src,String encoding,boolean checkDocx);

}
