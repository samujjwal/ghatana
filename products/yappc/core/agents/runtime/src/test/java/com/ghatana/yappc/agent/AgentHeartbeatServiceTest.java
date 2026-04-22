package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentHeartbeatService}.
 *
 * @doc.type class
 * @doc.purpose Verify AgentHeartbeatService lifecycle and behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("AgentHeartbeatService Tests [GH-90000]")
class AgentHeartbeatServiceTest {

  @Test
  @DisplayName("should create service with default interval [GH-90000]")
  void shouldCreateWithDefaultInterval() { // GH-90000
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); // GH-90000
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); // GH-90000

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop); // GH-90000

    assertThat(service).isNotNull(); // GH-90000
    assertThat(service.isRunning()).isFalse(); // GH-90000
    assertThat(service.getLastHeartbeat()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should create service with custom interval [GH-90000]")
  void shouldCreateWithCustomInterval() { // GH-90000
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); // GH-90000
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); // GH-90000

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop, 5000L); // GH-90000

    assertThat(service).isNotNull(); // GH-90000
    assertThat(service.isRunning()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("should reject non-positive interval [GH-90000]")
  void shouldRejectNonPositiveInterval() { // GH-90000
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); // GH-90000
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); // GH-90000

    assertThatThrownBy(() -> new AgentHeartbeatService(mockProvider, mockEventloop, 0L)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("heartbeatIntervalMs must be positive [GH-90000]");

    assertThatThrownBy(() -> new AgentHeartbeatService(mockProvider, mockEventloop, -100L)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("heartbeatIntervalMs must be positive [GH-90000]");
  }

  @Test
  @DisplayName("should have correct default interval constant [GH-90000]")
  void shouldHaveCorrectDefaultInterval() { // GH-90000
    assertThat(AgentHeartbeatService.DEFAULT_INTERVAL_MS).isEqualTo(30_000L); // GH-90000
  }

  @Test
  @DisplayName("should return running state [GH-90000]")
  void shouldReturnRunningState() { // GH-90000
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); // GH-90000
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); // GH-90000

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop); // GH-90000

    assertThat(service.isRunning()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("should return null lastHeartbeat before start [GH-90000]")
  void shouldReturnNullLastHeartbeatBeforeStart() { // GH-90000
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); // GH-90000
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); // GH-90000

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop); // GH-90000

    assertThat(service.getLastHeartbeat()).isNull(); // GH-90000
  }
}
