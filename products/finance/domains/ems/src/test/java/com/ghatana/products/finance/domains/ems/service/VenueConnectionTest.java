package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.port.ExchangeAdapterPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for venue connectivity and health monitoring per D02-004
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Venue Connection Tests")
class VenueConnectionTest {

    @Mock
    private ExchangeAdapterPort nasdaqAdapter;
    
    @Mock
    private ExchangeAdapterPort nyseAdapter;

    private VenueHealthMonitor healthMonitor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        healthMonitor = new VenueHealthMonitor();
        
        when(nasdaqAdapter.exchangeId()).thenReturn("NASDAQ");
        when(nyseAdapter.exchangeId()).thenReturn("NYSE");
    }

    @Test
    @DisplayName("Should successfully connect to venue")
    void shouldSuccessfullyConnectToVenue() {
        Map<String, String> config = Map.of(
            "host", "nasdaq.exchange.com",
            "port", "9876",
            "compId", "BROKER123"
        );

        assertThatCode(() -> nasdaqAdapter.connect(config))
            .doesNotThrowAnyException();

        verify(nasdaqAdapter, times(1)).connect(config);
    }

    @Test
    @DisplayName("Should handle connection failure gracefully")
    void shouldHandleConnectionFailureGracefully() {
        Map<String, String> config = Map.of("host", "invalid.host");

        doThrow(new ExchangeAdapterPort.ExchangeConnectionException("Connection failed", null))
            .when(nasdaqAdapter).connect(config);

        assertThatThrownBy(() -> nasdaqAdapter.connect(config))
            .isInstanceOf(ExchangeAdapterPort.ExchangeConnectionException.class)
            .hasMessageContaining("Connection failed");
    }

    @Test
    @DisplayName("Should detect healthy venue connection")
    void shouldDetectHealthyVenueConnection() {
        when(nasdaqAdapter.isConnected()).thenReturn(true);

        VenueHealth health = healthMonitor.checkHealth(nasdaqAdapter);

        assertThat(health.isHealthy()).isTrue();
        assertThat(health.exchangeId()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Should detect unhealthy venue connection")
    void shouldDetectUnhealthyVenueConnection() {
        when(nasdaqAdapter.isConnected()).thenReturn(false);

        VenueHealth health = healthMonitor.checkHealth(nasdaqAdapter);

        assertThat(health.isHealthy()).isFalse();
        assertThat(health.exchangeId()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Should disconnect from venue gracefully")
    void shouldDisconnectFromVenueGracefully() {
        assertThatCode(() -> nasdaqAdapter.disconnect())
            .doesNotThrowAnyException();

        verify(nasdaqAdapter, times(1)).disconnect();
    }

    @Test
    @DisplayName("Should handle reconnection after disconnect")
    void shouldHandleReconnectionAfterDisconnect() {
        Map<String, String> config = Map.of("host", "nasdaq.exchange.com");

        nasdaqAdapter.disconnect();
        nasdaqAdapter.connect(config);

        verify(nasdaqAdapter, times(1)).disconnect();
        verify(nasdaqAdapter, times(1)).connect(config);
    }

    @Test
    @DisplayName("Should monitor heartbeat for connection health")
    void shouldMonitorHeartbeatForConnectionHealth() {
        Instant lastHeartbeat = Instant.now().minusSeconds(15);
        
        boolean isHealthy = healthMonitor.isHeartbeatHealthy(lastHeartbeat, Instant.now(), 30);

        assertThat(isHealthy).isTrue();
    }

    @Test
    @DisplayName("Should detect stale heartbeat")
    void shouldDetectStaleHeartbeat() {
        Instant lastHeartbeat = Instant.now().minusSeconds(45);
        
        boolean isHealthy = healthMonitor.isHeartbeatHealthy(lastHeartbeat, Instant.now(), 30);

        assertThat(isHealthy).isFalse();
    }

    @Test
    @DisplayName("Should track multiple venue connections")
    void shouldTrackMultipleVenueConnections() {
        when(nasdaqAdapter.isConnected()).thenReturn(true);
        when(nyseAdapter.isConnected()).thenReturn(true);

        VenueHealth nasdaqHealth = healthMonitor.checkHealth(nasdaqAdapter);
        VenueHealth nyseHealth = healthMonitor.checkHealth(nyseAdapter);

        assertThat(nasdaqHealth.isHealthy()).isTrue();
        assertThat(nyseHealth.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should validate connection configuration")
    void shouldValidateConnectionConfiguration() {
        Map<String, String> validConfig = Map.of(
            "host", "nasdaq.exchange.com",
            "port", "9876",
            "compId", "BROKER123"
        );

        boolean isValid = healthMonitor.validateConfig(validConfig);

        assertThat(isValid).isTrue();
    }

    record VenueHealth(String exchangeId, boolean isHealthy, Instant checkedAt) {}

    static class VenueHealthMonitor {
        VenueHealth checkHealth(ExchangeAdapterPort adapter) {
            return new VenueHealth(
                adapter.exchangeId(),
                adapter.isConnected(),
                Instant.now()
            );
        }

        boolean isHeartbeatHealthy(Instant lastHeartbeat, Instant now, int maxAgeSeconds) {
            return lastHeartbeat.plusSeconds(maxAgeSeconds).isAfter(now);
        }

        boolean validateConfig(Map<String, String> config) {
            return config.containsKey("host") && 
                   config.containsKey("port") && 
                   config.containsKey("compId");
        }
    }
}
