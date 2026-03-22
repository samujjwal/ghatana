package com.ghatana.platform.schema;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a schema registration attempt violates the active
 * {@link CompatibilityMode}.
 *
 * @doc.type class
 * @doc.purpose Signal a schema evolution compatibility violation
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class SchemaCompatibilityException extends RuntimeException {

    private final String schemaName;
    private final String schemaVersion;
    private final CompatibilityMode mode;

    public SchemaCompatibilityException(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull CompatibilityMode mode,
            @NotNull String reason) {
        super(String.format(
                "Schema '%s' v%s violates %s compatibility: %s",
                schemaName, schemaVersion, mode, reason));
        this.schemaName = schemaName;
        this.schemaVersion = schemaVersion;
        this.mode = mode;
    }

    /** The schema name that triggered the violation. */
    @NotNull
    public String getSchemaName() { return schemaName; }

    /** The schema version that triggered the violation. */
    @NotNull
    public String getSchemaVersion() { return schemaVersion; }

    /** The compatibility mode that was being enforced. */
    @NotNull
    public CompatibilityMode getMode() { return mode; }
}
