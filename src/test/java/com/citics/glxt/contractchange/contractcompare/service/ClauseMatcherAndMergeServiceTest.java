package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeDetail;
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
import static org.junit.Assert.assertNull;

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
    public void shouldReturnOneModifiedClauseWithCompleteOldAndNewContent() {
        Clause oldClause = clause("OLD-1", "2.1", "合同金额", 1,
                "2.1 合同金额\n本合同总金额为人民币100万元。");
        Clause newClause = clause("NEW-1", "2.1", "合同金额", 1,
                "2.1 合同金额\n本合同总金额为人民币120万元。");

        List<ClauseChange> changes = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause));

        assertEquals(1, changes.size());
        assertEquals(ChangeType.MODIFIED, changes.get(0).getChangeType());
        assertEquals(Integer.valueOf(1), changes.get(0).getOldIndex());
        assertEquals(Integer.valueOf(1), changes.get(0).getNewIndex());
        assertEquals("2.1 合同金额\n本合同总金额为人民币100万元。", changes.get(0).getOldContent());
        assertEquals("2.1 合同金额\n本合同总金额为人民币120万元。", changes.get(0).getNewContent());
        assertDetail(changes.get(0), 0, DetailType.REPLACED, "100", "120");
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
        assertEquals("第八条 数据保护\n8.1 保护措施\n双方应依法保护个人信息。",
                changes.get(0).getNewContent());
        assertDetail(changes.get(0), 0, DetailType.INSERTED, null,
                "第八条 数据保护\n8.1 保护措施\n双方应依法保护个人信息。");
    }

    @Test
    public void shouldReturnInsertedAndDeletedTextWithExactOffsets() {
        Clause oldClause = clause("OLD-1", "第二条", "付款条件", 1,
                "第二条 付款条件\n收到发票后自动付款。");
        Clause newClause = clause("NEW-1", "第二条", "付款条件", 1,
                "第二条 付款条件\n收到合法发票后付款。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(2, change.getChangeDetails().size());
        assertDetail(change, 0, DetailType.INSERTED, null, "合法");
        assertDetail(change, 1, DetailType.DELETED, "自动", null);
    }

    @Test
    public void shouldReturnMultipleReplacementDetailsInOrder() {
        Clause oldClause = clause("OLD-1", "2.1", "付款安排", 1,
                "2.1 付款安排\n金额100万元，日期2025年。");
        Clause newClause = clause("NEW-1", "2.1", "付款安排", 1,
                "2.1 付款安排\n金额120万元，日期2026年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(2, change.getChangeDetails().size());
        assertDetail(change, 0, DetailType.REPLACED, "100", "120");
        assertDetail(change, 1, DetailType.REPLACED, "2025", "2026");
    }

    @Test
    public void shouldExcludeRenumberingFromModifiedDetails() {
        Clause oldClause = clause("OLD-1", "第五条", "保密义务", 1,
                "第五条 保密义务\n保密期限为3年。");
        Clause newClause = clause("NEW-1", "第六条", "保密义务", 2,
                "第六条 保密义务\n保密期限为5年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.singletonList(newClause)).get(0);

        assertEquals(1, change.getChangeDetails().size());
        assertDetail(change, 0, DetailType.REPLACED, "3", "5");
    }

    @Test
    public void shouldReturnWholeDeletedClauseAsOneDetail() {
        Clause oldClause = clause("OLD-1", "第九条", "自动续约", 1,
                "第九条 自动续约\n合同到期后自动续约一年。");

        ClauseChange change = merge(Collections.singletonList(oldClause),
                Collections.<Clause>emptyList()).get(0);

        assertEquals(ChangeType.DELETED, change.getChangeType());
        assertDetail(change, 0, DetailType.DELETED,
                "第九条 自动续约\n合同到期后自动续约一年。", null);
    }

    private List<ClauseChange> merge(List<Clause> oldClauses, List<Clause> newClauses) {
        Parsed oldContract = new Parsed(oldClauses);
        Parsed newContract = new Parsed(newClauses);
        MatchResult result = engine.match(oldContract, newContract);
        return engine.merge(oldContract, newContract, result);
    }

    private Clause clause(String id, String number, String title, int order, String content) {
        Clause clause = new Clause();
        clause.setClauseId(id);
        clause.setClauseNo(number);
        clause.setTitle(title);
        clause.setLevel(number != null && number.contains(".") ? 2 : 1);
        clause.setOrder(order);
        clause.getContentParts().add(content);
        return clause;
    }

    private void assertDetail(ClauseChange change, int index, DetailType type,
                              String oldText, String newText) {
        ChangeDetail detail = change.getChangeDetails().get(index);
        assertEquals(type, detail.getDetailType());
        assertEquals(oldText, detail.getOldText());
        assertEquals(newText, detail.getNewText());
        if (oldText == null) {
            assertNull(detail.getOldStart());
            assertNull(detail.getOldEnd());
        } else {
            assertEquals(oldText, change.getOldContent().substring(detail.getOldStart(), detail.getOldEnd()));
        }
        if (newText == null) {
            assertNull(detail.getNewStart());
            assertNull(detail.getNewEnd());
        } else {
            assertEquals(newText, change.getNewContent().substring(detail.getNewStart(), detail.getNewEnd()));
        }
    }
}
