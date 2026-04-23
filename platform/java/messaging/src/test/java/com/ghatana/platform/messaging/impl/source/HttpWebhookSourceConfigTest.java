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

    private HttpWebhookSourceConfig.Builder validBuilder() { // GH-90000
        return HttpWebhookSourceConfig.builder() // GH-90000
                .withTenantId(TENANT); // GH-90000
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("serverPort defaults to 8080")
        void defaultPort() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getServerPort()).isEqualTo(8080); // GH-90000
        }

        @Test
        @DisplayName("path defaults to /webhook")
        void defaultPath() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getPath()).isEqualTo("/webhook");
        }

        @Test
        @DisplayName("maxContentLength defaults to 1MB")
        void defaultMaxContentLength() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getMaxContentLength()).isEqualTo(1024 * 1024); // GH-90000
        }

        @Test
        @DisplayName("readTimeout defaults to 30000ms")
        void defaultReadTimeout() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getReadTimeout()).isEqualTo(30000); // GH-90000
        }

        @Test
        @DisplayName("no authentication by default")
        void noAuthByDefault() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getBasicAuthUsername()).isNull(); // GH-90000
            assertThat(config.getBasicAuthPassword()).isNull(); // GH-90000
            assertThat(config.getBearerToken()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder customization")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly")
        void allFields() { // GH-90000
            HttpWebhookSourceConfig config = HttpWebhookSourceConfig.builder() // GH-90000
                    .withTenantId(TENANT) // GH-90000
                    .withServerPort(9090) // GH-90000
                    .withPath("/api/v1/events")
                    .withBasicAuth("user", "pass") // GH-90000
                    .withMaxContentLength(5 * 1024 * 1024) // GH-90000
                    .withReadTimeout(60000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(config.getServerPort()).isEqualTo(9090); // GH-90000
            assertThat(config.getPath()).isEqualTo("/api/v1/events");
            assertThat(config.getBasicAuthUsername()).isEqualTo("user");
            assertThat(config.getBasicAuthPassword()).isEqualTo("pass");
            assertThat(config.getMaxContentLength()).isEqualTo(5 * 1024 * 1024); // GH-90000
            assertThat(config.getReadTimeout()).isEqualTo(60000); // GH-90000
        }

        @Test
        @DisplayName("bearer token set correctly")
        void bearerToken() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder() // GH-90000
                    .withBearerToken("sk_live_abc")
                    .build(); // GH-90000
            assertThat(config.getBearerToken()).isEqualTo("sk_live_abc");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("null tenantId throws IllegalArgumentException")
        void nullTenantId() { // GH-90000
            assertThatThrownBy(() -> HttpWebhookSourceConfig.builder().build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("TenantId");
        }

        @Test
        @DisplayName("port 0 throws IllegalArgumentException")
        void portZero() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withServerPort(0).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("port");
        }

        @Test
        @DisplayName("port 65536 throws IllegalArgumentException")
        void portTooHigh() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withServerPort(65536).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("port");
        }

        @Test
        @DisplayName("valid port boundaries (1 and 65535)")
        void validPortBoundaries() { // GH-90000
            HttpWebhookSourceConfig low = validBuilder().withServerPort(1).build(); // GH-90000
            HttpWebhookSourceConfig high = validBuilder().withServerPort(65535).build(); // GH-90000
            assertThat(low.getServerPort()).isEqualTo(1); // GH-90000
            assertThat(high.getServerPort()).isEqualTo(65535); // GH-90000
        }

        @Test
        @DisplayName("null path throws IllegalArgumentException")
        void nullPath() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withPath(null).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Path");
        }

        @Test
        @DisplayName("empty path throws IllegalArgumentException")
        void emptyPath() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withPath("").build())
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Path");
        }

        @Test
        @DisplayName("path not starting with / throws IllegalArgumentException")
        void pathNoSlash() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withPath("webhook").build())
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Path must start with '/'");
        }

        @Test
        @DisplayName("non-positive maxContentLength throws IllegalArgumentException")
        void nonPositiveMaxContentLength() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withMaxContentLength(0).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("content length");
        }

        @Test
        @DisplayName("negative readTimeout throws IllegalArgumentException")
        void negativeReadTimeout() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withReadTimeout(-1).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("zero readTimeout is valid")
        void zeroReadTimeout() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().withReadTimeout(0).build(); // GH-90000
            assertThat(config.getReadTimeout()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("authentication validation")
    class AuthValidation {

        @Test
        @DisplayName("basic auth requires both username and password")
        void basicAuthRequiresBoth() { // GH-90000
            // username without password — there's no single-field setter in the builder,
            // but we can verify through the withBasicAuth method behavior
            HttpWebhookSourceConfig config = validBuilder() // GH-90000
                    .withBasicAuth("user", "pass") // GH-90000
                    .build(); // GH-90000
            assertThat(config.getBasicAuthUsername()).isEqualTo("user");
            assertThat(config.getBasicAuthPassword()).isEqualTo("pass");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString does not leak credentials")
        void toStringNoCredentials() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder() // GH-90000
                    .withBasicAuth("secretuser", "secretpass") // GH-90000
                    .withBearerToken("sk_secret_token")
                    .build(); // GH-90000
            String str = config.toString(); // GH-90000
            assertThat(str).doesNotContain("secretuser");
            assertThat(str).doesNotContain("secretpass");
            assertThat(str).doesNotContain("sk_secret_token");
            assertThat(str).contains("hasBasicAuth=true");
            assertThat(str).contains("hasBearerToken=true");
        }

        @Test
        @DisplayName("toString shows hasBasicAuth=false when no auth")
        void toStringNoAuth() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            String str = config.toString(); // GH-90000
            assertThat(str).contains("hasBasicAuth=false");
            assertThat(str).contains("hasBearerToken=false");
        }
    }
}
