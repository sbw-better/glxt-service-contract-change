package com.citics.glxt.contractchange.embedding;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** 将内网 Embedding 容器可用性暴露到 Spring Boot Actuator。 */
@Component("embedding")
public class EmbeddingHealthIndicator implements HealthIndicator {
    private final EmbeddingClient embeddingClient;

    /** 注入统一的模型访问接口，避免健康检查重复实现 HTTP 调用。 */
    public EmbeddingHealthIndicator(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    /** 执行一次轻量健康请求，不发送任何合同文本。 */
    @Override
    public Health health() {
        return embeddingClient.isHealthy()
                ? Health.up().build()
                : Health.down().withDetail("reason", "Embedding服务不可用").build();
    }
}
