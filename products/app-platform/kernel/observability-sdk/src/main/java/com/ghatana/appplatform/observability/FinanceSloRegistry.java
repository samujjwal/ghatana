package com.ghatana.appplatform.observability;

import com.ghatana.platform.observability.SloChecker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

/**
 * Finance-domain SLO registry that pre-wires {@link SloChecker} instances with
 * the thresholds and rolling windows appropriate for financial operations.
 *
 * <p>Two SLO domains are tracked:
 * <ul>
 *   <li><strong>Ledger</strong> – 99.99 % success rate over a 5-minute window.
 *   <li><strong>Auth (IAM)</strong> – 99.99 % success rate over a 5-minute window.
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * FinanceSloRegistry slos = new FinanceSloRegistry(meterRegistry);
 *
 * // on every posting attempt:
 * slos.recordLedgerSuccess("tenant-abc");
 * // or:
 * slos.recordLedgerFailure("tenant-abc", "HashChainError");
 *
 * // periodic or on-demand check:
 * SloChecker.SloStatus status = slos.checkLedgerSlo("tenant-abc");
 * if (status.isBreached()) { alert(); }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Finance-domain SLO configuration; wraps platform SloChecker with domain thresholds
 * @doc.layer product
 * @doc.pattern Facade (wraps platform SloChecker per domain)
 */
public final class FinanceSloRegistry {

    /** 99.99 % success rate — financial ledger and auth SLO target. */
    public static final double FOUR_NINES = 0.9999;

    /** Rolling window for SLO evaluation. */
    public static final Duration SLO_WINDOW = Duration.ofMinutes(5);

    private final SloChecker ledgerSlo;
    private final SloChecker authSlo;

    /**
     * Creates SLO checkers for all finance domains.
     *
     * @param registry Micrometer registry supplied at module startup
     */
    public FinanceSloRegistry(MeterRegistry registry) {
        this.ledgerSlo = new SloChecker(registry, SLO_WINDOW, FOUR_NINES);
        this.authSlo   = new SloChecker(registry, SLO_WINDOW, FOUR_NINES);
    }

    // --- Ledger SLO ---

    /** Record a successful ledger posting for a tenant. */
    public void recordLedgerSuccess(String tenantId) {
        ledgerSlo.recordSuccess(tenantId, "ledger_posting");
    }

    /** Record a failed ledger posting for a tenant. */
    public void recordLedgerFailure(String tenantId, String errorType) {
        ledgerSlo.recordFailure(tenantId, "ledger_posting", errorType);
    }

    /** Check the current ledger SLO status for a tenant. */
    public SloChecker.SloStatus checkLedgerSlo(String tenantId) {
        return ledgerSlo.checkSlo(tenantId);
    }

    // --- Auth SLO ---

    /** Record a successful token issuance for a tenant. */
    public void recordAuthSuccess(String tenantId) {
        authSlo.recordSuccess(tenantId, "token_issuance");
    }

    /** Record a failed auth attempt for a tenant. */
    public void recordAuthFailure(String tenantId, String errorType) {
        authSlo.recordFailure(tenantId, "token_issuance", errorType);
    }

    /** Check the current auth SLO status for a tenant. */
    public SloChecker.SloStatus checkAuthSlo(String tenantId) {
        return authSlo.checkSlo(tenantId);
    }
}
