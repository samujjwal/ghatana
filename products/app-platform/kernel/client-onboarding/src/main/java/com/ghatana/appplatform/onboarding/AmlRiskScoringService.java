package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Calculate AML risk score (0–100) for onboarding clients.
 *              Factors: nationality (high-risk countries), PEP status, occupation,
 *              expected transaction volume, account type, source of funds.
 *              Score determines monitoring frequency and EDD requirement.
 *              Uses K-03 T2 rules + K-09 AI governance for ML scoring.
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-007: AML risk scoring engine
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS onboarding_risk_scores (
 *   score_id           TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   instance_id        TEXT NOT NULL,
 *   client_id          TEXT NOT NULL,
 *   rules_score        INT NOT NULL,
 *   ml_score           INT,
 *   final_score        INT NOT NULL,
 *   risk_tier          TEXT NOT NULL,  -- LOW | MEDIUM | HIGH
 *   nationality_factor INT NOT NULL,
 *   pep_factor         INT NOT NULL,
 *   occupation_factor  INT NOT NULL,
 *   volume_factor      INT NOT NULL,
 *   funds_factor       INT NOT NULL,
 *   edd_required       BOOLEAN NOT NULL,
 *   monitoring_freq    TEXT NOT NULL,  -- ANNUAL | SEMI_ANNUAL | QUARTERLY
 *   scored_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class AmlRiskScoringService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface RulesEnginePort {
        /** Evaluate K-03 T2 risk rules and return sub-scores keyed by factor name. */
        Map<String, Integer> evaluateRules(RiskProfile profile) throws Exception;
    }

    public interface MlScoringPort {
        /** Invoke K-09 AI governance ML model and return a 0–100 score. -1 if unavailable. */
        int score(RiskProfile profile) throws Exception;
    }

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum RiskTier { LOW, MEDIUM, HIGH }
    public enum MonitoringFrequency { ANNUAL, SEMI_ANNUAL, QUARTERLY }

    public record RiskProfile(
        String clientId,
        String nationality,
        boolean isPep,
        String occupationCategory,
        String accountType,
        long expectedMonthlyTransactionVolumeCents,
        String sourceOfFunds,
        List<String> countriesOfOperation
    ) {}

    public record RiskScore(
        String scoreId,
        String instanceId,
        String clientId,
        int rulesScore,
        int mlScore,
        int finalScore,
        RiskTier riskTier,
        int nationalityFactor,
        int pepFactor,
        int occupationFactor,
        int volumeFactor,
        int fundsFactor,
        boolean eddRequired,
        MonitoringFrequency monitoringFrequency,
        String scoredAt
    ) {}

    // ── Risk tier thresholds ──────────────────────────────────────────────────

    private static final int LOW_THRESHOLD    = 40;
    private static final int MEDIUM_THRESHOLD = 70;

    // ── High-risk country list (FATF and OFAC designations) ──────────────────
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "AF", "BY", "CF", "CG", "CD", "CU", "ER", "ET", "GN", "GW", "IR", "IQ",
        "LB", "LY", "ML", "MM", "NI", "KP", "PK", "RU", "RW", "SD", "SO", "SS",
        "SY", "TJ", "TM", "TT", "UZ", "VE", "YE", "ZW"
    );

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final RulesEnginePort rulesEngine;
    private final MlScoringPort mlScoring;
    private final EventPublishPort eventPublish;
    private final Executor executor;
    private final Counter highRiskCounter;
    private final Counter eddRequiredCounter;
    private final Counter mlScoredCounter;

    public AmlRiskScoringService(
        javax.sql.DataSource ds,
        RulesEnginePort rulesEngine,
        MlScoringPort mlScoring,
        EventPublishPort eventPublish,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds           = ds;
        this.rulesEngine  = rulesEngine;
        this.mlScoring    = mlScoring;
        this.eventPublish = eventPublish;
        this.executor     = executor;
        this.highRiskCounter   = Counter.builder("onboarding.risk.high_risk").register(registry);
        this.eddRequiredCounter = Counter.builder("onboarding.risk.edd_required").register(registry);
        this.mlScoredCounter   = Counter.builder("onboarding.risk.ml_scored").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Calculate the AML risk score for a client profile.
     * Blends rules-based score (primary) with ML score (advisory, 30% weight if available).
     */
    public Promise<RiskScore> score(String instanceId, RiskProfile profile) {
        return Promise.ofBlocking(executor, () -> {
            // Rules-based scoring
            Map<String, Integer> factors = rulesEngine.evaluateRules(profile);

            int nationalityFactor = nationalityScore(profile.nationality(), profile.countriesOfOperation());
            int pepFactor         = profile.isPep() ? 30 : 0;
            int occupationFactor  = factors.getOrDefault("occupation", occupationScore(profile.occupationCategory()));
            int volumeFactor      = factors.getOrDefault("volume", volumeScore(profile.expectedMonthlyTransactionVolumeCents()));
            int fundsFactor       = factors.getOrDefault("sourceOfFunds", fundsScore(profile.sourceOfFunds()));

            int rulesScore = Math.min(100, nationalityFactor + pepFactor + occupationFactor + volumeFactor + fundsFactor);

            // ML score (advisory)
            int mlScore = -1;
            try {
                mlScore = mlScoring.score(profile);
                if (mlScore >= 0) mlScoredCounter.increment();
            } catch (Exception ignored) {}

            // Blend
            int finalScore = (mlScore >= 0)
                ? (int) (rulesScore * 0.70 + mlScore * 0.30)
                : rulesScore;
            finalScore = Math.min(100, Math.max(0, finalScore));

            RiskTier tier = tierFor(finalScore);
            boolean eddRequired = finalScore >= MEDIUM_THRESHOLD || profile.isPep();
            MonitoringFrequency monFreq = monitoringFrequency(tier);

            if (tier == RiskTier.HIGH) highRiskCounter.increment();
            if (eddRequired) eddRequiredCounter.increment();

            String scoreId = persist(instanceId, profile, rulesScore, mlScore, finalScore,
                tier, nationalityFactor, pepFactor, occupationFactor, volumeFactor, fundsFactor,
                eddRequired, monFreq);

            // Publish RiskScoreCalculated event
            eventPublish.publish("RiskScoreCalculated", Map.of(
                "instanceId", instanceId, "clientId", profile.clientId(),
                "finalScore", finalScore, "riskTier", tier.name(), "eddRequired", eddRequired
            ));

            return new RiskScore(scoreId, instanceId, profile.clientId(), rulesScore, mlScore, finalScore,
                tier, nationalityFactor, pepFactor, occupationFactor, volumeFactor, fundsFactor,
                eddRequired, monFreq, java.time.Instant.now().toString());
        });
    }

    // ── Private scoring helpers ───────────────────────────────────────────────

    private int nationalityScore(String nationality, List<String> countriesOfOperation) {
        boolean highRisk = HIGH_RISK_COUNTRIES.contains(nationality)
            || countriesOfOperation.stream().anyMatch(HIGH_RISK_COUNTRIES::contains);
        return highRisk ? 25 : 0;
    }

    private int occupationScore(String occupationCategory) {
        if (occupationCategory == null) return 5;
        return switch (occupationCategory.toUpperCase()) {
            case "POLITICIAN", "GOVERNMENT_OFFICIAL" -> 20;
            case "CASH_INTENSIVE_BUSINESS"           -> 15;
            case "LAWYER", "ACCOUNTANT"              -> 10;
            case "FINANCIAL_SERVICES"                -> 8;
            default                                  -> 3;
        };
    }

    private int volumeScore(long monthlyVolumeCents) {
        if (monthlyVolumeCents > 100_000_000_00L) return 20; // > £100M
        if (monthlyVolumeCents > 10_000_000_00L)  return 12; // > £10M
        if (monthlyVolumeCents > 1_000_000_00L)   return 6;  // > £1M
        return 2;
    }

    private int fundsScore(String sourceOfFunds) {
        if (sourceOfFunds == null) return 10;
        return switch (sourceOfFunds.toUpperCase()) {
            case "BUSINESS_INCOME"  -> 2;
            case "EMPLOYMENT"       -> 2;
            case "INHERITANCE"      -> 8;
            case "LOAN"             -> 10;
            case "CRYPTO"           -> 15;
            case "UNKNOWN"          -> 20;
            default                 -> 5;
        };
    }

    private RiskTier tierFor(int score) {
        if (score < LOW_THRESHOLD)    return RiskTier.LOW;
        if (score < MEDIUM_THRESHOLD) return RiskTier.MEDIUM;
        return RiskTier.HIGH;
    }

    private MonitoringFrequency monitoringFrequency(RiskTier tier) {
        return switch (tier) {
            case LOW    -> MonitoringFrequency.ANNUAL;
            case MEDIUM -> MonitoringFrequency.SEMI_ANNUAL;
            case HIGH   -> MonitoringFrequency.QUARTERLY;
        };
    }

    private String persist(String instanceId, RiskProfile profile, int rulesScore, int mlScore, int finalScore,
        RiskTier tier, int nat, int pep, int occ, int vol, int funds, boolean edd, MonitoringFrequency freq
    ) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO onboarding_risk_scores " +
                 "(instance_id, client_id, rules_score, ml_score, final_score, risk_tier, " +
                 "nationality_factor, pep_factor, occupation_factor, volume_factor, funds_factor, " +
                 "edd_required, monitoring_freq) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING score_id"
             )) {
            ps.setString(1, instanceId);
            ps.setString(2, profile.clientId());
            ps.setInt(3, rulesScore);
            ps.setInt(4, mlScore);
            ps.setInt(5, finalScore);
            ps.setString(6, tier.name());
            ps.setInt(7, nat);
            ps.setInt(8, pep);
            ps.setInt(9, occ);
            ps.setInt(10, vol);
            ps.setInt(11, funds);
            ps.setBoolean(12, edd);
            ps.setString(13, freq.name());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }
}
