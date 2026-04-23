package com.ghatana.aep.devtools;

import com.ghatana.core.domain.pipeline.AepQuickStartTemplates;
import com.ghatana.core.domain.pipeline.PipelineSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link PipelineDebugConsole} — AEP-009.2.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the AEP pipeline debug console developer tooling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PipelineDebugConsole")
class PipelineDebugConsoleTest {

    // ─── create ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates console with matching pipeline name")
        void create_matchesPipelineName() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("my-pipe");
            assertThat(console.pipelineName()).isEqualTo("my-pipe");
        }

        @Test
        @DisplayName("starts with empty entry list")
        void create_emptyEntries() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThat(console.entries()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null name throws NullPointerException")
        void create_nullName_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> PipelineDebugConsole.create(null)); // GH-90000
        }
    }

    // ─── attachTo ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("attachTo(PipelineSpec)")
    class AttachTo {

        @Test
        @DisplayName("pre-populates METADATA entries for each stage")
        void attachTo_populatesMetadataEntries() { // GH-90000
            PipelineSpec spec = AepQuickStartTemplates
                    .fraudDetection("fraud", "t1") // GH-90000
                    .build(); // GH-90000

            PipelineDebugConsole console = PipelineDebugConsole.attachTo(spec); // GH-90000

            assertThat(console.entries()) // GH-90000
                    .hasSize(spec.stages().size()) // GH-90000
                    .allMatch(e -> e.kind() == PipelineDebugConsole.DebugEntry.Kind.METADATA); // GH-90000
        }

        @Test
        @DisplayName("null spec throws NullPointerException")
        void attachTo_nullSpec_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> PipelineDebugConsole.attachTo(null)); // GH-90000
        }
    }

    // ─── recordStageEntry ────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordStageEntry()")
    class RecordStageEntry {

        @Test
        @DisplayName("adds an ENTRY debug entry with correct stageId")
        void recordStageEntry_addsEntry() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageEntry("enrichment", "event-123"); // GH-90000

            assertThat(console.entries()).hasSize(1); // GH-90000
            PipelineDebugConsole.DebugEntry entry = console.entries().get(0); // GH-90000
            assertThat(entry.kind()).isEqualTo(PipelineDebugConsole.DebugEntry.Kind.ENTRY); // GH-90000
            assertThat(entry.stageId()).isEqualTo("enrichment");
            assertThat(entry.message()).contains("event-123");
        }

        @Test
        @DisplayName("null stageId throws NullPointerException")
        void recordStageEntry_nullStage_throwsNPE() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> console.recordStageEntry(null, "event")); // GH-90000
        }

        @Test
        @DisplayName("entryCount increments after recording")
        void recordStageEntry_incrementsEntryCount() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageEntry("stage-1", "e1"); // GH-90000
            console.recordStageEntry("stage-2", "e2"); // GH-90000
            assertThat(console.entryCount()).isEqualTo(2); // GH-90000
        }
    }

    // ─── recordStageExit ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordStageExit()")
    class RecordStageExit {

        @Test
        @DisplayName("adds an EXIT debug entry")
        void recordStageExit_addsExitEntry() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageExit("filter-stage", 3, 42L); // GH-90000

            assertThat(console.entries()).hasSize(1); // GH-90000
            PipelineDebugConsole.DebugEntry entry = console.entries().get(0); // GH-90000
            assertThat(entry.kind()).isEqualTo(PipelineDebugConsole.DebugEntry.Kind.EXIT); // GH-90000
            assertThat(entry.stageId()).isEqualTo("filter-stage");
            assertThat(entry.message()).contains("3").contains("42");
        }

        @Test
        @DisplayName("exitCount increments after recording")
        void recordStageExit_incrementsExitCount() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageExit("s1", 1, 10L); // GH-90000
            console.recordStageExit("s2", 0, 5L); // GH-90000
            assertThat(console.exitCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("null stageId throws NullPointerException")
        void recordStageExit_nullStage_throwsNPE() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> console.recordStageExit(null, 1, 10L)); // GH-90000
        }
    }

    // ─── recordError ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordError()")
    class RecordError {

        @Test
        @DisplayName("adds an ERROR debug entry")
        void recordError_addsErrorEntry() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordError("sink-stage", "timeout", "event-456"); // GH-90000

            assertThat(console.entries()).hasSize(1); // GH-90000
            PipelineDebugConsole.DebugEntry entry = console.entries().get(0); // GH-90000
            assertThat(entry.kind()).isEqualTo(PipelineDebugConsole.DebugEntry.Kind.ERROR); // GH-90000
            assertThat(entry.message()).contains("timeout").contains("event-456");
        }

        @Test
        @DisplayName("errorCount increments after recording")
        void recordError_incrementsErrorCount() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordError("s1", "err1", "e1"); // GH-90000
            console.recordError("s2", "err2", "e2"); // GH-90000
            assertThat(console.errorCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("null errorMessage throws NullPointerException")
        void recordError_nullMessage_throwsNPE() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> console.recordError("stage", null, "event")); // GH-90000
        }
    }

    // ─── clear ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("removes all captured entries")
        void clear_removesAllEntries() { // GH-90000
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageEntry("s1", "e1"); // GH-90000
            console.recordError("s2", "err", "e2"); // GH-90000
            console.clear(); // GH-90000
            assertThat(console.entries()).isEmpty(); // GH-90000
            assertThat(console.errorCount()).isZero(); // GH-90000
        }
    }

    // ─── entries immutability ─────────────────────────────────────────────────

    @Test
    @DisplayName("entries() returns an unmodifiable snapshot")
    void entries_returnsUnmodifiableSnapshot() { // GH-90000
        PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
        console.recordStageEntry("s1", "e1"); // GH-90000

        var entries = console.entries(); // GH-90000
        assertThat(entries).hasSize(1); // GH-90000

        // Additional recordings should not affect the previously captured snapshot
        console.recordStageEntry("s2", "e2"); // GH-90000
        assertThat(entries).hasSize(1); // GH-90000
    }

    // ─── printReport (smoke test) ───────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("printReport() completes without exceptions")
    void printReport_noException() { // GH-90000
        PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
        console.recordStageEntry("stage-1", "event-a"); // GH-90000
        console.recordStageExit("stage-1", 1, 12L); // GH-90000
        console.recordError("stage-2", "oops", "event-b"); // GH-90000
        // Should not throw
        console.printReport(); // GH-90000
    }
}
