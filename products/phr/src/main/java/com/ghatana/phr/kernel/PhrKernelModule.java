package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.AppointmentService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Top-level kernel module for PHR (Personal Health Record) product.
 *
 * <p>Composes 10 sub-modules: patient records, consent, document, appointment,
 * medication, billing, FHIR interop, clinical consent, imaging, referrals.</p>
 *
 * <p>This module implements the PHR-specific kernel integration following
 * Nepal healthcare regulations (Directive 2081, Privacy Act 2075) and
 * FHIR R4 standards.</p>
 *
 * @doc.type class
 * @doc.purpose PHR product kernel module — healthcare-specific composition root
 * @doc.layer product
 * @doc.pattern Service, Facade
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class PhrKernelModule implements KernelModule {

    private static final String MODULE_ID = "phr-core";
    private static final String VERSION = "1.0.0";

    private volatile KernelContext context;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<Object> serviceInstances = new ArrayList<>();

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            // PHR-specific capabilities (healthcare domain)
            KernelCapability.Products.PATIENT_RECORDS,
            KernelCapability.Products.CONSENT_MANAGEMENT,
            KernelCapability.Products.FHIR_INTEROP,
            KernelCapability.Products.CLINICAL_DOCUMENTS,
            KernelCapability.Products.MEDICATION_MANAGEMENT,

            // Shared capabilities used by PHR
            KernelCapability.Products.USER_AUTHENTICATION,
            KernelCapability.Products.DATA_STORAGE,
            KernelCapability.Products.API_FRAMEWORK,
            KernelCapability.Products.WORKFLOW_ENGINE,
            KernelCapability.Products.NOTIFICATION_SERVICE
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            new KernelDependency("kernel-core", "1.0.0", KernelDependency.DependencyType.MODULE, false),
            new KernelDependency("data-storage", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("user-authentication", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("workflow-engine", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false),
            new KernelDependency("fhir-server", "4.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, true),
            new KernelDependency("hl7-interface", "2.5", KernelDependency.DependencyType.EXTERNAL_SERVICE, true)
        );
    }

    @Override
    public void initialize(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException(MODULE_ID + " already initialized");
        }

        this.context = context;

        validateDependencies();
        initializeConfiguration();
        registerEventHandlers();
        initializeServices();
    }

    @Override
    public Promise<Void> start() {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Module not initialized"));
        }

        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }

        List<Promise<Void>> startPromises = new ArrayList<>();
        for (Object service : serviceInstances) {
            if (service instanceof PatientRecordService prs) {
                startPromises.add(prs.start());
            } else if (service instanceof ConsentManagementService cms) {
                startPromises.add(cms.start());
            } else if (service instanceof DocumentService ds) {
                startPromises.add(ds.start());
            } else if (service instanceof AppointmentService as) {
                startPromises.add(as.start());
            }
        }

        return Promises.all(startPromises)
            .whenException(e -> started.set(false));
    }

    @Override
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }

        List<Promise<Void>> stopPromises = new ArrayList<>();
        for (Object service : serviceInstances) {
            if (service instanceof PatientRecordService prs) {
                stopPromises.add(prs.stop());
            } else if (service instanceof ConsentManagementService cms) {
                stopPromises.add(cms.stop());
            } else if (service instanceof DocumentService ds) {
                stopPromises.add(ds.stop());
            } else if (service instanceof AppointmentService as) {
                stopPromises.add(as.stop());
            }
        }

        return Promises.all(stopPromises);
    }

    @Override
    public HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return HealthStatus.unhealthy("Module not initialized");
        }

        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.HEALTHY)
            .withMessage("PHR module operational");

        for (Object service : serviceInstances) {
            String name;
            boolean healthy;
            if (service instanceof PatientRecordService prs) {
                name = prs.getName();
                healthy = prs.isHealthy();
            } else if (service instanceof ConsentManagementService cms) {
                name = cms.getName();
                healthy = cms.isHealthy();
            } else if (service instanceof DocumentService ds) {
                name = ds.getName();
                healthy = ds.isHealthy();
            } else if (service instanceof AppointmentService as) {
                name = as.getName();
                healthy = as.isHealthy();
            } else {
                continue;
            }

            builder.withCheck(name,
                healthy ? HealthStatus.Status.HEALTHY : HealthStatus.Status.UNHEALTHY,
                healthy ? "Service healthy" : "Service unhealthy",
                0);
        }

        return builder.build();
    }

    private void validateDependencies() {
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
    }

    private void initializeConfiguration() {
        // Initialize PHR configuration from kernel config resolver
        KernelConfigResolver config = context.getDependency(KernelConfigResolver.class);
        // Load PHR-specific settings
    }

    private void registerEventHandlers() {
        // Register PHR-specific event handlers
        // - Patient record events
        // - Consent change events
        // - Appointment notifications
    }

    private void initializeServices() {
        serviceInstances.add(new PatientRecordService(context));
        serviceInstances.add(new ConsentManagementService(context));
        serviceInstances.add(new DocumentService(context));
        serviceInstances.add(new AppointmentService(context));
        // Additional services can be added here
    }
}
