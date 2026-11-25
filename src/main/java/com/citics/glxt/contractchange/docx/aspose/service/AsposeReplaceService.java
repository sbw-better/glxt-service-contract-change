package com.citics.glxt.contractchange.docx.aspose.service;

import com.aspose.words.Document;
import com.aspose.words.FindReplaceOptions;
import com.aspose.words.HeaderFooter;
import com.aspose.words.HeaderFooterCollection;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Range;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.citics.glxt.contractchange.docx.domain.ReplaceContentConfigRange;
import com.citics.glxt.contractchange.docx.domain.ReplaceContentConfiguration;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigRangeService;
import com.citics.glxt.contractchange.docx.service.ReplaceContentConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用 Aspose 根据配置替换文本并处理可选范围。 */
@Slf4j
@Service
public class AsposeReplaceService {
    private static final Pattern SCOPE_MARKER = Pattern.compile("\\$\\{(/?)([A-Z0-9]+)(?:-(\\d+))?\\}");

    @Resource private AsposeDocumentIO documentIO;
    @Resource private ReplaceContentConfigurationService configService;
    @Resource private ReplaceContentConfigRangeService rangeService;

    public String replace(DocxAnalysisDTO request) {
        if (request == null) throw new IllegalArgumentException("请求参数不得为空");
        require(request.getReplaceFileGetPath(), "文档获取地址不得为空，请核对！");
        require(request.getReplaceFileSavePath(), "文档保存地址不得为空，请核对！");
        require(request.getReplaceConfigIds(), "文档内容替换配置表ID串不得为空，请核对！");
        if (request.getIsAllText() == null) throw new IllegalArgumentException("是否为纯文本替换参数不得为空，请核对！");
        if (request.getIsModify() == null) throw new IllegalArgumentException("替换时是否开启修订模式不得为空，请核对！");

        // 与旧接口保持一致：isAllText 为 false 时不执行替换。
        if (!request.getIsAllText()) {
            log.info("Aspose 替换按兼容规则跳过：isAllText=false");
            return "";
        }

        List<ReplaceContentConfiguration> replacements = configService.list(
                new LambdaQueryWrapper<ReplaceContentConfiguration>().in(
                        ReplaceContentConfiguration::getId, parseIds(request.getReplaceConfigIds())));
        List<ReplaceContentConfigRange> ranges = new ArrayList<ReplaceContentConfigRange>();
        if (StringUtils.hasText(request.getReplaceConfigRangeIds())) {
            ranges = rangeService.list(new LambdaQueryWrapper<ReplaceContentConfigRange>().in(
                    ReplaceContentConfigRange::getId, parseIds(request.getReplaceConfigRangeIds())));
        }

        Map<String, String> rangeValues = new HashMap<String, String>();
        for (ReplaceContentConfigRange range : ranges) {
            rangeValues.put(range.getParamName(), range.getParamValue());
        }
        Document document = documentIO.load(request.getReplaceFileGetPath());
        removeExcludedRanges(document, rangeValues);

        if (request.getIsModify()) document.startTrackRevisions("合同变更系统", new Date());
        Map<String, String> replacementMap = new HashMap<String, String>();
        for (ReplaceContentConfiguration replacement : replacements) {
            if (StringUtils.hasText(replacement.getOldContent())) {
                replacementMap.put(replacement.getOldContent(),
                        replacement.getNewContent() == null ? "" : replacement.getNewContent());
            }
        }
        int count = 0;
        FindReplaceOptions options = new FindReplaceOptions();
        options.setMatchCase(true);
        for (Map.Entry<String, String> replacement : replacementMap.entrySet()) {
            count += replaceEverywhere(document, replacement.getKey(), replacement.getValue(), options);
        }
        if (request.getIsModify()) document.stopTrackRevisions();
        documentIO.save(document, request.getReplaceFileSavePath());
        log.info("Aspose 文本替换完成，配置数：{}，命中次数：{}", replacements.size(), count);
        return "";
    }

    public int replaceEverywhere(Document document, String oldText, String newText,
                                 FindReplaceOptions options) {
        int count = 0;
        try {
            for (Range range : bodyAndHeaders(document)) {
                count += range.replace(oldText, newText, options);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Aspose 文档文本替换失败", e);
        }
        return count;
    }

    private List<Range> bodyAndHeaders(Document document) {
        List<Range> ranges = new ArrayList<Range>();
        for (Object sectionObject : document.getSections()) {
            com.aspose.words.Section section = (com.aspose.words.Section) sectionObject;
            ranges.add(section.getBody().getRange());
            HeaderFooterCollection headers = section.getHeadersFooters();
            for (Object headerObject : headers) {
                ranges.add(((HeaderFooter) headerObject).getRange());
            }
        }
        return ranges;
    }

    /** 按 ${NAME-n} 范围标记处理正文顶层段落和表格。 */
    private void removeExcludedRanges(Document document, Map<String, String> configuredValues) {
        for (Object sectionObject : document.getSections()) {
            com.aspose.words.Body body = ((com.aspose.words.Section) sectionObject).getBody();
            NodeCollection children = body.getChildNodes(NodeType.ANY, false);
            List<com.aspose.words.Node> snapshot = new ArrayList<com.aspose.words.Node>();
            for (int i = 0; i < children.getCount(); i++) snapshot.add((com.aspose.words.Node) children.get(i));
            boolean inRange = false;
            boolean keepRange = false;
            for (com.aspose.words.Node child : snapshot) {
                String text = child.getNodeType() == NodeType.PARAGRAPH
                        ? ((com.aspose.words.Paragraph) child).getText() : "";
                Matcher matcher = SCOPE_MARKER.matcher(text);
                if (matcher.find()) {
                    String closing = matcher.group(1);
                    String name = matcher.group(2);
                    String index = matcher.group(3);
                    if (index == null) {
                        child.remove();
                        continue;
                    }
                    if ("/".equals(closing)) {
                        inRange = false;
                        keepRange = false;
                    } else {
                        inRange = true;
                        keepRange = configuredValues.containsKey(name)
                                && index.equals(configuredValues.get(name));
                    }
                    child.remove();
                    continue;
                }
                if (inRange && !keepRange) child.remove();
            }
        }
    }

    private List<String> parseIds(String ids) {
        List<String> result = new ArrayList<String>();
        for (String id : Arrays.asList(ids.split(","))) {
            if (StringUtils.hasText(id)) result.add(id.trim());
        }
        if (result.isEmpty()) throw new IllegalArgumentException("配置ID解析后为空");
        return result;
    }

    private void require(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    }
}






