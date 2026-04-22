package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentLifecycleStatus}.
 *
 * @doc.type class
 * @doc.purpose Verify AgentLifecycleStatus enum values
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("AgentLifecycleStatus Tests [GH-90000]")
class AgentLifecycleStatusTest {

  @Test
  @DisplayName("should have all expected enum values [GH-90000]")
  void shouldHaveAllExpectedValues() { // GH-90000
    assertThat(AgentLifecycleStatus.values()).containsExactly( // GH-90000
        AgentLifecycleStatus.REGISTERED,
        AgentLifecycleStatus.INITIALIZING,
        AgentLifecycleStatus.READY,
        AgentLifecycleStatus.FAILED,
        AgentLifecycleStatus.STOPPING,
        AgentLifecycleStatus.STOPPED
    );
  }

  @Test
  @DisplayName("should return correct enum by name [GH-90000]")
  void shouldReturnCorrectEnumByName() { // GH-90000
    assertThat(AgentLifecycleStatus.valueOf("REGISTERED [GH-90000]")).isEqualTo(AgentLifecycleStatus.REGISTERED);
    assertThat(AgentLifecycleStatus.valueOf("INITIALIZING [GH-90000]")).isEqualTo(AgentLifecycleStatus.INITIALIZING);
    assertThat(AgentLifecycleStatus.valueOf("READY [GH-90000]")).isEqualTo(AgentLifecycleStatus.READY);
    assertThat(AgentLifecycleStatus.valueOf("FAILED [GH-90000]")).isEqualTo(AgentLifecycleStatus.FAILED);
    assertThat(AgentLifecycleStatus.valueOf("STOPPING [GH-90000]")).isEqualTo(AgentLifecycleStatus.STOPPING);
    assertThat(AgentLifecycleStatus.valueOf("STOPPED [GH-90000]")).isEqualTo(AgentLifecycleStatus.STOPPED);
  }

  @Test
  @DisplayName("should have correct ordinal values [GH-90000]")
  void shouldHaveCorrectOrdinals() { // GH-90000
    assertThat(AgentLifecycleStatus.REGISTERED.ordinal()).isZero(); // GH-90000
    assertThat(AgentLifecycleStatus.INITIALIZING.ordinal()).isEqualTo(1); // GH-90000
    assertThat(AgentLifecycleStatus.READY.ordinal()).isEqualTo(2); // GH-90000
    assertThat(AgentLifecycleStatus.FAILED.ordinal()).isEqualTo(3); // GH-90000
    assertThat(AgentLifecycleStatus.STOPPING.ordinal()).isEqualTo(4); // GH-90000
    assertThat(AgentLifecycleStatus.STOPPED.ordinal()).isEqualTo(5); // GH-90000
  }
}
