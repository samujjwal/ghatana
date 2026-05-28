package com.ghatana.phr.security;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.security.PolicyEnforcementPoint;
import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import com.ghatana.phr.repository.UserRepository;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration and setup for PHRSecurity
 *
 * @doc.type class
 * @doc.purpose Configuration and setup for PHRSecurity
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class PHRSecurityConfig {
    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final ConsentService consentService;

    public PHRSecurityConfig(UserRepository userRepository,
                             ConsentRepository consentRepository,
                             TenantConfigRepository tenantConfigRepository,
                             ConsentService consentService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
        this.consentRepository = Objects.requireNonNull(consentRepository, "consentRepository cannot be null");
        this.tenantConfigRepository = Objects.requireNonNull(tenantConfigRepository,
            "tenantConfigRepository cannot be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService cannot be null");
    }

    public static PHRSecurityConfig persistent(DataSource dataSource, ConsentService consentService) {
        Objects.requireNonNull(dataSource, "dataSource cannot be null");
        return new PHRSecurityConfig(
            new UserRepository(dataSource),
            new ConsentRepository(dataSource),
            new TenantConfigRepository(dataSource),
            consentService
        );
    }

    public KernelSecurityManager kernelSecurityManager() {
        return new PHRSecurityManagerImpl(userRepository);
    }

    public PrivacyManager privacyManager() {
        Optional<SecretKey> configuredKey = loadConfiguredPiiCryptoKey();
        return configuredKey
            .map(secretKey -> new PHRPrivacyManagerImpl(consentRepository, tenantConfigRepository, consentService, secretKey))
            .orElseGet(() -> new PHRPrivacyManagerImpl(consentRepository, tenantConfigRepository, consentService));
    }

    public PolicyEnforcementPoint policyEnforcementPoint() {
        return new PolicyEnforcementPoint(
            kernelSecurityManager(),
            privacyManager()
        );
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public ConsentRepository getConsentRepository() {
        return consentRepository;
    }

    public TenantConfigRepository getTenantConfigRepository() {
        return tenantConfigRepository;
    }

    private static Optional<SecretKey> loadConfiguredPiiCryptoKey() {
        String configured = System.getProperty("phr.pii.crypto.key.base64");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("PHR_PII_CRYPTO_KEY_BASE64");
        }
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        byte[] keyBytes = Base64.getDecoder().decode(configured);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("PHR PII crypto key must decode to 32 bytes for AES-256-GCM");
        }
        return Optional.of(new SecretKeySpec(keyBytes, "AES"));
    }
}
