package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.aspose.words.Shape;
import com.aspose.words.ShapeType;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
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

        ContractCompareResponse response = service.extract(bytes);

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
        assertNull(response.getChanges().get(0).getContext());

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

        ContractCompareResponse response = service.extract(bytes);

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

        ContractCompareResponse response = service.extract(save(document));

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
                        "原约定内容。"));

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertEquals("原约定内容。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertNull(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    public void shouldTreatExplicitChangeAsFollowsHeadingContentAsNewText() throws Exception {
        ContractCompareResponse response = service.extract(document(
                "6、《基金合同》第十二节第（四）条变更如下约定：",
                "“（四）投资限制",
                "1、根据《运作指引》，本基金应遵循下述投资限制：",
                "（1）本基金投资于期货和衍生品的比例不得低于80%；”"));

        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertNull(response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent()
                .contains("1、根据《运作指引》"));
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    public void shouldTreatChapterLevelNumberedProvisionAsAddedInsteadOfOldText() throws Exception {
        ContractCompareResponse response = service.extract(document(
                "7、《基金合同》第十二节“基金的投资”如下约定：",
                "“（十二）本基金主要投资标的基金的投资范围、投资策略如下：",
                "1、标的基金的投资范围：",
                "2、标的基金的投资策略：”"));

        assertEquals(ChangeType.ADDED, response.getChanges().get(0).getChangeType());
        assertNull(response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent()
                .contains("（十二）本基金主要投资标的基金"));
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent()
                .contains("1、标的基金的投资范围"));
    }

    @Test
    public void shouldRejectEvaluationModeTruncatedDocumentInsteadOfReturningPartialChanges()
            throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("1、《基金合同》第二条增加如下约定：");
        for (int index = 0; index < 240; index++) {
            builder.writeln("新增条款内容段落" + index + "。");
        }

        try {
            service.extract(save(document));
        } catch (ContractChangeBusinessException exception) {
            assertTrue(exception.getMessage().contains("解析不完整"));
            assertTrue(exception.getMessage().contains("有效许可证"));
            return;
        }
        throw new AssertionError("应拒绝处理被 Aspose Evaluation Mode 截断的文档");
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

        ContractCompareResponse response = service.extract(save(document));

        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent()
                .contains("文本框中的新增风险提示"));
    }

    @Test
    public void shouldApplyExecutionDateHeadingRuleWithoutDocumentType() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、自本函件的变更执行日起，在《基金合同》第一条中增加如下约定：",
                        "新增内容。"));

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.ADDED, response.getChanges().get(0).getChangeType());
        assertEquals("新增内容。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldAcceptExecutionDateHeadingWithoutDeParticle() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、自本协议变更执行日起，在《基金合同》第一条中增加如下约定：",
                        "“新增内容。”"));

        assertEquals(1, response.getTotalChanges());
        assertEquals(ChangeType.ADDED, response.getChanges().get(0).getChangeType());
        assertEquals("“新增内容。”",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldKeepConsecutiveQuotedBlocksUnderOneHeading() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、在《基金合同》第一条中增加如下约定：",
                        "“新增约定一。”",
                        "“新增约定二。”",
                        "本次变更的办理流程。"));

        assertEquals("“新增约定一。”\n“新增约定二。”",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldWarnInsteadOfTreatingCombinedActionAsPureDeletion() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、删除《基金合同》第一条原约定并增加如下约定：",
                        "“替代后的新约定。”"));

        assertEquals(ChangeType.MODIFIED, response.getChanges().get(0).getChangeType());
        assertNull(response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertEquals("“替代后的新约定。”",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertTrue(response.getChanges().get(0).getChangedParagraphs().get(0)
                .getChangeDetails().isEmpty());
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    public void shouldKeepOldTextBeforeMarkerInSameParagraph() throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、《基金合同》第二条“合同金额”约定如下：",
                        "合同金额为100万元。上述内容变更如下：合同金额为120万元。"));

        assertEquals("合同金额为100万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getOldContent());
        assertEquals("合同金额为120万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldKeepAllUnquotedNewContentBlocksAfterMarker() throws Exception {
        ContractCompareResponse response = service.extract(document(
                "1、《基金合同》第二条“合同金额”约定如下：",
                "合同金额为100万元。上述内容变更如下：合同金额调整为120万元。",
                "调整后的金额自本协议生效日起适用。",
                "相关计算口径保持不变。"));

        assertEquals("合同金额调整为120万元。\n调整后的金额自本协议生效日起适用。\n相关计算口径保持不变。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertTrue(response.getWarnings().stream()
                .anyMatch(warning -> warning.contains("未使用引号且跨多个文本块")));
    }

    @Test
    public void shouldKeepSingleUnquotedNewContentBlockWithoutBoundaryWarning() throws Exception {
        ContractCompareResponse response = service.extract(document(
                "1、《基金合同》第二条“合同金额”约定如下：",
                "合同金额为100万元。上述内容变更如下：合同金额调整为120万元。"));

        assertEquals("合同金额调整为120万元。",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertFalse(response.getWarnings().stream()
                .anyMatch(warning -> warning.contains("未使用引号且跨多个文本块")));
    }

    @Test
    public void shouldExtractQuotedContentAcrossParagraphsAndHeadingWithPrefix()
            throws Exception {
        ContractCompareResponse response = service.extract(document(
                        "1、《基金合同》第十二部分第（三）条约定如下：",
                        "“（三）投资策略",
                        "商品及金融期货的跨期套利。",
                        "原投资策略说明。”",
                        "上述内容变更如下：",
                        "“（三）投资策略",
                        "商品期货期权及金融期货的跨期套利。",
                        "新投资策略说明。”",
                        "2、在《基金合同》“声明与承诺”部分增加如下约定：",
                        "“新增声明第一段。",
                        "新增声明第二段。”",
                        "二、本次变更的后续办理流程。"));

        assertEquals(2, response.getTotalChanges());
        assertEquals("“（三）投资策略\n商品期货期权及金融期货的跨期套利。\n新投资策略说明。”",
                response.getChanges().get(0).getChangedParagraphs().get(0).getNewContent());
        assertEquals(ChangeType.ADDED, response.getChanges().get(1).getChangeType());
        assertEquals("“新增声明第一段。\n新增声明第二段。”",
                response.getChanges().get(1).getChangedParagraphs().get(0).getNewContent());
        assertFalse(response.getChanges().get(1).getChangedParagraphs().get(0)
                .getNewContent().contains("后续办理流程"));
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
