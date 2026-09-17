package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.model.ChangeTypePrediction;
import com.citics.glxt.contractchange.model.PredictionResponse;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileChangeTypeAggregatorTest {
    private final FileChangeTypeAggregator aggregator = new FileChangeTypeAggregator();

    @Test
    public void shouldIncludeHighAndTwoParagraphCandidatesInSortedOrder() {
        List<String> paragraphs = Arrays.asList("段落一", "段落二", "段落三");
        List<PredictionResponse> predictions = Arrays.asList(
                prediction(type("28", "HIGH"), type("43", "CANDIDATE")),
                prediction(type("19", "CANDIDATE"), type("43", "CANDIDATE")),
                prediction(type("19", "CANDIDATE"), type("57", "CANDIDATE")));

        assertEquals(Arrays.asList("19", "28", "43"), aggregator.aggregate(paragraphs, predictions));
    }

    @Test
    public void shouldNotCountDuplicateNormalizedParagraphTwice() {
        List<String> paragraphs = Arrays.asList("重复段落", "重复段落");
        List<PredictionResponse> predictions = Arrays.asList(
                prediction(type("20", "CANDIDATE")),
                prediction(type("20", "CANDIDATE")));

        assertTrue(aggregator.aggregate(paragraphs, predictions).isEmpty());
    }

    @Test
    public void shouldIgnoreNoReliableMatchesAndSingleCandidate() {
        PredictionResponse noMatch = new PredictionResponse("NO_RELIABLE_MATCH", "test-v1", 0.5D,
                Collections.<ChangeTypePrediction>emptyList(), Collections.emptyList());

        assertTrue(aggregator.aggregate(Arrays.asList("段落一", "段落二"),
                Arrays.asList(noMatch, prediction(type("25", "CANDIDATE")))).isEmpty());
    }

    @Test
    public void shouldMergeDifferentTypesForSameParagraphWithoutDoubleCounting() {
        List<String> paragraphs = Arrays.asList("重复段落", "重复段落", "另一段落");
        List<List<ChangeTypePrediction>> types = Arrays.asList(
                Collections.singletonList(type("20", "CANDIDATE")),
                Arrays.asList(type("20", "CANDIDATE"), type("28", "HIGH")),
                Collections.singletonList(type("20", "CANDIDATE")));

        assertEquals(Arrays.asList("20", "28"),
                aggregator.aggregateChangeTypes(paragraphs, types));
    }

    private PredictionResponse prediction(ChangeTypePrediction... types) {
        return new PredictionResponse("SEMANTIC", "test-v1", 0.9D,
                Arrays.asList(types), Collections.emptyList());
    }

    private ChangeTypePrediction type(String code, String level) {
        return new ChangeTypePrediction(code, "HIGH".equals(level) ? 0.9D : 0.6D, 2, level);
    }
}
