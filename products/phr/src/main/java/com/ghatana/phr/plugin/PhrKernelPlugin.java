/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.phr.kernel.PhrCapabilities;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * PHR Kernel Plugin - Canonical Implementation.
 *
 * <p>This plugin registers PHR-specific capabilities with the kernel
 * using the canonical {@link KernelPlugin} interface. It replaces the
 * deprecated {@code ProductPlugin} pattern.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Patient Records - FHIR R4 and Nepal-2081 compliance</li>
 *   <li>Consent Management - Field-level granular consent</li>
 *   <li>FHIR Interoperability - R4 resource processing</li>
 *   <li>Clinical Documents - Lab reports, imaging, OCR</li>
 *   <li>Medication Management - Prescriptions, interactions</li>
 *   <li>Appointment Scheduling - Virtual and in-person</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR domain pack kernel plugin implementing canonical KernelPlugin interface
 * @doc.layer product
 * @doc.pattern Plugin, DomainPack
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class PhrKernelPlugin implements KernelPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhrKernelPlugin.class);

    private final PluginManifest manifest;
    @SuppressWarnings("unused")
    private KernelContext context;

    /**
     * Creates a new PHR kernel plugin with default manifest.
     */
    public PhrKernelPlugin() {
        this.manifest = createManifest();
    }

    private PluginManifest createManifest() {
        PluginManifest.Builder builder = PluginManifest.builder();
        builder.pluginId("phr");
        builder.version("1.0.0");
        builder.description("Personal Health Records domain pack with FHIR interoperability");
        builder.author("Ghatana PHR Team");
        builder.license("Proprietary");

        // Add capabilities
        for (KernelCapability capability : getDeclaredCapabilities()) {
            builder.capability(capability);
        }

        return builder.build();
    }

    @Override
    public PluginManifest getManifest() {
        return manifest;
    }

    @Override
    public Set<String> getExportedContracts() {
        return Set.of(
            "com.ghatana.phr.PatientRecordService",
            "com.ghatana.phr.ConsentService",
            "com.ghatana.phr.FHIRService",
            "com.ghatana.phr.AppointmentService",
            "com.ghatana.phr.MedicationService",
            "com.ghatana.phr.ClinicalDocumentService"
        );
    }

    @Override
    public Set<String> getRequiredContracts() {
        return Set.of(
            "com.ghatana.kernel.modules.authentication.AuthenticationService",
            "com.ghatana.kernel.modules.config.ConfigService",
            "com.ghatana.kernel.modules.eventstore.EventStoreService",
            "com.ghatana.kernel.modules.audit.AuditService"
        );
    }

    @Override
    public Promise<Void> install() {
        // One-time setup: database migrations, initial config
        return Promise.complete();
    }

    @Override
    public Promise<Void> uninstall() {
        // Cleanup when plugin is removed
        return Promise.complete();
    }

    @Override
    public Promise<Void> reload() {
        // Configuration refresh
        return Promise.complete();
    }

    @Override
    public void initialize(KernelContext context) {
        this.context = context;
        registerPHRServices();
    }

    @Override
    public Promise<Void> start() {
        startPatientRecordService();
        startConsentService();
        startFHIRService();
        startAppointmentService();
        startMedicationService();
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        stopPatientRecordService();
        stopConsentService();
        stopFHIRService();
        stopAppointmentService();
        stopMedicationService();
        return Promise.complete();
    }

    // ==================== Private Methods ====================

    private Set<KernelCapability> getDeclaredCapabilities() {
        return Set.of(
            PhrCapabilities.PATIENT_RECORDS,
            PhrCapabilities.CONSENT_MANAGEMENT,
            PhrCapabilities.FHIR_INTEROP,
            PhrCapabilities.CLINICAL_DOCUMENTS,
            PhrCapabilities.MEDICATION_MANAGEMENT,
            PhrCapabilities.APPOINTMENT_SCHEDULING
        );
    }

    private void registerPHRServices() {
        // Services registered via context
    }

    private void startPatientRecordService() {
        LOGGER.info("Starting PHR patient record service");
    }

    private void startConsentService() {
        LOGGER.info("Starting PHR consent service");
    }

    private void startFHIRService() {
        LOGGER.info("Starting PHR FHIR service");
    }

    private void startAppointmentService() {
        LOGGER.info("Starting PHR appointment service");
    }

    private void startMedicationService() {
        LOGGER.info("Starting PHR clinical therapy service");
    }

    private void stopPatientRecordService() {
        LOGGER.info("Stopping PHR patient record service");
    }

    private void stopConsentService() {
        LOGGER.info("Stopping PHR consent service");
    }

    private void stopFHIRService() {
        LOGGER.info("Stopping PHR FHIR service");
    }

    private void stopAppointmentService() {
        LOGGER.info("Stopping PHR appointment service");
    }

    private void stopMedicationService() {
        LOGGER.info("Stopping PHR clinical therapy service");
    }
}
