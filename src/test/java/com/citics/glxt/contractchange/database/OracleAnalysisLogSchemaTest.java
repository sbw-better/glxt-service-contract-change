package com.citics.glxt.contractchange.database;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class OracleAnalysisLogSchemaTest {
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "(?m)^\\s{4}([A-Z][A-Z0-9_]*)\\s+(?:NUMBER|VARCHAR2|CLOB|DATE|BLOB)\\b");

    @Test
    public void shouldDescribeEveryAnalysisLogColumn() throws Exception {
        String sql = new String(Files.readAllBytes(
                Paths.get("database", "oracle", "02_analysis_log.sql")),
                StandardCharsets.UTF_8);
        List<String> tables = Arrays.asList(
                "HT_ANALYSIS_LOG", "HT_ANALYSIS_PARA_LOG", "HT_ANALYSIS_MATCH_LOG");

        for (String table : tables) {
            Matcher tableMatcher = Pattern.compile("CREATE TABLE " + table
                    + " \\((.*?)\\n\\);", Pattern.DOTALL).matcher(sql);
            assertTrue("missing table definition: " + table, tableMatcher.find());

            Matcher columnMatcher = COLUMN_PATTERN.matcher(tableMatcher.group(1));
            int columnCount = 0;
            while (columnMatcher.find()) {
                columnCount++;
                String column = columnMatcher.group(1);
                assertTrue("missing column comment: " + table + "." + column,
                        sql.contains("COMMENT ON COLUMN " + table + "." + column + " IS '"));
            }
            assertTrue("no columns found in table definition: " + table, columnCount > 0);
        }
    }
}
