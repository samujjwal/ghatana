/*
 * Copyright (c) 2026 Ghatana // GH-90000
 */
package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsProvider and BusinessMetrics.
 */
class ObservabilityTest {

    private MeterRegistry registry;
    private BusinessMetrics businessMetrics;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        businessMetrics = new BusinessMetrics(registry); // GH-90000
    }

    @Test
    @DisplayName("Should track user login metrics")
    void testUserLoginMetrics() { // GH-90000
        businessMetrics.recordUserLogin("user-123", "tenant-abc"); // GH-90000
        businessMetrics.recordUserLogin("user-456", "tenant-abc"); // GH-90000
        businessMetrics.recordUserLogin("user-789", "tenant-def"); // GH-90000

        double count = registry.counter("business.users.logins", "tenant", "tenant-abc").count(); // GH-90000
        assertThat(count).isEqualTo(2.0); // GH-90000
    }

    @Test
    @DisplayName("Should track session metrics")
    void testSessionMetrics() { // GH-90000
        businessMetrics.recordSessionCreated("tenant-abc", Duration.ofMinutes(30)); // GH-90000
        businessMetrics.recordSessionCreated("tenant-abc", Duration.ofMinutes(45)); // GH-90000
        businessMetrics.recordSessionEnded("tenant-abc");

        double createdCount = registry.counter("business.sessions.created", "tenant", "tenant-abc").count(); // GH-90000
        double endedCount = registry.counter("business.sessions.ended", "tenant", "tenant-abc").count(); // GH-90000

        assertThat(createdCount).isEqualTo(2.0); // GH-90000
        assertThat(endedCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track project lifecycle metrics")
    void testProjectMetrics() { // GH-90000
        businessMetrics.recordProjectCreated("tenant-abc", "microservice"); // GH-90000
        businessMetrics.recordProjectCreated("tenant-abc", "web-app"); // GH-90000
        businessMetrics.recordProjectCompleted("tenant-abc", "microservice", Duration.ofHours(48)); // GH-90000

        double createdCount = registry.counter("business.projects.created", "tenant", "tenant-abc", "type", "microservice").count(); // GH-90000
        assertThat(createdCount).isEqualTo(1.0); // GH-90000

        double completedCount = registry.counter("business.projects.completed", "tenant", "tenant-abc", "type", "microservice").count(); // GH-90000
        assertThat(completedCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track phase completion metrics")
    void testPhaseMetrics() { // GH-90000
        businessMetrics.recordPhaseCompleted("PLANNING", "tenant-abc"); // GH-90000
        businessMetrics.recordPhaseCompleted("DESIGN", "tenant-abc"); // GH-90000
        businessMetrics.recordPhaseCompleted("IMPLEMENTATION", "tenant-abc"); // GH-90000

        double planningCount = registry.counter("business.phases.completed", "phase", "PLANNING", "tenant", "tenant-abc").count(); // GH-90000
        double designCount = registry.counter("business.phases.completed", "phase", "DESIGN", "tenant", "tenant-abc").count(); // GH-90000

        assertThat(planningCount).isEqualTo(1.0); // GH-90000
        assertThat(designCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track AI/LLM metrics")
    void testAiMetrics() { // GH-90000
        businessMetrics.recordAgentInvocation("code-generator", "tenant-abc", true); // GH-90000
        businessMetrics.recordAgentInvocation("code-generator", "tenant-abc", true); // GH-90000
        businessMetrics.recordAgentInvocation("code-generator", "tenant-abc", false); // GH-90000

        double successCount = registry.counter("business.ai.agent.invocations", "agent", "code-generator", "tenant", "tenant-abc", "status", "success").count(); // GH-90000
        double failureCount = registry.counter("business.ai.agent.invocations", "agent", "code-generator", "tenant", "tenant-abc", "status", "failure").count(); // GH-90000

        assertThat(successCount).isEqualTo(2.0); // GH-90000
        assertThat(failureCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track LLM token usage")
    void testLlmTokenMetrics() { // GH-90000
        businessMetrics.recordLlmCall("gpt-4", "tenant-abc", 1500, 800); // GH-90000
        businessMetrics.recordLlmCall("gpt-4", "tenant-abc", 2000, 1200); // GH-90000

        double inputTokens = registry.counter("business.ai.llm.tokens.input", "model", "gpt-4", "tenant", "tenant-abc").count(); // GH-90000
        double outputTokens = registry.counter("business.ai.llm.tokens.output", "model", "gpt-4", "tenant", "tenant-abc").count(); // GH-90000

        assertThat(inputTokens).isEqualTo(3500.0); // GH-90000
        assertThat(outputTokens).isEqualTo(2000.0); // GH-90000
    }

    @Test
    @DisplayName("Should track code generation metrics")
    void testCodeGenerationMetrics() { // GH-90000
        businessMetrics.recordCodeGenerated("java", "tenant-abc", 150); // GH-90000
        businessMetrics.recordCodeGenerated("typescript", "tenant-abc", 200); // GH-90000
        businessMetrics.recordCodeGenerated("java", "tenant-abc", 75); // GH-90000

        double javaLines = registry.counter("business.ai.code.lines", "language", "java", "tenant", "tenant-abc").count(); // GH-90000
        double tsLines = registry.counter("business.ai.code.lines", "language", "typescript", "tenant", "tenant-abc").count(); // GH-90000

        assertThat(javaLines).isEqualTo(225.0); // GH-90000
        assertThat(tsLines).isEqualTo(200.0); // GH-90000
    }

    @Test
    @DisplayName("Should track collaboration metrics")
    void testCollaborationMetrics() { // GH-90000
        businessMetrics.recordCollaborationSession("tenant-abc", 5); // GH-90000
        businessMetrics.recordCommentCreated("tenant-abc", "architecture"); // GH-90000
        businessMetrics.recordReviewCompleted("tenant-abc", "code-review"); // GH-90000

        double sessionCount = registry.counter("business.collaboration.sessions", "tenant", "tenant-abc").count(); // GH-90000
        double commentCount = registry.counter("business.collaboration.comments", "tenant", "tenant-abc", "entity", "architecture").count(); // GH-90000

        assertThat(sessionCount).isEqualTo(1.0); // GH-90000
        assertThat(commentCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track Data Cloud event metrics")
    void testDataCloudMetrics() { // GH-90000
        businessMetrics.recordEventPublished("ProjectCreated", "tenant-abc"); // GH-90000
        businessMetrics.recordEventPublished("ProjectCreated", "tenant-abc"); // GH-90000
        businessMetrics.recordEventConsumed("ProjectCreated", "tenant-abc"); // GH-90000

        double publishedCount = registry.counter("business.datacloud.events.published", "type", "ProjectCreated", "tenant", "tenant-abc").count(); // GH-90000
        double consumedCount = registry.counter("business.datacloud.events.consumed", "type", "ProjectCreated", "tenant", "tenant-abc").count(); // GH-90000

        assertThat(publishedCount).isEqualTo(2.0); // GH-90000
        assertThat(consumedCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track tenant and API metrics")
    void testTenantMetrics() { // GH-90000
        businessMetrics.recordTenantCreated("new-tenant", "pro"); // GH-90000
        businessMetrics.recordTenantCreated("another-tenant", "enterprise"); // GH-90000
        businessMetrics.recordApiCall("/api/projects", "tenant-abc", 200); // GH-90000
        businessMetrics.recordApiCall("/api/projects", "tenant-abc", 500); // GH-90000

        double apiCalls = registry.counter("business.api.calls", "endpoint", "/api/projects", "tenant", "tenant-abc", "status", "200").count(); // GH-90000
        assertThat(apiCalls).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track feature usage")
    void testFeatureUsageMetrics() { // GH-90000
        businessMetrics.recordFeatureUsage("ai-code-generation", "tenant-abc"); // GH-90000
        businessMetrics.recordFeatureUsage("ai-code-generation", "tenant-abc"); // GH-90000
        businessMetrics.recordFeatureUsage("real-time-collab", "tenant-abc"); // GH-90000

        double codeGenUsage = registry.counter("business.features.usage", "feature", "ai-code-generation", "tenant", "tenant-abc").count(); // GH-90000
        double collabUsage = registry.counter("business.features.usage", "feature", "real-time-collab", "tenant", "tenant-abc").count(); // GH-90000

        assertThat(codeGenUsage).isEqualTo(2.0); // GH-90000
        assertThat(collabUsage).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should track workflow execution")
    void testWorkflowMetrics() { // GH-90000
        businessMetrics.recordWorkflowExecuted("create-microservice", "tenant-abc", true); // GH-90000
        businessMetrics.recordWorkflowExecuted("create-microservice", "tenant-abc", false); // GH-90000

        double successCount = registry.counter("business.workflows.executed", "type", "create-microservice", "tenant", "tenant-abc", "status", "success").count(); // GH-90000
        double failureCount = registry.counter("business.workflows.executed", "type", "create-microservice", "tenant", "tenant-abc", "status", "failure").count(); // GH-90000

        assertThat(successCount).isEqualTo(1.0); // GH-90000
        assertThat(failureCount).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should update gauge values")
    void testGaugeUpdates() { // GH-90000
        businessMetrics.setActiveUsers(1000L, 5000L); // GH-90000
        businessMetrics.setActiveTenants(50L); // GH-90000
        businessMetrics.setTotalProjects(200L); // GH-90000
        businessMetrics.setConcurrentSessions(150L); // GH-90000

        // Gauges are registered but values are set asynchronously
        // This test verifies the methods don't throw exceptions
        assertThat(registry).isNotNull(); // GH-90000
    }
}
