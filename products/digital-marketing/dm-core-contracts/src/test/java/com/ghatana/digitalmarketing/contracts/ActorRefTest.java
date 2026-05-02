package com.ghatana.digitalmarketing.contracts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ActorRef}.
 */
@DisplayName("ActorRef")
class ActorRefTest {

    @Test
    @DisplayName("user() creates USER type actor with given principalId")
    void shouldCreateUserActor() {
        ActorRef actor = ActorRef.user("user-42");
        assertThat(actor.getPrincipalId()).isEqualTo("user-42");
        assertThat(actor.getType()).isEqualTo(ActorRef.ActorType.USER);
    }

    @Test
    @DisplayName("agent() creates AGENT type actor with given agentId")
    void shouldCreateAgentActor() {
        ActorRef actor = ActorRef.agent("ai-agent-1");
        assertThat(actor.getPrincipalId()).isEqualTo("ai-agent-1");
        assertThat(actor.getType()).isEqualTo(ActorRef.ActorType.AGENT);
    }

    @Test
    @DisplayName("SYSTEM sentinel has SYSTEM type and 'system' as principalId")
    void shouldHaveSystemSentinel() {
        assertThat(ActorRef.SYSTEM.getType()).isEqualTo(ActorRef.ActorType.SYSTEM);
        assertThat(ActorRef.SYSTEM.getPrincipalId()).isEqualTo("system");
    }

    @Test
    @DisplayName("user() rejects null principalId")
    void shouldRejectNullPrincipal() {
        assertThatNullPointerException()
            .isThrownBy(() -> ActorRef.user(null));
    }

    @Test
    @DisplayName("user() rejects blank principalId")
    void shouldRejectBlankPrincipal() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ActorRef.user("  "));
    }

    @Test
    @DisplayName("equals and hashCode are value-based on principalId + type")
    void shouldHaveValueBasedEquality() {
        ActorRef a = ActorRef.user("user-1");
        ActorRef b = ActorRef.user("user-1");
        ActorRef c = ActorRef.agent("user-1");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c); // same ID, different type
    }
}
