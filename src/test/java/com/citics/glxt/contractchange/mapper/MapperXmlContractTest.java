package com.citics.glxt.contractchange.mapper;

import org.junit.Test;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class MapperXmlContractTest {
    @Test
    public void shouldKeepAllSqlInXml() {
        for (Method method : ContractParagraphMapper.class.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                assertFalse(name.equals("Select") || name.equals("Insert")
                        || name.equals("Update") || name.equals("Delete"));
            }
        }
        InputStream xml = getClass().getClassLoader()
                .getResourceAsStream("mapper/contractchange/ContractParagraphMapper.xml");
        assertNotNull(xml);
    }
}
