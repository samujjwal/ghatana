package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies emergency review workflow can be constructed from kernel context without optional ports
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EmergencyAccessReviewWorkflowContext")
class EmergencyAccessReviewWorkflowContextTest extends EventloopTestBase {

    @Test
    @DisplayName("fails closed when required dependencies are absent")
    void failsClosedWhenDependenciesMissing() {
        assertThatThrownBy(() -> EmergencyAccessReviewWorkflow.fromContext(new EmptyKernelContext()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EmergencyAccessNotificationSender dependency is required");
    }

    @Test
    @DisplayName("wraps configured dependencies with retry-capable resilience adapters")
    void wrapsConfiguredDependenciesWithRetryCapableResilienceAdapters() {
        AtomicInteger notificationAttempts = new AtomicInteger(0);
        AtomicInteger auditAttempts = new AtomicInteger(0);
        RecordingNotificationSender notificationSender = new RecordingNotificationSender(notificationAttempts);
        RecordingAuditLogger auditLogger = new RecordingAuditLogger(auditAttempts);

        EmergencyAccessReviewWorkflow workflow = EmergencyAccessReviewWorkflow.fromContext(
            new PopulatedKernelContext(notificationSender, auditLogger)
        );

        EmergencyAccessReviewCase reviewCase = runPromise(() -> workflow.initiate(event()));

        assertThat(reviewCase.caseId()).isEqualTo("EMR-CONTEXT");
        assertThat(notificationAttempts.get()).isEqualTo(2);
        assertThat(auditAttempts.get()).isEqualTo(2);
        assertThat(notificationSender.deliveredCaseIds).containsExactly("EMR-CONTEXT");
        assertThat(auditLogger.loggedCaseIds).containsExactly("EMR-CONTEXT");
    }

    private static EmergencyAccessLogService.EmergencyAccessEvent event() {
        Instant accessedAt = Instant.now().minusSeconds(120);
        return new EmergencyAccessLogService.EmergencyAccessEvent(
            "event-ctx",
            "patient-ctx",
            "doctor-ctx",
            "ER_PHYSICIAN",
            "Emergency stroke response",
            Set.of("medications"),
            accessedAt,
            accessedAt.plusSeconds(14400),
            EmergencyAccessLogService.ReviewStatus.PENDING_REVIEW,
            accessedAt.plusSeconds(86400),
            null,
            null,
            null,
            "EMR-CONTEXT"
        );
    }

    private static class EmptyKernelContext implements KernelContext {
        @Override public <T> T getDependency(Class<T> type) { return null; }
        @Override public <T> Optional<T> getOptionalDependency(Class<T> type) { return Optional.empty(); }
        @Override public <T> boolean hasDependency(Class<T> type) { return false; }
        @Override public <T> T getDependency(String name, Class<T> type) { return null; }
        @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
        @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
        @Override public <E> void publishEvent(E event) {}
        @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext() { return null; }
        @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) { return null; }
        @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
        @Override public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
        @Override public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) { return false; }
        @Override public <T> T getConfig(String key, Class<T> type) { return null; }
        @Override public <T> Optional<T> getOptionalConfig(String key, Class<T> type) { return Optional.empty(); }
        @Override public String getKernelVersion() { return "1.0.0"; }
        @Override public String getEnvironment() { return "test"; }
        @Override public java.util.concurrent.Executor getExecutor(String executorName) { return Runnable::run; }
        @Override public <T> Optional<T> getCapability(String capabilityId) { return Optional.empty(); }
        @Override public <T> void registerService(Class<T> type, T service) {}
    }

    private static final class PopulatedKernelContext extends EmptyKernelContext {
        private final EmergencyAccessNotificationSender notificationSender;
        private final EmergencyAccessReviewAuditLogger auditLogger;

        private PopulatedKernelContext(
                EmergencyAccessNotificationSender notificationSender,
                EmergencyAccessReviewAuditLogger auditLogger) {
            this.notificationSender = notificationSender;
            this.auditLogger = auditLogger;
        }

        @Override
        public <T> Optional<T> getOptionalDependency(Class<T> type) {
            if (type == EmergencyAccessNotificationSender.class) {
                return Optional.of(type.cast(notificationSender));
            }
            if (type == EmergencyAccessReviewAuditLogger.class) {
                return Optional.of(type.cast(auditLogger));
            }
            return Optional.empty();
        }
    }

    private static final class RecordingNotificationSender implements EmergencyAccessNotificationSender {
        private final AtomicInteger attempts;
        private final java.util.List<String> deliveredCaseIds = new ArrayList<>();

        private RecordingNotificationSender(AtomicInteger attempts) {
            this.attempts = attempts;
        }

        @Override
        public Promise<Void> notifyComplianceLead(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
            if (attempts.incrementAndGet() == 1) {
                return Promise.ofException(new IllegalStateException("temporary notify failure"));
            }
            deliveredCaseIds.add(reviewCase.caseId());
            return Promise.complete();
        }

        @Override
        public Promise<Void> scheduleMandatoryReview(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyEscalation(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyPatient(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }
    }

    private static final class RecordingAuditLogger implements EmergencyAccessReviewAuditLogger {
        private final AtomicInteger attempts;
        private final java.util.List<String> loggedCaseIds = new ArrayList<>();

        private RecordingAuditLogger(AtomicInteger attempts) {
            this.attempts = attempts;
        }

        @Override
        public Promise<Void> logReviewQueued(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
            if (attempts.incrementAndGet() == 1) {
                return Promise.ofException(new IllegalStateException("temporary audit failure"));
            }
            loggedCaseIds.add(reviewCase.caseId());
            return Promise.complete();
        }

        @Override
        public Promise<Void> logReviewCompleted(EmergencyAccessReviewCase reviewCase, EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }
    }
}
