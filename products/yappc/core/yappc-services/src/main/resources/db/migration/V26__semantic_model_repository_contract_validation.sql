-- V26__semantic_model_repository_contract_validation.sql
-- P0: Validates that the semantic_models table schema matches the SemanticModelRepository contract
-- This migration ensures all required columns exist for the repository to function correctly
-- If this migration fails, it indicates a schema mismatch that must be fixed before running the application

-- Create a function to validate the semantic_models schema
CREATE OR REPLACE FUNCTION validate_semantic_models_schema() RETURNS VOID AS $$
DECLARE
    missing_columns TEXT[];
    required_columns TEXT[] := ARRAY[
        'id', 'element_id', 'element_type', 'name', 'qualified_name', 'file_path',
        'source_location_json', 'properties_json', 'dependencies_json', 'dependents_json',
        'confidence', 'review_required', 'review_reason', 'security_flags', 'privacy_flags',
        'graph_node_ids', 'residual_island_ids', 'source_ref', 'symbol_ref',
        'extractor_id', 'extractor_version', 'model_version_id', 'synthetic_reason',
        'provenance', 'extracted_at', 'snapshot_id', 'tenant_id', 'workspace_id', 'project_id'
    ];
    col RECORD;
BEGIN
    -- Check each required column
    FOREACH col IN ARRAY required_columns
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'semantic_models' 
            AND column_name = col
        ) THEN
            missing_columns := array_append(missing_columns, col);
        END IF;
    END LOOP;

    -- If any columns are missing, raise an exception
    IF array_length(missing_columns, 1) > 0 THEN
        RAISE EXCEPTION 'semantic_models table schema validation failed. Missing required columns: %', 
            array_to_string(missing_columns, ', ');
    END IF;
    
    RAISE NOTICE 'semantic_models table schema validation passed. All required columns present.';
END;
$$ LANGUAGE plpgsql;

-- Run the validation function
SELECT validate_semantic_models_schema();

-- Add comment to document the contract validation
COMMENT ON FUNCTION validate_semantic_models_schema() IS 
    'P0: Validates that the semantic_models table has all columns required by SemanticModelRepository. 
     Called during migration to detect schema mismatches early.';
