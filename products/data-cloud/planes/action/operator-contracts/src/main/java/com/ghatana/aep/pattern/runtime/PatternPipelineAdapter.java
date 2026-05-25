package com.ghatana.aep.pattern.runtime;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.CapabilityResolver;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.pattern.spec.CompiledPattern;
import com.ghatana.aep.pattern.spec.PatternRuntimeNode;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineBuilder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adapts compiled PatternSpec runtime graphs into executable Pipeline DAG contracts
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class PatternPipelineAdapter {

    private static final String SOURCE_STAGE_ID = "eventcloud-source";
    private static final String SINK_STAGE_ID = "eventcloud-sink";
    private static final String DEFAULT_VERSION = "1.0.0";

    private PatternPipelineAdapter() {
    }

    /**
     * Adapts a compiled pattern while leaving capability references for runtime resolution.
     *
     * @param pattern compiled PatternSpec graph
     * @return executable pipeline DAG contract
     */
    public static Pipeline toPipeline(CompiledPattern pattern) {
        return toPipeline(pattern, null);
    }

    /**
     * Adapts a compiled pattern and resolves capability references into stage metadata.
     *
     * @param pattern compiled PatternSpec graph
     * @param capabilityResolver resolver for capabilityRef bindings, or null to defer binding
     * @return executable pipeline DAG contract
     */
    public static Pipeline toPipeline(CompiledPattern pattern, CapabilityResolver capabilityResolver) {
        Objects.requireNonNull(pattern, "pattern");
        String tenantId = String.valueOf(pattern.metadata().getOrDefault("tenantId", "ghatana"));
        PipelineBuilder builder = Pipeline.builder(pattern.runtimePlanId(), DEFAULT_VERSION)
            .name(pattern.patternId())
            .description("Compiled PatternSpec runtime pipeline for " + pattern.patternId())
            .metadata("tenantId", tenantId)
            .metadata("patternId", pattern.patternId())
            .metadata("compiledFrom", "PatternSpec")
            .metadata("lifecycle", pattern.lifecycle())
            .metadata("governance", pattern.governance())
            .metadata("semantics", pattern.semantics())
            .stage(SOURCE_STAGE_ID, OperatorId.of(tenantId, "eventcloud", "source", DEFAULT_VERSION),
                Map.of("role", "source"));

        addNodeStages(builder, tenantId, pattern.root(), capabilityResolver);
        addNodeEdges(builder, pattern.root());
        connectSourceToLeaves(builder, pattern.root());

        builder.stage(SINK_STAGE_ID, OperatorId.of(tenantId, "eventcloud", "sink", DEFAULT_VERSION),
            Map.of("role", "sink", "emit", pattern.emit()));
        builder.edge(pattern.root().nodeId(), SINK_STAGE_ID);
        return builder.build();
    }

    private static void addNodeStages(
            PipelineBuilder builder,
            String tenantId,
            PatternRuntimeNode node,
            CapabilityResolver capabilityResolver) {
        builder.stage(node.nodeId(), operatorId(tenantId, node), stageConfig(node, capabilityResolver));
        for (PatternRuntimeNode child : node.children()) {
            addNodeStages(builder, tenantId, child, capabilityResolver);
        }
    }

    private static void addNodeEdges(PipelineBuilder builder, PatternRuntimeNode node) {
        for (PatternRuntimeNode child : node.children()) {
            addNodeEdges(builder, child);
            builder.edge(child.nodeId(), node.nodeId());
        }
    }

    private static void connectSourceToLeaves(PipelineBuilder builder, PatternRuntimeNode node) {
        if (node.children().isEmpty()) {
            builder.edge(SOURCE_STAGE_ID, node.nodeId());
            return;
        }
        for (PatternRuntimeNode child : node.children()) {
            connectSourceToLeaves(builder, child);
        }
    }

    private static OperatorId operatorId(String tenantId, PatternRuntimeNode node) {
        String type = node.isAgentCapability() ? "agent-capability" : operatorType(node.operatorKind());
        String name = node.capabilityRef()
            .map(PatternPipelineAdapter::sanitize)
            .or(() -> node.agentRef().map(PatternPipelineAdapter::sanitize))
            .orElseGet(() -> operatorName(node));
        return OperatorId.of(tenantId, type, name, DEFAULT_VERSION);
    }

    private static String operatorType(OperatorKind kind) {
        if (kind == OperatorKind.EVENT_REF) {
            return "event";
        }
        if (kind == OperatorKind.LEARNING) {
            return "learning";
        }
        return "pattern";
    }

    private static String operatorName(PatternRuntimeNode node) {
        return node.eventType()
            .map(PatternPipelineAdapter::sanitize)
            .orElseGet(() -> node.operatorKind().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static Map<String, Object> stageConfig(PatternRuntimeNode node, CapabilityResolver capabilityResolver) {
        Map<String, Object> config = new LinkedHashMap<>(node.parameters());
        config.put("operatorKind", node.operatorKind().name());
        node.eventType().ifPresent(value -> config.put("eventType", value));
        node.agentRef().ifPresent(value -> config.put("agentRef", value));
        node.capabilityRef().ifPresent(value -> config.put("capabilityRef", value));
        node.outputSchema().ifPresent(value -> config.put("outputSchema", value));
        node.capabilityRef().ifPresent(value -> bindCapability(config, value, capabilityResolver));
        return config;
    }

    private static void bindCapability(
            Map<String, Object> config,
            String capabilityRef,
            CapabilityResolver capabilityResolver) {
        if (capabilityResolver == null) {
            config.put("capabilityBinding", "unresolved");
            return;
        }
        CapabilityDescriptor descriptor = capabilityResolver.describe(CapabilityId.of(capabilityRef))
            .orElseThrow(() -> new IllegalArgumentException("Unknown capabilityRef: " + capabilityRef));
        config.put("capabilityBinding", "resolved");
        config.put("capabilityKind", descriptor.kind().name());
        config.put("capabilityAgentRef", descriptor.agentRef());
        config.put("capabilityInputSchema", descriptor.inputSchema());
        config.put("capabilityOutputSchema", descriptor.outputSchema());
        config.put("capabilitySideEffectProfile", descriptor.sideEffectProfile().name());
        config.put("capabilityTags", descriptor.tags());
        config.put("capabilityMetadata", descriptor.metadata());
    }

    private static String sanitize(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT)
            .replaceAll("@", "-")
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (sanitized.isBlank()) {
            return "operator";
        }
        return sanitized;
    }
}
