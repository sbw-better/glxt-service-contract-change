package com.citics.glxt.contractchange.common.service.impl;

import com.citics.glxt.contractchange.common.service.CommonService;
import com.citics.glxt.contractchange.common.utils.file.SftpUtil;
import com.citics.glxt.contractchange.common.utils.file.ToolUtil;
import com.citics.glxt.contractchange.contractquery.domain.TpifCpwdk;
import com.citics.glxt.contractchange.contractquery.mapper.TpifCpwdkMapper;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.Arrays;

import static com.citics.glxt.contractchange.common.constants.CommonConstants.FTP_FILE_RETURN_TYPE_FILENAME;
import static com.citics.glxt.contractchange.common.constants.CommonConstants.FTP_FILE_RETURN_TYPE_FILEPATH;
import static com.citics.glxt.contractchange.common.utils.assertUtils.Assert.isFalse;


@Slf4j
@Service
public class CommonServiceImpl implements CommonService {

    @Value("${common.file.project.ftp}")
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

    @Autowired
    private TpifCpwdkMapper tpifCpwdkMapper;

    // docx 文件头固定字节（PK\x03\x04）
    private static final byte[] DOCX_HEADER = new byte[]{0x50, 0x4B, 0x03, 0x04};

    /**
     * 下载文档原始字节，不创建旧版 docx4j 文档模型。
     * 使其他文档处理引擎可以独立于 docx4j 加载器读取文档。
     */
    @Override
    public byte[] downloadBytesFromTable(String fileGetPath) {
        if (StringUtils.isEmpty(fileGetPath)) {
            throw new IllegalArgumentException("文档获取地址不得为空，请核对！");
        }
        byte[] bytes = downloadBytes(sftp1Host, Integer.valueOf(sftp1Port),
                sftp1Username, sftp1Password, fileGetPath);
        if (bytes == null) {
            bytes = downloadBytes(sftp2Host, Integer.valueOf(sftp2Port),
                    sftp2Username, sftp2Password, fileGetPath);
        }
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("从文档库下载文件失败");
        }
        return bytes;
    }

    /** 按文档库固定目录规则从 SFTP 下载原始文件。 */
    @Override
    public byte[] downloadDocLibBytes(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("文档编号必须为正数");
        }
        String src = ftpPath + id / 100 + "/" + id + ".FJ";
        return downloadBytesFromTable(src);
    }

    private byte[] downloadBytes(String host, Integer port, String username,
                                 String password, String path) {
        SftpUtil sftpUtil = null;
        try {
            sftpUtil = SftpUtil.create(host, port);
            sftpUtil.connect(username, password, "GBK");
            try (InputStream input = sftpUtil.download(path);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } catch (Exception e) {
            log.warn("SFTP 原始文件下载失败，路径：{}，原因：{}", path, e.getMessage());
            return null;
        } finally {
            if (sftpUtil != null) {
                sftpUtil.disconnect();
            }
        }
    }

    @Override
    public Boolean sftpUpload(String src, String dst, String encoding) {
        Integer ftpPort = Integer.valueOf(sftp1Port);
        Boolean uploadStatus = sftpUpload(
                src,
                sftp1Host,
                ftpPort,
                sftp1Username,
                sftp1Password,
                dst,
                encoding
        );

        if (!uploadStatus) {
            ftpPort = Integer.valueOf(sftp2Port);
            uploadStatus = sftpUpload(
                    src,
                    sftp2Host,
                    ftpPort,
                    sftp2Username,
                    sftp2Password,
                    dst,
                    encoding
            );
        }

        return uploadStatus;
    }

    /**
     * 通用Sftp上传
     *
     */
    @Override
    public Boolean sftpUpload(String src, String host, Integer port, String username, String password, String dst, String encoding)
    {
        log.info("sftp connect, srce={}, host={}, port={}, username={}, filepath={}, encoding={}",
                src, host, port, username, dst, encoding);

        if (StringUtils.isEmpty(host) || null == port || StringUtils.isEmpty(src) || StringUtils.isEmpty(dst)) {
            log.error("sftp通用上传未传入所有基本参数, host={}:{}, username={}, src={}, dst={}, encoding={}",
                    host, port, username, src, dst, encoding);
            return false;
        }

        SftpUtil sftpUtil = null;
        try {
            sftpUtil = SftpUtil.create(host, port);
            sftpUtil.connect(username, password, encoding);
            sftpUtil.upload(src, dst);

        } catch (Exception e) {
            log.error("sftp上传失败, err={}, host={}:{}, username={}, src={}, dst={}, encoding={}",
                    e.getMessage(), host, port, username, src, dst, encoding);
            e.printStackTrace();
            return false;
        } finally {
            sftpUtil.disconnect();
        }

        return true;
    }

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
     * 从FTP文档库下载文件
     * @param id ID
     * @return 结果 根据type返回下载文件的文件名或下载到的临时路径
     */
    @Override
    public String downloadFromDocLib(Long id, int type) {
        // 暂时不从本地获取文件
        /*
        String path = getPath();
        File originFile = new File(path + id + ".FJ");
        */

        TpifCpwdk docLibInfo = tpifCpwdkMapper.selectByPrimaryKey(id);
        if (docLibInfo == null) {
            log.error("通用文档库下载-未找到文档记录,id={}", id);
            return null;
        }
        String docName = docLibInfo.getWdmc();
        if (StringUtils.isEmpty(docName)) {
            log.error("通用文档库下载-未找到文件名,id={}", id);
            return null;
        }

        String dstName = ToolUtil.encodingFilename(docName);
        String dst = ToolUtil.getDownloadPath() + dstName;
        File newFile = new File(dst);
        if (!newFile.getParentFile().exists()) {
            newFile.getParentFile().mkdirs();
        }
        if (newFile.exists()) {
            newFile.delete();
        }

        //从服务器下载文档
        String src = ftpPath + id/100 + "/" + id + ".FJ";
        Boolean downloadStatus = sftpDownload(src, dst, "GBK");

        if (!downloadStatus) {
            log.error("从服务器sftp下载失败，prod_doc_lib_id={}", id);
            return null;
        }

        if (type == FTP_FILE_RETURN_TYPE_FILENAME) {
            return dstName;
        }
        if (type == FTP_FILE_RETURN_TYPE_FILEPATH) {
            return dst;
        }
        return dstName;
    }

    @Override
    public Boolean sftpDownload(String src, String dst, String encoding) {
        Integer ftpPort = Integer.valueOf(sftp1Port);
        Boolean downloadStatus = sftpDownload(sftp1Host, ftpPort, sftp1Username, sftp1Password, src, dst, encoding);
        if (!downloadStatus) {
            ftpPort = Integer.valueOf(sftp2Port);
            downloadStatus = sftpDownload(sftp2Host, ftpPort, sftp2Username, sftp2Password, src, dst, encoding);
        }
        return downloadStatus;
    }

    /**
     * 通用sftp下载 将文件下载到指定路径下
     */
    @Override
    public Boolean sftpDownload(String host, Integer port, String username, String password, String src, String dst, String encoding)
    {
        InputStream is = null;
        log.info("sftp connect, host={}, port={}, username={}, src={}, dst={}, encoding={}", host, port, username, src, dst, encoding);
        if (StringUtils.isEmpty(host) || null == port || StringUtils.isEmpty(src) || StringUtils.isEmpty(dst)) {
            log.error("sftp通用下载未传入所有基本参数, host={}:{}, username={}, src={}, dst={}, encoding={}", host, port, username, src, dst, encoding);
            return false;
        }

        SftpUtil sftpUtil = null;
        try {
            sftpUtil = SftpUtil.create(host, port);
            sftpUtil.connect(username, password, encoding);
            sftpUtil.download(src, dst);

        } catch (Exception e) {
            log.error("sftp下载失败, err={}, host={}:{}, username={}, src={}, dst={}, encoding={}", e.getMessage(), host, port, username, src, dst, encoding);
            return false;
        }
        finally {
            sftpUtil.disconnect();
        }
        return true;
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
