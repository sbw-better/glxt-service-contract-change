package com.citics.glxt.contractchange.docx.aspose.service;

import com.aspose.words.Document;
import com.citics.glxt.contractchange.common.service.CommonService;
import com.citics.glxt.contractchange.common.utils.file.ToolUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/** 使用 Aspose 直接加载和保存 Word 文件，避免经过 docx4j 转换。 */
@Slf4j
@Service
public class AsposeDocumentIO {
    @Resource
    private CommonService commonService;

    public Document load(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("文档获取地址不得为空，请核对！");
        }
        try {
            byte[] bytes = commonService.downloadBytesFromTable(path);
            Document document = new Document(new ByteArrayInputStream(bytes));
            log.info("Aspose 文档加载完成，段落数：{}，表格数：{}",
                    document.getChildNodes(com.aspose.words.NodeType.PARAGRAPH, true).getCount(),
                    document.getChildNodes(com.aspose.words.NodeType.TABLE, true).getCount());
            return document;
        } catch (Exception e) {
            throw new IllegalArgumentException("Aspose 文档解析失败，请确认文件有效且未加密。", e);
        }
    }

    public void save(Document document, String destination) {
        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("文档保存地址不得为空，请核对！");
        }
        File tempDirectory = new File(ToolUtil.getDownloadPath());
        File tempFile = null;
        try {
            if (!tempDirectory.exists() && !tempDirectory.mkdirs()) {
                throw new IllegalStateException("创建文档临时目录失败");
            }
            tempFile = new File(tempDirectory, UUID.randomUUID() + "_aspose.docx");
            document.save(tempFile.getAbsolutePath());
            if (!commonService.sftpUpload(tempFile.getAbsolutePath(), destination, "GBK")) {
                throw new IllegalStateException("文档上传失败");
            }
            log.info("Aspose 文档保存完成，目标路径：{}", destination);
        } catch (Exception e) {
            throw new IllegalStateException("Aspose 文档保存失败，目标路径：" + destination, e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (Exception e) {
                    log.warn("删除 Aspose 临时文件失败：{}", tempFile.getAbsolutePath());
                }
            }
        }
    }
}

