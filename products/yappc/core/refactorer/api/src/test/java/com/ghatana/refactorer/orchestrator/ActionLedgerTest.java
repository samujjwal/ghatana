/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ActionLedger} class. 
 * @doc.type class
 * @doc.purpose Handles action ledger test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class ActionLedgerTest {

    @TempDir Path tempDir;

    private Path ledgerFile;
    private ActionLedger ledger;

    @BeforeEach
    void setUp() throws IOException {
        ledgerFile = tempDir.resolve("actions.ledger");
        ledger = new ActionLedger(ledgerFile);
    }

    @Test
    void constructor_shouldNotCreateLedgerFileImmediately() {
        // The file should not exist until first append
        assertFalse(
                Files.exists(ledgerFile), "Ledger file should not be created until first append");
    }

    @Test
    void append_shouldCreateFileAndWriteEntry() throws IOException {
        // Given
        String project = "test-project";
        String file = "src/main/Test.java";
        String rule = "test-rule";
        String hash = "abc123";
        String status = "APPLIED";

        // When
        ledger.append(project, file, rule, hash, status);

        // Then
        assertTrue(Files.exists(ledgerFile), "Ledger file should be created after append");
        List<String> lines = Files.readAllLines(ledgerFile);
        assertEquals(1, lines.size(), "Should write one line to ledger");

        String json = lines.get(0).trim();
        assertTrue(json.startsWith("{\"ts\":"), "Should start with timestamp");
        assertTrue(json.contains("\"repo\":\"test-project\""), "Should contain project");
        assertTrue(json.contains("\"file\":\"src/main/Test.java\""), "Should contain file path");
        assertTrue(json.contains("\"ruleId\":\"test-rule\""), "Should contain rule ID");
        assertTrue(json.contains("\"diffHash\":\"abc123\""), "Should contain hash");
        assertTrue(json.contains("\"outcome\":\"APPLIED\""), "Should contain status");
        assertTrue(json.endsWith("}"), "Should end with closing brace");
    }

    @Test
    void append_shouldHandleSpecialCharacters() throws IOException {
        // When
        String project = "test|project";
        String file = "src/main/Test|File.java";
        String rule = "test|rule";
        String hash = "abc|123";
        String status = "APPLIED|PENDING";

        // Should not throw
        ledger.append(project, file, rule, hash, status);

        // Then
        List<String> lines = Files.readAllLines(ledgerFile);
        assertFalse(lines.isEmpty(), "Should write to ledger file");
    }

    @Test
    void append_shouldBeThreadSafe() throws Exception {
        // Given
        int threadCount = 10;
        int iterations = 100;
        AtomicInteger threadCounter = new AtomicInteger(0);

        // When
        Runnable task =
                () -> {
                    int threadId = threadCounter.incrementAndGet();
                    for (int i = 0; i < iterations; i++) {
                        ledger.append(
                                "project" + threadId,
                                "file" + i,
                                "rule" + i,
                                "hash" + i,
                                "STATUS" + i);
                    }
                };

        // Start multiple threads
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - verify all entries were written without corruption
        List<String> lines = Files.readAllLines(ledgerFile);
        assertEquals(threadCount * iterations, lines.size(), "Should have one entry per operation");

        // Verify all lines are well-formed JSON
        for (String line : lines) {
            String json = line.trim();
            assertTrue(json.startsWith("{\"ts\":"), "Should start with timestamp");
            assertTrue(json.endsWith("}"), "Should end with closing brace");
            assertTrue(json.contains("\"repo\":"), "Should contain repo field");
            assertTrue(json.contains("\"file\":"), "Should contain file field");
            assertTrue(json.contains("\"ruleId\":"), "Should contain ruleId field");
            assertTrue(json.contains("\"diffHash\":"), "Should contain diffHash field");
            assertTrue(json.contains("\"outcome\":"), "Should contain outcome field");
        }
    }

    @Test
    void append_shouldThrowOnNullValues() {
        // Then - should throw NPE for null values
        assertThrows(
                NullPointerException.class,
                () -> ledger.append(null, "file", "rule", "hash", "status"),
                "Should throw NPE for null project");

        assertThrows(
                NullPointerException.class,
                () -> ledger.append("project", null, "rule", "hash", "status"),
                "Should throw NPE for null file");

        assertThrows(
                NullPointerException.class,
                () -> ledger.append("project", "file", null, "hash", "status"),
                "Should throw NPE for null rule");

        assertThrows(
                NullPointerException.class,
                () -> ledger.append("project", "file", "rule", null, "status"),
                "Should throw NPE for null hash");

        assertThrows(
                NullPointerException.class,
                () -> ledger.append("project", "file", "rule", "hash", null),
                "Should throw NPE for null status");
    }
}
