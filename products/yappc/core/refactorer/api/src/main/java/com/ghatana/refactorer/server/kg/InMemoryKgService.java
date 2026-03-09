package com.ghatana.refactorer.server.kg;

import com.ghatana.platform.core.exception.ErrorCodeMappers;
import com.ghatana.refactorer.server.kg.config.KgConfiguration;
import com.ghatana.refactorer.server.kg.config.KgConfigurationLoader;
import com.ghatana.refactorer.server.kg.learning.CorrelationAnalyzer;
import com.ghatana.refactorer.server.kg.learning.FrequentSequenceMiner;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-memory implementation of KgService for the refactorer.
 *
 *
 *
 * <p>
 * This implementation stores patterns in memory and is suitable for testing and
 *
 * development. For production use, this should be replaced with a persistent KG
 * backend
 *
 * (e.g., connecting to the pattern-compiler service).
 *
 *
 *
 * <p>
 * Binding Decision #11 enforcement: Refactorer uses KgService abstraction, not
 * direct
 *
 * pattern-compiler imports.
 *
 *
 *
 * <p>
 * Configuration-driven: Uses KgConfiguration for environment-specific tuning
 * (minSupport,
 *
 * minConfidence, pattern lifecycle, caching policies).
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Expose knowledge graph queries and updates for refactorer
 * workflows.
 *
 * @doc.layer product
 *
 * @doc.pattern Service
 *
 */
public final class InMemoryKgService implements KgService {

    private static final Logger logger = LogManager.getLogger(InMemoryKgService.class);

    private final Map<String, KgPattern> patternStore = new HashMap<>();
    private final AtomicInteger patternCounter = new AtomicInteger(0);
    private final Map<String, Integer> matchCounter = new HashMap<>();
    private final KgConfiguration configuration;

    /**
     * Creates an InMemoryKgService with configuration loaded from the current
     * environment.
     *
     * <p>
     * Configuration is loaded using
     * KgConfigurationLoader.loadFromSystemProperties(), which respects the
     * 'kg.environment' system property. If not set, defaults to 'default'
     * configuration.
     */
    public InMemoryKgService() {
        this(KgConfigurationLoader.loadFromSystemProperties());
    }

    /**
     * Creates an InMemoryKgService with explicit configuration.
     *
     * @param configuration the KgConfiguration to use for all operations
     */
    public InMemoryKgService(KgConfiguration configuration) {
        this.configuration = configuration;
        logger.info(
                "InMemoryKgService initialized with configuration - minSupport: {}, minConfidence: {}",
                configuration.mining().minSupport(),
                configuration.mining().minConfidence());
    }

    @Override
    public Promise<CompiledPattern> submitPattern(
            String tenantId,
            String patternName,
            String patternSpec,
            Map<String, String> metadata) {
        try {
            String patternId = "pattern-" + patternCounter.incrementAndGet();
            logger.info("Submitting pattern: {} for tenant: {}", patternId, tenantId);

            // Parse confidence from metadata, use configuration minimum as floor
            int confidence
                    = Integer.parseInt(metadata.getOrDefault("confidence", "50"));
            int minConfidenceRequired
                    = (int) (configuration.mining().minConfidence() * 100);
            if (confidence < minConfidenceRequired) {
                logger.warn(
                        "Pattern confidence {} below minimum {} - adjusting",
                        confidence,
                        minConfidenceRequired);
                confidence = minConfidenceRequired;
            }

            // Determine if pattern should auto-activate based on configuration
            KgService.PatternStatus initialStatus
                    = configuration.patterns().autoActivate()
                    ? KgService.PatternStatus.ACTIVE
                    : KgService.PatternStatus.DRAFT;

            // Create pattern
            KgPattern pattern
                    = new KgPattern(
                            patternId,
                            patternName,
                            patternSpec,
                            initialStatus,
                            confidence,
                            List.copyOf(Arrays.asList(metadata.getOrDefault("tags", "").split(","))),
                            System.currentTimeMillis(),
                            0);

            patternStore.put(patternId, pattern);
            matchCounter.put(patternId, 0);

            CompiledPattern compiled
                    = new CompiledPattern(
                            patternId,
                            patternName,
                            serializeDetectionPlan(pattern),
                            confidence,
                            System.currentTimeMillis());

            logger.info("Pattern compiled successfully: {} (status: {})", patternId, initialStatus);
            return Promise.of(compiled);

        } catch (Exception e) {
            logger.error("Failed to submit pattern for tenant: {}", tenantId, e);
            return Promise.ofException(
                    new RuntimeException(
                            ErrorCodeMappers.fromIngress("PATTERN_SUBMISSION_FAILED").name()
                            + ": "
                            + e.getMessage()));
        }
    }

