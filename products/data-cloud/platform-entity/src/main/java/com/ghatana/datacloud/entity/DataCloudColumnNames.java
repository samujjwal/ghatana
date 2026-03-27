package com.ghatana.datacloud.entity;

/**
 * Standard column names for JPA entities and native SQL queries.
 *
 * <p>Use these constants instead of string literals to prevent typos, simplify
 * refactoring, and ensure a consistent naming convention across all persistence
 * and query-builder code. Addresses FINDING-DC-L3.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * @Column(name = DataCloudColumnNames.TENANT_ID)
 * private String tenantId;
 *
 * // In native queries:
 * "WHERE " + DataCloudColumnNames.TENANT_ID + " = ?"
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralise all DB column name string literals
 * @doc.layer core
 * @doc.pattern Constants
 */
public final class DataCloudColumnNames {
    private DataCloudColumnNames() {}

    // ── Core identity ────────────────────────────────────────────────────────
    public static final String ID          = "id";
    public static final String TENANT_ID   = "tenant_id";
    public static final String NAME        = "name";
    public static final String LABEL       = "label";
    public static final String DESCRIPTION = "description";
    public static final String STATUS      = "status";
    public static final String ACTIVE      = "active";
    public static final String VERSION     = "version";

    // ── Collection / schema ──────────────────────────────────────────────────
    public static final String COLLECTION_ID      = "collection_id";
    public static final String COLLECTION_NAME    = "collection_name";
    public static final String RECORD_TYPE        = "record_type";
    public static final String FIELDS             = "fields";
    public static final String VALIDATION_SCHEMA  = "validation_schema";
    public static final String SCHEMA_VERSION     = "schema_version";
    public static final String STORAGE_PROFILE    = "storage_profile";
    public static final String STORAGE_CONFIG     = "storage_config";
    public static final String EVENT_CONFIG       = "event_config";
    public static final String RETENTION_POLICY   = "retention_policy";
    public static final String PERMISSIONS        = "permissions";
    public static final String PHYSICAL_MAPPING   = "physical_mapping";

    // ── Data / payload ───────────────────────────────────────────────────────
    /** Main JSONB data column. */
    public static final String DATA        = "data";
    /** JSONB metadata column. */
    public static final String METADATA    = "metadata";
    /** Legacy alias for the data column on entity tables. */
    public static final String ENTITY_DATA = "entity_data";

    // ── Event log ────────────────────────────────────────────────────────────
    public static final String EVENT_ID      = "event_id";
    public static final String EVENT_TYPE    = "event_type";
    public static final String PAYLOAD       = "payload";
    public static final String CONTENT_TYPE  = "content_type";
    public static final String IDEMPOTENCY_KEY = "idempotency_key";
    public static final String OFFSET_VALUE  = "offset_value";

    // ── Audit ────────────────────────────────────────────────────────────────
    public static final String CREATED_AT  = "created_at";
    public static final String CREATED_BY  = "created_by";
    public static final String UPDATED_AT  = "updated_at";
    public static final String UPDATED_BY  = "updated_by";
}

