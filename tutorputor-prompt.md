# Tutorputor Audit Prompt

## Role

Perform a comprehensive audit of the Tutorputor product, its platform dependencies, product-specific modules, business flows, integrations, and operational surfaces.

Your task is to scan, explore, review, and analyze the full Tutorputor implementation systematically:
- file by file
- module by module
- service by service
- workflow by workflow
- integration by integration
- product boundary by product boundary
- consolidation opportunity by consolidation opportunity

Focus on what needs to be resolved, what should be consolidated, where responsibilities are duplicated or sprawled, and exactly how to solve each issue.

## Scope

Review all Tutorputor-related code, including:
- Product-specific domain modules
- User, tutor, learner, session, scheduling, matching, messaging, payment, progress, and content flows where applicable
- APIs, SDKs, adapters, orchestrators, and service boundaries
- Data models, DTOs, schemas, and contracts
- UI modules or client flows if part of the product surface
- Background jobs, async workflows, notifications, and automation paths
- Internal and external integrations
- Shared platform dependencies used by Tutorputor
- Configuration, environment handling, feature flags, and deployment settings
- Error handling, retries, fallback logic, and recovery behavior
- Logging, metrics, tracing, observability, and operational tooling
- Tests, fixtures, mocks, validation logic, and migration logic

## Objectives

Ensure that:
- Tutorputor modules are functionally correct, maintainable, and understandable
- Product architecture is coherent and responsibilities are clearly separated
- Business flows behave correctly end-to-end
- Integrations and dependencies behave as expected
- Edge cases, failure cases, and operational risks are handled correctly
- Naming and documentation are clear and useful
- There is no dead code, stale configuration, duplicate logic, or unnecessary complexity
- There is no duplicate code, duplicate effort, overlapping ownership, or parallel implementations of the same responsibility
- Modules are cohesive and not unnecessarily sprawled across files, services, packages, or layers
- Code that should be merged, centralized, standardized, or moved into shared ownership is identified
- Product-specific logic is not incorrectly embedded in shared platform code
- Shared platform capabilities are reused where appropriate instead of being reimplemented locally
- Performance, scalability, resilience, and operational risks are identified
- Tests cover important behavior, regressions, integration paths, and business-critical scenarios

## Specific Review Focus

Evaluate:
- Product domain correctness
- Business workflow correctness across module and service boundaries
- Separation between platform concerns and Tutorputor product concerns
- API and service contract quality
- Scheduling, session, payment, notification, matching, and workflow reliability where relevant
- Configuration safety
- Error handling and resilience
- Observability and diagnosability
- Dependency quality and coupling
- Performance bottlenecks and scaling risks
- Duplicate business logic, duplicate orchestration, duplicate validation, duplicate mapping, duplicate retry logic, and duplicate notification or integration handling
- Product responsibilities spread across too many files or layers without clear benefit
- Abstractions that increase complexity instead of clarifying ownership
- Documentation and naming gaps

## Documentation Expectations

Require concise documentation when intent is not obvious.

For methods, functions, classes, modules, and important members, documentation should explain:
- What it does
- Why it exists
- How to use it
- Important inputs, outputs, side effects, and constraints
- Failure behavior where relevant
- Example use case when helpful

For major services, workflows, and product modules, documentation should explain:
- Purpose
- Responsibilities
- Dependencies
- Entry points
- Business assumptions
- Failure and retry behavior where relevant
- Operational assumptions

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate business logic
- Duplicate workflow orchestration
- Duplicate scheduling, matching, messaging, payment, or notification logic
- Duplicate validation, transformation, parsing, and mapping logic
- Duplicate config parsing or feature-flag handling
- Duplicate retry, fallback, or error-handling logic
- Duplicate schema or contract definitions
- Duplicate helpers, wrappers, and adapters
- Slightly different copies of the same responsibility
- Services or modules with overlapping ownership
- Product logic duplicated in both Tutorputor and shared platform layers
- Functionality spread across too many files or layers without clear benefit
- Abstractions that increase maintenance cost instead of improving clarity
- Code that should move into a shared platform capability or one canonical product implementation
- Maintenance hotspots where one change must be repeated in multiple places

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

1. Identify all Tutorputor-related modules, services, workflows, and dependencies.
2. Map responsibilities and end-to-end product flows.
3. Separate platform-owned capabilities from product-owned capabilities.
4. Review each unit in isolation.
5. Review end-to-end flows and integration points.
6. Review tests against behavior, contracts, edge cases, and failures.
7. Review duplication, overlap, sprawl, and consolidation opportunities.
8. Review naming, documentation, maintainability, performance, resilience, and operational readiness.
9. Produce a complete remediation-focused audit report.

## Required Output File

Produce the audit as a single comprehensive Markdown file.

Requirements:
- The output must be a complete Markdown file, not a short inline response
- The output must contain all findings, not just highlights or top issues
- The output must focus on what needs to be resolved and how to solve it
- The output must explicitly include duplication, consolidation, and module sprawl findings
- Include lower-severity but actionable issues as well
- If a reviewed file, module, service, or workflow has no material issue, state that clearly
- The output must be detailed enough to serve as an engineering follow-up document

## Required Output Structure

The Markdown output must contain:

1. `# Tutorputor Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## Product Architecture Overview`
5. `## Platform vs Product Boundary Review`
6. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module, service, or workflow name
- Problem to resolve
- Why it matters
- Evidence
- Functional, operational, business, or user impact
- Duplication type if applicable: `code`, `logic`, `ownership`, `workflow`, `none`
- Consolidation recommendation if applicable
- Target location for consolidated code if applicable
- Migration notes
- Exact fix recommendation
- Test gaps
- Documentation gaps

After the complete findings list, include:
- `## File-by-File / Module-by-Module Review`
- `## Architecture and Design Risks`
- `## Platform Boundary Violations`
- `## Business Flow Risks`
- `## Integration and Dependency Risks`
- `## Performance, Scalability, and Reliability Concerns`
- `## Error Handling and Resilience Gaps`
- `## Duplicate Code and Logic`
- `## Duplicate Effort and Overlapping Responsibilities`
- `## Sprawled Modules and Fragmented Ownership`
- `## Consolidation Opportunities`
- `## Recommended Simplifications`
- `## Naming and Documentation Issues`
- `## Dead Code and Redundant Logic`
- `## Missing Test Coverage`
- `## Full Remediation Plan`
- `## All Unresolved Findings By Severity`
- `## All Unresolved Findings By Module`
- `## Assumptions and Limitations`

## File-by-File / Module-by-Module Review Requirements

For every reviewed unit, include:
- Name and path
- Purpose
- Key responsibilities
- Dependencies
- Whether it is platform-owned or product-owned
- Review status
- Findings found in that unit
- Duplicates or overlaps found
- Consolidation opportunities
- Test gaps
- Documentation gaps
- Naming clarity notes
- Performance or maintainability concerns
- A brief statement if no material issue was found

## Review Rules

- Be concrete and actionable
- Focus on issues that should be resolved, not generic commentary
- Explain how to solve each issue
- Distinguish confirmed defects from probable risks
- If something appears correct but untested, call that out
- Explicitly look for code that should be consolidated, centralized, deduplicated, or merged
- Call out modules that are sprawled, fragmented, or split across too many files without sufficient justification
- Prefer recommendations that reduce maintenance overhead and improve ownership clarity
- Do not reduce the report to highlights only
- If no issue exists in a reviewed module, state that briefly

## Final Section

End with:
- Overall assessment of Tutorputor health
- Complete prioritized remediation plan
- Consolidation roadmap
- All unresolved issues grouped by severity and module
- Assumptions and limitations
