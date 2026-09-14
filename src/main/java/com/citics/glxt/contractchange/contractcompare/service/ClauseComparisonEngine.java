package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ContentBlock;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.MatchResult;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.RevisionSignal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 条款匹配、Revision定位和变更聚合。 */
@Service
public class ClauseComparisonEngine {
    private final ContractCompareProperties properties;

    public ClauseComparisonEngine(ContractCompareProperties properties) {
        this.properties = properties;
    }

    public Analysis analyze(Parsed oldContract, Parsed newContract, List<RevisionSignal> revisions) {
        MatchResult matches = match(oldContract, newContract);
        LocationResult locations = locate(revisions, oldContract, newContract);
        List<ClauseChange> changes = merge(oldContract, newContract, matches);
        List<String> warnings = new ArrayList<String>(matches.getWarnings());
        if (locations.unlocatedCount > 0) {
            warnings.add("有" + locations.unlocatedCount + "处Word修订无法可靠归属到具体条款");
        }
        if (revisions.isEmpty() && !changes.isEmpty()) {
            warnings.add("结构化文本存在差异，但Aspose未生成Revision，请人工复核");
        }
        return new Analysis(changes, warnings);
    }

    MatchResult match(Parsed oldContract, Parsed newContract) {
        Set<Clause> oldRemaining = identitySet(oldContract.getClauses());
        Set<Clause> newRemaining = identitySet(newContract.getClauses());
        Map<Clause, Clause> matches = new IdentityHashMap<Clause, Clause>();
        List<String> warnings = new ArrayList<String>();
        matchExactContent(oldRemaining, newRemaining, matches);
        matchUniqueTitles(oldRemaining, newRemaining, matches);
        matchFuzzy(oldContract.getClauses(), newContract.getClauses(), oldRemaining,
                newRemaining, matches, warnings);
        return new MatchResult(matches, oldRemaining, newRemaining, warnings);
    }

    List<ClauseChange> merge(Parsed oldContract, Parsed newContract, MatchResult matchResult) {
        List<RankedChange> ranked = new ArrayList<RankedChange>();
        for (Map.Entry<Clause, Clause> entry : matchResult.getMatches().entrySet()) {
            if (!ContractCompareText.comparableContent(entry.getKey())
                    .equals(ContractCompareText.comparableContent(entry.getValue()))) {
                ranked.add(new RankedChange(modified(entry.getKey(), entry.getValue()),
                        entry.getValue().getOrder(), 0));
            }
        }
        for (Clause newClause : newContract.getClauses()) {
            if (matchResult.getUnmatchedNew().contains(newClause)
                    && !hasUnmatchedAncestor(newClause, matchResult.getUnmatchedNew())) {
                ranked.add(new RankedChange(added(newClause), newClause.getOrder(), 0));
            }
        }
        for (Clause oldClause : oldContract.getClauses()) {
            if (matchResult.getUnmatchedOld().contains(oldClause)
                    && !hasUnmatchedAncestor(oldClause, matchResult.getUnmatchedOld())) {
                ranked.add(new RankedChange(deleted(oldClause),
                        deletionAnchor(oldClause, oldContract.getClauses(), matchResult.getMatches()),
                        oldClause.getOrder()));
            }
        }
        Collections.sort(ranked, Comparator.comparingInt(RankedChange::getAnchor)
                .thenComparingInt(RankedChange::getDeletedOrder));
        List<ClauseChange> changes = new ArrayList<ClauseChange>();
        for (RankedChange item : ranked) {
            changes.add(item.change);
        }
        return changes;
    }

