/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.billing;

import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.List;

/**
 * PHR-specific billing enrichment contract.
 *
 * <p>Extends {@link LedgerPostingService} with healthcare domain concepts —
 * insurance eligibility checks, co-payment calculation, claim-level posting,
 * and Nepal NHSF code mapping — without exposing these to the Finance module.
 *
 * <p>The PHR {@code BillingService} calls this interface to perform
 * healthcare-specific billing operations. An adapter in the PHR product
 * wires a {@link LedgerPostingService} implementation from Finance
 * alongside its own healthcare enrichment logic.
 *
 * @doc.type interface
 * @doc.purpose Healthcare billing extension — PHR-specific enrichment on top of LedgerPostingService
 * @doc.layer platform
 * @doc.pattern Port
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public interface HealthcareBillingExtension {

    /**
     * Checks whether the given patient has active insurance coverage for the
     * requested service code at the specified point in time.
     *
     * @param patientId    the patient identifier
     * @param insurerId    the insurer identifier (NHSF code or private insurer ID)
     * @param serviceCode  the CPT or national billing code for the service
     * @return a Promise wrapping the eligibility result
     */
    Promise<EligibilityResult> checkInsuranceEligibility(
            String patientId, String insurerId, String serviceCode);

    /**
     * Calculates the patient's co-payment and insurer share for a given charge.
     *
     * @param patientId    the patient identifier
     * @param insurerId    the insurer identifier
     * @param chargeAmount the gross charge amount
     * @param currency     ISO 4217 currency code (e.g. {@code "NPR"})
     * @return a Promise wrapping the co-payment split
     */
    Promise<CoPaymentSplit> calculateCoPayment(
            String patientId, String insurerId, BigDecimal chargeAmount, String currency);

    /**
     * Posts all billing transactions for a closed encounter to the ledger.
     *
     * <p>Implementations must:
     * <ol>
     *   <li>Calculate the insurer share and patient co-payment.</li>
     *   <li>Create a {@link BillingTransaction} for the insurer settlement.</li>
     *   <li>Create a {@link BillingTransaction} for the patient co-payment (if any).</li>
     *   <li>Call {@link LedgerPostingService#postTransaction} for each transaction.</li>
     *   <li>Return stable ledger entry IDs for reconciliation.</li>
     * </ol>
     *
     * @param encounterId  the encounter identifier
     * @param patientId    the patient identifier
     * @param tenantId     the tenant context
     * @return a Promise wrapping the resulting ledger entry IDs
     */
    Promise<List<String>> postEncounterToLedger(
            String encounterId, String patientId, String tenantId);

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Result of an insurance eligibility check.
     *
     * @param eligible      {@code true} when the patient has active coverage
     * @param coverageType  human-readable coverage type (e.g. "NHSF Basic", "Private Gold")
     * @param coveragePct   percentage of the charge covered by insurance (0.0–1.0)
     * @param maxCoverage   maximum covered amount (null means no cap)
     * @param reason        reason for ineligibility, or {@code null} when eligible
     */
    record EligibilityResult(
            boolean eligible,
            String coverageType,
            double coveragePct,
            BigDecimal maxCoverage,
            String reason
    ) {
        public static EligibilityResult eligible(String coverageType, double coveragePct,
                                                  BigDecimal maxCoverage) {
            return new EligibilityResult(true, coverageType, coveragePct, maxCoverage, null);
        }

        public static EligibilityResult ineligible(String reason) {
            return new EligibilityResult(false, null, 0.0, null, reason);
        }
    }

    /**
     * The split of a charge between the insurer and the patient.
     *
     * @param insurerAmount  the amount covered by insurance
     * @param patientAmount  the amount owed by the patient (co-payment)
     * @param currency       ISO 4217 currency code
     */
    record CoPaymentSplit(
            BigDecimal insurerAmount,
            BigDecimal patientAmount,
            String currency
    ) {
        public BigDecimal total() {
            return insurerAmount.add(patientAmount);
        }
    }
}
