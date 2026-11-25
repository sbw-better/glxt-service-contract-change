package com.citics.glxt.contractchange.docx.aspose;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.FindReplaceOptions;
import com.aspose.words.HeaderFooter;
import com.aspose.words.HeaderFooterType;
import com.aspose.words.NodeType;
import com.citics.glxt.contractchange.docx.aspose.model.ContractParagraph;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeContractParser;
import com.citics.glxt.contractchange.docx.aspose.service.AsposeReplaceService;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AsposeDocumentBehaviorTest {
    @Test
    public void parserTracksModuleAndCurrentHeadingWithoutMarkingBodyAsHeading() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("一、合同当事人");
        builder.writeln("（一）基本情况");
        builder.writeln("本段是正文");

        List<ContractParagraph> paragraphs = new AsposeContractParser().parse(document);
        assertEquals(3, paragraphs.size());
        assertEquals("（一）基本情况", paragraphs.get(1).getText());
        assertEquals(2, paragraphs.get(1).getTitleLevel());
        assertEquals("（一）", paragraphs.get(1).getTitlePrefix());
        assertEquals(0, paragraphs.get(2).getTitleLevel());
        assertEquals(paragraphs.get(1).getModule(), paragraphs.get(2).getModule());
        assertEquals("合同当事人", paragraphs.get(2).getTitleTexts()[0]);
    }

    @Test
    public void replaceEverywhereIncludesTablesAndHeaders() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("正文旧值");
        builder.startTable();
        builder.insertCell();
        builder.write("表格旧值");
        builder.endRow();
        builder.endTable();
        DocumentBuilder headerBuilder = new DocumentBuilder(document);
        headerBuilder.moveToHeaderFooter(HeaderFooterType.HEADER_PRIMARY);
        headerBuilder.write("页眉旧值");

        AsposeReplaceService service = new AsposeReplaceService();
        int replaced = service.replaceEverywhere(document, "旧值", "新值", new FindReplaceOptions());

        assertEquals(3, replaced);
        assertTrue(document.getText().contains("正文新值"));
        assertTrue(document.getText().contains("表格新值"));
        assertTrue(document.getFirstSection().getHeadersFooters()
                .getByHeaderFooterType(HeaderFooterType.HEADER_PRIMARY).getText().contains("页眉新值"));
        assertEquals(1, document.getChildNodes(NodeType.TABLE, true).getCount());
    }
}



