package com.citics.glxt.contractchange.mapper;

import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.service.ContractParagraphPersistenceService;
import com.citics.glxt.contractchange.util.VectorCodec;
import com.citics.glxt.contractchange.util.VectorUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.Test;
import org.junit.Assume;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import static org.junit.Assert.*;

/** Opt-in real Oracle test. Only random, newly created test objects are accessed. */
public class OracleVectorClobIntegrationTest {
    @Test
    public void shouldReadAndUpdateLargeClobThroughActualMapperAndRollbackWholeBatch() throws Exception {
        String url = System.getenv("ORACLE_VECTOR_TEST_URL");
        String username = System.getenv("ORACLE_VECTOR_TEST_USERNAME");
        String password = System.getenv("ORACLE_VECTOR_TEST_PASSWORD");
        String configPath = System.getProperty("oracle.vector.test.config");
        if (configPath != null) {
            JsonNode config = new ObjectMapper().readTree(new File(configPath));
            url = config.path("url").asText("jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1");
            username = config.path("schema").asText();
            password = config.path("password").asText();
        }
        Assume.assumeTrue("No isolated Oracle connection configured", url != null && !url.trim().isEmpty());
        assertNotNull("Oracle username required", username);
        assertNotNull("Oracle password required", password);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String table = "VEC_CLOB_T_" + suffix;
        String sequence = "VEC_CLOB_S_" + suffix;
        boolean tableCreated = false;
        boolean sequenceCreated = false;
        try (Connection connection = dataSource.getConnection(); Statement ddl = connection.createStatement()) {
            try {
                ddl.execute("CREATE TABLE " + table + " (ID NUMBER PRIMARY KEY, YWBW CLOB, GFBW CLOB, "
                        + "TEXT_HASH VARCHAR2(64) UNIQUE, BGLX_CODES VARCHAR2(1000), VECTOR_DATA CLOB NOT NULL, "
                        + "VECTOR_DIM NUMBER, MODEL_VERSION VARCHAR2(100), SOURCE_FILE VARCHAR2(255), "
                        + "SFSX NUMBER, CREATE_TIME DATE)");
                tableCreated = true;
                ddl.execute("CREATE SEQUENCE " + sequence);
                sequenceCreated = true;
                Configuration configuration = new Configuration(new Environment("isolated-vector-test",
                        new SpringManagedTransactionFactory(), dataSource));
                configuration.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
                String xml;
                try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                        "mybatis/mapper/contractchange/ContractParagraphMapper.xml")) {
                    assertNotNull(stream);
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = stream.read(buffer)) != -1) { output.write(buffer, 0, count); }
                    xml = new String(output.toByteArray(), StandardCharsets.UTF_8)
                            .replace("SEQ_TPIF_HTDLYB", sequence).replace("TPIF_HTDLYB", table);
                }
                new XMLMapperBuilder(new StringReader(xml), configuration, "isolated-vector-mapper",
                        configuration.getSqlFragments()).parse();
                ContractParagraphMapper mapper = new SqlSessionTemplate(
                        new SqlSessionFactoryBuilder().build(configuration)).getMapper(ContractParagraphMapper.class);
                ProxyFactory proxy = new ProxyFactory(new ContractParagraphPersistenceService(mapper));
                proxy.setProxyTargetClass(true);
                proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                        new AnnotationTransactionAttributeSource()));
                ContractParagraphPersistenceService persistence = (ContractParagraphPersistenceService) proxy.getProxy();
                ContractParagraphDO row = row("first", 1F);
                assertTrue(row.getVectorData().length() > 4000);
                persistence.saveAll(Collections.singletonList(row));
                try (java.sql.ResultSet result = ddl.executeQuery("SELECT DBMS_LOB.GETLENGTH(VECTOR_DATA) FROM " + table)) {
                    assertTrue(result.next());
                    assertEquals(row.getVectorData().length(), result.getInt(1));
                }
                assertEquals(row.getVectorData(), mapper.selectActiveParagraphs("test-clob", 1024).get(0).getVectorData());
                row.setVectorData(row("unused", -1F).getVectorData());
                persistence.saveAll(Collections.singletonList(row));
                ContractParagraphDO loaded = mapper.selectActiveParagraphs("test-clob", 1024).get(0);
                assertEquals(row.getVectorData(), loaded.getVectorData());
                float[] decoded = VectorCodec.decode(loaded.getVectorData(), 1024);
                float[] expectedVector = VectorCodec.decode(row.getVectorData(), 1024);
                for (int i = 0; i < decoded.length; i++) {
                    assertEquals(Float.floatToRawIntBits(expectedVector[i]), Float.floatToRawIntBits(decoded[i]));
                }
                com.citics.glxt.contractchange.config.ContractChangeProperties properties =
                        new com.citics.glxt.contractchange.config.ContractChangeProperties();
                properties.getEmbedding().setModelVersion("test-clob");
                properties.getEmbedding().setDimension(1024);
                com.citics.glxt.contractchange.service.ParagraphVectorIndexService index =
                        new com.citics.glxt.contractchange.service.ParagraphVectorIndexService(mapper, properties);
                assertEquals("READY", index.reload().getStatus());
                assertNotNull(index.exact("first"));
                com.citics.glxt.contractchange.embedding.EmbeddingClient embedding = org.mockito.Mockito.mock(
                        com.citics.glxt.contractchange.embedding.EmbeddingClient.class);
                org.mockito.Mockito.when(embedding.embed(org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString())).thenReturn(
                        new com.citics.glxt.contractchange.model.EmbeddingBatchResult(1024, Collections.singletonList(decoded)));
                com.citics.glxt.contractchange.model.PredictionResponse prediction =
                        new com.citics.glxt.contractchange.service.ContractParagraphPredictionService(index, embedding, properties)
                                .predict("未入库的新测试段落", "test");
                assertEquals("SEMANTIC", prediction.getMatchType());
                assertEquals("49", prediction.getChangeTypes().get(0).getCode());
                assertEquals(1, mapper.selectByTextHashes(Collections.singletonList("first")).size());
                try {
                    // The first insert and update must both roll back after the duplicate insert fails.
                    row.setVectorData(row("unused", 1F).getVectorData());
                    persistence.saveAll(Arrays.asList(row("second", 1F), row, row("first", 1F)));
                    fail("Expected unique constraint violation");
                } catch (RuntimeException expected) {
                    assertEquals(1, mapper.countAllParagraphs());
                    assertEquals(loaded.getVectorData(), mapper.selectActiveParagraphs("test-clob", 1024).get(0).getVectorData());
                }
                System.out.println("Isolated Oracle CLOB Mapper insert/read/update and Spring transaction rollback verified");
                System.out.println("Oracle test database major version: " + connection.getMetaData().getDatabaseMajorVersion());
            } finally {
                try { if (sequenceCreated) { ddl.execute("DROP SEQUENCE " + sequence); } }
                finally { if (tableCreated) { ddl.execute("DROP TABLE " + table + " PURGE"); } }
            }
        }
    }

    private ContractParagraphDO row(String hash, float first) {
        float[] vector = new float[1024];
        Arrays.fill(vector, 0.012345678F);
        vector[0] = first;
        VectorUtils.normalize(vector);
        ContractParagraphDO row = new ContractParagraphDO();
        row.setOriginalText("测试样本");
        row.setNormalizedText("测试样本");
        row.setTextHash(hash);
        row.setChangeTypeCodes("49");
        row.setVectorData(VectorCodec.encode(vector));
        row.setVectorDim(1024);
        row.setModelVersion("test-clob");
        row.setSourceFile("isolated-test.xlsx");
        row.setEnabled(1);
        return row;
    }
}
