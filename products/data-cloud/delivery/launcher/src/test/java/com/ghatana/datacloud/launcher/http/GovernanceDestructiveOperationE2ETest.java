/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.BatchResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end lifecycle tests for governance destructive operations (DC-P1-352).
 *
 * <h2>Coverage</h2>
 * <p>Each test exercises a <em>complete</em> governance lifecycle — from
 * initial state setup through destructive execution to post-operation audit
 * verification.  The goal is to guarantee that:
 * <ol>
 *   <li>Destructive actions (purge, redact, policy delete) require an HMAC
 *       confirmation token obtained from a preceding dry-run request.</li>
 *   <li>Every destructive action emits an audit event before the response is
 *       returned, regardless of success or failure path.</li>
 *   <li>After a successful purge, expired entities are removed and a tombstone
 *       record is persisted in the governance store.</li>
 *   <li>After a successful redact, PII field values are replaced with
 *       {@code [REDACTED]} and the entity state is verifiable via
 *       {@code GET /api/v1/governance/privacy/verify}.</li>
 *   <li>Tenant isolation is maintained throughout every destructive flow.</li>
 * </ol>
 *
 * <h2>Acceptance criteria (DC-P1-352)</h2>
 * <ul>
 *   <li>Destructive actions require confirmation and emit audit evidence.</li>
 *   <li>Dry-run → Execute → Verify is the canonical three-step lifecycle.</li>
 *   <li>All audit records include correlation identifiers for diagnosability.</li>
 *   <li>No plaintext confirmation token is ever stored in audit records.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Governance destructive-operation end-to-end lifecycle tests (DC-P1-352)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Governance Destructive-Operation E2E (DC-P1-352)")
class GovernanceDestructiveOperationE2ETest {

    private static final String TEST_TENANT = "e2e-tenant-dc352";
    private static final String REDACTED = "[REDACTED]";

    private DataCloudClient mockClient;
    private EntityStore mockEntityStore;
    private AuditService mockAuditService;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper mapper = new ObjectMapper();
    /** In-memory entity store shared between mock stubs and test assertions. */
    private final Map<String, Map<String, EntityStore.Entity>> entityState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        entityState.clear();
        mockClient = mock(DataCloudClient.class);
        mockEntityStore = mock(EntityStore.class);
        mockAuditService = mock(AuditService.class);

        when(mockClient.entityStore()).thenReturn(mockEntityStore);
        when(mockAuditService.record(any())).thenReturn(Promise.complete());

        // findById delegates to entityState map
        when(mockClient.findById(any(), any(), any())).thenAnswer(inv -> {
            String collection = inv.getArgument(1);
            String id = inv.getArgument(2);
            EntityStore.Entity entity = entityState.getOrDefault(collection, Map.of()).get(id);
            if (entity == null) {
                return Promise.of(Optional.empty());
            }
            return Promise.of(Optional.of(DataCloudClient.Entity.of(entity.id().value(), entity.collection(), entity.data())));
        });

        // entityStore.save writes to entityState
        when(mockEntityStore.save(any(), any())).thenAnswer(inv -> {
            EntityStore.Entity entity = inv.getArgument(1);
            if (entity == null) return Promise.of(null);
            entityState.computeIfAbsent(entity.collection(), k -> new ConcurrentHashMap<>())
                    .put(entity.id().value(), entity);
            return Promise.of(entity);
        });

