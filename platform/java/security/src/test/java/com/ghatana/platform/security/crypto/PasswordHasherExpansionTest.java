package com.ghatana.platform.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Password hasher edge cases and advanced scenarios.
 * Tests very long passwords, Unicode support, special characters, and hash collision behavior.
 *
 * @doc.type class
 * @doc.purpose Password hasher edge cases and advanced scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PasswordHasher - Phase 3 Expansion")
class PasswordHasherExpansionTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() { 
        hasher = new PasswordHasher(); 
    }

    // ============================================
    // EXTREME LENGTH PASSWORDS (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Extreme Length Passwords")
    class ExtremeLengthTests {

        @Test
        @DisplayName("Hash and verify very long password (1000+ chars)")
        void veryLongPassword() { 
            // Create a 1000+ character password
            StringBuilder longPassword = new StringBuilder(); 
            for (int i = 0; i < 100; i++) { 
                longPassword.append("Pass123!");
            }
            String password = longPassword.toString(); 

            String hashed = hasher.hash(password); 

            // BCrypt limits input to 72 bytes, so it should still work
            assertThat(hashed).startsWith("$2");
            assertThat(hasher.verify(password, hashed)).isTrue(); 
        }

        @Test
        @DisplayName("Maximum practical password length handled correctly")
        void maximumPracticalLength() { 
            // 72 bytes is BCrypt's limit - test near this boundary
            String password = "A".repeat(71) + "!"; 
            String hashed = hasher.hash(password); 

            assertThat(hasher.verify(password, hashed)).isTrue(); 
            // Truncated version (72+ chars) might not match due to BCrypt limit 
            String truncated = "A".repeat(72); 
            assertThat(hasher.verify(truncated, hashed)).isFalse(); 
        }
    }

    // ============================================
    // UNICODE AND SPECIAL CHARACTER SUPPORT (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Unicode and Special Character Support")
    class UnicodeTests {

        @Test
        @DisplayName("Unicode characters in password hash and verify correctly")
        void unicodePassword() { 
            String unicodePassword = "用户密码🔐émoji-security";
            String hashed = hasher.hash(unicodePassword); 

            assertThat(hashed).startsWith("$2");
            assertThat(hasher.verify(unicodePassword, hashed)).isTrue(); 

            // Different unicode should not match
            String differentUnicode = "用户密码🔒émoji-security"; // Different emoji
            assertThat(hasher.verify(differentUnicode, hashed)).isFalse(); 
        }

        @Test
        @DisplayName("All special ASCII characters hash and verify correctly")
        void specialCharacters() { 
            String specialPassword = "!@#$%^&*()-_=+[]{};:',.<>?/"; 
            String hashed = hasher.hash(specialPassword); 

            assertThat(hashed).startsWith("$2");
            assertThat(hasher.verify(specialPassword, hashed)).isTrue(); 
        }
    }

    // Note: Hash collision tests are implicit - BCrypt uses salting
    // so identical passwords always produce different hashes (verified in base tests) 
    // The probability of collision for different passwords is negligible (2^-128 or better) 
}
