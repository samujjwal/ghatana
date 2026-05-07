package com.ghatana.aep.kernel;

import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.Map;

/**
 * Canonical {@link KernelCapability} constants contributed by the AEP kernel bridge.
 *
 * @doc.type class
 * @doc.purpose Typed KernelCapability constants for AEP event and agent capabilities
 * @doc.layer adapter
 * @doc.pattern Constants
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class AepBridgeCapabilities {

    /**
     * Capability indicating that a live AEP adapter is registered in the kernel context.
     *
     * <p>When present, callers may safely call:
     * {@code context.getDependency(AepKernelAdapter.class)}</p>
     */
    public static final KernelCapability AEP_EVENT_STREAMING = new KernelCapability(
        "aep.event-streaming",
        "AEP Event Streaming",
        "Live AEP event stream adapter registered in kernel context",
        KernelCapability.CapabilityType.EVENT_PROCESSING,
        Map.of("is_shared", "true", "scope", "platform")
    );

    /**
     * Capability indicating that AEP agent deployment and management is available.
     */
    public static final KernelCapability AEP_AGENT_RUNTIME = new KernelCapability(
        "aep.agent-runtime",
        "AEP Agent Runtime",
        "AEP agent deployment and lifecycle management via kernel context",
        KernelCapability.CapabilityType.AI_ML,
        Map.of("is_shared", "true", "scope", "platform")
    );

    /**
     * Capability indicating that AEP pipeline orchestration is available.
     */
    public static final KernelCapability AEP_PIPELINE_ORCHESTRATION = new KernelCapability(
        "aep.pipeline-orchestration",
        "AEP Pipeline Orchestration",
        "AEP pipeline creation and execution via kernel context",
        KernelCapability.CapabilityType.WORKFLOW,
        Map.of("is_shared", "true", "scope", "platform")
    );

    private AepBridgeCapabilities() {
        // constants only
    }
}