    private void matchExactContent(Set<Clause> oldRemaining, Set<Clause> newRemaining,
                                   Map<Clause, Clause> matches) {
        for (Clause oldClause : new ArrayList<Clause>(oldRemaining)) {
            String oldText = ContractCompareText.comparableContent(oldClause);
            if (oldText.isEmpty()) {
                continue;
            }
            List<Clause> candidates = new ArrayList<Clause>();
            for (Clause newClause : newRemaining) {
                if (oldText.equals(ContractCompareText.comparableContent(newClause))) {
                    candidates.add(newClause);
                }
            }
            if (!candidates.isEmpty()) {
                pair(oldClause, nearestByOrder(oldClause, candidates), oldRemaining, newRemaining, matches);
            }
        }
    }

    private void matchUniqueTitles(Set<Clause> oldRemaining, Set<Clause> newRemaining,
                                   Map<Clause, Clause> matches) {
        for (Clause oldClause : new ArrayList<Clause>(oldRemaining)) {
            String title = ContractCompareText.normalize(oldClause.getTitle());
            if (title.isEmpty()) {
                continue;
            }
            List<Clause> oldSame = clausesWithTitle(oldRemaining, title);
            List<Clause> newSame = clausesWithTitle(newRemaining, title);
            if (oldSame.size() == 1 && newSame.size() == 1) {
                pair(oldClause, newSame.get(0), oldRemaining, newRemaining, matches);
            }
        }
    }

    private void matchFuzzy(List<Clause> allOld, List<Clause> allNew,
                            Set<Clause> oldRemaining, Set<Clause> newRemaining,
                            Map<Clause, Clause> matches, List<String> warnings) {
        List<Candidate> accepted = new ArrayList<Candidate>();
        for (Clause oldClause : oldRemaining) {
            List<Candidate> candidates = new ArrayList<Candidate>();
            for (Clause newClause : newRemaining) {
                candidates.add(new Candidate(oldClause, newClause, score(oldClause, newClause, allOld, allNew)));
            }
            Collections.sort(candidates, Comparator.comparingDouble(Candidate::getScore).reversed());
            if (candidates.isEmpty() || candidates.get(0).score < properties.getMinMatchScore()) {
                continue;
            }
            double second = candidates.size() < 2 ? 0.0D : candidates.get(1).score;
            if (candidates.get(0).score - second < properties.getAmbiguityMargin()) {
                warnings.add("条款匹配存在歧义：修改前位置" + oldClause.getOrder());
                continue;
            }
            accepted.add(candidates.get(0));
        }
        Collections.sort(accepted, Comparator.comparingDouble(Candidate::getScore).reversed());
        for (Candidate candidate : accepted) {
            if (oldRemaining.contains(candidate.oldClause) && newRemaining.contains(candidate.newClause)) {
                pair(candidate.oldClause, candidate.newClause, oldRemaining, newRemaining, matches);
            }
        }
    }

    private double score(Clause oldClause, Clause newClause, List<Clause> allOld, List<Clause> allNew) {
        double weighted = 0.0D;
        double totalWeight = 0.0D;
        String oldContent = ContractCompareText.comparableContent(oldClause);
        String newContent = ContractCompareText.comparableContent(newClause);
        if (!oldContent.isEmpty() && !newContent.isEmpty()) {
            weighted += 0.60D * ContractCompareText.dice(oldContent, newContent);
            totalWeight += 0.60D;
        }
        String oldTitle = ContractCompareText.normalize(oldClause.getTitle());
        String newTitle = ContractCompareText.normalize(newClause.getTitle());
        if (!oldTitle.isEmpty() && !newTitle.isEmpty()) {
            weighted += 0.25D * ContractCompareText.dice(oldTitle, newTitle);
            totalWeight += 0.25D;
        }
        String oldContext = context(oldClause, allOld);
        String newContext = context(newClause, allNew);
        if (!oldContext.isEmpty() && !newContext.isEmpty()) {
            weighted += 0.15D * ContractCompareText.dice(oldContext, newContext);
            totalWeight += 0.15D;
        }
        return totalWeight == 0.0D ? 0.0D : weighted / totalWeight;
    }

