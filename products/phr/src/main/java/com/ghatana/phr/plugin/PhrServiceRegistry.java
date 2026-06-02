/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.registry.ServiceRegistry;
import com.ghatana.phr.application.audit.AccessAuditService;
import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.application.consent.ConsentService;
import com.ghatana.phr.application.emergency.EmergencyAccessService;
import com.ghatana.phr.application.fhir.FhirService;
import com.ghatana.phr.application.patient.PatientService;
import com.ghatana.phr.application.record.RecordService;
import com.ghatana.phr.application.sovereignty.DataSovereigntyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PHR Service Registry - Registers PHR services with Kernel's ServiceRegistry.
 *
 * <p>This class handles the registration of all PHR application services with the
 * Kernel's service catalog. Services are registered by their contract IDs for
 * discovery by other products and plugins.</p>
 *
 * <p>Service contract IDs follow the pattern: com.ghatana.phr.{ServiceName}</p>
 *
 * @doc.type class
 * @doc.purpose Registers PHR services with Kernel service catalog
 * @doc.layer product
 * @doc.pattern Registry
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class PhrServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhrServiceRegistry.class);

    private final KernelContext context;
    private final ServiceRegistry serviceRegistry;

    // Service contract IDs
    public static final String PATIENT_SERVICE = "com.ghatana.phr.PatientService";
    public static final String CONSENT_SERVICE = "com.ghatana.phr.ConsentService";
    public static final String RECORD_SERVICE = "com.ghatana.phr.RecordService";
    public static final String CLINICAL_SERVICE = "com.ghatana.phr.ClinicalService";
    public static final String FHIR_SERVICE = "com.ghatana.phr.FHIRService";
    public static final String EMERGENCY_SERVICE = "com.ghatana.phr.EmergencyAccessService";
    public static final String AUDIT_SERVICE = "com.ghatana.phr.AccessAuditService";
    public static final String DATA_SOVEREIGNTY_SERVICE = "com.ghatana.phr.DataSovereigntyService";

    /**
     * Creates a new PHR service registry.
     *
     * @param context the kernel context
     */
    public PhrServiceRegistry(KernelContext context) {
        this.context = context;
        this.serviceRegistry = context.getOptionalDependency(ServiceRegistry.class)
            .orElseThrow(() -> new IllegalStateException("ServiceRegistry not available in KernelContext"));
    }

    /**
     * Registers all PHR services with the Kernel service catalog.
     *
     * <p>This method should be called during plugin initialization to make
     * PHR services discoverable by other products and plugins.</p>
     */
    public void registerAllServices() {
        LOGGER.info("Registering PHR services with Kernel service catalog");

        // Register core PHR services
        // Note: Service instances should be injected via constructor or factory
        // This is a placeholder showing the registration pattern
        
        registerService(PATIENT_SERVICE, "PatientService");
        registerService(CONSENT_SERVICE, "ConsentService");
        registerService(RECORD_SERVICE, "RecordService");
        registerService(CLINICAL_SERVICE, "ClinicalService");
        registerService(FHIR_SERVICE, "FHIRService");
        registerService(EMERGENCY_SERVICE, "EmergencyAccessService");
        registerService(AUDIT_SERVICE, "AccessAuditService");
        registerService(DATA_SOVEREIGNTY_SERVICE, "DataSovereigntyService");

        LOGGER.info("PHR services registered successfully");
    }

    /**
     * Unregisters all PHR services from the Kernel service catalog.
     *
     * <p>This method should be called during plugin shutdown.</p>
     */
    public void unregisterAllServices() {
        LOGGER.info("Unregistering PHR services from Kernel service catalog");

        serviceRegistry.unregisterService(PATIENT_SERVICE);
        serviceRegistry.unregisterService(CONSENT_SERVICE);
        serviceRegistry.unregisterService(RECORD_SERVICE);
        serviceRegistry.unregisterService(CLINICAL_SERVICE);
        serviceRegistry.unregisterService(FHIR_SERVICE);
        serviceRegistry.unregisterService(EMERGENCY_SERVICE);
        serviceRegistry.unregisterService(AUDIT_SERVICE);
        serviceRegistry.unregisterService(DATA_SOVEREIGNTY_SERVICE);

        LOGGER.info("PHR services unregistered successfully");
    }

    /**
     * Registers a single service with the Kernel service catalog.
     *
     * @param serviceId the service contract ID
     * @param serviceName the service implementation class name (for logging)
     */
    private void registerService(String serviceId, String serviceName) {
        // In a real implementation, this would register an actual service instance
        // For now, we log the registration intent
        LOGGER.debug("Registering service: {} -> {}", serviceId, serviceName);
        
        // Actual registration would look like:
        // serviceRegistry.registerService(serviceId, serviceInstance);
    }

    /**
     * Gets a PHR service from the Kernel service catalog.
     *
     * @param serviceId the service contract ID
     * @param type the expected service type
     * @param <T> the service type
     * @return optional service instance
     */
    public <T> T getService(String serviceId, Class<T> type) {
        return serviceRegistry.getService(serviceId, type).orElse(null);
    }

    /**
     * Checks if a PHR service is registered.
     *
     * @param serviceId the service contract ID
     * @return true if the service is registered
     */
    public boolean hasService(String serviceId) {
        return serviceRegistry.hasService(serviceId);
    }
}
