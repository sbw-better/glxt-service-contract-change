package com.citics.glxt.contractchange.service;

import com.citics.glxt.contractchange.model.IndexStatusResponse;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** 将Oracle历史样本内存索引状态暴露到Actuator，避免加载失败时服务仍显示完全健康。 */
@Component("contractParagraphIndex")
public class IndexHealthIndicator implements HealthIndicator {
    private final ParagraphVectorIndexService indexService;

    /** 注入当前实际提供预测查询的索引服务。 */
    public IndexHealthIndicator(ParagraphVectorIndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * 未就绪、加载失败以及没有任何可用记录的降级索引返回DOWN；
     * 正常空库属于已成功加载的业务状态，因此健康检查仍返回UP。
     */
    @Override
    public Health health() {
        IndexStatusResponse status = indexService.status();
        boolean unavailable = "NOT_READY".equals(status.getStatus())
                || "LOAD_FAILED".equals(status.getStatus())
                || ("DEGRADED".equals(status.getStatus()) && status.getSampleCount() == 0);
        Health.Builder builder = unavailable ? Health.down() : Health.up();
        return builder.withDetail("status", status.getStatus())
                .withDetail("sampleCount", status.getSampleCount())
                .withDetail("errorCount", status.getErrorCount())
                .withDetail("modelVersion", status.getModelVersion())
                .build();
    }
}
