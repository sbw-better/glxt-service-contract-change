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
        private List<ContentBlock> oldBlocks;
        private List<ContentBlock> newBlocks;
        private Integer oldIndex;
        private Integer newIndex;
    }

    public enum ChangeType {
        ADDED, DELETED, MODIFIED
    }

    /** 段落或表格内容块。 */
    @Data
    public static class ContentBlock implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type;
        private String text;
        private List<TableRow> rows;
    }

    @Data
    public static class TableRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private int rowIndex;
        private List<TableCell> cells;
    }

    @Data
    public static class TableCell implements Serializable {
        private static final long serialVersionUID = 1L;
        private int columnIndex;
        private String text;
        private String horizontalMerge;
        private String verticalMerge;
    }
}
