/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.sanctions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JaroWinklerMatchingService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Jaro-Winkler name similarity scoring (D14-005)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JaroWinklerMatchingService — Unit Tests")
class JaroWinklerMatchingServiceTest {

    private final JaroWinklerMatchingService jw = new JaroWinklerMatchingService();
    private final LevenshteinMatchingService lev = new LevenshteinMatchingService();

    // ─── score() tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("identical strings — score is 1.0")
    void identicalStrings_scoreIsOne() {
        assertThat(jw.score("John Smith", "John Smith")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("case-insensitive — identical except case returns 1.0")
    void caseInsensitive_identicalScore() {
        assertThat(jw.score("JOHN SMITH", "john smith")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("completely different strings — score is low (< 0.5)")
    void differentStrings_lowScore() {
        assertThat(jw.score("ABCDEF", "ZYXWVU")).isLessThan(0.5);
    }

    @Test
    @DisplayName("null inputs — score is 0.0 without exception")
    void nullInputs_returnZero() {
        assertThat(jw.score(null, "John")).isEqualTo(0.0);
        assertThat(jw.score("John", null)).isEqualTo(0.0);
        assertThat(jw.score(null, null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("one-character transposition — score is high (> 0.8)")
    void oneCharacterTransposition_highScore() {
        // "John" vs "Jahn" — differ by 1 character in middle
        assertThat(jw.score("John", "Jahn")).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("common prefix boost — prefix match boosts score via Winkler scaling")
    void commonPrefix_boostedScore() {
        // "Muhammad Ali" vs "Mohammed Ali" — shared common prefix "M"
        double scoreWithPrefix = jw.score("Muhammad Ali", "Mohammad Ali");
        double scoreWithoutPrefix = jw.score("Abdul Karim", "Ahmad Rahman");
        assertThat(scoreWithPrefix).isGreaterThan(scoreWithoutPrefix);
    }

    // ─── soundexMatch() tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("phonetically identical names — soundexMatch is true")
    void soundexMatch_phoneticallySimilar_returnsTrue() {
        // "Smith" and "Smyth" have the same Soundex code (S530)
        assertThat(jw.soundexMatch("Smith", "Smyth")).isTrue();
    }

    @Test
    @DisplayName("phonetically distinct names — soundexMatch is false")
    void soundexMatch_phoneticallydistinct_returnsFalse() {
        assertThat(jw.soundexMatch("Smith", "Jones")).isFalse();
    }

    @Test
    @DisplayName("identical strings — soundexMatch is true")
    void soundexMatch_identicalStrings_returnsTrue() {
        assertThat(jw.soundexMatch("Ahmad", "Ahmad")).isTrue();
    }

    // ─── combinedScore() tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("exact match — combinedScore is 1.0")
    void combinedScore_exactMatch_isOne() {
        assertThat(jw.combinedScore("Ali Hassan", "Ali Hassan", lev)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("phonetic match — combinedScore ≥ 0.80 via soundex bonus")
    void combinedScore_phoneticMatch_atLeastEightyPercent() {
        // "Smith" and "Smyth" — soundex match contributes 0.80
        assertThat(jw.combinedScore("Smith", "Smyth", lev)).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    @DisplayName("combinedScore — is max of JW, Levenshtein, and phonetic")
    void combinedScore_isMaxOfComponents() {
        // For "Ali" vs "Aly" — all three metrics should be high; combined should be ≥ max(JW,lev)
        double combined = jw.combinedScore("Ali", "Aly", lev);
        double jwoScore = jw.score("Ali", "Aly");
        assertThat(combined).isGreaterThanOrEqualTo(jwoScore);
    }

    @Test
    @DisplayName("completely different strings — combinedScore is below threshold (< 0.70)")
    void combinedScore_differentNames_belowThreshold() {
        assertThat(jw.combinedScore("Xyz Completely", "Different Name", lev)).isLessThan(0.70);
    }
}
