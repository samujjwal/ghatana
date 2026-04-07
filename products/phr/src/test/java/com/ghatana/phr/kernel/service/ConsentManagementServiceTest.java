/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.DataStream;
import com.ghatana.kernel.adapter.datacloud.DataStreamRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.adapter.datacloud.TransactionHandle;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.consent.ConsentService.*;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.phr.kernel.service.ConsentManagementService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConsentManagementService} covering:
 * <ul>
 *   <li>Grant creation and conflict checks</li>
 *   <li>Consent access decision (checkAccess) with self-access, emergency, cache</li>
 *   <li>Rate limiting on createGrant and checkAccess</li>
 *   <li>Service lifecycle (not-running guard)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for PHR consent management service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConsentManagementService")
class ConsentManagementServiceTest extends EventloopTestBase {

    private StubDataCloudAdapter dataCloud;
    private ConsentManagementService service;
    private PhrNotificationTestSupport.RecordingNotificationSender notificationSender;

    @BeforeEach
    void setUp() {
        dataCloud = new StubDataCloudAdapter();
        KernelContext context = createTestContext(dataCloud);
        notificationSender = new PhrNotificationTestSupport.RecordingNotificationSender();
        service = new ConsentManagementService(context, notificationSender);
        runPromise(service::start);
    }

