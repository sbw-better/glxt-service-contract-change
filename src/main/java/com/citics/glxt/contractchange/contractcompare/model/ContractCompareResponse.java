package com.citics.glxt.contractchange.contractcompare.model;

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
        private String clauseId;
        private String parentClauseId;
        private String clauseNo;
        private String clauseTitle;
        private String parentClauseNo;
        private Integer level;
        private String oldClauseNo;
        private String newClauseNo;
        private ChangeType changeType;
        private String oldContent;
        private String newContent;
        private List<ChangeDetail> changeDetails;
        private Integer oldIndex;
        private Integer newIndex;
    }

    public enum ChangeType {
        ADDED, DELETED, MODIFIED
    }

    /** 条款内一处可定位的具体文字变化。 */
    @Data
    public static class ChangeDetail implements Serializable {
        private static final long serialVersionUID = 1L;
        private DetailType detailType;
        private String oldText;
        private String newText;
        private Integer oldStart;
        private Integer oldEnd;
        private Integer newStart;
        private Integer newEnd;
    }

    public enum DetailType {
        INSERTED, DELETED, REPLACED
    }
}
