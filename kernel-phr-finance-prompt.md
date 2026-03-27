# Kernel Platform + PHR + Finance Audit Prompt

## Role

Perform a comprehensive audit of the Kernel platform and the two products built on top of it: PHR and Finance.

Your task is to scan, explore, review, and analyze the full multi-layer system systematically:
- file by file
- module by module
- package by package
- service by service
- product by product
- integration by integration
- platform boundary by platform boundary
- consolidation opportunity by consolidation opportunity

Focus on what needs to be resolved so the Kernel can serve as a clean platform foundation and PHR and Finance can be developed on top of it safely, consistently, and efficiently.

## System Context

Treat the system as three intentional layers:
- Kernel: the shared platform foundation
- PHR: a product built on top of Kernel
- Finance: a product built on top of Kernel

The review must evaluate whether the architecture truly supports this model.

## Scope

Review all relevant code across:
- Kernel platform modules, shared services, platform APIs, and shared infrastructure
- PHR product modules, workflows, schemas, contracts, and integrations
- Finance product modules, workflows, schemas, contracts, and integrations
- Shared libraries, utilities, and reusable components used across layers
- Product-specific extensions and platform extension points
- Cross-product dependencies and accidental coupling
- Configuration, environment handling, feature flags, and deployment boundaries
- Error handling, retries, fallback logic, and recovery behavior
- Logging, tracing, metrics, observability, and operational tooling
- Tests, fixtures, mocks, migration logic, validation logic, and contract coverage

## Objectives

Ensure that:
- Kernel is a clean, stable, reusable platform layer with clear ownership and boundaries
- PHR and Finance are implemented as products on top of Kernel rather than as tightly entangled exceptions
- Shared concerns live in Kernel only when they are genuinely platform concerns
- Product-specific logic remains inside the appropriate product layer unless there is a strong case for promotion into Kernel
- There is no duplicate code, duplicate effort, overlapping ownership, or parallel implementations across Kernel, PHR, and Finance
- There is no accidental coupling between PHR and Finance through shortcuts, backdoors, or improper shared dependencies
- Modules are cohesive and not sprawled across too many files, packages, or services without benefit
- Platform capabilities are reusable, understandable, and not overly abstract
- Product code uses Kernel consistently rather than reimplementing platform concerns
- There is no dead code, stale config, redundant abstraction, or unnecessary layering
- Naming and documentation are clear and useful
- Tests cover platform behavior, product behavior, boundary contracts, and integration paths
- Performance, scalability, resilience, and operational risks are identified

## Specific Review Focus

Evaluate:
- Whether Kernel responsibilities are well-defined and enforced
- Whether PHR and Finance depend on Kernel in a clean and intentional way
- Whether product-specific logic has leaked into Kernel
- Whether Kernel is too product-shaped and biased toward one product
- Whether PHR and Finance duplicate each other instead of sharing through Kernel
- Whether shared modules belong in Kernel or should remain product-specific
- API and service contract quality across layers
- Configuration safety and environment isolation
- Error handling and resilience
- Dependency quality, direction, and layering
- Cross-layer coupling and architectural violations
- Data flow consistency across Kernel and products
- Performance bottlenecks and scaling risks
- Duplicate orchestration, validation, mapping, config access, retry, and error-handling logic
- Fragmented ownership across Kernel, PHR, and Finance
- Abstractions that increase complexity instead of improving reuse or clarity
- Documentation and naming gaps

## Kernel Boundary and Layering Review Requirements

Explicitly assess:
- What belongs in Kernel
- What belongs only in PHR
- What belongs only in Finance
- What appears duplicated between PHR and Finance and should be promoted into Kernel
- What appears prematurely generalized in Kernel and should be pushed back into a product layer
- Whether Kernel exposes the right extension points for products
- Whether product implementations bypass Kernel in unsafe or inconsistent ways
- Whether the platform is modular enough to support future products beyond PHR and Finance

For every boundary issue found, explain:
- What the current ownership is
- What the correct ownership should be
- Why the current layering is problematic
- What should move
- Where it should move
- How to migrate safely without breaking products
- What tests and contracts should be added or updated

## Documentation Expectations

Require concise documentation when intent is not obvious.

For methods, functions, classes, modules, services, and extension points, documentation should explain:
- What it does
- Why it exists
- How it should be used
- Whether it is Kernel, PHR, or Finance owned
- Important inputs, outputs, side effects, and constraints
- Failure behavior where relevant
- Example use case when helpful

