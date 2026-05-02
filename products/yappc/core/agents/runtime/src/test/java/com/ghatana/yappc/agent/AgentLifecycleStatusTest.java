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
@DisplayName("AgentLifecycleStatus Tests")
class AgentLifecycleStatusTest {

  @Test
  @DisplayName("should have all expected enum values")
  void shouldHaveAllExpectedValues() { 
    assertThat(AgentLifecycleStatus.values()).containsExactly( 
        AgentLifecycleStatus.REGISTERED,
        AgentLifecycleStatus.INITIALIZING,
        AgentLifecycleStatus.READY,
        AgentLifecycleStatus.FAILED,
        AgentLifecycleStatus.STOPPING,
        AgentLifecycleStatus.STOPPED
    );
  }

  @Test
  @DisplayName("should return correct enum by name")
  void shouldReturnCorrectEnumByName() { 
    assertThat(AgentLifecycleStatus.valueOf("REGISTERED")).isEqualTo(AgentLifecycleStatus.REGISTERED);
    assertThat(AgentLifecycleStatus.valueOf("INITIALIZING")).isEqualTo(AgentLifecycleStatus.INITIALIZING);
    assertThat(AgentLifecycleStatus.valueOf("READY")).isEqualTo(AgentLifecycleStatus.READY);
    assertThat(AgentLifecycleStatus.valueOf("FAILED")).isEqualTo(AgentLifecycleStatus.FAILED);
    assertThat(AgentLifecycleStatus.valueOf("STOPPING")).isEqualTo(AgentLifecycleStatus.STOPPING);
    assertThat(AgentLifecycleStatus.valueOf("STOPPED")).isEqualTo(AgentLifecycleStatus.STOPPED);
  }

  @Test
  @DisplayName("should have correct ordinal values")
  void shouldHaveCorrectOrdinals() { 
    assertThat(AgentLifecycleStatus.REGISTERED.ordinal()).isZero(); 
    assertThat(AgentLifecycleStatus.INITIALIZING.ordinal()).isEqualTo(1); 
    assertThat(AgentLifecycleStatus.READY.ordinal()).isEqualTo(2); 
    assertThat(AgentLifecycleStatus.FAILED.ordinal()).isEqualTo(3); 
    assertThat(AgentLifecycleStatus.STOPPING.ordinal()).isEqualTo(4); 
    assertThat(AgentLifecycleStatus.STOPPED.ordinal()).isEqualTo(5); 
  }
}
