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

import com.ghatana.platform.dscli.model.TokenFile;
import com.ghatana.platform.dscli.model.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates DTCG token files for structural correctness and reference integrity.
 *
 * @doc.type class
 * @doc.purpose Validates DTCG token files: required fields, type correctness, alias refs.
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class TokenValidator {

    private static final Set<String> VALID_TYPES = Set.of(
            "color", "dimension", "fontFamily", "fontWeight", "duration",
            "cubicBezier", "number", "string", "boolean", "shadow",
            "gradient", "typography", "border", "transition"
    );

    // DTCG alias reference pattern: {group.token}
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^\\{[\\w.]+\\}$");

    // Hex color pattern
    private static final Pattern HEX_COLOR = Pattern.compile(
            "^#([A-Fa-f0-9]{3,4}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$");

    // Dimension pattern (value + unit)
    private static final Pattern DIMENSION = Pattern.compile(
            "^-?\\d+(\\.\\d+)?(px|rem|em|%|vh|vw|pt|pc|cm|mm|in|ex|ch)$");

    /**
     * Validates a loaded token file.
     *
     * @param tokenFile the parsed token file
     * @return list of validation issues (empty means clean)
     */
    public List<ValidationIssue> validate(final TokenFile tokenFile) {
        final List<ValidationIssue> issues = new ArrayList<>();

        if (tokenFile.getVersion() == null) {
            issues.add(ValidationIssue.warning(
                    "MISSING_VERSION", "Token file has no $version field", "$version"));
        }

        validateTokenMap(tokenFile.getTokens(), "", issues);
        validateAliasReferences(tokenFile.getTokens(), tokenFile.getTokens(), issues);

        return issues;
    }

    @SuppressWarnings("unchecked")
    private void validateTokenMap(
            final Map<String, Object> tokens,
            final String path,
            final List<ValidationIssue> issues) {

        for (final var entry : tokens.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final String fullPath = path.isEmpty() ? key : path + "." + key;

            if (key.startsWith("$")) {
                continue; // skip DTCG meta keys
            }

            if (!(value instanceof Map)) {
                issues.add(ValidationIssue.error(
                        "INVALID_TOKEN_NODE",
                        "Token node must be an object, got: " + (value == null ? "null" : value.getClass().getSimpleName()),
                        fullPath));
                continue;
            }

            final Map<String, Object> node = (Map<String, Object>) value;

            if (node.containsKey("$value")) {
                // Leaf token
                validateLeafToken(node, fullPath, issues);
            } else {
                // Group - recurse
                validateTokenMap(node, fullPath, issues);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateLeafToken(
            final Map<String, Object> node,
            final String path,
            final List<ValidationIssue> issues) {

        final Object rawValue = node.get("$value");
        final Object rawType = node.get("$type");

        if (rawValue == null) {
            issues.add(ValidationIssue.error("NULL_VALUE", "$value is null", path + ".$value"));
            return;
        }

        if (rawType instanceof String type) {
            if (!VALID_TYPES.contains(type)) {
                issues.add(ValidationIssue.warning(
                        "UNKNOWN_TYPE",
                        "Unknown DTCG token type: " + type + ". Valid types: " + VALID_TYPES,
                        path + ".$type"));
            }

            if (rawValue instanceof String strValue && !ALIAS_PATTERN.matcher(strValue).matches()) {
                validateValueForType(type, strValue, path, issues);
            }
        }
    }

    private void validateValueForType(
            final String type,
            final String value,
            final String path,
            final List<ValidationIssue> issues) {

        switch (type) {
            case "color" -> {
                if (!HEX_COLOR.matcher(value).matches()
                        && !value.startsWith("rgb(")
                        && !value.startsWith("rgba(")
                        && !value.startsWith("hsl(")) {
                    issues.add(ValidationIssue.error(
                            "INVALID_COLOR",
                            "Invalid color value: " + value + " (expected hex, rgb, rgba, or hsl)",
                            path + ".$value"));
                }
            }
            case "dimension" -> {
                if (!DIMENSION.matcher(value).matches()) {
                    issues.add(ValidationIssue.error(
                            "INVALID_DIMENSION",
                            "Invalid dimension value: " + value + " (expected e.g. 16px, 1rem)",
                            path + ".$value"));
                }
            }
            default -> { /* no specific format enforcement */ }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateAliasReferences(
            final Map<String, Object> node,
            final Map<String, Object> root,
            final List<ValidationIssue> issues) {

        for (final var entry : node.entrySet()) {
            final Object value = entry.getValue();

            if (value instanceof Map<?, ?> childMap) {
                if (childMap.containsKey("$value")) {
                    final Object tokenValue = childMap.get("$value");
                    if (tokenValue instanceof String strValue && ALIAS_PATTERN.matcher(strValue).matches()) {
                        final String ref = strValue.substring(1, strValue.length() - 1); // strip {}
                        if (!resolveRef(ref, root)) {
                            issues.add(ValidationIssue.error(
                                    "BROKEN_ALIAS",
                                    "Alias reference '" + strValue + "' cannot be resolved",
                                    entry.getKey() + ".$value"));
                        }
                    }
                } else {
                    validateAliasReferences((Map<String, Object>) childMap, root, issues);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean resolveRef(final String ref, final Map<String, Object> root) {
        final String[] parts = ref.split("\\.");
        Object current = root;
        for (final String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
            } else {
                return false;
            }
        }
        return current != null;
    }
}
