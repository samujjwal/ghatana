package com.ghatana.appplatform.config.port;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigSchema;
import com.ghatana.appplatform.config.domain.ConfigValue;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;

/**
 * Port (hexagonal architecture) for config schema registration and entry resolution.
 *
 * <p>All operations are async via ActiveJ {@link Promise}. Implementations must not
 * block the eventloop thread — wrap JDBC calls in {@link Promise#ofBlocking}.
 *
 * @doc.type interface
 * @doc.purpose Read/write port for config schemas and hierarchical entries
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ConfigStore {

    // -------------------------------------------------------------------------
    // K02-001: Schema registry
    // -------------------------------------------------------------------------

    /**
     * Registers or updates a config schema.
     *
     * @param schema the schema to register; uniquely identified by namespace + version
     * @return a promise that resolves when the schema is persisted
     */
    Promise<Void> registerSchema(ConfigSchema schema);

    /**
     * Retrieves a registered schema by namespace and version.
     *
     * @param namespace the config namespace
     * @param version   the schema version string
     * @return a promise resolving to the schema if found, or an empty optional
     */
    Promise<Optional<ConfigSchema>> getSchema(String namespace, String version);

    // -------------------------------------------------------------------------
    // K02-002/003: Entry management
    // -------------------------------------------------------------------------

    /**
     * Creates or updates a config entry at a specific hierarchy level.
     *
     * @param entry the entry to persist
     * @return a promise that resolves when the entry is persisted
     */
    Promise<Void> setEntry(ConfigEntry entry);

    // -------------------------------------------------------------------------
    // K02-004/005/006: Resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves all config keys in a namespace for a given resolution context.
     *
     * <p>The resolution context specifies what level IDs are available.
     * Entries from all applicable levels are loaded and merged by
     * {@link com.ghatana.appplatform.config.merge.ConfigMerger}.
     *
     * @param namespace    the config namespace to resolve
     * @param tenantId     tenant scope (may be null for GLOBAL-only resolution)
     * @param userId       user scope (may be null)
     * @param sessionId    session scope (may be null)
     * @param jurisdiction jurisdiction scope (may be null)
     * @return a promise resolving to a map of key → resolved {@link ConfigValue}
     */
    Promise<Map<String, ConfigValue>> resolve(
        String namespace,
        String tenantId,
        String userId,
        String sessionId,
        String jurisdiction);
}
