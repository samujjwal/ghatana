package com.ghatana.appplatform.compliance.service;

import com.ghatana.appplatform.compliance.domain.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Enforces share lock-in periods using BS calendar dates (D07-004, D07-005).
 *              Available-to-sell = current_position - sum(active_locked_qty).
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class LockInPeriodService {

    private static final Logger log = LoggerFactory.getLogger(LockInPeriodService.class);

    /** Injected store — adapter resolved outside. */
    private final LockInStore lockInStore;
    private final Executor executor;

    public LockInPeriodService(LockInStore lockInStore, Executor executor) {
        this.lockInStore = lockInStore;
        this.executor = executor;
    }

    /** Evaluate lock-in constraint for a SELL order. */
    public ComplianceCheckResult.RuleEvaluationDetail evaluate(ComplianceCheckRequest request) {
        try {
            String todayBs = lockInStore.getTodayBs();
            List<LockInRecord> lockIns = lockInStore
                    .findActive(request.clientId(), request.instrumentId(), todayBs)
                    .get();

            BigDecimal totalLocked = lockIns.stream()
                    .map(LockInRecord::lockedQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalLocked.compareTo(BigDecimal.ZERO) == 0) {
                return new ComplianceCheckResult.RuleEvaluationDetail(
                        "LOCK_IN_CHECK", "Lock-in Period Check", ComplianceStatus.PASS, null);
            }

            BigDecimal currentPosition = lockInStore.getCurrentPosition(
                    request.clientId(), request.instrumentId()).get();
            BigDecimal available = currentPosition.subtract(totalLocked);

            if (available.compareTo(request.quantity()) < 0) {
                String reason = String.format(
                        "Lock-in restriction: available qty=%s, locked=%s, requested=%s",
                        available, totalLocked, request.quantity());
                return new ComplianceCheckResult.RuleEvaluationDetail(
                        "LOCK_IN_CHECK", "Lock-in Period Check", ComplianceStatus.FAIL, reason);
            }

            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "LOCK_IN_CHECK", "Lock-in Period Check", ComplianceStatus.PASS, null);

        } catch (Exception e) {
            log.error("Lock-in check error: clientId={} instrumentId={}",
                    request.clientId(), request.instrumentId(), e);
            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "LOCK_IN_CHECK", "Lock-in Period Check", ComplianceStatus.FAIL,
                    "Lock-in check failed: " + e.getMessage());
        }
    }

    // ─── Inner port (allows wire-up without separate file) ───────────────────

    public interface LockInStore {
        String getTodayBs();
        Promise<List<LockInRecord>> findActive(String clientId, String instrumentId, String todayBs);
        Promise<BigDecimal> getCurrentPosition(String clientId, String instrumentId);
        Promise<Void> save(LockInRecord record);
        Promise<List<LockInRecord>> findByClient(String clientId);
    }
}
