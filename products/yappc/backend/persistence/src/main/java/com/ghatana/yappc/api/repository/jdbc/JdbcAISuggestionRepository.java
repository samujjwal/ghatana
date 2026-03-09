package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.AISuggestion;
import com.ghatana.yappc.api.domain.AISuggestion.Priority;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionStatus;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionType;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of AISuggestionRepository.
 *
 * <p>Uses standard JDBC with a DataSource for connection pooling.
 * Offloads blocking I/O to a dedicated Executor.
 *
 * @doc.type class
 * @doc.purpose JDBC persistence for AI suggestions
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcAISuggestionRepository implements AISuggestionRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcAISuggestionRepository.class);
    private static final String TABLE = "yappc.ai_suggestions";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcAISuggestionRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.executor = Executors.newCachedThreadPool();
    }

    // Constructor for testing
    public JdbcAISuggestionRepository(DataSource dataSource, Executor executor, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.mapper = mapper;
    }

    @Override
    public Promise<AISuggestion> save(AISuggestion suggestion) {
        return Promise.ofBlocking(executor, () -> {
            if (suggestion.getId() == null) {
                suggestion.setId(UUID.randomUUID());
            }
            if (existsSync(suggestion.getTenantId(), suggestion.getId())) {
                return updateSync(suggestion);
            } else {
                return insertSync(suggestion);
            }
        });
    }

    @Override
    public Promise<Optional<AISuggestion>> findById(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<AISuggestion>> findPending(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND status = 'PENDING'";
            return queryList(sql, tenantId);
        });
    }

    @Override
    public Promise<List<AISuggestion>> findPendingByProject(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? AND status = 'PENDING'";
            return queryList(sql, tenantId, projectId);
        });
    }

    @Override
    public Promise<List<AISuggestion>> findByStatus(String tenantId, SuggestionStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND status = ?";
            return queryList(sql, tenantId, status.name());
        });
    }

    @Override
    public Promise<List<AISuggestion>> findByType(String tenantId, SuggestionType type) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND type = ?";
            return queryList(sql, tenantId, type.name());
        });
    }

    @Override
    public Promise<List<AISuggestion>> findByTargetResource(String tenantId, String resourceType, String resourceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND target_resource_type = ? AND target_resource_id = ?";
            return queryList(sql, tenantId, resourceType, resourceId);
        });
    }

    @Override
    public Promise<List<AISuggestion>> findByMinConfidence(String tenantId, double confidenceThreshold) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND confidence >= ?";
            List<AISuggestion> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setDouble(2, confidenceThreshold);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Promise<List<AISuggestion>> findByPriority(String tenantId, Priority priority) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND priority = ?";
            return queryList(sql, tenantId, priority.name());
        });
    }

    @Override
    public Promise<List<AISuggestion>> findCreatedAfter(String tenantId, Instant after) {
         return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND created_at > ?";
            List<AISuggestion> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(after));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                         result.add(mapRow(rs));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Promise<List<AISuggestion>> findReviewedBy(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND reviewed_by = ?";
            return queryList(sql, tenantId, userId);
        });
    }

    @Override
    public Promise<Map<SuggestionStatus, Long>> countByStatus(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT status, COUNT(*) as cnt FROM " + TABLE + " WHERE tenant_id = ? GROUP BY status";
            Map<SuggestionStatus, Long> result = new EnumMap<>(SuggestionStatus.class);
            for (SuggestionStatus status : SuggestionStatus.values()) {
                result.put(status, 0L);
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                         String statusStr = rs.getString("status");
                         if (statusStr != null) {
                             try {
                                 result.put(SuggestionStatus.valueOf(statusStr), rs.getLong("cnt"));
                             } catch (IllegalArgumentException ignored) {}
                         }
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Promise<Map<SuggestionType, Long>> countPendingByType(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT type, COUNT(*) as cnt FROM " + TABLE + " WHERE tenant_id = ? AND status = 'PENDING' GROUP BY type";
            Map<SuggestionType, Long> result = new EnumMap<>(SuggestionType.class);
            for (SuggestionType type : SuggestionType.values()) {
                result.put(type, 0L);
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                         String typeStr = rs.getString("type");
                         if (typeStr != null) {
                             try {
                                 result.put(SuggestionType.valueOf(typeStr), rs.getLong("cnt"));
                             } catch (IllegalArgumentException ignored) {}
                         }
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Promise<Double> getAcceptanceRate(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT " +
                         "SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) as accepted, " +
                         "SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected " +
                         "FROM " + TABLE + " WHERE tenant_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long accepted = rs.getLong("accepted");
                        long rejected = rs.getLong("rejected");
                        long total = accepted + rejected;
                        if (total == 0) return 0.0;
                        return (double) accepted / total;
                    }
                }
            }
            return 0.0;
        });
    }

    @Override
    public Promise<Long> countUrgent(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ? AND status = 'PENDING' AND priority = 'HIGH'";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            }
        });
    }

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> existsSync(tenantId, id));
    }

    @Override
    public Promise<Void> delete(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // Not implementing countByStatus for now as requested interface signature involves Map<SuggestionStatus, Long>
    // but generic queryList helpers return lists. Will stick to core CRUD + List queries.


    private boolean existsSync(String tenantId, UUID id) throws SQLException {
        String sql = "SELECT 1 FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private AISuggestion insertSync(AISuggestion s) throws SQLException, IOException {
        String columns = "id, tenant_id, project_id, type, status, title, content, rationale, " +
                         "source_model, target_resource_id, target_resource_type, confidence, priority, " +
                         "created_by, reviewed_by, created_at, reviewed_at, review_notes, " +
                         "tags, metadata";
        
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb";

        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;
            ps.setObject(i++, s.getId());
            ps.setString(i++, s.getTenantId());
            ps.setString(i++, s.getProjectId());
            ps.setString(i++, s.getType().name());
            ps.setString(i++, s.getStatus().name());
            ps.setString(i++, s.getTitle());
            ps.setString(i++, s.getContent());
            ps.setString(i++, s.getRationale());
            ps.setString(i++, s.getSourceModel());
            ps.setString(i++, s.getTargetResourceId());
            ps.setString(i++, s.getTargetResourceType());
            ps.setDouble(i++, s.getConfidence());
            ps.setString(i++, s.getPriority().name());
            ps.setString(i++, s.getCreatedBy());
            ps.setString(i++, s.getReviewedBy());
            ps.setTimestamp(i++, s.getCreatedAt() != null ? Timestamp.from(s.getCreatedAt()) : null);
            ps.setTimestamp(i++, s.getReviewedAt() != null ? Timestamp.from(s.getReviewedAt()) : null);
            ps.setString(i++, s.getReviewNotes());
            ps.setString(i++, mapper.writeValueAsString(s.getTags()));
            ps.setString(i++, mapper.writeValueAsString(s.getMetadata()));

            ps.executeUpdate();
            return s;
        }
    }

    private AISuggestion updateSync(AISuggestion s) throws SQLException, IOException {
        String sql = "UPDATE " + TABLE + " SET project_id=?, type=?, status=?, title=?, content=?, " +
                     "rationale=?, source_model=?, target_resource_id=?, target_resource_type=?, " +
                     "confidence=?, priority=?, created_by=?, reviewed_by=?, created_at=?, " +
                     "reviewed_at=?, review_notes=?, tags=?::jsonb, metadata=?::jsonb " +
                     "WHERE tenant_id=? AND id=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, s.getProjectId());
            ps.setString(i++, s.getType().name());
            ps.setString(i++, s.getStatus().name());
            ps.setString(i++, s.getTitle());
            ps.setString(i++, s.getContent());
            ps.setString(i++, s.getRationale());
            ps.setString(i++, s.getSourceModel());
            ps.setString(i++, s.getTargetResourceId());
            ps.setString(i++, s.getTargetResourceType());
            ps.setDouble(i++, s.getConfidence());
            ps.setString(i++, s.getPriority().name());
            ps.setString(i++, s.getCreatedBy());
            ps.setString(i++, s.getReviewedBy());
            ps.setTimestamp(i++, s.getCreatedAt() != null ? Timestamp.from(s.getCreatedAt()) : null);
            ps.setTimestamp(i++, s.getReviewedAt() != null ? Timestamp.from(s.getReviewedAt()) : null);
            ps.setString(i++, s.getReviewNotes());
            ps.setString(i++, mapper.writeValueAsString(s.getTags()));
            ps.setString(i++, mapper.writeValueAsString(s.getMetadata()));
            
            ps.setString(i++, s.getTenantId());
            ps.setObject(i++, s.getId());

            ps.executeUpdate();
            return s;
        }
    }

    private List<AISuggestion> queryList(String sql, Object... params) throws SQLException {
        List<AISuggestion> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private AISuggestion mapRow(ResultSet rs) throws SQLException {
        try {
            AISuggestion s = new AISuggestion();
            s.setId(UUID.fromString(rs.getString("id")));
            s.setTenantId(rs.getString("tenant_id"));
            s.setProjectId(rs.getString("project_id"));
            s.setType(SuggestionType.valueOf(rs.getString("type")));
            s.setStatus(SuggestionStatus.valueOf(rs.getString("status")));
            s.setTitle(rs.getString("title"));
            s.setContent(rs.getString("content"));
            s.setRationale(rs.getString("rationale"));
            s.setSourceModel(rs.getString("source_model"));
            s.setTargetResourceId(rs.getString("target_resource_id"));
            s.setTargetResourceType(rs.getString("target_resource_type"));
            s.setConfidence(rs.getDouble("confidence"));
            s.setPriority(Priority.valueOf(rs.getString("priority")));
            s.setCreatedBy(rs.getString("created_by"));
            s.setReviewedBy(rs.getString("reviewed_by"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) s.setCreatedAt(createdAt.toInstant());
            
            Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
            if (reviewedAt != null) s.setReviewedAt(reviewedAt.toInstant());
            
            s.setReviewNotes(rs.getString("review_notes"));
            
            s.setTags(mapper.readValue(rs.getString("tags"), List.class));
            s.setMetadata(mapper.readValue(rs.getString("metadata"), Map.class));
            
            return s;
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON columns", e);
        }
    }
}
