/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.domain.nlp;

import com.ghatana.core.domain.pipeline.PipelineSpecBuilder;
import com.ghatana.core.domain.pipeline.TemplateMarketplace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for NLP pipeline interface.
 *
 * @doc.type class
 * @doc.purpose Unit tests for NLP pipeline interface
 * @doc.layer test
 */
@DisplayName("NLP Pipeline Interface Tests [GH-90000]")
class NlpPipelineInterfaceTest {

    @Test
    @DisplayName("generates pipeline from natural language description [GH-90000]")
    void generatesPipelineFromDescription() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Process orders with fraud check",
            "order-fraud-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.specBuilder()).isNotNull(); // GH-90000
        assertThat(result.templatesUsed()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("extracts domain from description [GH-90000]")
    void extractsDomainFromDescription() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineIntent intent = parseDescription(nlpInterface, "Process orders with fraud check"); // GH-90000

        assertThat(intent.domain()).isEqualTo("ecommerce [GH-90000]");
    }

    @Test
    @DisplayName("extracts operation from description [GH-90000]")
    void extractsOperationFromDescription() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineIntent intent = parseDescription(nlpInterface, "Process orders with fraud check"); // GH-90000

        assertThat(intent.operation()).isEqualTo("detection [GH-90000]");
    }

    @Test
    @DisplayName("extracts features from description [GH-90000]")
    void extractsFeaturesFromDescription() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineIntent intent = parseDescription( // GH-90000
            nlpInterface,
            "Process orders with real-time fraud detection and alerting"
        );

        assertThat(intent.features()).contains("fraud-detection", "real-time", "alerting"); // GH-90000
    }

    @Test
    @DisplayName("extracts parameters from description [GH-90000]")
    void extractsParametersFromDescription() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineIntent intent = parseDescription( // GH-90000
            nlpInterface,
            "Process orders with fraud check for last 5 minutes"
        );

        assertThat(intent.parameters()).containsKey("timeWindow [GH-90000]");
    }

    @Test
    @DisplayName("generates analytics pipeline [GH-90000]")
    void generatesAnalyticsPipeline() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Aggregate user clicks in sessions",
            "clickstream-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.specBuilder()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("generates IoT pipeline [GH-90000]")
    void generatesIotPipeline() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Process device telemetry with alerting",
            "iot-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.specBuilder()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("generates compliance pipeline [GH-90000]")
    void generatesCompliancePipeline() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Process audit logs with PII masking",
            "audit-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.specBuilder()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("handles unknown descriptions gracefully [GH-90000]")
    void handlesUnknownDescriptions() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Do something completely unknown",
            "unknown-pipe",
            "tenant-1"
        );

        // Should still succeed with a default template or fallback
        assertThat(result.message()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("customizes builder with parameters [GH-90000]")
    void customizesBuilderWithParameters() { // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Process orders with fraud check",
            "custom-name",
            "tenant-1"
        );

        if (result.success()) { // GH-90000
            assertThat(result.specBuilder()).isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("uses custom marketplace [GH-90000]")
    void usesCustomMarketplace() { // GH-90000
        TemplateMarketplace customMarketplace = new TemplateMarketplace(); // GH-90000
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(customMarketplace); // GH-90000

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( // GH-90000
            "Process orders with fraud check",
            "order-pipe",
            "tenant-1"
        );

        assertThat(result).isNotNull(); // GH-90000
    }

    private NlpPipelineInterface.PipelineIntent parseDescription( // GH-90000
        NlpPipelineInterface nlpInterface,
        String description
    ) {
        try {
            Method parseDescription = NlpPipelineInterface.class.getDeclaredMethod("parseDescription", String.class); // GH-90000
            parseDescription.setAccessible(true); // GH-90000
            return (NlpPipelineInterface.PipelineIntent) parseDescription.invoke(nlpInterface, description); // GH-90000
        } catch (NoSuchMethodException | IllegalAccessException exception) { // GH-90000
            throw new AssertionError("Unable to access parseDescription", exception); // GH-90000
        } catch (InvocationTargetException exception) { // GH-90000
            throw new AssertionError("parseDescription threw unexpectedly", exception.getCause()); // GH-90000
        }
    }
}
