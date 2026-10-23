package com.citics.glxt.contractchange.embedding;

import com.citics.glxt.contractchange.common.constants.CommonConstants;
import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.config.ContractChangeProperties;
import com.citics.glxt.contractchange.embedding.dto.EmbeddingGatewayData;
import com.citics.glxt.contractchange.embedding.dto.EmbeddingGatewayRequest;
import com.citics.glxt.contractchange.embedding.dto.EmbeddingGatewayResponse;
import com.citics.glxt.contractchange.model.EmbeddingBatchResult;
import com.citics.glxt.contractchange.util.VectorUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 公司内网统一 Embedding 网关客户端。
 *
 * <p>它只做一件事：把一批合同段落发给模型，并取回同样数量的数字向量。拿到结果后还会检查
 * 数量、维度和数值是否正常，再统一做归一化，保证历史段落和新段落使用相同的比较标准。</p>
 *
 * <p>日志严禁输出 API Key、UserId、合同正文、完整请求体和向量内容。</p>
 */
@Slf4j
@Component
public class HttpEmbeddingClient implements EmbeddingClient {
    private static final String USER_ID_HEADER = "UserId";
    private static final String FLOAT_ENCODING_FORMAT = "float";
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private final ContractChangeProperties contractProperties;
    private final RestTemplate restTemplate;

    /** 创建HTTP客户端；每批请求读取当前配置快照，支持Nacos更新后执行模型重建。 */
    public HttpEmbeddingClient(ContractChangeProperties contractProperties) {
        this.contractProperties = contractProperties;
        ContractChangeProperties.Embedding initial = snapshot();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(initial.getConnectTimeoutMs());
        factory.setReadTimeout(initial.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 批量生成向量。网络暂时中断或模型服务内部报错时可以重试；请求参数或权限错误重试也不会成功，
     * 因此遇到4xx直接返回明确错误。
     */
    @Override
    public EmbeddingBatchResult embed(List<String> texts, String userId) {
        ContractChangeProperties.Embedding properties = snapshot();
        validateRequest(texts, userId);
        long started = System.currentTimeMillis();
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                EmbeddingGatewayRequest body = new EmbeddingGatewayRequest(
                        properties.getModelName(),
                        texts,
                        String.valueOf(properties.getDimension()),
                        FLOAT_ENCODING_FORMAT);
                ResponseEntity<EmbeddingGatewayResponse> response = restTemplate.postForEntity(
                        properties.getUrl(), new HttpEntity<EmbeddingGatewayRequest>(body,
                                gatewayHeaders(userId, properties.getApiKey())),
                        EmbeddingGatewayResponse.class);
                List<float[]> vectors = parse(response.getBody(), texts.size(), properties.getDimension());
                log.info("Embedding网关调用成功, count={}, elapsedMs={}", texts.size(),
                        System.currentTimeMillis() - started);
                return new EmbeddingBatchResult(properties.getDimension(), vectors);
            } catch (HttpClientErrorException ex) {
                int status = ex.getStatusCode().value();
                String responseBody = ex.getResponseBodyAsString();
                if (isQuotaExceeded(responseBody)) {
                    log.warn("Embedding模型额度超限, status={}, count={}, attempt={}, providerCode={}",
                            status, texts.size(), attempt + 1, safeErrorMetadata(responseBody));
                    throw new EmbeddingQuotaExceededException(
                            "Embedding模型额度已超限，请检查模型平台额度或计费状态后重试", ex);
                }
                log.warn("Embedding网关请求被拒绝, status={}, count={}, attempt={}",
                        status, texts.size(), attempt + 1);
                throw unavailable(clientErrorMessage(status, ex), ex);
            } catch (HttpServerErrorException ex) {
                last = ex;
                log.warn("Embedding网关服务端异常, status={}, count={}, attempt={}/{}",
                        ex.getStatusCode().value(), texts.size(), attempt + 1,
                        properties.getMaxRetries() + 1);
            } catch (ResourceAccessException ex) {
                last = ex;
                log.warn("Embedding网关连接或读取失败, exception={}, count={}, attempt={}/{}",
                        ex.getClass().getSimpleName(), texts.size(), attempt + 1,
                        properties.getMaxRetries() + 1);
            } catch (ContractChangeBusinessException ex) {
                log.warn("Embedding网关响应校验失败, count={}, reason={}", texts.size(), ex.getMessage());
                throw unavailable(ex.getMessage(), ex);
            } catch (RuntimeException ex) {
                log.error("Embedding网关响应处理异常, count={}, exception={}",
                        texts.size(), ex.getClass().getSimpleName(), ex);
                throw unavailable("Embedding网关响应处理失败", ex);
            }
        }
        log.error("Embedding网关调用最终失败, count={}, attempts={}, exception={}, elapsedMs={}",
                texts.size(), properties.getMaxRetries() + 1,
                last == null ? "unknown" : last.getClass().getSimpleName(),
                System.currentTimeMillis() - started);
        throw unavailable("Embedding网关暂时不可用", last);
    }

