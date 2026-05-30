package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for RuntimeContext.
 *
 * @doc.type class
 * @doc.purpose Test runtime context functionality
 * @doc.layer shared-spi
 * @doc.pattern Test
 */
@DisplayName("RuntimeContext Tests")
class RuntimeContextTest {

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("Should build empty context")
        void shouldBuildEmptyContext() {
            RuntimeContext context = RuntimeContext.builder().build();
            
            assertNull(context.getCorrelationId());
            assertNull(context.getTenantId());
            assertNull(context.getSurface());
            assertNull(context.getRunId());
            assertNull(context.getJobId());
            assertNull(context.getAgentId());
            assertNull(context.getPipelineId());
            assertNull(context.getArtifactId());
            assertTrue(context.getAdditionalContext().isEmpty());
        }

        @Test
        @DisplayName("Should build context with all fields")
        void shouldBuildContextWithAllFields() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("api")
                .runId("run-789")
                .jobId("job-012")
                .agentId("agent-345")
                .pipelineId("pipeline-678")
                .artifactId("artifact-901")
                .additionalContext("custom-key", "custom-value")
                .build();
            
            assertEquals("corr-123", context.getCorrelationId());
            assertEquals("tenant-456", context.getTenantId());
            assertEquals("api", context.getSurface());
            assertEquals("run-789", context.getRunId());
            assertEquals("job-012", context.getJobId());
            assertEquals("agent-345", context.getAgentId());
            assertEquals("pipeline-678", context.getPipelineId());
            assertEquals("artifact-901", context.getArtifactId());
            assertEquals("custom-value", context.get("custom-key"));
        }

        @Test
        @DisplayName("Should build context with additional context map")
        void shouldBuildContextWithAdditionalContextMap() {
            Map<String, String> customContext = Map.of("key1", "value1", "key2", "value2");
            
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .additionalContext(customContext)
                .build();
            
            assertEquals("value1", context.get("key1"));
            assertEquals("value2", context.get("key2"));
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("Should create empty context")
        void shouldCreateEmptyContext() {
            RuntimeContext context = RuntimeContext.empty();
            
            assertNull(context.getCorrelationId());
            assertNull(context.getTenantId());
        }

        @Test
        @DisplayName("Should create context from correlation ID")
        void shouldCreateContextFromCorrelationId() {
            RuntimeContext context = RuntimeContext.fromCorrelationId("corr-123");
            
            assertEquals("corr-123", context.getCorrelationId());
            assertNull(context.getTenantId());
        }

        @Test
        @DisplayName("Should create context from tenant ID")
        void shouldCreateContextFromTenantId() {
            RuntimeContext context = RuntimeContext.fromTenantId("tenant-456");
            
            assertNull(context.getCorrelationId());
            assertEquals("tenant-456", context.getTenantId());
        }
    }

    @Nested
    @DisplayName("With Methods")
    class WithMethods {

        @Test
        @DisplayName("Should create new context with correlation ID")
        void shouldCreateNewContextWithCorrelationId() {
            RuntimeContext original = RuntimeContext.builder()
                .tenantId("tenant-456")
                .build();
            
            RuntimeContext updated = original.withCorrelationId("corr-123");
            
            assertEquals("corr-123", updated.getCorrelationId());
            assertEquals("tenant-456", updated.getTenantId());
            assertNull(original.getCorrelationId());
        }

        @Test
        @DisplayName("Should create new context with tenant ID")
        void shouldCreateNewContextWithTenantId() {
            RuntimeContext original = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            RuntimeContext updated = original.withTenantId("tenant-456");
            
            assertEquals("corr-123", updated.getCorrelationId());
            assertEquals("tenant-456", updated.getTenantId());
            assertNull(original.getTenantId());
        }

        @Test
        @DisplayName("Should create new context with additional context")
        void shouldCreateNewContextWithAdditionalContext() {
            RuntimeContext original = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            RuntimeContext updated = original.withAdditionalContext("custom-key", "custom-value");
            
            assertEquals("corr-123", updated.getCorrelationId());
            assertEquals("custom-value", updated.get("custom-key"));
            assertNull(original.get("custom-key"));
        }
    }

    @Nested
    @DisplayName("Has Methods")
    class HasMethods {

        @Test
        @DisplayName("Should return true when correlation ID is present")
        void shouldReturnTrueWhenCorrelationIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            assertTrue(context.hasCorrelationId());
        }

        @Test
        @DisplayName("Should return false when correlation ID is null")
        void shouldReturnFalseWhenCorrelationIdNull() {
            RuntimeContext context = RuntimeContext.builder().build();
            
            assertFalse(context.hasCorrelationId());
        }

        @Test
        @DisplayName("Should return false when correlation ID is blank")
        void shouldReturnFalseWhenCorrelationIdBlank() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("   ")
                .build();
            
            assertFalse(context.hasCorrelationId());
        }

        @Test
        @DisplayName("Should return true when tenant ID is present")
        void shouldReturnTrueWhenTenantIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .tenantId("tenant-456")
                .build();
            
            assertTrue(context.hasTenantId());
        }

        @Test
        @DisplayName("Should return true when run ID is present")
        void shouldReturnTrueWhenRunIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .runId("run-789")
                .build();
            
            assertTrue(context.hasRunId());
        }

        @Test
        @DisplayName("Should return true when job ID is present")
        void shouldReturnTrueWhenJobIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .jobId("job-012")
                .build();
            
            assertTrue(context.hasJobId());
        }

        @Test
        @DisplayName("Should return true when agent ID is present")
        void shouldReturnTrueWhenAgentIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .agentId("agent-345")
                .build();
            
            assertTrue(context.hasAgentId());
        }

        @Test
        @DisplayName("Should return true when pipeline ID is present")
        void shouldReturnTrueWhenPipelineIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .pipelineId("pipeline-678")
                .build();
            
            assertTrue(context.hasPipelineId());
        }

        @Test
        @DisplayName("Should return true when artifact ID is present")
        void shouldReturnTrueWhenArtifactIdPresent() {
            RuntimeContext context = RuntimeContext.builder()
                .artifactId("artifact-901")
                .build();
            
            assertTrue(context.hasArtifactId());
        }
    }

    @Nested
    @DisplayName("To Builder")
    class ToBuilder {

        @Test
        @DisplayName("Should create builder from existing context")
        void shouldCreateBuilderFromExistingContext() {
            RuntimeContext original = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .additionalContext("key1", "value1")
                .build();
            
            RuntimeContext updated = original.toBuilder()
                .runId("run-789")
                .build();
            
            assertEquals("corr-123", updated.getCorrelationId());
            assertEquals("tenant-456", updated.getTenantId());
            assertEquals("run-789", updated.getRunId());
            assertEquals("value1", updated.get("key1"));
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            RuntimeContext context1 = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .build();
            
            RuntimeContext context2 = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .build();
            
            assertEquals(context1, context2);
            assertEquals(context1.hashCode(), context2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            RuntimeContext context1 = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            RuntimeContext context2 = RuntimeContext.builder()
                .correlationId("corr-456")
                .build();
            
            assertNotEquals(context1, context2);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            assertNotEquals(null, context);
        }

        @Test
        @DisplayName("Should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .build();
            
            assertNotEquals("string", context);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToString {

        @Test
        @DisplayName("Should produce non-empty string representation")
        void shouldProduceNonEmptyStringRepresentation() {
            RuntimeContext context = RuntimeContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .build();
            
            String str = context.toString();
            
            assertNotNull(str);
            assertFalse(str.isBlank());
            assertTrue(str.contains("corr-123"));
            assertTrue(str.contains("tenant-456"));
        }
    }

    @Nested
    @DisplayName("Additional Context")
    class AdditionalContext {

        @Test
        @DisplayName("Should return null for non-existent key")
        void shouldReturnNullForNonExistentKey() {
            RuntimeContext context = RuntimeContext.builder().build();
            
            assertNull(context.get("non-existent-key"));
        }

        @Test
        @DisplayName("Should return additional context map")
        void shouldReturnAdditionalContextMap() {
            RuntimeContext context = RuntimeContext.builder()
                .additionalContext("key1", "value1")
                .additionalContext("key2", "value2")
                .build();
            
            Map<String, String> additionalContext = context.getAdditionalContext();
            
            assertEquals(2, additionalContext.size());
            assertEquals("value1", additionalContext.get("key1"));
            assertEquals("value2", additionalContext.get("key2"));
        }

        @Test
        @DisplayName("Should return immutable additional context map")
        void shouldReturnImmutableAdditionalContextMap() {
            RuntimeContext context = RuntimeContext.builder()
                .additionalContext("key1", "value1")
                .build();
            
            Map<String, String> additionalContext = context.getAdditionalContext();
            
            assertThrows(UnsupportedOperationException.class, () -> {
                additionalContext.put("key2", "value2");
            });
        }
    }
}
