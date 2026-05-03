package com.ghatana.digitalmarketing.domain.budget;

/**
 * Severity level for budget alerts.
 *
 * @doc.type class
 * @doc.purpose Categorizes budget alert severity (DMOS-F3-002)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmBudgetAlertLevel {
    INFO,
    WARNING,
    CRITICAL,
    EXHAUSTED
}
