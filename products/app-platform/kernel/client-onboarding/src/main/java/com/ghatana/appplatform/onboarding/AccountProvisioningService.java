package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * @doc.type    Service
 * @doc.purpose Saga-based account provisioning following KYC approval.
 *              Steps (ordered, with compensation on failure):
 *              1. Create IAM user (K-01)
 *              2. Create cash ledger accounts per currency (K-16)
 *              3. Create securities account with CSD link
 *              4. Set trading limits (K-03)
 *              5. Enrol in transaction monitoring (D-07) and sanctions monitoring (D-14)
 *              Publishes AccountProvisioned event on success.
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Saga (sequential with compensation); HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-009: Account creation and provisioning saga
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS onboarding_provision_sagas (
 *   saga_id           TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id       TEXT NOT NULL UNIQUE,
 *   status            TEXT NOT NULL DEFAULT 'IN_PROGRESS',
 *   iam_user_id       TEXT,
 *   last_step         TEXT,
 *   failure_reason    TEXT,
 *   started_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   completed_at      TIMESTAMPTZ
 * );
 * CREATE TABLE IF NOT EXISTS onboarding_provision_accounts (
 *   account_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   saga_id           TEXT NOT NULL REFERENCES onboarding_provision_sagas(saga_id),
 *   account_type      TEXT NOT NULL,   -- CASH | SECURITIES
 *   currency          TEXT,
 *   external_ref      TEXT NOT NULL,
 *   created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class AccountProvisioningService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface IamPort {
        String createUser(String clientId, String email, String role) throws Exception;
        void deleteUser(String iamUserId) throws Exception;
    }

    public interface LedgerPort {
        /** Creates a cash account and returns external account reference. */
        String createCashAccount(String clientId, String currency) throws Exception;
        void closeCashAccount(String externalRef) throws Exception;
    }

    public interface CsdPort {
        String createSecuritiesAccount(String clientId, String iamUserId) throws Exception;
        void closeSecuritiesAccount(String externalRef) throws Exception;
    }

    public interface TradingLimitsPort {
        void setLimits(String clientId, String riskTier) throws Exception;
    }

    public interface TransactionMonitoringPort {
        /** D-07 enrolment. */
        void enrolTransactionMonitoring(String clientId, String monitoringFrequency) throws Exception;
    }

    public interface SanctionsMonitoringPort {
        /** D-14 continuous monitoring enrolment. */
        void enrolSanctionsMonitoring(String clientId) throws Exception;
    }

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum SagaStatus { IN_PROGRESS, COMPLETED, COMPENSATED, FAILED }

    public record ProvisionResult(
        String sagaId,
        String iamUserId,
        List<String> cashAccountRefs,
        String securitiesAccountRef,
        SagaStatus status
    ) {}

    public record ProvisionRequest(
        String instanceId,
        String clientId,
        String email,
        List<String> currencies,
        String riskTier,
        String monitoringFrequency
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final String ONBOARDING_ROLE = "CLIENT";

    private final javax.sql.DataSource ds;
    private final IamPort iam;
    private final LedgerPort ledger;
    private final CsdPort csd;
    private final TradingLimitsPort tradingLimits;
    private final TransactionMonitoringPort txMonitoring;
    private final SanctionsMonitoringPort sanctionsMonitoring;
    private final EventPublishPort eventPublish;
    private final Executor executor;
    private final Counter completedCounter;
    private final Counter compensatedCounter;
    private final Timer sagaTimer;

    public AccountProvisioningService(
        javax.sql.DataSource ds,
        IamPort iam,
        LedgerPort ledger,
        CsdPort csd,
        TradingLimitsPort tradingLimits,
        TransactionMonitoringPort txMonitoring,
        SanctionsMonitoringPort sanctionsMonitoring,
        EventPublishPort eventPublish,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.iam                 = iam;
        this.ledger              = ledger;
        this.csd                 = csd;
        this.tradingLimits       = tradingLimits;
        this.txMonitoring        = txMonitoring;
        this.sanctionsMonitoring = sanctionsMonitoring;
        this.eventPublish        = eventPublish;
        this.executor            = executor;
        this.completedCounter    = Counter.builder("onboarding.provision.completed").register(registry);
        this.compensatedCounter  = Counter.builder("onboarding.provision.compensated").register(registry);
        this.sagaTimer           = Timer.builder("onboarding.provision.duration").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Execute the account provisioning saga for an approved KYC instance.
     */
    public Promise<ProvisionResult> provision(ProvisionRequest req) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            String sagaId = initSaga(req.instanceId());

            String iamUserId = null;
            List<String> cashRefs = new ArrayList<>();
            String securitiesRef = null;

            try {
                // Step 1: Create IAM user
                updateStep(sagaId, "CREATE_IAM_USER");
                iamUserId = iam.createUser(req.clientId(), req.email(), ONBOARDING_ROLE);
                updateIamUser(sagaId, iamUserId);

                // Step 2: Create cash accounts per currency
                updateStep(sagaId, "CREATE_CASH_ACCOUNTS");
                for (String currency : req.currencies()) {
                    String ref = ledger.createCashAccount(req.clientId(), currency);
                    persistAccount(sagaId, "CASH", currency, ref);
                    cashRefs.add(ref);
                }

                // Step 3: Create securities account
                updateStep(sagaId, "CREATE_SECURITIES_ACCOUNT");
                securitiesRef = csd.createSecuritiesAccount(req.clientId(), iamUserId);
                persistAccount(sagaId, "SECURITIES", null, securitiesRef);

                // Step 4: Set trading limits
                updateStep(sagaId, "SET_TRADING_LIMITS");
                tradingLimits.setLimits(req.clientId(), req.riskTier());

                // Step 5: Enrol monitoring
                updateStep(sagaId, "ENROL_MONITORING");
                txMonitoring.enrolTransactionMonitoring(req.clientId(), req.monitoringFrequency());
                sanctionsMonitoring.enrolSanctionsMonitoring(req.clientId());

                completeSaga(sagaId);
                completedCounter.increment();
                sample.stop(sagaTimer);

                eventPublish.publish("AccountProvisioned", Map.of(
                    "instanceId", req.instanceId(), "clientId", req.clientId(),
                    "iamUserId", iamUserId, "sagaId", sagaId
                ));

                return new ProvisionResult(sagaId, iamUserId, cashRefs, securitiesRef, SagaStatus.COMPLETED);

            } catch (Exception e) {
                failSaga(sagaId, e.getMessage());
                compensate(iamUserId, cashRefs, securitiesRef);
                compensatedCounter.increment();
                sample.stop(sagaTimer);
                throw e;
            }
        });
    }

    // ── Saga persistence helpers ──────────────────────────────────────────────

    private String initSaga(String instanceId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO onboarding_provision_sagas (instance_id) VALUES (?) RETURNING saga_id"
             )) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void updateStep(String sagaId, String step) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE onboarding_provision_sagas SET last_step = ? WHERE saga_id = ?"
             )) {
            ps.setString(1, step); ps.setString(2, sagaId); ps.executeUpdate();
        }
    }

    private void updateIamUser(String sagaId, String iamUserId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE onboarding_provision_sagas SET iam_user_id = ? WHERE saga_id = ?"
             )) {
            ps.setString(1, iamUserId); ps.setString(2, sagaId); ps.executeUpdate();
        }
    }

    private void persistAccount(String sagaId, String type, String currency, String ref) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO onboarding_provision_accounts (saga_id, account_type, currency, external_ref) VALUES (?,?,?,?)"
             )) {
            ps.setString(1, sagaId); ps.setString(2, type);
            ps.setString(3, currency); ps.setString(4, ref);
            ps.executeUpdate();
        }
    }

    private void completeSaga(String sagaId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE onboarding_provision_sagas SET status = 'COMPLETED', completed_at = NOW() WHERE saga_id = ?"
             )) {
            ps.setString(1, sagaId); ps.executeUpdate();
        }
    }

    private void failSaga(String sagaId, String reason) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE onboarding_provision_sagas SET status = 'COMPENSATED', failure_reason = ? WHERE saga_id = ?"
             )) {
            ps.setString(1, reason); ps.setString(2, sagaId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ── Saga compensation ─────────────────────────────────────────────────────

    private void compensate(String iamUserId, List<String> cashRefs, String securitiesRef) {
        if (securitiesRef != null) {
            try { csd.closeSecuritiesAccount(securitiesRef); } catch (Exception ignored) {}
        }
        for (String ref : cashRefs) {
            try { ledger.closeCashAccount(ref); } catch (Exception ignored) {}
        }
        if (iamUserId != null) {
            try { iam.deleteUser(iamUserId); } catch (Exception ignored) {}
        }
    }
}
