-- Content Studio Database Schema Enhancement
-- Phase 1.2: Enhanced Database Schema for Evidence-Based Content Generation

-- The current schema already includes most of the required tables:
-- ✅ LearningExperience - Core experience model
-- ✅ LearningClaim - Claims with contentNeeds JSON field
-- ✅ ClaimExample - Per-claim examples
-- ✅ ClaimSimulation - Per-claim simulations  
-- ✅ ClaimAnimation - Per-claim animations

-- However, we need to add a dedicated ContentNeeds table for better tracking
-- and add some missing enums and fields for complete functionality.

-- Add missing enums for content types and validation
CREATE TYPE content_example_type AS ENUM (
    'REAL_WORLD',
    'PROBLEM_SOLVING', 
    'ANALOGY',
    'CASE_STUDY',
    'CONCEPTUAL',
    'PROCEDURAL'
);

CREATE TYPE content_simulation_type AS ENUM (
    'PARAMETER_EXPLORATION',
    'PREDICTION',
    'CONSTRUCTION',
    'EXPERIMENT',
    'MODELING',
    'ANALYSIS'
);

CREATE TYPE content_animation_type AS ENUM (
    '2D',
    '3D', 
    'TIMELINE',
    'PROCESS_FLOW',
    'INTERACTIVE',
    'VISUALIZATION'
);

CREATE TYPE content_difficulty AS ENUM (
    'BEGINNER',
    'INTERMEDIATE',
    'ADVANCED',
    'EXPERT'
);

CREATE TYPE validation_pillar AS ENUM (
    'EDUCATIONAL',
    'EXPERIENTIAL',
    'SAFETY',
    'TECHNICAL',
    'ACCESSIBILITY'
);

-- Add ContentNeeds table for better tracking and analysis
CREATE TABLE content_needs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id UUID NOT NULL,
    experience_id UUID NOT NULL,
    
    -- Content requirements
    needs_examples BOOLEAN DEFAULT FALSE,
    needs_simulation BOOLEAN DEFAULT FALSE,
    needs_animation BOOLEAN DEFAULT FALSE,
    
    -- Specific requirements
    example_count INTEGER DEFAULT 0,
    example_types content_example_type[],
    simulation_type content_simulation_type,
    animation_type content_animation_type,
    
    -- Quality requirements
    difficulty_level content_difficulty DEFAULT 'INTERMEDIATE',
    estimated_time_minutes INTEGER DEFAULT 0,
    
    -- Metadata
    confidence_score DECIMAL(3,2) DEFAULT 0.0,
    priority_level INTEGER DEFAULT 1,
    auto_generated BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    analyzed_at TIMESTAMP DEFAULT NOW(),
    
    -- Foreign keys
    FOREIGN KEY (claim_id) REFERENCES learning_claims(id) ON DELETE CASCADE,
    FOREIGN KEY (experience_id) REFERENCES learning_experiences(id) ON DELETE CASCADE,
    
    -- Constraints
    UNIQUE(claim_id),
    CHECK (example_count >= 0 AND example_count <= 10),
    CHECK (estimated_time_minutes >= 0),
    CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0)
);

-- Add indexes for performance
CREATE INDEX idx_content_needs_claim_id ON content_needs(claim_id);
CREATE INDEX idx_content_needs_experience_id ON content_needs(experience_id);
CREATE INDEX idx_content_needs_priority ON content_needs(priority_level DESC);
CREATE INDEX idx_content_needs_auto_generated ON content_needs(auto_generated);

-- Add content generation tracking table
CREATE TABLE content_generation_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experience_id UUID NOT NULL,
    claim_id UUID,
    
    -- Job details
    job_type VARCHAR(50) NOT NULL, -- 'EXAMPLES', 'SIMULATION', 'ANIMATION', 'ALL'
    status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    priority INTEGER DEFAULT 1,
    
    -- Generation parameters
    parameters JSONB, -- Generation configuration
    model_used VARCHAR(100),
    prompt_hash VARCHAR(64),
    
    -- Results
    results JSONB, -- Generated content
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    
    -- Metrics
    tokens_used INTEGER DEFAULT 0,
    processing_time_ms INTEGER DEFAULT 0,
    confidence_score DECIMAL(3,2),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Foreign keys
    FOREIGN KEY (experience_id) REFERENCES learning_experiences(id) ON DELETE CASCADE,
    FOREIGN KEY (claim_id) REFERENCES learning_claims(id) ON DELETE SET NULL,
    
    -- Constraints
    CHECK (retry_count >= 0),
    CHECK (processing_time_ms >= 0),
    CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0)
);

-- Add indexes for content generation jobs
CREATE INDEX idx_content_generation_jobs_experience_id ON content_generation_jobs(experience_id);
CREATE INDEX idx_content_generation_jobs_status ON content_generation_jobs(status);
CREATE INDEX idx_content_generation_jobs_created_at ON content_generation_jobs(created_at DESC);
CREATE INDEX idx_content_generation_jobs_priority ON content_generation_jobs(priority DESC);

