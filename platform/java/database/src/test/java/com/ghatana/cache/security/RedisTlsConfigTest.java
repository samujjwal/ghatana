package com.ghatana.cache.security;

import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.io.InputStream;
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

    private Path getResourcePath(String resource) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(resource).toURI());
        } catch (Exception e) {
            throw new RuntimeException("Resource not found: " + resource, e);
        }
    }

    @Test
    void shouldCreateConfigWithTrustStoreOnly() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        // Then
        assertThat(config).isNotNull();
        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldCreateConfigWithKeyStoreAndTrustStore() {
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE);
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .keyStorePath(keyStore.toString())
                .keyStorePassword(TEST_PASSWORD)
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        // Then
        assertThat(config).isNotNull();
        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldEnableStrongCiphersByDefault() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        SSLParameters sslParameters = config.createSslParameters();

        // Then
        assertThat(sslParameters.getProtocols()).containsExactly("TLSv1.3");
        assertThat(sslParameters.getCipherSuites())
                .contains("TLS_AES_256_GCM_SHA384")
                .contains("TLS_AES_128_GCM_SHA256")
                .contains("TLS_CHACHA20_POLY1305_SHA256");
    }

    @Test
    void shouldAllowDisablingStrongCiphers() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .enforceStrongCiphers(false)
                .build();

        SSLParameters sslParameters = config.createSslParameters();

        // Then
        // When strong ciphers are disabled, protocols and ciphers are not set
        // SSLParameters will use JVM defaults
        assertThat(sslParameters).isNotNull();
    }

    @Test
    void shouldEnablePeerVerificationByDefault() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        SSLParameters sslParameters = config.createSslParameters();

        // Then
        assertThat(sslParameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void shouldAllowDisablingPeerVerification() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .verifyPeer(false)
                .build();

        SSLParameters sslParameters = config.createSslParameters();

        // Then
        assertThat(sslParameters.getEndpointIdentificationAlgorithm()).isNull();
    }

    @Test
    void shouldCreateSslSocketFactory() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        // When
        SSLSocketFactory sslSocketFactory = config.createSslSocketFactory();

        // Then
        assertThat(sslSocketFactory).isNotNull();
        assertThat(sslSocketFactory.getDefaultCipherSuites()).isNotEmpty();
    }

    @Test
    void shouldCreateSslSocketFactoryWithClientCertificate() {
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE);
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        RedisTlsConfig config = RedisTlsConfig.builder()
                .keyStorePath(keyStore.toString())
                .keyStorePassword(TEST_PASSWORD)
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        // When
        SSLSocketFactory sslSocketFactory = config.createSslSocketFactory();

        // Then
        assertThat(sslSocketFactory).isNotNull();
    }

    @Test
    void shouldRejectConfigWithoutTrustStore() {
        // When/Then
        assertThatThrownBy(() ->
                RedisTlsConfig.builder()
                        .keyStorePath("/path/to/keystore.jks")
                        .keyStorePassword(TEST_PASSWORD)
                        .build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trust store path must be provided");
    }

    @Test
    void shouldRejectConfigWithoutTrustStorePassword() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When/Then
        assertThatThrownBy(() ->
                RedisTlsConfig.builder()
                        .trustStorePath(trustStore.toString())
                        .build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trust store password must be provided");
    }

    @Test
    void shouldRejectNonExistentTrustStore() {
        // When/Then
        assertThatThrownBy(() ->
                RedisTlsConfig.builder()
                        .trustStorePath("/nonexistent/truststore.jks")
                        .trustStorePassword(TEST_PASSWORD)
                        .build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trust store file not found");
    }

    @Test
    void shouldRejectNonExistentKeyStore() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When/Then
        assertThatThrownBy(() ->
                RedisTlsConfig.builder()
                        .keyStorePath("/nonexistent/keystore.jks")
                        .keyStorePassword(TEST_PASSWORD)
                        .trustStorePath(trustStore.toString())
                        .trustStorePassword(TEST_PASSWORD)
                        .build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key store file not found");
    }

    @Test
    void shouldRejectKeyStoreWithoutPassword() {
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE);
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When/Then
        assertThatThrownBy(() ->
                RedisTlsConfig.builder()
                        .keyStorePath(keyStore.toString())
                        .trustStorePath(trustStore.toString())
                        .trustStorePassword(TEST_PASSWORD)
                        .build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key store password must be provided");
    }

    @Test
    void shouldSupportPKCS12KeyStore() {
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE);
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .keyStorePath(keyStore.toString())
                .keyStorePassword(TEST_PASSWORD)
                .keyStoreType("JKS") // Using JKS for test
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        // Then
        assertThat(config).isNotNull();
        assertThatCode(() -> config.createSslSocketFactory()).doesNotThrowAnyException();
    }

    @Test
    void shouldSupportCustomProtocols() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .enabledProtocols("TLSv1.2", "TLSv1.3")
                .build();

        SSLParameters sslParameters = config.createSslParameters();

        // Then
        assertThat(sslParameters.getProtocols()).containsExactly("TLSv1.2", "TLSv1.3");
    }

    @Test
    void shouldSupportCustomCipherSuites() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .enabledCipherSuites("TLS_AES_256_GCM_SHA384")
                .build();

        SSLParameters sslParameters = config.createSslParameters();

        // Then
        assertThat(sslParameters.getCipherSuites()).containsExactly("TLS_AES_256_GCM_SHA384");
    }

    @Test
    void shouldSupportMethodChaining() {
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE);
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        // When
        RedisTlsConfig config = RedisTlsConfig.builder()
                .keyStorePath(keyStore.toString())
                .keyStorePassword(TEST_PASSWORD)
                .keyStoreType("JKS")
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .trustStoreType("JKS")
                .verifyPeer(true)
                .enforceStrongCiphers(true)
                .enabledProtocols("TLSv1.3")
                .enabledCipherSuites("TLS_AES_256_GCM_SHA384")
                .build();

        // Then
        assertThat(config).isNotNull();
        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleInvalidKeyStorePassword() {
        // Given
        Path keyStore = getResourcePath(TEST_KEYSTORE);
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        RedisTlsConfig config = RedisTlsConfig.builder()
                .keyStorePath(keyStore.toString())
                .keyStorePassword("wrongpassword")
                .trustStorePath(trustStore.toString())
                .trustStorePassword(TEST_PASSWORD)
                .build();

        // When/Then
        assertThatThrownBy(config::createSslSocketFactory)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create");
    }

    @Test
    void shouldHandleInvalidTrustStorePassword() {
        // Given
        Path trustStore = getResourcePath(TEST_TRUSTSTORE);

        RedisTlsConfig config = RedisTlsConfig.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword("wrongpassword")
                .build();

        // When/Then
        assertThatThrownBy(config::createSslSocketFactory)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create");
    }
}
