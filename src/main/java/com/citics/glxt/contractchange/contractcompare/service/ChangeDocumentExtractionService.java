package com.citics.glxt.contractchange.contractcompare.service;

import com.aspose.words.Body;
import com.aspose.words.Cell;
import com.aspose.words.Document;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import com.aspose.words.Table;
import com.citics.glxt.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.aspose.AsposeCompareService;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.ChangeDocumentType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.ResultMode;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangeType;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ChangedParagraph;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseChange;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareResponse.ClauseContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从补充协议、征询意见函或协商函中按确定性规则提取变更条款。 */
@Service
public class ChangeDocumentExtractionService {
    private static final Pattern COMMON_HEADING = Pattern.compile(
            "^\\s*[0-9]+\\s*[、.．]\\s*《基金合同》.+$", Pattern.DOTALL);
    private static final Pattern SPECIAL_HEADING = Pattern.compile(
            "^\\s*(?:[0-9]+\\s*[、.．]\\s*)?自本(?:协议|函件)的变更执行日起"
                    + "(?=.*《基金合同》)(?=.*(?:增加|新增|删除|修改|变更)).*$",
            Pattern.DOTALL);
    private static final Pattern CHANGE_MARKER = Pattern.compile(
            "(?:上述)?内容\\s*变更\\s*如下\\s*[：:]?");
    private static final Pattern TARGET_SUFFIX = Pattern.compile(
            "\\s*(?:中)?(?:增加|新增|删除|修改|变更)?如下约定\\s*[：:]?.*$"
                    + "|\\s*约定如下\\s*[：:]?.*$", Pattern.DOTALL);

    private final AsposeCompareService asposeCompareService;
    private final ClauseComparisonEngine comparisonEngine;

    public ChangeDocumentExtractionService(AsposeCompareService asposeCompareService,
                                           ClauseComparisonEngine comparisonEngine) {
        this.asposeCompareService = asposeCompareService;
        this.comparisonEngine = comparisonEngine;
    }

    public ContractCompareResponse extract(byte[] bytes, ChangeDocumentType documentType,
                                           ResultMode resultMode) {
        Document document = asposeCompareService.load(bytes);
        List<TextBlock> blocks = readBlocks(document);
        if (blocks.isEmpty()) {
            throw new ContractChangeBusinessException("变更函中未读取到可解析文本");
        }
        List<Integer> headingIndexes = headingIndexes(blocks, documentType);
        if (headingIndexes.isEmpty()) {
            throw new ContractChangeBusinessException("变更函中未识别到变更条款标题");
        }

        List<ClauseChange> changes = new ArrayList<ClauseChange>();
        List<String> warnings = new ArrayList<String>();
        for (int index = 0; index < headingIndexes.size(); index++) {
            int headingIndex = headingIndexes.get(index);
            int end = index + 1 < headingIndexes.size()
                    ? headingIndexes.get(index + 1) : blocks.size();
            List<TextBlock> segment = new ArrayList<TextBlock>(
                    blocks.subList(headingIndex + 1, end));
            changes.add(extractChange(changes.size() + 1, blocks.get(headingIndex).text,
                    segment, resultMode, warnings));
        }
        return new ContractCompareResponse(changes.size(), changes, warnings, documentType);
    }

    private List<TextBlock> readBlocks(Document document) {
        try {
            document.updateListLabels();
        } catch (Exception ex) {
            throw new ContractChangeBusinessException("变更函列表编号解析失败");
        }
        List<TextBlock> blocks = new ArrayList<TextBlock>();
        for (Object sectionObject : document.getSections()) {
            Section section = (Section) sectionObject;
            Body body = section.getBody();
            NodeCollection children = body.getChildNodes(NodeType.ANY, false);
            for (Object childObject : children) {
                Node child = (Node) childObject;
                String text = null;
                if (child.getNodeType() == NodeType.PARAGRAPH) {
                    text = paragraphText((Paragraph) child);
                } else if (child.getNodeType() == NodeType.TABLE) {
                    text = tableText((Table) child);
                }
                text = ContractCompareText.cleanDisplayText(text);
                if (!text.isEmpty()) {
                    blocks.add(new TextBlock(text));
                }
            }
        }
        return blocks;
    }

    private String paragraphText(Paragraph paragraph) {
        String text = ContractCompareText.cleanDisplayText(paragraph.getText());
        if (!paragraph.isListItem()) {
            return text;
        }
        String label = ContractCompareText.cleanDisplayText(
                paragraph.getListLabel().getLabelString());
        if (label.isEmpty() || text.startsWith(label)) {
            return text;
        }
        return label + text;
    }

    private String tableText(Table table) {
        StringBuilder text = new StringBuilder();
        for (Object rowObject : table.getRows()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            int column = 0;
            for (Object cellObject : ((com.aspose.words.Row) rowObject).getCells()) {
                if (column++ > 0) {
                    text.append(" | ");
                }
                Cell cell = (Cell) cellObject;
                text.append(ContractCompareText.cleanDisplayText(cell.getText())
                        .replace('\n', ' '));
            }
        }
        return text.toString();
    }

