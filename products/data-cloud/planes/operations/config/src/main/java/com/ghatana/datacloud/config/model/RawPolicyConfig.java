/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Map;

/**
 * Raw policy configuration as parsed from YAML.
 * Policies define rules for encryption, masking, auditing, rate limiting, and validation.
 *
 * @doc.type record
 * @doc.purpose Raw YAML-parsed policy configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RawPolicyConfig(
        String apiVersion,
        String kind,
        RawPolicyMetadata metadata,
        RawPolicySpec spec) {

    /**
     * Policy metadata including name, namespace, and labels.
     */
    public record RawPolicyMetadata(
            String name,
            String namespace,
            Map<String, String> labels,
            Map<String, String> annotations) {
    }

    /**
     * Policy specification including selectors and rules.
     */
    public record RawPolicySpec(
            String displayName,
            String description,
            RawPolicySelector selector,
            List<RawPolicyRule> rules,
            Integer priority,
            Boolean enabled) {
    }

    /**
     * Policy selector for matching collections/fields.
     */
    public record RawPolicySelector(
            Boolean matchAll,
            Map<String, String> matchLabels,
            RawFieldSelector matchFields,
            List<String> matchCollections,
            String matchExpression) {
    }

    /**
     * Field-level selector for policies that target specific fields.
     */
    public record RawFieldSelector(
            Map<String, String> annotations,
            List<String> fieldNames,
            List<String> fieldTypes) {
    }

    /**
     * Individual policy rule definition.
     */
    public record RawPolicyRule(
            String name,
            String type,
            List<String> trigger,
            String condition,
            Map<String, Object> config,
            Boolean enabled) {
    }
}
