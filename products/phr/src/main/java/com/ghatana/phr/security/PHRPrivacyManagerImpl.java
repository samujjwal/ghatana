package com.ghatana.phr.security;

import com.ghatana.kernel.security.Policy;
import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.ProviderInfo;
import com.ghatana.phr.model.TenantConfig;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Component for PHRPrivacyManagerImpl
 *
 * @doc.type class
 * @doc.purpose Component for PHRPrivacyManagerImpl
 * @doc.layer product
 * @doc.pattern Manager
 */
public class PHRPrivacyManagerImpl implements PrivacyManager {
    private static final Logger logger = LoggerFactory.getLogger(PHRPrivacyManagerImpl.class);

    private final ConsentRepository consentRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final ConsentService consentService;

    /**
     * Creates a privacy manager backed by the centralized consent service.
     *
     * <p>Consent checks are delegated to the centralized consent module to keep
     * policy decisions in a single source of truth.
     */
    public PHRPrivacyManagerImpl(ConsentRepository consentRepository,
                                 TenantConfigRepository tenantConfigRepository,
                                 ConsentService consentService) {
        this.consentRepository = Objects.requireNonNull(consentRepository, "consentRepository cannot be null");
        this.tenantConfigRepository = Objects.requireNonNull(tenantConfigRepository,
            "tenantConfigRepository cannot be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService cannot be null");
    }

    @Override
    public ConsentStatus checkConsent(DataRequest request, String tenantId) {
        Objects.requireNonNull(request, "request cannot be null");
        return checkConsentViaConsentService(request, tenantId);
    }

    private ConsentStatus checkConsentViaConsentService(DataRequest request, String tenantId) {
        if (!"patient-health-records".equals(request.getDataType())) {
            return ConsentStatus.NOT_REQUIRED;
        }

        String patientId = extractPatientId(request);
        ConsentService.PurposeOfUse purpose = mapPurpose(request.getPurpose());

        ConsentService.ConsentCheckRequest checkRequest = new ConsentService.ConsentCheckRequest(
            UUID.randomUUID().toString(),
            tenantId,
            new ConsentService.ActorContext(
                request.getRequesterId(),
                ConsentService.ActorType.PROVIDER,
                null,
                request.getRequesterId(),
                null,
                Set.of("patient:read")
            ),
            new ConsentService.TargetResource(
                patientId,
                request.getDataType(),
                null,
                PhrDataClassification.C3
            ),
            ConsentService.ConsentAction.PATIENT_READ,
            purpose,
            purpose == ConsentService.PurposeOfUse.EMERGENCY
                ? new ConsentService.EmergencyContext(true,
                    String.valueOf((request.getMetadata() == null ? Map.of() : request.getMetadata())
                        .getOrDefault("justification", "Emergency access")),
                    ConsentService.EmergencyCategory.TRAUMA)
                : null
        );

        try {
            ConsentService.ConsentAccessDecision decision = await(consentService.checkAccess(checkRequest));
            if (decision.allowed()) {
                return ConsentStatus.GRANTED;
            }
            return switch (decision.reasonCode()) {
                case GRANT_EXPIRED -> ConsentStatus.EXPIRED;
                case GRANT_REVOKED -> ConsentStatus.DENIED;
                case SYSTEM_DENY -> ConsentStatus.PENDING;
                default -> ConsentStatus.DENIED;
            };
        } catch (Exception exception) {
            logger.warn("ConsentService check failed for tenant='{}' requester='{}': {}",
                tenantId, request.getRequesterId(), exception.getMessage());
            return ConsentStatus.PENDING;
        }
    }

    private static String extractPatientId(DataRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        if (metadata != null && metadata.containsKey("patient_id")) {
            return String.valueOf(metadata.get("patient_id"));
        }
        return request.getRequesterId();
    }

    private static ConsentService.PurposeOfUse mapPurpose(String purpose) {
        if (purpose == null) {
            return ConsentService.PurposeOfUse.CARE_DELIVERY;
        }
        return switch (purpose.toLowerCase()) {
            case "self_service", "self-service" -> ConsentService.PurposeOfUse.SELF_SERVICE;
            case "eligibility", "eligibility_check", "insurance" -> ConsentService.PurposeOfUse.ELIGIBILITY_CHECK;
            case "emergency" -> ConsentService.PurposeOfUse.EMERGENCY;
            case "audit", "audit_review" -> ConsentService.PurposeOfUse.AUDIT_REVIEW;
            default -> ConsentService.PurposeOfUse.CARE_DELIVERY;
        };
    }

    private static <T> T await(Promise<T> promise) {
        return promise.toCompletableFuture().join();
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
