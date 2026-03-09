package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Story;
import com.ghatana.yappc.api.domain.Story.StoryStatus;
import com.ghatana.yappc.api.domain.Story.StoryType;
import com.ghatana.yappc.api.domain.Story.Priority;
import com.ghatana.yappc.api.domain.Story.Task;
import com.ghatana.yappc.api.domain.Story.AcceptanceCriterion;
import com.ghatana.yappc.api.domain.Story.PullRequest;
import com.ghatana.yappc.api.repository.StoryRepository;
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
 * JDBC implementation of StoryRepository.
 * 
 * <p>Uses standard JDBC with a DataSource for connection pooling.
 * Offloads blocking I/O to a dedicated Executor.
 * 
 * @doc.type class
 * @doc.purpose JDBC persistence for stories
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcStoryRepository implements StoryRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(JdbcStoryRepository.class);
    private static final String TABLE = "yappc.stories";
    
    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcStoryRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<Story> save(Story story) {
        return Promise.ofBlocking(executor, () -> {
            if (story.getId() == null) {
                story.setId(UUID.randomUUID());
            }
            boolean exists = existsSync(story.getTenantId(), story.getId());
            if (exists) {
                return updateSync(story);
            } else {
                return insertSync(story);
            }
        });
    }

    @Override
    public Promise<Optional<Story>> findById(String tenantId, UUID id) {
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
    public Promise<List<Story>> findByProject(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? ORDER BY created_at DESC";
            List<Story> result = new ArrayList<>();
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
    public Promise<List<Story>> findBySprint(String tenantId, String sprintId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND sprint_id = ? ORDER BY priority, created_at";
            List<Story> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, sprintId);
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
    public Promise<List<Story>> findBacklog(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? " +
                         "AND (sprint_id IS NULL OR sprint_id = '') ORDER BY priority, created_at";
            List<Story> result = new ArrayList<>();
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
    public Promise<List<Story>> findByStatus(String tenantId, String projectId, StoryStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? AND status = ?";
            List<Story> result = new ArrayList<>();
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
    public Promise<List<Story>> findByType(String tenantId, String projectId, StoryType type) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? AND type = ?";
            List<Story> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setString(3, type.name());
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
    public Promise<List<Story>> findByAssignee(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND assigned_to @> ?::jsonb";
            List<Story> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, "[\"" + userId + "\"]");
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
    public Promise<List<Story>> findBlocked(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? " +
                         "AND (status = 'BLOCKED' OR jsonb_array_length(blocked_by) > 0)";
            List<Story> result = new ArrayList<>();
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
    public Promise<Optional<Story>> findByKey(String tenantId, String storyKey) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND story_key = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, storyKey);
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
    public Promise<Long> countByStatus(String tenantId, String projectId, StoryStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ? AND status = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setString(3, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    @Override
    public Promise<Integer> sumStoryPoints(String tenantId, String sprintId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COALESCE(SUM(story_points), 0) FROM " + TABLE + 
                         " WHERE tenant_id = ? AND sprint_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, sprintId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    @Override
    public Promise<Integer> sumCompletedStoryPoints(String tenantId, String sprintId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COALESCE(SUM(story_points), 0) FROM " + TABLE + 
                         " WHERE tenant_id = ? AND sprint_id = ? AND status = 'DONE'";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, sprintId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
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

    private Story insertSync(Story story) throws SQLException, IOException {
        String columns = "id, tenant_id, project_id, sprint_id, title, description, type, priority, status, " +
                         "story_points, estimated_hours, actual_hours, assigned_to, tasks, acceptance_criteria, " +
                         "branch, pull_request, blocked_by, blocks, created_by, metadata, created_at, updated_at, completed_at";
        
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?";
        
        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ")";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            ps.setObject(idx++, story.getId());
            ps.setString(idx++, story.getTenantId());
            ps.setString(idx++, story.getProjectId());
            ps.setString(idx++, story.getSprintId());
            ps.setString(idx++, story.getTitle());
            ps.setString(idx++, story.getDescription());
            ps.setString(idx++, story.getType() != null ? story.getType().name() : StoryType.FEATURE.name());
            ps.setString(idx++, story.getPriority() != null ? story.getPriority().name() : Priority.P2.name());
            ps.setString(idx++, story.getStatus().name());
            ps.setInt(idx++, story.getStoryPoints());
            ps.setObject(idx++, story.getEstimatedHours());
            ps.setObject(idx++, story.getActualHours());
            ps.setString(idx++, mapper.writeValueAsString(story.getAssignedTo()));
            ps.setString(idx++, mapper.writeValueAsString(story.getTasks()));
            ps.setString(idx++, mapper.writeValueAsString(story.getAcceptanceCriteria()));
            ps.setString(idx++, story.getBranch());
            ps.setString(idx++, story.getPullRequest() != null ? mapper.writeValueAsString(story.getPullRequest()) : null);
            ps.setString(idx++, mapper.writeValueAsString(story.getBlockedBy()));
            ps.setString(idx++, mapper.writeValueAsString(story.getBlocks()));
            ps.setString(idx++, story.getCreatedBy());
            ps.setString(idx++, mapper.writeValueAsString(story.getMetadata()));
            ps.setTimestamp(idx++, story.getCreatedAt() != null ? Timestamp.from(story.getCreatedAt()) : null);
            ps.setTimestamp(idx++, story.getUpdatedAt() != null ? Timestamp.from(story.getUpdatedAt()) : null);
            ps.setTimestamp(idx++, story.getCompletedAt() != null ? Timestamp.from(story.getCompletedAt()) : null);
            
            ps.executeUpdate();
            return story;
        }
    }

    private Story updateSync(Story story) throws SQLException, IOException {
        String sql = "UPDATE " + TABLE + " SET project_id=?, sprint_id=?, title=?, description=?, type=?, " +
                     "priority=?, status=?, story_points=?, estimated_hours=?, actual_hours=?, assigned_to=?::jsonb, " +
                     "tasks=?::jsonb, acceptance_criteria=?::jsonb, branch=?, pull_request=?::jsonb, " +
                     "blocked_by=?::jsonb, blocks=?::jsonb, created_by=?, metadata=?::jsonb, " +
                     "created_at=?, updated_at=?, completed_at=? " +
                     "WHERE tenant_id=? AND id=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            ps.setString(idx++, story.getProjectId());
            ps.setString(idx++, story.getSprintId());
            ps.setString(idx++, story.getTitle());
            ps.setString(idx++, story.getDescription());
            ps.setString(idx++, story.getType() != null ? story.getType().name() : StoryType.FEATURE.name());
            ps.setString(idx++, story.getPriority() != null ? story.getPriority().name() : Priority.P2.name());
            ps.setString(idx++, story.getStatus().name());
            ps.setInt(idx++, story.getStoryPoints());
            ps.setObject(idx++, story.getEstimatedHours());
            ps.setObject(idx++, story.getActualHours());
            ps.setString(idx++, mapper.writeValueAsString(story.getAssignedTo()));
            ps.setString(idx++, mapper.writeValueAsString(story.getTasks()));
            ps.setString(idx++, mapper.writeValueAsString(story.getAcceptanceCriteria()));
            ps.setString(idx++, story.getBranch());
            ps.setString(idx++, story.getPullRequest() != null ? mapper.writeValueAsString(story.getPullRequest()) : null);
            ps.setString(idx++, mapper.writeValueAsString(story.getBlockedBy()));
            ps.setString(idx++, mapper.writeValueAsString(story.getBlocks()));
            ps.setString(idx++, story.getCreatedBy());
            ps.setString(idx++, mapper.writeValueAsString(story.getMetadata()));
            ps.setTimestamp(idx++, story.getCreatedAt() != null ? Timestamp.from(story.getCreatedAt()) : null);
            ps.setTimestamp(idx++, story.getUpdatedAt() != null ? Timestamp.from(story.getUpdatedAt()) : null);
            ps.setTimestamp(idx++, story.getCompletedAt() != null ? Timestamp.from(story.getCompletedAt()) : null);
            ps.setString(idx++, story.getTenantId());
            ps.setObject(idx++, story.getId());
            
            ps.executeUpdate();
            return story;
        }
    }

    @SuppressWarnings("unchecked")
    private Story mapRow(ResultSet rs) throws SQLException {
        try {
            Story story = new Story();
            story.setId(UUID.fromString(rs.getString("id")));
            story.setTenantId(rs.getString("tenant_id"));
            story.setProjectId(rs.getString("project_id"));
            story.setSprintId(rs.getString("sprint_id"));
            story.setTitle(rs.getString("title"));
            story.setDescription(rs.getString("description"));
            
            String typeStr = rs.getString("type");
            if (typeStr != null) {
                story.setType(StoryType.valueOf(typeStr));
            }
            
            String priorityStr = rs.getString("priority");
            if (priorityStr != null) {
                story.setPriority(Priority.valueOf(priorityStr));
            }
            
            story.setStatus(StoryStatus.valueOf(rs.getString("status")));
            story.setStoryPoints(rs.getInt("story_points"));
            
            int estimatedHours = rs.getInt("estimated_hours");
            if (!rs.wasNull()) {
                story.setEstimatedHours(estimatedHours);
            }
            
            int actualHours = rs.getInt("actual_hours");
            if (!rs.wasNull()) {
                story.setActualHours(actualHours);
            }
            
            String assignedToJson = rs.getString("assigned_to");
            if (assignedToJson != null) {
                story.setAssignedTo(mapper.readValue(assignedToJson, List.class));
            }
            
            String tasksJson = rs.getString("tasks");
            if (tasksJson != null) {
                story.setTasks(mapper.readValue(tasksJson, 
                    mapper.getTypeFactory().constructCollectionType(List.class, Task.class)));
            }
            
            String acceptanceCriteriaJson = rs.getString("acceptance_criteria");
            if (acceptanceCriteriaJson != null) {
                story.setAcceptanceCriteria(mapper.readValue(acceptanceCriteriaJson, 
                    mapper.getTypeFactory().constructCollectionType(List.class, AcceptanceCriterion.class)));
            }
            
            story.setBranch(rs.getString("branch"));
            
            String prJson = rs.getString("pull_request");
            if (prJson != null) {
                story.setPullRequest(mapper.readValue(prJson, PullRequest.class));
            }
            
            String blockedByJson = rs.getString("blocked_by");
            if (blockedByJson != null) {
                story.setBlockedBy(mapper.readValue(blockedByJson, List.class));
            }
            
            String blocksJson = rs.getString("blocks");
            if (blocksJson != null) {
                story.setBlocks(mapper.readValue(blocksJson, List.class));
            }
            
            story.setCreatedBy(rs.getString("created_by"));
            
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                story.setMetadata(mapper.readValue(metadataJson, Map.class));
            }
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) story.setCreatedAt(createdAt.toInstant());
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) story.setUpdatedAt(updatedAt.toInstant());
            
            Timestamp completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) story.setCompletedAt(completedAt.toInstant());
            
            return story;
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON columns", e);
        }
    }
}
