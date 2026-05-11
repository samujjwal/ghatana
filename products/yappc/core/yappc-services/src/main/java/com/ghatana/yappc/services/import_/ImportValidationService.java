/**
 * Import Validation Service
 * 
 * Validates import sources before processing.
 * Ensures untrusted input is validated before mutation.
 * 
 * @doc.type interface
 * @doc.purpose Import validation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import com.ghatana.yappc.api.ImportController;

/**
 * Service interface for validating import sources.
 */
public interface ImportValidationService {

    /**
     * Validates an import source before processing.
     * 
     * @param request The import request to validate
     * @return ImportValidationResult containing validation status and any errors
     */
    ImportValidationResult validateImportSource(ImportController.ImportRequest request);

    /**
     * Validates source data before mutation.
     * 
     * @param sourceData The source data to validate
     * @param sourceType The source type
     * @return ImportValidationResult containing validation status and any errors
     */
    ImportValidationResult validateSourceData(String sourceData, String sourceType);
}
