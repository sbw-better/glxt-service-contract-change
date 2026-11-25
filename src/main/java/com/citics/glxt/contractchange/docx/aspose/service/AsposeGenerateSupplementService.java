package com.citics.glxt.contractchange.docx.aspose.service;

import com.aspose.words.Comment;
import com.aspose.words.CommentRangeEnd;
import com.aspose.words.CommentRangeStart;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.FindReplaceOptions;
import com.aspose.words.IReplacingCallback;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.ReplaceAction;
import com.aspose.words.Revision;
import com.aspose.words.RevisionCollection;
import com.aspose.words.RevisionType;
import com.aspose.words.Row;
import com.aspose.words.Table;
import com.aspose.words.Cell;
import com.aspose.words.Section;
import com.aspose.words.Body;
import com.aspose.words.ReplacingArgs;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citics.glxt.contractchange.docx.aspose.model.ContractParagraph;
import com.citics.glxt.contractchange.docx.domain.ReplaceContentConfiguration;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

import static com.citics.glxt.contractchange.docx.constants.Constants.AGREEMENT_PLACEHOLDERS;
import static com.citics.glxt.contractchange.docx.constants.Constants.MODULE_NAME_MAP;
import static com.citics.glxt.contractchange.constants.Constants.*;

/** 使用 Aspose 提取修订范围并生成补充协议内容。 */
@Slf4j
@Service
public class AsposeGenerateSupplementService {
    @Resource private AsposeDocumentIO documentIO;
    @Resource private AsposeContractParser parser;
    @Resource private AsposeReplaceService replaceService;
    @Resource private ReplaceContentConfigurationService configService;

    public String generate(DocxAnalysisDTO request) {
        validate(request);
        Document revisionDocument = documentIO.load(request.getRevisionFileGetPath());
        Document template = documentIO.load(request.getSupplementFileGetPath());
        List<ContractParagraph> paragraphs;
        try {
            paragraphs = parser.parse(revisionDocument);
        } catch (Exception e) {
            throw new IllegalStateException("修订文件结构解析失败", e);
        }

        Map<Paragraph, ContractParagraph> paragraphMetadata = new IdentityHashMap<Paragraph, ContractParagraph>();
        for (ContractParagraph info : paragraphs) {
            if (!paragraphMetadata.containsKey(info.getSource())) paragraphMetadata.put(info.getSource(), info);
        }
        List<Block> blocks = readBodyBlocks(revisionDocument, paragraphMetadata);
        List<ChangeRange> changes = collectChanges(revisionDocument, blocks, paragraphMetadata);
        validateRangeContexts(changes);
        List<ReplaceContentConfiguration> configs = loadReplacementConfigs(request.getReplaceConfigIds());
        int insertedCount;
        try { insertedCount = applyTemplate(template, changes, configs, request); }
        catch (Exception e) { throw new IllegalStateException("补充协议模板填充失败", e); }
        documentIO.save(template, request.getSupplementFileSavePath());
        log.info("Aspose 补充协议生成完成，批注变更范围：{}，配置替换项：{}，条款数：{}",
                changes.size(), configs.size(), insertedCount);
        return "";
    }

    private void validate(DocxAnalysisDTO request) {
        if (request == null) throw new IllegalArgumentException("请求参数不得为空");
        require(request.getRevisionFileGetPath(), "修订记录文件获取地址不得为空，请核对！");
        require(request.getSupplementFileGetPath(), "补充协议文件获取地址不得为空，请核对！");
        require(request.getSupplementFileSavePath(), "补充协议文件保存地址不得为空，请核对！");
        if (request.getGenerateByRevisionType() == null) throw new IllegalArgumentException("生成类型不得为空，请核对！");
        if (request.getIsAllText() == null || request.getIsModify() == null) {
            throw new IllegalArgumentException("纯文本替换和修订模式参数不得为空");
        }
    }

