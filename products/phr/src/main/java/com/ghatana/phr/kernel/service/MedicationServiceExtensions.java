package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Extension methods for MedicationService for dashboard and route family completion.
 *
 * <p>These methods provide convenience methods for the dashboard and other route families
 * that need aggregated medication data.</p>
 *
 * @doc.type class
 * @doc.purpose Extension methods for MedicationService
 * @doc.layer product
 * @doc.pattern Service Extension
 */
public final class MedicationServiceExtensions {

    private final MedicationService medicationService;

    public MedicationServiceExtensions(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    /**
     * Gets the count of active medications for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the count of active medications
     */
    public Promise<Integer> getActiveMedicationCount(String patientId) {
        return medicationService.getActivePrescriptions(patientId)
            .map(List::size);
    }
}
