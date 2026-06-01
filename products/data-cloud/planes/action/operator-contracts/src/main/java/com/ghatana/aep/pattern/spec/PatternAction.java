package com.ghatana.aep.pattern.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical action specification for PatternSpec.
 *
 * <p>Defines actions to be taken when a pattern matches, including
 * alerts, notifications, state updates, and side-effecting operations.
 *
 * @doc.type record
 * @doc.purpose Canonical action specification for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternAction(
        String type,
        String target,
        Map<String, Object> parameters,
        List<PatternAction> actions,
        Map<String, Object> options) {

    public PatternAction {
        Objects.requireNonNull(type, "type");
    }

    /**
     * Create a simple alert action.
     *
     * @param target alert target (e.g., "ops", "admin")
     * @param message alert message
     * @return PatternAction instance
     */
    public static PatternAction alert(String target, String message) {
        return new PatternAction("alert", target, Map.of("message", message), null, null);
    }

    /**
     * Create a notification action.
     *
     * @param target notification target
     * @param parameters notification parameters
     * @return PatternAction instance
     */
    public static PatternAction notification(String target, Map<String, Object> parameters) {
        return new PatternAction("notification", target, parameters, null, null);
    }

    /**
     * Create a state update action.
     *
     * @param target state target
     * @param state new state
     * @return PatternAction instance
     */
    public static PatternAction updateState(String target, String state) {
        return new PatternAction("updateState", target, Map.of("state", state), null, null);
    }

    /**
     * Create a composite action that executes multiple actions in sequence.
     *
     * @param actions list of actions to execute
     * @return PatternAction instance
     */
    public static PatternAction composite(List<PatternAction> actions) {
        return new PatternAction("composite", null, null, actions, null);
    }

    /**
     * Convert this PatternAction to a map representation.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("type", type);
        if (target != null) map.put("target", target);
        if (parameters != null && !parameters.isEmpty()) map.put("parameters", parameters);
        if (actions != null && !actions.isEmpty()) {
            map.put("actions", actions.stream().map(PatternAction::toMap).toList());
        }
        if (options != null && !options.isEmpty()) map.put("options", options);
        return java.util.Collections.unmodifiableMap(map);
    }
}
