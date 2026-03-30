/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Shared — Agent Registry Port
 */
package com.ghatana.yappc.agent.spi;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Set;

/**
 * YAPPC-internal port interface for agent registry operations.
 *
 * <p>This port is the adapter seam between YAPPC domain logic and the underlying
 * agent registry implementation (e.g., AEP {@code AgentRegistryService}). All
 * YAPPC modules that need to register or query agents must depend on this port,
 * <em>not</em> directly on AEP registry classes.
 *
 * <p>Implementations live in the infrastructure layer (e.g.,
 * {@code yappc-infrastructure}) and adapt the external service to this contract.
 *
 * @doc.type interface
 * @doc.purpose YAPPC-internal port for agent manifest management
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture / Ports &amp; Adapters)
 */
public interface AgentRegistryPort {

    /**
     * Register an agent manifest for the given tenant.
     *
     * @param tenantId tenant scope
     * @param manifest the agent manifest to register
     * @return promise of the registered manifest (may include server-assigned fields)
     */
    Promise<AgentManifestProto> register(TenantId tenantId, AgentManifestProto manifest);

    /**
     * Retrieve an agent manifest by its identifier.
     *
     * @param tenantId tenant scope
     * @param agentId  the agent identifier
     * @return promise of the manifest, or failure if not found
     */
    Promise<AgentManifestProto> getById(TenantId tenantId, String agentId);

    /**
     * List all agent manifests for the given tenant.
     *
     * @param tenantId tenant scope
     * @return promise of the full manifest list
     */
    Promise<List<AgentManifestProto>> listAll(TenantId tenantId);

    /**
     * Find agents whose capabilities intersect the requested set.
     *
     * @param tenantId     tenant scope
     * @param capabilities required capability names
     * @return promise of matching manifests
     */
    Promise<List<AgentManifestProto>> findByCapabilities(TenantId tenantId, Set<String> capabilities);

    /**
     * Find agents that handle the given event type.
     *
     * @param tenantId    tenant scope
     * @param eventTypeId fully-qualified event type identifier
     * @return promise of matching manifests
     */
    Promise<List<AgentManifestProto>> findByEventType(TenantId tenantId, String eventTypeId);

    /**
     * Update an existing agent manifest.
     *
     * @param tenantId tenant scope
     * @param agentId  agent to update
     * @param manifest updated manifest
     * @return promise of the updated manifest
     */
    Promise<AgentManifestProto> update(TenantId tenantId, String agentId, AgentManifestProto manifest);

    /**
     * Delete an agent from the registry.
     *
     * @param tenantId   tenant scope
     * @param agentId    agent to delete
     * @param hardDelete if {@code true}, permanently remove; otherwise soft-delete
     * @return promise of deletion success flag
     */
    Promise<Boolean> delete(TenantId tenantId, String agentId, boolean hardDelete);
}