    private LocationResult locate(List<RevisionSignal> revisions, Parsed oldContract, Parsed newContract) {
        Set<Clause> located = Collections.newSetFromMap(new IdentityHashMap<Clause, Boolean>());
        int unlocated = 0;
        for (RevisionSignal revision : revisions) {
            Clause clause = byParagraphIndex(revision.getParagraphIndex(), oldContract, newContract);
            if (clause == null) {
                clause = byText(revision.getText(), oldContract, newContract);
            }
            if (clause == null) {
                if (!ContractCompareText.normalize(revision.getText()).isEmpty()) {
                    unlocated++;
                }
            } else {
                located.add(clause);
            }
        }
        return new LocationResult(located, unlocated);
    }

    private Clause byParagraphIndex(Integer index, Parsed oldContract, Parsed newContract) {
        if (index == null) {
            return null;
        }
        Clause oldClause = mostSpecificAt(index, oldContract.getClauses());
        Clause newClause = mostSpecificAt(index, newContract.getClauses());
        return newClause != null ? newClause : oldClause;
    }

    private Clause mostSpecificAt(Integer index, List<Clause> clauses) {
        Clause result = null;
        for (Clause clause : clauses) {
            if (clause.getParagraphIndexes().contains(index)
                    && (result == null || clause.getLevel() > result.getLevel())) {
                result = clause;
            }
        }
        return result;
    }

    private Clause byText(String text, Parsed oldContract, Parsed newContract) {
        String normalized = ContractCompareText.normalize(text);
        if (normalized.isEmpty()) {
            return null;
        }
        Clause best = null;
        double bestScore = 0.0D;
        List<Clause> clauses = new ArrayList<Clause>(oldContract.getClauses());
        clauses.addAll(newContract.getClauses());
        for (Clause clause : clauses) {
            String content = ContractCompareText.comparableContent(clause);
            double current = !content.isEmpty() && (content.contains(normalized) || normalized.contains(content))
                    ? 1.0D : ContractCompareText.dice(content, normalized);
            if (current > bestScore) {
                bestScore = current;
                best = clause;
            }
        }
        return bestScore >= 0.35D ? best : null;
    }

    private ClauseChange modified(Clause oldClause, Clause newClause) {
        ClauseChange change = base(newClause);
        change.setOldClauseNo(oldClause.getClauseNo());
        change.setNewClauseNo(newClause.getClauseNo());
        change.setChangeType(ChangeType.MODIFIED);
        change.setOldContent(ContractCompareText.ownContent(oldClause));
        change.setNewContent(ContractCompareText.ownContent(newClause));
        change.setOldBlocks(new ArrayList<ContentBlock>(oldClause.getBlocks()));
        change.setNewBlocks(new ArrayList<ContentBlock>(newClause.getBlocks()));
        change.setOldIndex(oldClause.getOrder());
        change.setNewIndex(newClause.getOrder());
        return change;
    }

    private ClauseChange added(Clause clause) {
        ClauseChange change = base(clause);
        change.setNewClauseNo(clause.getClauseNo());
        change.setChangeType(ChangeType.ADDED);
        change.setNewContent(ContractCompareText.subtreeContent(clause));
        change.setNewBlocks(subtreeBlocks(clause));
        change.setNewIndex(clause.getOrder());
        return change;
    }

    private ClauseChange deleted(Clause clause) {
        ClauseChange change = base(clause);
        change.setOldClauseNo(clause.getClauseNo());
        change.setChangeType(ChangeType.DELETED);
        change.setOldContent(ContractCompareText.subtreeContent(clause));
        change.setOldBlocks(subtreeBlocks(clause));
        change.setOldIndex(clause.getOrder());
        return change;
    }

    private ClauseChange base(Clause clause) {
        ClauseChange change = new ClauseChange();
        change.setClauseId(clause.getClauseId());
        change.setParentClauseId(clause.getParentClauseId());
        change.setClauseNo(clause.getClauseNo());
        change.setClauseTitle(clause.getTitle());
        change.setParentClauseNo(clause.getParentClauseNo());
        change.setLevel(clause.getLevel());
        return change;
    }

