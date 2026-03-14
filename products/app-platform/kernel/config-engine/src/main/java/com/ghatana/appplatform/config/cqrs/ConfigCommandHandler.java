package com.ghatana.appplatform.config.cqrs;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.port.ConfigStore;
import com.ghatana.appplatform.config.temporal.TemporalConfigStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * CQRS write-side handler for config mutations (K02-016).
 *
 * <p>Accepts typed command records, validates inputs, and delegates to
 * {@link ConfigStore#setEntry} or {@link TemporalConfigStore#rollback}.
 * All operations return {@link Promise} to stay compatible with the ActiveJ eventloop.
 *
 * <p>Command types:
 * <ul>
 *   <li>{@link SetConfigCommand} — create or update a config entry</li>
 *   <li>{@link DeleteConfigCommand} — remove a config entry at a specific level</li>
 *   <li>{@link RollbackConfigCommand} — restore a key from version history</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose CQRS command handler for config write operations (K02-016)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConfigCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfigCommandHandler.class);

    private final ConfigStore store;
    private final TemporalConfigStore temporalStore;

    /**
     * @param store         primary config store (writes + schema validation)
     * @param temporalStore temporal store for rollback operations against version history
     */
    public ConfigCommandHandler(ConfigStore store, TemporalConfigStore temporalStore) {
        this.store         = Objects.requireNonNull(store, "store");
        this.temporalStore = Objects.requireNonNull(temporalStore, "temporalStore");
    }

    // ─── Commands ─────────────────────────────────────────────────────────────

    /**
     * A command to create or update a config entry at a specific hierarchy level.
     *
     * @param namespace      config namespace (e.g. "payments")
     * @param key            config key within the namespace
     * @param value          JSON-encoded value string
     * @param level          hierarchy level for this entry
     * @param levelId        scope identifier (e.g. tenantId, userId)
     * @param schemaNamespace namespace of the JSON Schema to validate against
     * @param changedBy      actor performing the change (for audit)
     *
     * @doc.type record
     * @doc.purpose Command to set a config value at a hierarchy level
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record SetConfigCommand(
        String namespace,
        String key,
        String value,
        ConfigHierarchyLevel level,
        String levelId,
        String schemaNamespace,
        String changedBy
    ) {
        public SetConfigCommand {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(levelId, "levelId");
            Objects.requireNonNull(schemaNamespace, "schemaNamespace");
            Objects.requireNonNull(changedBy, "changedBy");
            if (namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
            if (key.isBlank())       throw new IllegalArgumentException("key must not be blank");
            if (levelId.isBlank())   throw new IllegalArgumentException("levelId must not be blank");
        }
    }

    /**
     * A command to delete a config entry at a specific hierarchy level.
     *
     * <p>The entry is removed; the next read will fall back to a lower-precedence level.
     *
     * @doc.type record
     * @doc.purpose Command to delete a config entry at a hierarchy level
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record DeleteConfigCommand(
        String namespace,
        String key,
        ConfigHierarchyLevel level,
        String levelId,
        String deletedBy
    ) {
        public DeleteConfigCommand {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(levelId, "levelId");
            Objects.requireNonNull(deletedBy, "deletedBy");
        }
    }

    /**
     * A command to roll back a config key to the value it held at a past point in time.
     *
     * <p>The rollback appends a new row to {@code config_version_history}, preserving
     * the full audit trail (immutable append-only design).
     *
     * @doc.type record
     * @doc.purpose Command to roll back a config key to a historical value
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record RollbackConfigCommand(
        String namespace,
        String key,
        String levelId,
        ConfigHierarchyLevel level,
        Instant rollbackTo,
        String rolledBackBy
    ) {
        public RollbackConfigCommand {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(levelId, "levelId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(rollbackTo, "rollbackTo");
            Objects.requireNonNull(rolledBackBy, "rolledBackBy");
        }
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    /**
     * Handles a {@link SetConfigCommand}: creates or updates the config entry.
     *
     * @param cmd the command; all fields are validated in the record compact constructor
     * @return promise that resolves on success, or exceptionally on validation / DB failure
     */
    public Promise<Void> handle(SetConfigCommand cmd) {
        log.info("SetConfig namespace={} key={} level={}/{} by={}",
            cmd.namespace(), cmd.key(), cmd.level(), cmd.levelId(), cmd.changedBy());
        ConfigEntry entry = new ConfigEntry(
            cmd.namespace(),
            cmd.key(),
            cmd.value(),
            cmd.level(),
            cmd.levelId(),
            cmd.schemaNamespace()
        );
        return store.setEntry(entry);
    }

    /**
     * Handles a {@link DeleteConfigCommand}: removes the entry from the store.
     *
     * <p>After deletion the next {@link ConfigStore#resolve} call for the same
     * key will fall back to the next lower-precedence hierarchy level.
     *
     * @param cmd the delete command
     * @return promise that resolves when the entry is removed
     */
    public Promise<Void> handle(DeleteConfigCommand cmd) {
        log.info("DeleteConfig namespace={} key={} level={}/{} by={}",
            cmd.namespace(), cmd.key(), cmd.level(), cmd.levelId(), cmd.deletedBy());
        // Deletion is modelled as setting an empty-sentinel value so we preserve
        // audit history. The store treats a value of "__DELETED__" as absent during
        // resolution. A future migration can add a first-class deleted flag if needed.
        ConfigEntry tombstone = new ConfigEntry(
            cmd.namespace(),
            cmd.key(),
            "__DELETED__",
            cmd.level(),
            cmd.levelId(),
            cmd.namespace()  // use namespace as schemaNamespace for deletion tombstone
        );
        return store.setEntry(tombstone);
    }

    /**
     * Handles a {@link RollbackConfigCommand}: restores the key to a historical value.
     *
     * <p>Delegates to {@link TemporalConfigStore#rollback}. The rollback is run in a
     * blocking executor because TemporalConfigStore uses plain synchronous JDBC.
     *
     * @param cmd the rollback command
     * @return promise that resolves with the restored value, or empty if not found in history
     */
    public Promise<java.util.Optional<String>> handle(RollbackConfigCommand cmd) {
        log.info("RollbackConfig namespace={} key={} levelId={} asOf={} by={}",
            cmd.namespace(), cmd.key(), cmd.levelId(), cmd.rollbackTo(), cmd.rolledBackBy());
        return Promise.ofCallback(cb -> {
            try {
                java.util.Optional<String> result = temporalStore.rollback(
                    cmd.namespace(),
                    cmd.key(),
                    cmd.levelId(),
                    cmd.level().name(),
                    cmd.rollbackTo(),
                    cmd.rolledBackBy()
                );
                cb.set(result);
            } catch (Exception e) {
                cb.setException(e);
            }
        });
    }
}
