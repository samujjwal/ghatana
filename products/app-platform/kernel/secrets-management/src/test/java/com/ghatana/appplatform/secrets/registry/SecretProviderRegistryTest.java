/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.registry;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecretProviderRegistry}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for pluggable secret provider registry (K14-001)
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("SecretProviderRegistry — Unit Tests")
class SecretProviderRegistryTest {

    private SecretProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SecretProviderRegistry();
    }

    // ── Stub provider ─────────────────────────────────────────────────────────

    private static SecretProvider stubProvider(String name) {
        return new SecretProvider() {
            @Override
            public Promise<SecretValue> getSecret(String path) { return Promise.of(null); }
            @Override
            public Promise<SecretValue> putSecret(String path, char[] value, SecretMetadata metadata) { return Promise.of(null); }
            @Override
            public Promise<Void> deleteSecret(String path) { return Promise.of(null); }
            @Override
            public Promise<List<String>> listSecrets(String prefix) { return Promise.of(List.of()); }
            @Override
            public Promise<SecretValue> rotateSecret(String path) { return Promise.of(null); }
            @Override
            public Promise<SecretValue> getSecretVersion(String path, int version) { return Promise.of(null); }
            @Override
            public String toString() { return "StubProvider(" + name + ")"; }
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register and getActive — returns the default 'local' provider")
    void registerLocal_getActive_returnsLocalProvider() {
        SecretProvider local = stubProvider("local");
        registry.register("local", local);

        SecretProvider active = registry.getActive();

        assertThat(active).isSameAs(local);
    }

    @Test
    @DisplayName("setActiveProvider — switches active provider")
    void setActiveProvider_switchesActiveProvider() {
        SecretProvider local = stubProvider("local");
        SecretProvider vault = stubProvider("vault");
        registry.register("local", local);
        registry.register("vault", vault);

        registry.setActiveProvider("vault");

        assertThat(registry.getActive()).isSameAs(vault);
    }

    @Test
    @DisplayName("setActiveProvider — unknown name throws IllegalArgumentException")
    void setActiveProvider_unknownName_throws() {
        assertThatThrownBy(() -> registry.setActiveProvider("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("getActive — no providers registered throws IllegalStateException")
    void getActive_noProviders_throws() {
        assertThatThrownBy(() -> registry.getActive())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("get by name — returns specific provider without changing active")
    void get_byName_returnsSpecificProvider() {
        SecretProvider local = stubProvider("local");
        SecretProvider vault = stubProvider("vault");
        registry.register("local", local);
        registry.register("vault", vault);

        SecretProvider got = registry.get("vault");

        assertThat(got).isSameAs(vault);
        assertThat(registry.getActive()).isSameAs(local); // active unchanged
    }

    @Test
    @DisplayName("get — unknown name throws IllegalArgumentException")
    void get_unknownName_throws() {
        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("register overwrites existing provider with same name")
    void register_overwritesExistingProvider() {
        SecretProvider first  = stubProvider("local-v1");
        SecretProvider second = stubProvider("local-v2");
        registry.register("local", first);
        registry.register("local", second);

        assertThat(registry.getActive()).isSameAs(second);
    }

    @Test
    @DisplayName("multiple providers can coexist and be individually retrieved")
    void multipleProviders_coexist() {
        SecretProvider local = stubProvider("local");
        SecretProvider vault = stubProvider("vault");
        SecretProvider custom = stubProvider("custom");
        registry.register("local", local);
        registry.register("vault", vault);
        registry.register("custom", custom);

        assertThat(registry.get("local")).isSameAs(local);
        assertThat(registry.get("vault")).isSameAs(vault);
        assertThat(registry.get("custom")).isSameAs(custom);
    }
}