    private List<ReplaceContentConfiguration> loadReplacementConfigs(String ids) {
        if (!StringUtils.hasText(ids)) return Collections.emptyList();
        List<String> idList = new ArrayList<String>();
        for (String id : Arrays.asList(ids.split(","))) if (StringUtils.hasText(id)) idList.add(id.trim());
        if (idList.isEmpty()) return Collections.emptyList();
        return configService.list(new LambdaQueryWrapper<ReplaceContentConfiguration>()
                .in(ReplaceContentConfiguration::getId, idList));
    }

    private int applyTemplate(Document template, List<ChangeRange> changes,
                              List<ReplaceContentConfiguration> configs,
                              DocxAnalysisDTO request) throws Exception {
        if (request.getIsModify()) template.startTrackRevisions("合同变更系统", new java.util.Date());
        FindReplaceOptions textOptions = new FindReplaceOptions();
        Map<String, String> substitutions = new HashMap<String, String>();
        for (ReplaceContentConfiguration config : configs) {
            if (StringUtils.hasText(config.getOldContent())) {
                substitutions.put(config.getOldContent(), config.getNewContent() == null ? "" : config.getNewContent());
            }
        }
        for (Map.Entry<String, String> item : substitutions.entrySet()) {
            replaceService.replaceEverywhere(template, item.getKey(), item.getValue(), textOptions);
        }

        String html = buildHtml(changes, request.getGenerateByRevisionType());
        final String contentHtml = html;
        FindReplaceOptions placeholderOptions = new FindReplaceOptions();
        placeholderOptions.setReplacingCallback(new IReplacingCallback() {
            @Override
            public int replacing(ReplacingArgs args) throws Exception {
                if (!contentHtml.isEmpty()) {
                    DocumentBuilder builder = new DocumentBuilder((Document) args.getMatchNode().getDocument());
                    builder.moveTo(args.getMatchNode());
                    builder.insertHtml(contentHtml);
                }
                args.setReplacement("");
                return ReplaceAction.REPLACE;
            }
        });
        template.getRange().replace(Pattern.compile(Pattern.quote(AGREEMENT_PLACEHOLDERS)), "", placeholderOptions);
        if (request.getIsModify()) template.stopTrackRevisions();
        return changes.size();
    }

    private List<Block> readBodyBlocks(Document document, Map<Paragraph, ContractParagraph> metadata) {
        List<Block> blocks = new ArrayList<Block>();
        RevisionCollection revisions = document.getRevisions();
        for (Object sectionObject : document.getSections()) {
            Body body = ((Section) sectionObject).getBody();
            ContractParagraph currentContext = null;
            NodeCollection children = body.getChildNodes(NodeType.ANY, false);
            for (int i = 0; i < children.getCount(); i++) {
                Node node = (Node) children.get(i);
                if (node.getNodeType() == NodeType.PARAGRAPH) {
                    Paragraph paragraph = (Paragraph) node;
                    ContractParagraph info = metadata.get(paragraph);
                    if (info != null) currentContext = info;
                    blocks.add(Block.paragraph(paragraph, paragraphDelta(paragraph, revisions), currentContext));
                } else if (node.getNodeType() == NodeType.TABLE) {
                    Table table = (Table) node;
                    blocks.add(Block.table(table, tableDelta(table, revisions), currentContext));
                }
            }
        }
        return blocks;
    }

