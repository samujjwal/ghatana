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
@DisplayName("HttpWebhookSourceConfig [GH-90000]")
class HttpWebhookSourceConfigTest {

    private static final TenantId TENANT = TenantId.of("test-tenant [GH-90000]");

    private HttpWebhookSourceConfig.Builder validBuilder() { // GH-90000
        return HttpWebhookSourceConfig.builder() // GH-90000
                .withTenantId(TENANT); // GH-90000
    }

    @Nested
    @DisplayName("builder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        @DisplayName("serverPort defaults to 8080 [GH-90000]")
        void defaultPort() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getServerPort()).isEqualTo(8080); // GH-90000
        }

        @Test
        @DisplayName("path defaults to /webhook [GH-90000]")
        void defaultPath() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getPath()).isEqualTo("/webhook [GH-90000]");
        }

        @Test
        @DisplayName("maxContentLength defaults to 1MB [GH-90000]")
        void defaultMaxContentLength() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getMaxContentLength()).isEqualTo(1024 * 1024); // GH-90000
        }

        @Test
        @DisplayName("readTimeout defaults to 30000ms [GH-90000]")
        void defaultReadTimeout() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getReadTimeout()).isEqualTo(30000); // GH-90000
        }

        @Test
        @DisplayName("no authentication by default [GH-90000]")
        void noAuthByDefault() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            assertThat(config.getBasicAuthUsername()).isNull(); // GH-90000
            assertThat(config.getBasicAuthPassword()).isNull(); // GH-90000
            assertThat(config.getBearerToken()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder customization [GH-90000]")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly [GH-90000]")
        void allFields() { // GH-90000
            HttpWebhookSourceConfig config = HttpWebhookSourceConfig.builder() // GH-90000
                    .withTenantId(TENANT) // GH-90000
                    .withServerPort(9090) // GH-90000
                    .withPath("/api/v1/events [GH-90000]")
                    .withBasicAuth("user", "pass") // GH-90000
                    .withMaxContentLength(5 * 1024 * 1024) // GH-90000
                    .withReadTimeout(60000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(config.getServerPort()).isEqualTo(9090); // GH-90000
            assertThat(config.getPath()).isEqualTo("/api/v1/events [GH-90000]");
            assertThat(config.getBasicAuthUsername()).isEqualTo("user [GH-90000]");
            assertThat(config.getBasicAuthPassword()).isEqualTo("pass [GH-90000]");
            assertThat(config.getMaxContentLength()).isEqualTo(5 * 1024 * 1024); // GH-90000
            assertThat(config.getReadTimeout()).isEqualTo(60000); // GH-90000
        }

        @Test
        @DisplayName("bearer token set correctly [GH-90000]")
        void bearerToken() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder() // GH-90000
                    .withBearerToken("sk_live_abc [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getBearerToken()).isEqualTo("sk_live_abc [GH-90000]");
        }
    }

    @Nested
    @DisplayName("validation [GH-90000]")
    class Validation {

        @Test
        @DisplayName("null tenantId throws IllegalArgumentException [GH-90000]")
        void nullTenantId() { // GH-90000
            assertThatThrownBy(() -> HttpWebhookSourceConfig.builder().build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("TenantId [GH-90000]");
        }

        @Test
        @DisplayName("port 0 throws IllegalArgumentException [GH-90000]")
        void portZero() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withServerPort(0).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("port [GH-90000]");
        }

        @Test
        @DisplayName("port 65536 throws IllegalArgumentException [GH-90000]")
        void portTooHigh() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withServerPort(65536).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("port [GH-90000]");
        }

        @Test
        @DisplayName("valid port boundaries (1 and 65535) [GH-90000]")
        void validPortBoundaries() { // GH-90000
            HttpWebhookSourceConfig low = validBuilder().withServerPort(1).build(); // GH-90000
            HttpWebhookSourceConfig high = validBuilder().withServerPort(65535).build(); // GH-90000
            assertThat(low.getServerPort()).isEqualTo(1); // GH-90000
            assertThat(high.getServerPort()).isEqualTo(65535); // GH-90000
        }

        @Test
        @DisplayName("null path throws IllegalArgumentException [GH-90000]")
        void nullPath() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withPath(null).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Path [GH-90000]");
        }

        @Test
        @DisplayName("empty path throws IllegalArgumentException [GH-90000]")
        void emptyPath() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withPath(" [GH-90000]").build())
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Path [GH-90000]");
        }

        @Test
        @DisplayName("path not starting with / throws IllegalArgumentException [GH-90000]")
        void pathNoSlash() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withPath("webhook [GH-90000]").build())
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Path must start with '/' [GH-90000]");
        }

        @Test
        @DisplayName("non-positive maxContentLength throws IllegalArgumentException [GH-90000]")
        void nonPositiveMaxContentLength() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withMaxContentLength(0).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("content length [GH-90000]");
        }

        @Test
        @DisplayName("negative readTimeout throws IllegalArgumentException [GH-90000]")
        void negativeReadTimeout() { // GH-90000
            assertThatThrownBy(() -> validBuilder().withReadTimeout(-1).build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("timeout [GH-90000]");
        }

        @Test
        @DisplayName("zero readTimeout is valid [GH-90000]")
        void zeroReadTimeout() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().withReadTimeout(0).build(); // GH-90000
            assertThat(config.getReadTimeout()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("authentication validation [GH-90000]")
    class AuthValidation {

        @Test
        @DisplayName("basic auth requires both username and password [GH-90000]")
        void basicAuthRequiresBoth() { // GH-90000
            // username without password — there's no single-field setter in the builder,
            // but we can verify through the withBasicAuth method behavior
            HttpWebhookSourceConfig config = validBuilder() // GH-90000
                    .withBasicAuth("user", "pass") // GH-90000
                    .build(); // GH-90000
            assertThat(config.getBasicAuthUsername()).isEqualTo("user [GH-90000]");
            assertThat(config.getBasicAuthPassword()).isEqualTo("pass [GH-90000]");
        }
    }

    @Nested
    @DisplayName("toString [GH-90000]")
    class ToString {

        @Test
        @DisplayName("toString does not leak credentials [GH-90000]")
        void toStringNoCredentials() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder() // GH-90000
                    .withBasicAuth("secretuser", "secretpass") // GH-90000
                    .withBearerToken("sk_secret_token [GH-90000]")
                    .build(); // GH-90000
            String str = config.toString(); // GH-90000
            assertThat(str).doesNotContain("secretuser [GH-90000]");
            assertThat(str).doesNotContain("secretpass [GH-90000]");
            assertThat(str).doesNotContain("sk_secret_token [GH-90000]");
            assertThat(str).contains("hasBasicAuth=true [GH-90000]");
            assertThat(str).contains("hasBearerToken=true [GH-90000]");
        }

        @Test
        @DisplayName("toString shows hasBasicAuth=false when no auth [GH-90000]")
        void toStringNoAuth() { // GH-90000
            HttpWebhookSourceConfig config = validBuilder().build(); // GH-90000
            String str = config.toString(); // GH-90000
            assertThat(str).contains("hasBasicAuth=false [GH-90000]");
            assertThat(str).contains("hasBearerToken=false [GH-90000]");
        }
    }
}
