package com.ghatana.agent.memory.security;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Redaction filter that strips or masks sensitive content from memory items
 * before storage or retrieval.
 *
 * <p>Applies configurable patterns for:
 * <ul>
 *   <li>PII (emails, phone numbers, SSNs)</li>
 *   <li>Credentials (API keys, tokens, passwords)</li>
 *   <li>PHI (medical record numbers)</li>
 *   <li>Custom patterns via {@link RedactionPatternProvider} SPI</li>
 * </ul>
 *
 * <p>This filter is applied as a pre-write hook in the memory pipeline.
 * Patterns are sourced from one or more {@link RedactionPatternProvider}
 * instances, allowing externalization to YAML, databases, or configuration
 * services.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Default (built-in patterns):
 * MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter();
 *
 * // Custom patterns from YAML:
 * RedactionPatternProvider yaml = YamlRedactionPatternProvider.fromClasspath("/redaction.yaml");
 * MemoryRedactionFilter filter = new MemoryRedactionFilter(true, true, yaml);
 *
 * // Multiple providers:
 * MemoryRedactionFilter filter = new MemoryRedactionFilter(true, true,
 *     DefaultRedactionPatternProvider.instance(), customProvider);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Content redaction for memory items with pluggable pattern sources
 * @doc.layer agent-memory
 */
public class MemoryRedactionFilter {

    private static final Logger log = LoggerFactory.getLogger(MemoryRedactionFilter.class);

    private final boolean redactPii;
    private final boolean redactCredentials;
    private final String replacementToken;
    private final List<Pattern> piiPatterns;
    private final List<Pattern> credentialPatterns;
    private final List<RedactionPatternProvider> providers;

    /**
     * Creates a filter with the default built-in patterns.
     *
     * @param redactPii         whether to redact PII patterns
     * @param redactCredentials whether to redact credential patterns
     */
    public MemoryRedactionFilter(boolean redactPii, boolean redactCredentials) {
        this(redactPii, redactCredentials, DefaultRedactionPatternProvider.instance());
    }

    /**
     * Creates a filter using patterns from the given providers.
     * Patterns from all providers are aggregated.
     *
     * @param redactPii         whether to redact PII patterns
     * @param redactCredentials whether to redact credential patterns
     * @param providers         one or more pattern providers
     */
    public MemoryRedactionFilter(
            boolean redactPii,
            boolean redactCredentials,
            @NotNull RedactionPatternProvider... providers) {
        this.redactPii = redactPii;
        this.redactCredentials = redactCredentials;
        this.providers = List.of(providers);

        // Aggregate patterns from all providers
        List<Pattern> allPii = new ArrayList<>();
        List<Pattern> allCred = new ArrayList<>();
        String token = "[REDACTED]";

        for (RedactionPatternProvider provider : providers) {
            Objects.requireNonNull(provider, "provider must not be null");
            allPii.addAll(provider.piiPatterns());
            allCred.addAll(provider.credentialPatterns());
            // Use replacement token from first provider that specifies one
            if (!"[REDACTED]".equals(provider.replacementToken())) {
                token = provider.replacementToken();
            }
            log.debug("Loaded patterns from '{}': {} PII, {} credential",
                    provider.providerName(),
                    provider.piiPatterns().size(),
                    provider.credentialPatterns().size());
        }

        this.piiPatterns = Collections.unmodifiableList(allPii);
        this.credentialPatterns = Collections.unmodifiableList(allCred);
        this.replacementToken = token;

        log.info("MemoryRedactionFilter initialized: {} PII patterns, {} credential patterns, " +
                        "{} providers, replacement='{}'",
                allPii.size(), allCred.size(), providers.length, this.replacementToken);
    }

    /**
     * Creates a default filter that redacts both PII and credentials
     * using the built-in patterns.
     */
    public static MemoryRedactionFilter defaultFilter() {
        return new MemoryRedactionFilter(true, true);
    }

    /**
     * Redacts sensitive content from the given text.
     *
     * @param text The text to redact
     * @return Redacted text with sensitive content replaced
     */
    @NotNull
    public String redact(@NotNull String text) {
        String result = text;

        if (redactPii) {
            for (Pattern pattern : piiPatterns) {
                String before = result;
                result = pattern.matcher(result).replaceAll(replacementToken);
                if (!result.equals(before)) {
                    log.debug("PII pattern matched and redacted");
                }
            }
        }

        if (redactCredentials) {
            for (Pattern pattern : credentialPatterns) {
                String before = result;
                result = pattern.matcher(result).replaceAll(replacementToken);
                if (!result.equals(before)) {
                    log.debug("Credential pattern matched and redacted");
                }
            }
        }

        return result;
    }

    /**
     * Checks whether text contains patterns that should be redacted.
     *
     * @param text The text to check
     * @return true if sensitive content is detected
     */
    public boolean containsSensitiveContent(@NotNull String text) {
        if (redactPii) {
            for (Pattern pattern : piiPatterns) {
                if (pattern.matcher(text).find()) return true;
            }
        }
        if (redactCredentials) {
            for (Pattern pattern : credentialPatterns) {
                if (pattern.matcher(text).find()) return true;
            }
        }
        return false;
    }

    /**
     * Returns the number of PII patterns loaded.
     */
    public int piiPatternCount() {
        return piiPatterns.size();
    }

    /**
     * Returns the number of credential patterns loaded.
     */
    public int credentialPatternCount() {
        return credentialPatterns.size();
    }

    /**
     * Returns the replacement token used for redacted content.
     */
    @NotNull
    public String getReplacementToken() {
        return replacementToken;
    }

    /**
     * Returns an unmodifiable list of the loaded pattern providers.
     */
    @NotNull
    public List<RedactionPatternProvider> getProviders() {
        return providers;
    }
}
