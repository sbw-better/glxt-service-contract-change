package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Body;
import com.aspose.words.Cell;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import com.aspose.words.Table;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Parsed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将 Word 正文和表格解析为有父子关系的合同结构节点。 */
@Service
@Slf4j
public class ContractStructureParser {
    private static final Pattern CHINESE_SECTION = Pattern.compile(
            "^\\s*(第[一二三四五六七八九十百千万〇零两0-9]+(?:编|篇|章|部分|节))"
                    + "(?:[\\s\\u3000、:：.．]*(.*))?$");
    private static final Pattern CHINESE_CLAUSE = Pattern.compile(
            "^\\s*(第[一二三四五六七八九十百千万〇零两0-9]+条)(?:[\\s\\u3000、:：.．]*(.*))?$");
    private static final Pattern PARENTHESIZED_CLAUSE = Pattern.compile(
            "^\\s*([（(][一二三四五六七八九十百千万〇零两0-9]+[）)])"
                    + "(?:[\\s\\u3000、:：.．]*(.*))?$");
    private static final Pattern DECIMAL_CLAUSE = Pattern.compile(
            "^\\s*((?:[0-9]+\\.)+[0-9]+|[0-9]+)(?:[、.．\\s\\u3000]+(.+))?$");
    private static final Pattern CHINESE_NUMBERED_HEADING = Pattern.compile(
            "^\\s*([一二三四五六七八九十百千万〇零两]+[、.．])\\s*(.{1,80})$");
    private static final Pattern TOC_TITLE = Pattern.compile("^目\\s*录$");
    private static final Pattern TOC_PAGE_SUFFIX = Pattern.compile(".*(?:\\.{2,}|…{2,}|·{3,}|\\s)\\d{1,4}$");
    private static final Pattern SIGNATURE_START = Pattern.compile(
            "^(?:（?以下无正文|签署页|签字|盖章|甲方[：:]|乙方[：:]|法定代表人[：:]).*$");

