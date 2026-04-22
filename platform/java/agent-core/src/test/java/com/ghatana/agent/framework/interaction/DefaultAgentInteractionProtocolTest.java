/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the inter-agent protocol: {@link AgentMessage}, {@link AgentEvent},
 * {@link AgentResponse}, and {@link DefaultAgentInteractionProtocol}.
 *
 * @doc.type class
 * @doc.purpose Tests for P8-T5: inter-agent protocol
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultAgentInteractionProtocol (P8-T5) [GH-90000]")
class DefaultAgentInteractionProtocolTest extends EventloopTestBase {

    private DefaultAgentInteractionProtocol protocol;

    @BeforeEach
    void setUp() { // GH-90000
        protocol = new DefaultAgentInteractionProtocol(); // GH-90000
    }

    // ─── AgentMessage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentMessage [GH-90000]")
    class AgentMessageTests {

        @Test
        @DisplayName("blank senderAgentId is rejected [GH-90000]")
        void blankSenderRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentMessage( // GH-90000
                    "m1", "c1", "", null, "t1", null,
                    java.time.Instant.now(), Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("senderAgentId [GH-90000]");
        }

        @Test
        @DisplayName("metadata is immutable [GH-90000]")
        void metadataIsImmutable() { // GH-90000
            AgentMessage m = AgentMessage.of("sender", "recipient", "corr", "tenant", "body"); // GH-90000
            assertThatThrownBy(() -> m.metadata().put("x", "y")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("factory method produces non-null message ID [GH-90000]")
        void factoryProducesMessageId() { // GH-90000
            AgentMessage m = AgentMessage.of("s", "r", "c", "t", "payload"); // GH-90000
            assertThat(m.messageId()).isNotBlank(); // GH-90000
            assertThat(m.payload()).isEqualTo("payload [GH-90000]");
        }
    }

    // ─── AgentEvent ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentEvent [GH-90000]")
    class AgentEventTests {

        @Test
        @DisplayName("blank eventType is rejected [GH-90000]")
        void blankEventTypeRejected() { // GH-90000
            assertThatThrownBy(() -> AgentEvent.of("", "s", "t", null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("eventType [GH-90000]");
        }

        @Test
        @DisplayName("factory produces unique event IDs [GH-90000]")
        void factoryProducesUniqueIds() { // GH-90000
            AgentEvent e1 = AgentEvent.of("et", "s", "t", null); // GH-90000
            AgentEvent e2 = AgentEvent.of("et", "s", "t", null); // GH-90000
            assertThat(e1.eventId()).isNotEqualTo(e2.eventId()); // GH-90000
        }
    }

    // ─── AgentResponse ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentResponse [GH-90000]")
    class AgentResponseTests {

        @Test
        @DisplayName("success() creates successful response [GH-90000]")
        void successFactory() { // GH-90000
            AgentResponse r = AgentResponse.success("req-1", "agent-a", "result"); // GH-90000
            assertThat(r.isSuccess()).isTrue(); // GH-90000
            assertThat(r.status()).isEqualTo(AgentResponseStatus.SUCCESS); // GH-90000
            assertThat(r.payload()).isEqualTo("result [GH-90000]");
            assertThat(r.errorMessage()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("error() creates error response [GH-90000]")
        void errorFactory() { // GH-90000
            AgentResponse r = AgentResponse.error("req-2", "agent-a", "boom"); // GH-90000
            assertThat(r.isSuccess()).isFalse(); // GH-90000
            assertThat(r.status()).isEqualTo(AgentResponseStatus.ERROR); // GH-90000
            assertThat(r.errorMessage()).isEqualTo("boom [GH-90000]");
        }

        @Test
        @DisplayName("rejected() creates rejected response [GH-90000]")
        void rejectedFactory() { // GH-90000
            AgentResponse r = AgentResponse.rejected("req-3", "agent-a", "policy deny"); // GH-90000
            assertThat(r.status()).isEqualTo(AgentResponseStatus.REJECTED); // GH-90000
        }
    }

    // ─── DefaultAgentInteractionProtocol: send ────────────────────────────────

    @Nested
    @DisplayName("send() [GH-90000]")
    class SendTests {

        @Test
        @DisplayName("routes message to registered handler [GH-90000]")
        void routesToRegisteredHandler() { // GH-90000
            protocol.registerMessageHandler("agent-b", // GH-90000
                    msg -> Promise.of(AgentResponse.success(msg.messageId(), "agent-b", "pong"))); // GH-90000
            AgentMessage msg = AgentMessage.of("agent-a", "agent-b", "corr-1", "tenant-1", "ping"); // GH-90000
            AgentResponse response = runPromise(() -> protocol.send(msg)); // GH-90000
            assertThat(response.isSuccess()).isTrue(); // GH-90000
            assertThat(response.payload()).isEqualTo("pong [GH-90000]");
        }

        @Test
        @DisplayName("returns error response when no handler registered [GH-90000]")
        void returnsErrorWhenNoHandler() { // GH-90000
            AgentMessage msg = AgentMessage.of("agent-a", "missing-agent", "corr-2", "t", null); // GH-90000
            AgentResponse response = runPromise(() -> protocol.send(msg)); // GH-90000
            assertThat(response.status()).isEqualTo(AgentResponseStatus.ERROR); // GH-90000
        }

        @Test
        @DisplayName("null recipientAgentId causes exceptional promise [GH-90000]")
        void nullRecipientCausesException() { // GH-90000
            AgentMessage msg = AgentMessage.of("agent-a", null, "corr-3", "t", null); // GH-90000
            assertThatThrownBy(() -> runPromise(() -> protocol.send(msg))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─── DefaultAgentInteractionProtocol: publish ─────────────────────────────

    @Nested
    @DisplayName("publish() [GH-90000]")
    class PublishTests {

        @Test
        @DisplayName("invokes registered handler for matching event type [GH-90000]")
        void invokesRegisteredHandlerForMatchingType() { // GH-90000
            boolean[] called = {false};
            protocol.registerHandler(new AgentEventHandler() { // GH-90000
                @Override
                public String supportedEventType() { return "analysis.completed"; } // GH-90000

                @Override
                public Promise<Void> handle(AgentEvent event) { // GH-90000
                    called[0] = true;
                    return Promise.complete(); // GH-90000
                }
            });
            AgentEvent event = AgentEvent.of("analysis.completed", "agent-a", "t", null); // GH-90000
            runPromise(() -> protocol.publish(event)); // GH-90000
            assertThat(called[0]).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("silently drops events with no registered handler [GH-90000]")
        void dropsEventsWithNoHandler() { // GH-90000
            AgentEvent event = AgentEvent.of("unknown.event", "agent-a", "t", null); // GH-90000
            // should not throw
            assertThatCode(() -> runPromise(() -> protocol.publish(event))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("later-registered handler replaces earlier one for same type [GH-90000]")
        void laterHandlerReplacesEarlier() { // GH-90000
            int[] count = {0};
            protocol.registerHandler(new AgentEventHandler() { // GH-90000
                @Override
                public String supportedEventType() { return "test.event"; } // GH-90000
                @Override
                public Promise<Void> handle(AgentEvent event) { // GH-90000
                    count[0] += 10;
                    return Promise.complete(); // GH-90000
                }
            });
            protocol.registerHandler(new AgentEventHandler() { // GH-90000
                @Override
                public String supportedEventType() { return "test.event"; } // GH-90000
                @Override
                public Promise<Void> handle(AgentEvent event) { // GH-90000
                    count[0] += 1;
                    return Promise.complete(); // GH-90000
                }
            });
            AgentEvent event = AgentEvent.of("test.event", "s", "t", null); // GH-90000
            runPromise(() -> protocol.publish(event)); // GH-90000
            assertThat(count[0]).isEqualTo(1); // second handler wins // GH-90000
        }
    }
}
