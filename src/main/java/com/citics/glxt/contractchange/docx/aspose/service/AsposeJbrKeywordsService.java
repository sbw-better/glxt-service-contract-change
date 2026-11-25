package com.citics.glxt.contractchange.docx.aspose.service;

import com.aspose.words.Document;
import com.aspose.words.FindReplaceOptions;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.citics.glxt.contractchange.docx.aspose.model.ContractParagraph;
import com.citics.glxt.contractchange.docx.domain.ContractAnalysisDetail;
import com.citics.glxt.contractchange.docx.model.dto.DocxAnalysisDTO;
import com.citics.glxt.contractchange.docx.service.ContractAnalysisDetailService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.citics.glxt.contractchange.docx.constants.Constants.JBR_HANDLE_TYPE_ADD;
import static com.citics.glxt.contractchange.docx.constants.Constants.JBR_HANDLE_TYPE_DEL;
import static com.citics.glxt.contractchange.docx.constants.Constants.JBR_HANDLE_TYPE_NO;

/** 使用 Aspose 实现经办人不处理、删除和新增三种模式。 */
@Slf4j
@Service
public class AsposeJbrKeywordsService {
    private static final String SIGNATURE = "经办员（签字）： ";
    private static final String SIGNATURE_PREFIX = "经办员（签字）";

    @Resource private AsposeDocumentIO documentIO;
    @Resource private AsposeContractParser parser;
    @Resource private AsposeAnalyzeAndLandService analyzeService;
    @Resource private ContractAnalysisDetailService detailService;

    public String handle(DocxAnalysisDTO request) {
        if (request == null) throw new IllegalArgumentException("请求参数不得为空");
        String getPath = request.getJbrFileGetPath();
        String savePath = request.getJbrFileSavePath();
        Long type = request.getJbrType();
        String keyword = request.getJbrKeyWords();
        if (StringUtils.isBlank(getPath)) throw new IllegalArgumentException("文档获取地址不得为空，请核对！");
        if (StringUtils.isBlank(savePath)) throw new IllegalArgumentException("文档保存地址不得为空，请核对！");
        if (type == null) throw new IllegalArgumentException("经办人操作类型不得为空，请核对！");
        if (JBR_HANDLE_TYPE_ADD.equals(type) && StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("新增经办人时，定位关键字不得为空，请核对！");
        }
        if (JBR_HANDLE_TYPE_NO.equals(type)) return "";
        if (!JBR_HANDLE_TYPE_DEL.equals(type) && !JBR_HANDLE_TYPE_ADD.equals(type)) {
            throw new IllegalArgumentException("不支持的经办人操作类型：" + type);
        }

        Document document = documentIO.load(getPath);
        DocxAnalysisDTO landRequest = new DocxAnalysisDTO();
        landRequest.setFileGetPath(getPath);
        Long masterId = analyzeService.analyzeAndLand(landRequest);
        List<ContractAnalysisDetail> signerDetails = detailService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContractAnalysisDetail>()
                        .eq(ContractAnalysisDetail::getMasterId, masterId)
                        .like(ContractAnalysisDetail::getOriginalContent, SIGNATURE_PREFIX));
        if (signerDetails.size() > 1) {
            throw new IllegalArgumentException("文档处理失败，找到多处“经办员（签字）”，请核对！");
        }

        List<ContractParagraph> paragraphs;
        try { paragraphs = parser.parse(document); }
        catch (Exception e) { throw new IllegalStateException("经办人定位时解析合同结构失败", e); }
        if (JBR_HANDLE_TYPE_DEL.equals(type)) {
            deleteSignerAndDate(document, paragraphs, masterId, signerDetails);
        } else {
            addSignerAfterKeyword(document, paragraphs, signerDetails, keyword);
        }
        addMissingFinalPageBreak(document, paragraphs);
        documentIO.save(document, savePath);
        log.info("Aspose 经办人处理完成，操作类型：{}，经办员记录数：{}", type, signerDetails.size());
        return "";
    }

    private void deleteSignerAndDate(Document document, List<ContractParagraph> paragraphs,
                                     Long masterId, List<ContractAnalysisDetail> signerDetails) {
        if (signerDetails.isEmpty()) return;
        ContractAnalysisDetail signer = signerDetails.get(0);
        Long nextDetailId = signer.getId() + 1;
        List<ContractAnalysisDetail> next = detailService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContractAnalysisDetail>()
                        .eq(ContractAnalysisDetail::getMasterId, masterId)
                        .eq(ContractAnalysisDetail::getId, nextDetailId));
        boolean removeAssociatedDate = next.size() == 1
                && StringUtils.startsWith(next.get(0).getOriginalContent(), "日期");
        boolean signerFound = false;
        boolean waitingForDate = false;
        for (ContractParagraph info : paragraphs) {
            String text = info.getText();
            if (text.startsWith(SIGNATURE_PREFIX)) {
                clearParagraph(info.getSource());
                signerFound = true;
                waitingForDate = removeAssociatedDate;
            } else if (signerFound && waitingForDate && text.startsWith("日期")) {
                clearParagraph(info.getSource());
                waitingForDate = false;
            }
        }
        if (!signerFound) {
            log.warn("Aspose 经办员删除跳过：落地记录存在但正文中未定位到经办员段落，主表ID：{}", masterId);
        }
    }
    private void addSignerAfterKeyword(Document document, List<ContractParagraph> paragraphs,
                                       List<ContractAnalysisDetail> signerDetails, String keyword) {
        if (!signerDetails.isEmpty()) return;
        for (ContractParagraph info : paragraphs) {
            if (info.getText().startsWith(keyword)) {
                Paragraph anchor = info.getSource();
                Node reference = anchor;
                for (String text : new String[]{SIGNATURE, "日期： ", " "}) {
                    Paragraph paragraph = cloneEmptyParagraph(document, anchor, text);
                    anchor.getParentNode().insertAfter(paragraph, reference);
                    reference = paragraph;
                }
                return;
            }
        }
    }

    private Paragraph cloneEmptyParagraph(Document document, Paragraph source, String text) {
        Paragraph paragraph = (Paragraph) source.deepClone(false);
        NodeCollection children = paragraph.getChildNodes(NodeType.ANY, false);
        List<Node> remove = new ArrayList<Node>();
        for (int i = 0; i < children.getCount(); i++) remove.add((Node) children.get(i));
        for (Node child : remove) child.remove();
        paragraph.appendChild(new com.aspose.words.Run(document, text));
        return paragraph;
    }

    private void clearParagraph(Paragraph paragraph) {
        NodeCollection runs = paragraph.getChildNodes(NodeType.RUN, true);
        for (int i = runs.getCount() - 1; i >= 0; i--) {
            ((Node) runs.get(i)).remove();
        }
    }

    private void addMissingFinalPageBreak(Document document, List<ContractParagraph> paragraphs) {
        for (ContractParagraph info : paragraphs) {
            String text = info.getText();
            if (text.startsWith("（本页无正文")) {
                Paragraph paragraph = info.getSource();
                if (!paragraph.getParagraphFormat().getPageBreakBefore()
                        && !paragraph.getText().contains("\f")) {
                    paragraph.getParagraphFormat().setPageBreakBefore(true);
                }
            }
        }
    }
}