    /** 将Aspose文档按标题编号恢复为父子条款树，供条款匹配和变化段落切分使用。 */
    public Parsed parse(com.aspose.words.Document document) throws Exception {
        document.updateListLabels();
        Map<Paragraph, Integer> paragraphIndexes = paragraphIndexes(document);
        List<Clause> clauses = new ArrayList<Clause>();
        Deque<Clause> hierarchy = new ArrayDeque<Clause>();
        Clause current = null;
        int[] sequence = new int[]{0};
        boolean inTocRegion = false;
        int skippedTocParagraphs = 0;
        int parsedTables = 0;
        int parsedContentParagraphs = 0;
        int parsedTableRows = 0;
        int skippedEmptyParagraphs = 0;

        for (Object sectionObject : document.getSections()) {
            Section section = (Section) sectionObject;
            Body body = section.getBody();
            NodeCollection children = body.getChildNodes(NodeType.ANY, false);
            for (Object childObject : children) {
                Node node = (Node) childObject;
                if (node.getNodeType() == NodeType.PARAGRAPH) {
                    Paragraph paragraph = (Paragraph) node;
                    String text = ContractCompareText.cleanDisplayText(paragraph.getText());
                    if (text.isEmpty()) {
                        skippedEmptyParagraphs++;
                        continue;
                    }
                    if (isTocTitle(text)) {
                        inTocRegion = true;
                        skippedTocParagraphs++;
                        log.debug("识别并跳过目录标题, paragraphIndex={}", paragraphIndexes.get(paragraph));
                        continue;
                    }
                    if (isToc(paragraph)) {
                        skippedTocParagraphs++;
                        continue;
                    }
                    if (inTocRegion) {
                        if (looksLikeTocEntry(text)) {
                            skippedTocParagraphs++;
                            continue;
                        }
                        if (isBodyHeading(paragraph, text)) {
                            inTocRegion = false;
                            log.debug("目录区域结束, nextBodyHeadingIndex={}",
                                    paragraphIndexes.get(paragraph));
                        } else {
                            // 目录样式丢失时，优先留在目录区域，避免把目录行并入前言条款。
                            skippedTocParagraphs++;
                            continue;
                        }
                    }
                    parsedContentParagraphs++;
                    if (isPrefaceSubdocumentStart(text) && current != null && current.isSynthetic()) {
                        hierarchy.clear();
                        current = addClause(clauses, hierarchy, sequence,
                                null, "合格投资者承诺书", 1, true);
                        log.debug("识别合同前置承诺书边界, paragraphIndex={}",
                                paragraphIndexes.get(paragraph));
                    }
                    if (current != null && current.isSynthetic() && isBodyHeading(paragraph, text)) {
                        // 封面/承诺书不应成为第一章的结构父级。
                        hierarchy.clear();
                    }
                    Heading heading = heading(paragraph, text, hierarchy);
                    if (heading != null) {
                        current = addClause(clauses, hierarchy, sequence,
                                heading.clauseNo, heading.title, heading.level, false);
                    } else if (SIGNATURE_START.matcher(text).matches()) {
                        if (current == null || !"签署信息".equals(current.getTitle())) {
                            hierarchy.clear();
                            current = addClause(clauses, hierarchy, sequence,
                                    null, "签署信息", 1, true);
                        }
                    } else if (current == null) {
                        current = addClause(clauses, hierarchy, sequence,
                                null, text.length() <= 30 ? text : "合同主体信息", 1, true);
                    }
                    current.getContentParts().add(text);
                    Integer paragraphIndex = paragraphIndexes.get(paragraph);
                    if (paragraphIndex != null) {
                        current.getParagraphIndexes().add(paragraphIndex);
                    }
                } else if (node.getNodeType() == NodeType.TABLE) {
                    if (current == null) {
                        current = addClause(clauses, hierarchy, sequence,
                                null, "合同表格信息", 1, true);
                    }
                    Table table = (Table) node;
                    List<String> rows = tableRows(table);
                    current.getContentParts().addAll(rows);
                    parsedTables++;
                    parsedTableRows += rows.size();
                    int cellCount = 0;
                    for (Object rowObject : table.getRows()) {
                        cellCount += ((com.aspose.words.Row) rowObject).getCells().getCount();
                    }
                    log.debug("合同表格解析完成, tableIndex={}, rowCount={}, cellCount={}",
                            parsedTables, rows.size(), cellCount);
                    NodeCollection paragraphs = table.getChildNodes(NodeType.PARAGRAPH, true);
                    for (Object paragraphObject : paragraphs) {
                        Node paragraphNode = (Node) paragraphObject;
                        Integer paragraphIndex = paragraphIndexes.get((Paragraph) paragraphNode);
                        if (paragraphIndex != null) {
                            current.getParagraphIndexes().add(paragraphIndex);
                        }
                    }
                }
            }
        }
        log.info("合同结构解析完成, clauseCount={}, contentParagraphCount={}, tocParagraphsSkipped={}, emptyParagraphsSkipped={}, tableCount={}, tableRowCount={}, bodyParagraphNodeCount={}",
                clauses.size(), parsedContentParagraphs, skippedTocParagraphs,
                skippedEmptyParagraphs, parsedTables, parsedTableRows,
                paragraphIndexes.size());
        return new Parsed(clauses);
    }

    private Map<Paragraph, Integer> paragraphIndexes(com.aspose.words.Document document) {
        Map<Paragraph, Integer> indexes = new IdentityHashMap<Paragraph, Integer>();
        int index = 0;
        for (Object sectionObject : document.getSections()) {
            Section section = (Section) sectionObject;
            NodeCollection paragraphs = section.getBody().getChildNodes(NodeType.PARAGRAPH, true);
            for (Object paragraphObject : paragraphs) {
                Node node = (Node) paragraphObject;
                indexes.put((Paragraph) node, ++index);
            }
        }
        return indexes;
    }

    private boolean isToc(Paragraph paragraph) {
        String style = paragraph.getParagraphFormat().getStyleName();
        return style != null && (style.toLowerCase().startsWith("toc") || style.contains("目录"));
    }

    private boolean isTocTitle(String text) {
        return TOC_TITLE.matcher(text).matches();
    }

    private boolean looksLikeTocEntry(String text) {
        String compact = text.replaceAll("\\s+", "");
        return TOC_PAGE_SUFFIX.matcher(text).matches()
                || compact.matches("^(?:第[一二三四五六七八九十百千万〇零两0-9]+条|"
                + "[一二三四五六七八九十百千万〇零两]+[、.．]|[0-9]+(?:\\.[0-9]+)*|"
                + "[（(][一二三四五六七八九十百千万〇零两0-9]+[）)]).+\\d{1,4}$");
    }

    private boolean isBodyHeading(Paragraph paragraph, String text) {
        return paragraph.getParagraphFormat().isHeading()
                || CHINESE_SECTION.matcher(text).matches()
                || CHINESE_CLAUSE.matcher(text).matches()
                || CHINESE_NUMBERED_HEADING.matcher(text).matches();
    }

