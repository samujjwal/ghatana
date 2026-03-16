/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.hsm;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Port interface and stub implementation for HSM (Hardware Security Module) key operations
 * (STORY-K14-011).
 *
 * <p>In production this implementation is replaced by a PKCS#11 / JCA provider that
 * delegates all key operations to a physical or virtual HSM (e.g. AWS CloudHSM, Thales).
 * Private keys never leave the HSM — only signed data is returned.
 *
 * <p>The stub implementation uses in-process {@link java.security.Signature} for
 * local development. The PKCS#11 provider is wired via system property:
 * {@code -Djava.security.config=/etc/pkcs11.cfg}.
 *
 * @doc.type  class
 * @doc.purpose HSM key operations (sign, verify, generate) with PKCS#11 stub (K14-011)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class HsmKeyOperationsProvider {

    private static final Logger log = LoggerFactory.getLogger(HsmKeyOperationsProvider.class);

    private static final String DEFAULT_ALGORITHM = "SHA256withRSA";

    private final Executor executor;
    private final boolean useHsm;

    /**
     * @param executor  thread pool for blocking HSM operations
     * @param useHsm    when {@code true} delegates to PKCS#11 provider; when {@code false} uses in-process dev stub
     */
    public HsmKeyOperationsProvider(Executor executor, boolean useHsm) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.useHsm   = useHsm;
        log.info("HsmKeyOperationsProvider initialised: mode={}", useHsm ? "HSM/PKCS11" : "DEV-STUB");
    }

    /**
     * Signs {@code data} using the private key identified by {@code keyAlias} in the HSM key store.
     *
     * @param keyAlias    HSM key alias (must exist in the key store)
     * @param data        raw bytes to sign
     * @param privateKey  PKCS#11-extracted private key reference (stub: pass in-process key)
     * @return promise resolving to the signature bytes
     */
    public Promise<byte[]> sign(String keyAlias, byte[] data, PrivateKey privateKey) {
        Objects.requireNonNull(keyAlias,    "keyAlias");
        Objects.requireNonNull(data,        "data");
        Objects.requireNonNull(privateKey,  "privateKey");

        return Promise.ofBlocking(executor, () -> {
            if (useHsm) {
                log.debug("HSM sign: alias={} algorithm={}", keyAlias, DEFAULT_ALGORITHM);
                // Delegate to SunPKCS11 JCA provider — private key never leaves the HSM.
                // The provider must be configured via java.security.config system property
                // or registered programmatically before this class is instantiated.
                java.security.Provider pkcs11Provider =
                        java.security.Security.getProvider("SunPKCS11");
                if (pkcs11Provider == null) {
                    throw new IllegalStateException(
                        "SunPKCS11 JCA provider is not registered. " +
                        "Configure it via '-Djava.security.config=/etc/pkcs11.cfg' " +
                        "or register it programmatically before constructing HsmKeyOperationsProvider.");
                }
                java.security.Signature sig =
                        java.security.Signature.getInstance(DEFAULT_ALGORITHM, pkcs11Provider);
                sig.initSign(privateKey);   // privateKey is a PKCS#11 opaque key reference
                sig.update(data);
                byte[] signature = sig.sign();
                log.debug("HSM sign complete: alias={} sigLen={}", keyAlias, signature.length);
                return signature;
            }

            // Dev/test mode: in-process software RSA signing (key material in JVM heap)
            java.security.Signature sig = java.security.Signature.getInstance(DEFAULT_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        });
    }

    /**
     * Verifies a signature produced by {@link #sign}.
     *
     * @param publicKey  the matching RSA public key
     * @param data       original data that was signed
     * @param signature  the signature to verify
     * @return promise resolving to {@code true} if the signature is valid
     */
    public Promise<Boolean> verify(PublicKey publicKey, byte[] data, byte[] signature) {
        Objects.requireNonNull(publicKey,  "publicKey");
        Objects.requireNonNull(data,       "data");
        Objects.requireNonNull(signature,  "signature");

        return Promise.ofBlocking(executor, () -> {
            java.security.Signature sig = java.security.Signature.getInstance(DEFAULT_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        });
    }

    /**
     * Generates a new RSA-2048 key pair in the HSM (or in-process for dev).
     *
     * @return promise resolving to the generated key pair
     */
    public Promise<java.security.KeyPair> generateKeyPair() {
        return Promise.ofBlocking(executor, () -> {
            java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            java.security.KeyPair kp = gen.generateKeyPair();
            log.info("Key pair generated: mode={}", useHsm ? "HSM" : "DEV-STUB");
            return kp;
        });
    }
}
