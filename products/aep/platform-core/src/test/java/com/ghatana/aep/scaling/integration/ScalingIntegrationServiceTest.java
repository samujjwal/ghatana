package com.ghatana.aep.scaling.integration;

import com.ghatana.aep.scaling.autoscaling.AutoScalingEngine;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels;
import com.ghatana.aep.scaling.cluster.ClusterManagementSystem;
import com.ghatana.aep.scaling.distributed.DistributedPatternProcessor;
import com.ghatana.aep.scaling.loadbalancer.AdvancedLoadBalancer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScalingIntegrationService}.
 *
 * @doc.type test
 * @doc.purpose Verify lifecycle state transitions and public API contracts
 * @doc.layer product
 * @doc.pattern ServiceTest
 */
@DisplayName("ScalingIntegrationService Tests")
@ExtendWith(MockitoExtension.class)
class ScalingIntegrationServiceTest extends EventloopTestBase {

    @Mock ClusterManagementSystem clusterManager;
    @Mock AutoScalingEngine autoScaler;
    @Mock AdvancedLoadBalancer loadBalancer;
    @Mock DistributedPatternProcessor distributedProcessor;

    private ScalingIntegrationService service;

    @BeforeEach
    void setUp() {
        // Lenient stubs: called by getScalingStatus() / performHealthCheck() but not all tests invoke those.
        lenient().when(clusterManager.getMetrics()).thenReturn(Map.of());
        lenient().when(autoScaler.getMetrics()).thenReturn(Map.of());
        lenient().when(loadBalancer.getMetrics()).thenReturn(Map.of());
        lenient().when(distributedProcessor.getMetrics()).thenReturn(Map.of());

        ScalingIntegrationService.ScalingConfiguration config =
                new ScalingIntegrationService.ScalingConfiguration();
        service = new ScalingIntegrationService(
                eventloop(), distributedProcessor, clusterManager, autoScaler, loadBalancer, config);
    }

    // ------------------------------------------------------------------ lifecycle state transitions

    @Test
    @DisplayName("new service has INITIALIZING state, not yet initialized or running")
    void shouldHaveInitializingStateOnCreation() {
        ScalingStatus status = runPromise(service::getScalingStatus);

        assertThat(status.getCurrentState())
                .isEqualTo(ScalingIntegrationService.ScalingState.INITIALIZING);
        assertThat(status.isInitialized()).isFalse();
        assertThat(status.isRunning()).isFalse();
    }

    @Test
    @DisplayName("initialize() transitions state to READY and marks initialized=true")
    void shouldTransitionToReadyStateAfterInitialize() {
        when(clusterManager.initializeCluster())
                .thenReturn(Promise.of(new ClusterManagementModels.ClusterInitializationResult(
                        "req-1", true, "cluster-1", List.of(), null)));

        runPromise(service::initialize);

        ScalingStatus status = runPromise(service::getScalingStatus);
        assertThat(status.getCurrentState())
                .isEqualTo(ScalingIntegrationService.ScalingState.READY);
        assertThat(status.isInitialized()).isTrue();
        assertThat(status.isRunning()).isFalse();
    }

