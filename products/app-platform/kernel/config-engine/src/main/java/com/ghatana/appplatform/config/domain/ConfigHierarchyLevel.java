package com.ghatana.appplatform.config.domain;

/**
 * Levels in the configuration hierarchy, ordered from lowest to highest precedence.
 *
 * <p>During resolution, a higher-priority level's value overrides lower-priority levels
 * for the same key. The numeric priority makes ordering explicit and queryable.
 *
 * <table border="1">
 *   <tr><th>Level</th><th>Priority</th><th>Scope</th></tr>
 *   <tr><td>GLOBAL</td><td>0</td><td>All tenants, all users (default / fallback)</td></tr>
 *   <tr><td>JURISDICTION</td><td>1</td><td>Country/province regulation overrides</td></tr>
 *   <tr><td>TENANT</td><td>2</td><td>Per-tenant customization</td></tr>
 *   <tr><td>USER</td><td>3</td><td>Per-user preferences</td></tr>
 *   <tr><td>SESSION</td><td>4</td><td>Ephemeral per-session overrides (highest)</td></tr>
 * </table>
 *
 * @doc.type enum
 * @doc.purpose Configuration hierarchy levels ordered by precedence
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ConfigHierarchyLevel {

    /** Platform-wide defaults — lowest precedence, applies to all tenants and users. */
    GLOBAL(0),

    /** Jurisdiction (country/province) regulatory overrides. */
    JURISDICTION(1),

    /** Tenant-level customizations (per-bank, per-organization). */
    TENANT(2),

    /** Per-user preferences. */
    USER(3),

    /** Ephemeral per-session overrides — highest precedence, not persisted long-term. */
    SESSION(4);

    private final int priority;

    ConfigHierarchyLevel(int priority) {
        this.priority = priority;
    }

    /**
     * Returns the numeric priority; higher value means higher precedence.
     */
    public int priority() {
        return priority;
    }
}
