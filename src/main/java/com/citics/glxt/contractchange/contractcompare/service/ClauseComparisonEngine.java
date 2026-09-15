package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.config.ContractCompareProperties;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeDetail;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.DetailType;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.MatchResult;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.RevisionSignal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 条款匹配、Revision定位和变更聚合。 */
@Service
public class ClauseComparisonEngine {
    private static final int MAX_DIFF_TOKENS = 2000;
    private static final Pattern SEMANTIC_VALUE = Pattern.compile(
            "(?:[0-9]{4}[-/][0-9]{1,2}(?:[-/][0-9]{1,2})?)"
                    + "|(?:[0-9]{4}年[0-9]{1,2}月(?:[0-9]{1,2}日)?)"
                    + "|(?:[0-9]+(?:\\.[0-9]+)?[-~～—–至][0-9]+(?:\\.[0-9]+)?(?:个月|月|年|日|天)?)"
                    + "|(?:[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?[%％]?)"
                    + "|(?:[0-9]+(?:\\.[0-9]+)?[%％])"
                    + "|(?:[vV]?[0-9]+(?:\\.[0-9]+){1,3})");
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
        for (ClauseChange change : changes) {
            if (change.getChangeType() == ChangeType.MODIFIED && hasMissingDetails(change)) {
                warnings.add("条款" + displayName(change) + "的具体文字差异无法可靠生成，请结合变化段落复核");
            } else if (change.getChangeType() == ChangeType.MODIFIED
                    && hasWholeParagraphReplacement(change)) {
                warnings.add("条款" + displayName(change) + "存在无法可靠细分的变化，已按整段替换返回");
            }
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
        change.setChangeType(ChangeType.MODIFIED);
        List<PartSpan> oldParts = contentParts(oldClause, false);
        List<PartSpan> newParts = contentParts(newClause, false);
        change.setChangedParagraphs(changedParagraphs(oldParts, newParts,
                oldClause.getClauseNo(), newClause.getClauseNo()));
        return change;
    }

    private ClauseChange added(Clause clause) {
        ClauseChange change = base(clause);
        change.setChangeType(ChangeType.ADDED);
        change.setChangedParagraphs(oneSidedParagraphs(null, contentParts(clause, true)));
        return change;
    }

    private ClauseChange deleted(Clause clause) {
        ClauseChange change = base(clause);
        change.setChangeType(ChangeType.DELETED);
        change.setChangedParagraphs(oneSidedParagraphs(contentParts(clause, true), null));
        return change;
    }