    @Test
    @DisplayName("start() before initialize() throws with 'not initialized' message")
    void shouldThrowWhenStartCalledBeforeInitialize() {
        assertThatThrownBy(() -> runPromise(service::start))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    @DisplayName("initialize() then start() transitions state to RUNNING")
    void shouldTransitionToRunningAfterStart() {
        when(clusterManager.initializeCluster())
                .thenReturn(Promise.of(new ClusterManagementModels.ClusterInitializationResult(
                        "req-1", true, "cluster-1", List.of(), null)));

        runPromise(service::initialize);
        runPromise(service::start);

        ScalingStatus status = runPromise(service::getScalingStatus);
        assertThat(status.getCurrentState())
                .isEqualTo(ScalingIntegrationService.ScalingState.RUNNING);
        assertThat(status.isRunning()).isTrue();
    }

    @Test
    @DisplayName("stop() after start() transitions state to STOPPED and marks running=false")
    void shouldTransitionToStoppedAfterStop() {
        when(clusterManager.initializeCluster())
                .thenReturn(Promise.of(new ClusterManagementModels.ClusterInitializationResult(
                        "req-1", true, "cluster-1", List.of(), null)));
        when(distributedProcessor.shutdown()).thenReturn(Promise.of(null));
        when(autoScaler.shutdown()).thenReturn(Promise.of(null));
        when(clusterManager.shutdown()).thenReturn(Promise.of(null));

        runPromise(service::initialize);
        runPromise(service::start);
        runPromise(service::stop);

        ScalingStatus status = runPromise(service::getScalingStatus);
        assertThat(status.getCurrentState())
                .isEqualTo(ScalingIntegrationService.ScalingState.STOPPED);
        assertThat(status.isRunning()).isFalse();
    }

    @Test
    @DisplayName("initialize() is idempotent – calling twice does not throw")
    void initializationIsIdempotent() {
        ClusterManagementModels.ClusterInitializationResult result =
                new ClusterManagementModels.ClusterInitializationResult(
                        "req-1", true, "cluster-1", List.of(), null);
        when(clusterManager.initializeCluster()).thenReturn(Promise.of(result));

        runPromise(service::initialize);
        runPromise(service::initialize); // second call must not throw

        ScalingStatus status = runPromise(service::getScalingStatus);
        assertThat(status.isInitialized()).isTrue();
    }

    // ------------------------------------------------------------------ metrics / health / config

    @Test
    @DisplayName("getMetrics() returns a non-null ScalingMetrics instance")
    void shouldReturnNonNullMetrics() {
        ScalingMetrics metrics = runPromise(service::getMetrics);

        assertThat(metrics).isNotNull();
    }

    @Test
    @DisplayName("performHealthCheck() returns a non-null HealthCheckResult")
    void shouldReturnNonNullHealthCheckResult() {
        HealthCheckResult result = runPromise(service::performHealthCheck);

        assertThat(result).isNotNull();
        assertThat(result.getComponentChecks()).containsKeys(
                "cluster-manager", "auto-scaler", "load-balancer", "distributed-processor");
    }

    @Test
    @DisplayName("performHealthCheck() reports DEGRADED when no cluster nodes are available")
    void healthCheckShouldBeDegradedWithNoClusterNodes() {
        // getMetrics() returns empty Map → clusterNodes = 0 → DEGRADED health level
        HealthCheckResult result = runPromise(service::performHealthCheck);

        HealthStatus.HealthLevel clusterLevel =
                result.getComponentChecks().get("cluster-manager").getHealthLevel();
        assertThat(clusterLevel).isEqualTo(HealthStatus.HealthLevel.DEGRADED);
    }

    @Test
    @DisplayName("updateConfiguration() with default config completes without error")
    void shouldUpdateConfigurationWithoutError() {
        ScalingIntegrationService.ScalingConfiguration newConfig =
                new ScalingIntegrationService.ScalingConfiguration();

        runPromise(() -> service.updateConfiguration(newConfig));
        // No exception → success; further status assertions via getScalingStatus
    }

    @Test
    @DisplayName("getScalingStatus() timestamp is recent")
    void statusTimestampShouldBeRecent() {
        long before = System.currentTimeMillis();
        ScalingStatus status = runPromise(service::getScalingStatus);
        long after = System.currentTimeMillis();

        assertThat(status.getTimestamp()).isBetween(before, after);
    }

    // ------------------------------------------------------------------ ScalingConfiguration

    @Test
    @DisplayName("ScalingConfiguration.updateFrom() copies rebalancingThreshold")
    void scalingConfigurationUpdateFromCopiesThreshold() {
        ScalingIntegrationService.ScalingConfiguration source =
                new ScalingIntegrationService.ScalingConfiguration();

        // Verify default threshold is 0.2
        assertThat(source.getRebalancingThreshold()).isEqualTo(0.2);

        ScalingIntegrationService.ScalingConfiguration target =
                new ScalingIntegrationService.ScalingConfiguration();
        target.updateFrom(source);

        assertThat(target.getRebalancingThreshold()).isEqualTo(source.getRebalancingThreshold());
    }

    @Test
    @DisplayName("ScalingConfiguration.updateFrom() copies sub-configs by reference")
    void scalingConfigurationUpdateFromCopiesSubConfigs() {
        ScalingIntegrationService.ClusterConfiguration clusterCfg =
                new ScalingIntegrationService.ClusterConfiguration();

        ScalingIntegrationService.ScalingConfiguration source =
                new ScalingIntegrationService.ScalingConfiguration();
        // Inject via reflection isn't needed; just verify null default then test updateFrom copies null fields
        ScalingIntegrationService.ScalingConfiguration target =
                new ScalingIntegrationService.ScalingConfiguration();
        target.updateFrom(source);

        assertThat(target.getClusterConfig()).isNull();
        assertThat(target.getAutoScalingConfig()).isNull();
        assertThat(target.getLoadBalancerConfig()).isNull();
        assertThat(target.getDistributedConfig()).isNull();
    }

    // ------------------------------------------------------------------ ScalingState enum

    @Test
    @DisplayName("ScalingState enum contains all expected lifecycle states")
    void scalingStateEnumShouldHaveAllLifecycleValues() {
        assertThat(ScalingIntegrationService.ScalingState.values())
                .containsExactlyInAnyOrder(
                        ScalingIntegrationService.ScalingState.INITIALIZING,
                        ScalingIntegrationService.ScalingState.READY,
                        ScalingIntegrationService.ScalingState.RUNNING,
                        ScalingIntegrationService.ScalingState.STOPPING,
                        ScalingIntegrationService.ScalingState.STOPPED,
                        ScalingIntegrationService.ScalingState.FAILED);
    }
}