    // ==================== Service Lifecycle ====================

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("rejects createGrant when not running")
        void rejectsCreateGrantWhenStopped() {
            runPromise(service::stop);

            ConsentGrant grant = testGrant("patient-1", "doctor-1");
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createGrant(grant)));
            clearFatalError();
        }

        @Test
        @DisplayName("checkAccess returns SYSTEM_DENY when not running")
        void checkAccessDeniedWhenStopped() {
            runPromise(service::stop);

            ConsentCheckRequest request = selfAccessRequest("patient-1");
            ConsentAccessDecision decision = runPromise(() -> service.checkAccess(request));

            assertFalse(decision.allowed());
            assertEquals(ReasonCode.SYSTEM_DENY, decision.reasonCode());
        }

        @Test
        @DisplayName("isHealthy returns true when running")
        void healthyWhenRunning() {
            assertTrue(service.isHealthy());
        }

        @Test
        @DisplayName("isHealthy returns false after stop")
        void unhealthyAfterStop() {
            runPromise(service::stop);
            assertFalse(service.isHealthy());
        }
    }

    // ==================== checkAccess ====================

    @Nested
    @DisplayName("checkAccess")
    class CheckAccess {

        @Test
        @DisplayName("allows self-access (patient accessing own data)")
        void selfAccess() {
            ConsentCheckRequest request = selfAccessRequest("patient-1");
            ConsentAccessDecision decision = runPromise(() -> service.checkAccess(request));

            assertTrue(decision.allowed());
            assertEquals(ReasonCode.SELF_ACCESS, decision.reasonCode());
        }

        @Test
        @DisplayName("allows emergency access with audit obligation")
        void emergencyAccess() {
            ConsentCheckRequest request = emergencyAccessRequest("patient-1", "doctor-1");
            ConsentAccessDecision decision = runPromise(() -> service.checkAccess(request));

            assertTrue(decision.allowed());
            assertEquals(ReasonCode.EMERGENCY_GRANT, decision.reasonCode());
            assertTrue(decision.obligations().contains("SUBMIT_POST_HOC_JUSTIFICATION"));
        }

        @Test
        @DisplayName("denies access when no grant exists")
        void deniesWithNoGrant() {
            ConsentCheckRequest request = providerAccessRequest("patient-1", "doctor-1", "medications");
            ConsentAccessDecision decision = runPromise(() -> service.checkAccess(request));

            assertFalse(decision.allowed());
            assertEquals(ReasonCode.OUT_OF_SCOPE, decision.reasonCode());
        }
    }

    // ==================== assertAccess ====================

    @Nested
    @DisplayName("assertAccess")
    class AssertAccess {

        @Test
        @DisplayName("passes for self-access")
        void passesForSelfAccess() {
            ConsentCheckRequest request = selfAccessRequest("patient-1");
            ConsentAccessDecision decision = runPromise(() -> service.assertAccess(request));
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("throws when denied")
        void throwsWhenDenied() {
            ConsentCheckRequest request = providerAccessRequest("patient-1", "doc-999", "medications");
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.assertAccess(request)));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("createEmergencyAccess")
    class EmergencyAccessGrantTests {

        @Test
        @DisplayName("creates a time-limited emergency grant with constrained scope")
        void createsTimeLimitedEmergencyGrant() {
            EmergencyGrant grant = runPromise(() -> service.createEmergencyAccess(
                    new EmergencyAccessRequest("patient-1", "doctor-1", "Patient unconscious", "trauma")));

            assertEquals("patient-1", grant.getPatientId());
            assertEquals("doctor-1", grant.getProviderId());
            assertTrue(grant.getExpiresAt().isAfter(grant.getGrantedAt()));
            assertTrue(grant.getExpiresAt().isBefore(grant.getGrantedAt().plusSeconds(4 * 3600 + 60)));
            assertEquals(Set.of("allergies", "medications", "bloodType", "emergencyContacts"),
                    grant.getAllowedResources());
            assertFalse(grant.isJustificationSubmitted());
        }

        @Test
        @DisplayName("requires a category for emergency grants")
        void requiresCategory() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createEmergencyAccess(
                            new EmergencyAccessRequest("patient-1", "doctor-1", "Emergency", "  "))));
            clearFatalError();
        }

        @Test
        @DisplayName("sanitizes emergency justification text")
        void sanitizesEmergencyJustification() {
            EmergencyGrant grant = runPromise(() -> service.createEmergencyAccess(
                new EmergencyAccessRequest(
                    "patient-1",
                    "doctor-1",
                    "<script>alert('xss')</script>",
                    "trauma"
                )));

            assertThat(grant.getJustification())
                .isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
            assertThat(notificationSender.consentChangeNotifications()).hasSize(1);
            assertThat(notificationSender.consentChangeNotifications().getFirst().changeType())
                .isEqualTo(PhrNotificationSender.ConsentChangeType.EMERGENCY_ACCESS_GRANTED);
        }
    }

    @Nested
    @DisplayName("consent change notifications")
    class ConsentNotifications {

        @Test
        @DisplayName("notifies patient when a grant is created")
        void notifiesGrantCreated() {
            runPromise(() -> service.createGrant(testGrant("patient-1", "doctor-1")));

            assertThat(notificationSender.consentChangeNotifications()).hasSize(1);
            assertThat(notificationSender.consentChangeNotifications().getFirst().changeType())
                .isEqualTo(PhrNotificationSender.ConsentChangeType.GRANT_CREATED);
        }

        @Test
        @DisplayName("notifies patient when a grant is revoked")
        void notifiesGrantRevoked() {
            ConsentGrant created = runPromise(() -> service.createGrant(testGrant("patient-1", "doctor-1")));

            runPromise(() -> service.revokeGrant(created.getId()));

            assertThat(notificationSender.consentChangeNotifications()).hasSize(2);
            assertThat(notificationSender.consentChangeNotifications().getLast().changeType())
                .isEqualTo(PhrNotificationSender.ConsentChangeType.GRANT_REVOKED);
        }
    }

    // ==================== Rate Limiting ====================

    @Nested
    @DisplayName("rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("createGrant rejects after exceeding rate limit")
        void createGrantRateLimit() {
            // Exhaust the rate limit (20 per minute)
            for (int i = 0; i < 20; i++) {
                ConsentGrant grant = testGrant("patient-" + i, "doctor-flood");
                try {
                    runPromise(() -> service.createGrant(grant));
                } catch (Exception ignored) {
                    // Some may fail for other reasons (conflict etc.); rate limit is what matters
                }
            }

            // The 21st should be rate-limited
            ConsentGrant overLimit = testGrant("patient-overflow", "doctor-flood");
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createGrant(overLimit)));
            clearFatalError();
        }

        @Test
        @DisplayName("checkAccess returns SYSTEM_DENY after exceeding rate limit")
        void checkAccessRateLimit() {
            String actorId = "flood-actor";
            // Loop until rate-limited (up to 300) to tolerate token-bucket refill under load
            ConsentAccessDecision decision = null;
            for (int i = 0; i < 300; i++) {
                ConsentCheckRequest request = providerAccessRequest(
                        "patient-" + i, actorId, "medications");
                decision = runPromise(() -> service.checkAccess(request));
                if (!decision.allowed() && decision.reasonCode() == ReasonCode.SYSTEM_DENY
                        && decision.obligations().contains("RATE_LIMIT_EXCEEDED")) {
                    break;
                }
            }

            assertNotNull(decision);
            assertFalse(decision.allowed());
            assertEquals(ReasonCode.SYSTEM_DENY, decision.reasonCode());
            assertTrue(decision.obligations().contains("RATE_LIMIT_EXCEEDED"));
        }
    }

    // ==================== Cache Invalidation ====================

    @Nested
    @DisplayName("cache invalidation")
    class CacheInvalidation {

        @Test
        @DisplayName("invalidatePatientAccessCache completes without error")
        void invalidateCompletes() {
            CacheInvalidationRequest request = new CacheInvalidationRequest(
                    "tenant-1",
                    "patient-1",
                    CacheInvalidationReason.GRANT_REVOKED);

            runPromise(() -> service.invalidatePatientAccessCache(request));
            // No exception
        }
    }

    // ==================== Service Name ====================

    @Test
    @DisplayName("returns correct service name")
    void serviceName() {
        assertEquals("consent-management", service.getName());
    }

    // ==================== Helpers ====================

    private static ConsentGrant testGrant(String patientId, String recipientId) {
        return new ConsentGrant(
                null, patientId, recipientId,
                new ConsentScope(Set.of("medications", "lab-results"), false, Set.of(), Set.of("READ")),
                "ACTIVE", null, Instant.now().plusSeconds(3600), null
        );
    }

    private static ConsentCheckRequest selfAccessRequest(String patientId) {
        return new ConsentCheckRequest(
                java.util.UUID.randomUUID().toString(),
                "tenant-1",
                new ActorContext(patientId, ActorType.PATIENT, patientId, null, null, Set.of()),
                new TargetResource(patientId, "medications", null, PhrDataClassification.C3),
                ConsentAction.PATIENT_READ,
                PurposeOfUse.SELF_SERVICE,
                null
        );
    }

    private static ConsentCheckRequest emergencyAccessRequest(String patientId, String providerId) {
        return new ConsentCheckRequest(
                java.util.UUID.randomUUID().toString(),
                "tenant-1",
                new ActorContext(providerId, ActorType.PROVIDER, null, providerId, null, Set.of()),
                new TargetResource(patientId, "medications", null, PhrDataClassification.C3),
                ConsentAction.EMERGENCY_READ,
                PurposeOfUse.EMERGENCY,
                new EmergencyContext(true, "Patient unconscious", EmergencyCategory.TRAUMA)
        );
    }

    private static ConsentCheckRequest providerAccessRequest(String patientId, String providerId,
                                                              String resourceType) {
        return new ConsentCheckRequest(
                java.util.UUID.randomUUID().toString(),
                "tenant-1",
                new ActorContext(providerId, ActorType.PROVIDER, null, providerId, null, Set.of()),
                new TargetResource(patientId, resourceType, null, PhrDataClassification.C3),
                ConsentAction.PATIENT_READ,
                PurposeOfUse.CARE_DELIVERY,
                null
        );
    }

    private static KernelContext createTestContext(DataCloudKernelAdapter dataCloud) {
        return new KernelContext() {
            @SuppressWarnings("unchecked")
            @Override public <T> T getDependency(Class<T> type) {
                if (DataCloudKernelAdapter.class.isAssignableFrom(type)) {
                    return (T) dataCloud;
                }
                return null;
            }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) { return java.util.Optional.ofNullable(getDependency(type)); }
            @Override public <T> boolean hasDependency(Class<T> type) { return getDependency(type) != null; }
            @Override public <T> T getDependency(String name, Class<T> type) { return getDependency(type); }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext() { return null; }
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) { return java.util.Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return Runnable::run; }
            @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
            @Override public <T> void registerService(Class<T> type, T service) {}
        };
    }

    /**
     * Minimal DataCloudKernelAdapter stub that stores data in-memory.
     */
    private static class StubDataCloudAdapter implements DataCloudKernelAdapter {

        private final java.util.concurrent.ConcurrentHashMap<String, byte[]> store =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Promise<DataResult> readData(DataReadRequest request) {
            byte[] data = store.get(request.getDatasetId() + ":" + request.getRecordId());
            if (data == null) return Promise.of(null);
            return Promise.of(new DataResult(request.getRecordId(), data, Map.of(), System.currentTimeMillis()));
        }

        @Override
        public Promise<Void> writeData(DataWriteRequest request) {
            store.put(request.getDatasetId() + ":" + request.getRecordId(), request.getData());
            return Promise.complete();
        }

        @Override
        public Promise<Void> deleteData(DataDeleteRequest request) {
            store.remove(request.getDatasetId() + ":" + request.getRecordId());
            return Promise.complete();
        }

        @Override
        public Promise<QueryResult> queryData(DataQueryRequest request) {
            // Return empty results — consent grant queries return no active grants
            return Promise.of(new QueryResult(List.of(), 0, false));
        }

        @Override
        public Promise<Void> createSchema(SchemaCreateRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<SchemaInfo> getSchema(String datasetId) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<DatasetInfo>> listDatasets() {
            return Promise.of(List.of());
        }

        @Override
        public Promise<TransactionHandle> beginTransaction() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> commitTransaction(TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> rollbackTransaction(TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<DataStream> openStream(DataStreamRequest request) {
            return Promise.of(null);
        }
    }
}
