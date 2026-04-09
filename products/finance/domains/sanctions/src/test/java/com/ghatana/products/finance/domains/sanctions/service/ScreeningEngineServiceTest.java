/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.sanctions.service;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.finance.domains.sanctions.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ScreeningEngineService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for in-memory sanctions screening with fuzzy name matching (D14-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScreeningEngineService — Unit Tests")
class ScreeningEngineServiceTest extends EventloopTestBase {

    @Mock private EventBusPort eventBusPort;

    private final LevenshteinMatchingService levenshtein = new LevenshteinMatchingService();
    private final JaroWinklerMatchingService jaroWinkler = new JaroWinklerMatchingService();
    private final NameTransliterationService transliteration = new NameTransliterationService();

    private ScreeningEngineService service;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(eventBusPort).publish(any());
        service = new ScreeningEngineService(
                levenshtein, jaroWinkler, transliteration,
                Executors.newSingleThreadExecutor(),
                eventBusPort,
                new SimpleMeterRegistry()
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private SanctionsEntry ofacEntry(String name) {
        return new SanctionsEntry(UUID.randomUUID().toString(), SanctionsListType.OFAC_SDN,
                name, List.of(), "INDIVIDUAL", null, "US", "2025-01");
    }

    private SanctionsEntry ofacEntryWithAlias(String name, String alias) {
        return new SanctionsEntry(UUID.randomUUID().toString(), SanctionsListType.OFAC_SDN,
                name, List.of(alias), "INDIVIDUAL", null, "US", "2025-01");
    }

    private ScreeningRequest request(String name) {
        return new ScreeningRequest(UUID.randomUUID().toString(), name,
                ScreeningEntityType.INDIVIDUAL, null, null, List.of());
    }

    // ─── Empty list tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("empty sanctions list — no matches, decision is LOW")
    void emptyList_noMatches() {
        // list is empty by default
        ScreeningResult result = runPromise(() ->
                service.screen(request("John Doe"), "ref-1"));

        assertThat(result.matchFound()).isFalse();
        assertThat(result.matches()).isEmpty();
        assertThat(result.decision()).isEqualTo(ScreeningDecision.LOW);
    }

    // ─── Exact match tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("exact name match — matchFound is true and decision is AUTO_BLOCK")
    void exactNameMatch_autoBlock() {
        service.loadList(List.of(ofacEntry("Ali Hassan Al-Rashid")));

        ScreeningResult result = runPromise(() ->
                service.screen(request("Ali Hassan Al-Rashid"), "ref-1"));

        assertThat(result.matchFound()).isTrue();
        assertThat(result.decision()).isEqualTo(ScreeningDecision.AUTO_BLOCK);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).matchType()).isEqualTo(MatchType.EXACT);
        assertThat(result.matches().get(0).score()).isGreaterThanOrEqualTo(0.99);
    }

    @Test
    @DisplayName("exact match — emits ScreeningMatchFoundEvent")
    void exactMatch_emitsEvent() {
        service.loadList(List.of(ofacEntry("Ivan Petrov")));

        runPromise(() -> service.screen(request("Ivan Petrov"), "ref-event"));

        verify(eventBusPort).publish(any());
    }

    // ─── Fuzzy match tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("typo in name (one char difference) — high score match found")
    void typoName_highScoreMatch() {
        service.loadList(List.of(ofacEntry("Ali Hassan")));

        // "Ali Hasan" vs "Ali Hassan" — one missing char
        ScreeningResult result = runPromise(() ->
                service.screen(request("Ali Hasan"), "ref-2"));

        assertThat(result.matchFound()).isTrue();
        assertThat(result.highestScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("completely different name — no match (below threshold)")
    void differentName_noMatch() {
        service.loadList(List.of(ofacEntry("Ivan Petrov")));

        ScreeningResult result = runPromise(() ->
                service.screen(request("John Smith"), "ref-3"));

        assertThat(result.matchFound()).isFalse();
        assertThat(result.decision()).isEqualTo(ScreeningDecision.LOW);
    }

    // ─── List management tests ────────────────────────────────────────────────

    @Test
    @DisplayName("loadList — atomically replaces in-memory list")
    void loadList_replacesEntries() {
        service.loadList(List.of(ofacEntry("Alice Wonderland")));
        service.loadList(List.of(ofacEntry("Ivan Petrov"))); // replace

        // Alice should not match anymore
        ScreeningResult result = runPromise(() ->
                service.screen(request("Alice Wonderland"), "ref-4"));

        assertThat(result.matchFound()).isFalse();
    }

    @Test
    @DisplayName("multiple sanctions entries — matches sorted by score descending")
    void multipleEntries_sortedByScoreDescending() {
        service.loadList(List.of(
                ofacEntry("Ali Hassan"),
                ofacEntry("Ali Hassan Al-Rashid")
        ));

        // Exact match against "Ali Hassan Al-Rashid" should be top
        ScreeningResult result = runPromise(() ->
                service.screen(request("Ali Hassan Al-Rashid"), "ref-5"));

        assertThat(result.matchFound()).isTrue();
        assertThat(result.matches()).hasSizeGreaterThanOrEqualTo(1);
        // First match should have highest score
        double firstScore = result.matches().get(0).score();
        for (var match : result.matches()) {
            assertThat(match.score()).isLessThanOrEqualTo(firstScore);
        }
    }

    @Test
    @DisplayName("result — always has non-null requestId, decision, and evaluatedAt")
    void result_hasRequiredFields() {
        ScreeningResult result = runPromise(() ->
                service.screen(request("Test Name"), "ref-fields"));

        assertThat(result.resultId()).isNotBlank();
        assertThat(result.requestId()).isNotBlank();
        assertThat(result.decision()).isNotNull();
        assertThat(result.screenedAt()).isNotNull();
        assertThat(result.referenceId()).isEqualTo("ref-fields");
    }
}
