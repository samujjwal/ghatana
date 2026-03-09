package com.ghatana.security.config;

import com.ghatana.platform.security.auth.AuthenticationProvider;
import com.ghatana.platform.security.auth.CompositeAuthenticationProvider;
import com.ghatana.platform.security.auth.impl.JwtAuthenticationProvider;
import com.ghatana.platform.security.jwt.JwtTokenProvider;
import io.activej.config.Config;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// Removed unused imports that were causing compilation errors

/**
 * Main configuration class for security components.
 * 
 * <p>This class provides dependency injection bindings for all security-related components,
 * including authentication providers, JWT utilities, and configuration properties.</p>
 * 
 * <p>Example usage in an application:</p>
 * <pre>{@code
 * public class AppModule extends AbstractModule {
 *     @Override
 *     protected void configure() {
 *         install(new SecurityModule());
 *         // ... other bindings
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Security configuration DI module
 * @doc.layer core
 * @doc.pattern Configuration, Module
 */
public class SecurityConfigurationModule extends AbstractModule {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfigurationModule.class);
    
    private final Config config;
    
    /**
     * Creates a new SecurityModule with the application's root config.
     * 
     * @param config The root configuration object
     */
    public SecurityConfigurationModule(Config config) {
        this.config = config.getChild("security");
    }
    
    @Override
    protected void configure() {
        // Bind configuration
        bind(Config.class).toInstance(config);

        // Bind properties
        bind(SecurityProperties.class);
        bind(JwtProperties.class);
        bind(AuthProperties.class);
        bind(CorsProperties.class);
        bind(TlsProperties.class);
        bind(EncryptionProperties.class);
        bind(KeyManagementProperties.class);
        bind(RateLimitProperties.class);
        
        // Bind security components
        bind(JwtTokenProvider.class);
        bind(CompositeAuthenticationProvider.class);
        
        // Make AuthenticationProvider available through the interface
        bind(AuthenticationProvider.class).to(CompositeAuthenticationProvider.class);
    }
    
    @Provides
    SecurityProperties provideSecurityProperties(@SecurityConfig Config config) {
        return new SecurityProperties(config);
    }
    
    @Provides
    JwtProperties provideJwtProperties(SecurityProperties securityProperties) {
        return securityProperties.getJwt();
    }
    
    @Provides
    AuthProperties provideAuthProperties(SecurityProperties securityProperties) {
        return securityProperties.getAuth();
    }
    
    @Provides
    CorsProperties provideCorsProperties(SecurityProperties securityProperties) {
        return securityProperties.getCors();
    }
    
    @Provides
    TlsProperties provideTlsProperties(SecurityProperties securityProperties) {
        return securityProperties.getTls();
    }
    
    @Provides
    EncryptionProperties provideEncryptionProperties(SecurityProperties securityProperties) {
        return securityProperties.getEncryption();
    }
    
    @Provides
    KeyManagementProperties provideKeyManagementProperties(SecurityProperties securityProperties) {
        return securityProperties.getKeyManagement();
    }
    
    @Provides
    RateLimitProperties provideRateLimitProperties(@SecurityConfig Config config) {
        return new RateLimitProperties(config.getChild("rate-limit"));
    }
    
    @Provides
    JwtTokenProvider provideJwtTokenProvider(JwtProperties jwtProps) {
        return new JwtTokenProvider(
            jwtProps.getSecret(),
            jwtProps.getExpiration().toMillis()
        );
    }
    
    @Provides
    JwtAuthenticationProvider provideJwtAuthenticationProvider(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationProvider(
                (com.ghatana.platform.security.port.JwtTokenProvider) jwtTokenProvider);
    }

    @Provides
    CompositeAuthenticationProvider provideCompositeAuthenticationProvider(
            JwtAuthenticationProvider jwtAuthProvider) {
        return new CompositeAuthenticationProvider(jwtAuthProvider);
    }
}
