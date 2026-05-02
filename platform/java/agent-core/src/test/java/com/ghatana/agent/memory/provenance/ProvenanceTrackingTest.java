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
        void provenance_recordsOriginatingSource() { 
            Provenance provenance = Provenance.builder() 
                    .source("agent:reasoning-agent-v2")
                    .build(); 

            assertThat(provenance.getSource()).isEqualTo("agent:reasoning-agent-v2");
        }

        @Test
        @DisplayName("provenance with tool source uses tool: prefix by convention")
        void provenanceWithToolSource_usesToolPrefix() { 
            Provenance provenance = Provenance.builder() 
                    .source("tool:grep")
                    .build(); 

            assertThat(provenance.getSource()).startsWith("tool:");
        }

        @Test
        @DisplayName("provenance with user source uses user: prefix by convention")
        void provenanceWithUserSource_usesUserPrefix() { 
            Provenance provenance = Provenance.builder() 
                    .source("user:input")
                    .build(); 

            assertThat(provenance.getSource()).startsWith("user:");
        }

        @Test
        @DisplayName("default provenance has source unknown")
        void defaultProvenance_hasSourceUnknown() { 
            Provenance provenance = Provenance.builder().build(); 

            assertThat(provenance.getSource()).isEqualTo("unknown");
        }
    }

    // ── Trace ID propagation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("trace ID propagation")
    class TraceIdPropagation {

        @Test
        @DisplayName("provenance stores trace ID for correlation")
        void provenance_storesTraceIdForCorrelation() { 
            String traceId = UUID.randomUUID().toString(); 
            Provenance provenance = Provenance.builder() 
                    .source("inference:gpt-4")
                    .traceId(traceId) 
                    .build(); 

            assertThat(provenance.getTraceId()).isEqualTo(traceId); 
        }

        @Test
        @DisplayName("provenance with null trace ID is valid (offline mode)")
        void provenanceWithNullTraceId_isValid() { 
            Provenance provenance = Provenance.builder() 
                    .source("consolidation:v2")
                    .traceId(null) 
                    .build(); 

            assertThat(provenance.getTraceId()).isNull(); 
        }
    }

    // ── Confidence source ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("confidence source tracking")
    class ConfidenceSourceTracking {

        @Test
        @DisplayName("LLM_INFERENCE confidence source is default")
        void llmInference_isDefaultConfidenceSource() { 
            Provenance provenance = Provenance.builder() 
                    .source("agent:reasoner")
                    .build(); 

            assertThat(provenance.getConfidenceSource()) 
                    .isEqualTo(com.ghatana.agent.memory.model.Provenance.ConfidenceSource.LLM_INFERENCE); 
        }

        @Test
        @DisplayName("provenance can record HUMAN_FEEDBACK as confidence source")
        void provenance_canRecordHumanFeedbackAsConfidenceSource() { 
            Provenance provenance = Provenance.builder() 
                    .source("user:feedback")
                .confidenceSource(Provenance.ConfidenceSource.HUMAN) 
                    .build(); 

            assertThat(provenance.getConfidenceSource()) 
                .isEqualTo(Provenance.ConfidenceSource.HUMAN); 
        }
    }

    // ── Chain of custody (via memory item) ─────────────────────────────────── 

    @Nested
    @DisplayName("chain of custody via memory items")
    class ChainOfCustody {

        @Test
        @DisplayName("procedure records provenance attached to memory item")
        void procedure_recordsProvenanceAttachedToMemoryItem() { 
            String traceId = UUID.randomUUID().toString(); 
            Provenance provenance = Provenance.builder() 
                    .source("consolidation:hourly-run")
                    .traceId(traceId) 
                    .build(); 

            EnhancedProcedure procedure = EnhancedProcedure.builder() 
                    .id(UUID.randomUUID().toString()) 
                    .situation("Optimize database queries")
                    .action("Apply query hints")
                    .provenance(provenance) 
                    .build(); 

            assertThat(procedure.getProvenance().getSource()).isEqualTo("consolidation:hourly-run");
            assertThat(procedure.getProvenance().getTraceId()).isEqualTo(traceId); 
        }

        @Test
        @DisplayName("provenance of derived item differs from originating source")
        void provenanceOfDerivedItem_differsFromOriginatingSource() { 
            Provenance original  = Provenance.builder().source("user:input").build();
            Provenance derived   = Provenance.builder() 
                    .source("inference:gpt-4")
                    .confidenceSource(Provenance.ConfidenceSource.LLM_INFERENCE) 
                    .build(); 

            assertThat(original.getSource()).isNotEqualTo(derived.getSource()); 
            assertThat(derived.getSource()).startsWith("inference:");
        }

        @Test
        @DisplayName("two items with same trace ID can be correlated across a flow")
        void twoItemsWithSameTraceId_canBeCorrelatedAcrossFlow() { 
            String sharedTraceId = UUID.randomUUID().toString(); 

            Provenance prov1 = Provenance.builder().source("agent:a").traceId(sharedTraceId).build();
            Provenance prov2 = Provenance.builder().source("agent:b").traceId(sharedTraceId).build();

            assertThat(prov1.getTraceId()).isEqualTo(prov2.getTraceId()); 
        }
    }
}
