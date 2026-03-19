package com.ghatana.phr.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.health.HealthStatus;
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
        plugin = new FhirInteropKernelPlugin();
        mockContext = createMockContext();
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
        assertEquals("1.0.0", manifest.getRequiredKernelVersion());
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

        assertNotNull(manifest.getCapability());
        assertEquals("fhir.r4.interop", manifest.getCapability().getCapabilityId());
        assertEquals(com.ghatana.kernel.descriptor.KernelCapability.CapabilityType.INTEGRATION, 
            manifest.getCapability().getType());
        assertEquals("R4", manifest.getCapability().getMetadataValue("fhir_version"));
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

        String validObservationJson = "{\"resourceType\":\"Observation\",\"id\":\"1\"}";

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

        Exception exception = assertThrows(Exception.class, promise::getResult);
        assertTrue(exception.getMessage().contains("not started"));
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

        Set<String> resourceTypes = Set.of(
            "Patient", "Observation", "Encounter", "Condition", 
            "Procedure", "Medication", "AllergyIntolerance", "DiagnosticReport"
        );

        for (String resourceType : resourceTypes) {
            String json = "{\"resourceType\":\"" + resourceType + "\",\"id\":\"1\"}";
            
            Promise<FhirInteropKernelPlugin.ValidationResult> promise = 
                plugin.validateResource(resourceType, json);
            FhirInteropKernelPlugin.ValidationResult result = promise.getResult();

            assertTrue(result.isValid(), "Should validate " + resourceType);
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
        };
    }
}
