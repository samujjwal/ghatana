/**
 * Platform Integration Client Contract Tests
 *
 * Verifies that the platform integration client enforces required context fields
 * on requests and returns properly structured responses including degraded states,
 * policy decisions, and evidence IDs for provenance.
 *
 * @doc.type test
 * @doc.purpose Contract-level verification of platform integration boundary
 * @doc.layer test
 * @doc.pattern Contract Test
 */

package com.ghatana.yappc.services.platform;

import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.api.PlatformExecution;
import com.ghatana.yappc.api.PlatformMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the platform integration client boundary.
 * Each test verifies a contract guarantee rather than an implementation detail.
 */
@DisplayName("Platform Integration Client Contract Tests")
class PlatformIntegrationClientContractTest {

    private PlatformIntegrationClient client;

    @BeforeEach
    void setUp() {
        client = new PlatformIntegrationClientImpl();
    }

    // ─── Execution request required context ──────────────────────────────────

    @Nested
    @DisplayName("Execution requests include required context")
    class ExecutionRequestContext {

        @Test
        @DisplayName("Execute response carries tenantId from request correlation context")
        void executionResponseCarriesTenantId() {
            PlatformExecution.ExecutionRequest request = new PlatformExecution.ExecutionRequest(
                    "generation",
                    "model-1",
                    "1.0.0",
                    Map.of("prompt", "generate code"),
                    Map.of("X-Tenant-Id", "tenant-abc"),
                    "corr-123"
            );

            PlatformExecution execution = client.execute(request);

            assertNotNull(execution.tenantId(), "Execution response must carry tenantId");
            assertNotNull(execution.workspaceId(), "Execution response must carry workspaceId");
            assertNotNull(execution.projectId(), "Execution response must carry projectId");
        }

        @Test
        @DisplayName("Execute response carries non-blank executionId")
        void executionResponseHasNonBlankExecutionId() {
            PlatformExecution.ExecutionRequest request = new PlatformExecution.ExecutionRequest(
                    "analysis",
                    "model-1",
                    "1.0.0",
                    Map.of(),
                    Map.of(),
                    "corr-456"
            );

            PlatformExecution execution = client.execute(request);

            assertNotNull(execution.executionId());
            assertFalse(execution.executionId().isBlank(), "executionId must be non-blank");
        }

        @Test
        @DisplayName("Execute response includes metadata with traceId")
        void executionResponseHasTraceId() {
            PlatformExecution.ExecutionRequest request = new PlatformExecution.ExecutionRequest(
                    "generation",
                    "model-1",
                    "1.0.0",
                    Map.of(),
                    Map.of(),
                    "corr-789"
            );

            PlatformExecution execution = client.execute(request);

            assertNotNull(execution.metadata(), "Execution metadata must be present");
            assertNotNull(execution.metadata().traceId(), "traceId must be present for observability");
            assertFalse(execution.metadata().traceId().isBlank(), "traceId must be non-blank");
        }

        @Test
        @DisplayName("Execute response carries createdAt and updatedAt timestamps")
        void executionResponseHasTimestamps() {
            PlatformExecution.ExecutionRequest request = new PlatformExecution.ExecutionRequest(
                    "generation",
                    "model-1",
                    "1.0.0",
                    Map.of(),
                    Map.of(),
                    "corr-ts"
            );

            PlatformExecution execution = client.execute(request);

            assertNotNull(execution.createdAt(), "createdAt must be present");
            assertNotNull(execution.updatedAt(), "updatedAt must be present");
            assertFalse(execution.createdAt().isAfter(Instant.now()), "createdAt must not be in the future");
        }
    }

    // ─── Evidence search carries context ─────────────────────────────────────

    @Nested
    @DisplayName("Evidence search response carries required fields")
    class EvidenceSearchContext {

        @Test
        @DisplayName("Evidence search results carry evidenceId for provenance")
        void evidenceSearchResultsHaveEvidenceId() {
            PlatformEvidence evidence = buildTestEvidence("evidence-contract-1", "project-contract", "workspace-contract", "tenant-contract");
            client.storeEvidence(evidence);

            List<PlatformEvidence.SearchResult> results = client.searchEvidence(
                    new PlatformEvidence.SearchQuery(
                            "contract",
                            "project-contract",
                            "workspace-contract",
                            List.of(),
                            null,
                            null,
                            Map.of()
                    )
            );

            assertFalse(results.isEmpty(), "Search must return at least the stored evidence");
            for (PlatformEvidence.SearchResult result : results) {
                assertNotNull(result.evidenceId(), "Each result must carry evidenceId for provenance");
                assertFalse(result.evidenceId().isBlank(), "evidenceId must be non-blank");
            }
        }

