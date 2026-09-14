package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ContentBlock;
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
        ContentBlock block = new ContentBlock();
        block.setType("PARAGRAPH");
        block.setText(content);
        clause.getBlocks().add(block);
        return clause;
    }
}
