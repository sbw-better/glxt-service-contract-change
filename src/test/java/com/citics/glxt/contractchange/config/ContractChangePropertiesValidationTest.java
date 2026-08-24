package com.citics.glxt.contractchange.config;

import org.junit.Test;

import javax.validation.Validation;
import javax.validation.Validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 验证错误阈值、批量和Top-K配置会在应用启动阶段被拒绝。 */
public class ContractChangePropertiesValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void shouldAcceptCompleteGatewayConfiguration() {
        ContractChangeProperties properties = validProperties();
        assertTrue(validator.validate(properties).isEmpty());
        assertTrue(properties.getEmbedding().getBatchSize() == 1);
    }

    @Test
    public void shouldRejectInvalidBatchAndTopKCombination() {
        ContractChangeProperties properties = validProperties();
        properties.getEmbedding().setBatchSize(0);
        properties.getSearch().setRetrieveTopK(5);
        properties.getSearch().setVoteTopK(10);

        assertFalse(validator.validate(properties).isEmpty());
    }

    /** 模型平台未分配关键参数时必须阻止应用启动，避免误用旧模型默认值。 */
    @Test
    public void shouldRejectMissingGatewayConfiguration() {
        assertFalse(validator.validate(new ContractChangeProperties()).isEmpty());
    }

    /** 配置对象被诊断输出时不得包含模型平台API Key。 */
    @Test
    public void shouldHideApiKeyFromToString() {
        ContractChangeProperties properties = validProperties();
        assertFalse(properties.getEmbedding().toString().contains("test-key"));
    }

    private ContractChangeProperties validProperties() {
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setUrl("http://model/embedding/v1/embeddings");
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModelName("test-model");
        properties.getEmbedding().setModelVersion("test-model-v1");
        properties.getEmbedding().setDimension(3);
        return properties;
    }
}
