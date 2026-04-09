package com.ghatana.phr.security;

import com.ghatana.kernel.security.*;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Component for PHRSecurityManagerImpl
 *
 * @doc.type class
 * @doc.purpose Component for PHRSecurityManagerImpl
 * @doc.layer product
 * @doc.pattern Manager
 */
public class PHRSecurityManagerImpl implements KernelSecurityManager {
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public PHRSecurityManagerImpl(UserRepository userRepository) {
        this(userRepository, new PasswordHasher());
    }

    PHRSecurityManagerImpl(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public SecurityContext createSecurityContext(String tenantId, String userId) {
        PHRUser user = userRepository.findById(userId);

        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        return TenantSecurityContext.builder()
            .tenantId(tenantId)
            .userId(userId)
            .sessionId(UUID.randomUUID().toString())
            .roles(user.getRoles())
            .permissions(user.getPermissions())
            .attribute("healthcare_provider_id", user.getProviderId())
            .attribute("patient_access_level", user.getAccessLevel())
            .authenticated(true)
            .build();
    }

    @Override
    public boolean authorizeAction(Action action, SecurityContext context) {
        String resource = action.getResource();
        String operation = action.getOperation();

        if (resource.startsWith("patient-records")) {
            return checkHIPAACompliance(context, operation);
        }

        String requiredPermission = operation + ":" + resource;
        return context.hasPermission(requiredPermission);
    }

    @Override
    public void enforceSecurityPolicy(SecurityContext context, Policy policy) {
        if (!context.isAuthenticated()) {
            throw new SecurityPolicyViolationException("User not authenticated");
        }

        if (policy.getType() == Policy.PolicyType.DATA_ACCESS) {
            enforceDataAccessPolicy(context, policy);
        }
    }

    @Override
    public ValidationResult validateCredentials(Credentials credentials) {
        if (credentials.getUsername() == null || credentials.getPassword() == null) {
            return ValidationResult.failure("Username and password required");
        }

        PHRUser user = userRepository.findByUsername(credentials.getUsername()).orElse(null);

        if (user == null) {
            return ValidationResult.failure("Invalid credentials");
        }

        if (!user.isActive()) {
            return ValidationResult.failure("User account is inactive");
        }

        Instant now = Instant.now();
        if (user.isLockedAt(now)) {
            return ValidationResult.failure("Account locked");
        }

        String passwordHash = user.getPasswordHash();
        if (passwordHash == null || passwordHash.isBlank()) {
            return ValidationResult.failure("Invalid credentials");
        }

        if (!passwordHasher.verify(credentials.getPassword(), passwordHash)) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockoutUntil(now.plus(15, ChronoUnit.MINUTES));
                userRepository.save(user);
                return ValidationResult.failure("Account locked");
            }
            userRepository.save(user);
            return ValidationResult.failure("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        return ValidationResult.success();
    }

    @Override
    public SecurityContext getCurrentContext() {
        return SecurityContextHolder.getContext();
    }

    private boolean checkHIPAACompliance(SecurityContext context, String operation) {
        if (operation.equals("export")) {
            return context.hasRole("HEALTHCARE_PROVIDER") &&
                   context.hasPermission("export:phi");
        }

        if (operation.equals("read")) {
            return context.hasRole("HEALTHCARE_PROVIDER") ||
                   context.hasRole("PATIENT") ||
                   context.hasRole("ADMINISTRATOR");
        }

        return true;
    }

    private void enforceDataAccessPolicy(SecurityContext context, Policy policy) {
        for (Policy.PolicyRule rule : policy.getRules()) {
            if (!rule.evaluate(context)) {
                throw new SecurityPolicyViolationException(
                    "Policy rule violation: " + rule.getDescription()
                );
            }
        }
    }
}
