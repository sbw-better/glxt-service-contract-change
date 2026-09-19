package com.citics.glxt.contractchange.contractcompare.aspose;

import com.aspose.words.CompareOptions;
import com.aspose.words.ComparisonTargetType;
import com.aspose.words.Document;
import com.aspose.words.FileFormatInfo;
import com.aspose.words.FileFormatUtil;
import com.aspose.words.LoadFormat;
import com.aspose.words.Node;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Revision;
import com.aspose.words.RevisionType;
import com.aspose.words.Section;
import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.common.constants.CommonConstants;
import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.RevisionSignal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 使用 Aspose.Words 加载最终稿并生成文档 Revision。 */
@Service
public class AsposeCompareService {
    public ComparisonResult compare(byte[] oldBytes, byte[] newBytes) {
        Document oldDocument = load(oldBytes);
        Document newDocument = load(newBytes);
        try {
            Document comparedOld = oldDocument.deepClone();
            Document comparedNew = newDocument.deepClone();

            CompareOptions options = new CompareOptions();
            options.setIgnoreFormatting(true);
            options.setIgnoreComments(true);
            options.setIgnoreHeadersAndFooters(true);
            options.setIgnoreFootnotes(true);
            options.setIgnoreFields(true);
            options.setIgnoreTables(false);
            options.setIgnoreTextboxes(false);
            options.setTarget(ComparisonTargetType.NEW);
            comparedOld.compare(comparedNew, "contract-compare", new Date(), options);
            return new ComparisonResult(oldDocument, newDocument, extractSignals(comparedOld));
        } catch (Exception ex) {
            throw new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE,
                    "合同比较引擎处理失败，请稍后重试");
        }
    }

    /** 加载一份可供比较或规则提取使用的最终版DOCX。 */
    public Document load(byte[] bytes) {
        try {
            return loadFinalDocument(bytes);
        } catch (ContractChangeBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ContractChangeBusinessException("合同文件解析失败，请确认文件未损坏且未加密");
        }
    }

    private Document loadFinalDocument(byte[] bytes) throws Exception {
        FileFormatInfo format = FileFormatUtil.detectFileFormat(new ByteArrayInputStream(bytes));
        if (format.isEncrypted()) {
            throw new ContractChangeBusinessException("暂不支持加密的合同文件");
        }
        if (format.getLoadFormat() != LoadFormat.DOCX && format.getLoadFormat() != LoadFormat.DOCM
                && format.getLoadFormat() != LoadFormat.DOTX && format.getLoadFormat() != LoadFormat.DOTM) {
            throw new ContractChangeBusinessException("合同文件不是有效的DOCX文件");
        }
        Document document = new Document(new ByteArrayInputStream(bytes));
        if (document.hasRevisions()) {
            document.acceptAllRevisions();
        }
        return document;
    }

    private List<RevisionSignal> extractSignals(Document compared) {
        Map<Paragraph, Integer> indexes = new IdentityHashMap<Paragraph, Integer>();
        int paragraphIndex = 0;
        for (Object sectionObject : compared.getSections()) {
            Section section = (Section) sectionObject;
            NodeCollection paragraphs = section.getBody().getChildNodes(NodeType.PARAGRAPH, true);
            for (Object paragraphObject : paragraphs) {
                indexes.put((Paragraph) paragraphObject, ++paragraphIndex);
            }
        }
        List<RevisionSignal> signals = new ArrayList<RevisionSignal>();
        for (Object revisionObject : compared.getRevisions()) {
            Revision revision = (Revision) revisionObject;
            if (revision.getRevisionType() == RevisionType.FORMAT_CHANGE
                    || revision.getRevisionType() == RevisionType.STYLE_DEFINITION_CHANGE) {
                continue;
            }
            Node parent = revision.getParentNode();
            if (parent == null) {
                continue;
            }
            Paragraph paragraph = parent.getNodeType() == NodeType.PARAGRAPH
                    ? (Paragraph) parent : (Paragraph) parent.getAncestor(NodeType.PARAGRAPH);
            String text = paragraph == null ? parent.getText() : paragraph.getText();
            signals.add(new RevisionSignal(revision.getRevisionType(), text,
                    paragraph == null ? null : indexes.get(paragraph)));
        }
        return signals;
    }

    @Getter
    @AllArgsConstructor
    public static final class ComparisonResult {
        private final Document oldDocument;
        private final Document newDocument;
        private final List<RevisionSignal> revisions;
    }
}
