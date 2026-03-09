package com.ghatana.yappc.services.generate;

import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Produces concrete artifacts from validated specs
 * @doc.layer service
 * @doc.pattern Service
 */
public interface GenerationService {
    /**
     * Generates all artifacts from validated specification.
     * 
     * @param spec The validated specification
     * @return Promise of generated artifacts
     */
    Promise<GeneratedArtifacts> generate(ValidatedSpec spec);
    
    /**
     * Regenerates artifacts with diff to show changes.
     * 
     * @param spec The validated specification
     * @param existing Existing artifacts to diff against
     * @return Promise of diff result
     */
    Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing);
}
