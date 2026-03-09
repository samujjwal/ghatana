# YAPPC Core Policy Rules (OPA-compatible Rego)
#
# These policies are evaluated by policy-guard-agent at runtime.
# Policy evaluation order: platform deny → YAPPC rules → compliance packs → tenant overrides
# Conflict resolution: most restrictive rule wins (deny overrides allow)

package yappc.policies.core

import future.keywords.in

# =============================================================================
# Destructive Operations
# =============================================================================

# Rule: No agent may execute destructive database operations without HITL approval
deny[msg] {
    input.action == "database.migration.execute"
    input.migration.is_destructive == true
    count(input.approvals) == 0
    msg := "Destructive database migration requires human approval"
}

# Rule: No agent may drop database tables without HITL approval
deny[msg] {
    input.action == "database.migration.execute"
    contains(input.migration.sql, "DROP TABLE")
    not input.approvals[_].human == true
    msg := "DROP TABLE migration requires explicit human approval"
}

# =============================================================================
# Secret Access Control
# =============================================================================

# Rule: No agent may access secrets outside its declared scope
deny[msg] {
    input.action == "secret.read"
    not input.secret_key in input.agent.declared_secrets
    msg := sprintf("Agent %v attempted to read undeclared secret %v", [input.agent.id, input.secret_key])
}

# Rule: Secrets must not appear in agent output
deny[msg] {
    input.action == "agent.output"
    some secret in input.agent.declared_secrets
    contains(input.output, secret)
    msg := sprintf("Agent %v output contains secret material", [input.agent.id])
}

# =============================================================================
# Token Budget Enforcement
# =============================================================================

# Rule: LLM agents cannot produce output exceeding token budget
deny[msg] {
    input.action == "llm.invoke"
    input.requested_tokens > input.agent.token_budget
    msg := sprintf("Token request %v exceeds budget %v for agent %v",
                   [input.requested_tokens, input.agent.token_budget, input.agent.id])
}

# Rule: Block when project budget is exhausted
deny[msg] {
    input.action == "llm.invoke"
    input.project.budget_remaining <= 0
    msg := sprintf("Project budget exhausted for project %v", [input.project.id])
}

# Rule: Warn at 80% budget consumption
warn[msg] {
    input.action == "llm.invoke"
    input.project.budget_consumed / input.project.budget_total >= 0.80
    msg := sprintf("Project %v has consumed %v%% of budget",
                   [input.project.id, round(input.project.budget_consumed / input.project.budget_total * 100)])
}

# =============================================================================
# Production Deployment Controls
# =============================================================================

# Rule: Production deployments require explicit human approval
deny[msg] {
    input.action == "deployment.execute"
    input.environment == "production"
    not input.approvals[_].human == true
    msg := "Production deployment requires at least one human approval"
}

# Rule: Production deployments require passing supply chain verification
deny[msg] {
    input.action == "deployment.execute"
    input.environment == "production"
    not input.supply_chain_verified == true
    msg := "Production deployment requires supply chain verification (SLSA)"
}

# Rule: Canary deployments must have rollback plan
deny[msg] {
    input.action == "deployment.execute"
    input.strategy == "canary"
    not input.rollback_plan
    msg := "Canary deployment requires a rollback plan"
}

# =============================================================================
# Agent Capability Enforcement
# =============================================================================

# Rule: Agent may only use capabilities it declares
deny[msg] {
    input.action == "capability.use"
    not input.capability in input.agent.declared_capabilities
    msg := sprintf("Agent %v attempted to use undeclared capability %v",
                   [input.agent.id, input.capability])
}

# Rule: Cross-agent delegation must be declared
deny[msg] {
    input.action == "agent.delegate"
    not input.target_agent in input.agent.can_delegate_to
    msg := sprintf("Agent %v attempted undeclared delegation to %v",
                   [input.agent.id, input.target_agent])
}

# =============================================================================
# Guardrail Self-Protection
# =============================================================================

# Rule: Guardrail agents cannot be blocked by other guardrail agents
allow {
    input.agent.role == "guardrail"
    input.requested_by.role == "guardrail"
}

# Rule: Guardrail agents always have priority execution
allow {
    input.agent.role == "guardrail"
    input.action == "agent.execute"
}

# =============================================================================
# Compliance Requirements
# =============================================================================

# Rule: All agent actions must produce audit trail entries
deny[msg] {
    input.action == "agent.complete"
    not input.audit_entry_created == true
    msg := sprintf("Agent %v completed without creating audit trail entry", [input.agent.id])
}

# Rule: SBOM must be generated for every release
deny[msg] {
    input.action == "release.publish"
    not input.sbom_generated == true
    msg := "Release publish requires SBOM generation"
}

# Rule: Artifacts must be signed before release
deny[msg] {
    input.action == "release.publish"
    not input.artifacts_signed == true
    msg := "Release publish requires artifact signing"
}

# =============================================================================
# Data Protection
# =============================================================================

# Rule: No cross-tenant data access
deny[msg] {
    input.action in ["data.read", "data.write"]
    input.data.tenant_id != input.agent.tenant_id
    msg := sprintf("Cross-tenant data access denied: agent tenant %v, data tenant %v",
                   [input.agent.tenant_id, input.data.tenant_id])
}

# Rule: PII must not be included in LLM prompts
deny[msg] {
    input.action == "llm.invoke"
    input.prompt_contains_pii == true
    msg := "LLM prompt must not contain PII — sanitize before invocation"
}
