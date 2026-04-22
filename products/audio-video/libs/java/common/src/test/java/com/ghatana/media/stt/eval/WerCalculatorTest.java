package com.ghatana.media.stt.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link WerCalculator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the WER evaluation metric
 * @doc.layer platform
 * @doc.pattern TestCase
 */
@DisplayName("WerCalculator [GH-90000]")
class WerCalculatorTest {

    private final WerCalculator calc = new WerCalculator(); // GH-90000

    @Test
    @DisplayName("perfect match → WER = 0 [GH-90000]")
    void perfectMatch_werIsZero() { // GH-90000
        var r = calc.compute("the cat sat on the mat", "the cat sat on the mat"); // GH-90000
        assertThat(r.wer()).isCloseTo(0.0, within(1e-6)); // GH-90000
        assertThat(r.substitutions()).isZero(); // GH-90000
        assertThat(r.deletions()).isZero(); // GH-90000
        assertThat(r.insertions()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("one deletion → WER = 1/N [GH-90000]")
    void oneDeletion_werIsOneDivN() { // GH-90000
        // ref = 6 words, hypothesis drops "the" before "mat"
        var r = calc.compute("the cat sat on the mat", "the cat sat on mat"); // GH-90000
        assertThat(r.deletions()).isEqualTo(1); // GH-90000
        assertThat(r.wer()).isCloseTo(1.0 / 6, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("one insertion → WER = 1/N [GH-90000]")
    void oneInsertion_werIsOneDivN() { // GH-90000
        var r = calc.compute("hello world", "hello beautiful world"); // GH-90000
        assertThat(r.insertions()).isEqualTo(1); // GH-90000
        assertThat(r.wer()).isCloseTo(1.0 / 2, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("one substitution → WER = 1/N [GH-90000]")
    void oneSubstitution_werIsOneDivN() { // GH-90000
        var r = calc.compute("the quick brown fox", "the slow brown fox"); // GH-90000
        assertThat(r.substitutions()).isEqualTo(1); // GH-90000
        assertThat(r.wer()).isCloseTo(1.0 / 4, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("completely wrong → WER ≥ 1.0 [GH-90000]")
    void completelyWrong_werGtOne() { // GH-90000
        var r = calc.compute("hello world", "goodbye universe today"); // GH-90000
        assertThat(r.wer()).isGreaterThanOrEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("empty hypothesis → WER = 1.0 (all deletions) [GH-90000]")
    void emptyHypothesis_allDeletions() { // GH-90000
        var r = calc.compute("four words in here", ""); // GH-90000
        assertThat(r.deletions()).isEqualTo(4); // GH-90000
        assertThat(r.wer()).isCloseTo(1.0, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("empty reference and hypothesis → WER = 0 [GH-90000]")
    void bothEmpty_werIsZero() { // GH-90000
        var r = calc.compute("", ""); // GH-90000
        assertThat(r.wer()).isCloseTo(0.0, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("punctuation stripped before comparison [GH-90000]")
    void punctuationIgnored() { // GH-90000
        var r = calc.compute("Hello, world!", "hello world"); // GH-90000
        assertThat(r.wer()).isCloseTo(0.0, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("corpus WER aggregates over multiple pairs [GH-90000]")
    void corpusWer_aggregatesCorrectly() { // GH-90000
        // Pair 1: perfect  (N=2, E=0) // GH-90000
        // Pair 2: 1 del    (N=3, E=1) // GH-90000
        // Corpus WER = 1/5
        var result = calc.computeCorpus(List.of( // GH-90000
            new WerCalculator.Pair("hello world",        "hello world"), // GH-90000
            new WerCalculator.Pair("one two three",      "one three") // GH-90000
        ));
        assertThat(result.wer()).isCloseTo(1.0 / 5, within(1e-6)); // GH-90000
        assertThat(result.referenceLength()).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("accuracy() = 1 − WER [GH-90000]")
    void accuracy_isComplementOfWer() { // GH-90000
        var r = calc.compute("one two three four", "one two three five"); // GH-90000
        assertThat(r.accuracy()).isCloseTo(1.0 - r.wer(), within(1e-6)); // GH-90000
    }
}
