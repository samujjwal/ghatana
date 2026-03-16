package com.ghatana.appplatform.ems.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Contextual bandit / RL agent that optimises child-order scheduling for
 *              VWAP/TWAP/IS strategies. State: remaining_quantity, elapsed_time_fraction,
 *              volume_pace, spread, book_imbalance. Action: participation_rate for next
 *              interval. Reward: negative implementation shortfall vs VWAP.
 *              Trained offline; ε-greedy exploration (ε=0.05). K-09 TIER_3 HITL required
 *              for live use; shadow mode available for evaluation. Human override via D-01.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner RlPolicyPort (K-09 TIER_3 PPO/DQN); inner
 *              ShadowModePort for champion-challenger evaluation; advisory-only; all
 *              live action decisions require explicit HITL approval gate.
 */
public class RlExecutionOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(RlExecutionOptimizerService.class);

    private static final double EPSILON_EXPLORATION = 0.05;  // ε-greedy in production
    private static final double SHADOW_MODE_DEFAULT  = true;  // start in shadow mode

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RlPolicyPort     rlPolicyPort;
    private final ShadowModePort   shadowModePort;
    private final Counter          actionCounter;
    private final Counter          overrideCounter;
    private final Counter          hitlApprovalCounter;

    public RlExecutionOptimizerService(HikariDataSource dataSource, Executor executor,
                                       RlPolicyPort rlPolicyPort, ShadowModePort shadowModePort,
                                       MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.rlPolicyPort        = rlPolicyPort;
        this.shadowModePort      = shadowModePort;
        this.actionCounter       = registry.counter("ems.rl.action.generated");
        this.overrideCounter     = registry.counter("ems.rl.action.override");
        this.hitlApprovalCounter = registry.counter("ems.rl.hitl.approved");
    }

    // ─── Inner ports (K-09 TIER_3) ────────────────────────────────────────────

    /**
     * K-09 TIER_3: PPO or DQN policy network. Always advisory-only; HITL gate required.
     */
    public interface RlPolicyPort {
        PolicyAction recommend(PolicyState state);
        boolean       isInShadowMode(String strategyId);
    }

    /**
     * Shadow mode port: records shadow action alongside champion action for A/B evaluation.
     */
    public interface ShadowModePort {
        void recordShadowDecision(String orderId, PolicyAction shadow, PolicyAction champion);
        double evaluateShadowImprovement(String strategyId);  // bps improvement if positive
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record PolicyState(
        String orderId,
        double remainingQuantity,
        double elapsedTimeFraction,    // 0.0 – 1.0
        double volumePace,             // actual / expected volume so far
        double spreadBps,
        double bookImbalance           // (bidQty - askQty) / (bidQty + askQty)
    ) {}

    public record PolicyAction(
        double participationRate,      // 0.0 – 1.0 fraction of interval volume to take
        boolean isAdvisory,            // always true (K-09)
        boolean requiresHitl,          // true for TIER_3 live trading
        String  rationale
    ) {}

    public record OptimizerDecision(
        String       orderId,
        PolicyAction action,
        boolean      shadowMode,
        boolean      hitlApproved,
        String       decisionId
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Request a participation rate recommendation for the next interval.
     * In shadow mode: records decision but does not apply it.
     * In live mode: requires HITL approval before application.
     */
    public Promise<OptimizerDecision> recommend(PolicyState state) {
        return Promise.ofBlocking(executor, () -> {
            boolean inShadow = rlPolicyPort.isInShadowMode(state.orderId());
            PolicyAction recommended = rlPolicyPort.recommend(state);
            actionCounter.increment();

            if (inShadow) {
                // Champion baseline: proportional slicing (50% participation)
                PolicyAction champion = new PolicyAction(0.50, true, false, "STATIC_BASELINE");
                shadowModePort.recordShadowDecision(state.orderId(), recommended, champion);
                log.debug("RL shadow mode orderId={} recommended_rate={}", state.orderId(), recommended.participationRate());
                OptimizerDecision decision = new OptimizerDecision(state.orderId(), champion, true, false, UUID.randomUUID().toString());
                persistDecision(decision, recommended);
                return decision;
            }

            // Live mode: K-09 TIER_3 — decision must wait for HITL approval
            // We persist as PENDING_REVIEW; the approval is recorded separately via recordHitlApproval()
            log.info("RL live recommendation orderId={} rate={} — awaiting HITL approval",
                     state.orderId(), recommended.participationRate());
            OptimizerDecision pending = new OptimizerDecision(state.orderId(), recommended, false, false, UUID.randomUUID().toString());
            persistDecision(pending, recommended);
            return pending;
        });
    }

    /**
     * Record HITL approval of an RL-recommended action (K-09 TIER_3 gate).
     */
    public Promise<Void> recordHitlApproval(String decisionId, String approvedBy,
                                             boolean approved, String notes) {
        return Promise.ofBlocking(executor, () -> {
            if (approved) hitlApprovalCounter.increment();
            updateHitlStatus(decisionId, approved, approvedBy, notes);
            log.info("K-09 TIER_3 HITL {} decisionId={} by={}", approved ? "APPROVED" : "REJECTED", decisionId, approvedBy);
            return null;
        });
    }

    /**
     * Human override: apply a manual participation rate (bypasses RL policy).
     */
    public Promise<Void> applyHumanOverride(String orderId, double participationRate,
                                             String overrideBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            overrideCounter.increment();
            persistHumanOverride(orderId, participationRate, overrideBy, reason);
            log.info("RL human override applied orderId={} rate={} by={}", orderId, participationRate, overrideBy);
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void persistDecision(OptimizerDecision d, PolicyAction recommended) {
        String sql = """
            INSERT INTO rl_decisions
                (decision_id, order_id, recommended_rate, applied_rate,
                 shadow_mode, hitl_required, hitl_approved, rationale, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (decision_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.decisionId());
            ps.setString(2, d.orderId());
            ps.setDouble(3, recommended.participationRate());
            ps.setDouble(4, d.action().participationRate());
            ps.setBoolean(5, d.shadowMode());
            ps.setBoolean(6, recommended.requiresHitl());
            ps.setBoolean(7, d.hitlApproved());
            ps.setString(8, recommended.rationale());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist RL decision orderId={}", d.orderId(), ex);
        }
    }

    private void updateHitlStatus(String decisionId, boolean approved, String by, String notes) {
        String sql = """
            UPDATE rl_decisions
            SET hitl_approved  = ?,
                hitl_actor     = ?,
                hitl_notes     = ?,
                hitl_at        = now()
            WHERE decision_id  = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, approved);
            ps.setString(2, by);
            ps.setString(3, notes);
            ps.setString(4, decisionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to update HITL status decisionId={}", decisionId, ex);
        }
    }

    private void persistHumanOverride(String orderId, double rate, String by, String reason) {
        String sql = """
            INSERT INTO rl_human_overrides
                (override_id, order_id, override_rate, overridden_by, reason, overridden_at)
            VALUES (?, ?, ?, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, orderId);
            ps.setDouble(3, rate);
            ps.setString(4, by);
            ps.setString(5, reason);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist RL human override orderId={}", orderId, ex);
        }
    }
}
