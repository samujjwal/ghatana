package com.ghatana.appplatform.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Generates type-safe event classes from Avro/JSON schemas stored
 *              in the K-08 schema registry.  Produces: typed event builder,
 *              serializer/deserializer, validation, and consumer-stub (typed handler
 *              interface per event type).  Generation is triggered on every schema publish.
 *              All generated outputs are backward-compatible with the previous version.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class EventSchemaCodeGenerationService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface SchemaRegistryPort {
        /** Retrieve a schema by its full name + version. Returns schema document JSON. */
        Promise<String> getSchema(String schemaFullName, String version);

        /** List all published schemas (name + latest version). */
        Promise<List<SchemaRef>> listSchemas();

        record SchemaRef(String fullName, String latestVersion, String format) {}
    }

    public interface EventClassGeneratorPort {
        /**
         * Generate typed event class code from a schema document.
         *
         * @param schemaJson    Avro or JSON schema
         * @param format        "AVRO" or "JSON"
         * @param targetLanguage "TYPESCRIPT" | "PYTHON" | "JAVA"
         */
        Promise<GeneratedArtifact> generate(String schemaJson, String format, String targetLanguage);

        record GeneratedArtifact(String language, String sourceCode, String builderCode,
                                  String serializerCode, String consumerStubCode) {}
    }

    public interface CompatibilityCheckerPort {
        /** Returns true if newSchema is backward-compatible with previousSchema. */
        Promise<Boolean> isBackwardCompatible(String previousSchemaJson, String newSchemaJson, String format);
    }

    public interface ArtifactPublisherPort {
        /** Publish generated event class artifacts to the package registry. */
        Promise<String> publish(String schemaName, String version, String language,
                                EventClassGeneratorPort.GeneratedArtifact artifact);
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record GenerationRecord(
        String generationId,
        String schemaFullName,
        String schemaVersion,
        String format,
        String status,        // PENDING | COMPAT_CHECK | GENERATING | PUBLISHING | DONE | FAILED
        List<String> languages,
        List<String> publishedPackageUrls,
        boolean backwardCompatible,
        String failureReason,
        String generatedAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final SchemaRegistryPort schemaRegistry;
    private final EventClassGeneratorPort eventClassGenerator;
    private final CompatibilityCheckerPort compatibilityChecker;
    private final ArtifactPublisherPort artifactPublisher;

    private final Counter generationTotal;
    private final Counter compatIssueTotal;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TARGET_LANGUAGES = List.of("TYPESCRIPT", "PYTHON", "JAVA");

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public EventSchemaCodeGenerationService(DataSource dataSource,
                                             Executor executor,
                                             MeterRegistry meterRegistry,
                                             SchemaRegistryPort schemaRegistry,
                                             EventClassGeneratorPort eventClassGenerator,
                                             CompatibilityCheckerPort compatibilityChecker,
                                             ArtifactPublisherPort artifactPublisher) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.schemaRegistry       = schemaRegistry;
        this.eventClassGenerator  = eventClassGenerator;
        this.compatibilityChecker = compatibilityChecker;
        this.artifactPublisher    = artifactPublisher;

        this.generationTotal  = Counter.builder("sdk.event_schema_generation.total")
                .description("Total event schema code generation runs")
                .register(meterRegistry);
        this.compatIssueTotal = Counter.builder("sdk.event_schema_generation.compat_issue_total")
                .description("Schemas rejected due to backward-compatibility issues")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generate typed event classes for a newly published schema version.
     * Performs backward-compatibility check before generation.
     */
    public Promise<GenerationRecord> generateForSchema(String schemaFullName, String newVersion,
                                                        String format) {
        return schemaRegistry.getSchema(schemaFullName, newVersion).then(newSchemaJson -> {
            // Check backward compat if a previous version exists
            String previousVersion = decrementVersion(newVersion);
            return schemaRegistry.getSchema(schemaFullName, previousVersion)
                .then(prevSchemaJson -> compatibilityChecker
                    .isBackwardCompatible(prevSchemaJson, newSchemaJson, format)
                    .then(compat -> {
                        if (!compat) {
                            compatIssueTotal.increment();
                            String genId = insertRecordBlocking(schemaFullName, newVersion, format,
                                                                "FAILED", false,
                                                                "Backward-compatibility check failed");
                            return Promise.of(buildRecord(genId, schemaFullName, newVersion, format,
                                "FAILED", false, List.of(), List.of(), "Backward-compatibility check failed"));
                        }
                        return generateAllLanguages(schemaFullName, newVersion, format, newSchemaJson, compat);
                    }))
                .mapException(notFound -> {
                    // No previous version — first publish; skip compat check
                    return generateAllLanguages(schemaFullName, newVersion, format, newSchemaJson, true);
                })
                .then(promise -> promise);  // flatten
        }).whenComplete((r, e) -> generationTotal.increment());
    }

    /** Regenerate all event classes for all schemas (useful after generator upgrade). */
    public Promise<List<GenerationRecord>> regenerateAll() {
        return schemaRegistry.listSchemas().then(schemas -> {
            List<Promise<GenerationRecord>> promises = schemas.stream()
                .map(s -> generateForSchema(s.fullName(), s.latestVersion(), s.format()))
                .toList();
            return Promise.all(promises);
        });
    }

    /** List generation history for a schema. */
    public Promise<List<GenerationRecord>> listHistory(String schemaFullName, int limit) {
        return Promise.ofBlocking(executor, () -> queryHistory(schemaFullName, limit));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Promise<GenerationRecord> generateAllLanguages(String schemaFullName, String version,
                                                            String format, String schemaJson,
                                                            boolean compat) {
        String genId = insertRecordBlocking(schemaFullName, version, format, "GENERATING", compat, null);
        List<Promise<String>> publishPromises = TARGET_LANGUAGES.stream()
            .map(lang -> eventClassGenerator.generate(schemaJson, format, lang)
                .then(artifact -> artifactPublisher.publish(schemaFullName, version, lang, artifact)))
            .toList();

        return Promise.all(publishPromises).map(urls -> {
            updateRecordBlocking(genId, "DONE", null, urls);
            return buildRecord(genId, schemaFullName, version, format,
                               "DONE", compat, TARGET_LANGUAGES, urls, null);
        }).mapException(ex -> {
            updateRecordBlocking(genId, "FAILED", ex.getMessage(), List.of());
            return ex;
        });
    }

    private String insertRecordBlocking(String schemaFullName, String version, String format,
                                        String status, boolean compat, String failureReason) {
        String sql = """
            INSERT INTO sdk_event_schema_generations
                (generation_id, schema_full_name, schema_version, format, status,
                 backward_compatible, failure_reason, generated_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, now())
            RETURNING generation_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaFullName);
            ps.setString(2, version);
            ps.setString(3, format);
            ps.setString(4, status);
            ps.setBoolean(5, compat);
            ps.setString(6, failureReason);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("generation_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert schema generation record", e);
        }
    }

    private void updateRecordBlocking(String genId, String status, String failure, List<String> urls) {
        String sql = """
            UPDATE sdk_event_schema_generations
               SET status = ?, failure_reason = ?, published_urls = ?::jsonb
             WHERE generation_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, failure);
            ps.setString(3, toJsonArray(urls));
            ps.setString(4, genId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update schema generation record " + genId, e);
        }
    }

    private List<GenerationRecord> queryHistory(String schemaFullName, int limit) {
        String sql = """
            SELECT generation_id, schema_full_name, schema_version, format, status,
                   backward_compatible, failure_reason, generated_at::text,
                   published_urls::text
              FROM sdk_event_schema_generations
             WHERE schema_full_name = ?
             ORDER BY generated_at DESC
             LIMIT ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaFullName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<GenerationRecord> result = new ArrayList<>();
                while (rs.next()) {
                    String status = rs.getString("status");
                    // Languages are derived from TARGET_LANGUAGES when the job completed;
                    // for DONE records we reconstruct the list from the constant to avoid
                    // storing a redundant copy per row.
                    List<String> languages = "DONE".equals(status) ? TARGET_LANGUAGES : List.of();
                    List<String> urls = parseJsonStringArray(rs.getString("published_urls"));
                    result.add(new GenerationRecord(
                        rs.getString("generation_id"),
                        rs.getString("schema_full_name"),
                        rs.getString("schema_version"),
                        rs.getString("format"),
                        status,
                        languages,
                        urls,
                        rs.getBoolean("backward_compatible"),
                        rs.getString("failure_reason"),
                        rs.getString("generated_at")
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query history for " + schemaFullName, e);
        }
    }

    /** Parse a JSON string array stored as JSONB, e.g. {@code ["http://...","http://..."]}. */
    private static List<String> parseJsonStringArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            node.forEach(n -> result.add(n.asText()));
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private GenerationRecord buildRecord(String genId, String schemaFullName, String version,
                                          String format, String status, boolean compat,
                                          List<String> languages, List<String> urls, String failure) {
        return new GenerationRecord(genId, schemaFullName, version, format, status,
                                    languages, urls, compat, failure, null);
    }

    /** Best-effort previous-version resolution: decrement the patch level. */
    private String decrementVersion(String version) {
        if (version == null || !version.contains(".")) return "NONE";
        String[] parts = version.split("\\.");
        try {
            int patch = Integer.parseInt(parts[parts.length - 1]);
            if (patch <= 1) return "NONE";
            parts[parts.length - 1] = String.valueOf(patch - 1);
            return String.join(".", parts);
        } catch (NumberFormatException e) {
            return "NONE";
        }
    }

    private String toJsonArray(List<String> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }
}
