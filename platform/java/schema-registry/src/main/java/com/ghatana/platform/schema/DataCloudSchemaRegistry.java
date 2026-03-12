package com.ghatana.platform.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SchemaRegistry} implementation that persists schemas as
 * {@code schema.registered} events in a Data-Cloud {@link EventLogStore}.
 *
 * <h2>Persistence Model</h2>
 * <p>Each schema registration appends a {@code schema.registered} event with
 * a JSON payload:
 * <pre>{@code
 * {
 *   "schemaName":    "OrderCreated",
 *   "schemaVersion": "1.0.0",
 *   "jsonSchema":    "{ \"$schema\": ... }",
 *   "compatibilityMode": "BACKWARD",
 *   "registeredAt":  "2025-01-01T00:00:00Z"
 * }
 * }</pre>
 *
 * <h2>In-Memory Cache</h2>
 * <p>Schemas are cached in a {@link ConcurrentHashMap} after first load.
 * On first access, all {@code schema.registered} events are replayed from
 * the EventLogStore to populate the cache.
 *
 * <h2>Compatibility Checking</h2>
 * <p>Implemented using structural analysis of the JSON Schema {@code required} array:
 * <ul>
 *   <li>BACKWARD: no previously-required field may be removed.</li>
 *   <li>FORWARD: no new required field may be added.</li>
 *   <li>FULL: both constraints apply.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Event-sourced SchemaRegistry backed by Data-Cloud EventLogStore
 * @doc.layer platform
 * @doc.pattern EventSourced, Repository
 */
