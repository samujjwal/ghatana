package com.ghatana.yappc.kernel;

import java.util.Locale;

/**
 * Naming helpers shared by product scaffold generators.
 *
 * @doc.type class
 * @doc.purpose Normalize product and route names for generated artifacts
 * @doc.layer integration
 * @doc.pattern Utility
 */
final class ProductGenerationNaming {

    private ProductGenerationNaming() {
    }

    static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        return value;
    }

    static String productPathSegment(String productId) {
        return requireNonBlank(productId, "productId")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    static String packageSegment(String productId) {
        String normalized = productPathSegment(productId)
            .replaceAll("[^a-z0-9]", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("productId must contain at least one package-safe character");
        }
        if (Character.isDigit(normalized.charAt(0))) {
            return "product" + normalized;
        }
        return normalized;
    }

    static String kebabCase(String input) {
        return requireNonBlank(input, "input")
            .replaceAll("([A-Z])", "-$1")
            .toLowerCase(Locale.ROOT)
            .replaceAll("^-", "");
    }

    static String pascalCase(String input) {
        String normalized = requireNonBlank(input, "input")
            .replaceAll("[^A-Za-z0-9]+", " ")
            .trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("input must contain at least one name-safe character");
        }

        StringBuilder result = new StringBuilder();
        for (String part : normalized.split("\\s+|(?=[A-Z])")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }

    static String camelCase(String input) {
        String pascal = pascalCase(input);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }
}
