package com.ghatana.digitalmarketing.domain.event;

/**
 * Typed, versioned DMOS domain event types covering the full MVP event surface.
 *
 * <p>Every significant state transition in DMOS emits a typed event. The event type
 * is the primary discriminant for routing, schema versioning, PII classification,
 * and replay filtering.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS typed event type taxonomy for F2 execution MVP and audit surface
 * @doc.layer product
 * @doc.pattern Value Object, Registry
 */
public enum DmEventType {

    // ── Campaign lifecycle ────────────────────────────────────────────────────
    /** A new campaign record was created in DRAFT status. */
    CAMPAIGN_CREATED("dm.campaign.created.v1"),
    /** A campaign was launched; spend activation began. */
    CAMPAIGN_LAUNCHED("dm.campaign.launched.v1"),
    /** A campaign was paused (manual or kill-switch). */
    CAMPAIGN_PAUSED("dm.campaign.paused.v1"),
    /** A campaign reached its end date or budget limit and completed normally. */
    CAMPAIGN_COMPLETED("dm.campaign.completed.v1"),
    /** A campaign was cancelled before completion. */
    CAMPAIGN_CANCELLED("dm.campaign.cancelled.v1"),
    /** A campaign budget was updated. */
    CAMPAIGN_BUDGET_UPDATED("dm.campaign.budget-updated.v1"),

    // ── Content lifecycle ─────────────────────────────────────────────────────
    /** A new content version was created. */
    CONTENT_CREATED("dm.content.created.v1"),
    /** A content version was approved and published. */
    CONTENT_PUBLISHED("dm.content.published.v1"),
    /** A content version was archived. */
    CONTENT_ARCHIVED("dm.content.archived.v1"),
    /** A content version passed brand/claim validation. */
    CONTENT_VALIDATION_PASSED("dm.content.validation-passed.v1"),
    /** A content version failed brand/claim validation. */
    CONTENT_VALIDATION_FAILED("dm.content.validation-failed.v1"),

    // ── Approval lifecycle ────────────────────────────────────────────────────
    /** A human approval request was created. */
    APPROVAL_REQUESTED("dm.approval.requested.v1"),
    /** An approval request was approved by the reviewer. */
    APPROVAL_APPROVED("dm.approval.approved.v1"),
    /** An approval request was rejected. */
    APPROVAL_REJECTED("dm.approval.rejected.v1"),
    /** An approval request was escalated to a higher authority. */
    APPROVAL_ESCALATED("dm.approval.escalated.v1"),
    /** An approval request expired without a decision. */
    APPROVAL_EXPIRED("dm.approval.expired.v1"),

    // ── Consent lifecycle ─────────────────────────────────────────────────────
    /** Marketing consent was captured for a contact. */
    CONSENT_CAPTURED("dm.consent.captured.v1"),
    /** Consent was revoked by the contact or operator. */
    CONSENT_REVOKED("dm.consent.revoked.v1"),
    /** A durable consent proof snapshot was stored. */
    CONSENT_PROOF_STORED("dm.consent.proof-stored.v1"),

    // ── Lead lifecycle ────────────────────────────────────────────────────────
    /** A lead was captured via form submission or import. */
    LEAD_CAPTURED("dm.lead.captured.v1"),
    /** A lead was marked qualified for follow-up. */
    LEAD_QUALIFIED("dm.lead.qualified.v1"),
    /** A lead was converted to a customer. */
    LEAD_CONVERTED("dm.lead.converted.v1"),
    /** A lead was disqualified. */
    LEAD_DISQUALIFIED("dm.lead.disqualified.v1"),
    /** A lead touchpoint was recorded (ad click, page view, form view). */
    LEAD_TOUCHPOINT_RECORDED("dm.lead.touchpoint-recorded.v1"),

    // ── Proposal lifecycle ────────────────────────────────────────────────────
    /** A proposal was created. */
    PROPOSAL_CREATED("dm.proposal.created.v1"),
    /** A proposal was submitted to the prospect. */
    PROPOSAL_SUBMITTED("dm.proposal.submitted.v1"),
    /** A proposal was accepted. */
    PROPOSAL_APPROVED("dm.proposal.approved.v1"),
    /** A proposal was rejected. */
    PROPOSAL_REJECTED("dm.proposal.rejected.v1"),

