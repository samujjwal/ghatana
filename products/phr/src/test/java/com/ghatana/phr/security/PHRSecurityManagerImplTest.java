package com.ghatana.phr.security;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies credential validation edge cases for PHRSecurityManagerImpl
 * @doc.layer product
 * @doc.pattern Test
 */
class PHRSecurityManagerImplTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    private UserRepository userRepository;
    private PHRSecurityManagerImpl securityManager;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        securityManager = new PHRSecurityManagerImpl(userRepository, passwordHasher);
    }

    @Test
    void rejectsMissingUsername() {
        KernelSecurityManager.ValidationResult result = securityManager.validateCredentials(
            new KernelSecurityManager.Credentials(null, "Password123!", null)
        );

        assertFalse(result.isValid());
        assertEquals("Username and password required", result.getReason());
    }

    @Test
    void rejectsInactiveUser() {
        PHRUser user = new PHRUser("user-1", "doctor", "doctor@example.com");
        user.setPasswordHash(passwordHasher.hash("Password123!"));
        user.setActive(false);
        userRepository.save(user);

        KernelSecurityManager.ValidationResult result = securityManager.validateCredentials(
            new KernelSecurityManager.Credentials("doctor", "Password123!", null)
        );

        assertFalse(result.isValid());
        assertEquals("User account is inactive", result.getReason());
    }

    @Test
    void rejectsUserWithoutPasswordHash() {
        PHRUser user = new PHRUser("user-1", "doctor", "doctor@example.com");
        userRepository.save(user);

        KernelSecurityManager.ValidationResult result = securityManager.validateCredentials(
            new KernelSecurityManager.Credentials("doctor", "Password123!", null)
        );

        assertFalse(result.isValid());
        assertEquals("Invalid credentials", result.getReason());
    }

    @Test
    void resetsLockoutStateAfterSuccessfulLogin() {
        PHRUser user = new PHRUser("user-1", "doctor", "doctor@example.com");
        user.setPasswordHash(passwordHasher.hash("Password123!"));
        user.setFailedLoginAttempts(4);
        user.setLockoutUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        userRepository.save(user);

        KernelSecurityManager.ValidationResult result = securityManager.validateCredentials(
            new KernelSecurityManager.Credentials("doctor", "Password123!", null)
        );

        PHRUser stored = userRepository.findById("user-1");
        assertTrue(result.isValid());
        assertEquals(0, stored.getFailedLoginAttempts());
        assertNull(stored.getLockoutUntil());
    }
}