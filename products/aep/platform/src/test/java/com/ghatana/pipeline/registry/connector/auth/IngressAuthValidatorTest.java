package com.ghatana.pipeline.registry.connector.auth;

import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IngressAuthValidator}.
 */
class IngressAuthValidatorTest {

    // ─── NONE auth ───────────────────────────────────────────────────

    @Nested
    @DisplayName("NONE auth (allow-all)")
    class NoneAuthTests {

        @Test
        @DisplayName("null spec defaults to NONE — any token accepted")
        void nullSpec_allowAll() {
            IngressAuthValidator v = IngressAuthValidator.fromSpec(null);
            assertThat(v.validate(null).isAuthorized()).isTrue();
            assertThat(v.validate("anything").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("empty properties defaults to NONE")
        void emptyProperties_allowAll() {
            ConnectorSpec spec = ConnectorSpec.builder().id("c").build();
            IngressAuthValidator v = IngressAuthValidator.fromSpec(spec);
            assertThat(v.validate(null).isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("explicit auth.type=NONE allows any token")
        void explicitNone_allowAll() {
            ConnectorSpec spec = ConnectorSpec.builder()
                    .id("c")
                    .properties(Map.of("auth.type", "NONE"))
                    .build();
            IngressAuthValidator v = IngressAuthValidator.fromSpec(spec);
            assertThat(v.validate("random-garbage").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("unknown auth.type falls back to NONE")
        void unknownType_fallsBackToNone() {
            ConnectorSpec spec = ConnectorSpec.builder()
                    .id("c")
                    .properties(Map.of("auth.type", "OAUTH2"))
                    .build();
            IngressAuthValidator v = IngressAuthValidator.fromSpec(spec);
            assertThat(v.validate(null).isAuthorized()).isTrue();
        }
    }

    // ─── BEARER auth ─────────────────────────────────────────────────

    @Nested
    @DisplayName("BEARER auth")
    class BearerAuthTests {

        private IngressAuthValidator validator(String token) {
            ConnectorSpec spec = ConnectorSpec.builder()
                    .id("c")
                    .properties(Map.of("auth.type", "BEARER", "auth.token", token))
                    .build();
            return IngressAuthValidator.fromSpec(spec);
        }

        @Test
        @DisplayName("valid Bearer token is authorized")
        void validToken_authorized() {
            IngressAuthValidator v = validator("my-secret");
            assertThat(v.validate("Bearer my-secret").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("token with surrounding whitespace is accepted")
        void tokenWithWhitespace_authorized() {
            IngressAuthValidator v = validator("my-secret");
            assertThat(v.validate("Bearer  my-secret ").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("wrong token is denied")
        void wrongToken_denied() {
            IngressAuthValidator v = validator("my-secret");
            IngressAuthValidator.AuthResult result = v.validate("Bearer wrong");
            assertThat(result.isAuthorized()).isFalse();
            assertThat(result.reason()).contains("invalid bearer token");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("null or empty Authorization header is denied")
        void missingHeader_denied(String header) {
            IngressAuthValidator v = validator("my-secret");
            IngressAuthValidator.AuthResult result = v.validate(header);
            assertThat(result.isAuthorized()).isFalse();
            assertThat(result.reason()).contains("missing");
        }

        @Test
        @DisplayName("non-Bearer prefix is denied")
        void basicPrefix_denied() {
            IngressAuthValidator v = validator("my-secret");
            IngressAuthValidator.AuthResult result = v.validate("Basic bXktc2VjcmV0");
            assertThat(result.isAuthorized()).isFalse();
            assertThat(result.reason()).contains("Bearer");
        }

        @Test
        @DisplayName("missing auth.token falls back to NONE (logs warning)")
        void missingToken_fallsBackToNone() {
            ConnectorSpec spec = ConnectorSpec.builder()
                    .id("c")
                    .properties(Map.of("auth.type", "BEARER"))
                    .build();
            IngressAuthValidator v = IngressAuthValidator.fromSpec(spec);
            assertThat(v.validate(null).isAuthorized()).isTrue();
        }
    }

    // ─── API_KEY auth ─────────────────────────────────────────────────

    @Nested
    @DisplayName("API_KEY auth")
    class ApiKeyAuthTests {

        private IngressAuthValidator validator(String key) {
            ConnectorSpec spec = ConnectorSpec.builder()
                    .id("c")
                    .properties(Map.of("auth.type", "API_KEY", "auth.token", key))
                    .build();
            return IngressAuthValidator.fromSpec(spec);
        }

        @Test
        @DisplayName("valid API key is authorized")
        void validKey_authorized() {
            IngressAuthValidator v = validator("api-key-xyz");
            assertThat(v.validate("api-key-xyz").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("key with whitespace is stripped and accepted")
        void keyWithWhitespace_accepted() {
            IngressAuthValidator v = validator("api-key-xyz");
            assertThat(v.validate("  api-key-xyz  ").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("wrong API key is denied")
        void wrongKey_denied() {
            IngressAuthValidator v = validator("api-key-xyz");
            IngressAuthValidator.AuthResult result = v.validate("wrong-key");
            assertThat(result.isAuthorized()).isFalse();
            assertThat(result.reason()).contains("invalid API key");
        }

        @Test
        @DisplayName("null API key header is denied")
        void nullHeader_denied() {
            IngressAuthValidator v = validator("api-key-xyz");
            assertThat(v.validate(null).isAuthorized()).isFalse();
        }
    }

    // ─── BASIC auth ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BASIC auth")
    class BasicAuthTests {

        private IngressAuthValidator validator(String user, String pass) {
            ConnectorSpec spec = ConnectorSpec.builder()
                    .id("c")
                    .properties(Map.of(
                            "auth.type", "BASIC",
                            "auth.username", user,
                            "auth.password", pass))
                    .build();
            return IngressAuthValidator.fromSpec(spec);
        }

        @Test
        @DisplayName("valid Basic credentials are authorized")
        void validCredentials_authorized() {
            IngressAuthValidator v = validator("admin", "s3cr3t");
            // Base64("admin:s3cr3t") = "YWRtaW46czNjcjN0"
            assertThat(v.validate("Basic YWRtaW46czNjcjN0").isAuthorized()).isTrue();
        }

        @Test
        @DisplayName("wrong credentials are denied")
        void wrongCredentials_denied() {
            IngressAuthValidator v = validator("admin", "s3cr3t");
            assertThat(v.validate("Basic d3Jvbmc6Y3JlZA==").isAuthorized()).isFalse();
        }

        @Test
        @DisplayName("non-Basic prefix is denied")
        void bearerPrefix_denied() {
            IngressAuthValidator v = validator("admin", "s3cr3t");
            IngressAuthValidator.AuthResult result = v.validate("Bearer YWRtaW46czNjcjN0");
            assertThat(result.isAuthorized()).isFalse();
            assertThat(result.reason()).contains("Basic");
        }
    }

    // ─── AuthResult ───────────────────────────────────────────────────

    @Nested
    @DisplayName("AuthResult helpers")
    class AuthResultTests {

        @Test
        @DisplayName("AuthResult.allow() is authorized with empty reason")
        void allow_isAuthorized() {
            IngressAuthValidator.AuthResult r = IngressAuthValidator.AuthResult.allow();
            assertThat(r.isAuthorized()).isTrue();
            assertThat(r.reason()).isEmpty();
        }

        @Test
        @DisplayName("AuthResult.deny(reason) is not authorized")
        void deny_isNotAuthorized() {
            IngressAuthValidator.AuthResult r = IngressAuthValidator.AuthResult.deny("bad token");
            assertThat(r.isAuthorized()).isFalse();
            assertThat(r.reason()).isEqualTo("bad token");
        }
    }
}
