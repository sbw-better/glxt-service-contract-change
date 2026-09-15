package com.citics.glxt.contractchange.contractcompare.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 比较流程内部的数据结构，集中放置以免形成大量只有字段的文件。 */
public final class ContractCompareDocument {
    private ContractCompareDocument() {
    }

    @Getter
    @Setter
    static final class Clause {
        private String clauseNo;
        private String title;
        private int level;
        private Clause parent;
        private int order;
        private boolean synthetic;
        private final List<String> contentParts = new ArrayList<String>();
        private final List<Clause> children = new ArrayList<Clause>();
        private final Set<Integer> paragraphIndexes = new LinkedHashSet<Integer>();

        String getParentClauseNo() {
            return parent == null ? null : parent.getClauseNo();
        }
    }

    @Getter
    @AllArgsConstructor
    static final class Parsed {
        private final List<Clause> clauses;
    }

    @Getter
    @AllArgsConstructor
    public static final class RevisionSignal {
        private final int revisionType;
        private final String text;
        private final Integer paragraphIndex;
    }

    @Getter
    @AllArgsConstructor
    static final class MatchResult {
        private final Map<Clause, Clause> matches;
        private final Set<Clause> unmatchedOld;
        private final Set<Clause> unmatchedNew;
        private final List<String> warnings;
    }
}
