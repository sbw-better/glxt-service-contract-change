package com.citics.glxt.contractchange.contractquery.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TpifCpwdkMapperXmlTest {
    @Test
    public void shouldParseMapperAndKeepRequiredFiltersAndMappings() throws Exception {
        String path = "mybatis/mapper/contractquery/TpifCpwdkMapper.xml";
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(input);
        String xml;
        try (InputStream stream = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
            xml = new String(output.toByteArray(), StandardCharsets.UTF_8);
        }

        Configuration configuration = new Configuration();
        new XMLMapperBuilder(new java.io.StringReader(xml), configuration, path,
                configuration.getSqlFragments()).parse();
        assertNotNull(configuration.getMappedStatement(
                TpifCpwdkMapper.class.getName() + ".selectContracts"));
        assertNotNull(configuration.getMappedStatement(
                TpifCpwdkMapper.class.getName() + ".selectOwnedContractsForDownload"));
        assertTrue(xml.contains("wd.wdfl IN (106, 108)"));
        assertTrue(xml.contains("wd.sfyx = 1"));
        assertTrue(xml.contains("cp.glr = #{managerId}"));
        assertTrue(xml.contains("TO_DATE(TO_CHAR(NVL(wd.sxrq, wd.scrq), 'FM00000000'), 'YYYYMMDD')"));
        assertTrue(xml.contains("BETWEEN #{startDate} AND #{endDate}"));
        assertTrue(xml.contains("wd.id IN"));
        assertTrue(xml.contains("AND cp.glr = #{managerId}"));
        for (String column : new String[]{"FILE_ID", "PRODUCT_CODE", "PRODUCT_NAME", "CONTRACT_VERSION",
                "FILE_NAME", "CATEGORY_CODE", "EFFECTIVE_DATE", "UPDATED_AT"}) {
            assertTrue("missing selected result field " + column, xml.contains("AS " + column));
        }
    }
}
