# Shared Modules Audit Prompt

## Role

Perform a comprehensive audit of the shared modules in this codebase.

Your task is to scan, explore, review, and analyze all shared libraries, utilities, reusable components, shared contracts, common helpers, and cross-cutting packages systematically:
- file by file
- module by module
- package by package
- public API by public API
- consumer by consumer
- consolidation opportunity by consolidation opportunity

Focus on unresolved issues, what must be fixed, where code should be consolidated, and exactly how to solve each issue.

## Scope

Review all shared and reusable code, including:
- Shared libraries and packages
- Common utilities and helpers
- Shared types, interfaces, DTOs, schemas, and contracts
- Shared API clients, SDK wrappers, and adapters
- Shared configuration and environment utilities
- Shared validation, parsing, mapping, formatting, retry, and error-handling logic
- Shared logging, metrics, tracing, caching, and auth helpers
- Shared UI components if present
- Internal developer tooling or utilities used by multiple modules

## Objectives

Ensure that:
- Shared modules are correct, stable, maintainable, and safe to reuse
- Public APIs and contracts are clear, consistent, and minimally surprising
- Shared code does not introduce hidden coupling, implicit side effects, or unsafe assumptions
- Naming is logical, understandable, and consistent
- Documentation is sufficient for maintainers and consumers
- Tests cover important shared behavior, edge cases, and consumer expectations
- There is no duplicate code, duplicate logic, duplicate effort, or overlapping ownership
- There are no parallel implementations of the same shared responsibility
- Modules are cohesive and not sprawled across too many files or packages without benefit
- Code that should be centralized, merged, or moved into a single canonical shared implementation is identified
- Performance-sensitive shared code is efficient and not needlessly repeated
- There is no dead code, stale utility surface, or unnecessary abstraction

## Specific Review Focus

Evaluate:
- API design quality
- Contract stability and backward compatibility
- Consumer ergonomics and ease of correct use
- Hidden assumptions that make shared modules unsafe
- Type safety and schema consistency
- Cross-module coupling and dependency direction
- Error handling and observability
- Over-abstraction, wrapper sprawl, and indirection without value
- Duplicate helpers, duplicate adapters, duplicate validators, duplicate mappers, and duplicate config handling
- Shared concerns implemented independently in multiple places
- Modules that should be merged or responsibilities that should be owned by one clearer unit
- Public methods or exports that are confusing, redundant, or unused

## Documentation Expectations

If names do not fully communicate intent, require concise documentation.

For methods, functions, classes, exported symbols, and important members, documentation should explain:
- What it does
- Why it exists
- How it should be used
- Important inputs, outputs, side effects, and constraints
- When consumers should or should not use it
- A short example use case when helpful

For packages or modules, documentation should explain:
- Purpose
- Primary consumers
- Key responsibilities
- Important contracts
- Design constraints or non-obvious assumptions

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate code
- Duplicate business logic
- Duplicate helpers, adapters, wrappers, and utilities
- Duplicate configuration parsing or environment access
- Duplicate validation, mapping, formatting, retry, and error handling
- Parallel implementations of the same shared concern
- Slightly different copies of the same logic that should be standardized
- Shared modules with overlapping responsibility
- Responsibilities split across too many files or packages
- Sprawled modules that should be consolidated
- Abstractions that increase maintenance cost without improving clarity
- Code paths where the same change must be repeated in multiple places

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

1. Identify all shared modules, shared packages, and their consumers.
2. Map dependencies and consumer relationships.
3. Review each shared module in isolation.
4. Review each public interface and exported symbol.
5. Review usage patterns across consumers.
6. Review tests against actual consumer needs and edge cases.
7. Review duplication, overlap, sprawl, and consolidation opportunities.
8. Review naming, documentation, and maintainability.
9. Produce a complete remediation-focused audit report.

## Required Output File

Produce the audit as a single comprehensive Markdown file.

Requirements:
- The output must be a complete Markdown file, not a short inline response
- The output must contain all findings, not just highlights or top issues
- The output must focus on what needs to be resolved and how to solve it
- The output must include duplication, consolidation, and module sprawl findings explicitly
- The output must include modules with no material issues and state that clearly
- The output must be detailed enough to be used as an engineering handoff document

## Required Output Structure

The Markdown output must contain:

1. `# Shared Modules Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## Shared Module Inventory`
5. `## Dependency and Consumer Overview`
6. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module or package name
- Problem to resolve
- Why it matters
- Evidence
- Consumer impact
- Duplication type if applicable: `code`, `logic`, `ownership`, `workflow`, `none`
- Consolidation recommendation if applicable
- Target location for consolidated code if applicable
- Migration notes
- Exact fix recommendation
- Test gaps
- Documentation gaps

After the complete findings list, include:
- `## Module-by-Module Review`
- `## Contract and API Risks`
- `## Duplicate Code and Logic`
- `## Duplicate Effort and Overlapping Responsibilities`
- `## Sprawled Modules and Fragmented Ownership`
- `## Consolidation Opportunities`
- `## Recommended Simplifications`
- `## Naming and Documentation Issues`
- `## Dead Code and Redundant Abstractions`
- `## Performance Concerns`
- `## Missing Test Coverage`
- `## Full Remediation Plan`
- `## All Unresolved Findings By Severity`
- `## All Unresolved Findings By Module`
- `## Assumptions and Limitations`

## Module-by-Module Review Requirements

For every reviewed shared module or package, include:
- Name and path
- Purpose
- Main exports and responsibilities
- Main consumers
- Key dependencies
- Review status
- Findings found in that unit
- Duplicates or overlaps found
- Consolidation opportunities
- Test gaps
- Documentation gaps
- Naming concerns
- Maintainability concerns
- A brief statement if no material issue was found

## Review Rules

- Be concrete, direct, and actionable
- Focus on unresolved issues and how to fix them
- Do not reduce the report to summaries only
- Distinguish confirmed defects from probable risks
- If something appears correct but untested, call that out
- Explicitly look for code that should be consolidated, centralized, deduplicated, or merged
- Call out modules that are sprawled, fragmented, or split across too many files without sufficient justification
- Prefer recommendations that reduce maintenance overhead and improve ownership clarity
- If no issue exists in a reviewed module, state that briefly

## Final Section

End with:
- Overall assessment of shared module health
- Complete prioritized remediation plan
- Consolidation roadmap
- All unresolved issues grouped by severity and module
- Assumptions and limitations
