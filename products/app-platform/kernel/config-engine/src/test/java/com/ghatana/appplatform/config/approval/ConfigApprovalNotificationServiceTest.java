package com.ghatana.appplatform.config.approval;

import com.ghatana.appplatform.config.approval.ConfigApprovalNotificationService.ApprovalDecisionEvent;
import com.ghatana.appplatform.config.approval.ConfigApprovalNotificationService.ApprovalEventHandler;
import com.ghatana.appplatform.config.approval.ConfigApprovalNotificationService.Decision;
import com.ghatana.appplatform.config.approval.ConfigApprovalNotificationService.PendingApprovalEvent;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ConfigApprovalNotificationService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for config approval lifecycle notification dispatch (STORY-K02-015)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConfigApprovalNotificationService — Unit Tests")
class ConfigApprovalNotificationServiceTest {

    private ConfigApprovalNotificationService service;

    private static final PendingApprovalEvent PENDING_EVENT = new PendingApprovalEvent(
        "prop-001", "tenant-a", "payments", "max_limit",
        ConfigHierarchyLevel.TENANT, "tenant-a",
        "maker@ghatana.com", Instant.parse("2025-03-01T10:00:00Z")
    );

    private static final ApprovalDecisionEvent APPROVED_EVENT = new ApprovalDecisionEvent(
        "prop-001", "tenant-a", "payments", "max_limit",
        Decision.APPROVED, "checker@ghatana.com", null,
        Instant.parse("2025-03-01T11:00:00Z")
    );

    private static final ApprovalDecisionEvent REJECTED_EVENT = new ApprovalDecisionEvent(
        "prop-002", "tenant-b", "security", "mfa_required",
        Decision.REJECTED, "checker@ghatana.com", "Value exceeds policy limit.",
        Instant.parse("2025-03-01T12:00:00Z")
    );

    @BeforeEach
    void setUp() {
        service = new ConfigApprovalNotificationService(Set.of("security", "payments"));
    }

    @Test
    @DisplayName("notifyPending — dispatches PendingApprovalEvent to all handlers")
    void notifyPending_dispatchesToHandlers() {
        List<PendingApprovalEvent> received = new ArrayList<>();
        service.addHandler(testHandler(received::add, ignored -> {}));

        service.notifyPending(PENDING_EVENT);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).proposalId()).isEqualTo("prop-001");
        assertThat(received.get(0).namespace()).isEqualTo("payments");
    }

    @Test
    @DisplayName("notifyDecided — dispatches ApprovalDecisionEvent to all handlers")
    void notifyDecided_dispatchesToHandlers() {
        List<ApprovalDecisionEvent> decided = new ArrayList<>();
        service.addHandler(testHandler(ignored -> {}, decided::add));

        service.notifyDecided(APPROVED_EVENT);

        assertThat(decided).hasSize(1);
        assertThat(decided.get(0).decision()).isEqualTo(Decision.APPROVED);
        assertThat(decided.get(0).reason()).isNull();
    }

    @Test
    @DisplayName("notifyDecided — rejected event includes reason")
    void notifyDecided_rejection_includesReason() {
        List<ApprovalDecisionEvent> decided = new ArrayList<>();
        service.addHandler(testHandler(ignored -> {}, decided::add));

        service.notifyDecided(REJECTED_EVENT);

        assertThat(decided.get(0).decision()).isEqualTo(Decision.REJECTED);
        assertThat(decided.get(0).reason()).isEqualTo("Value exceeds policy limit.");
    }

    @Test
    @DisplayName("multiple handlers — all receive the event independently")
    void multipleHandlers_allReceiveEvent() {
        List<PendingApprovalEvent> first = new ArrayList<>();
        List<PendingApprovalEvent> second = new ArrayList<>();
        service.addHandler(testHandler(first::add, ignored -> {}));
        service.addHandler(testHandler(second::add, ignored -> {}));

        service.notifyPending(PENDING_EVENT);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
    }

    @Test
    @DisplayName("faulty handler — exception is suppressed; other handlers still called")
    void faultyHandler_exceptionSuppressed_othersStillCalled() {
        List<PendingApprovalEvent> received = new ArrayList<>();
        service.addHandler(new ApprovalEventHandler() {
            @Override
            public void onApprovalRequired(PendingApprovalEvent event) {
                throw new RuntimeException("Simulated handler failure");
            }
            @Override
            public void onApprovalDecided(ApprovalDecisionEvent event) {}
        });
        service.addHandler(testHandler(received::add, ignored -> {}));

        assertThatCode(() -> service.notifyPending(PENDING_EVENT))
            .doesNotThrowAnyException();
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("removeHandler — removed handler no longer receives events")
    void removeHandler_noLongerReceivesEvents() {
        List<PendingApprovalEvent> received = new ArrayList<>();
        ApprovalEventHandler handler = testHandler(received::add, ignored -> {});
        service.addHandler(handler);
        service.removeHandler(handler);

        service.notifyPending(PENDING_EVENT);

        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("isSensitiveNamespace — returns true for configured sensitive namespaces")
    void isSensitiveNamespace_configured_returnsTrue() {
        assertThat(service.isSensitiveNamespace("security")).isTrue();
        assertThat(service.isSensitiveNamespace("payments")).isTrue();
    }

    @Test
    @DisplayName("isSensitiveNamespace — returns false for unconfigured namespaces")
    void isSensitiveNamespace_unconfigured_returnsFalse() {
        assertThat(service.isSensitiveNamespace("feature-flags")).isFalse();
        assertThat(service.isSensitiveNamespace(null)).isFalse();
    }

    @Test
    @DisplayName("onConfigChange — dispatches onConfigLive to handlers")
    void onConfigChange_dispatchesLiveEvent() {
        List<String> liveEvents = new ArrayList<>();
        service.addHandler(new ApprovalEventHandler() {
            @Override
            public void onApprovalRequired(PendingApprovalEvent event) {}
            @Override
            public void onApprovalDecided(ApprovalDecisionEvent event) {}
            @Override
            public void onConfigLive(String namespace, String key, String level, String levelId) {
                liveEvents.add(namespace + "." + key);
            }
        });

        service.onConfigChange("payments", "max_limit", "TENANT", "tenant-a");

        assertThat(liveEvents).containsExactly("payments.max_limit");
    }

    @Test
    @DisplayName("PendingApprovalEvent — null proposalId throws NullPointerException")
    void pendingEvent_nullProposalId_throws() {
        assertThatThrownBy(() -> new PendingApprovalEvent(
            null, "tenant-a", "payments", "max_limit",
            ConfigHierarchyLevel.TENANT, "tenant-a",
            "maker@ghatana.com", Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ApprovalEventHandler testHandler(
            java.util.function.Consumer<PendingApprovalEvent> onPending,
            java.util.function.Consumer<ApprovalDecisionEvent> onDecided) {
        return new ApprovalEventHandler() {
            @Override
            public void onApprovalRequired(PendingApprovalEvent event) { onPending.accept(event); }
            @Override
            public void onApprovalDecided(ApprovalDecisionEvent event) { onDecided.accept(event); }
        };
    }
}
