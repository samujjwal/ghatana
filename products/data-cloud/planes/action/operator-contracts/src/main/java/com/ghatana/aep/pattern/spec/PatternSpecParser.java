package com.ghatana.aep.pattern.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses Map representation into typed PatternSpec model.
 *
 * @doc.type class
 * @doc.purpose Converts Map to typed PatternSpec at boundary
 * @doc.layer product
 * @doc.pattern Parser
 */
public final class PatternSpecParser {

    private PatternSpecParser() {}

    /**
     * Parses a Map representation into a typed PatternSpec.
     *
     * @param map the map representation
     * @return typed PatternSpec
     * @throws IllegalArgumentException if parsing fails
     */
    public static PatternSpec parse(Map<String, Object> map) {
        Objects.requireNonNull(map, "PatternSpec map must not be null");

        return new PatternSpec(
            requireString(map, "apiVersion"),
            requireString(map, "kind"),
            parseMetadata(map.get("metadata")),
            parseSemantics(map.get("semantics")),
            parseExpression(map.get("pattern")),
            parseEmit(map.get("emit")),
            parseLifecycle(map.get("lifecycle")),
            parseGovernance(map.get("governance")),
            parseObservability(map.get("observability")));
    }

    private static PatternMetadata parseMetadata(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("metadata must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadataMap = (Map<String, Object>) map;
        return new PatternMetadata(
            requireString(metadataMap, "name"),
            requireString(metadataMap, "namespace"),
            requireString(metadataMap, "version"),
            optionalString(metadataMap, "tenantId"),
            optionalString(metadataMap, "owner"),
            optionalString(metadataMap, "description"),
            optionalMap(metadataMap, "labels"),
            optionalMap(metadataMap, "annotations"));
    }

    private static PatternSemantics parseSemantics(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("semantics must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> semanticsMap = (Map<String, Object>) map;
        return new PatternSemantics(
            optionalString(semanticsMap, "timePolicy"),
            optionalString(semanticsMap, "timeMode"),
            requireString(semanticsMap, "uncertaintyPolicy"),
            requireString(semanticsMap, "replayPolicy"),
            optionalMap(semanticsMap, "options"));
    }

    private static PatternExpression parseExpression(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("pattern must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> exprMap = (Map<String, Object>) map;
        return parseExpression(exprMap, "");
    }

    static PatternExpression parseExpression(Map<String, Object> exprMap, String path) {
        List<PatternExpression> operands = null;
        Object operandsValue = exprMap.get("operands");
        if (operandsValue instanceof List<?> list) {
            operands = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childMap = (Map<String, Object>) itemMap;
                    operands.add(parseExpression(childMap, path + ".operands"));
                } else {
                    throw new IllegalArgumentException(path + ".operands must contain objects");
                }
            }
        }

        PatternExpression nestedPattern = null;
        Object patternValue = exprMap.get("pattern");
        if (patternValue instanceof Map<?, ?> patternMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> childMap = (Map<String, Object>) patternMap;
            nestedPattern = parseExpression(childMap, path + ".pattern");
        }

        return new PatternExpression(
            optionalString(exprMap, "operator"),
            optionalString(exprMap, "event"),
            optionalString(exprMap, "capabilityRef"),
            optionalString(exprMap, "agentRef"),
            optionalString(exprMap, "outputSchema"),
            operands,
            nestedPattern,
            optionalMap(exprMap, "parameters"),
            optionalMap(exprMap, "toolPolicy"),
            optionalString(exprMap, "window"),
            optionalString(exprMap, "windowSpec"));
    }

    private static PatternEmit parseEmit(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("emit must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> emitMap = (Map<String, Object>) map;
        return new PatternEmit(
            requireString(emitMap, "eventType"),
            requireString(emitMap, "outputSchema"),
            optionalMap(emitMap, "payloadTransform"));
    }

    private static PatternLifecycle parseLifecycle(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("lifecycle must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> lifecycleMap = (Map<String, Object>) map;
        return new PatternLifecycle(
            requireString(lifecycleMap, "state"),
            optionalString(lifecycleMap, "evidencePolicy"),
            optionalString(lifecycleMap, "evidenceStore"),
            optionalMap(lifecycleMap, "options"));
    }

    private static PatternGovernance parseGovernance(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("governance must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> governanceMap = (Map<String, Object>) map;
        return new PatternGovernance(
            optionalString(governanceMap, "commitSha"),
            optionalString(governanceMap, "approvalPolicy"),
            optionalString(governanceMap, "reviewPolicy"),
            optionalMap(governanceMap, "options"));
    }

    private static PatternObservability parseObservability(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("observability must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> observabilityMap = (Map<String, Object>) map;
        return new PatternObservability(
            optionalString(observabilityMap, "metricsPolicy"),
            optionalString(observabilityMap, "loggingPolicy"),
            optionalMap(observabilityMap, "options"));
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        String str = String.valueOf(value);
        if (str.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return str;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value);
        return str.isBlank() ? null : str;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optionalMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }
}
