package com.ghatana.yappc.agents.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentInput and AgentOutput base class contracts.
 *
 * @doc.type class
 * @doc.purpose Verify AgentInput and AgentOutput base class contracts and builder behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Agent Base Types (AgentInput / AgentOutput)")
class AgentBaseTypesTest {

    // ─── AgentOutput ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentOutput.ExecutionStatus")
    class ExecutionStatusTest {

        @Test
        @DisplayName("defines four canonical status values")
        void shouldDefineFourStatuses() { 
            var statuses = AgentOutput.ExecutionStatus.values(); 
            assertThat(statuses).hasSize(4); 
        }

        @Test
        @DisplayName("SUCCESS is a valid status")
        void successIsValid() { 
            assertThat(AgentOutput.ExecutionStatus.SUCCESS) 
                    .isNotNull(); 
        }

        @Test
        @DisplayName("FAILURE is a valid status")
        void failureIsValid() { 
            assertThat(AgentOutput.ExecutionStatus.FAILURE) 
                    .isNotNull(); 
        }

        @Test
        @DisplayName("PARTIAL_SUCCESS is a valid status")
        void partialSuccessIsValid() { 
            assertThat(AgentOutput.ExecutionStatus.PARTIAL_SUCCESS) 
                    .isNotNull(); 
        }

        @Test
        @DisplayName("PENDING is a valid status")
        void pendingIsValid() { 
            assertThat(AgentOutput.ExecutionStatus.PENDING) 
                    .isNotNull(); 
        }

        @Test
        @DisplayName("can round-trip through valueOf")
        void shouldRoundTripViaValueOf() { 
            for (var status : AgentOutput.ExecutionStatus.values()) { 
                assertThat(AgentOutput.ExecutionStatus.valueOf(status.name())) 
                        .isSameAs(status); 
            }
        }
    }

    // ─── Concrete subclass builders ───────────────────────────────────────────
    // AgentInput and AgentOutput are abstract; we need a concrete subclass to
    // exercise the @SuperBuilder and @Data behaviour.

    /** Minimal concrete AgentInput for testing the abstract base. */
    @lombok.Data
    @lombok.experimental.SuperBuilder
    static class ConcreteInput extends AgentInput {
        private String payload;
    }

    /** Minimal concrete AgentOutput for testing the abstract base. */
    @lombok.Data
    @lombok.experimental.SuperBuilder
    static class ConcreteOutput extends AgentOutput {
        private String result;
    }

    @Nested
    @DisplayName("AgentInput builder and fields")
    class AgentInputTest {

        @Test
        @DisplayName("builds with all common fields set")
        void shouldBuildWithCommonFields() { 
            long now = System.currentTimeMillis(); 
            var input = ConcreteInput.builder() 
                    .requestId("req-abc")
                    .initiator("user-1")
                    .timestamp(now) 
                    .context("some-context")
                    .payload("hello")
                    .build(); 

            assertThat(input.getRequestId()).isEqualTo("req-abc");
            assertThat(input.getInitiator()).isEqualTo("user-1");
            assertThat(input.getTimestamp()).isEqualTo(now); 
            assertThat(input.getContext()).isEqualTo("some-context");
            assertThat(input.getPayload()).isEqualTo("hello");
        }

        @Test
        @DisplayName("allows null optional fields")
        void shouldAllowNullOptionalFields() { 
            var input = ConcreteInput.builder() 
                    .requestId("req-xyz")
                    .build(); 

            assertThat(input.getRequestId()).isEqualTo("req-xyz");
            assertThat(input.getContext()).isNull(); 
            assertThat(input.getInitiator()).isNull(); 
            assertThat(input.getTimestamp()).isNull(); 
        }

        @Test
        @DisplayName("Lombok @Data generates equals/hashCode/toString")
        void shouldSupportEquality() { 
            var a = ConcreteInput.builder().requestId("same").payload("p").build();
            var b = ConcreteInput.builder().requestId("same").payload("p").build();
            assertThat(a).isEqualTo(b); 
            assertThat(a.hashCode()).isEqualTo(b.hashCode()); 
        }
    }

    @Nested
    @DisplayName("AgentOutput builder and fields")
    class AgentOutputTest {

        @Test
        @DisplayName("builds with all common fields set")
        void shouldBuildWithCommonFields() { 
            long now = System.currentTimeMillis(); 
            var output = ConcreteOutput.builder() 
                    .responseId("resp-001")
                    .status(AgentOutput.ExecutionStatus.SUCCESS) 
                    .timestamp(now) 
                    .result("done")
                    .build(); 

            assertThat(output.getResponseId()).isEqualTo("resp-001");
            assertThat(output.getStatus()).isEqualTo(AgentOutput.ExecutionStatus.SUCCESS); 
            assertThat(output.getTimestamp()).isEqualTo(now); 
            assertThat(output.getResult()).isEqualTo("done"); // y04-ok: domain getter, not ActiveJ Promise
        }

        @Test
        @DisplayName("builds with errorMessage when status is FAILURE")
        void shouldCaptureErrorMessage() { 
            var output = ConcreteOutput.builder() 
                    .responseId("resp-002")
                    .status(AgentOutput.ExecutionStatus.FAILURE) 
                    .errorMessage("Something went wrong")
                    .result(null) 
                    .build(); 

            assertThat(output.getStatus()).isEqualTo(AgentOutput.ExecutionStatus.FAILURE); 
            assertThat(output.getErrorMessage()).isEqualTo("Something went wrong");
        }

        @Test
        @DisplayName("Lombok @Data generates equals/hashCode")
        void shouldSupportEquality() { 
            var a = ConcreteOutput.builder() 
                    .responseId("resp-003")
                    .status(AgentOutput.ExecutionStatus.PENDING) 
                    .build(); 
            var b = ConcreteOutput.builder() 
                    .responseId("resp-003")
                    .status(AgentOutput.ExecutionStatus.PENDING) 
                    .build(); 
            assertThat(a).isEqualTo(b); 
        }
    }
}
