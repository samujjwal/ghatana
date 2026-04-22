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
@DisplayName("CustomVocabularyManager [GH-90000]")
class CustomVocabularyManagerTest {

    // ─── empty / of ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("empty(): no terms, size 0 [GH-90000]")
    void empty_noTerms() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
        assertThat(mgr.isEmpty()).isTrue(); // GH-90000
        assertThat(mgr.size()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("of(List): pre-populates terms [GH-90000]")
    void of_list_populatesTerms() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("Ghatana", "ActiveJ")); // GH-90000
        assertThat(mgr.size()).isEqualTo(2); // GH-90000
        assertThat(mgr.contains("ghatana [GH-90000]")).isTrue();
        assertThat(mgr.contains("activej [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("of(null) throws NullPointerException [GH-90000]")
    void of_null_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> CustomVocabularyManager.of(null)); // GH-90000
    }

    // ─── addTerm ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addTerm() [GH-90000]")
    class AddTerm {

        @Test
        @DisplayName("adds a single term and makes it findable [GH-90000]")
        void addTerm_singleTerm() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            mgr.addTerm("hyperparameter [GH-90000]");
            assertThat(mgr.contains("Hyperparameter [GH-90000]")).isTrue(); // case-insensitive
        }

        @Test
        @DisplayName("duplicate terms are deduplicated [GH-90000]")
        void addTerm_deduplication() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            mgr.addTerm("Ghatana [GH-90000]");
            mgr.addTerm("ghatana [GH-90000]"); // same after normalisation
            assertThat(mgr.size()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("adding same term with new weight updates the weight [GH-90000]")
        void addTerm_updateWeight() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            mgr.addTerm("eventloop", 1.0); // GH-90000
            mgr.addTerm("eventloop", 5.0); // GH-90000
            assertThat(mgr.size()).isEqualTo(1); // GH-90000
            assertThat(mgr.terms().get(0).weight()).isEqualTo(5.0); // GH-90000
        }

        @Test
        @DisplayName("blank term throws IllegalArgumentException [GH-90000]")
        void addTerm_blank_throwsIAE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm("    [GH-90000]"));
        }

        @Test
        @DisplayName("null term throws NullPointerException [GH-90000]")
        void addTerm_null_throwsNPE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm(null)); // GH-90000
        }

        @Test
        @DisplayName("weight < 0.1 throws IllegalArgumentException [GH-90000]")
        void addTerm_invalidWeightLow_throwsIAE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm("term", 0.0)); // GH-90000
        }

        @Test
        @DisplayName("weight > 10.0 throws IllegalArgumentException [GH-90000]")
        void addTerm_invalidWeightHigh_throwsIAE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> mgr.addTerm("term", 10.1)); // GH-90000
        }
    }

    // ─── removeTerm ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeTerm() [GH-90000]")
    class RemoveTerm {

        @Test
        @DisplayName("removes existing term and returns true [GH-90000]")
        void removeTerm_existing_returnsTrue() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("eventloop [GH-90000]"));
            assertThat(mgr.removeTerm("EventLoop [GH-90000]")).isTrue();
            assertThat(mgr.contains("eventloop [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("removing non-existing term returns false [GH-90000]")
        void removeTerm_nonExisting_returnsFalse() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThat(mgr.removeTerm("nonexistent [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("null term throws NullPointerException [GH-90000]")
        void removeTerm_null_throwsNPE() { // GH-90000
            CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> mgr.removeTerm(null)); // GH-90000
        }
    }

    // ─── apply ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("apply: returns transcript unchanged (model applies hints internally) [GH-90000]")
    void apply_returnsTranscriptUnchanged() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("Ghatana [GH-90000]"));
        String transcript = "Welcome to Ghatana platform";
        assertThat(mgr.apply(transcript)).isEqualTo(transcript); // GH-90000
    }

    @Test
    @DisplayName("apply: null transcript throws NullPointerException [GH-90000]")
    void apply_null_throwsNPE() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.empty(); // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> mgr.apply(null)); // GH-90000
    }

    // ─── exportTermStrings ────────────────────────────────────────────────────

    @Test
    @DisplayName("exportTermStrings: returns normalised lowercase terms [GH-90000]")
    void exportTermStrings_normalised() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("Ghatana", "ActiveJ")); // GH-90000
        List<String> exported = mgr.exportTermStrings(); // GH-90000
        assertThat(exported).containsExactlyInAnyOrder("ghatana", "activej"); // GH-90000
    }

    // ─── terms() snapshot ──────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("terms(): returns unmodifiable snapshot [GH-90000]")
    void terms_unmodifiableSnapshot() { // GH-90000
        CustomVocabularyManager mgr = CustomVocabularyManager.of(List.of("a [GH-90000]"));
        var snap = mgr.terms(); // GH-90000
        assertThat(snap).hasSize(1); // GH-90000
        mgr.addTerm("b [GH-90000]");
        assertThat(snap).hasSize(1); // snapshot unchanged // GH-90000
    }
}
