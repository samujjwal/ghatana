/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.audit.AuditSummaryProvider;
import com.ghatana.datacloud.spi.BatchResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.governance.PolicyEngine;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Integration tests for data lifecycle and governance API endpoints (DC-E5). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and exercises
 * all six governance routes: retention classify, retention policy, retention purge,
 * PII redaction, PII field listing, and compliance summary.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/governance/** HTTP endpoints (DC-E5) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Governance & Data Lifecycle Endpoints (DC-E5)")
class DataCloudHttpServerGovernanceTest {

    private DataCloudClient mockClient;
    private EntityStore mockEntityStore;
    private AuditService mockAuditService;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000
    private final Map<String, Map<String, EntityStore.Entity>> entityState = new ConcurrentHashMap<>(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        entityState.clear(); // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        mockEntityStore = mock(EntityStore.class); // GH-90000
        mockAuditService = mock(AuditService.class); // GH-90000
        when(mockClient.entityStore()).thenReturn(mockEntityStore); // GH-90000
        when(mockAuditService.record(any())).thenReturn(Promise.complete()); // GH-90000
        when(mockClient.findById(any(), any(), any())).thenAnswer(invocation -> { // GH-90000
            String collection = invocation.getArgument(1); // GH-90000
            String entityId = invocation.getArgument(2); // GH-90000
            EntityStore.Entity entity = entityState.getOrDefault(collection, Map.of()).get(entityId); // GH-90000
            if (entity == null) { // GH-90000
                return Promise.of(Optional.empty()); // GH-90000
            }
            return Promise.of(Optional.of(DataCloudClient.Entity.of( // GH-90000
                entity.id().value(), // GH-90000
                entity.collection(), // GH-90000
                entity.data() // GH-90000
            )));
        });
        when(mockEntityStore.save(any(), any())).thenAnswer(invocation -> { // GH-90000
            EntityStore.Entity entity = invocation.getArgument(1); // GH-90000
            if (entity == null) { // GH-90000
                return Promise.of(null); // GH-90000
            }
            entityState.computeIfAbsent(entity.collection(), ignored -> new ConcurrentHashMap<>()) // GH-90000
                .put(entity.id().value(), entity); // GH-90000
            return Promise.of(entity); // GH-90000
        });
        when(mockEntityStore.findById(any(), any())).thenAnswer(invocation -> { // GH-90000
            EntityStore.EntityId entityId = invocation.getArgument(1); // GH-90000
            Optional<EntityStore.Entity> entity = entityState.values().stream() // GH-90000
                .map(collection -> collection.get(entityId.value())) // GH-90000
                .filter(java.util.Objects::nonNull) // GH-90000
                .findFirst(); // GH-90000
            return Promise.of(entity); // GH-90000
        });
        when(mockEntityStore.query(any(), any())).thenAnswer(invocation -> { // GH-90000
            EntityStore.QuerySpec querySpec = invocation.getArgument(1); // GH-90000
            List<EntityStore.Entity> entities = entityState
                .getOrDefault(querySpec.collection(), Map.of()) // GH-90000
                .values() // GH-90000
                .stream() // GH-90000
                .sorted(Comparator.comparing(entity -> entity.id().value())) // GH-90000
                .skip(querySpec.offset()) // GH-90000
                .limit(querySpec.limit()) // GH-90000
                .toList(); // GH-90000
            long total = entityState.getOrDefault(querySpec.collection(), Map.of()).size(); // GH-90000
            return Promise.of(EntityStore.QueryResult.of(entities, total)); // GH-90000
        });
        when(mockEntityStore.deleteBatch(any(), any())).thenAnswer(invocation -> { // GH-90000
            List<EntityStore.EntityId> entityIds = invocation.getArgument(1); // GH-90000
            long deleted = 0;
            for (EntityStore.EntityId entityId : entityIds) { // GH-90000
                for (Map<String, EntityStore.Entity> collection : entityState.values()) { // GH-90000
                    if (collection.remove(entityId.value()) != null) { // GH-90000
                        deleted++;
                        break;
                    }
                }
            }
            return Promise.of(BatchResult.success((int) deleted)); // GH-90000
        });
        when(mockEntityStore.delete(any(), any())).thenAnswer(invocation -> { // GH-90000
            EntityStore.EntityId entityId = invocation.getArgument(1); // GH-90000
            entityState.values().forEach(collection -> collection.remove(entityId.value())); // GH-90000
            return Promise.of(null); // GH-90000
        });
        when(mockEntityStore.count(any(), any())).thenAnswer(invocation -> { // GH-90000
            EntityStore.QuerySpec querySpec = invocation.getArgument(1); // GH-90000
            return Promise.of((long) entityState.getOrDefault(querySpec.collection(), Map.of()).size()); // GH-90000
        });
        when(mockEntityStore.listCollections(any())).thenAnswer(invocation -> { // GH-90000
            List<String> collections = entityState.keySet().stream().sorted().toList(); // GH-90000
            return Promise.of(collections); // GH-90000
        });
        port       = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/governance/retention/classify
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/governance/retention/classify")
    class ClassifyRetentionTests {

        @Test
        @DisplayName("returns 200 and classifies a collection with valid tier")
        @SuppressWarnings("unchecked")
        void validTier_classifiesCollection() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "tier",       "compliance",
                "reason",     "GDPR Article 17"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data.get("tier")).isEqualTo("compliance");
            assertThat(data).containsKey("retentionDays");
            assertThat(data).containsKey("classifiedAt");
        }

        @Test
        @DisplayName("returns error for missing collection")
        @SuppressWarnings("unchecked")
        void missingCollection_returnsErrorBlock() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of("tier", "standard")); // GH-90000
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_COLLECTION");
        }

        @Test
        @DisplayName("returns error for invalid retention tier")
        @SuppressWarnings("unchecked")
        void invalidTier_returnsErrorBlock() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "events",
                "tier",       "extremely-long-tier-that-does-not-exist"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_TIER");
        }

        @Test
        @DisplayName("permanent tier has null expiresAt in response")
        @SuppressWarnings("unchecked")
        void permanentTier_hasNullExpiry() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "audit_logs",
                "tier",       "permanent",
                "reason",     "Legal hold"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("tier")).isEqualTo("permanent");
            // expiresAt is null for permanent — Jackson serializes as absent or null
            assertThat(data.containsKey("expiresAt") && data.get("expiresAt") != null).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/governance/retention/policy
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/governance/retention/policy")
    class GetRetentionPolicyTests {

        @Test
        @DisplayName("returns default policy for a known collection")
        @SuppressWarnings("unchecked")
        void knownCollection_returnsDefaultPolicy() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy?collection=user_profiles");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data).containsKey("tier");
            assertThat(data).containsKey("retentionDays");
            assertThat(data).containsKey("legalHolds");
            assertThat(data).containsKey("piiFields");
            // user_profiles collection should have PII fields derived
            List<?> piiFields = (List<?>) data.get("piiFields");
            assertThat(piiFields).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns error when collection query param is missing")
        @SuppressWarnings("unchecked")
        void missingCollection_returnsError() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
        }

        @Test
        @DisplayName("non-user collection returns empty piiFields list")
        @SuppressWarnings("unchecked")
        void nonUserCollection_emptyPiiFields() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy?collection=pipeline_runs");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            List<?> piiFields = (List<?>) data.get("piiFields");
            assertThat(piiFields).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/governance/retention/purge
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/governance/retention/purge")
    class PurgeTests {

        @Test
        @DisplayName("dry run returns DRY_RUN_COMPLETE status")
        @SuppressWarnings("unchecked")
        void dryRun_returnsDryRunComplete() throws Exception { // GH-90000
            storeEntity(entity("exp-1", "expired_sessions", Map.of("expiresAt", Instant.now().minusSeconds(60).toString()))); // GH-90000
            storeEntity(entity("live-1", "expired_sessions", Map.of("expiresAt", Instant.now().plusSeconds(3600).toString()))); // GH-90000

            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "expired_sessions",
                "dryRun",     true
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("dryRun")).isEqualTo(true);
            assertThat(data.get("status")).isEqualTo("DRY_RUN_COMPLETE");
            assertThat(data.get("estimatedRows")).isEqualTo(1);
            assertThat(data).containsKey("confirmationToken");
            assertThat(data).containsKey("tokenExpiresInSec");
        }

        @Test
        @DisplayName("dry run preview is deterministic for the same dataset")
        @SuppressWarnings("unchecked")
        void dryRunPreview_isDeterministicForSameDataset() throws Exception {
            storeEntity(entity("det-exp-1", "deterministic_sessions", Map.of("expiresAt", Instant.now().minusSeconds(60).toString())));
            storeEntity(entity("det-exp-2", "deterministic_sessions", Map.of("expiresAt", Instant.now().minusSeconds(120).toString())));
            storeEntity(entity("det-live-1", "deterministic_sessions", Map.of("expiresAt", Instant.now().plusSeconds(3600).toString())));

            server = new DataCloudHttpServer(mockClient, port)
                .withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            String dryRunBody = mapper.writeValueAsString(Map.of(
                "collection", "deterministic_sessions",
                "dryRun", true
            ));

            HttpResponse<String> first = post("/api/v1/governance/retention/purge", dryRunBody);
            HttpResponse<String> second = post("/api/v1/governance/retention/purge", dryRunBody);

            assertThat(first.statusCode()).isEqualTo(200);
            assertThat(second.statusCode()).isEqualTo(200);

            Map<String, Object> firstData = (Map<String, Object>) mapper.readValue(first.body(), Map.class).get("data");
            Map<String, Object> secondData = (Map<String, Object>) mapper.readValue(second.body(), Map.class).get("data");

            assertThat(firstData.get("status")).isEqualTo("DRY_RUN_COMPLETE");
            assertThat(secondData.get("status")).isEqualTo("DRY_RUN_COMPLETE");
            assertThat(firstData.get("estimatedRows")).isEqualTo(2);
            assertThat(secondData.get("estimatedRows")).isEqualTo(2);
            assertThat((List<String>) firstData.get("sampleEntityIds"))
                .containsExactlyElementsOf((List<String>) secondData.get("sampleEntityIds"));
        }

        @Test
        @DisplayName("real purge deletes expired entities and returns PURGE_COMPLETED status")
        @SuppressWarnings("unchecked")
        void realPurge_returnsPurgeScheduled() throws Exception { // GH-90000
            storeEntity(entity("old-1", "old_events", Map.of("expiresAt", Instant.now().minusSeconds(300).toString()))); // GH-90000
            storeEntity(entity("old-2", "old_events", Map.of("expiresAt", Instant.now().minusSeconds(120).toString()))); // GH-90000

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // Step 1: perform a dry-run to obtain a valid HMAC-signed confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "old_events",
                "dryRun",     true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody); // GH-90000
            assertThat(dryRunResp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> dryRunData = (Map<String, Object>) // GH-90000
                    mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotBlank(); // GH-90000

            // Step 2: execute the real purge using the signed token
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection",        "old_events",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("PURGE_COMPLETED");
            assertThat(data.get("deletedRows")).isEqualTo(2);
            verify(mockEntityStore).deleteBatch(any(), argThat(ids -> ids.size() == 2)); // GH-90000
            verify(mockAuditService).record(argThat(event -> // GH-90000
                "RETENTION_PURGE".equals(event.eventType()) // GH-90000
                    && sha256Hex(confirmationToken) // GH-90000
                        .equals(event.getDetail("confirmationTokenHash"))
                    && !confirmationToken.equals(event.getDetail("confirmationTokenHash"))));

            HttpResponse<String> deletedEntityResponse = get("/api/v1/entities/old_events/old-1");

            assertThat(deletedEntityResponse.statusCode()).isEqualTo(404); // GH-90000
            assertThat(entityState.getOrDefault("_governance_purge_tombstones", Map.of()).values()) // GH-90000
                .anySatisfy(tombstone -> { // GH-90000
                    assertThat(tombstone.data().get("collection")).isEqualTo("old_events");
                    assertThat(tombstone.data().get("deletedCount")).isEqualTo(2);
                });
        }

        @Test
        @DisplayName("repeating purge execute with same token is safe and returns zero additional deletions")
        @SuppressWarnings("unchecked")
        void repeatedExecuteWithSameToken_isSafeAndIdempotent() throws Exception {
            storeEntity(entity("dup-1", "idempotent_events", Map.of("expiresAt", Instant.now().minusSeconds(180).toString())));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            String dryRunBody = mapper.writeValueAsString(Map.of(
                "collection", "idempotent_events",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody);
            assertThat(dryRunResp.statusCode()).isEqualTo(200);
            Map<String, Object> dryRunData = (Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");

            String executeBody = mapper.writeValueAsString(Map.of(
                "collection", "idempotent_events",
                "confirmationToken", confirmationToken
            ));

            HttpResponse<String> firstExecute = post("/api/v1/governance/retention/purge", executeBody);
            assertThat(firstExecute.statusCode()).isEqualTo(200);
            Map<String, Object> firstData = (Map<String, Object>) mapper.readValue(firstExecute.body(), Map.class).get("data");
            assertThat(firstData.get("status")).isEqualTo("PURGE_COMPLETED");
            assertThat(firstData.get("deletedRows")).isEqualTo(1);

            HttpResponse<String> secondExecute = post("/api/v1/governance/retention/purge", executeBody);
            assertThat(secondExecute.statusCode()).isEqualTo(200);
            Map<String, Object> secondData = (Map<String, Object>) mapper.readValue(secondExecute.body(), Map.class).get("data");
            assertThat(secondData.get("status")).isEqualTo("PURGE_COMPLETED");
            assertThat(secondData.get("deletedRows")).isEqualTo(0);
            verify(mockEntityStore).deleteBatch(any(), argThat(ids -> ids.size() == 1));
        }

        @Test
        @DisplayName("real purge submits 1000+ expired entities as a single batch delete")
        void realPurge_largeBatch_usesDeleteBatchOnce() throws Exception { // GH-90000
            for (int index = 0; index < 1_200; index++) { // GH-90000
                storeEntity(entity( // GH-90000
                    "bulk-" + index,
                    "bulk_expired_events",
                    Map.of("expiresAt", Instant.now().minusSeconds(600 + index).toString()))); // GH-90000
            }

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String dryRunBody = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "bulk_expired_events",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody); // GH-90000
            assertThat(dryRunResp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> dryRunData = (Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");

            String purgeBody = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "bulk_expired_events",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", purgeBody); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("data");
            assertThat(data.get("status")).isEqualTo("PURGE_COMPLETED");
            assertThat(data.get("deletedRows")).isEqualTo(1_200);
            verify(mockEntityStore).deleteBatch(any(), argThat(ids -> ids.size() == 1_200)); // GH-90000
            assertThat(entityState.getOrDefault("bulk_expired_events", Map.of())).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("missing confirmationToken returns error")
        @SuppressWarnings("unchecked")
        void missingToken_returnsError() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of("collection", "old_events")); // GH-90000
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_CONFIRMATION");
        }

        @Test
        @DisplayName("invalid confirmationToken returns 403 with INVALID_CONFIRMATION_TOKEN")
        @SuppressWarnings("unchecked")
        void invalidToken_returnsForbidden() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "old_events",
                "confirmationToken", "invalid-token"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_CONFIRMATION_TOKEN");
        }

        @Test
        @DisplayName("production profile without purge token secret returns 503")
        @SuppressWarnings("unchecked")
        void missingPurgeSecretInProduction_returnsServiceUnavailable() throws Exception { // GH-90000
            String previousProfile = System.getProperty("DATACLOUD_PROFILE");
            String previousSecret = System.getProperty("DATACLOUD_PURGE_TOKEN_SECRET");
            try {
                System.setProperty("DATACLOUD_PROFILE", "production"); // GH-90000
                System.clearProperty("DATACLOUD_PURGE_TOKEN_SECRET");

                server = new DataCloudHttpServer(mockClient, port); // GH-90000
                server.start(); // GH-90000
                waitForServerReady(port); // GH-90000

                String body = mapper.writeValueAsString(Map.of( // GH-90000
                    "collection", "old_events",
                    "dryRun", true
                ));
                HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); // GH-90000

                assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
                Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
                assertThat(respBody).containsKey("error");
                Map<String, Object> error = (Map<String, Object>) respBody.get("error");
                assertThat(error.get("code")).isEqualTo("PURGE_TOKEN_SECRET_REQUIRED");
            } finally {
                restoreSystemProperty("DATACLOUD_PROFILE", previousProfile); // GH-90000
                restoreSystemProperty("DATACLOUD_PURGE_TOKEN_SECRET", previousSecret); // GH-90000
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/governance/privacy/redact
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/governance/privacy/redact")
    class RedactTests {

        @Test
        @DisplayName("redacts specified PII fields and returns REDACTED status")
        @SuppressWarnings("unchecked")
        void specififiedFields_redacted() throws Exception { // GH-90000
            EntityStore.Entity existingEntity = entity( // GH-90000
                "ent-abc123",
                "user_profiles",
                Map.of("email", "user@example.com", "phone", "+1-555-0101", "role", "admin") // GH-90000
            );
            storeEntity(existingEntity); // GH-90000

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // Perform dry-run first to get confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "entityId",   "ent-abc123",
                "fields",     List.of("email", "phone"), // GH-90000
                "reason",     "GDPR erasure request",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/privacy/redact", dryRunBody); // GH-90000
            assertThat(dryRunResp.statusCode()).isEqualTo(200);
            Map<String, Object> dryRunRespBody = mapper.readValue(dryRunResp.body(), Map.class);
            Map<String, Object> dryRunData = (Map<String, Object>) dryRunRespBody.get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotNull();

            // Use the token to perform actual redaction
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "entityId",   "ent-abc123",
                "fields",     List.of("email", "phone"), // GH-90000
                "reason",     "GDPR erasure request",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data.get("entityId")).isEqualTo("ent-abc123");
            assertThat(data.get("status")).isEqualTo("REDACTED");
            assertThat(data).containsKey("redactedFields");
            List<String> redactedFields = (List<String>) data.get("redactedFields");
            assertThat(redactedFields).containsExactlyInAnyOrder("email", "phone"); // GH-90000
            verify(mockEntityStore).save(any(), argThat(entity -> // GH-90000
                "[REDACTED]".equals(entity.data().get("email"))
                    && "[REDACTED]".equals(entity.data().get("phone"))
                    && "admin".equals(entity.data().get("role"))));
            verify(mockAuditService).record(argThat(event -> { // GH-90000
                if (!"PII_REDACT".equals(event.eventType())) { // GH-90000
                    return false;
                }
                Object rawHashes = event.getDetail("previousValueHashes");
                if (!(rawHashes instanceof Map<?, ?> hashes)) { // GH-90000
                    return false;
                }
                return sha256Hex("user@example.com").equals(hashes.get("email"))
                    && sha256Hex("+1-555-0101").equals(hashes.get("phone"))
                    && !hashes.containsValue("user@example.com")
                    && !hashes.containsValue("+1-555-0101");
            }));

                    HttpResponse<String> getResponse = get("/api/v1/entities/user_profiles/ent-abc123");

                    assertThat(getResponse.statusCode()).isEqualTo(200); // GH-90000
                    Map<String, Object> getBody = mapper.readValue(getResponse.body(), Map.class); // GH-90000
                    Map<String, Object> entityData = (Map<String, Object>) getBody.get("data");
                    assertThat(entityData.get("email")).isEqualTo("[REDACTED]");
                    assertThat(entityData.get("phone")).isEqualTo("[REDACTED]");
                    assertThat(entityData.get("role")).isEqualTo("admin");
        }

        @Test
        @DisplayName("omitting fields requests all global PII fields and redacts present values")
        @SuppressWarnings("unchecked")
        void noFields_defaultsToAllPiiFields() throws Exception { // GH-90000
            EntityStore.Entity existingEntity = entity( // GH-90000
                "ent-xyz789",
                "customers",
                Map.of("email", "customer@example.com", "phone", "+1-555-0102", "ssn", "123-45-6789") // GH-90000
            );
            storeEntity(existingEntity); // GH-90000

            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // Perform dry-run first to get confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "customers",
                "entityId",   "ent-xyz789",
                "reason",     "Platform-initiated GDPR sweep",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/privacy/redact", dryRunBody); // GH-90000
            assertThat(dryRunResp.statusCode()).isEqualTo(200);
            Map<String, Object> dryRunRespBody = mapper.readValue(dryRunResp.body(), Map.class);
            Map<String, Object> dryRunData = (Map<String, Object>) dryRunRespBody.get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotNull();

            // Use the token to perform actual redaction
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "customers",
                "entityId",   "ent-xyz789",
                "reason",     "Platform-initiated GDPR sweep",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            List<String> redactedFields = (List<String>) data.get("redactedFields");
            List<String> requestedFields = (List<String>) data.get("requestedFields");
            assertThat(requestedFields.size()).isGreaterThanOrEqualTo(9); // GH-90000
            assertThat(redactedFields).containsExactlyInAnyOrder("email", "phone", "ssn"); // GH-90000
        }

        @Test
        @DisplayName("missing entityId returns MISSING_REQUIRED error")
        @SuppressWarnings("unchecked")
        void missingEntityId_returnsError() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of("collection", "user_profiles")); // GH-90000
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_REQUIRED");
        }

        @Test
        @DisplayName("unknown entity returns 404 ENTITY_NOT_FOUND")
        @SuppressWarnings("unchecked")
        void unknownEntity_returnsNotFound() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "entityId", "ent-missing",
                "fields", List.of("email")
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("ENTITY_NOT_FOUND");
        }
    }

    @Test
    @DisplayName("executes classify, policy, redact, and purge as one governance lifecycle")
    @SuppressWarnings("unchecked")
    void governanceLifecycleExecutesAcrossRetentionAndPrivacyEndpoints() throws Exception { // GH-90000
        EntityStore.Entity existingEntity = entity( // GH-90000
            "ent-lifecycle",
            "user_profiles",
            Map.of("email", "person@example.com", "phone", "+1-555-0101", "name", "Person") // GH-90000
        );
        storeEntity(existingEntity); // GH-90000
        storeEntity(entity("expired-1", "user_profiles", Map.of("expiresAt", Instant.now().minusSeconds(180).toString()))); // GH-90000

        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withAuditService(mockAuditService); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> classifyResponse = post( // GH-90000
            "/api/v1/governance/retention/classify",
            mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "tier", "compliance",
                "reason", "lifecycle-test"
            )));
        assertThat(classifyResponse.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> policyResponse = get("/api/v1/governance/retention/policy?collection=user_profiles");
        assertThat(policyResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> policyBody = mapper.readValue(policyResponse.body(), Map.class); // GH-90000
        Map<String, Object> policyData = (Map<String, Object>) policyBody.get("data");
        assertThat(policyData.get("tier")).isEqualTo("compliance");

        // Perform dry-run first to get confirmation token
        HttpResponse<String> redactDryRunResponse = post( // GH-90000
            "/api/v1/governance/privacy/redact",
            mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "entityId", "ent-lifecycle",
                "fields", List.of("email", "phone"), // GH-90000
                "reason", "customer-erasure",
                "dryRun", true
            )));
        assertThat(redactDryRunResponse.statusCode()).isEqualTo(200);
        Map<String, Object> redactDryRunBody = mapper.readValue(redactDryRunResponse.body(), Map.class);
        Map<String, Object> redactDryRunData = (Map<String, Object>) redactDryRunBody.get("data");
        String redactConfirmationToken = (String) redactDryRunData.get("confirmationToken");
        if (redactConfirmationToken == null) {
            System.out.println("Redact dry-run response body: " + redactDryRunResponse.body());
        }
        assertThat(redactConfirmationToken).isNotNull();

        // Use the token to perform actual redaction
        HttpResponse<String> redactResponse = post( // GH-90000
            "/api/v1/governance/privacy/redact",
            mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "entityId", "ent-lifecycle",
                "fields", List.of("email", "phone"), // GH-90000
                "reason", "customer-erasure",
                "confirmationToken", redactConfirmationToken
            )));
        if (redactResponse.statusCode() != 200) {
            System.out.println("Redact response body: " + redactResponse.body());
        }
        assertThat(redactResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> redactBody = mapper.readValue(redactResponse.body(), Map.class); // GH-90000
        Map<String, Object> redactData = (Map<String, Object>) redactBody.get("data");
        assertThat(redactData.get("status")).isEqualTo("REDACTED");

        HttpResponse<String> dryRunResponse = post( // GH-90000
            "/api/v1/governance/retention/purge",
            mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "dryRun", true
            )));
        assertThat(dryRunResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> dryRunBody = mapper.readValue(dryRunResponse.body(), Map.class); // GH-90000
        String confirmationToken = (String) ((Map<String, Object>) dryRunBody.get("data")).get("confirmationToken");
        assertThat(confirmationToken).isNotBlank(); // GH-90000

        HttpResponse<String> purgeResponse = post( // GH-90000
            "/api/v1/governance/retention/purge",
            mapper.writeValueAsString(Map.of( // GH-90000
                "collection", "user_profiles",
                "confirmationToken", confirmationToken
            )));
        assertThat(purgeResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<String, Object> purgeBody = mapper.readValue(purgeResponse.body(), Map.class); // GH-90000
        Map<String, Object> purgeData = (Map<String, Object>) purgeBody.get("data");
        assertThat(purgeData.get("status")).isEqualTo("PURGE_COMPLETED");
        assertThat(purgeData.get("deletedRows")).isEqualTo(1);

        HttpResponse<String> deletedEntityResponse = get("/api/v1/entities/user_profiles/expired-1");
        assertThat(deletedEntityResponse.statusCode()).isEqualTo(404); // GH-90000
        assertThat(entityState.getOrDefault("_governance_purge_tombstones", Map.of()).values()) // GH-90000
            .anySatisfy(tombstone -> { // GH-90000
                assertThat(tombstone.data().get("collection")).isEqualTo("user_profiles");
                assertThat(tombstone.data().get("deletedCount")).isEqualTo(1);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/governance/privacy/pii-fields
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/governance/privacy/pii-fields")
    class ListPiiFieldsTests {

        @Test
        @DisplayName("returns global PII field registry")
        @SuppressWarnings("unchecked")
        void returns200WithGlobalFields() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/privacy/pii-fields");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data).containsKey("globalFields");
            assertThat(data).containsKey("tenantFields");
            assertThat(data).containsKey("effectiveCount");
            List<String> globalFields = (List<String>) data.get("globalFields");
            assertThat(globalFields).contains("email", "phone", "ssn"); // GH-90000
            assertThat(((Number) data.get("effectiveCount")).intValue()).isGreaterThanOrEqualTo(9);
        }

        @Test
        @DisplayName("collection query reports auto-detected common PII fields")
        @SuppressWarnings("unchecked")
        void collectionQuery_reportsAutoDetectedFields() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/privacy/pii-fields?collection=user_profiles");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            List<String> autoDetectedFields = (List<String>) data.get("autoDetectedFields");
            assertThat(autoDetectedFields).contains("email", "phone", "ssn"); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/v1/governance/privacy/verify")
    class VerifyRedactionTests {

        @Test
        @DisplayName("returns VERIFIED when requested PII fields are already redacted")
        @SuppressWarnings("unchecked")
        void verifyReportsVerified() throws Exception { // GH-90000
            storeEntity(entity( // GH-90000
                "ent-verified",
                "user_profiles",
                Map.of("email", "[REDACTED]", "phone", "[REDACTED]", "role", "admin") // GH-90000
            ));

            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/privacy/verify?collection=user_profiles&entityId=ent-verified&fields=email,phone");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("VERIFIED");
            assertThat((List<String>) data.get("verifiedFields")).containsExactly("email", "phone");
            assertThat((List<String>) data.get("pendingFields")).isEmpty();
        }

        @Test
        @DisplayName("returns NOT_REDACTED when sensitive fields are still present")
        @SuppressWarnings("unchecked")
        void verifyReportsPendingFields() throws Exception { // GH-90000
            storeEntity(entity( // GH-90000
                "ent-pending",
                "user_profiles",
                Map.of("email", "person@example.com", "phone", "[REDACTED]", "role", "admin") // GH-90000
            ));

            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/privacy/verify?collection=user_profiles&entityId=ent-pending&fields=email,phone");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("NOT_REDACTED");
            assertThat((List<String>) data.get("verifiedFields")).containsExactly("phone");
            assertThat((List<String>) data.get("pendingFields")).containsExactly("email");
        }

        @Test
        @DisplayName("returns 404 when entity does not exist")
        @SuppressWarnings("unchecked")
        void verifyMissingEntity_returnsNotFound() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/privacy/verify?collection=user_profiles&entityId=ent-missing&fields=email,phone");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("ENTITY_NOT_FOUND");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/governance/compliance/summary
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/governance/compliance/summary")
    class ComplianceSummaryTests {

        @Test
        @DisplayName("returns tenant compliance summary with all required fields")
        @SuppressWarnings("unchecked")
        void returns200WithComplianceSummary() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/governance/compliance/summary");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data).containsKey("tenantId");
            assertThat(data).containsKey("collectionsTotal");
            assertThat(data).containsKey("piiFieldsRegistered");
            assertThat(data).containsKey("legalHoldsActive");
            assertThat(data).containsKey("complianceStatus");
            assertThat(data).containsKey("generatedAt");
        }

        @Test
        @DisplayName("honours X-Tenant-Id header in compliance summary")
        @SuppressWarnings("unchecked")
        void tenantIdHeader_presentInSummary() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary")) // GH-90000
                .header("X-Tenant-Id", "acme-corp") // GH-90000
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<String, Object> meta = (Map<String, Object>) respBody.get("meta");
            if (meta != null) { // GH-90000
                assertThat(meta.get("tenantId")).isEqualTo("acme-corp");
            }
        }

        @Test
        @DisplayName("includes audit query dimensions in recent compliance events when audit summary provider is available")
        @SuppressWarnings("unchecked")
        void complianceSummary_includesRecentAuditDimensions() throws Exception {
            AuditService auditSummaryService = mock(AuditService.class, withSettings().extraInterfaces(AuditSummaryProvider.class));
            when(auditSummaryService.record(any())).thenReturn(Promise.complete());

            AuditSummaryProvider auditSummaryProvider = (AuditSummaryProvider) auditSummaryService;
            when(auditSummaryProvider.summarize(any(), any(Instant.class), eq(500)))
                .thenReturn(Promise.of(new AuditSummaryProvider.AuditSummary(
                    Instant.parse("2026-04-30T10:15:30Z"),
                    Map.of("RETENTION_PURGE", 2L, "PII_REDACT", 1L),
                    List.of(Map.of(
                        "eventType", "RETENTION_PURGE",
                        "resourceType", "GOVERNANCE",
                        "resourceId", "retention-policy-main",
                        "details", Map.of("correlationId", "corr-123", "requestId", "req-123")
                    ))
                )));

            server = new DataCloudHttpServer(mockClient, port)
                .withAuditService(auditSummaryService);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/compliance/summary");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            assertThat(data.get("lastAuditAt")).isEqualTo("2026-04-30T10:15:30Z");
            assertThat(((Number) data.get("auditEventsIn30Days")).longValue()).isEqualTo(3L);

            List<Map<String, Object>> recentAuditEvents = (List<Map<String, Object>>) data.get("recentAuditEvents");
            assertThat(recentAuditEvents).hasSize(1);
            Map<String, Object> event = recentAuditEvents.get(0);
            assertThat(event).containsEntry("eventType", "RETENTION_PURGE");
            assertThat(event).containsEntry("resourceType", "GOVERNANCE");
            assertThat(event).containsEntry("resourceId", "retention-policy-main");
            Map<String, Object> details = (Map<String, Object>) event.get("details");
            assertThat(details).containsEntry("correlationId", "corr-123");
            assertThat(details).containsEntry("requestId", "req-123");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("X-Tenant-Id", "tenant-test") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .header("X-Tenant-Id", "tenant-test") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port); // GH-90000
    }

    private static EntityStore.Entity entity(String id, String collection, Map<String, Object> data) { // GH-90000
        return new EntityStore.Entity( // GH-90000
            EntityStore.EntityId.of(id), // GH-90000
            collection,
            data,
            EntityStore.EntityMetadata.empty() // GH-90000
        );
    }

    private void storeEntity(EntityStore.Entity entity) { // GH-90000
        entityState.computeIfAbsent(entity.collection(), ignored -> new ConcurrentHashMap<>()) // GH-90000
            .put(entity.id().value(), entity); // GH-90000
    }

    private static String sha256Hex(String value) { // GH-90000
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))); // GH-90000
        } catch (NoSuchAlgorithmException e) { // GH-90000
            throw new IllegalStateException("SHA-256 unavailable", e); // GH-90000
        }
    }

    private static void restoreSystemProperty(String key, String value) { // GH-90000
        if (value == null) { // GH-90000
            System.clearProperty(key); // GH-90000
        } else {
            System.setProperty(key, value); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // P0-04: Cross-tenant enforcement at HTTP server level
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that when the security filter is active (API key auth enabled)
     * a caller whose API key is bound to tenant-A cannot access tenant-B resources.
     */
    @Nested
    @DisplayName("Cross-tenant enforcement (P0-04)")
    class CrossTenantEnforcementTests {

        private static final String TENANT_A_KEY = "key-tenant-a";
        private static final String TENANT_A     = "tenant-a";
        private static final String TENANT_B     = "tenant-b";

        private ApiKeyResolver tenantAResolver; // GH-90000
        private PolicyEngine permissivePolicyEngine; // GH-90000

        @BeforeEach
        void setUpResolver() { // GH-90000
            tenantAResolver = mock(ApiKeyResolver.class); // GH-90000
            permissivePolicyEngine = mock(PolicyEngine.class); // GH-90000
            when(tenantAResolver.resolve(TENANT_A_KEY))
                .thenReturn(Optional.of(new Principal("svc-tenant-a", List.of("admin"), TENANT_A))); // GH-90000
            when(permissivePolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(Boolean.TRUE)); // GH-90000
            when(mockAuditService.record(any())).thenReturn(Promise.complete()); // GH-90000
        }

        @Test
        @DisplayName("API-key principal for tenant-A receives 403 when X-Tenant-ID is tenant-B")
        @SuppressWarnings("unchecked")
        void crossTenantRequestReturnsForbidden() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port)
                .withApiKeyResolver(tenantAResolver)
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // Request claims tenant-B but API key belongs to tenant-A
            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-API-Key", TENANT_A_KEY)
                .header("X-Tenant-ID", TENANT_B) // GH-90000
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).containsKey("error"); // GH-90000
        }

        @Test
        @DisplayName("API-key principal for tenant-A receives 200 when X-Tenant-ID matches")
        @SuppressWarnings("unchecked")
        void sameTenanRequestIsAllowed() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port)
                .withApiKeyResolver(tenantAResolver)
                .withPolicyEngine(permissivePolicyEngine)
                .withAuditService(mockAuditService); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-API-Key", TENANT_A_KEY)
                .header("X-Tenant-ID", TENANT_A) // GH-90000
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }
    }
}
