package com.ghatana.phr.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.MedicationService;
import io.activej.promise.Promise;
import java.util.Set;

/**
 * Patient Services Module for PHR.
 *
 * <p>Groups patient-centric PHR services:
 * <ul>
 *   <li>Consent management and authorization</li>
 *   <li>Medication tracking and management</li>
 *   <li>Immunization records</li>
 *   <li>Caregiver relationships</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR patient services domain module
 * @doc.layer product
 * @doc.pattern Domain Module
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class PatientServicesModule implements KernelModule {

    private final ConsentManagementService consentManagementService;
    private final MedicationService medicationService;
    private final ImmunizationService immunizationService;
    private final CaregiverService caregiverService;

    public PatientServicesModule(
            ConsentManagementService consentManagementService,
            MedicationService medicationService,
            ImmunizationService immunizationService,
            CaregiverService caregiverService) {
        this.consentManagementService = consentManagementService;
        this.medicationService = medicationService;
        this.immunizationService = immunizationService;
        this.caregiverService = caregiverService;
    }

    @Override
    public String getModuleId() {
        return "phr-patient-services";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of();
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of();
    }

    @Override
    public void initialize(KernelContext context) {}

    @Override
    public Promise<Void> start() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return HealthStatus.healthy();
    }

    public String getName() {
        return "phr-patient-services";
    }

    public ConsentManagementService getConsentManagementService() {
        return consentManagementService;
    }

    public MedicationService getMedicationService() {
        return medicationService;
    }

    public ImmunizationService getImmunizationService() {
        return immunizationService;
    }

    public CaregiverService getCaregiverService() {
        return caregiverService;
    }
}
