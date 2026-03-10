-- Content Studio Phase 4 Migration
-- Purpose: Add validation gates, versioned drafts, and lifecycle enhancements
-- Date: 2025-01-22

-- 1. Add optimistic concurrency/version fields to existing tables
ALTER TABLE LearningExperience ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE LearningExperience ADD COLUMN lastEditedBy TEXT;
ALTER TABLE SimulationManifest ADD COLUMN version INTEGER DEFAULT 1;

-- 2. Add lifecycle status enums (if not exists)
-- Note: These should already exist in schema; ensure they're properly defined
-- SimulationManifestStatus: DRAFT, REVIEW, ACTIVE, DEPRECATED
-- ModuleStatus: DRAFT, PUBLISHED, ARCHIVED (already exists)

-- 3. Add draft/autosave tracking table
CREATE TABLE IF NOT EXISTS ExperienceDraft (
    id TEXT PRIMARY KEY,
    experienceId TEXT NOT NULL,
    tenantId TEXT NOT NULL,
    userId TEXT NOT NULL,
    phase TEXT NOT NULL CHECK (phase IN ('planning', 'creation', 'design', 'polish', 'review')),
    draftData JSON NOT NULL,
    etag TEXT NOT NULL, -- For optimistic concurrency
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (experienceId) REFERENCES LearningExperience(id) ON DELETE CASCADE,
    UNIQUE(experienceId, phase, userId)
);

-- 4. Add template usage tracking (if not already covered by SimulationTemplate.statsUses)
CREATE TABLE IF NOT EXISTS TemplateUsageLog (
    id TEXT PRIMARY KEY,
    tenantId TEXT NOT NULL,
    templateId TEXT NOT NULL,
    userId TEXT NOT NULL,
    experienceId TEXT,
    action TEXT NOT NULL CHECK (action IN ('viewed', 'cloned', 'customized')),
    metadata JSON, -- e.g., customization parameters
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (templateId) REFERENCES SimulationTemplate(id) ON DELETE CASCADE,
    FOREIGN KEY (experienceId) REFERENCES LearningExperience(id) ON DELETE SET NULL
);

-- 5. Add AI generation cost tracking
CREATE TABLE IF NOT EXISTS AIGenerationLog (
    id TEXT PRIMARY KEY,
    tenantId TEXT NOT NULL,
    userId TEXT NOT NULL,
    experienceId TEXT,
    provider TEXT NOT NULL, -- openai-primary, anthropic-backup, ollama-local
    model TEXT NOT NULL,
    promptTokens INTEGER,
    completionTokens INTEGER,
    totalTokens INTEGER,
    costCents INTEGER, -- Calculated cost in cents
    latencyMs INTEGER,
    status TEXT NOT NULL CHECK (status IN ('success', 'failure', 'retry')),
    errorMessage TEXT,
    cacheHit BOOLEAN DEFAULT FALSE,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (experienceId) REFERENCES LearningExperience(id) ON DELETE SET NULL
);

-- 6. Add validation job queue (for async heavy validation)
CREATE TABLE IF NOT EXISTS ValidationJob (
    id TEXT PRIMARY KEY,
    tenantId TEXT NOT NULL,
    experienceId TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('full', 'incremental', 'simulation_link')),
    priority INTEGER DEFAULT 0, -- Higher = more urgent
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'running', 'completed', 'failed')),
    payload JSON, -- Validation parameters
    result JSON, -- Validation result with errors/warnings
    error TEXT,
    startedAt DATETIME,
    completedAt DATETIME,
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (experienceId) REFERENCES LearningExperience(id) ON DELETE CASCADE
);

-- 7. Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_experience_draft_tenant_user ON ExperienceDraft(tenantId, userId);
CREATE INDEX IF NOT EXISTS idx_experience_draft_experience_phase ON ExperienceDraft(experienceId, phase);
CREATE INDEX IF NOT EXISTS idx_template_usage_tenant_template ON TemplateUsageLog(tenantId, templateId);
CREATE INDEX IF NOT EXISTS idx_ai_generation_tenant_user ON AIGenerationLog(tenantId, userId);
CREATE INDEX IF NOT EXISTS idx_ai_generation_experience ON AIGenerationLog(experienceId);
CREATE INDEX IF NOT EXISTS idx_ai_generation_status_created ON AIGenerationLog(status, createdAt);
CREATE INDEX IF NOT EXISTS idx_validation_job_status_priority ON ValidationJob(status, priority DESC);
CREATE INDEX IF NOT EXISTS idx_validation_job_experience ON ValidationJob(experienceId);

-- 8. Seed default templates (if not exists)
-- This will be handled by SimulationTemplateService.seedDefaultTemplates()

-- 9. Update existing records to set initial versions
UPDATE LearningExperience SET version = 1 WHERE version IS NULL;
UPDATE SimulationManifest SET version = 1 WHERE version IS NULL;

-- 10. Add triggers for updated timestamps (SQLite compatible)
-- Note: SQLite triggers are limited; these are basic implementations

CREATE TRIGGER IF NOT EXISTS update_experience_draft_updated
    AFTER UPDATE ON ExperienceDraft
    FOR EACH ROW
BEGIN
    UPDATE ExperienceDraft SET updatedAt = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_validation_job_started
    AFTER UPDATE ON ValidationJob
    WHEN NEW.status = 'running' AND OLD.status != 'running'
    FOR EACH ROW
BEGIN
    UPDATE ValidationJob SET startedAt = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_validation_job_completed
    AFTER UPDATE ON ValidationJob
    WHEN NEW.status IN ('completed', 'failed') AND OLD.status NOT IN ('completed', 'failed')
    FOR EACH ROW
BEGIN
    UPDATE ValidationJob SET completedAt = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
