package com.ghatana.platform.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for PasswordHasher BCrypt hashing and verification
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PasswordHasher — BCrypt hashing and verification")
class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() { // GH-90000
        hasher = new PasswordHasher(); // GH-90000
    }

    // ── hash ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hash returns non-null non-blank string")
    void hashReturnsNonBlank() { // GH-90000
        String hashed = hasher.hash("SecureP@ss1");
        assertThat(hashed).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("hash produces BCrypt-formatted string starting with $2a$")
    void hashProducesBcryptFormat() { // GH-90000
        String hashed = hasher.hash("password123");
        assertThat(hashed).startsWith("$2");
    }

    @Test
    @DisplayName("hash produces different values for the same password (salted)")
    void hashProducesDifferentValuesForSamePassword() { // GH-90000
        String hash1 = hasher.hash("same-password");
        String hash2 = hasher.hash("same-password");
        assertThat(hash1).isNotEqualTo(hash2); // GH-90000
    }

    @Test
    @DisplayName("hash throws IllegalArgumentException for null password")
    void hashThrowsForNullPassword() { // GH-90000
        assertThatThrownBy(() -> hasher.hash(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("hash throws IllegalArgumentException for blank password")
    void hashThrowsForBlankPassword() { // GH-90000
        assertThatThrownBy(() -> hasher.hash("   "))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("hash throws IllegalArgumentException for empty password")
    void hashThrowsForEmptyPassword() { // GH-90000
        assertThatThrownBy(() -> hasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify returns true when password matches hash")
    void verifyReturnsTrueForMatchingPassword() { // GH-90000
        String password = "MyStr0ng!Passw0rd";
        String hashed = hasher.hash(password); // GH-90000
        assertThat(hasher.verify(password, hashed)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for wrong password")
    void verifyReturnsFalseForWrongPassword() { // GH-90000
        String hashed = hasher.hash("correct-password");
        assertThat(hasher.verify("wrong-password", hashed)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify throws IllegalArgumentException for null password")
    void verifyThrowsForNullPassword() { // GH-90000
        String hashed = hasher.hash("secure");
        assertThatThrownBy(() -> hasher.verify(null, hashed)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("verify throws IllegalArgumentException for null hash")
    void verifyThrowsForNullHash() { // GH-90000
        assertThatThrownBy(() -> hasher.verify("password", null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for malformed hash")
    void verifyReturnsFalseForMalformedHash() { // GH-90000
        assertThat(hasher.verify("password", "not-a-bcrypt-hash")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify is case-sensitive for passwords")
    void verifyIsCaseSensitive() { // GH-90000
        String hashed = hasher.hash("CaseSensitive");
        assertThat(hasher.verify("casesensitive", hashed)).isFalse(); // GH-90000
    }
}
