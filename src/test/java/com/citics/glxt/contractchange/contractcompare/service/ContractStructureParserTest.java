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
