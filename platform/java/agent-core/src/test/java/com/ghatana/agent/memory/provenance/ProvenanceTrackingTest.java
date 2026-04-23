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
@DisplayName("Provenance Tracking Tests")
@Tag("integration")
class ProvenanceTrackingTest extends EventloopTestBase {

    // ── Source attribution ────────────────────────────────────────────────────

    @Nested
    @DisplayName("source attribution")
    class SourceAttribution {

        @Test
        @DisplayName("provenance records the originating source")
        void provenance_recordsOriginatingSource() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("agent:reasoning-agent-v2")
                    .build(); // GH-90000

            assertThat(provenance.getSource()).isEqualTo("agent:reasoning-agent-v2");
        }

        @Test
        @DisplayName("provenance with tool source uses tool: prefix by convention")
        void provenanceWithToolSource_usesToolPrefix() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("tool:grep")
                    .build(); // GH-90000

            assertThat(provenance.getSource()).startsWith("tool:");
        }

        @Test
        @DisplayName("provenance with user source uses user: prefix by convention")
        void provenanceWithUserSource_usesUserPrefix() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("user:input")
                    .build(); // GH-90000

            assertThat(provenance.getSource()).startsWith("user:");
        }

        @Test
        @DisplayName("default provenance has source unknown")
        void defaultProvenance_hasSourceUnknown() { // GH-90000
            Provenance provenance = Provenance.builder().build(); // GH-90000

            assertThat(provenance.getSource()).isEqualTo("unknown");
        }
    }

    // ── Trace ID propagation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("trace ID propagation")
    class TraceIdPropagation {

        @Test
        @DisplayName("provenance stores trace ID for correlation")
        void provenance_storesTraceIdForCorrelation() { // GH-90000
            String traceId = UUID.randomUUID().toString(); // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("inference:gpt-4")
                    .traceId(traceId) // GH-90000
                    .build(); // GH-90000

            assertThat(provenance.getTraceId()).isEqualTo(traceId); // GH-90000
        }

        @Test
        @DisplayName("provenance with null trace ID is valid (offline mode)")
        void provenanceWithNullTraceId_isValid() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("consolidation:v2")
                    .traceId(null) // GH-90000
                    .build(); // GH-90000

            assertThat(provenance.getTraceId()).isNull(); // GH-90000
        }
    }

    // ── Confidence source ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("confidence source tracking")
    class ConfidenceSourceTracking {

        @Test
        @DisplayName("LLM_INFERENCE confidence source is default")
        void llmInference_isDefaultConfidenceSource() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("agent:reasoner")
                    .build(); // GH-90000

            assertThat(provenance.getConfidenceSource()) // GH-90000
                    .isEqualTo(com.ghatana.agent.memory.model.Provenance.ConfidenceSource.LLM_INFERENCE); // GH-90000
        }

        @Test
        @DisplayName("provenance can record HUMAN_FEEDBACK as confidence source")
        void provenance_canRecordHumanFeedbackAsConfidenceSource() { // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("user:feedback")
                .confidenceSource(Provenance.ConfidenceSource.HUMAN) // GH-90000
                    .build(); // GH-90000

            assertThat(provenance.getConfidenceSource()) // GH-90000
                .isEqualTo(Provenance.ConfidenceSource.HUMAN); // GH-90000
        }
    }

    // ── Chain of custody (via memory item) ─────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("chain of custody via memory items")
    class ChainOfCustody {

        @Test
        @DisplayName("procedure records provenance attached to memory item")
        void procedure_recordsProvenanceAttachedToMemoryItem() { // GH-90000
            String traceId = UUID.randomUUID().toString(); // GH-90000
            Provenance provenance = Provenance.builder() // GH-90000
                    .source("consolidation:hourly-run")
                    .traceId(traceId) // GH-90000
                    .build(); // GH-90000

            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("Optimize database queries")
                    .action("Apply query hints")
                    .provenance(provenance) // GH-90000
                    .build(); // GH-90000

            assertThat(procedure.getProvenance().getSource()).isEqualTo("consolidation:hourly-run");
            assertThat(procedure.getProvenance().getTraceId()).isEqualTo(traceId); // GH-90000
        }

        @Test
        @DisplayName("provenance of derived item differs from originating source")
        void provenanceOfDerivedItem_differsFromOriginatingSource() { // GH-90000
            Provenance original  = Provenance.builder().source("user:input").build();
            Provenance derived   = Provenance.builder() // GH-90000
                    .source("inference:gpt-4")
                    .confidenceSource(Provenance.ConfidenceSource.LLM_INFERENCE) // GH-90000
                    .build(); // GH-90000

            assertThat(original.getSource()).isNotEqualTo(derived.getSource()); // GH-90000
            assertThat(derived.getSource()).startsWith("inference:");
        }

        @Test
        @DisplayName("two items with same trace ID can be correlated across a flow")
        void twoItemsWithSameTraceId_canBeCorrelatedAcrossFlow() { // GH-90000
            String sharedTraceId = UUID.randomUUID().toString(); // GH-90000

            Provenance prov1 = Provenance.builder().source("agent:a").traceId(sharedTraceId).build();
            Provenance prov2 = Provenance.builder().source("agent:b").traceId(sharedTraceId).build();

            assertThat(prov1.getTraceId()).isEqualTo(prov2.getTraceId()); // GH-90000
        }
    }
}
