package com.citics.glxt.contractchange.common.utils.file;

import java.io.File;
import java.util.UUID;

public class ToolUtil
{
    /**
     * 获取临时目录
     * @author zmr
     */
    public static String getTempPath() { return System.getProperty("java.io.tmpdir"); }

    /**
     * 获取当前项目工作目录
     * @return
     * @author zmr
     */
    public static String getUserDir() { return System.getProperty("user.dir"); }

    /**
     * 获取临时下载目录
     * @return
     * @author zmr
     */
    public static String getDownloadPath() { return getTempPath() + File.separator + "download" + File.separator; }

    /**
     * 编码文件名
     */
    public static String encodingFilename(String filename)
    {
        filename = UUID.randomUUID().toString() + "_" + filename;
        return filename;
    }
}


