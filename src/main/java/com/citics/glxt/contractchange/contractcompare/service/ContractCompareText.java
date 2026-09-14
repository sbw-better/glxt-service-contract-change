package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.contractcompare.service.ContractCompareDocument.Clause;

import java.util.HashMap;
import java.util.Map;

/** 比较文本规范化及轻量相似度算法。 */
final class ContractCompareText {
    private ContractCompareText() {
    }

    static String cleanDisplayText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u0007', ' ')
                .replace('\r', '\n')
                .replaceAll("[\\t\\u00A0\\u3000]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    static String normalize(String value) {
        return cleanDisplayText(value).replaceAll("\\s+", "").toLowerCase();
    }

    static String ownContent(Clause clause) {
        StringBuilder value = new StringBuilder();
        for (String part : clause.getContentParts()) {
            String text = cleanDisplayText(part);
            if (!text.isEmpty()) {
                if (value.length() > 0) {
                    value.append('\n');
                }
                value.append(text);
            }
        }
        return value.toString();
    }

    static String subtreeContent(Clause clause) {
        StringBuilder value = new StringBuilder(ownContent(clause));
        for (Clause child : clause.getChildren()) {
            String childText = subtreeContent(child);
            if (!childText.isEmpty()) {
                if (value.length() > 0) {
                    value.append('\n');
                }
                value.append(childText);
            }
        }
        return value.toString();
    }

    static String comparableContent(Clause clause) {
        String content = ownContent(clause);
        if (clause.getClauseNo() != null && !clause.getClauseNo().isEmpty()) {
            content = content.replaceFirst("^\\s*" + java.util.regex.Pattern.quote(clause.getClauseNo())
                    + "[\\s\\u3000、:：.．]*", "");
        }
        return normalize(content);
    }

    static double dice(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.equals(b)) {
            return 1.0D;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0D;
        }
        if (a.length() == 1 || b.length() == 1) {
            return a.equals(b) ? 1.0D : 0.0D;
        }
        Map<String, Integer> leftPairs = bigrams(a);
        Map<String, Integer> rightPairs = bigrams(b);
        int intersection = 0;
        for (Map.Entry<String, Integer> entry : leftPairs.entrySet()) {
            Integer other = rightPairs.get(entry.getKey());
            if (other != null) {
                intersection += Math.min(entry.getValue(), other);
            }
        }
        return (2.0D * intersection) / ((a.length() - 1) + (b.length() - 1));
    }

    private static Map<String, Integer> bigrams(String value) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (int i = 0; i < value.length() - 1; i++) {
            String pair = value.substring(i, i + 2);
            Integer count = counts.get(pair);
            counts.put(pair, count == null ? 1 : count + 1);
        }
        return counts;
    }
}
