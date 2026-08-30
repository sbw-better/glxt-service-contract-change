package com.citics.glxt.contractchange.common.service.impl;

import com.citics.glxt.common.service.CommonService;
import com.citics.glxt.common.utils.file.SftpUtil;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;


@Slf4j
@Service
public class CommonServiceImpl implements CommonService {

    @Value("${common.file.product.ftp}")
    private String ftpPath;
    @Value("${common.glxt.word2pdf_url}")
    private String word2pdfUrl;

    // 主要sftp地址
    @Value("${common.sftp.host1.host}")
    private String sftp1Host;
    @Value("${common.sftp.host1.port}")
    private String sftp1Port;
    @Value("${common.sftp.host1.username}")
    private String sftp1Username;
    @Value("${common.sftp.host1.password}")
    private String sftp1Password;

    // 备用sftp地址
    @Value("${common.sftp.host2.host}")
    private String sftp2Host;
    @Value("${common.sftp.host2.port}")
    private String sftp2Port;
    @Value("${common.sftp.host2.username}")
    private String sftp2Username;
    @Value("${common.sftp.host2.password}")
    private String sftp2Password;

    // docx 文件头固定字节（PK\x03\x04）
    private static final byte[] DOCX_HEADER = new byte[]{0x50, 0x4B, 0x03, 0x04};

    /**
     * 从指定表名路径下下载文件
     * @param fileGetPath 文件获取地址
     * @param checkDocx 是否校验是docx格式
     * @return 结果
     */
    @Override
    public Object downloadFromTable(String fileGetPath, boolean checkDocx) {
        WordprocessingMLPackage downloadRes = null;
        //从服务器下载文档
        downloadRes = sftpDownload2(fileGetPath, "GBK", checkDocx);
        if (null == downloadRes) {
            log.error("从服务器sftp下载失败，文件获取地址：{}", fileGetPath);
            return null;
        }
        return downloadRes;
    }

    @Override
    public WordprocessingMLPackage sftpDownload2(String src, String encoding, boolean checkDocx) {
        WordprocessingMLPackage downloadRes = null;
        Integer ftpPort = Integer.valueOf(sftp1Port);
        downloadRes = sftpDownload2(sftp1Host, ftpPort, sftp1Username, sftp1Password, src, encoding, checkDocx);
        if (null == downloadRes) {
            ftpPort = Integer.valueOf(sftp2Port);
            downloadRes = sftpDownload2(sftp2Host, ftpPort, sftp2Username, sftp2Password, src, encoding, checkDocx);
        }
        return downloadRes;
    }

    @Override
    public WordprocessingMLPackage sftpDownload2(String host, Integer port, String username, String password, String src, String encoding, boolean checkDocx)
    {
        InputStream is = null;
        WordprocessingMLPackage res = null;
        log.info("sftp connect, host={}, port={}, username={},src={}, encoding={}", host, port, username, src, encoding);
        if (StringUtils.isEmpty(host) || null == port || StringUtils.isEmpty(src)) {
            log.error("sftp通用下载未传入所有基本参数, host={}:{}, username={}, src={}, encoding={}", host, port, username, src, encoding);
            return null;
        }
        SftpUtil sftpUtil = null;
        try {
            sftpUtil = SftpUtil.create(host, port);
            sftpUtil.connect(username, password, encoding);
            is = sftpUtil.download(src);
            //判断是否是docx格式
            if (checkDocx) {
                // 转为可重复读取的流
                is = toResettableInputStream(is);
                isFalse(!isDocx(is), "文件非docx格式，请核对！");
            }
            log.info("开始加载文件流到WordprocessingMLPackage对象...");
            res = WordprocessingMLPackage.load(is);
            log.info("加载文件流到WordprocessingMLPackage对象成功...");
        } catch (Exception e) {
            log.error("sftp下载失败, err={}, host={}:{}, username={}, src={}, encoding={}", e.getMessage(), host, port, username, src, encoding);
            throw new RuntimeException(e.getMessage());
        }
        finally {
            if (null != sftpUtil) {
                sftpUtil.disconnect();
            }
        }
        return res;
    }

    /**
     * 判断是否为 docx 格式
     */
    private static boolean isDocx(InputStream inputStream) throws IOException {
        inputStream.mark(4); // 标记流位置
        byte[] header = new byte[4];
        inputStream.read(header);
        inputStream.reset(); // 重置流
        return Arrays.equals(header, DOCX_HEADER);
    }

    /**
     * 转换为可重置的流（解决普通InputStream只能读一次的问题）
     */
    private static InputStream toResettableInputStream(InputStream inputStream) throws IOException {
        if (inputStream.markSupported()) {
            return inputStream;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return new ByteArrayInputStream(buffer.toByteArray());
    }

}