    private List<ContentBlock> subtreeBlocks(Clause clause) {
        List<ContentBlock> blocks = new ArrayList<ContentBlock>(clause.getBlocks());
        for (Clause child : clause.getChildren()) {
            blocks.addAll(subtreeBlocks(child));
        }
        return blocks;
    }

    private boolean hasUnmatchedAncestor(Clause clause, Set<Clause> unmatched) {
        Clause parent = clause.getParent();
        while (parent != null) {
            if (unmatched.contains(parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private int deletionAnchor(Clause deleted, List<Clause> allOld, Map<Clause, Clause> matches) {
        int oldPosition = allOld.indexOf(deleted);
        for (int i = oldPosition - 1; i >= 0; i--) {
            Clause matchedNew = matches.get(allOld.get(i));
            if (matchedNew != null) {
                return matchedNew.getOrder();
            }
        }
        return 0;
    }

    private String context(Clause clause, List<Clause> all) {
        StringBuilder value = new StringBuilder();
        if (clause.getParent() != null) {
            value.append(clause.getParent().getTitle()).append('|');
        }
        int index = all.indexOf(clause);
        if (index > 0) {
            value.append(all.get(index - 1).getTitle()).append('|');
        }
        if (index >= 0 && index + 1 < all.size()) {
            value.append(all.get(index + 1).getTitle());
        }
        return ContractCompareText.normalize(value.toString());
    }

    private List<Clause> clausesWithTitle(Set<Clause> clauses, String title) {
        List<Clause> result = new ArrayList<Clause>();
        for (Clause clause : clauses) {
            if (title.equals(ContractCompareText.normalize(clause.getTitle()))) {
                result.add(clause);
            }
        }
        return result;
    }

    private Clause nearestByOrder(Clause oldClause, List<Clause> candidates) {
        Clause best = candidates.get(0);
        int bestDistance = Math.abs(oldClause.getOrder() - best.getOrder());
        for (int i = 1; i < candidates.size(); i++) {
            int distance = Math.abs(oldClause.getOrder() - candidates.get(i).getOrder());
            if (distance < bestDistance) {
                best = candidates.get(i);
                bestDistance = distance;
            }
        }
        return best;
    }

    private void pair(Clause oldClause, Clause newClause, Set<Clause> oldRemaining,
                      Set<Clause> newRemaining, Map<Clause, Clause> matches) {
        matches.put(oldClause, newClause);
        oldRemaining.remove(oldClause);
        newRemaining.remove(newClause);
    }

    private Set<Clause> identitySet(List<Clause> clauses) {
        Set<Clause> result = Collections.newSetFromMap(new IdentityHashMap<Clause, Boolean>());
        result.addAll(clauses);
        return result;
    }

    @Getter
    @AllArgsConstructor
    public static final class Analysis {
        private final List<ClauseChange> changes;
        private final List<String> warnings;
    }

    private static final class LocationResult {
        private final Set<Clause> clauses;
        private final int unlocatedCount;

        private LocationResult(Set<Clause> clauses, int unlocatedCount) {
            this.clauses = clauses;
            this.unlocatedCount = unlocatedCount;
        }
    }

    private static final class Candidate {
        private final Clause oldClause;
        private final Clause newClause;
        private final double score;

        private Candidate(Clause oldClause, Clause newClause, double score) {
            this.oldClause = oldClause;
            this.newClause = newClause;
            this.score = score;
        }

        private double getScore() {
            return score;
        }
    }

    private static final class RankedChange {
        private final ClauseChange change;
        private final int anchor;
        private final int deletedOrder;

        private RankedChange(ClauseChange change, int anchor, int deletedOrder) {
            this.change = change;
            this.anchor = anchor;
            this.deletedOrder = deletedOrder;
        }

        private int getAnchor() {
            return anchor;
        }

        private int getDeletedOrder() {
            return deletedOrder;
        }
    }
}
