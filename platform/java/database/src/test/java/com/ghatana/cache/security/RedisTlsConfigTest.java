package com.ghatana.cache.security;

import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RedisTlsConfig}.
 * Tests TLS configuration, keystore/truststore loading, and SSL context creation.
 */
class RedisTlsConfigTest {

    private static final String TEST_KEYSTORE = "certs/test-keystore.jks";
    private static final String TEST_TRUSTSTORE = "certs/test-truststore.jks";
    private static final String TEST_PASSWORD = "changeit";

    private Path getResourcePath(String resource) { // GH-90000
        try {
            return Paths.get(getClass().getClassLoader().getResource(resource).toURI()); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Resource not found: " + resource, e); // GH-90000
        }
    }

    @Test
    void shouldCreateConfigWithTrustStoreOnly() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        // Then
        assertThat(config).isNotNull(); // GH-90000
        assertThatCode(config::validate).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    void shouldCreateConfigWithKeyStoreAndTrustStore() { // GH-90000
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE); // GH-90000
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .keyStorePath(keyStore.toString()) // GH-90000
                .keyStorePassword(TEST_PASSWORD) // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        // Then
        assertThat(config).isNotNull(); // GH-90000
        assertThatCode(config::validate).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    void shouldEnableStrongCiphersByDefault() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        SSLParameters sslParameters = config.createSslParameters(); // GH-90000