    @Override
    public Promise<List<KgPattern>> queryPatterns(
            String tenantId, List<String> eventTypes, double minConfidence) {
        try {
            logger.debug("Querying patterns for tenant: {}", tenantId);

            // Use configuration minimum confidence as floor
            double effectiveMinConfidence
                    = Math.max(minConfidence, configuration.mining().minConfidence());

            List<KgPattern> results = new ArrayList<>();
            for (KgPattern pattern : patternStore.values()) {
                if (pattern.confidence() / 100.0 >= effectiveMinConfidence
                        && (eventTypes.isEmpty()
                        || pattern.spec().toLowerCase().contains(eventTypes.get(0).toLowerCase()))) {
                    results.add(pattern);
                }
            }

            logger.debug("Found {} patterns matching criteria", results.size());
            return Promise.of(results);

        } catch (Exception e) {
            logger.error("Failed to query patterns", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Optional<KgPattern>> getPattern(String patternId, String tenantId) {
        try {
            KgPattern pattern = patternStore.get(patternId);
            logger.debug("Retrieved pattern: {} for tenant: {}", patternId, tenantId);
            return Promise.of(Optional.ofNullable(pattern));

        } catch (Exception e) {
            logger.error("Failed to get pattern: {}", patternId, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Void> activatePattern(String patternId, String tenantId) {
        try {
            KgPattern pattern = patternStore.get(patternId);
            if (pattern == null) {
                return Promise.ofException(
                        new IllegalArgumentException("Pattern not found: " + patternId));
            }

            KgPattern activated
                    = new KgPattern(
                            pattern.id(),
                            pattern.name(),
                            pattern.spec(),
                            KgService.PatternStatus.ACTIVE,
                            pattern.confidence(),
                            pattern.tags(),
                            pattern.createdAt(),
                            pattern.matchCount());

            patternStore.put(patternId, activated);
            logger.info("Pattern activated: {} for tenant: {}", patternId, tenantId);
            return Promise.of(null);

        } catch (Exception e) {
            logger.error("Failed to activate pattern: {}", patternId, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Void> deactivatePattern(String patternId, String tenantId) {
        try {
            KgPattern pattern = patternStore.get(patternId);
            if (pattern == null) {
                return Promise.ofException(
                        new IllegalArgumentException("Pattern not found: " + patternId));
            }

            KgPattern deactivated
                    = new KgPattern(
                            pattern.id(),
                            pattern.name(),
                            pattern.spec(),
                            KgService.PatternStatus.INACTIVE,
                            pattern.confidence(),
                            pattern.tags(),
                            pattern.createdAt(),
                            pattern.matchCount());

            patternStore.put(patternId, deactivated);
            logger.info("Pattern deactivated: {} for tenant: {}", patternId, tenantId);
            return Promise.of(null);

        } catch (Exception e) {
            logger.error("Failed to deactivate pattern: {}", patternId, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<DiscoveredPattern>> analyzePatterns(
            String tenantId,
            List<String> eventTypes,
            int timeWindowHours,
            double minSupport) {
        try {
            // Use configuration minimum support as floor
            double effectiveMinSupport
                    = Math.max(minSupport, configuration.mining().minSupport());
            double effectiveMinConfidence = configuration.mining().minConfidence();

            logger.info(
                    "Analyzing patterns for tenant: {} over {} hours with minSupport: {}, minConfidence: {}",
                    tenantId,
                    timeWindowHours,
                    effectiveMinSupport,
                    effectiveMinConfidence);

            // Phase 3.3: Learning operators integration
            // Use FrequentSequenceMiner with Apriori algorithm to discover patterns
            FrequentSequenceMiner miner = new FrequentSequenceMiner(effectiveMinSupport);

            // Generate sample event sequences for demonstration
            // In production, this would fetch from EventCloud event store
            List<List<String>> sampleSequences = generateSampleSequences(eventTypes);
            for (List<String> sequence : sampleSequences) {
                miner.addSequence(sequence);
            }

            // Mine frequent patterns (Apriori algorithm) using configuration confidence
            List<FrequentSequenceMiner.Pattern> minedPatterns
                    = miner.mineWithConfidence(effectiveMinConfidence);

            // Convert mined patterns to DiscoveredPattern objects
            List<DiscoveredPattern> discovered = new ArrayList<>();
            int maxPatterns = configuration.mining().maxPatternsToReturn();
            int count = 0;
            for (FrequentSequenceMiner.Pattern pattern : minedPatterns) {
                if (count >= maxPatterns) {
                    logger.info(
                            "Reached maximum patterns limit ({}), stopping discovery", maxPatterns);
                    break;
                }
                int confidence = (int) (pattern.confidence() * 100);
                int support = (int) (pattern.support() * 100);
                DiscoveredPattern dp
                        = new DiscoveredPattern(
                                "discovered-" + pattern.sequence().replaceAll(" -> ", "-"),
                                pattern.sequence(),
                                support,
                                confidence,
                                pattern.events());
                discovered.add(dp);
                count++;
            }

            logger.info(
                    "Pattern analysis complete: {} patterns discovered with Apriori algorithm",
                    discovered.size());
            return Promise.of(discovered);

        } catch (Exception e) {
            logger.error("Failed to analyze patterns", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Generates sample event sequences for pattern mining.
     *
     * <p>
     * In production, this would fetch real event sequences from EventCloud or a
     * time-windowed event query. For now, generates deterministic patterns that
     * demonstrate the Apriori algorithm.
     */
    private List<List<String>> generateSampleSequences(List<String> eventTypes) {
        // If specific event types requested, use them; otherwise use defaults
        List<String> types
                = (eventTypes != null && !eventTypes.isEmpty())
                ? eventTypes
                : List.of("login", "access", "logout", "error", "data-read", "data-write");

        List<List<String>> sequences = new ArrayList<>();

        // Generate 100 sample sequences with common patterns
        for (int i = 0; i < 100; i++) {
            List<String> sequence = new ArrayList<>();

            // Common pattern 1: login -> access -> logout (60% of sequences)
            if (i < 60) {
                sequence.addAll(List.of("login", "access", "logout"));
                if (i % 3 == 0) {
                    sequence.add("data-read");
                }
            } // Common pattern 2: login -> error -> logout (30% of sequences)
            else if (i < 90) {
                sequence.addAll(List.of("login", "error", "logout"));
            } // Uncommon pattern 3: login -> data-write (10% of sequences)
            else {
                sequence.addAll(List.of("login", "data-write", "logout"));
            }

            sequences.add(sequence);
        }

        return sequences;
    }

    @Override
    public Promise<KgStatistics> getStatistics(String tenantId) {
        try {
            long totalPatterns = patternStore.size();
            long activePatterns
                    = patternStore.values().stream()
                            .filter(p -> p.status() == KgService.PatternStatus.ACTIVE)
                            .count();
            long totalMatches
                    = matchCounter.values().stream().mapToLong(Integer::longValue).sum();
            double avgConfidence
                    = patternStore.values().stream()
                            .mapToInt(KgPattern::confidence)
                            .average()
                            .orElse(0);

            KgStatistics stats
                    = new KgStatistics(totalPatterns, activePatterns, totalMatches, 0, avgConfidence);

            logger.debug(
                    "KG Statistics - Total: {}, Active: {}, Matches: {}",
                    totalPatterns,
                    activePatterns,
                    totalMatches);

            return Promise.of(stats);

        } catch (Exception e) {
            logger.error("Failed to get KG statistics", e);
            return Promise.ofException(e);
        }
    }

    private String serializeDetectionPlan(KgPattern pattern) {
        // Simple serialization for now - in production would use protobuf
        return String.format(
                "DetectionPlan(id=%s, name=%s, spec=%s, confidence=%d%%)",
                pattern.id(), pattern.name(), pattern.spec(), pattern.confidence());
    }
}
