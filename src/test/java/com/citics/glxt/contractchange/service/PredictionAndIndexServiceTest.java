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
