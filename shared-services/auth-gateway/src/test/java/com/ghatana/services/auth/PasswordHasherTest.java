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
    void hashProducesNonEmptyString() { // GH-90000
        String hash = PasswordHasher.hash("myPassword123");

        assertThat(hash).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("hash result is not plaintext")
    void hashIsNotPlaintext() { // GH-90000
        String hash = PasswordHasher.hash("myPassword123");

        assertThat(hash).doesNotContain("myPassword123");
    }

    @Test
    @DisplayName("hash result starts with $sha256$ prefix")
    void hashHasCorrectPrefix() { // GH-90000
        String hash = PasswordHasher.hash("myPassword123");

        assertThat(hash).startsWith("$sha256$");
    }

    @Test
    @DisplayName("same password produces different hashes on each call (due to salt)")
    void samePasswordProducesDifferentHashes() { // GH-90000
        String hash1 = PasswordHasher.hash("myPassword123");
        String hash2 = PasswordHasher.hash("myPassword123");

        assertThat(hash1).isNotEqualTo(hash2); // GH-90000
    }

    @Test
    @DisplayName("verify returns true when password matches hash")
    void verifyReturnsTrueForMatchingPassword() { // GH-90000
        String password = "correct-horse-battery-staple";
        String hash = PasswordHasher.hash(password); // GH-90000

        assertThat(PasswordHasher.verify(password, hash)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for wrong password")
    void verifyReturnsFalseForWrongPassword() { // GH-90000
        String hash = PasswordHasher.hash("originalPassword");

        assertThat(PasswordHasher.verify("wrongPassword", hash)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify detects tampered hash")
    void verifyDetectsTamperedHash() { // GH-90000
        String hash = PasswordHasher.hash("myPassword");
        String tampered = hash + "X"; // Append invalid char

        assertThat(PasswordHasher.verify("myPassword", tampered)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hash throws for null password")
    void hashThrowsForNullPassword() { // GH-90000
        assertThatThrownBy(() -> PasswordHasher.hash(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("hash throws for empty password")
    void hashThrowsForEmptyPassword() { // GH-90000
        assertThatThrownBy(() -> PasswordHasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for null plaintext")
    void verifyReturnsFalseForNullPlaintext() { // GH-90000
        String hash = PasswordHasher.hash("somePassword");

        assertThat(PasswordHasher.verify(null, hash)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for null stored hash")
    void verifyReturnsFalseForNullHash() { // GH-90000
        assertThat(PasswordHasher.verify("somePassword", null)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for non-sha256 format hash")
    void verifyReturnsFalseForUnknownHashFormat() { // GH-90000
        assertThat(PasswordHasher.verify("somePassword", "unknownformatXYZ")).isFalse(); // GH-90000
    }
}
