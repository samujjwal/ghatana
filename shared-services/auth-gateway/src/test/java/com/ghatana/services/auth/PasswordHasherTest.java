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
@DisplayName("PasswordHasher — SHA-256+salt hashing and verification [GH-90000]")
class PasswordHasherTest {

    @Test
    @DisplayName("hash produces a non-empty string [GH-90000]")
    void hashProducesNonEmptyString() { // GH-90000
        String hash = PasswordHasher.hash("myPassword123 [GH-90000]");

        assertThat(hash).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("hash result is not plaintext [GH-90000]")
    void hashIsNotPlaintext() { // GH-90000
        String hash = PasswordHasher.hash("myPassword123 [GH-90000]");

        assertThat(hash).doesNotContain("myPassword123 [GH-90000]");
    }

    @Test
    @DisplayName("hash result starts with $sha256$ prefix [GH-90000]")
    void hashHasCorrectPrefix() { // GH-90000
        String hash = PasswordHasher.hash("myPassword123 [GH-90000]");

        assertThat(hash).startsWith("$sha256$ [GH-90000]");
    }

    @Test
    @DisplayName("same password produces different hashes on each call (due to salt) [GH-90000]")
    void samePasswordProducesDifferentHashes() { // GH-90000
        String hash1 = PasswordHasher.hash("myPassword123 [GH-90000]");
        String hash2 = PasswordHasher.hash("myPassword123 [GH-90000]");

        assertThat(hash1).isNotEqualTo(hash2); // GH-90000
    }

    @Test
    @DisplayName("verify returns true when password matches hash [GH-90000]")
    void verifyReturnsTrueForMatchingPassword() { // GH-90000
        String password = "correct-horse-battery-staple";
        String hash = PasswordHasher.hash(password); // GH-90000

        assertThat(PasswordHasher.verify(password, hash)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for wrong password [GH-90000]")
    void verifyReturnsFalseForWrongPassword() { // GH-90000
        String hash = PasswordHasher.hash("originalPassword [GH-90000]");

        assertThat(PasswordHasher.verify("wrongPassword", hash)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify detects tampered hash [GH-90000]")
    void verifyDetectsTamperedHash() { // GH-90000
        String hash = PasswordHasher.hash("myPassword [GH-90000]");
        String tampered = hash + "X"; // Append invalid char

        assertThat(PasswordHasher.verify("myPassword", tampered)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hash throws for null password [GH-90000]")
    void hashThrowsForNullPassword() { // GH-90000
        assertThatThrownBy(() -> PasswordHasher.hash(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("hash throws for empty password [GH-90000]")
    void hashThrowsForEmptyPassword() { // GH-90000
        assertThatThrownBy(() -> PasswordHasher.hash(" [GH-90000]"))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for null plaintext [GH-90000]")
    void verifyReturnsFalseForNullPlaintext() { // GH-90000
        String hash = PasswordHasher.hash("somePassword [GH-90000]");

        assertThat(PasswordHasher.verify(null, hash)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for null stored hash [GH-90000]")
    void verifyReturnsFalseForNullHash() { // GH-90000
        assertThat(PasswordHasher.verify("somePassword", null)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("verify returns false for non-sha256 format hash [GH-90000]")
    void verifyReturnsFalseForUnknownHashFormat() { // GH-90000
        assertThat(PasswordHasher.verify("somePassword", "unknownformatXYZ")).isFalse(); // GH-90000
    }
}
