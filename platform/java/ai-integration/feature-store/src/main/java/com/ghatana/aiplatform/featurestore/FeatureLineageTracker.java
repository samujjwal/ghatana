package com.ghatana.aiplatform.featurestore;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks feature lineage: derivation chain, version history, and dependencies.
 *
 * <p><b>Purpose</b><br>
 * Provides visibility into feature engineering pipeline:
 * - What raw inputs were used to compute a feature
 * - How feature versions evolved
 * - Cross-feature dependencies
 *
 * This enables:
 * - Root cause analysis (if output drift detected, which input feature changed?)
 * - Impact analysis (if input data quality degrades, which features are affected?)
 * - Reproducibility (recreate feature with exact version chain)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FeatureLineageTracker lineage = new FeatureLineageTracker(metrics);
 *
 * // Record raw feature ingestion
 * lineage.recordRawFeature("tenant-123", "transaction_amount", "source:database");
 *
 * // Record derived feature with inputs
 * lineage.recordDerivedFeature(
 *     "tenant-123",
 *     "transaction_amount_7d_avg",
 *     List.of("transaction_amount", "timestamp"),
 *     "sql:SELECT AVG(amount) FROM transactions WHERE created_at > now() - interval 7 day"
 * );
 *
 * // Query lineage
 * FeatureLineage lineage = lineage.getLineage("tenant-123", "transaction_amount_7d_avg");
 * System.out.println("Inputs: " + lineage.inputFeatures);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of ai-platform feature store. Used by data scientists and ops for:
 * - Feature discovery and documentation
 * - Debugging data pipeline issues
 * - Understanding feature staleness and recalculation frequency
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe: uses ConcurrentHashMap for lineage tracking.
 *
 * @doc.type class
 * @doc.purpose Feature lineage and version tracking
 * @doc.layer platform
 * @doc.pattern Repository
 */
public class FeatureLineageTracker {

    private static final Logger log = LoggerFactory.getLogger(FeatureLineageTracker.class);

    private final MetricsCollector metrics;

