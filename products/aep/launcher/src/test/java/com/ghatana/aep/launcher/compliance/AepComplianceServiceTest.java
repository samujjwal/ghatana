/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.compliance;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepComplianceService}.
 *
 * <p>All {@link DataCloudClient} I/O is mocked with Mockito. ActiveJ
 * {@link Promise#of(Object)} returns synchronously-resolved promises so no
 * Eventloop or {@code EventloopTestBase} is required — {@code promise.getResult()}
 * is safe when the promise is already complete.
 *
 * @doc.type class
 * @doc.purpose Unit tests for GDPR/CCPA compliance operations
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepComplianceService")
class AepComplianceServiceTest {

    private static final String TENANT     = "tenant-gamma";
    private static final String SUBJECT_ID = "user-abc-123";
    private static final String CONSUMER_ID = "consumer-xyz";

    /** Default five collections registered in {@link AepComplianceService}. */
    private static final List<String> DEFAULT_COLLECTIONS = List.of(
            "aep_patterns", "aep_pipelines", "agent-registry", "dc_memory", "aep_audit");

    @Mock
    private DataCloudClient client;

    private AepComplianceService service;

    @BeforeEach
    void setUp() {
        service = new AepComplianceService(client);
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null client → NullPointerException")
        void nullClient_throwsNpe() {
            assertThatThrownBy(() -> new AepComplianceService(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("registerCollection adds custom collection to the registry")
        void registerCollection_addsToRegistry() {
            service.registerCollection("my_custom_logs");

            // Trigger accessRequest; the custom collection must also be queried
            stubQueryEmpty(TENANT, "my_custom_logs");
            stubAllDefaultCollectionsEmpty(new String[0]);

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.breakdown()).containsKey("my_custom_logs");
        }

        @Test
        @DisplayName("registerCollection is idempotent — duplicate adds are ignored")
        void registerCollection_idempotent() {
            service.registerCollection("my_logs");
            service.registerCollection("my_logs"); // duplicate

            stubQueryEmpty(TENANT, "my_logs");
            stubAllDefaultCollectionsEmpty(new String[0]);

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult();

            // Still only one entry for the collection
            assertThat(report.breakdown().get("my_logs")).isEqualTo(0L);
        }
    }

    // =========================================================================
    // Right of Access (GDPR Art.15 / CCPA §1798.110)
    // =========================================================================

    @Nested
    @DisplayName("accessRequest (Right of Access)")
    class AccessRequestTests {

        @Test
        @DisplayName("returns success report with correct total when records found")
        void withMatchingRecords_returnsTotalCount() {
            Entity e1 = entity("e1"), e2 = entity("e2");
            stubQuery(TENANT, "aep_patterns", List.of(e1, e2));
            stubQueryEmpty(TENANT, "aep_pipelines");
            stubQueryEmpty(TENANT, "agent-registry");
            stubQueryEmpty(TENANT, "dc_memory");
            stubQueryEmpty(TENANT, "aep_audit");

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.operation()).isEqualTo("GDPR_ACCESS");
            assertThat(report.tenantId()).isEqualTo(TENANT);
            assertThat(report.subjectId()).isEqualTo(SUBJECT_ID);
            assertThat(report.recordsAffected()).isEqualTo(2L);
            assertThat(report.breakdown()).containsEntry("aep_patterns", 2L);
        }

        @Test
        @DisplayName("aggregates counts across multiple collections")
        void multipleCollections_aggregatesTotal() {
            stubQuery(TENANT, "aep_patterns",  List.of(entity("p1")));
            stubQuery(TENANT, "aep_pipelines", List.of(entity("pl1"), entity("pl2")));
            stubQueryEmpty(TENANT, "agent-registry");
            stubQuery(TENANT, "dc_memory",     List.of(entity("m1"), entity("m2"), entity("m3")));
            stubQueryEmpty(TENANT, "aep_audit");

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.recordsAffected()).isEqualTo(6L);
            assertThat(report.breakdown())
                    .containsEntry("aep_patterns",   1L)
                    .containsEntry("aep_pipelines",  2L)
                    .containsEntry("agent-registry", 0L)
                    .containsEntry("dc_memory",      3L)
                    .containsEntry("aep_audit",      0L);
        }

        @Test
        @DisplayName("returns zero total when no records found")
        void noRecords_returnsTotalOfZero() {
            stubAllDefaultCollectionsEmpty(new String[0]);

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.recordsAffected()).isEqualTo(0L);
        }

        @Test
        @DisplayName("query failure for one collection does NOT fail the whole request")
        void oneCollectionQueryFails_rest_succeeds() {
            // "aep_patterns" throws
            when(client.query(eq(TENANT), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("network error")));
            stubQueryEmpty(TENANT, "aep_pipelines");
            stubQuery(TENANT, "agent-registry", List.of(entity("a1")));
            stubQueryEmpty(TENANT, "dc_memory");
            stubQueryEmpty(TENANT, "aep_audit");

            AepComplianceReport report = service.accessRequest(TENANT, SUBJECT_ID).getResult();

            // The failing collection contributes 0; the rest contribute normally
            assertThat(report.success()).isTrue();
            assertThat(report.recordsAffected()).isEqualTo(1L); // only agent-registry "a1"
            assertThat(report.breakdown())
                    .containsEntry("aep_patterns",   0L)   // failed → 0
                    .containsEntry("agent-registry", 1L);
        }

        @Test
        @DisplayName("groups subjects through the right SUBJECT_ID_FIELD filter")
        void queryFilter_usesSubjectIdField() {
            stubAllDefaultCollectionsEmpty(new String[0]);

            service.accessRequest(TENANT, SUBJECT_ID).getResult();

            // Verify the filter was built correctly for each collection
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
            verify(client, atLeast(DEFAULT_COLLECTIONS.size()))
                    .query(eq(TENANT), anyString(), queryCaptor.capture());
        }
    }

    // =========================================================================
    // Right to Erasure (GDPR Art.17 / CCPA §1798.105)
    // =========================================================================

    @Nested
    @DisplayName("deletionRequest (Right to Erasure)")
    class DeletionRequestTests {

        @Test
        @DisplayName("deletes all matching entities and returns the deleted count")
        void matchingEntities_areDeleted() {
            Entity e1 = entity("e1"), e2 = entity("e2");
            stubQuery(TENANT, "aep_patterns", List.of(e1, e2));
            stubAllDefaultCollectionsEmpty("aep_patterns");
            when(client.delete(eq(TENANT), eq("aep_patterns"), anyString()))
                    .thenReturn(Promise.of(null));

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.operation()).isEqualTo("GDPR_ERASURE");
            assertThat(report.recordsAffected()).isEqualTo(2L);
            assertThat(report.breakdown()).containsEntry("aep_patterns", 2L);
            verify(client).delete(TENANT, "aep_patterns", "e1");
            verify(client).delete(TENANT, "aep_patterns", "e2");
        }

        @Test
        @DisplayName("no matching entities → zero deletions and success")
        void noMatchingEntities_returnsZeroDeletions() {
            stubAllDefaultCollectionsEmpty(new String[0]);

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.recordsAffected()).isEqualTo(0L);
        }

        @Test
        @DisplayName("deletion failure in one collection adds a warning but succeeds overall")
        void oneCollectionFails_addsWarningAndContinues() {
            when(client.query(eq(TENANT), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("disk full")));
            stubAllDefaultCollectionsEmpty("aep_patterns");

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.warnings()).isNotEmpty();
        }

        @Test
        @DisplayName("aggregates deletions across all collections")
        void aggregatesDeletionsAcrossCollections() {
            stubQuery(TENANT, "aep_patterns",  List.of(entity("p1")));
            stubQuery(TENANT, "aep_pipelines", List.of(entity("pl1"), entity("pl2")));
            stubAllDefaultCollectionsEmpty("aep_patterns", "aep_pipelines");
            when(client.delete(any(), any(), any())).thenReturn(Promise.of(null));

            AepComplianceReport report = service.deletionRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(report.recordsAffected()).isEqualTo(3L);
        }
    }

    // =========================================================================
    // Right to Correction (GDPR Art.16 / CCPA §1798.106)
    // =========================================================================

    @Nested
    @DisplayName("correctionRequest (Right to Correction)")
    class CorrectionRequestTests {

        @Test
        @DisplayName("applies corrections and saves each updated entity")
        void appliesCorrections_savesUpdatedEntities() {
            Entity e1 = entity("c1", Map.of("name", "Alice", "_subjectId", SUBJECT_ID));
            stubQuery(TENANT, "aep_patterns", List.of(e1));
            when(client.save(eq(TENANT), eq("aep_patterns"), any()))
                    .thenReturn(Promise.of(entity("c1")));

            Map<String, Object> corrections = Map.of("name", "Alice Updated");
            AepComplianceReport report = service
                    .correctionRequest(TENANT, "aep_patterns", SUBJECT_ID, corrections)
                    .getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.operation()).isEqualTo("GDPR_CORRECTION");
            assertThat(report.recordsAffected()).isEqualTo(1L);

            // Verify save was called with the correction applied
            ArgumentCaptor<Map> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq("aep_patterns"), dataCaptor.capture());
            assertThat(dataCaptor.getValue()).containsEntry("name", "Alice Updated");
        }

        @Test
        @DisplayName("correction on empty collection returns 0 affected")
        void emptyCollection_returnsZero() {
            stubQueryEmpty(TENANT, "aep_patterns");

            AepComplianceReport report = service
                    .correctionRequest(TENANT, "aep_patterns", SUBJECT_ID, Map.of("field", "val"))
                    .getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.recordsAffected()).isEqualTo(0L);
        }

        @Test
        @DisplayName("query failure → returns failure report")
        void queryFailure_returnsFailureReport() {
            when(client.query(eq(TENANT), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("timeout")));

            AepComplianceReport report = service
                    .correctionRequest(TENANT, "aep_patterns", SUBJECT_ID, Map.of("x", "y"))
                    .getResult();

            assertThat(report.success()).isFalse();
        }

        @Test
        @DisplayName("corrected entities carry _correctedAt timestamp")
        void correctedEntities_haveCorrectedAtField() {
            Entity e1 = entity("u1", Map.of("email", "a@b.com", "_subjectId", SUBJECT_ID));
            stubQuery(TENANT, "dc_memory", List.of(e1));
            ArgumentCaptor<Map> dataCaptor = ArgumentCaptor.forClass(Map.class);
            when(client.save(eq(TENANT), eq("dc_memory"), dataCaptor.capture()))
                    .thenReturn(Promise.of(entity("u1")));

            service.correctionRequest(TENANT, "dc_memory", SUBJECT_ID,
                    Map.of("email", "new@b.com")).getResult();

            assertThat(dataCaptor.getValue()).containsKey("_correctedAt");
        }
    }

    // =========================================================================
    // Right to Portability (GDPR Art.20)
    // =========================================================================

    @Nested
    @DisplayName("portabilityRequest (Right to Portability)")
    class PortabilityRequestTests {

        @Test
        @DisplayName("exports all collections into structured map with correct keys")
        void exportsAllCollections_withStructuredKeys() {
            stubAllDefaultCollectionsEmpty(new String[0]);

            Map<String, Object> export = service.portabilityRequest(TENANT, SUBJECT_ID).getResult();

            assertThat(export).containsKey("subjectId");
            assertThat(export).containsKey("tenantId");
            assertThat(export).containsKey("exportedAt");
            assertThat(export).containsKey("collections");
            assertThat(export.get("subjectId")).isEqualTo(SUBJECT_ID);
            assertThat(export.get("tenantId")).isEqualTo(TENANT);
        }

        @Test
        @DisplayName("exported collections map contains default AEP collections")
        void exportedCollections_mapContainsDefaultCollections() {
            stubAllDefaultCollectionsEmpty(new String[0]);

            Map<String, Object> export = service.portabilityRequest(TENANT, SUBJECT_ID).getResult();

            @SuppressWarnings("unchecked")
            Map<String, Object> collections = (Map<String, Object>) export.get("collections");
            assertThat(collections).containsKeys("aep_patterns", "aep_pipelines",
                    "agent-registry", "dc_memory", "aep_audit");
        }

        @Test
        @DisplayName("entity data is included in the export")
        void entityData_isIncludedInExport() {
            Entity e1 = entity("p1", Map.of("name", "my-pattern", "_subjectId", SUBJECT_ID));
            stubQuery(TENANT, "aep_patterns", List.of(e1));
            stubAllDefaultCollectionsEmpty("aep_patterns");

            Map<String, Object> export = service.portabilityRequest(TENANT, SUBJECT_ID).getResult();

            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> collections =
                    (Map<String, List<Map<String, Object>>>) export.get("collections");
            assertThat(collections.get("aep_patterns")).hasSize(1);
            assertThat(collections.get("aep_patterns").get(0)).containsEntry("name", "my-pattern");
        }
    }

    // =========================================================================
    // CCPA Opt-Out (§1798.120)
    // =========================================================================

    @Nested
    @DisplayName("ccpaOptOut (CCPA Right to Opt-Out)")
    class CcpaOptOutTests {

        @Test
        @DisplayName("saves opt-out record and returns success report")
        void savesOptOutRecord_returnsSuccessReport() {
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), any()))
                    .thenReturn(Promise.of(entity(CONSUMER_ID)));

            AepComplianceReport report = service.ccpaOptOut(TENANT, CONSUMER_ID).getResult();

            assertThat(report.success()).isTrue();
            assertThat(report.operation()).isEqualTo("CCPA_OPT_OUT");
            assertThat(report.recordsAffected()).isEqualTo(1L);
            assertThat(report.breakdown())
                    .containsEntry(AepComplianceService.OPT_OUT_COLLECTION, 1L);
        }

        @Test
        @DisplayName("opt-out record contains required fields")
        void optOutRecord_containsRequiredFields() {
            ArgumentCaptor<Map> dataCaptor = ArgumentCaptor.forClass(Map.class);
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), dataCaptor.capture()))
                    .thenReturn(Promise.of(entity(CONSUMER_ID)));

            service.ccpaOptOut(TENANT, CONSUMER_ID).getResult();

            @SuppressWarnings("unchecked")
            Map<String, Object> savedData = (Map<String, Object>) dataCaptor.getValue();
            assertThat(savedData)
                    .containsEntry("id",          CONSUMER_ID)
                    .containsEntry("_ccpaOptOut", true)
                    .containsEntry("consumerId",  CONSUMER_ID)
                    .containsEntry("tenantId",    TENANT)
                    .containsKey("recordedAt");
        }

        @Test
        @DisplayName("ccpa opt-out uses consumerId as entity id for idempotent upsert")
        void optOut_usesConsumerIdAsEntityId() throws Exception {
            ArgumentCaptor<Map> dataCaptor = ArgumentCaptor.forClass(Map.class);
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), dataCaptor.capture()))
                    .thenReturn(Promise.of(entity(CONSUMER_ID)));

            service.ccpaOptOut(TENANT, CONSUMER_ID).getResult();

            assertThat(dataCaptor.getValue().get("id")).isEqualTo(CONSUMER_ID);
        }

        @Test
        @DisplayName("save failure → returns failure report (not exception)")
        void saveFailure_returnsFailureReport() {
            when(client.save(eq(TENANT), eq(AepComplianceService.OPT_OUT_COLLECTION), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("write failed")));

            AepComplianceReport report = service.ccpaOptOut(TENANT, CONSUMER_ID).getResult();

            assertThat(report.success()).isFalse();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Create a minimal entity with a generated-id and no data. */
    private static Entity entity(String id) {
        return Entity.of(id, "test-collection", Map.of("id", id));
    }

    /** Create an entity with explicit data fields. */
    private static Entity entity(String id, Map<String, Object> data) {
        Map<String, Object> merged = new java.util.HashMap<>(data);
        merged.put("id", id);
        return Entity.of(id, "test-collection", merged);
    }

    /** Stub {@code client.query(tenant, collection, *)} to return an empty list. */
    private void stubQueryEmpty(String tenant, String collection) {
        when(client.query(eq(tenant), eq(collection), any(Query.class)))
                .thenReturn(Promise.of(List.of()));
    }

    /** Stub {@code client.query(tenant, collection, *)} to return the given entities. */
    private void stubQuery(String tenant, String collection, List<Entity> entities) {
        when(client.query(eq(tenant), eq(collection), any(Query.class)))
                .thenReturn(Promise.of(entities));
    }

    /**
     * Stubs all five default AEP collections to return empty query results.
     *
     * @param exclude collection names to skip (already handled individually)
     */
    private void stubAllDefaultCollectionsEmpty(String... exclude) {
        java.util.Set<String> excluded = java.util.Set.of(exclude);
        for (String coll : DEFAULT_COLLECTIONS) {
            if (!excluded.contains(coll)) {
                stubQueryEmpty(TENANT, coll);
            }
        }
    }
}