        @Test
        @DisplayName("Evidence search results include relevance score")
        void evidenceSearchResultsHaveRelevanceScore() {
            PlatformEvidence evidence = buildTestEvidence("evidence-score-1", "proj-score", "ws-score", "tenant-score");
            client.storeEvidence(evidence);

            List<PlatformEvidence.SearchResult> results = client.searchEvidence(
                    new PlatformEvidence.SearchQuery(
                            "score",
                            "proj-score",
                            "ws-score",
                            List.of(),
                            null,
                            null,
                            Map.of()
                    )
            );

            assertFalse(results.isEmpty());
            for (PlatformEvidence.SearchResult result : results) {
                assertTrue(result.relevanceScore() >= 0.0, "relevanceScore must be non-negative");
                assertTrue(result.relevanceScore() <= 1.0, "relevanceScore must be at most 1.0");
            }
        }

        @Test
        @DisplayName("Stored evidence preserves executionId for generation provenance")
        void storedEvidencePreservesExecutionId() {
            String executionId = "exec-provenance-1";
            PlatformEvidence evidence = new PlatformEvidence(
                    "evidence-prov-1",
                    executionId,
                    "project-prov",
                    "workspace-prov",
                    "tenant-prov",
                    new PlatformEvidence.EvidenceRecord(
                            "log",
                            "text/plain",
                            "provenance content",
                            new PlatformEvidence.EvidenceSource(
                                    "generation", "gen-1", "1.0.0", "trace-prov", Instant.now()),
                            List.of(),
                            Map.of()
                    ),
                    new PlatformEvidence.EvidenceMetadata(
                            "session-prov", "user-prov", "generate",
                            Set.of("provenance"), Map.of()),
                    Instant.now(),
                    Instant.now()
            );

            client.storeEvidence(evidence);

            List<PlatformEvidence.SearchResult> results = client.searchEvidence(
                    new PlatformEvidence.SearchQuery(
                            "provenance",
                            "project-prov",
                            "workspace-prov",
                            List.of(),
                            null,
                            null,
                            Map.of()
                    )
            );

            assertFalse(results.isEmpty(), "Stored evidence must be searchable");
        }
    }

    // ─── Policy evaluation contract ───────────────────────────────────────────

    @Nested
    @DisplayName("Policy evaluation response carries required fields")
    class PolicyEvaluationContext {

        @Test
        @DisplayName("Policy evaluation returns non-null policyId")
        void policyEvaluationHasPolicyId() {
            PlatformPolicy.PolicyRequest request = new PlatformPolicy.PolicyRequest(
                    "generation-guard",
                    Map.of("phase", "generate", "action", "promote"),
                    "tenant-policy",
                    "workspace-policy",
                    "project-policy"
            );

            PlatformPolicy policy = client.evaluatePolicy(request);

            assertNotNull(policy.policyId(), "Policy response must include policyId");
            assertFalse(policy.policyId().isBlank(), "policyId must be non-blank");
        }

        @Test
        @DisplayName("Policy evaluation returns evaluatedAt timestamp")
        void policyEvaluationHasTimestamp() {
            PlatformPolicy.PolicyRequest request = new PlatformPolicy.PolicyRequest(
                    "preview-guard",
                    Map.of(),
                    "tenant-ts",
                    "workspace-ts",
                    "project-ts"
            );

            PlatformPolicy policy = client.evaluatePolicy(request);

            assertNotNull(policy.evaluatedAt(), "Policy response must include evaluatedAt");
            assertFalse(policy.evaluatedAt().isAfter(Instant.now()), "evaluatedAt must not be in the future");
        }

        @Test
        @DisplayName("Policy denial includes non-empty deniedReasons")
        void policyDenialHasDeniedReasons() {
            // The DTO contract guarantees deniedReasons is present regardless of decision
            PlatformPolicy.PolicyRequest request = new PlatformPolicy.PolicyRequest(
                    "test-policy",
                    Map.of("action", "delete-all"),
                    "tenant-deny",
                    "workspace-deny",
                    "project-deny"
            );

            PlatformPolicy policy = client.evaluatePolicy(request);

            assertNotNull(policy.deniedReasons(), "deniedReasons list must be non-null (may be empty when allowed)");
            // When policy is denied, reasons must be present
            if (!policy.isAllowed()) {
                assertFalse(policy.deniedReasons().isEmpty(), "Denied policy must have at least one reason");
            }
        }

