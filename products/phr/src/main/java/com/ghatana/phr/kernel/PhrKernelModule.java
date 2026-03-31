package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.contracts.ModuleContract;
import com.ghatana.kernel.contracts.SchemaRegistration;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.AbstractKernelModule;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ClinicalDecisionSupportService;
import com.ghatana.phr.kernel.service.ClinicalNoteService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.PhrServiceCatalog;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class PhrKernelModule extends AbstractKernelModule {

    private static final String MODULE_ID = "phr-core";
    private static final String VERSION = "1.0.0";
    private volatile PhrServiceCatalog serviceCatalog;

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
            PhrCapabilities.PATIENT_RECORDS,
            PhrCapabilities.CONSENT_MANAGEMENT,
            PhrCapabilities.FHIR_INTEROP,
            PhrCapabilities.CLINICAL_DOCUMENTS,
            PhrCapabilities.MEDICATION_MANAGEMENT,
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
    protected void validateDependencies(KernelContext context) {
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
    }

    @Override
    protected void initializeConfiguration(KernelContext context) {
        context.getDependency(KernelConfigResolver.class);
    }

    @Override
    protected void registerEventHandlers(KernelContext context) {
        // Register PHR-specific event handlers when concrete events are promoted to kernel contracts.
    }

    @Override
    protected void registerServices(List<KernelLifecycleAware> services, KernelContext context) {
        PatientRecordService patientRecords = new PatientRecordService(context);
        ConsentManagementService consent = new ConsentManagementService(context);
        DocumentService documents = new DocumentService(context);
        AppointmentService appointments = new AppointmentService(context);
        MedicationService medications = new MedicationService(context);
        LabResultService labResults = new LabResultService(context);
        ImmunizationService immunizations = new ImmunizationService(context);
        ClinicalNoteService clinicalNotes = new ClinicalNoteService(context);
        ClinicalDecisionSupportService clinicalDecisionSupport = new ClinicalDecisionSupportService();
        ImagingService imaging = new ImagingService(context);
        ReferralService referrals = new ReferralService(context);
        BillingService billing = new BillingService(context);
        TelemedicineService telemedicine = new TelemedicineService(context);
        CaregiverService caregivers = new CaregiverService(context);
        EmergencyAccessLogService emergencyAccess = new EmergencyAccessLogService(context);

        serviceCatalog = new PhrServiceCatalog(
            new PhrServiceCatalog.ClinicalServices(
                patientRecords,
                clinicalNotes,
                labResults,
                imaging,
                documents,
                clinicalDecisionSupport
            ),
            new PhrServiceCatalog.AdministrativeServices(appointments, billing, referrals, telemedicine),
            new PhrServiceCatalog.PatientServices(consent, medications, immunizations, caregivers),
            new PhrServiceCatalog.EmergencyServices(emergencyAccess)
        );

        services.add(patientRecords);
        services.add(consent);
        services.add(documents);
        services.add(appointments);
        services.add(medications);
        services.add(labResults);
        services.add(immunizations);
        services.add(clinicalNotes);
        services.add(clinicalDecisionSupport);
        services.add(imaging);
        services.add(referrals);
        services.add(billing);
        services.add(telemedicine);
        services.add(caregivers);
        services.add(emergencyAccess);
    }

    @Override
    protected void registerModuleContract(KernelContext context) {
        if (!context.hasDependency(ContractRegistry.class)) {
            return;
        }

        ContractRegistry registry = context.getDependency(ContractRegistry.class);
        registry.registerModuleContract(new ModuleContract(
            MODULE_ID, VERSION, getCapabilities(), getDependencies(), Map.of()
        ));

        registry.registerSchemaContract(new SchemaRegistration(
            "phr.patient.records", VERSION, "json",
            Map.of("fields", List.of("patientId", "name", "dateOfBirth", "gender", "bloodType")),
            Map.of("owner", MODULE_ID)
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.consent.grants", VERSION, "json",
            Map.of("fields", List.of("grantId", "patientId", "recipientId", "resourceType", "status", "expiresAt")),
            Map.of("owner", MODULE_ID)
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.medications", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "medicationCode", "status", "prescribedAt")),
            Map.of("owner", MODULE_ID, "retention", "10years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.lab.results", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "loincCode", "status", "resultedAt")),
            Map.of("owner", MODULE_ID, "retention", "25years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.lab.panels", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "status", "orderedAt")),
            Map.of("owner", MODULE_ID, "retention", "25years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.immunizations", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "cvxCode", "status", "administeredAt")),
            Map.of("owner", MODULE_ID, "retention", "permanent")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.clinical.notes", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "noteType", "status", "createdAt")),
            Map.of("owner", MODULE_ID, "retention", "25years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.imaging.orders", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "modalityCode", "status", "orderedAt")),
            Map.of("owner", MODULE_ID, "retention", "25years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.imaging.studies", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "dcmStudyInstanceUid", "status", "studiedAt")),
            Map.of("owner", MODULE_ID, "retention", "permanent")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.referrals", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "status", "urgency", "createdAt")),
            Map.of("owner", MODULE_ID, "retention", "10years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.billing.encounters", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "status", "totalAmount", "createdAt")),
            Map.of("owner", MODULE_ID, "retention", "10years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.billing.claims", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "encounterId", "status", "submittedAt")),
            Map.of("owner", MODULE_ID, "retention", "10years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.telemedicine.sessions", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "providerId", "status", "scheduledAt")),
            Map.of("owner", MODULE_ID, "retention", "10years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.caregiver.relationships", VERSION, "json",
            Map.of("fields", List.of("id", "caregiverId", "patientId", "status", "createdAt")),
            Map.of("owner", MODULE_ID, "retention", "10years")
        ));
        registry.registerSchemaContract(new SchemaRegistration(
            "phr.emergency.access.log", VERSION, "json",
            Map.of("fields", List.of("id", "patientId", "accessorId", "reviewStatus", "accessedAt")),
            Map.of("owner", MODULE_ID, "retention", "permanent")
        ));
    }

    @Override
    protected String getHealthyMessage() {
        return "PHR module operational";
    }

    public PhrServiceCatalog getServiceCatalog() {
        return serviceCatalog;
    }
}
