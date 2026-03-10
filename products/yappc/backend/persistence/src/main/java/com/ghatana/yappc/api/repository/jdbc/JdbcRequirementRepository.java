package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Requirement;
import com.ghatana.yappc.api.domain.Requirement.Priority;
import com.ghatana.yappc.api.domain.Requirement.QualityMetrics;
import com.ghatana.yappc.api.domain.Requirement.RequirementStatus;
import com.ghatana.yappc.api.domain.Requirement.RequirementType;
import com.ghatana.yappc.api.repository.RequirementRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.inject.annotation.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of RequirementRepository.
 * 
 * <p>Uses standard JDBC with a DataSource for connection pooling.
 * Offloads blocking I/O to a dedicated Executor.
 * 
 * @doc.type class
 * @doc.purpose JDBC persistence for requirements
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcRequirementRepository implements RequirementRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(JdbcRequirementRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });

    private static final String TABLE = "yappc.requirements";
    
    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcRequirementRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<Requirement> save(Requirement requirement) {
        return Promise.ofBlocking(executor, () -> {
            if (requirement.getId() == null) {
                requirement.setId(UUID.randomUUID());
            }
            boolean exists = existsSync(requirement.getTenantId(), requirement.getId());
            if (exists) {
                return updateSync(requirement);
            } else {
                return insertSync(requirement);
            }
        });
    }

    @Override
    public Promise<Optional<Requirement>> findById(String tenantId, UUID id) {
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
    public Promise<List<Requirement>> findAllByTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ?";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
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
    public Promise<List<Requirement>> findByProject(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
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
    public Promise<List<Requirement>> findByAssignee(String tenantId, String assigneeId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND assigned_to = ?";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, assigneeId);
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
    public Promise<List<Requirement>> findByStatus(String tenantId, RequirementStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND status = ?";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, status.name());
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
    public Promise<List<Requirement>> findByType(String tenantId, RequirementType type) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND type = ?";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, type.name());
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
    public Promise<List<Requirement>> findByPriority(String tenantId, Priority priority) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND priority = ?";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, priority.name());
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
    public Promise<List<Requirement>> findBelowQualityThreshold(String tenantId, double threshold) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND (" +
                         "(quality_metrics->>'testabilityScore')::float < ? OR " +
                         "(quality_metrics->>'completenessScore')::float < ? OR " +
                         "(quality_metrics->>'clarityScore')::float < ?)";
            List<Requirement> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setDouble(2, threshold);
                ps.setDouble(3, threshold);
                ps.setDouble(4, threshold);
                
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

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> existsSync(tenantId, id));
    }

    @Override
    public Promise<Map<RequirementStatus, Long>> countByStatus(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT status, COUNT(*) as count FROM " + TABLE + " WHERE tenant_id = ? GROUP BY status";
            Map<RequirementStatus, Long> counts = new EnumMap<>(RequirementStatus.class);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String statusStr = rs.getString("status");
                        long count = rs.getLong("count");
                        try {
                            counts.put(RequirementStatus.valueOf(statusStr), count);
                        } catch (IllegalArgumentException e) {
                            logger.warn("Unknown status in database: {}", statusStr);
                        }
                    }
                }
            }
            return counts;
        });
    }

    // --- Helper Methods ---

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

    private Requirement insertSync(Requirement req) throws SQLException, IOException {
        String columns = "id, tenant_id, project_id, title, description, status, priority, type, " + 
                         "assigned_to, created_by, category, version_number, " +
                         "tags, dependencies, quality_metrics, metadata, created_at, updated_at";
        
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?";
        
        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ")";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setObject(1, req.getId());
            ps.setString(2, req.getTenantId());
            ps.setString(3, req.getProjectId());
            ps.setString(4, req.getTitle());
            ps.setString(5, req.getDescription());
            ps.setString(6, req.getStatus().name());
            ps.setString(7, req.getPriority().name());
            ps.setString(8, req.getType().name());
            ps.setString(9, req.getAssignedTo());
            ps.setString(10, req.getCreatedBy());
            ps.setString(11, req.getCategory());
            ps.setInt(12, req.getVersionNumber() != null ? req.getVersionNumber() : 1);
            
            ps.setString(13, mapper.writeValueAsString(req.getTags()));
            ps.setString(14, mapper.writeValueAsString(req.getDependencies()));
            ps.setString(15, mapper.writeValueAsString(req.getQualityMetrics()));
            ps.setString(16, mapper.writeValueAsString(req.getMetadata()));
            
            ps.setTimestamp(17, req.getCreatedAt() != null ? Timestamp.from(req.getCreatedAt()) : null);
            ps.setTimestamp(18, req.getUpdatedAt() != null ? Timestamp.from(req.getUpdatedAt()) : null);
            
            ps.executeUpdate();
            return req;
        }
    }

    private Requirement updateSync(Requirement req) throws SQLException, IOException {
        String sql = "UPDATE " + TABLE + " SET project_id=?, title=?, description=?, status=?, priority=?, type=?, " +
                     "assigned_to=?, created_by=?, category=?, version_number=?, " +
                     "tags=?::jsonb, dependencies=?::jsonb, quality_metrics=?::jsonb, metadata=?::jsonb, " +
                     "created_at=?, updated_at=? " +
                     "WHERE tenant_id=? AND id=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, req.getProjectId());
            ps.setString(2, req.getTitle());
            ps.setString(3, req.getDescription());
            ps.setString(4, req.getStatus().name());
            ps.setString(5, req.getPriority().name());
            ps.setString(6, req.getType().name());
            ps.setString(7, req.getAssignedTo());
            ps.setString(8, req.getCreatedBy());
            ps.setString(9, req.getCategory());
            ps.setInt(10, req.getVersionNumber() != null ? req.getVersionNumber() : 1);
            
            ps.setString(11, mapper.writeValueAsString(req.getTags()));
            ps.setString(12, mapper.writeValueAsString(req.getDependencies()));
            ps.setString(13, mapper.writeValueAsString(req.getQualityMetrics()));
            ps.setString(14, mapper.writeValueAsString(req.getMetadata()));
            
            ps.setTimestamp(15, req.getCreatedAt() != null ? Timestamp.from(req.getCreatedAt()) : null);
            ps.setTimestamp(16, req.getUpdatedAt() != null ? Timestamp.from(req.getUpdatedAt()) : null);
            
            ps.setString(17, req.getTenantId());
            ps.setObject(18, req.getId());
            
            ps.executeUpdate();
            return req;
        }
    }

    @SuppressWarnings("unchecked")
    private Requirement mapRow(ResultSet rs) throws SQLException {
        try {
            return Requirement.builder()
                .id(UUID.fromString(rs.getString("id")))
                .tenantId(rs.getString("tenant_id"))
                .projectId(rs.getString("project_id"))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .status(RequirementStatus.valueOf(rs.getString("status")))
                .priority(Priority.valueOf(rs.getString("priority")))
                .type(RequirementType.valueOf(rs.getString("type")))
                .assignedTo(rs.getString("assigned_to"))
                .category(rs.getString("category"))
                .createdBy(rs.getString("created_by"))
                .tags(mapper.readValue(rs.getString("tags"), List.class))
                .qualityMetrics(mapper.readValue(rs.getString("quality_metrics"), QualityMetrics.class))
                .dependencies(mapper.readValue(rs.getString("dependencies"), List.class))
                .metadata(mapper.readValue(rs.getString("metadata"), Map.class))
                .build();
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON columns", e);
        }
    }
}
