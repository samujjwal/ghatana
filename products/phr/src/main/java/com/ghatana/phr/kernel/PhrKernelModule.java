package com.ghatana.phr.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.contracts.ContractRegistry;
import com.ghatana.kernel.contracts.ModuleContract;
import com.ghatana.kernel.contracts.SchemaRegistration;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.release.FileBasedReleaseReadinessRuntimeService;
import com.ghatana.kernel.release.ReleaseReadinessRuntimeService;
import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.http.security.RoleEvaluator;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import com.ghatana.phr.api.FhirController;
import com.ghatana.phr.api.NepalHieController;
import com.ghatana.phr.api.PhrHttpServer;
import com.ghatana.phr.api.routes.PhrAdministrativeRoutes;
import com.ghatana.phr.api.routes.PhrAuditRoutes;
import com.ghatana.phr.api.routes.PhrAuthRoutes;
import com.ghatana.phr.api.routes.PhrCaregiverRoutes;
import com.ghatana.phr.api.routes.PhrClinicalRoutes;
import com.ghatana.phr.api.routes.PhrConsentRoutes;
import com.ghatana.phr.api.routes.PhrDashboardRoutes;
import com.ghatana.phr.api.routes.PhrDocumentImagingRoutes;
import com.ghatana.phr.api.routes.PhrEntitlementRoutes;
import com.ghatana.phr.api.routes.PhrEmergencyRoutes;
import com.ghatana.phr.api.routes.PhrFchvRoutes;
import com.ghatana.phr.api.routes.PhrFhirRoutes;
import com.ghatana.phr.api.routes.PhrHealthRoutes;
import com.ghatana.phr.api.routes.PhrMobileRoutes;
import com.ghatana.phr.api.routes.PhrNotificationRoutes;
import com.ghatana.phr.api.routes.PhrPatientProfileRoutes;
import com.ghatana.phr.api.routes.PhrPatientRecordRoutes;
import com.ghatana.phr.api.routes.PhrProviderRoutes;
import com.ghatana.phr.api.routes.PhrReleaseReadinessRoutes;
import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.application.clinical.ClinicalServiceImpl;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.hie.HttpNepalHieClient;
import com.ghatana.phr.hie.NepalHieConfig;
import com.ghatana.phr.hie.NepalHieIntegrationService;
import com.ghatana.phr.hie.NepalHieMessageBuilder;
import com.ghatana.phr.hl7.Hl7LabResultIntegrationService;
import com.ghatana.phr.kernel.evidence.FileBackedPhrEvidenceOutbox;
import com.ghatana.phr.kernel.evidence.PhrEvidenceOutboxDispatcher;
import com.ghatana.phr.kernel.event.PhrAuditEvent;
import com.ghatana.phr.kernel.event.PhrConsentEvent;
import com.ghatana.phr.kernel.event.PhrLifecycleEvent;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ClinicalDecisionSupportService;
import com.ghatana.phr.kernel.service.ClinicalNoteService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.EmergencyAccessNotificationSender;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewAuditLogger;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewWorkflow;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.KernelEventEmergencyAccessNotificationSender;
import com.ghatana.phr.kernel.service.KernelEventEmergencyAccessReviewAuditLogger;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.PhrNotificationDeliveryChannelsFactory;
import com.ghatana.phr.kernel.service.PhrNotificationOutboxDispatcher;
import com.ghatana.phr.kernel.service.PhrNotificationSender;
import com.ghatana.phr.kernel.service.PhrServiceCatalog;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.phr.observability.PHRAuditTrailServiceImpl;
import com.ghatana.phr.repository.UserRepository;
import com.ghatana.phr.security.PHRSecurityManagerImpl;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
 * <p>PHR-domain capabilities are declared in {@link PhrCapabilities}, keeping
 * product-owned capability IDs out of generic Kernel capability containers.</p>
 *
 * @doc.type class
 * @doc.purpose PHR product kernel module — healthcare-specific composition root
 * @doc.layer product
 * @doc.pattern Service, Facade
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class PhrKernelModule implements KernelModule {

    private static final Logger LOG = LoggerFactory.getLogger(PhrKernelModule.class);
    private static final String MODULE_ID = "phr-core";
    private static final String VERSION = "1.0.0";
    private static final String DATASET_LIFECYCLE = "phr.lifecycle.evidence";
    private static final String DATASET_AUDIT = "phr.audit.evidence";
    private static final String DATASET_CONSENT = "phr.consent.evidence";
    private static final ObjectMapper EVENT_OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final int MAX_EVIDENCE_WRITE_RETRIES = 3;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong evidenceWriteFailureCount = new AtomicLong(0);
    private final List<KernelLifecycleAware> services = new ArrayList<>();
    private volatile KernelContext context;
    private volatile PhrServiceCatalog serviceCatalog;
    private volatile PhrEvidenceOutboxDispatcher evidenceOutboxDispatcher;

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

        if (evidenceOutboxDispatcher != null) {
            HealthStatus evidenceHealth = evidenceOutboxDispatcher.getHealthStatus();
            builder.withCheck(
                "phr-regulated-evidence-outbox",
                evidenceHealth.getStatus(),
                evidenceHealth.getMessage(),
                0
            );
            builder.withDetail("pendingEvidenceWrites", evidenceOutboxDispatcher.pendingCount());
            builder.withDetail("deadLetterEvidenceWrites", evidenceOutboxDispatcher.deadLetterCount());
            if (evidenceHealth.isUnhealthy()) {
                builder.withStatus(HealthStatus.Status.UNHEALTHY);
            } else if (evidenceHealth.isDegraded() && allHealthy) {
                builder.withStatus(HealthStatus.Status.DEGRADED);
            }
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
        // Register PHR lifecycle event handler
        context.registerEventHandler(
            PhrLifecycleEvent.class,
            (PhrLifecycleEvent event) -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("schemaVersion", "1.0.0");
                payload.put("eventType", "phr.lifecycle.phase-transition");
                payload.put("eventId", nullable(event.eventId()));
                payload.put("productId", nullable(event.productId()));
                payload.put("phase", nullable(event.phase()));
                payload.put("status", nullable(event.status()));
                payload.put("runId", nullable(event.runId()));
                payload.put("correlationId", nullable(event.correlationId()));
                payload.put("environment", nullable(event.environment()));
                payload.put("tenantId", nullable(event.tenantId()));
                payload.put("occurredAt", toIso(event.timestamp()));

                persistEventEvidence(DATASET_LIFECYCLE, event.eventId(), payload, event.timestamp(), event.correlationId(), event.tenantId());
            }
        );

        // Register PHR audit event handler
        context.registerEventHandler(
            PhrAuditEvent.class,
            (PhrAuditEvent event) -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("schemaVersion", "1.0.0");
                payload.put("eventType", "phr.audit.trail");
                payload.put("eventId", nullable(event.eventId()));
                payload.put("productId", nullable(event.productId()));
                payload.put("auditType", nullable(event.auditType()));
                payload.put("action", nullable(event.action()));
                payload.put("resourceType", nullable(event.resourceType()));
                payload.put("resourceId", nullable(event.resourceId()));
                payload.put("actorId", nullable(event.actorId()));
                payload.put("actorRole", nullable(event.actorRole()));
                payload.put("tenantId", nullable(event.tenantId()));
                payload.put("patientId", nullable(event.patientId()));
                payload.put("metadata", event.metadata() == null ? Map.of() : event.metadata());
                payload.put("correlationId", nullable(event.correlationId()));
                payload.put("occurredAt", toIso(event.timestamp()));

                persistEventEvidence(DATASET_AUDIT, event.eventId(), payload, event.timestamp(), event.correlationId(), event.tenantId());
            }
        );

        // Register PHR consent event handler
        context.registerEventHandler(
            PhrConsentEvent.class,
            (PhrConsentEvent event) -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("schemaVersion", "1.0.0");
                payload.put("eventType", "phr.consent.change");
                payload.put("eventId", nullable(event.eventId()));
                payload.put("productId", nullable(event.productId()));
                payload.put("consentType", nullable(event.consentType()));
                payload.put("action", nullable(event.action()));
                payload.put("patientId", nullable(event.patientId()));
                payload.put("recipientId", nullable(event.recipientId()));
                payload.put("resourceType", nullable(event.resourceType()));
                payload.put("purpose", nullable(event.purpose()));
                payload.put("expiresAt", event.expiresAt() == null ? "" : event.expiresAt().toString());
                payload.put("tenantId", nullable(event.tenantId()));
                payload.put("metadata", event.metadata() == null ? Map.of() : event.metadata());
                payload.put("correlationId", nullable(event.correlationId()));
                payload.put("occurredAt", toIso(event.timestamp()));

                persistEventEvidence(DATASET_CONSENT, event.eventId(), payload, event.timestamp(), event.correlationId(), event.tenantId());

                // Trigger cache invalidation for the affected consent
                if (context.hasDependency(DistributedCachePort.class)) {
                    DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> cache = resolveConsentCache(context);
                    String cacheKey = event.patientId() + ":" + event.recipientId();
                    cache.invalidate(cacheKey);
                }
            }
        );
    }

    private void registerServices(KernelContext context) {
        PhrEvidenceOutboxDispatcher evidenceDispatcher = createEvidenceOutboxDispatcher(context);
        evidenceOutboxDispatcher = evidenceDispatcher;
        DurablePhrNotificationSender notificationSender = new DurablePhrNotificationSender(context);
        EmergencyAccessNotificationSender emergencyNotificationSender = new KernelEventEmergencyAccessNotificationSender(context);
        EmergencyAccessReviewAuditLogger emergencyAuditLogger = new KernelEventEmergencyAccessReviewAuditLogger(context);
        PhrNotificationOutboxDispatcher notificationDispatcher = new PhrNotificationOutboxDispatcher(
            context,
            PhrNotificationDeliveryChannelsFactory.fromContext(context)
        );
        DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> consentCache = resolveConsentCache(context);
        context.registerService(PhrNotificationSender.class, notificationSender);
        context.registerService(EmergencyAccessNotificationSender.class, emergencyNotificationSender);
        context.registerService(EmergencyAccessReviewAuditLogger.class, emergencyAuditLogger);
        context.registerService(PhrNotificationOutboxDispatcher.class, notificationDispatcher);
        AuditTrailService auditTrailService = resolveAuditTrailService(context);
        UserRepository userRepository = resolveUserRepository(context);
        KernelSecurityManager securityManager = resolveSecurityManager(context, userRepository);

        PatientRecordService patientRecords = new PatientRecordService(context);
        ConsentManagementService consent = new ConsentManagementService(context, consentCache);
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
        ClinicalService clinicalService = new ClinicalServiceImpl();
        TreatmentRelationshipService treatmentRelationship = new TreatmentRelationshipService(context);
        FchvCommunityAssignmentService fchvAssignment = new FchvCommunityAssignmentService(context);
        EmergencyAccessReviewWorkflow emergencyReview = EmergencyAccessReviewWorkflow.fromContext(context);
        EmergencyAccessLogService emergencyAccess = new EmergencyAccessLogService(context, emergencyReview);
        
        // Create policy evaluator with required services (injected, not static)
        PhrPolicyEvaluator policyEvaluator = new PhrPolicyEvaluator(consent, treatmentRelationship, fchvAssignment);
        context.registerService(PhrPolicyEvaluator.class, policyEvaluator);
        
        // Create route objects with eventloop
        Eventloop eventloop = context.getEventloop();
        PhrFhirRoutes fhirRoutes = new PhrFhirRoutes(eventloop, fhirController);
        PhrDashboardRoutes dashboardRoutes = new PhrDashboardRoutes(eventloop, userRepository);
        PhrPatientRecordRoutes patientRecordRoutes = new PhrPatientRecordRoutes(eventloop, patientRecords, policyEvaluator);
        PhrConsentRoutes consentRoutes = new PhrConsentRoutes(eventloop, consent, policyEvaluator);
        PhrClinicalRoutes clinicalRoutes = new PhrClinicalRoutes(
            eventloop,
            labResults,
            medications,
            immunizations,
            consent,
            policyEvaluator
        );
        PhrEmergencyRoutes emergencyRoutes = new PhrEmergencyRoutes(eventloop, emergencyAccess, treatmentRelationship, policyEvaluator);
        PhrAdministrativeRoutes administrativeRoutes = new PhrAdministrativeRoutes(
            eventloop,
            appointments,
            telemedicine,
            referrals,
            billing,
            consent,
            policyEvaluator
        );
        PhrDocumentImagingRoutes documentImagingRoutes = new PhrDocumentImagingRoutes(
            eventloop,
            documents,
            imaging,
            consent,
            policyEvaluator
        );
        PhrHealthRoutes healthRoutes = new PhrHealthRoutes(eventloop, fhirServer, evidenceDispatcher::getHealthStatus);
        RouteEntitlementEvaluator routeEntitlementEvaluator = new RouteEntitlementEvaluator(new RoleEvaluator.FailClosed());
        IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache = new IdentityAwareBoundedCache<>(1000, 300);
        PhrEntitlementRoutes entitlementRoutes = new PhrEntitlementRoutes(eventloop, routeEntitlementEvaluator, entitlementCache);
        ReleaseReadinessRuntimeService releaseReadinessService = new FileBasedReleaseReadinessRuntimeService();
        PhrReleaseReadinessRoutes releaseReadinessRoutes = new PhrReleaseReadinessRoutes(eventloop, releaseReadinessService);
        PhrMobileRoutes mobileRoutes = new PhrMobileRoutes(
            eventloop,
            patientRecords,
            consent,
            documents,
            notificationSender
        );
        PhrAuditRoutes auditRoutes = new PhrAuditRoutes(eventloop, auditTrailService);
        PhrAuthRoutes authRoutes = new PhrAuthRoutes(eventloop, securityManager, userRepository, auditTrailService);
        PhrProviderRoutes providerRoutes = new PhrProviderRoutes(eventloop, patientRecords, consent, clinicalService);
        PhrCaregiverRoutes caregiverRoutes = new PhrCaregiverRoutes(eventloop, caregivers, patientRecords);
        PhrFchvRoutes fchvRoutes = new PhrFchvRoutes(eventloop);
        PhrNotificationRoutes notificationRoutes = new PhrNotificationRoutes(eventloop, notificationSender);
        PhrPatientProfileRoutes patientProfileRoutes = new PhrPatientProfileRoutes(eventloop, userRepository);
        
        PhrHttpServer phrHttpServer = new PhrHttpServer(
            eventloop,
            fhirRoutes,
            dashboardRoutes,
            patientRecordRoutes,
            consentRoutes,
            clinicalRoutes,
            emergencyRoutes,
            administrativeRoutes,
            documentImagingRoutes,
            releaseReadinessRoutes,
            entitlementRoutes,
            healthRoutes,
            auditRoutes,
            authRoutes,
            providerRoutes,
            caregiverRoutes,
            fchvRoutes,
            mobileRoutes,
            notificationRoutes,
            patientProfileRoutes
        );
        context.registerService(PhrEvidenceOutboxDispatcher.class, evidenceDispatcher);
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

        services.add(evidenceDispatcher);
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
        services.add(treatmentRelationship);
        services.add(fchvAssignment);
        services.add(emergencyAccess);
    }

    private PhrEvidenceOutboxDispatcher createEvidenceOutboxDispatcher(KernelContext context) {
        String configuredPath = context.getOptionalConfig("phr.evidence.outbox.dir", String.class)
            .filter(path -> !path.isBlank())
            .orElseGet(() -> {
                if ("test".equalsIgnoreCase(context.getEnvironment())) {
                    return Path.of(System.getProperty("java.io.tmpdir"), "ghatana", "phr", "evidence-outbox").toString();
                }
                return ".kernel/runtime/phr/evidence-outbox";
            });
        return new PhrEvidenceOutboxDispatcher(
            context,
            new FileBackedPhrEvidenceOutbox(Path.of(configuredPath)),
            MAX_EVIDENCE_WRITE_RETRIES
        );
    }

    private AuditTrailService resolveAuditTrailService(KernelContext context) {
        return context.getOptionalDependency(AuditTrailService.class)
            .orElseGet(() -> {
                if (!context.hasDependency(DataCloudKernelAdapter.class)) {
                    throw new IllegalStateException(
                        "AuditTrailService or DataCloudKernelAdapter dependency required for PhrAuditRoutes"
                    );
                }
                return new PHRAuditTrailServiceImpl(context.getDependency(DataCloudKernelAdapter.class));
            });
    }

    private UserRepository resolveUserRepository(KernelContext context) {
        return context.getOptionalDependency(UserRepository.class)
            .orElseGet(UserRepository::new);
    }

    private KernelSecurityManager resolveSecurityManager(KernelContext context, UserRepository userRepository) {
        return context.getOptionalDependency(KernelSecurityManager.class)
            .orElseGet(() -> new PHRSecurityManagerImpl(userRepository));
    }

    @SuppressWarnings("unchecked")
    private DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> resolveConsentCache(KernelContext context) {
        if (!context.hasDependency(DistributedCachePort.class)) {
            throw new IllegalStateException(
                "DistributedCachePort dependency not available for ConsentManagementService; in-memory consent cache is not allowed in production wiring"
            );
        }

        Object cache = context.getDependency(DistributedCachePort.class);
        if (!(cache instanceof DistributedCachePort<?, ?>)) {
            throw new IllegalStateException("DistributedCachePort dependency has invalid type");
        }

        return (DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry>) cache;
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

    private void persistEventEvidence(
            String datasetId,
            String eventId,
            Map<String, Object> payload,
            Instant timestamp,
            String correlationId,
            String tenantId) {
        byte[] body;
        try {
            body = EVENT_OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception serializationError) {
            long failures = evidenceWriteFailureCount.incrementAndGet();
            LOG.error("[PHR-EVIDENCE-DURABILITY] Failed to serialize PHR event evidence — write will not be retried. "
                    + "dataset={} eventId={} tenantId={} totalFailures={}",
                    datasetId, eventId, tenantId, failures, serializationError);
            return;
        }

        Map<String, String> metadata = Map.of(
            "productId", "phr",
            "eventId", eventId,
            "tenantId", tenantId == null ? "" : tenantId,
            "correlationId", correlationId == null ? "" : correlationId,
            "occurredAt", toIso(timestamp),
            "contentType", "application/json"
        );

        if (evidenceOutboxDispatcher == null) {
            long failures = evidenceWriteFailureCount.incrementAndGet();
            LOG.error("[PHR-EVIDENCE-DURABILITY] PHR evidence outbox unavailable — regulated evidence was not accepted. "
                    + "dataset={} eventId={} tenantId={} correlationId={} totalFailures={}",
                    datasetId, eventId, tenantId, correlationId, failures);
            return;
        }

        evidenceOutboxDispatcher.enqueue(datasetId, eventId, body, metadata);
    }

    /**
     * Returns the total count of permanent evidence write failures since module start.
     * Exposed for health checks and observability.
     *
     * @return number of permanently failed evidence writes
     */
    public long getEvidenceWriteFailureCount() {
        long outboxDeadLetters = evidenceOutboxDispatcher == null ? 0L : evidenceOutboxDispatcher.deadLetterCount();
        return evidenceWriteFailureCount.get() + outboxDeadLetters;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private String toIso(Instant timestamp) {
        return timestamp == null ? Instant.now().toString() : timestamp.toString();
    }

    public PhrServiceCatalog getServiceCatalog() {
        return serviceCatalog;
    }
}
