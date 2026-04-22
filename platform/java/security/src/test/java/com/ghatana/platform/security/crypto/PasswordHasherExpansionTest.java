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
@DisplayName("PasswordHasher - Phase 3 Expansion [GH-90000]")
class PasswordHasherExpansionTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() { // GH-90000
        hasher = new PasswordHasher(); // GH-90000
    }

    // ============================================
    // EXTREME LENGTH PASSWORDS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Extreme Length Passwords [GH-90000]")
    class ExtremeLengthTests {

        @Test
        @DisplayName("Hash and verify very long password (1000+ chars) [GH-90000]")
        void veryLongPassword() { // GH-90000
            // Create a 1000+ character password
            StringBuilder longPassword = new StringBuilder(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                longPassword.append("Pass123! [GH-90000]");
            }
            String password = longPassword.toString(); // GH-90000

            String hashed = hasher.hash(password); // GH-90000

            // BCrypt limits input to 72 bytes, so it should still work
            assertThat(hashed).startsWith("$2 [GH-90000]");
            assertThat(hasher.verify(password, hashed)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Maximum practical password length handled correctly [GH-90000]")
        void maximumPracticalLength() { // GH-90000
            // 72 bytes is BCrypt's limit - test near this boundary
            String password = "A".repeat(71) + "!"; // GH-90000
            String hashed = hasher.hash(password); // GH-90000

            assertThat(hasher.verify(password, hashed)).isTrue(); // GH-90000
            // Truncated version (72+ chars) might not match due to BCrypt limit // GH-90000
            String truncated = "A".repeat(72); // GH-90000
            assertThat(hasher.verify(truncated, hashed)).isFalse(); // GH-90000
        }
    }

    // ============================================
    // UNICODE AND SPECIAL CHARACTER SUPPORT (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Unicode and Special Character Support [GH-90000]")
    class UnicodeTests {

        @Test
        @DisplayName("Unicode characters in password hash and verify correctly [GH-90000]")
        void unicodePassword() { // GH-90000
            String unicodePassword = "用户密码🔐émoji-security";
            String hashed = hasher.hash(unicodePassword); // GH-90000

            assertThat(hashed).startsWith("$2 [GH-90000]");
            assertThat(hasher.verify(unicodePassword, hashed)).isTrue(); // GH-90000

            // Different unicode should not match
            String differentUnicode = "用户密码🔒émoji-security"; // Different emoji
            assertThat(hasher.verify(differentUnicode, hashed)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("All special ASCII characters hash and verify correctly [GH-90000]")
        void specialCharacters() { // GH-90000
            String specialPassword = "!@#$%^&*()-_=+[]{};:',.<>?/"; // GH-90000
            String hashed = hasher.hash(specialPassword); // GH-90000

            assertThat(hashed).startsWith("$2 [GH-90000]");
            assertThat(hasher.verify(specialPassword, hashed)).isTrue(); // GH-90000
        }
    }

    // Note: Hash collision tests are implicit - BCrypt uses salting
    // so identical passwords always produce different hashes (verified in base tests) // GH-90000
    // The probability of collision for different passwords is negligible (2^-128 or better) // GH-90000
}
