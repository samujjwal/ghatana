package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Pre-built Corporate Action (CA) processing workflow.
 *              Steps: CA Announced → WAIT record date → Calculate entitlements →
 *              Tax withholding → DECISION (election required?) →
 *              [election portal + WAIT elections] / [proceed] → Ledger posting → Confirmation.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; Promise.ofBlocking; Saga
 *
 * STORY-W01-012: Corporate action processing workflow
 */
public class CorporateActionWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface EntitlementCalculatorPort {
        List<ClientEntitlement> calculate(String caId, String recordDate) throws Exception;
    }

    public interface TaxWithholdingPort {
        TaxResult applyWithholding(ClientEntitlement entitlement) throws Exception;
    }

    public interface ElectionPortalPort {
        String openElectionPortal(String caId, List<ClientEntitlement> eligible) throws Exception;
        List<ElectionRecord> collectElections(String portalId) throws Exception;
    }

    public interface LedgerPostingPort {
        String postEntitlements(String caId, List<ClientEntitlement> netEntitlements) throws Exception;
    }

    public interface CaNotificationPort {
        void sendConfirmation(String caId, String clientId, String entitlementRef) throws Exception;
    }

    public interface WorkflowInstancePort {
        String startInstance(String workflowName, Map<String, Object> ctx) throws Exception;
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record CorporateAction(
        String caId, String issuer, String caType,  // DIVIDEND | RIGHTS | SPLIT | MERGER
        String recordDate, boolean electionRequired, String electionDeadline
    ) {}

    public record ClientEntitlement(
        String caId, String clientId, String currency,
        long grossAmountCents, long withheldAmountCents, long netAmountCents
    ) {}

    public record TaxResult(long withheldAmountCents, double withholdingRate) {}

    public record ElectionRecord(String caId, String clientId, String electionType, String submittedAt) {}

    public record CaProcessingResult(
        String instanceId, String caId, String status,
        int affectedClients, String ledgerRef
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final EntitlementCalculatorPort entitlementCalculator;
    private final TaxWithholdingPort taxWithholding;
    private final ElectionPortalPort electionPortal;
    private final LedgerPostingPort ledgerPosting;
    private final CaNotificationPort caNotification;
    private final WorkflowInstancePort workflowInstance;
    private final Executor executor;
    private final Counter caProcessedCounter;
    private final Counter electionRequiredCounter;

    public CorporateActionWorkflowService(
        EntitlementCalculatorPort entitlementCalculator,
        TaxWithholdingPort taxWithholding,
        ElectionPortalPort electionPortal,
        LedgerPostingPort ledgerPosting,
        CaNotificationPort caNotification,
        WorkflowInstancePort workflowInstance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.entitlementCalculator = entitlementCalculator;
        this.taxWithholding        = taxWithholding;
        this.electionPortal        = electionPortal;
        this.ledgerPosting         = ledgerPosting;
        this.caNotification        = caNotification;
        this.workflowInstance      = workflowInstance;
        this.executor              = executor;
        this.caProcessedCounter      = Counter.builder("ca.workflow.processed").register(registry);
        this.electionRequiredCounter = Counter.builder("ca.workflow.election_required").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Initiate the CA processing workflow on announcement.
     * The record date WAIT is delegated to WaitCorrelationStepService in a real deployment;
     * here we accept the recordDate as pre-set.
     */
    public Promise<CaProcessingResult> processOnAnnouncement(CorporateAction ca) {
        return Promise.ofBlocking(executor, () -> {
            String instanceId = workflowInstance.startInstance("CorporateActionProcessing",
                Map.of("caId", ca.caId(), "caType", ca.caType(), "recordDate", ca.recordDate()));

            try {
                // Step 1: WAIT record date (simulated — WaitCorrelationStepService handles the actual pause)
                workflowInstance.completeStep(instanceId, "WAIT_RECORD_DATE",
                    Map.of("recordDate", ca.recordDate()));

                // Step 2: Calculate entitlements
                List<ClientEntitlement> entitlements = entitlementCalculator.calculate(ca.caId(), ca.recordDate());
                workflowInstance.completeStep(instanceId, "CALCULATE_ENTITLEMENTS",
                    Map.of("count", entitlements.size()));

                // Step 3: Tax withholding
                List<ClientEntitlement> net = new ArrayList<>();
                for (ClientEntitlement e : entitlements) {
                    TaxResult tax = taxWithholding.applyWithholding(e);
                    net.add(new ClientEntitlement(e.caId(), e.clientId(), e.currency(),
                        e.grossAmountCents(), tax.withheldAmountCents(),
                        e.grossAmountCents() - tax.withheldAmountCents()));
                }
                workflowInstance.completeStep(instanceId, "TAX_WITHHOLDING", Map.of());

                // Step 4: DECISION — election required?
                if (ca.electionRequired()) {
                    electionRequiredCounter.increment();
                    String portalId = electionPortal.openElectionPortal(ca.caId(), net);
                    workflowInstance.completeStep(instanceId, "OPEN_ELECTION_PORTAL",
                        Map.of("portalId", portalId));
                    // WAIT elections → handled by WaitCorrelationStepService (signal: ElectionsComplete)
                    List<ElectionRecord> elections = electionPortal.collectElections(portalId);
                    workflowInstance.completeStep(instanceId, "WAIT_ELECTIONS",
                        Map.of("electionsReceived", elections.size()));
                }

                // Step 5: Ledger posting
                String ledgerRef = ledgerPosting.postEntitlements(ca.caId(), net);
                workflowInstance.completeStep(instanceId, "LEDGER_POST", Map.of("ref", ledgerRef));

                // Step 6: Confirmation
                for (ClientEntitlement e : net) {
                    caNotification.sendConfirmation(ca.caId(), e.clientId(), ledgerRef);
                }
                workflowInstance.completeStep(instanceId, "CONFIRM", Map.of());

                caProcessedCounter.increment();
                return new CaProcessingResult(instanceId, ca.caId(), "COMPLETED", net.size(), ledgerRef);

            } catch (Exception e) {
                workflowInstance.failStep(instanceId, "CA_PROCESSING", e.getMessage());
                throw e;
            }
        });
    }
}