    /** 组装模型平台要求的请求头；API Key只来自服务端配置，不能由前端传入。 */
    private HttpHeaders gatewayHeaders(String userId, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        headers.set(USER_ID_HEADER, userId.trim());
        return headers;
    }

    /** 在发送请求前拒绝空文本或空操作人，避免产生无意义的网关调用。 */
    private void validateRequest(List<String> texts, String userId) {
        if (texts == null || texts.isEmpty()) {
            throw new ContractChangeBusinessException("向量化文本不能为空");
        }
        for (String text : texts) {
            if (text == null || text.trim().isEmpty()) {
                throw new ContractChangeBusinessException("向量化文本不能为空");
            }
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new ContractChangeBusinessException("UserId不能为空");
        }
    }

    /**
     * 检查模型返回结果，并把每条向量调整到统一长度标准。
     * 这样后续直接计算两个向量的点积，就能得到段落之间的余弦相似度。
     */
    private List<float[]> parse(EmbeddingGatewayResponse response, int expectedCount, int dimension) {
        List<EmbeddingGatewayData> rows = response == null ? null : response.getData();
        if (rows == null || rows.size() != expectedCount) {
            throw new ContractChangeBusinessException("Embedding返回数量与输入数量不一致");
        }
        List<EmbeddingGatewayData> orderedRows = orderByInputIndex(rows, expectedCount);
        List<float[]> vectors = new ArrayList<float[]>(orderedRows.size());
        for (EmbeddingGatewayData row : orderedRows) {
            List<Double> values = row == null ? null : row.getEmbedding();
            if (values == null || values.size() != dimension) {
                throw new ContractChangeBusinessException(
                        "Embedding向量维度不是" + dimension);
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Double value = values.get(i);
                if (value == null || value.isNaN() || value.isInfinite()) {
                    throw new ContractChangeBusinessException("Embedding向量包含非法数值");
                }
                vector[i] = value.floatValue();
                if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                    throw new ContractChangeBusinessException("Embedding向量包含非法数值");
                }
            }
            try {
                VectorUtils.normalize(vector);
            } catch (IllegalArgumentException ex) {
                throw new ContractChangeBusinessException("Embedding向量无法归一化");
            }
            vectors.add(vector);
        }
        return vectors;
    }

    /** 每个模型批次固定读取一份配置，避免Nacos刷新时请求参数前后不一致。 */
    private ContractChangeProperties.Embedding snapshot() {
        ContractChangeProperties.Embedding value = new ContractChangeProperties.Embedding();
        org.springframework.beans.BeanUtils.copyProperties(contractProperties.getEmbedding(), value);
        return value;
    }

    /**
     * 批量调用时，模型返回顺序不一定与输入顺序一致，所以根据index把结果放回原来的位置，
     * 防止某个段落误用了另一个段落的向量。单条调用允许模型不返回index。
     */
    private List<EmbeddingGatewayData> orderByInputIndex(List<EmbeddingGatewayData> rows,
                                                          int expectedCount) {
        if (expectedCount == 1) {
            EmbeddingGatewayData row = rows.get(0);
            if (row != null && row.getIndex() != null && row.getIndex() != 0) {
                throw new ContractChangeBusinessException("Embedding返回index不正确");
            }
            return rows;
        }
        List<EmbeddingGatewayData> ordered = new ArrayList<EmbeddingGatewayData>(
                Collections.nCopies(expectedCount, (EmbeddingGatewayData) null));
        for (EmbeddingGatewayData row : rows) {
            Integer index = row == null ? null : row.getIndex();
            if (index == null || index < 0 || index >= expectedCount || ordered.get(index) != null) {
                throw new ContractChangeBusinessException("Embedding批量结果index缺失、重复或越界");
            }
            ordered.set(index, row);
        }
        return ordered;
    }

    /** 将常见 4xx 转换为不泄露平台响应体的明确诊断信息。 */
    private String clientErrorMessage(int status, HttpClientErrorException ex) {
        String message;
        if (status == 401 || status == 403) {
            message="Embedding网关认证或权限失败";
        } else if (status == 404) {
            message="Embedding接口地址或模型部署名称错误";
        } else if (status == 400 || status == 422) {
            message="Embedding请求格式、输入长度或批量参数被拒绝";
        } else if (status == 429) {
            message="Embedding网关请求过于频繁，请稍后重试";
        } else {
            message="Embedding网关请求被拒绝, status=" + status;
        }
        String hint=safeErrorMetadata(ex.getResponseBodyAsString());
        return hint.isEmpty() ? message : message+"（网关错误信息："+hint+"）";
    }

    /** 额度错误可能以普通文本或 JSON 错误字段返回；只识别明确的额度耗尽措辞。 */
    private boolean isQuotaExceeded(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return false;
        }
        String normalized = responseBody.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
        return normalized.contains("quota exceeded")
                || normalized.contains("exceeded your current quota")
                || normalized.contains("insufficient quota")
                || normalized.contains("insufficient balance")
                || normalized.contains("billing limit")
                || normalized.contains("billing hard limit")
                || normalized.contains("exceeded the maximum amount")
                || normalized.contains("no remaining quota")
                || normalized.contains("out of quota")
                || normalized.contains("daily token limit exceeded")
                || normalized.contains("额度已超")
                || normalized.contains("额度不足")
                || normalized.contains("额度用尽")
                || normalized.contains("配额用尽")
                || normalized.contains("余额不足")
                || normalized.contains("超出额度")
                || normalized.contains("配额不足")
                || normalized.contains("配额超");
    }

    /** 只提取错误码、类型和参数名；不透出可能回显输入文本的网关错误正文。 */
    private String safeErrorMetadata(String responseBody) {
        if (responseBody==null || responseBody.trim().isEmpty()) return "";
        try {
            JsonNode root=ERROR_MAPPER.readTree(responseBody);
            JsonNode error=root.path("error").isObject() ? root.path("error") : root;
            String code=safeMetadataValue(error.path("code").asText());
            String type=safeMetadataValue(error.path("type").asText());
            String param=safeMetadataValue(error.path("param").asText());
            StringBuilder result=new StringBuilder();
            if (!code.isEmpty()) result.append("code=").append(code);
            if (!type.isEmpty()) {
                if (result.length()>0) result.append(", ");
                result.append("type=").append(type);
            }
            if (!param.isEmpty()) {
                if (result.length()>0) result.append(", ");
                result.append("param=").append(param);
            }
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeMetadataValue(String value) {
        if (value==null || value.isEmpty()) return "";
        String safe=value.replaceAll("[^A-Za-z0-9_.:\\[\\]-]", "");
        return safe.length()>100 ? safe.substring(0,100) : safe;
    }

    /** 将模型侧异常统一转换为服务不可用错误，同时避免透出响应体和敏感请求信息。 */
    private ContractChangeBusinessException unavailable(String message, Throwable cause) {
        String detail = cause == null ? message : message + ": " + cause.getClass().getSimpleName();
        return new ContractChangeBusinessException(CommonConstants.SERVICE_UNAVAILABLE, detail);
    }
}
