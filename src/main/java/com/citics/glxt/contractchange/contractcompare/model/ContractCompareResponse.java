package com.citics.glxt.contractchange.contractcompare.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.citics.glxt.common.serializer.FourDecimalDoubleSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 合同双版本比较结果。 */
@Data
public class ContractCompareResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalChanges;
    private List<ClauseChange> changes;
    private List<String> warnings;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PredictionSummary predictionSummary;
    public ContractCompareResponse(int totalChanges, List<ClauseChange> changes,
                                   List<String> warnings) {
        this.totalChanges = totalChanges;
        this.changes = changes;
        this.warnings = warnings;
    }

    /** 本次合同分析的类型识别数量汇总。 */
    @Data
    @AllArgsConstructor
    public static class PredictionSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private int matchedCount;
        private int noReliableMatchCount;
        private int failedCount;
        private int skippedTooLongCount;
    }

    /** 一条完整条款变更。 */
    @Data
    public static class ClauseChange implements Serializable {
        private static final long serialVersionUID = 1L;
        private String clauseNo;
        private String clauseTitle;
        private String parentClauseNo;
        private ChangeType changeType;
        private List<ChangedParagraph> changedParagraphs;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String sourceHeading;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String targetClauseReference;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ClauseContext context;
    }

    /** CONTEXT模式下返回的完整条款内容。 */
    @Data
    @AllArgsConstructor
    public static class ClauseContext implements Serializable {
        private static final long serialVersionUID = 1L;
        private String oldContent;
        private String newContent;
    }

    public enum ChangeType {
        ADDED, DELETED, MODIFIED
    }

    /** 条款内一个发生变化的段落；表格以阅读顺序文本表示。 */
    @Data
    public static class ChangedParagraph implements Serializable {
        private static final long serialVersionUID = 1L;
        private ChangeType paragraphChangeType;
        private String oldContent;
        private String newContent;
        private List<ChangeDetail> changeDetails;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BusinessTypePrediction businessTypePrediction;
    }

    /** 一条变化段落与历史向量库比对后的业务类型结果。 */
    @Data
    public static class BusinessTypePrediction implements Serializable {
        private static final long serialVersionUID = 1L;
        private String status;
        private String inputScope;
        private boolean fallbackUsed;
        private String matchType;
        private String modelVersion;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonSerialize(using = FourDecimalDoubleSerializer.class)
        private Double maxSimilarity;
        private List<com.citics.glxt.contractchange.model.ChangeTypePrediction> changeTypes;
        private List<com.citics.glxt.contractchange.model.PredictionReference> references;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String message;
    }

    /** 变化段落内一处具体文字变化。 */
    @Data
    public static class ChangeDetail implements Serializable {
        private static final long serialVersionUID = 1L;
        private DetailType detailType;
        private String oldText;
        private String newText;
    }

    public enum DetailType {
        INSERTED, DELETED, REPLACED
    }
}
