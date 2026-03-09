package com.ghatana.softwareorg.support;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

/**
 * Support Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for support-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Ticket lifecycle management
 * - Customer feedback collection
 * - SLA tracking
 *
 * @doc.type class
 * @doc.purpose Support department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class SupportDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "SUPPORT";
    public static final String DEPARTMENT_NAME = "Support";

    public SupportDepartment(AbstractOrganization org, EventPublisher publisher) {
        super(org, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Open a new support ticket.
     *
     * @param title       ticket title
     * @param description ticket description
     * @param priority    ticket priority
     * @return ticket ID
     */
    public String openTicket(String title, String description, String priority) {
        String ticketId = Identifier.random().raw();

        publishEvent("SupportTicketOpened", newPayload()
                .withField("ticket_id", ticketId)
                .withField("title", title)
                .withField("description", description)
                .withField("priority", priority)
                .withField("status", "OPENED")
                .withTimestamp()
                .build());

        return ticketId;
    }

    /**
     * Hook: Assign ticket to support agent.
     *
     * @param ticketId ticket identifier
     * @param agentId  target agent
     */
    public void assignTicket(String ticketId, String agentId) {
        publishEvent("SupportTicketAssigned", newPayload()
                .withField("ticket_id", ticketId)
                .withField("agent_id", agentId)
                .withField("status", "ASSIGNED")
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Add response to ticket.
     *
     * @param ticketId     ticket identifier
     * @param responseText agent or system response
     */
    public void addTicketResponse(String ticketId, String responseText) {
        publishEvent("SupportTicketUpdated", newPayload()
                .withField("ticket_id", ticketId)
                .withField("response_text", responseText)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Resolve support ticket.
     *
     * @param ticketId          ticket identifier
     * @param resolutionSummary summary of resolution
     */
    public void resolveTicket(String ticketId, String resolutionSummary) {
        publishEvent("SupportTicketResolved", newPayload()
                .withField("ticket_id", ticketId)
                .withField("resolution_summary", resolutionSummary)
                .withField("status", "RESOLVED")
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Record customer feedback.
     *
     * @param ticketId          associated ticket
     * @param satisfactionScore 1-5 rating
     * @param feedbackText      customer comment
     */
    public void submitFeedback(String ticketId, int satisfactionScore, String feedbackText) {
        publishEvent("CustomerFeedbackSubmitted", newPayload()
                .withField("ticket_id", ticketId)
                .withField("satisfaction_score", satisfactionScore)
                .withField("feedback_text", feedbackText)
                .withTimestamp()
                .build());
    }
}
