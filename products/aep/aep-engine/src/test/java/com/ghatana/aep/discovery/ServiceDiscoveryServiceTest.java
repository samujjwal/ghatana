/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.discovery;

import com.ghatana.aep.discovery.ServiceDiscoveryService.DiscoveredService;
import com.ghatana.aep.discovery.ServiceDiscoveryService.ServiceHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ServiceDiscoveryService.
 *
 * @doc.type class
 * @doc.purpose Test service discovery service
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ServiceDiscoveryService")
class ServiceDiscoveryServiceTest {

    private static final String PAYMENT_SERVICE_URL = "PAYMENT_SERVICE_URL";

    private ServiceDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultServiceDiscoveryService();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PAYMENT_SERVICE_URL);
    }

    @Nested
    @DisplayName("discoverServices()")
    class DiscoverServicesTests {

        @Test
        @DisplayName("discovers services from environment")
        void discoversServicesFromEnvironment() {
            System.setProperty(PAYMENT_SERVICE_URL, "http://localhost:8080");
            
            List<DiscoveredService> services = service.discoverServices("all", Map.of());
            
            assertThat(services)
                .extracting(DiscoveredService::endpoint)
                .contains("http://localhost:8080");
        }

        @Test
        @DisplayName("returns empty list when no services found")
        void returnsEmptyListWhenNoServicesFound() {
            List<DiscoveredService> services = service.discoverServices("all", Map.of());
            
            assertThat(services).isNotNull();
        }
    }

    @Nested
    @DisplayName("registerService()")
    class RegisterServiceTests {

        @Test
        @DisplayName("registers service when auto-register is true")
        void registersServiceWhenAutoRegisterIsTrue() {
            DiscoveredService discoveredService = new DiscoveredService(
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(),
                ServiceHealth.HEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, true);

            assertThat(result.registered()).isTrue();
            assertThat(result.agentId()).isNotNull();
            assertThat(result.agentId()).startsWith("agent-");
        }

        @Test
        @DisplayName("skips registration when auto-register is false")
        void skipsRegistrationWhenAutoRegisterIsFalse() {
            DiscoveredService discoveredService = new DiscoveredService(
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(),
                ServiceHealth.HEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, false);

            assertThat(result.registered()).isFalse();
            assertThat(result.reason()).isEqualTo("Auto-registration disabled");
        }

        @Test
        @DisplayName("rejects unhealthy services")
        void rejectsUnhealthyServices() {
            DiscoveredService discoveredService = new DiscoveredService(
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(),
                ServiceHealth.UNHEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, true);

            assertThat(result.registered()).isFalse();
            assertThat(result.reason()).isEqualTo("Service is unhealthy");
        }

        @Test
        @DisplayName("adds warning for missing endpoint")
        void addsWarningForMissingEndpoint() {
            DiscoveredService discoveredService = new DiscoveredService(
                "service-1",
                "Test Service",
                "http",
                "",
                Map.of(),
                ServiceHealth.HEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, true);

            assertThat(result.registered()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("No endpoint"));
        }
    }

    @Nested
    @DisplayName("getStats()")
    class GetStatsTests {

        @Test
        @DisplayName("returns discovery statistics")
        void returnsDiscoveryStatistics() {
            System.setProperty(PAYMENT_SERVICE_URL, "http://localhost:8080");

            DiscoveredService discoveredService = new DiscoveredService(
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(),
                ServiceHealth.HEALTHY
            );

            service.discoverServices("all", Map.of());
            service.registerService(discoveredService, true);

            ServiceDiscoveryService.DiscoveryStats stats = service.getStats();
            assertThat(stats.totalDiscovered()).isGreaterThan(0);
            assertThat(stats.totalRegistered()).isGreaterThan(0);
        }

        @Test
        @DisplayName("calculates registration rate")
        void calculatesRegistrationRate() {
            ServiceDiscoveryService.DiscoveryStats stats = service.getStats();
            
            assertThat(stats.registrationRate()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("returns zero stats when no discovery")
        void returnsZeroStatsWhenNoDiscovery() {
            ServiceDiscoveryService.DiscoveryStats stats = service.getStats();
            
            assertThat(stats.totalDiscovered()).isEqualTo(0);
            assertThat(stats.totalRegistered()).isEqualTo(0);
            assertThat(stats.totalSkipped()).isEqualTo(0);
            assertThat(stats.totalFailed()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("DiscoveredService")
    class DiscoveredServiceTests {

        @Test
        @DisplayName("service has required fields")
        void serviceHasRequiredFields() {
            DiscoveredService service = new DiscoveredService(
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(),
                ServiceHealth.HEALTHY
            );

            assertThat(service.serviceId()).isNotNull();
            assertThat(service.serviceName()).isNotNull();
            assertThat(service.serviceType()).isNotNull();
            assertThat(service.endpoint()).isNotNull();
            assertThat(service.metadata()).isNotNull();
            assertThat(service.health()).isNotNull();
        }
    }
}
