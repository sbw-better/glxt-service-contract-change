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
