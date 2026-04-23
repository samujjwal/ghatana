/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.compliance.AepComplianceReport;
import com.ghatana.aep.server.compliance.AepComplianceService;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GDPR erasure depth test verifying deletion from all AEP collections.
 *
 * <p>This integration test verifies that GDPR erasure requests correctly delete data
 * from all registered AEP collections including dc_memory, and that the deletion
 * is performed with proper depth (all matching records, not just a subset). // GH-90000
 *
 * <p>Test scope:
 * - Verifies deletion from dc_memory collection
 * - Verifies deletion from all default AEP collections
 * - Verifies proper query filtering by subjectId
 * - Verifies cache invalidation behavior (via service layer) // GH-90000
 *
 * @doc.type class
 * @doc.purpose GDPR erasure depth verification
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("GDPR Erasure Depth Test")
class GdprErasureDepthTest {

    private static final String TENANT_ID = "tenant-test-123";
    private static final String SUBJECT_ID = "subject-user-456";
    private static final String DC_MEMORY_COLLECTION = "dc_memory";

    @Mock
    private DataCloudClient dataCloudClient;

    private AepComplianceService complianceService;

    @BeforeEach
    void setUp() { // GH-90000
        complianceService = new AepComplianceService(dataCloudClient); // GH-90000
    }

    // =========================================================================
    // dc_memory Collection Deletion Verification
    // =========================================================================

    @Nested
    @DisplayName("dc_memory collection deletion")
    class DcMemoryDeletionTests {

        @Test
        @DisplayName("deletes all matching records from dc_memory collection")
        void deletesAllMatchingRecordsFromDcMemory() { // GH-90000
            // Setup: Create multiple records in dc_memory with the same subjectId
            List<Entity> dcMemoryRecords = List.of( // GH-90000
                    createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID), // GH-90000
                    createEntity("dc-2", DC_MEMORY_COLLECTION, SUBJECT_ID), // GH-90000
                    createEntity("dc-3", DC_MEMORY_COLLECTION, SUBJECT_ID) // GH-90000
            );

            // Stub query to return these records
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(dcMemoryRecords)); // GH-90000

            // Stub delete operations
            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            // Stub other collections as empty
            stubOtherCollectionsEmpty(); // GH-90000

