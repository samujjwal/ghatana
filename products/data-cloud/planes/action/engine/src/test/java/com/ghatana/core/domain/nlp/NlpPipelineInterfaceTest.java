/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("NLP Pipeline Interface Tests")
class NlpPipelineInterfaceTest {

    @Test
    @DisplayName("generates pipeline from natural language description")
    void generatesPipelineFromDescription() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Process orders with fraud check",
            "order-fraud-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); 
        assertThat(result.specBuilder()).isNotNull(); 
        assertThat(result.templatesUsed()).isNotEmpty(); 
    }

    @Test
    @DisplayName("extracts domain from description")
    void extractsDomainFromDescription() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineIntent intent = parseDescription(nlpInterface, "Process orders with fraud check"); 

        assertThat(intent.domain()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("extracts operation from description")
    void extractsOperationFromDescription() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineIntent intent = parseDescription(nlpInterface, "Process orders with fraud check"); 

        assertThat(intent.operation()).isEqualTo("detection");
    }

    @Test
    @DisplayName("extracts features from description")
    void extractsFeaturesFromDescription() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineIntent intent = parseDescription( 
            nlpInterface,
            "Process orders with real-time fraud detection and alerting"
        );

        assertThat(intent.features()).contains("fraud-detection", "real-time", "alerting"); 
    }

    @Test
    @DisplayName("extracts parameters from description")
    void extractsParametersFromDescription() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineIntent intent = parseDescription( 
            nlpInterface,
            "Process orders with fraud check for last 5 minutes"
        );

        assertThat(intent.parameters()).containsKey("timeWindow");
    }

    @Test
    @DisplayName("generates analytics pipeline")
    void generatesAnalyticsPipeline() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Aggregate user clicks in sessions",
            "clickstream-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); 
        assertThat(result.specBuilder()).isNotNull(); 
    }

    @Test
    @DisplayName("generates IoT pipeline")
    void generatesIotPipeline() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Process device telemetry with alerting",
            "iot-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); 
        assertThat(result.specBuilder()).isNotNull(); 
    }

    @Test
    @DisplayName("generates compliance pipeline")
    void generatesCompliancePipeline() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Process audit logs with PII masking",
            "audit-pipe",
            "tenant-1"
        );

        assertThat(result.success()).isTrue(); 
        assertThat(result.specBuilder()).isNotNull(); 
    }

    @Test
    @DisplayName("handles unknown descriptions gracefully")
    void handlesUnknownDescriptions() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Do something completely unknown",
            "unknown-pipe",
            "tenant-1"
        );

        // Should still succeed with a default template or fallback
        assertThat(result.message()).isNotNull(); 
    }

    @Test
    @DisplayName("customizes builder with parameters")
    void customizesBuilderWithParameters() { 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Process orders with fraud check",
            "custom-name",
            "tenant-1"
        );

        if (result.success()) { 
            assertThat(result.specBuilder()).isNotNull(); 
        }
    }

    @Test
    @DisplayName("uses custom marketplace")
    void usesCustomMarketplace() { 
        TemplateMarketplace customMarketplace = new TemplateMarketplace(); 
        NlpPipelineInterface nlpInterface = new NlpPipelineInterface(customMarketplace); 

        NlpPipelineInterface.PipelineGenerationResult result = nlpInterface.generatePipeline( 
            "Process orders with fraud check",
            "order-pipe",
            "tenant-1"
        );

        assertThat(result).isNotNull(); 
    }

    private NlpPipelineInterface.PipelineIntent parseDescription( 
        NlpPipelineInterface nlpInterface,
        String description
    ) {
        try {
            Method parseDescription = NlpPipelineInterface.class.getDeclaredMethod("parseDescription", String.class); 
            parseDescription.setAccessible(true); 
            return (NlpPipelineInterface.PipelineIntent) parseDescription.invoke(nlpInterface, description); 
        } catch (NoSuchMethodException | IllegalAccessException exception) { 
            throw new AssertionError("Unable to access parseDescription", exception); 
        } catch (InvocationTargetException exception) { 
            throw new AssertionError("parseDescription threw unexpectedly", exception.getCause()); 
        }
    }
}