    private ClauseChange base(Clause clause) {
        ClauseChange change = new ClauseChange();
        change.setClauseNo(clause.getClauseNo());
        change.setClauseTitle(clause.getTitle());
        change.setParentClauseNo(clause.getParentClauseNo());
        return change;
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

    private String displayName(ClauseChange change) {
        if (change.getClauseNo() != null && !change.getClauseNo().isEmpty()) {
            return change.getClauseNo();
        }
        return change.getClauseTitle() == null || change.getClauseTitle().isEmpty()
                ? "未编号条款" : change.getClauseTitle();
    }

    private boolean hasMissingDetails(ClauseChange change) {
        if (change.getChangedParagraphs() == null || change.getChangedParagraphs().isEmpty()) {
            return true;
        }
        for (ChangedParagraph paragraph : change.getChangedParagraphs()) {
            if (paragraph.getChangeDetails() == null || paragraph.getChangeDetails().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWholeParagraphReplacement(ClauseChange change) {
        for (ChangedParagraph paragraph : change.getChangedParagraphs()) {
            if (paragraph.getParagraphChangeType() != ChangeType.MODIFIED
                    || paragraph.getChangeDetails() == null
                    || paragraph.getChangeDetails().size() != 1) {
                continue;
            }
            ChangeDetail detail = paragraph.getChangeDetails().get(0);
            if (detail.getDetailType() == DetailType.REPLACED
                    && equalsText(paragraph.getOldContent(), detail.getOldText())
                    && equalsText(paragraph.getNewContent(), detail.getNewText())) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private List<ChangeDetail> singleDetail(DetailType type, String oldText, String newText) {
        ChangeDetail detail = new ChangeDetail();
        detail.setDetailType(type);
        if (oldText != null) {
            detail.setOldText(oldText);
        }
        if (newText != null) {
            detail.setNewText(newText);
        }
        List<ChangeDetail> details = new ArrayList<ChangeDetail>();
        details.add(detail);
        return details;
    }

    private List<ChangeDetail> diffDetails(String oldContent, String newContent,
                                           String oldClauseNo, String newClauseNo) {
        List<Token> oldTokens = tokens(oldContent, contentStart(oldContent, oldClauseNo));
        List<Token> newTokens = tokens(newContent, contentStart(newContent, newClauseNo));
        int prefix = 0;
        while (prefix < oldTokens.size() && prefix < newTokens.size()
                && oldTokens.get(prefix).key.equals(newTokens.get(prefix).key)) {
            prefix++;
        }
        int oldEnd = oldTokens.size();
        int newEnd = newTokens.size();
        while (oldEnd > prefix && newEnd > prefix
                && oldTokens.get(oldEnd - 1).key.equals(newTokens.get(newEnd - 1).key)) {
            oldEnd--;
            newEnd--;
        }
        List<Token> oldMiddle = oldTokens.subList(prefix, oldEnd);
        List<Token> newMiddle = newTokens.subList(prefix, newEnd);
        if (oldMiddle.isEmpty() && newMiddle.isEmpty()) {
            return Collections.emptyList();
        }
        if (oldMiddle.size() + newMiddle.size() > MAX_DIFF_TOKENS) {
            return Collections.emptyList();
        }
        return detailsFromAtoms(myers(oldMiddle, newMiddle), oldContent, newContent);
    }

    private int contentStart(String content, String clauseNo) {
        if (content == null || clauseNo == null || clauseNo.isEmpty()) {
            return 0;
        }
        Matcher matcher = Pattern.compile("^\\s*" + Pattern.quote(clauseNo)
                + "[\\s\\u3000、:：.．]*").matcher(content);
        return matcher.find() ? matcher.end() : 0;
    }

    private List<Token> tokens(String content, int start) {
        List<Token> result = new ArrayList<Token>();
        int index = Math.max(0, start);
        while (index < content.length()) {
            int codePoint = content.codePointAt(index);
            if (ignored(codePoint)) {
                index += Character.charCount(codePoint);
                continue;
            }
            int end = index + Character.charCount(codePoint);
            Matcher semantic = SEMANTIC_VALUE.matcher(content);
            semantic.region(index, content.length());
            if (semantic.lookingAt()) {
                end = semantic.end();
            } else if (isAsciiLetter(codePoint)) {
                while (end < content.length() && isAsciiWordPart(content.codePointAt(end))) {
                    end += Character.charCount(content.codePointAt(end));
                }
            } else if (Character.isDigit(codePoint)) {
                while (end < content.length() && isNumberPart(content.codePointAt(end))) {
                    end += Character.charCount(content.codePointAt(end));
                }
            }
            String value = content.substring(index, end);
            result.add(new Token(value.toLowerCase(Locale.ROOT), index, end));
            index = end;
        }
        return result;
    }

    private boolean ignored(int codePoint) {
        return codePoint == 7 || Character.isWhitespace(codePoint)
                || Character.getType(codePoint) == Character.SPACE_SEPARATOR;
    }

    private boolean isAsciiLetter(int codePoint) {
        return codePoint >= 'A' && codePoint <= 'Z' || codePoint >= 'a' && codePoint <= 'z';
    }

    private boolean isAsciiWordPart(int codePoint) {
        return isAsciiLetter(codePoint) || Character.isDigit(codePoint)
                || codePoint == '_' || codePoint == '-';
    }

    private boolean isNumberPart(int codePoint) {
        return Character.isDigit(codePoint) || codePoint == '.' || codePoint == ','
                || codePoint == '%' || codePoint == '％';
    }

    private List<PartSpan> contentParts(Clause clause, boolean includeChildren) {
        List<PartSpan> spans = new ArrayList<PartSpan>();
        appendContent(clause, includeChildren, spans);
        return spans;
    }

    private void appendContent(Clause clause, boolean includeChildren,
                               List<PartSpan> spans) {
        for (String part : clause.getContentParts()) {
            String text = ContractCompareText.cleanDisplayText(part);
            if (text.isEmpty()) {
                continue;
            }
            spans.add(new PartSpan(text, spans.size() + 1));
        }
        if (includeChildren) {
            for (Clause child : clause.getChildren()) {
                appendContent(child, true, spans);
            }
        }
    }

    private List<ChangedParagraph> changedParagraphs(List<PartSpan> oldParts,
                                                     List<PartSpan> newParts,
                                                     String oldClauseNo, String newClauseNo) {
        int[][] cost = new int[oldParts.size() + 1][newParts.size() + 1];
        for (int oldIndex = 0; oldIndex <= oldParts.size(); oldIndex++) {
            cost[oldIndex][0] = oldIndex;
        }
        for (int newIndex = 0; newIndex <= newParts.size(); newIndex++) {
            cost[0][newIndex] = newIndex;
        }
        for (int oldIndex = 1; oldIndex <= oldParts.size(); oldIndex++) {
            for (int newIndex = 1; newIndex <= newParts.size(); newIndex++) {
                boolean equal = partKey(oldParts.get(oldIndex - 1), oldClauseNo)
                        .equals(partKey(newParts.get(newIndex - 1), newClauseNo));
                int replace = cost[oldIndex - 1][newIndex - 1] + (equal ? 0 : 1);
                int delete = cost[oldIndex - 1][newIndex] + 1;
                int insert = cost[oldIndex][newIndex - 1] + 1;
                cost[oldIndex][newIndex] = Math.min(replace, Math.min(delete, insert));
            }
        }

        int oldIndex = oldParts.size();
        int newIndex = newParts.size();
        List<PartEdit> reversed = new ArrayList<PartEdit>();
        while (oldIndex > 0 || newIndex > 0) {
            if (oldIndex > 0 && newIndex > 0) {
                PartSpan oldPart = oldParts.get(oldIndex - 1);
                PartSpan newPart = newParts.get(newIndex - 1);
                boolean equal = partKey(oldPart, oldClauseNo).equals(partKey(newPart, newClauseNo));
                int diagonal = cost[oldIndex - 1][newIndex - 1] + (equal ? 0 : 1);
                if (cost[oldIndex][newIndex] == diagonal) {
                    reversed.add(new PartEdit(equal ? PartEditType.EQUAL : PartEditType.MODIFIED,
                            oldPart, newPart));
                    oldIndex--;
                    newIndex--;
                    continue;
                }
            }
            if (newIndex > 0 && cost[oldIndex][newIndex] == cost[oldIndex][newIndex - 1] + 1) {
                reversed.add(new PartEdit(PartEditType.ADDED, null, newParts.get(--newIndex)));
            } else {
                reversed.add(new PartEdit(PartEditType.DELETED, oldParts.get(--oldIndex), null));
            }
        }
        Collections.reverse(reversed);
        List<ChangedParagraph> result = new ArrayList<ChangedParagraph>();
        for (PartEdit edit : reversed) {
            if (edit.type != PartEditType.EQUAL) {
                result.add(toChangedParagraph(edit, oldClauseNo, newClauseNo));
            }
        }
        return result;
    }

    private String partKey(PartSpan part, String clauseNo) {
        String text = part.text;
        if (part.index == 1 && clauseNo != null && !clauseNo.isEmpty()) {
            int start = contentStart(text, clauseNo);
            text = text.substring(start);
        }
        return ContractCompareText.normalize(text);
    }

    private List<ChangedParagraph> oneSidedParagraphs(List<PartSpan> oldParts,
                                                       List<PartSpan> newParts) {
        List<ChangedParagraph> result = new ArrayList<ChangedParagraph>();
        if (oldParts != null) {
            for (PartSpan part : oldParts) {
                result.add(toChangedParagraph(new PartEdit(PartEditType.DELETED, part, null), null, null));
            }
        } else if (newParts != null) {
            for (PartSpan part : newParts) {
                result.add(toChangedParagraph(new PartEdit(PartEditType.ADDED, null, part), null, null));
            }
        }
        return result;
    }

    private ChangedParagraph toChangedParagraph(PartEdit edit,
                                                String oldClauseNo, String newClauseNo) {
        ChangedParagraph changed = new ChangedParagraph();
        changed.setParagraphChangeType(edit.type == PartEditType.ADDED
                ? ChangeType.ADDED : edit.type == PartEditType.DELETED
                ? ChangeType.DELETED : ChangeType.MODIFIED);
        if (edit.oldPart != null) {
            changed.setOldContent(edit.oldPart.text);
        }
        if (edit.newPart != null) {
            changed.setNewContent(edit.newPart.text);
        }
        if (edit.type == PartEditType.ADDED) {
            changed.setChangeDetails(singleDetail(DetailType.INSERTED, null, edit.newPart.text));
        } else if (edit.type == PartEditType.DELETED) {
            changed.setChangeDetails(singleDetail(DetailType.DELETED, edit.oldPart.text, null));
        } else {
            String detailOldNo = edit.oldPart.index == 1 ? oldClauseNo : null;
            String detailNewNo = edit.newPart.index == 1 ? newClauseNo : null;
            if (ContractCompareText.dice(edit.oldPart.text, edit.newPart.text) < 0.35D) {
                changed.setChangeDetails(singleDetail(DetailType.REPLACED,
                        edit.oldPart.text, edit.newPart.text));
            } else {
                List<ChangeDetail> details = diffDetails(edit.oldPart.text, edit.newPart.text,
                        detailOldNo, detailNewNo);
                changed.setChangeDetails(details.isEmpty()
                        ? singleDetail(DetailType.REPLACED, edit.oldPart.text, edit.newPart.text)
                        : details);
            }
        }
        return changed;
    }

    private List<DiffAtom> myers(List<Token> oldTokens, List<Token> newTokens) {
        int oldSize = oldTokens.size();
        int newSize = newTokens.size();
        int max = oldSize + newSize;
        if (max == 0) {
            return Collections.emptyList();
        }
        int offset = max + 1;
        int[] furthest = new int[2 * max + 3];
        Arrays.fill(furthest, -1);
        furthest[offset + 1] = 0;
        List<int[]> trace = new ArrayList<int[]>();
        for (int distance = 0; distance <= max; distance++) {
            trace.add(furthest.clone());
            for (int diagonal = -distance; diagonal <= distance; diagonal += 2) {
                int x;
                if (diagonal == -distance || (diagonal != distance
                        && furthest[offset + diagonal - 1] < furthest[offset + diagonal + 1])) {
                    x = furthest[offset + diagonal + 1];
                } else {
                    x = furthest[offset + diagonal - 1] + 1;
                }
                int y = x - diagonal;
                while (x < oldSize && y < newSize
                        && oldTokens.get(x).key.equals(newTokens.get(y).key)) {
                    x++;
                    y++;
                }
                furthest[offset + diagonal] = x;
                if (x >= oldSize && y >= newSize) {
                    return backtrack(trace, oldTokens, newTokens, distance, offset);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<DiffAtom> backtrack(List<int[]> trace, List<Token> oldTokens,
                                     List<Token> newTokens, int distance, int offset) {
        int x = oldTokens.size();
        int y = newTokens.size();
        List<DiffAtom> reversed = new ArrayList<DiffAtom>();
        for (int current = distance; current >= 0; current--) {
            int[] furthest = trace.get(current);
            int diagonal = x - y;
            int previousDiagonal;
            if (diagonal == -current || (diagonal != current
                    && furthest[offset + diagonal - 1] < furthest[offset + diagonal + 1])) {
                previousDiagonal = diagonal + 1;
            } else {
                previousDiagonal = diagonal - 1;
            }
            int previousX = furthest[offset + previousDiagonal];
            int previousY = previousX - previousDiagonal;
            while (x > previousX && y > previousY) {
                reversed.add(DiffAtom.equal(oldTokens.get(--x), newTokens.get(--y)));
            }
            if (current == 0) {
                break;
            }
            if (x == previousX) {
                reversed.add(DiffAtom.inserted(newTokens.get(--y)));
            } else {
                reversed.add(DiffAtom.deleted(oldTokens.get(--x)));
            }
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private List<ChangeDetail> detailsFromAtoms(List<DiffAtom> atoms,
                                                String oldContent, String newContent) {
        List<ChangeDetail> details = new ArrayList<ChangeDetail>();
        int index = 0;
        while (index < atoms.size()) {
            if (atoms.get(index).type == EditType.EQUAL) {
                index++;
                continue;
            }
            Token firstOld = null;
            Token lastOld = null;
            Token firstNew = null;
            Token lastNew = null;
            while (index < atoms.size() && atoms.get(index).type != EditType.EQUAL) {
                DiffAtom atom = atoms.get(index++);
                if (atom.oldToken != null) {
                    firstOld = firstOld == null ? atom.oldToken : firstOld;
                    lastOld = atom.oldToken;
                }
                if (atom.newToken != null) {
                    firstNew = firstNew == null ? atom.newToken : firstNew;
                    lastNew = atom.newToken;
                }
            }
            ChangeDetail detail = new ChangeDetail();
            if (firstOld != null && firstNew != null) {
                detail.setDetailType(DetailType.REPLACED);
            } else if (firstNew != null) {
                detail.setDetailType(DetailType.INSERTED);
            } else {
                detail.setDetailType(DetailType.DELETED);
            }
            if (firstOld != null) {
                detail.setOldText(oldContent.substring(firstOld.start, lastOld.end));
            }
            if (firstNew != null) {
                detail.setNewText(newContent.substring(firstNew.start, lastNew.end));
            }
            details.add(detail);
        }
        return details;
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

    private enum EditType {
        EQUAL, INSERTED, DELETED
    }

    private enum PartEditType {
        EQUAL, ADDED, DELETED, MODIFIED
    }

    private static final class PartSpan {
        private final String text;
        private final int index;

        private PartSpan(String text, int index) {
            this.text = text;
            this.index = index;
        }
    }

    private static final class PartEdit {
        private final PartEditType type;
        private final PartSpan oldPart;
        private final PartSpan newPart;

        private PartEdit(PartEditType type, PartSpan oldPart, PartSpan newPart) {
            this.type = type;
            this.oldPart = oldPart;
            this.newPart = newPart;
        }
    }

    private static final class Token {
        private final String key;
        private final int start;
        private final int end;

        private Token(String key, int start, int end) {
            this.key = key;
            this.start = start;
            this.end = end;
        }
    }

    private static final class DiffAtom {
        private final EditType type;
        private final Token oldToken;
        private final Token newToken;

        private DiffAtom(EditType type, Token oldToken, Token newToken) {
            this.type = type;
            this.oldToken = oldToken;
            this.newToken = newToken;
        }

        private static DiffAtom equal(Token oldToken, Token newToken) {
            return new DiffAtom(EditType.EQUAL, oldToken, newToken);
        }

        private static DiffAtom inserted(Token newToken) {
            return new DiffAtom(EditType.INSERTED, null, newToken);
        }

        private static DiffAtom deleted(Token oldToken) {
            return new DiffAtom(EditType.DELETED, oldToken, null);
        }
    }
}
