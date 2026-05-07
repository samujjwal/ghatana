-- V015: Create evaluation_results table for agent evaluation evidence
--
-- Stores structured evaluation results from agent runs: LLM judge scores,
-- rubric-based assessments, regression baselines, and A/B comparisons.
-- Serves as the evidence plane for promotion decision-making.

CREATE TABLE IF NOT EXISTS evaluation_results (
    id                  BIGSERIAL       PRIMARY KEY,
    evaluation_id       VARCHAR(255)    NOT NULL,
    agent_release_id    VARCHAR(255)    NOT NULL,
    tenant_id           VARCHAR(255)    NOT NULL,
    evaluator_type      VARCHAR(100)    NOT NULL,
    score               DOUBLE PRECISION NOT NULL CHECK (score BETWEEN 0.0 AND 1.0),
    passed              BOOLEAN         NOT NULL,
    rubric_name         VARCHAR(255),
    evaluated_at        TIMESTAMPTZ     NOT NULL,
    run_id              VARCHAR(255),
    trace_id            VARCHAR(255),
    data                JSONB           NOT NULL DEFAULT '{}'
);

-- Unique evaluation ID per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uidx_evaluation_results_eval_id
    ON evaluation_results (tenant_id, evaluation_id);

-- Lookup by release: find all evaluations for a given agent release
CREATE INDEX IF NOT EXISTS idx_evaluation_results_release_id
    ON evaluation_results (tenant_id, agent_release_id);

-- Lookup by evaluator type and score for aggregation queries
CREATE INDEX IF NOT EXISTS idx_evaluation_results_type_score
    ON evaluation_results (tenant_id, agent_release_id, evaluator_type, score);

-- Lookup by run ID for traceability
CREATE INDEX IF NOT EXISTS idx_evaluation_results_run_id
    ON evaluation_results (tenant_id, run_id)
    WHERE run_id IS NOT NULL;

-- Partial index: passed evaluations for promotion gate queries
CREATE INDEX IF NOT EXISTS idx_evaluation_results_passed
    ON evaluation_results (tenant_id, agent_release_id, evaluated_at)
    WHERE passed = TRUE;
