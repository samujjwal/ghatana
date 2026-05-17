-- V24__promote_semantic_model_governance_fields.sql
-- P0: Promote governance fields from properties_json reserved keys to first-class columns
-- This enables queryability, indexing, validation, and governance for semantic models

-- Add governance fields as first-class columns
ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS review_required BOOLEAN DEFAULT FALSE;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS review_reason TEXT;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS security_flags JSONB;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS privacy_flags JSONB;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS graph_node_ids JSONB;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS residual_island_ids JSONB;

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS source_ref VARCHAR(255);

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS symbol_ref VARCHAR(255);

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS extractor_id VARCHAR(255);

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS extractor_version VARCHAR(255);

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS model_version_id VARCHAR(255);

ALTER TABLE semantic_models
ADD COLUMN IF NOT EXISTS synthetic_reason TEXT;

-- Migrate data from properties_json reserved __ keys to first-class columns
UPDATE semantic_models
SET confidence = (properties_json->>'__confidence')::DOUBLE PRECISION
WHERE confidence IS NULL AND properties_json ? '__confidence';

UPDATE semantic_models
SET review_required = (properties_json->>'__reviewRequired')::BOOLEAN
WHERE review_required IS NULL AND properties_json ? '__reviewRequired';

UPDATE semantic_models
SET review_reason = properties_json->>'__reviewReason'
WHERE review_reason IS NULL AND properties_json ? '__reviewReason';

UPDATE semantic_models
SET security_flags = properties_json->'__securityFlags'
WHERE security_flags IS NULL AND properties_json ? '__securityFlags';

UPDATE semantic_models
SET privacy_flags = properties_json->'__privacyFlags'
WHERE privacy_flags IS NULL AND properties_json ? '__privacyFlags';

UPDATE semantic_models
SET graph_node_ids = properties_json->'__graphNodeIds'
WHERE graph_node_ids IS NULL AND properties_json ? '__graphNodeIds';

UPDATE semantic_models
SET residual_island_ids = properties_json->'__residualIslandIds'
WHERE residual_island_ids IS NULL AND properties_json ? '__residualIslandIds';

UPDATE semantic_models
SET source_ref = properties_json->>'__sourceRef'
WHERE source_ref IS NULL AND properties_json ? '__sourceRef';

UPDATE semantic_models
SET symbol_ref = properties_json->>'__symbolRef'
WHERE symbol_ref IS NULL AND properties_json ? '__symbolRef';

UPDATE semantic_models
SET extractor_id = properties_json->>'__extractorId'
WHERE extractor_id IS NULL AND properties_json ? '__extractorId';

UPDATE semantic_models
SET extractor_version = properties_json->>'__extractorVersion'
WHERE extractor_version IS NULL AND properties_json ? '__extractorVersion';

UPDATE semantic_models
SET model_version_id = properties_json->>'__modelVersionId'
WHERE model_version_id IS NULL AND properties_json ? '__modelVersionId';

UPDATE semantic_models
SET synthetic_reason = properties_json->>'__syntheticReason'
WHERE synthetic_reason IS NULL AND properties_json ? '__syntheticReason';

-- Create indexes for governance fields to enable efficient queries
CREATE INDEX IF NOT EXISTS idx_semantic_models_confidence
ON semantic_models(confidence) WHERE confidence IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_models_review_required
ON semantic_models(review_required) WHERE review_required = TRUE;

CREATE INDEX IF NOT EXISTS idx_semantic_models_source_ref
ON semantic_models(source_ref) WHERE source_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_models_symbol_ref
ON semantic_models(symbol_ref) WHERE symbol_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_models_extractor_id
ON semantic_models(extractor_id) WHERE extractor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_models_graph_node_ids
ON semantic_models USING GIN (graph_node_ids) WHERE graph_node_ids IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_models_residual_island_ids
ON semantic_models USING GIN (residual_island_ids) WHERE residual_island_ids IS NOT NULL;
