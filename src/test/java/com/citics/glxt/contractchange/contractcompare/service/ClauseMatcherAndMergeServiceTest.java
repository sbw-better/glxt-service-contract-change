package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeDetail;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.DetailType;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.MatchResult;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ClauseMatcherAndMergeServiceTest {
    private final ClauseComparisonEngine engine = new ClauseComparisonEngine(new ContractCompareProperties());

    @Test
    public void shouldIgnorePureRenumberingAndMovement() {
        Clause oldClause = clause("OLD-1", "第五条", "保密义务", 1,
                "第五条 保密义务\n双方应当保守商业秘密。");
        Clause newClause = clause("NEW-1", "第六条", "保密义务", 3,
                "第六条 保密义务\n双方应当保守商业秘密。");

        List<ClauseChange> changes = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause));

        assertEquals(0, changes.size());
    }

    @Test
    public void shouldReturnOneModifiedClauseWithChangedParagraphAndDetails() {
        Clause oldClause = clause("OLD-1", "2.1", "合同金额", 1,
                "2.1 合同金额\n本合同总金额为人民币100万元。");
        Clause newClause = clause("NEW-1", "2.1", "合同金额", 1,
                "2.1 合同金额\n本合同总金额为人民币120万元。");

        List<ClauseChange> changes = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause));

        assertEquals(1, changes.size());
        assertEquals(ChangeType.MODIFIED, changes.get(0).getChangeType());
        assertEquals(1, changes.get(0).getChangedParagraphs().size());
        assertChangedParagraph(changes.get(0), 0, ChangeType.MODIFIED,
                "本合同总金额为人民币100万元。", "本合同总金额为人民币120万元。");
        assertDetail(changes.get(0).getChangedParagraphs().get(0), 0,
                DetailType.REPLACED, "100", "120");
    }

    @Test
    public void shouldCollapseAnEntireAddedSubtreeIntoParentChange() {
        Clause parent = clause("NEW-1", "第八条", "数据保护", 1,
                "第八条 数据保护");
        Clause child = clause("NEW-2", "8.1", "保护措施", 2,
                "8.1 保护措施\n双方应依法保护个人信息。");
        child.setParent(parent);
        parent.getChildren().add(child);

        List<ClauseChange> changes = merge(Collections.<Clause>emptyList(),
                Arrays.asList(parent, child));

        assertEquals(1, changes.size());
        assertEquals(ChangeType.ADDED, changes.get(0).getChangeType());
        assertEquals(3, changes.get(0).getChangedParagraphs().size());
        assertChangedParagraph(changes.get(0), 0, ChangeType.ADDED, null, "第八条 数据保护");
        assertChangedParagraph(changes.get(0), 1, ChangeType.ADDED, null, "8.1 保护措施");
        assertChangedParagraph(changes.get(0), 2, ChangeType.ADDED, null, "双方应依法保护个人信息。");
    }

    @Test
    public void shouldReturnInsertedAndDeletedTextInsideChangedParagraph() {
        Clause oldClause = clause("OLD-1", "第二条", "付款条件", 1,
                "第二条 付款条件\n收到发票后自动付款。");
        Clause newClause = clause("NEW-1", "第二条", "付款条件", 1,
                "第二条 付款条件\n收到合法发票后付款。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        ChangedParagraph paragraph = change.getChangedParagraphs().get(0);
        assertEquals(2, paragraph.getChangeDetails().size());
        assertDetail(paragraph, 0, DetailType.INSERTED, null, "合法");
        assertDetail(paragraph, 1, DetailType.DELETED, "自动", null);
    }

    @Test
    public void shouldReturnMultipleReplacementDetailsInOrder() {
        Clause oldClause = clause("OLD-1", "2.1", "付款安排", 1,
                "2.1 付款安排\n金额100万元，日期2025年。");
        Clause newClause = clause("NEW-1", "2.1", "付款安排", 1,
                "2.1 付款安排\n金额120万元，日期2026年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        ChangedParagraph paragraph = change.getChangedParagraphs().get(0);
        assertEquals(2, paragraph.getChangeDetails().size());
        assertDetail(paragraph, 0, DetailType.REPLACED, "100", "120");
        assertDetail(paragraph, 1, DetailType.REPLACED, "2025", "2026");
    }

    @Test
    public void shouldExcludeRenumberingFromModifiedDetails() {
        Clause oldClause = clause("OLD-1", "第五条", "保密义务", 1,
                "第五条 保密义务\n保密期限为3年。");
        Clause newClause = clause("NEW-1", "第六条", "保密义务", 2,
                "第六条 保密义务\n保密期限为5年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangedParagraphs().get(0).getChangeDetails().size());
        assertDetail(change.getChangedParagraphs().get(0), 0, DetailType.REPLACED, "3", "5");
    }

    @Test
    public void shouldReturnWholeDeletedClauseAsOneDetail() {
        Clause oldClause = clause("OLD-1", "第九条", "自动续约", 1,
                "第九条 自动续约\n合同到期后自动续约一年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.<Clause>emptyList()).get(0);

        assertEquals(ChangeType.DELETED, change.getChangeType());
        assertEquals(2, change.getChangedParagraphs().size());
        assertChangedParagraph(change, 0, ChangeType.DELETED, "第九条 自动续约", null);
        assertChangedParagraph(change, 1, ChangeType.DELETED, "合同到期后自动续约一年。", null);
    }

    @Test
    public void shouldReturnOnlyChangedMiddleParagraph() {
        Clause oldClause = clause("OLD-1", "第一条", "风险说明", 1,
                "第一条 风险说明\n上方段落没有变化。\n风险等级为R3，适合C4投资者。\n下方段落没有变化。");
        Clause newClause = clause("NEW-1", "第一条", "风险说明", 1,
                "第一条 风险说明\n上方段落没有变化。\n风险等级为R4，适合C5投资者。\n下方段落没有变化。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangedParagraphs().size());
        assertChangedParagraph(change, 0, ChangeType.MODIFIED,
                "风险等级为R3，适合C4投资者。", "风险等级为R4，适合C5投资者。");
        assertEquals(2, change.getChangedParagraphs().get(0).getChangeDetails().size());
    }

    @Test
    public void shouldReturnCompleteDateInsteadOfOnlyChangedDigit() {
        Clause oldClause = clause("OLD-1", null, "基金合同", 1,
                "基金合同\n（2026-1）\n基金管理人：甲公司");
        Clause newClause = clause("NEW-1", null, "基金合同", 1,
                "基金合同\n（2026-2）\n基金管理人：甲公司");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertChangedParagraph(change, 0, ChangeType.MODIFIED, "（2026-1）", "（2026-2）");
        assertDetail(change.getChangedParagraphs().get(0), 0,
                DetailType.REPLACED, "2026-1", "2026-2");
    }

    @Test
    public void shouldKeepSemanticNumericValuesTogether() {
        Clause oldClause = clause("OLD-1", "第一条", "参数", 1,
                "第一条 参数\n日期2026年1月15日，比例5.25%，金额1,200.00元，期限30-60日，版本v1.2。");
        Clause newClause = clause("NEW-1", "第一条", "参数", 1,
                "第一条 参数\n日期2026年2月15日，比例6.50%，金额1,500.00元，期限60-90日，版本v1.3。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        ChangedParagraph paragraph = change.getChangedParagraphs().get(0);
        assertEquals(5, paragraph.getChangeDetails().size());
        assertDetail(paragraph, 0, DetailType.REPLACED, "2026年1月15日", "2026年2月15日");
        assertDetail(paragraph, 1, DetailType.REPLACED, "5.25%", "6.50%");
        assertDetail(paragraph, 2, DetailType.REPLACED, "1,200.00", "1,500.00");
        assertDetail(paragraph, 3, DetailType.REPLACED, "30-60日", "60-90日");
        assertDetail(paragraph, 4, DetailType.REPLACED, "v1.2", "v1.3");
    }

    @Test
    public void shouldReturnTwoChangedParagraphsInDocumentOrder() {
        Clause oldClause = clause("OLD-1", "第一条", "付款", 1,
                "第一条 付款\n金额100万元。\n中间内容不变。\n日期为2025年。");
        Clause newClause = clause("NEW-1", "第一条", "付款", 1,
                "第一条 付款\n金额120万元。\n中间内容不变。\n日期为2026年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(2, change.getChangedParagraphs().size());
        assertEquals("金额100万元。", change.getChangedParagraphs().get(0).getOldContent());
        assertEquals("日期为2025年。", change.getChangedParagraphs().get(1).getOldContent());
    }

    @Test
    public void shouldIdentifyInsertedParagraphBetweenUnchangedParagraphs() {
        Clause oldClause = clause("OLD-1", "第一条", "说明", 1,
                "第一条 说明\n前段不变。\n后段不变。");
        Clause newClause = clause("NEW-1", "第一条", "说明", 1,
                "第一条 说明\n前段不变。\n新增提示段落。\n后段不变。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangedParagraphs().size());
        assertChangedParagraph(change, 0, ChangeType.ADDED, null, "新增提示段落。");
    }

    @Test
    public void shouldIdentifyDeletedParagraphBetweenUnchangedParagraphs() {
        Clause oldClause = clause("OLD-1", "第一条", "说明", 1,
                "第一条 说明\n前段不变。\n待删除提示段落。\n后段不变。");
        Clause newClause = clause("NEW-1", "第一条", "说明", 1,
                "第一条 说明\n前段不变。\n后段不变。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangedParagraphs().size());
        assertChangedParagraph(change, 0, ChangeType.DELETED, "待删除提示段落。", null);
    }

    @Test
    public void shouldReturnPunctuationChangeWithParagraphContext() {
        Clause oldClause = clause("OLD-1", "第一条", "人员", 1,
                "第一条 人员\n基金经理为张三，任期三年。");
        Clause newClause = clause("NEW-1", "第一条", "人员", 1,
                "第一条 人员\n基金经理为张三；任期三年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);
        ChangedParagraph paragraph = change.getChangedParagraphs().get(0);

        assertEquals("基金经理为张三，任期三年。", paragraph.getOldContent());
        assertEquals("基金经理为张三；任期三年。", paragraph.getNewContent());
        assertDetail(paragraph, 0, DetailType.REPLACED, "，", "；");
    }

    @Test
    public void shouldKeepInsertedAndReplacedNamesTogether() {
        Clause oldClause = clause("OLD-1", "第一条", "人员", 1,
                "第一条 人员\n负责人为张三。\n基金经理为张三。\n联系人为王小明。");
        Clause newClause = clause("NEW-1", "第一条", "人员", 1,
                "第一条 人员\n负责人为张三、李四。\n基金经理为李四。\n联系人为王大明。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(3, change.getChangedParagraphs().size());
        assertDetail(change.getChangedParagraphs().get(0), 0,
                DetailType.INSERTED, null, "、李四");
        assertDetail(change.getChangedParagraphs().get(1), 0,
                DetailType.REPLACED, "张三", "李四");
        assertDetail(change.getChangedParagraphs().get(2), 0,
                DetailType.REPLACED, "小", "大");
    }

    @Test
    public void shouldNotMatchAnInsertedBiographyAgainstOtherParagraphs() {
        String biography = "基金经理：王某，英国伦敦大学学院数学与经济学学士，具有多年从业经验。";
        Clause oldClause = clause("OLD-1", "第一条", "管理人员", 1,
                "第一条 管理人员\n管理人为上海秋晟资产有限公司。\n托管人为中信证券。");
        Clause newClause = clause("NEW-1", "第一条", "管理人员", 1,
                "第一条 管理人员\n管理人为上海秋晟资产有限公司。\n" + biography + "\n托管人为中信证券。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangedParagraphs().size());
        assertChangedParagraph(change, 0, ChangeType.ADDED, null, biography);
        assertDetail(change.getChangedParagraphs().get(0), 0,
                DetailType.INSERTED, null, biography);
    }

    @Test
    public void shouldReplaceWholeParagraphWhenTextsCannotBeReliablyAligned() {
        String oldText = "管理人为上海秋晟资产有限公司。";
        String newText = "基金经理王某拥有数学与经济学学位，并具有多年证券投资管理经验。";
        Clause oldClause = clause("OLD-1", "第一条", "管理人员", 1,
                "第一条 管理人员\n" + oldText);
        Clause newClause = clause("NEW-1", "第一条", "管理人员", 1,
                "第一条 管理人员\n" + newText);

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangedParagraphs().size());
        assertDetail(change.getChangedParagraphs().get(0), 0,
                DetailType.REPLACED, oldText, newText);
    }

    private List<ClauseChange> merge(List<Clause> oldClauses, List<Clause> newClauses) {
        Parsed oldContract = new Parsed(oldClauses);
        Parsed newContract = new Parsed(newClauses);
        MatchResult result = engine.match(oldContract, newContract);
        return engine.merge(oldContract, newContract, result);
    }

    private Clause clause(String id, String number, String title, int order, String content) {
        Clause clause = new Clause();
        clause.setClauseNo(number);
        clause.setTitle(title);
        clause.setLevel(number != null && number.contains(".") ? 2 : 1);
        clause.setOrder(order);
        for (String paragraph : content.split("\\n", -1)) {
            clause.getContentParts().add(paragraph);
        }
        return clause;
    }

    private void assertChangedParagraph(ClauseChange change, int index, ChangeType type,
                                        String oldText, String newText) {
        ChangedParagraph paragraph = change.getChangedParagraphs().get(index);
        assertEquals(type, paragraph.getParagraphChangeType());
        assertEquals(oldText, paragraph.getOldContent());
        assertEquals(newText, paragraph.getNewContent());
        if (type != ChangeType.MODIFIED) {
            assertEquals(1, paragraph.getChangeDetails().size());
            assertDetail(paragraph, 0,
                    type == ChangeType.ADDED ? DetailType.INSERTED : DetailType.DELETED,
                    oldText, newText);
        }
    }

    private void assertDetail(ChangedParagraph paragraph, int index, DetailType type,
                              String oldText, String newText) {
        ChangeDetail detail = paragraph.getChangeDetails().get(index);
        assertEquals(type, detail.getDetailType());
        assertEquals(oldText, detail.getOldText());
        assertEquals(newText, detail.getNewText());
    }
}
