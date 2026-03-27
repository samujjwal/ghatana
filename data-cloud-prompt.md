# Data-Cloud Audit Prompt

## Role

Perform a comprehensive audit of the Data-Cloud product, its platform dependencies, product-specific modules, data flows, integrations, and operational surfaces.

Your task is to scan, explore, review, and analyze the full Data-Cloud implementation systematically:
- file by file
- module by module
- service by service
- schema by schema
- pipeline by pipeline
- integration by integration
- consolidation opportunity by consolidation opportunity

Focus on what needs to be resolved, what should be consolidated, where responsibilities are duplicated or sprawled, and exactly how to solve each issue.

## Scope

Review all Data-Cloud-related code, including:
- Product-specific domain modules
- Data ingestion, transformation, enrichment, and serving flows
- Product schemas, contracts, and models
- APIs, SDKs, adapters, and service boundaries
- Storage, indexing, caching, and retrieval layers
- Product workflows and orchestration logic
- Internal and external integrations
- Shared platform dependencies used by Data-Cloud
- Configuration, environment handling, and feature flags
- Error handling, retries, fallback logic, and recovery behavior
- Logging, tracing, metrics, observability, and operational tooling
- Tests, fixtures, mocks, migration logic, and validation logic

## Objectives

Ensure that:
- Data-Cloud modules are functionally correct, maintainable, and understandable
- The product architecture is coherent and responsibilities are clearly separated
- Product data flows are consistent, validated, traceable, and reliable
- Integrations and dependencies behave as expected
- Edge cases, failure cases, and operational risks are handled correctly
- Naming and documentation are clear and useful
- There is no dead code, stale configuration, duplicate logic, or unnecessary complexity
- There is no duplicate code, duplicate effort, overlapping ownership, or parallel implementations of the same responsibility
- Modules are cohesive and not unnecessarily sprawled across files, services, packages, or layers
- Code that should be merged, centralized, standardized, or moved into shared ownership is identified
- Product-specific logic is not incorrectly embedded in shared platform code
- Shared platform capabilities are reused where appropriate instead of being reimplemented locally
- Performance, scalability, resilience, and cost risks are identified
- Tests cover important behavior, regressions, integration paths, and data-quality expectations

## Specific Review Focus

Evaluate:
- Product domain correctness
- Data model fitness and schema consistency
- Data flow correctness across module and service boundaries
- Separation between platform concerns and Data-Cloud product concerns
- API and service contract quality
- Ingestion, validation, transformation, and serving reliability
- Configuration safety
- Error handling and resilience
- Observability and diagnosability
- Dependency quality and coupling
- Performance bottlenecks and scaling risks
- Duplicate ingestion, mapping, validation, retry, or reconciliation logic
- Duplicate workflow logic or partially overlapping product services
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

For major services, pipelines, and product modules, documentation should explain:
- Purpose
- Responsibilities
- Dependencies
- Data contracts
- Entry points
- Failure and retry behavior where relevant
- Operational assumptions

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate business logic
- Duplicate ingestion, mapping, transformation, and validation logic
- Duplicate reconciliation, retry, fallback, or error-handling logic
- Duplicate schema definitions or contract logic
- Duplicate config parsing or feature-flag handling
- Duplicate helpers, wrappers, and adapters
- Slightly different copies of the same responsibility
- Services or modules with overlapping ownership
- Product logic duplicated in both Data-Cloud and shared platform layers
- Functionality spread across too many files or layers without clear benefit
- Abstractions that increase maintenance cost instead of improving clarity
- Code that should move into a shared platform capability or into one canonical product implementation
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

1. Identify all Data-Cloud-related modules, services, schemas, pipelines, and dependencies.
2. Map responsibilities and end-to-end data flows.
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
- If a reviewed file, module, service, schema, or pipeline has no material issue, state that clearly
- The output must be detailed enough to serve as an engineering follow-up document

## Required Output Structure

The Markdown output must contain:

1. `# Data-Cloud Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## Product Architecture Overview`
5. `## Platform vs Product Boundary Review`
6. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module, service, schema, or pipeline name
- Problem to resolve
- Why it matters
- Evidence
- Functional, operational, or data-quality impact
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
- `## Data Integrity and Contract Risks`
- `## Integration and Dependency Risks`
- `## Performance, Scalability, and Cost Concerns`
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
- Overall assessment of Data-Cloud health
- Complete prioritized remediation plan
- Consolidation roadmap
- All unresolved issues grouped by severity and module
- Assumptions and limitations
