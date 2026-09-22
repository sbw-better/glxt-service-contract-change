package com.citics.glxt.contractchange.mapper;

import org.junit.Test;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class MapperXmlContractTest {
    @Test
    public void shouldKeepAllSqlInXml() {
        assertXmlMapper(ContractParagraphMapper.class, "ContractParagraphMapper.xml");
        assertXmlMapper(AnalysisLogMapper.class, "AnalysisLogMapper.xml");
        assertXmlMapper(AnalysisParagraphLogMapper.class, "AnalysisParagraphLogMapper.xml");
        assertXmlMapper(AnalysisMatchLogMapper.class, "AnalysisMatchLogMapper.xml");
    }

    @Test
    public void shouldMapSnapshotAndPredictionScopeColumns() throws Exception {
        String main = resourceText("AnalysisLogMapper.xml");
        String paragraph = resourceText("AnalysisParagraphLogMapper.xml");
        assertFalse(main.contains("<select "));
        assertFalse(paragraph.contains("<select "));
        org.junit.Assert.assertTrue(main.contains("RESULT_JSON"));
        org.junit.Assert.assertTrue(paragraph.contains("INPUT_SCOPE"));
        org.junit.Assert.assertTrue(paragraph.contains("SOURCE_HEADING"));
        org.junit.Assert.assertTrue(paragraph.contains("TARGET_CLAUSE_REFERENCE"));
    }

    private void assertXmlMapper(Class<?> mapperType, String resourceName) {
        for (Method method : mapperType.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                assertFalse(name.equals("Select") || name.equals("Insert")
                        || name.equals("Update") || name.equals("Delete"));
            }
        }
        InputStream xml = getClass().getClassLoader().getResourceAsStream(
                "mybatis/mapper/contractchange/" + resourceName);
        assertNotNull(xml);
    }

    private String resourceText(String resourceName) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(
                "mybatis/mapper/contractchange/" + resourceName);
        assertNotNull(input);
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
