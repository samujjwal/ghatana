# Aura Engineering Sprint Plan (First 6 Months)

## Assumptions
- 2-week sprints
- Team: 1 PM, 1 designer, 3 full-stack engineers, 1 ML engineer, 1 QA/shared automation
- Initial focus: beauty-first MVP
- Launch sequence target: internal alpha in Month 3, invite beta launch in Month 5, public launch in Month 8

This document is the schedule view. Detailed `what`, `how`, `where`, and validation expectations for
each task live in `Aura_Task_Execution_Matrix.md`.

## Month 1
### Sprint 1
- `S01-T01` Finalize product scope and user journeys
- `S01-T02` Stand up monorepo, Gitea Actions CI/CD, environments
- `S01-T03` Define modular-monolith boundaries and service extraction criteria
- `S01-T04` Define domain model and Prisma schema
- `S01-T05` Implement auth and profile onboarding skeleton

### Sprint 2
- `S02-T01` Build catalog ingestion MVP
- `S02-T02` Add product, brand, ingredient entities
- `S02-T03` Create basic feed API
- `S02-T04` Create wireframes and design system foundation

## Month 2
### Sprint 3
- `S03-T01` Implement product detail page
- `S03-T02` Build save/bookmark flows
- `S03-T03` Add consent center v1
- `S03-T04` Add data export request flow
- `S03-T05` Add ingestion jobs and admin diagnostics

### Sprint 4
- `S04-T01` Implement ingredient analyzer v1
- `S04-T02` Add rules engine for allergen and ethical filtering
- `S04-T03` Build recommendation explanation payload shape
- `S04-T04` Add unit and integration tests

## Month 3
### Sprint 5
- `S05-T01` Implement shade-matching v1
- `S05-T02` Add recommendation query API
- `S05-T03` Build personalized recommendation cards in UI
- `S05-T04` Instrument analytics events

### Sprint 6
- `S06-T01` Improve ranking heuristics
- `S06-T02` Add compare products screen
- `S06-T03` Add feedback capture: view, click, save, dismiss
- `S06-T04` Add post-use outcome event contract: shade mismatch, reaction, return
- `S06-T05` Run internal alpha

## Month 4
### Sprint 7
- `S07-T01` Add review ingestion and sentiment pipeline
- `S07-T02` Expand product enrichment jobs
- `S07-T03` Improve profile editing and inferred-attribute display
- `S07-T04` Harden observability and error budgets

### Sprint 8
- `S08-T01` Add assistant query flow
- `S08-T02` Implement search + guided recommendation prompts
- `S08-T03` Refine explanations and confidence display
- `S08-T04` Add user-facing outcome reporting in product detail and saved items
- `S08-T05` Complete invite beta prep

## Month 5
### Sprint 9
- `S09-T01` Launch invite beta
- `S09-T02` Add experimentation framework
- `S09-T03` Tune ranking using interaction data
- `S09-T04` Add moderation tools for review/community ingestion

### Sprint 10
- `S10-T01` Affiliate link instrumentation
- `S10-T02` Conversion funnel dashboards
- `S10-T03` Recommendation quality and safety outcome reviews
- `S10-T04` Privacy and trust UX pass
- `S10-T05` Pilot selfie-based undertone inference behind explicit scoped consent

## Month 6
### Sprint 11
- `S11-T01` Optimize ingestion coverage and freshness
- `S11-T02` Improve recommendation latency
- `S11-T03` Add backlog items from beta learning
- `S11-T04` Start premium packaging analysis

### Sprint 12
- `S12-T01` Production hardening
- `S12-T02` QA sweep and regression automation
- `S12-T03` Launch readiness review
- `S12-T04` Plan phase-2 features
