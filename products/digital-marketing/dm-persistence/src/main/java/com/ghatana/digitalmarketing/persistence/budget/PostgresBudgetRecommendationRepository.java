package com.ghatana.digitalmarketing.persistence.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.BudgetChannelAllocation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendationStatus;
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
 * Production PostgreSQL adapter for {@link BudgetRecommendationRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics (INSERT … ON CONFLICT DO UPDATE) for idempotent saves.
 * Serializes BudgetChannelAllocation and daily caps as JSONB.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS budget recommendation persistence
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresBudgetRecommendationRepository implements BudgetRecommendationRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresBudgetRecommendationRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_budget_recommendations " +
        "  (recommendation_id, workspace_id, monthly_budget, channel_split, daily_caps, " +
        "   risk_level, rationale, assumptions, model_version, generated_at, generated_by) " +
        "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (recommendation_id, workspace_id) DO UPDATE SET " +
        "  monthly_budget = EXCLUDED.monthly_budget, " +
        "  channel_split = EXCLUDED.channel_split, " +
        "  daily_caps = EXCLUDED.daily_caps, " +
        "  risk_level = EXCLUDED.risk_level, " +
        "  rationale = EXCLUDED.rationale, " +
        "  assumptions = EXCLUDED.assumptions";

    private static final String SELECT_BY_ID_SQL =
        "SELECT recommendation_id, workspace_id, monthly_budget, channel_split, daily_caps, " +
        "       risk_level, rationale, assumptions, model_version, generated_at, generated_by " +
        "FROM dmos_budget_recommendations " +
        "WHERE recommendation_id = ? AND workspace_id = ?";

    private static final String SELECT_BY_RECOMMENDATION_ID_ONLY_SQL =
        "SELECT recommendation_id, workspace_id, monthly_budget, channel_split, daily_caps, " +
        "       risk_level, rationale, assumptions, model_version, generated_at, generated_by " +
        "FROM dmos_budget_recommendations " +
        "WHERE recommendation_id = ? " +
        "LIMIT 1";

    private static final String SELECT_LATEST_BY_WORKSPACE_SQL =
        "SELECT recommendation_id, workspace_id, monthly_budget, channel_split, daily_caps, " +
        "       risk_level, rationale, assumptions, model_version, generated_at, generated_by " +
        "FROM dmos_budget_recommendations " +
        "WHERE workspace_id = ? " +
        "ORDER BY generated_at DESC " +
        "LIMIT 1";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public PostgresBudgetRecommendationRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<BudgetRecommendation> save(BudgetRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, recommendation.getRecommendationId());
                stmt.setString(2, recommendation.getWorkspaceId().getValue());
                stmt.setDouble(3, recommendation.getTotalMonthlyCap());
                stmt.setString(4, objectMapper.writeValueAsString(recommendation.getChannelAllocations()));
                stmt.setString(5, objectMapper.writeValueAsString(buildDailyCapsMap(recommendation)));
                stmt.setInt(6, (int) (recommendation.getChangeThresholdPct() * 10)); // Store as 1-10 scale
                stmt.setString(7, recommendation.getRationale());
                stmt.setString(8, recommendation.getAssumptions());
                stmt.setString(9, recommendation.getModelVersion());
                stmt.setTimestamp(10, Timestamp.from(recommendation.getGeneratedAt()));
                stmt.setString(11, recommendation.getGeneratedBy());
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] budget recommendation upserted: id={} workspace={}",
                    recommendation.getRecommendationId(), recommendation.getWorkspaceId().getValue());
                return recommendation;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save recommendation id={}: {}",
                    recommendation.getRecommendationId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save budget recommendation: " + recommendation.getRecommendationId(), e);
            } catch (JsonProcessingException e) {
                LOG.error("[DMOS-PERSIST] failed to serialize recommendation id={}: {}",
                    recommendation.getRecommendationId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to serialize budget recommendation: " + recommendation.getRecommendationId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<BudgetRecommendation>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
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
                LOG.error("[DMOS-PERSIST] failed to find latest recommendation for workspace={}: {}",
                    workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to find latest budget recommendation for workspace: " + workspaceId.getValue(), e);
            }
        });
    }

    @Override
    public Promise<Optional<BudgetRecommendation>> findById(String recommendationId) {
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_RECOMMENDATION_ID_ONLY_SQL)) {
                stmt.setString(1, recommendationId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find recommendation id={}: {}",
                    recommendationId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find budget recommendation: " + recommendationId, e);
            }
        });
    }

    private static BudgetRecommendation mapRow(ResultSet rs) throws SQLException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Instant generatedAt = rs.getTimestamp("generated_at").toInstant();
        
        List<BudgetChannelAllocation> channelAllocations = parseChannelAllocations(mapper, rs.getString("channel_split"));
        
        return BudgetRecommendation.builder()
            .recommendationId(rs.getString("recommendation_id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .strategyId(null) // Not stored in current schema, can be added later
            .totalMonthlyCap(rs.getDouble("monthly_budget"))
            .changeThresholdPct(rs.getInt("risk_level") / 10.0)
            .channelAllocations(channelAllocations)
            .rationale(rs.getString("rationale"))
            .assumptions(rs.getString("assumptions"))
            .modelVersion(rs.getString("model_version"))
            .status(BudgetRecommendationStatus.DRAFT) // Status not stored in current schema
            .generatedAt(generatedAt)
            .generatedBy(rs.getString("generated_by"))
            .approvedAt(null)
            .approvedBy(null)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<BudgetChannelAllocation> parseChannelAllocations(ObjectMapper mapper, String json) throws JsonProcessingException {
        List<?> rawList = mapper.readValue(json, List.class);
        List<BudgetChannelAllocation> allocations = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof java.util.Map<?, ?>) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                String channelTypeRaw = (String) map.get("channelType");
                String channelType = channelTypeRaw != null ? channelTypeRaw : (String) map.get("channel");
                double recommendedAmount = map.get("recommendedAmount") != null
                    ? ((Number) map.get("recommendedAmount")).doubleValue()
                    : map.get("allocationPct") != null ? ((Number) map.get("allocationPct")).doubleValue() : 0.0;
                double dailyCap = map.get("dailyCap") != null ? ((Number) map.get("dailyCap")).doubleValue() : 0.0;
                String rationaleRaw = (String) map.get("rationale");
                String rationale = rationaleRaw != null ? rationaleRaw : "";
                allocations.add(new BudgetChannelAllocation(channelType != null ? channelType : "unknown", recommendedAmount, dailyCap, rationale));
            }
        }
        return List.copyOf(allocations);
    }

    private static java.util.Map<String, Object> buildDailyCapsMap(BudgetRecommendation recommendation) {
        java.util.Map<String, Object> dailyCaps = new java.util.HashMap<>();
        for (BudgetChannelAllocation allocation : recommendation.getChannelAllocations()) {
            dailyCaps.put(allocation.channelType(), allocation.dailyCap());
        }
        return dailyCaps;
    }
}
