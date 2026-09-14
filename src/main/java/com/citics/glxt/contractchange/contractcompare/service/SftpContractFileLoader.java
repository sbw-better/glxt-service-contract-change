package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.common.exception.ContractChangeBusinessException;
import com.citics.glxt.common.utils.file.SftpUtil;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 从现有主备 SFTP 服务器读取原始 DOCX 字节。 */
@Slf4j
@Service
public class SftpContractFileLoader {
    @Value("${common.sftp.host1.host}")
    private String host1;
    @Value("${common.sftp.host1.port}")
    private int port1;
    @Value("${common.sftp.host1.username}")
    private String username1;
    @Value("${common.sftp.host1.password}")
    private String password1;
    @Value("${common.sftp.host2.host}")
    private String host2;
    @Value("${common.sftp.host2.port}")
    private int port2;
    @Value("${common.sftp.host2.username}")
    private String username2;
    @Value("${common.sftp.host2.password}")
    private String password2;

    private final ContractCompareProperties properties;

    public SftpContractFileLoader(ContractCompareProperties properties) {
        this.properties = properties;
    }

    public byte[] load(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ContractChangeBusinessException("合同文件路径不能为空");
        }
        Exception primaryFailure;
        try {
            byte[] bytes = download(host1, port1, username1, password1, path);
            validateDocxHeader(bytes);
            return bytes;
        } catch (ContractChangeBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            primaryFailure = ex;
            log.warn("主SFTP读取合同失败, exception={}", ex.getClass().getSimpleName());
        }
        try {
            byte[] bytes = download(host2, port2, username2, password2, path);
            validateDocxHeader(bytes);
            return bytes;
        } catch (ContractChangeBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("备用SFTP读取合同失败, primaryException={}, backupException={}",
                    primaryFailure.getClass().getSimpleName(), ex.getClass().getSimpleName());
            throw new ContractChangeBusinessException("合同文件下载失败，请核对服务器路径");
        }
    }

    private byte[] download(String host, int port, String username, String password, String path) throws Exception {
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("SFTP host is empty");
        }
        SftpUtil client = SftpUtil.create(host, port);
        try {
            client.connect(username, password, "GBK");
            try (InputStream input = client.download(path);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > properties.getMaxFileSizeBytes()) {
                        throw new ContractChangeBusinessException("单份合同文件大小超过限制");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            client.disconnect();
        }
    }

    private void validateDocxHeader(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B) {
            throw new ContractChangeBusinessException("合同文件不是有效的DOCX文件");
        }
    }
}
