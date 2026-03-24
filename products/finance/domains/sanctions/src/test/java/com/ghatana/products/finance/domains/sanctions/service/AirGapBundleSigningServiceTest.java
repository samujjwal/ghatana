package com.ghatana.products.finance.domains.sanctions.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AirGapBundleSigningService}.
 *
 * <p>Tests cover happy-path signing/verification, tampered-bundle detection,
 * public key export, and empty/large payload handling.</p>
 *
 * @doc.type test
 * @doc.purpose Verify Ed25519 air-gap bundle signing and verification
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("AirGapBundleSigningService Tests")
class AirGapBundleSigningServiceTest {

    private static AirGapBundleSigningService service;
    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        keyPair = kpg.generateKeyPair();
        service = new AirGapBundleSigningService(
            keyPair.getPrivate().getEncoded(),
            keyPair.getPublic().getEncoded()
        );
    }

    @Test
    @DisplayName("Should sign and verify a valid bundle")
    void shouldSignAndVerifyValidBundle() {
        byte[] payload = "{\"list\":\"OFAC\",\"version\":42}".getBytes(StandardCharsets.UTF_8);

        AirGapBundleSigningService.SignedBundle bundle = service.sign("bundle-001", payload);

        assertNotNull(bundle);
        assertEquals("bundle-001", bundle.bundleId());
        assertArrayEquals(payload, bundle.payload());
        assertNotNull(bundle.signatureB64());
        assertNotNull(bundle.signedAt());
        assertTrue(service.verify(bundle));
    }

    @Test
    @DisplayName("Should detect tampered payload")
    void shouldDetectTamperedPayload() {
        byte[] payload = "original-data".getBytes(StandardCharsets.UTF_8);
        AirGapBundleSigningService.SignedBundle signed = service.sign("bundle-tamper", payload);

        // Tamper with payload after signing
        byte[] tampered = "modified-data".getBytes(StandardCharsets.UTF_8);
        AirGapBundleSigningService.SignedBundle tamperedBundle =
            new AirGapBundleSigningService.SignedBundle(
                signed.bundleId(), tampered, signed.signatureB64(), signed.signedAt());

        assertFalse(service.verify(tamperedBundle));
    }

    @Test
    @DisplayName("Should detect tampered signature")
    void shouldDetectTamperedSignature() {
        byte[] payload = "test-payload".getBytes(StandardCharsets.UTF_8);
        AirGapBundleSigningService.SignedBundle signed = service.sign("bundle-badsig", payload);

        // Replace signature with garbage (valid Base64 but wrong signature)
        AirGapBundleSigningService.SignedBundle badSigBundle =
            new AirGapBundleSigningService.SignedBundle(
                signed.bundleId(), signed.payload(), "AAAA", signed.signedAt());

        assertFalse(service.verify(badSigBundle));
    }

    @Test
    @DisplayName("Should support empty payload")
    void shouldSupportEmptyPayload() {
        byte[] empty = new byte[0];
        AirGapBundleSigningService.SignedBundle bundle = service.sign("bundle-empty", empty);

        assertNotNull(bundle.signatureB64());
        assertTrue(service.verify(bundle));
    }

    @Test
    @DisplayName("Should support large payload")
    void shouldSupportLargePayload() {
        byte[] large = new byte[1024 * 1024]; // 1 MB
        java.util.Arrays.fill(large, (byte) 0x42);

        AirGapBundleSigningService.SignedBundle bundle = service.sign("bundle-large", large);

        assertTrue(service.verify(bundle));
    }

    @Test
    @DisplayName("Should export public key as Base64")
    void shouldExportPublicKeyAsBase64() {
        String publicKeyB64 = service.getPublicKeyB64();

        assertNotNull(publicKeyB64);
        assertFalse(publicKeyB64.isBlank());
        // Should be valid Base64
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(publicKeyB64));
    }

    @Test
    @DisplayName("Should reject invalid private key material")
    void shouldRejectInvalidPrivateKeyMaterial() {
        byte[] badKey = "not-a-key".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () ->
            new AirGapBundleSigningService(badKey, keyPair.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("Should reject invalid public key material")
    void shouldRejectInvalidPublicKeyMaterial() {
        byte[] badKey = "not-a-key".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () ->
            new AirGapBundleSigningService(keyPair.getPrivate().getEncoded(), badKey));
    }

    @Test
    @DisplayName("Verification with different key pair should fail")
    void verificationWithDifferentKeyPairShouldFail() throws Exception {
        // Sign with first key pair
        byte[] payload = "cross-key-test".getBytes(StandardCharsets.UTF_8);
        AirGapBundleSigningService.SignedBundle signed = service.sign("bundle-crosskey", payload);

        // Create a second service with different keys
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair otherKeyPair = kpg.generateKeyPair();
        AirGapBundleSigningService otherService = new AirGapBundleSigningService(
            otherKeyPair.getPrivate().getEncoded(),
            otherKeyPair.getPublic().getEncoded()
        );

        // Verify with different key should fail
        assertFalse(otherService.verify(signed));
    }
}