    private List<ChangeRange> collectChanges(Document document, List<Block> blocks,
                                              Map<Paragraph, ContractParagraph> metadata) {
        Map<String, Comment> emptyComments = new LinkedHashMap<String, Comment>();
        NodeCollection comments = document.getChildNodes(NodeType.COMMENT, true);
        for (int i = 0; i < comments.getCount(); i++) {
            Comment comment = (Comment) comments.get(i);
            if (!StringUtils.hasText(clean(comment.getText()))) {
                emptyComments.put(String.valueOf(comment.getId()), comment);
            }
        }
        if (emptyComments.isEmpty()) return Collections.emptyList();

        Map<String, Integer> starts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> ends = new LinkedHashMap<String, Integer>();
        NodeCollection startNodes = document.getChildNodes(NodeType.COMMENT_RANGE_START, true);
        for (int i = 0; i < startNodes.getCount(); i++) {
            CommentRangeStart start = (CommentRangeStart) startNodes.get(i);
            starts.put(String.valueOf(start.getId()), blockIndex(start, blocks));
        }
        NodeCollection endNodes = document.getChildNodes(NodeType.COMMENT_RANGE_END, true);
        for (int i = 0; i < endNodes.getCount(); i++) {
            CommentRangeEnd end = (CommentRangeEnd) endNodes.get(i);
            ends.put(String.valueOf(end.getId()), blockIndex(end, blocks));
        }

        List<ChangeRange> changes = new ArrayList<ChangeRange>();
        for (String id : emptyComments.keySet()) {
            Integer first = starts.get(id);
            Integer last = ends.get(id);
            if (first == null || last == null || first < 0 || last < first) {
                throw new IllegalArgumentException("空批注范围起止标记不完整，批注ID：" + id);
            }
            List<Block> inRange = new ArrayList<Block>(blocks.subList(first, last + 1));
            ChangeRange range = new ChangeRange(id, inRange);
            for (Block block : inRange) {
                range.hasInsertion |= block.delta.hasInsertion;
                range.hasDeletion |= block.delta.hasDeletion;
                range.hasPlainText |= block.delta.hasPlainText;
                if (block.context != null) range.paragraphs.add(block.context);
            }
            if (range.hasInsertion || range.hasDeletion) changes.add(range);
        }
        return changes;
    }

