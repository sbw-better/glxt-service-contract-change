package com.citics.glxt.contractchange.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 验证预测响应只在JSON输出阶段限制小数位，不损失内部计算精度。 */
public class PredictionResponseSerializationTest {

    @Test
    public void shouldSerializePredictionNumbersWithAtMostFourDecimalPlaces() throws Exception {
        double original = 0.879620385984689D;
        ChangeTypePrediction type = new ChangeTypePrediction("26", original, 1, "CANDIDATE");
        PredictionReference reference = new PredictionReference(9L, "历史段落", original,
                Arrays.asList("26", "54", "76"));
        PredictionResponse response = new PredictionResponse("SEMANTIC", "test-v1", original,
                Collections.singletonList(type), Collections.singletonList(reference));

        String json = new ObjectMapper().writeValueAsString(response);
        JsonNode root = new ObjectMapper().readTree(json);

        assertEquals(0.8796D, root.get("maxSimilarity").doubleValue(), 0D);
        assertEquals(0.8796D, root.get("changeTypes").get(0).get("score").doubleValue(), 0D);
        assertEquals(0.8796D, root.get("references").get(0).get("similarity").doubleValue(), 0D);
        assertTrue(json.contains("\"maxSimilarity\":0.8796"));

        // JSON输出四舍五入不能反向修改用于阈值判断和排序的原始double。
        assertEquals(original, response.getMaxSimilarity(), 0D);
        assertEquals(original, response.getChangeTypes().get(0).getScore(), 0D);
        assertEquals(original, response.getReferences().get(0).getSimilarity(), 0D);
    }

    @Test
    public void shouldKeepExactMatchSimilarityAsJsonNumber() throws Exception {
        PredictionResponse response = new PredictionResponse("EXACT", "test-v1", 1D,
                Collections.<ChangeTypePrediction>emptyList(),
                Collections.<PredictionReference>emptyList());

        String json = new ObjectMapper().writeValueAsString(response);
        JsonNode root = new ObjectMapper().readTree(json);

        assertTrue(root.get("maxSimilarity").isNumber());
        assertEquals(1D, root.get("maxSimilarity").doubleValue(), 0D);
    }
}
