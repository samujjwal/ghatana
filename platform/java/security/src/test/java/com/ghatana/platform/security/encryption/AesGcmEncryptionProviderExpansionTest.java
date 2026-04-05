/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        provider = AesGcmEncryptionProvider.withNewKey(256, "test-key-phase3");
    }

    // ============================================
    // NULL/EMPTY VALUE TESTS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Null/Empty Handling")
    class NullEmptyTests {

        @Test
        @DisplayName("Rejects null plaintext")
        void rejectNullPlaintext() {
            assertThatThrownBy(() -> {
                runPromise(() -> provider.encrypt(null));
            }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Encryption failed");
        }

        @Test
        @DisplayName("Rejects null ciphertext")
        void rejectNullCiphertext() {
            assertThatThrownBy(() -> {
                runPromise(() -> provider.decrypt(null));
            }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
        }

        @Test
        @DisplayName("Handles empty plaintext")
        void emptyPlaintext() {
            byte[] encrypted = runPromise(() -> provider.encrypt(new byte[0]));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            assertThat(decrypted).isEmpty();
        }
    }

    // ============================================
    // BOUNDARY VALUE TESTS (5 tests)
    // ============================================

    @Nested
    @DisplayName("Boundary Values")
    class BoundaryTests {

        @Test
        @DisplayName("Encrypts single byte")
        void singleByte() {
            byte[] plaintext = new byte[] { (byte) 0xFF };
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Encrypts exactly 1 KB data")
        void exactlyOnekB() {
            byte[] plaintext = new byte[1024];
            Arrays.fill(plaintext, (byte) 0xAA);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Encrypts very large data (10 MB)")
        void veryLargeData() {
            byte[] plaintext = new byte[10 * 1024 * 1024];
            Arrays.fill(plaintext, (byte) 0x42);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Ciphertext is larger than plaintext (IV + tag overhead)")
        void ciphertextLarger() {
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            // GCM adds IV (12 bytes typically) + auth tag (16 bytes)
            assertThat(encrypted.length).isGreaterThan(plaintext.length);
        }

        @Test
        @DisplayName("Binary data with all byte values (0-255)")
        void allByteValues() {
            byte[] plaintext = new byte[256];
            for (int i = 0; i < 256; i++) {
                plaintext[i] = (byte) i;
            }
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    // ============================================
    // TAMPERING DETECTION (5 tests)
    // ============================================

    @Nested
    @DisplayName("Tampering & Integrity Detection")
    class TamperingTests {

        @Test
        @DisplayName("Detects modified ciphertext (bit flip)")
        void detectBitFlip() {
            byte[] plaintext = "Sensitive Data".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            
            // Flip single bit in middle
            encrypted[encrypted.length / 2] ^= 0x01;
            
            // Decryption should fail or return invalid data
            assertThatThrownBy(() -> {
                runPromise(() -> provider.decrypt(encrypted));
            }).isInstanceOf(Exception.class); // Could be DecryptionException, specific to impl
        }

        @Test
        @DisplayName("Detects truncated ciphertext")
        void detectTruncation() {
            byte[] plaintext = "Test Data".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            
            // Truncate to 50%
            byte[] truncated = Arrays.copyOf(encrypted, encrypted.length / 2);
            
            assertThatThrownBy(() -> {
                runPromise(() -> provider.decrypt(truncated));
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Rejects ciphertext appended with extra bytes")
        void detectAppendedBytes() {
            byte[] plaintext = "Data".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            
            // Append garbage
            byte[] extended = Arrays.copyOf(encrypted, encrypted.length + 10);
            extended[encrypted.length] = (byte) 0xFF;
            
            assertThatThrownBy(() -> {
                runPromise(() -> provider.decrypt(extended));
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Different providers reject each other's ciphertexts")
        void differentKeysRejectCiphertext() {
            AesGcmEncryptionProvider provider1 = AesGcmEncryptionProvider.withNewKey(256, "key-1");
            AesGcmEncryptionProvider provider2 = AesGcmEncryptionProvider.withNewKey(256, "key-2");
            
            byte[] plaintext = "Secret".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = runPromise(() -> provider1.encrypt(plaintext));
            
            assertThatThrownBy(() -> {
                runPromise(() -> provider2.decrypt(encrypted));
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("IV/nonce reuse produces different ciphertexts")
        void nonceDiversity() {
            byte[] plaintext = "Deterministic?".getBytes(StandardCharsets.UTF_8);
            
            byte[] encrypted1 = runPromise(() -> provider.encrypt(plaintext));
            byte[] encrypted2 = runPromise(() -> provider.encrypt(plaintext));
            
            // With random IV (as GCM should), ciphertexts should differ
            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    // ============================================
    // CHARACTER ENCODING TESTS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Character Encoding")
    class EncodingTests {

        @Test
        @DisplayName("Round-trip UTF-8 encoded strings with non-ASCII")
        void utf8NonAscii() {
            String original = "Hello 世界 مرحبا мир";
            byte[] plaintext = original.getBytes(StandardCharsets.UTF_8);
            
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            
            String recovered = new String(decrypted, StandardCharsets.UTF_8);
            assertThat(recovered).isEqualTo(original);
        }

        @Test
        @DisplayName("Round-trip emoji and special Unicode")
        void unicodeSpecial() {
            String original = "🔒🔐🗝️💬📞";
            byte[] plaintext = original.getBytes(StandardCharsets.UTF_8);
            
            byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
            byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
            
            String recovered = new String(decrypted, StandardCharsets.UTF_8);
            assertThat(recovered).isEqualTo(original);
        }

        @Test
        @DisplayName("Different encodings produce different ciphertexts")
        void differentEncodings() {
            String text = "Test";
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            byte[] utf16 = text.getBytes(StandardCharsets.UTF_16);
            
            byte[] encrypted1 = runPromise(() -> provider.encrypt(utf8));
            byte[] encrypted2 = runPromise(() -> provider.encrypt(utf16));
            
            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    // ============================================
    // CONCURRENT ACCESS (2 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent encryption is thread-safe")
        void concurrentEncryption() throws InterruptedException {
            byte[] plaintext = "Concurrent Test Data".getBytes(StandardCharsets.UTF_8);
            java.util.List<byte[]> encryptedResults = new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(5);
            
            for (int i = 0; i < 5; i++) {
                new Thread(() -> {
                    try {
                        byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
                        encryptedResults.add(encrypted);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            
            latch.await();
            // All should produce valid ciphertexts (even if different due to IV)
            assertThat(encryptedResults).hasSize(5);
            encryptedResults.forEach(ct -> assertThat(ct).isNotNull());
        }

        @Test
        @DisplayName("Concurrent encrypt/decrypt doesn't corrupt data")
        void concurrentRoundTrip() throws InterruptedException {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(10);
            java.util.List<Boolean> allValid = new java.util.concurrent.CopyOnWriteArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                final byte[] plaintext = ("Thread " + i).getBytes(StandardCharsets.UTF_8);
                new Thread(() -> {
                    try {
                        byte[] encrypted = runPromise(() -> provider.encrypt(plaintext));
                        byte[] decrypted = runPromise(() -> provider.decrypt(encrypted));
                        allValid.add(Arrays.equals(plaintext, decrypted));
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            
            latch.await();
            assertThat(allValid).hasSize(10).allMatch(b -> b);
        }
    }
}
