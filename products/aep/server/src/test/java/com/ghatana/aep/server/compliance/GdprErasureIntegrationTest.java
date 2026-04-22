/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.compliance;

import com.ghatana.aep.compliance.AepComplianceReport;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P1-10: Integration test verifying GDPR erasure deletes from dc_memory, EventLogStore, and all caches.
 *
 * <p>This test uses an in-memory mock of DataCloudClient that simulates real storage behavior
 * to verify that the deletionRequest method properly removes data from all registered collections.
 *
 * @doc.type class
 * @doc.purpose Integration test for GDPR erasure across all AEP collections
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("GDPR Erasure Integration Test [GH-90000]")
class GdprErasureIntegrationTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";
    private static final String SUBJECT_ID = "user-123";
    private static final String DC_MEMORY_COLLECTION = "dc_memory";
    private static final String EVENT_LOG_COLLECTION = "aep_audit";
    private static final String AGENT_REGISTRY_COLLECTION = "agent-registry";
    private static final String CACHE_COLLECTION = "aep_patterns";

    /** In-memory storage simulating DataCloud backend */
    private final ConcurrentMap<String, ConcurrentMap<String, Map<String, Object>>> storage = new ConcurrentHashMap<>(); // GH-90000

    @Mock
    private DataCloudClient mockClient;

    private AepComplianceService service;

    @BeforeEach
    void setUp() { // GH-90000
        // Create a mock client that uses in-memory storage
        setupMockClientWithInMemoryStorage(); // GH-90000

        service = new AepComplianceService(mockClient); // GH-90000
        service.registerCollection(DC_MEMORY_COLLECTION); // GH-90000
        service.registerCollection(EVENT_LOG_COLLECTION); // GH-90000
        service.registerCollection(AGENT_REGISTRY_COLLECTION); // GH-90000
        service.registerCollection(CACHE_COLLECTION); // GH-90000

        // Populate test data
        populateTestData(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        storage.clear(); // GH-90000
    }

    @Test
    @DisplayName("GDPR erasure deletes from dc_memory, EventLogStore, and all caches [GH-90000]")
    void gdprErasure_deletesFromAllCollections() { // GH-90000
        // Verify initial data exists
        assertThat(storage.get(DC_MEMORY_COLLECTION)).hasSize(3); // GH-90000
        assertThat(storage.get(EVENT_LOG_COLLECTION)).hasSize(2); // GH-90000
        assertThat(storage.get(AGENT_REGISTRY_COLLECTION)).hasSize(1); // GH-90000
        assertThat(storage.get(CACHE_COLLECTION)).hasSize(2); // GH-90000

        // Execute deletion request on eventloop
        AepComplianceReport report = eventloop().submit(() -> // GH-90000
            service.deletionRequest(TENANT, SUBJECT_ID) // GH-90000
        ).join(); // GH-90000

        // Verify deletion succeeded
        assertThat(report.success()).isTrue(); // GH-90000
        assertThat(report.operation()).isEqualTo("GDPR_ERASURE [GH-90000]");
        assertThat(report.recordsAffected()).isEqualTo(8L); // 3 + 2 + 1 + 2 // GH-90000

        // Verify data was deleted from all collections
        assertThat(storage.get(DC_MEMORY_COLLECTION)).isEmpty(); // GH-90000
        assertThat(storage.get(EVENT_LOG_COLLECTION)).isEmpty(); // GH-90000
        assertThat(storage.get(AGENT_REGISTRY_COLLECTION)).isEmpty(); // GH-90000
        assertThat(storage.get(CACHE_COLLECTION)).isEmpty(); // GH-90000

        // Verify breakdown is correct
        assertThat(report.breakdown()) // GH-90000
            .containsEntry(DC_MEMORY_COLLECTION, 3L) // GH-90000
            .containsEntry(EVENT_LOG_COLLECTION, 2L) // GH-90000
            .containsEntry(AGENT_REGISTRY_COLLECTION, 1L) // GH-90000
            .containsEntry(CACHE_COLLECTION, 2L); // GH-90000
    }

    @Test
    @DisplayName("GDPR erasure only deletes records matching subjectId [GH-90000]")
    void gdprErasure_onlyDeletesMatchingSubjectId() { // GH-90000
        // Add records for a different subject
        String otherSubject = "user-456";
        addEntity(DC_MEMORY_COLLECTION, "mem-4", Map.of( // GH-90000
            "_subjectId", otherSubject,
            "type", "EPISODIC",
            "data", "other user data"
        ));
        addEntity(EVENT_LOG_COLLECTION, "audit-3", Map.of( // GH-90000
            "_subjectId", otherSubject,
            "event", "other event"
        ));

        // Execute deletion
        AepComplianceReport report = eventloop().submit(() -> // GH-90000
            service.deletionRequest(TENANT, SUBJECT_ID) // GH-90000
        ).join(); // GH-90000

        assertThat(report.recordsAffected()).isEqualTo(8L); // GH-90000
        assertThat(storage.get(DC_MEMORY_COLLECTION)).hasSize(1); // GH-90000
        assertThat(storage.get(EVENT_LOG_COLLECTION)).hasSize(1); // GH-90000
        assertThat(storage.get(DC_MEMORY_COLLECTION).get("mem-4 [GH-90000]").get("_subjectId [GH-90000]")).isEqualTo(otherSubject);
        assertThat(storage.get(EVENT_LOG_COLLECTION).get("audit-3 [GH-90000]").get("_subjectId [GH-90000]")).isEqualTo(otherSubject);
    }

    @Test
    @DisplayName("GDPR erasure deletes subject records across multiple result pages [GH-90000]")
    void gdprErasure_deletesAcrossMultiplePages() { // GH-90000
        for (int index = 0; index < 550; index++) { // GH-90000
            addEntity(DC_MEMORY_COLLECTION, "bulk-" + index, Map.of( // GH-90000
                "_subjectId", SUBJECT_ID,
                "type", "EPISODIC",
                "data", "page-" + index
            ));
        }

        AepComplianceReport report = eventloop().submit(() -> // GH-90000
            service.deletionRequest(TENANT, SUBJECT_ID) // GH-90000
        ).join(); // GH-90000

        assertThat(report.success()).isTrue(); // GH-90000
        assertThat(report.breakdown().get(DC_MEMORY_COLLECTION)).isEqualTo(553L); // GH-90000
        assertThat(storage.get(DC_MEMORY_COLLECTION)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("GDPR erasure handles empty collections gracefully [GH-90000]")
    void gdprErasure_handlesEmptyCollections() { // GH-90000
        // Clear one collection
        storage.get(CACHE_COLLECTION).clear(); // GH-90000

        AepComplianceReport report = eventloop().submit(() -> // GH-90000
            service.deletionRequest(TENANT, SUBJECT_ID) // GH-90000
        ).join(); // GH-90000

        assertThat(report.success()).isTrue(); // GH-90000
        assertThat(report.recordsAffected()).isEqualTo(6L); // 3 + 2 + 1 + 0 // GH-90000
        assertThat(report.breakdown()).containsEntry(CACHE_COLLECTION, 0L); // GH-90000
    }

    @Test
    @DisplayName("GDPR erasure is idempotent - multiple calls are safe [GH-90000]")
    void gdprErasure_isIdempotent() { // GH-90000
        // First deletion
        AepComplianceReport report1 = eventloop().submit(() -> // GH-90000
            service.deletionRequest(TENANT, SUBJECT_ID) // GH-90000
        ).join(); // GH-90000

        assertThat(report1.recordsAffected()).isEqualTo(8L); // GH-90000

        // Second deletion (should delete 0 records) // GH-90000
        AepComplianceReport report2 = eventloop().submit(() -> // GH-90000
            service.deletionRequest(TENANT, SUBJECT_ID) // GH-90000
        ).join(); // GH-90000

        assertThat(report2.success()).isTrue(); // GH-90000
        assertThat(report2.recordsAffected()).isEqualTo(0L); // GH-90000
        assertThat(report2.breakdown()).allSatisfy((key, value) ->  // GH-90000
            assertThat(value).isEqualTo(0L) // GH-90000
        );
    }

    // =========================================================================
    // Setup helpers
    // =========================================================================

    private void setupMockClientWithInMemoryStorage() { // GH-90000
        // Initialize storage maps for each collection
        storage.put(DC_MEMORY_COLLECTION, new ConcurrentHashMap<>()); // GH-90000
        storage.put(EVENT_LOG_COLLECTION, new ConcurrentHashMap<>()); // GH-90000
        storage.put(AGENT_REGISTRY_COLLECTION, new ConcurrentHashMap<>()); // GH-90000
        storage.put(CACHE_COLLECTION, new ConcurrentHashMap<>()); // GH-90000

        // Mock query operation
        when(mockClient.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
            .thenAnswer(invocation -> { // GH-90000
                String collection = invocation.getArgument(1); // GH-90000
                Query query = invocation.getArgument(2); // GH-90000
                return Promise.of(queryCollection(collection, query)); // GH-90000
            });

        // Mock delete operation
        when(mockClient.delete(eq(TENANT), anyString(), anyString())) // GH-90000
            .thenAnswer(invocation -> { // GH-90000
                String collection = invocation.getArgument(1); // GH-90000
                String entityId = invocation.getArgument(2); // GH-90000
                storage.getOrDefault(collection, new ConcurrentHashMap<>()).remove(entityId); // GH-90000
                return Promise.of(null); // GH-90000
            });
    }

    private void populateTestData() { // GH-90000
        // Add dc_memory records (episodes, facts, policies) // GH-90000
        addEntity(DC_MEMORY_COLLECTION, "mem-1", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "type", "EPISODIC",
            "data", "episode data"
        ));
        addEntity(DC_MEMORY_COLLECTION, "mem-2", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "type", "SEMANTIC",
            "data", "fact data"
        ));
        addEntity(DC_MEMORY_COLLECTION, "mem-3", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "type", "PROCEDURAL",
            "data", "policy data"
        ));

        // Add EventLogStore records (audit events) // GH-90000
        addEntity(EVENT_LOG_COLLECTION, "audit-1", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "eventId", "evt-1",
            "timestamp", Instant.now().toString() // GH-90000
        ));
        addEntity(EVENT_LOG_COLLECTION, "audit-2", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "eventId", "evt-2",
            "timestamp", Instant.now().toString() // GH-90000
        ));

        // Add agent registry record
        addEntity(AGENT_REGISTRY_COLLECTION, "agent-1", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "agentId", "agent-1",
            "status", "ACTIVE"
        ));

        // Add cache records (patterns) // GH-90000
        addEntity(CACHE_COLLECTION, "pattern-1", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "patternId", "pat-1",
            "spec", "count > 5"
        ));
        addEntity(CACHE_COLLECTION, "pattern-2", Map.of( // GH-90000
            "_subjectId", SUBJECT_ID,
            "patternId", "pat-2",
            "spec", "rate > 0.5"
        ));
    }

    private void addEntity(String collection, String id, Map<String, Object> data) { // GH-90000
        Map<String, Object> entityData = new HashMap<>(data); // GH-90000
        entityData.put("id", id); // GH-90000
        storage.get(collection).put(id, entityData); // GH-90000
    }

    private List<Entity> queryCollection(String collection, Query query) { // GH-90000
        ConcurrentMap<String, Map<String, Object>> collectionStorage = storage.get(collection); // GH-90000
        if (collectionStorage == null) { // GH-90000
            return List.of(); // GH-90000
        }
        List<Entity> filtered = collectionStorage.values().stream() // GH-90000
            .filter(data -> matchesFilters(data, query)) // GH-90000
            .sorted(Comparator.comparing(data -> String.valueOf(data.get("id [GH-90000]"))))
            .map(data -> Entity.of((String) data.get("id [GH-90000]"), collection, data))
            .toList(); // GH-90000
        int fromIndex = Math.min(query.offset(), filtered.size()); // GH-90000
        int toIndex = Math.min(fromIndex + query.limit(), filtered.size()); // GH-90000
        return filtered.subList(fromIndex, toIndex); // GH-90000
    }

    private boolean matchesFilters(Map<String, Object> data, Query query) { // GH-90000
        return query.filters().stream().allMatch(filter -> { // GH-90000
            Object value = data.get(filter.field()); // GH-90000
            if ("eq".equals(filter.operator())) { // GH-90000
                return java.util.Objects.equals(value, filter.value()); // GH-90000
            }
            return true;
        });
    }
}
