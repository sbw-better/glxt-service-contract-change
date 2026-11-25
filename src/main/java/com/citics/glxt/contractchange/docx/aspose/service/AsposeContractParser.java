package com.citics.glxt.contractchange.docx.aspose.service;

import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import com.aspose.words.Body;
import com.aspose.words.Node;
import com.citics.glxt.contractchange.docx.aspose.model.ContractParagraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.citics.glxt.contractchange.constants.Constants.*;

/** 标准化段落文本与 Word 编号，并解析所属模块和标题上下文。 */
@Service
public class AsposeContractParser {
    private static final Pattern[] TITLE_PATTERNS = new Pattern[]{
            Pattern.compile("^([零一二三四五六七八九十百千万]+[、.])"),
            Pattern.compile("^([(（][零一二三四五六七八九十百千万]+[)）])"),
            Pattern.compile("^(\\d+[、.])"),
            Pattern.compile("^([(（]\\d+[)）])"),
            Pattern.compile("^(\\d+[)）]|[\\u2460-\\u2487])")
    };

    public List<ContractParagraph> parse(Document document) throws Exception {
        document.updateListLabels();
        List<ContractParagraph> result = new ArrayList<ContractParagraph>();
        long module = CONTRACT_ANALYSIS_MODULE_OTHER;
        String[] numbers = new String[MAX_TITLE_LEVEL];
        String[] titles = new String[MAX_TITLE_LEVEL];
        for (int i = 0; i < MAX_TITLE_LEVEL; i++) {
            numbers[i] = "";
            titles[i] = "";
        }
        for (Object sectionObject : document.getSections()) {
            Body body = ((Section) sectionObject).getBody();
            NodeCollection nodes = body.getChildNodes(NodeType.ANY, false);
            for (int i = 0; i < nodes.getCount(); i++) {
                Node node = (Node) nodes.get(i);
                if (node.getNodeType() != NodeType.PARAGRAPH) continue;
                Paragraph paragraph = (Paragraph) node;
                String rawText = paragraph.getText();
                String[] lines = rawText.split("[\\u000b\\n\\r]", -1);
                boolean firstLine = true;
                for (String rawLine : lines) {
                    String text = clean(rawLine);
                    if (text.isEmpty()) continue;
                    if (firstLine && paragraph.isListItem()) {
                        String label = paragraph.getListLabel().getLabelString();
                        if (label != null && !label.trim().isEmpty() && !text.startsWith(label.trim())) {
                            text = label.trim() + text;
                        }
                    }
                    firstLine = false;
                    module = moduleFor(text, module);
                    updateTitles(text, numbers, titles);
                    int activeLevel = validTitleLevel(numbers);
                    String prefix = extractPrefix(text, activeLevel);
                    int ownTitleLevel = prefix.isEmpty() ? 0 : activeLevel;
                    result.add(new ContractParagraph(paragraph, text, module,
                            numbers.clone(), titles.clone(), ownTitleLevel, prefix));
                }
            }
        }
        return result;
    }

    public String clean(String text) {
        if (text == null) return "";
        return text.replace('\u0007', ' ').replace('\r', ' ').replace('\u000b', ' ')
                .replace('\u00a0', ' ').trim();
    }

    private long moduleFor(String text, long current) {
        if ("合同封面".equals(text)) return CONTRACT_ANALYSIS_MODULE_COVER;
        if ("特别约定".equals(text)) return CONTRACT_ANALYSIS_MODULE_SPECIAL;
        if ("风险揭示书".equals(text)) return CONTRACT_ANALYSIS_MODULE_RISK;
        if ("合格投资者承诺书".equals(text)) return CONTRACT_ANALYSIS_MODULE_UNDERTAKING;
        if ("目录".equals(text)) return CONTRACT_ANALYSIS_MODULE_CONTENTS;
        if ("一、合同当事人".equals(text)) return CONTRACT_ANALYSIS_MODULE_MAIN;
        if ("签署页".equals(text)) return CONTRACT_ANALYSIS_MODULE_SIGNATURE;
        if ("附件".equals(text)) return CONTRACT_ANALYSIS_MODULE_ATTACHMENT;
        return current;
    }

    private void updateTitles(String text, String[] numbers, String[] titles) {
        for (int level = 0; level < TITLE_PATTERNS.length; level++) {
            Matcher matcher = TITLE_PATTERNS[level].matcher(text);
            if (!matcher.find()) continue;
            numbers[level] = matcher.group(1);
            titles[level] = text.substring(matcher.end()).trim();
            for (int lower = level + 1; lower < MAX_TITLE_LEVEL; lower++) {
                numbers[lower] = "";
                titles[lower] = "";
            }
            return;
        }
    }

    private int validTitleLevel(String[] numbers) {
        for (int i = numbers.length - 1; i >= 0; i--) {
            if (numbers[i] != null && !numbers[i].isEmpty()) return i + 1;
        }
        return 0;
    }

    private String extractPrefix(String text, int level) {
        if (level < 1 || level > TITLE_PATTERNS.length) return "";
        Matcher matcher = TITLE_PATTERNS[level - 1].matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}

