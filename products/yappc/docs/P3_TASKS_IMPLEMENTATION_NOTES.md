# P3 Tasks Implementation Notes

## Overview

P3 tasks are strategic improvements with lower priority. These require significant architectural work and should be implemented after all P0, P1, and P2 tasks are complete.

## P3-1: Implement proactive lifecycle gate pre-check

**Status:** Pending

**Description:** Run PhaseGateValidator every 5 minutes in background to proactively check lifecycle gate status.

**Implementation Requirements:**
- Create background scheduler service in Java
- Schedule PhaseGateValidator execution every 5 minutes
- Emit metrics and alerts for gate failures
- Integrate with existing observability stack
- Add configuration for check interval

**Estimated Effort:** 8-12 hours

## P3-2: Canvas AI suggestions progressive disclosure

**Status:** Pending

**Description:** Show only highest-priority suggestion instead of all suggestions at once.

**Implementation Requirements:**
- Add priority scoring to AI suggestion service
- Modify UI to show single top suggestion with "show more" option
- Implement suggestion ranking logic (impact, effort, urgency)
- Add user preference for disclosure mode

**Estimated Effort:** 6-8 hours

## P3-3: Artifact pre-population at phase entry

**Status:** Pending

**Description:** AI generates draft artifacts using project context when entering a new lifecycle phase.

**Implementation Requirements:**
- Hook into phase transition events
- Call AIService with project context and phase requirements
- Generate draft artifacts (documents, checklists, templates)
- Save to project workspace
- Allow user to accept, modify, or regenerate drafts

**Estimated Effort:** 12-16 hours

## P3-4: Create dedicated YAPPC operations runbook (OPERATIONS.md)

**Status:** Pending

**Description:** Create comprehensive operations runbook for YAPPC production deployment and maintenance.

**Content Requirements:**
- Deployment procedures
- Health check endpoints and monitoring
- Common troubleshooting scenarios
- Backup and restore procedures
- Scaling guidelines
- Incident response procedures
- Configuration management
- Security hardening checklist

**Estimated Effort:** 4-6 hours

## P3-5: Define PostgreSQL backup/restore strategy with RPO/RTO

**Status:** Pending

**Description:** Define and document backup/restore strategy with clear RPO and RTO targets.

**Requirements:**
- Define RPO (Recovery Point Objective) - e.g., 5 minutes
- Define RTO (Recovery Time Objective) - e.g., 1 hour
- Document backup procedure (pg_dump, WAL archiving, or managed service)
- Document restore procedure
- Implement automated backup jobs
- Add backup verification and monitoring
- Document disaster recovery plan

**Estimated Effort:** 6-8 hours

## P3-6: Consolidate collaboration implementations

**Status:** Pending

**Description:** Pick libs/collab + Yjs as canonical collaboration implementation.

**Current State:**
- Multiple collaboration libraries may exist
- Yjs is used for CRDT-based real-time collaboration
- libs/collab provides collaboration utilities

**Implementation Requirements:**
- Audit all collaboration implementations
- Standardize on libs/collab + Yjs
- Remove or deprecate alternative implementations
- Update all consumers to use canonical implementation
- Document collaboration patterns

**Estimated Effort:** 8-10 hours

## P3-7: Add enrichment-worker dead-letter queue and alerting

**Status:** Pending

**Description:** Add dead-letter queue and alerting for enrichment-worker failures.

**Implementation Requirements:**
- Add DLQ to enrichment-worker message queue
- Route failed messages to DLQ after retry exhaustion
- Implement DLQ monitoring and alerting
- Add replay mechanism for DLQ messages
- Integrate with observability stack
- Document DLQ handling procedures

**Estimated Effort:** 6-8 hours

## Summary

**Total Estimated Effort:** 50-68 hours across all P3 tasks

**Recommended Implementation Order:**
1. P3-4 (Operations runbook) - Quick win, documentation only
2. P3-5 (Backup/restore strategy) - Critical for production readiness
3. P3-1 (Proactive gate checks) - Improves system reliability
4. P3-6 (Collaboration consolidation) - Reduces technical debt
5. P3-2 (Progressive disclosure) - UX improvement
6. P3-3 (Artifact pre-population) - AI enhancement
7. P3-7 (DLQ and alerting) - Production hardening
