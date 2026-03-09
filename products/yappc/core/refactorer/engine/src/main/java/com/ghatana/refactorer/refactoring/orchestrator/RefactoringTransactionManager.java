package com.ghatana.refactorer.refactoring.orchestrator;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages transactions for refactoring operations to ensure atomicity. 
 * @doc.type class
 * @doc.purpose Handles refactoring transaction manager operations
 * @doc.layer core
 * @doc.pattern Manager
*/
public class RefactoringTransactionManager {
    private static final Logger log = LoggerFactory.getLogger(RefactoringTransactionManager.class);
    private static final String BACKUP_SUFFIX = ".polyfix.bak";

    private final Map<String, List<Path>> transactionFiles = new ConcurrentHashMap<>();
    private final Map<String, Map<Path, Path>> backupFiles = new ConcurrentHashMap<>();

    /**
 * Starts a new transaction and returns its ID. */
    public String beginTransaction() {
        String transactionId = UUID.randomUUID().toString();
        transactionFiles.put(transactionId, new ArrayList<>());
        backupFiles.put(transactionId, new HashMap<>());
        log.debug("Began transaction: {}", transactionId);
        return transactionId;
    }

    /**
 * Registers a file to be tracked by a transaction. */
    public void registerFile(String transactionId, Path filePath) throws IOException {
        if (transactionId == null || filePath == null) {
            throw new IllegalArgumentException("Transaction ID and file path cannot be null");
        }

        List<Path> files = transactionFiles.get(transactionId);
        if (files == null) {
            throw new IllegalStateException("No active transaction with ID: " + transactionId);
        }

        // Create a backup of the file
        if (Files.exists(filePath)) {
            Path backupPath = createBackup(transactionId, filePath);
            backupFiles.get(transactionId).put(filePath, backupPath);
            log.debug("Created backup of {} at {}", filePath, backupPath);
        }

        files.add(filePath);
    }

    /**
 * Commits a transaction, making all changes permanent. */
    public void commitTransaction(String transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }

        log.debug("Committing transaction: {}", transactionId);

        try {
            // Delete all backup files
            Map<Path, Path> backups = backupFiles.get(transactionId);
            if (backups != null) {
                for (Path backupPath : backups.values()) {
                    try {
                        Files.deleteIfExists(backupPath);
                        log.debug("Deleted backup file: {}", backupPath);
                    } catch (IOException e) {
                        log.warn("Failed to delete backup file: " + backupPath, e);
                    }
                }
            }
        } finally {
            cleanupTransaction(transactionId);
        }
    }

    /**
 * Rolls back a transaction, restoring all files to their original state. */
    public void rollbackTransaction(String transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }

        log.debug("Rolling back transaction: {}", transactionId);

        try {
            Map<Path, Path> backups = backupFiles.get(transactionId);
            if (backups != null) {
                for (Map.Entry<Path, Path> entry : backups.entrySet()) {
                    Path originalPath = entry.getKey();
                    Path backupPath = entry.getValue();

                    try {
                        if (Files.exists(backupPath)) {
                            // Restore the original file
                            Files.move(
                                    backupPath,
                                    originalPath,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.ATOMIC_MOVE);
                            log.debug("Restored file from backup: {}", originalPath);
                        }
                    } catch (IOException e) {
                        log.error("Failed to restore file from backup: " + originalPath, e);
                        // Continue with other files
                    }
                }
            }
        } finally {
            cleanupTransaction(transactionId);
        }
    }

    /**
 * Creates a backup of a file and returns the backup path. */
    private Path createBackup(String transactionId, Path filePath) throws IOException {
        // Create a unique backup file name
        String backupFileName = filePath.getFileName() + "." + transactionId + BACKUP_SUFFIX;
        Path backupDir = filePath.getParent().resolve(".polyfix/backups");

        // Create backup directory if it doesn't exist
        Files.createDirectories(backupDir);

        Path backupPath = backupDir.resolve(backupFileName);

        // Copy the file to the backup location
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        return backupPath;
    }

    /**
 * Cleans up transaction resources. */
    private void cleanupTransaction(String transactionId) {
        transactionFiles.remove(transactionId);
        backupFiles.remove(transactionId);
    }

    /**
 * Cleans up old backup files. */
    public void cleanupOldBackups() throws IOException {
        // Find all backup files
        Path workingDir = Paths.get(".");
        FileVisitor<Path> visitor =
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (file.getFileName().toString().endsWith(BACKUP_SUFFIX)) {
                            try {
                                Files.deleteIfExists(file);
                                log.debug("Deleted old backup file: {}", file);
                            } catch (IOException e) {
                                log.warn("Failed to delete old backup file: " + file, e);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        log.warn("Failed to visit file: " + file, exc);
                        return FileVisitResult.CONTINUE;
                    }
                };

        // Walk through all directories
        Files.walkFileTree(workingDir, EnumSet.noneOf(FileVisitOption.class), 10, visitor);
    }
}
