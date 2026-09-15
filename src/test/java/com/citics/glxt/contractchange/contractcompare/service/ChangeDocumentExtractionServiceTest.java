package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.aspose.words.Shape;
import com.aspose.words.ShapeType;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.ResultMode;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChangeDocumentExtractionServiceTest {
    private ChangeDocumentExtractionService service;

    @Before
    public void setUp() {
        AsposeCompareService aspose = new AsposeCompareService();
        service = new ChangeDocumentExtractionService(aspose,
                new ClauseComparisonEngine(new ContractCompareProperties()));
    }

    @Test
    public void shouldExtractModifiedAndAddedClausesInDocumentOrder() throws Exception {
        byte[] bytes = document(
                "1、《基金合同》第九节“基金当事人的权利和义务”如下约定：",
                "本合同总金额为人民币100万元。",
                "自本协议的变更执行日起，上述内容变更如下：",
                "本合同总金额为人民币120万元。",
                "2、自本协议的变更执行日起，在《基金合同》“声明与承诺”部分第（一）条中增加如下约定：",
                "新增信息披露义务。"
        );

        ContractCompareResponse response = service.extract(bytes, ResultMode.CONTEXT);

        assertEquals(2, response.getTotalChanges());
        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertEquals("本合同总金额为人民币100万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertEquals("本合同总金额为人民币120万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertEquals("100", response.getChanges().get(0).getChangedParagraphs().get(0)
                .getChangeDetails().get(0).getOldText());
        assertTrue(response.getChanges().get(0).getTargetClauseReference()
                .startsWith("《基金合同》第九节"));
        assertEquals("本合同总金额为人民币120万元。",
                response.getChanges().get(0).getContext().getNewContent());

        assertEquals(ChangeType.ADDED, response.getChanges().get(1).getChangeType());
        assertNull(response.getChanges().get(1).getChangedParagraphs().get(0).getOldContent());
        assertEquals("新增信息披露义务。",
                response.getChanges().get(1).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldExtractDeletedClause() throws Exception {
        byte[] bytes = document(
                "1、自本协议的变更执行日起，删除《基金合同》第十二节“基金的投资”第（五）条如下约定：",
                "本基金投资于单一资产的资金不得超过基金净资产的25%。"
        );

        ContractCompareResponse response = service.extract(bytes, ResultMode.SIMPLE);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.DELETED, response.getChanges().get(0).getChangeType());
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent()
                .contains("25%"));
        assertNull(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertNull(response.getChanges().get(0).getContext());
    }

    @Test
    public void shouldReadAutomaticListLabelAndTableContent() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getListFormat().applyNumberDefault();
        builder.writeln("《基金合同》第十四节“基金的财产”中增加如下约定：");
        builder.getListFormat().removeNumbers();
        builder.startTable();
        builder.insertCell();
        builder.write("资产类型");
        builder.insertCell();
        builder.write("投资比例不超过20%");
        builder.endRow();
        builder.endTable();

        ContractCompareResponse response = service.extract(save(document), ResultMode.SIMPLE);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.ADDED, response.getChanges().get(0).getChangeType());
        assertTrue(response.getChanges().get(0).getSourceHeading().startsWith("1"));
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent()
                .contains("投资比例不超过20%"));
    }

    @Test
    public void shouldKeepRecognizedItemAndWarnWhenMarkerIsMissing() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、《基金合同》第二十三节“合同的变更”约定如下：",
                        "原约定内容。"),
                ResultMode.SIMPLE);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertEquals("原约定内容。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertNull(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    public void shouldReadEditableTextInsideTextBox() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("1、《基金合同》风险揭示书中增加如下约定：");
        Shape textBox = builder.insertShape(ShapeType.TEXT_BOX, 300, 80);
        builder.moveTo(textBox.getFirstParagraph());
        builder.write("文本框中的新增风险提示。");
        builder.moveToDocumentEnd();

        ContractCompareResponse response = service.extract(save(document), ResultMode.SIMPLE);

        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent()
                .contains("文本框中的新增风险提示"));
    }

    @Test
    public void shouldApplyExecutionDateHeadingRuleWithoutDocumentType() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、自本函件的变更执行日起，在《基金合同》第一条中增加如下约定：",
                        "新增内容。"),
                ResultMode.SIMPLE);

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.ADDED, response.getChanges().get(0).getChangeType());
        assertEquals("新增内容。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldKeepOldTextBeforeMarkerInSameParagraph() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、《基金合同》第二条“合同金额”约定如下：",
                        "合同金额为100万元。上述内容变更如下：合同金额为120万元。"),
                ResultMode.SIMPLE);

        assertEquals("合同金额为100万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertEquals("合同金额为120万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
    }

    private byte[] document(String... paragraphs) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        for (String paragraph : paragraphs) {
            builder.writeln(paragraph);
        }
        return save(document);
    }

    private byte[] save(Document document) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output, SaveFormat.DOCX);
        return output.toByteArray();
    }
}
