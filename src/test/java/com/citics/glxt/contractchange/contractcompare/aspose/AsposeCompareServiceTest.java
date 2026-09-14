package com.citics.glxt.contractchange.contractcompare.aspose;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService.ComparisonResult;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class AsposeCompareServiceTest {
    @Test
    public void shouldGenerateRevisionSignalsForTextChange() throws Exception {
        AsposeCompareService service = new AsposeCompareService();

        ComparisonResult result = service.compare(document("第二条 合同金额\n人民币100万元。"),
                document("第二条 合同金额\n人民币120万元。"));

        assertFalse(result.getOldDocument().hasRevisions());
        assertFalse(result.getNewDocument().hasRevisions());
        assertTrue(result.getRevisions().size() > 0);
    }

    @Test
    public void shouldAcceptExistingRevisionsAndCompareFinalContent() throws Exception {
        AsposeCompareService service = new AsposeCompareService();

        ComparisonResult result = service.compare(revisedDocument(),
                document("第二条 合同金额\n人民币100万元。"));

        assertFalse(result.getOldDocument().hasRevisions());
        assertFalse(result.getNewDocument().hasRevisions());
        assertEquals(0, result.getRevisions().size());
    }

    private byte[] document(String content) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        for (String line : content.split("\\n")) {
            builder.writeln(line);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output, SaveFormat.DOCX);
        return output.toByteArray();
    }

    private byte[] revisedDocument() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("第二条 合同金额");
        document.startTrackRevisions("tester", new Date());
        builder.writeln("人民币100万元。");
        document.stopTrackRevisions();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output, SaveFormat.DOCX);
        return output.toByteArray();
    }
}
