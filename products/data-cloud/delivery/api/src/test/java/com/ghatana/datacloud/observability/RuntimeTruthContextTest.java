package com.ghatana.datacloud.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RuntimeTruthContext.
 *
 * @doc.type class
 * @doc.purpose Runtime truth context tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RuntimeTruthContext Tests")
class RuntimeTruthContextTest {

    @Test
    @DisplayName("Should build context with all identifiers")
    void shouldBuildContextWithAllIdentifiers() {
        var context = RuntimeTruthContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("api")
                .runId("run-789")
                .jobId("job-abc")
                .agentId("agent-def")
                .pipelineId("pipeline-ghi")
                .artifactId("artifact-jkl")
                .build();
        
        assertThat(context.correlationId()).isEqualTo("corr-123");
        assertThat(context.tenantId()).isEqualTo("tenant-456");
        assertThat(context.surface()).isEqualTo("api");
        assertThat(context.runId()).isEqualTo("run-789");
        assertThat(context.jobId()).isEqualTo("job-abc");
        assertThat(context.agentId()).isEqualTo("agent-def");
        assertThat(context.pipelineId()).isEqualTo("pipeline-ghi");
        assertThat(context.artifactId()).isEqualTo("artifact-jkl");
    }

    @Test
    @DisplayName("Should generate correlation ID if not provided")
    void shouldGenerateCorrelationIdIfNotProvided() {
        var context = RuntimeTruthContext.builder()
                .tenantId("tenant-456")
                .surface("api")
                .build();
        
        assertThat(context.correlationId()).isNotNull();
        assertThat(context.correlationId()).isNotEmpty();
    }

    @Test
    @DisplayName("Should convert to MDC map")
    void shouldConvertToMdcMap() {
        var context = RuntimeTruthContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("api")
                .runId("run-789")
                .build();
        
        var mdc = context.toMdc();
        assertThat(mdc).containsEntry("correlationId", "corr-123");
        assertThat(mdc).containsEntry("tenantId", "tenant-456");
        assertThat(mdc).containsEntry("surface", "api");
        assertThat(mdc).containsEntry("runId", "run-789");
    }

    @Test
    @DisplayName("Should convert to HTTP headers")
    void shouldConvertToHttpHeaders() {
        var context = RuntimeTruthContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("api")
                .runId("run-789")
                .build();
        
        var headers = context.toHttpHeaders();
        assertThat(headers).containsEntry("X-Correlation-ID", "corr-123");
        assertThat(headers).containsEntry("X-Tenant-ID", "tenant-456");
        assertThat(headers).containsEntry("X-Surface", "api");
        assertThat(headers).containsEntry("X-Run-ID", "run-789");
    }

    @Test
    @DisplayName("Should create context from HTTP headers")
    void shouldCreateContextFromHttpHeaders() {
        var headers = Map.of(
                "X-Correlation-ID", "corr-123",
                "X-Tenant-ID", "tenant-456",
                "X-Surface", "api",
                "X-Run-ID", "run-789"
        );
        
        var context = RuntimeTruthContext.fromHttpHeaders(headers);
        assertThat(context.correlationId()).isEqualTo("corr-123");
        assertThat(context.tenantId()).isEqualTo("tenant-456");
        assertThat(context.surface()).isEqualTo("api");
        assertThat(context.runId()).isEqualTo("run-789");
    }

    @Test
    @DisplayName("Should create child context")
    void shouldCreateChildContext() {
        var parent = RuntimeTruthContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("api")
                .build();
        
        var child = parent.createChild();
        assertThat(child.correlationId()).isEqualTo(parent.correlationId());
        assertThat(child.tenantId()).isEqualTo(parent.tenantId());
        assertThat(child.surface()).isEqualTo(parent.surface());
    }

    @Test
    @DisplayName("Should add metadata to context")
    void shouldAddMetadataToContext() {
        var context = RuntimeTruthContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .build();
        
        var updated = context.withMetadata("custom-key", "custom-value");
        assertThat(updated.additionalMetadata()).containsEntry("custom-key", "custom-value");
    }

    @Test
    @DisplayName("Should generate unique correlation IDs")
    void shouldGenerateUniqueCorrelationIds() {
        var id1 = RuntimeTruthContext.generateCorrelationId();
        var id2 = RuntimeTruthContext.generateCorrelationId();
        
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Should generate unique run IDs")
    void shouldGenerateUniqueRunIds() {
        var id1 = RuntimeTruthContext.generateRunId();
        var id2 = RuntimeTruthContext.generateRunId();
        
        assertThat(id1).isNotEqualTo(id2);
    }
}
