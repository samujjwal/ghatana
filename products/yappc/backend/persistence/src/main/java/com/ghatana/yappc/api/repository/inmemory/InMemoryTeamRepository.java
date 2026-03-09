/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.yappc.api.domain.Team;
import com.ghatana.yappc.api.repository.TeamRepository;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of TeamRepository.
 *
 * @doc.type class
 * @doc.purpose In-memory storage for teams
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryTeamRepository implements TeamRepository {

    private final Map<String, Map<UUID, Team>> tenantTeams = new ConcurrentHashMap<>();

    private Map<UUID, Team> getTeamMap(String tenantId) {
        return tenantTeams.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Promise<Team> save(Team team) {
        if (team.getId() == null) {
            team.setId(UUID.randomUUID());
        }
        getTeamMap(team.getTenantId()).put(team.getId(), team);
        return Promise.of(team);
    }

    @Override
    public Promise<Optional<Team>> findById(String tenantId, UUID id) {
        return Promise.of(Optional.ofNullable(getTeamMap(tenantId).get(id)));
    }

    @Override
    public Promise<List<Team>> findByOrganization(String tenantId, String organizationId) {
        return Promise.of(
            getTeamMap(tenantId).values().stream()
                .filter(t -> organizationId.equals(t.getOrganizationId()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Team>> findByMember(String tenantId, String userId) {
        return Promise.of(
            getTeamMap(tenantId).values().stream()
                .filter(t -> t.getMembers() != null && 
                    t.getMembers().stream().anyMatch(m -> userId.equals(m.getUserId())))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Team>> findByType(String tenantId, Team.TeamType type) {
        return Promise.of(
            getTeamMap(tenantId).values().stream()
                .filter(t -> type == t.getType())
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Boolean> delete(String tenantId, UUID id) {
        Team removed = getTeamMap(tenantId).remove(id);
        return Promise.of(removed != null);
    }

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.of(getTeamMap(tenantId).containsKey(id));
    }
}