    private List<Integer> headingIndexes(List<TextBlock> blocks,
                                         ChangeDocumentType documentType) {
        List<Integer> indexes = new ArrayList<Integer>();
        boolean allowSpecial = documentType == ChangeDocumentType.SUPPLEMENTAL_AGREEMENT
                || documentType == ChangeDocumentType.INQUIRY_LETTER;
        for (int index = 0; index < blocks.size(); index++) {
            String text = blocks.get(index).text;
            if (COMMON_HEADING.matcher(text).matches()
                    || allowSpecial && SPECIAL_HEADING.matcher(text).matches()) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private ClauseChange extractChange(int sequence, String heading, List<TextBlock> segment,
                                       ResultMode resultMode, List<String> warnings) {
        ChangeType type = changeType(heading, segment);
        ExtractedContent content;
        if (type == ChangeType.ADDED) {
            content = new ExtractedContent(null, join(segment));
        } else if (type == ChangeType.DELETED) {
            content = new ExtractedContent(join(segment), null);
        } else {
            content = modifiedContent(segment, sequence, warnings);
        }

        String oldContent = emptyToNull(content.oldContent);
        String newContent = emptyToNull(content.newContent);
        if (oldContent == null && newContent == null) {
            warnings.add("第" + sequence + "个变更标题后的内容为空，请人工复核");
        }

        ClauseChange change = new ClauseChange();
        String targetReference = targetReference(heading);
        change.setClauseTitle(targetReference);
        change.setChangeType(type);
        change.setSourceHeading(heading);
        change.setTargetClauseReference(targetReference);
        if (oldContent == null && newContent == null) {
            change.setChangedParagraphs(Collections.<ChangedParagraph>emptyList());
        } else {
            change.setChangedParagraphs(Collections.singletonList(
                    comparisonEngine.describeTextChange(type, oldContent, newContent)));
        }
        if (resultMode == ResultMode.CONTEXT) {
            change.setContext(new ClauseContext(oldContent, newContent));
        }
        return change;
    }

    private ChangeType changeType(String heading, List<TextBlock> segment) {
        if (heading.contains("删除")) {
            return ChangeType.DELETED;
        }
        if (heading.contains("增加") || heading.contains("新增")) {
            return ChangeType.ADDED;
        }
        for (TextBlock block : segment) {
            if (CHANGE_MARKER.matcher(block.text).find()) {
                return ChangeType.MODIFIED;
            }
        }
        return ChangeType.MODIFIED;
    }

    private ExtractedContent modifiedContent(List<TextBlock> segment, int sequence,
                                             List<String> warnings) {
        int markerIndex = -1;
        Matcher marker = null;
        for (int index = 0; index < segment.size(); index++) {
            Matcher candidate = CHANGE_MARKER.matcher(segment.get(index).text);
            if (candidate.find()) {
                markerIndex = index;
                marker = candidate;
                break;
            }
        }
        if (markerIndex < 0) {
            warnings.add("第" + sequence + "个变更标题未找到“内容变更如下”标记，请人工复核");
            return new ExtractedContent(join(segment), null);
        }

        String oldContent = join(segment.subList(0, markerIndex));
        String markerText = segment.get(markerIndex).text;
        String sameBlockNew = markerText.substring(marker.end()).trim();
        String newContent = sameBlockNew;
        if (newContent.isEmpty() && markerIndex + 1 < segment.size()) {
            newContent = segment.get(markerIndex + 1).text;
        }
        if (oldContent.isEmpty()) {
            warnings.add("第" + sequence + "个变更标题未提取到变更前内容，请人工复核");
        }
        if (newContent.isEmpty()) {
            warnings.add("第" + sequence + "个变更标题未提取到变更后内容，请人工复核");
        }
        return new ExtractedContent(oldContent, newContent);
    }

    private String targetReference(String heading) {
        int start = heading.indexOf("《基金合同》");
        if (start < 0) {
            return null;
        }
        String target = heading.substring(start).trim();
        target = TARGET_SUFFIX.matcher(target).replaceFirst("").trim();
        return target.replaceAll("[，,；;：:]$", "").trim();
    }

    private String join(List<TextBlock> blocks) {
        StringBuilder text = new StringBuilder();
        for (TextBlock block : blocks) {
            String value = ContractCompareText.cleanDisplayText(block.text);
            if (!value.isEmpty()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(value);
            }
        }
        return text.toString();
    }

    private String emptyToNull(String value) {
        String cleaned = ContractCompareText.cleanDisplayText(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static final class TextBlock {
        private final String text;

        private TextBlock(String text) {
            this.text = text;
        }
    }

    private static final class ExtractedContent {
        private final String oldContent;
        private final String newContent;

        private ExtractedContent(String oldContent, String newContent) {
            this.oldContent = oldContent;
            this.newContent = newContent;
        }
    }
}
