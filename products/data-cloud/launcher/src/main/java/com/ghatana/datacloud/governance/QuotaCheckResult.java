package com.ghatana.datacloud.governance;

/**
 * Result of a tenant quota check.
 *
 * @doc.type class
 * @doc.purpose Represents the outcome of a tenant quota check, including allowance status and quota details.
 * @doc.layer product
 * @doc.pattern Record
 */
public record QuotaCheckResult(boolean allowed, String message, int quotaValue, int usedAmount) {

    public boolean isAllowed() { return allowed; }

    public static QuotaCheckResult permit() {
        return new QuotaCheckResult(true, null, 0, 0);
    }

    public static QuotaCheckResult reject(String message, int quotaValue, int usedAmount) {
        return new QuotaCheckResult(false, message, quotaValue, usedAmount);
    }
}