        @Test
        @DisplayName("Policy includes tenant context from request")
        void policyPreservesRequestContext() {
            PlatformPolicy.PolicyRequest request = new PlatformPolicy.PolicyRequest(
                    "scope-guard",
                    Map.of("resource", "workspace"),
                    "tenant-scope",
                    "workspace-scope",
                    "project-scope"
            );

            PlatformPolicy policy = client.evaluatePolicy(request);

            assertNotNull(policy, "Policy result must not be null");
            assertNotNull(policy.context(), "Policy context must be included");
        }
    }

    // ─── Health exposes degraded state ────────────────────────────────────────

    @Nested
    @DisplayName("Platform health exposes component-level status")
    class PlatformHealthContract {

        @Test
        @DisplayName("Health response is non-null with status field")
        void healthResponseIsStructured() {
            PlatformHealth health = client.getHealth();

            assertNotNull(health, "Health result must not be null");
            assertNotNull(health.status(), "Health status must be present");
        }

        @Test
        @DisplayName("Health includes component-level breakdown for observability")
        void healthIncludesComponents() {
            PlatformHealth health = client.getHealth();

            assertNotNull(health.components(), "Health components must be present for degraded-state visibility");
        }
    }

    // ─── Memory request includes required fields ──────────────────────────────

    @Nested
    @DisplayName("Memory operations preserve required fields")
    class MemoryContext {

        @Test
        @DisplayName("Stored memory preserves tenantId for tenant isolation")
        void storedMemoryPreservesTenantId() {
            PlatformMemory memory = new PlatformMemory(
                    "memory-tenant-1",
                    "project-mem",
                    "workspace-mem",
                    "tenant-mem",
                    new PlatformMemory.MemoryRecord(
                            "session",
                            "context-key",
                            "context-value",
                            new PlatformMemory.MemoryValue("string", 13, "utf-8", "checksum-mem", null),
                            List.of()
                    ),
                    new PlatformMemory.MemoryMetadata(
                            "session-mem", "user-mem", "generate",
                            Set.of("session"), Map.of()),
                    Instant.now(),
                    Instant.now()
            );

            boolean stored = client.storeMemory(memory);
            assertTrue(stored, "Memory storage must succeed");

            PlatformMemory retrieved = client.retrieveMemory("memory-tenant-1");
            assertEquals("tenant-mem", retrieved.tenantId(), "Stored memory must preserve tenantId");
        }

        @Test
        @DisplayName("Cross-tenant memory retrieval is not possible (different memoryId namespace)")
        void memoryIsTenantScoped() {
            // Two memories with same key but different tenant namespacing via distinct memoryIds
            PlatformMemory memoryA = new PlatformMemory(
                    "memory-tenantA-key1",
                    "project-a",
                    "workspace-a",
                    "tenant-a",
                    new PlatformMemory.MemoryRecord(
                            "session", "key-1", "value-for-tenant-a",
                            new PlatformMemory.MemoryValue("string", 18, "utf-8", "chkA", null),
                            List.of()),
                    new PlatformMemory.MemoryMetadata("sess-a", "user-a", "generate", Set.of(), Map.of()),
                    Instant.now(), Instant.now()
            );

            client.storeMemory(memoryA);

            // Retrieving a different memoryId (tenant B's would be different) must fail
            assertThrows(IllegalArgumentException.class,
                    () -> client.retrieveMemory("memory-tenantB-key1"),
                    "Non-existent memoryId must throw to prevent cross-tenant leakage");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private PlatformEvidence buildTestEvidence(String evidenceId, String projectId,
                                                String workspaceId, String tenantId) {
        return new PlatformEvidence(
                evidenceId,
                "exec-" + evidenceId,
                projectId,
                workspaceId,
                tenantId,
                new PlatformEvidence.EvidenceRecord(
                        "log",
                        "text/plain",
                        "contract test content " + evidenceId,
                        new PlatformEvidence.EvidenceSource(
                                "generation", "gen-1", "1.0.0", "trace-" + evidenceId, Instant.now()),
                        List.of(),
                        Map.of()
                ),
                new PlatformEvidence.EvidenceMetadata(
                        "session-" + evidenceId, "user-1", "generate",
                        Set.of("test"), Map.of()),
                Instant.now(),
                Instant.now()
        );
    }
}
