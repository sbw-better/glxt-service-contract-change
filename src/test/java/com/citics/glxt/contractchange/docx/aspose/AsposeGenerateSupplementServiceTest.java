package com.citics.glxt.contractchange.docx.aspose;

import com.aspose.words.Comment;
import com.aspose.words.CommentRangeEnd;
import com.aspose.words.CommentRangeStart;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Node;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Run;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeContractParser;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeDocumentIO;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeGenerateSupplementService;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeReplaceService;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigurationService;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AsposeGenerateSupplementServiceTest {
    @Test
    public void replacesAgreementPlaceholderWithClauseForBlankCommentInsertionRange() throws Exception {
        final Document revision = revisionWithBlankCommentInsertion();
        final Document template = templateWithPlaceholder();
        final MemoryDocumentIO io = new MemoryDocumentIO(revision, template);
        ReplaceContentConfigurationService configs = mock(ReplaceContentConfigurationService.class);
        when(configs.list(any())).thenReturn(Collections.emptyList());

        AsposeGenerateSupplementService service = new AsposeGenerateSupplementService();
        set(service, "documentIO", io);
        set(service, "parser", new AsposeContractParser());
        set(service, "replaceService", new AsposeReplaceService());
        set(service, "configService", configs);

        DocxAnalysisDTO request = new DocxAnalysisDTO();
        request.setRevisionFileGetPath("revision.docx");
        request.setSupplementFileGetPath("template.docx");
        request.setSupplementFileSavePath("output.docx");
        request.setGenerateByRevisionType(1);
        request.setIsAllText(true);
        request.setIsModify(false);
        service.generate(request);

        assertNotNull(io.saved);
        String result = io.saved.getText();
        assertFalse(result.contains("#{BCXYMBXGNR}"));
        assertTrue(result.contains("新增如下约定"));
        assertTrue(result.contains("新增条款内容"));
    }

    private Document revisionWithBlankCommentInsertion() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("一、合同当事人");
        Paragraph paragraph = (Paragraph) document.getLastSection().getBody()
                .getChildNodes(NodeType.PARAGRAPH, false).get(1);
        Comment comment = new Comment(document, "tester", "tester", new Date());
        int id = comment.getId();
        builder.moveTo(paragraph);
        builder.insertNode(new CommentRangeStart(document, id));
        document.startTrackRevisions("tester", new Date());
        builder.write("新增条款内容");
        document.stopTrackRevisions();
        builder.insertNode(new CommentRangeEnd(document, id));
        builder.insertNode(comment);
        return document;
    }

    private Document templateWithPlaceholder() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("补充协议：");
        builder.write("#{BCXYMBXGNR}");
        return document;
    }

    private void set(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class MemoryDocumentIO extends AsposeDocumentIO {
        private final Document revision;
        private final Document template;
        private Document saved;
        private MemoryDocumentIO(Document revision, Document template) {
            this.revision = revision;
            this.template = template;
        }
        @Override public Document load(String path) {
            return "revision.docx".equals(path) ? revision : template;
        }
        @Override public void save(Document document, String destination) {
            this.saved = document;
        }
    }
}
