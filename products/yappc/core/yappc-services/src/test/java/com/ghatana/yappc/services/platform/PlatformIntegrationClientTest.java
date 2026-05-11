/**
 * Platform Integration Client Tests
 * 
 * Production-grade tests for platform integration client.
 * Ensures platform integration works correctly with execution, evidence, and memory operations.
 * 
 * @doc.type test
 * @doc.purpose Platform integration tests
 * @doc.layer test
 * @doc.pattern Integration Test
 */

package com.ghatana.yappc.services.platform;

import com.ghatana.yappc.api.PlatformExecution;
import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.api.PlatformMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade tests for platform integration client.
 */
@DisplayName("Platform Integration Client Tests")
class PlatformIntegrationClientTest {

    private PlatformIntegrationClient client;

    @BeforeEach
    void setUp() {
        client = new PlatformIntegrationClientImpl();
    }

    @Test
    @DisplayName("Should execute platform operation")
    void shouldExecutePlatformOperation() {
        PlatformExecution.ExecutionRequest request = new PlatformExecution.ExecutionRequest(
                "generation",
                "model-1",
                "1.0.0",
                Map.of("prompt", "test"),
                Map.of("Authorization", "Bearer token"),
                "correlation-1"
        );

        PlatformExecution execution = client.execute(request);

        assertNotNull(execution.executionId(), "Execution ID should be generated");
        assertEquals("generation", execution.request().executionType());
        assertEquals(PlatformExecution.ExecutionStatus.ExecutionState.COMPLETED, execution.status().state());
    }

    @Test
    @DisplayName("Should get execution status")
    void shouldGetExecutionStatus() {
        PlatformExecution.ExecutionRequest request = new PlatformExecution.ExecutionRequest(
                "generation",
                "model-1",
                "1.0.0",
                Map.of(),
                Map.of(),
                "correlation-1"
        );

        PlatformExecution execution = client.execute(request);
        PlatformExecution status = client.getExecutionStatus(execution.executionId());

        assertEquals(execution.executionId(), status.executionId());
        assertEquals(execution.status().state(), status.status().state());
    }

    @Test
    @DisplayName("Should store and retrieve evidence")
    void shouldStoreAndRetrieveEvidence() {
        PlatformEvidence evidence = createTestEvidence();

        boolean stored = client.storeEvidence(evidence);
        assertTrue(stored, "Evidence should be stored successfully");

        List<PlatformEvidence.SearchResult> results = client.searchEvidence(
                new PlatformEvidence.SearchQuery(
                        "test content",
                        "project-1",
                        "workspace-1",
                        List.of(),
                        null,
                        null,
                        Map.of()
                )
        );

        assertFalse(results.isEmpty(), "Search should return results");
    }

    @Test
    @DisplayName("Should store and retrieve memory")
    void shouldStoreAndRetrieveMemory() {
        PlatformMemory memory = createTestMemory();

        boolean stored = client.storeMemory(memory);
        assertTrue(stored, "Memory should be stored successfully");

        PlatformMemory retrieved = client.retrieveMemory(memory.memoryId());
        assertEquals(memory.memoryId(), retrieved.memoryId());
        assertEquals(memory.record().key(), retrieved.record().key());
    }

    @Test
    @DisplayName("Should delete memory")
    void shouldDeleteMemory() {
        PlatformMemory memory = createTestMemory();
        client.storeMemory(memory);

        boolean deleted = client.deleteMemory(memory.memoryId());
        assertTrue(deleted, "Memory should be deleted successfully");

        assertThrows(IllegalArgumentException.class, () -> client.retrieveMemory(memory.memoryId()));
    }

    @Test
    @DisplayName("Should get platform health")
    void shouldGetPlatformHealth() {
        PlatformHealth health = client.getHealth();

        assertTrue(health.isHealthy(), "Platform should be healthy");
        assertNotNull(health.status(), "Health status should not be null");
        assertNotNull(health.components(), "Health components should not be null");
    }

    @Test
    @DisplayName("Should handle non-existent execution")
    void shouldHandleNonExistentExecution() {
        assertThrows(IllegalArgumentException.class, () -> client.getExecutionStatus("non-existent-id"));
    }

    @Test
    @DisplayName("Should handle non-existent memory")
    void shouldHandleNonExistentMemory() {
        assertThrows(IllegalArgumentException.class, () -> client.retrieveMemory("non-existent-id"));
    }

    // Helper methods to create test data

    private PlatformEvidence createTestEvidence() {
        return new PlatformEvidence(
                "evidence-1",
                "exec-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                new PlatformEvidence.EvidenceRecord(
                        "log",
                        "text/plain",
                        "test content",
                        new PlatformEvidence.EvidenceSource("generation", "gen-1", "1.0.0", "trace-1", Instant.now()),
                        List.of(),
                        Map.of()
                ),
                new PlatformEvidence.EvidenceMetadata(
                        "session-1",
                        "user-1",
                        "generate",
                        Set.of("test"),
                        Map.of()
                ),
                Instant.now(),
                Instant.now()
        );
    }

    private PlatformMemory createTestMemory() {
        return new PlatformMemory(
                "memory-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                new PlatformMemory.MemoryRecord(
                        "session",
                        "key-1",
                        "value-1",
                        new PlatformMemory.MemoryValue("string", 10, "utf-8", "checksum", null),
                        List.of()
                ),
                new PlatformMemory.MemoryMetadata(
                        "session-1",
                        "user-1",
                        "generate",
                        Set.of("test"),
                        Map.of()
                ),
                Instant.now(),
                Instant.now()
        );
    }
}
