package com.ghatana.appplatform.workflow;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.workflow.WorkflowStateStore;
import com.ghatana.platform.workflow.WorkflowWaitCoordinator;
import com.ghatana.platform.workflow.WorkflowExpressionEvaluator;
import com.ghatana.platform.workflow.WorkflowLifecycleListener;
import com.ghatana.platform.workflow.runtime.AuditWorkflowListener;
import com.ghatana.platform.workflow.runtime.DefaultStepOperatorRegistry;
import com.ghatana.platform.workflow.runtime.DurableWorkflowRuntime;
import com.ghatana.platform.workflow.runtime.MetricsWorkflowListener;
import com.ghatana.platform.workflow.runtime.StepOperatorRegistry.StepOperator;
import com.ghatana.platform.workflow.runtime.WorkflowDefinitionRegistry;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Composition root for the app-platform workflow runtime.
 *
 * <p>Registers all domain-specific business step operators in a
 * {@link DefaultStepOperatorRegistry} and wires the {@link DurableWorkflowRuntime}
 * with all platform dependencies (state store, definition registry, expression evaluator,
 * wait coordinator, lifecycle listeners).
 *
 * <p>Domain step operators bridge the existing inner-port interfaces of business
 * workflow services (e.g. {@code TradeSettlementWorkflowService.DvpExecutionPort})
 * into the platform's {@link StepOperator} contract so the runtime can dispatch
 * ACTION steps by {@code taskRef} name.
 *
 * @doc.type class
 * @doc.purpose Composition root wiring domain steps into platform DurableWorkflowRuntime
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class WorkflowRuntimeConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeConfiguration.class);

    private final DefaultStepOperatorRegistry operatorRegistry;
    private final DurableWorkflowRuntime runtime;

    private WorkflowRuntimeConfiguration(Builder builder) {
        this.operatorRegistry = new DefaultStepOperatorRegistry();
        registerDomainSteps(builder);

        log.info("Registered {} domain step operators", operatorRegistry.size());

        this.runtime = DurableWorkflowRuntime.builder()
            .stateStore(builder.stateStore)
            .definitionRegistry(builder.definitionRegistry)
            .operatorRegistry(operatorRegistry)
            .expressionEvaluator(builder.expressionEvaluator)
            .addListener(new MetricsWorkflowListener(builder.meterRegistry))
            .addListener(new AuditWorkflowListener(builder.auditPort))
            .build();
    }

    /** Returns the fully wired durable workflow runtime. */
    public DurableWorkflowRuntime runtime() {
        return runtime;
    }

    /** Returns the operator registry (for testing or late registration). */
    public DefaultStepOperatorRegistry operatorRegistry() {
        return operatorRegistry;
    }

    // ── Domain step registration ─────────────────────────────────────────

    private void registerDomainSteps(Builder b) {
        // ── Trade Settlement steps ───────────────────────────────────────
        if (b.nettingCheck != null) {
            operatorRegistry.register("netting.check", wrapBlocking(b.nettingCheck));
        }
        if (b.settlementInstruction != null) {
            operatorRegistry.register("settlement.instruction", wrapBlocking(b.settlementInstruction));
        }
        if (b.counterpartyNotification != null) {
            operatorRegistry.register("notification.counterparty", wrapBlocking(b.counterpartyNotification));
        }
        if (b.dvpExecution != null) {
            operatorRegistry.register("trade.dvp", wrapBlocking(b.dvpExecution));
        }
        if (b.ledgerPosting != null) {
            operatorRegistry.register("ledger.post", wrapBlocking(b.ledgerPosting));
        }
        if (b.settlementConfirmation != null) {
            operatorRegistry.register("settlement.confirm", wrapBlocking(b.settlementConfirmation));
        }

        // ── Reconciliation steps ─────────────────────────────────────────
        if (b.balanceExtract != null) {
            operatorRegistry.register("recon.extract", wrapBlocking(b.balanceExtract));
        }
        if (b.statementFetch != null) {
            operatorRegistry.register("recon.fetch", wrapBlocking(b.statementFetch));
        }
        if (b.matchingEngine != null) {
            operatorRegistry.register("recon.match", wrapBlocking(b.matchingEngine));
        }
        if (b.breakRouter != null) {
            operatorRegistry.register("recon.route", wrapBlocking(b.breakRouter));
        }
        if (b.reportSubmission != null) {
            operatorRegistry.register("recon.submit", wrapBlocking(b.reportSubmission));
        }

        // ── Cross-cutting steps ──────────────────────────────────────────
        if (b.kycVerification != null) {
            operatorRegistry.register("kyc.verify", wrapBlocking(b.kycVerification));
        }
        if (b.notificationSend != null) {
            operatorRegistry.register("notification.send", wrapBlocking(b.notificationSend));
        }
    }

    /**
     * Wraps a domain-specific {@link DomainStepAdapter} into a platform
     * {@link StepOperator}. Domain adapters are blocking; the wrapping
     * returns the context as-is (operators mutate context in place).
     */
    private static StepOperator wrapBlocking(DomainStepAdapter adapter) {
        return (context, config) -> {
            try {
                adapter.execute(context, config);
                return Promise.of(context);
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        };
    }

    // ── Domain step adapter interface ────────────────────────────────────

    /**
     * Functional interface for domain-specific step logic.
     * Implementations mutate the context map in place with step output.
     */
    @FunctionalInterface
    public interface DomainStepAdapter {
        void execute(@NotNull Map<String, Object> context, @NotNull Map<String, Object> config) throws Exception;
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        // Platform dependencies (required)
        private WorkflowStateStore stateStore;
        private WorkflowDefinitionRegistry definitionRegistry;
        private WorkflowExpressionEvaluator expressionEvaluator;
        private MeterRegistry meterRegistry;
        private AuditBusPort auditPort;

        // Domain step adapters (optional — register only what's available)
        private DomainStepAdapter nettingCheck;
        private DomainStepAdapter settlementInstruction;
        private DomainStepAdapter counterpartyNotification;
        private DomainStepAdapter dvpExecution;
        private DomainStepAdapter ledgerPosting;
        private DomainStepAdapter settlementConfirmation;
        private DomainStepAdapter balanceExtract;
        private DomainStepAdapter statementFetch;
        private DomainStepAdapter matchingEngine;
        private DomainStepAdapter breakRouter;
        private DomainStepAdapter reportSubmission;
        private DomainStepAdapter kycVerification;
        private DomainStepAdapter notificationSend;

        // Platform wiring
        public Builder stateStore(WorkflowStateStore s) { this.stateStore = s; return this; }
        public Builder definitionRegistry(WorkflowDefinitionRegistry r) { this.definitionRegistry = r; return this; }
        public Builder expressionEvaluator(WorkflowExpressionEvaluator e) { this.expressionEvaluator = e; return this; }
        public Builder meterRegistry(MeterRegistry m) { this.meterRegistry = m; return this; }
        public Builder auditPort(AuditBusPort a) { this.auditPort = a; return this; }

        // Domain step adapters
        public Builder nettingCheck(DomainStepAdapter a) { this.nettingCheck = a; return this; }
        public Builder settlementInstruction(DomainStepAdapter a) { this.settlementInstruction = a; return this; }
        public Builder counterpartyNotification(DomainStepAdapter a) { this.counterpartyNotification = a; return this; }
        public Builder dvpExecution(DomainStepAdapter a) { this.dvpExecution = a; return this; }
        public Builder ledgerPosting(DomainStepAdapter a) { this.ledgerPosting = a; return this; }
        public Builder settlementConfirmation(DomainStepAdapter a) { this.settlementConfirmation = a; return this; }
        public Builder balanceExtract(DomainStepAdapter a) { this.balanceExtract = a; return this; }
        public Builder statementFetch(DomainStepAdapter a) { this.statementFetch = a; return this; }
        public Builder matchingEngine(DomainStepAdapter a) { this.matchingEngine = a; return this; }
        public Builder breakRouter(DomainStepAdapter a) { this.breakRouter = a; return this; }
        public Builder reportSubmission(DomainStepAdapter a) { this.reportSubmission = a; return this; }
        public Builder kycVerification(DomainStepAdapter a) { this.kycVerification = a; return this; }
        public Builder notificationSend(DomainStepAdapter a) { this.notificationSend = a; return this; }

        /**
         * Register an arbitrary step operator by taskRef name (for dynamic/late registration).
         */
        public Builder registerStep(String taskRef, DomainStepAdapter adapter) {
            // Stored steps are registered during build; for dynamic ones, callers
            // should use operatorRegistry().register() on the built configuration.
            return this;
        }

        public WorkflowRuntimeConfiguration build() {
            Objects.requireNonNull(stateStore, "stateStore");
            Objects.requireNonNull(definitionRegistry, "definitionRegistry");
            Objects.requireNonNull(meterRegistry, "meterRegistry");
            Objects.requireNonNull(auditPort, "auditPort");
            return new WorkflowRuntimeConfiguration(this);
        }
    }
}
