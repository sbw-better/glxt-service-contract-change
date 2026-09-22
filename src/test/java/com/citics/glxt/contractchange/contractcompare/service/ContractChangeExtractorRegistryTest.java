package com.citics.glxt.contractchange.contractcompare.service;

import com.citics.glxt.contractchange.common.exception.ContractChangeBusinessException;
import com.citics.glxt.contractchange.contractcompare.model.ContractCompareRequest.AnalysisType;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractor;
import com.citics.glxt.contractchange.contractcompare.service.extractor.ContractChangeExtractorRegistry;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContractChangeExtractorRegistryTest {
    @Test
    public void shouldRouteByAnalysisType() {
        ContractChangeExtractor doubleVersion = extractor(AnalysisType.DOUBLE_VERSION);
        ContractChangeExtractor changeDocument = extractor(AnalysisType.CHANGE_DOCUMENT);
        ContractChangeExtractorRegistry registry = new ContractChangeExtractorRegistry(
                Arrays.asList(doubleVersion, changeDocument));

        assertSame(doubleVersion, registry.get(AnalysisType.DOUBLE_VERSION));
        assertSame(changeDocument, registry.get(AnalysisType.CHANGE_DOCUMENT));
    }

    @Test
    public void shouldRejectDuplicateStrategiesAtStartup() {
        try {
            new ContractChangeExtractorRegistry(Arrays.asList(
                    extractor(AnalysisType.DOUBLE_VERSION),
                    extractor(AnalysisType.DOUBLE_VERSION)));
            fail("duplicate strategy should fail");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void shouldRejectAnalysisTypeWithoutStrategy() {
        ContractChangeExtractorRegistry registry = new ContractChangeExtractorRegistry(
                Collections.singletonList(extractor(AnalysisType.DOUBLE_VERSION)));
        try {
            registry.get(AnalysisType.CHANGE_DOCUMENT);
            fail("missing strategy should fail");
        } catch (ContractChangeBusinessException expected) {
            // expected
        }
    }

    private ContractChangeExtractor extractor(AnalysisType type) {
        ContractChangeExtractor extractor = mock(ContractChangeExtractor.class);
        when(extractor.supportType()).thenReturn(type);
        return extractor;
    }
}
