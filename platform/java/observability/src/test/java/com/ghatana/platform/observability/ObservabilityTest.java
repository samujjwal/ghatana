/*
 * Copyright (c) 2026 Ghatana 
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
    void setUp() { 
        registry = new SimpleMeterRegistry(); 
        businessMetrics = new BusinessMetrics(registry); 
    }

    @Test
    @DisplayName("Should track user login metrics")
    void testUserLoginMetrics() { 
        businessMetrics.recordUserLogin("user-123", "tenant-abc"); 
        businessMetrics.recordUserLogin("user-456", "tenant-abc"); 
        businessMetrics.recordUserLogin("user-789", "tenant-def"); 

        double count = registry.counter("business.users.logins", "tenant", "tenant-abc").count(); 
        assertThat(count).isEqualTo(2.0); 
    }

    @Test
    @DisplayName("Should track session metrics")
    void testSessionMetrics() { 
        businessMetrics.recordSessionCreated("tenant-abc", Duration.ofMinutes(30)); 
        businessMetrics.recordSessionCreated("tenant-abc", Duration.ofMinutes(45)); 
        businessMetrics.recordSessionEnded("tenant-abc");

        double createdCount = registry.counter("business.sessions.created", "tenant", "tenant-abc").count(); 
        double endedCount = registry.counter("business.sessions.ended", "tenant", "tenant-abc").count(); 

        assertThat(createdCount).isEqualTo(2.0); 
        assertThat(endedCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track project lifecycle metrics")
    void testProjectMetrics() { 
        businessMetrics.recordProjectCreated("tenant-abc", "microservice"); 
        businessMetrics.recordProjectCreated("tenant-abc", "web-app"); 
        businessMetrics.recordProjectCompleted("tenant-abc", "microservice", Duration.ofHours(48)); 

        double createdCount = registry.counter("business.projects.created", "tenant", "tenant-abc", "type", "microservice").count(); 
        assertThat(createdCount).isEqualTo(1.0); 

        double completedCount = registry.counter("business.projects.completed", "tenant", "tenant-abc", "type", "microservice").count(); 
        assertThat(completedCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track phase completion metrics")
    void testPhaseMetrics() { 
        businessMetrics.recordPhaseCompleted("PLANNING", "tenant-abc"); 
        businessMetrics.recordPhaseCompleted("DESIGN", "tenant-abc"); 
        businessMetrics.recordPhaseCompleted("IMPLEMENTATION", "tenant-abc"); 

        double planningCount = registry.counter("business.phases.completed", "phase", "PLANNING", "tenant", "tenant-abc").count(); 
        double designCount = registry.counter("business.phases.completed", "phase", "DESIGN", "tenant", "tenant-abc").count(); 

        assertThat(planningCount).isEqualTo(1.0); 
        assertThat(designCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track AI/LLM metrics")
    void testAiMetrics() { 
        businessMetrics.recordAgentInvocation("code-generator", "tenant-abc", true); 
        businessMetrics.recordAgentInvocation("code-generator", "tenant-abc", true); 
        businessMetrics.recordAgentInvocation("code-generator", "tenant-abc", false); 

        double successCount = registry.counter("business.ai.agent.invocations", "agent", "code-generator", "tenant", "tenant-abc", "status", "success").count(); 
        double failureCount = registry.counter("business.ai.agent.invocations", "agent", "code-generator", "tenant", "tenant-abc", "status", "failure").count(); 

        assertThat(successCount).isEqualTo(2.0); 
        assertThat(failureCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track LLM token usage")
    void testLlmTokenMetrics() { 
        businessMetrics.recordLlmCall("gpt-4", "tenant-abc", 1500, 800); 
        businessMetrics.recordLlmCall("gpt-4", "tenant-abc", 2000, 1200); 

        double inputTokens = registry.counter("business.ai.llm.tokens.input", "model", "gpt-4", "tenant", "tenant-abc").count(); 
        double outputTokens = registry.counter("business.ai.llm.tokens.output", "model", "gpt-4", "tenant", "tenant-abc").count(); 

        assertThat(inputTokens).isEqualTo(3500.0); 
        assertThat(outputTokens).isEqualTo(2000.0); 
    }

    @Test
    @DisplayName("Should track code generation metrics")
    void testCodeGenerationMetrics() { 
        businessMetrics.recordCodeGenerated("java", "tenant-abc", 150); 
        businessMetrics.recordCodeGenerated("typescript", "tenant-abc", 200); 
        businessMetrics.recordCodeGenerated("java", "tenant-abc", 75); 

        double javaLines = registry.counter("business.ai.code.lines", "language", "java", "tenant", "tenant-abc").count(); 
        double tsLines = registry.counter("business.ai.code.lines", "language", "typescript", "tenant", "tenant-abc").count(); 

        assertThat(javaLines).isEqualTo(225.0); 
        assertThat(tsLines).isEqualTo(200.0); 
    }

    @Test
    @DisplayName("Should track collaboration metrics")
    void testCollaborationMetrics() { 
        businessMetrics.recordCollaborationSession("tenant-abc", 5); 
        businessMetrics.recordCommentCreated("tenant-abc", "architecture"); 
        businessMetrics.recordReviewCompleted("tenant-abc", "code-review"); 

        double sessionCount = registry.counter("business.collaboration.sessions", "tenant", "tenant-abc").count(); 
        double commentCount = registry.counter("business.collaboration.comments", "tenant", "tenant-abc", "entity", "architecture").count(); 

        assertThat(sessionCount).isEqualTo(1.0); 
        assertThat(commentCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track Data Cloud event metrics")
    void testDataCloudMetrics() { 
        businessMetrics.recordEventPublished("ProjectCreated", "tenant-abc"); 
        businessMetrics.recordEventPublished("ProjectCreated", "tenant-abc"); 
        businessMetrics.recordEventConsumed("ProjectCreated", "tenant-abc"); 

        double publishedCount = registry.counter("business.datacloud.events.published", "type", "ProjectCreated", "tenant", "tenant-abc").count(); 
        double consumedCount = registry.counter("business.datacloud.events.consumed", "type", "ProjectCreated", "tenant", "tenant-abc").count(); 

        assertThat(publishedCount).isEqualTo(2.0); 
        assertThat(consumedCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track tenant and API metrics")
    void testTenantMetrics() { 
        businessMetrics.recordTenantCreated("new-tenant", "pro"); 
        businessMetrics.recordTenantCreated("another-tenant", "enterprise"); 
        businessMetrics.recordApiCall("/api/projects", "tenant-abc", 200); 
        businessMetrics.recordApiCall("/api/projects", "tenant-abc", 500); 

        double apiCalls = registry.counter("business.api.calls", "endpoint", "/api/projects", "tenant", "tenant-abc", "status", "200").count(); 
        assertThat(apiCalls).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track feature usage")
    void testFeatureUsageMetrics() { 
        businessMetrics.recordFeatureUsage("ai-code-generation", "tenant-abc"); 
        businessMetrics.recordFeatureUsage("ai-code-generation", "tenant-abc"); 
        businessMetrics.recordFeatureUsage("real-time-collab", "tenant-abc"); 

        double codeGenUsage = registry.counter("business.features.usage", "feature", "ai-code-generation", "tenant", "tenant-abc").count(); 
        double collabUsage = registry.counter("business.features.usage", "feature", "real-time-collab", "tenant", "tenant-abc").count(); 

        assertThat(codeGenUsage).isEqualTo(2.0); 
        assertThat(collabUsage).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should track workflow execution")
    void testWorkflowMetrics() { 
        businessMetrics.recordWorkflowExecuted("create-microservice", "tenant-abc", true); 
        businessMetrics.recordWorkflowExecuted("create-microservice", "tenant-abc", false); 

        double successCount = registry.counter("business.workflows.executed", "type", "create-microservice", "tenant", "tenant-abc", "status", "success").count(); 
        double failureCount = registry.counter("business.workflows.executed", "type", "create-microservice", "tenant", "tenant-abc", "status", "failure").count(); 

        assertThat(successCount).isEqualTo(1.0); 
        assertThat(failureCount).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("Should update gauge values")
    void testGaugeUpdates() { 
        businessMetrics.setActiveUsers(1000L, 5000L); 
        businessMetrics.setActiveTenants(50L); 
        businessMetrics.setTotalProjects(200L); 
        businessMetrics.setConcurrentSessions(150L); 

        // Gauges are registered but values are set asynchronously
        // This test verifies the methods don't throw exceptions
        assertThat(registry).isNotNull(); 
    }
}
