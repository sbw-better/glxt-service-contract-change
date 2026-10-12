package com.citics.glxt.contractchange.mapper;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.*;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.model.*;
import com.citics.glxt.contractchange.service.*;
import com.citics.glxt.contractchange.util.*;
import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.fasterxml.jackson.databind.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 生产DDL和Mapper替换为随机隔离对象；不连接或修改任何已有业务表。模型始终是测试替身。 */
public class OracleSampleLibraryIntegrationTest {
    @Test public void atomicUpgradeRollbackMutualExclusionAndRecoveryOnIsolatedOracle() throws Exception {
        String configPath=System.getProperty("oracle.vector.test.config");
        Assume.assumeTrue("Isolated Oracle config not supplied",configPath!=null);
        JsonNode config=new ObjectMapper().readTree(new File(configPath));
        DriverManagerDataSource ds=new DriverManagerDataSource(config.path("url").asText("jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1"),
                config.path("schema").asText(),config.path("password").asText());
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        String prefix="R"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();
        Map<String,String> names=new LinkedHashMap<>();
        names.put("SEQ_TPIF_HTDLYB",prefix+"_SEQ"); names.put("TPIF_HTDLYB",prefix+"_PARA");
        names.put("HT_SAMPLE_LIBRARY",prefix+"_CTRL");
        names.put("IDX_HTDLYB_MODEL",prefix+"_IDX_P");
        try (Connection conn=ds.getConnection(); Statement statement=conn.createStatement()) {
            try {
                String ddl=replace(new String(Files.readAllBytes(Paths.get("database/oracle/01_schema.sql")),StandardCharsets.UTF_8),names);
                ddl=ddl.replaceAll("(?m)^--[^\\r\\n]*","");
                for (String part:ddl.split(";")) if (!part.trim().isEmpty()) statement.execute(part.trim());
                Configuration configuration=new Configuration(new Environment("isolated-library",
                        new SpringManagedTransactionFactory(),ds));
                configuration.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
                for (String resource:Arrays.asList("ContractParagraphMapper","SampleLibraryMapper")) {
                    try (InputStream in=getClass().getClassLoader().getResourceAsStream(
                            "mybatis/mapper/contractchange/"+resource+".xml")) {
                        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int count;
                        while ((count=in.read(buffer))!=-1) out.write(buffer,0,count);
                        new XMLMapperBuilder(new StringReader(replace(new String(out.toByteArray(),StandardCharsets.UTF_8),names)),
                                configuration,resource,configuration.getSqlFragments()).parse();
                    }
                }
                SqlSessionTemplate session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
                ContractParagraphMapper paragraphs=session.getMapper(ContractParagraphMapper.class);
                SampleLibraryMapper actual=session.getMapper(SampleLibraryMapper.class);
                SampleLibraryMapper library=mock(SampleLibraryMapper.class,org.mockito.AdditionalAnswers.delegatesTo(actual));
                ContractParagraphPersistenceService persistence=transactional(new ContractParagraphPersistenceService(paragraphs),ds);
                SampleLibraryStore store=transactional(new SampleLibraryStore(library,paragraphs,persistence),ds);
                ContractChangeProperties props=new ContractChangeProperties();
                props.getEmbedding().setModelName("model-old"); props.getEmbedding().setModelVersion("old-v1");
                props.getEmbedding().setDimension(1024); props.getEmbedding().setBatchSize(16);
                float[] vector=new float[1024]; Arrays.fill(vector,0.012345678F); vector[0]=1;
                VectorUtils.normalize(vector);
                EmbeddingClient embedding=mock(EmbeddingClient.class);
                doAnswer(inv -> {
                    List<float[]> vectors=new ArrayList<>(); List<String> texts=inv.getArgument(0);
                    for(String ignored:texts) vectors.add(vector.clone()); return new EmbeddingBatchResult(1024,vectors);
                }).when(embedding).embed(anyList(),anyString());
                ParagraphVectorIndexService index=new ParagraphVectorIndexService(paragraphs,props); index.setStore(store);
                ContractParagraphImportService importer=new ContractParagraphImportService(paragraphs,persistence,index,embedding,props);
                SampleLibraryImportService service=new SampleLibraryImportService(importer,paragraphs,store,index,props,org.mockito.Mockito.mock(com.citics.glxt.contractchange.contractcompare.service.SftpContractFileLoader.class));
                assertTrue(((ImportResponse)service.importExcel(excel("历史条款","49","人工停用条款","71"),"test-user")).isSuccess());
                assertEquals("old-v1",store.control().getModelVersion());
                List<ContractParagraphDO> original=paragraphs.selectActiveParagraphs("old-v1",1024);
                Long oldId=original.get(0).getId(); String oldJson=original.get(0).getVectorData();
                assertTrue(oldJson.length()>4000);
                statement.executeUpdate("UPDATE "+names.get("TPIF_HTDLYB")+" SET SFSX=0 WHERE ID="+original.get(1).getId());
                String op="mutex-test"; store.claim(op,props.getEmbedding());
                ExecutorService other=Executors.newSingleThreadExecutor();
                try {
                    assertEquals(Integer.valueOf(409),other.submit(() -> {
                        try { store.claim("second-writer",props.getEmbedding()); return 0; }
                        catch(ContractChangeBusinessException ex) { return ex.getCode(); }
                    }).get(10,TimeUnit.SECONDS));
                } finally { other.shutdownNow(); store.release(op); }

                // 模型身份变更后，中途失败不能写入新版本或停用旧版本。
                props.getEmbedding().setModelName("model-new"); props.getEmbedding().setModelVersion("new-v2");
                when(embedding.embed(anyList(),anyString())).thenThrow(new IllegalStateException("sensitive"));
                try { service.importExcel(excel("新增条款","49"),"test-user"); fail(); }
                catch(ContractChangeBusinessException expected) { assertEquals(503,expected.getCode()); }
                assertEquals("old-v1",store.control().getModelVersion()); assertEquals(1,paragraphs.countActiveParagraphs("old-v1"));

                doAnswer(inv -> {
                    List<float[]> vectors=new ArrayList<>(); List<String> texts=inv.getArgument(0);
                    for(String ignored:texts) vectors.add(vector.clone()); return new EmbeddingBatchResult(1024,vectors);
                }).when(embedding).embed(anyList(),anyString());
                // 在插入、失效及控制行切换之后注入异常，验证整事务回滚。
                doThrow(new IllegalStateException("commit injection")).when(library).committed(anyString(),anyInt());
                try { service.importExcel(excel("新增条款","49"),"test-user"); fail(); }
                catch(ContractChangeBusinessException expected) { assertEquals(503,expected.getCode()); }
                assertEquals(2,paragraphs.countAllParagraphs()); assertEquals("old-v1",store.control().getModelVersion());
                assertEquals(1,paragraphs.countActiveParagraphs("old-v1")); assertEquals(0,paragraphs.countActiveParagraphs("new-v2"));
                doAnswer(inv -> actual.committed(inv.getArgument(0),inv.getArgument(1)))
                        .when(library).committed(anyString(),anyInt());

                clearInvocations(embedding);
                SampleRebuildResponse response=(SampleRebuildResponse)service.importExcel(excel("新增条款","49"),"test-user");
                assertTrue(response.isSuccess()); assertEquals("IDLE",store.control().getOperationStatus()); assertEquals(2,response.getInsertedSamples());
                assertEquals(1,response.getDisabledSamples()); assertEquals(4,paragraphs.countAllParagraphs());
                assertEquals(0,paragraphs.countActiveParagraphs("old-v1")); assertEquals(2,paragraphs.countActiveParagraphs("new-v2"));
                List<ContractParagraphDO> newRows=paragraphs.selectActiveParagraphs("new-v2",1024);
                assertNotEquals(oldId,newRows.get(0).getId()); assertEquals(oldJson,newRows.get(0).getVectorData());
                assertEquals(1,paragraphs.selectByTextHashes(Collections.singletonList(HashUtils.sha256("历史条款")),"old-v1").size());
                assertEquals(1,paragraphs.selectByTextHashes(Collections.singletonList(HashUtils.sha256("历史条款")),"new-v2").size());
                assertEquals(2,index.search(vector,10).size());
                assertEquals(1D,index.search(vector,10).get(0).getSimilarity(),1e-5);
                assertTrue(index.status().isRecognitionAvailable());
                verify(embedding,times(1)).embed(argThat(texts -> texts.size()==2),eq("test-user"));
                ContractParagraphPredictionService prediction=new ContractParagraphPredictionService(index,embedding,props);
                PredictionResponse predicted=prediction.predict("新查询文本","test-user");
                assertEquals("49",predicted.getChangeTypes().get(0).getCode());
                assertEquals(2,predicted.getChangeTypes().get(0).getSupportCount());
                assertEquals(1D,predicted.getChangeTypes().get(0).getScore(),1e-9);
                try (ResultSet result=statement.executeQuery("SELECT ID,YWBW,BGLX_CODES,VECTOR_DATA,SFSX,CREATE_TIME FROM "+names.get("TPIF_HTDLYB")+" WHERE ID="+oldId)) {
                    assertTrue(result.next()); assertEquals("历史条款",result.getString(2)); assertEquals("49",result.getString(3));
                    assertEquals(oldJson,result.getString(4)); assertEquals(0,result.getInt(5));
                    assertEquals(original.get(0).getCreateTime().getTime(),result.getTimestamp(6).getTime());
                }
                // 提交后占用不能由普通释放清除，重启只恢复索引，不重复调用模型。
                store.claim("recover-index",props.getEmbedding()); actual.committed("recover-index",2);
                store.release("recover-index"); assertEquals("DB_COMMITTED",store.control().getOperationStatus());
                clearInvocations(embedding);
                ParagraphVectorIndexService restarted=new ParagraphVectorIndexService(paragraphs,props); restarted.setStore(store);
                restarted.initialize(); assertEquals("IDLE",store.control().getOperationStatus());
                assertTrue(restarted.status().isRecognitionAvailable()); verifyZeroInteractions(embedding);
                // 已用版本不可回切；即使旧行全部失效也一样。
                props.getEmbedding().setModelName("model-old"); props.getEmbedding().setModelVersion("old-v1");
                try { service.importExcel(excel("历史条款","49"),"test-user"); fail(); }
                catch(ContractChangeBusinessException expected) { assertEquals(409,expected.getCode()); }
                System.out.println("Isolated Oracle model rebuild: composite uniqueness, >4000-character CLOB, writer exclusion, atomic rollback and full switch verified; database major="+conn.getMetaData().getDatabaseMajorVersion());
            } finally {
                // 仅删除本测试随机命名的对象；不枚举或操作已有业务对象。
                for(String name:Arrays.asList(names.get("HT_SAMPLE_LIBRARY"),names.get("TPIF_HTDLYB"))) {
                    assertTrue(name.startsWith(prefix+"_"));
                    try { statement.execute("DROP TABLE "+name+" PURGE"); }
                    catch(SQLException ex) { if(ex.getErrorCode()!=942) throw ex; }
                }
                try { statement.execute("DROP SEQUENCE "+names.get("SEQ_TPIF_HTDLYB")); }
                catch(SQLException ex) { if(ex.getErrorCode()!=2289) throw ex; }
            }
        }
    }
    private static String replace(String text,Map<String,String> names) {
        for(Map.Entry<String,String> entry:names.entrySet()) text=text.replace(entry.getKey(),entry.getValue());
        return text;
    }
    @SuppressWarnings("unchecked") private static <T> T transactional(T target,DriverManagerDataSource ds) {
        ProxyFactory proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        return (T)proxy.getProxy();
    }
    private static MockMultipartFile excel(String... cells) throws Exception {
        try(XSSFWorkbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet=wb.createSheet(); sheet.createRow(0).createCell(0).setCellValue("合同段落");
            sheet.getRow(0).createCell(1).setCellValue("变更类型编码");
            for(int i=0;i<cells.length;i+=2) { sheet.createRow(i/2+1).createCell(0).setCellValue(cells[i]); sheet.getRow(i/2+1).createCell(1).setCellValue(cells[i+1]); }
            wb.write(out); return new MockMultipartFile("file","test.xlsx","application/octet-stream",out.toByteArray());
        }
    }
}
