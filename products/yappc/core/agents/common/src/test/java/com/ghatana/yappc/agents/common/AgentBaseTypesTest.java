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
        void shouldDefineFourStatuses() { // GH-90000
            var statuses = AgentOutput.ExecutionStatus.values(); // GH-90000
            assertThat(statuses).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("SUCCESS is a valid status")
        void successIsValid() { // GH-90000
            assertThat(AgentOutput.ExecutionStatus.SUCCESS) // GH-90000
                    .isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("FAILURE is a valid status")
        void failureIsValid() { // GH-90000
            assertThat(AgentOutput.ExecutionStatus.FAILURE) // GH-90000
                    .isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("PARTIAL_SUCCESS is a valid status")
        void partialSuccessIsValid() { // GH-90000
            assertThat(AgentOutput.ExecutionStatus.PARTIAL_SUCCESS) // GH-90000
                    .isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("PENDING is a valid status")
        void pendingIsValid() { // GH-90000
            assertThat(AgentOutput.ExecutionStatus.PENDING) // GH-90000
                    .isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("can round-trip through valueOf")
        void shouldRoundTripViaValueOf() { // GH-90000
            for (var status : AgentOutput.ExecutionStatus.values()) { // GH-90000
                assertThat(AgentOutput.ExecutionStatus.valueOf(status.name())) // GH-90000
                        .isSameAs(status); // GH-90000
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
        void shouldBuildWithCommonFields() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            var input = ConcreteInput.builder() // GH-90000
                    .requestId("req-abc")
                    .initiator("user-1")
                    .timestamp(now) // GH-90000
                    .context("some-context")
                    .payload("hello")
                    .build(); // GH-90000

            assertThat(input.getRequestId()).isEqualTo("req-abc");
            assertThat(input.getInitiator()).isEqualTo("user-1");
            assertThat(input.getTimestamp()).isEqualTo(now); // GH-90000
            assertThat(input.getContext()).isEqualTo("some-context");
            assertThat(input.getPayload()).isEqualTo("hello");
        }

        @Test
        @DisplayName("allows null optional fields")
        void shouldAllowNullOptionalFields() { // GH-90000
            var input = ConcreteInput.builder() // GH-90000
                    .requestId("req-xyz")
                    .build(); // GH-90000

            assertThat(input.getRequestId()).isEqualTo("req-xyz");
            assertThat(input.getContext()).isNull(); // GH-90000
            assertThat(input.getInitiator()).isNull(); // GH-90000
            assertThat(input.getTimestamp()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Lombok @Data generates equals/hashCode/toString")
        void shouldSupportEquality() { // GH-90000
            var a = ConcreteInput.builder().requestId("same").payload("p").build();
            var b = ConcreteInput.builder().requestId("same").payload("p").build();
            assertThat(a).isEqualTo(b); // GH-90000
            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }
    }

    @Nested
    @DisplayName("AgentOutput builder and fields")
    class AgentOutputTest {

        @Test
        @DisplayName("builds with all common fields set")
        void shouldBuildWithCommonFields() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            var output = ConcreteOutput.builder() // GH-90000
                    .responseId("resp-001")
                    .status(AgentOutput.ExecutionStatus.SUCCESS) // GH-90000
                    .timestamp(now) // GH-90000
                    .result("done")
                    .build(); // GH-90000

            assertThat(output.getResponseId()).isEqualTo("resp-001");
            assertThat(output.getStatus()).isEqualTo(AgentOutput.ExecutionStatus.SUCCESS); // GH-90000
            assertThat(output.getTimestamp()).isEqualTo(now); // GH-90000
            assertThat(output.getResult()).isEqualTo("done"); // y04-ok: domain getter, not ActiveJ Promise
        }

        @Test
        @DisplayName("builds with errorMessage when status is FAILURE")
        void shouldCaptureErrorMessage() { // GH-90000
            var output = ConcreteOutput.builder() // GH-90000
                    .responseId("resp-002")
                    .status(AgentOutput.ExecutionStatus.FAILURE) // GH-90000
                    .errorMessage("Something went wrong")
                    .result(null) // GH-90000
                    .build(); // GH-90000

            assertThat(output.getStatus()).isEqualTo(AgentOutput.ExecutionStatus.FAILURE); // GH-90000
            assertThat(output.getErrorMessage()).isEqualTo("Something went wrong");
        }

        @Test
        @DisplayName("Lombok @Data generates equals/hashCode")
        void shouldSupportEquality() { // GH-90000
            var a = ConcreteOutput.builder() // GH-90000
                    .responseId("resp-003")
                    .status(AgentOutput.ExecutionStatus.PENDING) // GH-90000
                    .build(); // GH-90000
            var b = ConcreteOutput.builder() // GH-90000
                    .responseId("resp-003")
                    .status(AgentOutput.ExecutionStatus.PENDING) // GH-90000
                    .build(); // GH-90000
            assertThat(a).isEqualTo(b); // GH-90000
        }
    }
}
