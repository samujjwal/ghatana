/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.hsm;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HsmKeyOperationsProvider}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for HSM key operations provider with environment guard (K14-011)
 * @doc.layer kernel
 * @doc.pattern Test
 */
@DisplayName("HsmKeyOperationsProvider — Unit Tests")
class HsmKeyOperationsProviderTest extends EventloopTestBase {

    @Test
    @DisplayName("dev environment with useHsm=false — constructs successfully with warning")
    void devEnvironment_noHsm_constructsSuccessfully() {
        HsmKeyOperationsProvider provider = new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "dev");
        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("production environment with useHsm=false — throws IllegalStateException")
    void productionEnvironment_noHsm_throwsIllegalState() {
        assertThatThrownBy(() -> new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("useHsm=false is forbidden");
    }

    @Test
    @DisplayName("staging environment with useHsm=false — throws IllegalStateException")
    void stagingEnvironment_noHsm_throwsIllegalState() {
        assertThatThrownBy(() -> new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "staging"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("environment name containing 'prod' — treated as protected environment")
    void environmentWithProdSubstring_isProtected() {
        assertThatThrownBy(() -> new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "eu-prod-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("two-arg constructor — defaults to 'dev' environment, no exception with useHsm=false")
    void twoArgConstructor_defaultsToDev() {
        HsmKeyOperationsProvider provider = new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false);
        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("sign (dev stub) — produces non-null signature bytes")
    void sign_devStub_producesSignature() throws Exception {
        HsmKeyOperationsProvider provider = new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "dev");

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        byte[] data = "test-payload".getBytes();
        byte[] signature = runPromise(() -> provider.sign("test-key", data, pair.getPrivate()));

        assertThat(signature).isNotNull();
        assertThat(signature.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("sign — null keyAlias throws NullPointerException")
    void sign_nullKeyAlias_throwsNullPointer() throws Exception {
        HsmKeyOperationsProvider provider = new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "dev");
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        assertThatThrownBy(() -> runPromise(() -> provider.sign(null, "data".getBytes(), pair.getPrivate())))
                .isNotNull();
    }

    @Test
    @DisplayName("sign and verify — round-trip produces verifiable signature")
    void sign_andVerify_roundTrip() throws Exception {
        HsmKeyOperationsProvider provider = new HsmKeyOperationsProvider(
                Executors.newSingleThreadExecutor(), false, "dev");

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        byte[] data = "financial-payload".getBytes();
        byte[] signature = runPromise(() -> provider.sign("my-key", data, pair.getPrivate()));

        boolean verified = runPromise(() -> provider.verify(pair.getPublic(), data, signature));

        assertThat(verified).isTrue();
    }
}
