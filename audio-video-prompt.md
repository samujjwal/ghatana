# Audio-Video Audit Prompt

## Role

Perform a comprehensive audit of the audio-video modules, pipelines, integrations, and dependencies in this codebase.

Your task is to scan, explore, review, and analyze the full audio-video surface systematically:
- file by file
- module by module
- service by service
- media flow by media flow
- integration by integration
- consolidation opportunity by consolidation opportunity

Focus on what must be resolved, reliability and performance risks, duplicate media logic, module sprawl, and exactly how to solve each issue.

## Scope

Review all audio-video related code, including:
- Audio capture, playback, encoding, decoding, buffering, and processing
- Video capture, rendering, encoding, decoding, playback, and streaming
- Media synchronization logic
- Media session lifecycle handling
- Device access, permissions, and capability detection
- Reconnection, retry, fallback, and degraded-mode behavior
- Media transport and streaming protocols
- Media storage and delivery integrations
- Metadata extraction and media state handling
- Monitoring, logging, metrics, tracing, and debugging support
- Tests, mocks, fixtures, and media validation paths
- Shared utilities used by audio-video features

## Objectives

Ensure that:
- Audio-video flows are correct and reliable under realistic operating conditions
- Sync, buffering, timing, and lifecycle logic are correct
- Network instability, dropped frames, missing audio, codec mismatch, device failures, and permission failures are handled safely
- Performance is acceptable in CPU, memory, latency, startup time, bandwidth use, and cleanup behavior
- Integrations work correctly end-to-end
- Tests cover realistic success paths, degraded paths, and failure scenarios
- Naming and documentation are understandable
- There is no duplicate code, duplicate media logic, duplicate effort, or overlapping media ownership
- Media responsibilities are not sprawled across too many files or layers without justification
- Repeated media handling logic that should be consolidated into shared utilities or services is identified
- There is no dead media code, redundant wrappers, or unnecessary abstraction

## Specific Review Focus

Evaluate:
- Playback correctness
- Recording correctness
- Audio-video synchronization
- Session and resource lifecycle handling
- Retry, reconnect, and fallback behavior
- Device and browser or platform compatibility assumptions
- Codec support and format handling
- State machine correctness
- Cleanup and leak prevention
- Observability and debuggability
- Duplicate media state handling, duplicate buffering logic, duplicate retry logic, duplicate device handling, and duplicate metadata logic
- Media code split across too many modules with unclear ownership
- Overlapping utilities or pipelines that should be consolidated

## Documentation Expectations

Require concise documentation for non-obvious media logic.

For methods, functions, classes, and important modules, documentation should explain:
- What it does
- Why it exists
- How to use it
- Lifecycle or timing assumptions
- Platform, device, or codec limitations
- Important side effects
- Example use when helpful

For major flows or media modules, documentation should explain:
- Purpose
- Entry points
- Dependencies
- Failure behavior
- Recovery behavior
- Operational assumptions

## Consolidation and Duplication Review Requirements

Explicitly review for:
- Duplicate playback logic
- Duplicate recording logic
- Duplicate media session lifecycle handling
- Duplicate buffering, retry, or reconnect logic
- Duplicate codec or capability checks
- Duplicate permission or device access handling
- Duplicate media metadata parsing or state management
- Duplicate error handling across media flows
- Audio-video responsibilities spread across too many files or services
- Overlapping media abstractions or wrappers
- Slightly different implementations of the same media behavior that should be standardized
- Code that should move into a shared media utility, service, or pipeline
- Media modules whose structure creates maintenance overhead because the same change must be repeated in multiple places

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

1. Identify all audio-video entry points and media flows.
2. Map recording, transport, processing, rendering, and playback paths.
3. Review each module and service in isolation.
4. Review end-to-end media behavior.
5. Review failure and degraded-mode handling.
6. Review tests against realistic media conditions.
7. Review duplication, overlap, and sprawl.
8. Review naming, documentation, performance, and maintainability.
9. Produce a complete remediation-focused audit report.

## Required Output File

Produce the audit as a single comprehensive Markdown file.

Requirements:
- The output must be a complete Markdown file, not a short inline response
- The output must contain all findings, not just highlights or top issues
- The output must focus on what must be resolved and how to solve it
- The output must explicitly include duplication, consolidation, and module sprawl findings
- If a reviewed unit has no material issue, state that clearly
- The output must be usable as an engineering follow-up document

## Required Output Structure

The Markdown output must contain:

1. `# Audio-Video Audit Report`
2. `## Executive Summary`
3. `## Scope Reviewed`
4. `## Media Flow Overview`
5. `## Findings`

For each finding include:
- Finding ID
- Severity: `critical`, `high`, `medium`, `low`
- File path
- Module, service, or pipeline name
- Problem to resolve
- Why it matters
- Evidence
- User or system impact
- Duplication type if applicable: `code`, `logic`, `ownership`, `workflow`, `none`
- Consolidation recommendation if applicable
- Target location for consolidated code if applicable
- Migration notes
- Exact fix recommendation
- Test gaps
- Documentation gaps

After the complete findings list, include:
- `## Module-by-Module Review`
- `## Playback and Recording Risks`
- `## Sync, Buffering, and Retry Risks`
- `## Performance and Resource Concerns`
- `## Platform and Compatibility Risks`
- `## Duplicate Code and Logic`
- `## Duplicate Effort and Overlapping Responsibilities`
- `## Sprawled Modules and Fragmented Ownership`
- `## Consolidation Opportunities`
- `## Recommended Simplifications`
- `## Missing Test Coverage`
- `## Naming and Documentation Issues`
- `## Full Remediation Plan`
- `## All Unresolved Findings By Severity`
- `## All Unresolved Findings By Area`
- `## Assumptions and Limitations`

## Module-by-Module Review Requirements

For every reviewed media unit, include:
- Name and path
- Purpose
- Media responsibilities
- Dependencies
- Lifecycle role
- Review status
- Findings found in that unit
- Duplicates or overlaps found
- Consolidation opportunities
- Test gaps
- Documentation gaps
- Naming clarity concerns
- Performance or cleanup concerns
- A brief statement if no material issue was found

## Review Rules

- Be concrete, direct, and actionable
- Focus on defects, operational risks, and exact fixes
- Call out cleanup, leak, timing, race-condition, and retry issues explicitly
- Distinguish confirmed issues from probable risks
- If something appears correct but untested under realistic media conditions, call that out
- Explicitly look for code that should be consolidated, centralized, deduplicated, or merged
- Call out modules that are sprawled, fragmented, or split across too many files without sufficient justification
- Prefer recommendations that reduce maintenance overhead and improve ownership clarity
- If no issue exists in a reviewed module, state that briefly

## Final Section

End with:
- Overall assessment of audio-video system health
- Complete prioritized remediation plan
- Consolidation roadmap
- All unresolved issues grouped by severity and area
- Assumptions and limitations
