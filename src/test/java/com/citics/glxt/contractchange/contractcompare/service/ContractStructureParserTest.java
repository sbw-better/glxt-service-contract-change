package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeDetail;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContractStructureParserTest {
    @Test
    public void shouldParseNestedClausesAndTableContent() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("合同编号：TEST-001");
        builder.writeln("第三条 付款方式");
        builder.writeln("3.1 首期款");
        builder.writeln("甲方应支付首期款。 ");
        builder.writeln("3.2 尾款");
        builder.startTable();
        builder.insertCell();
        builder.write("付款比例");
        builder.insertCell();
        builder.write("20%");
        builder.endRow();
        builder.endTable();
        builder.writeln("3.2.1");
        builder.writeln("尾款应在验收后支付。");

        Parsed parsed = new ContractStructureParser().parse(document);

        assertEquals(5, parsed.getClauses().size());
        Clause preamble = parsed.getClauses().get(0);
        Clause parent = parsed.getClauses().get(1);
        Clause child = parsed.getClauses().get(3);
        assertNull(preamble.getClauseNo());
        assertEquals("第三条", parent.getClauseNo());
        assertEquals("3.2", child.getClauseNo());
        assertEquals(parent, child.getParent());
        assertTrue(child.getContentParts().get(1).contains("付款比例 | 20%"));
        assertEquals("3.2.1", parsed.getClauses().get(4).getClauseNo());
        assertEquals(child, parsed.getClauses().get(4).getParent());
    }

    @Test
    public void shouldLocateDetailedAmountChangeInsideFlattenedTable() throws Exception {
        ContractStructureParser parser = new ContractStructureParser();
        Parsed oldContract = parser.parse(tableDocument("100万元"));
        Parsed newContract = parser.parse(tableDocument("120万元"));
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());

        List<ClauseChange> changes = engine.merge(oldContract, newContract,
                engine.match(oldContract, newContract));

        assertEquals(1, changes.size());
        assertEquals(1, changes.get(0).getChangedParagraphs().size());
        ChangedParagraph paragraph = changes.get(0).getChangedParagraphs().get(0);
        ChangeDetail detail = paragraph.getChangeDetails().get(0);
        assertEquals("100", detail.getOldText());
        assertEquals("120", detail.getNewText());
        assertTrue(paragraph.getOldContent().contains("合同金额 | 100万元"));
        assertTrue(paragraph.getNewContent().contains("合同金额 | 120万元"));
    }

    @Test
    public void shouldSplitChinesePartChapterAndSectionHeadings() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("第一章 总则");
        builder.writeln("本章内容。");
        builder.writeln("第二部分 投资范围");
        builder.writeln("（一）证券投资");
        builder.writeln("本条内容。");
        builder.writeln("第三节 风险揭示");
        builder.writeln("本节内容。");

        Parsed parsed = new ContractStructureParser().parse(document);

        assertEquals(4, parsed.getClauses().size());
        assertEquals("第一章", parsed.getClauses().get(0).getClauseNo());
        assertEquals("第二部分", parsed.getClauses().get(1).getClauseNo());
        assertEquals("（一）", parsed.getClauses().get(2).getClauseNo());
        assertEquals(parsed.getClauses().get(1), parsed.getClauses().get(2).getParent());
        assertEquals("第三节", parsed.getClauses().get(3).getClauseNo());
        assertEquals("投资范围", parsed.getClauses().get(1).getTitle());
    }

    @Test
    public void shouldSkipUnstyledTocAndKeepPrefaceOutOfBodyHierarchy() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("合同编号：TEST-002");
        builder.writeln("远澜基金合同");
        builder.writeln("合格投资者承诺书");
        builder.writeln("投资者承诺内容");
        builder.writeln("目    录");
        builder.writeln("一、合同当事人\t4");
        builder.writeln("一、合同当事人");
        builder.writeln("合同主体内容。");

        Parsed parsed = new ContractStructureParser().parse(document);

        assertEquals(3, parsed.getClauses().size());
        assertTrue(parsed.getClauses().get(0).isSynthetic());
        assertEquals("合同编号：TEST-002", parsed.getClauses().get(0).getTitle());
        assertEquals(2, parsed.getClauses().get(0).getContentParts().size());
        assertEquals("合格投资者承诺书", parsed.getClauses().get(1).getTitle());
        assertTrue(parsed.getClauses().get(1).isSynthetic());
        assertEquals("一、", parsed.getClauses().get(2).getClauseNo());
        assertEquals("合同当事人", parsed.getClauses().get(2).getTitle());
        assertNull(parsed.getClauses().get(2).getParent());
        for (Clause clause : parsed.getClauses()) {
            for (String part : clause.getContentParts()) {
                assertTrue(!part.contains("目    录"));
                assertTrue(!part.contains("合同当事人\t4"));
            }
        }
    }

    @Test
    public void shouldIgnoreAsposeEvaluationWatermarkInParsedContractContent() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("Evaluation Only. Created with Aspose.Words. Copyright 2003-2019 Aspose Pty Ltd.");
        builder.writeln("第二条 合同金额");
        builder.writeln("合同金额为100万元。");

        Parsed parsed = new ContractStructureParser().parse(document);

        assertEquals(1, parsed.getClauses().size());
        assertEquals("第二条", parsed.getClauses().get(0).getClauseNo());
        for (String part : parsed.getClauses().get(0).getContentParts()) {
            assertTrue(!part.contains("Evaluation Only"));
        }
    }

    @Test
    public void shouldNestSimpleNumberedStepsUnderParenthesizedClauseAndPreserveTableRows() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("二十三、基金财产清算");
        builder.writeln("（四）基金财产清算程序");
        builder.writeln("1、对基金资产进行清理和确认；");
        builder.writeln("2、对基金资产进行估价；");
        builder.writeln("3、对基金资产进行变现；");
        builder.startTable();
        builder.insertCell();
        builder.write("项目");
        builder.insertCell();
        builder.write("金额");
        builder.endRow();
        builder.insertCell();
        builder.write("清算资产");
        builder.insertCell();
        builder.write("100万元");
        builder.endRow();
        builder.endTable();

        Parsed parsed = new ContractStructureParser().parse(document);

        Clause section = parsed.getClauses().get(0);
        Clause procedure = parsed.getClauses().get(1);
        Clause step1 = parsed.getClauses().get(2);
        Clause step2 = parsed.getClauses().get(3);
        Clause step3 = parsed.getClauses().get(4);
        assertEquals("二十三、", section.getClauseNo());
        assertEquals("（四）", procedure.getClauseNo());
        assertEquals(procedure, step1.getParent());
        assertEquals(procedure, step2.getParent());
        assertEquals(procedure, step3.getParent());
        assertEquals(3, procedure.getChildren().size());
        assertEquals(3, step3.getContentParts().size());
        assertTrue(step3.getContentParts().get(1).contains("项目 | 金额"));
        assertTrue(step3.getContentParts().get(2).contains("清算资产 | 100万元"));
    }

    @Test
    public void shouldNestChineseNumberedSubheadingsUnderCurrentClause() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("第二十三、基金财产清算");
        builder.writeln("（四）基金财产清算程序");
        builder.writeln("一、清理和确认");
        builder.writeln("二、估价和变现");

        Parsed parsed = new ContractStructureParser().parse(document);

        Clause procedure = parsed.getClauses().get(1);
        Clause firstStep = parsed.getClauses().get(2);
        Clause secondStep = parsed.getClauses().get(3);
        assertEquals(procedure, firstStep.getParent());
        assertEquals(procedure, secondStep.getParent());
        assertEquals(firstStep.getLevel(), secondStep.getLevel());
    }

    @Test
    public void shouldResumeOuterNumberingAfterNestedRiskItems() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("二十二、风险揭示");
        builder.writeln("8、基金投资风险");
        builder.writeln("（1）市场风险");
        builder.writeln("市场风险说明。");
        builder.writeln("（2）流动性风险");
        builder.writeln("流动性风险说明。");
        builder.writeln("9、其他特殊风险");
        builder.writeln("其他风险说明。");

        Parsed parsed = new ContractStructureParser().parse(document);

        Clause section = parsed.getClauses().get(0);
        Clause investmentRisk = parsed.getClauses().get(1);
        Clause marketRisk = parsed.getClauses().get(2);
        Clause liquidityRisk = parsed.getClauses().get(3);
        Clause otherRisk = parsed.getClauses().get(4);
        assertEquals("二十二、", section.getClauseNo());
        assertEquals(section, investmentRisk.getParent());
        assertEquals(investmentRisk, marketRisk.getParent());
        assertEquals(investmentRisk, liquidityRisk.getParent());
        assertEquals(section, otherRisk.getParent());
    }

    private Document tableDocument(String amount) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("第二条 合同金额");
        builder.startTable();
        builder.insertCell();
        builder.write("项目");
        builder.insertCell();
        builder.write("金额");
        builder.endRow();
        builder.insertCell();
        builder.write("合同金额");
        builder.insertCell();
        builder.write(amount);
        builder.endRow();
        builder.endTable();
        return document;
    }
}
