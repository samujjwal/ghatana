package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.Team;
import com.ghatana.yappc.api.domain.Team.TeamMember;
import com.ghatana.yappc.api.domain.Team.TeamSettings;
import com.ghatana.yappc.api.repository.TeamRepository;
import io.activej.promise.Promise;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JdbcTeamRepository.
 *
 * @doc.type class
 * @doc.purpose jdbc team repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcTeamRepository implements TeamRepository {
    private static final Logger logger = LoggerFactory.getLogger(JdbcTeamRepository.class);
    private static final String TABLE = "yappc.teams";
    private static final String MEMBERS_TABLE = "yappc.team_members";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcTeamRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = Executors.newCachedThreadPool();
        this.mapper = mapper;
    }

    @Override
    public Promise<Team> save(Team team) {
        return Promise.ofBlocking(executor, () -> {
            if (team.getId() == null) {
                team.setId(UUID.randomUUID());
            }
            if (team.getCreatedAt() == null) team.setCreatedAt(Instant.now());
            team.setUpdatedAt(Instant.now());

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Upsert Team
                    upsertTeam(conn, team);
                    // Update Members (delete and insert for simplicity, or diff)
                    updateMembers(conn, team);
                    conn.commit();
                    return team;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }
        });
    }

    private void upsertTeam(Connection conn, Team team) throws SQLException, JsonProcessingException {
        String sql = "INSERT INTO " + TABLE + " (id, tenant_id, organization_id, name, description, type, visibility, timezone, working_hours, settings, metadata, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET " +
                     "name = EXCLUDED.name, description = EXCLUDED.description, type = EXCLUDED.type, " +
                     "visibility = EXCLUDED.visibility, timezone = EXCLUDED.timezone, working_hours = EXCLUDED.working_hours, " +
                     "settings = EXCLUDED.settings, metadata = EXCLUDED.metadata, updated_at = EXCLUDED.updated_at";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, team.getId());
            ps.setString(2, team.getTenantId());
            ps.setString(3, team.getOrganizationId());
            ps.setString(4, team.getName());
            ps.setString(5, team.getDescription());
            ps.setString(6, team.getType() != null ? team.getType().name() : "DELIVERY");
            ps.setString(7, team.getVisibility() != null ? team.getVisibility().name() : "PRIVATE");
            ps.setString(8, team.getTimezone());
            ps.setString(9, mapper.writeValueAsString(team.getWorkingHours()));
            ps.setString(10, mapper.writeValueAsString(team.getSettings()));
            ps.setString(11, mapper.writeValueAsString(team.getMetadata()));
            ps.setTimestamp(12, Timestamp.from(team.getCreatedAt()));
            ps.setTimestamp(13, Timestamp.from(team.getUpdatedAt()));
            ps.executeUpdate();
        }
    }

    private void updateMembers(Connection conn, Team team) throws SQLException {
        // Simple strategy: delete all for team and re-insert
        // A better strategy would be to diff, but this is acceptable for MVP
        // Note: This relies on team.getMembers() being the source of truth
        String deleteSql = "DELETE FROM " + MEMBERS_TABLE + " WHERE team_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setObject(1, team.getId());
            ps.executeUpdate();
        }

        if (team.getMembers() != null && !team.getMembers().isEmpty()) {
            String insertSql = "INSERT INTO " + MEMBERS_TABLE + " (team_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (TeamMember member : team.getMembers()) {
                    ps.setObject(1, team.getId());
                    ps.setObject(2, member.getUserId());
                    ps.setString(3, member.getRole().name());
                    ps.setTimestamp(4, Timestamp.from(Instant.now())); // joined_at logic could be better
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Override
    public Promise<Optional<Team>> findById(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToTeam(conn, rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Promise<List<Team>> findByOrganization(String tenantId, String organizationId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND organization_id = ?";
            List<Team> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, organizationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRowToTeam(conn, rs));
                    }
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<Team>> findByMember(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            // Join teams and team_members
            String sql = "SELECT t.* FROM " + TABLE + " t " +
                         "JOIN " + MEMBERS_TABLE + " tm ON t.id = tm.team_id " +
                         "WHERE t.tenant_id = ? AND tm.user_id = ?::uuid";
            List<Team> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, userId); // Ensure userId is valid UUID string or handle conversion
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRowToTeam(conn, rs));
                    }
                }
            }
            return results;
        });
    }

    private Team mapRowToTeam(Connection conn, ResultSet rs) throws SQLException, IOException {
        Team team = new Team();
        team.setId(rs.getObject("id", UUID.class));
        team.setTenantId(rs.getString("tenant_id"));
        team.setOrganizationId(rs.getString("organization_id"));
        team.setName(rs.getString("name"));
        team.setDescription(rs.getString("description"));
        // Enums
        // team.setType(...) 
        // team.setVisibility(...)
        team.setTimezone(rs.getString("timezone"));
        team.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        team.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());

        // JSONB fields
        String settingsJson = rs.getString("settings");
        if (settingsJson != null) team.setSettings(mapper.readValue(settingsJson, TeamSettings.class));

        // Load members
        team.setMembers(findMembers(conn, team.getId()));

        return team;
    }

    private List<TeamMember> findMembers(Connection conn, UUID teamId) throws SQLException {
        String sql = "SELECT * FROM " + MEMBERS_TABLE + " WHERE team_id = ?";
        List<TeamMember> members = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TeamMember member = new TeamMember();
                    member.setUserId(rs.getString("user_id"));
                    member.setEmail(rs.getString("email"));
                    member.setDisplayName(rs.getString("display_name"));
                    String roleStr = rs.getString("role");
                    if (roleStr != null) {
                        member.setRole(Team.MemberRole.valueOf(roleStr));
                    }
                    Timestamp joinedTs = rs.getTimestamp("joined_at");
                    if (joinedTs != null) {
                        member.setJoinedAt(joinedTs.toInstant());
                    }
                    members.add(member);
                }
            }
        }
        return members;
    }
    
    @Override
    public Promise<Boolean> exists(String tenantId, UUID teamId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                stmt.setObject(2, teamId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                logger.error("Error checking team existence", e);
                return false;
            }
        });
    }
    
    @Override
    public Promise<Boolean> delete(String tenantId, UUID teamId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                stmt.setObject(2, teamId);
                int rowsAffected = stmt.executeUpdate();
                logger.debug("Deleted team {} for tenant {}", teamId, tenantId);
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.error("Error deleting team", e);
                throw new RuntimeException("Failed to delete team", e);
            }
        });
    }
    
    @Override
    public Promise<List<Team>> findByType(String tenantId, Team.TeamType type) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND type = ?";
            List<Team> teams = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, type.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        teams.add(mapRowToTeam(conn, rs));
                    }
                }
            } catch (SQLException | IOException e) {
                logger.error("Error finding teams by type", e);
                throw new RuntimeException("Failed to find teams by type", e);
            }
            return teams;
        });
    }
}
