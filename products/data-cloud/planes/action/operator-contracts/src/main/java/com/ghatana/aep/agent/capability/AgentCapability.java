package com.ghatana.aep.agent.capability;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Canonical executable capability exposed by a TypedAgent
 * @doc.layer product
 * @doc.pattern Capability
 */
public interface AgentCapability<I, O> {

    CapabilityId capabilityId();

    CapabilityDescriptor descriptor();

    Promise<CapabilityResult<O>> invoke(CapabilityInvocation<I> invocation);
}
