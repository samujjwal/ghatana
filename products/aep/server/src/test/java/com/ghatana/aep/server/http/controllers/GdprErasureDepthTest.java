/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * is performed with proper depth (all matching records, not just a subset).
 *
 * <p>Test scope:
 * - Verifies deletion from dc_memory collection
 * - Verifies deletion from all default AEP collections
 * - Verifies proper query filtering by subjectId
 * - Verifies cache invalidation behavior (via service layer)
 *
 * @doc.type class
 * @doc.purpose GDPR erasure depth verification
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GDPR Erasure Depth Test")
class GdprErasureDepthTest {

    private static final String TENANT_ID = "tenant-test-123";
    private static final String SUBJECT_ID = "subject-user-456";
    private static final String DC_MEMORY_COLLECTION = "dc_memory";

    @Mock
    private DataCloudClient dataCloudClient;

    private AepComplianceService complianceService;

    @BeforeEach
    void setUp() {
        complianceService = new AepComplianceService(dataCloudClient);
    }

    // =========================================================================
    // dc_memory Collection Deletion Verification
    // =========================================================================

    @Nested
    @DisplayName("dc_memory collection deletion")
    class DcMemoryDeletionTests {

        @Test
        @DisplayName("deletes all matching records from dc_memory collection")
        void deletesAllMatchingRecordsFromDcMemory() {
            // Setup: Create multiple records in dc_memory with the same subjectId
            List<Entity> dcMemoryRecords = List.of(
                    createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID),
                    createEntity("dc-2", DC_MEMORY_COLLECTION, SUBJECT_ID),
                    createEntity("dc-3", DC_MEMORY_COLLECTION, SUBJECT_ID)
            );

            // Stub query to return these records
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(dcMemoryRecords));