    private boolean isPrefaceSubdocumentStart(String text) {
        return "合格投资者承诺书".equals(text.replaceAll("\\s+", ""));
    }

    private Heading heading(Paragraph paragraph, String text, Deque<Clause> hierarchy) {
        Matcher section = CHINESE_SECTION.matcher(text);
        if (section.matches()) {
            return new Heading(section.group(1), valueOrEmpty(section.group(2)), 1);
        }
        Matcher chineseNumbered = CHINESE_NUMBERED_HEADING.matcher(text);
        if (chineseNumbered.matches()) {
            return new Heading(chineseNumbered.group(1),
                    titleFromRemainder(chineseNumbered.group(2)),
                    resolveNumberedLevel(chineseNumbered.group(1), 1, hierarchy));
        }
        Matcher chinese = CHINESE_CLAUSE.matcher(text);
        if (chinese.matches()) {
            return new Heading(chinese.group(1), valueOrEmpty(chinese.group(2)),
                    resolveNumberedLevel(chinese.group(1), 1, hierarchy));
        }
        Matcher parenthesized = PARENTHESIZED_CLAUSE.matcher(text);
        if (parenthesized.matches()) {
            return new Heading(parenthesized.group(1),
                    titleFromRemainder(parenthesized.group(2)),
                    resolveNumberedLevel(parenthesized.group(1), 2, hierarchy));
        }
        Matcher decimal = DECIMAL_CLAUSE.matcher(text);
        if (decimal.matches()
                && (decimal.group(1).contains(".") || decimal.group(2) != null)
                && !looksLikeDate(decimal.group(1), decimal.group(2))) {
            String number = decimal.group(1);
            int defaultLevel = number.split("\\.").length;
            return new Heading(number, titleFromRemainder(decimal.group(2)),
                    resolveNumberedLevel(number, defaultLevel, hierarchy));
        }
        if (paragraph.isListItem()) {
            String number = ContractCompareText.cleanDisplayText(paragraph.getListLabel().getLabelString());
            String title = titleFromRemainder(text);
            if (!number.isEmpty() && !title.isEmpty()) {
                int listLevel = paragraph.getListFormat().getListLevelNumber() + 1;
                int level = resolveNumberedLevel(number, listLevel, hierarchy);
                return new Heading(number.replaceAll("[、.．\\s]+$", ""), title, level);
            }
        }
        if (paragraph.getParagraphFormat().isHeading()) {
            int outline = paragraph.getParagraphFormat().getOutlineLevel();
            return new Heading(null, text, Math.max(1, Math.min(9, outline + 1)));
        }
        return null;
    }

    /**
     * 将编号层级相对于当前章节恢复：同一编号体系连续出现时并列；新编号体系嵌套在当前节点下。
     * 旧逻辑把所有整数编号固定为第 1 层，导致“（四）程序”下的“1、2、3”反而脱离父条款。
     */
    private int resolveNumberedLevel(String number, int defaultLevel, Deque<Clause> hierarchy) {
        if (hierarchy.isEmpty()) {
            return Math.max(1, defaultLevel);
        }
        Clause current = hierarchy.peekLast();
        if (sameNumberingFamily(current.getClauseNo(), number)) {
            return current.getLevel();
        }
        Clause ancestor = current.getParent();
        while (ancestor != null) {
            if (sameNumberingFamily(ancestor.getClauseNo(), number)
                    && resumesSiblingNumbering(ancestor.getClauseNo(), number)) {
                // 一个编号序列的子项结束后，编号继续递增或重复时回到该序列原有层级。
                return ancestor.getLevel();
            }
            ancestor = ancestor.getParent();
        }
        return Math.max(defaultLevel, current.getLevel() + 1);
    }

    private boolean resumesSiblingNumbering(String previousNumber, String nextNumber) {
        Integer previous = numberValue(previousNumber);
        Integer next = numberValue(nextNumber);
        // 编号重置为 1 时优先视为子层级新列表，避免误提升到外层。
        return previous != null && next != null && next >= previous && next > 1;
    }

