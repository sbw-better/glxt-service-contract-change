package com.citics.glxt.contractchange.embedding;

import com.citics.glxt.contractchange.common.BusinessException;
import com.citics.glxt.contractchange.common.CommonConstants;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.util.VectorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hugging Face Embedding 容器的 HTTP 客户端。
 *
 * <p>客户端只发送批量文本并接收二维浮点数组，负责数量、维度和数值合法性校验，随后在
 * Java 侧进行 L2 归一化。日志严禁输出请求正文和向量。</p>
 */
@Slf4j
@Component
public class HttpEmbeddingClient implements EmbeddingClient {
    private final ContractChangeProperties.Embedding properties;
    private final RestTemplate restTemplate;

    /** 根据配置创建带连接和读取超时的轻量 HTTP 客户端。 */
    public HttpEmbeddingClient(ContractChangeProperties contractProperties) {
        this.properties = contractProperties.getEmbedding();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public EmbeddingBatchResult embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) throw new BusinessException("向量化文本不能为空");
        long started = System.currentTimeMillis();
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                Map<String, Object> body = new HashMap<String, Object>();
                body.put("inputs", texts);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<List> response = restTemplate.postForEntity(
                        properties.getUrl(), new HttpEntity<Map<String, Object>>(body, headers), List.class);
                List<?> rows = response.getBody();
                List<float[]> vectors = parse(rows, texts.size());
                log.info("Embedding调用成功, count={}, elapsedMs={}", texts.size(),
                        System.currentTimeMillis() - started);
                return new EmbeddingBatchResult(properties.getDimension(), vectors);
            } catch (HttpClientErrorException ex) {
                log.warn("Embedding请求被拒绝, status={}, count={}, attempt={}",
                        ex.getStatusCode().value(), texts.size(), attempt + 1);
                throw unavailable("Embedding请求被拒绝, status=" + ex.getStatusCode().value(), ex);
            } catch (HttpServerErrorException ex) {
                last = ex;
                log.warn("Embedding服务端异常, status={}, count={}, attempt={}/{}",
                        ex.getStatusCode().value(), texts.size(), attempt + 1, properties.getMaxRetries() + 1);
            } catch (ResourceAccessException ex) {
                last = ex;
                log.warn("Embedding连接或读取失败, exception={}, count={}, attempt={}/{}",
                        ex.getClass().getSimpleName(), texts.size(), attempt + 1,
                        properties.getMaxRetries() + 1);
            } catch (BusinessException ex) {
                log.warn("Embedding响应校验失败, count={}, reason={}", texts.size(), ex.getMessage());
                throw unavailable(ex.getMessage(), ex);
            } catch (RuntimeException ex) {
                log.error("Embedding响应处理异常, count={}, exception={}",
                        texts.size(), ex.getClass().getSimpleName(), ex);
                throw unavailable("Embedding响应处理失败", ex);
            }
        }
        log.error("Embedding服务调用最终失败, count={}, attempts={}, exception={}, elapsedMs={}",
                texts.size(), properties.getMaxRetries() + 1,
                last == null ? "unknown" : last.getClass().getSimpleName(),
                System.currentTimeMillis() - started);
        throw unavailable("Embedding服务调用失败", last);
    }

    @Override
    public boolean isHealthy() {
        try {
            restTemplate.getForEntity(properties.getHealthUrl(), String.class);
            return true;
        } catch (RuntimeException ex) {
            // Actuator 可能高频探测，失败仅记 DEBUG，避免模型故障时刷屏。
            log.debug("Embedding健康检查失败, exception={}", ex.getClass().getSimpleName());
            return false;
        }
    }

    /** 校验响应数量、向量维度和元素类型，并对每条向量执行 L2 归一化。 */
    private List<float[]> parse(List<?> rows, int expectedCount) {
        if (rows == null || rows.size() != expectedCount) {
            throw new BusinessException("Embedding返回数量与输入数量不一致");
        }
        List<float[]> vectors = new ArrayList<float[]>(rows.size());
        for (Object row : rows) {
            if (!(row instanceof List)) throw new BusinessException("Embedding返回格式错误");
            List<?> values = (List<?>) row;
            if (values.size() != properties.getDimension()) {
                throw new BusinessException("Embedding向量维度不是" + properties.getDimension());
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (!(value instanceof Number)) throw new BusinessException("Embedding向量包含非数字值");
                vector[i] = ((Number) value).floatValue();
            }
            VectorUtils.normalize(vector);
            vectors.add(vector);
        }
        return vectors;
    }

    /** 将模型侧异常统一转换为不暴露响应体的 503 业务异常。 */
    private BusinessException unavailable(String message, Throwable cause) {
        String detail = cause == null ? message : message + ": " + cause.getClass().getSimpleName();
        return new BusinessException(CommonConstants.SERVICE_UNAVAILABLE, detail);
    }
}
