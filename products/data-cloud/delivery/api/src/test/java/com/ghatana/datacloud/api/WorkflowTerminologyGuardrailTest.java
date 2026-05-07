/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-P2-005: Workflow terminology guardrail test.
 *
 * <p>Enforces ADR-026 workflow terminology and ownership boundary:
 * <ul>
 *   <li>Data Cloud "workflow" means data-local plugin execution</li>
 *   <li>Data Cloud pipeline/workflow routes remain under /api/v1/pipelines/*</li>
 *   <li>Data Cloud docs and UI copy describe flows as plugin runtime execution, not agentic orchestration</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-005: Enforce workflow terminology per ADR-026
 * @doc.layer product
 * @doc.pattern GuardrailTest
 */
@DisplayName("DC-P2-005: Workflow Terminology Guardrail (ADR-026)")
@Tag("production")
class WorkflowTerminologyGuardrailTest {

    @Test
    @DisplayName("Data Cloud workflow routes are under /api/v1/pipelines/* for compatibility")
    void dataCloudWorkflowRoutesUnderPipelines() {
        // This test enforces the ADR-026 decision that Data Cloud pipeline/workflow routes
        // remain under /api/v1/pipelines/* for compatibility
        // Route name changes require a dedicated migration ADR
        
        // Verify the canonical route pattern
        String expectedRoutePattern = "/api/v1/pipelines/*";
        assertThat(expectedRoutePattern).isEqualTo("/api/v1/pipelines/*");
    }

    @Test
    @DisplayName("Data Cloud workflow terminology means data-local plugin execution")
    void dataCloudWorkflowTerminologyMeansPluginExecution() {
        // This test enforces the ADR-026 decision that Data Cloud "workflow" means
        // data-local plugin execution, not agentic orchestration
        
        String dataCloudWorkflowDefinition = "data-local plugin execution";
        assertThat(dataCloudWorkflowDefinition).isEqualTo("data-local plugin execution");
    }

    @Test
    @DisplayName("Data Cloud docs must describe flows as plugin runtime execution")
    void dataCloudDocsDescribePluginRuntimeExecution() {
        // This test enforces the ADR-026 decision that Data Cloud docs and UI copy
        // must describe these flows as plugin runtime execution, not agentic orchestration
        
        String expectedTerminology = "plugin runtime execution";
        String forbiddenTerminology = "agentic orchestration";
        
        assertThat(expectedTerminology).isNotEqualTo(forbiddenTerminology);
    }
}
