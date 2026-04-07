package com.ghatana.phr.security;

import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Tests consent delegation behavior in PHRPrivacyManagerImpl
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PHRPrivacyManagerImpl")
class PHRPrivacyManagerImplTest {

    @Test
    @DisplayName("delegates to ConsentService and returns GRANTED for allow decision")
    void delegatesAllowDecision() {
        ConsentService consentService = new StubConsentService(
            ConsentService.ConsentAccessDecision.allow(
                ConsentService.ReasonCode.EXPLICIT_GRANT,
                "grant-1",
                ConsentService.CacheStatus.MISS,
                Instant.now().plusSeconds(60)
            )
        );

        PHRPrivacyManagerImpl manager = new PHRPrivacyManagerImpl(
            new ConsentRepository(), new TenantConfigRepository(), consentService);

        PrivacyManager.ConsentStatus status = manager.checkConsent(
            new PrivacyManager.DataRequest(
                "provider-1",
                "patient-health-records",
                "care_delivery",
                java.util.Map.of("patient_id", "patient-1")
            ),
            "tenant-1"
        );

        assertEquals(PrivacyManager.ConsentStatus.GRANTED, status);
    }

    @Test
    @DisplayName("delegates to ConsentService and maps GRANT_EXPIRED to EXPIRED")
    void delegatesExpiredDecision() {
        ConsentService consentService = new StubConsentService(
            ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.GRANT_EXPIRED,
                ConsentService.CacheStatus.MISS
            )
        );

        PHRPrivacyManagerImpl manager = new PHRPrivacyManagerImpl(
            new ConsentRepository(), new TenantConfigRepository(), consentService);

        PrivacyManager.ConsentStatus status = manager.checkConsent(
            new PrivacyManager.DataRequest(
                "provider-1",
                "patient-health-records",
                "care_delivery",
                java.util.Map.of("patient_id", "patient-1")
            ),
            "tenant-1"
        );

        assertEquals(PrivacyManager.ConsentStatus.EXPIRED, status);
    }

    @Test
    @DisplayName("returns NOT_REQUIRED for non patient health record access")
    void returnsNotRequiredForNonPatientHealthRecordAccess() {
        ConsentService consentService = new StubConsentService(
            ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.SYSTEM_DENY,
                ConsentService.CacheStatus.MISS
            )
        );

        PHRPrivacyManagerImpl manager = new PHRPrivacyManagerImpl(
            new ConsentRepository(), new TenantConfigRepository(), consentService);

        PrivacyManager.ConsentStatus status = manager.checkConsent(
            new PrivacyManager.DataRequest(
                "provider-1",
                "provider-directory",
                "care_delivery",
                Map.of("patient_id", "patient-1")
            ),
            "tenant-1"
        );

        assertEquals(PrivacyManager.ConsentStatus.NOT_REQUIRED, status);
    }

    @Test
    @DisplayName("maps SYSTEM_DENY to PENDING")
    void mapsSystemDenyToPending() {
        ConsentService consentService = new StubConsentService(
            ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.SYSTEM_DENY,
                ConsentService.CacheStatus.HIT
            )
        );

        PHRPrivacyManagerImpl manager = new PHRPrivacyManagerImpl(
            new ConsentRepository(), new TenantConfigRepository(), consentService);

        PrivacyManager.ConsentStatus status = manager.checkConsent(
            new PrivacyManager.DataRequest(
                "provider-1",
                "patient-health-records",
                "care_delivery",
                Map.of("patient_id", "patient-1")
            ),
            "tenant-1"
        );

        assertEquals(PrivacyManager.ConsentStatus.PENDING, status);
    }

    @Test
    @DisplayName("records consent in repository")
    void recordsConsent() {
        ConsentRepository consentRepository = new ConsentRepository();
        PHRPrivacyManagerImpl manager = new PHRPrivacyManagerImpl(
            consentRepository,
            new TenantConfigRepository(),
            new StubConsentService(ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.GRANT_REVOKED,
                ConsentService.CacheStatus.MISS
            ))
        );

        manager.recordConsent("tenant-1", "patient-1", "care_delivery", true);

        PatientConsent stored = consentRepository.findByPatientAndPurpose("patient-1", "care_delivery");
        assertNotNull(stored);
        assertEquals("tenant-1", stored.getTenantId());
        assertEquals(true, stored.isGranted());
    }

    @Test
    @DisplayName("requires centralized consent service")
    void requiresCentralizedConsentService() {
        assertThrows(NullPointerException.class, () -> new PHRPrivacyManagerImpl(
            new ConsentRepository(),
            new TenantConfigRepository(),
            null
        ));
    }

    private static final class StubConsentService implements ConsentService {

        private final ConsentAccessDecision decision;

        private StubConsentService(ConsentAccessDecision decision) {
            this.decision = decision;
        }

        @Override
        public Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request) {
            return Promise.of(decision);
        }

        @Override
        public Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request) {
            return Promise.of(decision);
        }

        @Override
        public Promise<Void> invalidatePatientAccessCache(CacheInvalidationRequest request) {
            return Promise.complete();
        }
    }
}
