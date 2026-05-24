package com.ghatana.aep.agent.capability;

import java.util.List;

/**
 * @doc.type interface
 * @doc.purpose Describes capabilities exposed by non-AEP agent systems
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface ExternalAgentProvider {

    String providerId();

    List<CapabilityDescriptor> capabilities();
}
