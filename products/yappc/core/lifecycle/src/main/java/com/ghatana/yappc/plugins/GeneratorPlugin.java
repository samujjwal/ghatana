package com.ghatana.yappc.plugins;

import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import io.activej.promise.Promise;

import java.util.List;

/**
 * @doc.type interface
 * @doc.purpose Plugin interface for custom artifact generators
 * @doc.layer plugin
 * @doc.pattern SPI
 */
public interface GeneratorPlugin extends YappcPlugin {
    
    /**
     * Gets the generator ID.
     * 
     * @return Unique generator identifier
     */
    String getGeneratorId();
    
    /**
     * Gets the artifact types this generator produces.
     * 
     * @return Array of artifact type names
     */
    String[] getArtifactTypes();
    
    /**
     * Gets the programming languages this generator supports.
     * 
     * @return Array of language names
     */
    String[] getSupportedLanguages();
    
    /**
     * Generates artifacts from a validated specification.
     * 
     * @param spec Validated specification
     * @return Promise of generated artifacts
     */
    Promise<List<Artifact>> generate(ValidatedSpec spec);
    
    /**
     * Checks if this generator can handle the given spec.
     * 
     * @param spec Validated specification
     * @return true if can generate, false otherwise
     */
    boolean canGenerate(ValidatedSpec spec);
}
