package com.ghatana.appplatform.config.bundle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigSchema;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Exports a complete snapshot of all config schemas and entries as a
 * gzip-compressed JSON bundle suitable for air-gap deployment.
 *
 * <p><b>Bundle format</b>: A gzip-compressed JSON object written to the provided
 * {@link OutputStream}:
 * <pre>
 * {
 *   "manifest": { bundleId, environment, formatVersion, generatedAt, ... },
 *   "schemas":  [ { namespace, version, jsonSchema, description, defaults }, ... ],
 *   "entries":  [ { namespace, key, value, level, levelId, schemaNamespace }, ... ]
 * }
 * </pre>
 *
 * <p>The {@code manifest.contentHash} is the SHA-256 hex digest of the UTF-8
 * encoding of the JSON array of schemas followed by the JSON array of entries
 * (before gzip compression and before the manifest is included). This allows
 * independent verification of the content without the manifest header.
 *
 * <p>To sign a bundle after export, use {@link ConfigBundleSigner}.
 *
 * @doc.type class
 * @doc.purpose Exports config schemas and entries as a gzip-compressed air-gap bundle (K02-012)
 * @doc.layer product
 * @doc.pattern Service
 */
public class ConfigBundleExporter {

    private static final Logger LOG = Logger.getLogger(ConfigBundleExporter.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final String QUERY_SCHEMAS =
        "SELECT namespace, version, json_schema, description, defaults " +
        "FROM config_schemas ORDER BY namespace, version";

    private static final String QUERY_ENTRIES =
        "SELECT namespace, key, value, level, level_id, schema_namespace " +
        "FROM config_entries ORDER BY namespace, key, level, level_id";

    private final DataSource dataSource;
    private final String environment;
    private final String generatedBy;

    /**
     * @param dataSource  JDBC source for config_schemas and config_entries tables
     * @param environment target environment label written into the bundle manifest
     * @param generatedBy user or service requesting the export (written into manifest)
     */
    public ConfigBundleExporter(DataSource dataSource, String environment, String generatedBy) {
        this.dataSource  = Objects.requireNonNull(dataSource, "dataSource");
        this.environment = Objects.requireNonNull(environment, "environment").strip();
        this.generatedBy = Objects.requireNonNull(generatedBy, "generatedBy").strip();
        if (this.environment.isEmpty()) throw new IllegalArgumentException("environment must not be blank");
        if (this.generatedBy.isEmpty()) throw new IllegalArgumentException("generatedBy must not be blank");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Builds and writes a gzip-compressed JSON bundle to {@code output}.
     *
     * <p>This is a blocking call. Run inside {@code Promise.ofBlocking} when
     * used from an ActiveJ eventloop context.
     *
     * @param output target stream; caller is responsible for closing it
     * @return the bundle manifest (useful for logging / signing before writing)
     * @throws IOException  on serialization or stream errors
     * @throws SQLException on database query failures
     */
    public ConfigBundle export(OutputStream output) throws IOException, SQLException {
        LOG.info("[ConfigBundleExporter] Starting export for environment=" + environment);

        List<ConfigSchema>      schemas = querySchemas();
        List<ConfigBundleEntry> entries = queryEntries();

        String contentHash = computeContentHash(schemas, entries);

        ConfigBundleManifest manifest = new ConfigBundleManifest(
            UUID.randomUUID().toString(),
            environment,
            ConfigBundleManifest.CURRENT_FORMAT_VERSION,
            Instant.now(),
            generatedBy,
            entries.size(),
            schemas.size(),
            contentHash,
            null   // unsigned; caller may use ConfigBundleSigner to attach signature
        );

        ConfigBundle bundle = new ConfigBundle(manifest, schemas, entries);

        try (GZIPOutputStream gz = new GZIPOutputStream(output)) {
            MAPPER.writeValue(gz, new BundleJson(manifest, schemas, entries));
        }

        LOG.info("[ConfigBundleExporter] Export complete bundleId=" + manifest.bundleId()
            + " schemas=" + schemas.size() + " entries=" + entries.size());

        return bundle;
    }

    /**
     * Builds the in-memory {@link ConfigBundle} without writing to any stream.
     * Useful for signing before writing.
     *
     * @return the bundle (unsigned)
     * @throws SQLException on database query failures
     * @throws IOException  on content-hash computation failure
     */
    public ConfigBundle build() throws IOException, SQLException {
        List<ConfigSchema>      schemas = querySchemas();
        List<ConfigBundleEntry> entries = queryEntries();

        String contentHash = computeContentHash(schemas, entries);

        ConfigBundleManifest manifest = new ConfigBundleManifest(
            UUID.randomUUID().toString(),
            environment,
            ConfigBundleManifest.CURRENT_FORMAT_VERSION,
            Instant.now(),
            generatedBy,
            entries.size(),
            schemas.size(),
            contentHash,
            null
        );

        return new ConfigBundle(manifest, schemas, entries);
    }

    /**
     * Writes a previously-built (and optionally signed) bundle to the output stream.
     *
     * @param bundle the bundle to serialise
     * @param output target stream; caller is responsible for closing it
     * @throws IOException on serialization or stream errors
     */
    public void write(ConfigBundle bundle, OutputStream output) throws IOException {
        try (GZIPOutputStream gz = new GZIPOutputStream(output)) {
            MAPPER.writeValue(gz, new BundleJson(
                bundle.manifest(), bundle.schemas(), bundle.entries()));
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private List<ConfigSchema> querySchemas() throws SQLException {
        List<ConfigSchema> schemas = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_SCHEMAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                schemas.add(new ConfigSchema(
                    rs.getString("namespace"),
                    rs.getString("version"),
                    rs.getString("json_schema"),
                    rs.getString("description"),
                    rs.getString("defaults")
                ));
            }
        }
        return schemas;
    }

    private List<ConfigBundleEntry> queryEntries() throws SQLException {
        List<ConfigBundleEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_ENTRIES);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(new ConfigBundleEntry(
                    rs.getString("namespace"),
                    rs.getString("key"),
                    rs.getString("value"),
                    ConfigHierarchyLevel.valueOf(rs.getString("level")),
                    rs.getString("level_id"),
                    rs.getString("schema_namespace")
                ));
            }
        }
        return entries;
    }

    /**
     * SHA-256 of UTF-8 JSON encoding of schemas array + entries array (concatenated).
     */
    private String computeContentHash(
            List<ConfigSchema> schemas, List<ConfigBundleEntry> entries) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(MAPPER.writeValueAsBytes(schemas));
            digest.update(MAPPER.writeValueAsBytes(entries));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Jackson serialisation view ─────────────────────────────────────────────

    /** Internal DTO used only for JSON serialisation of the bundle file. */
    record BundleJson(
        ConfigBundleManifest manifest,
        List<ConfigSchema> schemas,
        List<ConfigBundleEntry> entries
    ) {}
}
