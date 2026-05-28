package com.ghatana.phr.security;

import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        ).toCompletableFuture().join();

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
        ).toCompletableFuture().join();

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
        ).toCompletableFuture().join();

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
        ).toCompletableFuture().join();

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

    @Test
    @DisplayName("encrypts and decrypts PII with authenticated tenant-bound AES-GCM")
    void encryptsAndDecryptsPiiWithTenantBoundAesGcm() {
        PHRPrivacyManagerImpl manager = managerWithCryptoKey();

        String ciphertext = manager.encryptPII("tenant-1", "Aarati Shrestha")
            .toCompletableFuture()
            .join();

        assertNotEquals("Aarati Shrestha", ciphertext);
        assertEquals("Aarati Shrestha", manager.decryptPII("tenant-1", ciphertext)
            .toCompletableFuture()
            .join());
        assertThrows(SecurityException.class, () -> manager.decryptPII("tenant-2", ciphertext)
            .toCompletableFuture()
            .join());
    }

    @Test
    @DisplayName("fails closed when PII crypto key is missing")
    void failsClosedWhenPiiCryptoKeyIsMissing() {
        PHRPrivacyManagerImpl manager = new PHRPrivacyManagerImpl(
            new ConsentRepository(),
            new TenantConfigRepository(),
            new StubConsentService(ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.SYSTEM_DENY,
                ConsentService.CacheStatus.MISS
            ))
        );

        assertThrows(SecurityException.class, () -> manager.encryptPII("tenant-1", "Aarati")
            .toCompletableFuture()
            .join());
    }

    @Test
    @DisplayName("hashes PII identifiers with tenant-scoped HMAC")
    void hashesPiiIdentifiersWithTenantScopedHmac() {
        PHRPrivacyManagerImpl manager = managerWithCryptoKey();

        String tenantOneHash = manager.hashPIIIdentifier("tenant-1", "national-id-1")
            .toCompletableFuture()
            .join();
        String tenantTwoHash = manager.hashPIIIdentifier("tenant-2", "national-id-1")
            .toCompletableFuture()
            .join();

        assertNotEquals("national-id-1", tenantOneHash);
        assertNotEquals(tenantOneHash, tenantTwoHash);
    }

    private static PHRPrivacyManagerImpl managerWithCryptoKey() {
        return new PHRPrivacyManagerImpl(
            new ConsentRepository(),
            new TenantConfigRepository(),
            new StubConsentService(ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.SYSTEM_DENY,
                ConsentService.CacheStatus.MISS
            )),
            fixedTestKey()
        );
    }

    private static SecretKey fixedTestKey() {
        byte[] key = Arrays.copyOf("phr-test-key-material".getBytes(StandardCharsets.UTF_8), 32);
        return new SecretKeySpec(key, "AES");
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

        @Override
        public Promise<ConsentRevokeResult> revokeConsent(ConsentRevokeRequest request) {
            return Promise.of(new ConsentRevokeResult(true, request.target().resourceId()));
        }
    }
}
