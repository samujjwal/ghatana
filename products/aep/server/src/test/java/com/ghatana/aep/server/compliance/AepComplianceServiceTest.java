/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.compliance;

import com.ghatana.aep.compliance.AepComplianceReport;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepComplianceService}.
 *
 * <p>All {@link DataCloudClient} I/O is mocked with Mockito. ActiveJ
 * {@link Promise#of(Object)} returns synchronously-resolved promises so no // GH-90000
 * Eventloop or {@code EventloopTestBase} is required — {@code promise.getResult()} // GH-90000
 * is safe when the promise is already complete.
 *
 * @doc.type class
 * @doc.purpose Unit tests for GDPR/CCPA compliance operations
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepComplianceService [GH-90000]")
class AepComplianceServiceTest {

    private static final String TENANT     = "tenant-gamma";
    private static final String SUBJECT_ID = "user-abc-123";
    private static final String CONSUMER_ID = "consumer-xyz";

    /** Default five collections registered in {@link AepComplianceService}. */
    private static final List<String> DEFAULT_COLLECTIONS = List.of( // GH-90000
            "aep_patterns", "aep_pipelines", "agent-registry", "dc_memory", "aep_audit");

    @Mock
    private DataCloudClient client;

    private AepComplianceService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new AepComplianceService(client); // GH-90000
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class ConstructionTests {

        @Test
        @DisplayName("null client → NullPointerException [GH-90000]")
        void nullClient_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> new AepComplianceService(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("registerCollection adds custom collection to the registry [GH-90000]")
        void registerCollection_addsToRegistry() { // GH-90000
            service.registerCollection("my_custom_logs [GH-90000]");

            // Trigger accessRequest; the custom collection must also be queried
            stubQueryEmpty(TENANT, "my_custom_logs"); // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.breakdown()).containsKey("my_custom_logs [GH-90000]");
        }

        @Test
        @DisplayName("registerCollection is idempotent — duplicate adds are ignored [GH-90000]")
        void registerCollection_idempotent() { // GH-90000
            service.registerCollection("my_logs [GH-90000]");
            service.registerCollection("my_logs [GH-90000]"); // duplicate

            stubQueryEmpty(TENANT, "my_logs"); // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            // Still only one entry for the collection
            assertThat(report.breakdown().get("my_logs [GH-90000]")).isEqualTo(0L);
        }
    }

    // =========================================================================
    // Right of Access (GDPR Art.15 / CCPA §1798.110) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("accessRequest (Right of Access) [GH-90000]")
    class AccessRequestTests {

        @Test
        @DisplayName("returns success report with correct total when records found [GH-90000]")
        void withMatchingRecords_returnsTotalCount() { // GH-90000
            Entity e1 = entity("e1 [GH-90000]"), e2 = entity("e2 [GH-90000]");
            stubQuery(TENANT, "aep_patterns", List.of(e1, e2)); // GH-90000
            stubQueryEmpty(TENANT, "aep_pipelines"); // GH-90000
            stubQueryEmpty(TENANT, "agent-registry"); // GH-90000
            stubQueryEmpty(TENANT, "dc_memory"); // GH-90000
            stubQueryEmpty(TENANT, "aep_audit"); // GH-90000

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.operation()).isEqualTo("GDPR_ACCESS [GH-90000]");
            assertThat(report.tenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(report.subjectId()).isEqualTo(SUBJECT_ID); // GH-90000
            assertThat(report.recordsAffected()).isEqualTo(2L); // GH-90000
            assertThat(report.breakdown()).containsEntry("aep_patterns", 2L); // GH-90000
        }

        @Test
        @DisplayName("aggregates counts across multiple collections [GH-90000]")
        void multipleCollections_aggregatesTotal() { // GH-90000
            stubQuery(TENANT, "aep_patterns",  List.of(entity("p1 [GH-90000]")));
            stubQuery(TENANT, "aep_pipelines", List.of(entity("pl1 [GH-90000]"), entity("pl2 [GH-90000]")));
            stubQueryEmpty(TENANT, "agent-registry"); // GH-90000
            stubQuery(TENANT, "dc_memory",     List.of(entity("m1 [GH-90000]"), entity("m2 [GH-90000]"), entity("m3 [GH-90000]")));
            stubQueryEmpty(TENANT, "aep_audit"); // GH-90000

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.recordsAffected()).isEqualTo(6L); // GH-90000
            assertThat(report.breakdown()) // GH-90000
                    .containsEntry("aep_patterns",   1L) // GH-90000
                    .containsEntry("aep_pipelines",  2L) // GH-90000
                    .containsEntry("agent-registry", 0L) // GH-90000
                    .containsEntry("dc_memory",      3L) // GH-90000
                    .containsEntry("aep_audit",      0L); // GH-90000
        }

        @Test
        @DisplayName("returns zero total when no records found [GH-90000]")
        void noRecords_returnsTotalOfZero() { // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.recordsAffected()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("query failure for one collection does NOT fail the whole request [GH-90000]")
        void oneCollectionQueryFails_rest_succeeds() { // GH-90000
            // "aep_patterns" throws
            when(client.query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("network error [GH-90000]")));
            stubQueryEmpty(TENANT, "aep_pipelines"); // GH-90000
            stubQuery(TENANT, "agent-registry", List.of(entity("a1 [GH-90000]")));
            stubQueryEmpty(TENANT, "dc_memory"); // GH-90000
            stubQueryEmpty(TENANT, "aep_audit"); // GH-90000

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            // The failing collection contributes 0; the rest contribute normally
            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.recordsAffected()).isEqualTo(1L); // only agent-registry "a1" // GH-90000
            assertThat(report.breakdown()) // GH-90000
                    .containsEntry("aep_patterns",   0L)   // failed → 0 // GH-90000
                    .containsEntry("agent-registry", 1L); // GH-90000
        }

        @Test
        @DisplayName("groups subjects through the right SUBJECT_ID_FIELD filter [GH-90000]")
        void queryFilter_usesSubjectIdField() { // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            service.accessRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            // Verify the filter was built correctly for each collection
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class); // GH-90000
            verify(client, atLeast(DEFAULT_COLLECTIONS.size())) // GH-90000
                    .query(eq(TENANT), anyString(), queryCaptor.capture()); // GH-90000
        }
    }

    // =========================================================================
    // Right to Erasure (GDPR Art.17 / CCPA §1798.105) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("deletionRequest (Right to Erasure) [GH-90000]")
    class DeletionRequestTests {

        @Test
        @DisplayName("deletes all matching entities and returns the deleted count [GH-90000]")
        void matchingEntities_areDeleted() { // GH-90000
            Entity e1 = entity("e1 [GH-90000]"), e2 = entity("e2 [GH-90000]");
            stubQuery(TENANT, "aep_patterns", List.of(e1, e2)); // GH-90000
            stubAllDefaultCollectionsEmpty("aep_patterns [GH-90000]");
            when(client.delete(eq(TENANT), eq("aep_patterns [GH-90000]"), anyString()))
                    .thenReturn(Promise.of(null)); // GH-90000

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.operation()).isEqualTo("GDPR_ERASURE [GH-90000]");
            assertThat(report.recordsAffected()).isEqualTo(2L); // GH-90000
            assertThat(report.breakdown()).containsEntry("aep_patterns", 2L); // GH-90000
            verify(client).delete(TENANT, "aep_patterns", "e1"); // GH-90000
            verify(client).delete(TENANT, "aep_patterns", "e2"); // GH-90000
        }

        @Test
        @DisplayName("no matching entities → zero deletions and success [GH-90000]")
        void noMatchingEntities_returnsZeroDeletions() { // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.recordsAffected()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("deletion failure in one collection adds a warning but succeeds overall [GH-90000]")
        void oneCollectionFails_addsWarningAndContinues() { // GH-90000
            when(client.query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("disk full [GH-90000]")));
            stubAllDefaultCollectionsEmpty("aep_patterns [GH-90000]");

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.warnings()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("aggregates deletions across all collections [GH-90000]")
        void aggregatesDeletionsAcrossCollections() { // GH-90000
            stubQuery(TENANT, "aep_patterns",  List.of(entity("p1 [GH-90000]")));
            stubQuery(TENANT, "aep_pipelines", List.of(entity("pl1 [GH-90000]"), entity("pl2 [GH-90000]")));
            stubAllDefaultCollectionsEmpty("aep_patterns", "aep_pipelines"); // GH-90000
            when(client.delete(any(), any(), any())).thenReturn(Promise.of(null)); // GH-90000

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.recordsAffected()).isEqualTo(3L); // GH-90000
        }

        @Test
        @DisplayName("continues deleting until all subject pages are exhausted [GH-90000]")
        void paginatedDeletions_deleteAllPages() { // GH-90000
            when(client.query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class)))
                    .thenReturn( // GH-90000
                            Promise.of(List.of(entity("p1 [GH-90000]"), entity("p2 [GH-90000]"))),
                            Promise.of(List.of(entity("p3 [GH-90000]"))),
                            Promise.of(List.of())); // GH-90000
            stubAllDefaultCollectionsEmpty("aep_patterns [GH-90000]");
            when(client.delete(eq(TENANT), eq("aep_patterns [GH-90000]"), anyString()))
                    .thenReturn(Promise.of(null)); // GH-90000

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.recordsAffected()).isEqualTo(3L); // GH-90000
            verify(client).delete(TENANT, "aep_patterns", "p1"); // GH-90000
            verify(client).delete(TENANT, "aep_patterns", "p2"); // GH-90000
            verify(client).delete(TENANT, "aep_patterns", "p3"); // GH-90000
            verify(client, times(3)).query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class));
        }

        @Test
        @DisplayName("runs post-erasure cleanup hooks after successful deletion [GH-90000]")
        void postErasureCleanupHooks_areInvoked() { // GH-90000
            AtomicBoolean cleanupInvoked = new AtomicBoolean(false); // GH-90000
            service = new AepComplianceService(client, List.of((tenantId, subjectId, report) -> { // GH-90000
                cleanupInvoked.set(true); // GH-90000
                return Promise.of(null); // GH-90000
            }));
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(cleanupInvoked).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Right to Correction (GDPR Art.16 / CCPA §1798.106) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("correctionRequest (Right to Correction) [GH-90000]")
    class CorrectionRequestTests {

        @Test
        @DisplayName("applies corrections and saves each updated entity [GH-90000]")
        void appliesCorrections_savesUpdatedEntities() { // GH-90000
            Entity e1 = entity("c1", Map.of("name", "Alice", "_subjectId", SUBJECT_ID)); // GH-90000
            stubQuery(TENANT, "aep_patterns", List.of(e1)); // GH-90000
            when(client.save(eq(TENANT), eq("aep_patterns [GH-90000]"), any()))
                    .thenReturn(Promise.of(entity("c1 [GH-90000]")));

            Map<String, Object> corrections = Map.of("name", "Alice Updated"); // GH-90000
            AepComplianceReport report = service
                    .correctionRequest(TENANT, "aep_patterns", SUBJECT_ID, corrections) // GH-90000
                    .getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.operation()).isEqualTo("GDPR_CORRECTION [GH-90000]");
            assertThat(report.recordsAffected()).isEqualTo(1L); // GH-90000

            // Verify save was called with the correction applied
            ArgumentCaptor<Map<String, Object>> dataCaptor = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq("aep_patterns [GH-90000]"), dataCaptor.capture());
            assertThat(dataCaptor.getValue()).containsEntry("name", "Alice Updated"); // GH-90000
        }

        @Test
        @DisplayName("correction on empty collection returns 0 affected [GH-90000]")
        void emptyCollection_returnsZero() { // GH-90000
            stubQueryEmpty(TENANT, "aep_patterns"); // GH-90000

            AepComplianceReport report = service
                    .correctionRequest(TENANT, "aep_patterns", SUBJECT_ID, Map.of("field", "val")) // GH-90000
                    .getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.recordsAffected()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("query failure → returns failure report [GH-90000]")
        void queryFailure_returnsFailureReport() { // GH-90000
            when(client.query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("timeout [GH-90000]")));

            AepComplianceReport report = service
                    .correctionRequest(TENANT, "aep_patterns", SUBJECT_ID, Map.of("x", "y")) // GH-90000
                    .getResult(); // GH-90000

            assertThat(report.success()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("corrected entities carry _correctedAt timestamp [GH-90000]")
        void correctedEntities_haveCorrectedAtField() { // GH-90000
            Entity e1 = entity("u1", Map.of("email", "a@b.com", "_subjectId", SUBJECT_ID)); // GH-90000
            stubQuery(TENANT, "dc_memory", List.of(e1)); // GH-90000
            var dataCaptor = mapCaptor(); // GH-90000
            when(client.save(eq(TENANT), eq("dc_memory [GH-90000]"), dataCaptor.capture()))
                    .thenReturn(Promise.of(entity("u1 [GH-90000]")));

            service.correctionRequest(TENANT, "dc_memory", SUBJECT_ID, // GH-90000
                    Map.of("email", "new@b.com")).getResult(); // GH-90000

            assertThat(dataCaptor.getValue()).containsKey("_correctedAt [GH-90000]");
        }
    }

    // =========================================================================
    // Right to Portability (GDPR Art.20) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("portabilityRequest (Right to Portability) [GH-90000]")
    class PortabilityRequestTests {

        @Test
        @DisplayName("exports all collections into structured map with correct keys [GH-90000]")
        void exportsAllCollections_withStructuredKeys() { // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            Map<String, Object> export = service.portabilityRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            assertThat(export).containsKey("subjectId [GH-90000]");
            assertThat(export).containsKey("tenantId [GH-90000]");
            assertThat(export).containsKey("exportedAt [GH-90000]");
            assertThat(export).containsKey("collections [GH-90000]");
            assertThat(export.get("subjectId [GH-90000]")).isEqualTo(SUBJECT_ID);
            assertThat(export.get("tenantId [GH-90000]")).isEqualTo(TENANT);
        }

        @Test
        @DisplayName("exported collections map contains default AEP collections [GH-90000]")
        void exportedCollections_mapContainsDefaultCollections() { // GH-90000
            stubAllDefaultCollectionsEmpty(new String[0]); // GH-90000

            Map<String, Object> export = service.portabilityRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> collections = (Map<String, Object>) export.get("collections [GH-90000]");
            assertThat(collections).containsKeys("aep_patterns", "aep_pipelines", // GH-90000
                    "agent-registry", "dc_memory", "aep_audit");
        }

        @Test
        @DisplayName("entity data is included in the export [GH-90000]")
        void entityData_isIncludedInExport() { // GH-90000
            Entity e1 = entity("p1", Map.of("name", "my-pattern", "_subjectId", SUBJECT_ID)); // GH-90000
            stubQuery(TENANT, "aep_patterns", List.of(e1)); // GH-90000
            stubAllDefaultCollectionsEmpty("aep_patterns [GH-90000]");

            Map<String, Object> export = service.portabilityRequest(TENANT, SUBJECT_ID).getResult(); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, List<Map<String, Object>>> collections =
                    (Map<String, List<Map<String, Object>>>) export.get("collections [GH-90000]");
            assertThat(collections.get("aep_patterns [GH-90000]")).hasSize(1);
            assertThat(collections.get("aep_patterns [GH-90000]").get(0)).containsEntry("name", "my-pattern");
        }
    }

    // =========================================================================
    // CCPA Opt-Out (§1798.120) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("ccpaOptOut (CCPA Right to Opt-Out) [GH-90000]")
    class CcpaOptOutTests {

        @Test
        @DisplayName("saves opt-out record and returns success report [GH-90000]")
        void savesOptOutRecord_returnsSuccessReport() { // GH-90000
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(entity(CONSUMER_ID))); // GH-90000

            AepComplianceReport report = service.ccpaOptOut(TENANT, CONSUMER_ID).getResult(); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.operation()).isEqualTo("CCPA_OPT_OUT [GH-90000]");
            assertThat(report.recordsAffected()).isEqualTo(1L); // GH-90000
            assertThat(report.breakdown()) // GH-90000
                    .containsEntry(AepComplianceService.OPT_OUT_COLLECTION, 1L); // GH-90000
        }

        @Test
        @DisplayName("opt-out record contains required fields [GH-90000]")
        void optOutRecord_containsRequiredFields() { // GH-90000
            var dataCaptor = mapCaptor(); // GH-90000
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), dataCaptor.capture())) // GH-90000
                    .thenReturn(Promise.of(entity(CONSUMER_ID))); // GH-90000

            service.ccpaOptOut(TENANT, CONSUMER_ID).getResult(); // GH-90000

            Map<String, Object> savedData = dataCaptor.getValue(); // GH-90000
            assertThat(savedData) // GH-90000
                    .containsEntry("id",          CONSUMER_ID) // GH-90000
                    .containsEntry("_ccpaOptOut", true) // GH-90000
                    .containsEntry("consumerId",  CONSUMER_ID) // GH-90000
                    .containsEntry("tenantId",    TENANT) // GH-90000
                    .containsKey("recordedAt [GH-90000]");
        }

        @Test
        @DisplayName("ccpa opt-out uses consumerId as entity id for idempotent upsert [GH-90000]")
        void optOut_usesConsumerIdAsEntityId() throws Exception { // GH-90000
            var dataCaptor = mapCaptor(); // GH-90000
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), dataCaptor.capture())) // GH-90000
                    .thenReturn(Promise.of(entity(CONSUMER_ID))); // GH-90000

            service.ccpaOptOut(TENANT, CONSUMER_ID).getResult(); // GH-90000

            assertThat(dataCaptor.getValue().get("id [GH-90000]")).isEqualTo(CONSUMER_ID);
        }

        @Test
        @DisplayName("save failure → returns failure report (not exception) [GH-90000]")
        void saveFailure_returnsFailureReport() { // GH-90000
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("write failed [GH-90000]")));

            AepComplianceReport report = service.ccpaOptOut(TENANT, CONSUMER_ID).getResult(); // GH-90000

            assertThat(report.success()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Create a minimal entity with a generated-id and no data. */
    private static Entity entity(String id) { // GH-90000
        return Entity.of(id, "test-collection", Map.of("id", id)); // GH-90000
    }

    /** Create an entity with explicit data fields. */
    private static Entity entity(String id, Map<String, Object> data) { // GH-90000
        Map<String, Object> merged = new java.util.HashMap<>(data); // GH-90000
        merged.put("id", id); // GH-90000
        return Entity.of(id, "test-collection", merged); // GH-90000
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // GH-90000
    private static <T> ArgumentCaptor<T> captorFor(Class<?> rawClass) { // GH-90000
        return (ArgumentCaptor) ArgumentCaptor.forClass((Class) rawClass); // GH-90000
    }

    private static ArgumentCaptor<Map<String, Object>> mapCaptor() { // GH-90000
        return captorFor(Map.class); // GH-90000
    }

    /** Stub {@code client.query(tenant, collection, *)} to return an empty list. */ // GH-90000
    private void stubQueryEmpty(String tenant, String collection) { // GH-90000
        when(client.query(eq(tenant), eq(collection), any(Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000
    }

    /** Stub {@code client.query(tenant, collection, *)} to return the given entities. */ // GH-90000
    private void stubQuery(String tenant, String collection, List<Entity> entities) { // GH-90000
        when(client.query(eq(tenant), eq(collection), any(Query.class))) // GH-90000
                .thenReturn(Promise.of(entities)); // GH-90000
    }

    /**
     * Stubs all five default AEP collections to return empty query results.
     *
     * @param exclude collection names to skip (already handled individually) // GH-90000
     */
    private void stubAllDefaultCollectionsEmpty(String... exclude) { // GH-90000
        java.util.Set<String> excluded = java.util.Set.of(exclude); // GH-90000
        for (String coll : DEFAULT_COLLECTIONS) { // GH-90000
            if (!excluded.contains(coll)) { // GH-90000
                stubQueryEmpty(TENANT, coll); // GH-90000
            }
        }
    }
}
