package com.ghatana.yappc.ai;

import java.util.Objects;

/**
 * Versioned prompt template model.
 *
 * @doc.type class
 * @doc.purpose Represents one version/variant of a prompt template
 * @doc.layer service
 * @doc.pattern Model
 */
public record PromptTemplateVersion(
        String key,
        String version,
        String variant,
        String template,
        int weight
) {
    public PromptTemplateVersion {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(variant, "variant must not be null");
        Objects.requireNonNull(template, "template must not be null");
        if (weight < 0) {
            throw new IllegalArgumentException("weight must be >= 0");
        }
    }

    public static PromptTemplateVersion of(
            String key,
            String version,
            String variant,
            String template,
            int weight
    ) {
        return new PromptTemplateVersion(key, version, variant, template, weight);
    }
}