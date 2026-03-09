package com.ghatana.yappc.storage;

import com.ghatana.yappc.domain.PhaseType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Manages versioned artifact storage in data-cloud
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class YappcArtifactRepository {
    
    private static final Logger log = LoggerFactory.getLogger(YappcArtifactRepository.class);
    
    private final InMemoryArtifactStore store;
    
    /**
     * Constructor with artifact store.
     * 
     * @param store Artifact store implementation
     */
    public YappcArtifactRepository(InMemoryArtifactStore store) {
        this.store = store;
    }
    
    /**
     * Default constructor with in-memory store.
     */
    public YappcArtifactRepository() {
        this(new InMemoryArtifactStore());
    }
    
    /**
     * Stores an artifact for a specific product and phase.
     * 
     * @param productId Product identifier
     * @param phase Phase type
     * @param content Artifact content
     * @return Promise of version identifier
     */
    public Promise<String> storeArtifact(String productId, PhaseType phase, byte[] content) {
        String path = String.format("products/%s/phases/%s", 
                productId, phase.name().toLowerCase());
        
        log.info("Storing artifact: {}", path);
        
        return store.put(path, content);
    }
    
    /**
     * Retrieves an artifact by product, phase, and version.
     * 
     * @param productId Product identifier
     * @param phase Phase type
     * @param version Version identifier
     * @return Promise of artifact content
     */
    public Promise<byte[]> getArtifact(String productId, PhaseType phase, String version) {
        String path = String.format("products/%s/phases/%s/%s", 
                productId, phase.name().toLowerCase(), version);
        
        log.info("Retrieving artifact: {}", path);
        
        return store.get(path);
    }
    
    /**
     * Lists all versions of an artifact.
     * 
     * @param productId Product identifier
     * @param phase Phase type
     * @return Promise of version list
     */
    public Promise<java.util.List<String>> listVersions(String productId, PhaseType phase) {
        String prefix = String.format("products/%s/phases/%s/", 
                productId, phase.name().toLowerCase());
        
        log.info("Listing versions for: {}", prefix);
        
        return store.list(prefix);
    }
    
    /**
     * Stores artifact metadata.
     * 
     * @param productId Product identifier
     * @param phase Phase type
     * @param version Version identifier
     * @param metadata Metadata to store
     * @return Promise of completion
     */
    public Promise<Void> storeMetadata(String productId, PhaseType phase, String version, 
                                       Map<String, String> metadata) {
        String path = String.format("products/%s/phases/%s/%s/metadata", 
                productId, phase.name().toLowerCase(), version);
        
        log.info("Storing metadata: {}", path);
        
        return store.putMetadata(path, metadata);
    }
}
