package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.domain.ContractParagraphDO;
import com.citics.glxt.contractchange.embedding.EmbeddingClient;
import com.citics.glxt.contractchange.mapper.ContractParagraphMapper;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.model.PredictionResponse;
import com.citics.glxt.contractchange.service.ContractParagraphPredictionService.LenientPrediction;
import com.citics.glxt.contractchange.util.HashUtils;
import com.citics.glxt.contractchange.util.VectorCodec;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BatchPredictionServiceTest {
    private ContractChangeProperties properties;
    private ParagraphVectorIndexService indexService;

    @Before
    public void setUp() {
        properties = new ContractChangeProperties();
        properties.getEmbedding().setDimension(3);
        properties.getEmbedding().setModelVersion("test-v1");
        properties.getEmbedding().setBatchSize(16);
        properties.getSearch().setMinSimilarity(0.5D);

        ContractParagraphMapper mapper = mock(ContractParagraphMapper.class);
        when(mapper.selectActiveParagraphs("test-v1", 3)).thenReturn(Collections.singletonList(
                paragraph(1L, "历史段落", "TYPE_A", new float[]{1F, 0F, 0F})));
        indexService = new ParagraphVectorIndexService(mapper, properties);
        indexService.reload();
    }

    @Test
    public void shouldSplitSeventeenTextsIntoTwoEmbeddingBatches() {
        assertBatchSizes(17, Arrays.asList(16, 1));
    }

    @Test
    public void shouldSplitThirtyThreeTextsIntoThreeEmbeddingBatches() {
        assertBatchSizes(33, Arrays.asList(16, 16, 1));
    }

    @Test
    public void shouldSkipEmbeddingForExactMatchesInMixedBatch() {
        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList(), eq("employee-001"))).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return vectors(texts.size());
        });
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(indexService, client, properties);

        List<PredictionResponse> responses = service.predictBatch(
                Arrays.asList("历史段落", "新的段落"), "employee-001");

        assertEquals("EXACT", responses.get(0).getMatchType());
        assertEquals("SEMANTIC", responses.get(1).getMatchType());
        verify(client).embed(eq(Collections.singletonList("新的段落")), eq("employee-001"));
    }

    @Test
    public void shouldNotCallEmbeddingWhenEveryParagraphIsExact() {
        EmbeddingClient client = mock(EmbeddingClient.class);
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(indexService, client, properties);

        service.predictBatch(Arrays.asList("历史段落", "历史段落"), "employee-001");

        verify(client, never()).embed(anyList(), eq("employee-001"));
    }

    @Test
    public void shouldContinueAfterOneLenientEmbeddingBatchFails() {
        properties.getEmbedding().setBatchSize(1);
        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList(), eq("employee-001")))
                .thenThrow(new RuntimeException("gateway unavailable"))
                .thenReturn(vectors(1));
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(indexService, client, properties);

        List<LenientPrediction> responses = service.predictBatchLenient(
                Arrays.asList("失败段落", "成功段落"), "employee-001");

        assertEquals("EMBEDDING_UNAVAILABLE", responses.get(0).getErrorCode());
        assertEquals("SEMANTIC", responses.get(1).getPrediction().getMatchType());
    }

    private void assertBatchSizes(int paragraphCount, List<Integer> expectedSizes) {
        EmbeddingClient client = mock(EmbeddingClient.class);
        List<Integer> actualSizes = new ArrayList<Integer>();
        when(client.embed(anyList(), eq("employee-001"))).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            actualSizes.add(texts.size());
            return vectors(texts.size());
        });
        ContractParagraphPredictionService service =
                new ContractParagraphPredictionService(indexService, client, properties);
        List<String> paragraphs = new ArrayList<String>();
        for (int i = 0; i < paragraphCount; i++) {
            paragraphs.add("新段落" + i);
        }

        List<PredictionResponse> responses = service.predictBatch(paragraphs, "employee-001");

        assertEquals(paragraphCount, responses.size());
        assertEquals(expectedSizes, actualSizes);
    }

    private EmbeddingBatchResult vectors(int count) {
        List<float[]> vectors = new ArrayList<float[]>();
        for (int i = 0; i < count; i++) {
            vectors.add(new float[]{1F, 0F, 0F});
        }
        return new EmbeddingBatchResult(3, vectors);
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
