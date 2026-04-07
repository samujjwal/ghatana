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
        void create_matchesPipelineName() {
            PipelineDebugConsole console = PipelineDebugConsole.create("my-pipe");
            assertThat(console.pipelineName()).isEqualTo("my-pipe");
        }

        @Test
        @DisplayName("starts with empty entry list")
        void create_emptyEntries() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThat(console.entries()).isEmpty();
        }

        @Test
        @DisplayName("null name throws NullPointerException")
        void create_nullName_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PipelineDebugConsole.create(null));
        }
    }

    // ─── attachTo ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("attachTo(PipelineSpec)")
    class AttachTo {

        @Test
        @DisplayName("pre-populates METADATA entries for each stage")
        void attachTo_populatesMetadataEntries() {
            PipelineSpec spec = AepQuickStartTemplates
                    .fraudDetection("fraud", "t1")
                    .build();

            PipelineDebugConsole console = PipelineDebugConsole.attachTo(spec);

            assertThat(console.entries())
                    .hasSize(spec.stages().size())
                    .allMatch(e -> e.kind() == PipelineDebugConsole.DebugEntry.Kind.METADATA);
        }

        @Test
        @DisplayName("null spec throws NullPointerException")
        void attachTo_nullSpec_throwsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> PipelineDebugConsole.attachTo(null));
        }
    }

    // ─── recordStageEntry ────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordStageEntry()")
    class RecordStageEntry {

        @Test
        @DisplayName("adds an ENTRY debug entry with correct stageId")
        void recordStageEntry_addsEntry() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageEntry("enrichment", "event-123");

            assertThat(console.entries()).hasSize(1);
            PipelineDebugConsole.DebugEntry entry = console.entries().get(0);
            assertThat(entry.kind()).isEqualTo(PipelineDebugConsole.DebugEntry.Kind.ENTRY);
            assertThat(entry.stageId()).isEqualTo("enrichment");
            assertThat(entry.message()).contains("event-123");
        }

        @Test
        @DisplayName("null stageId throws NullPointerException")
        void recordStageEntry_nullStage_throwsNPE() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThatNullPointerException()
                    .isThrownBy(() -> console.recordStageEntry(null, "event"));
        }

        @Test
        @DisplayName("entryCount increments after recording")
        void recordStageEntry_incrementsEntryCount() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageEntry("stage-1", "e1");
            console.recordStageEntry("stage-2", "e2");
            assertThat(console.entryCount()).isEqualTo(2);
        }
    }

    // ─── recordStageExit ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordStageExit()")
    class RecordStageExit {

        @Test
        @DisplayName("adds an EXIT debug entry")
        void recordStageExit_addsExitEntry() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageExit("filter-stage", 3, 42L);

            assertThat(console.entries()).hasSize(1);
            PipelineDebugConsole.DebugEntry entry = console.entries().get(0);
            assertThat(entry.kind()).isEqualTo(PipelineDebugConsole.DebugEntry.Kind.EXIT);
            assertThat(entry.stageId()).isEqualTo("filter-stage");
            assertThat(entry.message()).contains("3").contains("42");
        }

        @Test
        @DisplayName("exitCount increments after recording")
        void recordStageExit_incrementsExitCount() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageExit("s1", 1, 10L);
            console.recordStageExit("s2", 0, 5L);
            assertThat(console.exitCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("null stageId throws NullPointerException")
        void recordStageExit_nullStage_throwsNPE() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThatNullPointerException()
                    .isThrownBy(() -> console.recordStageExit(null, 1, 10L));
        }
    }

    // ─── recordError ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordError()")
    class RecordError {

        @Test
        @DisplayName("adds an ERROR debug entry")
        void recordError_addsErrorEntry() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordError("sink-stage", "timeout", "event-456");

            assertThat(console.entries()).hasSize(1);
            PipelineDebugConsole.DebugEntry entry = console.entries().get(0);
            assertThat(entry.kind()).isEqualTo(PipelineDebugConsole.DebugEntry.Kind.ERROR);
            assertThat(entry.message()).contains("timeout").contains("event-456");
        }

        @Test
        @DisplayName("errorCount increments after recording")
        void recordError_incrementsErrorCount() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordError("s1", "err1", "e1");
            console.recordError("s2", "err2", "e2");
            assertThat(console.errorCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("null errorMessage throws NullPointerException")
        void recordError_nullMessage_throwsNPE() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            assertThatNullPointerException()
                    .isThrownBy(() -> console.recordError("stage", null, "event"));
        }
    }

    // ─── clear ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("removes all captured entries")
        void clear_removesAllEntries() {
            PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
            console.recordStageEntry("s1", "e1");
            console.recordError("s2", "err", "e2");
            console.clear();
            assertThat(console.entries()).isEmpty();
            assertThat(console.errorCount()).isZero();
        }
    }

    // ─── entries immutability ─────────────────────────────────────────────────

    @Test
    @DisplayName("entries() returns an unmodifiable snapshot")
    void entries_returnsUnmodifiableSnapshot() {
        PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
        console.recordStageEntry("s1", "e1");

        var entries = console.entries();
        assertThat(entries).hasSize(1);

        // Additional recordings should not affect the previously captured snapshot
        console.recordStageEntry("s2", "e2");
        assertThat(entries).hasSize(1);
    }

    // ─── printReport (smoke test) ─────────────────────────────────────────────

    @Test
    @DisplayName("printReport() completes without exceptions")
    void printReport_noException() {
        PipelineDebugConsole console = PipelineDebugConsole.create("pipe");
        console.recordStageEntry("stage-1", "event-a");
        console.recordStageExit("stage-1", 1, 12L);
        console.recordError("stage-2", "oops", "event-b");
        // Should not throw
        console.printReport();
    }
}

