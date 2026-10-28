package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClauseComparisonEngineTest {
    @Test
    public void shouldTreatRenamedSameNumberClauseAsModifiedBelowFuzzyThreshold() throws Exception {
        ContractStructureParser parser = new ContractStructureParser();
        Parsed oldContract = parser.parse(riskDocument(
                "8、基金投资风险", "基金投资面临的风险，包括但不限于："));
        Parsed newContract = parser.parse(riskDocument(
                "8、基金投资运作风险", "基金投资面临的投资运作风险，包括但不限于："));
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());

        ContractCompareDocument.MatchResult matches = engine.match(oldContract, newContract);
        List<ClauseChange> changes = engine.merge(oldContract, newContract, matches);

        assertEquals(1, changes.size());
        assertEquals(ChangeType.MODIFIED, changes.get(0).getChangeType());
        assertEquals("基金投资运作风险", changes.get(0).getClauseTitle());
        assertEquals(2, changes.get(0).getChangedParagraphs().size());
        assertEquals("8、基金投资风险",
                changes.get(0).getChangedParagraphs().get(0).getOldContent());
        assertEquals("8、基金投资运作风险",
                changes.get(0).getChangedParagraphs().get(0).getNewContent());
    }

    @Test
    public void shouldScopeRepeatedNumberMatchingToItsParentPath() throws Exception {
        ContractStructureParser parser = new ContractStructureParser();
        Document oldDocument = new Document();
        DocumentBuilder oldBuilder = new DocumentBuilder(oldDocument);
        oldBuilder.writeln("第二十一、基金风险");
        oldBuilder.writeln("8、关联交易风险");
        oldBuilder.writeln("关联交易风险内容。");
        oldBuilder.writeln("第二十二、基金投资风险");
        oldBuilder.writeln("8、投资风险");
        oldBuilder.writeln("投资风险内容。");

        Document newDocument = new Document();
        DocumentBuilder newBuilder = new DocumentBuilder(newDocument);
        newBuilder.writeln("第二十一、基金风险");
        newBuilder.writeln("8、关联交易风险");
        newBuilder.writeln("关联交易风险内容。");
        newBuilder.writeln("第二十二、基金投资风险");
        newBuilder.writeln("8、投资运作风险");
        newBuilder.writeln("投资运作风险内容。");

        Parsed oldContract = parser.parse(oldDocument);
        Parsed newContract = parser.parse(newDocument);
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());
        ContractCompareDocument.MatchResult matches = engine.match(oldContract, newContract);
        List<ClauseChange> changes = engine.merge(oldContract, newContract, matches);

        assertEquals(1, changes.size());
        assertEquals(ChangeType.MODIFIED, changes.get(0).getChangeType());
        assertEquals("投资运作风险", changes.get(0).getClauseTitle());
    }

    @Test
    public void shouldAlignChangedHeadingWithNewHeadingInsteadOfFollowingBodyParagraph()
            throws Exception {
        String oldHeading = "（三）本基金应披露的基金信息包括：";
        String newHeading = "（三）基金的信息披露";
        String newBody = "基金管理人应当按照信息披露相关规定履行信息披露义务。";
        Document oldDocument = new Document();
        DocumentBuilder oldBuilder = new DocumentBuilder(oldDocument);
        oldBuilder.writeln("第二十一节 信息披露");
        oldBuilder.writeln(oldHeading);
        oldBuilder.writeln("1、基金合同；");

        Document newDocument = new Document();
        DocumentBuilder newBuilder = new DocumentBuilder(newDocument);
        newBuilder.writeln("第二十一节 信息披露");
        newBuilder.writeln(newHeading);
        newBuilder.writeln(newBody);
        newBuilder.writeln("1、基金份额净值与基金份额累计净值");

        ContractStructureParser parser = new ContractStructureParser();
        Parsed oldContract = parser.parse(oldDocument);
        Parsed newContract = parser.parse(newDocument);
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());
        List<ClauseChange> changes = engine.merge(oldContract, newContract,
                engine.match(oldContract, newContract));
        ClauseChange disclosure = null;
        for (ClauseChange change : changes) {
            if ("（三）".equals(change.getClauseNo())) {
                disclosure = change;
                break;
            }
        }

        assertTrue("编号相同的披露条款应被配对", disclosure != null);
        boolean matchedHeadings = false;
        boolean bodyAdded = false;
        for (ChangedParagraph paragraph : disclosure.getChangedParagraphs()) {
            if (oldHeading.equals(paragraph.getOldContent())
                    && newHeading.equals(paragraph.getNewContent())) {
                matchedHeadings = true;
            }
            if (newBody.equals(paragraph.getNewContent())
                    && paragraph.getParagraphChangeType() == ChangeType.ADDED) {
                bodyAdded = true;
            }
            assertFalse("旧标题不能被配到新版正文",
                    oldHeading.equals(paragraph.getOldContent())
                            && newBody.equals(paragraph.getNewContent()));
        }
        assertTrue("标题变化应和对应的新标题配对", matchedHeadings);
        assertTrue("新版新增正文应作为新增段落保留", bodyAdded);
    }

    @Test
    public void shouldNotPairUnrelatedParagraphsAsModification() throws Exception {
        Document oldDocument = new Document();
        DocumentBuilder oldBuilder = new DocumentBuilder(oldDocument);
        oldBuilder.writeln("第二十一节 信息披露");
        oldBuilder.writeln("基金的资产清算情况");

        Document newDocument = new Document();
        DocumentBuilder newBuilder = new DocumentBuilder(newDocument);
        newBuilder.writeln("第二十一节 信息披露");
        newBuilder.writeln("基金管理人应当在每次清算结束后及时向委托人披露清算报告，超过一年无法完成清算的，应当每年至少披露一次清算有关事项。");

        ContractStructureParser parser = new ContractStructureParser();
        Parsed oldContract = parser.parse(oldDocument);
        Parsed newContract = parser.parse(newDocument);
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());
        List<ClauseChange> changes = engine.merge(oldContract, newContract,
                engine.match(oldContract, newContract));

        assertEquals(1, changes.size());
        List<ChangedParagraph> paragraphs = changes.get(0).getChangedParagraphs();
        assertEquals(2, paragraphs.size());
        assertTrue(paragraphs.stream().anyMatch(p -> p.getParagraphChangeType() == ChangeType.DELETED
                && "基金的资产清算情况".equals(p.getOldContent())));
        assertTrue(paragraphs.stream().anyMatch(p -> p.getParagraphChangeType() == ChangeType.ADDED
                && p.getNewContent() != null
                && p.getNewContent().startsWith("基金管理人应当在每次清算结束后")));
    }

    @Test
    public void shouldNotPairOldDisclosureListHeadingWithNewClearingDisclosureBody()
            throws Exception {
        String oldHeading = "5、基金的资产负债情况；";
        String newHeading = "5、清算期间的信息披露";
        String newBody = "基金管理人应当在每次清算结束后及时向委托人披露清算报告，超过一年无法完成清算的，应当每年至少披露一次清算有关事项。";
        Document oldDocument = new Document();
        DocumentBuilder oldBuilder = new DocumentBuilder(oldDocument);
        oldBuilder.writeln("第十一条 信息披露");
        oldBuilder.writeln("（三）本基金应披露的基金信息包括：");
        oldBuilder.writeln(oldHeading);

        Document newDocument = new Document();
        DocumentBuilder newBuilder = new DocumentBuilder(newDocument);
        newBuilder.writeln("第十一条 信息披露");
        newBuilder.writeln("（三）基金的信息披露");
        newBuilder.writeln(newHeading);
        newBuilder.writeln(newBody);

        ContractStructureParser parser = new ContractStructureParser();
        Parsed oldContract = parser.parse(oldDocument);
        Parsed newContract = parser.parse(newDocument);
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());
        List<ClauseChange> changes = engine.merge(oldContract, newContract,
                engine.match(oldContract, newContract));
        assertTrue("低相似度的重用编号旧条款应标为删除",
                changes.stream().anyMatch(change -> "5".equals(change.getClauseNo())
                        && change.getChangeType() == ChangeType.DELETED
                        && change.getChangedParagraphs().stream().anyMatch(paragraph ->
                        oldHeading.equals(paragraph.getOldContent()))));
        assertTrue("低相似度的重用编号新条款应标为新增，正文不可与旧标题配对",
                changes.stream().anyMatch(change -> "5".equals(change.getClauseNo())
                        && change.getChangeType() == ChangeType.ADDED
                        && change.getChangedParagraphs().stream().anyMatch(paragraph ->
                        newBody.equals(paragraph.getNewContent()))));
        assertFalse("旧披露标题不能与新版清算正文配成替换",
                changes.stream().flatMap(change -> change.getChangedParagraphs().stream())
                        .anyMatch(paragraph -> oldHeading.equals(paragraph.getOldContent())
                                && newBody.equals(paragraph.getNewContent())));
    }

    @Test
    public void shouldNotCrossMatchDuplicateExactTextAcrossDifferentParents() throws Exception {
        Document oldDocument = new Document();
        DocumentBuilder oldBuilder = new DocumentBuilder(oldDocument);
        oldBuilder.writeln("第一章 甲");
        oldBuilder.writeln("（一）重复条款");
        oldBuilder.writeln("共同正文。");
        oldBuilder.writeln("第二章 乙");
        oldBuilder.writeln("（一）重复条款");
        oldBuilder.writeln("共同正文。");

        Document newDocument = new Document();
        DocumentBuilder newBuilder = new DocumentBuilder(newDocument);
        newBuilder.writeln("第一章 甲");
        newBuilder.writeln("（一）重复条款");
        newBuilder.writeln("甲章节修改后的正文。");
        newBuilder.writeln("第二章 乙");
        newBuilder.writeln("（一）重复条款");
        newBuilder.writeln("共同正文。");

        ContractStructureParser parser = new ContractStructureParser();
        Parsed oldContract = parser.parse(oldDocument);
        Parsed newContract = parser.parse(newDocument);
        ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());
        ContractCompareDocument.MatchResult matches = engine.match(oldContract, newContract);

        Clause oldA = numberedClause(oldContract, "第一章", "（一）");
        Clause oldB = numberedClause(oldContract, "第二章", "（一）");
        Clause newA = numberedClause(newContract, "第一章", "（一）");
        Clause newB = numberedClause(newContract, "第二章", "（一）");
        assertEquals(newA, matches.getMatches().get(oldA));
        assertEquals(newB, matches.getMatches().get(oldB));
    }

    private Clause numberedClause(Parsed parsed, String parentNo, String clauseNo) {
        for (Clause clause : parsed.getClauses()) {
            if (!clauseNo.equals(clause.getClauseNo()) || clause.getParent() == null) {
                continue;
            }
            if (parentNo.equals(clause.getParent().getClauseNo())) {
                return clause;
            }
        }
        return null;
    }

    private Document riskDocument(String heading, String introduction) throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("二十二、风险揭示");
        builder.writeln("8、基金的关联交易风险");
        builder.writeln("关联交易风险内容。");
        builder.writeln(heading);
        builder.writeln(introduction);
        builder.writeln("（1）市场风险");
        builder.writeln("市场风险内容。");
        return document;
    }
}