    private Integer numberValue(String value) {
        if (value == null) {
            return null;
        }
        String number = value.replaceAll("[第条、.．\\s（）()]", "");
        if (number.matches("[0-9]+")) {
            try {
                return Integer.valueOf(number);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (!number.matches("[一二三四五六七八九十百千万〇零两]+")) {
            return null;
        }
        int total = 0;
        int digit = 0;
        for (int index = 0; index < number.length(); index++) {
            char current = number.charAt(index);
            int valueOfDigit = chineseDigit(current);
            if (valueOfDigit >= 0) {
                digit = valueOfDigit;
            } else if (current == '十') {
                total += (digit == 0 ? 1 : digit) * 10;
                digit = 0;
            } else if (current == '百') {
                total += (digit == 0 ? 1 : digit) * 100;
                digit = 0;
            } else if (current == '千') {
                total += (digit == 0 ? 1 : digit) * 1000;
                digit = 0;
            } else if (current == '万') {
                total = (total + digit) * 10000;
                digit = 0;
            }
        }
        return total + digit;
    }

    private int chineseDigit(char value) {
        switch (value) {
            case '一': return 1;
            case '二':
            case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            case '〇':
            case '零': return 0;
            default: return -1;
        }
    }

    private boolean sameNumberingFamily(String currentNumber, String nextNumber) {
        if (currentNumber == null || nextNumber == null) {
            return false;
        }
        String current = currentNumber.replaceAll("[、.．\\s]+$", "");
        String next = nextNumber.replaceAll("[、.．\\s]+$", "");
        if (current.matches("[0-9]+(?:\\.[0-9]+)*")
                && next.matches("[0-9]+(?:\\.[0-9]+)*")) {
            return current.split("\\.").length == next.split("\\.").length;
        }
        if (current.matches("[一二三四五六七八九十百千万〇零两]+")
                && next.matches("[一二三四五六七八九十百千万〇零两]+")) {
            return true;
        }
        if (current.matches("第[一二三四五六七八九十百千万〇零两0-9]+条")
                && next.matches("第[一二三四五六七八九十百千万〇零两0-9]+条")) {
            return true;
        }
        return isParenthesizedNumber(current) && isParenthesizedNumber(next)
                && isChineseNumber(current) == isChineseNumber(next);
    }

    private boolean isParenthesizedNumber(String value) {
        return value.matches("[（(].+[）)]");
    }

    private boolean isChineseNumber(String value) {
        return value.matches("[（(][一二三四五六七八九十百千万〇零两]+[）)]");
    }

    private boolean looksLikeDate(String number, String remainder) {
        return number.matches("(?:19|20)[0-9]{2}(?:\\.[0-9]{1,2})+")
                || (remainder != null && remainder.matches("^[0-9]{1,2}(?:日|号)?$"));
    }

    private String titleFromRemainder(String remainder) {
        String value = ContractCompareText.cleanDisplayText(remainder);
        if (value.length() <= 40 && !value.matches(".*[。；;！？!?].*")) {
            return value;
        }
        return "";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : ContractCompareText.cleanDisplayText(value);
    }

    private Clause addClause(List<Clause> clauses, Deque<Clause> hierarchy,
                                     int[] sequence, String number, String title,
                                     int level, boolean synthetic) {
        while (!hierarchy.isEmpty() && hierarchy.peekLast().getLevel() >= level) {
            hierarchy.removeLast();
        }
        Clause clause = new Clause();
        int order = ++sequence[0];
        clause.setClauseNo(number);
        clause.setTitle(title);
        clause.setLevel(level);
        clause.setOrder(order);
        clause.setSynthetic(synthetic);
        if (!hierarchy.isEmpty()) {
            clause.setParent(hierarchy.peekLast());
            hierarchy.peekLast().getChildren().add(clause);
        }
        clauses.add(clause);
        hierarchy.addLast(clause);
        return clause;
    }

    /** 每一行保留为一个差异单元，单元格以分隔符连接，避免整张表被压成一个超长段落。 */
    private List<String> tableRows(Table table) {
        List<String> readableRows = new ArrayList<String>();
        for (Object rowObject : table.getRows()) {
            int columnIndex = 0;
            StringBuilder readable = new StringBuilder();
            for (Object cellObject : ((com.aspose.words.Row) rowObject).getCells()) {
                Cell cell = (Cell) cellObject;
                if (++columnIndex > 1) {
                    readable.append(" | ");
                }
                readable.append(ContractCompareText.cleanDisplayText(cell.getText()).replace('\n', ' '));
            }
            String row = ContractCompareText.cleanDisplayText(readable.toString());
            if (!row.isEmpty()) {
                readableRows.add(row);
            }
        }
        return readableRows;
    }

    private static final class Heading {
        private final String clauseNo;
        private final String title;
        private final int level;

        private Heading(String clauseNo, String title, int level) {
            this.clauseNo = clauseNo;
            this.title = title;
            this.level = level;
        }
    }
}
