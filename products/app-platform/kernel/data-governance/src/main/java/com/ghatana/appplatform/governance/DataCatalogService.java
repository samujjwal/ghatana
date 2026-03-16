package com.ghatana.appplatform.governance;

import com.ghatana.appplatform.governance.port.DataCatalogStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Centralized data catalog that inventories all data assets across services.
 *              Auto-discovery: on service registration (K-05 SchemaRegistered event) assets
 *              are upserted into the catalog. Provides search/browse/tag REST API surface.
 *              OpenMetadata-compatible metadata model. Satisfies STORY-K08-001.
 * @doc.layer   Kernel
 * @doc.pattern K-05 event-driven auto-discovery; OpenMetadata-compatible; Counter + Gauge;
 *              ON CONFLICT DO UPDATE idempotency; full-text search via pg_trgm.
 */
public class DataCatalogService {

    private final DataCatalogStore catalogStore;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          assetsRegisteredCounter;
    private final AtomicLong       catalogSizeGaugeValue = new AtomicLong(0);

    public DataCatalogService(DataCatalogStore catalogStore, Executor executor,
                               EventPort eventPort, MeterRegistry registry) {
        this.catalogStore           = catalogStore;
        this.executor               = executor;
        this.eventPort              = eventPort;
        this.assetsRegisteredCounter = Counter.builder("governance.catalog.assets_registered_total").register(registry);
        Gauge.builder("governance.catalog.total_assets", catalogSizeGaugeValue, AtomicLong::get)
                .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Enums & Records ─────────────────────────────────────────────────────

    public enum Classification { PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED }

    public record DataAsset(String assetId, String name, String serviceOwner, String schemaRef,
                             Classification classification, String description,
                             List<String> tags, List<String> lineageRefs,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<DataAsset> register(String name, String serviceOwner, String schemaRef,
                                        Classification classification, String description) {
        return Promise.ofBlocking(executor, () -> {
            String assetId = UUID.randomUUID().toString();
            DataAsset asset = catalogStore.upsert(assetId, name, serviceOwner, schemaRef,
                    classification, description);
            catalogSizeGaugeValue.set(catalogStore.countAssets());
            assetsRegisteredCounter.increment();
            eventPort.publish("governance.catalog.asset_registered", asset);
            return asset;
        });
    }

    public Promise<Optional<DataAsset>> findById(String assetId) {
        return Promise.ofBlocking(executor, () -> catalogStore.findById(assetId));
    }

    public Promise<List<DataAsset>> search(String query, String tag, Classification classification) {
        return Promise.ofBlocking(executor, () -> catalogStore.search(query, tag, classification));
    }

    public Promise<DataAsset> addTag(String assetId, String tag) {
        return Promise.ofBlocking(executor, () -> catalogStore.addTag(assetId, tag));
    }

    public Promise<DataAsset> updateClassification(String assetId, Classification classification) {
        return Promise.ofBlocking(executor, () -> catalogStore.updateClassification(assetId, classification));
    }
}
