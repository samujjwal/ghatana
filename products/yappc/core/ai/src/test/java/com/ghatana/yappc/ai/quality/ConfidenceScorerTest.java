package com.ghatana.yappc.ai.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConfidenceScorer}.
 */
@DisplayName("ConfidenceScorer Tests [GH-90000]")
class ConfidenceScorerTest {

    // ── parse() happy path ──────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("parse – valid confidence JSON [GH-90000]")
    class ParseValid {

        @Test
        @DisplayName("extracts confidence 0.85 and labels it HIGH [GH-90000]")
        void parsesHighConfidence() { // GH-90000
            String content = "{\"confidence\": 0.85, \"content\": \"Generated code here\"}";
            ConfidenceScore score = ConfidenceScorer.parse(content); // GH-90000

            assertThat(score.value()).isEqualTo(0.85); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); // GH-90000
            assertThat(score.raw()).isEqualTo("0.85 [GH-90000]");
        }

        @Test
        @DisplayName("extracts confidence 0.65 and labels it MEDIUM [GH-90000]")
        void parsesMediumConfidence() { // GH-90000
            String content = "{\"confidence\": 0.65, \"content\": \"Some analysis\"}";
            ConfidenceScore score = ConfidenceScorer.parse(content); // GH-90000

            assertThat(score.value()).isEqualTo(0.65); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.MEDIUM); // GH-90000
        }

        @Test
        @DisplayName("extracts confidence 0.3 and labels it LOW [GH-90000]")
        void parsesLowConfidence() { // GH-90000
            String content = "{\"confidence\": 0.3}";
            ConfidenceScore score = ConfidenceScorer.parse(content); // GH-90000

            assertThat(score.value()).isEqualTo(0.3); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.LOW); // GH-90000
        }

        @Test
        @DisplayName("parses JSON embedded within prose [GH-90000]")
        void parsesEmbeddedJson() { // GH-90000
            String content = "Here is my analysis:\n{\"confidence\": 0.9, \"content\": \"The code is secure\"}\nEnd.";
            ConfidenceScore score = ConfidenceScorer.parse(content); // GH-90000

            assertThat(score.value()).isEqualTo(0.9); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); // GH-90000
        }

        @Test
        @DisplayName("boundary 0.8 exactly is labeled HIGH [GH-90000]")
        void boundaryHighIsHigh() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.8}"); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); // GH-90000
        }

        @Test
        @DisplayName("boundary 0.5 exactly is labeled MEDIUM [GH-90000]")
        void boundaryMediumIsMedium() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.5}"); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.MEDIUM); // GH-90000
        }
    }

    // ── parse() – absent / malformed ───────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("parse – absent or malformed content [GH-90000]")
    class ParseAbsent {

        @Test
        @DisplayName("returns absent score for null content [GH-90000]")
        void returnsAbsentForNull() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse(null); // GH-90000
            assertThat(score).isEqualTo(ConfidenceScore.absent()); // GH-90000
        }

        @Test
        @DisplayName("returns absent score for blank content [GH-90000]")
        void returnsAbsentForBlank() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("    [GH-90000]");
            assertThat(score).isEqualTo(ConfidenceScore.absent()); // GH-90000
        }

        @Test
        @DisplayName("returns absent score when no JSON block present [GH-90000]")
        void returnsAbsentForProseOnly() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("This is plain text with no JSON. [GH-90000]");
            assertThat(score).isEqualTo(ConfidenceScore.absent()); // GH-90000
        }

        @Test
        @DisplayName("returns absent when JSON has no confidence key [GH-90000]")
        void returnsAbsentForMissingKey() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"content\": \"some text\"}"); // GH-90000
            assertThat(score).isEqualTo(ConfidenceScore.absent()); // GH-90000
        }

        @Test
        @DisplayName("returns absent when confidence value is a string [GH-90000]")
        void returnsAbsentForStringValue() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": \"high\"}"); // GH-90000
            assertThat(score).isEqualTo(ConfidenceScore.absent()); // GH-90000
        }

        @Test
        @DisplayName("returns absent when JSON is malformed [GH-90000]")
        void returnsAbsentForMalformedJson() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.8, broken}"); // GH-90000
            assertThat(score).isEqualTo(ConfidenceScore.absent()); // GH-90000
        }
    }

    // ── out-of-range clamping ─────────────────────────────────────────────────

    @Nested
    @DisplayName("parse – out-of-range clamping [GH-90000]")
    class Clamping {

        @Test
        @DisplayName("clamps value > 1.0 to 1.0 [GH-90000]")
        void clampsAboveRange() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 1.5}"); // GH-90000
            assertThat(score.value()).isEqualTo(1.0); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); // GH-90000
        }

        @Test
        @DisplayName("clamps value < 0.0 to 0.0 [GH-90000]")
        void clampsBelowRange() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": -0.2}"); // GH-90000
            assertThat(score.value()).isEqualTo(0.0); // GH-90000
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.LOW); // GH-90000
        }
    }

    // ── ConfidenceScore helpers ───────────────────────────────────────────────

    @Nested
    @DisplayName("ConfidenceScore.meetsThreshold [GH-90000]")
    class MeetsThreshold {

        @Test
        @DisplayName("returns true when value equals threshold [GH-90000]")
        void trueAtThreshold() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.7}"); // GH-90000
            assertThat(score.meetsThreshold(0.7)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns false when value is below threshold [GH-90000]")
        void falseBelow() { // GH-90000
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.4}"); // GH-90000
            assertThat(score.meetsThreshold(0.5)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("absent score does not meet 0.5 threshold [GH-90000]")
        void absentDoesNotMeetDefaultThreshold() { // GH-90000
            assertThat(ConfidenceScore.absent().meetsThreshold(0.5)).isFalse(); // GH-90000
        }
    }
}
