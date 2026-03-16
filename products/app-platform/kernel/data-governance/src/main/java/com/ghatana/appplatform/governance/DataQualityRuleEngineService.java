package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Data quality framework: define and execute quality rules against data assets.
 *              Rule types: COMPLETENESS (non-null %), UNIQUENESS (duplicate check), VALIDITY
 *              (regex/range), CONSISTENCY (cross-field), TIMELINESS (freshness). Rules executed
 *              via K-03 T2 sandboxed engine. QualityCheckResult: asset, rule, score 0-100,
 *              violations[]. Publishes QualityBreak event when score drops. Satisfies STORY-K08-006.
 * @doc.layer   Kernel
 * @doc.pattern K-03 T2 sandboxed quality rule execution; COMPLETENESS/UNIQUENESS/VALIDITY/
 *              CONSISTENCY/TIMELINESS rule types; quality score 0-100; breaks event; Counter.
 */
public class DataQualityRuleEngineService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final RuleExecutionPort rulePort;
    private final EventPort         eventPort;
    private final Counter           rulesExecutedCounter;
    private final Counter           breaksCounter;

    public DataQualityRuleEngineService(HikariDataSource dataSource, Executor executor,
                                         RuleExecutionPort rulePort, EventPort eventPort,
                                         MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.rulePort            = rulePort;
        this.eventPort           = eventPort;
        this.rulesExecutedCounter = Counter.builder("governance.dq.rules_executed_total").register(registry);
        this.breaksCounter        = Counter.builder("governance.dq.breaks_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** K-03 T2 sandboxed rule execution engine. */
    public interface RuleExecutionPort {
        RuleExecutionResult execute(String assetId, QualityRule rule);
    }

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Enums & Records ─────────────────────────────────────────────────────

    public enum RuleType { COMPLETENESS, UNIQUENESS, VALIDITY, CONSISTENCY, TIMELINESS }
    public enum Schedule  { ON_INSERT, HOURLY, DAILY, ON_DEMAND }

    public record QualityRule(String ruleId, String assetId, RuleType type, String name,
                               String expression, double threshold, Schedule schedule,
                               boolean active, LocalDateTime createdAt) {}

    public record RuleViolation(String field, String reason, Object violatingValue) {}

    public record RuleExecutionResult(double score, List<RuleViolation> violations) {}

    public record QualityCheckResult(String checkId, String assetId, String ruleId,
                                      RuleType ruleType, double score, double threshold,
                                      boolean passed, List<RuleViolation> violations,
                                      LocalDateTime executedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<QualityRule> defineRule(String assetId, RuleType type, String name,
                                            String expression, double threshold, Schedule schedule) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    INSERT INTO quality_rules
                        (rule_id, asset_id, rule_type, name, expression, threshold, schedule,
                         active, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, NOW())
                    ON CONFLICT (asset_id, name) DO UPDATE
                        SET expression=EXCLUDED.expression, threshold=EXCLUDED.threshold,
                            schedule=EXCLUDED.schedule
                    RETURNING *
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, assetId);
                ps.setString(3, type.name()); ps.setString(4, name);
                ps.setString(5, expression); ps.setDouble(6, threshold);
                ps.setString(7, schedule.name());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next(); return mapRuleRow(rs);
                }
            }
        });
    }

    public Promise<QualityCheckResult> executeRule(String assetId, String ruleId) {
        return Promise.ofBlocking(executor, () -> {
            QualityRule rule = loadRule(ruleId);
            RuleExecutionResult result = rulePort.execute(assetId, rule);

            boolean passed = result.score() >= rule.threshold();
            QualityCheckResult check = persistCheckResult(assetId, ruleId, rule.type(),
                    result.score(), rule.threshold(), passed, result.violations());
            rulesExecutedCounter.increment();

            if (!passed) {
                breaksCounter.increment();
                eventPort.publish("governance.dq.quality_break",
                        new QualityBreakEvent(assetId, ruleId, rule.name(), result.score(),
                                rule.threshold()));
            }
            return check;
        });
    }

    /** Execute all active rules for a given asset. */
    public Promise<List<QualityCheckResult>> executeAllRules(String assetId) {
        return Promise.ofBlocking(executor, () -> {
            List<QualityRule> rules = loadActiveRulesForAsset(assetId);
            List<QualityCheckResult> results = new ArrayList<>();
            for (QualityRule rule : rules) {
                results.add(executeRule(assetId, rule.ruleId()).get());
            }
            return results;
        });
    }

    public Promise<List<QualityRule>> listRules(String assetId) {
        return Promise.ofBlocking(executor, () -> loadActiveRulesForAsset(assetId));
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private QualityCheckResult persistCheckResult(String assetId, String ruleId, RuleType type,
                                                   double score, double threshold, boolean passed,
                                                   List<RuleViolation> violations) throws SQLException {
        String sql = """
                INSERT INTO quality_check_results
                    (check_id, asset_id, rule_id, rule_type, score, threshold, passed,
                     violation_count, executed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (asset_id, rule_id, executed_at) DO NOTHING
                RETURNING check_id, executed_at
                """;
        String checkId = UUID.randomUUID().toString();
        LocalDateTime executedAt = LocalDateTime.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, checkId); ps.setString(2, assetId); ps.setString(3, ruleId);
            ps.setString(4, type.name()); ps.setDouble(5, score); ps.setDouble(6, threshold);
            ps.setBoolean(7, passed); ps.setInt(8, violations.size());
            ps.executeQuery();
        }
        return new QualityCheckResult(checkId, assetId, ruleId, type, score, threshold, passed,
                violations, executedAt);
    }

    private QualityRule loadRule(String ruleId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM quality_rules WHERE rule_id=?")) {
            ps.setString(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Rule not found: " + ruleId);
                return mapRuleRow(rs);
            }
        }
    }

    private List<QualityRule> loadActiveRulesForAsset(String assetId) throws SQLException {
        List<QualityRule> rules = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM quality_rules WHERE asset_id=? AND active=TRUE")) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rules.add(mapRuleRow(rs));
            }
        }
        return rules;
    }

    private QualityRule mapRuleRow(ResultSet rs) throws SQLException {
        return new QualityRule(rs.getString("rule_id"), rs.getString("asset_id"),
                RuleType.valueOf(rs.getString("rule_type")), rs.getString("name"),
                rs.getString("expression"), rs.getDouble("threshold"),
                Schedule.valueOf(rs.getString("schedule")), rs.getBoolean("active"),
                rs.getObject("created_at", LocalDateTime.class));
    }

    record QualityBreakEvent(String assetId, String ruleId, String ruleName,
                              double actualScore, double threshold) {}
}
