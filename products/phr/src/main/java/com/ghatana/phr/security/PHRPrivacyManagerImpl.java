package com.ghatana.phr.security;

import com.ghatana.kernel.security.Policy;
import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.ProviderInfo;
import com.ghatana.phr.model.TenantConfig;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;

import java.time.Instant;

/**
 * Component for PHRPrivacyManagerImpl
 *
 * @doc.type class
 * @doc.purpose Component for PHRPrivacyManagerImpl
 * @doc.layer product
 * @doc.pattern Manager
 */
public class PHRPrivacyManagerImpl implements PrivacyManager {
    private final ConsentRepository consentRepository;
    private final TenantConfigRepository tenantConfigRepository;

    public PHRPrivacyManagerImpl(ConsentRepository consentRepository,
                                 TenantConfigRepository tenantConfigRepository) {
        this.consentRepository = consentRepository;
        this.tenantConfigRepository = tenantConfigRepository;
    }

    @Override
    public ConsentStatus checkConsent(DataRequest request, String tenantId) {
        String dataType = request.getDataType();
        String purpose = request.getPurpose();
        
        if (dataType.equals("patient-health-records")) {
            // Use patient_id from metadata when available (provider accessing patient records),
            // otherwise fall back to the requesterId (patient accessing their own records).
            String patientId = (request.getMetadata() != null && request.getMetadata().containsKey("patient_id"))
                ? (String) request.getMetadata().get("patient_id")
                : request.getRequesterId();

            PatientConsent consent = consentRepository.findByPatientAndPurpose(
                patientId, purpose
            );
            
            if (consent == null) {
                return ConsentStatus.PENDING;
            }
            
            if (consent.isExpired()) {
                return ConsentStatus.EXPIRED;
            }
            
            return consent.isGranted() ? ConsentStatus.GRANTED : ConsentStatus.DENIED;
        }
        
        return ConsentStatus.NOT_REQUIRED;
    }

    @Override
    public DataClassification classifyData(Object data) {
        if (data instanceof PatientRecord) {
            return DataClassification.PHI;
        }
        
        if (data instanceof ProviderInfo) {
            return DataClassification.CONFIDENTIAL;
        }
        
        return DataClassification.INTERNAL;
    }

    @Override
    public boolean enforceResidency(DataLocation location, String tenantId) {
        TenantConfig config = tenantConfigRepository.findById(tenantId);
        
        if (config == null) {
            return false;
        }
        
        return config.getAllowedRegions().contains(location.getRegion());
    }

    @Override
    public void recordConsent(String tenantId, String userId, String purpose, boolean granted) {
        PatientConsent consent = new PatientConsent();
        consent.setPatientId(userId);
        consent.setTenantId(tenantId);
        consent.setPurpose(purpose);
        consent.setGranted(granted);
        consent.setTimestamp(Instant.now());
        
        consentRepository.save(consent);
    }

    @Override
    public Policy getPrivacyPolicy(String tenantId) {
        return new HIPAAPrivacyPolicy(tenantId);
    }
}
