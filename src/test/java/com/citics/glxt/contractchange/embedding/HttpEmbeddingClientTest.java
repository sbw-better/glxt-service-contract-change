package com.citics.glxt.contractchange.embedding;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 统一模型网关请求协议、鉴权、响应校验和重试规则测试。 */
public class HttpEmbeddingClientTest {
    @Test
    public void shouldSendSingleOpenAiCompatibleRequestAndNormalizeResponse() {
        HttpEmbeddingClient client = client(3, 0);
        MockRestServiceServer server = server(client);
        server.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(header("UserId", "employee-001"))
                .andExpect(content().json("{\"model\":\"test-model\",\"input\":\"测试段落\","
                        + "\"dimensions\":\"3\",\"encoding_format\":\"float\"}"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[3,4,0]}]}",
                        MediaType.APPLICATION_JSON));

        EmbeddingBatchResult result = client.embed(
                Collections.singletonList("测试段落"), " employee-001 ");

        assertEquals(3, result.getDimension());
        assertEquals(0.6D, result.getVectors().get(0)[0], 0.000001D);
        assertEquals(0.8D, result.getVectors().get(0)[1], 0.000001D);
        server.verify();
    }

    @Test
    public void shouldSendArrayInputWhenBatchContainsMultipleTexts() {
        HttpEmbeddingClient client = client(2, 0);
        MockRestServiceServer server = server(client);
        server.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andExpect(content().json(
                        "{\"model\":\"test-model\",\"input\":[\"段落一\",\"段落二\"],"
                                + "\"dimensions\":\"2\",\"encoding_format\":\"float\"}"))
                .andRespond(withSuccess("{\"data\":[{\"index\":1,\"embedding\":[0,1]},"
                        + "{\"index\":0,\"embedding\":[1,0]}]}", MediaType.APPLICATION_JSON));

        EmbeddingBatchResult result = client.embed(Arrays.asList("段落一", "段落二"), "employee-001");

        assertEquals(2, result.getVectors().size());
        assertEquals(1D, result.getVectors().get(0)[0], 0.000001D);
        assertEquals(1D, result.getVectors().get(1)[1], 0.000001D);
        server.verify();
    }

    @Test
    public void shouldRejectBatchResponseWithoutIndexes() {
        HttpEmbeddingClient client = client(2, 0);
        MockRestServiceServer server = server(client);
        server.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[1,0]},"
                                + "{\"embedding\":[0,1]}]}",
                        MediaType.APPLICATION_JSON));

        assertUnavailable(client, Arrays.asList("段落一", "段落二"), "批量结果index");
        server.verify();
    }

    @Test
    public void shouldRejectWrongDimension() {
        HttpEmbeddingClient client = client(3, 0);
        MockRestServiceServer server = server(client);
        server.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[1,2]}]}",
                        MediaType.APPLICATION_JSON));

        assertUnavailable(client, "Embedding向量维度不是3");
        server.verify();
    }

    @Test
    public void shouldMapClientErrorsWithoutRetrying() {
        assertClientError(HttpStatus.BAD_REQUEST, "请求格式、输入长度或批量参数被拒绝");
        assertClientError(HttpStatus.UNAUTHORIZED, "认证或权限失败");
        assertClientError(HttpStatus.FORBIDDEN, "认证或权限失败");
        assertClientError(HttpStatus.NOT_FOUND, "接口地址或模型部署名称错误");
        assertClientError(HttpStatus.UNPROCESSABLE_ENTITY, "请求格式、输入长度或批量参数被拒绝");
        assertClientError(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁");
    }

    @Test
    public void shouldRetryServerErrorOnce() {
        HttpEmbeddingClient client = client(3, 1);
        MockRestServiceServer server = server(client);
        server.expect(times(2), requestTo("http://model/embedding/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertUnavailable(client, "暂时不可用");
        server.verify();
    }

    @Test
    public void shouldRetryConnectionOrReadFailureOnce() {
        HttpEmbeddingClient client = client(3, 1);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        AtomicInteger attempts = new AtomicInteger();
        restTemplate.setRequestFactory((uri, method) -> {
            attempts.incrementAndGet();
            throw new SocketTimeoutException("timeout");
        });

        assertUnavailable(client, "暂时不可用");
        assertEquals(2, attempts.get());
    }

    @Test
    public void shouldRejectResponseCountMismatchAndZeroVector() {
        HttpEmbeddingClient countClient = client(3, 0);
        MockRestServiceServer countServer = server(countClient);
        countServer.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertUnavailable(countClient, "返回数量与输入数量不一致");
        countServer.verify();

        HttpEmbeddingClient zeroClient = client(3, 0);
        MockRestServiceServer zeroServer = server(zeroClient);
        zeroServer.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[0,0,0]}]}",
                        MediaType.APPLICATION_JSON));
        assertUnavailable(zeroClient, "无法归一化");
        zeroServer.verify();
    }

    private void assertClientError(HttpStatus status, String messagePart) {
        HttpEmbeddingClient client = client(3, 1);
        MockRestServiceServer server = server(client);
        server.expect(once(), requestTo("http://model/embedding/v1/embeddings"))
                .andRespond(withStatus(status));
        assertUnavailable(client, messagePart);
        server.verify();
    }

    private void assertUnavailable(HttpEmbeddingClient client, String messagePart) {
        assertUnavailable(client, Collections.singletonList("测试段落"), messagePart);
    }

    private void assertUnavailable(HttpEmbeddingClient client, java.util.List<String> texts,
                                   String messagePart) {
        try {
            client.embed(texts, "employee-001");
            fail("应抛出模型服务不可用异常");
        } catch (ContractChangeBusinessException ex) {
            assertEquals(503, ex.getCode());
            assertTrue(ex.getMessage().contains(messagePart));
        }
    }

    private HttpEmbeddingClient client(int dimension, int maxRetries) {
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setUrl("http://model/embedding/v1/embeddings");
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModelName("test-model");
        properties.getEmbedding().setModelVersion("test-model-v1");
        properties.getEmbedding().setDimension(dimension);
        properties.getEmbedding().setMaxRetries(maxRetries);
        return new HttpEmbeddingClient(properties);
    }

    private MockRestServiceServer server(HttpEmbeddingClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
