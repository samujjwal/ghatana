/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.memory.security;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Built-in redaction patterns for common PII and credential formats.
 *
 * <p>These are the default patterns that were previously hardcoded in
 * {@link MemoryRedactionFilter}. They cover:
 * <ul>
 *   <li>Email addresses</li>
 *   <li>US phone numbers (XXX-XXX-XXXX)</li>
 *   <li>US SSNs (XXX-XX-XXXX)</li>
 *   <li>Generic API keys/secrets</li>
 *   <li>Bearer tokens</li>
 *   <li>Passwords in key=value pairs</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Built-in default redaction patterns
 * @doc.layer agent-memory
 * @doc.pattern Strategy
 *
 * @since 3.0.0
 */
public final class DefaultRedactionPatternProvider implements RedactionPatternProvider {

    private static final DefaultRedactionPatternProvider INSTANCE = new DefaultRedactionPatternProvider();

    private static final List<Pattern> PII = List.of(
            // Email addresses
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            // US phone numbers
            Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"),
            // SSN
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    );

    private static final List<Pattern> CREDENTIALS = List.of(
            // API keys (generic)
            Pattern.compile("(?i)(api[_-]?key|apikey|api_secret|secret_key)\\s*[:=]\\s*\\S+"),
            // Bearer tokens
            Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9\\-._~+/]+=*"),
            // Passwords in key=value
            Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*\\S+")
    );

    private DefaultRedactionPatternProvider() {}

    /** Returns the singleton instance. */
    public static DefaultRedactionPatternProvider instance() {
        return INSTANCE;
    }

    @Override
    @NotNull
    public List<Pattern> piiPatterns() {
        return PII;
    }

    @Override
    @NotNull
    public List<Pattern> credentialPatterns() {
        return CREDENTIALS;
    }

    @Override
    @NotNull
    public String providerName() {
        return "built-in-defaults";
    }
}
