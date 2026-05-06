package com.ghatana.digitalmarketing.persistence.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.strategy.MarketingStrategyRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.strategy.CampaignPlan;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.digitalmarketing.domain.strategy.StrategyChannel;
import com.ghatana.digitalmarketing.domain.strategy.StrategyGoal;
import com.ghatana.digitalmarketing.domain.strategy.StrategyStatus;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link MarketingStrategyRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics (INSERT … ON CONFLICT DO UPDATE) for idempotent saves.
 * Serializes StrategyGoal and CampaignPlan lists as JSONB.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS marketing strategy persistence
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresMarketingStrategyRepository implements MarketingStrategyRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresMarketingStrategyRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_marketing_strategies " +
        "  (strategy_id, workspace_id, status, goals, channel_plans, budget_cap, " +
        "   rationale, assumptions, measurement_plan, content_plan, model_version, " +
        "   generated_at, generated_by, approved_at, approved_by) " +
        "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (strategy_id, workspace_id) DO UPDATE SET " +
        "  status = EXCLUDED.status, " +
        "  goals = EXCLUDED.goals, " +
        "  channel_plans = EXCLUDED.channel_plans, " +
        "  budget_cap = EXCLUDED.budget_cap, " +
        "  rationale = EXCLUDED.rationale, " +
        "  assumptions = EXCLUDED.assumptions, " +
        "  measurement_plan = EXCLUDED.measurement_plan, " +
        "  content_plan = EXCLUDED.content_plan, " +
        "  approved_at = EXCLUDED.approved_at, " +
        "  approved_by = EXCLUDED.approved_by";

    private static final String SELECT_BY_ID_SQL =
        "SELECT strategy_id, workspace_id, status, goals, channel_plans, budget_cap, " +
        "       rationale, assumptions, measurement_plan, content_plan, model_version, " +
        "       generated_at, generated_by, approved_at, approved_by " +
        "FROM dmos_marketing_strategies " +
        "WHERE strategy_id = ? AND workspace_id = ?";

    private static final String SELECT_LATEST_BY_WORKSPACE_SQL =
        "SELECT strategy_id, workspace_id, status, goals, channel_plans, budget_cap, " +
        "       rationale, assumptions, measurement_plan, content_plan, model_version, " +
        "       generated_at, generated_by, approved_at, approved_by " +
        "FROM dmos_marketing_strategies " +
        "WHERE workspace_id = ? " +
        "ORDER BY generated_at DESC, strategy_id DESC " +
        "LIMIT 1";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public PostgresMarketingStrategyRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<MarketingStrategy> save(MarketingStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, strategy.getStrategyId());
                stmt.setString(2, strategy.getWorkspaceId().getValue());
                stmt.setString(3, strategy.getStatus().name());
                stmt.setString(4, objectMapper.writeValueAsString(strategy.getGoals()));
                stmt.setString(5, objectMapper.writeValueAsString(strategy.getChannelPlans()));
                stmt.setDouble(6, strategy.getBudgetCap());
                stmt.setString(7, strategy.getRationale());
                stmt.setString(8, strategy.getAssumptions());
                stmt.setString(9, strategy.getMeasurementPlan());
                stmt.setString(10, strategy.getContentPlan());
                stmt.setString(11, strategy.getModelVersion());
                stmt.setTimestamp(12, Timestamp.from(strategy.getGeneratedAt()));
                stmt.setString(13, strategy.getGeneratedBy());
                stmt.setTimestamp(14, strategy.getApprovedAt() != null ? Timestamp.from(strategy.getApprovedAt()) : null);
                stmt.setString(15, strategy.getApprovedBy());
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] marketing strategy upserted: id={} workspace={} status={}",
                    strategy.getStrategyId(), strategy.getWorkspaceId().getValue(), strategy.getStatus());
                return strategy;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save strategy id={}: {}", strategy.getStrategyId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save marketing strategy: " + strategy.getStrategyId(), e);
            } catch (JsonProcessingException e) {
                LOG.error("[DMOS-PERSIST] failed to serialize strategy id={}: {}", strategy.getStrategyId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to serialize marketing strategy: " + strategy.getStrategyId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<MarketingStrategy>> findById(DmWorkspaceId workspaceId, String strategyId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(strategyId, "strategyId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, strategyId);
                stmt.setString(2, workspaceId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find strategy id={} workspace={}: {}",
                    strategyId, workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to find marketing strategy: " + strategyId, e);
            }
        });
    }

    @Override
    public Promise<Optional<MarketingStrategy>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_LATEST_BY_WORKSPACE_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find latest strategy for workspace={}: {}",
                    workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to find latest marketing strategy for workspace: " + workspaceId.getValue(), e);
            }
        });
    }

    private static MarketingStrategy mapRow(ResultSet rs) throws SQLException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        
        Instant generatedAt = rs.getTimestamp("generated_at").toInstant();
        Timestamp approvedAtTs = rs.getTimestamp("approved_at");
        Instant approvedAt = approvedAtTs != null ? approvedAtTs.toInstant() : null;

        List<StrategyGoal> goals = parseGoals(mapper, rs.getString("goals"));
        List<CampaignPlan> channelPlans = parseCampaignPlans(mapper, rs.getString("channel_plans"));

        return MarketingStrategy.builder()
            .strategyId(rs.getString("strategy_id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .status(StrategyStatus.valueOf(rs.getString("status")))
            .goals(goals)
            .channelPlans(channelPlans)
            .budgetCap(rs.getDouble("budget_cap"))
            .rationale(rs.getString("rationale"))
            .assumptions(rs.getString("assumptions"))
            .measurementPlan(rs.getString("measurement_plan"))
            .contentPlan(rs.getString("content_plan"))
            .modelVersion(rs.getString("model_version"))
            .generatedAt(generatedAt)
            .generatedBy(rs.getString("generated_by"))
            .approvedAt(approvedAt)
            .approvedBy(rs.getString("approved_by"))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<StrategyGoal> parseGoals(ObjectMapper mapper, String json) throws JsonProcessingException {
        List<?> rawList = mapper.readValue(json, List.class);
        List<StrategyGoal> goals = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof java.util.Map<?, ?>) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                goals.add(new StrategyGoal(
                    (String) map.get("goalType"),
                    (String) map.get("description"),
                    (String) map.get("targetMetric"),
                    (String) map.get("measurementMethod")
                ));
            }
        }
        return List.copyOf(goals);
    }

    @SuppressWarnings("unchecked")
    private static List<CampaignPlan> parseCampaignPlans(ObjectMapper mapper, String json) throws JsonProcessingException {
        List<?> rawList = mapper.readValue(json, List.class);
        List<CampaignPlan> plans = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof java.util.Map<?, ?>) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                Object keyMsgsRaw = map.get("keyMessages");
                Object keywordsRaw = map.get("targetKeywords");
                List<String> keyMessages = keyMsgsRaw instanceof List ? (List<String>) keyMsgsRaw : List.of();
                List<String> targetKeywords = keywordsRaw instanceof List ? (List<String>) keywordsRaw : List.of();
                plans.add(new CampaignPlan(
                    StrategyChannel.valueOf((String) map.get("channelType")),
                    (String) map.get("objective"),
                    ((Number) map.get("estimatedBudget")).intValue(),
                    keyMessages,
                    targetKeywords
                ));
            }
        }
        return List.copyOf(plans);
    }
}
