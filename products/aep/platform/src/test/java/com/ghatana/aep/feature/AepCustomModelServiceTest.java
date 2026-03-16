/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepCustomModelService}.
 *
 * <p>All JDBC calls are mocked. The ActiveJ event-loop dispatch is exercised
 * through {@link EventloopTestBase#runPromise}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepCustomModelService")
class AepCustomModelServiceTest extends EventloopTestBase {

    private static final String TENANT = "tenant-test";
    private static final String MODEL  = "pattern-recommender";
    private static final String V1     = "v1.0.0";
    private static final String V2     = "v2.0.0";
    private static final String SHA256 = "a".repeat(64);

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ModelRegistryService modelRegistry;
    @Mock private Array sqlArray;

    private MeterRegistry meterRegistry;
    private AepCustomModelService service;

    @BeforeEach
    void setUp() throws SQLException {
        meterRegistry = new SimpleMeterRegistry();
        service = new AepCustomModelService(
                dataSource, modelRegistry, meterRegistry,
                Executors.newSingleThreadExecutor());

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.createArrayOf(anyString(), any())).thenReturn(sqlArray);
        when(preparedStatement.executeUpdate()).thenReturn(1);
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null dataSource throws NullPointerException")
        void nullDataSource() {
            assertThatThrownBy(() ->
                    new AepCustomModelService(null, modelRegistry, meterRegistry,
                            Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dataSource");
        }

        @Test
        @DisplayName("null modelRegistry throws NullPointerException")
        void nullModelRegistry() {
            assertThatThrownBy(() ->
                    new AepCustomModelService(dataSource, null, meterRegistry,
                            Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("modelRegistry");
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutor() {
            assertThatThrownBy(() ->
                    new AepCustomModelService(dataSource, modelRegistry, meterRegistry, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("blockingExecutor");
        }
    }

    // =========================================================================
    // Artifact registration
    // =========================================================================

    @Nested
    @DisplayName("Artifact registration")
    class ArtifactRegistrationTests {

        @Test
        @DisplayName("registerArtifact persists correct SQL parameters")
        void registerArtifact_persistsCorrectly() throws Exception {
            AepCustomModelVersion version = AepCustomModelVersion.of(
                    TENANT, UUID.randomUUID(), MODEL, V2,
                    "s3://models/v2.onnx", SHA256,
                    Map.of("lr", "0.001"), Map.of("f1", 0.90));

            runPromise(() -> service.registerArtifact(version));

            verify(preparedStatement).setString(eq(2), eq(TENANT));
            verify(preparedStatement).setString(eq(4), eq(MODEL));
            verify(preparedStatement).setString(eq(5), eq(V2));
            verify(preparedStatement).setString(eq(7), eq(SHA256));
            verify(preparedStatement).executeUpdate();

            assertThat(meterRegistry.find("aep.custom_model.artifacts.registered")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("registerArtifact rejects malformed SHA-256 before any DB call")
        void registerArtifact_badSha256_rejectsEarly() {
            AepCustomModelVersion version = new AepCustomModelVersion(
                    UUID.randomUUID(), TENANT, UUID.randomUUID(),
                    MODEL, V2, "s3://models/v2.onnx", "notahex",
                    null, null, Map.of(), Map.of(), Instant.now());

            assertThatThrownBy(() -> runPromise(() -> service.registerArtifact(version)))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SHA");

            // No DB interaction when validation fails
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("listArtifactVersions returns empty list when no rows")
        void listArtifactVersions_empty() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            List<AepCustomModelVersion> result =
                    runPromise(() -> service.listArtifactVersions(TENANT, MODEL));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Validation gates
    // =========================================================================

    @Nested
    @DisplayName("Validation gates")
    class ValidationGateTests {

        @Test
        @DisplayName("validate returns true when all live metrics exceed thresholds")
        void validate_allPass_returnsTrue() throws Exception {
            // Arrange: findVersion returns a version with f1 threshold 0.90
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false); // one version row, then empty audit insert
            stubVersionRow(UUID.randomUUID(), Map.of("f1_score", 0.90));

            Boolean result = runPromise(() -> service.validate(
                    TENANT, MODEL, V2, Map.of("f1_score", 0.93)));

            assertThat(result).isTrue();
            assertThat(meterRegistry.find("aep.custom_model.validation.passed")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("validate returns false when any live metric is below threshold")
        void validate_belowThreshold_returnsFalse() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubVersionRow(UUID.randomUUID(), Map.of("f1_score", 0.90));

            Boolean result = runPromise(() -> service.validate(
                    TENANT, MODEL, V2, Map.of("f1_score", 0.85)));

            assertThat(result).isFalse();
            assertThat(meterRegistry.find("aep.custom_model.validation.failed")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("validate throws when version not found")
        void validate_versionNotFound_throws() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            assertThatThrownBy(() ->
                    runPromise(() -> service.validate(TENANT, MODEL, "vX", Map.of())))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Version not found");
        }

        private void stubVersionRow(UUID modelId, Map<String, Double> thresholds)
                throws SQLException {
            when(resultSet.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
            when(resultSet.getString("tenant_id")).thenReturn(TENANT);
            when(resultSet.getObject("model_id", UUID.class)).thenReturn(modelId);
            when(resultSet.getString("model_name")).thenReturn(MODEL);
            when(resultSet.getString("version")).thenReturn(V2);
            when(resultSet.getString("artifact_uri")).thenReturn("s3://m");
            when(resultSet.getString("artifact_sha256")).thenReturn(SHA256);
            when(resultSet.getString("git_commit_sha")).thenReturn(null);
            when(resultSet.getString("training_dataset_hash")).thenReturn(null);
            when(resultSet.getString("hyperparameters")).thenReturn("{}");
            // Return JSON for validation thresholds
            String threshJson = "{" + thresholds.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                    .reduce((a, b) -> a + "," + b).orElse("") + "}";
            when(resultSet.getString("validation_thresholds")).thenReturn(threshJson);
            when(resultSet.getTimestamp("created_at"))
                    .thenReturn(Timestamp.from(Instant.now()));
        }
    }

    // =========================================================================
    // Canary deployment
    // =========================================================================

    @Nested
    @DisplayName("Canary deployment")
    class CanaryDeploymentTests {

        @Test
        @DisplayName("startCanary persists deployment and returns ACTIVE canary")
        void startCanary_persists() throws Exception {
            AepCanaryDeployment canary =
                    runPromise(() -> service.startCanary(TENANT, MODEL, V1, V2, 10));

            assertThat(canary.status()).isEqualTo(AepCanaryDeployment.Status.ACTIVE);
            assertThat(canary.canaryTrafficPct()).isEqualTo(10);
            assertThat(canary.productionVersion()).isEqualTo(V1);
            assertThat(canary.canaryVersion()).isEqualTo(V2);
            verify(preparedStatement).executeUpdate(); // INSERT
            assertThat(meterRegistry.find("aep.custom_model.canary.started")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("startCanary with invalid traffic pct throws IllegalArgumentException")
        void startCanary_invalidPct_throws() {
            assertThatThrownBy(() ->
                    runPromise(() -> service.startCanary(TENANT, MODEL, V1, V2, 101)))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canaryTrafficPct");
        }

        @Test
        @DisplayName("adjustCanaryTraffic throws when no active canary")
        void adjustCanaryTraffic_noCanary_throws() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false); // no canary found

            assertThatThrownBy(() ->
                    runPromise(() -> service.adjustCanaryTraffic(TENANT, MODEL, 50)))
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No active canary");
        }

        @Test
        @DisplayName("promoteCanary updates model registry status to PRODUCTION")
        void promoteCanary_updatesRegistryStatus() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true); // active canary found
            stubCanaryRow();

            UUID modelId = UUID.randomUUID();
            ModelMetadata modelMeta = ModelMetadata.builder()
                    .id(modelId).tenantId(TENANT).name(MODEL).version(V2)
                    .framework("onnx").deploymentStatus(DeploymentStatus.CANARY)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
            when(modelRegistry.findByName(TENANT, MODEL, V2))
                    .thenReturn(Optional.of(modelMeta));

            AepCanaryDeployment result =
                    runPromise(() -> service.promoteCanary(TENANT, MODEL));

            verify(modelRegistry).updateStatus(TENANT, modelId, DeploymentStatus.PRODUCTION);
            assertThat(result.status()).isEqualTo(AepCanaryDeployment.Status.PROMOTED);
            assertThat(meterRegistry.find("aep.custom_model.canary.promoted")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("rollbackCanary sets traffic to 0% and marks ROLLED_BACK")
        void rollbackCanary_setsTrafficZero() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            stubCanaryRow();

            AepCanaryDeployment result =
                    runPromise(() -> service.rollbackCanary(TENANT, MODEL));

            assertThat(result.status()).isEqualTo(AepCanaryDeployment.Status.ROLLED_BACK);
            assertThat(result.canaryTrafficPct()).isEqualTo(0);
            assertThat(meterRegistry.find("aep.custom_model.canary.rolled_back")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("routeRequest returns production version when no canary active")
        void routeRequest_noCanary_returnsProduction() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false); // no canary

            ModelMetadata prodModel = ModelMetadata.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT)
                    .name(MODEL).version(V1)
                    .framework("onnx").deploymentStatus(DeploymentStatus.PRODUCTION)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
            when(modelRegistry.findByStatus(TENANT, DeploymentStatus.PRODUCTION))
                    .thenReturn(List.of(prodModel));

            String selected = runPromise(() -> service.routeRequest(TENANT, MODEL));

            assertThat(selected).isEqualTo(V1);
        }

        private void stubCanaryRow() throws SQLException {
            when(resultSet.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
            when(resultSet.getString("tenant_id")).thenReturn(TENANT);
            when(resultSet.getString("model_name")).thenReturn(MODEL);
            when(resultSet.getString("production_version")).thenReturn(V1);
            when(resultSet.getString("canary_version")).thenReturn(V2);
            when(resultSet.getInt("canary_traffic_pct")).thenReturn(10);
            when(resultSet.getString("status")).thenReturn("ACTIVE");
            when(resultSet.getTimestamp("started_at"))
                    .thenReturn(Timestamp.from(Instant.now()));
            when(resultSet.getTimestamp("concluded_at")).thenReturn(null);
        }
    }

    // =========================================================================
    // Value objects
    // =========================================================================

    @Nested
    @DisplayName("AepCanaryDeployment value object")
    class CanaryValueObjectTests {

        @Test
        @DisplayName("start() creates ACTIVE canary with correct fields")
        void start_createsActiveCanary() {
            AepCanaryDeployment c = AepCanaryDeployment.start(TENANT, MODEL, V1, V2, 10);

            assertThat(c.status()).isEqualTo(AepCanaryDeployment.Status.ACTIVE);
            assertThat(c.canaryTrafficPct()).isEqualTo(10);
            assertThat(c.id()).isNotNull();
            assertThat(c.concludedAt()).isNull();
        }

        @Test
        @DisplayName("canaryTrafficPct outside [0, 100] throws")
        void invalidTrafficPct_throws() {
            assertThatThrownBy(() ->
                    AepCanaryDeployment.start(TENANT, MODEL, V1, V2, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("withTrafficPct returns new record with correct pct")
        void withTrafficPct_returnsNewRecord() {
            AepCanaryDeployment c = AepCanaryDeployment.start(TENANT, MODEL, V1, V2, 10)
                    .withTrafficPct(50);

            assertThat(c.canaryTrafficPct()).isEqualTo(50);
        }

        @Test
        @DisplayName("withStatus PROMOTED sets concludedAt")
        void withStatus_promoted_setConcludedAt() {
            AepCanaryDeployment c = AepCanaryDeployment.start(TENANT, MODEL, V1, V2, 10)
                    .withStatus(AepCanaryDeployment.Status.PROMOTED);

            assertThat(c.status()).isEqualTo(AepCanaryDeployment.Status.PROMOTED);
            assertThat(c.concludedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("AepCustomModelVersion value object")
    class ModelVersionValueObjectTests {

        @Test
        @DisplayName("of() creates record with correct fields")
        void of_createsRecord() {
            UUID modelId = UUID.randomUUID();
            AepCustomModelVersion v = AepCustomModelVersion.of(
                    TENANT, modelId, MODEL, V2,
                    "s3://m", SHA256, Map.of(), Map.of("f1", 0.90));

            assertThat(v.tenantId()).isEqualTo(TENANT);
            assertThat(v.modelId()).isEqualTo(modelId);
            assertThat(v.artifactSha256()).isEqualTo(SHA256);
            assertThat(v.validationThresholds()).containsEntry("f1", 0.90);
        }

        @Test
        @DisplayName("hyperparameters map is made immutable")
        void hyperparameters_immutable() {
            Map<String, String> mutable = new java.util.HashMap<>();
            mutable.put("lr", "0.001");
            AepCustomModelVersion v = AepCustomModelVersion.of(
                    TENANT, UUID.randomUUID(), MODEL, V2,
                    "s3://m", SHA256, mutable, Map.of());

            assertThatThrownBy(() -> v.hyperparameters().put("extra", "val"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
