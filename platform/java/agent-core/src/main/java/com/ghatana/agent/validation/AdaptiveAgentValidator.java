/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Validates adaptive agent definitions for learning level and adaptation metadata
 * @doc.layer agent-core
 * @doc.pattern Validator
 */
public final class AdaptiveAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();

        // Extract and validate learning level
        String learningLevelStr = definition.getLearningLevel() != null
                ? definition.getLearningLevel()
                : String.valueOf(metadata.getOrDefault("learningLevel", "L0"));
        LearningLevel learningLevel;
        try {
            learningLevel = LearningLevel.valueOf(learningLevelStr);
        } catch (IllegalArgumentException e) {
            errors.add("[type:adaptive] invalid learning level: " + learningLevelStr);
            return errors;
        }

        // L2+ required for adaptive agents
        if (learningLevel.ordinal() < LearningLevel.L2.ordinal()) {
            errors.add("[type:adaptive] learning level must be L2 or higher, got: " + learningLevel);
        }

        // Validate adaptation targets match LearningTarget enum
        if (!metadata.containsKey("adaptationTargets")) {
            errors.add("[type:adaptive] adaptation targets are required");
        } else {
            Object adaptationTargetsObj = metadata.get("adaptationTargets");
            if (adaptationTargetsObj instanceof List<?> targetsList) {
                for (Object targetObj : targetsList) {
                    if (targetObj instanceof String targetStr) {
                        try {
                            LearningTarget.valueOf(targetStr);
                        } catch (IllegalArgumentException e) {
                            errors.add("[type:adaptive] invalid adaptation target: " + targetStr);
                        }
                    } else {
                        errors.add("[type:adaptive] adaptation targets must be strings");
                    }
                }
            } else {
                errors.add("[type:adaptive] adaptation targets must be a list");
            }
        }

        // Validate drift controls
        if (!metadata.containsKey("driftControls")) {
            errors.add("[type:adaptive] drift controls are required");
        } else {
            Object driftControlsObj = metadata.get("driftControls");
            if (driftControlsObj instanceof Map<?, ?> driftControls) {
                if (!driftControls.containsKey("enabled")) {
                    errors.add("[type:adaptive] drift controls must have 'enabled' field");
                } else if (!(driftControls.get("enabled") instanceof Boolean)) {
                    errors.add("[type:adaptive] drift controls 'enabled' must be boolean");
                }
            } else {
                errors.add("[type:adaptive] drift controls must be a map");
            }
        }

        // L3+ requires promotionRequired=true
        if (learningLevel.ordinal() >= LearningLevel.L3.ordinal()) {
            Object promotionRequired = metadata.get("promotionRequired");
            if (promotionRequired == null) {
                errors.add("[type:adaptive] promotionRequired is required for L3+ learning level");
            } else if (!(promotionRequired instanceof Boolean) || !((Boolean) promotionRequired)) {
                errors.add("[type:adaptive] promotionRequired must be true for L3+ learning level");
            }
        }

        // L2+ requires provenanceRequired=true
        if (learningLevel.ordinal() >= LearningLevel.L2.ordinal()) {
            Object provenanceRequired = metadata.get("provenanceRequired");
            if (provenanceRequired == null) {
                errors.add("[type:adaptive] provenanceRequired is required for L2+ learning level");
            } else if (!(provenanceRequired instanceof Boolean) || !((Boolean) provenanceRequired)) {
                errors.add("[type:adaptive] provenanceRequired must be true for L2+ learning level");
            }
        }

        // L5 must be offline-only
        if (learningLevel == LearningLevel.L5) {
            Object offlineOnly = metadata.get("offlineOnly");
            if (offlineOnly == null) {
                errors.add("[type:adaptive] offlineOnly is required for L5 learning level");
            } else if (!(offlineOnly instanceof Boolean) || !((Boolean) offlineOnly)) {
                errors.add("[type:adaptive] offlineOnly must be true for L5 learning level");
            }
        }

        return errors;
    }
}
