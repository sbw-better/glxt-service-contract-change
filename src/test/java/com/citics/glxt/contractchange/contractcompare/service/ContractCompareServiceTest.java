package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证从两份 DOCX 字节到条款级响应的完整核心链路。 */
public class ContractCompareServiceTest {
    @Test
    public void shouldReturnOneCompleteModifiedClause() throws Exception {
        SftpContractFileLoader loader = mock(SftpContractFileLoader.class);
        when(loader.load("/old.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币100万元。"));
        when(loader.load("/new.docx")).thenReturn(document(
                "第二条 合同金额", "本合同总金额为人民币120万元。"));

        ContractCompareProperties properties = new ContractCompareProperties();
        AsposeCompareService aspose = new AsposeCompareService();
        ContractCompareService service = new ContractCompareService(loader, aspose,
                new ContractStructureParser(), new ClauseComparisonEngine(properties));
        ContractCompareRequest request = new ContractCompareRequest();
        request.setOldFileGetPath("/old.docx");
        request.setNewFileGetPath("/new.docx");

        ContractCompareResponse response = service.compare(request);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertTrue(response.getChanges().get(0).getOldContent().contains("100万元"));
        assertTrue(response.getChanges().get(0).getNewContent().contains("120万元"));
        assertEquals("100", response.getChanges().get(0).getChangeDetails().get(0).getOldText());
        assertEquals("120", response.getChanges().get(0).getChangeDetails().get(0).getNewText());
    }

    private byte[] document(String... paragraphs) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        for (String paragraph : paragraphs) {
            builder.writeln(paragraph);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output, SaveFormat.DOCX);
        return output.toByteArray();
    }
}
