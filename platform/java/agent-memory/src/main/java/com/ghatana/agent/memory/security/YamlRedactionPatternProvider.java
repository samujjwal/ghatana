/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.memory.security;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads redaction patterns from a YAML configuration file.
 *
 * <h2>YAML Format</h2>
 * <pre>{@code
 * replacementToken: "[REDACTED]"
 * pii:
 *   - "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
 *   - "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"
 * credentials:
 *   - "(?i)(api[_-]?key|apikey)\\s*[:=]\\s*\\S+"
 *   - "(?i)bearer\\s+[a-zA-Z0-9\\-._~+/]+=*"
 * }</pre>
 *
 * <p>The YAML is parsed manually (no Jackson dependency required) using
 * a simple line-based parser suitable for the flat list structure.
 *
 * @doc.type class
 * @doc.purpose YAML-based redaction pattern provider
 * @doc.layer agent-memory
 * @doc.pattern Strategy, Configuration
 *
 * @since 3.0.0
 */
public final class YamlRedactionPatternProvider implements RedactionPatternProvider {

    private static final Logger log = LoggerFactory.getLogger(YamlRedactionPatternProvider.class);

    private final String name;
    private final List<Pattern> piiPatterns;
    private final List<Pattern> credentialPatterns;
    private final String replacementToken;

    private YamlRedactionPatternProvider(
            String name,
            List<Pattern> piiPatterns,
            List<Pattern> credentialPatterns,
            String replacementToken) {
        this.name = name;
        this.piiPatterns = List.copyOf(piiPatterns);
        this.credentialPatterns = List.copyOf(credentialPatterns);
        this.replacementToken = replacementToken;
    }

    /**
     * Loads patterns from a YAML file on the filesystem.
     *
     * @param yamlPath path to the YAML file
     * @return a configured provider
     * @throws IOException if the file cannot be read or parsed
     */
    @NotNull
    public static YamlRedactionPatternProvider fromPath(@NotNull Path yamlPath) throws IOException {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            return parse(yamlPath.getFileName().toString(), is);
        }
    }

    /**
     * Loads patterns from a classpath resource.
     *
     * @param resourcePath classpath resource path (e.g., "/redaction-patterns.yaml")
     * @return a configured provider
     * @throws IOException if the resource cannot be found or parsed
     */
    @NotNull
    public static YamlRedactionPatternProvider fromClasspath(@NotNull String resourcePath) throws IOException {
        InputStream is = YamlRedactionPatternProvider.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        try (is) {
            return parse(resourcePath, is);
        }
    }

    /**
     * Creates a provider from pre-built pattern lists (for programmatic construction).
     *
     * @param piiPatterns        PII regex strings
     * @param credentialPatterns credential regex strings
     * @param replacementToken   the replacement token (null → default)
     * @return a configured provider
     */
    @NotNull
    public static YamlRedactionPatternProvider of(
            @NotNull List<String> piiPatterns,
            @NotNull List<String> credentialPatterns,
            String replacementToken) {
        return new YamlRedactionPatternProvider(
                "programmatic",
                compilePatterns(piiPatterns),
                compilePatterns(credentialPatterns),
                replacementToken != null ? replacementToken : "[REDACTED]");
    }

    @Override
    @NotNull
    public List<Pattern> piiPatterns() {
        return piiPatterns;
    }

    @Override
    @NotNull
    public List<Pattern> credentialPatterns() {
        return credentialPatterns;
    }

    @Override
    @NotNull
    public String replacementToken() {
        return replacementToken;
    }

    @Override
    @NotNull
    public String providerName() {
        return "yaml:" + name;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // YAML Parsing (lightweight, no Jackson dependency)
    // ═══════════════════════════════════════════════════════════════════════════

    private static YamlRedactionPatternProvider parse(String sourceName, InputStream is) throws IOException {
        String content = new String(is.readAllBytes());
        String[] lines = content.split("\\R");

        List<String> piiRegexes = new ArrayList<>();
        List<String> credRegexes = new ArrayList<>();
        String replacement = "[REDACTED]";

        String currentSection = null;
        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            // Skip comments and empty lines
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;

            // Top-level key
            if (!line.startsWith(" ") && !line.startsWith("\t") && line.contains(":")) {
                String key = line.substring(0, line.indexOf(':')).trim().toLowerCase();
                String valueAfterColon = line.substring(line.indexOf(':') + 1).trim();

                if ("replacementtoken".equals(key.replace("_", "").replace("-", ""))) {
                    replacement = unquote(valueAfterColon);
                    currentSection = null;
                } else if (key.equals("pii")) {
                    currentSection = "pii";
                } else if (key.equals("credentials")) {
                    currentSection = "credentials";
                } else {
                    currentSection = null;
                }
                continue;
            }

            // List item under current section
            if (currentSection != null && line.stripLeading().startsWith("- ")) {
                String value = unquote(line.stripLeading().substring(2).trim());
                if (!value.isEmpty()) {
                    if ("pii".equals(currentSection)) {
                        piiRegexes.add(value);
                    } else if ("credentials".equals(currentSection)) {
                        credRegexes.add(value);
                    }
                }
            }
        }

        log.info("Loaded redaction patterns from '{}': {} PII, {} credential patterns",
                sourceName, piiRegexes.size(), credRegexes.size());

        return new YamlRedactionPatternProvider(
                sourceName,
                compilePatterns(piiRegexes),
                compilePatterns(credRegexes),
                replacement);
    }

    private static String unquote(String s) {
        if (s.length() >= 2) {
            if ((s.startsWith("\"") && s.endsWith("\"")) ||
                    (s.startsWith("'") && s.endsWith("'"))) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static List<Pattern> compilePatterns(List<String> regexes) {
        List<Pattern> compiled = new ArrayList<>(regexes.size());
        for (String regex : regexes) {
            try {
                compiled.add(Pattern.compile(regex));
            } catch (Exception e) {
                log.warn("Invalid regex pattern '{}': {}", regex, e.getMessage());
            }
        }
        return compiled;
    }
}
