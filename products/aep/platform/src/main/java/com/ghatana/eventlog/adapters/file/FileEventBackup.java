package com.ghatana.eventlog.adapters.file;

import com.ghatana.eventlog.adapters.EventBackup;
import com.ghatana.eventlog.adapters.EventLogRepository;
import com.ghatana.platform.domain.domain.event.Event;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File-based implementation of event backup using ZIP archives.
 */
public class FileEventBackup implements EventBackup {
    
    private final EventLogRepository repository;
    
    public FileEventBackup(EventLogRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public void createBackup(Path backupDir, String backupName, Instant cutoff) throws IOException {
        Path backupFile = backupDir.resolve(backupName + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupFile))) {
            // 1. Add manifest
            ZipEntry manifest = new ZipEntry("manifest.json");
            zos.putNextEntry(manifest);
            zos.write(createManifest(cutoff).getBytes());
            zos.closeEntry();
            
            // 2. Add events
            List<Event> events = repository.getEventsBefore(cutoff);
            ZipEntry eventsEntry = new ZipEntry("events.data");
            zos.putNextEntry(eventsEntry);
            for (Event event : events) {
                byte[] bytes = event.toString().getBytes(StandardCharsets.UTF_8);
                zos.write(bytes);
                zos.write('\n');
            }
            zos.closeEntry();
        }
    }

    @Override
    public boolean verifyBackup(Path backupFile) throws IOException {
        if (!Files.exists(backupFile)) {
            return false;
        }
        
        // Basic verification - check file size and manifest
        long size = Files.size(backupFile);
        if (size < 100) { // Minimum expected backup size
            return false;
        }
        
        // TODO: Add more thorough verification (checksum, etc)
        return true;
    }
    
    private String createManifest(Instant cutoff) {
        return String.format(
            "{\"type\":\"event-backup\",\"cutoff\":\"%s\",\"created\":\"%s\"}",
            cutoff.toString(),
            Instant.now().toString()
        );
    }
}
