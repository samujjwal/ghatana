/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
        void messageConstructor() { // GH-90000
            SecretResolutionException ex = new SecretResolutionException("boom");
            assertThat(ex).hasMessage("boom");
        }

        @Test
        void messageCauseConstructor() { // GH-90000
            Throwable cause = new RuntimeException("root");
            SecretResolutionException ex = new SecretResolutionException("wrapped", cause); // GH-90000
            assertThat(ex).hasMessage("wrapped").hasCause(cause);
        }
    }

    // ─── SecretValue ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecretValue")
    class SecretValueTest {

        @Test
        void ofStringAndBack() { // GH-90000
            SecretValue sv = SecretValue.ofString("mysecret");
            assertThat(sv.asString()).isEqualTo("mysecret");
        }

        @Test
        void ofCharArray() { // GH-90000
            char[] chars = {'a', 'b', 'c'};
            SecretValue sv = SecretValue.of(chars); // GH-90000
            assertThat(sv.asString()).isEqualTo("abc");
        }

        @Test
        void asCharArrayCopyIsDefensive() { // GH-90000
            SecretValue sv = SecretValue.ofString("xyz");
            char[] copy = sv.asCharArrayCopy(); // GH-90000
            copy[0] = 'Z';
            // Original should be unchanged
            assertThat(sv.asString()).isEqualTo("xyz");
        }

        @Test
        void clearMakesValueInaccessible() { // GH-90000
            SecretValue sv = SecretValue.ofString("secret");
            sv.clear(); // GH-90000
            assertThatThrownBy(sv::asString) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("cleared");
        }

        @Test
        void clearMakesCharArrayInaccessible() { // GH-90000
            SecretValue sv = SecretValue.ofString("secret");
            sv.clear(); // GH-90000
            assertThatThrownBy(sv::asCharArrayCopy) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        void clearIsIdempotent() { // GH-90000
            SecretValue sv = SecretValue.ofString("secret");
            sv.clear(); // GH-90000
            sv.clear(); // should not throw // GH-90000
        }

        @Test
        void toStringIsRedacted() { // GH-90000
            assertThat(SecretValue.ofString("mypassword").toString()).isEqualTo("***");
        }
    }

    // ─── SecretReference ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecretReference")
    class SecretReferenceTest {

        @Test
        void parseValidReference() { // GH-90000
            SecretReference ref = SecretReference.parse("secret:env:MY_VAR");
            assertThat(ref.provider()).isEqualTo("env");
            assertThat(ref.locator()).isEqualTo("MY_VAR");
        }

        @Test
        void parseWithColonInLocator() { // GH-90000
            SecretReference ref = SecretReference.parse("secret:file:/etc/secrets/db:password");
            assertThat(ref.provider()).isEqualTo("file");
            assertThat(ref.locator()).isEqualTo("/etc/secrets/db:password");
        }

        @Test
        void toStringRoundTrip() { // GH-90000
            String original = "secret:env:MY_VAR";
            assertThat(SecretReference.parse(original).toString()).isEqualTo(original); // GH-90000
        }

        @Test
        void isSecretReferenceReturnsTrueForPrefixed() { // GH-90000
            assertThat(SecretReference.isSecretReference("secret:env:VAR")).isTrue();
        }

        @Test
        void isSecretReferenceReturnsFalseForPlain() { // GH-90000
            assertThat(SecretReference.isSecretReference("plainvalue")).isFalse();
        }

        @Test
        void isSecretReferenceReturnsFalseForNull() { // GH-90000
            assertThat(SecretReference.isSecretReference(null)).isFalse(); // GH-90000
        }

        @Test
        void parseBlankThrows() { // GH-90000
            assertThatThrownBy(() -> SecretReference.parse(""))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("blank");
        }

        @Test
        void parseNullThrows() { // GH-90000
            assertThatThrownBy(() -> SecretReference.parse(null)) // GH-90000
                    .isInstanceOf(SecretResolutionException.class); // GH-90000
        }

        @Test
        void parseMissingPrefixThrows() { // GH-90000
            assertThatThrownBy(() -> SecretReference.parse("env:MY_VAR"))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("prefix");
        }

        @Test
        void parseMissingLocatorThrows() { // GH-90000
            // "secret:env:" — locator is empty
            assertThatThrownBy(() -> SecretReference.parse("secret:env:"))
                    .isInstanceOf(SecretResolutionException.class); // GH-90000
        }

        @Test
        void parseMissingProviderSeparatorThrows() { // GH-90000
            // "secret:nocoronafter" — no second colon
            assertThatThrownBy(() -> SecretReference.parse("secret:nocolon"))
                    .isInstanceOf(SecretResolutionException.class); // GH-90000
        }
    }

    // ─── SecretResolver ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecretResolver")
    class SecretResolverTest {

        @Test
        void defaultResolverHasEnvAndFileProviders() { // GH-90000
            SecretResolver resolver = SecretResolver.defaultResolver(); // GH-90000
            assertThat(resolver.providers()).containsKeys("env", "file"); // GH-90000
        }

        @Test
        void ofWithCustomProvider() { // GH-90000
            SecretProvider stub = new SecretProvider() { // GH-90000
                @Override
                public String name() { return "stub"; } // GH-90000
                @Override
                public SecretValue resolve(String locator) { // GH-90000
                    return SecretValue.ofString("stubval-" + locator); // GH-90000
                }
            };
            SecretResolver resolver = SecretResolver.of(Map.of("stub", stub)); // GH-90000
            SecretValue val = resolver.resolveSecretReference("secret:stub:mykey");
            assertThat(val.asString()).isEqualTo("stubval-mykey");
        }

        @Test
        void resolveToStringPassesThroughPlainValue() { // GH-90000
            SecretResolver resolver = SecretResolver.of(Map.of()); // GH-90000
            assertThat(resolver.resolveToString("plain-text")).isEqualTo("plain-text");
        }

        @Test
        void resolveToStringResolvesSecretRef() { // GH-90000
            SecretProvider stub = new SecretProvider() { // GH-90000
                @Override public String name() { return "stub"; } // GH-90000
                @Override public SecretValue resolve(String locator) { return SecretValue.ofString("resolved"); }
            };
            SecretResolver resolver = SecretResolver.of(Map.of("stub", stub)); // GH-90000
            assertThat(resolver.resolveToString("secret:stub:key")).isEqualTo("resolved");
        }

        @Test
        void resolveUnknownProviderThrows() { // GH-90000
            SecretResolver resolver = SecretResolver.of(Map.of()); // GH-90000
            assertThatThrownBy(() -> resolver.resolveSecretReference("secret:unknown:key"))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("unknown");
        }

        @Test
        void ofNullThrows() { // GH-90000
            assertThatThrownBy(() -> SecretResolver.of(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        void redactSecretReference() { // GH-90000
            SecretResolver resolver = SecretResolver.of(Map.of()); // GH-90000
            String redacted = resolver.redact("secret:env:MY_SECRET");
            assertThat(redacted).isEqualTo("secret:env:***");
        }

        @Test
        void redactPlainValue() { // GH-90000
            SecretResolver resolver = SecretResolver.of(Map.of()); // GH-90000
            assertThat(resolver.redact("plaintext")).isEqualTo("***");
        }

        @Test
        void redactNull() { // GH-90000
            SecretResolver resolver = SecretResolver.of(Map.of()); // GH-90000
            assertThat(resolver.redact(null)).isNull(); // GH-90000
        }
    }

    // ─── EnvSecretProvider ───────────────────────────────────────────────────

    @Nested
    @DisplayName("EnvSecretProvider")
    class EnvSecretProviderTest {

        @Test
        void nameIsEnv() { // GH-90000
            assertThat(new EnvSecretProvider().name()).isEqualTo("env");
        }

        @Test
        void blankLocatorThrows() { // GH-90000
            assertThatThrownBy(() -> new EnvSecretProvider().resolve(""))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("blank");
        }

        @Test
        void nullLocatorThrows() { // GH-90000
            assertThatThrownBy(() -> new EnvSecretProvider().resolve(null)) // GH-90000
                    .isInstanceOf(SecretResolutionException.class); // GH-90000
        }

        @Test
        void missingEnvVarThrows() { // GH-90000
            // Use an env var that will not be set
            assertThatThrownBy(() -> new EnvSecretProvider().resolve("__GHATANA_NOT_SET_EVER__"))
                    .isInstanceOf(SecretResolutionException.class) // GH-90000
                    .hasMessageContaining("not set");
        }

        @Test
        void pathVariableResolvedWhenSet() { // GH-90000
            // PATH is always set in test environments
            String path = System.getenv("PATH");
            if (path != null && !path.isBlank()) { // GH-90000
                SecretValue val = new EnvSecretProvider().resolve("PATH");
                assertThat(val.asString()).isEqualTo(path); // GH-90000
            }
        }
    }
}
