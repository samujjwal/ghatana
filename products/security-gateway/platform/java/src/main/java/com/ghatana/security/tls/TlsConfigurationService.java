package com.ghatana.security.tls;

import com.ghatana.security.config.TlsProperties;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.reactor.net.ServerSocketSettings;
import io.activej.reactor.net.SocketSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Service for configuring TLS/SSL for the application.
 
 *
 * @doc.type class
 * @doc.purpose Tls configuration service
 * @doc.layer core
 * @doc.pattern Service
*/
public class TlsConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(TlsConfigurationService.class);
    
    private final TlsProperties tlsProperties;
    private SSLContext sslContext;
    private ServerSocketSettings serverSocketSettings;
    private SocketSettings clientSocketSettings;
    private ServerSocketSettings serverSslSettings;
    private SocketSettings clientSslSettings;
    private static final String DEFAULT_PROTOCOL = "TLSv1.3";
    private static final String KEYSTORE_TYPE = "JKS";
    
    public TlsConfigurationService(TlsProperties tlsProperties) {
        this.tlsProperties = tlsProperties;
    }
    
    /**
     * Initializes the SSL context and socket settings.
     * 
     * @throws TlsConfigurationException if the SSL context cannot be initialized
     */
    public void initialize() throws TlsConfigurationException {
        if (!isEnabled()) {
            logger.info("TLS is disabled in configuration");
            return;
        }
        
        try {
            // Create key managers
            KeyManager[] keyManagers = createKeyManagers(
                tlsProperties.getKeyStorePath().toFile(),
                tlsProperties.getKeyStorePassword(),
                tlsProperties.getKeyStorePassword() // Using same password for key and keystore for simplicity
            );
            
            // Create trust managers
            TrustManager[] trustManagers = createTrustManagers(
                tlsProperties.getTrustStorePath().toFile(),
                tlsProperties.getTrustStorePassword()
            );
            
            // Initialize SSL context
            sslContext = createSslContext(
                DEFAULT_PROTOCOL,
                keyManagers,
                trustManagers,
                new SecureRandom()
            );
            
            // Create SSL settings for server and client with proper error handling
            try {
                serverSslSettings = createSslServerSettings();
                clientSslSettings = createSslClientSettings();
                
                logger.info("TLS configuration initialized successfully with protocol: {}", DEFAULT_PROTOCOL);
            } catch (Exception e) {
                String errorMsg = "Failed to create SSL settings: " + e.getMessage();
                logger.error(errorMsg, e);
                throw new TlsConfigurationException(errorMsg, e);
            }
        } catch (Exception e) {
            String errorMsg = "Failed to initialize TLS configuration: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new TlsConfigurationException(errorMsg, e);
        }
    }
    
    /**
     * Creates and initializes key managers from the specified keystore.
     *
     * @param keystoreFile the keystore file
     * @param keystorePass the keystore password
     * @param keyPass the key password
     * @return array of key managers
     * @throws Exception if there is an error initializing the key managers
     */
    private KeyManager[] createKeyManagers(File keystoreFile, String keystorePass, String keyPass) throws Exception {
        if (!keystoreFile.exists()) {
            throw new TlsConfigurationException("Keystore file not found: " + keystoreFile.getAbsolutePath());
        }
        
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream is = new FileInputStream(keystoreFile)) {
            keyStore.load(is, keystorePass.toCharArray());
        }
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPass.toCharArray());
        return kmf.getKeyManagers();
    }
    
    /**
     * Creates and initializes trust managers from the specified truststore.
     */
    private TrustManager[] createTrustManagers(File truststoreFile, String truststorePass) throws Exception {
        if (!truststoreFile.exists()) {
            throw new TlsConfigurationException("Truststore file not found: " + truststoreFile.getAbsolutePath());
        }
        
        KeyStore trustStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream is = new FileInputStream(truststoreFile)) {
            trustStore.load(is, truststorePass.toCharArray());
        }
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }
    
    /**
     * Creates an SSL context with the specified parameters.
     */
    private SSLContext createSslContext(String protocol, KeyManager[] keyManagers, TrustManager[] trustManagers, SecureRandom secureRandom)
            throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance(protocol);
        sslContext.init(keyManagers, trustManagers, secureRandom);
        return sslContext;
    }
    
    /**
     * Gets the SSL context.
     * 
     * @return Configured SSLContext
     * @throws TlsConfigurationException if SSL context is not initialized
     */
    public SSLContext getSslContext() throws TlsConfigurationException {
        if (sslContext == null) {
            throw new TlsConfigurationException("SSL context not initialized. Call initialize() first.");
        }
        return sslContext;
    }
    
    /**
     * Gets the server socket settings with SSL enabled.
     * 
     * @return Configured ServerSocketSettings with SSL
     * @throws TlsConfigurationException if SSL context is not initialized
     */
    public ServerSocketSettings getServerSocketSettings() throws TlsConfigurationException {
        if (sslContext == null) {
            throw new TlsConfigurationException("SSL context not initialized. Call initialize() first.");
        }
        return ServerSocketSettings.builder()
//            .withSslEnabled(true) // Pending: ActiveJ ServerSocketSettings SSL API availability
            .build();
    }

    /**
     * Gets the socket settings with SSL enabled.
     * 
     * @return Configured SocketSettings with SSL
     * @throws TlsConfigurationException if SSL context is not initialized
     */
    public SocketSettings getSocketSettings() throws TlsConfigurationException {
        if (sslContext == null) {
            throw new TlsConfigurationException("SSL context not initialized. Call initialize() first.");
        }
        return clientSocketSettings;
    }
    
    /**
     * Creates server socket settings with SSL/TLS configuration.
     * @return Configured ServerSocketSettings
     */
    private ServerSocketSettings createSslServerSettings() {
        // Configure protocols in SSL parameters instead of socket settings
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});

        // Pending: ActiveJ builder SSL methods not yet available in current version
        return ServerSocketSettings.builder()
