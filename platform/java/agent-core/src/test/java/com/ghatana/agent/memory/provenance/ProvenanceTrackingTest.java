package com.ghatana.agent.memory.provenance;

import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for provenance tracking — validates source attribution, confidence scoring,
 * trace ID propagation, and chain-of-custody records across memory items.
 *
 * @doc.type class
 * @doc.purpose Tests for provenance tracking and auditability of memory items
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Provenance Tracking Tests [GH-90000]")
@Tag("integration [GH-90000]")
class ProvenanceTrackingTest extends EventloopTestBase {

    // ── Source attribution ────────────────────────────────────────────────────

    @Nested
    @DisplayName("source attribution [GH-90000]")
    class SourceAttribution {

        @Test
        @DisplayName("provenance records the originating source [GH-90000]")
        void provenance_recordsOriginatingSource() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("agent:reasoning-agent-v2 [GH-90000]")
                    .build(); // GH-90000

            assertThat(provenance.getSource()).isEqualTo("agent:reasoning-agent-v2 [GH-90000]");
        }

        @Test
        @DisplayName("provenance with tool source uses tool: prefix by convention [GH-90000]")
        void provenanceWithToolSource_usesToolPrefix() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("tool:grep [GH-90000]")
                    .build(); // GH-90000

            assertThat(provenance.getSource()).startsWith("tool: [GH-90000]");
        }

        @Test
        @DisplayName("provenance with user source uses user: prefix by convention [GH-90000]")
        void provenanceWithUserSource_usesUserPrefix() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("user:input [GH-90000]")
                    .build(); // GH-90000

            assertThat(provenance.getSource()).startsWith("user: [GH-90000]");
        }

        @Test
        @DisplayName("default provenance has source unknown [GH-90000]")
        void defaultProvenance_hasSourceUnknown() { // GH-90000
            Provenance provenance = Provenance.builder().build(); // GH-90000

            assertThat(provenance.getSource()).isEqualTo("unknown [GH-90000]");
        }
    }

    // ── Trace ID propagation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("trace ID propagation [GH-90000]")
    class TraceIdPropagation {

        @Test
        @DisplayName("provenance stores trace ID for correlation [GH-90000]")
        void provenance_storesTraceIdForCorrelation() { // GH-90000
            String traceId = UUID.randomUUID().toString(); // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("inference:gpt-4 [GH-90000]")
                    .traceId(traceId) // GH-90000
                    .build(); // GH-90000

            assertThat(provenance.getTraceId()).isEqualTo(traceId); // GH-90000
        }

        @Test
        @DisplayName("provenance with null trace ID is valid (offline mode) [GH-90000]")
        void provenanceWithNullTraceId_isValid() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("consolidation:v2 [GH-90000]")
                    .traceId(null) // GH-90000
                    .build(); // GH-90000

            assertThat(provenance.getTraceId()).isNull(); // GH-90000
        }
    }

    // ── Confidence source ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("confidence source tracking [GH-90000]")
    class ConfidenceSourceTracking {

        @Test
        @DisplayName("LLM_INFERENCE confidence source is default [GH-90000]")
        void llmInference_isDefaultConfidenceSource() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("agent:reasoner [GH-90000]")
                    .build(); // GH-90000

            assertThat(provenance.getConfidenceSource()) // GH-90000
                    .isEqualTo(com.ghatana.agent.memory.model.Provenance.ConfidenceSource.LLM_INFERENCE); // GH-90000
        }

        @Test
        @DisplayName("provenance can record HUMAN_FEEDBACK as confidence source [GH-90000]")
        void provenance_canRecordHumanFeedbackAsConfidenceSource() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("user:feedback [GH-90000]")
                .confidenceSource(Provenance.ConfidenceSource.HUMAN) // GH-90000
                    .build(); // GH-90000

            assertThat(provenance.getConfidenceSource()) // GH-90000
                .isEqualTo(Provenance.ConfidenceSource.HUMAN); // GH-90000
        }
    }

    // ── Chain of custody (via memory item) ─────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("chain of custody via memory items [GH-90000]")
    class ChainOfCustody {

        @Test
        @DisplayName("procedure records provenance attached to memory item [GH-90000]")
        void procedure_recordsProvenanceAttachedToMemoryItem() { // GH-90000
            String traceId = UUID.randomUUID().toString(); // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("consolidation:hourly-run [GH-90000]")
                    .traceId(traceId) // GH-90000
                    .build(); // GH-90000

            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("Optimize database queries [GH-90000]")
                    .action("Apply query hints [GH-90000]")
                    .provenance(provenance) // GH-90000
                    .build(); // GH-90000

            assertThat(procedure.getProvenance().getSource()).isEqualTo("consolidation:hourly-run [GH-90000]");
            assertThat(procedure.getProvenance().getTraceId()).isEqualTo(traceId); // GH-90000
        }

        @Test
        @DisplayName("provenance of derived item differs from originating source [GH-90000]")
        void provenanceOfDerivedItem_differsFromOriginatingSource() { // GH-90000
            Provenance original  = Provenance.builder().source("user:input [GH-90000]").build();
            Provenance derived   = Provenance.builder() // GH-90000
                    .source("inference:gpt-4 [GH-90000]")
                    .confidenceSource(Provenance.ConfidenceSource.LLM_INFERENCE) // GH-90000
                    .build(); // GH-90000

            assertThat(original.getSource()).isNotEqualTo(derived.getSource()); // GH-90000
            assertThat(derived.getSource()).startsWith("inference: [GH-90000]");
        }

        @Test
        @DisplayName("two items with same trace ID can be correlated across a flow [GH-90000]")
        void twoItemsWithSameTraceId_canBeCorrelatedAcrossFlow() { // GH-90000
            String sharedTraceId = UUID.randomUUID().toString(); // GH-90000

            Provenance prov1 = Provenance.builder().source("agent:a [GH-90000]").traceId(sharedTraceId).build();
            Provenance prov2 = Provenance.builder().source("agent:b [GH-90000]").traceId(sharedTraceId).build();

            assertThat(prov1.getTraceId()).isEqualTo(prov2.getTraceId()); // GH-90000
        }
    }
}
