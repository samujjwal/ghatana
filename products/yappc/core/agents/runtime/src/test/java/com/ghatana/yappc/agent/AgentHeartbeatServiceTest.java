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
@DisplayName("AgentHeartbeatService Tests")
class AgentHeartbeatServiceTest {

  @Test
  @DisplayName("should create service with default interval")
  void shouldCreateWithDefaultInterval() { 
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); 
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); 

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop); 

    assertThat(service).isNotNull(); 
    assertThat(service.isRunning()).isFalse(); 
    assertThat(service.getLastHeartbeat()).isNull(); 
  }

  @Test
  @DisplayName("should create service with custom interval")
  void shouldCreateWithCustomInterval() { 
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); 
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); 

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop, 5000L); 

    assertThat(service).isNotNull(); 
    assertThat(service.isRunning()).isFalse(); 
  }

  @Test
  @DisplayName("should reject non-positive interval")
  void shouldRejectNonPositiveInterval() { 
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); 
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); 

    assertThatThrownBy(() -> new AgentHeartbeatService(mockProvider, mockEventloop, 0L)) 
        .isInstanceOf(IllegalArgumentException.class) 
        .hasMessageContaining("heartbeatIntervalMs must be positive");

    assertThatThrownBy(() -> new AgentHeartbeatService(mockProvider, mockEventloop, -100L)) 
        .isInstanceOf(IllegalArgumentException.class) 
        .hasMessageContaining("heartbeatIntervalMs must be positive");
  }

  @Test
  @DisplayName("should have correct default interval constant")
  void shouldHaveCorrectDefaultInterval() { 
    assertThat(AgentHeartbeatService.DEFAULT_INTERVAL_MS).isEqualTo(30_000L); 
  }

  @Test
  @DisplayName("should return running state")
  void shouldReturnRunningState() { 
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); 
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); 

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop); 

    assertThat(service.isRunning()).isFalse(); 
  }

  @Test
  @DisplayName("should return null lastHeartbeat before start")
  void shouldReturnNullLastHeartbeatBeforeStart() { 
    AgentHealthProvider mockProvider = mock(AgentHealthProvider.class); 
    io.activej.eventloop.Eventloop mockEventloop = mock(io.activej.eventloop.Eventloop.class); 

    AgentHeartbeatService service = new AgentHeartbeatService(mockProvider, mockEventloop); 

    assertThat(service.getLastHeartbeat()).isNull(); 
  }
}
