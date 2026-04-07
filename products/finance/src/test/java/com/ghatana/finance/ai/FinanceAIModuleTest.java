package com.ghatana.finance.ai;

import com.ghatana.agent.spi.AgentLogicProvider;
import io.activej.inject.Injector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies finance AI module wiring for shared fraud inference dependencies
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceAIModuleTest {

    @Test
    void sharesModelRepositoryAcrossProviderAndInferenceBindings() throws Exception {
        Injector injector = Injector.of(FinanceAIModule.create());

        ModelRepository modelRepository = injector.getInstance(ModelRepository.class);
        AgentLogicProvider provider = injector.getInstance(AgentLogicProvider.class);
        FraudModelInferenceService inferenceService = injector.getInstance(FraudModelInferenceService.class);
        FinanceFraudDetectionKernelAgent kernelAgent = injector.getInstance(FinanceFraudDetectionKernelAgent.class);

        Field providerRepository = FinanceAgentLogicProvider.class.getDeclaredField("modelRepository");
        providerRepository.setAccessible(true);
        assertSame(modelRepository, providerRepository.get(provider));

        Field inferenceRepository = DefaultFraudModelInferenceService.class.getDeclaredField("modelRepository");
        inferenceRepository.setAccessible(true);
        assertSame(modelRepository, inferenceRepository.get(inferenceService));

        Field kernelInference = FinanceFraudDetectionKernelAgent.class.getDeclaredField("inferenceService");
        kernelInference.setAccessible(true);
        assertSame(inferenceService, kernelInference.get(kernelAgent));

        assertTrue(provider.getSupportedRefs().contains("finance:fraud-detection"));
    }
}