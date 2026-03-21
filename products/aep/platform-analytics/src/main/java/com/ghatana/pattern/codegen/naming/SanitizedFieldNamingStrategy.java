package com.ghatana.pattern.codegen.naming;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default implementation that normalizes names into lowerCamelCase identifiers.
 */
public final class SanitizedFieldNamingStrategy implements FieldNamingStrategy {
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");
    private static final Set<String> RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            // common framework conflicts
            "record"
    );

    @Override
    /**
     * Converts an arbitrary identifier into a lowerCamelCase, Java-safe field name.
     *
     * @param candidate raw schema name (may be {@code null})
     * @return sanitized identifier suitable for generated classes
     */
    public String toFieldName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "field";
        }
        String normalized = NON_ALPHANUM.matcher(candidate).replaceAll(" ");
        String[] parts = normalized.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (i == 0) {
                builder.append(lower);
            } else {
                builder.append(Character.toUpperCase(lower.charAt(0)));
                builder.append(lower.substring(1));
            }
        }
        if (builder.length() == 0) {
            builder.append("field");
        }
        if (Character.isDigit(builder.charAt(0))) {
            builder.insert(0, "f");
        }
        String result = builder.toString();
        if (RESERVED.contains(result)) {
            result = result + "Value";
        }
        return result;
    }
}
