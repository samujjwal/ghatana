package com.ghatana.digitalmarketing.domain.command;

/**
 * All MVP DMOS command types.
 *
 * <p>Commands drive durable, compensable state changes through connectors
 * and external platforms. Each command maps to exactly one handler and
 * one (or more) compensating command type.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS command taxonomy for the command store (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern CQRS, Command
 */
public enum DmCommandType {

    // ── Campaign commands ────────────────────────────────────────────────────
    CAMPAIGN_CREATE,
    CAMPAIGN_UPDATE,
    CAMPAIGN_PAUSE,
    CAMPAIGN_RESUME,
    CAMPAIGN_DELETE,

    // ── Ad group commands ────────────────────────────────────────────────────
    AD_GROUP_CREATE,
    AD_GROUP_UPDATE,
    AD_GROUP_PAUSE,

    // ── Budget commands ──────────────────────────────────────────────────────
    BUDGET_ADJUST,
    BUDGET_PAUSE,

    // ── Connector / OAuth commands ───────────────────────────────────────────
    CONNECTOR_OAUTH_CONNECT,
    CONNECTOR_OAUTH_REFRESH,
    CONNECTOR_SYNC_PERFORMANCE,
    GOOGLE_ADS_CAMPAIGN_CREATE,
    GOOGLE_ADS_CAMPAIGN_ROLLBACK,

    // ── Lead and CRM commands ────────────────────────────────────────────────
    LEAD_CAPTURE_REGISTER,
    LEAD_SCORE_COMPUTE,
    LEAD_EXPORT_TO_CRM,

    // ── Landing page commands ────────────────────────────────────────────────
    LANDING_PAGE_PUBLISH,
    LANDING_PAGE_UNPUBLISH,

    // ── Email commands ───────────────────────────────────────────────────────
    EMAIL_SEND,
    EMAIL_EXPORT,

    // ── Analytics commands ───────────────────────────────────────────────────
    ANALYTICS_EVENT_INGEST,
    ANALYTICS_REPORT_GENERATE,

    // ── Safety and control commands ──────────────────────────────────────────
    KILL_SWITCH_ENGAGE,
    KILL_SWITCH_DISENGAGE,
    ROLLBACK_INITIATE,

    // ── Audit command ────────────────────────────────────────────────────────
    AUDIT_RECORD
}
