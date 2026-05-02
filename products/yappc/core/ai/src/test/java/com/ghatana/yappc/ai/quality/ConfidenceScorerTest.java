package com.ghatana.yappc.ai.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConfidenceScorer}.
 */
@DisplayName("ConfidenceScorer Tests")
class ConfidenceScorerTest {

    // ── parse() happy path ──────────────────────────────────────────────────── 

    @Nested
    @DisplayName("parse – valid confidence JSON")
    class ParseValid {

        @Test
        @DisplayName("extracts confidence 0.85 and labels it HIGH")
        void parsesHighConfidence() { 
            String content = "{\"confidence\": 0.85, \"content\": \"Generated code here\"}";
            ConfidenceScore score = ConfidenceScorer.parse(content); 

            assertThat(score.value()).isEqualTo(0.85); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); 
            assertThat(score.raw()).isEqualTo("0.85");
        }

        @Test
        @DisplayName("extracts confidence 0.65 and labels it MEDIUM")
        void parsesMediumConfidence() { 
            String content = "{\"confidence\": 0.65, \"content\": \"Some analysis\"}";
            ConfidenceScore score = ConfidenceScorer.parse(content); 

            assertThat(score.value()).isEqualTo(0.65); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.MEDIUM); 
        }

        @Test
        @DisplayName("extracts confidence 0.3 and labels it LOW")
        void parsesLowConfidence() { 
            String content = "{\"confidence\": 0.3}";
            ConfidenceScore score = ConfidenceScorer.parse(content); 

            assertThat(score.value()).isEqualTo(0.3); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.LOW); 
        }

        @Test
        @DisplayName("parses JSON embedded within prose")
        void parsesEmbeddedJson() { 
            String content = "Here is my analysis:\n{\"confidence\": 0.9, \"content\": \"The code is secure\"}\nEnd.";
            ConfidenceScore score = ConfidenceScorer.parse(content); 

            assertThat(score.value()).isEqualTo(0.9); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); 
        }

        @Test
        @DisplayName("boundary 0.8 exactly is labeled HIGH")
        void boundaryHighIsHigh() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.8}"); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); 
        }

        @Test
        @DisplayName("boundary 0.5 exactly is labeled MEDIUM")
        void boundaryMediumIsMedium() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.5}"); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.MEDIUM); 
        }
    }

    // ── parse() – absent / malformed ───────────────────────────────────────── 

    @Nested
    @DisplayName("parse – absent or malformed content")
    class ParseAbsent {

        @Test
        @DisplayName("returns absent score for null content")
        void returnsAbsentForNull() { 
            ConfidenceScore score = ConfidenceScorer.parse(null); 
            assertThat(score).isEqualTo(ConfidenceScore.absent()); 
        }

        @Test
        @DisplayName("returns absent score for blank content")
        void returnsAbsentForBlank() { 
            ConfidenceScore score = ConfidenceScorer.parse("   ");
            assertThat(score).isEqualTo(ConfidenceScore.absent()); 
        }

        @Test
        @DisplayName("returns absent score when no JSON block present")
        void returnsAbsentForProseOnly() { 
            ConfidenceScore score = ConfidenceScorer.parse("This is plain text with no JSON.");
            assertThat(score).isEqualTo(ConfidenceScore.absent()); 
        }

        @Test
        @DisplayName("returns absent when JSON has no confidence key")
        void returnsAbsentForMissingKey() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"content\": \"some text\"}"); 
            assertThat(score).isEqualTo(ConfidenceScore.absent()); 
        }

        @Test
        @DisplayName("returns absent when confidence value is a string")
        void returnsAbsentForStringValue() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": \"high\"}"); 
            assertThat(score).isEqualTo(ConfidenceScore.absent()); 
        }

        @Test
        @DisplayName("returns absent when JSON is malformed")
        void returnsAbsentForMalformedJson() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.8, broken}"); 
            assertThat(score).isEqualTo(ConfidenceScore.absent()); 
        }
    }

    // ── out-of-range clamping ─────────────────────────────────────────────────

    @Nested
    @DisplayName("parse – out-of-range clamping")
    class Clamping {

        @Test
        @DisplayName("clamps value > 1.0 to 1.0")
        void clampsAboveRange() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 1.5}"); 
            assertThat(score.value()).isEqualTo(1.0); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.HIGH); 
        }

        @Test
        @DisplayName("clamps value < 0.0 to 0.0")
        void clampsBelowRange() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": -0.2}"); 
            assertThat(score.value()).isEqualTo(0.0); 
            assertThat(score.label()).isEqualTo(ConfidenceScore.Label.LOW); 
        }
    }

    // ── ConfidenceScore helpers ───────────────────────────────────────────────

    @Nested
    @DisplayName("ConfidenceScore.meetsThreshold")
    class MeetsThreshold {

        @Test
        @DisplayName("returns true when value equals threshold")
        void trueAtThreshold() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.7}"); 
            assertThat(score.meetsThreshold(0.7)).isTrue(); 
        }

        @Test
        @DisplayName("returns false when value is below threshold")
        void falseBelow() { 
            ConfidenceScore score = ConfidenceScorer.parse("{\"confidence\": 0.4}"); 
            assertThat(score.meetsThreshold(0.5)).isFalse(); 
        }

        @Test
        @DisplayName("absent score does not meet 0.5 threshold")
        void absentDoesNotMeetDefaultThreshold() { 
            assertThat(ConfidenceScore.absent().meetsThreshold(0.5)).isFalse(); 
        }
    }
}