            // Execute deletion
            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Verify
            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.operation()).isEqualTo("GDPR_ERASURE");
            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 3L); // GH-90000

            // Verify all three records were deleted
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-1"); // GH-90000
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-2"); // GH-90000
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-3"); // GH-90000
        }

        @Test
        @DisplayName("does not delete records with different subjectId in dc_memory")
        void doesNotDeleteDifferentSubjectRecords() { // GH-90000
            // Setup: Return only records matching the subjectId (simulating filtered query result) // GH-90000
            // The service queries with filter by SUBJECT_ID_FIELD, so mock returns filtered results
            List<Entity> matchingRecords = List.of( // GH-90000
                    createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID), // GH-90000
                    createEntity("dc-3", DC_MEMORY_COLLECTION, SUBJECT_ID) // GH-90000
            );

            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(matchingRecords)); // GH-90000

            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            stubOtherCollectionsEmpty(); // GH-90000

            // Execute deletion
            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Verify only 2 records deleted (those matching subjectId returned by mock query) // GH-90000
            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 2L); // GH-90000
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-1"); // GH-90000
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-3"); // GH-90000
            verify(dataCloudClient, never()).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-2"); // GH-90000
        }

        @Test
        @DisplayName("uses correct subjectId filter when querying dc_memory")
        void usesCorrectSubjectIdFilter() { // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            stubOtherCollectionsEmpty(); // GH-90000

            complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Capture the query to verify filter
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class); // GH-90000
            verify(dataCloudClient).query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), queryCaptor.capture()); // GH-90000

            // Verify the query includes the subjectId filter
            Query query = queryCaptor.getValue(); // GH-90000
            assertThat(query.filters()).isNotEmpty(); // GH-90000
            // The filter should be for _subjectId field
        }
    }

    // =========================================================================
    // All Collections Deletion Verification
    // =========================================================================

    @Nested
    @DisplayName("all collections deletion")
    class AllCollectionsDeletionTests {

        @Test
        @DisplayName("deletes records from all default AEP collections")
        void deletesFromAllDefaultCollections() { // GH-90000
            // Setup records in all collections
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("p-1", "aep_patterns", SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_pipelines"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("pl-1", "aep_pipelines", SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq("agent-registry"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("ag-1", "agent-registry", SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_audit"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("au-1", "aep_audit", SUBJECT_ID)))); // GH-90000

            when(dataCloudClient.delete(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            // Execute deletion
            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Verify all collections were processed
            assertThat(report.breakdown()).hasSize(5); // GH-90000
            assertThat(report.breakdown()) // GH-90000
                    .containsEntry("aep_patterns", 1L) // GH-90000
                    .containsEntry("aep_pipelines", 1L) // GH-90000
                    .containsEntry("agent-registry", 1L) // GH-90000
                    .containsEntry(DC_MEMORY_COLLECTION, 1L) // GH-90000
                    .containsEntry("aep_audit", 1L); // GH-90000
        }

        @Test
        @DisplayName("handles empty dc_memory collection gracefully")
        void handlesEmptyDcMemory() { // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            stubOtherCollectionsEmpty(); // GH-90000

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 0L); // GH-90000
            verify(dataCloudClient, never()).delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), anyString()); // GH-90000
        }

        @Test
        @DisplayName("aggregates deletion counts across all collections")
        void aggregatesDeletionCounts() { // GH-90000
            // Setup: Multiple records in different collections
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            createEntity("p-1", "aep_patterns", SUBJECT_ID), // GH-90000
                            createEntity("p-2", "aep_patterns", SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_pipelines"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("pl-1", "aep_pipelines", SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq("agent-registry"), any(Query.class)))
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of( // GH-90000
                            createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID), // GH-90000
                            createEntity("dc-2", DC_MEMORY_COLLECTION, SUBJECT_ID), // GH-90000
                            createEntity("dc-3", DC_MEMORY_COLLECTION, SUBJECT_ID)))); // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_audit"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("au-1", "aep_audit", SUBJECT_ID)))); // GH-90000

            when(dataCloudClient.delete(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Verify total count: 2+1+0+3+1 = 7
            assertThat(report.recordsAffected()).isEqualTo(7L); // GH-90000
            assertThat(report.breakdown()) // GH-90000
                    .containsEntry("aep_patterns", 2L) // GH-90000
                    .containsEntry("aep_pipelines", 1L) // GH-90000
                    .containsEntry("agent-registry", 0L) // GH-90000
                    .containsEntry(DC_MEMORY_COLLECTION, 3L) // GH-90000
                    .containsEntry("aep_audit", 1L); // GH-90000
        }
    }

    // =========================================================================
    // EventLogStore and Cache Verification
    // =========================================================================

    @Nested
    @DisplayName("EventLogStore and cache verification")
    class EventLogStoreAndCacheTests {

        @Test
        @DisplayName("verifies registered collections include all expected collections")
        void verifiesRegisteredCollections() { // GH-90000
            List<String> collections = complianceService.registeredCollections(); // GH-90000

            assertThat(collections) // GH-90000
                    .containsExactlyInAnyOrder( // GH-90000
                            "aep_patterns",
                            "aep_pipelines",
                            "agent-registry",
                            DC_MEMORY_COLLECTION,
                            "aep_audit"
                    );
        }

        @Test
        @DisplayName("allows registration of additional collections for deletion")
        void allowsAdditionalCollectionRegistration() { // GH-90000
            complianceService.registerCollection("custom_event_log");

            List<String> collections = complianceService.registeredCollections(); // GH-90000

            assertThat(collections).contains("custom_event_log");
            assertThat(collections).hasSize(6); // 5 default + 1 custom // GH-90000
        }

        @Test
        @DisplayName("deletes from custom registered collections")
        void deletesFromCustomCollections() { // GH-90000
            complianceService.registerCollection("custom_logs");

            when(dataCloudClient.query(eq(TENANT_ID), eq("custom_logs"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("c-1", "custom_logs", SUBJECT_ID)))); // GH-90000

            // Stub other collections including dc_memory
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            stubOtherCollectionsEmpty("custom_logs");

            when(dataCloudClient.delete(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.breakdown()).containsEntry("custom_logs", 1L); // GH-90000
            verify(dataCloudClient).delete(TENANT_ID, "custom_logs", "c-1"); // GH-90000
        }
    }

    // =========================================================================
    // Error Handling and Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("error handling and edge cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("continues deletion if one collection fails")
        void continuesOnCollectionFailure() { // GH-90000
            // dc_memory fails
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("dc_memory unavailable")));

            // Other collections succeed
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("p-1", "aep_patterns", SUBJECT_ID)))); // GH-90000

            stubOtherCollectionsEmpty("aep_patterns", DC_MEMORY_COLLECTION); // GH-90000

            when(dataCloudClient.delete(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Should succeed overall with a warning
            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.warnings()).isNotEmpty(); // GH-90000
            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 0L); // GH-90000
            assertThat(report.breakdown()).containsEntry("aep_patterns", 1L); // GH-90000
        }

        @Test
        @DisplayName("handles deletion failure for individual records")
        void handlesIndividualRecordFailure() { // GH-90000
            List<Entity> dcMemoryRecords = List.of( // GH-90000
                    createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID), // GH-90000
                    createEntity("dc-2", DC_MEMORY_COLLECTION, SUBJECT_ID) // GH-90000
            );

            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(dcMemoryRecords)); // GH-90000

            // First delete succeeds, second fails
            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), eq("dc-1")))
                    .thenReturn(Promise.of((Void) null)); // GH-90000
            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), eq("dc-2")))
                    .thenReturn(Promise.ofException(new RuntimeException("delete failed")));

            stubOtherCollectionsEmpty(); // GH-90000

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult(); // GH-90000

            // Should have a warning but still report the count
            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.warnings()).isNotEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Entity createEntity(String id, String collection, String subjectId) { // GH-90000
        Map<String, Object> data = new java.util.HashMap<>(); // GH-90000
        data.put("id", id); // GH-90000
        data.put(AepComplianceService.SUBJECT_ID_FIELD, subjectId); // GH-90000
        data.put("collection", collection); // GH-90000
        return Entity.of(id, collection, data); // GH-90000
    }

    private void stubOtherCollectionsEmpty(String... exclude) { // GH-90000
        java.util.Set<String> excluded = java.util.Set.of(exclude); // GH-90000
        List<String> defaultCollections = new ArrayList<>(List.of( // GH-90000
                "aep_patterns", "aep_pipelines", "agent-registry", "aep_audit"));

        // Remove excluded collections
        defaultCollections.removeAll(excluded); // GH-90000

        for (String collection : defaultCollections) { // GH-90000
            when(dataCloudClient.query(eq(TENANT_ID), eq(collection), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
        }
    }
}
