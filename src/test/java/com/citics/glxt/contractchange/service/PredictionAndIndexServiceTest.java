package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.util.HashUtils;
import com.citics.glxt.contractchange.util.VectorCodec;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PredictionAndIndexServiceTest {
    private ParagraphVectorIndexService indexService;
    private ContractChangeProperties properties;

    @Before
    public void setUp() {
        properties = new ContractChangeProperties();
        properties.getEmbedding().setDimension(3);
        properties.getEmbedding().setModelVersion("test-v1");
        properties.getSearch().setMinSimilarity(0.5D);
        properties.getSearch().setCandidateThreshold(0.55D);
        properties.getSearch().setHighThreshold(0.80D);
        properties.getSearch().setMinSupportCount(2);

        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Arrays.asList(
                paragraph(1L, "历史段落一", "TYPE_A;TYPE_B", new float[]{1F, 0F, 0F}),
                paragraph(2L, "历史段落二", "TYPE_A", new float[]{0.9F, 0.4358899F, 0F})
        ));
        indexService = new ParagraphVectorIndexService(mapper, properties);
        indexService.reload();
    }

    @Test
    public void shouldBuildIndexAndReturnExactMatch() {
        EmbeddingClient client = mock(EmbeddingClient.class);
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(indexService, client, properties);
        PredictionResponse response = service.predict("历史段落一");
        assertEquals("EXACT", response.getMatchType());
        assertEquals(2, response.getChangeTypes().size());
        assertEquals(2, indexService.status().getSampleCount());
    }

    @Test
    public void shouldVoteAcrossSemanticMatches() {
        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1F, 0F, 0F})));
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(indexService, client, properties);

        PredictionResponse response = service.predict("新的相似段落");

        assertEquals("SEMANTIC", response.getMatchType());
        assertEquals("TYPE_A", response.getChangeTypes().get(0).getCode());
        assertEquals("HIGH", response.getChangeTypes().get(0).getLevel());
        assertTrue(response.getChangeTypes().stream()
                .anyMatch(value -> "TYPE_B".equals(value.getCode()) && "CANDIDATE".equals(value.getLevel())));
    }

    /**
     * 已召回相似历史段落、但没有任何类型达到候选阈值时，应明确返回无可靠匹配。
     * 参考段落仍然保留，调用方可以据此展示相近案例，而不会误认为类型识别已经成功。
     */
    @Test
    public void shouldReturnNoReliableMatchWhenAllTypeScoresAreBelowCandidateThreshold() {
        double firstSimilarity = 0.7021242132970985D;
        double secondSimilarity = 0.6374979584748034D;
        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Arrays.asList(
                paragraph(3L, "风险揭示段落", "TYPE_20;TYPE_25;TYPE_28",
                        vectorWithSimilarity(secondSimilarity)),
                paragraph(4L, "管理人义务段落", "TYPE_35;TYPE_40;TYPE_45",
                        vectorWithSimilarity(firstSimilarity))
        ));
        ParagraphVectorIndexService disjointTypeIndex = new ParagraphVectorIndexService(mapper, properties);
        disjointTypeIndex.reload();

        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1F, 0F, 0F})));
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(disjointTypeIndex, client, properties);

        PredictionResponse response = service.predict("新的待识别段落");

        assertEquals("NO_RELIABLE_MATCH", response.getMatchType());
        assertTrue(response.getChangeTypes().isEmpty());
        assertEquals(2, response.getReferences().size());
        assertEquals(firstSimilarity, response.getMaxSimilarity(), 0.000001D);
    }

    /**
     * 复现真实接口中第一名相似度约0.84、但多组互斥标签导致投票得分被稀释的场景。
     * 第一名达到强匹配阈值且明显领先第二名时，应返回第一名标签作为候选结果。
     */
    @Test
    public void shouldReturnFirstSampleTypesWhenStrongTopMatchClearlyLeads() {
        double firstSimilarity = 0.8396705156167351D;
        double secondSimilarity = 0.7439026569371111D;
        double thirdSimilarity = 0.7000000000000000D;
        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Arrays.asList(
                paragraph(6L, "管理人义务段落", "35;40;45;69", vectorWithSimilarity(firstSimilarity)),
                paragraph(9L, "信息披露段落", "26;54;76", vectorWithSimilarity(secondSimilarity)),
                paragraph(10L, "风险揭示段落", "20;25;28", vectorWithSimilarity(thirdSimilarity))
        ));
        ParagraphVectorIndexService strongMatchIndex = new ParagraphVectorIndexService(mapper, properties);
        strongMatchIndex.reload();

        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1F, 0F, 0F})));
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(strongMatchIndex, client, properties);

        PredictionResponse response = service.predict("强相似但标签分散的新段落");

        assertEquals("SEMANTIC", response.getMatchType());
        assertEquals(4, response.getChangeTypes().size());
        assertTrue(response.getChangeTypes().stream().allMatch(value -> "CANDIDATE".equals(value.getLevel())));
        assertTrue(response.getChangeTypes().stream().allMatch(value -> value.getSupportCount() == 1));
        assertTrue(response.getChangeTypes().stream()
                .anyMatch(value -> "35".equals(value.getCode())
                        && value.getScore() < properties.getSearch().getCandidateThreshold()));
    }

    /** 简化规则：投票无结果但第一名达到0.80时返回候选，不再增加前两名差值条件。 */
    @Test
    public void shouldReturnCandidateWhenFirstSimilarityReachesStrongThreshold() {
        double firstSimilarity = 0.84D;
        double secondSimilarity = 0.81D;
        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Arrays.asList(
                paragraph(11L, "相近段落一", "TYPE_X", vectorWithSimilarity(firstSimilarity)),
                paragraph(12L, "相近段落二", "TYPE_Y", vectorWithSimilarity(secondSimilarity)),
                paragraph(13L, "相近段落三", "TYPE_Z", vectorWithSimilarity(0.79D))
        ));
        ParagraphVectorIndexService closeMatchIndex = new ParagraphVectorIndexService(mapper, properties);
        closeMatchIndex.reload();

        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1F, 0F, 0F})));
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(closeMatchIndex, client, properties);

        PredictionResponse response = service.predict("两个结果非常接近的新段落");

        assertEquals("SEMANTIC", response.getMatchType());
        assertTrue(response.getChangeTypes().stream()
                .anyMatch(value -> "TYPE_X".equals(value.getCode())
                        && "CANDIDATE".equals(value.getLevel())));
        assertEquals(3, response.getReferences().size());
    }

    /** 正常空库应直接返回明确原因，不浪费CPU模型调用。 */
    @Test
    public void shouldReturnEmptyIndexWithoutCallingEmbedding() {
        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Collections.emptyList());
        ParagraphVectorIndexService emptyIndex = new ParagraphVectorIndexService(mapper, properties);
        emptyIndex.reload();
        EmbeddingClient client = mock(EmbeddingClient.class);
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(emptyIndex, client, properties);

        PredictionResponse response = service.predict("新段落");

        assertEquals("NO_RELIABLE_MATCH", response.getMatchType());
        verify(client, never()).embed(anyList());
    }

    /** 低于业务阈值时仍返回真实最高相似度和参考段落，而不是误报为0。 */
    @Test
    public void shouldKeepActualBestSimilarityBelowThreshold() {
        properties.getSearch().setMinSimilarity(0.6D);
        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Collections.singletonList(
                paragraph(20L, "弱相似段落", "TYPE_W", vectorWithSimilarity(0.59D))));
        ParagraphVectorIndexService weakIndex = new ParagraphVectorIndexService(mapper, properties);
        weakIndex.reload();
        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList())).thenReturn(new EmbeddingBatchResult(3,
                Collections.singletonList(new float[]{1F, 0F, 0F})));
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(weakIndex, client, properties);

        PredictionResponse response = service.predict("低于阈值的新段落");

        assertEquals(0.59D, response.getMaxSimilarity(), 0.000001D);
        assertEquals(1, response.getReferences().size());
    }

    /** 数据库有记录但全部向量损坏时必须标记为DEGRADED，而不是正常EMPTY。 */
    @Test
    public void shouldMarkIndexDegradedWhenAllDatabaseVectorsAreInvalid() {
        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        ContractParagraphDO broken = paragraph(30L, "损坏段落", "TYPE_BROKEN", new float[]{1F, 0F, 0F});
        broken.setVectorData(new byte[]{1, 2, 3});
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Collections.singletonList(broken));
        ParagraphVectorIndexService brokenIndex = new ParagraphVectorIndexService(mapper, properties);

        assertEquals("DEGRADED", brokenIndex.reload().getStatus());
        assertEquals(0, brokenIndex.status().getSampleCount());
        assertEquals(1, brokenIndex.status().getErrorCount());
    }

    /** 构造与查询向量 {@code [1, 0, 0]} 具有指定余弦相似度的单位向量。 */
    private float[] vectorWithSimilarity(double similarity) {
        return new float[]{(float) similarity, (float) Math.sqrt(1D - similarity * similarity), 0F};
    }

    private ContractParagraphDO paragraph(long id, String text, String codes, float[] vector) {
        ContractParagraphDO row = new ContractParagraphDO();
        row.setId(id);
        row.setOriginalText(text);
        row.setNormalizedText(text);
        row.setTextHash(HashUtils.sha256(text));
        row.setChangeTypeCodes(codes);
        row.setVectorData(VectorCodec.encode(vector));
        row.setVectorDim(3);
        row.setModelVersion("test-v1");
        row.setEnabled(1);
        return row;
    }
}
