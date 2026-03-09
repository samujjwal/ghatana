package com.ghatana.refactorer.consistency;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles rollback of changes when regressions are detected.
 * Maintains backups in `.polyfix/backups` and ensures atomic operations.
 
 * @doc.type class
 * @doc.purpose Handles rollback manager operations
 * @doc.layer core
 * @doc.pattern Manager
*/
public class RollbackManager {
    private static final Logger logger = LogManager.getLogger(RollbackManager.class);
    private static final String BACKUP_DIR = ".polyfix/backups";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PolyfixProjectContext context;
    private final Path backupRoot;
    private final Map<Path, Path> activeBackups = new ConcurrentHashMap<>();

    public RollbackManager(PolyfixProjectContext context) {
        this.context = Objects.requireNonNull(context, "Context cannot be null");
        this.backupRoot = context.root().resolve(BACKUP_DIR);
        createBackupDirectoryIfNeeded();
    }

    /**
     * Creates a backup of the specified file.
     *
     * @param file The file to back up
     * @return The path to the backup file, or null if backup failed
     */
    public Path createBackup(Path file) {
        if (!Files.exists(file)) {
            logger.warn("Cannot backup non-existent file: {}", file);
            return null;
        }

        try {
            String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
            String relativePath = context.root().relativize(file).toString();
            String safePath = relativePath.replaceAll("[^a-zA-Z0-9-_.]", "_");
            Path backupFile = backupRoot.resolve(safePath + "." + timestamp + ".bak");

            // Ensure parent directories exist
            Files.createDirectories(backupFile.getParent());

            // Copy the file atomically
            Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            
            // Store the backup mapping
            activeBackups.put(file, backupFile);
            
            logger.debug("Created backup of {} at {}", file, backupFile);
            return backupFile;
        } catch (IOException e) {
            logger.error("Failed to create backup of " + file, e);
            return null;
        }
    }

    /**
     * Rolls back a single file to its most recent backup.
     *
     * @param file The file to roll back
     * @return true if rollback was successful, false otherwise
     */
    public boolean rollbackFile(Path file) {
        Path backupFile = activeBackups.get(file);
        if (backupFile == null || !Files.exists(backupFile)) {
            logger.warn("No backup found for file: {}", file);
            return false;
        }

        try {
            // Create a temporary file for atomic replacement
            Path tempFile = Files.createTempFile(file.getParent(), ".rollback-", ".tmp");
            
            try {
                // Copy backup to temp file
                Files.copy(backupFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
                
                // Atomically replace the original file
                Files.move(tempFile, file, 
                    StandardCopyOption.REPLACE_EXISTING, 
                    StandardCopyOption.ATOMIC_MOVE);
                
                logger.info("Rolled back file: {}", file);
                return true;
                
            } finally {
                // Clean up temp file if it still exists
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to clean up temp file: " + tempFile, e);
                }
            }
            
        } catch (IOException e) {
            logger.error("Failed to rollback file: " + file, e);
            return false;
        }
    }

    /**
     * Rolls back all files that have active backups.
     *
     * @return Map of file paths to rollback status (true = success, false = failure)
     */
    public Map<Path, Boolean> rollbackAll() {
        Map<Path, Boolean> results = new HashMap<>();
        
        // Create a copy to avoid concurrent modification
        Set<Path> filesToRollback = new HashSet<>(activeBackups.keySet());
        
        for (Path file : filesToRollback) {
            boolean success = rollbackFile(file);
            results.put(file, success);
            
            if (success) {
                activeBackups.remove(file);
            }
        }
        
        return results;
    }

    /**
     * Cleans up old backup files.
     * 
     * @param maxAgeHours Maximum age of backup files in hours
     * @return Number of files deleted
     */
    public int cleanupOldBackups(long maxAgeHours) {
        if (maxAgeHours <= 0) {
            return 0;
        }

        final Instant cutoff = Instant.now().minusSeconds(maxAgeHours * 3600);
        final List<Path> toDelete = new ArrayList<>();
        
        try {
            Files.walkFileTree(backupRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                            toDelete.add(file);
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to check file modification time: " + file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            int deleted = 0;
            for (Path file : toDelete) {
                try {
                    Files.deleteIfExists(file);
                    deleted++;
                    
                    // Remove empty parent directories
                    Path parent = file.getParent();
                    while (parent != null && !parent.equals(backupRoot) && 
                           Files.isDirectory(parent) && isEmptyDirectory(parent)) {
                        Files.deleteIfExists(parent);
                        parent = parent.getParent();
                    }
                    
                } catch (IOException e) {
                    logger.warn("Failed to delete old backup: " + file, e);
                }
            }
            
            logger.info("Cleaned up {} old backup files", deleted);
            return deleted;
            
        } catch (IOException e) {
            logger.error("Failed to clean up old backups", e);
            return 0;
        }
    }
    
    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }
    
    private void createBackupDirectoryIfNeeded() {
        try {
            Files.createDirectories(backupRoot);
            // Set hidden attribute on Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    Files.setAttribute(backupRoot, "dos:hidden", true);
                } catch (Exception e) {
                    logger.debug("Failed to set hidden attribute on backup directory", e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create backup directory: " + backupRoot, e);
        }
    }
}
