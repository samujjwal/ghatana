package com.ghatana.phr.security;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.security.PolicyEnforcementPoint;
import com.ghatana.kernel.security.PrivacyManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import com.ghatana.phr.repository.UserRepository;

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

    public PHRSecurityConfig() {
        this(new UserRepository(), new ConsentRepository(), new TenantConfigRepository(), null);
    }

    public PHRSecurityConfig(UserRepository userRepository, 
                            ConsentRepository consentRepository,
                            TenantConfigRepository tenantConfigRepository) {
        this(userRepository, consentRepository, tenantConfigRepository, null);
    }

    public PHRSecurityConfig(UserRepository userRepository,
                             ConsentRepository consentRepository,
                             TenantConfigRepository tenantConfigRepository,
                             ConsentService consentService) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.consentService = consentService;
    }

    public KernelSecurityManager kernelSecurityManager() {
        return new PHRSecurityManagerImpl(userRepository);
    }

    public PrivacyManager privacyManager() {
        return new PHRPrivacyManagerImpl(consentRepository, tenantConfigRepository, consentService);
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
}
