package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.contract.ContractRegistry;
import com.ghatana.kernel.contract.ContractValidator;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ClinicalNoteService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
// PhrCapabilities owns all PHR capability constants — per CODE_ALIGNMENT_SPECIFICATION §2.2
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>PHR-domain capabilities are declared in {@link PhrCapabilities}, not in
 * {@code KernelCapability.Products} (which is deprecated — per
 * KERNEL_CANONICALIZATION_DECISIONS.md §D1 and CODE_ALIGNMENT_SPECIFICATION §2.2).</p>
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
            // PHR-owned healthcare capabilities — defined in PhrCapabilities, NOT in the
            // deprecated KernelCapability.Products (per KERNEL_CANONICALIZATION_DECISIONS §D1).
            PhrCapabilities.PATIENT_RECORDS,
            PhrCapabilities.CONSENT_MANAGEMENT,
            PhrCapabilities.FHIR_INTEROP,
            PhrCapabilities.CLINICAL_DOCUMENTS,
            PhrCapabilities.MEDICATION_MANAGEMENT,

            // Core kernel capabilities reused by PHR (from KernelCapability.Core)
            KernelCapability.Core.USER_AUTHENTICATION,
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.API_FRAMEWORK,
            KernelCapability.Core.WORKFLOW_ENGINE
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
        registerModuleContract();
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
            } else if (service instanceof MedicationService ms) {
                startPromises.add(ms.start());
            } else if (service instanceof LabResultService lrs) {
                startPromises.add(lrs.start());
            } else if (service instanceof ImmunizationService is) {
                startPromises.add(is.start());
            } else if (service instanceof ClinicalNoteService cns) {
                startPromises.add(cns.start());
            } else if (service instanceof ImagingService ims) {
                startPromises.add(ims.start());
            } else if (service instanceof ReferralService rs) {
                startPromises.add(rs.start());
            } else if (service instanceof BillingService bs) {
                startPromises.add(bs.start());
            } else if (service instanceof TelemedicineService ts) {
                startPromises.add(ts.start());
            } else if (service instanceof CaregiverService cs) {
                startPromises.add(cs.start());
            } else if (service instanceof EmergencyAccessLogService eals) {
                startPromises.add(eals.start());
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
            } else if (service instanceof MedicationService ms) {
                stopPromises.add(ms.stop());
            } else if (service instanceof LabResultService lrs) {
                stopPromises.add(lrs.stop());
            } else if (service instanceof ImmunizationService is) {
                stopPromises.add(is.stop());
            } else if (service instanceof ClinicalNoteService cns) {
                stopPromises.add(cns.stop());
            } else if (service instanceof ImagingService ims) {
                stopPromises.add(ims.stop());
            } else if (service instanceof ReferralService rs) {
                stopPromises.add(rs.stop());
            } else if (service instanceof BillingService bs) {
                stopPromises.add(bs.stop());
            } else if (service instanceof TelemedicineService ts) {
                stopPromises.add(ts.stop());
            } else if (service instanceof CaregiverService cs) {
                stopPromises.add(cs.stop());
            } else if (service instanceof EmergencyAccessLogService eals) {
                stopPromises.add(eals.stop());
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
            } else if (service instanceof MedicationService ms) {
                name = ms.getName();
                healthy = ms.isHealthy();
            } else if (service instanceof LabResultService lrs) {
                name = lrs.getName();
                healthy = lrs.isHealthy();
            } else if (service instanceof ImmunizationService is) {
                name = is.getName();
                healthy = is.isHealthy();
            } else if (service instanceof ClinicalNoteService cns) {
                name = cns.getName();
                healthy = cns.isHealthy();
            } else if (service instanceof ImagingService ims) {
                name = ims.getName();
                healthy = ims.isHealthy();
            } else if (service instanceof ReferralService rs) {
                name = rs.getName();
                healthy = rs.isHealthy();
            } else if (service instanceof BillingService bs) {
                name = bs.getName();
                healthy = bs.isHealthy();
            } else if (service instanceof TelemedicineService ts) {
                name = ts.getName();
                healthy = ts.isHealthy();
            } else if (service instanceof CaregiverService cs) {
                name = cs.getName();
                healthy = cs.isHealthy();
            } else if (service instanceof EmergencyAccessLogService eals) {
                name = eals.getName();
                healthy = eals.isHealthy();
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
        serviceInstances.add(new MedicationService(context));
        serviceInstances.add(new LabResultService(context));
        serviceInstances.add(new ImmunizationService(context));
        serviceInstances.add(new ClinicalNoteService(context));
        serviceInstances.add(new ImagingService(context));
        serviceInstances.add(new ReferralService(context));
        serviceInstances.add(new BillingService(context));
        serviceInstances.add(new TelemedicineService(context));
        serviceInstances.add(new CaregiverService(context));
        serviceInstances.add(new EmergencyAccessLogService(context));
    }

    private void registerModuleContract() {
        if (context.hasDependency(ContractRegistry.class)) {
            ContractRegistry registry = context.getDependency(ContractRegistry.class);
            registry.registerModuleContract(new ContractValidator.ModuleContract(
                MODULE_ID, VERSION, getCapabilities(), getDependencies(), Map.of()
            ));

            // ── Core dataset schema contracts (existing) ───────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.patient.records", VERSION, "json",
                Map.of("fields", List.of("patientId", "name", "dateOfBirth", "gender", "bloodType")),
                Map.of("owner", MODULE_ID)
            ));
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.consent.grants", VERSION, "json",
                Map.of("fields", List.of("grantId", "patientId", "recipientId", "resourceType", "status", "expiresAt")),
                Map.of("owner", MODULE_ID)
            ));

            // ── Medication service datasets ─────────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.medications", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "medicationCode", "status", "prescribedAt")),
                Map.of("owner", MODULE_ID, "retention", "10years")
            ));

            // ── Lab result service datasets ─────────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.lab.results", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "loincCode", "status", "resultedAt")),
                Map.of("owner", MODULE_ID, "retention", "25years")
            ));
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.lab.panels", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "status", "orderedAt")),
                Map.of("owner", MODULE_ID, "retention", "25years")
            ));

            // ── Immunization service datasets ───────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.immunizations", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "cvxCode", "status", "administeredAt")),
                Map.of("owner", MODULE_ID, "retention", "permanent")
            ));

            // ── Clinical note service datasets ──────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.clinical.notes", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "noteType", "status", "createdAt")),
                Map.of("owner", MODULE_ID, "retention", "25years")
            ));

            // ── Imaging service datasets ────────────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.imaging.orders", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "modalityCode", "status", "orderedAt")),
                Map.of("owner", MODULE_ID, "retention", "25years")
            ));
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.imaging.studies", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "dcmStudyInstanceUid", "status", "studiedAt")),
                Map.of("owner", MODULE_ID, "retention", "permanent")
            ));

            // ── Referral service datasets ───────────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.referrals", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "status", "urgency", "createdAt")),
                Map.of("owner", MODULE_ID, "retention", "10years")
            ));

            // ── Billing service datasets ────────────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.billing.encounters", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "status", "totalAmount", "createdAt")),
                Map.of("owner", MODULE_ID, "retention", "10years")
            ));
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.billing.claims", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "encounterId", "status", "submittedAt")),
                Map.of("owner", MODULE_ID, "retention", "10years")
            ));

            // ── Telemedicine service datasets ───────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.telemedicine.sessions", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "providerId", "status", "scheduledAt")),
                Map.of("owner", MODULE_ID, "retention", "10years")
            ));

            // ── Caregiver service datasets ──────────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.caregiver.relationships", VERSION, "json",
                Map.of("fields", List.of("id", "caregiverId", "patientId", "status", "createdAt")),
                Map.of("owner", MODULE_ID, "retention", "10years")
            ));

            // ── Emergency access log datasets ───────────────────────────────────────
            registry.registerSchemaContract(new ContractValidator.SchemaContract(
                "phr.emergency.access.log", VERSION, "json",
                Map.of("fields", List.of("id", "patientId", "accessorId", "reviewStatus", "accessedAt")),
                Map.of("owner", MODULE_ID, "retention", "permanent")
            ));
        }
    }
}
