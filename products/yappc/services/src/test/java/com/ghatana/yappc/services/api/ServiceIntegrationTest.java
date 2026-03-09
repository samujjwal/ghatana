package com.ghatana.yappc.services.api;

import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import com.ghatana.yappc.services.domain.DomainServiceFacade;
import com.ghatana.yappc.services.infrastructure.InfrastructureServiceFacade;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.activej.promise.Promise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the unified YAPPC service layer.
 *
 * <p>Uses {@link EventloopTestBase} as required by Golden Rule #5:
 * "ALL async Java tests MUST use EventloopTestBase."</p>
 *
 * @doc.type class
 * @doc.purpose Integration test for unified YAPPC service layer
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("YAPPC Service Integration Tests")
class ServiceIntegrationTest extends EventloopTestBase {

    @Nested
    @DisplayName("Domain Service")
    class DomainServiceTests {

        @Test
        @DisplayName("should return OK from health check")
        void shouldReturnOkFromHealthCheck() {
            // GIVEN
            DomainServiceFacade domainService = new DomainServiceFacade(
                    mock(IntentService.class), mock(ShapeService.class));

            // WHEN
            String health = runPromise(domainService::healthCheck);

            // THEN
            assertThat(health).isEqualTo("OK");
        }

        @Test
        @DisplayName("should return zero entity count when empty")
        void shouldReturnZeroEntityCount() {
            // GIVEN
            IntentService intentService = mock(IntentService.class);
            ShapeService shapeService = mock(ShapeService.class);
            when(intentService.count()).thenReturn(Promise.of(0L));
            when(shapeService.count()).thenReturn(Promise.of(0L));
            DomainServiceFacade domainService = new DomainServiceFacade(
                    intentService, shapeService);

            // WHEN
            Long count = runPromise(domainService::entityCount);

            // THEN
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("Infrastructure Service")
    class InfrastructureServiceTests {

        @Test
        @DisplayName("should return OK from health check")
        void shouldReturnOkFromHealthCheck() {
            // GIVEN
            InfrastructureServiceFacade infraService = new InfrastructureServiceFacade(
                    new SecurityServiceAdapter());

            // WHEN
            String health = runPromise(infraService::healthCheck);

            // THEN
            assertThat(health).isEqualTo("OK");
        }

        @Test
        @DisplayName("should report database as reachable")
        void shouldReportDatabaseAsReachable() {
            // GIVEN
            InfrastructureServiceFacade infraService = new InfrastructureServiceFacade(
                    new SecurityServiceAdapter());

            // WHEN
            Boolean reachable = runPromise(infraService::isDatabaseReachable);

            // THEN
            assertThat(reachable).isTrue();
        }
    }

    @Nested
    @DisplayName("Plugin Registry")
    class PluginRegistryTests {

        @Test
        @DisplayName("should start with empty registry")
        void shouldStartWithEmptyRegistry() {
            // GIVEN / WHEN
            PluginRegistry registry = new PluginRegistry();

            // THEN
            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("should aggregate health for empty registry")
        void shouldAggregateHealthForEmptyRegistry() {
            // GIVEN
            PluginRegistry registry = new PluginRegistry();

            // WHEN
            var health = runPromise(registry::aggregateHealth);

            // THEN
            assertThat(health.healthy()).isTrue();
        }
    }
}
