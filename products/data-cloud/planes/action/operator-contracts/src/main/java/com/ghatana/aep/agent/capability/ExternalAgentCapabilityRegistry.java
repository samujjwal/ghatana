package com.ghatana.aep.agent.capability;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Registry of capability metadata supplied by external agent providers
 * @doc.layer product
 * @doc.pattern Registry
 */
public interface ExternalAgentCapabilityRegistry {

    void register(ExternalAgentProvider provider);

    Optional<CapabilityDescriptor> find(CapabilityId capabilityId);

    List<CapabilityDescriptor> list();
}
