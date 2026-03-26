# AEP Audit Prompt

## Role

Perform a comprehensive audit of the AEP-related modules, integrations, flows, contracts, and dependencies in this codebase.

Your task is to scan, explore, review, and analyze the AEP implementation systematically:
- file by file
- module by module
- integration by integration
- event flow by event flow
- configuration path by configuration path
- consolidation opportunity by consolidation opportunity

Focus on what needs to be resolved, where integration behavior is duplicated or fragmented, what could cause data loss or inconsistency, and exactly how to solve each issue.

## Scope

Review all AEP-related code, including:
- AEP client integrations
- Event creation, mapping, enrichment, validation, and delivery
- Data contracts and payload schemas
- Identity handling and profile mapping
- Consent and privacy logic
- Queueing, batching, retry, idempotency, and delivery flows
- Configuration and environment handling
- Error handling and fallback behavior
- Monitoring, logging, metrics, and tracing
- Tests and fixtures for AEP-related flows
- Shared utilities and dependencies that affect AEP ingestion, transformation, or delivery

## Objectives

Ensure that:
- AEP integrations are correct, complete, and maintainable
- Events are valid, mapped consistently, and compliant with contracts
- Identity, consent, and privacy handling are safe and correctly enforced
- Configuration is robust and environment-safe
- Failures do not silently drop, corrupt, or misroute data
- Retries, batching, ordering, and idempotency are correct where relevant
- Naming and documentation are understandable
- Tests cover success paths, failure paths, invalid payloads, and integration edge cases
- There is no duplicate code, duplicate event-mapping logic, duplicate delivery logic, duplicate effort, or overlapping ownership
- AEP responsibilities are not sprawled across too many files or modules without benefit
- Logic that should be centralized into canonical event builders, validators, or delivery layers is identified
- There is no dead integration code, stale config, or unnecessary wrapper abstraction

## Specific Review Focus

Evaluate:
- Event taxonomy consistency
- Payload correctness and contract compliance
- Required and optional field handling
- Identity stitching and profile mapping assumptions
- Consent and privacy enforcement
- Retry, backoff, batching, ordering, and drop behavior
- Observability for delivery and mapping failures
- Environment and configuration safety
- Duplicate event builders, duplicate field mapping, duplicate validation, duplicate consent checks, duplicate queue or retry logic
- AEP flow ownership split across too many places
- Overlapping wrappers or integration abstractions that should be consolidated
- Missing or weak integration tests
- Documentation of event purpose, contract, and usage

## Documentation Expectations

Require concise documentation for:
- Event-producing modules
- Event contracts and mapping logic
- Identity and consent decisions
- Delivery and retry behavior
- Non-obvious AEP integration behaviors

For methods, functions, classes, and important members, documentation should explain:
- What it does
- Why it exists
- How to use it
- Expected payload shape or important side effects
- Failure behavior
- Constraints or assumptions
- Example use case when helpful

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate event creation logic
- Duplicate field mapping logic
- Duplicate schema validation logic
- Duplicate identity resolution logic
- Duplicate consent enforcement logic
- Duplicate retry, batching, queue, or delivery handling
- Duplicate environment or configuration parsing
- Duplicate observability logic around AEP failures
- Similar but inconsistent AEP flows that should use one standard implementation
- Modules with overlapping AEP responsibility
- AEP logic sprawled across too many files or packages
- Code that should be moved into shared AEP builders, validators, or delivery services
- Workflows that increase maintenance overhead because the same change must be repeated in multiple places

For every duplication or sprawl issue found, explain:
- What is duplicated, fragmented, or overlapping
- Where it exists
- Why it is a problem
- Whether it is code duplication, logic duplication, ownership duplication, or workflow duplication
- What should be consolidated
- Where the consolidated implementation should live
- How to migrate safely
- What tests should be added before and after consolidation

## Audit Process

1. Identify all AEP-related modules and integration points.
2. Map event creation, transformation, validation, and delivery flows end-to-end.
3. Review each module in isolation.
4. Review end-to-end integration behavior into AEP.
5. Review failure, retry, batching, and consent paths.
6. Review tests against contracts and integration risks.
7. Review duplication, overlap, sprawl, and consolidation opportunities.
8. Review naming, documentation, and maintainability.
9. Produce a complete remediation-focused audit report.

## Required Output File

Produce the audit as a single comprehensive Markdown file.

Requirements:
- The output must be a complete Markdown file, not a short inline response
- The output must contain all findings, not just summaries or top issues
- The output must focus on what needs to be resolved and how to solve it
- The output must explicitly include duplication, consolidation, and module sprawl findings
- The output must include both confirmed defects and clearly labeled probable risks
- If a reviewed unit has no material issue, state that clearly
- The output must be usable as an engineering handoff document

## Required Output Structure

The Markdown output must contain:

1. `# AEP Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## AEP Flow Overview`
5. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module or integration name
- Problem to resolve
- Why it matters
- Evidence
- AEP or business impact
- Duplication type if applicable: `code`, `logic`, `ownership`, `workflow`, `none`
- Consolidation recommendation if applicable
- Target location for consolidated code if applicable
- Migration notes
- Exact fix recommendation
- Test gaps
- Documentation gaps

After the complete findings list, include:
- `## Module-by-Module Review`
- `## Event Contract Risks`
- `## Identity and Consent Risks`
- `## Delivery, Retry, and Failure Handling Risks`
- `## Configuration Risks`
- `## Duplicate Code and Logic`
- `## Duplicate Effort and Overlapping Responsibilities`
- `## Sprawled Modules and Fragmented Ownership`
- `## Consolidation Opportunities`
- `## Recommended Simplifications`
- `## Missing Test Coverage`
- `## Naming and Documentation Issues`
- `## Full Remediation Plan`
- `## All Unresolved Findings By Severity`
- `## All Unresolved Findings By Flow`
- `## Assumptions and Limitations`

## Module-by-Module Review Requirements

For every reviewed AEP-related unit, include:
- Name and path
- Purpose
- Main responsibilities
- Upstream and downstream dependencies
- Review status
- Findings found in that unit
- Duplicates or overlaps found
- Consolidation opportunities
- Test gaps
- Documentation gaps
- Naming concerns
- Operational or maintainability concerns
- A brief statement if no material issue was found

## Review Rules

- Be concrete, direct, and actionable
- Focus on unresolved issues and how to fix them
- Distinguish confirmed defects from probable integration risks
- Call out silent data-loss or silent data-corruption risks explicitly
- If something appears functionally correct but weakly tested, call that out
- Explicitly look for code that should be consolidated, centralized, deduplicated, or merged
- Call out modules that are sprawled, fragmented, or split across too many files without sufficient justification
- Prefer recommendations that reduce maintenance overhead and improve ownership clarity
- If no issue exists in a reviewed module, state that briefly

## Final Section

End with:
- Overall assessment of AEP integration health
- Complete prioritized remediation plan
- Consolidation roadmap
- All unresolved issues grouped by severity and flow
- Assumptions and limitations
