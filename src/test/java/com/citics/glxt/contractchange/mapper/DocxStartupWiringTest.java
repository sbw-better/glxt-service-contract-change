package com.citics.glxt.contractchange.mapper;

import com.citics.glxt.contractchange.ContractChangeApplication;
import com.citics.glxt.contractchange.docx.mapper.ContractAnalysisMainMapper;
import com.citics.glxt.contractchange.docx.mapper.ContractAnalysisDetailMapper;
import org.junit.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import static org.junit.Assert.*;

/** 包迁移后，启动配置和实际XML仍须指向现有Mapper及实体类。 */
public class DocxStartupWiringTest {
    @Test public void wordTextNodesMustBeRecognizedInsideRuns() {
        org.docx4j.wml.Text text=new org.docx4j.wml.Text(); text.setValue("合同正文");
        org.docx4j.wml.R run=new org.docx4j.wml.R();
        run.getContent().add(new javax.xml.bind.JAXBElement<org.docx4j.wml.Text>(
                new javax.xml.namespace.QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t"),
                org.docx4j.wml.Text.class,text));
        StringBuilder result=new StringBuilder();
        com.citics.glxt.contractchange.docx.utils.CommonUtils.extractTextFromNodeList(
                java.util.Collections.singletonList(run),result);
        assertEquals("合同正文",result.toString());
    }
    @Test public void movedMapperXmlMustResolveCurrentDomainClasses() throws Exception {
        Configuration configuration=new Configuration();
        for (String name:new String[]{"ContractAnalysisMainMapper.xml","ContractAnalysisDetailMapper.xml"}) {
            String path="mybatis/mapper/docx/"+name;
            try (InputStream input=new ClassPathResource(path).getInputStream()) {
                new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();
            }
        }
        assertTrue(configuration.hasStatement(ContractAnalysisMainMapper.class.getName()+".insert"));
        assertTrue(configuration.hasStatement(ContractAnalysisDetailMapper.class.getName()+".insert"));
        assertTrue(configuration.hasStatement(ContractAnalysisDetailMapper.class.getName()+".insertBatch"));
    }

    @Test public void applicationMustScanBothMapperPackages() {
        MapperScan scan=ContractChangeApplication.class.getAnnotation(MapperScan.class);
        for (String mapperPackage:new String[]{"com.citics.glxt.contractchange.mapper","com.citics.glxt.contractchange.docx.mapper"}) {
            boolean covered=false;
            for (String base:scan.value()) covered|=mapperPackage.equals(base) || mapperPackage.startsWith(base+".");
            assertTrue("Mapper package not scanned: "+mapperPackage,covered);
        }
    }
}
