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

import com.ghatana.platform.dscli.model.ValidationIssue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audits DTCG token files for duplicates, a11y coverage, and governance checks.
 *
 * @doc.type class
 * @doc.purpose Governance auditing: duplicate detection, a11y coverage, naming conventions.
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class TokenAuditor {

    /**
     * Audits a collection of loaded token files for governance issues.
     *
     * @param files loaded token files to audit
     * @return list of audit findings
     */
    public List<ValidationIssue> audit(final List<TokenFileLoader.LoadedTokenFile> files) {
        final List<ValidationIssue> issues = new ArrayList<>();

        if (files.isEmpty()) {
            issues.add(ValidationIssue.warning("NO_FILES", "No token files found to audit", "."));
            return issues;
        }

        // Collect all resolved values across all files for duplicate detection
        final Map<String, List<String>> valueIndex = new HashMap<>(); // value -> list of token paths

        for (final var loaded : files) {
            collectValues(loaded.tokenFile().getTokens(), "", valueIndex);
        }

        // Duplicate detection
        detectDuplicates(valueIndex, issues);

        // A11y coverage
        checkA11yCoverage(files, issues);

        // Naming conventions
        for (final var loaded : files) {
            checkNamingConventions(loaded.tokenFile().getTokens(), "", loaded.path().toString(), issues);
        }

        return issues;
    }

    @SuppressWarnings("unchecked")
    private void collectValues(
            final Map<String, Object> tokens,
            final String path,
            final Map<String, List<String>> valueIndex) {

        for (final var entry : tokens.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final String fullPath = path.isEmpty() ? key : path + "." + key;

            if (key.startsWith("$")) continue;

            if (value instanceof Map<?, ?> node) {
                if (node.containsKey("$value")) {
                    final Object tokenValue = node.get("$value");
                    if (tokenValue instanceof String strVal) {
                        valueIndex.computeIfAbsent(strVal, k -> new ArrayList<>()).add(fullPath);
                    }
                } else {
                    collectValues((Map<String, Object>) node, fullPath, valueIndex);
                }
            }
        }
    }

    private void detectDuplicates(
            final Map<String, List<String>> valueIndex,
            final List<ValidationIssue> issues) {

        for (final var entry : valueIndex.entrySet()) {
            final List<String> paths = entry.getValue();
            if (paths.size() > 1) {
                issues.add(ValidationIssue.warning(
                        "DUPLICATE_VALUE",
                        "Value '" + entry.getKey() + "' is duplicated across tokens: " + paths,
                        paths.get(0)));
            }
        }
    }

    private void checkA11yCoverage(
            final List<TokenFileLoader.LoadedTokenFile> files,
            final List<ValidationIssue> issues) {

        // Check that color tokens have a11y-relevant pairs (e.g., on-* semantic tokens)
        boolean hasColorTokens = false;
        boolean hasOnColorTokens = false;

        for (final var loaded : files) {
            final Map<String, Object> tokens = loaded.tokenFile().getTokens();
            for (final String key : tokens.keySet()) {
                if (!key.startsWith("$")) {
                    if (key.startsWith("color") || key.equals("colors")) hasColorTokens = true;
                    if (key.startsWith("on-") || key.contains("foreground") || key.contains("on")) {
                        hasOnColorTokens = true;
                    }
                }
            }
        }

        if (hasColorTokens && !hasOnColorTokens) {
            issues.add(ValidationIssue.warning(
                    "MISSING_A11Y_PAIRS",
                    "Color tokens found but no foreground/on-* contrast pair tokens detected. "
                            + "Ensure contrast pairs exist for WCAG 2.1 AA compliance.",
                    "colors"));
        }
    }

    @SuppressWarnings("unchecked")
    private void checkNamingConventions(
            final Map<String, Object> tokens,
            final String path,
            final String file,
            final List<ValidationIssue> issues) {

        for (final var entry : tokens.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final String fullPath = path.isEmpty() ? key : path + "." + key;

            if (key.startsWith("$")) continue;

            // Naming: kebab-case only
            if (!key.chars().allMatch(c -> Character.isLowerCase(c) || Character.isDigit(c) || c == '-') || key.contains("_") || key.contains(" ")) {
                issues.add(ValidationIssue.warning(
                        "NAMING_CONVENTION",
                        "Token key '" + key + "' in " + file + " should use kebab-case",
                        fullPath));
            }

            if (value instanceof Map<?, ?> node) {
                if (!node.containsKey("$value")) {
                    checkNamingConventions((Map<String, Object>) node, fullPath, file, issues);
                }
            }
        }
    }
}
