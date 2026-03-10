package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.BootstrappingSession;
import com.ghatana.yappc.api.domain.BootstrappingSession.*;
import com.ghatana.yappc.api.repository.BootstrappingSessionRepository;
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
 * JDBC implementation of BootstrappingSessionRepository.
 * 
 * <p>Uses standard JDBC with a DataSource for connection pooling.
 * Offloads blocking I/O to a dedicated Executor.
 * 
 * @doc.type class
 * @doc.purpose JDBC persistence for bootstrapping sessions
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JdbcBootstrappingSessionRepository implements BootstrappingSessionRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(JdbcBootstrappingSessionRepository.class);
    private static final Executor JDBC_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "jdbc-repo");
        t.setDaemon(true);
        return t;
    });

    private static final String TABLE = "yappc.bootstrapping_sessions";
    
    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcBootstrappingSessionRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public Promise<BootstrappingSession> save(BootstrappingSession session) {
        return Promise.ofBlocking(executor, () -> {
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            boolean exists = existsSync(session.getTenantId(), session.getId());
            if (exists) {
                return updateSync(session);
            } else {
                return insertSync(session);
            }
        });
    }

    @Override
    public Promise<Optional<BootstrappingSession>> findById(String tenantId, UUID id) {
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
    public Promise<List<BootstrappingSession>> findAllByTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? ORDER BY created_at DESC";
            List<BootstrappingSession> result = new ArrayList<>();
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
    public Promise<List<BootstrappingSession>> findByUser(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND user_id = ? ORDER BY created_at DESC";
            List<BootstrappingSession> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, userId);
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
    public Promise<List<BootstrappingSession>> findByStatus(String tenantId, SessionStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND status = ?";
            List<BootstrappingSession> result = new ArrayList<>();
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
    public Promise<List<BootstrappingSession>> findActiveByUser(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND user_id = ? " +
                         "AND status NOT IN ('APPROVED', 'ABANDONED', 'REJECTED') " +
                         "ORDER BY last_activity_at DESC";
            List<BootstrappingSession> result = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, userId);
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
    public Promise<List<BootstrappingSession>> findInactive(String tenantId, int timeoutDays) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? " +
                         "AND status NOT IN ('APPROVED', 'ABANDONED', 'REJECTED') " +
                         "AND last_activity_at < NOW() - INTERVAL '" + timeoutDays + " days'";
            List<BootstrappingSession> result = new ArrayList<>();
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
    public Promise<Long> countByStatus(String tenantId, SessionStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ? AND status = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
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

    private BootstrappingSession insertSync(BootstrappingSession session) throws SQLException, IOException {
        String columns = "id, tenant_id, user_id, workspace_id, status, initial_idea, " +
                         "user_profile, project_hints, organization_context, collaboration_settings, " +
                         "project_definition, project_graph, validation_report, conversation_history, " +
                         "transition_data, metadata, created_at, updated_at, approved_at, last_activity_at";
        
        String values = "?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?";
        
        String sql = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ")";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            ps.setObject(idx++, session.getId());
            ps.setString(idx++, session.getTenantId());
            ps.setString(idx++, session.getUserId());
            ps.setString(idx++, session.getWorkspaceId());
            ps.setString(idx++, session.getStatus().name());
            ps.setString(idx++, session.getInitialIdea());
            ps.setString(idx++, session.getUserProfile() != null ? mapper.writeValueAsString(session.getUserProfile()) : null);
            ps.setString(idx++, session.getProjectHints() != null ? mapper.writeValueAsString(session.getProjectHints()) : null);
            ps.setString(idx++, session.getOrganizationContext() != null ? mapper.writeValueAsString(session.getOrganizationContext()) : null);
            ps.setString(idx++, session.getCollaborationSettings() != null ? mapper.writeValueAsString(session.getCollaborationSettings()) : null);
            ps.setString(idx++, session.getProjectDefinition() != null ? mapper.writeValueAsString(session.getProjectDefinition()) : null);
            ps.setString(idx++, session.getProjectGraph() != null ? mapper.writeValueAsString(session.getProjectGraph()) : null);
            ps.setString(idx++, session.getValidationReport() != null ? mapper.writeValueAsString(session.getValidationReport()) : null);
            ps.setString(idx++, mapper.writeValueAsString(session.getConversationHistory()));
            ps.setString(idx++, session.getTransitionData() != null ? mapper.writeValueAsString(session.getTransitionData()) : null);
            ps.setString(idx++, mapper.writeValueAsString(session.getMetadata()));
            ps.setTimestamp(idx++, session.getCreatedAt() != null ? Timestamp.from(session.getCreatedAt()) : null);
            ps.setTimestamp(idx++, session.getUpdatedAt() != null ? Timestamp.from(session.getUpdatedAt()) : null);
            ps.setTimestamp(idx++, session.getApprovedAt() != null ? Timestamp.from(session.getApprovedAt()) : null);
            ps.setTimestamp(idx++, session.getLastActivityAt() != null ? Timestamp.from(session.getLastActivityAt()) : null);
            
            ps.executeUpdate();
            return session;
        }
    }

    private BootstrappingSession updateSync(BootstrappingSession session) throws SQLException, IOException {
        String sql = "UPDATE " + TABLE + " SET user_id=?, workspace_id=?, status=?, initial_idea=?, " +
                     "user_profile=?::jsonb, project_hints=?::jsonb, organization_context=?::jsonb, " +
                     "collaboration_settings=?::jsonb, project_definition=?::jsonb, project_graph=?::jsonb, " +
                     "validation_report=?::jsonb, conversation_history=?::jsonb, transition_data=?::jsonb, " +
                     "metadata=?::jsonb, created_at=?, updated_at=?, approved_at=?, last_activity_at=? " +
                     "WHERE tenant_id=? AND id=?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            int idx = 1;
            ps.setString(idx++, session.getUserId());
            ps.setString(idx++, session.getWorkspaceId());
            ps.setString(idx++, session.getStatus().name());
            ps.setString(idx++, session.getInitialIdea());
            ps.setString(idx++, session.getUserProfile() != null ? mapper.writeValueAsString(session.getUserProfile()) : null);
            ps.setString(idx++, session.getProjectHints() != null ? mapper.writeValueAsString(session.getProjectHints()) : null);
            ps.setString(idx++, session.getOrganizationContext() != null ? mapper.writeValueAsString(session.getOrganizationContext()) : null);
            ps.setString(idx++, session.getCollaborationSettings() != null ? mapper.writeValueAsString(session.getCollaborationSettings()) : null);
            ps.setString(idx++, session.getProjectDefinition() != null ? mapper.writeValueAsString(session.getProjectDefinition()) : null);
            ps.setString(idx++, session.getProjectGraph() != null ? mapper.writeValueAsString(session.getProjectGraph()) : null);
            ps.setString(idx++, session.getValidationReport() != null ? mapper.writeValueAsString(session.getValidationReport()) : null);
            ps.setString(idx++, mapper.writeValueAsString(session.getConversationHistory()));
            ps.setString(idx++, session.getTransitionData() != null ? mapper.writeValueAsString(session.getTransitionData()) : null);
            ps.setString(idx++, mapper.writeValueAsString(session.getMetadata()));
            ps.setTimestamp(idx++, session.getCreatedAt() != null ? Timestamp.from(session.getCreatedAt()) : null);
            ps.setTimestamp(idx++, session.getUpdatedAt() != null ? Timestamp.from(session.getUpdatedAt()) : null);
            ps.setTimestamp(idx++, session.getApprovedAt() != null ? Timestamp.from(session.getApprovedAt()) : null);
            ps.setTimestamp(idx++, session.getLastActivityAt() != null ? Timestamp.from(session.getLastActivityAt()) : null);
            ps.setString(idx++, session.getTenantId());
            ps.setObject(idx++, session.getId());
            
            ps.executeUpdate();
            return session;
        }
    }

    @SuppressWarnings("unchecked")
    private BootstrappingSession mapRow(ResultSet rs) throws SQLException {
        try {
            BootstrappingSession session = new BootstrappingSession();
            session.setId(UUID.fromString(rs.getString("id")));
            session.setTenantId(rs.getString("tenant_id"));
            session.setUserId(rs.getString("user_id"));
            session.setWorkspaceId(rs.getString("workspace_id"));
            session.setStatus(SessionStatus.valueOf(rs.getString("status")));
            session.setInitialIdea(rs.getString("initial_idea"));
            
            String userProfileJson = rs.getString("user_profile");
            if (userProfileJson != null) {
                session.setUserProfile(mapper.readValue(userProfileJson, UserProfile.class));
            }
            
            String projectHintsJson = rs.getString("project_hints");
            if (projectHintsJson != null) {
                session.setProjectHints(mapper.readValue(projectHintsJson, ProjectHints.class));
            }
            
            String orgContextJson = rs.getString("organization_context");
            if (orgContextJson != null) {
                session.setOrganizationContext(mapper.readValue(orgContextJson, OrganizationContext.class));
            }
            
            String collabSettingsJson = rs.getString("collaboration_settings");
            if (collabSettingsJson != null) {
                session.setCollaborationSettings(mapper.readValue(collabSettingsJson, CollaborationSettings.class));
            }
            
            String projectDefJson = rs.getString("project_definition");
            if (projectDefJson != null) {
                session.setProjectDefinition(mapper.readValue(projectDefJson, ProjectDefinition.class));
            }
            
            String projectGraphJson = rs.getString("project_graph");
            if (projectGraphJson != null) {
                session.setProjectGraph(mapper.readValue(projectGraphJson, ProjectGraph.class));
            }
            
            String validationJson = rs.getString("validation_report");
            if (validationJson != null) {
                session.setValidationReport(mapper.readValue(validationJson, ValidationReport.class));
            }
            
            String conversationJson = rs.getString("conversation_history");
            if (conversationJson != null) {
                session.setConversationHistory(mapper.readValue(conversationJson, 
                    mapper.getTypeFactory().constructCollectionType(List.class, ConversationTurn.class)));
            }
            
            String transitionJson = rs.getString("transition_data");
            if (transitionJson != null) {
                session.setTransitionData(mapper.readValue(transitionJson, TransitionData.class));
            }
            
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                session.setMetadata(mapper.readValue(metadataJson, Map.class));
            }
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) session.setCreatedAt(createdAt.toInstant());
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) session.setUpdatedAt(updatedAt.toInstant());
            
            Timestamp approvedAt = rs.getTimestamp("approved_at");
            if (approvedAt != null) session.setApprovedAt(approvedAt.toInstant());
            
            Timestamp lastActivityAt = rs.getTimestamp("last_activity_at");
            if (lastActivityAt != null) session.setLastActivityAt(lastActivityAt.toInstant());
            
            return session;
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON columns", e);
        }
    }
}
