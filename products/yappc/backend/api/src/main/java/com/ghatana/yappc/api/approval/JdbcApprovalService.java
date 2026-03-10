/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.approval.dto.CreateWorkflowRequest;
import com.ghatana.yappc.api.approval.dto.SubmitDecisionRequest;
import com.ghatana.yappc.api.approval.dto.WorkflowResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed approval service that persists workflow state to PostgreSQL.
 *
 * <p><b>Purpose</b><br>
 * Extends {@link ApprovalService} to add durability: every state mutation is
 * persisted to the {@code approval_workflows} table immediately after the
 * in-memory state machine has been updated. On construction the service
 * rehydrates all existing workflows from the DB so no state is lost on restart.
 *
 * <p><b>Serialization</b><br>
 * Each workflow (including all stages and approval records) is serialized to
 * a single JSONB column using Jackson. Reconstruction is done manually because
 * the inner domain classes are package-private.
 *
 * <p><b>Thread Safety</b><br>
 * All mutating operations are atomic at the service level: the DB write occurs
 * immediately after the in-memory update within the same method call. This is
 * sufficient for a single-node ActiveJ deployment.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed durable approval service
 * @doc.layer product
 * @doc.pattern Repository, Decorator
 */
public class JdbcApprovalService extends ApprovalService {

  private static final Logger logger = LoggerFactory.getLogger(JdbcApprovalService.class);
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  /**
   * Creates a JdbcApprovalService and loads pre-existing workflows from the DB.
   *
   * @param dataSource   HikariCP DataSource
   * @param objectMapper Jackson mapper for JSONB serialization
   */
  public JdbcApprovalService(DataSource dataSource, ObjectMapper objectMapper) {
    super();
    this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    rehydrateFromDb();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Mutating overrides — call super (state machine), then persist
  // ─────────────────────────────────────────────────────────────────────────

  @Override
  public WorkflowResponse create(String tenantId, String userId, CreateWorkflowRequest req) {
    WorkflowResponse response = super.create(tenantId, userId, req);
    persistWorkflow(tenantId, response.id());
    return response;
  }

  @Override
  public WorkflowResponse submitDecision(String tenantId, String userId,
      String workflowId, SubmitDecisionRequest req) {
    WorkflowResponse response = super.submitDecision(tenantId, userId, workflowId, req);
    persistWorkflow(tenantId, workflowId);
    return response;
  }

  @Override
  public WorkflowResponse cancel(String tenantId, String userId, String workflowId) {
    WorkflowResponse response = super.cancel(tenantId, userId, workflowId);
    persistWorkflow(tenantId, workflowId);
    return response;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Internal persistence helpers
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Retrieves the workflow from the in-memory store (via package-private access)
   * and upserts it into the DB.
   */
  private void persistWorkflow(String tenantId, String workflowId) {
    // Package-private access — JdbcApprovalService is in the same package
    Map<String, Workflow> tenantStore = store.get(tenantId);
    if (tenantStore == null) {
      logger.warn("Cannot persist workflow {} — tenant {} not in store", workflowId, tenantId);
      return;
    }
    Workflow wf = tenantStore.get(workflowId);
    if (wf == null) {
      logger.warn("Cannot persist workflow {} — not found in store", workflowId);
      return;
    }

    try {
      String json = serializeWorkflow(wf);
      String sql = """
          INSERT INTO approval_workflows
              (id, tenant_id, state_json, created_at, updated_at)
          VALUES (?, ?, ?::jsonb, ?, ?)
          ON CONFLICT (id) DO UPDATE
            SET state_json = EXCLUDED.state_json,
                updated_at  = EXCLUDED.updated_at
          """;

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, wf.id);
        ps.setString(2, tenantId);
        ps.setString(3, json);
        ps.setTimestamp(4, Timestamp.from(wf.createdAt));
        ps.setTimestamp(5, Timestamp.from(wf.updatedAt));
        ps.executeUpdate();
      }

      logger.debug("Persisted approval workflow id={} tenant={} status={}",
          workflowId, tenantId, wf.status);

    } catch (Exception e) {
      logger.error("Failed to persist approval workflow id={} tenant={}: {}",
          workflowId, tenantId, e.getMessage(), e);
    }
  }

