package com.ghatana.refactorer.refactoring.orchestrator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles refactoring transaction manager test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RefactoringTransactionManagerTest {

    @TempDir Path tempDir;
    private RefactoringTransactionManager transactionManager;
    private Path testFile;
    private final String testContent = "Test content";

    @BeforeEach
    void setUp() throws IOException {
        transactionManager = new RefactoringTransactionManager();
        testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, testContent);
    }

    @AfterEach
    void tearDown() {
        // Clean up any remaining backup files
        try {
            transactionManager.cleanupOldBackups();
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testTransactionLifecycle() throws IOException {
        // Start a transaction
        String transactionId = transactionManager.beginTransaction();
        assertNotNull(transactionId);

        // Register a file with the transaction
        transactionManager.registerFile(transactionId, testFile);

        // Modify the file
        String modifiedContent = "Modified content";
        Files.writeString(testFile, modifiedContent);
        assertEquals(modifiedContent, Files.readString(testFile));

        // Rollback the transaction
        transactionManager.rollbackTransaction(transactionId);

        // Verify the file was restored
        assertEquals(testContent, Files.readString(testFile));
    }

    @Test
    void testCommitTransaction() throws IOException {
        String transactionId = transactionManager.beginTransaction();
        transactionManager.registerFile(transactionId, testFile);

        // Modify the file
        String modifiedContent = "Modified content";
        Files.writeString(testFile, modifiedContent);

        // Commit the transaction
        transactionManager.commitTransaction(transactionId);

        // Verify the file was not restored
        assertEquals(modifiedContent, Files.readString(testFile));
    }

    @Test
    void testMultipleFilesInTransaction() throws IOException {
        // Create a second test file
        Path testFile2 = tempDir.resolve("test2.txt");
        String testContent2 = "Test content 2";
        Files.writeString(testFile2, testContent2);

        String transactionId = transactionManager.beginTransaction();

        // Register both files
        transactionManager.registerFile(transactionId, testFile);
        transactionManager.registerFile(transactionId, testFile2);

        // Modify both files
        String modifiedContent = "Modified content";
        Files.writeString(testFile, modifiedContent);
        Files.writeString(testFile2, modifiedContent);

        // Rollback and verify both files were restored
        transactionManager.rollbackTransaction(transactionId);
        assertEquals(testContent, Files.readString(testFile));
        assertEquals(testContent2, Files.readString(testFile2));
    }

    @Test
    void testNonexistentFile() {
        String transactionId = transactionManager.beginTransaction();
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");

        // Should not throw when registering a non-existent file
        assertDoesNotThrow(() -> transactionManager.registerFile(transactionId, nonExistentFile));

        // Should handle rollback gracefully
        assertDoesNotThrow(() -> transactionManager.rollbackTransaction(transactionId));
    }

    @Test
    void testCleanupOldBackups() throws IOException {
        // Create some backup files under the working directory
        String suffix = deriveBackupSuffix();
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path backupDir = cwd.resolve(".polyfix/backups");
        Path backup1 = backupDir.resolve("test.txt.123" + suffix);
        Path backup2 = backupDir.resolve("test.txt.456" + suffix);

        Files.createDirectories(backupDir);
        Files.createFile(backup1);
        Files.createFile(backup2);

        // Run cleanup
        transactionManager.cleanupOldBackups();

        // Verify backups were deleted
        assertFalse(Files.exists(backup1));
        assertFalse(Files.exists(backup2));
    }

    private String deriveBackupSuffix() throws IOException {
        Path probeFile = tempDir.resolve("probe.txt");
        Files.writeString(probeFile, "probe");
        String transactionId = transactionManager.beginTransaction();
        transactionManager.registerFile(transactionId, probeFile);

        Path backupsDir = probeFile.getParent().resolve(".polyfix/backups");
        try (var stream = Files.list(backupsDir)) {
            String suffix =
                    stream.findFirst()
                            .map(path -> path.getFileName().toString())
                            .map(name -> name.replace("probe.txt." + transactionId, ""))
                            .orElse(".polyfix.bak");
            transactionManager.rollbackTransaction(transactionId);
            return suffix;
        }
    }

    @Test
    void testConcurrentTransactions() throws InterruptedException, IOException {
        // Test that transactions are isolated
        String tx1 = transactionManager.beginTransaction();
        String tx2 = transactionManager.beginTransaction();

        // Register same file in both transactions
        transactionManager.registerFile(tx1, testFile);
        transactionManager.registerFile(tx2, testFile);

        // Modify file in first transaction
        String tx1Content = "TX1 content";
        Files.writeString(testFile, tx1Content);

        // Rollback first transaction, commit second
        transactionManager.rollbackTransaction(tx1);
        transactionManager.commitTransaction(tx2);

        // File should be restored to original content after both transactions
        assertEquals(testContent, Files.readString(testFile));
    }
}
