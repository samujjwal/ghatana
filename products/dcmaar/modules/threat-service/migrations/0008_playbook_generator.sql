-- Migration 0008: Playbook Generator Tables
-- This migration creates comprehensive playbook management infrastructure
-- for storing and managing automated remediation playbooks generated from incidents

-- Playbooks: Main playbook storage table
CREATE TABLE IF NOT EXISTS playbooks (
    playbook_id String,
    incident_id String,
    title String,
    description String,
    severity Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4),
    category String,
    
    -- Generation metadata
    generated_at DateTime,
    generated_by String,
    model_version String,
    confidence_score Float64,
    
    -- Execution details
    estimated_duration_minutes UInt32,
    required_skills Array(String),
    prerequisites Array(String),
    
    -- Status and lifecycle
    status Enum8('draft' = 1, 'review' = 2, 'approved' = 3, 'active' = 4, 'deprecated' = 5, 'archived' = 6),
    approval_required Bool DEFAULT false,
    approved_by String,
    approved_at Nullable(DateTime),
    
    -- Execution tracking
    execution_count UInt32 DEFAULT 0,
    success_count UInt32 DEFAULT 0,
    failure_count UInt32 DEFAULT 0,
    last_executed Nullable(DateTime),
    success_rate Float64 DEFAULT 0.0,
    
    -- Safety and compliance
    risk_level Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4) DEFAULT 1,
    safety_warnings_count UInt32 DEFAULT 0,
    compliance_verified Bool DEFAULT false,
    
    -- Metadata
    tags Array(String),
    related_playbooks Array(String),
    version String DEFAULT '1.0.0',
    
    -- Context
    tenant_id String,
    environment String DEFAULT 'production',
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(created_at)
ORDER BY (playbook_id, tenant_id)
TTL created_at + INTERVAL 2 YEAR
SETTINGS index_granularity = 8192;

-- Playbook Steps: Individual remediation steps
CREATE TABLE IF NOT EXISTS playbook_steps (
    playbook_id String,
    step_id String,
    step_number UInt16,
    title String,
    description String,
    action_type Enum8('command' = 1, 'api_call' = 2, 'manual' = 3, 'script' = 4),
    
    -- Execution details
    command String,
    parameters String, -- JSON string
    expected_output String,
    timeout_seconds UInt32 DEFAULT 30,
    retry_count UInt8 DEFAULT 3,
    
    -- Dependencies and conditions
    depends_on Array(String),
    conditions String, -- JSON string of conditions
    skip_conditions String, -- JSON string of skip conditions
    
    -- Safety and validation
    risk_level Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4) DEFAULT 1,
    safety_checks_count UInt8 DEFAULT 0,
    validation_rules_count UInt8 DEFAULT 0,
    
    -- Automation flags
    automated Bool DEFAULT true,
    requires_human Bool DEFAULT false,
    
    -- Rollback information
    rollback_command String,
    rollback_notes String,
    
    -- Execution tracking
    execution_count UInt32 DEFAULT 0,
    success_count UInt32 DEFAULT 0,
    failure_count UInt32 DEFAULT 0,
    avg_execution_time_ms Float64 DEFAULT 0.0,
    
    -- Context
    tenant_id String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY playbook_id
ORDER BY (playbook_id, step_number, step_id)
SETTINGS index_granularity = 8192;