  /**
   * Loads all workflows from the DB and rehydrates the parent's in-memory store.
   * Called once during construction.
   */
  private void rehydrateFromDb() {
    String sql = "SELECT tenant_id, state_json FROM approval_workflows";
    int count = 0;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {

      while (rs.next()) {
        String tenantId = rs.getString("tenant_id");
        String json = rs.getString("state_json");
        try {
          Workflow wf = deserializeWorkflow(json);
          // Package-private access to parent's store
          store.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
              .put(wf.id, wf);
          count++;
        } catch (Exception e) {
          logger.warn("Failed to rehydrate approval workflow from DB: {}", e.getMessage());
        }
      }

      logger.info("Rehydrated {} approval workflow(s) from PostgreSQL", count);

    } catch (Exception e) {
      logger.error("Failed to load approval workflows from DB: {}", e.getMessage(), e);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Serialization helpers
  // ─────────────────────────────────────────────────────────────────────────

  private String serializeWorkflow(Workflow wf) throws Exception {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", wf.id);
    map.put("tenantId", wf.tenantId);
    map.put("resourceType", wf.resourceType);
    map.put("resourceId", wf.resourceId);
    map.put("workflowType", wf.workflowType);
    map.put("initiator", wf.initiator);
    map.put("status", wf.status.name());
    map.put("currentStageIndex", wf.currentStageIndex);
    map.put("createdAt", wf.createdAt.toString());
    map.put("updatedAt", wf.updatedAt.toString());

    List<Map<String, Object>> stages = new ArrayList<>();
    for (Stage s : wf.stages) {
      Map<String, Object> stageMap = new LinkedHashMap<>();
      stageMap.put("name", s.name);
      stageMap.put("approvers", s.approvers);
      stageMap.put("requiredApprovals", s.requiredApprovals);
      stageMap.put("parallel", s.parallel);

      List<Map<String, Object>> records = new ArrayList<>();
      for (ApprovalRecord r : s.records) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("userId", r.userId);
        rec.put("decision", r.decision.name());
        rec.put("comments", r.comments);
        rec.put("timestamp", r.timestamp.toString());
        records.add(rec);
      }
      stageMap.put("records", records);
      stages.add(stageMap);
    }
    map.put("stages", stages);

    return objectMapper.writeValueAsString(map);
  }

  @SuppressWarnings("unchecked")
  private Workflow deserializeWorkflow(String json) throws Exception {
    Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);

    String id = (String) map.get("id");
    String tenantId = (String) map.get("tenantId");
    String resourceType = (String) map.get("resourceType");
    String resourceId = (String) map.get("resourceId");
    String workflowType = (String) map.get("workflowType");
    String initiator = (String) map.get("initiator");
    String statusStr = (String) map.get("status");
    int currentStageIndex = ((Number) map.get("currentStageIndex")).intValue();
    Instant createdAt = Instant.parse((String) map.get("createdAt"));
    Instant updatedAt = Instant.parse((String) map.get("updatedAt"));

    List<Map<String, Object>> stagesMaps =
        (List<Map<String, Object>>) map.getOrDefault("stages", List.of());

    List<Stage> stages = new ArrayList<>();
    for (Map<String, Object> sm : stagesMaps) {
      String name = (String) sm.get("name");
      List<String> approvers = (List<String>) sm.getOrDefault("approvers", List.of());
      int requiredApprovals = ((Number) sm.getOrDefault("requiredApprovals", 1)).intValue();
      boolean parallel = Boolean.TRUE.equals(sm.get("parallel"));

      Stage stage = new Stage(name, approvers, requiredApprovals, parallel);

      List<Map<String, Object>> recMaps =
          (List<Map<String, Object>>) sm.getOrDefault("records", List.of());
      for (Map<String, Object> rm : recMaps) {
        String userId = (String) rm.get("userId");
        Decision decision = Decision.valueOf((String) rm.get("decision"));
        String comments = (String) rm.getOrDefault("comments", "");
        ApprovalRecord rec = new ApprovalRecord(userId, decision, comments);
        stage.records.add(rec);
      }
      stages.add(stage);
    }

    Workflow wf = new Workflow(id, tenantId, resourceType, resourceId,
        workflowType, initiator, stages);

    // Restore mutable state (package-private fields, same package access)
    wf.status = Status.valueOf(statusStr);
    wf.currentStageIndex = currentStageIndex;
    wf.createdAt = createdAt;  // restore original creation time (field is non-final for rehydration)
    wf.updatedAt = updatedAt;

    return wf;
  }
}