-- Add content quality metrics table
CREATE TABLE content_quality_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experience_id UUID NOT NULL,
    claim_id UUID,
    content_type VARCHAR(20) NOT NULL, -- 'CLAIM', 'EXAMPLE', 'SIMULATION', 'ANIMATION'
    content_id UUID NOT NULL,
    
    -- Quality scores (0-100)
    educational_quality INTEGER DEFAULT 0,
    engagement_quality INTEGER DEFAULT 0,
    clarity_quality INTEGER DEFAULT 0,
    accuracy_quality INTEGER DEFAULT 0,
    completeness_quality INTEGER DEFAULT 0,
    overall_quality INTEGER DEFAULT 0,
    
    -- Validation results
    validation_status VARCHAR(20) DEFAULT 'PENDING',
    validation_errors JSONB,
    validation_warnings JSONB,
    
    -- User feedback
    user_rating INTEGER CHECK (user_rating >= 1 AND user_rating <= 5),
    user_feedback TEXT,
    
    -- AI analysis
    ai_confidence DECIMAL(3,2),
    ai_suggestions JSONB,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_validated_at TIMESTAMP,
    
    -- Foreign keys
    FOREIGN KEY (experience_id) REFERENCES learning_experiences(id) ON DELETE CASCADE,
    FOREIGN KEY (claim_id) REFERENCES learning_claims(id) ON DELETE SET NULL,
    
    -- Constraints
    CHECK (educational_quality >= 0 AND educational_quality <= 100),
    CHECK (engagement_quality >= 0 AND engagement_quality <= 100),
    CHECK (clarity_quality >= 0 AND clarity_quality <= 100),
    CHECK (accuracy_quality >= 0 AND accuracy_quality <= 100),
    CHECK (completeness_quality >= 0 AND completeness_quality <= 100),
    CHECK (overall_quality >= 0 AND overall_quality <= 100),
    CHECK (ai_confidence >= 0.0 AND ai_confidence <= 1.0)
);

-- Add indexes for content quality metrics
CREATE INDEX idx_content_quality_metrics_experience_id ON content_quality_metrics(experience_id);
CREATE INDEX idx_content_quality_metrics_content_id ON content_quality_metrics(content_id);
CREATE INDEX idx_content_quality_metrics_overall_quality ON content_quality_metrics(overall_quality DESC);
CREATE INDEX idx_content_quality_metrics_validation_status ON content_quality_metrics(validation_status);

-- Add enhanced validation results table
CREATE TABLE enhanced_validation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experience_id UUID NOT NULL,
    claim_id UUID,
    
    -- Validation metadata
    validation_type VARCHAR(50) NOT NULL, -- 'FULL', 'EDUCATIONAL', 'EXPERIENTIAL', etc.
    validation_version VARCHAR(20) DEFAULT '1.0',
    model_used VARCHAR(100),
    
    -- Overall results
    is_valid BOOLEAN DEFAULT FALSE,
    overall_score INTEGER DEFAULT 0,
    can_publish BOOLEAN DEFAULT FALSE,
    confidence_score DECIMAL(3,2),
    
    -- Pillar scores
    educational_score INTEGER DEFAULT 0,
    experiential_score INTEGER DEFAULT 0,
    safety_score INTEGER DEFAULT 0,
    technical_score INTEGER DEFAULT 0,
    accessibility_score INTEGER DEFAULT 0,
    
    -- Issues and suggestions
    issues JSONB, -- Array of validation issues
    suggestions JSONB, -- Array of improvement suggestions
    auto_fixes JSONB, -- Automatically applied fixes
    
    -- Processing metrics
    processing_time_ms INTEGER DEFAULT 0,
    tokens_analyzed INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP DEFAULT (NOW() + INTERVAL '30 days'),
    
    -- Foreign keys
    FOREIGN KEY (experience_id) REFERENCES learning_experiences(id) ON DELETE CASCADE,
    FOREIGN KEY (claim_id) REFERENCES learning_claims(id) ON DELETE SET NULL,
    
    -- Constraints
    CHECK (overall_score >= 0 AND overall_score <= 100),
    CHECK (educational_score >= 0 AND educational_score <= 100),
    CHECK (experiential_score >= 0 AND experiential_score <= 100),
    CHECK (safety_score >= 0 AND safety_score <= 100),
    CHECK (technical_score >= 0 AND technical_score <= 100),
    CHECK (accessibility_score >= 0 AND accessibility_score <= 100),
    CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0),
    CHECK (processing_time_ms >= 0),
    CHECK (tokens_analyzed >= 0)
);

