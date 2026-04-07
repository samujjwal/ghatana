package com.ghatana.phr.hie;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.phr.api.FhirApiResponse;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NepalHieIntegrationServiceTest extends EventloopTestBase {

    private PhrFhirR4Server server;
    private NepalHieIntegrationService service;

    @BeforeEach
    void setUp() {
        server = new PhrFhirR4Server(mockContext());
        runPromise(server::start);
        runPromise(() -> server.createResource("Patient", patientJson()));
        runPromise(() -> server.createResource("Observation", observationJson()));
        runPromise(() -> server.createResource("MedicationRequest", medicationJson()));
        service = new NepalHieIntegrationService(
            server,
            (patientId, correlationId, hl7Message) -> Promise.of(new NepalHieAck("ctrl-2", "AA", "Accepted")),
            new NepalHieMessageBuilder(),
            new NepalHieConfig("https://example.invalid", "GHATANA-PHR", "PHR-NEPAL", "NEPAL-HIE", "NHIE", "token", Duration.ofSeconds(5))
        );
        runPromise(service::start);
    }

    @Test
    void submitsPatientSummaryToNepalHie() {
        NepalHieSyncResult result = runPromise(() -> service.submitPatientSummary("patient-1", "corr-1"));

        assertTrue(result.accepted());
        assertEquals("AA", result.acknowledgementCode());
        assertTrue(result.hl7Message().contains("PID|||patient-1||Aarati Shrestha"));
    }

    private KernelContext mockContext() {
        ConcurrentHashMap<Class<?>, Object> registeredServices = new ConcurrentHashMap<>();
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) {
                if (type.equals(KernelConfigResolver.class)) {
                    return type.cast(new KernelConfigResolver() {
                        @Override public <R> R resolve(String key, Class<R> type, KernelTenantContext tenantContext) { return null; }
                        @Override public <R> R resolveWithDefault(String key, Class<R> type, R defaultValue, KernelTenantContext tenantContext) { return defaultValue; }
                        @Override public <R> Optional<R> resolveOptional(String key, Class<R> type, KernelTenantContext tenantContext) { return Optional.empty(); }
                        @Override public void addConfigProvider(KernelConfigResolver.ConfigProvider provider) {}
                        @Override public Promise<Void> reloadConfig(String tenantId) { return Promise.complete(); }
                        @Override public java.util.List<String> getAvailableKeys(KernelTenantContext tenantContext) { return java.util.List.of(); }
                    });
                }
                Object registered = registeredServices.get(type);
                if (registered != null) {
                    return type.cast(registered);
                }
                throw new IllegalStateException("Missing dependency: " + type.getName());
            }
            @Override public <T> Optional<T> getOptionalDependency(Class<T> type) { return Optional.ofNullable(type.cast(registeredServices.get(type))); }
            @Override public <T> boolean hasDependency(Class<T> type) { return type.equals(KernelConfigResolver.class) || registeredServices.containsKey(type); }
            @Override public <T> T getDependency(String name, Class<T> type) { return null; }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public <T> void registerService(Class<T> type, T service) { registeredServices.put(type, service); }
            @Override public KernelTenantContext getTenantContext() { return null; }
            @Override public KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> Optional<T> getOptionalConfig(String key, Class<T> type) { return Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return Runnable::run; }
            @Override public <T> Optional<T> getCapability(String capabilityId) { return Optional.empty(); }
        };
    }

    private String patientJson() {
        return """
            {"resourceType":"Patient","id":"patient-1","name":[{"text":"Aarati Shrestha"}]}
            """;
    }

    private String observationJson() {
        return """
            {"resourceType":"Observation","id":"obs-1","status":"final","subject":{"reference":"Patient/patient-1"},"code":{"coding":[{"system":"http://loinc.org","code":"4548-4"}],"text":"HbA1c"},"valueQuantity":{"value":6.8,"unit":"%"}}
            """;
    }

    private String medicationJson() {
        return """
            {"resourceType":"MedicationRequest","id":"med-1","status":"active","intent":"order","subject":{"reference":"Patient/patient-1"},"medicationCodeableConcept":{"coding":[{"system":"http://www.nlm.nih.gov/research/umls/rxnorm","code":"860975"}],"text":"Metformin"},"dosageInstruction":[{"text":"1 tablet twice daily"}]}
            """;
    }
}