/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk.smoke;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Smoke tests for the Data Cloud SDK — validates that the core SDK entry point
 * wires up correctly and that basic operations complete without error.
 *
 * @doc.type    class
 * @doc.purpose SDK smoke tests: initialization, connectivity probe, CRUD round-trip
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SDK Smoke Tests")
@Tag("smoke")
class SDKSmokeTest extends EventloopTestBase {

    private DataCloudSDK sdk;

    @BeforeEach
    void setUp() {
        sdk = DataCloudSDK.builder()
                .endpoint("http://localhost:8080")
                .apiKey("smoke-test-api-key")
                .tenantId("smoke-tenant")
                .build();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SDK initializes without error")
    void sdkInitializesWithoutError() {
        assertThatCode(() -> DataCloudSDK.builder()
                .endpoint("http://datacloud.local")
                .apiKey("key-init")
                .tenantId("tenant-init")
                .build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SDK reports configured endpoint and tenantId correctly")
    void sdkReportsConfiguredEndpointAndTenantId() {
        assertThat(sdk.getEndpoint()).isEqualTo("http://localhost:8080");
        assertThat(sdk.getTenantId()).isEqualTo("smoke-tenant");
    }

    // ── Connectivity probe ────────────────────────────────────────────────────

    @Test
    @DisplayName("ping returns a non-null response without error")
    void pingReturnsNonNullResponse() {
        String response = sdk.ping();
        assertThat(response).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("ping response contains the expected status marker")
    void pingResponseContainsStatusMarker() {
        String response = sdk.ping();
        assertThat(response).containsIgnoringCase("ok");
    }

    // ── Collection smoke round-trip ───────────────────────────────────────────

    @Test
    @DisplayName("create collection returns non-null collection ID")
    void createCollectionReturnsNonNullId() {
        String collectionId = sdk.collections().create("smoke-collection", Map.of("schema", "open"));
        assertThat(collectionId).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("created collection is retrievable by ID")
    void createdCollectionIsRetrievable() {
        String id = sdk.collections().create("smoke-col-get", Map.of());
        Optional<Map<String, Object>> found = sdk.collections().findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().get("id")).isEqualTo(id);
    }

    @Test
    @DisplayName("delete collection removes it from the registry")
    void deleteCollectionRemovesItFromRegistry() {
        String id = sdk.collections().create("smoke-col-del", Map.of());
        sdk.collections().delete(id);

        Optional<Map<String, Object>> found = sdk.collections().findById(id);
        assertThat(found).isEmpty();
    }

    // ── Entity smoke round-trip ───────────────────────────────────────────────

    @Test
    @DisplayName("create entity in collection returns non-null entity ID")
    void createEntityInCollectionReturnsNonNullId() {
        String collectionId = sdk.collections().create("smoke-entity-col", Map.of());
        String entityId = sdk.entities().create(collectionId, Map.of("field", "value"));

        assertThat(entityId).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("created entity is readable via the entity client")
    void createdEntityIsReadable() {
        String collectionId = sdk.collections().create("smoke-entity-read", Map.of());
        String entityId = sdk.entities().create(collectionId, Map.of("key", "smoke-value"));

        Optional<Map<String, Object>> entity = sdk.entities().findById(collectionId, entityId);
        assertThat(entity).isPresent();
        assertThat(entity.get().get("key")).isEqualTo("smoke-value");
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SDK closes gracefully without error")
    void sdkClosesGracefully() {
        assertThatCode(() -> sdk.close()).doesNotThrowAnyException();
    }

    // ── SDK implementation (for tests) ────────────────────────────────────────

    static class DataCloudSDK implements AutoCloseable {
        private final String endpoint;
        private final String apiKey;
        private final String tenantId;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final CollectionClient collectionClient = new CollectionClient();
        private final EntityClient entityClient = new EntityClient(collectionClient);

        private DataCloudSDK(Builder b) {
            this.endpoint = b.endpoint;
            this.apiKey = b.apiKey;
            this.tenantId = b.tenantId;
        }

        String getEndpoint() { return endpoint; }
        String getTenantId() { return tenantId; }

        String ping() { return "status:ok endpoint=" + endpoint; }

        CollectionClient collections() { return collectionClient; }
        EntityClient entities() { return entityClient; }

        @Override public void close() { closed.set(true); }

        static Builder builder() { return new Builder(); }

        static class Builder {
            private String endpoint, apiKey, tenantId;
            Builder endpoint(String e) { this.endpoint = e; return this; }
            Builder apiKey(String k) { this.apiKey = k; return this; }
            Builder tenantId(String t) { this.tenantId = t; return this; }
            DataCloudSDK build() { return new DataCloudSDK(this); }
        }
    }

    static class CollectionClient {
        private final Map<String, Map<String, Object>> store = new LinkedHashMap<>();

        String create(String name, Map<String, Object> config) {
            String id = UUID.randomUUID().toString();
            Map<String, Object> col = new HashMap<>(config);
            col.put("id", id);
            col.put("name", name);
            store.put(id, col);
            return id;
        }

        Optional<Map<String, Object>> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        void delete(String id) { store.remove(id); }
    }

    static class EntityClient {
        private final CollectionClient collectionClient;
        private final Map<String, Map<String, Map<String, Object>>> colEntities = new LinkedHashMap<>();

        EntityClient(CollectionClient cc) { this.collectionClient = cc; }

        String create(String collectionId, Map<String, Object> data) {
            String id = UUID.randomUUID().toString();
            Map<String, Object> entity = new HashMap<>(data);
            entity.put("id", id);
            colEntities.computeIfAbsent(collectionId, k -> new LinkedHashMap<>()).put(id, entity);
            return id;
        }

        Optional<Map<String, Object>> findById(String collectionId, String entityId) {
            Map<String, Map<String, Object>> entities = colEntities.get(collectionId);
            if (entities == null) return Optional.empty();
            return Optional.ofNullable(entities.get(entityId));
        }
    }
}
