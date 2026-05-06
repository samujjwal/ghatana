package com.ghatana.digitalmarketing.application.googleads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.command.DmCommandHandler;
import com.ghatana.digitalmarketing.application.event.DmOutboxService;
import com.ghatana.digitalmarketing.application.killswitch.DmKillSwitchService;
import com.ghatana.digitalmarketing.application.rollback.DmRollbackActionService;
import com.ghatana.digitalmarketing.application.rollback.DmRollbackActionService.ScheduleRollbackCommand;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.event.DmDeadLetterEntry;
import com.ghatana.digitalmarketing.domain.event.DmEvent;
import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.googleads.GoogleAdsCommandPayload;
import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackStatus;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.TracingManager;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for P1-023: Google Ads workflow/outbox execution.
 */
@DisplayName("P1-023: GoogleAdsOutboxExecutor Tests")
class GoogleAdsOutboxExecutorTest extends EventloopTestBase {

    private CapturingOutboxService outboxService;
    private ConfigurableKillSwitchService killSwitchService;
    private CapturingRollbackActionService rollbackActionService;
    private CapturingKernelAdapter kernelAdapter;
    private CapturingCommandHandler createCommandHandler;
    private CapturingCommandHandler rollbackCommandHandler;

    private GoogleAdsOutboxExecutor executor;
    private ObjectMapper objectMapper;
    private DmOperationContext testCtx;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        outboxService = new CapturingOutboxService();
        killSwitchService = new ConfigurableKillSwitchService();
        rollbackActionService = new CapturingRollbackActionService();
        kernelAdapter = new CapturingKernelAdapter();
        createCommandHandler = new CapturingCommandHandler(null);
        rollbackCommandHandler = new CapturingCommandHandler(null);

        DmosObservability observability = new DmosObservability(
            new Metrics(new SimpleMeterRegistry()),
            new TracingManager(OpenTelemetry.noop())
        );

        executor = new GoogleAdsOutboxExecutor(
            eventloop(),
            outboxService,
            killSwitchService,
            rollbackActionService,
            kernelAdapter,
            createCommandHandler,
            rollbackCommandHandler,
            objectMapper,
            observability
        );

        testCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("test-tenant"))
            .workspaceId(DmWorkspaceId.of("test-workspace"))
            .actor(ActorRef.user("test-user"))
            .correlationId(DmCorrelationId.generate())
            .build();
    }

    @Test
    @DisplayName("P1-023: Should enqueue campaign creation to outbox")
    void shouldEnqueueCampaignCreation() {
        // Given
        GoogleAdsCommandPayload.CreateCampaign payload = buildCreateCampaignPayload();

        // When
        String commandId = runPromise(() -> executor.enqueueCampaignCreation(testCtx, payload));

        // Then
        assertThat(commandId).isNotNull();
        assertThat(outboxService.appendCallCount()).isGreaterThan(0);
        assertThat(kernelAdapter.auditCallCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("P1-024: Should block execution when kill switch is active")
    void shouldBlockExecutionWhenKillSwitchActive() {
        // Given
        DmCommand command = buildCreateCommand("test-tenant", "test-workspace");

        DmKillSwitch activeKillSwitch = DmKillSwitch.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("test-tenant")
            .scope("GOOGLE_ADS")
            .scopeId("GLOBAL")
            .reason("Emergency maintenance")
            .active(true)
            .createdAt(java.time.Instant.now())
            .build();
        killSwitchService.setGlobalKillSwitch(activeKillSwitch);

        // When/Then
        assertThatThrownBy(() -> runPromise(() -> executor.executeCommand(command)))
            .isInstanceOf(GoogleAdsOutboxExecutor.KillSwitchActiveException.class)
            .hasMessageContaining("temporarily disabled");

        assertThat(kernelAdapter.hasAuditCallWithAction("GOOGLE_ADS_COMMAND_BLOCKED_BY_KILL_SWITCH")).isTrue();
    }

    @Test
    @DisplayName("P1-024: Should allow execution when no kill switch is active")
    void shouldAllowExecutionWhenNoKillSwitch() {
        // Given
        DmCommand command = DmCommand.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("test-tenant")
            .workspaceId("test-workspace")
            .correlationId(UUID.randomUUID().toString())
            .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE)
            .issuedBy("test-user")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .scheduledAt(Instant.now())
            .build();

        killSwitchService.clearAll();

        // When
        runPromise(() -> executor.executeCommand(command));

        // Then
        assertThat(createCommandHandler.handleCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("P1-025: Should schedule rollback on execution failure")
    void shouldScheduleRollbackOnFailure() {
        // Given
        DmCommand command = buildCreateCommand("test-tenant", "test-workspace");
        killSwitchService.clearAll();

        // Command handler will throw
        RuntimeException expectedError = new RuntimeException("API call failed");
        createCommandHandler = new CapturingCommandHandler(expectedError);

        DmosObservability observability = new DmosObservability(
            new Metrics(new SimpleMeterRegistry()),
            new TracingManager(OpenTelemetry.noop())
        );
        executor = new GoogleAdsOutboxExecutor(
            eventloop(), outboxService, killSwitchService, rollbackActionService,
            kernelAdapter, createCommandHandler, rollbackCommandHandler, objectMapper, observability
        );

        // When
        try {
            runPromise(() -> executor.executeCommand(command));
        } catch (Exception e) {
            // Expected
        }

        // Then - verify rollback was scheduled
        assertThat(rollbackActionService.scheduleCallCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("P1-023: Should execute rollback command type")
    void shouldExecuteRollbackCommand() {
        // Given
        DmCommand command = DmCommand.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("test-tenant")
            .workspaceId("test-workspace")
            .correlationId(UUID.randomUUID().toString())
            .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK)
            .issuedBy("test-user")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .scheduledAt(Instant.now())
            .build();

        killSwitchService.clearAll();

        // When
        runPromise(() -> executor.executeCommand(command));

        // Then
        assertThat(rollbackCommandHandler.handleCallCount()).isEqualTo(1);
        assertThat(createCommandHandler.handleCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("P1-028: Should record audit events during execution")
    void shouldRecordAuditEvents() {
        // Given
        GoogleAdsCommandPayload.CreateCampaign payload = buildCreateCampaignPayload();

        // When
        runPromise(() -> executor.enqueueCampaignCreation(testCtx, payload));

        // Then
        assertThat(kernelAdapter.auditCallCount()).isGreaterThan(0);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private DmCommand buildCreateCommand(String tenantId, String workspaceId) {
        return DmCommand.builder()
            .id(UUID.randomUUID().toString())
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .correlationId(UUID.randomUUID().toString())
            .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE)
            .issuedBy("test-user")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .scheduledAt(Instant.now())
            .build();
    }

    private GoogleAdsCommandPayload.CreateCampaign buildCreateCampaignPayload() {
        return new GoogleAdsCommandPayload.CreateCampaign(
            "campaign-123",
            "123-456-7890",
            "Test Campaign",
            GoogleAdsCommandPayload.CampaignGoal.SALES,
            new BigDecimal("1000.00"),
            LocalDate.now(),
            LocalDate.now().plusMonths(1),
            new String[]{"search"},
            new String[]{"adgroup1"},
            "https://example.com/landing",
            "https://example.com/tracking"
        );
    }

    // ─── In-memory test doubles ───────────────────────────────────────────────

    private static class CapturingOutboxService implements DmOutboxService {
        private int appendCalls = 0;

        @Override
        public <T> Promise<DmOutboxEntry> append(DmOperationContext ctx, DmEvent<T> event) {
            appendCalls++;
            java.time.Instant now = java.time.Instant.now();
            return Promise.of(DmOutboxEntry.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .tenantId(event.getTenantId())
                .workspaceId(event.getWorkspaceId())
                .correlationId(event.getCorrelationId())
                .serializedPayload("{}")
                .status(com.ghatana.digitalmarketing.domain.event.DmOutboxStatus.PENDING)
                .createdAt(now)
                .scheduledAt(now)
                .build());
        }

        @Override
        public Promise<Integer> dispatchPending(String tenantId, int batchSize) {
            return Promise.of(0);
        }

        @Override
        public Promise<Integer> retryFailed(String tenantId, int batchSize) {
            return Promise.of(0);
        }

        @Override
        public Promise<List<DmDeadLetterEntry>> listDeadLetters(DmOperationContext ctx, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<DmDeadLetterEntry> replayDeadLetter(DmOperationContext ctx, String dlqId) {
            return Promise.ofException(new UnsupportedOperationException("not used in test"));
        }

        int appendCallCount() {
            return appendCalls;
        }
    }

    private static class ConfigurableKillSwitchService implements DmKillSwitchService {
        private DmKillSwitch globalKillSwitch = null;

        void setGlobalKillSwitch(DmKillSwitch ks) {
            this.globalKillSwitch = ks;
        }

        void clearAll() {
            this.globalKillSwitch = null;
        }

        @Override
        public Promise<DmKillSwitch> activate(DmOperationContext ctx, DmKillSwitchService.ActivateKillSwitchCommand command) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<DmKillSwitch> deactivate(DmOperationContext ctx, String killSwitchId) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<Optional<DmKillSwitch>> findById(DmOperationContext ctx, String killSwitchId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<DmKillSwitch>> listActive(DmOperationContext ctx) {
            return Promise.of(globalKillSwitch == null ? List.of() : List.of(globalKillSwitch));
        }

        @Override
        public Promise<Optional<DmKillSwitch>> findActiveByScope(DmOperationContext ctx, String scope, String scopeId) {
            if (globalKillSwitch != null && "GOOGLE_ADS".equals(scope) && "GLOBAL".equals(scopeId)) {
                return Promise.of(Optional.of(globalKillSwitch));
            }
            return Promise.of(Optional.empty());
        }
    }

    private static class CapturingRollbackActionService implements DmRollbackActionService {
        private int scheduleCalls = 0;

        @Override
        public Promise<DmRollbackAction> schedule(DmOperationContext ctx, ScheduleRollbackCommand command) {
            scheduleCalls++;
            return Promise.of(DmRollbackAction.builder()
                .id("rollback-" + scheduleCalls)
                .tenantId(ctx.getTenantId().getValue())
                .commandId(command.commandId())
                .actionType("COMPENSATE")
                .status(DmRollbackStatus.PENDING)
                .createdAt(java.time.Instant.now())
                .build());
        }

        @Override
        public Promise<DmRollbackAction> markCompleted(DmOperationContext ctx, String actionId) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<DmRollbackAction> markFailed(DmOperationContext ctx, String actionId, String reason) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<Optional<DmRollbackAction>> findById(DmOperationContext ctx, String actionId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<DmRollbackAction>> listByCommand(DmOperationContext ctx, String commandId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<DmRollbackAction>> listByStatus(DmOperationContext ctx, DmRollbackStatus status, int limit) {
            return Promise.of(List.of());
        }

        int scheduleCallCount() {
            return scheduleCalls;
        }
    }

    private static class CapturingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final List<String> auditActions = new ArrayList<>();

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext ctx, String operationType, String subjectId, String description) {
            return Promise.of("approval-id");
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action, Map<String, Object> attributes) {
            auditActions.add(action);
            return Promise.of("audit-" + auditActions.size());
        }

        int auditCallCount() {
            return auditActions.size();
        }

        boolean hasAuditCallWithAction(String action) {
            return auditActions.contains(action);
        }
    }

    private static class CapturingCommandHandler implements DmCommandHandler {
        private final RuntimeException errorToThrow;
        private int handleCalls = 0;

        CapturingCommandHandler(RuntimeException errorToThrow) {
            this.errorToThrow = errorToThrow;
        }

        @Override
        public Promise<Void> handle(DmCommand command) {
            handleCalls++;
            if (errorToThrow != null) {
                return Promise.ofException(errorToThrow);
            }
            return Promise.of(null);
        }

        int handleCallCount() {
            return handleCalls;
        }
    }
}
