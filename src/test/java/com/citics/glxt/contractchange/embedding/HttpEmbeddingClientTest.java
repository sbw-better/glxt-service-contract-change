package com.citics.glxt.contractchange.embedding;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class HttpEmbeddingClientTest {
    @Test
    public void shouldValidateAndNormalizeEmbeddingResponse() {
        ContractChangeProperties properties = properties(3);
        HttpEmbeddingClient client = new HttpEmbeddingClient(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://model/embed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("[[3,4,0]]", MediaType.APPLICATION_JSON));

        EmbeddingBatchResult result = client.embed(Collections.singletonList("测试段落"));

        assertEquals(3, result.getDimension());
        assertEquals(0.6D, result.getVectors().get(0)[0], 0.000001D);
        assertEquals(0.8D, result.getVectors().get(0)[1], 0.000001D);
        server.verify();
    }

    @Test(expected = ContractChangeBusinessException.class)
    public void shouldRejectWrongDimension() {
        ContractChangeProperties properties = properties(3);
        HttpEmbeddingClient client = new HttpEmbeddingClient(properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://model/embed"))
                .andRespond(withSuccess("[[1,2]]", MediaType.APPLICATION_JSON));
        client.embed(Collections.singletonList("测试段落"));
    }

    private ContractChangeProperties properties(int dimension) {
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setUrl("http://model/embed");
        properties.getEmbedding().setDimension(dimension);
        properties.getEmbedding().setMaxRetries(0);
        return properties;
    }
}
