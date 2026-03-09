package com.ghatana.yappc.storage;

import com.ghatana.yappc.domain.PhaseType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose In-memory artifact store implementation for development/testing
 * @doc.layer infrastructure
 * @doc.pattern Repository
 * 
 * Production deployment should replace this with actual data-cloud integration.
 */
public class InMemoryArtifactStore {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryArtifactStore.class);
    
    private final Map<String, byte[]> artifacts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> metadata = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(System.currentTimeMillis());
    
    /**
     * Stores an artifact.
     * 
     * @param path Artifact path
     * @param content Artifact content
     * @return Promise of version identifier
     */
    public Promise<String> put(String path, byte[] content) {
        String version = "v-" + versionCounter.incrementAndGet();
        String versionedPath = path + "/" + version;
        
        artifacts.put(versionedPath, content);
        log.info("Stored artifact: {} (size: {} bytes)", versionedPath, content.length);
        
        return Promise.of(version);
    }
    
    /**
     * Retrieves an artifact.
     * 
     * @param path Artifact path (including version)
     * @return Promise of artifact content
     */
    public Promise<byte[]> get(String path) {
        byte[] content = artifacts.get(path);
        
        if (content == null) {
            log.warn("Artifact not found: {}", path);
            return Promise.ofException(new RuntimeException("Artifact not found: " + path));
        }
        
        log.info("Retrieved artifact: {} (size: {} bytes)", path, content.length);
        return Promise.of(content);
    }
    
    /**
     * Lists all versions for a path prefix.
     * 
     * @param prefix Path prefix
     * @return Promise of version list
     */
    public Promise<List<String>> list(String prefix) {
        List<String> versions = artifacts.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(key.lastIndexOf('/') + 1))
                .distinct()
                .collect(Collectors.toList());
        
        log.info("Listed {} versions for prefix: {}", versions.size(), prefix);
        return Promise.of(versions);
    }
    
    /**
     * Stores metadata for an artifact.
     * 
     * @param path Artifact path
     * @param meta Metadata map
     * @return Promise of completion
     */
    public Promise<Void> putMetadata(String path, Map<String, String> meta) {
        metadata.put(path, Map.copyOf(meta));
        log.info("Stored metadata for: {}", path);
        return Promise.complete();
    }
    
    /**
     * Retrieves metadata for an artifact.
     * 
     * @param path Artifact path
     * @return Promise of metadata map
     */
    public Promise<Map<String, String>> getMetadata(String path) {
        Map<String, String> meta = metadata.get(path);
        
        if (meta == null) {
            return Promise.of(Map.of());
        }
        
        return Promise.of(meta);
    }
    
    /**
     * Clears all stored artifacts (for testing).
     */
    public void clear() {
        artifacts.clear();
        metadata.clear();
        log.info("Cleared all artifacts");
    }
    
    /**
     * Gets the number of stored artifacts.
     * 
     * @return Artifact count
     */
    public int size() {
        return artifacts.size();
    }
}
