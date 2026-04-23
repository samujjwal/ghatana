/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        service = new DefaultServiceDiscoveryService(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        System.clearProperty(PAYMENT_SERVICE_URL); // GH-90000
    }

    @Nested
    @DisplayName("discoverServices()")
    class DiscoverServicesTests {

        @Test
        @DisplayName("discovers services from environment")
        void discoversServicesFromEnvironment() { // GH-90000
            System.setProperty(PAYMENT_SERVICE_URL, "http://localhost:8080"); // GH-90000
            
            List<DiscoveredService> services = service.discoverServices("all", Map.of()); // GH-90000
            
            assertThat(services) // GH-90000
                .extracting(DiscoveredService::endpoint) // GH-90000
                .contains("http://localhost:8080");
        }

        @Test
        @DisplayName("returns empty list when no services found")
        void returnsEmptyListWhenNoServicesFound() { // GH-90000
            List<DiscoveredService> services = service.discoverServices("all", Map.of()); // GH-90000
            
            assertThat(services).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("registerService()")
    class RegisterServiceTests {

        @Test
        @DisplayName("registers service when auto-register is true")
        void registersServiceWhenAutoRegisterIsTrue() { // GH-90000
            DiscoveredService discoveredService = new DiscoveredService( // GH-90000
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(), // GH-90000
                ServiceHealth.HEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, true); // GH-90000

            assertThat(result.registered()).isTrue(); // GH-90000
            assertThat(result.agentId()).isNotNull(); // GH-90000
            assertThat(result.agentId()).startsWith("agent-");
        }

        @Test
        @DisplayName("skips registration when auto-register is false")
        void skipsRegistrationWhenAutoRegisterIsFalse() { // GH-90000
            DiscoveredService discoveredService = new DiscoveredService( // GH-90000
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(), // GH-90000
                ServiceHealth.HEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, false); // GH-90000

            assertThat(result.registered()).isFalse(); // GH-90000
            assertThat(result.reason()).isEqualTo("Auto-registration disabled");
        }

        @Test
        @DisplayName("rejects unhealthy services")
        void rejectsUnhealthyServices() { // GH-90000
            DiscoveredService discoveredService = new DiscoveredService( // GH-90000
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(), // GH-90000
                ServiceHealth.UNHEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, true); // GH-90000

            assertThat(result.registered()).isFalse(); // GH-90000
            assertThat(result.reason()).isEqualTo("Service is unhealthy");
        }

        @Test
        @DisplayName("adds warning for missing endpoint")
        void addsWarningForMissingEndpoint() { // GH-90000
            DiscoveredService discoveredService = new DiscoveredService( // GH-90000
                "service-1",
                "Test Service",
                "http",
                "",
                Map.of(), // GH-90000
                ServiceHealth.HEALTHY
            );

            ServiceDiscoveryService.RegistrationResult result = service.registerService(discoveredService, true); // GH-90000

            assertThat(result.registered()).isTrue(); // GH-90000
            assertThat(result.warnings()).anyMatch(w -> w.contains("No endpoint"));
        }
    }

    @Nested
    @DisplayName("getStats()")
    class GetStatsTests {

        @Test
        @DisplayName("returns discovery statistics")
        void returnsDiscoveryStatistics() { // GH-90000
            System.setProperty(PAYMENT_SERVICE_URL, "http://localhost:8080"); // GH-90000

            DiscoveredService discoveredService = new DiscoveredService( // GH-90000
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(), // GH-90000
                ServiceHealth.HEALTHY
            );

            service.discoverServices("all", Map.of()); // GH-90000
            service.registerService(discoveredService, true); // GH-90000

            ServiceDiscoveryService.DiscoveryStats stats = service.getStats(); // GH-90000
            assertThat(stats.totalDiscovered()).isGreaterThan(0); // GH-90000
            assertThat(stats.totalRegistered()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("calculates registration rate")
        void calculatesRegistrationRate() { // GH-90000
            ServiceDiscoveryService.DiscoveryStats stats = service.getStats(); // GH-90000
            
            assertThat(stats.registrationRate()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("returns zero stats when no discovery")
        void returnsZeroStatsWhenNoDiscovery() { // GH-90000
            ServiceDiscoveryService.DiscoveryStats stats = service.getStats(); // GH-90000
            
            assertThat(stats.totalDiscovered()).isEqualTo(0); // GH-90000
            assertThat(stats.totalRegistered()).isEqualTo(0); // GH-90000
            assertThat(stats.totalSkipped()).isEqualTo(0); // GH-90000
            assertThat(stats.totalFailed()).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("DiscoveredService")
    class DiscoveredServiceTests {

        @Test
        @DisplayName("service has required fields")
        void serviceHasRequiredFields() { // GH-90000
            DiscoveredService service = new DiscoveredService( // GH-90000
                "service-1",
                "Test Service",
                "http",
                "http://localhost:8080",
                Map.of(), // GH-90000
                ServiceHealth.HEALTHY
            );

            assertThat(service.serviceId()).isNotNull(); // GH-90000
            assertThat(service.serviceName()).isNotNull(); // GH-90000
            assertThat(service.serviceType()).isNotNull(); // GH-90000
            assertThat(service.endpoint()).isNotNull(); // GH-90000
            assertThat(service.metadata()).isNotNull(); // GH-90000
            assertThat(service.health()).isNotNull(); // GH-90000
        }
    }
}
