/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure — AEP Adapter
 */
package com.ghatana.yappc.infrastructure.aep;

import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.yappc.agent.spi.AgentRegistryPort;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Adapts AEP's {@code AgentRegistryService} to YAPPC's {@link AgentRegistryPort}.
 *
 * <p>This adapter is the <em>only</em> place in YAPPC that directly references
 * the AEP registry. All other YAPPC modules should depend on
 * {@link AgentRegistryPort} and receive this adapter through dependency injection.
 *
 * @doc.type class
 * @doc.purpose Adapter from AEP AgentRegistryService to YAPPC AgentRegistryPort
 * @doc.layer product
 * @doc.pattern Adapter (Ports &amp; Adapters)
 */
public class AepAgentRegistryAdapter implements AgentRegistryPort {

    private static final Logger log = LoggerFactory.getLogger(AepAgentRegistryAdapter.class);

    private final AgentRegistryService delegate;

    /**
     * @param delegate the underlying AEP {@code AgentRegistryService}
     */
    @Inject
    public AepAgentRegistryAdapter(AgentRegistryService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Promise<AgentManifestProto> register(TenantId tenantId, AgentManifestProto manifest) {
        log.debug("Registering agent '{}' for tenant '{}'",
                manifest.getMetadata().getId(), tenantId);
        return delegate.register(tenantId, manifest);
    }

    @Override
    public Promise<AgentManifestProto> getById(TenantId tenantId, String agentId) {
        return delegate.getById(tenantId, agentId);
    }

    @Override
    public Promise<List<AgentManifestProto>> listAll(TenantId tenantId) {
        return delegate.listAll(tenantId);
    }

    @Override
    public Promise<List<AgentManifestProto>> findByCapabilities(TenantId tenantId, Set<String> capabilities) {
        return delegate.findByCapabilities(tenantId, capabilities);
    }

    @Override
    public Promise<List<AgentManifestProto>> findByEventType(TenantId tenantId, String eventTypeId) {
        return delegate.findByEventType(tenantId, eventTypeId);
    }

    @Override
    public Promise<AgentManifestProto> update(TenantId tenantId, String agentId, AgentManifestProto manifest) {
        log.debug("Updating agent '{}' for tenant '{}'", agentId, tenantId);
        return delegate.update(tenantId, agentId, manifest);
    }

    @Override
    public Promise<Boolean> delete(TenantId tenantId, String agentId, boolean hardDelete) {
        log.debug("Deleting agent '{}' for tenant '{}' (hard={})", agentId, tenantId, hardDelete);
        return delegate.delete(tenantId, agentId, hardDelete);
    }
}