    // ── Workflow execution ────────────────────────────────────────────────────
    /** A durable workflow execution was started. */
    WORKFLOW_STARTED("dm.workflow.started.v1"),
    /** A workflow step completed successfully. */
    WORKFLOW_STEP_COMPLETED("dm.workflow.step-completed.v1"),
    /** A workflow step failed; may be retried. */
    WORKFLOW_STEP_FAILED("dm.workflow.step-failed.v1"),
    /** A workflow was paused waiting for external input (e.g. approval). */
    WORKFLOW_PAUSED("dm.workflow.paused.v1"),
    /** A paused workflow was resumed after external input. */
    WORKFLOW_RESUMED("dm.workflow.resumed.v1"),
    /** A workflow completed all steps successfully. */
    WORKFLOW_COMPLETED("dm.workflow.completed.v1"),
    /** A workflow reached a terminal failure after retries exhausted. */
    WORKFLOW_FAILED("dm.workflow.failed.v1"),

    // ── Command execution ─────────────────────────────────────────────────────
    /** A typed command was created and is awaiting execution. */
    COMMAND_CREATED("dm.command.created.v1"),
    /** A command was executed successfully. */
    COMMAND_EXECUTED("dm.command.executed.v1"),
    /** A command execution failed. */
    COMMAND_FAILED("dm.command.failed.v1"),
    /** A compensating/rollback action was executed for a command. */
    COMMAND_ROLLED_BACK("dm.command.rolled-back.v1"),

    // ── Connector / external system ───────────────────────────────────────────
    /** An external account was connected (e.g. Google Ads). */
    CONNECTOR_CONNECTED("dm.connector.connected.v1"),
    /** An external account connection was removed. */
    CONNECTOR_DISCONNECTED("dm.connector.disconnected.v1"),
    /** A connector experienced an error during operation. */
    CONNECTOR_ERROR("dm.connector.error.v1"),
    /** A connector sync cycle completed. */
    CONNECTOR_SYNCED("dm.connector.synced.v1"),
    /** A connector transition to degraded/unhealthy. */
    CONNECTOR_HEALTH_CHANGED("dm.connector.health-changed.v1"),

    // ── Analytics / funnel ────────────────────────────────────────────────────
    /** A landing page view was recorded. */
    PAGE_VIEW("dm.analytics.page-view.v1"),
    /** A lead capture form was submitted. */
    FORM_SUBMISSION("dm.analytics.form-submission.v1"),
    /** An ad click was recorded or imported. */
    AD_CLICK("dm.analytics.ad-click.v1"),
    /** A conversion event was recorded. */
    CONVERSION("dm.analytics.conversion.v1"),
    /** Campaign performance metrics were synced from an external platform. */
    PERFORMANCE_SYNCED("dm.analytics.performance-synced.v1"),

    // ── Audit / transparency ──────────────────────────────────────────────────
    /** An AI action was recorded to the transparency log. */
    AI_ACTION_RECORDED("dm.audit.ai-action-recorded.v1"),
    /** A business intake was submitted. */
    INTAKE_SUBMITTED("dm.audit.intake-submitted.v1"),
    /** A 30-day marketing strategy was generated. */
    STRATEGY_GENERATED("dm.audit.strategy-generated.v1"),
    /** A budget recommendation was approved. */
    BUDGET_APPROVED("dm.audit.budget-approved.v1"),
    /** A kill switch was activated. */
    KILL_SWITCH_ACTIVATED("dm.audit.kill-switch-activated.v1"),
    /** A kill switch was deactivated. */
    KILL_SWITCH_DEACTIVATED("dm.audit.kill-switch-deactivated.v1");

    private final String schemaId;

    DmEventType(String schemaId) {
        this.schemaId = schemaId;
    }

    /**
     * Returns the canonical schema ID for this event type.
     * Format: {@code dm.<domain>.<action>.v<version>}.
     *
     * @return never {@code null} or blank
     */
    public String getSchemaId() {
        return schemaId;
    }
}
