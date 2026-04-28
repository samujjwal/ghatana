/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — GDPR Data Service
 */
package com.ghatana.yappc.services.lifecycle.gdpr;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GDPR data erasure and export service.
 *
 * <p>Provides tenant-scoped data operations required for GDPR compliance:
 * <ul>
 *   <li>Right to erasure (Article 17) — {@link #deleteAllTenantData} and {@link #deleteUserData}</li>
 *   <li>Right to data portability (Article 20) — {@link #exportTenantData}</li>
 * </ul>
 *
 * <p>Each collection registered via {@link #registerCollection} is asked to delete or export
 * its records when the corresponding GDPR operation is triggered. Collections implement
 * {@link DeletableCollection} and/or {@link ExportableCollection} as appropriate.
 *
 * @doc.type class
 * @doc.purpose GDPR erasure and portability operations for YAPPC lifecycle service
 * @doc.layer product
 * @doc.pattern Service, Composite
 */
public final class GdprDataService {

    private static final Logger log = LoggerFactory.getLogger(GdprDataService.class);

    /** Registered collections keyed by logical name. */
    private final Map<String, DeletableCollection>  deletableCollections  = new LinkedHashMap<>();
    private final Map<String, ExportableCollection> exportableCollections = new LinkedHashMap<>();

    // ── Registration API ──────────────────────────────────────────────────────

    /**
     * Registers a collection as a GDPR deletion target.
     *
     * @param name       logical collection name (used in erasure summary)
     * @param collection collection adapter
     * @return this service (fluent)
     */
    public GdprDataService registerCollection(String name, DeletableCollection collection) {
        Objects.requireNonNull(name,       "name");
        Objects.requireNonNull(collection, "collection");
        deletableCollections.put(name, collection);
        return this;
    }

    /**
     * Registers a collection as a GDPR export source.
     *
     * @param name       logical collection name
     * @param collection collection adapter
     * @return this service (fluent)
     */
    public GdprDataService registerCollection(String name, ExportableCollection collection) {
        Objects.requireNonNull(name,       "name");
        Objects.requireNonNull(collection, "collection");
        exportableCollections.put(name, collection);
        return this;
    }

    // ── Operations ────────────────────────────────────────────────────────────

    /**
     * Deletes all data for the given tenant across all registered deletable collections.
     *
     * @param tenantId tenant to erase
     * @return promise resolving to a summary map of {@code collectionName → rowsDeleted}
     */
    public Promise<Map<String, Long>> deleteAllTenantData(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        Map<String, Long> summary = new ConcurrentHashMap<>();
        log.info("GDPR erasure: starting deletion across {} collections for tenantId={}",
                deletableCollections.size(), tenantId);

        if (deletableCollections.isEmpty()) {
            summary.put("_note", 0L);
            return Promise.of(Map.copyOf(summary));
        }

        // Execute deletions sequentially to avoid overwhelming the database
        Promise<Void> chain = Promise.complete();
        for (Map.Entry<String, DeletableCollection> entry : deletableCollections.entrySet()) {
            String name  = entry.getKey();
            DeletableCollection col = entry.getValue();
            chain = chain.then(ignored ->
                    col.deleteTenantData(tenantId)
                       .whenResult(rows -> {
                           summary.put(name, rows);
                           log.debug("GDPR: deleted {} rows from '{}' for tenantId={}", rows, name, tenantId);
                       })
                       .mapException(ex -> {
                           log.error("GDPR: deletion failed for collection '{}': {}", name, ex.getMessage(), ex);
                           summary.put(name, -1L);
                           return ex;
                       })
                       .toVoid());
        }

        return chain.map(ignored -> Map.copyOf(summary));
    }

    /**
     * Exports all data for a tenant from all registered exportable collections.
     *
     * @param tenantId tenant to export
     * @return promise resolving to a {@link TenantDataExport}
     */
    public Promise<TenantDataExport> exportTenantData(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        Map<String, List<Map<String, Object>>> collectionData = new LinkedHashMap<>();

        Promise<Void> chain = Promise.complete();
        for (Map.Entry<String, ExportableCollection> entry : exportableCollections.entrySet()) {
            String name  = entry.getKey();
            ExportableCollection col = entry.getValue();
            chain = chain.then(ignored ->
                    col.exportTenantData(tenantId)
                       .whenResult(records -> {
                           collectionData.put(name, records);
                           log.debug("GDPR export: {} records from '{}' for tenantId={}", records.size(), name, tenantId);
                       })
                       .mapException(ex -> {
                           log.error("GDPR export: failed for '{}': {}", name, ex.getMessage(), ex);
                           collectionData.put(name, List.of());
                           return ex;
                       })
                       .toVoid());
        }

        return chain.map(ignored -> {
            long total = collectionData.values().stream().mapToLong(List::size).sum();
            return new TenantDataExport(tenantId, Instant.now().toString(), collectionData, total);
        });
    }

    /**
     * Deletes personal data for a specific user within a tenant.
     *
     * @param tenantId tenant context
     * @param userId   user whose data should be erased
     * @return promise resolving to a summary map
     */
    public Promise<Map<String, Long>> deleteUserData(String tenantId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId,   "userId");

        Map<String, Long> summary = new ConcurrentHashMap<>();

        Promise<Void> chain = Promise.complete();
        for (Map.Entry<String, DeletableCollection> entry : deletableCollections.entrySet()) {
            String name  = entry.getKey();
            DeletableCollection col = entry.getValue();
            chain = chain.then(ignored ->
                    col.deleteUserData(tenantId, userId)
                       .whenResult(rows -> summary.put(name, rows))
                       .mapException(ex -> {
                           log.warn("GDPR user delete failed for '{}': {}", name, ex.getMessage());
                           summary.put(name, -1L);
                           return ex;
                       })
                       .toVoid());
        }

        return chain.map(ignored -> Map.copyOf(summary));
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * SPI for collections that support tenant-scoped deletion.
     *
     * @doc.type interface
     * @doc.purpose GDPR deletion adapter for data collections
     * @doc.layer product
     * @doc.pattern SPI
     */
    public interface DeletableCollection {
        /**
         * Delete all records for the tenant.
         *
         * @param tenantId tenant
         * @return number of records deleted
         */
        Promise<Long> deleteTenantData(String tenantId);

        /**
         * Delete personal data records for a specific user within the tenant.
         *
         * @param tenantId tenant
         * @param userId   user
         * @return number of records deleted
         */
        Promise<Long> deleteUserData(String tenantId, String userId);
    }

    /**
     * SPI for collections that support data export.
     *
     * @doc.type interface
     * @doc.purpose GDPR export adapter for data collections
     * @doc.layer product
     * @doc.pattern SPI
     */
    public interface ExportableCollection {
        /**
         * Export all records for the tenant as a list of plain maps.
         *
         * @param tenantId tenant
         * @return all records as field-value maps
         */
        Promise<List<Map<String, Object>>> exportTenantData(String tenantId);
    }

    /**
     * Immutable GDPR data export result.
     *
     * @doc.type class
     * @doc.purpose GDPR Article 20 data portability payload
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record TenantDataExport(
            String tenantId,
            String exportedAt,
            Map<String, List<Map<String, Object>>> collections,
            long totalRecords
    ) {}
}
