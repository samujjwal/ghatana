package com.ghatana.products.finance.domains.corporateactions;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Corporate Action (CA) processing workflow orchestration service.
 *
 * <p>Manages the full CA lifecycle:
 * <ol>
 *   <li>CA Announced → WAIT record date (WaitCorrelationStepService)</li>
 *   <li>Calculate entitlements per client</li>
 *   <li>Apply tax withholding per entitlement</li>
 *   <li>DECISION: election required?</li>
 *   <li>If yes → open election portal + WAIT elections</li>
 *   <li>Ledger posting of net entitlements</li>
 *   <li>Client confirmation</li>
 * </ol></p>
 *
 * <p>Extracted from {@code products/app-platform/kernel/workflow-orchestration} per
 * KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md Workstream C (Day 12):
 * finance-shaped workflow services belong in the corporate-actions domain pack,
 * not in the generic platform layer.</p>
 *
 * @doc.type    class
 * @doc.purpose Corporate action processing workflow — entitlements, tax withholding, election portal
 * @doc.layer   product
 * @doc.pattern Port-Adapter; Promise.ofBlocking; Saga
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class CorporateActionWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    /**
     * Port for calculating client entitlements for a corporate action.
     */
    public interface EntitlementCalculatorPort {
        List<ClientEntitlement> calculate(String caId, String recordDate) throws Exception;
    }

    /**
     * Port for applying tax withholding rules to an entitlement.
     */
    public interface TaxWithholdingPort {
        TaxResult applyWithholding(ClientEntitlement entitlement) throws Exception;
    }

    /**
     * Port for managing the optional election portal when client choice is required.
     */
    public interface ElectionPortalPort {
        String openElectionPortal(String caId, List<ClientEntitlement> eligible) throws Exception;
        List<ElectionRecord> collectElections(String portalId) throws Exception;
    }

    /**
     * Port for posting net entitlements to the ledger.
     */
    public interface LedgerPostingPort {
        String postEntitlements(String caId, List<ClientEntitlement> netEntitlements) throws Exception;
    }

    /**
     * Port for sending CA processing confirmation to clients.
     */
    public interface CaNotificationPort {
        void sendConfirmation(String caId, String clientId, String entitlementRef) throws Exception;
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
     * Corporate action announcement details.
     */
    public record CorporateAction(
        String caId, String issuer, String caType,  // DIVIDEND | RIGHTS | SPLIT | MERGER
        String recordDate, boolean electionRequired, String electionDeadline
    ) {}

    /**
     * Calculated entitlement for a single client.
     */
    public record ClientEntitlement(
        String caId, String clientId, String currency,
        long grossAmountCents, long withheldAmountCents, long netAmountCents
    ) {}

    /**
     * Result of tax withholding application.
     */
    public record TaxResult(long withheldAmountCents, double withholdingRate) {}

    /**
     * A client election record from the election portal.
     */
    public record ElectionRecord(String caId, String clientId, String electionType, String submittedAt) {}

    /**
     * Final result of a complete CA processing workflow run.
     */
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

    /**
     * Creates a new corporate action workflow service.
     *
     * @param entitlementCalculator port for entitlement calculations
     * @param taxWithholding        port for tax withholding
     * @param electionPortal        port for election portal management
     * @param ledgerPosting         port for ledger posting
     * @param caNotification        port for client notification
     * @param workflowInstance      port for workflow lifecycle
     * @param registry              Micrometer registry for metrics
     * @param executor              executor for blocking I/O operations
     */
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
        this.entitlementCalculator = Objects.requireNonNull(entitlementCalculator, "entitlementCalculator");
        this.taxWithholding        = Objects.requireNonNull(taxWithholding, "taxWithholding");
        this.electionPortal        = Objects.requireNonNull(electionPortal, "electionPortal");
        this.ledgerPosting         = Objects.requireNonNull(ledgerPosting, "ledgerPosting");
        this.caNotification        = Objects.requireNonNull(caNotification, "caNotification");
        this.workflowInstance      = Objects.requireNonNull(workflowInstance, "workflowInstance");
        this.executor              = Objects.requireNonNull(executor, "executor");
        this.caProcessedCounter      = Counter.builder("ca.workflow.processed").register(registry);
        this.electionRequiredCounter = Counter.builder("ca.workflow.election_required").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Initiates the CA processing workflow on announcement of the corporate action.
     *
     * <p>The record date WAIT is handled by {@code WaitCorrelationStepService} in a
     * real deployment; here the recordDate is accepted as already settled.</p>
     *
     * @param ca the corporate action to process
     * @return Promise resolving to the CA processing result
     */
    public Promise<CaProcessingResult> processOnAnnouncement(CorporateAction ca) {
        Objects.requireNonNull(ca, "ca");
        return Promise.ofBlocking(executor, () -> {
            String instanceId = workflowInstance.startInstance("CorporateActionProcessing",
                Map.of("caId", ca.caId(), "caType", ca.caType(), "recordDate", ca.recordDate()));

            try {
                // Step 1: WAIT record date (WaitCorrelationStepService handles the pause)
                workflowInstance.completeStep(instanceId, "WAIT_RECORD_DATE",
                    Map.of("recordDate", ca.recordDate()));

                // Step 2: Calculate entitlements
                List<ClientEntitlement> entitlements =
                    entitlementCalculator.calculate(ca.caId(), ca.recordDate());
                workflowInstance.completeStep(instanceId, "CALCULATE_ENTITLEMENTS",
                    Map.of("count", entitlements.size()));

                // Step 3: Tax withholding per entitlement
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
                    // WAIT elections — WaitCorrelationStepService signal: ElectionsComplete
                    List<ElectionRecord> elections = electionPortal.collectElections(portalId);
                    workflowInstance.completeStep(instanceId, "WAIT_ELECTIONS",
                        Map.of("electionsReceived", elections.size()));
                }

                // Step 5: Ledger posting
                String ledgerRef = ledgerPosting.postEntitlements(ca.caId(), net);
                workflowInstance.completeStep(instanceId, "LEDGER_POST",
                    Map.of("ref", ledgerRef));

                // Step 6: Confirmation per client
                for (ClientEntitlement e : net) {
                    caNotification.sendConfirmation(ca.caId(), e.clientId(), ledgerRef);
                }
                workflowInstance.completeStep(instanceId, "CONFIRM", Map.of());

                caProcessedCounter.increment();
                return new CaProcessingResult(instanceId, ca.caId(), "COMPLETED",
                    net.size(), ledgerRef);

            } catch (Exception e) {
                workflowInstance.failStep(instanceId, "CA_PROCESSING", e.getMessage());
                throw e;
            }
        });
    }
}