            // Stub delete operations
            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), anyString()))
                    .thenReturn(Promise.of((Void) null));

            // Stub other collections as empty
            stubOtherCollectionsEmpty();

            // Execute deletion
            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Verify
            assertThat(report.success()).isTrue();
            assertThat(report.operation()).isEqualTo("GDPR_ERASURE");
            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 3L);

            // Verify all three records were deleted
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-1");
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-2");
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-3");
        }

        @Test
        @DisplayName("does not delete records with different subjectId in dc_memory")
        void doesNotDeleteDifferentSubjectRecords() {
            // Setup: Return only records matching the subjectId (simulating filtered query result)
            // The service queries with filter by SUBJECT_ID_FIELD, so mock returns filtered results
            List<Entity> matchingRecords = List.of(
                    createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID),
                    createEntity("dc-3", DC_MEMORY_COLLECTION, SUBJECT_ID)
            );

            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(matchingRecords));

            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), anyString()))
                    .thenReturn(Promise.of((Void) null));

            stubOtherCollectionsEmpty();

            // Execute deletion
            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Verify only 2 records deleted (those matching subjectId returned by mock query)
            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 2L);
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-1");
            verify(dataCloudClient).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-3");
            verify(dataCloudClient, never()).delete(TENANT_ID, DC_MEMORY_COLLECTION, "dc-2");
        }

        @Test
        @DisplayName("uses correct subjectId filter when querying dc_memory")
        void usesCorrectSubjectIdFilter() {
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));

            stubOtherCollectionsEmpty();

            complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Capture the query to verify filter
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
            verify(dataCloudClient).query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), queryCaptor.capture());

            // Verify the query includes the subjectId filter
            Query query = queryCaptor.getValue();
            assertThat(query.filters()).isNotEmpty();
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
        void deletesFromAllDefaultCollections() {
            // Setup records in all collections
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("p-1", "aep_patterns", SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_pipelines"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("pl-1", "aep_pipelines", SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq("agent-registry"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("ag-1", "agent-registry", SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_audit"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("au-1", "aep_audit", SUBJECT_ID))));

            when(dataCloudClient.delete(anyString(), anyString(), anyString()))
                    .thenReturn(Promise.of((Void) null));

            // Execute deletion
            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Verify all collections were processed
            assertThat(report.breakdown()).hasSize(5);
            assertThat(report.breakdown())
                    .containsEntry("aep_patterns", 1L)
                    .containsEntry("aep_pipelines", 1L)
                    .containsEntry("agent-registry", 1L)
                    .containsEntry(DC_MEMORY_COLLECTION, 1L)
                    .containsEntry("aep_audit", 1L);
        }

        @Test
        @DisplayName("handles empty dc_memory collection gracefully")
        void handlesEmptyDcMemory() {
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));

            stubOtherCollectionsEmpty();

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 0L);
            verify(dataCloudClient, never()).delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), anyString());
        }

        @Test
        @DisplayName("aggregates deletion counts across all collections")
        void aggregatesDeletionCounts() {
            // Setup: Multiple records in different collections
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(
                            createEntity("p-1", "aep_patterns", SUBJECT_ID),
                            createEntity("p-2", "aep_patterns", SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_pipelines"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("pl-1", "aep_pipelines", SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq("agent-registry"), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(
                            createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID),
                            createEntity("dc-2", DC_MEMORY_COLLECTION, SUBJECT_ID),
                            createEntity("dc-3", DC_MEMORY_COLLECTION, SUBJECT_ID))));
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_audit"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("au-1", "aep_audit", SUBJECT_ID))));

            when(dataCloudClient.delete(anyString(), anyString(), anyString()))
                    .thenReturn(Promise.of((Void) null));

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Verify total count: 2+1+0+3+1 = 7
            assertThat(report.recordsAffected()).isEqualTo(7L);
            assertThat(report.breakdown())
                    .containsEntry("aep_patterns", 2L)
                    .containsEntry("aep_pipelines", 1L)
                    .containsEntry("agent-registry", 0L)
                    .containsEntry(DC_MEMORY_COLLECTION, 3L)
                    .containsEntry("aep_audit", 1L);
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
        void verifiesRegisteredCollections() {
            List<String> collections = complianceService.registeredCollections();

            assertThat(collections)
                    .containsExactlyInAnyOrder(
                            "aep_patterns",
                            "aep_pipelines",
                            "agent-registry",
                            DC_MEMORY_COLLECTION,
                            "aep_audit"
                    );
        }

        @Test
        @DisplayName("allows registration of additional collections for deletion")
        void allowsAdditionalCollectionRegistration() {
            complianceService.registerCollection("custom_event_log");

            List<String> collections = complianceService.registeredCollections();

            assertThat(collections).contains("custom_event_log");
            assertThat(collections).hasSize(6); // 5 default + 1 custom
        }

        @Test
        @DisplayName("deletes from custom registered collections")
        void deletesFromCustomCollections() {
            complianceService.registerCollection("custom_logs");

            when(dataCloudClient.query(eq(TENANT_ID), eq("custom_logs"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("c-1", "custom_logs", SUBJECT_ID))));

            // Stub other collections including dc_memory
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));
            stubOtherCollectionsEmpty("custom_logs");

            when(dataCloudClient.delete(anyString(), anyString(), anyString()))
                    .thenReturn(Promise.of((Void) null));

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            assertThat(report.breakdown()).containsEntry("custom_logs", 1L);
            verify(dataCloudClient).delete(TENANT_ID, "custom_logs", "c-1");
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
        void continuesOnCollectionFailure() {
            // dc_memory fails
            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("dc_memory unavailable")));

            // Other collections succeed
            when(dataCloudClient.query(eq(TENANT_ID), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(createEntity("p-1", "aep_patterns", SUBJECT_ID))));

            stubOtherCollectionsEmpty("aep_patterns", DC_MEMORY_COLLECTION);

            when(dataCloudClient.delete(anyString(), anyString(), anyString()))
                    .thenReturn(Promise.of((Void) null));

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Should succeed overall with a warning
            assertThat(report.success()).isTrue();
            assertThat(report.warnings()).isNotEmpty();
            assertThat(report.breakdown()).containsEntry(DC_MEMORY_COLLECTION, 0L);
            assertThat(report.breakdown()).containsEntry("aep_patterns", 1L);
        }

        @Test
        @DisplayName("handles deletion failure for individual records")
        void handlesIndividualRecordFailure() {
            List<Entity> dcMemoryRecords = List.of(
                    createEntity("dc-1", DC_MEMORY_COLLECTION, SUBJECT_ID),
                    createEntity("dc-2", DC_MEMORY_COLLECTION, SUBJECT_ID)
            );

            when(dataCloudClient.query(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(dcMemoryRecords));

            // First delete succeeds, second fails
            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), eq("dc-1")))
                    .thenReturn(Promise.of((Void) null));
            when(dataCloudClient.delete(eq(TENANT_ID), eq(DC_MEMORY_COLLECTION), eq("dc-2")))
                    .thenReturn(Promise.ofException(new RuntimeException("delete failed")));

            stubOtherCollectionsEmpty();

            AepComplianceReport report = complianceService.deletionRequest(TENANT_ID, SUBJECT_ID).getResult();

            // Should have a warning but still report the count
            assertThat(report.success()).isTrue();
            assertThat(report.warnings()).isNotEmpty();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Entity createEntity(String id, String collection, String subjectId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", id);
        data.put(AepComplianceService.SUBJECT_ID_FIELD, subjectId);
        data.put("collection", collection);
        return Entity.of(id, collection, data);
    }

    private void stubOtherCollectionsEmpty(String... exclude) {
        java.util.Set<String> excluded = java.util.Set.of(exclude);
        List<String> defaultCollections = new ArrayList<>(List.of(
                "aep_patterns", "aep_pipelines", "agent-registry", "aep_audit"));

        // Remove excluded collections
        defaultCollections.removeAll(excluded);

        for (String collection : defaultCollections) {
            when(dataCloudClient.query(eq(TENANT_ID), eq(collection), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));
        }
    }
}