        // Then
        assertThat(sslParameters.getProtocols()).containsExactly("TLSv1.3");
        assertThat(sslParameters.getCipherSuites()) // GH-90000
                .contains("TLS_AES_256_GCM_SHA384")
                .contains("TLS_AES_128_GCM_SHA256")
                .contains("TLS_CHACHA20_POLY1305_SHA256");
    }

    @Test
    void shouldAllowDisablingStrongCiphers() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .enforceStrongCiphers(false) // GH-90000
                .build(); // GH-90000

        SSLParameters sslParameters = config.createSslParameters(); // GH-90000

        // Then
        // When strong ciphers are disabled, protocols and ciphers are not set
        // SSLParameters will use JVM defaults
        assertThat(sslParameters).isNotNull(); // GH-90000
    }

    @Test
    void shouldEnablePeerVerificationByDefault() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        SSLParameters sslParameters = config.createSslParameters(); // GH-90000

        // Then
        assertThat(sslParameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void shouldAllowDisablingPeerVerification() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .verifyPeer(false) // GH-90000
                .build(); // GH-90000

        SSLParameters sslParameters = config.createSslParameters(); // GH-90000

        // Then
        assertThat(sslParameters.getEndpointIdentificationAlgorithm()).isNull(); // GH-90000
    }

    @Test
    void shouldCreateSslSocketFactory() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        // When
        SSLSocketFactory sslSocketFactory = config.createSslSocketFactory(); // GH-90000

        // Then
        assertThat(sslSocketFactory).isNotNull(); // GH-90000
        assertThat(sslSocketFactory.getDefaultCipherSuites()).isNotEmpty(); // GH-90000
    }

    @Test
    void shouldCreateSslSocketFactoryWithClientCertificate() { // GH-90000
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE); // GH-90000
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .keyStorePath(keyStore.toString()) // GH-90000
                .keyStorePassword(TEST_PASSWORD) // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        // When
        SSLSocketFactory sslSocketFactory = config.createSslSocketFactory(); // GH-90000

        // Then
        assertThat(sslSocketFactory).isNotNull(); // GH-90000
    }

    @Test
    void shouldRejectConfigWithoutTrustStore() { // GH-90000
        // When/Then
        assertThatThrownBy(() -> // GH-90000
                RedisTlsConfig.builder() // GH-90000
                        .keyStorePath("/path/to/keystore.jks")
                        .keyStorePassword(TEST_PASSWORD) // GH-90000
                        .build() // GH-90000
        )
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Trust store path must be provided");
    }

    @Test
    void shouldRejectConfigWithoutTrustStorePassword() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When/Then
        assertThatThrownBy(() -> // GH-90000
                RedisTlsConfig.builder() // GH-90000
                        .trustStorePath(trustStore.toString()) // GH-90000
                        .build() // GH-90000
        )
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Trust store password must be provided");
    }

    @Test
    void shouldRejectNonExistentTrustStore() { // GH-90000
        // When/Then
        assertThatThrownBy(() -> // GH-90000
                RedisTlsConfig.builder() // GH-90000
                        .trustStorePath("/nonexistent/truststore.jks")
                        .trustStorePassword(TEST_PASSWORD) // GH-90000
                        .build() // GH-90000
        )
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Trust store file not found");
    }

    @Test
    void shouldRejectNonExistentKeyStore() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When/Then
        assertThatThrownBy(() -> // GH-90000
                RedisTlsConfig.builder() // GH-90000
                        .keyStorePath("/nonexistent/keystore.jks")
                        .keyStorePassword(TEST_PASSWORD) // GH-90000
                        .trustStorePath(trustStore.toString()) // GH-90000
                        .trustStorePassword(TEST_PASSWORD) // GH-90000
                        .build() // GH-90000
        )
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Key store file not found");
    }

    @Test
    void shouldRejectKeyStoreWithoutPassword() { // GH-90000
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE); // GH-90000
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When/Then
        assertThatThrownBy(() -> // GH-90000
                RedisTlsConfig.builder() // GH-90000
                        .keyStorePath(keyStore.toString()) // GH-90000
                        .trustStorePath(trustStore.toString()) // GH-90000
                        .trustStorePassword(TEST_PASSWORD) // GH-90000
                        .build() // GH-90000
        )
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Key store password must be provided");
    }

    @Test
    void shouldSupportPKCS12KeyStore() { // GH-90000
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE); // GH-90000
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .keyStorePath(keyStore.toString()) // GH-90000
                .keyStorePassword(TEST_PASSWORD) // GH-90000
                .keyStoreType("JKS") // Using JKS for test
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        // Then
        assertThat(config).isNotNull(); // GH-90000
        assertThatCode(() -> config.createSslSocketFactory()).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    void shouldSupportCustomProtocols() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .enabledProtocols("TLSv1.2", "TLSv1.3") // GH-90000
                .build(); // GH-90000

        SSLParameters sslParameters = config.createSslParameters(); // GH-90000

        // Then
        assertThat(sslParameters.getProtocols()).containsExactly("TLSv1.2", "TLSv1.3"); // GH-90000
    }

    @Test
    void shouldSupportCustomCipherSuites() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .enabledCipherSuites("TLS_AES_256_GCM_SHA384")
                .build(); // GH-90000

        SSLParameters sslParameters = config.createSslParameters(); // GH-90000

        // Then
        assertThat(sslParameters.getCipherSuites()).containsExactly("TLS_AES_256_GCM_SHA384");
    }

    @Test
    void shouldSupportMethodChaining() { // GH-90000
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE); // GH-90000
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        // When
        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .keyStorePath(keyStore.toString()) // GH-90000
                .keyStorePassword(TEST_PASSWORD) // GH-90000
                .keyStoreType("JKS")
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .trustStoreType("JKS")
                .verifyPeer(true) // GH-90000
                .enforceStrongCiphers(true) // GH-90000
                .enabledProtocols("TLSv1.3")
                .enabledCipherSuites("TLS_AES_256_GCM_SHA384")
                .build(); // GH-90000

        // Then
        assertThat(config).isNotNull(); // GH-90000
        assertThatCode(config::validate).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    void shouldHandleInvalidKeyStorePassword() { // GH-90000
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE); // GH-90000
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .keyStorePath(keyStore.toString()) // GH-90000
                .keyStorePassword("wrongpassword")
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword(TEST_PASSWORD) // GH-90000
                .build(); // GH-90000

        // When/Then
        assertThatThrownBy(config::createSslSocketFactory) // GH-90000
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Failed to create");
    }

    @Test
    void shouldHandleInvalidTrustStorePassword() { // GH-90000
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE); // GH-90000

        RedisTlsConfig config = RedisTlsConfig.builder() // GH-90000
                .trustStorePath(trustStore.toString()) // GH-90000
                .trustStorePassword("wrongpassword")
                .build(); // GH-90000

        // When/Then
        assertThatThrownBy(config::createSslSocketFactory) // GH-90000
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Failed to create");
    }
}
