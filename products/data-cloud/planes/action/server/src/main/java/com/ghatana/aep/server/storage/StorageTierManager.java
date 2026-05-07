/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.storage;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.StorageTier;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Multi-tier storage manager for AEP entities (patterns, pipelines, agents).
 *
 * <h3>Tier Strategy</h3>
 * <ul>
 *   <li><b>HOT</b>  ({@value HOT_SUFFIX}): active entities accessed in the last hour —
 *       stored in the primary collection; full-fidelity, low-latency reads.</li>
 *   <li><b>WARM</b> ({@value WARM_SUFFIX}): entities accessed within the last 7 days —
 *       still in PostgreSQL-backed DataCloud but with the {@code lastTier=WARM} flag.</li>
 *   <li><b>COOL</b> ({@value COOL_SUFFIX}): entities older than 7 days but within 90 days —
 *       moved to a separate collection; metadata-only on tier read hits.</li>
 *   <li><b>COLD</b> ({@value COLD_SUFFIX}): entities older than 90 days — archive
 *       collection; accessed only for compliance/restore scenarios.</li>
 * </ul>
 *
 * <h3>Promotion / Demotion</h3>
 * <p>Access automatically promotes an entity to HOT. The {@link DataLifecycleManager}
 * calls this class periodically to demote entities that have not been accessed recently.
 *
 * <h3>Observability</h3>
 * <p>All tier hits, misses, promotions, demotions, and errors are tracked with
 * Micrometer counters and timers.
 *
 * @doc.type class
 * @doc.purpose Multi-tier storage management for AEP entity data (HOT/WARM/COOL/COLD)
 * @doc.layer product
 * @doc.pattern Strategy, Repository
 * @since 1.0.0
 */
public final class StorageTierManager {

    private static final Logger log = LoggerFactory.getLogger(StorageTierManager.class);

    /** Collection name suffix for HOT tier (base collection). */
    public static final String HOT_SUFFIX = "";
    /** Collection name suffix for WARM tier entities. */
    public static final String WARM_SUFFIX = "_warm";
    /** Collection name suffix for COOL tier entities. */
    public static final String COOL_SUFFIX = "_cool";
    /** Collection name suffix for COLD tier entities. */
    public static final String COLD_SUFFIX = "_cold";

    /** Field name storing the entity's current storage tier. */
    static final String TIER_FIELD = "storageTier";
    /** Field name storing last access timestamp (used for tier demotion decisions). */
    static final String LAST_ACCESSED_FIELD = "lastAccessedAt";

    private final DataCloudClient client;

    // Metrics
    private final Counter hotHits;
    private final Counter warmHits;
    private final Counter coolHits;
    private final Counter coldHits;
    private final Counter misses;
    private final Counter promotions;
    private final Counter demotions;
    private final Counter errors;
    private final Timer lookupTimer;

    /**
     * Constructs a tier manager with Micrometer observability.
     *
     * @param client        Data-Cloud client
     * @param meterRegistry Micrometer registry
     */
    public StorageTierManager(DataCloudClient client, MeterRegistry meterRegistry) {
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.hotHits     = Counter.builder("aep.storage.tier.hits").tag("tier", "HOT").register(meterRegistry);
        this.warmHits    = Counter.builder("aep.storage.tier.hits").tag("tier", "WARM").register(meterRegistry);
        this.coolHits    = Counter.builder("aep.storage.tier.hits").tag("tier", "COOL").register(meterRegistry);
        this.coldHits    = Counter.builder("aep.storage.tier.hits").tag("tier", "COLD").register(meterRegistry);
        this.misses      = Counter.builder("aep.storage.tier.misses").register(meterRegistry);
        this.promotions  = Counter.builder("aep.storage.tier.promotions").register(meterRegistry);
        this.demotions   = Counter.builder("aep.storage.tier.demotions").register(meterRegistry);
        this.errors      = Counter.builder("aep.storage.tier.errors").register(meterRegistry);
        this.lookupTimer = Timer.builder("aep.storage.tier.lookup.duration")
                .description("Tiered entity lookup duration")
                .register(meterRegistry);
    }

    // =========================================================================
    // Save (always to HOT tier)
    // =========================================================================

