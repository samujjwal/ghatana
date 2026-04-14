/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event.spi.secrets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the secrets package: {@link SecretReference}, {@link SecretResolver},
 * {@link SecretResolutionException}, {@link SecretValue}, and {@link EnvSecretProvider}.
 */
@DisplayName("Secrets package")
class SecretsTest {

    // ─── SecretResolutionException ────────────────────────────────────────────

    @Nested
    @DisplayName("SecretResolutionException")
    class SecretResolutionExceptionTest {

        @Test
        void messageConstructor() {
            SecretResolutionException ex = new SecretResolutionException("boom");
            assertThat(ex).hasMessage("boom");
        }

        @Test
        void messageCauseConstructor() {
            Throwable cause = new RuntimeException("root");
            SecretResolutionException ex = new SecretResolutionException("wrapped", cause);
            assertThat(ex).hasMessage("wrapped").hasCause(cause);
        }
    }

    // ─── SecretValue ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecretValue")
    class SecretValueTest {

        @Test
        void ofStringAndBack() {
            SecretValue sv = SecretValue.ofString("mysecret");
            assertThat(sv.asString()).isEqualTo("mysecret");
        }

        @Test
        void ofCharArray() {
            char[] chars = {'a', 'b', 'c'};
            SecretValue sv = SecretValue.of(chars);
            assertThat(sv.asString()).isEqualTo("abc");
        }

        @Test
        void asCharArrayCopyIsDefensive() {
            SecretValue sv = SecretValue.ofString("xyz");
            char[] copy = sv.asCharArrayCopy();
            copy[0] = 'Z';
            // Original should be unchanged
            assertThat(sv.asString()).isEqualTo("xyz");
        }

        @Test
        void clearMakesValueInaccessible() {
            SecretValue sv = SecretValue.ofString("secret");
            sv.clear();
            assertThatThrownBy(sv::asString)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cleared");
        }

        @Test
        void clearMakesCharArrayInaccessible() {
            SecretValue sv = SecretValue.ofString("secret");
            sv.clear();
            assertThatThrownBy(sv::asCharArrayCopy)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void clearIsIdempotent() {
            SecretValue sv = SecretValue.ofString("secret");
            sv.clear();
            sv.clear(); // should not throw
        }

        @Test
        void toStringIsRedacted() {
            assertThat(SecretValue.ofString("mypassword").toString()).isEqualTo("***");
        }
    }

    // ─── SecretReference ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecretReference")
    class SecretReferenceTest {

        @Test
        void parseValidReference() {
            SecretReference ref = SecretReference.parse("secret:env:MY_VAR");
            assertThat(ref.provider()).isEqualTo("env");
            assertThat(ref.locator()).isEqualTo("MY_VAR");
        }

        @Test
        void parseWithColonInLocator() {
            SecretReference ref = SecretReference.parse("secret:file:/etc/secrets/db:password");
            assertThat(ref.provider()).isEqualTo("file");
            assertThat(ref.locator()).isEqualTo("/etc/secrets/db:password");
        }

        @Test
        void toStringRoundTrip() {
            String original = "secret:env:MY_VAR";
            assertThat(SecretReference.parse(original).toString()).isEqualTo(original);
        }

        @Test
        void isSecretReferenceReturnsTrueForPrefixed() {
            assertThat(SecretReference.isSecretReference("secret:env:VAR")).isTrue();
        }

        @Test
        void isSecretReferenceReturnsFalseForPlain() {
            assertThat(SecretReference.isSecretReference("plainvalue")).isFalse();
        }

        @Test
        void isSecretReferenceReturnsFalseForNull() {
            assertThat(SecretReference.isSecretReference(null)).isFalse();
        }

