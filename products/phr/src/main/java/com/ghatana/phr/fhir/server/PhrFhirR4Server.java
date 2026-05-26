package com.ghatana.phr.fhir.server;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.api.PhrApiResponse;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Kernel-managed FHIR R4 create, read, and search server for the PHR module
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PhrFhirR4Server implements KernelLifecycleAware {

    private final FhirInteropKernelPlugin plugin;
    private final FhirResourceProviderRegistry providerRegistry;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public PhrFhirR4Server(KernelContext context) {
        this.plugin = new FhirInteropKernelPlugin();
        this.plugin.initialize(context);
        this.providerRegistry = new FhirResourceProviderRegistry(
            new PatientFhirResourceProvider(plugin, plugin),
            new ObservationFhirResourceProvider(plugin, plugin),
            new MedicationRequestFhirResourceProvider(plugin, plugin),
            new ImmunizationFhirResourceProvider(plugin, plugin),
            new DiagnosticReportFhirResourceProvider(plugin, plugin),
            new DocumentReferenceFhirResourceProvider(plugin, plugin)
        );
    }

    @Override
    public Promise<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }
        return plugin.install().then($ -> plugin.start());
    }

    @Override
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }
        return plugin.stop().then($ -> plugin.uninstall());
    }

    @Override
    public boolean isHealthy() {
        return started.get() && plugin.getHealthStatus().isHealthy();
    }

    @Override
    public String getName() {
        return "phr-fhir-r4-server";
    }

    public FhirResourceProviderRegistry providerRegistry() {
        return providerRegistry;
    }

    public Promise<PhrApiResponse> createResource(String resourceType, String resourceJson) {
        Optional<FhirResourceProvider> provider = providerRegistry.find(resourceType);
        if (provider.isEmpty()) {
            return Promise.of(notFound(resourceType));
        }

        try {
            return provider.get().create(resourceJson)
                .map(resource -> PhrApiResponse.fhirJson(201, resource.getJson()));
        } catch (IllegalArgumentException exception) {
            return Promise.of(PhrApiResponse.fhirJson(400,
                FhirBundleSupport.operationOutcome("error", "invalid", exception.getMessage())));
        }
    }

    public Promise<PhrApiResponse> getResource(String resourceType, String resourceId) {
        Optional<FhirResourceProvider> provider = providerRegistry.find(resourceType);
        if (provider.isEmpty()) {
            return Promise.of(notFound(resourceType));
        }

        return provider.get().read(resourceId)
            .map(resource -> resource
                .map(value -> PhrApiResponse.fhirJson(200, value.getJson()))
                .orElseGet(() -> PhrApiResponse.fhirJson(404,
                    FhirBundleSupport.operationOutcome("error", "not-found", "FHIR resource not found"))));
    }

    public Promise<PhrApiResponse> searchResources(String resourceType, Map<String, String> searchParams) {
        Optional<FhirResourceProvider> provider = providerRegistry.find(resourceType);
        if (provider.isEmpty()) {
            return Promise.of(notFound(resourceType));
        }

        return provider.get().search(searchParams)
            .map(result -> PhrApiResponse.fhirJson(200, FhirBundleSupport.toSearchBundle(result)));
    }

    private PhrApiResponse notFound(String resourceType) {
        return PhrApiResponse.fhirJson(404,
            FhirBundleSupport.operationOutcome("error", "not-found", "Unsupported resource type: " + resourceType));
    }
}
