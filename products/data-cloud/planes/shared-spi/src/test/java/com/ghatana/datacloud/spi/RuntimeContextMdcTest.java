package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for RuntimeContextMdc.
 *
 * @doc.type class
 * @doc.purpose Test runtime context MDC functionality
 * @doc.layer shared-spi
 * @doc.pattern Test
 */
@DisplayName("RuntimeContextMdc Tests")
class RuntimeContextMdcTest {

    @Nested
    @DisplayName("Populate MDC")
    class PopulateMdc {

        @Test
        @DisplayName("Should populate MDC with correlation ID")
        void shouldPopulateMdcWithCorrelationId() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("corr-123", RuntimeContextMdc.getCorrelationId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with tenant ID")
        void shouldPopulateMdcWithTenantId() {
            RuntimeContext context = RuntimeContext.builder()
                .tenantId("tenant-456")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("tenant-456", RuntimeContextMdc.getTenantId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with surface")
        void shouldPopulateMdcWithSurface() {
            RuntimeContext context = RuntimeContext.builder()
                .surface("api")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("api", MDC.get("surface"));
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with run ID")
        void shouldPopulateMdcWithRunId() {
            RuntimeContext context = RuntimeContext.builder()
                .runId("run-789")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("run-789", RuntimeContextMdc.getRunId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with job ID")
        void shouldPopulateMdcWithJobId() {
            RuntimeContext context = RuntimeContext.builder()
                .jobId("job-012")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("job-012", RuntimeContextMdc.getJobId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with agent ID")
        void shouldPopulateMdcWithAgentId() {
            RuntimeContext context = RuntimeContext.builder()
                .agentId("agent-345")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("agent-345", RuntimeContextMdc.getAgentId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with pipeline ID")
        void shouldPopulateMdcWithPipelineId() {
            RuntimeContext context = RuntimeContext.builder()
                .pipelineId("pipeline-678")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("pipeline-678", RuntimeContextMdc.getPipelineId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with artifact ID")
        void shouldPopulateMdcWithArtifactId() {
            RuntimeContext context = RuntimeContext.builder()
                .artifactId("artifact-901")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("artifact-901", RuntimeContextMdc.getArtifactId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should populate MDC with all fields")
        void shouldPopulateMdcWithAllFields() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("api")
                .runId("run-789")
                .jobId("job-012")
                .agentId("agent-345")
                .pipelineId("pipeline-678")
                .artifactId("artifact-901")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertEquals("corr-123", RuntimeContextMdc.getCorrelationId());
            assertEquals("tenant-456", RuntimeContextMdc.getTenantId());
            assertEquals("run-789", RuntimeContextMdc.getRunId());
            assertEquals("job-012", RuntimeContextMdc.getJobId());
            assertEquals("agent-345", RuntimeContextMdc.getAgentId());
            assertEquals("pipeline-678", RuntimeContextMdc.getPipelineId());
            assertEquals("artifact-901", RuntimeContextMdc.getArtifactId());
            
            RuntimeContextMdc.clearMdc();
        }

        @Test
        @DisplayName("Should handle null context gracefully")
        void shouldHandleNullContextGracefully() {
            RuntimeContextMdc.populateMdc(null);
            
            // Should not throw exception
            assertNull(RuntimeContextMdc.getCorrelationId());
        }

        @Test
        @DisplayName("Should not populate MDC with blank values")
        void shouldNotPopulateMdcWithBlankValues() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("   ")
                .tenantId("")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertNull(RuntimeContextMdc.getCorrelationId());
            assertNull(RuntimeContextMdc.getTenantId());
            
            RuntimeContextMdc.clearMdc();
        }
    }

    @Nested
    @DisplayName("Clear MDC")
    class ClearMdc {

        @Test
        @DisplayName("Should clear all MDC keys")
        void shouldClearAllMdcKeys() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .runId("run-789")
                .build();
            
            RuntimeContextMdc.populateMdc(context);
            
            assertNotNull(RuntimeContextMdc.getCorrelationId());
            assertNotNull(RuntimeContextMdc.getTenantId());
            assertNotNull(RuntimeContextMdc.getRunId());
            
            RuntimeContextMdc.clearMdc();
            
            assertNull(RuntimeContextMdc.getCorrelationId());
            assertNull(RuntimeContextMdc.getTenantId());
            assertNull(RuntimeContextMdc.getRunId());
        }

        @Test
        @DisplayName("Should handle clearing empty MDC gracefully")
        void shouldHandleClearingEmptyMdcGracefully() {
            // Should not throw exception
            RuntimeContextMdc.clearMdc();
        }
    }

    @Nested
    @DisplayName("Mdc Scope")
    class MdcScope {

        @Test
        @DisplayName("Should auto-close and clear MDC")
        void shouldAutoCloseAndClearMdc() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .build();
            
            try (RuntimeContextMdc.MdcScope scope = RuntimeContextMdc.withContext(context)) {
                assertEquals("corr-123", RuntimeContextMdc.getCorrelationId());
                assertEquals("tenant-456", RuntimeContextMdc.getTenantId());
            }
            
            // MDC should be cleared after scope closes
            assertNull(RuntimeContextMdc.getCorrelationId());
            assertNull(RuntimeContextMdc.getTenantId());
        }

        @Test
        @DisplayName("Should handle null context in scope")
        void shouldHandleNullContextInScope() {
            try (RuntimeContextMdc.MdcScope scope = RuntimeContextMdc.withContext(null)) {
                // Should not throw exception
            }
            
            assertNull(RuntimeContextMdc.getCorrelationId());
        }

        @Test
        @DisplayName("Should handle nested scopes")
        void shouldHandleNestedScopes() {
            RuntimeContext context1 = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            RuntimeContext context2 = RuntimeContext.builder()
                .correlationId("corr-456")
                .build();
            
            try (RuntimeContextMdc.MdcScope scope1 = RuntimeContextMdc.withContext(context1)) {
                assertEquals("corr-123", RuntimeContextMdc.getCorrelationId());
                
                try (RuntimeContextMdc.MdcScope scope2 = RuntimeContextMdc.withContext(context2)) {
                    assertEquals("corr-456", RuntimeContextMdc.getCorrelationId());
                }
                
                // After inner scope closes, outer scope value should still be present
                // (This is expected behavior - inner scope doesn't restore outer scope)
                assertEquals("corr-456", RuntimeContextMdc.getCorrelationId());
            }
            
            // After outer scope closes, MDC should be cleared
            assertNull(RuntimeContextMdc.getCorrelationId());
        }
    }

    @Nested
    @DisplayName("Getter Methods")
    class GetterMethods {

        @Test
        @DisplayName("Should return null when MDC is empty")
        void shouldReturnNullWhenMdcEmpty() {
            RuntimeContextMdc.clearMdc();
            
            assertNull(RuntimeContextMdc.getCorrelationId());
            assertNull(RuntimeContextMdc.getTenantId());
            assertNull(RuntimeContextMdc.getRunId());
            assertNull(RuntimeContextMdc.getJobId());
            assertNull(RuntimeContextMdc.getAgentId());
            assertNull(RuntimeContextMdc.getPipelineId());
            assertNull(RuntimeContextMdc.getArtifactId());
        }

        @Test
        @DisplayName("Should return current correlation ID from MDC")
        void shouldReturnCurrentCorrelationIdFromMdc() {
            MDC.put("correlationId", "corr-123");
            
            assertEquals("corr-123", RuntimeContextMdc.getCorrelationId());
            
            MDC.remove("correlationId");
        }

        @Test
        @DisplayName("Should return current tenant ID from MDC")
        void shouldReturnCurrentTenantIdFromMdc() {
            MDC.put("tenantId", "tenant-456");
            
            assertEquals("tenant-456", RuntimeContextMdc.getTenantId());
            
            MDC.remove("tenantId");
        }

        @Test
        @DisplayName("Should return current run ID from MDC")
        void shouldReturnCurrentRunIdFromMdc() {
            MDC.put("runId", "run-789");
            
            assertEquals("run-789", RuntimeContextMdc.getRunId());
            
            MDC.remove("runId");
        }

        @Test
        @DisplayName("Should return current job ID from MDC")
        void shouldReturnCurrentJobIdFromMdc() {
            MDC.put("jobId", "job-012");
            
            assertEquals("job-012", RuntimeContextMdc.getJobId());
            
            MDC.remove("jobId");
        }

        @Test
        @DisplayName("Should return current agent ID from MDC")
        void shouldReturnCurrentAgentIdFromMdc() {
            MDC.put("agentId", "agent-345");
            
            assertEquals("agent-345", RuntimeContextMdc.getAgentId());
            
            MDC.remove("agentId");
        }

        @Test
        @DisplayName("Should return current pipeline ID from MDC")
        void shouldReturnCurrentPipelineIdFromMdc() {
            MDC.put("pipelineId", "pipeline-678");
            
            assertEquals("pipeline-678", RuntimeContextMdc.getPipelineId());
            
            MDC.remove("pipelineId");
        }

        @Test
        @DisplayName("Should return current artifact ID from MDC")
        void shouldReturnCurrentArtifactIdFromMdc() {
            MDC.put("artifactId", "artifact-901");
            
            assertEquals("artifact-901", RuntimeContextMdc.getArtifactId());
            
            MDC.remove("artifactId");
        }
    }
}
