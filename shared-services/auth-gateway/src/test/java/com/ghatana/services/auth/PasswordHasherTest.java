package com.ghatana.services.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for PasswordHasher hashing and verification logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PasswordHasher — SHA-256+salt hashing and verification")
class PasswordHasherTest {

    @Test
    @DisplayName("hash produces a non-empty string")
    void hashProducesNonEmptyString() { 
        String hash = PasswordHasher.hash("myPassword123");

        assertThat(hash).isNotBlank(); 
    }

    @Test
    @DisplayName("hash result is not plaintext")
    void hashIsNotPlaintext() { 
        String hash = PasswordHasher.hash("myPassword123");

        assertThat(hash).doesNotContain("myPassword123");
    }

    @Test
    @DisplayName("hash result starts with $sha256$ prefix")
    void hashHasCorrectPrefix() { 
        String hash = PasswordHasher.hash("myPassword123");

        assertThat(hash).startsWith("$sha256$");
    }

    @Test
    @DisplayName("same password produces different hashes on each call (due to salt)")
    void samePasswordProducesDifferentHashes() { 
        String hash1 = PasswordHasher.hash("myPassword123");
        String hash2 = PasswordHasher.hash("myPassword123");

        assertThat(hash1).isNotEqualTo(hash2); 
    }

    @Test
    @DisplayName("verify returns true when password matches hash")
    void verifyReturnsTrueForMatchingPassword() { 
        String password = "correct-horse-battery-staple";
        String hash = PasswordHasher.hash(password); 

        assertThat(PasswordHasher.verify(password, hash)).isTrue(); 
    }

    @Test
    @DisplayName("verify returns false for wrong password")
    void verifyReturnsFalseForWrongPassword() { 
        String hash = PasswordHasher.hash("originalPassword");

        assertThat(PasswordHasher.verify("wrongPassword", hash)).isFalse(); 
    }

    @Test
    @DisplayName("verify detects tampered hash")
    void verifyDetectsTamperedHash() { 
        String hash = PasswordHasher.hash("myPassword");
        String tampered = hash + "X"; // Append invalid char

        assertThat(PasswordHasher.verify("myPassword", tampered)).isFalse(); 
    }

    @Test
    @DisplayName("hash throws for null password")
    void hashThrowsForNullPassword() { 
        assertThatThrownBy(() -> PasswordHasher.hash(null)) 
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("hash throws for empty password")
    void hashThrowsForEmptyPassword() { 
        assertThatThrownBy(() -> PasswordHasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class); 
    }

    @Test
    @DisplayName("verify returns false for null plaintext")
    void verifyReturnsFalseForNullPlaintext() { 
        String hash = PasswordHasher.hash("somePassword");

        assertThat(PasswordHasher.verify(null, hash)).isFalse(); 
    }

    @Test
    @DisplayName("verify returns false for null stored hash")
    void verifyReturnsFalseForNullHash() { 
        assertThat(PasswordHasher.verify("somePassword", null)).isFalse(); 
    }

    @Test
    @DisplayName("verify returns false for non-sha256 format hash")
    void verifyReturnsFalseForUnknownHashFormat() { 
        assertThat(PasswordHasher.verify("somePassword", "unknownformatXYZ")).isFalse(); 
    }
}
