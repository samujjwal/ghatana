package com.ghatana.phr.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.plugin.PluginManifest;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FhirInteropKernelPlugin}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for FHIR R4 interoperability plugin
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("FhirInteropKernelPlugin Tests")
class FhirInteropKernelPluginTest {

    private FhirInteropKernelPlugin plugin;
    private KernelContext mockContext;

    @BeforeEach
    void setUp() {
        // Set DEGRADED mode to allow tests to pass without DataCloud dependency
        System.setProperty("PHR_DATACLOUD_MODE", "DEGRADED");
        plugin = new FhirInteropKernelPlugin();
        mockContext = createMockContext();
    }

    @Test
    @DisplayName("Should fail initialization in STRICT mode when DataCloud is unavailable")
    void shouldFailInitializationInStrictModeWhenDataCloudUnavailable() {
        // Clear the DEGRADED mode and set STRICT
        System.clearProperty("PHR_DATACLOUD_MODE");
        System.setProperty("PHR_DATACLOUD_MODE", "STRICT");
        
        FhirInteropKernelPlugin strictPlugin = new FhirInteropKernelPlugin();
        KernelContext strictContext = createMockContext();
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> strictPlugin.initialize(strictContext)
        );
        
        assertTrue(exception.getMessage().contains("DataCloud adapter is required in STRICT mode"));
        assertTrue(exception.getMessage().contains("not available"));
        
