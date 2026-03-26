# YAPPC Audit Prompt

## Role

Perform a comprehensive audit of the YAPPC modules, services, integrations, business flows, and dependencies in this codebase.

Your task is to scan, explore, review, and analyze the YAPPC implementation systematically:
- file by file
- module by module
- service by service
- flow by flow
- integration by integration
- consolidation opportunity by consolidation opportunity

Focus on what needs to be resolved, what should be consolidated, what is duplicated or sprawled, and exactly how to solve each issue.

## Scope

Review all YAPPC-related code, including:
- Core domain modules
- Service and API layers
- Business logic and orchestration flows
- Data models, DTOs, contracts, and schemas
- Internal and external integrations
- Shared dependencies used by YAPPC
- Configuration, environment handling, and feature flags
- Error handling, retries, and fallback logic
- Logging, metrics, tracing, and observability
- Tests, fixtures, mocks, validation logic, and regression coverage

## Objectives

Ensure that:
- YAPPC modules are functionally correct, maintainable, and understandable
- Architecture is coherent and responsibilities are clearly separated
- Integrations and dependencies behave as expected
- Edge cases, failure cases, and operational risks are handled correctly
- Naming and documentation are clear and useful
- There is no dead code, duplicate logic, stale configuration, or unnecessary complexity
- There is no duplicate code, duplicate effort, overlapping ownership, or parallel implementations of the same responsibility
- Modules are cohesive and not unnecessarily sprawled across files, services, or layers
- Code that should be merged, centralized, simplified, or moved into shared ownership is identified
- Performance and scalability issues are identified
- Tests cover the most important behavioral, regression, and integration paths

## Specific Review Focus

Evaluate:
- Domain correctness
- Service boundaries and separation of concerns
- API and service contract quality
- Flow correctness across module boundaries
- Configuration safety
- Error handling and resilience
- Observability and diagnosability
- Dependency quality and coupling
- Data flow consistency
- Performance bottlenecks
- Duplicate orchestration logic, duplicate validation, duplicate mapping, duplicate configuration access, duplicate retry or error handling
- Redundant or confusing abstractions
- Modules or services with overlapping responsibilities
- Fragmented ownership across too many files or layers
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

For major flows or services, documentation should explain:
- Purpose
- Responsibilities
- Dependencies
- Entry points
- Important assumptions
- Failure and retry behavior where relevant

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate business logic
- Duplicate service orchestration
- Duplicate helpers, wrappers, and adapters
- Duplicate data mapping, transformation, validation, or parsing
- Duplicate config parsing or feature-flag handling
- Duplicate retry, fallback, or error-handling logic
- Duplicate contract definitions or schema logic
- Slightly different copies of the same responsibility
- Services or modules with overlapping ownership
- Functionality spread across too many files or layers without clear benefit
- Abstractions that increase complexity instead of clarifying ownership
- Code that should move into shared services, shared modules, or one canonical implementation
- Maintenance hotspots where the same change must be repeated across multiple places

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

1. Identify all YAPPC-related modules, services, and dependencies.
2. Map their responsibilities and interactions.
3. Review each unit in isolation.
4. Review end-to-end flows and integration points.
5. Review tests against actual behavior, edge cases, and failures.
6. Review duplication, overlap, sprawl, and consolidation opportunities.
7. Review naming, documentation, maintainability, performance, and resilience.
8. Produce a complete remediation-focused audit report.

## Required Output File

Produce the audit as a single comprehensive Markdown file.

Requirements:
- The output must be a complete Markdown file, not a short inline response
- The output must contain all findings, not just highlights or top 10s
- The output must focus on what needs to be resolved and how to solve it
- The output must explicitly include duplication, consolidation, and module sprawl findings
- Include lower-severity but actionable issues as well
- If a reviewed file, module, or service has no material issue, state that clearly
- The output must be detailed enough to serve as an engineering follow-up document

## Required Output Structure

The Markdown output must contain:

1. `# YAPPC Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## Architecture Overview`
5. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module or service name
- Problem to resolve
- Why it matters
- Evidence
- Functional or operational impact
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
- `## Integration and Dependency Risks`
- `## Performance and Scalability Concerns`
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
- Overall assessment of YAPPC health
- Complete prioritized remediation plan
- Consolidation roadmap
- All unresolved issues grouped by severity and module
- Assumptions and limitations
