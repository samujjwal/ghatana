-- 0005_incident_summaries.sql
-- Implements storage for LLM-generated incident summaries

-- Incident summaries table for storing LLM-generated analysis
CREATE TABLE IF NOT EXISTS incident_summaries (
    summary_id String DEFAULT generateUUIDv4() CODEC(ZSTD(1)),
    incident_id String CODEC(ZSTD(1)),
    created_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    updated_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    
    -- Summary content
    summary String CODEC(ZSTD(1)),
    timeline String CODEC(ZSTD(1)), -- JSON array of timeline events
    root_cause String CODEC(ZSTD(1)),
    impact String CODEC(ZSTD(1)),
    recommendations String CODEC(ZSTD(1)), -- JSON array of recommendations
    
    -- Generation metadata
    confidence Float64 CODEC(Gorilla, ZSTD(1)),
    generated_at DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    model String CODEC(ZSTD(1)),
    tokens_used UInt32 CODEC(ZSTD(1)),
    generation_time_ms UInt32 CODEC(ZSTD(1)),
    
    -- Classification and status
    summary_version UInt8 DEFAULT 1 CODEC(ZSTD(1)),
    status Enum8('draft' = 1, 'final' = 2, 'archived' = 3) DEFAULT 'final' CODEC(ZSTD(1)),
    quality_score Float64 CODEC(Gorilla, ZSTD(1)),
    human_reviewed Bool DEFAULT false CODEC(ZSTD(1)),
    reviewed_by String CODEC(ZSTD(1)),
    reviewed_at Nullable(DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    
    -- Additional metadata
    labels String CODEC(ZSTD(1)), -- JSON map of labels
    
    -- Indexes for efficient querying
    INDEX idx_incident_id incident_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_created_at created_at TYPE minmax GRANULARITY 3,
    INDEX idx_generated_at generated_at TYPE minmax GRANULARITY 3,
    INDEX idx_model model TYPE bloom_filter GRANULARITY 3,
    INDEX idx_status status TYPE bloom_filter GRANULARITY 3,
    INDEX idx_confidence confidence TYPE minmax GRANULARITY 3,
    INDEX idx_quality_score quality_score TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(created_at)
ORDER BY (incident_id, created_at, summary_version);

-- Summary feedback table for quality tracking and model improvement
CREATE TABLE IF NOT EXISTS summary_feedback (
    feedback_id String DEFAULT generateUUIDv4() CODEC(ZSTD(1)),
    summary_id String CODEC(ZSTD(1)),
    incident_id String CODEC(ZSTD(1)),
    created_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    
    -- Feedback details
    rating Enum8('poor' = 1, 'fair' = 2, 'good' = 3, 'excellent' = 4) CODEC(ZSTD(1)),
    accuracy_score Float64 CODEC(Gorilla, ZSTD(1)), -- How accurate was the analysis?
    completeness_score Float64 CODEC(Gorilla, ZSTD(1)), -- How complete was the summary?
    usefulness_score Float64 CODEC(Gorilla, ZSTD(1)), -- How useful for operations?
    
    -- Specific feedback
    feedback_text String CODEC(ZSTD(1)),
    suggested_improvements String CODEC(ZSTD(1)),
    
    -- Reviewer information
    reviewer_id String CODEC(ZSTD(1)),
    reviewer_role String CODEC(ZSTD(1)),
    
    -- Categories of issues (if any)
    has_factual_errors Bool DEFAULT false CODEC(ZSTD(1)),
    has_missing_context Bool DEFAULT false CODEC(ZSTD(1)),
    has_unclear_language Bool DEFAULT false CODEC(ZSTD(1)),
    has_wrong_priorities Bool DEFAULT false CODEC(ZSTD(1)),
    
    -- Indexes
    INDEX idx_summary_id summary_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_incident_id incident_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_created_at created_at TYPE minmax GRANULARITY 3,
    INDEX idx_rating rating TYPE bloom_filter GRANULARITY 3,
    INDEX idx_reviewer_id reviewer_id TYPE bloom_filter GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(created_at)
ORDER BY (summary_id, created_at);

-- View for summary analytics and quality metrics
CREATE VIEW IF NOT EXISTS summary_quality_metrics AS
SELECT 
    toStartOfDay(s.created_at) as date,
    s.model,
    count() as total_summaries,
    avg(s.confidence) as avg_confidence,
    avg(s.quality_score) as avg_quality_score,
    avg(s.tokens_used) as avg_tokens_used,
    avg(s.generation_time_ms) as avg_generation_time_ms,
    
    -- Feedback metrics (if available)
    avgIf(f.accuracy_score, f.accuracy_score > 0) as avg_accuracy_feedback,
    avgIf(f.completeness_score, f.completeness_score > 0) as avg_completeness_feedback,
    avgIf(f.usefulness_score, f.usefulness_score > 0) as avg_usefulness_feedback,
    
    -- Quality indicators
    countIf(s.human_reviewed = true) as human_reviewed_count,
    countIf(f.has_factual_errors = true) as factual_errors_count,
    countIf(f.has_missing_context = true) as missing_context_count
    
FROM incident_summaries s
LEFT JOIN summary_feedback f ON s.summary_id = f.summary_id
GROUP BY date, s.model
ORDER BY date DESC, s.model;

-- Summary performance by incident type
CREATE VIEW IF NOT EXISTS summary_performance_by_type AS
SELECT 
    JSONExtractString(ci.labels, 'incident_type') as incident_type,
    ci.severity,
    count() as summary_count,
    avg(s.confidence) as avg_confidence,
    avg(s.generation_time_ms) as avg_generation_time,
    
    -- Quality metrics
    avgIf(f.accuracy_score, f.accuracy_score > 0) as avg_accuracy,
    avgIf(f.usefulness_score, f.usefulness_score > 0) as avg_usefulness
    
FROM incident_summaries s
LEFT JOIN correlated_incidents ci ON s.incident_id = ci.incident_id
LEFT JOIN summary_feedback f ON s.summary_id = f.summary_id
GROUP BY incident_type, ci.severity
ORDER BY summary_count DESC;