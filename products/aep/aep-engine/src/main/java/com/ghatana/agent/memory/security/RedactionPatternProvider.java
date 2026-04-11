/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.memory.security;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * SPI for providing redaction patterns to the {@link MemoryRedactionFilter}.
 *
 * <p>Implementations can load patterns from YAML files, databases,
 * configuration services, or any other source. Multiple providers
 * can be composed — the filter aggregates patterns from all providers.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RedactionPatternProvider yamlProvider = YamlRedactionPatternProvider.fromClasspath("/redaction-patterns.yaml");
 * RedactionPatternProvider customProvider = () -> Map.of(
 *     RedactionCategory.PII, List.of(Pattern.compile("...")),
 *     RedactionCategory.CREDENTIAL, List.of(Pattern.compile("..."))
 * );
 *
 * MemoryRedactionFilter filter = new MemoryRedactionFilter(yamlProvider, customProvider);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose SPI for pluggable redaction pattern sources
 * @doc.layer agent-memory
 * @doc.pattern Strategy, SPI
 *
 * @since 3.0.0
 */
public interface RedactionPatternProvider {

    /**
     * Returns the patterns for PII detection (emails, phone numbers, SSNs, etc.).
     *
     * @return list of compiled regex patterns; empty list if none
     */
    @NotNull
    List<Pattern> piiPatterns();

    /**
     * Returns the patterns for credential detection (API keys, tokens, passwords, etc.).
     *
     * @return list of compiled regex patterns; empty list if none
     */
    @NotNull
    List<Pattern> credentialPatterns();

    /**
     * Returns the replacement string to use for redacted content.
     * Default is {@code [REDACTED]}.
     *
     * @return the replacement string
     */
    @NotNull
    default String replacementToken() {
        return "[REDACTED]";
    }

    /**
     * Provider name for logging and diagnostic purposes.
     *
     * @return human-readable provider name
     */
    @NotNull
    default String providerName() {
        return getClass().getSimpleName();
    }
}
