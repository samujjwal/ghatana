package com.ghatana.appplatform.sanctions.service;

import com.ghatana.appplatform.sanctions.domain.*;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Core in-memory sanctions screening engine (D14-001).
 *              Loads all sanctions entries into an AtomicReference for lock-free atomic list
 *              swap on updates. Evaluates name matching via Levenshtein + Jaro-Winkler.
 *              Must complete < 50ms P99 per spec.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class ScreeningEngineService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningEngineService.class);
    private static final double DEFAULT_THRESHOLD = 0.70;

    /** Atomic swap ensures no partial state during list updates (D14-001). */
    private final AtomicReference<List<SanctionsEntry>> sanctionsListRef =
            new AtomicReference<>(List.of());

    private final LevenshteinMatchingService levenshtein;
    private final JaroWinklerMatchingService jaroWinkler;
    private final NameTransliterationService transliteration;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Timer screeningTimer;

    public ScreeningEngineService(LevenshteinMatchingService levenshtein,
                                   JaroWinklerMatchingService jaroWinkler,
                                   NameTransliterationService transliteration,
                                   Executor executor,
                                   Consumer<Object> eventPublisher,
                                   MeterRegistry meterRegistry) {
        this.levenshtein = levenshtein;
        this.jaroWinkler = jaroWinkler;
        this.transliteration = transliteration;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.screeningTimer = meterRegistry.timer("sanctions.screening.duration");
    }

    /** Atomically replace the in-memory sanctions list (D14-001 — no partial state). */
    public void loadList(List<SanctionsEntry> entries) {
        sanctionsListRef.set(List.copyOf(entries));
        log.info("Sanctions list loaded: {} entries", entries.size());
    }

    /** Screen a single entity against all loaded sanctions lists (D14-001, D14-002). */
    public Promise<ScreeningResult> screen(ScreeningRequest request, String referenceId) {
        return Promise.ofBlocking(executor, () -> {
            var startTime = System.currentTimeMillis();
            var matches = new ArrayList<ScreeningResult.MatchResult>();

            // Normalize and transliterate query name
            String normalizedQuery = levenshtein.normalize(request.name());
            String transliterated = transliteration.transliterate(request.name());
            String normalizedTranslit = levenshtein.normalize(transliterated);

            List<SanctionsEntry> currentList = sanctionsListRef.get();
            for (var entry : currentList) {
                double bestScore = scoreAgainstEntry(normalizedQuery, normalizedTranslit, entry);
                if (bestScore >= DEFAULT_THRESHOLD) {
                    MatchType matchType = bestScore >= 0.99 ? MatchType.EXACT : MatchType.LEVENSHTEIN;
                    matches.add(new ScreeningResult.MatchResult(
                            entry.listType(), entry.entryId(), entry.primaryName(),
                            bestScore, matchType));
                }
            }

            // Sort by score descending
            matches.sort((a, b) -> Double.compare(b.score(), a.score()));

            double highestScore = matches.isEmpty() ? 0.0 : matches.get(0).score();
            boolean matchFound = !matches.isEmpty();
            ScreeningDecision decision = matchFound
                    ? ScreeningDecision.fromScore(highestScore)
                    : ScreeningDecision.LOW;

            ScreeningResult result = new ScreeningResult(
                    UUID.randomUUID().toString(),
                    request.requestId(),
                    matchFound,
                    List.copyOf(matches),
                    decision,
                    highestScore,
                    Instant.now(),
                    referenceId
            );

            long elapsed = System.currentTimeMillis() - startTime;
            screeningTimer.record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (matchFound) {
                eventPublisher.accept(new ScreeningMatchFoundEvent(result));
                log.info("Screening match: requestId={} referenceId={} decision={} score={}",
                        request.requestId(), referenceId, decision, highestScore);
            }
            return result;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double scoreAgainstEntry(String normalizedQuery, String normalizedTranslit,
                                      SanctionsEntry entry) {
        double best = 0.0;

        // Score against primary name and all aliases (D14-006 alias expansion)
        var namesToCheck = new ArrayList<String>();
        namesToCheck.add(levenshtein.normalize(entry.primaryName()));
        for (var alias : entry.aliases()) {
            namesToCheck.add(levenshtein.normalize(alias));
        }

        for (var candidateName : namesToCheck) {
            double s = jaroWinkler.combinedScore(normalizedQuery, candidateName, levenshtein);
            if (s > best) best = s;

            // Also score transliterated version
            if (!normalizedTranslit.equals(normalizedQuery)) {
                s = jaroWinkler.combinedScore(normalizedTranslit, candidateName, levenshtein);
                if (s > best) best = s;
            }
        }
        return best;
    }

    public record ScreeningMatchFoundEvent(ScreeningResult result) {}
}
