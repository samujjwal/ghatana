/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("DefaultAgentInteractionProtocol (P8-T5)")
class DefaultAgentInteractionProtocolTest extends EventloopTestBase {

    private DefaultAgentInteractionProtocol protocol;

    @BeforeEach
    void setUp() { 
        protocol = new DefaultAgentInteractionProtocol(); 
    }

    // ─── AgentMessage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentMessage")
    class AgentMessageTests {

        @Test
        @DisplayName("blank senderAgentId is rejected")
        void blankSenderRejected() { 
            assertThatThrownBy(() -> new AgentMessage( 
                    "m1", "c1", "", null, "t1", null,
                    java.time.Instant.now(), Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("senderAgentId");
        }

        @Test
        @DisplayName("metadata is immutable")
        void metadataIsImmutable() { 
            AgentMessage m = AgentMessage.of("sender", "recipient", "corr", "tenant", "body"); 
            assertThatThrownBy(() -> m.metadata().put("x", "y")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("factory method produces non-null message ID")
        void factoryProducesMessageId() { 
            AgentMessage m = AgentMessage.of("s", "r", "c", "t", "payload"); 
            assertThat(m.messageId()).isNotBlank(); 
            assertThat(m.payload()).isEqualTo("payload");
        }
    }

    // ─── AgentEvent ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentEvent")
    class AgentEventTests {

        @Test
        @DisplayName("blank eventType is rejected")
        void blankEventTypeRejected() { 
            assertThatThrownBy(() -> AgentEvent.of("", "s", "t", null)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("eventType");
        }

        @Test
        @DisplayName("factory produces unique event IDs")
        void factoryProducesUniqueIds() { 
            AgentEvent e1 = AgentEvent.of("et", "s", "t", null); 
            AgentEvent e2 = AgentEvent.of("et", "s", "t", null); 
            assertThat(e1.eventId()).isNotEqualTo(e2.eventId()); 
        }
    }

    // ─── AgentResponse ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentResponse")
    class AgentResponseTests {

        @Test
        @DisplayName("success() creates successful response")
        void successFactory() { 
            AgentResponse r = AgentResponse.success("req-1", "agent-a", "result"); 
            assertThat(r.isSuccess()).isTrue(); 
            assertThat(r.status()).isEqualTo(AgentResponseStatus.SUCCESS); 
            assertThat(r.payload()).isEqualTo("result");
            assertThat(r.errorMessage()).isNull(); 
        }

        @Test
        @DisplayName("error() creates error response")
        void errorFactory() { 
            AgentResponse r = AgentResponse.error("req-2", "agent-a", "boom"); 
            assertThat(r.isSuccess()).isFalse(); 
            assertThat(r.status()).isEqualTo(AgentResponseStatus.ERROR); 
            assertThat(r.errorMessage()).isEqualTo("boom");
        }

        @Test
        @DisplayName("rejected() creates rejected response")
        void rejectedFactory() { 
            AgentResponse r = AgentResponse.rejected("req-3", "agent-a", "policy deny"); 
            assertThat(r.status()).isEqualTo(AgentResponseStatus.REJECTED); 
        }
    }

    // ─── DefaultAgentInteractionProtocol: send ────────────────────────────────

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("routes message to registered handler")
        void routesToRegisteredHandler() { 
            protocol.registerMessageHandler("agent-b", 
                    msg -> Promise.of(AgentResponse.success(msg.messageId(), "agent-b", "pong"))); 
            AgentMessage msg = AgentMessage.of("agent-a", "agent-b", "corr-1", "tenant-1", "ping"); 
            AgentResponse response = runPromise(() -> protocol.send(msg)); 
            assertThat(response.isSuccess()).isTrue(); 
            assertThat(response.payload()).isEqualTo("pong");
        }

        @Test
        @DisplayName("returns error response when no handler registered")
        void returnsErrorWhenNoHandler() { 
            AgentMessage msg = AgentMessage.of("agent-a", "missing-agent", "corr-2", "t", null); 
            AgentResponse response = runPromise(() -> protocol.send(msg)); 
            assertThat(response.status()).isEqualTo(AgentResponseStatus.ERROR); 
        }

        @Test
        @DisplayName("null recipientAgentId causes exceptional promise")
        void nullRecipientCausesException() { 
            AgentMessage msg = AgentMessage.of("agent-a", null, "corr-3", "t", null); 
            assertThatThrownBy(() -> runPromise(() -> protocol.send(msg))) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // ─── DefaultAgentInteractionProtocol: publish ─────────────────────────────

    @Nested
    @DisplayName("publish()")
    class PublishTests {

        @Test
        @DisplayName("invokes registered handler for matching event type")
        void invokesRegisteredHandlerForMatchingType() { 
            boolean[] called = {false};
            protocol.registerHandler(new AgentEventHandler() { 
                @Override
                public String supportedEventType() { return "analysis.completed"; } 

                @Override
                public Promise<Void> handle(AgentEvent event) { 
                    called[0] = true;
                    return Promise.complete(); 
                }
            });
            AgentEvent event = AgentEvent.of("analysis.completed", "agent-a", "t", null); 
            runPromise(() -> protocol.publish(event)); 
            assertThat(called[0]).isTrue(); 
        }

        @Test
        @DisplayName("silently drops events with no registered handler")
        void dropsEventsWithNoHandler() { 
            AgentEvent event = AgentEvent.of("unknown.event", "agent-a", "t", null); 
            // should not throw
            assertThatCode(() -> runPromise(() -> protocol.publish(event))) 
                    .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("later-registered handler replaces earlier one for same type")
        void laterHandlerReplacesEarlier() { 
            int[] count = {0};
            protocol.registerHandler(new AgentEventHandler() { 
                @Override
                public String supportedEventType() { return "test.event"; } 
                @Override
                public Promise<Void> handle(AgentEvent event) { 
                    count[0] += 10;
                    return Promise.complete(); 
                }
            });
            protocol.registerHandler(new AgentEventHandler() { 
                @Override
                public String supportedEventType() { return "test.event"; } 
                @Override
                public Promise<Void> handle(AgentEvent event) { 
                    count[0] += 1;
                    return Promise.complete(); 
                }
            });
            AgentEvent event = AgentEvent.of("test.event", "s", "t", null); 
            runPromise(() -> protocol.publish(event)); 
            assertThat(count[0]).isEqualTo(1); // second handler wins 
        }
    }
}
