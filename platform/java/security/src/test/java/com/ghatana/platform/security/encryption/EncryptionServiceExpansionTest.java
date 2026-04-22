package com.ghatana.platform.security.encryption;

import com.ghatana.platform.security.encryption.impl.AesGcmEncryptionProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: EncryptionService edge cases and concurrent scenarios.
 * Tests large data, concurrent encryption, multi-key scenarios, and JSON/binary support.
 *
 * @doc.type class
 * @doc.purpose EncryptionService edge cases and concurrent encryption scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("EncryptionService - Phase 3 Expansion [GH-90000]")
class EncryptionServiceExpansionTest extends EventloopTestBase {

    private EncryptionService encryptionService;
    private AesGcmEncryptionProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = AesGcmEncryptionProvider.withNewKey(256, "svc-test-key"); // GH-90000
        encryptionService = new EncryptionService(provider, eventloop()); // GH-90000
    }

    // ============================================
    // LARGE DATA ENCRYPTION (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Large Data Encryption [GH-90000]")
    class LargeDataTests {

        @Test
        @DisplayName("Encrypt and decrypt large data (1MB) [GH-90000]")
        void largeMegabyteData() { // GH-90000
            byte[] plaintext = new byte[1024 * 1024]; // 1 MB
            for (int i = 0; i < plaintext.length; i++) { // GH-90000
                plaintext[i] = (byte) (i % 256); // GH-90000
            }

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
            assertThat(encrypted.length).isGreaterThan(plaintext.length); // GH-90000
        }

        @Test
        @DisplayName("Encrypt very small data (single byte) [GH-90000]")
        void singleByteData() { // GH-90000
            byte[] plaintext = new byte[] { 42 };

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000
            assertThat(encrypted[0]).isNotEqualTo(42); // GH-90000
        }
    }

    // ============================================
    // STRUCTURED DATA SUPPORT (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Structured Data Support [GH-90000]")
    class StructuredDataTests {

        @Test
        @DisplayName("Encrypt and decrypt JSON data [GH-90000]")
        void jsonDataEncryption() { // GH-90000
            String jsonData = "{\"userId\":\"user-123\",\"email\":\"user@example.com\",\"nested\":{\"field\":\"value\"}}";
            byte[] plaintext = jsonData.getBytes(StandardCharsets.UTF_8); // GH-90000

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000

            String decryptedJson = new String(decrypted, StandardCharsets.UTF_8); // GH-90000
            assertThat(decryptedJson).isEqualTo(jsonData); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT ENCRYPTION (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Encryption [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent encryption/decryption operations don't corrupt data [GH-90000]")
        void concurrentEncryptionIntegrity() throws InterruptedException { // GH-90000
            int threadCount = 5;
            String[] testData = new String[] {
                "Data thread 0",
                "Data thread 1",
                "Data thread 2",
                "Data thread 3",
                "Data thread 4"
            };

            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            ConcurrentHashMap<Integer, String> results = new ConcurrentHashMap<>(); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                int index = i;
                new Thread(() -> { // GH-90000
                    try {
                        byte[] plaintext = testData[index].getBytes(StandardCharsets.UTF_8); // GH-90000
                        byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
                        byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000
                        String result = new String(decrypted, StandardCharsets.UTF_8); // GH-90000

                        if (result.equals(testData[index])) { // GH-90000
                            results.put(index, result); // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            // All threads should have successfully encrypted and decrypted
            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
            assertThat(results).hasSize(threadCount); // GH-90000
        }
    }

    // ============================================
    // BINARY DATA HANDLING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Binary Data Handling [GH-90000]")
    class BinaryDataTests {

        @Test
        @DisplayName("Encrypt and decrypt arbitrary binary data [GH-90000]")
        void binaryDataRoundTrip() { // GH-90000
            // Test data with all byte values (0-255) // GH-90000
            byte[] plaintext = new byte[256];
            for (int i = 0; i < 256; i++) { // GH-90000
                plaintext[i] = (byte) i; // GH-90000
            }

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext)); // GH-90000
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted)); // GH-90000

            assertThat(decrypted).isEqualTo(plaintext); // GH-90000

            // All encrypted bytes should be different from original (statistical property) // GH-90000
            int differentCount = 0;
            for (int i = 0; i < Math.min(plaintext.length, encrypted.length); i++) { // GH-90000
                if (plaintext[i] != encrypted[i]) { // GH-90000
                    differentCount++;
                }
            }
            assertThat(differentCount).isGreaterThan(plaintext.length / 2); // GH-90000
        }
    }
}
