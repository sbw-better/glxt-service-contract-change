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
    public void shouldAcceptFirstVersionDefaults() {
        assertTrue(validator.validate(new ContractChangeProperties()).isEmpty());
    }

    @Test
    public void shouldRejectInvalidBatchAndTopKCombination() {
        ContractChangeProperties properties = new ContractChangeProperties();
        properties.getEmbedding().setBatchSize(0);
        properties.getSearch().setRetrieveTopK(5);
        properties.getSearch().setVoteTopK(10);

        assertFalse(validator.validate(properties).isEmpty());
    }
}
