/*
 * Copyright (c) 2025 Ghatana // GH-90000
 */
package com.ghatana.services.auth;

import io.activej.eventloop.Eventloop;
import io.activej.inject.Injector;
import io.activej.inject.module.Module;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AuthGatewayLauncher migration to ServiceLauncher.
 *
 * <p>Verifies that the launcher correctly extends ServiceLauncher and provides
 * the required bindings through ServiceCommonModule and ObservabilityModule.
 *
 * @doc.type class
 * @doc.purpose Integration tests for AuthGatewayLauncher ServiceLauncher migration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AuthGatewayLauncher ServiceLauncher Migration Tests")
@Tag("integration")
class AuthGatewayLauncherIntegrationTest {

    /**
     * Test that verifies AuthGatewayLauncher extends ServiceLauncher.
     */
    @Test
    @DisplayName("AuthGatewayLauncher extends ServiceLauncher")
    void authGatewayLauncherExtendsServiceLauncher() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        assertThat(launcher).isInstanceOf(com.ghatana.core.activej.launcher.ServiceLauncher.class); // GH-90000
    }

    /**
     * Test that verifies createModule() returns a valid module. // GH-90000
     */
    @Test
    @DisplayName("createModule() returns valid module")
    void createModuleReturnsValidModule() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        assertThat(module).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides Eventloop binding.
     */
    @Test
    @DisplayName("Module provides Eventloop binding")
    void moduleProvidesEventloopBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify Eventloop is provided
        Eventloop eventloop = injector.getInstance(Eventloop.class); // GH-90000
        assertThat(eventloop).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides MeterRegistry binding.
     */
    @Test
    @DisplayName("Module provides MeterRegistry binding")
    void moduleProvidesMeterRegistryBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify MeterRegistry is provided
        MeterRegistry meterRegistry = injector.getInstance(MeterRegistry.class); // GH-90000
        assertThat(meterRegistry).isNotNull(); // GH-90000
        assertThat(meterRegistry).isInstanceOf(SimpleMeterRegistry.class); // GH-90000
    }

    /**
     * Test that verifies the module provides MetricsCollector binding.
     */
    @Test
    @DisplayName("Module provides MetricsCollector binding")
    void moduleProvidesMetricsCollectorBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify MetricsCollector is provided
        com.ghatana.platform.observability.MetricsCollector metricsCollector = 
            injector.getInstance(com.ghatana.platform.observability.MetricsCollector.class); // GH-90000
        assertThat(metricsCollector).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides ConfigManager binding.
     */
    @Test
    @DisplayName("Module provides ConfigManager binding")
    void moduleProvidesConfigManagerBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify ConfigManager is provided
        com.ghatana.platform.config.ConfigManager configManager = 
            injector.getInstance(com.ghatana.platform.config.ConfigManager.class); // GH-90000
        assertThat(configManager).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides JwtTokenProvider binding.
     */
    @Test
    @DisplayName("Module provides JwtTokenProvider binding")
    void moduleProvidesJwtTokenProviderBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify JwtTokenProvider is provided
        com.ghatana.platform.security.port.JwtTokenProvider tokenProvider = 
            injector.getInstance(com.ghatana.platform.security.port.JwtTokenProvider.class); // GH-90000
        assertThat(tokenProvider).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides CredentialStore binding.
     */
    @Test
    @DisplayName("Module provides CredentialStore binding")
    void moduleProvidesCredentialStoreBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify CredentialStore is provided
        CredentialStore credentialStore = injector.getInstance(CredentialStore.class); // GH-90000
        assertThat(credentialStore).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides TenantExtractor binding.
     */
    @Test
    @DisplayName("Module provides TenantExtractor binding")
    void moduleProvidesTenantExtractorBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify TenantExtractor is provided
        TenantExtractor tenantExtractor = injector.getInstance(TenantExtractor.class); // GH-90000
        assertThat(tenantExtractor).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides RateLimiter binding.
     */
    @Test
    @DisplayName("Module provides RateLimiter binding")
    void moduleProvidesRateLimiterBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify RateLimiter is provided
        com.ghatana.platform.security.ratelimit.RateLimiter rateLimiter = 
            injector.getInstance(com.ghatana.platform.security.ratelimit.RateLimiter.class); // GH-90000
        assertThat(rateLimiter).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides RoutingServlet binding.
     */
    @Test
    @DisplayName("Module provides RoutingServlet binding")
    void moduleProvidesRoutingServletBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify RoutingServlet is provided
        io.activej.http.RoutingServlet servlet = injector.getInstance(io.activej.http.RoutingServlet.class); // GH-90000
        assertThat(servlet).isNotNull(); // GH-90000
    }

    /**
     * Test that verifies the module provides HttpServer binding.
     */
    @Test
    @DisplayName("Module provides HttpServer binding")
    void moduleProvidesHttpServerBinding() { // GH-90000
        AuthGatewayLauncher launcher = new AuthGatewayLauncher(); // GH-90000
        Module module = launcher.createModule(); // GH-90000
        
        // Create injector from the module
        Injector injector = Injector.of(module); // GH-90000
        
        // Verify HttpServer is provided
        io.activej.http.HttpServer httpServer = injector.getInstance(io.activej.http.HttpServer.class); // GH-90000
        assertThat(httpServer).isNotNull(); // GH-90000
    }
}
