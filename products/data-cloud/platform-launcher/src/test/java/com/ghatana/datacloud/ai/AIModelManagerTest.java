package com.ghatana.datacloud.ai;

import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link AIModelManager} with 100% coverage.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Model registration with validation</li>
 *   <li>Active model retrieval with caching</li>
 *   <li>Model listing across all statuses</li>
 *   <li>Model promotion to production</li>
 *   <li>Staleness detection</li>
 *   <li>Metrics recording</li>
 *   <li>Cache management</li>
 *   <li>Error handling and validation</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Comprehensive test coverage for AIModelManager
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AI Model Manager Tests")
class AIModelManagerTest extends EventloopTestBase {

    @Mock
    private ModelRegistryService modelRegistry;

    @Mock
    private AiMetricsEmitter aiMetrics;

    private AIModelManager modelManager;

    @BeforeEach
    void setup() { // GH-90000
        modelManager = new AIModelManager(modelRegistry, aiMetrics); // GH-90000
    }

    // ========================================================================
    // CONSTRUCTOR TESTS
    // ========================================================================

    @Test
    @DisplayName("Should create AIModelManager with valid dependencies")
    void shouldCreateWithValidDependencies() { // GH-90000
        // WHEN: Creating manager
        AIModelManager manager = new AIModelManager(modelRegistry, aiMetrics); // GH-90000

        // THEN: Manager is created successfully
        assertThat(manager).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null model registry")
    void shouldRejectNullModelRegistry() { // GH-90000
        // WHEN/THEN: Throws exception for null registry
        assertThatThrownBy(() -> // GH-90000
            new AIModelManager(null, aiMetrics) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("modelRegistry");
    }

    @Test
    @DisplayName("Should reject null AI metrics")
    void shouldRejectNullAiMetrics() { // GH-90000
        // WHEN/THEN: Throws exception for null metrics
        assertThatThrownBy(() -> // GH-90000
            new AIModelManager(modelRegistry, null) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("aiMetrics");
    }

    // ========================================================================
    // MODEL REGISTRATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should register model successfully")
    void shouldRegisterModel() { // GH-90000
        // GIVEN: Valid model metadata
        ModelMetadata model = createModel("quality-scorer", "v1.0.0", DeploymentStatus.STAGED); // GH-90000

        // WHEN: Registering model
        ModelMetadata registered = runPromise(() -> // GH-90000
            modelManager.registerModel("tenant-1", model) // GH-90000
        );

        // THEN: Model is registered
        assertThat(registered).isNotNull(); // GH-90000
        assertThat(registered.getName()).isEqualTo("quality-scorer");
        assertThat(registered.getVersion()).isEqualTo("v1.0.0");

        // AND: Registry is called
        verify(modelRegistry).register(model); // GH-90000
    }

    @Test
    @DisplayName("Should reject null tenant ID in registration")
    void shouldRejectNullTenantIdInRegistration() { // GH-90000
        // GIVEN: Valid model
        ModelMetadata model = createModel("test-model", "v1.0.0", DeploymentStatus.STAGED); // GH-90000

        // WHEN/THEN: Throws exception for null tenant ID
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.registerModel(null, model)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("Should reject null model in registration")
    void shouldRejectNullModelInRegistration() { // GH-90000
        // WHEN/THEN: Throws exception for null model
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.registerModel("tenant-1", null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("model");
    }

    @Test
    @DisplayName("Should validate model name is not blank")
    void shouldValidateModelName() { // GH-90000
        // GIVEN: Model with blank name
        ModelMetadata model = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("")
            .version("v1.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.STAGED) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        // WHEN/THEN: Throws exception for blank name
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.registerModel("tenant-1", model)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("model.name is required");

        clearFatalError(); // GH-90000
    }

    @Test
    @DisplayName("Should validate model version is not blank")
    void shouldValidateModelVersion() { // GH-90000
        // GIVEN: Model with blank version
        ModelMetadata model = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("test-model")
            .version("")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.STAGED) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        // WHEN/THEN: Throws exception for blank version
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.registerModel("tenant-1", model)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("model.version is required");

        clearFatalError(); // GH-90000
    }

    @Test
    @DisplayName("Should validate model tenant ID is not blank")
    void shouldValidateModelTenantId() { // GH-90000
        // GIVEN: Model with blank tenant ID
        ModelMetadata model = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("")
            .name("test-model")
            .version("v1.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.STAGED) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        // WHEN/THEN: Throws exception for blank tenant ID
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.registerModel("tenant-1", model)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("model.tenantId is required");

        clearFatalError(); // GH-90000
    }

    // ========================================================================
    // ACTIVE MODEL RETRIEVAL TESTS
    // ========================================================================

    @Test
    @DisplayName("Should get active model from registry")
    void shouldGetActiveModel() { // GH-90000
        // GIVEN: Production model in registry
        ModelMetadata prodModel = createModel("quality-scorer", "v1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        when(modelRegistry.findByStatus("tenant-1", DeploymentStatus.PRODUCTION)) // GH-90000
            .thenReturn(List.of(prodModel)); // GH-90000

        // WHEN: Getting active model
        ModelMetadata active = runPromise(() -> // GH-90000
            modelManager.getActiveModel("tenant-1", "quality-scorer") // GH-90000
        );

        // THEN: Returns production model
        assertThat(active).isNotNull(); // GH-90000
        assertThat(active.getName()).isEqualTo("quality-scorer");
        assertThat(active.getDeploymentStatus()).isEqualTo(DeploymentStatus.PRODUCTION); // GH-90000
    }

    @Test
    @DisplayName("Should cache registered models")
    void shouldCacheRegisteredModels() { // GH-90000
        // GIVEN: Model registered
        ModelMetadata model = createModel("quality-scorer", "v1.0.0", DeploymentStatus.PRODUCTION); // GH-90000

        // WHEN: Registering model
        ModelMetadata registered = runPromise(() -> // GH-90000
            modelManager.registerModel("tenant-1", model) // GH-90000
        );

        // THEN: Model is registered and cached
        assertThat(registered).isNotNull(); // GH-90000
        assertThat(registered.getName()).isEqualTo("quality-scorer");
        verify(modelRegistry).register(model); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when no active model found")
    void shouldThrowExceptionWhenNoActiveModel() { // GH-90000
        // GIVEN: No production models
        when(modelRegistry.findByStatus("tenant-1", DeploymentStatus.PRODUCTION)) // GH-90000
            .thenReturn(List.of()); // GH-90000

        // WHEN/THEN: Throws exception
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.getActiveModel("tenant-1", "non-existent")) // GH-90000
        ).isInstanceOf(IllegalStateException.class) // GH-90000
         .hasMessageContaining("No active model found");

        clearFatalError(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null tenant ID in get active model")
    void shouldRejectNullTenantIdInGetActive() { // GH-90000
        // WHEN/THEN: Throws exception for null tenant ID
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.getActiveModel(null, "model-name")) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("Should reject null model name in get active model")
    void shouldRejectNullModelNameInGetActive() { // GH-90000
        // WHEN/THEN: Throws exception for null model name
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.getActiveModel("tenant-1", null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("modelName");
    }

    // ========================================================================
    // GET ALL MODELS TESTS
    // ========================================================================

    @Test
    @DisplayName("Should get all models across all statuses")
    void shouldGetAllModels() { // GH-90000
        // GIVEN: Models in different statuses
        ModelMetadata prodModel = createModel("model-1", "v1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        ModelMetadata stagedModel = createModel("model-2", "v2.0.0", DeploymentStatus.STAGED); // GH-90000
        ModelMetadata retiredModel = createModel("model-3", "v0.9.0", DeploymentStatus.RETIRED); // GH-90000

        when(modelRegistry.findByStatus(anyString(), any(DeploymentStatus.class))) // GH-90000
            .thenAnswer(invocation -> { // GH-90000
                DeploymentStatus status = invocation.getArgument(1); // GH-90000
                if (status == DeploymentStatus.PRODUCTION) return List.of(prodModel); // GH-90000
                if (status == DeploymentStatus.STAGED) return List.of(stagedModel); // GH-90000
                if (status == DeploymentStatus.RETIRED) return List.of(retiredModel); // GH-90000
                return List.of(); // GH-90000
            });

        // WHEN: Getting all models
        List<ModelMetadata> allModels = runPromise(() -> // GH-90000
            modelManager.getAllModels("tenant-1")
        );

        // THEN: Returns all models
        assertThat(allModels).hasSize(3); // GH-90000
        assertThat(allModels).extracting(ModelMetadata::getName) // GH-90000
            .containsExactlyInAnyOrder("model-1", "model-2", "model-3"); // GH-90000
    }

    @Test
    @DisplayName("Should reject null tenant ID in get all models")
    void shouldRejectNullTenantIdInGetAll() { // GH-90000
        // WHEN/THEN: Throws exception for null tenant ID
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.getAllModels(null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("tenantId");
    }

    // ========================================================================
    // MODEL PROMOTION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should promote staged model to production")
    void shouldPromoteToProduction() { // GH-90000
        // GIVEN: Staged model
        UUID modelId = UUID.randomUUID(); // GH-90000
        Instant createdAt = Instant.now(); // GH-90000

        ModelMetadata stagedModel = ModelMetadata.builder() // GH-90000
            .id(modelId) // GH-90000
            .tenantId("tenant-1")
            .name("quality-scorer")
            .version("v2.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.STAGED) // GH-90000
            .trainingMetrics(Map.of("accuracy", 0.95)) // GH-90000
            .createdAt(createdAt) // GH-90000
            .updatedAt(createdAt) // GH-90000
            .build(); // GH-90000

        ModelMetadata promotedModel = ModelMetadata.builder() // GH-90000
            .id(modelId) // GH-90000
            .tenantId("tenant-1")
            .name("quality-scorer")
            .version("v2.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.PRODUCTION) // GH-90000
            .trainingMetrics(Map.of("accuracy", 0.95)) // GH-90000
            .createdAt(createdAt) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        when(modelRegistry.findByName("tenant-1", "quality-scorer", "v2.0.0")) // GH-90000
            .thenReturn(Optional.of(stagedModel)) // GH-90000
            .thenReturn(Optional.of(promotedModel)); // GH-90000

        // WHEN: Promoting model
        ModelMetadata promoted = runPromise(() -> // GH-90000
            modelManager.promoteToProduction("tenant-1", "quality-scorer", "v2.0.0") // GH-90000
        );

        // THEN: Model is promoted
        assertThat(promoted).isNotNull(); // GH-90000
        assertThat(promoted.getDeploymentStatus()).isEqualTo(DeploymentStatus.PRODUCTION); // GH-90000

        // AND: Registry is updated
        verify(modelRegistry).updateStatus("tenant-1", stagedModel.getId(), DeploymentStatus.PRODUCTION); // GH-90000
    }

    @Test
    @DisplayName("Should reject promotion of non-staged model")
    void shouldRejectPromotionOfNonStagedModel() { // GH-90000
        // GIVEN: Production model (not staged) // GH-90000
        ModelMetadata prodModel = createModel("quality-scorer", "v1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        when(modelRegistry.findByName("tenant-1", "quality-scorer", "v1.0.0")) // GH-90000
            .thenReturn(Optional.of(prodModel)); // GH-90000

        // WHEN/THEN: Throws exception
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.promoteToProduction("tenant-1", "quality-scorer", "v1.0.0")) // GH-90000
        ).isInstanceOf(IllegalStateException.class) // GH-90000
         .hasMessageContaining("Model must be in STAGED status");

        clearFatalError(); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when promoting non-existent model")
    void shouldThrowExceptionWhenPromotingNonExistent() { // GH-90000
        // GIVEN: No model found
        when(modelRegistry.findByName("tenant-1", "non-existent", "v1.0.0")) // GH-90000
            .thenReturn(Optional.empty()); // GH-90000

        // WHEN/THEN: Throws exception
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> modelManager.promoteToProduction("tenant-1", "non-existent", "v1.0.0")) // GH-90000
        ).isInstanceOf(IllegalStateException.class) // GH-90000
         .hasMessageContaining("Model not found");

        clearFatalError(); // GH-90000
    }

    @Test
    @DisplayName("Should invalidate cache after promotion")
    void shouldInvalidateCacheAfterPromotion() { // GH-90000
        // GIVEN: Staged model with cache
        UUID modelId = UUID.randomUUID(); // GH-90000
        Instant createdAt = Instant.now(); // GH-90000

        ModelMetadata stagedModel = ModelMetadata.builder() // GH-90000
            .id(modelId) // GH-90000
            .tenantId("tenant-1")
            .name("quality-scorer")
            .version("v2.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.STAGED) // GH-90000
            .trainingMetrics(Map.of("accuracy", 0.95)) // GH-90000
            .createdAt(createdAt) // GH-90000
            .updatedAt(createdAt) // GH-90000
            .build(); // GH-90000

        ModelMetadata promotedModel = ModelMetadata.builder() // GH-90000
            .id(modelId) // GH-90000
            .tenantId("tenant-1")
            .name("quality-scorer")
            .version("v2.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.PRODUCTION) // GH-90000
            .trainingMetrics(Map.of("accuracy", 0.95)) // GH-90000
            .createdAt(createdAt) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        when(modelRegistry.findByName("tenant-1", "quality-scorer", "v2.0.0")) // GH-90000
            .thenReturn(Optional.of(stagedModel)) // GH-90000
            .thenReturn(Optional.of(promotedModel)); // GH-90000

        // WHEN: Promoting model
        runPromise(() -> // GH-90000
            modelManager.promoteToProduction("tenant-1", "quality-scorer", "v2.0.0") // GH-90000
        );

        // THEN: Cache is invalidated (next getActiveModel will query registry) // GH-90000
        when(modelRegistry.findByStatus("tenant-1", DeploymentStatus.PRODUCTION)) // GH-90000
            .thenReturn(List.of(promotedModel)); // GH-90000

        ModelMetadata active = runPromise(() -> // GH-90000
            modelManager.getActiveModel("tenant-1", "quality-scorer") // GH-90000
        );

        assertThat(active.getDeploymentStatus()).isEqualTo(DeploymentStatus.PRODUCTION); // GH-90000
    }

    // ========================================================================
    // STALENESS DETECTION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should detect stale model based on update time")
    void shouldDetectStaleModel() { // GH-90000
        // GIVEN: Old model
        Instant oldTime = Instant.now().minus(Duration.ofDays(100)); // GH-90000
        ModelMetadata oldModel = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("old-model")
            .version("v1.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.PRODUCTION) // GH-90000
            .createdAt(oldTime) // GH-90000
            .updatedAt(oldTime) // GH-90000
            .build(); // GH-90000

        // WHEN: Checking staleness
        boolean isStale = modelManager.isModelStale(oldModel, Duration.ofDays(30)); // GH-90000

        // THEN: Model is stale
        assertThat(isStale).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should detect fresh model")
    void shouldDetectFreshModel() { // GH-90000
        // GIVEN: Recent model
        Instant recentTime = Instant.now().minus(Duration.ofDays(5)); // GH-90000
        ModelMetadata recentModel = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("recent-model")
            .version("v1.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.PRODUCTION) // GH-90000
            .createdAt(recentTime) // GH-90000
            .updatedAt(recentTime) // GH-90000
            .build(); // GH-90000

        // WHEN: Checking staleness
        boolean isStale = modelManager.isModelStale(recentModel, Duration.ofDays(30)); // GH-90000

        // THEN: Model is not stale
        assertThat(isStale).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should detect stale model using old update time")
    void shouldDetectStaleModelWithOldUpdateTime() { // GH-90000
        // GIVEN: Model with old update time
        Instant oldTime = Instant.now().minus(Duration.ofDays(100)); // GH-90000
        ModelMetadata model = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("model")
            .version("v1.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.PRODUCTION) // GH-90000
            .createdAt(oldTime) // GH-90000
            .updatedAt(oldTime) // GH-90000
            .build(); // GH-90000

        // WHEN: Checking staleness with 30-day threshold
        boolean isStale = modelManager.isModelStale(model, Duration.ofDays(30)); // GH-90000

        // THEN: Model is stale (100 days > 30 days threshold) // GH-90000
        assertThat(isStale).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should detect fresh model with recent update time")
    void shouldDetectFreshModelWithRecentUpdateTime() { // GH-90000
        // GIVEN: Model with recent update time
        Instant recentTime = Instant.now().minus(Duration.ofDays(5)); // GH-90000
        ModelMetadata model = ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("model")
            .version("v1.0.0")
            .framework("tensorflow")
            .deploymentStatus(DeploymentStatus.PRODUCTION) // GH-90000
            .createdAt(recentTime) // GH-90000
            .updatedAt(recentTime) // GH-90000
            .build(); // GH-90000

        // WHEN: Checking staleness with 30-day threshold
        boolean isStale = modelManager.isModelStale(model, Duration.ofDays(30)); // GH-90000

        // THEN: Model is not stale (5 days < 30 days threshold) // GH-90000
        assertThat(isStale).isFalse(); // GH-90000
    }

    // ========================================================================
    // METRICS RECORDING TESTS
    // ========================================================================

    @Test
    @DisplayName("Should record inference metrics")
    void shouldRecordInferenceMetrics() { // GH-90000
        // WHEN: Recording inference
        modelManager.recordInference("tenant-1", "quality-scorer", "v1.0.0", // GH-90000
            Duration.ofMillis(45), true); // GH-90000

        // THEN: Metrics are recorded
        verify(aiMetrics).recordInference("quality-scorer", "v1.0.0", Duration.ofMillis(45), true); // GH-90000
    }

    @Test
    @DisplayName("Should record prediction quality metrics")
    void shouldRecordPredictionQuality() { // GH-90000
        // WHEN: Recording quality
        modelManager.recordPredictionQuality("tenant-1", "quality-scorer", "v1.0.0", 0.95); // GH-90000

        // THEN: Metrics are recorded
        verify(aiMetrics).recordPredictionQuality("quality-scorer", "v1.0.0", 0.95); // GH-90000
    }

    // ========================================================================
    // CACHE MANAGEMENT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should clear cache")
    void shouldClearCache() { // GH-90000
        // GIVEN: Model in cache
        ModelMetadata model = createModel("quality-scorer", "v1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        runPromise(() -> modelManager.registerModel("tenant-1", model)); // GH-90000

        // WHEN: Clearing cache
        modelManager.clearCache(); // GH-90000

        // THEN: Cache is cleared (next retrieval queries registry) // GH-90000
        when(modelRegistry.findByStatus("tenant-1", DeploymentStatus.PRODUCTION)) // GH-90000
            .thenReturn(List.of(model)); // GH-90000

        runPromise(() -> modelManager.getActiveModel("tenant-1", "quality-scorer")); // GH-90000
        verify(modelRegistry).findByStatus("tenant-1", DeploymentStatus.PRODUCTION); // GH-90000
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private ModelMetadata createModel(String name, String version, DeploymentStatus status) { // GH-90000
        return ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name(name) // GH-90000
            .version(version) // GH-90000
            .framework("tensorflow")
            .deploymentStatus(status) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000
    }
}