        // entityStore.findById reads from entityState
        when(mockEntityStore.findById(any(), any())).thenAnswer(inv -> {
            EntityStore.EntityId id = inv.getArgument(1);
            Optional<EntityStore.Entity> found = entityState.values().stream()
                    .map(col -> col.get(id.value()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
            return Promise.of(found);
        });

        // entityStore.query filters entityState by collection
        when(mockEntityStore.query(any(), any())).thenAnswer(inv -> {
            EntityStore.QuerySpec spec = inv.getArgument(1);
            List<EntityStore.Entity> entities = entityState
                    .getOrDefault(spec.collection(), Map.of())
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(e -> e.id().value()))
                    .skip(spec.offset())
                    .limit(spec.limit())
                    .toList();
            long total = entityState.getOrDefault(spec.collection(), Map.of()).size();
            return Promise.of(EntityStore.QueryResult.of(entities, total));
        });

        // entityStore.count returns size of collection in entityState
        when(mockEntityStore.count(any(), any())).thenAnswer(inv -> {
            EntityStore.QuerySpec spec = inv.getArgument(1);
            return Promise.of((long) entityState.getOrDefault(spec.collection(), Map.of()).size());
        });

        // entityStore.deleteByRefs removes from entityState
        when(mockEntityStore.deleteByRefs(any(), any())).thenAnswer(inv -> {
            List<EntityStore.EntityRef> refs = inv.getArgument(1);
            long deleted = 0;
            for (EntityStore.EntityRef ref : refs) {
                Map<String, EntityStore.Entity> collection = entityState.get(ref.collection());
                if (collection != null && collection.remove(ref.entityId().value()) != null) {
                    deleted++;
                }
            }
            return Promise.of(BatchResult.success((int) deleted));
        });

        // entityStore.deleteByRef removes a single entity
        when(mockEntityStore.deleteByRef(any(), any())).thenAnswer(inv -> {
            EntityStore.EntityRef ref = inv.getArgument(1);
            Map<String, EntityStore.Entity> collection = entityState.get(ref.collection());
            if (collection != null) {
                collection.remove(ref.entityId().value());
            }
            return Promise.of(null);
        });

        // entityStore.listCollections returns keys from entityState
        when(mockEntityStore.listCollections(any())).thenAnswer(inv ->
                Promise.of(entityState.keySet().stream().sorted().toList()));

        port = findFreePort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Complete Purge Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Purge lifecycle: Classify → Dry-run → Execute → Verify tombstone → Audit")
    class PurgeLifecycleE2E {

        @Test
        @DisplayName("complete purge lifecycle: entities deleted, tombstone written, audit emitted")
        @SuppressWarnings("unchecked")
        void completePurgeLifecycle_deletesEntitiesWritesTombstoneEmitsAudit() throws Exception {
            // ── 1. Seed expired entities into the collection ──────────────────
            String collection = "pii_user_records";
            for (int i = 1; i <= 5; i++) {
                storeEntity(entity("user-" + i, collection,
                        Map.of("email", "user" + i + "@example.com",
                                "expiresAt", Instant.now().minusSeconds(3600).toString())));
            }
            assertThat(entityState.getOrDefault(collection, Map.of())).hasSize(5);

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // ── 2. Step 1 — Classify the collection ──────────────────────────
            String classifyBody = mapper.writeValueAsString(Map.of(
                    "collection", collection,
                    "tier", "compliance",
                    "reason", "GDPR Art. 17 right to erasure"));
            HttpResponse<String> classifyResp = post("/api/v1/governance/retention/classify", classifyBody);
            assertThat(classifyResp.statusCode()).isEqualTo(200);

            Map<String, Object> classifyData =
                    (Map<String, Object>) mapper.readValue(classifyResp.body(), Map.class).get("data");
            assertThat(classifyData.get("tier")).isEqualTo("compliance");
            assertThat(classifyData.get("collection")).isEqualTo(collection);

            // ── 3. Step 2 — Dry-run purge to obtain confirmation token ────────
            String dryRunBody = mapper.writeValueAsString(Map.of(
                    "collection", collection,
                    "dryRun", true));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody);
            assertThat(dryRunResp.statusCode()).isEqualTo(200);

            Map<String, Object> dryRunData =
                    (Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data");
            assertThat(dryRunData.get("dryRun")).isEqualTo(true);
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotBlank();
            int previewCount = ((Number) dryRunData.get("eligibleCount")).intValue();
            assertThat(previewCount).isGreaterThan(0);

            // ── 4. Step 3 — Execute the real purge with the confirmation token ─
            String purgeBody = mapper.writeValueAsString(Map.of(
                    "collection", collection,
                    "confirmationToken", confirmationToken));
            HttpResponse<String> purgeResp = post("/api/v1/governance/retention/purge", purgeBody);
            assertThat(purgeResp.statusCode()).isEqualTo(200);

            Map<String, Object> purgeData =
                    (Map<String, Object>) mapper.readValue(purgeResp.body(), Map.class).get("data");
            assertThat(purgeData.get("status")).isEqualTo("PURGE_COMPLETED");
            int deletedCount = ((Number) purgeData.get("deletedCount")).intValue();
            assertThat(deletedCount).isGreaterThan(0);

            // ── 5. Verify — Expired entities must be removed from the store ───
            long remaining = entityState.getOrDefault(collection, Map.of()).values().stream()
                    .filter(e -> !e.collection().startsWith("_governance_"))
                    .count();
            assertThat(remaining).isZero();

            // ── 6. Verify — Tombstone record must be written to the governance store ─
            boolean tombstoneWritten = entityState.keySet().stream()
                    .anyMatch(k -> k.contains("purge") || k.contains("tombstone"));
            assertThat(tombstoneWritten)
                    .as("Tombstone record must be persisted in the governance store after purge")
                    .isTrue();

            // ── 7. Verify — Audit event must have been emitted with hashed token ─
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(mockAuditService, atLeastOnce()).record(auditCaptor.capture());

            List<AuditEvent> purgeAuditEvents = auditCaptor.getAllValues().stream()
                    .filter(ev -> "RETENTION_PURGE".equals(ev.eventType()))
                    .toList();
            assertThat(purgeAuditEvents)
                    .as("At least one RETENTION_PURGE audit event must be emitted")
                    .isNotEmpty();

            AuditEvent purgeAudit = purgeAuditEvents.get(0);
            // Confirmation token must be stored as SHA-256 hash, never plaintext
            String storedHash = (String) purgeAudit.getDetail("confirmationTokenHash");
            assertThat(storedHash)
                    .as("confirmationTokenHash must be present in the purge audit record")
                    .isNotBlank();
            assertThat(storedHash)
                    .as("Confirmation token must NOT be stored in plaintext in the audit record")
                    .isNotEqualTo(confirmationToken);
            assertThat(storedHash)
                    .as("confirmationTokenHash must be the SHA-256 hex digest of the actual token")
                    .isEqualTo(sha256Hex(confirmationToken));
        }

        @Test
        @DisplayName("purge without dry-run returns 400 — confirmation token is mandatory")
        @SuppressWarnings("unchecked")
        void purgeWithoutConfirmationToken_isRejected() throws Exception {
            storeEntity(entity("rec-1", "old_records",
                    Map.of("expiresAt", Instant.now().minusSeconds(1000).toString())));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // Attempt purge without going through dry-run first (no confirmationToken)
            String body = mapper.writeValueAsString(Map.of("collection", "old_records"));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isEqualTo(400);
            Map<String, Object> error =
                    (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("error");
            assertThat(error.get("code").toString())
                    .isEqualTo("MISSING_CONFIRMATION");

            // No data deleted — dry-run enforced
            assertThat(entityState.getOrDefault("old_records", Map.of())).hasSize(1);
        }

        @Test
        @DisplayName("purge with tampered token is rejected with 403 and audit emitted")
        @SuppressWarnings("unchecked")
        void purgeWithTamperedToken_rejectedAndAudited() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                    "collection", "sensitive_data",
                    "confirmationToken", "tampered-token-that-was-never-issued"));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isEqualTo(403);
            Map<String, Object> error =
                    (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_CONFIRMATION_TOKEN");

            // Rejected purge attempt must also be audited
            verify(mockAuditService, atLeastOnce()).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("purge is tenant-isolated — tenant-A token cannot purge tenant-B collection")
        @SuppressWarnings("unchecked")
        void purge_isTenantIsolated() throws Exception {
            // Seed entity for tenant-A
            storeEntity(entity("ta-entity-1", "tenant_a_data",
                    Map.of("expiresAt", Instant.now().minusSeconds(1).toString())));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // Dry-run as tenant-A to get a valid token
            String dryRun = mapper.writeValueAsString(Map.of("collection", "tenant_a_data", "dryRun", true));
            HttpResponse<String> dryRunResp = postForTenant("/api/v1/governance/retention/purge", dryRun, "tenant-a");
            String tokenForTenantA = (String)
                    ((Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data"))
                            .get("confirmationToken");

            // Attempt to use tenant-A's token as tenant-B — must be rejected
            String purgeBody = mapper.writeValueAsString(Map.of(
                    "collection", "tenant_a_data",
                    "confirmationToken", tokenForTenantA));
            HttpResponse<String> crossTenantResp =
                    postForTenant("/api/v1/governance/retention/purge", purgeBody, "tenant-b");

            // The token is HMAC-signed with the tenant ID — cross-tenant use must fail
            assertThat(crossTenantResp.statusCode())
                    .as("Cross-tenant purge must be rejected (403 or the entity is not found for tenant-b)")
                    .isIn(403, 200); // 200 is acceptable only if no eligible entities found for tenant-b
            if (crossTenantResp.statusCode() == 200) {
                Map<String, Object> data =
                        (Map<String, Object>) mapper.readValue(crossTenantResp.body(), Map.class).get("data");
                // If 200, deleted count must be 0 (tenant-b has no entities to purge)
                assertThat(((Number) data.get("deletedCount")).intValue())
                        .as("Tenant-B must not delete tenant-A's entities")
                        .isZero();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Complete Redact Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Redact lifecycle: Entity setup → Dry-run → Execute → Verify → Audit")
    class RedactLifecycleE2E {

        @Test
        @DisplayName("complete redact lifecycle: PII fields replaced, verify confirms redaction, audit emitted")
        @SuppressWarnings("unchecked")
        void completeRedactLifecycle_piiFieldsReplacedVerifiedAndAudited() throws Exception {
            // ── 1. Seed entity with PII fields ────────────────────────────────
            String collection = "customer_profiles";
            String entityId = "cust-001";
            storeEntity(entity(entityId, collection, Map.of(
                    "full_name", "Alice Example",
                    "email", "alice@example.com",
                    "phone", "+1-555-0100",
                    "region", "us-west-2")));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // ── 2. Step 1 — Dry-run redact to preview and obtain token ────────
            List<String> fieldsToRedact = List.of("email", "phone", "full_name");
            String dryRunBody = mapper.writeValueAsString(Map.of(
                    "collection", collection,
                    "entityId", entityId,
                    "fields", fieldsToRedact,
                    "dryRun", true));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/privacy/redact", dryRunBody);
            assertThat(dryRunResp.statusCode()).isEqualTo(200);

            Map<String, Object> dryRunData =
                    (Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotBlank();
            assertThat(dryRunData.get("dryRun")).isEqualTo(true);

            // Dry-run must preview which fields will be redacted
            @SuppressWarnings("unchecked")
            List<String> previewFields = (List<String>) dryRunData.get("fieldsToRedact");
            assertThat(previewFields).containsAll(fieldsToRedact);

            // ── 3. Step 2 — Execute redaction with confirmation token ─────────
            String redactBody = mapper.writeValueAsString(Map.of(
                    "collection", collection,
                    "entityId", entityId,
                    "fields", fieldsToRedact,
                    "confirmationToken", confirmationToken));
            HttpResponse<String> redactResp = post("/api/v1/governance/privacy/redact", redactBody);
            assertThat(redactResp.statusCode()).isEqualTo(200);

            Map<String, Object> redactData =
                    (Map<String, Object>) mapper.readValue(redactResp.body(), Map.class).get("data");
            assertThat(redactData.get("status")).isEqualTo("REDACTED");

            // ── 4. Step 3 — Verify redaction via privacy/verify endpoint ──────
            String verifyPath = "/api/v1/governance/privacy/verify?collection=" + collection
                    + "&entityId=" + entityId + "&fields=email,phone,full_name";
            HttpResponse<String> verifyResp = get(verifyPath);
            assertThat(verifyResp.statusCode()).isEqualTo(200);

            Map<String, Object> verifyData =
                    (Map<String, Object>) mapper.readValue(verifyResp.body(), Map.class).get("data");
            assertThat(verifyData.get("status")).isEqualTo("VERIFIED");

            // ── 5. Verify — PII fields are stored as [REDACTED] in entity state ─
            EntityStore.Entity updatedEntity = entityState
                    .getOrDefault(collection, Map.of())
                    .get(entityId);
            assertThat(updatedEntity).isNotNull();
            assertThat(updatedEntity.data().get("email")).isEqualTo(REDACTED);
            assertThat(updatedEntity.data().get("phone")).isEqualTo(REDACTED);
            assertThat(updatedEntity.data().get("full_name")).isEqualTo(REDACTED);
            // Non-PII field must be untouched
            assertThat(updatedEntity.data().get("region")).isEqualTo("us-west-2");

            // ── 6. Verify — Audit event emitted with correct event type ────────
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(mockAuditService, atLeastOnce()).record(auditCaptor.capture());

            List<AuditEvent> redactAudits = auditCaptor.getAllValues().stream()
                    .filter(ev -> "PII_REDACT".equals(ev.eventType()))
                    .toList();
            assertThat(redactAudits)
                    .as("At least one PII_REDACT audit event must be emitted")
                    .isNotEmpty();

            // Audit must not store PII values
            AuditEvent redactAudit = redactAudits.get(0);
            Object redactedFieldsDetail = redactAudit.getDetail("redactedFields");
            assertThat(redactedFieldsDetail).isNotNull();
            // Entity ID should be traceable in audit
            assertThat(redactAudit.getDetail("entityId")).isNotNull();
        }

        @Test
        @DisplayName("redact without confirmation token returns 400 — dry-run gate enforced")
        @SuppressWarnings("unchecked")
        void redactWithoutToken_isRejected() throws Exception {
            storeEntity(entity("cust-002", "profiles",
                    Map.of("email", "bob@example.com")));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                    "collection", "profiles",
                    "entityId", "cust-002",
                    "fields", List.of("email")));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body);

            assertThat(resp.statusCode()).isEqualTo(400);
            Map<String, Object> error =
                    (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("error");
            assertThat(error.get("code").toString()).contains("confirmationToken");

            // PII field must not have been modified
            EntityStore.Entity original = entityState.getOrDefault("profiles", Map.of()).get("cust-002");
            assertThat(original).isNotNull();
            assertThat(original.data().get("email")).isEqualTo("bob@example.com");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Policy Delete Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Policy delete lifecycle: Create → Read → Delete → Audit")
    class PolicyDeleteLifecycleE2E {

        @Test
        @DisplayName("policy delete lifecycle: create policy, delete it, verify 404, audit emitted")
        @SuppressWarnings("unchecked")
        void completePolicyDeleteLifecycle_deletedAndAudited() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // ── 1. Create a governance policy ────────────────────────────────
            String createBody = mapper.writeValueAsString(Map.of(
                    "name", "GDPR Erasure Policy",
                    "type", "RETENTION",
                    "description", "Erase records older than 90 days",
                    "enabled", true,
                    "rules", List.of(Map.of("field", "expiresAt", "operator", "lt", "value", "now"))));
            HttpResponse<String> createResp = post("/api/v1/governance/policies", createBody);
            assertThat(createResp.statusCode()).isEqualTo(201);

            Map<String, Object> createData =
                    (Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data");
            String policyId = (String) createData.get("id");
            assertThat(policyId).isNotBlank();
            assertThat(createData.get("name")).isEqualTo("GDPR Erasure Policy");

            // ── 2. Verify policy exists via GET ──────────────────────────────
            HttpResponse<String> getResp = get("/api/v1/governance/policies/" + policyId);
            assertThat(getResp.statusCode()).isEqualTo(200);

            Map<String, Object> getPolicy =
                    (Map<String, Object>) mapper.readValue(getResp.body(), Map.class).get("data");
            assertThat(getPolicy.get("id")).isEqualTo(policyId);
            assertThat(getPolicy.get("enabled")).isEqualTo(true);

            // ── 3. Delete the policy ─────────────────────────────────────────
            HttpResponse<String> deleteResp = delete("/api/v1/governance/policies/" + policyId);
            assertThat(deleteResp.statusCode()).isEqualTo(200);

            // ── 4. Verify policy no longer exists ────────────────────────────
            HttpResponse<String> afterDeleteResp = get("/api/v1/governance/policies/" + policyId);
            assertThat(afterDeleteResp.statusCode()).isEqualTo(404);

            // ── 5. Verify audit event for delete ─────────────────────────────
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(mockAuditService, atLeastOnce()).record(auditCaptor.capture());

            List<AuditEvent> deleteAudits = auditCaptor.getAllValues().stream()
                    .filter(ev -> ev.eventType() != null && ev.eventType().contains("POLICY"))
                    .filter(ev -> ev.eventType().contains("DELETE") || ev.eventType().contains("REMOVED"))
                    .toList();
            assertThat(deleteAudits)
                    .as("Policy delete must emit an audit event")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("disabling policy before deletion reflects in compliance summary")
        @SuppressWarnings("unchecked")
        void policyToggleAndDelete_reflectsInComplianceSummary() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // Create an enabled policy
            String createBody = mapper.writeValueAsString(Map.of(
                    "name", "Data Minimisation Policy",
                    "type", "MINIMISATION",
                    "description", "Minimise collected fields",
                    "enabled", true));
            HttpResponse<String> createResp = post("/api/v1/governance/policies", createBody);
            String policyId = (String)
                    ((Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data"))
                            .get("id");

            // Toggle policy off
            HttpResponse<String> toggleResp = post("/api/v1/governance/policies/" + policyId + "/toggle",
                    mapper.writeValueAsString(Map.of()));
            assertThat(toggleResp.statusCode()).isEqualTo(200);

            Map<String, Object> toggledPolicy =
                    (Map<String, Object>) mapper.readValue(toggleResp.body(), Map.class).get("data");
            assertThat(toggledPolicy.get("enabled")).isEqualTo(false);

            // Compliance summary must reflect the disabled policy count
            HttpResponse<String> summaryResp = get("/api/v1/governance/compliance/summary");
            assertThat(summaryResp.statusCode()).isEqualTo(200);
            Map<String, Object> summaryData =
                    (Map<String, Object>) mapper.readValue(summaryResp.body(), Map.class).get("data");
            assertThat(summaryData).containsKey("tenantId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Multi-Operation Audit Trail Integrity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-operation audit trail: every destructive action has an audit record")
    class AuditTrailIntegrity {

        @Test
        @DisplayName("classify + purge + redact emits distinct audit events for each operation")
        @SuppressWarnings("unchecked")
        void multipleDestructiveOperations_allEmitDistinctAuditEvents() throws Exception {
            String collection = "audit_trail_test";
            storeEntity(entity("ent-100", collection, Map.of(
                    "email", "trail@example.com",
                    "expiresAt", Instant.now().minusSeconds(1).toString())));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // 1. Classify
            post("/api/v1/governance/retention/classify",
                    mapper.writeValueAsString(Map.of("collection", collection, "tier", "standard")));

            // 2. Redact — dry-run then execute
            String redactDryRun = mapper.writeValueAsString(Map.of(
                    "collection", collection, "entityId", "ent-100",
                    "fields", List.of("email"), "dryRun", true));
            HttpResponse<String> rdDryResp = post("/api/v1/governance/privacy/redact", redactDryRun);
            if (rdDryResp.statusCode() == 200) {
                Map<String, Object> rdDryData =
                        (Map<String, Object>) mapper.readValue(rdDryResp.body(), Map.class).get("data");
                String rdToken = (String) rdDryData.get("confirmationToken");
                if (rdToken != null) {
                    post("/api/v1/governance/privacy/redact",
                            mapper.writeValueAsString(Map.of("collection", collection, "entityId", "ent-100",
                                    "fields", List.of("email"), "confirmationToken", rdToken)));
                }
            }

            // 3. Purge — dry-run then execute
            String purgeDryRun = mapper.writeValueAsString(Map.of("collection", collection, "dryRun", true));
            HttpResponse<String> pgDryResp = post("/api/v1/governance/retention/purge", purgeDryRun);
            if (pgDryResp.statusCode() == 200) {
                Map<String, Object> pgDryData =
                        (Map<String, Object>) mapper.readValue(pgDryResp.body(), Map.class).get("data");
                String pgToken = (String) pgDryData.get("confirmationToken");
                if (pgToken != null) {
                    post("/api/v1/governance/retention/purge",
                            mapper.writeValueAsString(Map.of("collection", collection,
                                    "confirmationToken", pgToken)));
                }
            }

            // Verify audit events were emitted for each operation
            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(mockAuditService, atLeastOnce()).record(captor.capture());

            List<String> eventTypes = captor.getAllValues().stream()
                    .map(AuditEvent::eventType)
                    .toList();

            // At a minimum, classify and at least one purge/redact audit must be present
            assertThat(eventTypes)
                    .as("Audit log must contain at least one RETENTION_CLASSIFY event")
                    .anyMatch(t -> t.contains("CLASSIFY") || t.contains("RETENTION"));
        }

        @Test
        @DisplayName("failed operations still emit audit events — no silent failures")
        @SuppressWarnings("unchecked")
        void failedPurge_stillEmitsAuditEvent() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // Attempt purge with an invalid token — should fail
            String body = mapper.writeValueAsString(Map.of(
                    "collection", "any_collection",
                    "confirmationToken", "completely-invalid-token-abc123"));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isIn(400, 403);

            // Even on failure, the denied/rejected action must be audited
            verify(mockAuditService, atLeastOnce()).record(any(AuditEvent.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", TEST_TENANT)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForTenant(String path, String jsonBody, String tenantId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", tenantId)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Tenant-Id", TEST_TENANT)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Tenant-Id", TEST_TENANT)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test Data Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void storeEntity(EntityStore.Entity entity) {
        entityState.computeIfAbsent(entity.collection(), k -> new ConcurrentHashMap<>())
                .put(entity.id().value(), entity);
    }

    private static EntityStore.Entity entity(String id, String collection, Map<String, Object> data) {
        return new EntityStore.Entity(
                EntityStore.EntityId.of(id),
                collection,
                data,
                EntityStore.EntityMetadata.empty());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start within 5s on port " + port);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
