/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Team;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Team entities.
 *
 * @doc.type interface
 * @doc.purpose Repository for team persistence
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface TeamRepository {

    Promise<Team> save(Team team);

    Promise<Optional<Team>> findById(String tenantId, UUID id);

    Promise<List<Team>> findByOrganization(String tenantId, String organizationId);

    Promise<List<Team>> findByMember(String tenantId, String userId);

    Promise<List<Team>> findByType(String tenantId, Team.TeamType type);

    Promise<Boolean> delete(String tenantId, UUID id);

    Promise<Boolean> exists(String tenantId, UUID id);
}
