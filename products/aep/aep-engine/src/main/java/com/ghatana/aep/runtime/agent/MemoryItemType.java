package com.ghatana.agent.memory.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enumerates the memory tiers supported by the memory plane.
 *
 * <p>Products can register custom memory item types via {@link #registerCustomType(String)}.
 * Custom items use {@link #CUSTOM} as their {@link MemoryItem#getType()} and carry the
 * specific custom type name in their {@link MemoryItem#getLabels()} under the
 * key {@value #CUSTOM_TYPE_LABEL_KEY}.
 *
 * <p>Example:
 * <pre>{@code
 * MemoryItemType.registerCustomType("CONVERSATION");
 * // When creating a custom MemoryItem, set:
 * //   type = MemoryItemType.CUSTOM
 * //   labels = Map.of(MemoryItemType.CUSTOM_TYPE_LABEL_KEY, "CONVERSATION")
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Extensible memory tier classification
 * @doc.layer agent-memory
 */
public enum MemoryItemType {

    /** Episodic memory: what happened during agent interactions. */
    EPISODE,

    /** Semantic memory: factual knowledge (SPO triples). */
    FACT,

    /** Procedural memory: reusable skills and procedures. */
    PROCEDURE,

    /** Task-state memory: long-running multi-session workflow state. */
    TASK_STATE,

    /** Working memory: bounded, ephemeral in-run state. */
    WORKING,

    /** Preference memory: user/agent preferences. */
    PREFERENCE,

    /** Typed artifact: generic domain objects (Decision, ToolUse, etc.). */
    ARTIFACT,

    /**
     * Custom product-specific memory type.
     *
     * <p>The specific custom type name is stored in MemoryItem labels under
     * key {@value #CUSTOM_TYPE_LABEL_KEY}. Use {@link #registerCustomType(String)}
     * to whitelist custom names.
     */
    CUSTOM;

    // =========================================================================
    // Custom type registry
    // =========================================================================

    /** Label key used to carry the custom type name on a MemoryItem. */
    public static final String CUSTOM_TYPE_LABEL_KEY = "memory.custom.type";

    private static final Set<String> CUSTOM_TYPES = ConcurrentHashMap.newKeySet();

    /**
     * Registers a custom memory item type name. Products should call this at
     * startup to declare their custom memory types.
     *
     * @param name non-null, non-blank custom type name (e.g., "CONVERSATION", "INSIGHT")
     * @throws IllegalArgumentException if name is blank
     */
    public static void registerCustomType(@NotNull String name) {
        Objects.requireNonNull(name, "custom type name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("custom type name must not be blank");
        }
        CUSTOM_TYPES.add(name.toUpperCase());
    }

    /**
     * Checks whether a custom type name is registered.
     *
     * @param name custom type name
     * @return true if registered via {@link #registerCustomType(String)}
     */
    public static boolean isCustomTypeRegistered(@NotNull String name) {
        return CUSTOM_TYPES.contains(name.toUpperCase());
    }

    /**
     * Returns an unmodifiable view of all registered custom type names.
     *
     * @return set of custom type names (upper-cased)
     */
    @NotNull
    public static Set<String> registeredCustomTypes() {
        return Set.copyOf(CUSTOM_TYPES);
    }
}
