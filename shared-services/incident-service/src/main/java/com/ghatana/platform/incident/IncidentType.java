/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

/**
 * Structured taxonomy of security incidents affecting the agent platform.
 *
 * <p>Used to classify incidents at creation time so that playbooks, notification
 * routes, and SLA timers can be selected automatically. Values are ordered
 * roughly by severity from lowest to highest.
 *
 * @doc.type enum
 * @doc.purpose Classify platform security incidents by type for automated routing
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum IncidentType {

    /** An agent violated a data-governance policy (consent, purpose, minimisation). */
    DATA_GOVERNANCE_VIOLATION,

    /** An agent attempted or succeeded in accessing data outside its authorised scope. */
    UNAUTHORISED_DATA_ACCESS,

    /** Prompt-injection attack detected in agent inputs or tool outputs. */
    PROMPT_INJECTION,

    /** Agent egress exceeded configured byte or rate thresholds. */
    EGRESS_ANOMALY,

    /** Agent credential was compromised, expired unexpectedly, or misused. */
    CREDENTIAL_COMPROMISE,

    /** Agent action denied by policy engine with high risk score. */
    POLICY_VIOLATION,

    /** Agent exhibiting unexpected behaviour pattern (model drift, hallucination attack). */
    ANOMALOUS_BEHAVIOUR,

    /** Infrastructure-level security event (e.g. TLS failure, key rotation failure). */
    INFRASTRUCTURE_SECURITY
}
