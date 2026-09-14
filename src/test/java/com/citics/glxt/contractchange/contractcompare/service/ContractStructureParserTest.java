package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import org.junit.Test;

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

        Parsed parsed = new ContractStructureParser().parse(document, "NEW");

        assertEquals(5, parsed.getClauses().size());
        Clause preamble = parsed.getClauses().get(0);
        Clause parent = parsed.getClauses().get(1);
        Clause child = parsed.getClauses().get(3);
        assertNull(preamble.getClauseNo());
        assertEquals("第三条", parent.getClauseNo());
        assertEquals("3.2", child.getClauseNo());
        assertEquals(parent, child.getParent());
        assertEquals("TABLE", child.getBlocks().get(1).getType());
        assertTrue(child.getBlocks().get(1).getText().contains("20%"));
        assertEquals("3.2.1", parsed.getClauses().get(4).getClauseNo());
        assertEquals(child, parsed.getClauses().get(4).getParent());
    }
}
