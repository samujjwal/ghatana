/*
 * Copyright (c) 2025 Ghatana
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
    void authGatewayLauncherExtendsServiceLauncher() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        assertThat(launcher).isInstanceOf(com.ghatana.core.activej.launcher.ServiceLauncher.class);
    }

    /**
     * Test that verifies createModule() returns a valid module.
     */
    @Test
    @DisplayName("createModule() returns valid module")
    void createModuleReturnsValidModule() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        assertThat(module).isNotNull();
    }

    /**
     * Test that verifies the module provides Eventloop binding.
     */
    @Test
    @DisplayName("Module provides Eventloop binding")
    void moduleProvidesEventloopBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify Eventloop is provided
        Eventloop eventloop = injector.getInstance(Eventloop.class);
        assertThat(eventloop).isNotNull();
    }

    /**
     * Test that verifies the module provides MeterRegistry binding.
     */
    @Test
    @DisplayName("Module provides MeterRegistry binding")
    void moduleProvidesMeterRegistryBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify MeterRegistry is provided
        MeterRegistry meterRegistry = injector.getInstance(MeterRegistry.class);
        assertThat(meterRegistry).isNotNull();
        assertThat(meterRegistry).isInstanceOf(SimpleMeterRegistry.class);
    }

    /**
     * Test that verifies the module provides MetricsCollector binding.
     */
    @Test
    @DisplayName("Module provides MetricsCollector binding")
    void moduleProvidesMetricsCollectorBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify MetricsCollector is provided
        com.ghatana.platform.observability.MetricsCollector metricsCollector = 
            injector.getInstance(com.ghatana.platform.observability.MetricsCollector.class);
        assertThat(metricsCollector).isNotNull();
    }

    /**
     * Test that verifies the module provides ConfigManager binding.
     */
    @Test
    @DisplayName("Module provides ConfigManager binding")
    void moduleProvidesConfigManagerBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify ConfigManager is provided
        com.ghatana.platform.config.ConfigManager configManager = 
            injector.getInstance(com.ghatana.platform.config.ConfigManager.class);
        assertThat(configManager).isNotNull();
    }

    /**
     * Test that verifies the module provides JwtTokenProvider binding.
     */
    @Test
    @DisplayName("Module provides JwtTokenProvider binding")
    void moduleProvidesJwtTokenProviderBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify JwtTokenProvider is provided
        com.ghatana.platform.security.port.JwtTokenProvider tokenProvider = 
            injector.getInstance(com.ghatana.platform.security.port.JwtTokenProvider.class);
        assertThat(tokenProvider).isNotNull();
    }

    /**
     * Test that verifies the module provides CredentialStore binding.
     */
    @Test
    @DisplayName("Module provides CredentialStore binding")
    void moduleProvidesCredentialStoreBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify CredentialStore is provided
        CredentialStore credentialStore = injector.getInstance(CredentialStore.class);
        assertThat(credentialStore).isNotNull();
    }

    /**
     * Test that verifies the module provides TenantExtractor binding.
     */
    @Test
    @DisplayName("Module provides TenantExtractor binding")
    void moduleProvidesTenantExtractorBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify TenantExtractor is provided
        TenantExtractor tenantExtractor = injector.getInstance(TenantExtractor.class);
        assertThat(tenantExtractor).isNotNull();
    }

    /**
     * Test that verifies the module provides RateLimiter binding.
     */
    @Test
    @DisplayName("Module provides RateLimiter binding")
    void moduleProvidesRateLimiterBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify RateLimiter is provided
        com.ghatana.platform.security.ratelimit.RateLimiter rateLimiter = 
            injector.getInstance(com.ghatana.platform.security.ratelimit.RateLimiter.class);
        assertThat(rateLimiter).isNotNull();
    }

    /**
     * Test that verifies the module provides RoutingServlet binding.
     */
    @Test
    @DisplayName("Module provides RoutingServlet binding")
    void moduleProvidesRoutingServletBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify RoutingServlet is provided
        io.activej.http.RoutingServlet servlet = injector.getInstance(io.activej.http.RoutingServlet.class);
        assertThat(servlet).isNotNull();
    }

    /**
     * Test that verifies the module provides HttpServer binding.
     */
    @Test
    @DisplayName("Module provides HttpServer binding")
    void moduleProvidesHttpServerBinding() {
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        Module module = launcher.createModule();
        
        // Create injector from the module
        Injector injector = Injector.of(module);
        
        // Verify HttpServer is provided
        io.activej.http.HttpServer httpServer = injector.getInstance(io.activej.http.HttpServer.class);
        assertThat(httpServer).isNotNull();
    }
}
