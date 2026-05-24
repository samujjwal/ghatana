package com.ghatana.aep.agent.capability;

import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Resolves capability references to executable agent capabilities
 * @doc.layer product
 * @doc.pattern Resolver
 */
public interface CapabilityResolver {

    Optional<CapabilityDescriptor> describe(CapabilityId capabilityId);

    <I, O> Optional<AgentCapability<I, O>> resolve(CapabilityId capabilityId, Class<I> inputType, Class<O> outputType);
}
