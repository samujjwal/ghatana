/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.platform.dscli.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds DTCG-compliant CSS custom properties and JSON outputs from token files.
 *
 * @doc.type class
 * @doc.purpose Transforms DTCG token files into CSS custom properties and platform outputs.
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class TokenBuilder {

    private static final Logger log = LoggerFactory.getLogger(TokenBuilder.class);
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^\\{([\\w.]+)\\}$");

    /**
     * Output format for token builds.
     */
    public enum OutputFormat {
        CSS,
        JSON,
        TS
    }

    /**
     * Result of a token build.
     */
    public record BuildResult(
            String content,
            OutputFormat format,
            int tokenCount,
            List<String> warnings) {}

    /**
     * Builds tokens into the specified output format.
     *
     * @param files      source token files
     * @param format     target output format
     * @param themeName  CSS theme scope (for CSS output), e.g. ":root" or "[data-theme='dark']"
     * @return build result
     */
    public BuildResult build(
            final List<TokenFileLoader.LoadedTokenFile> files,
            final OutputFormat format,
            final String themeName) {

        final Map<String, Object> merged = mergeFiles(files);
        final List<String> warnings = new ArrayList<>();
        final Map<String, String> resolved = resolveAll(merged, merged, warnings);

        return switch (format) {
            case CSS -> buildCss(resolved, themeName, warnings);
            case JSON -> buildJson(resolved, warnings);
            case TS -> buildTypeScript(resolved, warnings);
        };
    }

    /**
     * Writes the build result to an output file.
     *
     * @param result build result to write
     * @param output target file path
     * @throws IOException if writing fails
     */
    public void writeTo(final BuildResult result, final Path output) throws IOException {
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.content());
        log.info("Written {} tokens to {}", result.tokenCount(), output);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeFiles(final List<TokenFileLoader.LoadedTokenFile> files) {
        final Map<String, Object> merged = new LinkedHashMap<>();
        for (final var loaded : files) {
            deepMerge(merged, loaded.tokenFile().getTokens());
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(final Map<String, Object> target, final Map<String, Object> source) {
        for (final var entry : source.entrySet()) {
            final String key = entry.getKey();
            final Object srcVal = entry.getValue();
            final Object tgtVal = target.get(key);
            if (tgtVal instanceof Map && srcVal instanceof Map) {
                deepMerge((Map<String, Object>) tgtVal, (Map<String, Object>) srcVal);
            } else {
                target.put(key, srcVal);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> resolveAll(
            final Map<String, Object> tokens,
            final Map<String, Object> root,
            final List<String> warnings) {

        final Map<String, String> result = new LinkedHashMap<>();
        flattenAndResolve(tokens, root, "", result, warnings);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flattenAndResolve(
            final Map<String, Object> tokens,
            final Map<String, Object> root,
            final String prefix,
            final Map<String, String> result,
            final List<String> warnings) {

        for (final var entry : tokens.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final String fullKey = prefix.isEmpty() ? key : prefix + "-" + key;

            if (key.startsWith("$")) continue;

            if (value instanceof Map<?, ?> node) {
                if (node.containsKey("$value")) {
                    final Object rawVal = node.get("$value");
                    final String strVal = rawVal != null ? rawVal.toString() : "";
                    final var aliasMatcher = ALIAS_PATTERN.matcher(strVal);
                    if (aliasMatcher.matches()) {
                        final String ref = aliasMatcher.group(1);
                        final String resolved = resolveRef(ref, root);
                        if (resolved != null) {
                            result.put(fullKey, resolved);
                        } else {
                            warnings.add("Unresolved alias: {" + ref + "} at " + fullKey);
                            result.put(fullKey, strVal);
                        }
                    } else {
                        result.put(fullKey, strVal);
                    }
                } else {
                    flattenAndResolve((Map<String, Object>) node, root, fullKey, result, warnings);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveRef(final String ref, final Map<String, Object> root) {
        final String[] parts = ref.split("\\.");
        Object current = root;
        for (final String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
            } else {
                return null;
            }
        }
        if (current instanceof Map<?, ?> leaf && ((Map<?, ?>) leaf).containsKey("$value")) {
            return ((Map<?, ?>) leaf).get("$value").toString();
        }
        return current != null ? current.toString() : null;
    }

    private BuildResult buildCss(
            final Map<String, String> resolved,
            final String themeName,
            final List<String> warnings) {

        final var sb = new StringBuilder();
        sb.append("/* Generated by ds-cli - DO NOT EDIT */\n");
        sb.append(themeName).append(" {\n");
        for (final var entry : resolved.entrySet()) {
            sb.append("  --").append(entry.getKey()).append(": ").append(entry.getValue()).append(";\n");
        }
        sb.append("}\n");
        return new BuildResult(sb.toString(), OutputFormat.CSS, resolved.size(), warnings);
    }

    private BuildResult buildJson(
            final Map<String, String> resolved,
            final List<String> warnings) {

        final var sb = new StringBuilder();
        sb.append("{\n");
        final var entries = new ArrayList<>(resolved.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            final var entry = entries.get(i);
            sb.append("  \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}\n");
        return new BuildResult(sb.toString(), OutputFormat.JSON, resolved.size(), warnings);
    }

    private BuildResult buildTypeScript(
            final Map<String, String> resolved,
            final List<String> warnings) {

        final var sb = new StringBuilder();
        sb.append("// Generated by ds-cli - DO NOT EDIT\n\n");
        sb.append("export const tokens = {\n");
        for (final var entry : resolved.entrySet()) {
            final String camelKey = kebabToCamel(entry.getKey());
            sb.append("  ").append(camelKey).append(": '").append(entry.getValue()).append("',\n");
        }
        sb.append("} as const;\n\n");
        sb.append("export type TokenKey = keyof typeof tokens;\n");
        return new BuildResult(sb.toString(), OutputFormat.TS, resolved.size(), warnings);
    }

    private String kebabToCamel(final String kebab) {
        final String[] parts = kebab.split("-");
        final var sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
