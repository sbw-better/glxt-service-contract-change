package com.citics.glxt.contractchange.contractcompare.aspose;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService.ComparisonResult;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AsposeCompareServiceTest {
    @Test
    public void shouldGenerateRevisionSignalsForTextChange() throws Exception {
        ContractCompareProperties properties = new ContractCompareProperties();
        properties.setRequireAsposeLicense(false);
        AsposeCompareService service = new AsposeCompareService(properties, new DefaultResourceLoader());
        service.initialize();

        ComparisonResult result = service.compare(document("第二条 合同金额\n人民币100万元。"),
                document("第二条 合同金额\n人民币120万元。"));

        assertFalse(result.getOldDocument().hasRevisions());
        assertFalse(result.getNewDocument().hasRevisions());
        assertTrue(result.getRevisions().size() > 0);
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
}
