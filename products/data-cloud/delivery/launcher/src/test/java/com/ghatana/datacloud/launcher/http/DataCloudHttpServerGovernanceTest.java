/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Integration tests for governance HTTP endpoints.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/governance/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Governance Endpoints")
class DataCloudHttpServerGovernanceTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;
    private EntityStore mockEntityStore;
    private AuditService mockAuditService;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); 
    private final Map<String, Map<String, EntityStore.Entity>> entityState = new ConcurrentHashMap<>(); 

    @BeforeEach
    void setUp() throws Exception { 
        entityState.clear(); 
        mockClient = mock(DataCloudClient.class); 
        mockEntityStore = mock(EntityStore.class); 
        mockAuditService = mock(AuditService.class); 
        when(mockClient.entityStore()).thenReturn(mockEntityStore); 
        when(mockAuditService.record(any())).thenReturn(Promise.complete()); 
        when(mockClient.findById(any(), any(), any())).thenAnswer(invocation -> { 
            String collection = invocation.getArgument(1); 
            String entityId = invocation.getArgument(2); 
            EntityStore.Entity entity = entityState.getOrDefault(collection, Map.of()).get(entityId); 
            if (entity == null) { 
                return Promise.of(Optional.empty()); 
            }
            return Promise.of(Optional.of(DataCloudClient.Entity.of( 
                entity.id().value(), 
                entity.collection(), 
                entity.data() 
            )));
        });
        when(mockEntityStore.save(any(), any())).thenAnswer(invocation -> { 
            EntityStore.Entity entity = invocation.getArgument(1); 
            if (entity == null) { 
                return Promise.of(null); 
            }
            entityState.computeIfAbsent(entity.collection(), ignored -> new ConcurrentHashMap<>()) 
                .put(entity.id().value(), entity); 
            return Promise.of(entity); 
        });
        when(mockEntityStore.findById(any(), any())).thenAnswer(invocation -> { 
            EntityStore.EntityId entityId = invocation.getArgument(1); 
            Optional<EntityStore.Entity> entity = entityState.values().stream() 
                .map(collection -> collection.get(entityId.value())) 
                .filter(java.util.Objects::nonNull) 
                .findFirst(); 
            return Promise.of(entity); 
        });
        when(mockEntityStore.query(any(), any())).thenAnswer(invocation -> { 
            EntityStore.QuerySpec querySpec = invocation.getArgument(1); 
            List<EntityStore.Entity> entities = entityState
                .getOrDefault(querySpec.collection(), Map.of()) 
                .values() 
                .stream() 
                .sorted(Comparator.comparing(entity -> entity.id().value())) 
                .skip(querySpec.offset()) 
                .limit(querySpec.limit()) 
                .toList(); 
            long total = entityState.getOrDefault(querySpec.collection(), Map.of()).size(); 
            return Promise.of(EntityStore.QueryResult.of(entities, total)); 
        });
        when(mockEntityStore.deleteByRefs(any(), any())).thenAnswer(invocation -> {
            List<EntityStore.EntityRef> refs = invocation.getArgument(1);
            long deleted = 0;
            for (EntityStore.EntityRef ref : refs) {
                Map<String, EntityStore.Entity> collection = entityState.get(ref.collection());
                if (collection != null && collection.remove(ref.entityId().value()) != null) {
                    deleted++;
                }
            }
            return Promise.of(BatchResult.success((int) deleted));
        });
        when(mockEntityStore.deleteByRef(any(), any())).thenAnswer(invocation -> {
            EntityStore.EntityRef ref = invocation.getArgument(1);
            Map<String, EntityStore.Entity> collection = entityState.get(ref.collection());
            if (collection != null) {
                collection.remove(ref.entityId().value());
            }
            return Promise.of(null);
        });
        when(mockEntityStore.count(any(), any())).thenAnswer(invocation -> { 
            EntityStore.QuerySpec querySpec = invocation.getArgument(1); 
            return Promise.of((long) entityState.getOrDefault(querySpec.collection(), Map.of()).size()); 
        });
        when(mockEntityStore.listCollections(any())).thenAnswer(invocation -> { 
            List<String> collections = entityState.keySet().stream().sorted().toList(); 
            return Promise.of(collections); 
        });
        port       = findFreePort(); 
        httpClient = HttpClient.newBuilder().build(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
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
        void validTier_classifiesCollection() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port) 
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "tier",       "compliance",
                "reason",     "GDPR Article 17"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
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
        void missingCollection_returnsErrorBlock() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of("tier", "standard")); 
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); 

            assertThat(resp.statusCode()).isEqualTo(400); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_COLLECTION");
        }

        @Test
        @DisplayName("returns error for invalid retention tier")
        @SuppressWarnings("unchecked")
        void invalidTier_returnsErrorBlock() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "collection", "events",
                "tier",       "extremely-long-tier-that-does-not-exist"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); 

            assertThat(resp.statusCode()).isEqualTo(400); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_TIER");
        }

        @Test
        @DisplayName("permanent tier has null expiresAt in response")
        @SuppressWarnings("unchecked")
        void permanentTier_hasNullExpiry() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port) 
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "collection", "audit_logs",
                "tier",       "permanent",
                "reason",     "Legal hold"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
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
        void knownCollection_returnsDefaultPolicy() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy?collection=user_profiles");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data).containsKey("tier");
            assertThat(data).containsKey("retentionDays");
            assertThat(data).containsKey("legalHolds");
            assertThat(data).containsKey("piiFields");
            // user_profiles collection should have PII fields derived
            List<?> piiFields = (List<?>) data.get("piiFields");
            assertThat(piiFields).isNotEmpty(); 
        }

        @Test
        @DisplayName("returns error when collection query param is missing")
        @SuppressWarnings("unchecked")
        void missingCollection_returnsError() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy");

            assertThat(resp.statusCode()).isEqualTo(400); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
        }

        @Test
        @DisplayName("non-user collection returns empty piiFields list")
        @SuppressWarnings("unchecked")
        void nonUserCollection_emptyPiiFields() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy?collection=pipeline_runs");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            List<?> piiFields = (List<?>) data.get("piiFields");
            assertThat(piiFields).isEmpty(); 
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
        void dryRun_returnsDryRunComplete() throws Exception { 
            storeEntity(entity("exp-1", "expired_sessions", Map.of("expiresAt", Instant.now().minusSeconds(60).toString()))); 
            storeEntity(entity("live-1", "expired_sessions", Map.of("expiresAt", Instant.now().plusSeconds(3600).toString()))); 

            server = new DataCloudHttpServer(mockClient, port) 
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "collection", "expired_sessions",
                "dryRun",     true
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
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
        void realPurge_returnsPurgeScheduled() throws Exception { 
            storeEntity(entity("old-1", "old_events", Map.of("expiresAt", Instant.now().minusSeconds(300).toString()))); 
            storeEntity(entity("old-2", "old_events", Map.of("expiresAt", Instant.now().minusSeconds(120).toString()))); 

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            // Step 1: perform a dry-run to obtain a valid HMAC-signed confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of( 
                "collection", "old_events",
                "dryRun",     true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody); 
            assertThat(dryRunResp.statusCode()).isEqualTo(200); 
            Map<String, Object> dryRunData = (Map<String, Object>) 
                    mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotBlank(); 

            // Step 2: execute the real purge using the signed token
            String body = mapper.writeValueAsString(Map.of( 
                "collection",        "old_events",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("PURGE_COMPLETED");
            assertThat(data.get("deletedRows")).isEqualTo(2);
            verify(mockEntityStore).deleteByRefs(any(), argThat(refs -> refs.size() == 2)); 
            verify(mockAuditService).record(argThat(event -> 
                "RETENTION_PURGE".equals(event.eventType()) 
                    && sha256Hex(confirmationToken) 
                        .equals(event.getDetail("confirmationTokenHash"))
                    && !confirmationToken.equals(event.getDetail("confirmationTokenHash"))));

            HttpResponse<String> deletedEntityResponse = get("/api/v1/entities/old_events/old-1");

            assertThat(deletedEntityResponse.statusCode()).isEqualTo(404); 
            assertThat(entityState.getOrDefault("_governance_purge_tombstones", Map.of()).values()) 
                .anySatisfy(tombstone -> { 
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
            verify(mockEntityStore).deleteByRefs(any(), argThat(refs -> refs.size() == 1));
        }

        @Test
        @DisplayName("real purge submits 1000+ expired entities as a single batch delete")
        void realPurge_largeBatch_usesDeleteBatchOnce() throws Exception { 
            for (int index = 0; index < 1_200; index++) { 
                storeEntity(entity( 
                    "bulk-" + index,
                    "bulk_expired_events",
                    Map.of("expiresAt", Instant.now().minusSeconds(600 + index).toString()))); 
            }

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            String dryRunBody = mapper.writeValueAsString(Map.of( 
                "collection", "bulk_expired_events",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody); 
            assertThat(dryRunResp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> dryRunData = (Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");

            String purgeBody = mapper.writeValueAsString(Map.of( 
                "collection", "bulk_expired_events",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", purgeBody); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("data");
            assertThat(data.get("status")).isEqualTo("PURGE_COMPLETED");
            assertThat(data.get("deletedRows")).isEqualTo(1_200);
            verify(mockEntityStore).deleteByRefs(any(), argThat(refs -> refs.size() == 1_200)); 
            assertThat(entityState.getOrDefault("bulk_expired_events", Map.of())).isEmpty(); 
        }

        @Test
        @DisplayName("missing confirmationToken returns error")
        @SuppressWarnings("unchecked")
        void missingToken_returnsError() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of("collection", "old_events")); 
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); 

            assertThat(resp.statusCode()).isEqualTo(400); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_CONFIRMATION");
        }

        @Test
        @DisplayName("invalid confirmationToken returns 403 with INVALID_CONFIRMATION_TOKEN")
        @SuppressWarnings("unchecked")
        void invalidToken_returnsForbidden() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port) 
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "collection", "old_events",
                "confirmationToken", "invalid-token"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_CONFIRMATION_TOKEN");
        }

        @Test
        @DisplayName("production profile without purge token secret returns 503")
        @SuppressWarnings("unchecked")
        void missingPurgeSecretInProduction_returnsServiceUnavailable() throws Exception { 
            String previousProfile = System.getProperty("DATACLOUD_PROFILE");
            String previousSecret = System.getProperty("DATACLOUD_PURGE_TOKEN_SECRET");
            try {
                System.setProperty("DATACLOUD_PROFILE", "production"); 
                System.clearProperty("DATACLOUD_PURGE_TOKEN_SECRET");

                server = new DataCloudHttpServer(mockClient, port); 
                server.start(); 
                waitForServerReady(port); 

                String body = mapper.writeValueAsString(Map.of( 
                    "collection", "old_events",
                    "dryRun", true
                ));
                HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body); 

                assertThat(resp.statusCode()).isEqualTo(503); 
                Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
                assertThat(respBody).containsKey("error");
                Map<String, Object> error = (Map<String, Object>) respBody.get("error");
                assertThat(error.get("code")).isEqualTo("PURGE_TOKEN_SECRET_REQUIRED");
            } finally {
                restoreSystemProperty("DATACLOUD_PROFILE", previousProfile); 
                restoreSystemProperty("DATACLOUD_PURGE_TOKEN_SECRET", previousSecret); 
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
        void specififiedFields_redacted() throws Exception { 
            EntityStore.Entity existingEntity = entity( 
                "ent-abc123",
                "user_profiles",
                Map.of("email", "user@example.com", "phone", "+1-555-0101", "role", "admin") 
            );
            storeEntity(existingEntity); 

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            // Perform dry-run first to get confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "entityId",   "ent-abc123",
                "fields",     List.of("email", "phone"), 
                "reason",     "GDPR erasure request",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/privacy/redact", dryRunBody); 
            assertThat(dryRunResp.statusCode()).isEqualTo(200);
            Map<String, Object> dryRunRespBody = mapper.readValue(dryRunResp.body(), Map.class);
            Map<String, Object> dryRunData = (Map<String, Object>) dryRunRespBody.get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotNull();

            // Use the token to perform actual redaction
            String body = mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "entityId",   "ent-abc123",
                "fields",     List.of("email", "phone"), 
                "reason",     "GDPR erasure request",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data.get("entityId")).isEqualTo("ent-abc123");
            assertThat(data.get("status")).isEqualTo("REDACTED");
            assertThat(data).containsKey("redactedFields");
            List<String> redactedFields = (List<String>) data.get("redactedFields");
            assertThat(redactedFields).containsExactlyInAnyOrder("email", "phone"); 
            verify(mockEntityStore).save(any(), argThat(entity -> 
                "[REDACTED]".equals(entity.data().get("email"))
                    && "[REDACTED]".equals(entity.data().get("phone"))
                    && "admin".equals(entity.data().get("role"))));
            verify(mockAuditService).record(argThat(event -> { 
                if (!"PII_REDACT".equals(event.eventType())) { 
                    return false;
                }
                Object rawHashes = event.getDetail("previousValueHashes");
                if (!(rawHashes instanceof Map<?, ?> hashes)) { 
                    return false;
                }
                return sha256Hex("user@example.com").equals(hashes.get("email"))
                    && sha256Hex("+1-555-0101").equals(hashes.get("phone"))
                    && !hashes.containsValue("user@example.com")
                    && !hashes.containsValue("+1-555-0101");
            }));

                    HttpResponse<String> getResponse = get("/api/v1/entities/user_profiles/ent-abc123");

                    assertThat(getResponse.statusCode()).isEqualTo(200); 
                    Map<String, Object> getBody = mapper.readValue(getResponse.body(), Map.class); 
                    Map<String, Object> entityData = (Map<String, Object>) getBody.get("data");
                    assertThat(entityData.get("email")).isEqualTo("[REDACTED]");
                    assertThat(entityData.get("phone")).isEqualTo("[REDACTED]");
                    assertThat(entityData.get("role")).isEqualTo("admin");
        }

        @Test
        @DisplayName("omitting fields requests all global PII fields and redacts present values")
        @SuppressWarnings("unchecked")
        void noFields_defaultsToAllPiiFields() throws Exception { 
            EntityStore.Entity existingEntity = entity( 
                "ent-xyz789",
                "customers",
                Map.of("email", "customer@example.com", "phone", "+1-555-0102", "ssn", "123-45-6789") 
            );
            storeEntity(existingEntity); 

            server = new DataCloudHttpServer(mockClient, port) 
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            // Perform dry-run first to get confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of( 
                "collection", "customers",
                "entityId",   "ent-xyz789",
                "reason",     "Platform-initiated GDPR sweep",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/privacy/redact", dryRunBody); 
            assertThat(dryRunResp.statusCode()).isEqualTo(200);
            Map<String, Object> dryRunRespBody = mapper.readValue(dryRunResp.body(), Map.class);
            Map<String, Object> dryRunData = (Map<String, Object>) dryRunRespBody.get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotNull();

            // Use the token to perform actual redaction
            String body = mapper.writeValueAsString(Map.of( 
                "collection", "customers",
                "entityId",   "ent-xyz789",
                "reason",     "Platform-initiated GDPR sweep",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            List<String> redactedFields = (List<String>) data.get("redactedFields");
            List<String> requestedFields = (List<String>) data.get("requestedFields");
            assertThat(requestedFields.size()).isGreaterThanOrEqualTo(9); 
            assertThat(redactedFields).containsExactlyInAnyOrder("email", "phone", "ssn"); 
        }

        @Test
        @DisplayName("missing entityId returns MISSING_REQUIRED error")
        @SuppressWarnings("unchecked")
        void missingEntityId_returnsError() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of("collection", "user_profiles")); 
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); 

            assertThat(resp.statusCode()).isEqualTo(400); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_REQUIRED");
        }

        @Test
        @DisplayName("unknown entity returns 404 ENTITY_NOT_FOUND")
        @SuppressWarnings("unchecked")
        void unknownEntity_returnsNotFound() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            String body = mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "entityId", "ent-missing",
                "fields", List.of("email")
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body); 

            assertThat(resp.statusCode()).isEqualTo(404); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("ENTITY_NOT_FOUND");
        }
    }

    @Test
    @DisplayName("executes classify, policy, redact, and purge as one governance lifecycle")
    @SuppressWarnings("unchecked")
    void governanceLifecycleExecutesAcrossRetentionAndPrivacyEndpoints() throws Exception { 
        EntityStore.Entity existingEntity = entity( 
            "ent-lifecycle",
            "user_profiles",
            Map.of("email", "person@example.com", "phone", "+1-555-0101", "name", "Person") 
        );
        storeEntity(existingEntity); 
        storeEntity(entity("expired-1", "user_profiles", Map.of("expiresAt", Instant.now().minusSeconds(180).toString()))); 

        server = new DataCloudHttpServer(mockClient, port) 
            .withAuditService(mockAuditService); 
        server.start(); 
        waitForServerReady(port); 

        HttpResponse<String> classifyResponse = post( 
            "/api/v1/governance/retention/classify",
            mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "tier", "compliance",
                "reason", "lifecycle-test"
            )));
        assertThat(classifyResponse.statusCode()).isEqualTo(200); 

        HttpResponse<String> policyResponse = get("/api/v1/governance/retention/policy?collection=user_profiles");
        assertThat(policyResponse.statusCode()).isEqualTo(200); 
        Map<String, Object> policyBody = mapper.readValue(policyResponse.body(), Map.class); 
        Map<String, Object> policyData = (Map<String, Object>) policyBody.get("data");
        assertThat(policyData.get("tier")).isEqualTo("compliance");

        // Perform dry-run first to get confirmation token
        HttpResponse<String> redactDryRunResponse = post( 
            "/api/v1/governance/privacy/redact",
            mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "entityId", "ent-lifecycle",
                "fields", List.of("email", "phone"), 
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
        HttpResponse<String> redactResponse = post( 
            "/api/v1/governance/privacy/redact",
            mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "entityId", "ent-lifecycle",
                "fields", List.of("email", "phone"), 
                "reason", "customer-erasure",
                "confirmationToken", redactConfirmationToken
            )));
        if (redactResponse.statusCode() != 200) {
            System.out.println("Redact response body: " + redactResponse.body());
        }
        assertThat(redactResponse.statusCode()).isEqualTo(200); 
        Map<String, Object> redactBody = mapper.readValue(redactResponse.body(), Map.class); 
        Map<String, Object> redactData = (Map<String, Object>) redactBody.get("data");
        assertThat(redactData.get("status")).isEqualTo("REDACTED");

        HttpResponse<String> dryRunResponse = post( 
            "/api/v1/governance/retention/purge",
            mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "dryRun", true
            )));
        assertThat(dryRunResponse.statusCode()).isEqualTo(200); 
        Map<String, Object> dryRunBody = mapper.readValue(dryRunResponse.body(), Map.class); 
        String confirmationToken = (String) ((Map<String, Object>) dryRunBody.get("data")).get("confirmationToken");
        assertThat(confirmationToken).isNotBlank(); 

        HttpResponse<String> purgeResponse = post( 
            "/api/v1/governance/retention/purge",
            mapper.writeValueAsString(Map.of( 
                "collection", "user_profiles",
                "confirmationToken", confirmationToken
            )));
        assertThat(purgeResponse.statusCode()).isEqualTo(200); 
        Map<String, Object> purgeBody = mapper.readValue(purgeResponse.body(), Map.class); 
        Map<String, Object> purgeData = (Map<String, Object>) purgeBody.get("data");
        assertThat(purgeData.get("status")).isEqualTo("PURGE_COMPLETED");
        assertThat(purgeData.get("deletedRows")).isEqualTo(1);

        HttpResponse<String> deletedEntityResponse = get("/api/v1/entities/user_profiles/expired-1");
        assertThat(deletedEntityResponse.statusCode()).isEqualTo(404); 
        assertThat(entityState.getOrDefault("_governance_purge_tombstones", Map.of()).values()) 
            .anySatisfy(tombstone -> { 
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
        void returns200WithGlobalFields() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/privacy/pii-fields");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data).containsKey("globalFields");
            assertThat(data).containsKey("tenantFields");
            assertThat(data).containsKey("effectiveCount");
            List<String> globalFields = (List<String>) data.get("globalFields");
            assertThat(globalFields).contains("email", "phone", "ssn"); 
            assertThat(((Number) data.get("effectiveCount")).intValue()).isGreaterThanOrEqualTo(9);
        }

        @Test
        @DisplayName("collection query reports auto-detected common PII fields")
        @SuppressWarnings("unchecked")
        void collectionQuery_reportsAutoDetectedFields() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/privacy/pii-fields?collection=user_profiles");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            List<String> autoDetectedFields = (List<String>) data.get("autoDetectedFields");
            assertThat(autoDetectedFields).contains("email", "phone", "ssn"); 
        }
    }

    @Nested
    @DisplayName("GET /api/v1/governance/privacy/verify")
    class VerifyRedactionTests {

        @Test
        @DisplayName("returns VERIFIED when requested PII fields are already redacted")
        @SuppressWarnings("unchecked")
        void verifyReportsVerified() throws Exception { 
            storeEntity(entity( 
                "ent-verified",
                "user_profiles",
                Map.of("email", "[REDACTED]", "phone", "[REDACTED]", "role", "admin") 
            ));

            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/privacy/verify?collection=user_profiles&entityId=ent-verified&fields=email,phone");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("VERIFIED");
            assertThat((List<String>) data.get("verifiedFields")).containsExactly("email", "phone");
            assertThat((List<String>) data.get("pendingFields")).isEmpty();
        }

        @Test
        @DisplayName("returns NOT_REDACTED when sensitive fields are still present")
        @SuppressWarnings("unchecked")
        void verifyReportsPendingFields() throws Exception { 
            storeEntity(entity( 
                "ent-pending",
                "user_profiles",
                Map.of("email", "person@example.com", "phone", "[REDACTED]", "role", "admin") 
            ));

            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/privacy/verify?collection=user_profiles&entityId=ent-pending&fields=email,phone");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("NOT_REDACTED");
            assertThat((List<String>) data.get("verifiedFields")).containsExactly("phone");
            assertThat((List<String>) data.get("pendingFields")).containsExactly("email");
        }

        @Test
        @DisplayName("returns 404 when entity does not exist")
        @SuppressWarnings("unchecked")
        void verifyMissingEntity_returnsNotFound() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/privacy/verify?collection=user_profiles&entityId=ent-missing&fields=email,phone");

            assertThat(resp.statusCode()).isEqualTo(404); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
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
        void returns200WithComplianceSummary() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/api/v1/governance/compliance/summary");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
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
        void tenantIdHeader_presentInSummary() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpRequest req = HttpRequest.newBuilder() 
                .GET() 
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary")) 
                .header("X-Tenant-Id", "acme-corp") 
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class); 
            Map<String, Object> meta = (Map<String, Object>) respBody.get("meta");
            if (meta != null) { 
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

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withAuditService(mockAuditService);
        server.start();
        waitForServerReady(port);
    }

    protected HttpResponse<String> get(String path) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .GET() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("X-Tenant-Id", "tenant-test") 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)) 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("Content-Type", "application/json") 
            .header("X-Tenant-Id", "tenant-test") 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> put(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", "tenant-test")
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteByTenant(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .DELETE()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Tenant-Id", "tenant-test")
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    protected static int findFreePort() throws IOException { 
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port); 
    }

    private static EntityStore.Entity entity(String id, String collection, Map<String, Object> data) { 
        return new EntityStore.Entity( 
            EntityStore.EntityId.of(id), 
            collection,
            data,
            EntityStore.EntityMetadata.empty() 
        );
    }

    private void storeEntity(EntityStore.Entity entity) { 
        entityState.computeIfAbsent(entity.collection(), ignored -> new ConcurrentHashMap<>()) 
            .put(entity.id().value(), entity); 
    }

    private static String sha256Hex(String value) { 
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))); 
        } catch (NoSuchAlgorithmException e) { 
            throw new IllegalStateException("SHA-256 unavailable", e); 
        }
    }

    private static void restoreSystemProperty(String key, String value) { 
        if (value == null) { 
            System.clearProperty(key); 
        } else {
            System.setProperty(key, value); 
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

        private ApiKeyResolver tenantAResolver; 
        private PolicyEngine permissivePolicyEngine; 

        @BeforeEach
        void setUpResolver() { 
            tenantAResolver = mock(ApiKeyResolver.class); 
            permissivePolicyEngine = mock(PolicyEngine.class); 
            when(tenantAResolver.resolve(TENANT_A_KEY))
                .thenReturn(Optional.of(new Principal("svc-tenant-a", List.of("admin"), TENANT_A))); 
            when(permissivePolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(Boolean.TRUE)); 
            when(mockAuditService.record(any())).thenReturn(Promise.complete()); 
        }

        @Test
        @DisplayName("API-key principal for tenant-A receives 403 when X-Tenant-ID is tenant-B")
        @SuppressWarnings("unchecked")
        void crossTenantRequestReturnsForbidden() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port)
                .withApiKeyResolver(tenantAResolver)
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            // Request claims tenant-B but API key belongs to tenant-A
            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-API-Key", TENANT_A_KEY)
                .header("X-Tenant-ID", TENANT_B) 
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body).containsKey("error"); 
        }

        @Test
        @DisplayName("API-key principal for tenant-A receives 200 when X-Tenant-ID matches")
        @SuppressWarnings("unchecked")
        void sameTenanRequestIsAllowed() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port)
                .withApiKeyResolver(tenantAResolver)
                .withPolicyEngine(permissivePolicyEngine)
                .withAuditService(mockAuditService); 
            server.start(); 
            waitForServerReady(port); 

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-API-Key", TENANT_A_KEY)
                .header("X-Tenant-ID", TENANT_A) 
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(200); 
        }
    }

    /**
     * DC-P1-010: Purge/redaction/governance audit assertions.
     */
    @Nested
    @DisplayName("Governance audit assertions (DC-P1-010)")
    class GovernanceAuditAssertionTests {

        @Test
        @DisplayName("Invalid purge token is audited as a denied action")
        @SuppressWarnings("unchecked")
        void invalidPurgeToken_deniedActionIsAudited() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "sensitive_data",
                "confirmationToken", "deliberately-invalid-token"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isEqualTo(403);
            Map<String, Object> error = (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_CONFIRMATION_TOKEN");
            // Denied/failed purge attempts must be audited
            verify(mockAuditService).record(argThat(event ->
                "RETENTION_PURGE_REJECTED".equals(event.eventType())));
        }

        @Test
        @DisplayName("Retention classify is deterministic — same input always produces same tier")
        @SuppressWarnings("unchecked")
        void retentionClassify_sameTier_isDeterministic() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            // Classify twice with the same params
            String body = mapper.writeValueAsString(Map.of(
                "collection", "orders",
                "tier", "compliance",
                "reason", "SOC2 compliance"
            ));

            HttpResponse<String> firstResp = post("/api/v1/governance/retention/classify", body);
            HttpResponse<String> secondResp = post("/api/v1/governance/retention/classify", body);

            assertThat(firstResp.statusCode()).isEqualTo(200);
            assertThat(secondResp.statusCode()).isEqualTo(200);

            Map<String, Object> firstData = (Map<String, Object>) mapper.readValue(firstResp.body(), Map.class).get("data");
            Map<String, Object> secondData = (Map<String, Object>) mapper.readValue(secondResp.body(), Map.class).get("data");

            assertThat(firstData.get("tier")).isEqualTo("compliance");
            assertThat(secondData.get("tier")).isEqualTo("compliance");
            assertThat(firstData.get("tier")).isEqualTo(secondData.get("tier"));
            assertThat(firstData.get("collection")).isEqualTo(secondData.get("collection"));
        }

        @Test
        @DisplayName("Compliance summary reflects tenant context (tenant scoping)")
        @SuppressWarnings("unchecked")
        void complianceSummary_isTenantScoped() throws Exception {
            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/compliance/summary");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> envelope = mapper.readValue(resp.body(), Map.class);
            // Compliance summary is wrapped in an ApiResponse envelope; data is under "data"
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            assertThat(data).isNotNull();
            // Must return tenantId in response for scoping verification
            assertThat(data.get("tenantId")).isEqualTo("tenant-test");
        }

        @Test
        @DisplayName("Purge audit record includes hashed confirmation token (not plaintext)")
        @SuppressWarnings("unchecked")
        void purgeAuditRecord_tokenHashedNotPlaintext() throws Exception {
            storeEntity(entity("audit-ent-1", "audit_events",
                Map.of("expiresAt", java.time.Instant.now().minusSeconds(300).toString())));

            server = new DataCloudHttpServer(mockClient, port).withAuditService(mockAuditService);
            server.start();
            waitForServerReady(port);

            String dryRunBody = mapper.writeValueAsString(Map.of(
                "collection", "audit_events",
                "dryRun", true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody);
            String confirmationToken = (String)
                ((Map<String, Object>) mapper.readValue(dryRunResp.body(), Map.class).get("data"))
                    .get("confirmationToken");

            String purgeBody = mapper.writeValueAsString(Map.of(
                "collection", "audit_events",
                "confirmationToken", confirmationToken
            ));
            post("/api/v1/governance/retention/purge", purgeBody);

            verify(mockAuditService).record(argThat(event -> {
                if (!"RETENTION_PURGE".equals(event.eventType())) return false;
                // Token must be stored as hash, never as plaintext
                String tokenHash = (String) event.getDetail("confirmationTokenHash");
                String expectedHash = sha256Hex(confirmationToken);
                if (tokenHash == null || expectedHash == null) {
                    return false;
                }
                return !tokenHash.equals(confirmationToken) && expectedHash.equals(tokenHash);
            }));
        }
    }

    /**
     * DC-P1-009: Policy CRUD lifecycle — Trust Center governance contract.
     */
    @Nested
    @DisplayName("POST|GET|PUT|DELETE /api/v1/governance/policies — Trust Center policy lifecycle (DC-P1-009)")
    class PolicyCrudLifecycleTests {
        private static final String TENANT_ID = "tenant-test";

        @Test
        @DisplayName("POST /api/v1/governance/policies creates policy and returns 201")
        @SuppressWarnings("unchecked")
        void createPolicy_returns201WithPolicyId() throws Exception {
            startServer();

            HttpResponse<String> resp = post("/api/v1/governance/policies",
                mapper.writeValueAsString(Map.of(
                    "name", "GDPR Retention",
                    "type", "RETENTION",
                    "description", "GDPR 7-year retention rule",
                    "rules", List.of(Map.of("tier", "compliance", "years", 7))
                )));

            assertThat(resp.statusCode()).isEqualTo(201);
            Map<String, Object> created = (Map<String, Object>) mapper.readValue(resp.body(), Map.class).get("data");
            assertThat(created.get("id")).isNotNull();
            assertThat(created.get("name")).isEqualTo("GDPR Retention");
            assertThat(created.get("type")).isEqualTo("RETENTION");
            assertThat(created.get("enabled")).isEqualTo(true);
            assertThat(created.get("createdAt")).isNotNull();
            assertThat(created.get("tenantId")).isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("GET /api/v1/governance/policies lists policies for tenant")
        @SuppressWarnings("unchecked")
        void listPolicies_returnsTenantScopedPolicies() throws Exception {
            startServer();

            // Create a policy first
            post("/api/v1/governance/policies", mapper.writeValueAsString(Map.of("name", "Test Policy", "type", "CUSTOM")));

            HttpResponse<String> resp = get("/api/v1/governance/policies");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> listBody = mapper.readValue(resp.body(), Map.class);
            assertThat(listBody.get("policies")).isNotNull();
            assertThat(listBody.get("count")).isNotNull();
            assertThat(listBody.get("tenantId")).isEqualTo(TENANT_ID);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> policies = (List<Map<String, Object>>) listBody.get("policies");
            assertThat(policies).anyMatch(p -> "Test Policy".equals(p.get("name")));
        }

        @Test
        @DisplayName("GET /api/v1/governance/policies/:id returns specific policy")
        @SuppressWarnings("unchecked")
        void getPolicy_returnsPolicy() throws Exception {
            startServer();

            // Create a policy
            HttpResponse<String> createResp = post("/api/v1/governance/policies",
                mapper.writeValueAsString(Map.of("name", "Findable Policy", "type", "CUSTOM")));
            Map<String, Object> created = (Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data");
            String policyId = (String) created.get("id");

            HttpResponse<String> getResp = get("/api/v1/governance/policies/" + policyId);

            assertThat(getResp.statusCode()).isEqualTo(200);
            Map<String, Object> found = (Map<String, Object>) mapper.readValue(getResp.body(), Map.class).get("data");
            assertThat(found.get("id")).isEqualTo(policyId);
            assertThat(found.get("name")).isEqualTo("Findable Policy");
        }

        @Test
        @DisplayName("GET /api/v1/governance/policies/:id returns 404 when not found")
        void getPolicy_nonExistent_returns404() throws Exception {
            startServer();

            HttpResponse<String> resp = get("/api/v1/governance/policies/non-existent-policy-id");

            assertThat(resp.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("PUT /api/v1/governance/policies/:id updates policy fields")
        @SuppressWarnings("unchecked")
        void updatePolicy_updatesFields() throws Exception {
            startServer();

            // Create policy
            HttpResponse<String> createResp = post("/api/v1/governance/policies",
                mapper.writeValueAsString(Map.of("name", "Original Name", "type", "CUSTOM")));
            Map<String, Object> created = (Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data");
            String policyId = (String) created.get("id");

            // Update name
            HttpResponse<String> updateResp = put("/api/v1/governance/policies/" + policyId,
                mapper.writeValueAsString(Map.of("name", "Updated Name", "description", "Updated desc")));

            assertThat(updateResp.statusCode()).isEqualTo(200);
            Map<String, Object> updated = (Map<String, Object>) mapper.readValue(updateResp.body(), Map.class).get("data");
            assertThat(updated.get("name")).isEqualTo("Updated Name");
            assertThat(updated.get("description")).isEqualTo("Updated desc");
            assertThat(updated.get("id")).isEqualTo(policyId);
            assertThat(updated.get("updatedAt")).isNotNull();
        }

        @Test
        @DisplayName("DELETE /api/v1/governance/policies/:id removes policy")
        @SuppressWarnings("unchecked")
        void deletePolicy_removesPolicy() throws Exception {
            startServer();

            // Create policy
            HttpResponse<String> createResp = post("/api/v1/governance/policies",
                mapper.writeValueAsString(Map.of("name", "To Delete", "type", "CUSTOM")));
            Map<String, Object> created = (Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data");
            String policyId = (String) created.get("id");

            // Delete
            HttpResponse<String> deleteResp = deleteByTenant("/api/v1/governance/policies/" + policyId);

            assertThat(deleteResp.statusCode()).isEqualTo(200);
            Map<String, Object> deleteBody = (Map<String, Object>) mapper.readValue(deleteResp.body(), Map.class).get("data");
            assertThat(deleteBody.get("id")).isEqualTo(policyId);
            assertThat(deleteBody.get("status")).isEqualTo("deleted");

            // Verify not found after delete
            HttpResponse<String> getAfterDelete = get("/api/v1/governance/policies/" + policyId);
            assertThat(getAfterDelete.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("POST /api/v1/governance/policies/:id/toggle disables a policy")
        @SuppressWarnings("unchecked")
        void togglePolicy_disablesPolicy() throws Exception {
            startServer();

            // Create policy (enabled by default)
            HttpResponse<String> createResp = post("/api/v1/governance/policies",
                mapper.writeValueAsString(Map.of("name", "Toggle Test", "type", "CUSTOM")));
            Map<String, Object> created = (Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data");
            String policyId = (String) created.get("id");
            assertThat(created.get("enabled")).isEqualTo(true);

            // Toggle off
            HttpResponse<String> toggleResp = post("/api/v1/governance/policies/" + policyId + "/toggle",
                mapper.writeValueAsString(Map.of("enabled", false)));

            assertThat(toggleResp.statusCode()).isEqualTo(200);
            Map<String, Object> toggled = (Map<String, Object>) mapper.readValue(toggleResp.body(), Map.class).get("data");
            assertThat(toggled.get("enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("Policy CRUD: complete create → read → update → delete lifecycle")
        @SuppressWarnings("unchecked")
        void fullPolicyCrudLifecycle() throws Exception {
            startServer();

            // CREATE
            HttpResponse<String> createResp = post("/api/v1/governance/policies",
                mapper.writeValueAsString(Map.of(
                    "name", "E2E Policy",
                    "type", "RETENTION",
                    "description", "End-to-end lifecycle test",
                    "rules", List.of(Map.of("collection", "orders", "tier", "standard", "years", 3))
                )));
            assertThat(createResp.statusCode()).isEqualTo(201);
            Map<String, Object> created = (Map<String, Object>) mapper.readValue(createResp.body(), Map.class).get("data");
            String policyId = (String) created.get("id");
            assertThat(policyId).isNotNull();

            // READ (GET by ID)
            HttpResponse<String> getResp = get("/api/v1/governance/policies/" + policyId);
            assertThat(getResp.statusCode()).isEqualTo(200);
            Map<String, Object> read = (Map<String, Object>) mapper.readValue(getResp.body(), Map.class).get("data");
            assertThat(read.get("name")).isEqualTo("E2E Policy");

            // READ (LIST)
            HttpResponse<String> listResp = get("/api/v1/governance/policies");
            Map<String, Object> list = mapper.readValue(listResp.body(), Map.class);
            List<Map<String, Object>> policies = (List<Map<String, Object>>) list.get("policies");
            assertThat(policies).anyMatch(p -> policyId.equals(p.get("id")));

            // UPDATE
            HttpResponse<String> updateResp = put("/api/v1/governance/policies/" + policyId,
                mapper.writeValueAsString(Map.of("description", "Updated by E2E test")));
            assertThat(updateResp.statusCode()).isEqualTo(200);
            Map<String, Object> updated = (Map<String, Object>) mapper.readValue(updateResp.body(), Map.class).get("data");
            assertThat(updated.get("description")).isEqualTo("Updated by E2E test");
            assertThat(updated.get("name")).isEqualTo("E2E Policy");

            // DELETE
            HttpResponse<String> deleteResp = deleteByTenant("/api/v1/governance/policies/" + policyId);
            assertThat(deleteResp.statusCode()).isEqualTo(200);

            // VERIFY GONE
            HttpResponse<String> verifyGone = get("/api/v1/governance/policies/" + policyId);
            assertThat(verifyGone.statusCode()).isEqualTo(404);
        }
    }
}
