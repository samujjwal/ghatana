package com.ghatana.digitalmarketing.application.googleads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.command.GoogleAdsCampaignCreateCommandHandler;
import com.ghatana.digitalmarketing.application.command.GoogleAdsCampaignRollbackCommandHandler;
import com.ghatana.digitalmarketing.application.event.DmOutboxService;
import com.ghatana.digitalmarketing.application.killswitch.DmKillSwitchService;
import com.ghatana.digitalmarketing.application.rollback.DmRollbackActionService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.googleads.GoogleAdsCommandPayload;
import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.test.eventloop.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for P1-023: Google Ads workflow/outbox execution.
 */
@DisplayName("P1-023: GoogleAdsOutboxExecutor Tests")
class GoogleAdsOutboxExecutorTest extends EventloopTestBase {

    @Mock
    private DmOutboxService outboxService;

    @Mock
    private DmKillSwitchService killSwitchService;

    @Mock
    private DmRollbackActionService rollbackActionService;

    @Mock
    private DigitalMarketingKernelAdapter kernelAdapter;

    @Mock
    private GoogleAdsCampaignCreateCommandHandler createCommandHandler;

    @Mock
    private GoogleAdsCampaignRollbackCommandHandler rollbackCommandHandler;

    @Mock
    private DmosObservability observability;

    private GoogleAdsOutboxExecutor executor;
    private ObjectMapper objectMapper;
    private DmOperationContext testCtx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        executor = new GoogleAdsOutboxExecutor(
            eventloop,
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

        // Default stubbing
        when(kernelAdapter.recordAudit(any(), any(), any(), any())).thenReturn(Promise.of(true));
        when(observability.createSpan(any(), any(), any())).thenReturn(mock(io.opentelemetry.api.trace.Span.class));
    }

    @Test
    @DisplayName("P1-023: Should enqueue campaign creation to outbox")
    void shouldEnqueueCampaignCreation() {
        // Given
        GoogleAdsCommandPayload.CreateCampaign payload = new GoogleAdsCommandPayload.CreateCampaign(
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

        DmOutboxEntry mockEntry = DmOutboxEntry.builder()
            .id(UUID.randomUUID().toString())
            .build();

        when(outboxService.append(any(), any())).thenReturn(Promise.of(mockEntry));

        // When
        String commandId = runPromise(() -> executor.enqueueCampaignCreation(testCtx, payload));

        // Then
        assertThat(commandId).isNotNull();
        verify(outboxService).append(any(), any());
        verify(kernelAdapter).recordAudit(any(), eq("google-ads-outbox"), eq("GOOGLE_ADS_CAMPAIGN_ENQUEUED"), any());
    }

    @Test
    @DisplayName("P1-024: Should block execution when kill switch is active")
    void shouldBlockExecutionWhenKillSwitchActive() {
        // Given
        DmCommand command = DmCommand.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("test-tenant")
            .workspaceId("test-workspace")
            .correlationId(UUID.randomUUID().toString())
            .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE)
            .targetEntityId("campaign-123")
            .targetEntityType("CAMPAIGN")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .build();

        // Kill switch is active at global scope
        DmKillSwitch activeKillSwitch = DmKillSwitch.builder()
            .id(UUID.randomUUID().toString())
            .scope("GOOGLE_ADS")
            .scopeId("GLOBAL")
            .reason("Emergency maintenance")
            .active(true)
            .build();

        when(killSwitchService.findActiveByScope(any(), eq("GOOGLE_ADS"), eq("GLOBAL")))
            .thenReturn(Promise.of(Optional.of(activeKillSwitch)));

        // When/Then
        assertThatThrownBy(() -> runPromise(() -> executor.executeCommand(command)))
            .isInstanceOf(GoogleAdsOutboxExecutor.KillSwitchActiveException.class)
            .hasMessageContaining("temporarily disabled");

        // Verify audit event recorded
        verify(kernelAdapter).recordAudit(any(), any(), eq("GOOGLE_ADS_COMMAND_BLOCKED_BY_KILL_SWITCH"), any());
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
            .targetEntityId("campaign-123")
            .targetEntityType("CAMPAIGN")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .build();

        // No kill switch active
        when(killSwitchService.findActiveByScope(any(), eq("GOOGLE_ADS"), eq("GLOBAL")))
            .thenReturn(Promise.of(Optional.empty()));
        when(killSwitchService.findActiveByScope(any(), eq("GOOGLE_ADS"), eq("test-tenant")))
            .thenReturn(Promise.of(Optional.empty()));
        when(killSwitchService.findActiveByScope(any(), eq("GOOGLE_ADS"), eq("test-workspace")))
            .thenReturn(Promise.of(Optional.empty()));

        when(createCommandHandler.handle(any())).thenReturn(Promise.of(null));

        // When
        runPromise(() -> executor.executeCommand(command));

        // Then
        verify(createCommandHandler).handle(command);
    }

    @Test
    @DisplayName("P1-025: Should schedule rollback on execution failure")
    void shouldScheduleRollbackOnFailure() {
        // Given
        DmCommand command = DmCommand.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("test-tenant")
            .workspaceId("test-workspace")
            .correlationId(UUID.randomUUID().toString())
            .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE)
            .targetEntityId("campaign-123")
            .targetEntityType("CAMPAIGN")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .build();

        // No kill switch
        when(killSwitchService.findActiveByScope(any(), eq("GOOGLE_ADS"), any()))
            .thenReturn(Promise.of(Optional.empty()));

        // Command handler throws error
        RuntimeException expectedError = new RuntimeException("API call failed");
        when(createCommandHandler.handle(any())).thenReturn(Promise.ofException(expectedError));

        // Rollback service returns action
        var mockAction = mock(com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction.class);
        when(mockAction.getId()).thenReturn("rollback-123");
        when(rollbackActionService.schedule(any(), any()))
            .thenReturn(Promise.of(mockAction));

        // When
        try {
            runPromise(() -> executor.executeCommand(command));
        } catch (Exception e) {
            // Expected
        }

        // Then - verify rollback was scheduled
        verify(rollbackActionService).schedule(any(), any());
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
            .targetEntityId("campaign-123")
            .targetEntityType("CAMPAIGN")
            .serializedPayload("{}")
            .createdAt(Instant.now())
            .build();

        when(killSwitchService.findActiveByScope(any(), eq("GOOGLE_ADS"), any()))
            .thenReturn(Promise.of(Optional.empty()));

        when(rollbackCommandHandler.handle(any())).thenReturn(Promise.of(null));

        // When
        runPromise(() -> executor.executeCommand(command));

        // Then
        verify(rollbackCommandHandler).handle(command);
        verify(createCommandHandler, never()).handle(any());
    }

    @Test
    @DisplayName("P1-028: Should record audit events during execution")
    void shouldRecordAuditEvents() {
        // Given
        GoogleAdsCommandPayload.CreateCampaign payload = new GoogleAdsCommandPayload.CreateCampaign(
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

        DmOutboxEntry mockEntry = DmOutboxEntry.builder()
            .id(UUID.randomUUID().toString())
            .build();

        when(outboxService.append(any(), any())).thenReturn(Promise.of(mockEntry));

        // When
        runPromise(() -> executor.enqueueCampaignCreation(testCtx, payload));

        // Then
        verify(kernelAdapter, atLeastOnce()).recordAudit(any(), any(), any(), any());
    }
}
