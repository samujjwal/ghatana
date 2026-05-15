/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config.materializer;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.framework.config.AgentDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Materializes an {@link AgentDescriptor} from an {@link AgentDefinition}.
 *
 * <p>This class is responsible for converting the canonical agent definition
 * into the runtime descriptor used by the agent execution layer.
 *
 * @doc.type class
 * @doc.purpose Materializes AgentDescriptor from AgentDefinition
 * @doc.layer platform
 * @doc.pattern Materializer
 */
public final class AgentDescriptorMaterializer {

    private AgentDescriptorMaterializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Materializes the runtime descriptor from a canonical agent definition.
     *
     * @param definition the agent definition
     * @return the materialized agent descriptor
     */
    @NotNull
    public static AgentDescriptor materialize(@NotNull AgentDefinition definition) {
        Map<String, Object> descriptorMetadata = new LinkedHashMap<>(definition.getMetadata());
        descriptorMetadata.put("specDigest", definition.canonicalDigest());
        descriptorMetadata.put("status", definition.getStatus());
        descriptorMetadata.put("roles", definition.getRoles());
        descriptorMetadata.put("personas", definition.getPersonas());
        descriptorMetadata.put("policyRefs", definition.getPolicyRefs());
        descriptorMetadata.put("evaluationRefs", definition.getEvaluationRefs());

        Map<String, String> descriptorLabels = new LinkedHashMap<>(definition.getLabels());
        if (definition.getCriticality() != null) {
            descriptorLabels.put("criticality", definition.getCriticality());
        }
        if (definition.getAutonomyLevel() != null) {
            descriptorLabels.put("autonomyLevel", definition.getAutonomyLevel());
        }
        if (definition.getLearningLevel() != null) {
            descriptorLabels.put("learningLevel", definition.getLearningLevel());
        }

        return AgentDescriptor.builder()
                .agentId(definition.getId())
                .name(definition.getName())
                .version(definition.getVersion())
                .description(definition.getDescription())
                .namespace(definition.getNamespace())
                .type(definition.getType())
                .subtype(definition.getSubtype())
                .determinism(definition.getDeterminism())
                .latencySla(definition.getTimeout())
                .stateMutability(definition.getStateMutability())
                .failureMode(definition.getFailureMode())
                .capabilities(definition.getCapabilities())
                .metadata(Map.copyOf(descriptorMetadata))
                .labels(Map.copyOf(descriptorLabels))
                .build();
    }
}
