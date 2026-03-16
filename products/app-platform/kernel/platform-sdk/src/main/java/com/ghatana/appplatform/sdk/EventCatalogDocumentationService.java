package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Build the event catalog from K-08 schema registry and K-05 topic registry.
 *              Outputs AsyncAPI 2.x format, a searchable web UI, and event lineage data.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter; Promise.ofBlocking; CI-triggered
 *
 * STORY-K12-011: Event catalog documentation
 */
public class EventCatalogDocumentationService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface SchemaRegistryPort {
        List<EventSchema> listSchemas() throws Exception;
        EventSchema getSchema(String schemaId) throws Exception;
    }

    public interface TopicRegistryPort {
        List<TopicInfo> listTopics() throws Exception;
    }

    public interface CatalogStoragePort {
        String store(String filename, byte[] content) throws Exception;
        Optional<byte[]> load(String filename) throws Exception;
        List<CatalogEntry> listEntries() throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record EventSchema(
        String schemaId,
        String eventType,
        String version,
        String avroSchemaDef,
        String description,
        String ownerService
    ) {}

    public record TopicInfo(
        String topicName,
        String eventType,
        String partitionKey,
        int partitionCount,
        String retentionPolicy
    ) {}

    public record EventLineageEdge(String produces, String consumes, String relationship) {}

    public record CatalogEntry(String eventType, String version, String asyncApiUrl, String generatedAt) {}

    public record EventCatalog(
        String version,
        List<AsyncApiDocument> documents,
        List<EventLineageEdge> lineage,
        String catalogIndexUrl
    ) {}

    public record AsyncApiDocument(
        String eventType,
        String asyncApiJson,
        String storedUrl
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final SchemaRegistryPort schemaRegistry;
    private final TopicRegistryPort topicRegistry;
    private final CatalogStoragePort catalogStorage;
    private final Executor executor;
    private final Counter catalogBuildCounter;

    public EventCatalogDocumentationService(
        SchemaRegistryPort schemaRegistry,
        TopicRegistryPort topicRegistry,
        CatalogStoragePort catalogStorage,
        MeterRegistry registry,
        Executor executor
    ) {
        this.schemaRegistry  = schemaRegistry;
        this.topicRegistry   = topicRegistry;
        this.catalogStorage  = catalogStorage;
        this.executor        = executor;
        this.catalogBuildCounter = Counter.builder("sdk.event.catalog.builds").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Build and publish the full event catalog: one AsyncAPI doc per event type. */
    public Promise<EventCatalog> buildCatalog(String catalogVersion) {
        return Promise.ofBlocking(executor, () -> {
            List<EventSchema> schemas = schemaRegistry.listSchemas();
            List<TopicInfo> topics = topicRegistry.listTopics();

            // Map eventType → topics
            Map<String, List<TopicInfo>> topicsByEvent = new HashMap<>();
            for (TopicInfo t : topics) {
                topicsByEvent.computeIfAbsent(t.eventType(), k -> new ArrayList<>()).add(t);
            }

            List<AsyncApiDocument> documents = new ArrayList<>();
            List<EventLineageEdge> lineage = new ArrayList<>();

            for (EventSchema schema : schemas) {
                String asyncApiJson = buildAsyncApiDocument(schema, topicsByEvent.getOrDefault(schema.eventType(), List.of()));
                String filename = "events/" + schema.eventType() + "-" + schema.version() + ".json";
                String url = catalogStorage.store(filename, asyncApiJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                documents.add(new AsyncApiDocument(schema.eventType(), asyncApiJson, url));

                // Infer lineage from ownerService annotation
                if (schema.ownerService() != null) {
                    lineage.add(new EventLineageEdge(schema.ownerService(), schema.eventType(), "PRODUCES"));
                }
            }

            // Build catalog index HTML
            byte[] indexHtml = buildIndexHtml(documents, catalogVersion);
            String indexUrl = catalogStorage.store("catalog-index.html", indexHtml);

            catalogBuildCounter.increment();
            return new EventCatalog(catalogVersion, documents, lineage, indexUrl);
        });
    }

    /** Search the catalog for event types matching a keyword. */
    public Promise<List<CatalogEntry>> search(String keyword) {
        return Promise.ofBlocking(executor, () -> {
            List<CatalogEntry> all = catalogStorage.listEntries();
            String lc = keyword.toLowerCase();
            return all.stream()
                .filter(e -> e.eventType().toLowerCase().contains(lc))
                .toList();
        });
    }

    /** Return lineage edges as a JSON adjacency list for the lineage visualization UI. */
    public Promise<String> getLineageJson(String catalogVersion) {
        return Promise.ofBlocking(executor, () -> {
            Optional<byte[]> stored = catalogStorage.load("lineage-" + catalogVersion + ".json");
            return stored.map(b -> new String(b, java.nio.charset.StandardCharsets.UTF_8))
                .orElse("{}");
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String buildAsyncApiDocument(EventSchema schema, List<TopicInfo> topics) {
        StringBuilder channels = new StringBuilder();
        for (TopicInfo t : topics) {
            channels.append("\"").append(t.topicName()).append("\":{")
                .append("\"subscribe\":{\"message\":{\"$ref\":\"#/components/schemas/")
                .append(schema.eventType()).append("\"}}}},");
        }
        return "{" +
            "\"asyncapi\":\"2.6.0\"," +
            "\"info\":{\"title\":\"" + schema.eventType() + "\",\"version\":\"" + schema.version() + "\"," +
            "\"description\":\"" + escape(schema.description()) + "\"}," +
            "\"channels\":{" + (channels.length() > 0 ? channels.substring(0, channels.length() - 1) : "") + "}," +
            "\"components\":{\"schemas\":{\"" + schema.eventType() + "\":" + schema.avroSchemaDef() + "}}" +
            "}";
    }

    private byte[] buildIndexHtml(List<AsyncApiDocument> docs, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><title>Event Catalog v").append(version)
          .append("</title></head><body><h1>Event Catalog</h1><ul>");
        for (AsyncApiDocument d : docs) {
            sb.append("<li><a href=\"").append(d.storedUrl()).append("\">").append(d.eventType()).append("</a></li>");
        }
        sb.append("</ul></body></html>");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escape(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
}
