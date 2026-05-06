package com.ghatana.kernel.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads list-valued manifest fields from JSON or YAML product manifests for contract tests.
 *
 * @doc.type class
 * @doc.purpose Shared list-field reader for product manifest contract tests
 * @doc.layer testing
 * @doc.pattern Utility
 */
final class DeclaredPolicyManifestListReader {

    private static final Pattern JSON_ITEM = Pattern.compile("\"([^\\\"]+)\"");

    private DeclaredPolicyManifestListReader() {
    }

    static Set<String> read(Path manifestPath, String fieldName) {
        try {
            String content = Files.readString(manifestPath);
            if (manifestPath.getFileName().toString().endsWith(".json")) {
                return readJson(content, manifestPath, fieldName);
            }
            return readYaml(content, manifestPath, fieldName);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read manifest field '" + fieldName + "' from " + manifestPath,
                    exception);
        }
    }

    private static Set<String> readJson(String content, Path manifestPath, String fieldName) {
        Pattern fieldPattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[(.*?)]",
                Pattern.DOTALL);
        Matcher blockMatcher = fieldPattern.matcher(content);
        if (!blockMatcher.find()) {
            throw new IllegalStateException(fieldName + " not found in manifest: " + manifestPath);
        }

        Matcher itemMatcher = JSON_ITEM.matcher(blockMatcher.group(1));
        LinkedHashSet<String> items = new LinkedHashSet<>();
        while (itemMatcher.find()) {
            items.add(itemMatcher.group(1));
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("No " + fieldName + " entries declared in manifest: " + manifestPath);
        }
        return Set.copyOf(items);
    }

    private static Set<String> readYaml(String content, Path manifestPath, String fieldName) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        String[] lines = content.split("\\R");
        boolean inField = false;
        int fieldIndent = -1;

        for (String line : lines) {
            if (!inField) {
                int keyIndex = line.indexOf(fieldName + ":");
                if (keyIndex >= 0) {
                    inField = true;
                    fieldIndent = countIndent(line);
                }
                continue;
            }

            if (line.isBlank()) {
                continue;
            }

            int indent = countIndent(line);
            String trimmed = line.trim();

            if (indent <= fieldIndent && !trimmed.startsWith("-")) {
                break;
            }

            if (trimmed.startsWith("- ")) {
                items.add(trimmed.substring(2).trim());
            }
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("No " + fieldName + " entries declared in manifest: " + manifestPath);
        }
        return Set.copyOf(items);
    }

    private static int countIndent(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }
}