public final class DataCloudSchemaRegistry implements SchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataCloudSchemaRegistry.class);

    /** Event type name used for schema registration events. */
    public static final String SCHEMA_EVENT_TYPE = "schema.registered";

    private static final int READ_BATCH_SIZE = 1000;

    private final EventLogStore eventLogStore;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory jsonSchemaFactory;

    /**
     * Cached view: qualifiedId ({@code name:version}) → {@link RegisteredSchema}.
     * Populated lazily on first read from the event log.
     */
    private final ConcurrentHashMap<String, RegisteredSchema> cache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a registry backed by the given EventLogStore.
     *
     * @param eventLogStore event store for persistence
     * @param tenantContext tenant scope for all operations
     */
    public DataCloudSchemaRegistry(
            @NotNull EventLogStore eventLogStore,
            @NotNull TenantContext tenantContext) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore must not be null");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        this.objectMapper = new ObjectMapper();
        this.jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    // ─── SchemaRegistry API ───────────────────────────────────────────────────

    @NotNull
    @Override
    public Promise<Optional<RegisteredSchema>> getSchema(
            @NotNull String schemaName, @NotNull String schemaVersion) {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(schemaVersion, "schemaVersion");

        return ensureCacheLoaded()
                .map(v -> Optional.ofNullable(cache.get(qualifiedId(schemaName, schemaVersion))));
    }

    @NotNull
    @Override
    public Promise<Optional<RegisteredSchema>> getLatestSchema(@NotNull String schemaName) {
        Objects.requireNonNull(schemaName, "schemaName");

        return ensureCacheLoaded()
                .map(v -> cache.entrySet().stream()
                        .filter(e -> e.getValue().schemaName().equals(schemaName))
                        .map(Map.Entry::getValue)
                        .max(Comparator.comparing(RegisteredSchema::registeredAt)));
    }

    @NotNull
    @Override
    public Promise<ValidationResult> validate(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull String payloadJson) {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(payloadJson, "payloadJson");

        return getSchema(schemaName, schemaVersion)
                .map(optional -> {
                    if (optional.isEmpty()) {
                        return ValidationResult.failure(
                                "/",
                                "Schema '" + schemaName + "' v" + schemaVersion + " not found in registry");
                    }
                    return validatePayload(optional.get().jsonSchema(), payloadJson);
                });
    }

    @NotNull
    @Override
    public Promise<RegisteredSchema> registerSchema(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull String jsonSchema) {
        return registerSchema(schemaName, schemaVersion, jsonSchema, CompatibilityMode.BACKWARD);
    }

    @NotNull
    @Override
    public Promise<RegisteredSchema> registerSchema(
            @NotNull String schemaName,
            @NotNull String schemaVersion,
            @NotNull String jsonSchema,
            @NotNull CompatibilityMode compatibilityMode) {
        Objects.requireNonNull(schemaName, "schemaName");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
        Objects.requireNonNull(compatibilityMode, "compatibilityMode");

        return ensureCacheLoaded()
                .then(v -> {
                    // Idempotency: same name+version already registered
                    String qid = qualifiedId(schemaName, schemaVersion);
                    RegisteredSchema existing = cache.get(qid);
                    if (existing != null) {
                        log.debug("Schema '{}' already registered — idempotent no-op", qid);
                        return Promise.of(existing);
                    }

                    // Compatibility check against latest version
                    Optional<RegisteredSchema> latest = cache.entrySet().stream()
                            .filter(e -> e.getValue().schemaName().equals(schemaName))
                            .map(Map.Entry::getValue)
                            .max(Comparator.comparing(RegisteredSchema::registeredAt));

                    if (latest.isPresent()) {
                        checkCompatibility(latest.get(), schemaName, schemaVersion,
                                jsonSchema, compatibilityMode);
                    }

                    // Persist as event
                    RegisteredSchema schema = new RegisteredSchema(
                            schemaName, schemaVersion, jsonSchema, compatibilityMode, Instant.now());
                    EventEntry event = buildEvent(schema);

                    return eventLogStore.append(tenantContext, event)
                            .map(offset -> {
                                cache.put(qid, schema);
                                log.info("Registered schema '{}' v{} ({}) at offset {}",
                                        schemaName, schemaVersion, compatibilityMode, offset);
                                return schema;
                            });
                });
    }

    // ─── Cache management ─────────────────────────────────────────────────────

    /**
     * Ensures the in-memory cache has been populated from the event log.
     * Subsequent calls after the first successful load are no-ops.
     */
    @NotNull
    private Promise<Void> ensureCacheLoaded() {
        if (cacheLoaded) return Promise.complete();

        return eventLogStore.readByType(tenantContext, SCHEMA_EVENT_TYPE, Offset.zero(), READ_BATCH_SIZE)
                .then(entries -> {
                    for (EventEntry entry : entries) {
                        try {
                            RegisteredSchema schema = deserialise(entry);
                            cache.put(schema.qualifiedId(), schema);
                        } catch (Exception e) {
                            log.warn("Failed to deserialise schema event {}: {}", entry.eventId(), e.getMessage());
                        }
                    }
                    log.info("Schema registry cache loaded: {} schema(s)", cache.size());
                    cacheLoaded = true;
                    return Promise.complete();
                });
    }

    // ─── Compatibility checking ────────────────────────────────────────────────

    /**
     * Checks schema evolution compatibility. Throws {@link SchemaCompatibilityException}
     * if the new schema violates the mode.
     */
    private void checkCompatibility(
            @NotNull RegisteredSchema existing,
            @NotNull String newName,
            @NotNull String newVersion,
            @NotNull String newJsonSchema,
            @NotNull CompatibilityMode mode) {

        Set<String> oldRequired = extractRequiredFields(existing.jsonSchema());
        Set<String> newRequired = extractRequiredFields(newJsonSchema);

        if (mode == CompatibilityMode.BACKWARD || mode == CompatibilityMode.FULL) {
            // BACKWARD: cannot remove previously-required fields
            Set<String> removed = new java.util.HashSet<>(oldRequired);
            removed.removeAll(newRequired);
            if (!removed.isEmpty()) {
                throw new SchemaCompatibilityException(newName, newVersion, mode,
                        "required fields were removed: " + removed);
            }
        }

        if (mode == CompatibilityMode.FORWARD || mode == CompatibilityMode.FULL) {
            // FORWARD: cannot add new required fields (old consumers can't satisfy them)
            Set<String> added = new java.util.HashSet<>(newRequired);
            added.removeAll(oldRequired);
            if (!added.isEmpty()) {
                throw new SchemaCompatibilityException(newName, newVersion, mode,
                        "new required fields added that old consumers cannot satisfy: " + added);
            }
        }
    }

    /**
     * Extracts the set of field names listed under the top-level {@code required} keyword
     * in a JSON Schema string.
     */
    @NotNull
    private Set<String> extractRequiredFields(@NotNull String jsonSchema) {
        try {
            JsonNode root = objectMapper.readTree(jsonSchema);
            JsonNode required = root.get("required");
            if (required == null || !required.isArray()) return Set.of();
            Set<String> fields = new java.util.HashSet<>();
            required.forEach(node -> {
                if (node.isTextual()) fields.add(node.asText());
            });
            return Collections.unmodifiableSet(fields);
        } catch (Exception e) {
            log.warn("Failed to extract required fields from schema — compatibility check skipped: {}", e.getMessage());
            return Set.of();
        }
    }

    // ─── Payload validation ────────────────────────────────────────────────────

    @NotNull
    private ValidationResult validatePayload(@NotNull String jsonSchemaStr, @NotNull String payloadJson) {
        try {
            JsonSchema schema = jsonSchemaFactory.getSchema(jsonSchemaStr);
            JsonNode payload = objectMapper.readTree(payloadJson);
            Set<ValidationMessage> messages = schema.validate(payload);
            if (messages.isEmpty()) {
                return ValidationResult.valid();
            }
            List<ValidationResult.ValidationError> errors = new ArrayList<>();
            for (ValidationMessage msg : messages) {
                errors.add(new ValidationResult.ValidationError(
                        msg.getInstanceLocation().toString(),
                        msg.getMessage()));
            }
            return ValidationResult.failure(errors);
        } catch (Exception e) {
            return ValidationResult.failure("/", "Validation failed: " + e.getMessage());
        }
    }

    // ─── Serialisation helpers ─────────────────────────────────────────────────

    @NotNull
    private EventEntry buildEvent(@NotNull RegisteredSchema schema) {
        try {
            Map<String, Object> payload = Map.of(
                    "schemaName", schema.schemaName(),
                    "schemaVersion", schema.schemaVersion(),
                    "jsonSchema", schema.jsonSchema(),
                    "compatibilityMode", schema.compatibilityMode().name(),
                    "registeredAt", schema.registeredAt().toString()
            );
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return EventEntry.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(SCHEMA_EVENT_TYPE)
                    .payload(bytes)
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise schema event for " +
                    schema.qualifiedId(), e);
        }
    }

    @NotNull
    private RegisteredSchema deserialise(@NotNull EventEntry entry) {
        try {
            byte[] bytes = new byte[entry.payload().remaining()];
            entry.payload().duplicate().get(bytes);
            Map<String, Object> map = objectMapper.readValue(
                    bytes, new TypeReference<Map<String, Object>>() {});

            return new RegisteredSchema(
                    (String) map.get("schemaName"),
                    (String) map.get("schemaVersion"),
                    (String) map.get("jsonSchema"),
                    CompatibilityMode.valueOf((String) map.get("compatibilityMode")),
                    Instant.parse((String) map.get("registeredAt"))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialise schema event " + entry.eventId(), e);
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    @NotNull
    private static String qualifiedId(@NotNull String name, @NotNull String version) {
        return name + ":" + version;
    }
}
