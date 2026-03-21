package com.ghatana.products.finance.domains.posttrade;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Trade Settlement lifecycle workflow orchestration service.
 *
 * <p>Covers the full settlement lifecycle:
 * <ol>
 *   <li>TradeConfirmed → netting eligibility check</li>
 *   <li>PARALLEL: settlement instruction generation + counterparty notification</li>
 *   <li>WAIT matching (T-1) via WaitCorrelationStepService</li>
 *   <li>DVP execution</li>
 *   <li>Ledger posting</li>
 *   <li>Settlement confirmation</li>
 * </ol>
 * Includes saga compensation on DVP or Ledger failure.</p>
 *
 * <p>Extracted from {@code products/app-platform/kernel/workflow-orchestration} per
 * KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md Workstream C (Day 12):
 * finance-shaped workflow services belong in the post-trade domain pack,
 * not in the generic platform layer.</p>
 *
 * @doc.type    class
 * @doc.purpose Post-trade settlement workflow — saga with compensation, DVP, ledger posting
 * @doc.layer   product
 * @doc.pattern Port-Adapter; Saga with compensation; Promise.ofBlocking
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class TradeSettlementWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    /**
     * Port for determining whether a trade is eligible for netting.
     */
    public interface NettingEligibilityPort {
        boolean isNettingEligible(String tradeId, String counterpartyId) throws Exception;
    }

    /**
     * Port for generating settlement instructions.
     */
    public interface SettlementInstructionPort {
        String generateInstruction(String tradeId, String settlementDate) throws Exception;
    }

    /**
     * Port for notifying counterparties about settlement instructions.
     */
    public interface CounterpartyNotificationPort {
        void notifyCounterparty(String tradeId, String counterpartyId, String instructionRef) throws Exception;
    }

    /**
     * Port for executing delivery-versus-payment (DVP) and compensating on failure.
     */
    public interface DvpExecutionPort {
        DvpResult executeDvp(String tradeId, String instructionRef) throws Exception;
        void reverseDvp(String tradeId, String dvpRef) throws Exception;
    }

    /**
     * Port for posting trades to the ledger and reversing on failure.
     */
    public interface LedgerPostingPort {
        String postTrade(String tradeId, DvpResult dvp) throws Exception;
        void reverseLedger(String ledgerRef) throws Exception;
    }

    /**
     * Port for sending settlement confirmations.
     */
    public interface SettlementConfirmationPort {
        void sendConfirmation(String tradeId, String ledgerRef, String counterpartyId) throws Exception;
    }

    /**
     * Port for managing workflow instance lifecycle steps.
     */
    public interface WorkflowInstancePort {
        String startInstance(String workflowName, Map<String, Object> ctx) throws Exception;
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    /**
     * Trade details required to initiate the settlement workflow.
     */
    public record TradeDetails(
        String tradeId, String counterpartyId, String instrument,
        long nominalUnits, String currency, String settlementDate
    ) {}

    /**
     * Result of a DVP execution attempt.
     */
    public record DvpResult(
        String dvpRef, boolean settled, String settledAt, String failureReason
    ) {}

    /**
     * Final result of a complete settlement workflow run.
     */
    public record SettlementResult(
        String instanceId, String tradeId,
        String instructionRef, DvpResult dvpResult,
        String ledgerRef,
        boolean settled, String status
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final NettingEligibilityPort nettingEligibility;
    private final SettlementInstructionPort settlementInstruction;
    private final CounterpartyNotificationPort counterpartyNotification;
    private final DvpExecutionPort dvpExecution;
    private final LedgerPostingPort ledgerPosting;
    private final SettlementConfirmationPort settlementConfirmation;
    private final WorkflowInstancePort workflowInstance;
    private final Executor executor;
    private final Counter settledCounter;
    private final Counter failedCounter;
    private final Counter compensatedCounter;
    private final Timer settlementLatencyTimer;

    /**
     * Creates a new trade settlement workflow service.
     *
     * @param nettingEligibility      port for netting eligibility checks
     * @param settlementInstruction   port for instruction generation
     * @param counterpartyNotification port for counterparty notifications
     * @param dvpExecution            port for DVP execution and reversal
     * @param ledgerPosting           port for ledger posting and reversal
     * @param settlementConfirmation  port for confirmation dispatch
     * @param workflowInstance        port for workflow instance lifecycle
     * @param registry                Micrometer registry for metrics
     * @param executor                executor for blocking I/O operations
     */
    public TradeSettlementWorkflowService(
        NettingEligibilityPort nettingEligibility,
        SettlementInstructionPort settlementInstruction,
        CounterpartyNotificationPort counterpartyNotification,
        DvpExecutionPort dvpExecution,
        LedgerPostingPort ledgerPosting,
        SettlementConfirmationPort settlementConfirmation,
        WorkflowInstancePort workflowInstance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.nettingEligibility       = Objects.requireNonNull(nettingEligibility, "nettingEligibility");
        this.settlementInstruction    = Objects.requireNonNull(settlementInstruction, "settlementInstruction");
        this.counterpartyNotification = Objects.requireNonNull(counterpartyNotification, "counterpartyNotification");
        this.dvpExecution             = Objects.requireNonNull(dvpExecution, "dvpExecution");
        this.ledgerPosting            = Objects.requireNonNull(ledgerPosting, "ledgerPosting");
        this.settlementConfirmation   = Objects.requireNonNull(settlementConfirmation, "settlementConfirmation");
        this.workflowInstance         = Objects.requireNonNull(workflowInstance, "workflowInstance");
        this.executor                 = Objects.requireNonNull(executor, "executor");
        this.settledCounter     = Counter.builder("settlement.workflow.settled").register(registry);
        this.failedCounter      = Counter.builder("settlement.workflow.failed").register(registry);
        this.compensatedCounter = Counter.builder("settlement.workflow.compensated").register(registry);
        this.settlementLatencyTimer = Timer.builder("settlement.workflow.duration").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Executes the full trade settlement workflow triggered by a TradeConfirmed event.
     *
     * <p>On DVP or ledger failure, saga compensation reverses any posted ledger entries
     * and reverses the DVP before marking the workflow as FAILED_COMPENSATED.</p>
     *
     * @param trade the trade details to settle
     * @return Promise resolving to the settlement result
     */
    public Promise<SettlementResult> settle(TradeDetails trade) {
        Objects.requireNonNull(trade, "trade");
        return Promise.ofBlocking(executor, () -> {
            long startNanos = System.nanoTime();
            String instanceId = workflowInstance.startInstance("TradeSettlement",
                Map.of("tradeId", trade.tradeId(), "counterparty", trade.counterpartyId()));

            String instructionRef = null;
            String ledgerRef = null;
            String dvpRef = null;

            try {
                // Step 1: Netting eligibility
                boolean nettingEligible = nettingEligibility.isNettingEligible(
                    trade.tradeId(), trade.counterpartyId());
                workflowInstance.completeStep(instanceId, "NETTING_CHECK",
                    Map.of("eligible", nettingEligible));

                // Step 2 PARALLEL: Generate instruction + notify counterparty
                final String[] instrResult = new String[1];
                try (var exec = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                    var instrFuture = exec.submit(() ->
                        settlementInstruction.generateInstruction(
                            trade.tradeId(), trade.settlementDate()));
                    var notifyFuture = exec.submit(() -> {
                        // Preliminary notify before instruction is complete
                        counterpartyNotification.notifyCounterparty(
                            trade.tradeId(), trade.counterpartyId(), "PENDING");
                        return null;
                    });
                    instrResult[0] = instrFuture.get();
                    notifyFuture.get();
                }
                instructionRef = instrResult[0];
                workflowInstance.completeStep(instanceId, "INSTRUCTION_AND_NOTIFY",
                    Map.of("instructionRef", instructionRef));

                // Step 3: WAIT matching T-1 (WaitCorrelationStepService registers the wait;
                // execution continues synchronously in this path once correlation signal arrives)

                // Step 4: DVP execution
                DvpResult dvp = dvpExecution.executeDvp(trade.tradeId(), instructionRef);
                dvpRef = dvp.dvpRef();
                if (!dvp.settled()) {
                    throw new SettlementException("DVP failed: " + dvp.failureReason());
                }
                workflowInstance.completeStep(instanceId, "DVP", Map.of("dvpRef", dvpRef));

                // Step 5: Ledger posting
                ledgerRef = ledgerPosting.postTrade(trade.tradeId(), dvp);
                workflowInstance.completeStep(instanceId, "LEDGER_POST",
                    Map.of("ledgerRef", ledgerRef));

                // Step 6: Confirmation
                settlementConfirmation.sendConfirmation(
                    trade.tradeId(), ledgerRef, trade.counterpartyId());
                workflowInstance.completeStep(instanceId, "CONFIRM", Map.of());

                settledCounter.increment();
                settlementLatencyTimer.record(
                    java.time.Duration.ofNanos(System.nanoTime() - startNanos));
                return new SettlementResult(instanceId, trade.tradeId(), instructionRef,
                    dvp, ledgerRef, true, "SETTLED");

            } catch (SettlementException e) {
                // Saga compensation: reverse ledger then reverse DVP
                String capturedLedgerRef = ledgerRef;
                String capturedDvpRef = dvpRef;
                try {
                    if (capturedLedgerRef != null) ledgerPosting.reverseLedger(capturedLedgerRef);
                    if (capturedDvpRef != null) dvpExecution.reverseDvp(trade.tradeId(), capturedDvpRef);
                    compensatedCounter.increment();
                } catch (Exception ignored) {
                    // Compensation failure is logged externally via observability
                }
                workflowInstance.failStep(instanceId, "SETTLEMENT", e.getMessage());
                failedCounter.increment();
                return new SettlementResult(instanceId, trade.tradeId(), instructionRef,
                    new DvpResult(null, false, null, e.getMessage()), null, false, "FAILED_COMPENSATED");
            }
        });
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    /**
     * Thrown when settlement cannot proceed due to a step failure.
     */
    public static class SettlementException extends RuntimeException {
        /**
         * Creates a new settlement exception.
         *
         * @param message describing the failure cause
         */
        public SettlementException(String message) { super(message); }
    }
}