    // tenant:featureName -> FeatureLineageEntry
    private final ConcurrentHashMap<String, FeatureLineageEntry> lineage = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param metrics MetricsCollector for observability
     */
    public FeatureLineageTracker(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    /**
     * Record a raw feature (no derivation, sourced directly).
     *
     * GIVEN: Raw feature name and source
     * WHEN: recordRawFeature() is called
     * THEN: Feature lineage tracked with no dependencies
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     * @param source source description (e.g., "database:users", "api:segment")
     */
    public void recordRawFeature(String tenantId, String featureName, String source) {
        if (tenantId == null || featureName == null || source == null) {
            throw new NullPointerException("tenant, feature name, and source cannot be null");
        }

        String key = tenantId + ":" + featureName;
        FeatureLineageEntry entry = new FeatureLineageEntry(
            featureName,
            true,  // isRaw
            source,
            Collections.emptyList(),  // no inputs
            null,  // no derivation
            1      // version
        );

        lineage.put(key, entry);
        metrics.incrementCounter(
            "ai.feature.lineage.raw",
            "tenant", tenantId,
            "feature", featureName
        );

        log.debug("Recorded raw feature {}:{} from {}", tenantId, featureName, source);
    }

    /**
     * Record a derived feature with input dependencies.
     *
     * GIVEN: Feature name, input features, and derivation expression
     * WHEN: recordDerivedFeature() is called
     * THEN: Lineage tracked with dependency chain
     *
     * @param tenantId tenant identifier
     * @param featureName derived feature name
     * @param inputFeatures list of input feature names
     * @param derivationExpr derivation expression (SQL/code/description)
     */
    public void recordDerivedFeature(String tenantId, String featureName,
                                     List<String> inputFeatures, String derivationExpr) {
        if (tenantId == null || featureName == null || inputFeatures == null || derivationExpr == null) {
            throw new NullPointerException("tenant, feature, inputs, and derivation cannot be null");
        }

        String key = tenantId + ":" + featureName;
        FeatureLineageEntry existing = lineage.get(key);
        int nextVersion = existing != null ? existing.version + 1 : 1;

        FeatureLineageEntry entry = new FeatureLineageEntry(
            featureName,
            false,  // isRaw
            null,   // no source
            new ArrayList<>(inputFeatures),
            derivationExpr,
            nextVersion
        );

        lineage.put(key, entry);
        metrics.incrementCounter(
            "ai.feature.lineage.derived",
            "tenant", tenantId,
            "feature", featureName
        );

        log.debug("Recorded derived feature {}:{} v{} with {} inputs",
            tenantId, featureName, nextVersion, inputFeatures.size());
    }

    /**
     * Get full lineage for a feature.
     *
     * GIVEN: Tenant and feature name
     * WHEN: getLineage() is called
     * THEN: Returns full lineage including version history
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     * @return FeatureLineage with history and dependencies, or null if not found
     */
    public FeatureLineage getLineage(String tenantId, String featureName) {
        String key = tenantId + ":" + featureName;
        FeatureLineageEntry entry = lineage.get(key);

        if (entry == null) {
            return null;
        }

        // Recursively collect input lineage
        Set<String> transitiveInputs = new HashSet<>(entry.inputFeatures);
        for (String input : entry.inputFeatures) {
            FeatureLineage inputLineage = getLineage(tenantId, input);
            if (inputLineage != null) {
                transitiveInputs.addAll(inputLineage.transitiveInputs);
            }
        }

        return new FeatureLineage(
            featureName,
            entry.version,
            entry.isRaw,
            entry.source,
            entry.inputFeatures,
            transitiveInputs,
            entry.derivationExpr,
            entry.createdAtMs
        );
    }

    /**
     * Get all features for a tenant.
     *
     * @param tenantId tenant identifier
     * @return map of feature names to lineage entries
     */
    public Map<String, FeatureLineageEntry> getFeaturesForTenant(String tenantId) {
        String prefix = tenantId + ":";
        Map<String, FeatureLineageEntry> result = new HashMap<>();

        lineage.forEach((key, entry) -> {
            if (key.startsWith(prefix)) {
                String featureName = key.substring(prefix.length());
                result.put(featureName, entry);
            }
        });

        return result;
    }

    /**
     * Record feature version update (e.g., pipeline recompute).
     *
     * @param tenantId tenant identifier
     * @param featureName feature name
     */
    public void recordUpdate(String tenantId, String featureName) {
        String key = tenantId + ":" + featureName;
        FeatureLineageEntry existing = lineage.get(key);

        if (existing != null) {
            metrics.incrementCounter(
                "ai.feature.lineage.update",
                "tenant", tenantId,
                "feature", featureName
            );
        }
    }

    /**
     * Feature lineage result (immutable).
     */
    public static class FeatureLineage {
        public final String featureName;
        public final int version;
        public final boolean isRaw;
        public final String source;
        public final List<String> directInputs;
        public final Set<String> transitiveInputs;
        public final String derivationExpr;
        public final long createdAtMs;

        public FeatureLineage(String featureName, int version, boolean isRaw, String source,
                             List<String> directInputs, Set<String> transitiveInputs,
                             String derivationExpr, long createdAtMs) {
            this.featureName = featureName;
            this.version = version;
            this.isRaw = isRaw;
            this.source = source;
            this.directInputs = Collections.unmodifiableList(directInputs);
            this.transitiveInputs = Collections.unmodifiableSet(transitiveInputs);
            this.derivationExpr = derivationExpr;
            this.createdAtMs = createdAtMs;
        }
    }

    /**
     * Internal lineage entry.
     */
    public static class FeatureLineageEntry {
        public final String featureName;
        public final boolean isRaw;
        public final String source;
        public final List<String> inputFeatures;
        public final String derivationExpr;
        public final int version;
        public final long createdAtMs;

        public FeatureLineageEntry(String featureName, boolean isRaw, String source,
                                  List<String> inputFeatures, String derivationExpr, int version) {
            this.featureName = featureName;
            this.isRaw = isRaw;
            this.source = source;
            this.inputFeatures = inputFeatures;
            this.derivationExpr = derivationExpr;
            this.version = version;
            this.createdAtMs = Instant.now().toEpochMilli();
        }
    }
}
