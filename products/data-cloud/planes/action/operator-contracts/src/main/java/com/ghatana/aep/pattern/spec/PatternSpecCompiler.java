package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Compiles structurally valid PatternSpec maps into deterministic runtime graph contracts
 * @doc.layer product
 * @doc.pattern Compiler
 */
public final class PatternSpecCompiler {

    private PatternSpecCompiler() {
    }

    public static CompiledPattern compile(Map<String, Object> spec) {
        PatternSpecValidationResult validation = PatternSpecValidator.validate(spec);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }

        Map<String, Object> metadata = mapSection(spec, "metadata");
        String patternId = text(metadata.getOrDefault("name", metadata.getOrDefault("id", "pattern")));
        PatternRuntimeNode root = compileExpression(mapSection(spec, "pattern"), "root");
        List<String> nodeOrder = new ArrayList<>();
        collectNodeOrder(root, nodeOrder);
        return new CompiledPattern(
            patternId,
            "pattern-runtime-" + patternId,
            root,
            nodeOrder,
            metadata,
            mapSection(spec, "semantics"),
            mapSection(spec, "emit"),
            mapSection(spec, "lifecycle"),
            mapSection(spec, "governance"));
    }

    private static PatternRuntimeNode compileExpression(Map<String, Object> expression, String path) {
        OperatorKind operatorKind = operatorKind(expression);
        List<PatternRuntimeNode> children = new ArrayList<>();
        Object operands = expression.get("operands");
        if (operands instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                children.add(compileExpression(castMap(list.get(i), path + ".operands[" + i + "]"), path + "-" + i));
            }
        }
        Object nestedPattern = expression.get("pattern");
        if (nestedPattern instanceof Map<?, ?>) {
            children.add(compileExpression(castMap(nestedPattern, path + ".pattern"), path + "-pattern"));
        }

        return new PatternRuntimeNode(
            path,
            operatorKind,
            optionalText(expression.get("event")),
            optionalText(expression.get("agentRef")),
            optionalText(expression.get("outputSchema")),
            parameters(expression),
            children);
    }

    private static OperatorKind operatorKind(Map<String, Object> expression) {
        Object operator = expression.get("operator");
        if (operator == null) {
            return OperatorKind.EVENT_REF;
        }
        return OperatorKind.valueOf(String.valueOf(operator));
    }

    private static Map<String, Object> parameters(Map<String, Object> expression) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : expression.entrySet()) {
            String key = entry.getKey();
            if (!"operands".equals(key) && !"pattern".equals(key)) {
                parameters.put(key, entry.getValue());
            }
        }
        return parameters;
    }

    private static void collectNodeOrder(PatternRuntimeNode node, List<String> nodeOrder) {
        nodeOrder.add(node.nodeId());
        for (PatternRuntimeNode child : node.children()) {
            collectNodeOrder(child, nodeOrder);
        }
    }

    private static Map<String, Object> mapSection(Map<String, Object> spec, String section) {
        return castMap(spec.get(section), section);
    }

    private static Map<String, Object> castMap(Object value, String path) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(path + " contains non-string key");
                }
                result.put(key, entry.getValue());
            }
            return result;
        }
        throw new IllegalArgumentException(path + " must be an object");
    }

    private static Optional<String> optionalText(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return String.valueOf(value);
    }
}