        System.setProperty("PHR_DATACLOUD_MODE", "DEGRADED");
    }

    @Test
    @DisplayName("Should initialize successfully in DEGRADED mode when DataCloud is unavailable")
    void shouldInitializeInDegradedModeWhenDataCloudUnavailable() {
        System.clearProperty("PHR_DATACLOUD_MODE");
        System.setProperty("PHR_DATACLOUD_MODE", "DEGRADED");
        
        FhirInteropKernelPlugin degradedPlugin = new FhirInteropKernelPlugin();
        KernelContext degradedContext = createMockContext();
        
        assertDoesNotThrow(() -> degradedPlugin.initialize(degradedContext));
    }

    @Test
    @DisplayName("Should make DataCloud dependency required in STRICT mode in manifest")
    void shouldMakeDataCloudDependencyRequiredInStrictModeInManifest() {
        System.clearProperty("PHR_DATACLOUD_MODE");
        System.setProperty("PHR_DATACLOUD_MODE", "STRICT");
        
        FhirInteropKernelPlugin strictPlugin = new FhirInteropKernelPlugin();
        PluginManifest manifest = strictPlugin.getManifest();
        
        assertNotNull(manifest.getDependencies());
        var dataCloudDep = manifest.getDependencies().stream()
            .filter(d -> "data-storage".equals(d.getDependencyId()))
            .findFirst();
        
        assertTrue(dataCloudDep.isPresent());
        assertFalse(dataCloudDep.get().isOptional(), "DataCloud dependency should be required in STRICT mode");
        
        System.setProperty("PHR_DATACLOUD_MODE", "DEGRADED");
    }

    @Test
    @DisplayName("Should make DataCloud dependency optional in DEGRADED mode in manifest")
    void shouldMakeDataCloudDependencyOptionalInDegradedModeInManifest() {
        System.clearProperty("PHR_DATACLOUD_MODE");
        System.setProperty("PHR_DATACLOUD_MODE", "DEGRADED");
        
        FhirInteropKernelPlugin degradedPlugin = new FhirInteropKernelPlugin();
        PluginManifest manifest = degradedPlugin.getManifest();
        
        assertNotNull(manifest.getDependencies());
        var dataCloudDep = manifest.getDependencies().stream()
            .filter(d -> "data-storage".equals(d.getDependencyId()))
            .findFirst();
        
        assertTrue(dataCloudDep.isPresent());
        assertTrue(dataCloudDep.get().isOptional(), "DataCloud dependency should be optional in DEGRADED mode");
    }

    @Test
    @DisplayName("Should return correct plugin manifest")
    void shouldReturnCorrectPluginManifest() {
        PluginManifest manifest = plugin.getManifest();

        assertNotNull(manifest);
        assertEquals("fhir-interop-r4", manifest.getPluginId());
        assertEquals("1.0.0", manifest.getVersion());
        assertEquals("FHIR R4 interoperability plugin for healthcare data exchange", manifest.getDescription());
        assertEquals("Ghatana PHR Team", manifest.getAuthor());
        assertEquals("Apache-2.0", manifest.getLicense());
        assertTrue(manifest.getRequiredKernelVersions().contains("1.0.0"));
    }

    @Test
    @DisplayName("Should declare correct exported contracts")
    void shouldDeclareCorrectExportedContracts() {
        Set<String> contracts = plugin.getExportedContracts();

        assertEquals(3, contracts.size());
        assertTrue(contracts.contains("com.ghatana.phr.fhir.FhirResourceService"));
        assertTrue(contracts.contains("com.ghatana.phr.fhir.FhirValidator"));
        assertTrue(contracts.contains("com.ghatana.phr.fhir.FhirTransformer"));
    }

    @Test
    @DisplayName("Should declare correct required contracts")
    void shouldDeclareCorrectRequiredContracts() {
        Set<String> contracts = plugin.getRequiredContracts();

        assertEquals(1, contracts.size());
        assertTrue(contracts.contains("com.ghatana.kernel.storage.DataStorageService"));
    }

    @Test
    @DisplayName("Should have FHIR capability in manifest")
    void shouldHaveFhirCapabilityInManifest() {
        PluginManifest manifest = plugin.getManifest();

        assertNotNull(manifest.getCapabilities());
        com.ghatana.kernel.descriptor.KernelCapability fhirCap = manifest.getCapabilities().stream()
            .filter(c -> "fhir.r4.interop".equals(c.getCapabilityId()))
            .findFirst().orElse(null);
        assertNotNull(fhirCap);
        assertEquals("fhir.r4.interop", fhirCap.getCapabilityId());
        assertEquals(com.ghatana.kernel.descriptor.KernelCapability.CapabilityType.INTEGRATION,
            fhirCap.getType());
        assertEquals("R4", fhirCap.getMetadata().get("fhir_version"));
    }

    @Test
    @DisplayName("Should initialize successfully")
    void shouldInitializeSuccessfully() {
        assertDoesNotThrow(() -> plugin.initialize(mockContext));
    }

    @Test
    @DisplayName("Should install successfully")
    void shouldInstallSuccessfully() {
        plugin.initialize(mockContext);

        Promise<Void> installPromise = plugin.install();
        assertDoesNotThrow(installPromise::getResult);
    }

    @Test
    @DisplayName("Should start successfully after install")
    void shouldStartSuccessfullyAfterInstall() {
        plugin.initialize(mockContext);
        plugin.install().getResult();

        Promise<Void> startPromise = plugin.start();
        assertDoesNotThrow(startPromise::getResult);
    }

    @Test
    @DisplayName("Should validate FHIR Patient resource")
    void shouldValidateFhirPatientResource() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        String validPatientJson = "{\"resourceType\":\"Patient\",\"id\":\"1\",\"name\":[{\"family\":\"Test\"}]}";

        Promise<FhirInteropKernelPlugin.ValidationResult> promise =
            plugin.validateResource("Patient", validPatientJson);
        FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

        assertTrue(result.isValid());
        assertEquals("Patient", result.getResourceType());
    }

    @Test
    @DisplayName("Should validate FHIR Observation resource")
    void shouldValidateFhirObservationResource() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        String validObservationJson = "{\"resourceType\":\"Observation\",\"id\":\"1\",\"status\":\"final\",\"code\":{\"text\":\"test\"},\"subject\":{\"reference\":\"Patient/1\"}}";

        Promise<FhirInteropKernelPlugin.ValidationResult> promise =
            plugin.validateResource("Observation", validObservationJson);
        FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject invalid FHIR resource type")
    void shouldRejectInvalidFhirResourceType() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        String invalidJson = "{\"resourceType\":\"InvalidType\",\"id\":\"1\"}";

        Promise<FhirInteropKernelPlugin.ValidationResult> promise =
            plugin.validateResource("InvalidType", invalidJson);
        FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should reject null resource")
    void shouldRejectNullResource() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        Promise<FhirInteropKernelPlugin.ValidationResult> promise =
            plugin.validateResource("Patient", null);
        FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should reject empty resource")
    void shouldRejectEmptyResource() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        Promise<FhirInteropKernelPlugin.ValidationResult> promise =
            plugin.validateResource("Patient", "");
        FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should transform internal data to FHIR")
    void shouldTransformInternalDataToFhir() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        Object internalData = Map.of("patientId", "123", "name", "John Doe");

        Promise<String> promise = plugin.transformToFhir(internalData, "Patient");
        String fhirJson = promise.getResult();

        assertNotNull(fhirJson);
        assertTrue(fhirJson.contains("Patient"));
    }

    @Test
    @DisplayName("Should transform FHIR to internal format")
    void shouldTransformFhirToInternalFormat() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        String fhirJson = "{\"resourceType\":\"Patient\",\"id\":\"1\"}";

        Promise<Object> promise = plugin.transformFromFhir(fhirJson, "Patient");
        Object internalData = promise.getResult();

        assertNotNull(internalData);
    }

    @Test
    @DisplayName("Should store and retrieve FHIR resource")
    void shouldStoreAndRetrieveFhirResource() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        FhirInteropKernelPlugin.FhirResource resource = new FhirInteropKernelPlugin.FhirResource(
            "resource-123", "Patient", "{\"resourceType\":\"Patient\"}", Instant.now()
        );

        Promise<Void> storePromise = plugin.storeResource(resource);
        assertDoesNotThrow(storePromise::getResult);

        Promise<FhirInteropKernelPlugin.FhirResource> retrievePromise = plugin.getResource("resource-123");
        FhirInteropKernelPlugin.FhirResource retrieved = retrievePromise.getResult();

        assertNotNull(retrieved);
        assertEquals("resource-123", retrieved.getId());
        assertEquals("Patient", retrieved.getResourceType());
    }

    @Test
    @DisplayName("Should search FHIR resources")
    void shouldSearchFhirResources() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        Map<String, String> searchParams = Map.of("name", "Doe");

        Promise<FhirInteropKernelPlugin.SearchResult> promise =
            plugin.searchResources("Patient", searchParams);
        FhirInteropKernelPlugin.SearchResult result = promise.getResult();

        assertNotNull(result);
        assertNotNull(result.getResources());
    }

    @Test
    @DisplayName("Should reject operations when not started")
    void shouldRejectOperationsWhenNotStarted() {
        plugin.initialize(mockContext);
        // Don't install or start

        Promise<FhirInteropKernelPlugin.ValidationResult> promise =
            plugin.validateResource("Patient", "{\"test\":\"data\"}");

        assertTrue(promise.isException());
        assertTrue(promise.getException().getMessage().contains("not started"));
    }

    @Test
    @DisplayName("Should stop successfully")
    void shouldStopSuccessfully() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        Promise<Void> stopPromise = plugin.stop();
        assertDoesNotThrow(stopPromise::getResult);
    }

    @Test
    @DisplayName("Should uninstall successfully")
    void shouldUninstallSuccessfully() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        Promise<Void> uninstallPromise = plugin.uninstall();
        assertDoesNotThrow(uninstallPromise::getResult);
    }

    @Test
    @DisplayName("Should report healthy status when started")
    void shouldReportHealthyStatusWhenStarted() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        HealthStatus status = plugin.getHealthStatus();

        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("operational"));
    }

    @Test
    @DisplayName("Should report unhealthy status when not started")
    void shouldReportUnhealthyStatusWhenNotStarted() {
        plugin.initialize(mockContext);
        // Don't start

        HealthStatus status = plugin.getHealthStatus();

        assertEquals(HealthStatus.Status.UNHEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("not started"));
    }

    @Test
    @DisplayName("Should handle all supported FHIR resource types")
    void shouldHandleAllSupportedFhirResourceTypes() {
        plugin.initialize(mockContext);
        plugin.install().getResult();
        plugin.start().getResult();

        // Test resources with valid FHIR structure for each type
        Map<String, String> resourceTypes = Map.of(
            "Patient", "{\"resourceType\":\"Patient\",\"id\":\"1\",\"name\":[{\"family\":\"Test\"}]}",
            "Observation", "{\"resourceType\":\"Observation\",\"id\":\"1\",\"status\":\"final\",\"code\":{\"text\":\"test\"},\"subject\":{\"reference\":\"Patient/1\"}}",
            "Encounter", "{\"resourceType\":\"Encounter\",\"id\":\"1\",\"status\":\"finished\",\"class\":{\"code\":\"AMB\"},\"subject\":{\"reference\":\"Patient/1\"}}",
            "Condition", "{\"resourceType\":\"Condition\",\"id\":\"1\",\"code\":{\"text\":\"test\"},\"subject\":{\"reference\":\"Patient/1\"}}",
            "Procedure", "{\"resourceType\":\"Procedure\",\"id\":\"1\",\"status\":\"completed\",\"code\":{\"text\":\"test\"},\"subject\":{\"reference\":\"Patient/1\"}}",
            "Medication", "{\"resourceType\":\"Medication\",\"id\":\"1\"}",
            "AllergyIntolerance", "{\"resourceType\":\"AllergyIntolerance\",\"id\":\"1\"}",
            "DiagnosticReport", "{\"resourceType\":\"DiagnosticReport\",\"id\":\"1\",\"status\":\"final\",\"code\":{\"text\":\"test\"},\"subject\":{\"reference\":\"Patient/1\"}}"
        );

        for (Map.Entry<String, String> entry : resourceTypes.entrySet()) {
            Promise<FhirInteropKernelPlugin.ValidationResult> promise =
                plugin.validateResource(entry.getKey(), entry.getValue());
            FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

            assertTrue(result.isValid(), "Should validate " + entry.getKey());
        }
    }

    // ==================== Test Helpers ====================

    private KernelContext createMockContext() {
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) { return java.util.Optional.empty(); }
            @Override public <T> boolean hasDependency(Class<T> type) { return false; }
            @Override public <T> T getDependency(String name, Class<T> type) { return null; }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext() { return null; }
            @Override public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) { return java.util.Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return Runnable::run; }
            @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
            @Override public <T> void registerService(Class<T> type, T service) {}
        };
    }
}
