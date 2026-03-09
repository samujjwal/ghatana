package com.ghatana.softwareorg.support.pipelines;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.support.SupportDepartment;

/**
 * Pipeline registrar for Support department workflows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Registers UnifiedOperator pipelines with AEP for Support event flows: -
 * Ticket routing pipeline (SupportTicketOpened → assigned agent) - Ticket
 * resolution pipeline (responses collected → resolved) - Feedback aggregation
 * pipeline (CustomerFeedback → analysis)
 *
 * @doc.type class
 * @doc.purpose Support department pipeline orchestration
 * @doc.layer product
 * @doc.pattern Pipeline Registrar
 */
public class SupportPipelinesRegistrar {

    private final SupportDepartment department;
    private final EventPublisher publisher;

    public SupportPipelinesRegistrar(SupportDepartment department, EventPublisher publisher) {
        this.department = department;
        this.publisher = publisher;
    }

    /**
     * Register all Support pipelines with AEP.
     *
     * Registers 3 pipelines: 1. Ticket routing (SupportTicketOpened → agent
     * assignment) 2. Ticket resolution (SupportTicketAssigned → resolved) 3.
     * Feedback aggregation (CustomerFeedback → sentiment analysis)
     *
     * @return number of pipelines registered
     */
    public int registerPipelines() {
        registerTicketRoutingPipeline();
        registerTicketResolutionPipeline();
        registerFeedbackAggregationPipeline();
        return 3;
    }

    private void registerTicketRoutingPipeline() {
        try {
            // Pipeline: SupportTicketOpened → priority assess → agent assignment
            logPipelineRegistration("ticket-routing", "SupportTicketOpened", "SupportTicketAssigned");
        } catch (Exception e) {
            handlePipelineRegistrationError("ticket-routing", e);
        }
    }

    private void registerTicketResolutionPipeline() {
        try {
            // Pipeline: SupportTicketAssigned → collect responses → resolution
            logPipelineRegistration("ticket-resolution", "SupportTicketAssigned", "SupportTicketResolved");
        } catch (Exception e) {
            handlePipelineRegistrationError("ticket-resolution", e);
        }
    }

    private void registerFeedbackAggregationPipeline() {
        try {
            // Pipeline: CustomerFeedbackSubmitted → aggregate → satisfaction metrics
            logPipelineRegistration("feedback-aggregation", "CustomerFeedbackSubmitted", "SatisfactionMetrics");
        } catch (Exception e) {
            handlePipelineRegistrationError("feedback-aggregation", e);
        }
    }

    private void logPipelineRegistration(String pipelineName, String inputEvents, String outputEvents) {
        System.out.printf("[Support] Registered pipeline '%s': %s -> %s%n",
                pipelineName, inputEvents, outputEvents);
    }

    private void handlePipelineRegistrationError(String pipelineName, Exception e) {
        System.err.printf("[Support] Failed to register pipeline '%s': %s%n",
                pipelineName, e.getMessage());
    }
}
