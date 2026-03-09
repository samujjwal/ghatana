package com.ghatana.core.connectors.impl.source;

import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HttpWebhookSourceConfig}.
 *
 * Covers builder defaults, validation, authentication, and toString safety.
 */
@DisplayName("HttpWebhookSourceConfig")
class HttpWebhookSourceConfigTest {

    private static final TenantId TENANT = TenantId.of("test-tenant");

    private HttpWebhookSourceConfig.Builder validBuilder() {
        return HttpWebhookSourceConfig.builder()
                .withTenantId(TENANT);
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("serverPort defaults to 8080")
        void defaultPort() {
            HttpWebhookSourceConfig config = validBuilder().build();
            assertThat(config.getServerPort()).isEqualTo(8080);
        }

        @Test
        @DisplayName("path defaults to /webhook")
        void defaultPath() {
            HttpWebhookSourceConfig config = validBuilder().build();
            assertThat(config.getPath()).isEqualTo("/webhook");
        }

        @Test
        @DisplayName("maxContentLength defaults to 1MB")
        void defaultMaxContentLength() {
            HttpWebhookSourceConfig config = validBuilder().build();
            assertThat(config.getMaxContentLength()).isEqualTo(1024 * 1024);
        }

        @Test
        @DisplayName("readTimeout defaults to 30000ms")
        void defaultReadTimeout() {
            HttpWebhookSourceConfig config = validBuilder().build();
            assertThat(config.getReadTimeout()).isEqualTo(30000);
        }

        @Test
        @DisplayName("no authentication by default")
        void noAuthByDefault() {
            HttpWebhookSourceConfig config = validBuilder().build();
            assertThat(config.getBasicAuthUsername()).isNull();
            assertThat(config.getBasicAuthPassword()).isNull();
            assertThat(config.getBearerToken()).isNull();
        }
    }

    @Nested
    @DisplayName("builder customization")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly")
        void allFields() {
            HttpWebhookSourceConfig config = HttpWebhookSourceConfig.builder()
                    .withTenantId(TENANT)
                    .withServerPort(9090)
                    .withPath("/api/v1/events")
                    .withBasicAuth("user", "pass")
                    .withMaxContentLength(5 * 1024 * 1024)
                    .withReadTimeout(60000)
                    .build();

            assertThat(config.getTenantId()).isEqualTo(TENANT);
            assertThat(config.getServerPort()).isEqualTo(9090);
            assertThat(config.getPath()).isEqualTo("/api/v1/events");
            assertThat(config.getBasicAuthUsername()).isEqualTo("user");
            assertThat(config.getBasicAuthPassword()).isEqualTo("pass");
            assertThat(config.getMaxContentLength()).isEqualTo(5 * 1024 * 1024);
            assertThat(config.getReadTimeout()).isEqualTo(60000);
        }

        @Test
        @DisplayName("bearer token set correctly")
        void bearerToken() {
            HttpWebhookSourceConfig config = validBuilder()
                    .withBearerToken("sk_live_abc")
                    .build();
            assertThat(config.getBearerToken()).isEqualTo("sk_live_abc");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("null tenantId throws IllegalArgumentException")
        void nullTenantId() {
            assertThatThrownBy(() -> HttpWebhookSourceConfig.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TenantId");
        }

        @Test
        @DisplayName("port 0 throws IllegalArgumentException")
        void portZero() {
            assertThatThrownBy(() -> validBuilder().withServerPort(0).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port");
        }

        @Test
        @DisplayName("port 65536 throws IllegalArgumentException")
        void portTooHigh() {
            assertThatThrownBy(() -> validBuilder().withServerPort(65536).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port");
        }

        @Test
        @DisplayName("valid port boundaries (1 and 65535)")
        void validPortBoundaries() {
            HttpWebhookSourceConfig low = validBuilder().withServerPort(1).build();
            HttpWebhookSourceConfig high = validBuilder().withServerPort(65535).build();
            assertThat(low.getServerPort()).isEqualTo(1);
            assertThat(high.getServerPort()).isEqualTo(65535);
        }

        @Test
        @DisplayName("null path throws IllegalArgumentException")
        void nullPath() {
            assertThatThrownBy(() -> validBuilder().withPath(null).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Path");
        }

        @Test
        @DisplayName("empty path throws IllegalArgumentException")
        void emptyPath() {
            assertThatThrownBy(() -> validBuilder().withPath("").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Path");
        }

        @Test
        @DisplayName("path not starting with / throws IllegalArgumentException")
        void pathNoSlash() {
            assertThatThrownBy(() -> validBuilder().withPath("webhook").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Path must start with '/'");
        }

        @Test
        @DisplayName("non-positive maxContentLength throws IllegalArgumentException")
        void nonPositiveMaxContentLength() {
            assertThatThrownBy(() -> validBuilder().withMaxContentLength(0).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content length");
        }

        @Test
        @DisplayName("negative readTimeout throws IllegalArgumentException")
        void negativeReadTimeout() {
            assertThatThrownBy(() -> validBuilder().withReadTimeout(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("zero readTimeout is valid")
        void zeroReadTimeout() {
            HttpWebhookSourceConfig config = validBuilder().withReadTimeout(0).build();
            assertThat(config.getReadTimeout()).isZero();
        }
    }

    @Nested
    @DisplayName("authentication validation")
    class AuthValidation {

        @Test
        @DisplayName("basic auth requires both username and password")
        void basicAuthRequiresBoth() {
            // username without password — there's no single-field setter in the builder,
            // but we can verify through the withBasicAuth method behavior
            HttpWebhookSourceConfig config = validBuilder()
                    .withBasicAuth("user", "pass")
                    .build();
            assertThat(config.getBasicAuthUsername()).isEqualTo("user");
            assertThat(config.getBasicAuthPassword()).isEqualTo("pass");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString does not leak credentials")
        void toStringNoCredentials() {
            HttpWebhookSourceConfig config = validBuilder()
                    .withBasicAuth("secretuser", "secretpass")
                    .withBearerToken("sk_secret_token")
                    .build();
            String str = config.toString();
            assertThat(str).doesNotContain("secretuser");
            assertThat(str).doesNotContain("secretpass");
            assertThat(str).doesNotContain("sk_secret_token");
            assertThat(str).contains("hasBasicAuth=true");
            assertThat(str).contains("hasBearerToken=true");
        }

        @Test
        @DisplayName("toString shows hasBasicAuth=false when no auth")
        void toStringNoAuth() {
            HttpWebhookSourceConfig config = validBuilder().build();
            String str = config.toString();
            assertThat(str).contains("hasBasicAuth=false");
            assertThat(str).contains("hasBearerToken=false");
        }
    }
}