-- Add indexes for enhanced validation results
CREATE INDEX idx_enhanced_validation_experience_id ON enhanced_validation_results(experience_id);
CREATE INDEX idx_enhanced_validation_claim_id ON enhanced_validation_results(claim_id);
CREATE INDEX idx_enhanced_validation_overall_score ON enhanced_validation_results(overall_score DESC);
CREATE INDEX idx_enhanced_validation_created_at ON enhanced_validation_results(created_at DESC);
CREATE INDEX idx_enhanced_validation_expires_at ON enhanced_validation_results(expires_at);

-- Add content enhancement tracking table
CREATE TABLE content_enhancements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experience_id UUID NOT NULL,
    claim_id UUID,
    
    -- Enhancement details
    enhancement_type VARCHAR(50) NOT NULL, -- 'SUGGESTIONS', 'GRADE_ADAPTATION', 'ACCESSIBILITY'
    target_grade_level VARCHAR(20),
    
    -- Enhancement data
    original_content JSONB,
    enhanced_content JSONB,
    suggestions JSONB,
    
    -- Quality metrics
    improvement_score DECIMAL(3,2), -- How much improvement was achieved
    confidence_score DECIMAL(3,2),
    
    -- Processing info
    model_used VARCHAR(100),
    processing_time_ms INTEGER DEFAULT 0,
    tokens_generated INTEGER DEFAULT 0,
    
    -- Status
    status VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'APPLIED', 'REJECTED'
    applied_by VARCHAR(100),
    applied_at TIMESTAMP,
    rejection_reason TEXT,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- Foreign keys
    FOREIGN KEY (experience_id) REFERENCES learning_experiences(id) ON DELETE CASCADE,
    FOREIGN KEY (claim_id) REFERENCES learning_claims(id) ON DELETE SET NULL,
    
    -- Constraints
    CHECK (improvement_score >= 0.0 AND improvement_score <= 1.0),
    CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0),
    CHECK (processing_time_ms >= 0),
    CHECK (tokens_generated >= 0)
);

-- Add indexes for content enhancements
CREATE INDEX idx_content_enhancements_experience_id ON content_enhancements(experience_id);
CREATE INDEX idx_content_enhancements_claim_id ON content_enhancements(claim_id);
CREATE INDEX idx_content_enhancements_type ON content_enhancements(enhancement_type);
CREATE INDEX idx_content_enhancements_status ON content_enhancements(status);
CREATE INDEX idx_content_enhancements_improvement ON content_enhancements(improvement_score DESC);

-- Add triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to tables with updated_at
CREATE TRIGGER update_content_needs_updated_at 
    BEFORE UPDATE ON content_needs 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_content_quality_metrics_updated_at 
    BEFORE UPDATE ON content_quality_metrics 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_content_enhancements_updated_at 
    BEFORE UPDATE ON content_enhancements 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add views for common queries
CREATE VIEW content_needs_summary AS
SELECT 
    cn.experience_id,
    cn.claim_id,
    COUNT(*) FILTER (WHERE cn.needs_examples) as examples_needed,
    COUNT(*) FILTER (WHERE cn.needs_simulation) as simulations_needed,
    COUNT(*) FILTER (WHERE cn.needs_animation) as animations_needed,
    AVG(cn.confidence_score) as avg_confidence,
    MAX(cn.priority_level) as max_priority
FROM content_needs cn
GROUP BY cn.experience_id, cn.claim_id;

CREATE VIEW experience_content_status AS
SELECT 
    le.id as experience_id,
    le.title,
    COUNT(DISTINCT lc.id) as total_claims,
    COUNT(DISTINCT ce.id) as total_examples,
    COUNT(DISTINCT cs.id) as total_simulations,
    COUNT(DISTINCT ca.id) as total_animations,
    AVG(cqm.overall_quality) as avg_quality_score,
    MAX(evr.overall_score) as latest_validation_score,
    le.status as experience_status,
    le.updated_at
FROM learning_experiences le
LEFT JOIN learning_claims lc ON le.id = lc.experience_id
LEFT JOIN claim_examples ce ON lc.id = ce.claim_id
LEFT JOIN claim_simulations cs ON lc.id = cs.claim_id  
LEFT JOIN claim_animations ca ON lc.id = ca.claim_id
LEFT JOIN content_quality_metrics cqm ON le.id = cqm.experience_id
LEFT JOIN enhanced_validation_results evr ON le.id = evr.experience_id
GROUP BY le.id, le.title, le.status, le.updated_at;

-- Add comments for documentation
COMMENT ON TABLE content_needs IS 'Tracks content generation needs for each claim';
COMMENT ON TABLE content_generation_jobs IS 'Tracks background content generation jobs';
COMMENT ON TABLE content_quality_metrics IS 'Stores quality metrics for generated content';
COMMENT ON TABLE enhanced_validation_results IS 'Enhanced validation results with pillar scoring';
COMMENT ON TABLE content_enhancements IS 'Tracks content enhancements and improvements';

COMMENT ON VIEW content_needs_summary IS 'Summary of content needs by experience and claim';
COMMENT ON VIEW experience_content_status IS 'Overview of content status for each experience';