-- Validation Steps: Playbook validation procedures
CREATE TABLE IF NOT EXISTS playbook_validations (
    playbook_id String,
    validation_id String,
    title String,
    description String,
    check_type Enum8('health' = 1, 'performance' = 2, 'security' = 3, 'functional' = 4),
    
    -- Check details
    command String,
    expected_result String,
    timeout_seconds UInt32 DEFAULT 10,
    
    -- Execution flags
    automated Bool DEFAULT true,
    critical Bool DEFAULT false, -- If true, failure blocks execution
    
    -- Success/failure criteria
    success_criteria String, -- JSON string
    failure_criteria String, -- JSON string
    
    -- Execution tracking
    execution_count UInt32 DEFAULT 0,
    success_count UInt32 DEFAULT 0,
    failure_count UInt32 DEFAULT 0,
    
    -- Context
    tenant_id String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY playbook_id
ORDER BY (playbook_id, validation_id)
SETTINGS index_granularity = 8192;

-- Rollback Steps: Rollback procedures for playbooks
CREATE TABLE IF NOT EXISTS playbook_rollbacks (
    playbook_id String,
    rollback_id String,
    step_number UInt16,
    title String,
    description String,
    
    -- Rollback details
    command String,
    parameters String, -- JSON string
    timeout_seconds UInt32 DEFAULT 30,
    
    -- Conditions and triggers
    trigger_conditions String, -- JSON string
    safety_checks String, -- JSON string
    
    -- Risk assessment
    risk_level Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4) DEFAULT 1,
    requires_human Bool DEFAULT false,
    automated Bool DEFAULT false,
    
    -- Execution tracking
    execution_count UInt32 DEFAULT 0,
    success_count UInt32 DEFAULT 0,
    failure_count UInt32 DEFAULT 0,
    
    -- Context
    tenant_id String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY playbook_id
ORDER BY (playbook_id, step_number, rollback_id)
SETTINGS index_granularity = 8192;

-- Safety Warnings: Safety warnings and mitigations for playbooks
CREATE TABLE IF NOT EXISTS playbook_safety_warnings (
    playbook_id String,
    warning_id String,
    level Enum8('info' = 1, 'warning' = 2, 'critical' = 3),
    message String,
    mitigation String,
    
    -- Context
    step_id String, -- Associated step if warning is step-specific
    category String, -- Warning category
    acknowledged Bool DEFAULT false,
    acknowledged_by String,
    acknowledged_at Nullable(DateTime),
    
    -- Metadata
    tenant_id String,
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY playbook_id
ORDER BY (playbook_id, warning_id, level)
SETTINGS index_granularity = 8192;

-- Execution Records: Track playbook execution history
CREATE TABLE IF NOT EXISTS playbook_executions (
    execution_id String,
    playbook_id String,
    incident_id String,
    
    -- Execution details
    executed_by String,
    executed_at DateTime,
    completed_at Nullable(DateTime),
    status Enum8('running' = 1, 'completed' = 2, 'failed' = 3, 'cancelled' = 4, 'timeout' = 5),
    
    -- Progress tracking
    total_steps UInt16,
    completed_steps UInt16,
    failed_steps UInt16,
    skipped_steps UInt16,
    current_step UInt16 DEFAULT 0,
    
    -- Performance metrics
    execution_duration_ms UInt64,
    avg_step_duration_ms Float64,
    total_retry_count UInt32 DEFAULT 0,
    
    -- Results
    success_rate Float64,
    error_messages Array(String),
    warnings Array(String),
    notes String,
    
    -- Context
    environment String,
    executed_from_ip String,
    user_agent String,
    
    -- Metadata
    tenant_id String,
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(executed_at)
ORDER BY (playbook_id, executed_at, execution_id)
TTL executed_at + INTERVAL 1 YEAR
SETTINGS index_granularity = 8192;

-- Step Execution Details: Detailed execution logs for individual steps
CREATE TABLE IF NOT EXISTS step_executions (
    execution_id String,
    playbook_id String,
    step_id String,
    step_number UInt16,
    
    -- Execution details
    started_at DateTime,
    completed_at Nullable(DateTime),
    status Enum8('pending' = 1, 'running' = 2, 'completed' = 3, 'failed' = 4, 'skipped' = 5, 'timeout' = 6),
    
    -- Command execution
    command_executed String,
    parameters_used String, -- JSON string
    actual_output String,
    expected_output String,
    output_matched Bool,
    
    -- Performance
    execution_duration_ms UInt32,
    retry_count UInt8 DEFAULT 0,
    timeout_occurred Bool DEFAULT false,
    
    -- Results
    exit_code Nullable(Int32),
    error_message String,
    warning_message String,
    
    -- Safety and validation
    safety_checks_passed UInt8 DEFAULT 0,
    safety_checks_failed UInt8 DEFAULT 0,
    validation_passed Bool DEFAULT true,
    
    -- Context
    executed_by String,
    execution_node String, -- Which node/server executed this step
    
    -- Metadata
    tenant_id String,
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(started_at)
ORDER BY (execution_id, step_number, step_id)
TTL started_at + INTERVAL 6 MONTH
SETTINGS index_granularity = 8192;

-- Playbook Templates: Reusable templates for playbook generation
CREATE TABLE IF NOT EXISTS playbook_templates (
    template_id String,
    name String,
    category String,
    severity String,
    description String,
    
    -- Template content
    template_content String, -- The actual template text
    variables String, -- JSON string of template variables
    examples Array(String),
    
    -- Usage tracking
    usage_count UInt32 DEFAULT 0,
    success_rate Float64 DEFAULT 0.0,
    avg_confidence_score Float64 DEFAULT 0.0,
    
    -- Metadata
    version String DEFAULT '1.0.0',
    author String,
    tags Array(String),
    
    -- Lifecycle
    active Bool DEFAULT true,
    deprecated Bool DEFAULT false,
    replacement_template_id String,
    
    -- Context
    tenant_id String,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY category
ORDER BY (template_id, category, severity)
SETTINGS index_granularity = 8192;

-- Playbook References: External references and documentation links
CREATE TABLE IF NOT EXISTS playbook_references (
    playbook_id String,
    reference_id String,
    type Enum8('documentation' = 1, 'runbook' = 2, 'kb' = 3, 'ticket' = 4, 'sop' = 5),
    title String,
    url String,
    description String,
    
    -- Metadata
    tenant_id String,
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY playbook_id
ORDER BY (playbook_id, reference_id, type)
SETTINGS index_granularity = 8192;

-- Materialized Views for Analytics

-- Playbook Performance Dashboard: Real-time playbook performance metrics
CREATE MATERIALIZED VIEW IF NOT EXISTS playbook_performance_mv
TO playbook_performance_dashboard
AS SELECT
    playbook_id,
    tenant_id,
    environment,
    category,
    severity,
    count() as total_executions,
    countIf(status = 'completed') as successful_executions,
    countIf(status = 'failed') as failed_executions,
    avg(execution_duration_ms) as avg_execution_time_ms,
    max(executed_at) as last_executed,
    argMax(success_rate, executed_at) as current_success_rate,
    avg(completed_steps) / avg(total_steps) as avg_completion_rate
FROM playbook_executions
WHERE executed_at >= now() - INTERVAL 30 DAY
GROUP BY playbook_id, tenant_id, environment, category, severity;

CREATE TABLE IF NOT EXISTS playbook_performance_dashboard (
    playbook_id String,
    tenant_id String,
    environment String,
    category String,
    severity String,
    total_executions UInt64,
    successful_executions UInt64,
    failed_executions UInt64,
    avg_execution_time_ms Float64,
    last_executed DateTime,
    current_success_rate Float64,
    avg_completion_rate Float64
)
ENGINE = SummingMergeTree()
ORDER BY (playbook_id, tenant_id, environment);

-- Step Performance Analytics: Performance metrics by step type
CREATE MATERIALIZED VIEW IF NOT EXISTS step_performance_mv
TO step_performance_analytics
AS SELECT
    action_type,
    risk_level,
    automated,
    tenant_id,
    toStartOfHour(started_at) as hour,
    count() as total_executions,
    countIf(status = 'completed') as successful_executions,
    countIf(status = 'failed') as failed_executions,
    avg(execution_duration_ms) as avg_duration_ms,
    max(execution_duration_ms) as max_duration_ms,
    sum(retry_count) as total_retries,
    countIf(timeout_occurred = true) as timeout_count
FROM step_executions se
JOIN playbook_steps ps ON se.step_id = ps.step_id
WHERE started_at >= now() - INTERVAL 7 DAY
GROUP BY action_type, risk_level, automated, tenant_id, hour;

CREATE TABLE IF NOT EXISTS step_performance_analytics (
    action_type String,
    risk_level String, 
    automated Bool,
    tenant_id String,
    hour DateTime,
    total_executions UInt64,
    successful_executions UInt64,
    failed_executions UInt64,
    avg_duration_ms Float64,
    max_duration_ms Float64,
    total_retries UInt64,
    timeout_count UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, action_type, risk_level, tenant_id);

-- Template Usage Analytics: Track template effectiveness
CREATE MATERIALIZED VIEW IF NOT EXISTS template_usage_mv
TO template_usage_analytics
AS SELECT
    template_id,
    category,
    severity,
    tenant_id,
    toStartOfDay(p.created_at) as date,
    count() as playbooks_generated,
    avg(confidence_score) as avg_confidence,
    countIf(status IN ('approved', 'active')) as approved_playbooks,
    sum(execution_count) as total_executions,
    avg(success_rate) as avg_success_rate
FROM playbooks p
JOIN playbook_templates pt ON p.category = pt.category 
WHERE p.created_at >= now() - INTERVAL 30 DAY
GROUP BY template_id, category, severity, tenant_id, date;

CREATE TABLE IF NOT EXISTS template_usage_analytics (
    template_id String,
    category String,
    severity String,
    tenant_id String,
    date Date,
    playbooks_generated UInt64,
    avg_confidence Float64,
    approved_playbooks UInt64,
    total_executions UInt64,
    avg_success_rate Float64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, template_id, category);

-- Indexes for improved query performance

-- Index for playbook lookup by status and category
ALTER TABLE playbooks ADD INDEX idx_status_category (status, category) TYPE set(100) GRANULARITY 1;

-- Index for execution time-based queries
ALTER TABLE playbook_executions ADD INDEX idx_executed_at (executed_at) TYPE minmax GRANULARITY 1;

-- Index for step performance analysis
ALTER TABLE step_executions ADD INDEX idx_status_duration (status, execution_duration_ms) TYPE minmax GRANULARITY 1;

-- Index for playbook search by tags
ALTER TABLE playbooks ADD INDEX idx_tags (tags) TYPE set(1000) GRANULARITY 1;

-- Index for template lookup
ALTER TABLE playbook_templates ADD INDEX idx_category_severity (category, severity) TYPE set(100) GRANULARITY 1;

-- Index for safety warning queries
ALTER TABLE playbook_safety_warnings ADD INDEX idx_level_acknowledged (level, acknowledged) TYPE set(10) GRANULARITY 1;

-- Comments for documentation
ALTER TABLE playbooks COMMENT 'Main storage for generated remediation playbooks with metadata and lifecycle tracking';
ALTER TABLE playbook_steps COMMENT 'Individual remediation steps within playbooks with execution details';
ALTER TABLE playbook_validations COMMENT 'Validation procedures to verify playbook execution success';
ALTER TABLE playbook_rollbacks COMMENT 'Rollback procedures for safe recovery from failed playbook execution';
ALTER TABLE playbook_safety_warnings COMMENT 'Safety warnings and risk mitigations for playbook operations';
ALTER TABLE playbook_executions COMMENT 'Historical record of playbook execution attempts and results';
ALTER TABLE step_executions COMMENT 'Detailed execution logs for individual playbook steps';
ALTER TABLE playbook_templates COMMENT 'Reusable templates for automated playbook generation';
ALTER TABLE playbook_references COMMENT 'External documentation and reference links for playbooks';