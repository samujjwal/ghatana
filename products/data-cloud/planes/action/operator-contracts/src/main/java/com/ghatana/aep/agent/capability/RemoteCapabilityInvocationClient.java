package com.ghatana.aep.agent.capability;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Invokes a typed capability hosted by an external agent provider
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface RemoteCapabilityInvocationClient {

    <I, O> Promise<CapabilityResult<O>> invoke(CapabilityDescriptor descriptor, CapabilityInvocation<I> invocation, Class<O> outputType);
}