    private int blockIndex(Node marker, List<Block> blocks) {
        Node current = marker;
        while (current != null && current.getParentNode() != null
                && current.getParentNode().getNodeType() != NodeType.BODY) {
            current = current.getParentNode();
        }
        if (current == null) return -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).node == current) return i;
        }
        return -1;
    }

    private Delta paragraphDelta(Paragraph paragraph, RevisionCollection revisions) {
        StringBuilder oldText = new StringBuilder();
        StringBuilder newText = new StringBuilder();
        boolean insertion = false, deletion = false, plain = false;
        NodeCollection runs = paragraph.getChildNodes(NodeType.RUN, true);
        for (int i = 0; i < runs.getCount(); i++) {
            Node run = (Node) runs.get(i);
            String text = run.getText();
            if (!StringUtils.hasText(text)) continue;
            int type = revisionType(run, revisions);
            if (type == RevisionType.INSERTION) {
                insertion = true;
                newText.append(text);
            } else if (type == RevisionType.DELETION) {
                deletion = true;
                oldText.append(text);
            } else {
                plain = true;
                oldText.append(text);
                newText.append(text);
            }
        }
        String label = "";
        if (paragraph.isListItem()) {
            String listLabel = paragraph.getListLabel().getLabelString();
            if (listLabel != null) label = listLabel;
        }
        if (!label.isEmpty()) {
            oldText.insert(0, label);
            newText.insert(0, label);
        }
        return new Delta(clean(oldText.toString()), clean(newText.toString()), insertion, deletion, plain);
    }

    private Delta tableDelta(Table table, RevisionCollection revisions) {
        boolean insertion = false, deletion = false, plain = false;
        StringBuilder oldHtml = new StringBuilder("<table>");
        StringBuilder newHtml = new StringBuilder("<table>");
        for (Object rowObject : table.getRows()) {
            oldHtml.append("<tr>"); newHtml.append("<tr>");
            Row row = (Row) rowObject;
            for (Object cellObject : row.getCells()) {
                Cell cell = (Cell) cellObject;
                Delta cellDelta = paragraphCollectionDelta(cell.getChildNodes(NodeType.PARAGRAPH, true), revisions);
                insertion |= cellDelta.hasInsertion; deletion |= cellDelta.hasDeletion; plain |= cellDelta.hasPlainText;
                oldHtml.append("<td>").append(escape(cellDelta.oldText)).append("</td>");
                newHtml.append("<td>").append(escape(cellDelta.newText)).append("</td>");
            }
            oldHtml.append("</tr>"); newHtml.append("</tr>");
        }
        oldHtml.append("</table>"); newHtml.append("</table>");
        return new Delta(oldHtml.toString(), newHtml.toString(), insertion, deletion, plain);
    }

    private Delta paragraphCollectionDelta(NodeCollection paragraphs, RevisionCollection revisions) {
        StringBuilder oldText = new StringBuilder(), newText = new StringBuilder();
        boolean insertion = false, deletion = false, plain = false;
        for (int i = 0; i < paragraphs.getCount(); i++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(i);
            Delta delta = paragraphDelta(paragraph, revisions);
            if (oldText.length() > 0) oldText.append("<br>");
            if (newText.length() > 0) newText.append("<br>");
            oldText.append(escape(delta.oldText)); newText.append(escape(delta.newText));
            insertion |= delta.hasInsertion; deletion |= delta.hasDeletion; plain |= delta.hasPlainText;
        }
        return new Delta(oldText.toString(), newText.toString(), insertion, deletion, plain);
    }

    private int revisionType(Node node, RevisionCollection revisions) {
        for (int i = 0; i < revisions.getCount(); i++) {
            Revision revision = (Revision) revisions.get(i);
            Node changed = revision.getParentNode();
            for (Node current = node; current != null; current = current.getParentNode()) {
                if (current == changed) return revision.getRevisionType();
            }
        }
        return -1;
    }

    private void validateRangeContexts(List<ChangeRange> ranges) {
        for (ChangeRange range : ranges) {
            ContractParagraph first = null;
            String firstTitle = null;
            range.titleNumbers = new String[MAX_TITLE_LEVEL];
            range.titleTexts = new String[MAX_TITLE_LEVEL];
            for (ContractParagraph paragraph : range.paragraphs) {
                if (first == null) {
                    first = paragraph;
                    firstTitle = paragraph.getTitleTexts()[0];
                    range.titleNumbers = paragraph.getTitleNumbers().clone();
                    range.titleTexts = paragraph.getTitleTexts().clone();
                } else if (paragraph.getModule() != first.getModule()
                        || !safe(paragraph.getTitleTexts()[0]).equals(safe(firstTitle))) {
                    throw new IllegalArgumentException("同个空批注跨多个模块或一级标题，请核对。批注ID：" + range.commentId);
                } else {
                    for (int level = 1; level < MAX_TITLE_LEVEL; level++) {
                        if (!safe(paragraph.getTitleNumbers()[level]).equals(safe(range.titleNumbers[level]))
                                || !safe(paragraph.getTitleTexts()[level]).equals(safe(range.titleTexts[level]))) {
                            range.titleNumbers[level] = "";
                            range.titleTexts[level] = "";
                        }
                    }
                }
                if (paragraph.getTitleLevel() > 0) range.titleLevels.add(paragraph.getTitleLevel());
            }
            range.context = first;
        }
    }

    private String buildHtml(List<ChangeRange> ranges, Integer type) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < ranges.size(); i++) {
            ChangeRange range = ranges.get(i);
            boolean pureDelete = range.hasDeletion && !range.hasInsertion && !range.hasPlainText;
            boolean pureInsert = range.hasInsertion && !range.hasDeletion && !range.hasPlainText;
            if (i > 0) html.append("<p>&nbsp;</p>");
            html.append("<p><b>").append(i + 1).append("、《基金合同》")
                    .append(contextTitle(range));
            if (pureDelete) html.append("删除如下约定：</b></p>");
            else if (pureInsert) html.append("新增如下约定：</b></p>");
            else html.append("如下约定：</b></p>");
            appendRangeContent(html, range, !pureInsert);
            if (!pureDelete && !pureInsert) {
                String phrase = type != null && type == 2
                        ? "上述内容变更如下：" : "自本协议的变更执行日起，上述内容变更如下：";
                html.append("<p><b>").append(phrase).append("</b></p>");
                appendRangeContent(html, range, false);
            }
        }
        return html.toString();
    }

    private void appendRangeContent(StringBuilder html, ChangeRange range, boolean old) {
        boolean hasContent = false;
        for (Block block : range.blocks) {
            if (block.delta == null) continue;
            String value = old ? block.delta.oldText : block.delta.newText;
            if (StringUtils.hasText(value)) { hasContent = true; break; }
        }
        if (!hasContent) return;
        html.append("<p>“</p>");
        for (Block block : range.blocks) {
            if (block.delta == null) continue;
            if (block.paragraph != null) {
                String value = old ? block.delta.oldText : block.delta.newText;
                if (StringUtils.hasText(value)) html.append("<p>").append(escape(value)).append("</p>");
            } else if (block.table != null) {
                String value = old ? block.delta.oldText : block.delta.newText;
                if (StringUtils.hasText(value)) html.append(value);
            }
        }
        html.append("<p>”</p>");
    }

    private String contextTitle(ChangeRange range) {
        ContractParagraph context = range.context;
        if (context == null) return "其他";
        StringBuilder title = new StringBuilder();
        String module = MODULE_NAME_MAP.get(context.getModule());
        if (module != null) title.append(module);
        String[] numbers = range.titleNumbers;
        String[] texts = range.titleTexts;
        for (int i = 0; i < 4; i++) {
            if (range.titleLevels.contains(i + 1)) continue;
            if (!StringUtils.hasText(texts[i]) || texts[i].length() > 50) continue;
            if (i == 0) title.append("第").append(numbers[i].replace("、", "")).append("节“").append(texts[i]).append("”");
            else if (i == 1) title.append("第").append(numbers[i]).append("条“").append(texts[i]).append("”");
            else if (i == 2) title.append("第").append(numbers[i].replace("、", "")).append("款“").append(texts[i]).append("”");
            else title.append("第").append(numbers[i]).append("项“").append(texts[i]).append("”");
        }
        return title.toString();
    }

    private String clean(String value) { return value == null ? "" : value.replace('\u0007', ' ').replace('\r', ' ').trim(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private void require(String value, String message) { if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message); }

    private static final class Block {
        private final Node node;
        private final Paragraph paragraph;
        private final Table table;
        private final Delta delta;
        private final ContractParagraph context;
        private Block(Node node, Paragraph paragraph, Table table, Delta delta, ContractParagraph context) {
            this.node = node; this.paragraph = paragraph; this.table = table; this.delta = delta; this.context = context;
        }
        private static Block paragraph(Paragraph p, Delta d, ContractParagraph context) { return new Block(p, p, null, d, context); }
        private static Block table(Table t, Delta d, ContractParagraph context) { return new Block(t, null, t, d, context); }
    }
    private static final class Delta {
        private final String oldText, newText;
        private final boolean hasInsertion, hasDeletion, hasPlainText;
        private Delta(String oldText, String newText, boolean insertion, boolean deletion, boolean plain) {
            this.oldText = oldText; this.newText = newText;
            this.hasInsertion = insertion; this.hasDeletion = deletion; this.hasPlainText = plain;
        }
    }
    private static final class ChangeRange {
        private final String commentId;
        private final List<Block> blocks;
        private final List<ContractParagraph> paragraphs = new ArrayList<ContractParagraph>();
        private boolean hasInsertion, hasDeletion, hasPlainText;
        private ContractParagraph context;
        private String[] titleNumbers;
        private String[] titleTexts;
        private final Set<Integer> titleLevels = new HashSet<Integer>();
        private ChangeRange(String id, List<Block> blocks) { this.commentId = id; this.blocks = blocks; }
    }
}

