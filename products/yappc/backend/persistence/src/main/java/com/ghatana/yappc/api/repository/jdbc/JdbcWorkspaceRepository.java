package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Workspace;
import com.ghatana.yappc.api.domain.Workspace.WorkspaceStatus;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
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
 * JDBC implementation of WorkspaceRepository.
 *
 * <p>Uses standard JDBC with JSONB columns for complex structures (settings, members, teams).
 *
 * @doc.type class
 * @doc.purpose JDBC persistence for Workspaces
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcWorkspaceRepository implements WorkspaceRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcWorkspaceRepository.class);
    private static final String TABLE = "yappc.workspaces";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcWorkspaceRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.executor = Executors.newCachedThreadPool();
    }

    public JdbcWorkspaceRepository(DataSource dataSource, Executor executor, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.mapper = mapper;
    }

    @Override
    public Promise<Workspace> save(Workspace w) {
        return Promise.ofBlocking(executor, () -> {
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
            }
            // Update timestamp
            w.setUpdatedAt(Instant.now());
            if (w.getCreatedAt() == null) {
                w.setCreatedAt(Instant.now());
            }

            if (existsSync(w.getTenantId(), w.getId())) {
                return updateSync(w);
            } else {
                return insertSync(w);
            }
        });
    }

    @Override
    public Promise<Optional<Workspace>> findById(String tenantId, UUID id) {
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
    public Promise<List<Workspace>> findByTenantId(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ?";
            return queryList(sql, tenantId);
        });
    }

    @Override
    public Promise<List<Workspace>> findByMemberUserId(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            // Using JSONB containment operator @> to find if members array contains an object with this userId
            // The parameter must be a JSON string representing a list containing a partial object: '[{"userId": "..."}]'
            String filterJson = "[{\"userId\": \"" + userId + "\"}]";
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND members @> ?::jsonb";
            return queryList(sql, tenantId, filterJson);
        });
    }

    @Override
    public Promise<List<Workspace>> findByOwnerId(String tenantId, String ownerId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND owner_id = ?";
            return queryList(sql, tenantId, ownerId);
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
    
    // Correction: delete returns Promise<Void>
    // I need to adjust the method signature above or just implement standard void return.
    // The previous block had syntax error (UserId return type?). I will fix in follow-up internal logic check.
    // Actually the interface says Promise<Void>.

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> existsSync(tenantId, id));
    }

    @Override
    public Promise<Long> count(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
             String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ?";
             try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                    return 0L;
                }
            }
        });
    }

    // Helpers

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

    private Workspace insertSync(Workspace w) throws SQLException, IOException {
        String columns = "id, tenant_id, name, description, owner_id, status, " +
                         "settings, members, teams, metadata, created_at, updated_at";
        
        String values = "?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?";

        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int i = 1;
            ps.setObject(i++, w.getId());
            ps.setString(i++, w.getTenantId());
            ps.setString(i++, w.getName());
            ps.setString(i++, w.getDescription());
            ps.setString(i++, w.getOwnerId());
            ps.setString(i++, w.getStatus().name());
            
            ps.setString(i++, mapper.writeValueAsString(w.getSettings()));
            ps.setString(i++, mapper.writeValueAsString(w.getMembers()));
            ps.setString(i++, mapper.writeValueAsString(w.getTeams()));
            ps.setString(i++, mapper.writeValueAsString(w.getMetadata()));
            
            ps.setTimestamp(i++, w.getCreatedAt() != null ? Timestamp.from(w.getCreatedAt()) : null);
            ps.setTimestamp(i++, w.getUpdatedAt() != null ? Timestamp.from(w.getUpdatedAt()) : null);

            ps.executeUpdate();
            return w;
        }
    }

    private Workspace updateSync(Workspace w) throws SQLException, IOException {
        String sql = "UPDATE " + TABLE + " SET name=?, description=?, owner_id=?, status=?, " +
                     "settings=?::jsonb, members=?::jsonb, teams=?::jsonb, metadata=?::jsonb, " +
                     "updated_at=? WHERE tenant_id=? AND id=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int i = 1;
            ps.setString(i++, w.getName());
            ps.setString(i++, w.getDescription());
            ps.setString(i++, w.getOwnerId());
            ps.setString(i++, w.getStatus().name());
            
            ps.setString(i++, mapper.writeValueAsString(w.getSettings()));
            ps.setString(i++, mapper.writeValueAsString(w.getMembers()));
            ps.setString(i++, mapper.writeValueAsString(w.getTeams()));
            ps.setString(i++, mapper.writeValueAsString(w.getMetadata()));
            
            ps.setTimestamp(i++, w.getUpdatedAt() != null ? Timestamp.from(w.getUpdatedAt()) : null);
            
            ps.setString(i++, w.getTenantId());
            ps.setObject(i++, w.getId());

            ps.executeUpdate();
            return w;
        }
    }

    private List<Workspace> queryList(String sql, Object... params) throws SQLException {
        List<Workspace> result = new ArrayList<>();
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
    private Workspace mapRow(ResultSet rs) throws SQLException {
        try {
            Workspace w = new Workspace();
            w.setId(UUID.fromString(rs.getString("id")));
            w.setTenantId(rs.getString("tenant_id"));
            w.setName(rs.getString("name"));
            w.setDescription(rs.getString("description"));
            w.setOwnerId(rs.getString("owner_id"));
            w.setStatus(WorkspaceStatus.valueOf(rs.getString("status")));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) w.setCreatedAt(createdAt.toInstant());
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) w.setUpdatedAt(updatedAt.toInstant());
            
            // JSONB mapping
            w.setSettings(mapper.readValue(rs.getString("settings"), Workspace.WorkspaceSettings.class));
            
            // List types require TypeReference or Class mapping if simple
            // Using Arrays.asList from array type or constructing list type
            // Since we can't easily use TypeReference with simple readValue(String, Class), we might get LinkedHashMaps
            // Best to use TypeReference for Lists
            
            w.setMembers(mapper.readValue(rs.getString("members"), 
                mapper.getTypeFactory().constructCollectionType(List.class, Workspace.WorkspaceMember.class)));
                
            w.setTeams(mapper.readValue(rs.getString("teams"), 
                mapper.getTypeFactory().constructCollectionType(List.class, Workspace.Team.class)));
            
            w.setMetadata(mapper.readValue(rs.getString("metadata"), Map.class));
            
            return w;
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON columns", e);
        }
    }
}
