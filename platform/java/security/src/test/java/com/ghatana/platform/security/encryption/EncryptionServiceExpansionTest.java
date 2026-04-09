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
@DisplayName("EncryptionService - Phase 3 Expansion")
class EncryptionServiceExpansionTest extends EventloopTestBase {

    private EncryptionService encryptionService;
    private AesGcmEncryptionProvider provider;

    @BeforeEach
    void setUp() {
        provider = AesGcmEncryptionProvider.withNewKey(256, "svc-test-key");
        encryptionService = new EncryptionService(provider, eventloop());
    }

    // ============================================
    // LARGE DATA ENCRYPTION (2 tests)
    // ============================================

    @Nested
    @DisplayName("Large Data Encryption")
    class LargeDataTests {

        @Test
        @DisplayName("Encrypt and decrypt large data (1MB)")
        void largeMegabyteData() {
            byte[] plaintext = new byte[1024 * 1024]; // 1 MB
            for (int i = 0; i < plaintext.length; i++) {
                plaintext[i] = (byte) (i % 256);
            }

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);
            assertThat(encrypted.length).isGreaterThan(plaintext.length);
        }

        @Test
        @DisplayName("Encrypt very small data (single byte)")
        void singleByteData() {
            byte[] plaintext = new byte[] { 42 };

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);
            assertThat(encrypted[0]).isNotEqualTo(42);
        }
    }

    // ============================================
    // STRUCTURED DATA SUPPORT (1 test)
    // ============================================

    @Nested
    @DisplayName("Structured Data Support")
    class StructuredDataTests {

        @Test
        @DisplayName("Encrypt and decrypt JSON data")
        void jsonDataEncryption() {
            String jsonData = "{\"userId\":\"user-123\",\"email\":\"user@example.com\",\"nested\":{\"field\":\"value\"}}";
            byte[] plaintext = jsonData.getBytes(StandardCharsets.UTF_8);

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));

            String decryptedJson = new String(decrypted, StandardCharsets.UTF_8);
            assertThat(decryptedJson).isEqualTo(jsonData);
        }
    }

    // ============================================
    // CONCURRENT ENCRYPTION (1 test)
    // ============================================

    @Nested
    @DisplayName("Concurrent Encryption")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent encryption/decryption operations don't corrupt data")
        void concurrentEncryptionIntegrity() throws InterruptedException {
            int threadCount = 5;
            String[] testData = new String[] {
                "Data thread 0",
                "Data thread 1",
                "Data thread 2",
                "Data thread 3",
                "Data thread 4"
            };

            CountDownLatch latch = new CountDownLatch(threadCount);
            ConcurrentHashMap<Integer, String> results = new ConcurrentHashMap<>();
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                int index = i;
                new Thread(() -> {
                    try {
                        byte[] plaintext = testData[index].getBytes(StandardCharsets.UTF_8);
                        byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
                        byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));
                        String result = new String(decrypted, StandardCharsets.UTF_8);

                        if (result.equals(testData[index])) {
                            results.put(index, result);
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            // All threads should have successfully encrypted and decrypted
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(results).hasSize(threadCount);
        }
    }

    // ============================================
    // BINARY DATA HANDLING (1 test)
    // ============================================

    @Nested
    @DisplayName("Binary Data Handling")
    class BinaryDataTests {

        @Test
        @DisplayName("Encrypt and decrypt arbitrary binary data")
        void binaryDataRoundTrip() {
            // Test data with all byte values (0-255)
            byte[] plaintext = new byte[256];
            for (int i = 0; i < 256; i++) {
                plaintext[i] = (byte) i;
            }

            byte[] encrypted = runPromise(() -> encryptionService.encryptAsync(plaintext));
            byte[] decrypted = runPromise(() -> encryptionService.decryptAsync(encrypted));

            assertThat(decrypted).isEqualTo(plaintext);

            // All encrypted bytes should be different from original (statistical property)
            int differentCount = 0;
            for (int i = 0; i < Math.min(plaintext.length, encrypted.length); i++) {
                if (plaintext[i] != encrypted[i]) {
                    differentCount++;
                }
            }
            assertThat(differentCount).isGreaterThan(plaintext.length / 2);
        }
    }
}
