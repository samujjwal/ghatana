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
    void setUp() { 
        hasher = new PasswordHasher(); 
    }

    // ── hash ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hash returns non-null non-blank string")
    void hashReturnsNonBlank() { 
        String hashed = hasher.hash("SecureP@ss1");
        assertThat(hashed).isNotBlank(); 
    }

    @Test
    @DisplayName("hash produces BCrypt-formatted string starting with $2a$")
    void hashProducesBcryptFormat() { 
        String hashed = hasher.hash("password123");
        assertThat(hashed).startsWith("$2");
    }

    @Test
    @DisplayName("hash produces different values for the same password (salted)")
    void hashProducesDifferentValuesForSamePassword() { 
        String hash1 = hasher.hash("same-password");
        String hash2 = hasher.hash("same-password");
        assertThat(hash1).isNotEqualTo(hash2); 
    }

    @Test
    @DisplayName("hash throws IllegalArgumentException for null password")
    void hashThrowsForNullPassword() { 
        assertThatThrownBy(() -> hasher.hash(null)) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("hash throws IllegalArgumentException for blank password")
    void hashThrowsForBlankPassword() { 
        assertThatThrownBy(() -> hasher.hash("   "))
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("hash throws IllegalArgumentException for empty password")
    void hashThrowsForEmptyPassword() { 
        assertThatThrownBy(() -> hasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class); 
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify returns true when password matches hash")
    void verifyReturnsTrueForMatchingPassword() { 
        String password = "MyStr0ng!Passw0rd";
        String hashed = hasher.hash(password); 
        assertThat(hasher.verify(password, hashed)).isTrue(); 
    }

    @Test
    @DisplayName("verify returns false for wrong password")
    void verifyReturnsFalseForWrongPassword() { 
        String hashed = hasher.hash("correct-password");
        assertThat(hasher.verify("wrong-password", hashed)).isFalse(); 
    }

    @Test
    @DisplayName("verify throws IllegalArgumentException for null password")
    void verifyThrowsForNullPassword() { 
        String hashed = hasher.hash("secure");
        assertThatThrownBy(() -> hasher.verify(null, hashed)) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("verify throws IllegalArgumentException for null hash")
    void verifyThrowsForNullHash() { 
        assertThatThrownBy(() -> hasher.verify("password", null)) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("verify returns false for malformed hash")
    void verifyReturnsFalseForMalformedHash() { 
        assertThat(hasher.verify("password", "not-a-bcrypt-hash")).isFalse(); 
    }

    @Test
    @DisplayName("verify is case-sensitive for passwords")
    void verifyIsCaseSensitive() { 
        String hashed = hasher.hash("CaseSensitive");
        assertThat(hasher.verify("casesensitive", hashed)).isFalse(); 
    }
}
