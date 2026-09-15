package com.citics.glxt.contractchange.contractcompare.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 合同双版本比较结果。 */
@Data
@AllArgsConstructor
public class ContractCompareResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalChanges;
    private List<ClauseChange> changes;
    private List<String> warnings;

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