    /**
     * Saves an entity to the HOT tier (primary collection).
     *
     * <p>The entity data is augmented with tier metadata fields before persistence.
     * Callers should use the base collection name (without any suffix).
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name (e.g., {@code "aep_patterns"})
     * @param data       entity data; must contain {@code "id"} for stable keys
     * @return promise of the saved entity decorated with tier metadata
     */
    public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(data, "data");
        Map<String, Object> enriched = enrich(data, StorageTier.HOT);
        return client.save(tenantId, hotCollection(collection), enriched)
                .whenException(e -> {
                    errors.increment();
                    log.error("[tier-mgr] save failed tenant={} coll={}: {}", tenantId, collection, e.getMessage(), e);
                });
    }

    // =========================================================================
    // Tiered lookup (HOT → WARM → COOL → COLD, promote on hit)
    // =========================================================================

    /**
     * Tiered entity lookup — searches HOT first, then WARM, COOL, COLD.
     *
     * <p>On a cache miss in HOT/WARM but a hit in COOL/COLD, the entity is
     * automatically promoted to WARM tier (write-back promotion). This keeps
     * recently re-accessed entities in fast storage.
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param id         entity ID
     * @return promise of the entity if found in any tier
     */
    public Promise<Optional<TieredEntity>> findById(
            String tenantId, String collection, String id) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(id, "id");
        return lookupTimer.record(() ->
            lookupHot(tenantId, collection, id)
                .then(optHot -> {
                    if (optHot.isPresent()) {
                        hotHits.increment();
                        return Promise.of(optHot);
                    }
                    return lookupWarm(tenantId, collection, id)
                            .then(optWarm -> {
                                if (optWarm.isPresent()) {
                                    warmHits.increment();
                                    return touchTimestamp(tenantId, warmCollection(collection), id)
                                            .map(v -> optWarm);
                                }
                                return lookupCool(tenantId, collection, id)
                                        .then(optCool -> {
                                            if (optCool.isPresent()) {
                                                coolHits.increment();
                                                return promoteToWarm(tenantId, collection,
                                                        optCool.get().entity())
                                                        .map(v -> optCool);
                                            }
                                            return lookupCold(tenantId, collection, id)
                                                    .then(optCold -> {
                                                        if (optCold.isPresent()) {
                                                            coldHits.increment();
                                                            return promoteToWarm(tenantId, collection,
                                                                    optCold.get().entity())
                                                                    .map(v -> optCold);
                                                        }
                                                        misses.increment();
                                                        return Promise.of(Optional.empty());
                                                    });
                                        });
                            });
                })
        );
    }

    /**
     * Queries entities from HOT tier only (for real-time access patterns).
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param query      query specification
     * @return promise of matching entities from HOT tier
     */
    public Promise<List<TieredEntity>> queryHot(
            String tenantId, String collection, Query query) {
        return client.query(tenantId, hotCollection(collection), query)
                .map(entities -> entities.stream()
                        .map(e -> new TieredEntity(e, StorageTier.HOT))
                        .toList())
                .whenException(e -> errors.increment());
    }

    /**
     * Queries across HOT and WARM tiers, deduplicating by entity ID.
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param query      query applied to both tiers
     * @return promise of merged results (HOT takes precedence over WARM duplicates)
     */
    public Promise<List<TieredEntity>> queryHotAndWarm(
            String tenantId, String collection, Query query) {
        Promise<List<TieredEntity>> hotQuery = queryHot(tenantId, collection, query);
        Promise<List<TieredEntity>> warmQuery = client.query(tenantId, warmCollection(collection), query)
                .map(entities -> entities.stream()
                        .map(e -> new TieredEntity(e, StorageTier.WARM))
                        .toList())
                .whenException(e -> errors.increment());
        return Promises.toList(List.of(hotQuery, warmQuery))
                .map(lists -> mergeDeduped(lists.get(0), lists.get(1)));
    }

    // =========================================================================
    // Tier demotion (called by DataLifecycleManager)
    // =========================================================================

    /**
     * Demotes entities in a collection that have not been accessed since {@code idleSince}.
     *
     * <p>The demotion cascade is: HOT → WARM → COOL → COLD.
     * Already COLD entities are left in place (they will be purged by retention policy).
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param idleSince  entities not accessed since this instant will be demoted
     * @return promise of the number of entities demoted
     */
    public Promise<Integer> demoteIdleEntities(
            String tenantId, String collection, Instant idleSince) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(idleSince, "idleSince");
        Query q = Query.builder()
                .filter(Filter.lte(LAST_ACCESSED_FIELD, idleSince.toString()))
                .limit(10_000)
                .build();
        return client.query(tenantId, hotCollection(collection), q)
                .then(hotEntities -> {
                    List<Promise<Void>> moves = new ArrayList<>();
                    for (Entity e : hotEntities) {
                        moves.add(moveToWarm(tenantId, collection, e));
                    }
                    return Promises.all(moves).map(v -> {
                        if (!hotEntities.isEmpty()) {
                            demotions.increment(hotEntities.size());
                            log.info("[tier-mgr] Demoted {} entities HOT→WARM tenant={} coll={}",
                                    hotEntities.size(), tenantId, collection);
                        }
                        return hotEntities.size();
                    });
                })
                .whenException(e -> {
                    errors.increment();
                    log.error("[tier-mgr] demoteIdleEntities failed: {}", e.getMessage(), e);
                });
    }

    /**
     * Demotes WARM entities idle since {@code idleSince} to COOL tier.
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param idleSince  idle threshold
     * @return promise of demoted entity count
     */
    public Promise<Integer> demoteWarmToCool(
            String tenantId, String collection, Instant idleSince) {
        Query q = Query.builder()
                .filter(Filter.lte(LAST_ACCESSED_FIELD, idleSince.toString()))
                .limit(10_000)
                .build();
        return client.query(tenantId, warmCollection(collection), q)
                .then(warmEntities -> {
                    List<Promise<Void>> moves = new ArrayList<>();
                    for (Entity e : warmEntities) {
                        moves.add(moveToCool(tenantId, collection, e));
                    }
                    return Promises.all(moves).map(v -> {
                        demotions.increment(warmEntities.size());
                        return warmEntities.size();
                    });
                });
    }

    /**
     * Demotes COOL entities idle since {@code idleSince} to COLD tier.
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param idleSince  idle threshold
     * @return promise of demoted entity count
     */
    public Promise<Integer> demoteCoolToCold(
            String tenantId, String collection, Instant idleSince) {
        Query q = Query.builder()
                .filter(Filter.lte(LAST_ACCESSED_FIELD, idleSince.toString()))
                .limit(10_000)
                .build();
        return client.query(tenantId, coolCollection(collection), q)
                .then(coolEntities -> {
                    List<Promise<Void>> moves = new ArrayList<>();
                    for (Entity e : coolEntities) {
                        moves.add(moveToCold(tenantId, collection, e));
                    }
                    return Promises.all(moves).map(v -> {
                        demotions.increment(coolEntities.size());
                        return coolEntities.size();
                    });
                });
    }

    // =========================================================================
    // Delete across all tiers
    // =========================================================================

    /**
     * Deletes an entity from all tiers (used for GDPR erasure and explicit deletes).
     *
     * @param tenantId   tenant identifier
     * @param collection base collection name
     * @param id         entity ID
     * @return promise completing when all tier copies are removed
     */
    public Promise<Void> deleteFromAllTiers(String tenantId, String collection, String id) {
        return Promises.all(List.of(
                deleteIfExists(tenantId, hotCollection(collection), id),
                deleteIfExists(tenantId, warmCollection(collection), id),
                deleteIfExists(tenantId, coolCollection(collection), id),
                deleteIfExists(tenantId, coldCollection(collection), id)
        )).whenException(e -> {
            errors.increment();
            log.error("[tier-mgr] deleteFromAllTiers failed id={}: {}", id, e.getMessage(), e);
        });
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Promise<Optional<TieredEntity>> lookupHot(String t, String c, String id) {
        return client.findById(t, hotCollection(c), id)
                .map(opt -> opt.map(e -> new TieredEntity(e, StorageTier.HOT)));
    }

    private Promise<Optional<TieredEntity>> lookupWarm(String t, String c, String id) {
        return client.findById(t, warmCollection(c), id)
                .map(opt -> opt.map(e -> new TieredEntity(e, StorageTier.WARM)));
    }

    private Promise<Optional<TieredEntity>> lookupCool(String t, String c, String id) {
        return client.findById(t, coolCollection(c), id)
                .map(opt -> opt.map(e -> new TieredEntity(e, StorageTier.COOL)));
    }

    private Promise<Optional<TieredEntity>> lookupCold(String t, String c, String id) {
        return client.findById(t, coldCollection(c), id)
                .map(opt -> opt.map(e -> new TieredEntity(e, StorageTier.COLD)));
    }

    private Promise<Void> promoteToWarm(String tenantId, String collection, Entity entity) {
        Map<String, Object> promoted = enrich(entity.data(), StorageTier.WARM);
        return client.save(tenantId, warmCollection(collection), promoted)
                .then(saved -> deleteIfExists(tenantId, coolCollection(collection), entity.id())
                        .then(v2 -> deleteIfExists(tenantId, coldCollection(collection), entity.id())))
                .map(v -> {
                    promotions.increment();
                    return (Void) null;
                })
                .whenException(e -> errors.increment());
    }

    private Promise<Void> moveToWarm(String tenantId, String collection, Entity entity) {
        Map<String, Object> moved = enrich(entity.data(), StorageTier.WARM);
        return client.save(tenantId, warmCollection(collection), moved)
                .then(saved -> client.delete(tenantId, hotCollection(collection), entity.id()))
                .map(v -> (Void) null);
    }

    private Promise<Void> moveToCool(String tenantId, String collection, Entity entity) {
        Map<String, Object> moved = enrich(entity.data(), StorageTier.COOL);
        return client.save(tenantId, coolCollection(collection), moved)
                .then(saved -> client.delete(tenantId, warmCollection(collection), entity.id()))
                .map(v -> (Void) null);
    }

    private Promise<Void> moveToCold(String tenantId, String collection, Entity entity) {
        Map<String, Object> moved = enrich(entity.data(), StorageTier.COLD);
        return client.save(tenantId, coldCollection(collection), moved)
                .then(saved -> client.delete(tenantId, coolCollection(collection), entity.id()))
                .map(v -> (Void) null);
    }

    private Promise<Void> deleteIfExists(String tenantId, String collection, String id) {
        return client.findById(tenantId, collection, id)
                .then(opt -> opt.isPresent()
                        ? client.delete(tenantId, collection, id).map(v -> (Void) null)
                        : Promise.complete());
    }

    private Promise<Void> touchTimestamp(String tenantId, String collection, String id) {
        return client.findById(tenantId, collection, id)
                .then(opt -> {
                    if (opt.isEmpty()) return Promise.complete();
                    Map<String, Object> updated = new HashMap<>(opt.get().data());
                    updated.put(LAST_ACCESSED_FIELD, Instant.now().toString());
                    return client.save(tenantId, collection, updated).map(e -> (Void) null);
                });
    }

    private static Map<String, Object> enrich(Map<String, Object> data, StorageTier tier) {
        Map<String, Object> enriched = new HashMap<>(data);
        enriched.put(TIER_FIELD, tier.name());
        enriched.put(LAST_ACCESSED_FIELD, Instant.now().toString());
        return enriched;
    }

    private static List<TieredEntity> mergeDeduped(List<TieredEntity> hot, List<TieredEntity> warm) {
        Map<String, TieredEntity> byId = new HashMap<>();
        for (TieredEntity e : warm) byId.put(e.entity().id(), e);
        for (TieredEntity e : hot)  byId.put(e.entity().id(), e);   // HOT wins
        return List.copyOf(byId.values());
    }

    // =========================================================================
    // Collection name helpers
    // =========================================================================

    /** Returns the HOT (primary) collection name. */
    public static String hotCollection(String base)  { return base; }
    /** Returns the WARM collection name. */
    public static String warmCollection(String base) { return base + WARM_SUFFIX; }
    /** Returns the COOL collection name. */
    public static String coolCollection(String base) { return base + COOL_SUFFIX; }
    /** Returns the COLD collection name. */
    public static String coldCollection(String base) { return base + COLD_SUFFIX; }

    // =========================================================================
    // Value types
    // =========================================================================

    /**
     * An entity retrieved from a specific storage tier.
     *
     * @param entity the raw DataCloud entity
     * @param tier   the tier it was found in
     */
    public record TieredEntity(Entity entity, StorageTier tier) {

        /** Convenience access to entity data. */
        public Map<String, Object> data() { return entity.data(); }

        /** Convenience access to entity ID. */
        public String id() { return entity.id(); }

        /** Returns {@code true} if the entity is in a warm or colder tier. */
        public boolean needsPromotionConsideration() {
            return tier.isLowerThan(StorageTier.HOT);
        }

        /** Returns the age of the entity based on its {@code lastAccessedAt} field. */
        public Duration idleDuration() {
            Object raw = entity.data().get(LAST_ACCESSED_FIELD);
            if (raw == null) return Duration.ZERO;
            try {
                return Duration.between(Instant.parse(raw.toString()), Instant.now());
            } catch (Exception e) {
                return Duration.ZERO;
            }
        }
    }
}
