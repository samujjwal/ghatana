package com.ghatana.platform.governance.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SsrfGuard}.
 *
 * Verifies that SSRF protection correctly allows legitimate endpoints
 * and blocks dangerous targets: non-HTTP schemes, cloud metadata IPs,
 * link-local ranges, and (in strict mode) private/loopback addresses. // GH-90000
 */
@DisplayName("SsrfGuard [GH-90000]")
class SsrfGuardTest {

    // -----------------------------------------------------------------------
    // validateEndpoint (permissive mode) // GH-90000
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validateEndpoint — allowed URLs [GH-90000]")
    class AllowedUrls {

        @Test
        @DisplayName("http localhost (Ollama default) is allowed [GH-90000]")
        void ollamaDefaultIsAllowed() { // GH-90000
            assertThatCode(() -> SsrfGuard.validateEndpoint("http://localhost:11434 [GH-90000]"))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("https external API is allowed [GH-90000]")
        void httpsExternalAllowed() { // GH-90000
            assertThatCode(() -> SsrfGuard.validateEndpoint("https://api.example.com [GH-90000]"))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("http private IP is allowed in permissive mode [GH-90000]")
        void privateIpAllowedPermissive() { // GH-90000
            assertThatCode(() -> SsrfGuard.validateEndpoint("http://10.0.0.5:11434 [GH-90000]"))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("http 192.168.x.x is allowed in permissive mode [GH-90000]")
        void rfc1918AllowedPermissive() { // GH-90000
            assertThatCode(() -> SsrfGuard.validateEndpoint("http://192.168.1.1:8080 [GH-90000]"))
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateEndpoint — blocked URLs [GH-90000]")
    class BlockedUrls {

        @Test
        @DisplayName("null URL throws IllegalArgumentException [GH-90000]")
        void nullUrlThrows() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("file:// scheme is blocked [GH-90000]")
        void fileSchemeBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("file:///etc/passwd [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("http/https [GH-90000]");
        }

        @Test
        @DisplayName("ftp:// scheme is blocked [GH-90000]")
        void ftpSchemeBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("ftp://attacker.com/data [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("http/https [GH-90000]");
        }

        @Test
        @DisplayName("gopher:// scheme is blocked [GH-90000]")
        void gopherSchemeBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("gopher://attacker.com/ [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("http/https [GH-90000]");
        }

        @Test
        @DisplayName("AWS IMDS ip (169.254.169.254) is blocked [GH-90000]")
        void awsImdsIpBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://169.254.169.254/latest/meta-data/ [GH-90000]"))
                .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("link-local prefix 169.254.x.x is blocked [GH-90000]")
        void linkLocalPrefixBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://169.254.0.1/ [GH-90000]"))
                .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Alibaba Cloud metadata (100.100.100.200) is blocked [GH-90000]")
        void alibabaMetadataBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://100.100.100.200/latest/meta-data/ [GH-90000]"))
                .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("GCP metadata hostname is blocked [GH-90000]")
        void gcpMetadataHostBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://metadata.google.internal/ [GH-90000]"))
                .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("URL with embedded credentials is blocked [GH-90000]")
        void embeddedCredentialsBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://user:pass@internal-service/ [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("credentials [GH-90000]");
        }

        @Test
        @DisplayName("URL with no host is blocked [GH-90000]")
        void noHostBlocked() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http:///path [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("no host [GH-90000]");
        }
    }

    // -----------------------------------------------------------------------
    // validateEndpointStrict
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validateEndpointStrict — additional private/loopback blocking [GH-90000]")
    class StrictMode {

        @Test
        @DisplayName("https external API still allowed in strict mode [GH-90000]")
        void externalAllowedStrict() { // GH-90000
            assertThatCode(() -> SsrfGuard.validateEndpointStrict("https://api.openai.com/v1 [GH-90000]"))
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("localhost is blocked in strict mode [GH-90000]")
        void localhostBlockedStrict() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://localhost:11434 [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("strict mode [GH-90000]");
        }

        @Test
        @DisplayName("127.0.0.1 is blocked in strict mode [GH-90000]")
        void loopbackIpBlockedStrict() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://127.0.0.1:6379 [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("strict mode [GH-90000]");
        }

        @Test
        @DisplayName("10.x.x.x private IP is blocked in strict mode [GH-90000]")
        void privateIpBlockedStrict() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://10.0.0.5:8080 [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("strict mode [GH-90000]");
        }

        @Test
        @DisplayName("172.16.x.x private IP is blocked in strict mode [GH-90000]")
        void privateIp172BlockedStrict() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://172.16.0.1/ [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("strict mode [GH-90000]");
        }

        @Test
        @DisplayName("192.168.x.x is blocked in strict mode [GH-90000]")
        void privateIp192BlockedStrict() { // GH-90000
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://192.168.1.100:9200 [GH-90000]"))
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("strict mode [GH-90000]");
        }
    }
}
