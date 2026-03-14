package com.ghatana.datacloud.infrastructure.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.entity.Entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared conversion utilities between {@link Entity} domain objects and the
 * {@code Map<String, Object>} documents used by storage connectors.
 *
 * <h2>Motivation — Code Deduplication</h2>
 * <p>Before this class was introduced, every storage connector ({@code OpenSearchConnector},
 * {@code ClickHouseTimeSeriesConnector}, {@code PostgresJsonbConnector}, etc.) contained
 * structurally identical {@code docFrom} / {@code entityFrom} / {@code mapToJson} /
 * {@code jsonToMap} helper methods. Divergences accumulated across copies (e.g. different
 * null-handling for {@code collectionName}), introducing subtle inconsistencies and making
 * maintenance harder. This class provides a single, fully-tested canonical implementation.
 *
 * <h2>Document conventions</h2>
 * <ul>
 *   <li>Metadata fields injected into documents use the {@code _dc_} prefix so they are
 *       easily distinguished from user-provided data fields. Connectors may choose to store
 *       metadata in a separate column/field family instead of embedding it in the JSON
 *       body — they should then omit the injection step and use their own metadata path.</li>
 *   <li>User-visible entity data fields are passed through without modification.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>All methods are stateless and thread-safe. The shared {@link ObjectMapper} instance
 * itself is configured to be thread-safe (Jackson's default configuration is thread-safe
 * after initial setup).
 *
 * @doc.type class
 * @doc.purpose Shared entity ↔ document conversion for storage connectors (deduplication)
 * @doc.layer product
 * @doc.pattern Utility, Value Converter
 */
public final class EntityDocumentMapper {

    // =========================================================================
    //  Metadata field constants (shared across all connectors that embed metadata)
    // =========================================================================

    /** Tenant identifier injected into every document. */
    public static final String FIELD_TENANT_ID       = "_dc_tenant_id";

    /** Collection name injected into every document so cross-collection queries work. */
    public static final String FIELD_COLLECTION_NAME = "_dc_collection_name";

    /** Entity UUID injected into every document as a string for round-trip fidelity. */
    public static final String FIELD_ENTITY_ID       = "_dc_entity_id";

    /** Set of all injected metadata fields for efficient exclusion in {@link #fromDocument}. */
    private static final Set<String> METADATA_FIELDS = Set.of(
            FIELD_TENANT_ID, FIELD_COLLECTION_NAME, FIELD_ENTITY_ID);

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // utility class — no instances
    private EntityDocumentMapper() {}

    // =========================================================================
    //  Entity → Document
    // =========================================================================

    /**
     * Converts an {@link Entity} to a flat {@code Map} document suitable for
     * storage in document databases (OpenSearch, CouchDB, etc.).
     *
     * <p>The returned map starts with a shallow copy of {@code entity.getData()},
     * then injects the three {@code _dc_*} metadata fields so they can be used
     * for tenant scoping, collection filtering, and ID-based retrieval.
     *
     * <p>Callers that store metadata separately (e.g. as dedicated RDBMS columns
     * or ClickHouse String columns) should avoid calling this method and instead
     * use {@link #toJson(Map)} directly on {@code entity.getData()}.
     *
     * @param entity the entity to convert; must not be {@code null}
     * @return mutable document map containing entity data + DC metadata fields
     */
    public static Map<String, Object> toDocument(Entity entity) {
        Map<String, Object> doc = new HashMap<>(
                entity.getData() != null ? entity.getData() : Map.of());
        doc.put(FIELD_TENANT_ID,       entity.getTenantId());
        doc.put(FIELD_COLLECTION_NAME, entity.getCollectionName());
        doc.put(FIELD_ENTITY_ID,       entity.getId() != null ? entity.getId().toString() : null);
        return doc;
    }

    // =========================================================================
    //  Document → Entity
    // =========================================================================

    /**
     * Reconstructs an {@link Entity} from a document map.
     *
     * <p>The three {@code _dc_*} metadata fields are extracted and removed from the
     * returned entity's data map so that callers always receive a clean user-data
     * payload without internal metadata pollution.
     *
     * <p>If {@code _dc_entity_id} is absent or unparseable, a new random UUID is
     * assigned rather than throwing, preserving re-hydration resilience.
     *
     * @param source the raw document map from storage (not modified)
     * @return reconstructed {@code Entity}
     */
    @SuppressWarnings("unchecked")
    public static Entity fromDocument(Map<?, ?> source) {
        Map<String, Object> data = new HashMap<>((Map<String, Object>) source);

        String tenantId       = (String) data.remove(FIELD_TENANT_ID);
        String collectionName = (String) data.remove(FIELD_COLLECTION_NAME);
        String entityIdStr    = (String) data.remove(FIELD_ENTITY_ID);

        UUID entityId;
        try {
            entityId = entityIdStr != null ? UUID.fromString(entityIdStr) : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            entityId = UUID.randomUUID();
        }

        return Entity.builder()
                .id(entityId)
                .tenantId(tenantId  != null ? tenantId  : "")
                .collectionName(isBlankOrNull(collectionName) ? null : collectionName)
                .data(data)
                .build();
    }

    // =========================================================================
    //  JSON serialisation helpers
    // =========================================================================

    /**
     * Serialises a {@code Map} to a compact JSON string.
     *
     * <p>Returns {@code "{}"} if {@code data} is {@code null} or empty, or if
     * serialisation fails — rather than propagating a checked exception — because
     * storage connectors use this method inside {@link java.util.concurrent.Callable}
     * lambdas where unchecked propagation is preferred over checked-exception tunnelling.
     *
     * @param data entity data map (may be null)
     * @return JSON string; never {@code null}
     */
    public static String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Deserialises a JSON string to a {@code Map<String, Object>}.
     *
     * <p>Returns an empty map on blank input or parse failure rather than
     * throwing, because malformed JSON stored by older code should degrade
     * gracefully at read time instead of crashing the connector.
     *
     * @param json JSON text (may be null or blank)
     * @return parsed map; never {@code null}
     */
    public static Map<String, Object> fromJson(String json) {
        if (isBlankOrNull(json)) return Collections.emptyMap();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    // =========================================================================
    //  SQL injection prevention
    // =========================================================================

    /**
     * Escapes a string value for safe embedding in a SQL string literal by
     * replacing all single quotes with the SQL-standard escaped form {@code \'}.
     *
     * <p>This is safe for string <em>values</em> embedded inside SQL quotes
     * (e.g. {@code WHERE tenant_id = '<escaped>'}). It must NOT be used for
     * SQL identifiers (table/column names) — use {@link #escapeIdentifier} for
     * those.
     *
     * @param value the raw string; may be {@code null}
     * @return escaped string, or {@code ""} if {@code null}
     */
    public static String escapeValue(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    /**
     * Escapes a string for safe use as an identifier fragment embedded in SQL.
     *
     * <p>Identical to {@link #escapeValue} but named differently to make call-site
     * intent clear — identifiers and values need the same escaping in ClickHouse's
     * string quoting, but conceptually they are different.
     *
     * @param value the raw identifier string; may be {@code null}
     * @return escaped identifier, or {@code ""} if {@code null}
     */
    public static String escapeIdentifier(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    // =========================================================================
    //  Internal
    // =========================================================================

    private static boolean isBlankOrNull(String s) {
        return s == null || s.isBlank();
    }
}
