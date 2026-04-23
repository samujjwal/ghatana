package com.ghatana.stt.vocabulary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link CustomVocabularyManager} — AV-007.3.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the custom vocabulary manager
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CustomVocabularyManager")
class CustomVocabularyManagerTest {

    // ─── empty / of ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("empty(): no terms, size 0")
    void empty_noTerms() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
        assertThat(mgr.isEmpty()).isTrue(); // GH-90000
        assertThat(mgr.size()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("of(List): pre-populates terms")
    void of_list_populatesTerms() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("Ghatana", "ActiveJ")); // GH-90000
        assertThat(mgr.size()).isEqualTo(2); // GH-90000
        assertThat(mgr.contains("ghatana")).isTrue();
        assertThat(mgr.contains("activej")).isTrue();
    }

    @Test
    @DisplayName("of(null) throws NullPointerException")
    void of_null_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> CustomVocabularyManager.of(null)); // GH-90000
    }

    // ─── addTerm ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addTerm()")
    class AddTerm {

        @Test
        @DisplayName("adds a single term and makes it findable")
        void addTerm_singleTerm() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            mgr.addTerm("hyperparameter");
            assertThat(mgr.contains("Hyperparameter")).isTrue(); // case-insensitive
        }

        @Test
        @DisplayName("duplicate terms are deduplicated")
        void addTerm_deduplication() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            mgr.addTerm("Ghatana");
            mgr.addTerm("ghatana"); // same after normalisation
            assertThat(mgr.size()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("adding same term with new weight updates the weight")
        void addTerm_updateWeight() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            mgr.addTerm("eventloop", 1.0); // GH-90000
            mgr.addTerm("eventloop", 5.0); // GH-90000
            assertThat(mgr.size()).isEqualTo(1); // GH-90000
            assertThat(mgr.terms().get(0).weight()).isEqualTo(5.0); // GH-90000
        }

        @Test
        @DisplayName("blank term throws IllegalArgumentException")
        void addTerm_blank_throwsIAE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm("   "));
        }

        @Test
        @DisplayName("null term throws NullPointerException")
        void addTerm_null_throwsNPE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm(null)); // GH-90000
        }

        @Test
        @DisplayName("weight < 0.1 throws IllegalArgumentException")
        void addTerm_invalidWeightLow_throwsIAE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm("term", 0.0)); // GH-90000
        }

        @Test
        @DisplayName("weight > 10.0 throws IllegalArgumentException")
        void addTerm_invalidWeightHigh_throwsIAE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm("term", 10.1)); // GH-90000
        }
    }

    // ─── removeTerm ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeTerm()")
    class RemoveTerm {

        @Test
        @DisplayName("removes existing term and returns true")
        void removeTerm_existing_returnsTrue() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("eventloop"));
            assertThat(mgr.removeTerm("EventLoop")).isTrue();
            assertThat(mgr.contains("eventloop")).isFalse();
        }

        @Test
        @DisplayName("removing non-existing term returns false")
        void removeTerm_nonExisting_returnsFalse() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThat(mgr.removeTerm("nonexistent")).isFalse();
        }

        @Test
        @DisplayName("null term throws NullPointerException")
        void removeTerm_null_throwsNPE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> mgr.removeTerm(null)); // GH-90000
        }
    }

    // ─── apply ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("apply: returns transcript unchanged (model applies hints internally)")
    void apply_returnsTranscriptUnchanged() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("Ghatana"));
        String transcript = "Welcome to Ghatana platform";
        assertThat(mgr.apply(transcript)).isEqualTo(transcript); // GH-90000
    }

    @Test
    @DisplayName("apply: null transcript throws NullPointerException")
    void apply_null_throwsNPE() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> mgr.apply(null)); // GH-90000
    }

    // ─── exportTermStrings ────────────────────────────────────────────────────

    @Test
    @DisplayName("exportTermStrings: returns normalised lowercase terms")
    void exportTermStrings_normalised() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("Ghatana", "ActiveJ")); // GH-90000
        List<String> exported = mgr.exportTermStrings(); // GH-90000
        assertThat(exported).containsExactlyInAnyOrder("ghatana", "activej"); // GH-90000
    }

    // ─── terms() snapshot ──────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("terms(): returns unmodifiable snapshot")
    void terms_unmodifiableSnapshot() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("a"));
        var snap = mgr.terms(); // GH-90000
        assertThat(snap).hasSize(1); // GH-90000
        mgr.addTerm("b");
        assertThat(snap).hasSize(1); // snapshot unchanged // GH-90000
    }
}
