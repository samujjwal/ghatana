package com.ghatana.digitalmarketing.application.googleads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.command.DmCommandHandler;
import com.ghatana.digitalmarketing.application.command.DmCommandHandler;
import com.ghatana.digitalmarketing.application.command.GoogleAdsCampaignCreateCommandHandler;
import com.ghatana.digitalmarketing.application.command.GoogleAdsCampaignRollbackCommandHandler;
import com.ghatana.digitalmarketing.application.event.DmOutboxService;
import com.ghatana.digitalmarketing.application.killswitch.DmKillSwitchService;
import com.ghatana.digitalmarketing.application.rollback.DmRollbackActionService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.event.DmEvent;
import com.ghatana.digitalmarketing.domain.event.DmEventType;
import com.ghatana.digitalmarketing.domain.googleads.GoogleAdsCommandPayload;
import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P1-023: Google Ads workflow/outbox execution service.
 *
 * <p>Executes Google Ads writes through durable outbox/workflow after approval.
 * Integrates with kill switch, rollback actions, and audit trail.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Approved campaign launch creates connector command/outbox record</li>
 *   <li>Worker executes external call with retry/backoff/DLQ for failures</li>
 *   <li>Audit every external write</li>
 *   <li>Kill switch prevents execution (P1-024)</li>
 *   <li>Rollback/compensation story defined (P1-025)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Google Ads workflow/outbox execution (P1-023, P1-024, P1-025)
 * @doc.layer product
 * @doc.pattern Outbox, Saga, Command Handler
 */
