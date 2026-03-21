package com.ghatana.pattern.api.codegen;

/**
 * Generation mode for dynamically compiled event types.
 *
 * <p>The compiler supports two forms:
 * <ul>
 *     <li>{@link #CLASS}: emits a concrete {@code class} that extends {@code GEvent}</li>
 *     <li>{@link #RECORD}: emits a Java {@code record} that implements {@code GEventLike}</li>
 * </ul>
 *
 * <p>Modes are intentionally explicit so call sites can enforce invariants
 * (e.g., record mode for analytics pipelines that prefer value semantics).</p>
 */
public enum GeneratedTypeMode {
    CLASS,
    RECORD;

    /**
     * @return {@code true} when the generation target is a concrete class.
     */
    public boolean isClassMode() {
        return this == CLASS;
    }

    /**
     * @return {@code true} when the generation target is a Java record.
     */
    public boolean isRecordMode() {
        return this == RECORD;
    }
}