For major platform and product modules, documentation should explain:
- Purpose
- Responsibilities
- Layer ownership
- Dependencies
- Extension or customization points
- Important assumptions
- Failure and retry behavior where relevant

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate business logic across Kernel, PHR, and Finance
- Duplicate service orchestration
- Duplicate helpers, wrappers, adapters, and utilities
- Duplicate mapping, validation, transformation, parsing, retry, and fallback logic
- Duplicate config parsing, permission handling, or feature-flag handling
- Duplicate contracts or schema logic
- PHR and Finance implementing the same concern differently when it should be a Kernel capability
- Kernel containing multiple overlapping implementations of the same platform concern
- Slightly different copies of the same responsibility across layers
- Services or modules with overlapping ownership
- Functionality spread across too many files or layers without clear benefit
- Abstractions that increase complexity instead of improving reuse
- Maintenance hotspots where the same change must be repeated across Kernel, PHR, and Finance

For every duplication or sprawl issue found, explain:
- What is duplicated, fragmented, or overlapping
- Where it exists
- Why it is a problem
- Whether it is code duplication, logic duplication, ownership duplication, or workflow duplication
- What should be consolidated
- Whether it should live in Kernel, PHR, or Finance
- How to migrate safely
- What tests should be added before and after consolidation

## Audit Process

1. Identify all Kernel, PHR, and Finance modules, packages, services, and dependencies.
2. Map responsibilities, dependency direction, and extension points.
3. Review Kernel as a platform in isolation.
4. Review PHR as a product on top of Kernel.
5. Review Finance as a product on top of Kernel.
6. Review cross-layer interactions and architectural boundaries.
7. Review tests against platform contracts, product behavior, and integration paths.
8. Review duplication, overlap, sprawl, and consolidation opportunities.
9. Review naming, documentation, maintainability, performance, resilience, and operational readiness.
10. Produce a complete remediation-focused audit report.

## Required Output File

Produce the audit as a single comprehensive Markdown file.

Requirements:
- The output must be a complete Markdown file, not a short inline response
- The output must contain all findings, not just highlights or top issues
- The output must focus on what needs to be resolved and how to solve it
- The output must explicitly include duplication, consolidation, layering, and module sprawl findings
- Include lower-severity but actionable issues as well
- If a reviewed file, module, package, or service has no material issue, state that clearly
- The output must be detailed enough to serve as an engineering follow-up document for platform and product teams

## Required Output Structure

The Markdown output must contain:

1. `# Kernel Platform + PHR + Finance Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## Layered Architecture Overview`
5. `## Kernel Ownership and Boundary Review`
6. `## PHR Product Review`
7. `## Finance Product Review`
8. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module, package, or service name
- Layer: `Kernel`, `PHR`, `Finance`, or `Cross-Layer`
- Problem to resolve
- Why it matters
- Evidence
- Functional, operational, architectural, or product impact
- Duplication type if applicable: `code`, `logic`, `ownership`, `workflow`, `none`
- Consolidation recommendation if applicable
- Correct ownership recommendation: `Kernel`, `PHR`, `Finance`, or `Split/Refactor`
- Target location for consolidated code if applicable
- Migration notes
- Exact fix recommendation
- Test gaps
- Documentation gaps

After the complete findings list, include:
- `## File-by-File / Module-by-Module Review`
- `## Kernel Platform Risks`
- `## PHR Product Risks`
- `## Finance Product Risks`
- `## Cross-Layer Architecture Violations`
- `## Product-to-Platform Boundary Violations`
- `## Duplicate Code and Logic`
- `## Duplicate Effort and Overlapping Responsibilities`
- `## Sprawled Modules and Fragmented Ownership`
- `## Consolidation Opportunities`
- `## Recommended Simplifications`
- `## Naming and Documentation Issues`
- `## Dead Code and Redundant Logic`
- `## Integration and Dependency Risks`
- `## Performance, Scalability, and Resilience Concerns`
- `## Missing Test Coverage`
- `## Full Remediation Plan`
- `## All Unresolved Findings By Severity`
- `## All Unresolved Findings By Layer`
- `## All Unresolved Findings By Module`
- `## Assumptions and Limitations`

## File-by-File / Module-by-Module Review Requirements

For every reviewed unit, include:
- Name and path
- Layer ownership
- Purpose
- Key responsibilities
- Dependencies
- Consumers
- Review status
- Findings found in that unit
- Duplicates or overlaps found
- Consolidation opportunities
- Boundary violations if any
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
- Prefer Kernel only for true platform concerns, not product-specific convenience code
- Call out product-specific logic that has leaked into Kernel
- Call out duplicated product logic that should become a Kernel capability
- Do not reduce the report to highlights only
- If no issue exists in a reviewed module, state that briefly

## Final Section

End with:
- Overall assessment of Kernel platform health
- Overall assessment of PHR product health
- Overall assessment of Finance product health
- Assessment of whether the current architecture can support platform-plus-products evolution
- Complete prioritized remediation plan
- Consolidation roadmap
- Suggested ownership corrections across Kernel, PHR, and Finance
- All unresolved issues grouped by severity and layer
- Assumptions and limitations
