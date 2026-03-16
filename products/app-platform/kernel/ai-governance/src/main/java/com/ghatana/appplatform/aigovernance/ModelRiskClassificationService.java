package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Assigns a risk tier to each ML model based on a questionnaire covering
 *              client impact, regulatory implications, decision reversibility, and
 *              the degree of human-in-the-loop oversight. Tiers:
 *                TIER_1 – Informational / analytical (low stakes).
 *                TIER_2 – Decision-support (human must confirm before action).
 *                TIER_3 – Automated decision (highest scrutiny, mandatory HITL safeguards).
 *              Tier determines: validation rigour, approval chain depth, monitoring
 *              frequency, and explainability requirements.  Satisfies STORY-K09-014.
 * @doc.layer   Kernel
 * @doc.pattern Questionnaire scoring; tier boundary rules; ON CONFLICT DO UPDATE;
 *              EventPort; reclassificationCounter.
 */
public class ModelRiskClassificationService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          classificationsCounter;
    private final Counter          reclassificationsCounter;

    public ModelRiskClassificationService(HikariDataSource dataSource, Executor executor,
                                           EventPort eventPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.eventPort               = eventPort;
        this.classificationsCounter   = Counter.builder("ai.risk.classifications_total").register(registry);
        this.reclassificationsCounter = Counter.builder("ai.risk.reclassifications_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Enums / Records ─────────────────────────────────────────────────────

    public enum RiskTier { TIER_1, TIER_2, TIER_3 }

    /**
     * Questionnaire answers — each score field is 1 (low risk) to 3 (high risk).
     *   clientImpactScore:    1=internal analytics / 2=limited client impact / 3=direct client decisions.
     *   regulatoryScore:      1=none / 2=indirectly regulated / 3=directly regulated (MiFID, Basel).
     *   reversibilityScore:   1=easily reversed / 2=partially reversible / 3=irreversible.
     *   automationScore:      1=human reviews always / 2=human reviews exceptions / 3=fully automated.
     */
    public record RiskQuestionnaire(int clientImpactScore, int regulatoryScore,
                                     int reversibilityScore, int automationScore) {
        public RiskQuestionnaire {
            for (int s : new int[]{clientImpactScore, regulatoryScore, reversibilityScore, automationScore}) {
                if (s < 1 || s > 3) throw new IllegalArgumentException("Score must be 1-3, got " + s);
            }
        }
    }

    public record RiskClassification(String classificationId, String modelId, RiskTier tier,
                                      int totalScore, RiskQuestionnaire questionnaire,
                                      String assessedBy, LocalDateTime assessedAt) {}

    public record TierRequirements(RiskTier tier, String validationRigour,
                                    int approvalChainDepth, String monitoringFrequency,
                                    boolean explainabilityRequired, boolean hitlRequired) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RiskClassification> classify(String modelId, RiskQuestionnaire questionnaire,
                                                 String assessedBy) {
        return Promise.ofBlocking(executor, () -> {
            int score = questionnaire.clientImpactScore() + questionnaire.regulatoryScore()
                        + questionnaire.reversibilityScore() + questionnaire.automationScore();
            RiskTier tier = determineTier(score, questionnaire);

            Optional<RiskClassification> existing = loadClassification(modelId);
            boolean isReclassification = existing.isPresent()
                    && existing.get().tier() != tier;

            String classificationId = UUID.randomUUID().toString();
            RiskClassification classification = new RiskClassification(
                    classificationId, modelId, tier, score, questionnaire, assessedBy, LocalDateTime.now());
            persist(classification);

            if (isReclassification) {
                reclassificationsCounter.increment();
                eventPort.publish("model-governance", "ModelRiskReclassified",
                        Map.of("modelId", modelId,
                               "previousTier", existing.get().tier().name(),
                               "newTier", tier.name()));
            } else {
                classificationsCounter.increment();
            }

            return classification;
        });
    }

    public Promise<Optional<RiskClassification>> getClassification(String modelId) {
        return Promise.ofBlocking(executor, () -> loadClassification(modelId));
    }

    public TierRequirements requirementsFor(RiskTier tier) {
        return switch (tier) {
            case TIER_1 -> new TierRequirements(tier,
                    "Basic unit tests + domain review", 1,
                    "WEEKLY", false, false);
            case TIER_2 -> new TierRequirements(tier,
                    "Full validation suite + fairness audit", 2,
                    "DAILY", true, true);
            case TIER_3 -> new TierRequirements(tier,
                    "Regulatory validation + bias + adversarial testing", 3,
                    "HOURLY", true, true);
        };
    }

    // ─── Scoring logic ────────────────────────────────────────────────────────

    /**
     * Tier boundary: total 4-6 = TIER_1, 7-9 = TIER_2, 10-12 = TIER_3.
     * Hard override: any single axis scoring 3 AND automationScore >= 2 → TIER_3.
     */
    private RiskTier determineTier(int total, RiskQuestionnaire q) {
        boolean hardTier3 = q.automationScore() >= 2
                && (q.clientImpactScore() == 3 || q.regulatoryScore() == 3
                    || q.reversibilityScore() == 3);
        if (hardTier3) return RiskTier.TIER_3;
        if (total >= 10)  return RiskTier.TIER_3;
        if (total >= 7)   return RiskTier.TIER_2;
        return RiskTier.TIER_1;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void persist(RiskClassification r) throws SQLException {
        String sql = """
                INSERT INTO model_risk_classifications
                    (classification_id, model_id, tier, total_score,
                     client_impact_score, regulatory_score, reversibility_score,
                     automation_score, assessed_by, assessed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (model_id) DO UPDATE
                    SET classification_id=EXCLUDED.classification_id,
                        tier=EXCLUDED.tier,
                        total_score=EXCLUDED.total_score,
                        client_impact_score=EXCLUDED.client_impact_score,
                        regulatory_score=EXCLUDED.regulatory_score,
                        reversibility_score=EXCLUDED.reversibility_score,
                        automation_score=EXCLUDED.automation_score,
                        assessed_by=EXCLUDED.assessed_by,
                        assessed_at=EXCLUDED.assessed_at
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.classificationId()); ps.setString(2, r.modelId());
            ps.setString(3, r.tier().name());       ps.setInt(4, r.totalScore());
            ps.setInt(5, r.questionnaire().clientImpactScore());
            ps.setInt(6, r.questionnaire().regulatoryScore());
            ps.setInt(7, r.questionnaire().reversibilityScore());
            ps.setInt(8, r.questionnaire().automationScore());
            ps.setString(9, r.assessedBy());
            ps.executeUpdate();
        }
    }

    private Optional<RiskClassification> loadClassification(String modelId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM model_risk_classifications WHERE model_id=?")) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new RiskClassification(
                        rs.getString("classification_id"), rs.getString("model_id"),
                        RiskTier.valueOf(rs.getString("tier")), rs.getInt("total_score"),
                        new RiskQuestionnaire(rs.getInt("client_impact_score"),
                                rs.getInt("regulatory_score"), rs.getInt("reversibility_score"),
                                rs.getInt("automation_score")),
                        rs.getString("assessed_by"),
                        rs.getObject("assessed_at", LocalDateTime.class)));
            }
        }
    }
}
