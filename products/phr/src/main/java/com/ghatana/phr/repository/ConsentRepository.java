package com.ghatana.phr.repository;

import com.ghatana.phr.model.PatientConsent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Data access layer for Consent
 *
 * @doc.type class
 * @doc.purpose Data access layer for Consent
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ConsentRepository {
    private final Map<String, PatientConsent> consents = new HashMap<>();

    public PatientConsent findByPatientAndPurpose(String patientId, String purpose) {
        return consents.values().stream()
            .filter(consent -> consent.getPatientId().equals(patientId) 
                && consent.getPurpose().equals(purpose))
            .findFirst()
            .orElse(null);
    }

    public List<PatientConsent> findByPatientId(String patientId) {
        return consents.values().stream()
            .filter(consent -> consent.getPatientId().equals(patientId))
            .collect(Collectors.toList());
    }

    public void save(PatientConsent consent) {
        if (consent.getConsentId() == null) {
            consent.setConsentId(UUID.randomUUID().toString());
        }
        consents.put(consent.getConsentId(), consent);
    }

    public void delete(String consentId) {
        consents.remove(consentId);
    }

    public PatientConsent findById(String consentId) {
        return consents.get(consentId);
    }
}
