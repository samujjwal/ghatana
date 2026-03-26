package com.ghatana.phr.security;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.security.PolicyEnforcementPoint;
import com.ghatana.kernel.security.PrivacyManager;
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

    public PHRSecurityConfig() {
        this.userRepository = new UserRepository();
        this.consentRepository = new ConsentRepository();
        this.tenantConfigRepository = new TenantConfigRepository();
    }

    public PHRSecurityConfig(UserRepository userRepository, 
                            ConsentRepository consentRepository,
                            TenantConfigRepository tenantConfigRepository) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.tenantConfigRepository = tenantConfigRepository;
    }

    public KernelSecurityManager kernelSecurityManager() {
        return new PHRSecurityManagerImpl(userRepository);
    }

    public PrivacyManager privacyManager() {
        return new PHRPrivacyManagerImpl(consentRepository, tenantConfigRepository);
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
