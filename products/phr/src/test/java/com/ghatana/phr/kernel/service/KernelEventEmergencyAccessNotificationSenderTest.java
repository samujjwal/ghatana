package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.EventHandler;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.ReviewStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies emergency notification events carry protected references instead of raw justification text
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KernelEventEmergencyAccessNotificationSender")
class KernelEventEmergencyAccessNotificationSenderTest extends EventloopTestBase {

    @Test
    @DisplayName("compliance notification uses protected justification metadata")
    void complianceNotificationUsesProtectedJustificationMetadata() {
        CapturingKernelContext context = new CapturingKernelContext(eventloop());
        KernelEventEmergencyAccessNotificationSender sender = new KernelEventEmergencyAccessNotificationSender(context);
        EmergencyAccessEvent event = event("Patient unconscious in ER, immediate access required");

        runPromise(() -> sender.notifyComplianceLead(reviewCase(event), event));

        KernelEventEmergencyAccessNotificationSender.EmergencyAccessNotificationEvent notification = context.onlyEvent();
        assertThat(notification.metadata())
            .containsEntry("justificationCaptured", "true")
            .containsEntry("justificationReference", "EMR-SAFE:event-1")
            .containsKey("justificationHash")
            .doesNotContainKey("justification");
        assertThat(notification.metadata().values())
            .doesNotContain("Patient unconscious in ER, immediate access required");
        assertThat(notification.metadata().get("justificationHash")).hasSize(64);
    }

    @Test
    @DisplayName("patient notification excludes raw justification")
    void patientNotificationExcludesRawJustification() {
        CapturingKernelContext context = new CapturingKernelContext(eventloop());
        KernelEventEmergencyAccessNotificationSender sender = new KernelEventEmergencyAccessNotificationSender(context);
        EmergencyAccessEvent event = event("Patient unconscious in ER, immediate access required");

        runPromise(() -> sender.notifyPatient(reviewCase(event), event));

        KernelEventEmergencyAccessNotificationSender.EmergencyAccessNotificationEvent notification = context.onlyEvent();
        assertThat(notification.metadata())
            .containsEntry("justificationCaptured", "true")
            .containsEntry("justificationReference", "EMR-SAFE:event-1")
            .doesNotContainKey("justification");
        assertThat(notification.metadata().values())
            .doesNotContain("Patient unconscious in ER, immediate access required");
    }

    private static EmergencyAccessReviewCase reviewCase(EmergencyAccessEvent event) {
        return new EmergencyAccessReviewCase(
            event.reviewCaseId(),
            event.id(),
            event.patientId(),
            event.accessorId(),
            event.accessedAt(),
            event.accessExpiresAt(),
            event.reviewDueAt(),
            event.accessedAt().plusSeconds(3600),
            EmergencyAccessReviewCase.ReviewCaseStatus.QUEUED
        );
    }

    private static EmergencyAccessEvent event(String justification) {
        Instant accessedAt = Instant.now();
        return new EmergencyAccessEvent(
            "event-1",
            "patient-1",
            "doctor-1",
            "ER_PHYSICIAN",
            justification,
            Set.of("medications", "labs"),
            accessedAt,
            accessedAt.plusSeconds(14400),
            ReviewStatus.PENDING_REVIEW,
            accessedAt.plusSeconds(86400),
            null,
            null,
            null,
            "EMR-SAFE"
        );
    }

    private static final class CapturingKernelContext implements KernelContext {
        private final Eventloop eventloop;
        private final List<Object> events = new ArrayList<>();

        private CapturingKernelContext(Eventloop eventloop) {
            this.eventloop = eventloop;
        }

        private KernelEventEmergencyAccessNotificationSender.EmergencyAccessNotificationEvent onlyEvent() {
            assertThat(events).hasSize(1);
            return (KernelEventEmergencyAccessNotificationSender.EmergencyAccessNotificationEvent) events.get(0);
        }

        @Override public <T> T getDependency(Class<T> type) { return null; }
        @Override public <T> Optional<T> getOptionalDependency(Class<T> type) { return Optional.empty(); }
        @Override public <T> boolean hasDependency(Class<T> type) { return false; }
        @Override public <T> T getDependency(String name, Class<T> type) { return null; }
        @Override public <E> void registerEventHandler(Class<E> eventType, EventHandler<E> handler) {}
        @Override public <E> void unregisterEventHandler(Class<E> eventType, EventHandler<E> handler) {}
        @Override public <E> void publishEvent(E event) { events.add(event); }
        @Override public KernelTenantContext getTenantContext() { return null; }
        @Override public KernelTenantContext getTenantContext(String tenantId) { return null; }
        @Override public Eventloop getEventloop() { return eventloop; }
        @Override public Set<KernelCapability> getAvailableCapabilities() { return Set.of(); }
        @Override public boolean hasCapability(KernelCapability capability) { return false; }
        @Override public <T> T getConfig(String key, Class<T> type) { return null; }
        @Override public <T> Optional<T> getOptionalConfig(String key, Class<T> type) { return Optional.empty(); }
        @Override public String getKernelVersion() { return "1.0.0"; }
        @Override public String getEnvironment() { return "test"; }
        @Override public Executor getExecutor(String executorName) { return Runnable::run; }
        @Override public <T> Optional<T> getCapability(String capabilityId) { return Optional.empty(); }
        @Override public <T> void registerService(Class<T> type, T service) {}
    }
}
