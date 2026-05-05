package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.contracts.ModuleContract;
import com.ghatana.kernel.contracts.SchemaRegistration;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.phr.api.FhirController;
import com.ghatana.phr.api.NepalHieController;
import com.ghatana.phr.api.PhrHttpServer;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.hie.HttpNepalHieClient;
import com.ghatana.phr.hie.NepalHieConfig;
import com.ghatana.phr.hie.NepalHieIntegrationService;
import com.ghatana.phr.hie.NepalHieMessageBuilder;
import com.ghatana.phr.hl7.Hl7LabResultIntegrationService;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ClinicalDecisionSupportService;
import com.ghatana.phr.kernel.service.ClinicalNoteService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewWorkflow;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.PhrNotificationDeliveryChannelsFactory;
import com.ghatana.phr.kernel.service.PhrNotificationOutboxDispatcher;
import com.ghatana.phr.kernel.service.PhrNotificationSender;
import com.ghatana.phr.kernel.service.PhrServiceCatalog;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.http.HttpClient;

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

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final List<KernelLifecycleAware> services = new ArrayList<>();
    private volatile KernelContext context;
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
    public void initialize(KernelContext context) {
        if (initialized.getAndSet(true)) {
            throw new IllegalStateException("PhrKernelModule already initialized");
        }
        
        this.context = context;
        
        // Validate dependencies
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
        
        // Initialize configuration
        context.getDependency(KernelConfigResolver.class);
        
        // Register event handlers
        registerEventHandlers(context);
        
        // Register services
        registerServices(context);
        
        // Register module contract
        registerModuleContract(context);
    }

    @Override
    public Promise<Void> start() {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Module not initialized"));
        }
        
        started.set(true);
        List<Promise<Void>> startPromises = new ArrayList<>();
        for (KernelLifecycleAware service : services) {
            startPromises.add(service.start());
        }
        return Promises.all(startPromises);
    }

    @Override
    public Promise<Void> stop() {
        started.set(false);
        List<Promise<Void>> stopPromises = new ArrayList<>();
        // Stop in reverse order
        for (int i = services.size() - 1; i >= 0; i--) {
            stopPromises.add(services.get(i).stop());
        }
        return Promises.all(stopPromises);
    }

    @Override
    public HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return HealthStatus.unhealthy("Module not initialized");
        }
        if (!started.get()) {
            return HealthStatus.unhealthy("Module not started");
        }
        
        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.HEALTHY)
            .withMessage("PHR module operational");
        
        boolean allHealthy = true;
        for (KernelLifecycleAware service : services) {
            boolean serviceHealthy = service.isHealthy();
            builder.withCheck(
                service.getName(),
                serviceHealthy ? HealthStatus.Status.HEALTHY : HealthStatus.Status.UNHEALTHY,
                serviceHealthy ? "Operational" : "Unhealthy",
                0
            );
            allHealthy = allHealthy && serviceHealthy;
        }
        
        if (!allHealthy) {
            builder.withStatus(HealthStatus.Status.UNHEALTHY);
        }
        
        return builder.build();
    }

    public boolean providesCapability(KernelCapability capability) {
        return getCapabilities().contains(capability);
    }

    private void validateDependencies(KernelContext context) {
        if (!context.hasDependency(KernelConfigResolver.class)) {
            throw new IllegalStateException("KernelConfigResolver not available");
        }
    }

    private void initializeConfiguration(KernelContext context) {
        context.getDependency(KernelConfigResolver.class);
    }

    private void registerEventHandlers(KernelContext context) {
        // Register PHR-specific event handlers when concrete events are promoted to kernel contracts.
    }

    private void registerServices(KernelContext context) {
        DurablePhrNotificationSender notificationSender = new DurablePhrNotificationSender(context);
        PhrNotificationOutboxDispatcher notificationDispatcher = new PhrNotificationOutboxDispatcher(
            context,
            PhrNotificationDeliveryChannelsFactory.fromContext(context)
        );
        context.registerService(PhrNotificationSender.class, notificationSender);
        context.registerService(PhrNotificationOutboxDispatcher.class, notificationDispatcher);

        PatientRecordService patientRecords = new PatientRecordService(context);
        ConsentManagementService consent = new ConsentManagementService(context);
        DocumentService documents = new DocumentService(context);
        AppointmentService appointments = new AppointmentService(context);
        MedicationService medications = new MedicationService(context);
        LabResultService labResults = new LabResultService(context);
        ImmunizationService immunizations = new ImmunizationService(context);
        PhrFhirR4Server fhirServer = new PhrFhirR4Server(context);
        FhirController fhirController = new FhirController(fhirServer);
        NepalHieIntegrationService nepalHieIntegration = new NepalHieIntegrationService(
            fhirServer,
            new HttpNepalHieClient(HttpClient.newHttpClient(), context.getExecutor("phr-nepal-hie"), NepalHieConfig.fromEnvironment()),
            new NepalHieMessageBuilder(),
            NepalHieConfig.fromEnvironment()
        );
        NepalHieController nepalHieController = new NepalHieController(nepalHieIntegration);
        Hl7LabResultIntegrationService hl7LabIntegration = new Hl7LabResultIntegrationService(labResults);
        ClinicalNoteService clinicalNotes = new ClinicalNoteService(context);
        ClinicalDecisionSupportService clinicalDecisionSupport = new ClinicalDecisionSupportService();
        ImagingService imaging = new ImagingService(context);
        ReferralService referrals = new ReferralService(context);
        BillingService billing = new BillingService(context);
        TelemedicineService telemedicine = new TelemedicineService(context);
        CaregiverService caregivers = new CaregiverService(context);
        EmergencyAccessReviewWorkflow emergencyReview = EmergencyAccessReviewWorkflow.fromContext(context);
        EmergencyAccessLogService emergencyAccess = new EmergencyAccessLogService(context, emergencyReview);
        PhrHttpServer phrHttpServer = new PhrHttpServer(fhirServer, fhirController, billing);
        context.registerService(PhrHttpServer.class, phrHttpServer);
        context.registerService(PhrFhirR4Server.class, fhirServer);
        context.registerService(FhirController.class, fhirController);
        context.registerService(NepalHieIntegrationService.class, nepalHieIntegration);
        context.registerService(NepalHieController.class, nepalHieController);
        context.registerService(Hl7LabResultIntegrationService.class, hl7LabIntegration);

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
            new PhrServiceCatalog.EmergencyServices(emergencyAccess, emergencyReview)
        );

        services.add(notificationSender);
        services.add(notificationDispatcher);
        services.add(patientRecords);
        services.add(consent);
        services.add(documents);
        services.add(appointments);
        services.add(medications);
        services.add(labResults);
        services.add(immunizations);
        services.add(phrHttpServer);
        services.add(fhirServer);
        services.add(nepalHieIntegration);
        services.add(hl7LabIntegration);
        services.add(clinicalNotes);
        services.add(clinicalDecisionSupport);
        services.add(imaging);
        services.add(referrals);
        services.add(billing);
        services.add(telemedicine);
        services.add(caregivers);
        services.add(emergencyAccess);
    }

    private void registerModuleContract(KernelContext context) {
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

    public PhrServiceCatalog getServiceCatalog() {
        return serviceCatalog;
    }
}
