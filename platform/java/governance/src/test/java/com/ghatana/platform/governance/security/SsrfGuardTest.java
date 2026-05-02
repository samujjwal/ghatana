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
 * link-local ranges, and (in strict mode) private/loopback addresses. 
 */
@DisplayName("SsrfGuard")
class SsrfGuardTest {

    // -----------------------------------------------------------------------
    // validateEndpoint (permissive mode) 
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validateEndpoint — allowed URLs")
    class AllowedUrls {

        @Test
        @DisplayName("http localhost (Ollama default) is allowed")
        void ollamaDefaultIsAllowed() { 
            assertThatCode(() -> SsrfGuard.validateEndpoint("http://localhost:11434"))
                .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("https external API is allowed")
        void httpsExternalAllowed() { 
            assertThatCode(() -> SsrfGuard.validateEndpoint("https://api.example.com"))
                .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("http private IP is allowed in permissive mode")
        void privateIpAllowedPermissive() { 
            assertThatCode(() -> SsrfGuard.validateEndpoint("http://10.0.0.5:11434"))
                .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("http 192.168.x.x is allowed in permissive mode")
        void rfc1918AllowedPermissive() { 
            assertThatCode(() -> SsrfGuard.validateEndpoint("http://192.168.1.1:8080"))
                .doesNotThrowAnyException(); 
        }
    }

    @Nested
    @DisplayName("validateEndpoint — blocked URLs")
    class BlockedUrls {

        @Test
        @DisplayName("null URL throws IllegalArgumentException")
        void nullUrlThrows() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint(null)) 
                .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("file:// scheme is blocked")
        void fileSchemeBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("file:///etc/passwd"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("http/https");
        }

        @Test
        @DisplayName("ftp:// scheme is blocked")
        void ftpSchemeBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("ftp://attacker.com/data"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("http/https");
        }

        @Test
        @DisplayName("gopher:// scheme is blocked")
        void gopherSchemeBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("gopher://attacker.com/"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("http/https");
        }

        @Test
        @DisplayName("AWS IMDS ip (169.254.169.254) is blocked")
        void awsImdsIpBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(SecurityException.class); 
        }

        @Test
        @DisplayName("link-local prefix 169.254.x.x is blocked")
        void linkLocalPrefixBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://169.254.0.1/"))
                .isInstanceOf(SecurityException.class); 
        }

        @Test
        @DisplayName("Alibaba Cloud metadata (100.100.100.200) is blocked")
        void alibabaMetadataBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://100.100.100.200/latest/meta-data/"))
                .isInstanceOf(SecurityException.class); 
        }

        @Test
        @DisplayName("GCP metadata hostname is blocked")
        void gcpMetadataHostBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://metadata.google.internal/"))
                .isInstanceOf(SecurityException.class); 
        }

        @Test
        @DisplayName("URL with embedded credentials is blocked")
        void embeddedCredentialsBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http://user:pass@internal-service/"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("credentials");
        }

        @Test
        @DisplayName("URL with no host is blocked")
        void noHostBlocked() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpoint("http:///path"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("no host");
        }
    }

    // -----------------------------------------------------------------------
    // validateEndpointStrict
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validateEndpointStrict — additional private/loopback blocking")
    class StrictMode {

        @Test
        @DisplayName("https external API still allowed in strict mode")
        void externalAllowedStrict() { 
            assertThatCode(() -> SsrfGuard.validateEndpointStrict("https://api.openai.com/v1"))
                .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("localhost is blocked in strict mode")
        void localhostBlockedStrict() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://localhost:11434"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("strict mode");
        }

        @Test
        @DisplayName("127.0.0.1 is blocked in strict mode")
        void loopbackIpBlockedStrict() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://127.0.0.1:6379"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("strict mode");
        }

        @Test
        @DisplayName("10.x.x.x private IP is blocked in strict mode")
        void privateIpBlockedStrict() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://10.0.0.5:8080"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("strict mode");
        }

        @Test
        @DisplayName("172.16.x.x private IP is blocked in strict mode")
        void privateIp172BlockedStrict() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://172.16.0.1/"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("strict mode");
        }

        @Test
        @DisplayName("192.168.x.x is blocked in strict mode")
        void privateIp192BlockedStrict() { 
            assertThatThrownBy(() -> SsrfGuard.validateEndpointStrict("http://192.168.1.100:9200"))
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("strict mode");
        }
    }
}