        @Test
        void parseBlankThrows() {
            assertThatThrownBy(() -> SecretReference.parse(""))
                    .isInstanceOf(SecretResolutionException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void parseNullThrows() {
            assertThatThrownBy(() -> SecretReference.parse(null))
                    .isInstanceOf(SecretResolutionException.class);
        }

        @Test
        void parseMissingPrefixThrows() {
            assertThatThrownBy(() -> SecretReference.parse("env:MY_VAR"))
                    .isInstanceOf(SecretResolutionException.class)
                    .hasMessageContaining("prefix");
        }

        @Test
        void parseMissingLocatorThrows() {
            // "secret:env:" — locator is empty
            assertThatThrownBy(() -> SecretReference.parse("secret:env:"))
                    .isInstanceOf(SecretResolutionException.class);
        }

        @Test
        void parseMissingProviderSeparatorThrows() {
            // "secret:nocoronafter" — no second colon
            assertThatThrownBy(() -> SecretReference.parse("secret:nocolon"))
                    .isInstanceOf(SecretResolutionException.class);
        }
    }

    // ─── SecretResolver ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecretResolver")
    class SecretResolverTest {

        @Test
        void defaultResolverHasEnvAndFileProviders() {
            SecretResolver resolver = SecretResolver.defaultResolver();
            assertThat(resolver.providers()).containsKeys("env", "file");
        }

        @Test
        void ofWithCustomProvider() {
            SecretProvider stub = new SecretProvider() {
                @Override
                public String name() { return "stub"; }
                @Override
                public SecretValue resolve(String locator) {
                    return SecretValue.ofString("stubval-" + locator);
                }
            };
            SecretResolver resolver = SecretResolver.of(Map.of("stub", stub));
            SecretValue val = resolver.resolveSecretReference("secret:stub:mykey");
            assertThat(val.asString()).isEqualTo("stubval-mykey");
        }

        @Test
        void resolveToStringPassesThroughPlainValue() {
            SecretResolver resolver = SecretResolver.of(Map.of());
            assertThat(resolver.resolveToString("plain-text")).isEqualTo("plain-text");
        }

        @Test
        void resolveToStringResolvesSecretRef() {
            SecretProvider stub = new SecretProvider() {
                @Override public String name() { return "stub"; }
                @Override public SecretValue resolve(String locator) { return SecretValue.ofString("resolved"); }
            };
            SecretResolver resolver = SecretResolver.of(Map.of("stub", stub));
            assertThat(resolver.resolveToString("secret:stub:key")).isEqualTo("resolved");
        }

        @Test
        void resolveUnknownProviderThrows() {
            SecretResolver resolver = SecretResolver.of(Map.of());
            assertThatThrownBy(() -> resolver.resolveSecretReference("secret:unknown:key"))
                    .isInstanceOf(SecretResolutionException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void ofNullThrows() {
            assertThatThrownBy(() -> SecretResolver.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void redactSecretReference() {
            SecretResolver resolver = SecretResolver.of(Map.of());
            String redacted = resolver.redact("secret:env:MY_SECRET");
            assertThat(redacted).isEqualTo("secret:env:***");
        }

        @Test
        void redactPlainValue() {
            SecretResolver resolver = SecretResolver.of(Map.of());
            assertThat(resolver.redact("plaintext")).isEqualTo("***");
        }

        @Test
        void redactNull() {
            SecretResolver resolver = SecretResolver.of(Map.of());
            assertThat(resolver.redact(null)).isNull();
        }
    }

    // ─── EnvSecretProvider ───────────────────────────────────────────────────

    @Nested
    @DisplayName("EnvSecretProvider")
    class EnvSecretProviderTest {

        @Test
        void nameIsEnv() {
            assertThat(new EnvSecretProvider().name()).isEqualTo("env");
        }

        @Test
        void blankLocatorThrows() {
            assertThatThrownBy(() -> new EnvSecretProvider().resolve(""))
                    .isInstanceOf(SecretResolutionException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void nullLocatorThrows() {
            assertThatThrownBy(() -> new EnvSecretProvider().resolve(null))
                    .isInstanceOf(SecretResolutionException.class);
        }

        @Test
        void missingEnvVarThrows() {
            // Use an env var that will not be set
            assertThatThrownBy(() -> new EnvSecretProvider().resolve("__GHATANA_NOT_SET_EVER__"))
                    .isInstanceOf(SecretResolutionException.class)
                    .hasMessageContaining("not set");
        }

        @Test
        void pathVariableResolvedWhenSet() {
            // PATH is always set in test environments
            String path = System.getenv("PATH");
            if (path != null && !path.isBlank()) {
                SecretValue val = new EnvSecretProvider().resolve("PATH");
                assertThat(val.asString()).isEqualTo(path);
            }
        }
    }
}
