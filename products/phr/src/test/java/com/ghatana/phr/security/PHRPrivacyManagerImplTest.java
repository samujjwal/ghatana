package com.ghatana.phr.security;

import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
