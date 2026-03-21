package com.ghatana.pattern.api.codegen;

import java.util.Objects;

/**
 * Compiler feature flags supplied by callers.
 */
public record CompileOptions(
        GeneratedTypeMode mode,
        boolean generateProtoGlue,
        boolean generateJsonGlue,
        boolean prewarm) {

    public CompileOptions {
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * @return canonical defaults (CLASS mode, proto/json glue enabled, no pre-warming).
     */
    public static CompileOptions defaults() {
        return new CompileOptions(GeneratedTypeMode.CLASS, true, true, false);
    }

    /**
     * Returns a copy with a different generation mode.
     *
     * @param newMode desired mode
     */
    public CompileOptions withMode(GeneratedTypeMode newMode) {
        return new CompileOptions(Objects.requireNonNull(newMode, "mode"),
                generateProtoGlue,
                generateJsonGlue,
                prewarm);
    }

    /**
     * @return copy with both proto and JSON glue disabled.
     */
    public CompileOptions disableSerializationGlue() {
        return new CompileOptions(mode, false, false, prewarm);
    }

    /**
     * @return {@code true} when either proto or JSON glue generation is required.
     */
    public boolean requiresSerializationArtifacts() {
        return generateProtoGlue || generateJsonGlue;
    }
}
