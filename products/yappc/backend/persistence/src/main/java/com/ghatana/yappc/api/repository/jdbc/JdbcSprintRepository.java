package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Sprint;
import com.ghatana.yappc.api.domain.Sprint.SprintStatus;
import com.ghatana.yappc.api.domain.Sprint.SprintRetrospective;
import com.ghatana.yappc.api.domain.Story;
import com.ghatana.yappc.api.repository.SprintRepository;
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
 * JDBC implementation of SprintRepository.
 * 
 * <p>Uses standard JDBC with a DataSource for connection pooling.
 * Offloads blocking I/O to a dedicated Executor.
 * 
 * @doc.type class
 * @doc.purpose JDBC persistence for sprints
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcSprintRepository implements SprintRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(JdbcSprintRepository.class);
    private static final String TABLE = "yappc.sprints";
    
    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcSprintRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<Sprint> save(Sprint sprint) {
        return Promise.ofBlocking(executor, () -> {
            if (sprint.getId() == null) {
                sprint.setId(UUID.randomUUID());
            }
            boolean exists = existsSync(sprint.getTenantId(), sprint.getId());
            if (exists) {
                return updateSync(sprint);
            } else {
                return insertSync(sprint);
            }
        });
    }

    @Override
    public Promise<Optional<Sprint>> findById(String tenantId, UUID id) {
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
    public Promise<List<Sprint>> findByProject(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? ORDER BY start_date DESC";
            List<Sprint> result = new ArrayList<>();
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
    public Promise<Optional<Sprint>> findCurrentSprint(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? AND status = 'ACTIVE' LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                
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
    public Promise<List<Sprint>> findByStatus(String tenantId, String projectId, SprintStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? AND status = ?";
            List<Sprint> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setString(3, status.name());
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
    public Promise<List<Sprint>> findCompletedSprints(String tenantId, String projectId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? " +
                         "AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT ?";
            List<Sprint> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setInt(3, limit);
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
    public Promise<Boolean> delete(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> existsSync(tenantId, id));
    }

    @Override
    public Promise<Double> calculateAverageVelocity(String tenantId, String projectId, int lastN) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT AVG(velocity) FROM (SELECT velocity FROM " + TABLE + 
                         " WHERE tenant_id = ? AND project_id = ? AND status = 'COMPLETED' " +
                         "ORDER BY completed_at DESC LIMIT ?) sub";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setInt(3, lastN);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble(1);
                    }
                    return 0.0;
                }
            }
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

    private Sprint insertSync(Sprint sprint) throws SQLException, IOException {
        String columns = "id, tenant_id, project_id, name, goals, status, start_date, end_date, " +
                         "team_capacity, committed_points, completed_points, velocity, " +
                         "created_by, retrospective, metadata, created_at, updated_at, completed_at";
        
        String values = "?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?";
        
        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ")";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            ps.setObject(idx++, sprint.getId());
            ps.setString(idx++, sprint.getTenantId());
            ps.setString(idx++, sprint.getProjectId());
            ps.setString(idx++, sprint.getName());
            ps.setString(idx++, mapper.writeValueAsString(sprint.getGoals()));
            ps.setString(idx++, sprint.getStatus().name());
            ps.setTimestamp(idx++, sprint.getStartDate() != null ? Timestamp.from(sprint.getStartDate()) : null);
            ps.setTimestamp(idx++, sprint.getEndDate() != null ? Timestamp.from(sprint.getEndDate()) : null);
            ps.setInt(idx++, sprint.getTeamCapacity());
            ps.setInt(idx++, sprint.getCommittedPoints());
            ps.setInt(idx++, sprint.getCompletedPoints());
            ps.setDouble(idx++, sprint.getVelocity());
            ps.setString(idx++, sprint.getCreatedBy());
            ps.setString(idx++, sprint.getRetrospective() != null ? 
                         mapper.writeValueAsString(sprint.getRetrospective()) : null);
            ps.setString(idx++, mapper.writeValueAsString(sprint.getMetadata()));
            ps.setTimestamp(idx++, sprint.getCreatedAt() != null ? Timestamp.from(sprint.getCreatedAt()) : null);
            ps.setTimestamp(idx++, sprint.getUpdatedAt() != null ? Timestamp.from(sprint.getUpdatedAt()) : null);
            ps.setTimestamp(idx++, sprint.getCompletedAt() != null ? Timestamp.from(sprint.getCompletedAt()) : null);
            
            ps.executeUpdate();
            return sprint;
        }
    }

    private Sprint updateSync(Sprint sprint) throws SQLException, IOException {
        String sql = "UPDATE " + TABLE + " SET project_id=?, name=?, goals=?::jsonb, status=?, " +
                     "start_date=?, end_date=?, team_capacity=?, committed_points=?, completed_points=?, " +
                     "velocity=?, created_by=?, retrospective=?::jsonb, metadata=?::jsonb, " +
                     "created_at=?, updated_at=?, completed_at=? " +
                     "WHERE tenant_id=? AND id=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            ps.setString(idx++, sprint.getProjectId());
            ps.setString(idx++, sprint.getName());
            ps.setString(idx++, mapper.writeValueAsString(sprint.getGoals()));
            ps.setString(idx++, sprint.getStatus().name());
            ps.setTimestamp(idx++, sprint.getStartDate() != null ? Timestamp.from(sprint.getStartDate()) : null);
            ps.setTimestamp(idx++, sprint.getEndDate() != null ? Timestamp.from(sprint.getEndDate()) : null);
            ps.setInt(idx++, sprint.getTeamCapacity());
            ps.setInt(idx++, sprint.getCommittedPoints());
            ps.setInt(idx++, sprint.getCompletedPoints());
            ps.setDouble(idx++, sprint.getVelocity());
            ps.setString(idx++, sprint.getCreatedBy());
            ps.setString(idx++, sprint.getRetrospective() != null ? 
                         mapper.writeValueAsString(sprint.getRetrospective()) : null);
            ps.setString(idx++, mapper.writeValueAsString(sprint.getMetadata()));
            ps.setTimestamp(idx++, sprint.getCreatedAt() != null ? Timestamp.from(sprint.getCreatedAt()) : null);
            ps.setTimestamp(idx++, sprint.getUpdatedAt() != null ? Timestamp.from(sprint.getUpdatedAt()) : null);
            ps.setTimestamp(idx++, sprint.getCompletedAt() != null ? Timestamp.from(sprint.getCompletedAt()) : null);
            ps.setString(idx++, sprint.getTenantId());
            ps.setObject(idx++, sprint.getId());
            
            ps.executeUpdate();
            return sprint;
        }
    }

    @SuppressWarnings("unchecked")
    private Sprint mapRow(ResultSet rs) throws SQLException {
        try {
            Sprint sprint = new Sprint();
            sprint.setId(UUID.fromString(rs.getString("id")));
            sprint.setTenantId(rs.getString("tenant_id"));
            sprint.setProjectId(rs.getString("project_id"));
            sprint.setName(rs.getString("name"));
            
            String goalsJson = rs.getString("goals");
            if (goalsJson != null) {
                sprint.setGoals(mapper.readValue(goalsJson, List.class));
            }
            
            sprint.setStatus(SprintStatus.valueOf(rs.getString("status")));
            
            Timestamp startDate = rs.getTimestamp("start_date");
            if (startDate != null) sprint.setStartDate(startDate.toInstant());
            
            Timestamp endDate = rs.getTimestamp("end_date");
            if (endDate != null) sprint.setEndDate(endDate.toInstant());
            
            sprint.setTeamCapacity(rs.getInt("team_capacity"));
            sprint.setCommittedPoints(rs.getInt("committed_points"));
            sprint.setCompletedPoints(rs.getInt("completed_points"));
            sprint.setVelocity(rs.getDouble("velocity"));
            sprint.setCreatedBy(rs.getString("created_by"));
            
            String retroJson = rs.getString("retrospective");
            if (retroJson != null) {
                sprint.setRetrospective(mapper.readValue(retroJson, SprintRetrospective.class));
            }
            
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                sprint.setMetadata(mapper.readValue(metadataJson, Map.class));
            }
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) sprint.setCreatedAt(createdAt.toInstant());
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) sprint.setUpdatedAt(updatedAt.toInstant());
            
            Timestamp completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) sprint.setCompletedAt(completedAt.toInstant());
            
            // Note: stories are not loaded here - use StoryRepository.findBySprint()
            sprint.setStories(new ArrayList<>());
            
            return sprint;
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON columns", e);
        }
    }
}