public final class GoogleAdsOutboxExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAdsOutboxExecutor.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String KILL_SWITCH_SCOPE = "GOOGLE_ADS";

    private final Eventloop eventloop;
    private final DmOutboxService outboxService;
    private final DmKillSwitchService killSwitchService;
    private final DmRollbackActionService rollbackActionService;
    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final DmCommandHandler createCommandHandler;
    private final DmCommandHandler rollbackCommandHandler;
    private final ObjectMapper objectMapper;
    private final DmosObservability observability;

    public GoogleAdsOutboxExecutor(
            Eventloop eventloop,
            DmOutboxService outboxService,
            DmKillSwitchService killSwitchService,
            DmRollbackActionService rollbackActionService,
            DigitalMarketingKernelAdapter kernelAdapter,
            DmCommandHandler createCommandHandler,
            DmCommandHandler rollbackCommandHandler,
            ObjectMapper objectMapper,
            DmosObservability observability) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
        this.rollbackActionService = Objects.requireNonNull(rollbackActionService, "rollbackActionService must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.createCommandHandler = Objects.requireNonNull(createCommandHandler, "createCommandHandler must not be null");
        this.rollbackCommandHandler = Objects.requireNonNull(rollbackCommandHandler, "rollbackCommandHandler must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
    }

    /**
     * P1-023: Enqueues a Google Ads campaign creation command to the outbox.
     *
     * <p>This is called after approval to create a durable outbox record
     * that will be executed asynchronously.</p>
     *
     * @param ctx the operation context
     * @param payload the campaign creation payload
     * @return promise resolving to the command ID
     */
    public Promise<String> enqueueCampaignCreation(DmOperationContext ctx, GoogleAdsCommandPayload.CreateCampaign payload) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        String commandId = UUID.randomUUID().toString();
        String correlationId = ctx.getCorrelationId() != null
            ? ctx.getCorrelationId().getValue()
            : DmCorrelationId.generate().getValue();

        LOG.info("[DMOS-GOOGLE-ADS] Enqueuing campaign creation: commandId={}, campaignId={}",
            commandId, payload.internalCampaignId());

        MDC.put("commandId", commandId);
        MDC.put("correlationId", correlationId);
        MDC.put("tenantId", ctx.getTenantId().getValue());
        MDC.put("workspaceId", ctx.getWorkspaceId().getValue());

        try {
            // Create the domain event for the outbox
            DmEvent<GoogleAdsCommandPayload.CreateCampaign> event = DmEvent.<GoogleAdsCommandPayload.CreateCampaign>builder()
                .eventId(commandId)
                .schemaVersion("1.0.0")
                .tenantId(ctx.getTenantId().getValue())
                .workspaceId(ctx.getWorkspaceId().getValue())
                .correlationId(correlationId)
                .idempotencyKey(commandId)
                .sourceService("google-ads-outbox-executor")
                .actor(ctx.getActor().getPrincipalId())
                .actorType(DmEvent.ActorType.USER)
                .eventType(DmEventType.COMMAND_CREATED)
                .payload(payload)
                .occurredAt(Instant.now())
                .piiClassification(com.ghatana.digitalmarketing.domain.event.DmPiiClassification.NONE)
                .build();

            // P1-028: Record audit event before enqueuing
            return recordAuditEvent(ctx, "GOOGLE_ADS_CAMPAIGN_ENQUEUED",
                Map.of(
                    "commandId", commandId,
                    "internalCampaignId", payload.internalCampaignId(),
                    "googleAdsCustomerId", payload.googleAdsCustomerId()
                ))
                .then(__ -> outboxService.append(ctx, event))
                .map(outboxEntry -> {
                    LOG.info("[DMOS-GOOGLE-ADS] Campaign creation enqueued: commandId={}", commandId);
                    return commandId;
                })
                .whenException(e -> {
                    LOG.error("[DMOS-GOOGLE-ADS] Failed to enqueue campaign creation", e);
                    recordAuditEvent(ctx, "GOOGLE_ADS_CAMPAIGN_ENQUEUE_FAILED",
                        Map.of("commandId", commandId, "error", e.getMessage()));
                });
        } finally {
            MDC.clear();
        }
    }

    /**
     * P1-023/P1-024: Executes pending Google Ads commands from the outbox.
     *
     * <p>This method is called by the outbox dispatcher worker to execute
     * Google Ads commands. It checks kill switch before execution.</p>
     *
     * @param command the command to execute
     * @return promise resolving when execution is complete
     */
    public Promise<Void> executeCommand(DmCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        LOG.info("[DMOS-GOOGLE-ADS] Executing command: type={}, id={}",
            command.getCommandType(), command.getId());

        MDC.put("commandId", command.getId());
        MDC.put("correlationId", command.getCorrelationId());
        MDC.put("tenantId", command.getTenantId());
        MDC.put("workspaceId", command.getWorkspaceId());

        // P1-026: Create OpenTelemetry span
        Span span = observability.createSpan("GOOGLE_ADS_COMMAND_EXECUTE", "command.id", command.getId());
        span.setAttribute("command.type", command.getCommandType().name());
        span.setAttribute("tenant.id", command.getTenantId());
        span.setAttribute("workspace.id", command.getWorkspaceId());

        try (Scope scope = span.makeCurrent()) {
            // P1-024: Check kill switch before execution
            return checkKillSwitch(command)
                .then(killSwitchActive -> {
                    if (killSwitchActive) {
                        LOG.warn("[DMOS-GOOGLE-ADS] Kill switch active, blocking command: {}", command.getId());
                        span.setAttribute("killswitch.blocked", true);
                        span.setStatus(StatusCode.ERROR, "Kill switch active");

                        // P1-028: Audit the blocked action
                        return recordAuditEventForCommand(command, "GOOGLE_ADS_COMMAND_BLOCKED_BY_KILL_SWITCH",
                            Map.of("reason", "Kill switch active for GOOGLE_ADS scope"))
                            .then(__ -> Promise.ofException(new KillSwitchActiveException(
                                "Google Ads operations are temporarily disabled by kill switch")));
                    }

                    span.setAttribute("killswitch.blocked", false);

                    // Execute based on command type
                    return executeByType(command, span);
                })
                .whenResult(__ -> {
                    span.setStatus(StatusCode.OK);
                    LOG.info("[DMOS-GOOGLE-ADS] Command executed successfully: {}", command.getId());
                })
                .whenException(e -> {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    LOG.error("[DMOS-GOOGLE-ADS] Command execution failed: {}", command.getId(), e);

                    // P1-025: Schedule rollback if execution failed
                    if (isRollbackable(command)) {
                        scheduleRollback(command, e.getMessage());
                    }
                })
                .toVoid();
        } finally {
            span.end();
            MDC.clear();
        }
    }

    /**
     * P1-024: Checks if kill switch is active for Google Ads operations.
     */
    private Promise<Boolean> checkKillSwitch(DmCommand command) {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of(command.getTenantId()))
            .workspaceId(DmWorkspaceId.of(command.getWorkspaceId()))
            .actor(ActorRef.SYSTEM)
            .correlationId(DmCorrelationId.of(command.getCorrelationId()))
            .build();

        // Check global GOOGLE_ADS scope
        return killSwitchService.findActiveByScope(ctx, KILL_SWITCH_SCOPE, "GLOBAL")
            .map(Optional::isPresent)
            .then(globalActive -> {
                if (globalActive) {
                    return Promise.of(Boolean.TRUE);
                }
                // Check tenant-specific scope
                return killSwitchService.findActiveByScope(ctx, KILL_SWITCH_SCOPE, command.getTenantId())
                    .map(Optional::isPresent);
            })
            .then(tenantActive -> {
                if (tenantActive) {
                    return Promise.of(Boolean.TRUE);
                }
                // Check workspace-specific scope
                return killSwitchService.findActiveByScope(ctx, KILL_SWITCH_SCOPE, command.getWorkspaceId())
                    .map(Optional::isPresent);
            });
    }

    /**
     * Executes the command based on its type.
     */
    private Promise<Void> executeByType(DmCommand command, Span span) {
        return switch (command.getCommandType()) {
            case GOOGLE_ADS_CAMPAIGN_CREATE ->
                createCommandHandler.handle(command).toVoid();
            case GOOGLE_ADS_CAMPAIGN_ROLLBACK ->
                rollbackCommandHandler.handle(command).toVoid();
            default -> {
                LOG.warn("[DMOS-GOOGLE-ADS] Unknown command type: {}", command.getCommandType());
                span.setAttribute("error.type", "UNKNOWN_COMMAND");
                yield Promise.ofException(new IllegalArgumentException(
                    "Unknown command type: " + command.getCommandType()));
            }
        };
    }

    /**
     * P1-025: Schedules a rollback action for failed commands.
     */
    private void scheduleRollback(DmCommand command, String failureReason) {
        LOG.info("[DMOS-GOOGLE-ADS] Scheduling rollback for failed command: {}, reason: {}",
            command.getId(), failureReason);

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of(command.getTenantId()))
            .workspaceId(DmWorkspaceId.of(command.getWorkspaceId()))
            .actor(ActorRef.SYSTEM)
            .correlationId(DmCorrelationId.of(command.getCorrelationId()))
            .build();

        String rollbackActionType = determineRollbackActionType(command.getCommandType());
        DmRollbackActionService.ScheduleRollbackCommand rollbackCmd =
            new DmRollbackActionService.ScheduleRollbackCommand(
                command.getId(),
                rollbackActionType,
                command.getId(),
                command.getCommandType().name()
            );
        rollbackActionService.schedule(ctx, rollbackCmd)
            .whenException(e -> LOG.error("[DMOS-GOOGLE-ADS] Failed to schedule rollback for commandId={}", command.getId(), e));
    }

    private String determineRollbackActionType(DmCommandType originalType) {
        return switch (originalType) {
            case GOOGLE_ADS_CAMPAIGN_CREATE -> "GOOGLE_ADS_CAMPAIGN_DELETE";
            case CAMPAIGN_UPDATE -> "GOOGLE_ADS_CAMPAIGN_REVERT";
            default -> "GOOGLE_ADS_GENERIC_ROLLBACK";
        };
    }

    private boolean isRollbackable(DmCommand command) {
        return command.getCommandType() == DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE ||
               command.getCommandType() == DmCommandType.CAMPAIGN_UPDATE;
    }

    /**
     * P1-028: Records structured audit event.
     */
    private Promise<Void> recordAuditEvent(DmOperationContext ctx, String action, Map<String, Object> metadata) {
        return kernelAdapter.recordAudit(ctx, "google-ads-outbox", action, metadata).toVoid();
    }

    private Promise<Void> recordAuditEventForCommand(DmCommand command, String action, Map<String, Object> metadata) {
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of(command.getTenantId()))
            .workspaceId(DmWorkspaceId.of(command.getWorkspaceId()))
            .actor(ActorRef.SYSTEM)
            .correlationId(DmCorrelationId.of(command.getCorrelationId()))
            .build();
        return recordAuditEvent(ctx, action, metadata);
    }

    /**
     * Exception thrown when kill switch is active.
     */
    public static class KillSwitchActiveException extends RuntimeException {
        public KillSwitchActiveException(String message) {
            super(message);
        }
    }
}
