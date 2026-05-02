/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
@DisplayName("GDPR Erasure Integration Test")
class GdprErasureIntegrationTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";
    private static final String SUBJECT_ID = "user-123";
    private static final String DC_MEMORY_COLLECTION = "dc_memory";
    private static final String EVENT_LOG_COLLECTION = "aep_audit";
    private static final String AGENT_REGISTRY_COLLECTION = "agent-registry";
    private static final String CACHE_COLLECTION = "aep_patterns";

    /** In-memory storage simulating DataCloud backend */
    private final ConcurrentMap<String, ConcurrentMap<String, Map<String, Object>>> storage = new ConcurrentHashMap<>(); 

    @Mock
    private DataCloudClient mockClient;

    private AepComplianceService service;

    @BeforeEach
    void setUp() { 
        // Create a mock client that uses in-memory storage
        setupMockClientWithInMemoryStorage(); 

        service = new AepComplianceService(mockClient); 
        service.registerCollection(DC_MEMORY_COLLECTION); 
        service.registerCollection(EVENT_LOG_COLLECTION); 
        service.registerCollection(AGENT_REGISTRY_COLLECTION); 
        service.registerCollection(CACHE_COLLECTION); 

        // Populate test data
        populateTestData(); 
    }

    @AfterEach
    void tearDown() { 
        storage.clear(); 
    }

    @Test
    @DisplayName("GDPR erasure deletes from dc_memory, EventLogStore, and all caches")
    void gdprErasure_deletesFromAllCollections() { 
        // Verify initial data exists
        assertThat(storage.get(DC_MEMORY_COLLECTION)).hasSize(3); 
        assertThat(storage.get(EVENT_LOG_COLLECTION)).hasSize(2); 
        assertThat(storage.get(AGENT_REGISTRY_COLLECTION)).hasSize(1); 
        assertThat(storage.get(CACHE_COLLECTION)).hasSize(2); 

        // Execute deletion request on eventloop
        AepComplianceReport report = eventloop().submit(() -> 
            service.deletionRequest(TENANT, SUBJECT_ID) 
        ).join(); 

        // Verify deletion succeeded
        assertThat(report.success()).isTrue(); 
        assertThat(report.operation()).isEqualTo("GDPR_ERASURE");
        assertThat(report.recordsAffected()).isEqualTo(8L); // 3 + 2 + 1 + 2 

        // Verify data was deleted from all collections
        assertThat(storage.get(DC_MEMORY_COLLECTION)).isEmpty(); 
        assertThat(storage.get(EVENT_LOG_COLLECTION)).isEmpty(); 
        assertThat(storage.get(AGENT_REGISTRY_COLLECTION)).isEmpty(); 
        assertThat(storage.get(CACHE_COLLECTION)).isEmpty(); 

        // Verify breakdown is correct
        assertThat(report.breakdown()) 
            .containsEntry(DC_MEMORY_COLLECTION, 3L) 
            .containsEntry(EVENT_LOG_COLLECTION, 2L) 
            .containsEntry(AGENT_REGISTRY_COLLECTION, 1L) 
            .containsEntry(CACHE_COLLECTION, 2L); 
    }

    @Test
    @DisplayName("GDPR erasure only deletes records matching subjectId")
    void gdprErasure_onlyDeletesMatchingSubjectId() { 
        // Add records for a different subject
        String otherSubject = "user-456";
        addEntity(DC_MEMORY_COLLECTION, "mem-4", Map.of( 
            "_subjectId", otherSubject,
            "type", "EPISODIC",
            "data", "other user data"
        ));
        addEntity(EVENT_LOG_COLLECTION, "audit-3", Map.of( 
            "_subjectId", otherSubject,
            "event", "other event"
        ));

        // Execute deletion
        AepComplianceReport report = eventloop().submit(() -> 
            service.deletionRequest(TENANT, SUBJECT_ID) 
        ).join(); 

        assertThat(report.recordsAffected()).isEqualTo(8L); 
        assertThat(storage.get(DC_MEMORY_COLLECTION)).hasSize(1); 
        assertThat(storage.get(EVENT_LOG_COLLECTION)).hasSize(1); 
        assertThat(storage.get(DC_MEMORY_COLLECTION).get("mem-4").get("_subjectId")).isEqualTo(otherSubject);
        assertThat(storage.get(EVENT_LOG_COLLECTION).get("audit-3").get("_subjectId")).isEqualTo(otherSubject);
    }

    @Test
    @DisplayName("GDPR erasure deletes subject records across multiple result pages")
    void gdprErasure_deletesAcrossMultiplePages() { 
        for (int index = 0; index < 550; index++) { 
            addEntity(DC_MEMORY_COLLECTION, "bulk-" + index, Map.of( 
                "_subjectId", SUBJECT_ID,
                "type", "EPISODIC",
                "data", "page-" + index
            ));
        }

        AepComplianceReport report = eventloop().submit(() -> 
            service.deletionRequest(TENANT, SUBJECT_ID) 
        ).join(); 

        assertThat(report.success()).isTrue(); 
        assertThat(report.breakdown().get(DC_MEMORY_COLLECTION)).isEqualTo(553L); 
        assertThat(storage.get(DC_MEMORY_COLLECTION)).isEmpty(); 
    }

    @Test
    @DisplayName("GDPR erasure handles empty collections gracefully")
    void gdprErasure_handlesEmptyCollections() { 
        // Clear one collection
        storage.get(CACHE_COLLECTION).clear(); 

        AepComplianceReport report = eventloop().submit(() -> 
            service.deletionRequest(TENANT, SUBJECT_ID) 
        ).join(); 

        assertThat(report.success()).isTrue(); 
        assertThat(report.recordsAffected()).isEqualTo(6L); // 3 + 2 + 1 + 0 
        assertThat(report.breakdown()).containsEntry(CACHE_COLLECTION, 0L); 
    }

    @Test
    @DisplayName("GDPR erasure is idempotent - multiple calls are safe")
    void gdprErasure_isIdempotent() { 
        // First deletion
        AepComplianceReport report1 = eventloop().submit(() -> 
            service.deletionRequest(TENANT, SUBJECT_ID) 
        ).join(); 

        assertThat(report1.recordsAffected()).isEqualTo(8L); 

        // Second deletion (should delete 0 records) 
        AepComplianceReport report2 = eventloop().submit(() -> 
            service.deletionRequest(TENANT, SUBJECT_ID) 
        ).join(); 

        assertThat(report2.success()).isTrue(); 
        assertThat(report2.recordsAffected()).isEqualTo(0L); 
        assertThat(report2.breakdown()).allSatisfy((key, value) ->  
            assertThat(value).isEqualTo(0L) 
        );
    }

    // =========================================================================
    // Setup helpers
    // =========================================================================

    private void setupMockClientWithInMemoryStorage() { 
        // Initialize storage maps for each collection
        storage.put(DC_MEMORY_COLLECTION, new ConcurrentHashMap<>()); 
        storage.put(EVENT_LOG_COLLECTION, new ConcurrentHashMap<>()); 
        storage.put(AGENT_REGISTRY_COLLECTION, new ConcurrentHashMap<>()); 
        storage.put(CACHE_COLLECTION, new ConcurrentHashMap<>()); 

        // Mock query operation
        when(mockClient.query(eq(TENANT), anyString(), any(Query.class))) 
            .thenAnswer(invocation -> { 
                String collection = invocation.getArgument(1); 
                Query query = invocation.getArgument(2); 
                return Promise.of(queryCollection(collection, query)); 
            });

        // Mock delete operation
        when(mockClient.delete(eq(TENANT), anyString(), anyString())) 
            .thenAnswer(invocation -> { 
                String collection = invocation.getArgument(1); 
                String entityId = invocation.getArgument(2); 
                storage.getOrDefault(collection, new ConcurrentHashMap<>()).remove(entityId); 
                return Promise.of(null); 
            });
    }

    private void populateTestData() { 
        // Add dc_memory records (episodes, facts, policies) 
        addEntity(DC_MEMORY_COLLECTION, "mem-1", Map.of( 
            "_subjectId", SUBJECT_ID,
            "type", "EPISODIC",
            "data", "episode data"
        ));
        addEntity(DC_MEMORY_COLLECTION, "mem-2", Map.of( 
            "_subjectId", SUBJECT_ID,
            "type", "SEMANTIC",
            "data", "fact data"
        ));
        addEntity(DC_MEMORY_COLLECTION, "mem-3", Map.of( 
            "_subjectId", SUBJECT_ID,
            "type", "PROCEDURAL",
            "data", "policy data"
        ));

        // Add EventLogStore records (audit events) 
        addEntity(EVENT_LOG_COLLECTION, "audit-1", Map.of( 
            "_subjectId", SUBJECT_ID,
            "eventId", "evt-1",
            "timestamp", Instant.now().toString() 
        ));
        addEntity(EVENT_LOG_COLLECTION, "audit-2", Map.of( 
            "_subjectId", SUBJECT_ID,
            "eventId", "evt-2",
            "timestamp", Instant.now().toString() 
        ));

        // Add agent registry record
        addEntity(AGENT_REGISTRY_COLLECTION, "agent-1", Map.of( 
            "_subjectId", SUBJECT_ID,
            "agentId", "agent-1",
            "status", "ACTIVE"
        ));

        // Add cache records (patterns) 
        addEntity(CACHE_COLLECTION, "pattern-1", Map.of( 
            "_subjectId", SUBJECT_ID,
            "patternId", "pat-1",
            "spec", "count > 5"
        ));
        addEntity(CACHE_COLLECTION, "pattern-2", Map.of( 
            "_subjectId", SUBJECT_ID,
            "patternId", "pat-2",
            "spec", "rate > 0.5"
        ));
    }

    private void addEntity(String collection, String id, Map<String, Object> data) { 
        Map<String, Object> entityData = new HashMap<>(data); 
        entityData.put("id", id); 
        storage.get(collection).put(id, entityData); 
    }

    private List<Entity> queryCollection(String collection, Query query) { 
        ConcurrentMap<String, Map<String, Object>> collectionStorage = storage.get(collection); 
        if (collectionStorage == null) { 
            return List.of(); 
        }
        List<Entity> filtered = collectionStorage.values().stream() 
            .filter(data -> matchesFilters(data, query)) 
            .sorted(Comparator.comparing(data -> String.valueOf(data.get("id"))))
            .map(data -> Entity.of((String) data.get("id"), collection, data))
            .toList(); 
        int fromIndex = Math.min(query.offset(), filtered.size()); 
        int toIndex = Math.min(fromIndex + query.limit(), filtered.size()); 
        return filtered.subList(fromIndex, toIndex); 
    }

    private boolean matchesFilters(Map<String, Object> data, Query query) { 
        return query.filters().stream().allMatch(filter -> { 
            Object value = data.get(filter.field()); 
            if ("eq".equals(filter.operator())) { 
                return java.util.Objects.equals(value, filter.value()); 
            }
            return true;
        });
    }
}