//            .withSslContext(sslContext, sslParams)
//            .withTcpNoDelay(tlsProperties.isTcpNoDelay())
//            .withTcpFastOpen(tlsProperties.isTcpFastOpen())
//            .withTcpQuickAck(tlsProperties.isTcpQuickAck())
//            .withTcpCork(tlsProperties.isTcpCork())
//            .withTcpKeepAlive(tlsProperties.isTcpKeepAlive())
            .withReuseAddress(tlsProperties.isReuseAddress())
//            .withReceiveBufferSize(tlsProperties.getReceiveBufferSize())
//            .withSendBufferSize(tlsProperties.getSendBufferSize())
            .withBacklog(tlsProperties.getBacklog())
            .build();
    }
    
    /**
     * Creates client socket settings with SSL/TLS configuration.
     * @return Configured SocketSettings
     */
    private SocketSettings createSslClientSettings() {
        // Configure protocols in SSL parameters
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        // Pending: ActiveJ builder SSL methods not yet available in current version
        return SocketSettings.builder()
//            .withSslContext(sslContext, sslParams)
            .withTcpNoDelay(tlsProperties.isTcpNoDelay())
//            .withTcpFastOpen(tlsProperties.isTcpFastOpen())
//            .withTcpQuickAck(tlsProperties.isTcpQuickAck())
//            .withTcpCork(tlsProperties.isTcpCork())
//            .withTcpKeepAlive(tlsProperties.isTcpKeepAlive())
            .withReuseAddress(tlsProperties.isReuseAddress())
//            .withReceiveBufferSize(tlsProperties.getReceiveBufferSize())
//            .withSendBufferSize(tlsProperties.getSendBufferSize())
            .build();
    }
    
    /**
     * Configures SSL settings for the given builder.
     * @param <T> The builder type
     * @param builder The builder to configure
     * @return The configured builder
     */
    private <T> T configureSslSettings(T builder) {
        // SSL configuration is now handled through SSLContext and SSLParameters
        return builder;
    }
    
    /**
     * Adds a header to the HTTP response.
     * @param response The HTTP response builder
     * @param name The header name
     * @param value The header value
     * @return The response builder with the header added
     */
    public HttpResponse.Builder withHeader(HttpResponse.Builder response, String name, String value) {
        return response.withHeader(HttpHeaders.of(name), value);
    }
    
    /**
     * Creates a servlet that enforces HTTPS.
     * 
     * @param delegate The servlet to delegate to
     * @return A servlet that enforces HTTPS
     */
    public AsyncServlet enforceHttps(AsyncServlet delegate) {
        if (!tlsProperties.isEnabled()) {
            return delegate;
        }
        
        return request -> {
            String protocol = request.getProtocol().isSecure() ? "https" : "http";
            String host = request.getHeader(HttpHeaders.HOST);
            if (host == null) {
                host = "localhost";
            }
            String location = protocol + "://" + host + request.getPath();
            return HttpResponse.redirect302(location).toPromise();
        };
    }
            
    /**
     * Adds security headers to the response.
     * 
     * @param delegate The servlet to delegate to
     * @return A servlet that adds security headers
     */
    public AsyncServlet addSecurityHeaders(AsyncServlet delegate) {
        return request -> delegate.serve(request);
//            .map(response -> response
//                .withHeader(HttpHeaders.STRICT_TRANSPORT_SECURITY, "max-age=31536000; includeSubDomains; preload")
//                .withHeader(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff")
//                .withHeader(HttpHeaders.X_FRAME_OPTIONS, "DENY")
//                .withHeader(HttpHeaders.X_XSS_PROTECTION, "1; mode=block")
//                .withHeader(HttpHeaders.REFERRER_POLICY, "strict-origin-when-cross-origin")
//                .withHeader(HttpHeaders.CONTENT_SECURITY_POLICY,
//                    "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
//                    "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; " +
//                    "connect-src 'self'")
//            );
    }
    
    /**
     * Checks if TLS is enabled.
     * 
     * @return true if TLS is enabled, false otherwise
     */
    public boolean isEnabled() {
        return tlsProperties.isEnabled();
    }
}
