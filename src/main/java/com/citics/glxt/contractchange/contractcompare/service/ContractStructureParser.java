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
public class ContractStructureParser {
    private static final Pattern CHINESE_CLAUSE = Pattern.compile(
            "^\\s*(第[一二三四五六七八九十百千万〇零两0-9]+条)(?:[\\s\\u3000、:：.．]*(.*))?$");
    private static final Pattern DECIMAL_CLAUSE = Pattern.compile(
            "^\\s*((?:[0-9]+\\.)+[0-9]+|[0-9]+)(?:[、.．\\s\\u3000]+(.+))?$");
    private static final Pattern SIGNATURE_START = Pattern.compile(
            "^(?:（?以下无正文|签署页|签字|盖章|甲方[：:]|乙方[：:]|法定代表人[：:]).*$");

    public Parsed parse(com.aspose.words.Document document) throws Exception {
        document.updateListLabels();
        Map<Paragraph, Integer> paragraphIndexes = paragraphIndexes(document);
        List<Clause> clauses = new ArrayList<Clause>();
        Deque<Clause> hierarchy = new ArrayDeque<Clause>();
        Clause current = null;
        int[] sequence = new int[]{0};

        for (Object sectionObject : document.getSections()) {
            Section section = (Section) sectionObject;
            Body body = section.getBody();
            NodeCollection children = body.getChildNodes(NodeType.ANY, false);
            for (Object childObject : children) {
                Node node = (Node) childObject;
                if (node.getNodeType() == NodeType.PARAGRAPH) {
                    Paragraph paragraph = (Paragraph) node;
                    String text = ContractCompareText.cleanDisplayText(paragraph.getText());
                    if (text.isEmpty() || isToc(paragraph)) {
                        continue;
                    }
                    Heading heading = heading(paragraph, text);
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
                    current.getContentParts().add(tableText(table));
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

    private Heading heading(Paragraph paragraph, String text) {
        Matcher chinese = CHINESE_CLAUSE.matcher(text);
        if (chinese.matches()) {
            return new Heading(chinese.group(1), valueOrEmpty(chinese.group(2)), 1);
        }
        Matcher decimal = DECIMAL_CLAUSE.matcher(text);
        if (decimal.matches()
                && (decimal.group(1).contains(".") || decimal.group(2) != null)
                && !looksLikeDate(decimal.group(1), decimal.group(2))) {
            String number = decimal.group(1);
            return new Heading(number, titleFromRemainder(decimal.group(2)), number.split("\\.").length);
        }
        if (paragraph.isListItem()) {
            String number = ContractCompareText.cleanDisplayText(paragraph.getListLabel().getLabelString());
            int level = paragraph.getListFormat().getListLevelNumber() + 1;
            if (!number.isEmpty()) {
                return new Heading(number.replaceAll("[、.．\\s]+$", ""), titleFromRemainder(text), level);
            }
        }
        if (paragraph.getParagraphFormat().isHeading()) {
            int outline = paragraph.getParagraphFormat().getOutlineLevel();
            return new Heading(null, text, Math.max(1, Math.min(9, outline + 1)));
        }
        return null;
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

    private String tableText(Table table) {
        StringBuilder readable = new StringBuilder();
        for (Object rowObject : table.getRows()) {
            int columnIndex = 0;
            for (Object cellObject : ((com.aspose.words.Row) rowObject).getCells()) {
                Cell cell = (Cell) cellObject;
                if (++columnIndex > 1) {
                    readable.append(" | ");
                }
                readable.append(ContractCompareText.cleanDisplayText(cell.getText()).replace('\n', ' '));
            }
            readable.append('\n');
        }
        return ContractCompareText.cleanDisplayText(readable.toString());
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
