/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.security.encryption;

import com.ghatana.platform.security.encryption.impl.AesGcmEncryptionProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 expansion: Edge cases and failure modes for AES-GCM encryption.
 * Covers boundary values, null handling, concurrency, and data integrity.
 *
 * @doc.type class
 * @doc.purpose Encryption edge cases, boundary conditions, and failure scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AesGcmEncryptionProvider - Phase 3 Expansion")
class AesGcmEncryptionProviderExpansionTest extends EventloopTestBase {

    private AesGcmEncryptionProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = AesGcmEncryptionProvider.withNewKey(256, "test-key-phase3"); // GH-90000
    }

    // ============================================
    // NULL/EMPTY VALUE TESTS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Null/Empty Handling")
    class NullEmptyTests {

        @Test
        @DisplayName("Rejects null plaintext")
        void rejectNullPlaintext() { // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> provider.encrypt(null)); // GH-90000
            }).isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Encryption failed");
        }

        @Test
        @DisplayName("Rejects null ciphertext")
        void rejectNullCiphertext() { // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> provider.decrypt(null)); // GH-90000
            }).isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Decryption failed");
        }

        @Test
        @DisplayName("Handles empty plaintext")
        void emptyPlaintext() { // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(new byte[0])); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000
            assertThat(decrypted).isEmpty(); // GH-90000
        }
    }

    // ============================================
    // BOUNDARY VALUE TESTS (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Boundary Values")
    class BoundaryTests {

        @Test
        @DisplayName("Encrypts single byte")
        void singleByte() { // GH-90000
            byte[] plaintext = new byte[] { (byte) 0xFF }; // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000
            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("Encrypts exactly 1 KB data")
        void exactlyOnekB() { // GH-90000
            byte[] plaintext = new byte[1024];
            Arrays.fill(plaintext, (byte) 0xAA); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000
            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("Encrypts very large data (10 MB)")
        void veryLargeData() { // GH-90000
            byte[] plaintext = new byte[10 * 1024 * 1024];
            Arrays.fill(plaintext, (byte) 0x42); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000
            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }

        @Test
        @DisplayName("Ciphertext is larger than plaintext (IV + tag overhead)")
        void ciphertextLarger() { // GH-90000
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            // GCM adds IV (12 bytes typically) + auth tag (16 bytes) // GH-90000
            assertThat(encrypted.length).isGreaterThan(plaintext.length); // GH-90000
        }

        @Test
        @DisplayName("Binary data with all byte values (0-255)")
        void allByteValues() { // GH-90000
            byte[] plaintext = new byte[256];
            for (int i = 0; i < 256; i++) { // GH-90000
                plaintext[i] = (byte) i; // GH-90000
            }
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000
            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
        }
    }

    // ============================================
    // TAMPERING DETECTION (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Tampering & Integrity Detection")
    class TamperingTests {

        @Test
        @DisplayName("Detects modified ciphertext (bit flip)")
        void detectBitFlip() { // GH-90000
            byte[] plaintext = "Sensitive Data".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            // Flip single bit in middle
            encrypted[encrypted.length / 2] ^= 0x01;

            // Decryption should fail or return invalid data
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> provider.decrypt(encrypted)); // GH-90000
            }).isInstanceOf(Exception.class); // Could be DecryptionException, specific to impl // GH-90000
        }

        @Test
        @DisplayName("Detects truncated ciphertext")
        void detectTruncation() { // GH-90000
            byte[] plaintext = "Test Data".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            // Truncate to 50%
            byte[] truncated = Arrays.copyOf(encrypted, encrypted.length / 2); // GH-90000

            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> provider.decrypt(truncated)); // GH-90000
            }).isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("Rejects ciphertext appended with extra bytes")
        void detectAppendedBytes() { // GH-90000
            byte[] plaintext = "Data".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            // Append garbage
            byte[] extended = Arrays.copyOf(encrypted, encrypted.length + 10); // GH-90000
            extended[encrypted.length] = (byte) 0xFF; // GH-90000

            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> provider.decrypt(extended)); // GH-90000
            }).isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("Different providers reject each other's ciphertexts")
        void differentKeysRejectCiphertext() { // GH-90000
            AesGcmEncryptionProvider provider1 = AesGcmEncryptionProvider.withNewKey(256, "key-1"); // GH-90000
            AesGcmEncryptionProvider provider2 = AesGcmEncryptionProvider.withNewKey(256, "key-2"); // GH-90000

            byte[] plaintext = "Secret".getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] encrypted = runPromise(() -> provider1.encrypt(plaintext)); // GH-90000

            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> provider2.decrypt(encrypted)); // GH-90000
            }).isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("IV/nonce reuse produces different ciphertexts")
        void nonceDiversity() { // GH-90000
            byte[] plaintext = "Deterministic?".getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted1 = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] encrypted2 = runPromise(() -> provider.encrypt(plaintext)); // GH-90000

            // With random IV (as GCM should), ciphertexts should differ // GH-90000
            assertThat(encrypted1).isNotEqualTo(encrypted2); // GH-90000
        }
    }

    // ============================================
    // CHARACTER ENCODING TESTS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Character Encoding")
    class EncodingTests {

        @Test
        @DisplayName("Round-trip UTF-8 encoded strings with non-ASCII")
        void utf8NonAscii() { // GH-90000
            String original = "Hello 世界 مرحبا мир";
            byte[] plaintext = original.getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000

            String recovered = new String(decrypted, StandardCharsets.UTF_8); // GH-90000
            assertThat(recovered).isEqualTo(original); // GH-90000
        }

        @Test
        @DisplayName("Round-trip emoji and special Unicode")
        void unicodeSpecial() { // GH-90000
            String original = "🔒🔐🗝️💬📞";
            byte[] plaintext = original.getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000

            String recovered = new String(decrypted, StandardCharsets.UTF_8); // GH-90000
            assertThat(recovered).isEqualTo(original); // GH-90000
        }

        @Test
        @DisplayName("Different encodings produce different ciphertexts")
        void differentEncodings() { // GH-90000
            String text = "Test";
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8); // GH-90000
            byte[] utf16 = text.getBytes(StandardCharsets.UTF_16); // GH-90000

            byte[] encrypted1 = runPromise(() -> provider.encrypt(utf8)); // GH-90000
            byte[] encrypted2 = runPromise(() -> provider.encrypt(utf16)); // GH-90000

            assertThat(encrypted1).isNotEqualTo(encrypted2); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT ACCESS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent encryption is thread-safe")
        void concurrentEncryption() throws InterruptedException { // GH-90000
            byte[] plaintext = "Concurrent Test Data".getBytes(StandardCharsets.UTF_8); // GH-90000
            java.util.List<byte[]> encryptedResults = new java.util.concurrent.CopyOnWriteArrayList<>(); // GH-90000
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(5); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                new Thread(() -> { // GH-90000
                    try {
                        byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
                        encryptedResults.add(encrypted); // GH-90000
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000
            // All should produce valid ciphertexts (even if different due to IV) // GH-90000
            assertThat(encryptedResults).hasSize(5); // GH-90000
            encryptedResults.forEach(ct -> assertThat(ct).isNotNull()); // GH-90000
        }

        @Test
        @DisplayName("Concurrent encrypt/decrypt doesn't corrupt data")
        void concurrentRoundTrip() throws InterruptedException { // GH-90000
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(10); // GH-90000
            java.util.List<Boolean> allValid = new java.util.concurrent.CopyOnWriteArrayList<>(); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                final byte[] plaintext = ("Thread " + i).getBytes(StandardCharsets.UTF_8); // GH-90000
                new Thread(() -> { // GH-90000
                    try {
                        byte[] encrypted = runPromise(() -> provider.encrypt(plaintext)); // GH-90000
                        byte[] decrypted = runPromise(() -> provider.decrypt(encrypted)); // GH-90000
                        allValid.add(Arrays.equals(plaintext, decrypted)); // GH-90000
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000
            assertThat(allValid).hasSize(10).allMatch(b -> b); // GH-90000
        }
    }
}
