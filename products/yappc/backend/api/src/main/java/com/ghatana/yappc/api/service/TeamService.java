/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Team;
import com.ghatana.yappc.api.domain.Team.*;
import com.ghatana.yappc.api.repository.TeamRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing teams.
 *
 * @doc.type class
 * @doc.purpose Business logic for team operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class TeamService {

    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository repository;
    private final AuditService auditService;

    @Inject
    public TeamService(TeamRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Creates a new team.
     */
    public Promise<Team> createTeam(String tenantId, CreateTeamInput input) {
        logger.info("Creating team: {} for tenant: {}", input.name(), tenantId);

        Team team = new Team();
        team.setTenantId(tenantId);
        team.setOrganizationId(input.organizationId());
        team.setName(input.name());
        team.setDescription(input.description());
        team.setType(input.type());
        team.setVisibility(input.visibility());
        team.setTimezone(input.timezone() != null ? input.timezone() : "UTC");

        // Add creator as owner
        if (input.creatorId() != null) {
            TeamMember owner = new TeamMember();
            owner.setUserId(input.creatorId());
            owner.setRole(MemberRole.OWNER);
            owner.setJoinedAt(Instant.now());
            team.addMember(owner);
        }

        return repository.save(team);
    }

    /**
     * Gets a team by ID.
     */
    public Promise<Optional<Team>> getTeam(String tenantId, UUID teamId) {
        return repository.findById(tenantId, teamId);
    }

    /**
     * Lists teams for an organization.
     */
    public Promise<List<Team>> listOrganizationTeams(String tenantId, String organizationId) {
        return repository.findByOrganization(tenantId, organizationId);
    }

    /**
     * Lists teams a user belongs to.
     */
    public Promise<List<Team>> listUserTeams(String tenantId, String userId) {
        return repository.findByMember(tenantId, userId);
    }

    /**
     * Updates a team.
     */
    public Promise<Team> updateTeam(String tenantId, UUID teamId, UpdateTeamInput input) {
        return repository.findById(tenantId, teamId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Team not found"));
                    }
                    Team team = opt.get();

                    if (input.name() != null) team.setName(input.name());
                    if (input.description() != null) team.setDescription(input.description());
                    if (input.visibility() != null) team.setVisibility(input.visibility());
                    if (input.timezone() != null) team.setTimezone(input.timezone());
                    team.setUpdatedAt(Instant.now());

                    return repository.save(team);
                });
    }

    /**
     * Adds a member to a team.
     */
    public Promise<Team> addMember(String tenantId, UUID teamId, AddMemberInput input) {
        return repository.findById(tenantId, teamId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Team not found"));
                    }
                    Team team = opt.get();

                    if (team.hasMember(input.userId())) {
                        return Promise.ofException(new IllegalStateException("User is already a member"));
                    }

                    TeamMember member = new TeamMember();
                    member.setUserId(input.userId());
                    member.setEmail(input.email());
                    member.setDisplayName(input.displayName());
                    member.setRole(input.role() != null ? input.role() : MemberRole.MEMBER);
                    member.setJoinedAt(Instant.now());

                    team.addMember(member);
                    return repository.save(team);
                });
    }

    /**
     * Updates a member's role.
     */
    public Promise<Team> updateMemberRole(String tenantId, UUID teamId, String userId, MemberRole newRole) {
        return repository.findById(tenantId, teamId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Team not found"));
                    }
                    Team team = opt.get();

                    TeamMember member = team.getMember(userId);
                    if (member == null) {
                        return Promise.ofException(new IllegalArgumentException("Member not found"));
                    }

                    // Prevent removing the last owner
                    if (member.getRole() == MemberRole.OWNER && newRole != MemberRole.OWNER) {
                        long ownerCount = team.getMembers().stream()
                                .filter(m -> m.getRole() == MemberRole.OWNER)
                                .count();
                        if (ownerCount <= 1) {
                            return Promise.ofException(new IllegalStateException("Cannot remove the last owner"));
                        }
                    }

                    member.setRole(newRole);
                    team.setUpdatedAt(Instant.now());
                    return repository.save(team);
                });
    }

    /**
     * Removes a member from a team.
     */
    public Promise<Team> removeMember(String tenantId, UUID teamId, String userId) {
        return repository.findById(tenantId, teamId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Team not found"));
                    }
                    Team team = opt.get();

                    TeamMember member = team.getMember(userId);
                    if (member == null) {
                        return Promise.ofException(new IllegalArgumentException("Member not found"));
                    }

                    // Prevent removing the last owner
                    if (member.getRole() == MemberRole.OWNER) {
                        long ownerCount = team.getMembers().stream()
                                .filter(m -> m.getRole() == MemberRole.OWNER)
                                .count();
                        if (ownerCount <= 1) {
                            return Promise.ofException(new IllegalStateException("Cannot remove the last owner"));
                        }
                    }

                    team.removeMember(userId);
                    return repository.save(team);
                });
    }

    /**
     * Deletes a team.
     */
    public Promise<Boolean> deleteTeam(String tenantId, UUID teamId) {
        return repository.delete(tenantId, teamId);
    }

    /**
     * Gets team statistics.
     */
    public Promise<TeamStatistics> getTeamStatistics(String tenantId, UUID teamId) {
        return repository.findById(tenantId, teamId)
                .map(opt -> {
                    if (opt.isEmpty()) {
                        return new TeamStatistics(0, 0, 0, 0, 0);
                    }
                    Team team = opt.get();
                    int totalMembers = team.getMembers().size();
                    int owners = (int) team.getMembers().stream()
                            .filter(m -> m.getRole() == MemberRole.OWNER)
                            .count();
                    int admins = (int) team.getMembers().stream()
                            .filter(m -> m.getRole() == MemberRole.ADMIN)
                            .count();
                    int members = (int) team.getMembers().stream()
                            .filter(m -> m.getRole() == MemberRole.MEMBER)
                            .count();
                    int guests = (int) team.getMembers().stream()
                            .filter(m -> m.getRole() == MemberRole.GUEST)
                            .count();
                    return new TeamStatistics(totalMembers, owners, admins, members, guests);
                });
    }

    // ========== Input/Output DTOs ==========

    public record CreateTeamInput(
            String organizationId,
            String name,
            String description,
            TeamType type,
            TeamVisibility visibility,
            String timezone,
            String creatorId
    ) {}

    public record UpdateTeamInput(
            String name,
            String description,
            TeamVisibility visibility,
            String timezone
    ) {}

    public record AddMemberInput(
            String userId,
            String email,
            String displayName,
            MemberRole role
    ) {}

    public record TeamStatistics(
            int totalMembers,
            int owners,
            int admins,
            int members,
            int guests
    ) {}
}
