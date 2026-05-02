# Digital Marketing Operating System - Product Architecture Document (Revised)

**Product**: Digital Marketing Operating System (DMOS)  
**Repository Location**: `products/digital-marketing/`  
**Version**: 2.0.0 (Revised per Executive Verdict)  
**Status**: Strategic Architecture Draft - Not Implementation-Ready  
**Last Updated**: 2026-05-01

---

## Executive Summary

**Product Direction**: Strong and worth pursuing. DMOS targets the gap between fragmented martech tools, manual agencies, weak attribution, and rising AI automation.

**Architecture Status**: Strategic architecture draft, not implementation-ready. Requires hardening in four areas:
1. Repo/platform alignment verification
2. MVP scope narrowing
3. Critical execution architecture specification
4. Production-grade data and compliance model

**Core Thesis**: An AI-native operating system that plans, sells, executes, measures, optimizes, and only asks humans for judgment/risk/governance decisions.

**Critical Architecture Decision**: DMOS should not be a collection of marketing agents. It should be a governed, event-driven growth execution system where agents propose and optimize, policies decide what is safe, workflows execute durably, humans approve only meaningful risk, and every action is measurable, reversible, and auditable.

---

## 1. Vision and Positioning

### Vision Statement
An AI-native digital marketing operating system that plans, sells, executes, measures, and improves growth campaigns end to end—with human approval only where judgment, risk, or governance requires it.

### Strategic Positioning
"From growth goal to signed contract to live campaign to measurable revenue—automated."

### Differentiation
Contract-to-campaign-to-revenue closed loop, governed automation, self-marketing, and role/persona-aware action execution. NOT: another AI content tool, email automation platform, or analytics dashboard.

---

## 2. Market and Competitive Context

### Market Validation
- IAB 2025: U.S. digital advertising $300B, 13.9% YoY growth, moving to AI-powered growth
- Gartner 2025: Marketing budgets flat at 7.7% revenue, martech utilization dropped to 49%
- 81% of martech leaders piloting AI agents, but 45% say vendors fail to meet performance

### Competitive Landscape
| Competitor | Strength | DMOS Differentiation |
|-----------|----------|---------------------|
| HubSpot | CRM + marketing integration | Contract-to-revenue closed loop |
| Klaviyo | E-commerce focus | Service business focus, proposal/contract |
| Google Ads | Native ad platform | Full workflow beyond ads |

---

## 3. Target Segments, Personas, and Tiers

### Primary Beachhead (MVP)
**Non-regulated local service businesses**: Home services, consultants, coaches, small B2B. **Excluded**: Clinics, financial services, legal, insurance (require compliance packs).

### User Personas
| Persona | Primary UI | Approval Authority |
|---------|-----------|-------------------|
| Business Owner | Growth health, spend, leads, revenue | High-budget changes, contracts |
| Marketing Manager | Campaign plan, assets, approvals | Campaign launches, creative approval |
| Legal/Compliance | Claims, disclosures, contract clauses | Claims, contracts, regulated content |
| Platform Operator | Connector health, incidents | Emergency kill-switch |

### Feature Tiers
Starter (intake, audit, plan, manual export) → Growth (Google Ads integration, email, reporting) → Pro (multi-channel, experiments) → Agency (multi-client) → Enterprise (SSO, data residency).

---

## 4. MVP Scope and Non-Goals

### MVP: "AI Growth Operator for Local Service Businesses"

**One Campaign Type**: Google Search lead generation + landing page + CRM-lite + email follow-up

**One Revenue Loop**: Intake → audit → 30-day plan → landing page/ad copy/email draft → approval → launch/export → lead capture → dashboard → next action

**One Contract Loop**: Proposal/SOW draft from templates, human-approved, explicitly not legal advice

**One Analytics Level**: Basic funnel metrics and CRM outcomes; advanced attribution to Phase 3

### MVP Exclusions (Post-MVP)
Meta, LinkedIn, TikTok ads, SMS campaigns, external CRM integrations, email automation platforms, advanced attribution, industry playbooks, agency mode, enterprise features, regulated industries.

### MVP Success Metric
Customer goes from intake → approved plan → live campaign → captured lead → performance report → renewal recommendation without marketing expert. Time to first campaign < 48 hours, lead capture rate > 5%, retention > 70%, NPS > 40.

---

## 5. End-to-End Lifecycle

### Complete Domain Loop
Platform markets itself → Visitor captured → AI qualification → Proposal/contract → Customer onboarding → Market research → Strategy plan → Asset generation → Human approval → Campaign execution → Lead capture → Analytics → Optimization → Renewal.

### MVP Lifecycle Stages
1. Self-Marketing: Landing page, free audit funnel, AI chat qualification
2. Customer Acquisition: AI chat agent, lead scoring, business intake, auto-generated audit
3. Contract/Proposal: Scope builder, pricing recommender, proposal generator, contract draft generator (template-based, human-approved)
4. Market Research: Competitor research, keyword research, market map, channel plan (Google only), 30-day calendar
5. Campaign Planning: Google Search campaigns, landing page generation, ad copy, email sequence
6. Creative/Content: Brand system, landing page, ad copy variants, email sequence
7. Execution: Google Ads connector, landing page publishing, email sending
8. Analytics: Funnel metrics, conversion rate, CAC estimation, campaign summary
9. Governance: Consent management, claim validation, audit log, human approval gates, unsubscribe handling

---

## 6. Domain Model and Bounded Contexts

### Bounded Contexts
Customer, Market, Strategy, Campaign, Content, Execution, Sales, Analytics, Governance, Learning.

### Core Domain Entities (Corrected)

**Contact and Identity Context (NEW)**:
- Contact (id, firstName, lastName, email, phone, piiClassification, consentStatus)
- Account (id, organizationId, name, industry, tier)
- Identity (id, contactId, externalId, externalSystem, identityHash)
- ContactPoint (id, contactId, type, value, verified, optedOut)

**Customer Context**: Organization, Workspace, Brand, Product, Offer

**Market Context**: Segment, Persona, Competitor, Keyword, MarketingChannelDefinition

**Strategy Context**: Goal, KPI, Budget, Positioning, CampaignPlan

**Campaign Context**: Campaign, CampaignChannelExecution, Audience, Creative, ContentVersion, Schedule

**Content Context**: Asset, LandingPage, Email, Template, Clause

**Execution Context (NEW - Durable Workflows)**:
- WorkflowDefinition (id, name, version, steps, retryPolicy, rollbackPlan)
- WorkflowExecution (id, definitionId, status, currentState, startedAt)
- Command (id, workflowId, type, payload, status, result, rollbackInfo)
- CommandResult (id, commandId, success, externalIds, warnings)
- ApprovalSnapshot (id, commandId, approvedBy, approvedAt, contentSnapshot, policySnapshot)
- RollbackPlan (id, commandId, steps, status, executedAt)

**Sales Context**: Lead, Opportunity, Proposal, Contract, Invoice

**Analytics Context (NEW - Touchpoint Model)**:
- Event (id, eventType, schemaVersion, timestamp, correlationId, causationId, idempotencyKey)
- Touchpoint (id, sessionId, type, channelId, externalRefId)
- Session (id, contactId, startedAt, endedAt, device, utmParams)
- ConversionEvent (id, touchpointId, value, attributedTo, modelVersion)
- Attribution (id, conversionId, touchpoints, weights, model, confidence)
- Experiment (id, campaignId, variants, hypothesis, duration, statisticalMethod)

**Governance Context (Enhanced)**:
- Consent (id, contactId, type, channel, lawfulBasis, source, proof, region, policyVersion, grantedAt, revokedAt, revocationReason)
- ConsentBasis (id, consentId, basisType, sourceDocument, timestamp)
- SuppressionList (id, workspaceId, type, scope, reason, entries)
- DoNotContactRule (id, workspaceId, criteria, scope, reason)
- Policy (id, workspaceId, type, region, rules, version, active)
- Claim (id, contentId, text, evidenceId, approvalStatus, riskLevel)
- Evidence (id, claimId, sourceUrl, sourceFile, reviewer, expiration)
- Disclosure (id, contentId, type, required, addedBy)
- AuditLog (id, action, actor, timestamp, changes, correlationId, policySnapshotId, consentSnapshotId)

**Learning Context (Enhanced)**:
- Playbook (id, workspaceId, industry, steps, successRate, versionId)
- PlaybookVersion (id, playbookId, version, changes, promotedBy)
- ExperimentResult (id, experimentId, variant, metrics, statisticalSignificance)
- Recommendation (id, campaignId, type, priority, expectedImpact, agentId)
- ExperimentLearning (id, experimentId, finding, confidence, actionability)
- PromotionDecision (id, learningId, promotedToPlaybookId, promotedBy)

**Integration Context (NEW)**:
- ExternalConnection (id, workspaceId, platform, accountName, oauthScope, secretId, status, health)
- ExternalObjectMapping (id, connectionId, localType, localId, externalType, externalId, mappingType)
- ConnectorAccount (id, workspaceId, platform, accountId, quota, rateLimit, lastSync)

### Event Schema (Typed, Versioned)
Required fields: eventId, eventType, schemaVersion, tenantId, workspaceId, actor, actorType, correlationId, causationId, idempotencyKey, occurredAt, sourceService, externalRefs, policySnapshotId, consentSnapshotId, piiClassification, payloadSchema, payload.

---

## 7. Event, Command, and Workflow Architecture

### Event-Driven Architecture
- Outbox pattern for exactly-once-ish event publishing
- Idempotency keys for duplicate detection
- Schema versioning for backward compatibility
- Correlation IDs for distributed tracing
- Inbox pattern for idempotent event consumption
- Dead letter queue for failed events
- Retry with exponential backoff

### Command Architecture
**Flow**: Agent → Plan/Recommendation → Policy Check → Approval Gate → Command → Connector/Service → Event → Analytics/Learning

**Command Types**: LaunchCampaignCommand, PublishLandingPageCommand, UpdateBudgetCommand, SendEmailCommand, CreateProposalCommand, GenerateContractCommand

**Command Result**: Success/failure, external IDs, warnings, rollback information, execution duration.

### Durable Workflow Architecture
**Workflow States**: PENDING, RUNNING, PAUSED, COMPLETED, FAILED, ROLLED_BACK, CANCELLED

**Retry Policy**: Exponential backoff, max retry count per command type, dead letter queue.

**Rollback Plan**: Pause campaign, revert budget, unpublish page, suppress email, record rollback event.

### Kill Switch Architecture
**Levels**: Campaign-level, Workspace-level, Tenant-level, Global

**Triggers**: Manual operator action, budget exhaustion, compliance violation, connector health failure, rate limit exhaustion, security incident.

---

## 8. Agent Architecture and Permission Model

### Agent Taxonomy
| Agent | Type | Permissions |
|-------|------|-------------|
| Growth Strategist | PLANNING | Read: market data, Write: strategy plans |
| Market Research | HYBRID | Read: external APIs, Write: research data |
| Brand | DETERMINISTIC | Read: brand guidelines, Write: content validation |
| Creative | PROBABILISTIC | Write: creative drafts (requires approval) |
| Media Buyer | ADAPTIVE | Read: campaign data, Write: bid adjustments (with budget authority) |
| Compliance | DETERMINISTIC | Read: all content, Write: compliance flags |
| Analytics | HYBRID | Read: all data, Write: analytics reports |
| Optimization | ADAPTIVE | Read: performance data, Write: optimization proposals |

### Agent Contract (Required Fields)
Capabilities, Permissions (read/write scopes by tenant/workspace/channel/budget), Confidence Model (auto-execute vs escalate), Evidence Requirements, Tool Allowlist, Budget Authority (hard cap and delta threshold), Rollback Support, Evaluation Suite, Prompt/Model Version, Memory Policy.

### Permission Scopes
tenant:read/write, workspace:read/write, campaign:read/write/launch, budget:read/write/approve, content:read/write/approve, connector:read/write, compliance:read/write.

### Agent Implementation
Implement kernel's `AgentOrchestrator.KernelAgent` interface. Agents return recommendations, not direct actions. Commands created after policy check and approval.

### Human Involvement Model
Low risk: auto-execute. Medium risk: notify + allow override. High risk: require human approval.

---

## 9. Integration/Connector Runtime

### Connector Responsibilities
OAuth authentication and token management, rate limiting and adaptive throttling, idempotency and retry logic, external ID mapping, health monitoring, error categorization.

### Google Ads Connector (MVP)
Campaign creation/modification, ad group creation/modification, ad copy creation/modification, bid management, budget management, performance data sync, conversion tracking setup.

### Rate Limiting
Per-platform rate limit configuration, adaptive throttling based on 429 responses, queue-based request management, operator-visible rate limit status.

### Health Monitoring
Authentication status, rate limit status, last successful sync timestamp, error rate by operation type, queue depth. Health statuses: HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN.

---

## 10. Consent-First Data Collection and Measurement

### Consent Mode Architecture
Consent banner implementation, consent categories (necessary, analytics, marketing), granular consent per channel, consent expiration and renewal, tag governance (consent-dependent tag firing), server-side tag collection with privacy filters.

### Enhanced Conversions
Capture consented first-party data, hash data before sending to external systems, match consented data for attribution where allowed, store consent proof with hashed data.

### Suppression Enforcement
Global suppression, channel-specific suppression, campaign-specific suppression, temporary suppression. Enforcement before email send, SMS send, ad audience sync, CRM export.

### Data Subject Request Workflows
Export, delete, correct, restrict/limit, opt out. Verify identity, collect data, apply consent/policy restrictions, generate response, execute action, audit request.

### Purpose Limitation
Every audience export tagged with purpose, every action tagged with campaign purpose, consent validated against purpose, purpose changes require re-consent.

### Consent Proof Storage
Source form/URL, timestamp, consent text shown, policy version, IP and user agent, consent method.

### Regional Policy Execution
MVP: US (CAN-SPAM), EU (GDPR, ePrivacy), UK (GDPR, PECR), Canada (CASL). Region detection, apply regional rules automatically, block non-compliant actions.

---

## 11. Analytics, Attribution, Experimentation, and Learning

### Analytics Architecture (MVP)
Funnel metrics: impressions, clicks, landing page views, form submissions, leads captured, conversion rate, CPL, CPA. Post-MVP: multi-touch attribution, ROAS, LTV, payback period, revenue influenced.

### Attribution Model
MVP: Last-click attribution (simple, defensible). Post-MVP: first-click, linear, time-decay, position-based, data-driven.

### Touchpoint Model
Session tracking, UTM parameter capture, click ID tracking, device and browser tracking, geographic tracking.

### Experimentation Design
Hypothesis statement, minimum sample size calculation, statistical method, winner criteria, learning record. A/B tests, budget allocation tests, audience targeting tests, channel comparison tests.

### Learning and Playbooks
Industry-specific best practices, campaign templates, budget allocation patterns, creative templates, targeting strategies. Playbook versioning, promotion workflow from experiment to playbook, approval for playbook promotion.

---

## 12. Governance, Compliance, Privacy, and Audit

### Governance Framework
Platform-level (kernel security, tenant isolation, audit trail), Product-level (marketing-specific compliance, approval gates, brand safety), Integration-level (platform-specific policies).

### Compliance Controls
**Consent Management**: Purpose limitation, data minimization, consent revocation, regional policies. Use plugin-consent for generic consent lifecycle engine. Regional regulatory rules (GDPR, CCPA, CAN-SPAM, HIPAA, etc.) must be in DigitalMarketingComplianceRulePack, following the pack-driven plugin model.

**Claim/Disclosure Management**: Validation with evidence, endorsements, AI labeling, regulated industry rules, brand safety. Use plugin-compliance for generic rule evaluation; specific regulatory rules in DigitalMarketingComplianceRulePack.

**Audit Trail**: Action logging, change tracking, correlation IDs, tamper evidence. Use plugin-audit-trail for generic audit logging.

**Human Approval Gates**: Risk-based triggers, workflows, timeout handling, override mechanism. Use plugin-human-approval for generic approval workflow; product-specific approval rules in DigitalMarketingComplianceRulePack.

### Campaign Safety Guardrails (Preflight Checklist)
Tracking (conversion events, UTM, click IDs, consent banner, destination URL), Budget (daily/monthly cap, customer-approved maximum, pacing), Creative (brand validation, claim validation, forbidden claims, disclosures), Audience (region, age restrictions, consent/audience source, exclusion lists), Compliance (channel policy, industry policy, privacy policy, unsubscribe), Technical (landing page availability, form works, CRM mapping), Rollback (pause/unpublish/suppress instructions exist), Human Approval (required approvals captured).

### Brand Governance
Voice and tone guidelines, approved claims library, forbidden claims blacklist, design token validation, multi-brand support.

---

## 13. UX Model: Dashboard, Approvals, Transparency

### Dashboard Design
Dashboard-first UX, low cognitive load, action-oriented. Elements: growth health score, current goal, active campaigns, AI actions, needs approval, results, next best actions, risk/compliance.

### Approval UX
Approval queue (filterable by risk/type/urgency), one-click approve/reject, comment required for rejection, approval history, escalation for overdue approvals. Approval detail view: content with version, risk level and rationale, policy snapshot, evidence for claims, rollback plan, approver guidance.

### Transparency
Action transparency (what AI did and why, confidence scores, evidence sources, policy checks, human decisions), Performance transparency (campaign performance, agent recommendation acceptance rate, human override rate, automation rate).

### Override/Delegation
Override mechanism (human can override any AI decision, requires reason, logged for learning), Delegation mechanism (delegate approval authority, delegation rules, delegation expiration, delegation audit).

---

## 14. Security, Tenancy, Secrets, Data Retention

### Security Model
Authentication (SSO for enterprise, email/password for starter/growth, MFA for sensitive operations), Authorization (RBAC/ABAC, permission scopes, tenant isolation, workspace isolation), Data classification (public, internal, confidential, restricted).

### Tenancy Model
Tenant isolation (database-level isolation, API-level isolation, resource-level isolation), Workspace model (multiple workspaces per tenant, workspace-level isolation, workspace-specific roles).

### Secrets Management
OAuth tokens, API keys, database credentials, encryption keys. Encrypted at rest, access logging, rotation policies, revocation support.

### Data Retention
Campaign data: 2 years minimum, audit logs: 7 years minimum (compliance), PII: per consent and regional requirements, analytics data: configurable per workspace.

### Data Subject Requests
Export (30 days), Delete (30 days), Correct (30 days), Restrict/Limit (30 days), Opt out (10 days - CAN-SPAM).

---

## 15. Observability and Operations

### Observability Stack
Metrics (business, technical, agent, connector), Logging (structured logs with correlation IDs, log levels, sensitive data redaction), Tracing (distributed tracing with correlation IDs, request/response tracking, agent execution tracing, workflow execution tracing).

### Health Monitoring
Service health (API endpoints, database connectivity, event bus connectivity, connector health), Business health (campaign execution, lead capture, budget pacing, compliance).

### Incident Response
Incident types (service outage, data breach, compliance violation, connector failure, budget overrun). Process: detection, triage, response, communication, post-mortem.

### Kill Switch Implementation
Triggers (manual, budget exhaustion, compliance violation, connector failure, rate limit exhaustion, security incident). Actions (pause campaign, pause workspace campaigns, pause tenant campaigns, global stop).

---

## 16. Module/Repo Alignment Matrix

### Platform Architecture Verification

**Status**: Architecture assumes target platform abstractions that must be verified against current Ghatana repo before implementation.

| Architecture Concept | Verified Existing Concept | Action Required |
|---------------------|-------------------------|-----------------|
| Platform Kernel | platform-kernel/ (verified) | Use kernel lifecycle and capability abstractions |
| AgentOrchestrator | Not in CAPABILITY_MATRIX | Verify if exists or use AEP/DataCloud bridge pattern |
| KernelEventBus | Not in CAPABILITY_MATRIX | Verify or use AEP event publisher |
| plugin-consent | plugin-consent (verified) | Use for consent management |
| plugin-compliance | plugin-compliance (verified) | Use for compliance rule evaluation |
| plugin-audit-trail | plugin-audit-trail (verified) | Use for audit trail |
| plugin-human-approval | plugin-human-approval (verified) | Use for human-in-the-loop approvals |
| Data Cloud adapter | DataCloudKernelAdapterImpl (verified) | Use for data persistence via kernel bridge |

### Domain Pack Model (Required)

Per PRODUCT_DEVELOPMENT_GUIDE.md, Digital Marketing must implement:

**BoundaryPolicyStore**: Product-specific boundary policy rules
- Last rule must be default-deny covering "**" source and digital-marketing scope
- Rule IDs prefixed with "DM-" (e.g., DM-BP-001)
- Read operations requiring consent set `.requiresConsent(true)`
- Operations requiring audit trail set `.requiresAudit(true)`

**ComplianceRulePack**: Product-specific compliance rules registered with plugin-compliance
- Rule IDs prefixed with "DM-" (e.g., DM-DI-001)
- Rule set ID constants unique across platform
- Registered at product startup, not kernel boot time
- Regulatory rules (GDPR, CCPA, CAN-SPAM, HIPAA) belong here, not in kernel/plugins

### Kernel Bridge Pattern (Required for Integrations)

Per PRODUCT_DEVELOPMENT_GUIDE.md, Digital Marketing integrations must use AbstractKernelBridge:

**Bridge Context Requirements**: Every bridge call must carry BridgeContext with:
- `tenantId` (required) - Tenant isolation
- `principalId` - Audit and authorization (defaults to "anonymous")
- `correlationId` - Distributed tracing (defaults to "none")
- `idempotencyKey` - At-most-once writes (nullable for reads)

**Bridge Pattern**: Extend AbstractKernelBridge with:
- BridgeAuthorizationService for authorization
- BridgeAuditEmitter for audit logging
- BridgeHealthIndicator for health reporting
- `requireStarted()` at top of adapter methods
- `checkAuthorized()` before sensitive operations
- `executeWithRetry()` for transient failures
- `redact()` for sensitive metadata in logs

### Required Verification Steps
1. Audit current Ghatana repo for actual module names and paths
2. Map DMOS architecture concepts to verified repo concepts
3. Identify gaps where new modules or adapters are required
4. Update architecture document with verified module names and paths
5. Create adapter layer if mapping requires translation
6. Implement Domain Pack Model (BoundaryPolicyStore, ComplianceRulePack)
7. Use Kernel Bridge pattern (AbstractKernelBridge) for integrations
8. Follow kernel/plugin purity rules (no product-domain identifiers in kernel/plugins)

**Until verification is complete, remove statement "Deviation from Generic Patterns: None." Accurate statement: "Architecture assumes target platform abstractions that must be verified against current Ghatana repo before implementation. Regulatory frameworks (GDPR, CCPA, CAN-SPAM, HIPAA, etc.) must be implemented in Digital Marketing compliance rule packs, not in kernel or generic architecture."**

---

## 17. Delivery Roadmap

### Phase 1: Foundation (Months 1-3) - MVP Focus
Product shell (workspace, org, users, roles), Brand profile, Intake (business profile, growth goal), Market research (competitor, keyword), Plan generator (30-day strategy), Asset generator (landing page, ad copy, email), Approval workflow, Basic analytics, Consent foundation.

**Technical Milestones**: Verified platform framework integration, agent framework integration, basic web app with authentication, first end-to-end campaign flow (intake → plan → content → approval), consent management foundation.

### Phase 2: Execution (Months 4-6) - MVP Completion
Google Ads connector (OAuth, campaign creation, bid management, performance sync), Landing page publishing (Webflow/WordPress), Email sending (internal or SendGrid), CRM-lite (lead capture, status tracking), Proposal engine (scope, pricing, proposal PDF), Contract drafts (template-based SOW), Audit log (full trace), Durable workflows (command execution, rollback, DLQ), Preflight checklist (campaign launch safety).

**Technical Milestones**: Google Ads integration end-to-end, first automated campaign launch, proposal to contract workflow, comprehensive audit trail, durable workflow execution, preflight campaign safety.

### Phase 3: Autonomous Optimization (Months 7-9)
Experiment engine (A/B tests, creative variants, budget shifts), Basic attribution (first-click/last-click), Recommendation engine (next-best-action queue), Budget pacing (alerts, autonomous adjustments), AI performance reviews (weekly/monthly reports), Learning system (playbook versioning, experiment learning).

**Technical Milestones**: A/B testing framework, attribution modeling, autonomous budget optimization, performance reporting automation, playbook learning system.

### Phase 4: Platformization (Months 10-12)
Multi-channel (Meta, LinkedIn), External CRM (HubSpot, Salesforce), Email automation (Mailchimp, Klaviyo), Agency mode (multi-client, white-label), Advanced attribution (multi-touch), Industry playbooks (SaaS, healthcare, legal), Enterprise features (SSO, data residency, custom policies).

**Technical Milestones**: Multi-platform ad integrations, external CRM integrations, multi-tenant agency mode, enterprise security features, industry-specific playbooks.

### Phase 5: Ecosystem Expansion (Months 13+)
Self-marketing scale, Advanced AI (custom model training), Predictive analytics (forecasting, prescriptive), Voice/video AI, Community, API platform, Marketplace.

---

## 18. Test Strategy and Quality Gates

### Test Coverage Requirements
**Stricter Standard**: 100% coverage for changed/touched critical code and workflows, not just 80% general coverage.

**Test Types**: Unit tests (business logic), Integration tests (boundaries), Connector contract tests (API integrations), API E2E tests (API workflows), UI E2E tests (user journeys), Workflow replay tests (durable workflows), Permission/security tests (access control), Compliance scenario tests (regulatory requirements), Pack contract tests (BoundaryPolicyStore, ComplianceRulePack).

**Pack Contract Tests (Required per PRODUCT_DEVELOPMENT_GUIDE)**:
- BoundaryPolicyStore.loadRules() returns non-empty, well-formed rules
- Last rule is default-deny
- Key rules have expected effects (e.g., sensitive read requires consent + audit)
- Compliance rule packs are non-empty with prefixed rule IDs
- Store does not extend any kernel implementation class
- Pack classes use only kernel public interfaces

### Quality Gates
**Pre-Commit**: All TypeScript fully typed (no `any`), ESLint zero warnings, Prettier applied, unit tests pass.

**Pre-Merge**: All test types pass, type checking with strict mode, security scan passes, compliance scan passes.

**CI Gates (Required per CAPABILITY_MATRIX.md)**:
- Kernel purity check (if touching kernel code)
- Plugin purity check (if touching plugin code)
- Domain pack manifest validation
- Policy pack validation (BoundaryPolicyStore)
- Compliance rule pack validation
- Architecture tests (ArchUnit)
- Pack contract tests (*PackContractTest)

**Pre-Release**: Load testing meets performance targets, security penetration testing, compliance audit, documentation complete.

### Success Metrics (Revised)
**Keep**: Time to first campaign, lead capture rate, retention, NPS, campaign performance, agent/API latency, integration success rate.

**Add**: Time to first approved plan, time to first measurable conversion, approval latency, automation rate (percent actions auto-executed safely), human override rate, agent recommendation acceptance rate, claim rejection rate, tracking health score, consent coverage, attribution coverage, cost per qualified lead, lead-to-opportunity conversion, revenue influenced, rollback/incident count, connector freshness lag, data deletion/export SLA, experiment learning velocity.

---

## 19. Risks and Mitigations

### Technical Risks
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Platform architecture mismatch | High | High | Verify platform abstractions before implementation, create adapters |
| Platform API rate limits | High | Medium | Adaptive rate limiting, queue-based execution |
| Integration breaking changes | Medium | High | Version adapters, comprehensive integration tests |
| Agent hallucinations | Medium | High | Human approval gates, claim validation, brand safety |
| Durable workflow complexity | Medium | High | Use proven workflow engine, comprehensive testing |
| Scalability bottlenecks | Medium | Medium | Load testing, horizontal scaling design |

### Business Risks
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Market adoption slower | Medium | High | Narrow beachhead, iterate based on feedback |
| Competition from established players | High | Medium | End-to-end automation and governance differentiation |
| Customer churn due to complexity | Medium | High | Dashboard-first UX, progressive disclosure |
| Pricing misalignment | Medium | Medium | Flexible pricing models |
| Regulatory changes | Low | High | Compliance-first design, regional policy engine |

### Operational Risks
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Data privacy violations | Low | Critical | Consent management, data minimization, audits |
| Security breach | Low | Critical | Security-first design, penetration testing, incident response |
| Vendor lock-in (ad platforms) | High | Medium | Multi-platform support, data portability |
| Talent acquisition | Medium | Medium | Leverage platform team expertise |
| Customer support overload | Medium | Medium | Self-service documentation, AI-powered support |

### Compliance Risks
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| FTC/Regulatory enforcement | Low | Critical | Legal review, regular compliance audits |
| GDPR/CCPA violations | Medium | High | Consent management, data deletion/export, regional policies |
| CAN-SPAM/TCPA violations | Medium | High | Email/SMS compliance checks, unsubscribe handling |
| Platform-specific policy violations | Medium | Medium | Platform policy monitoring, automated compliance |
| SMS TCPA violations | Medium | High | SMS excluded from MVP until consent model strong |

---

## 20. Epics and Acceptance Criteria

### Epic E1: Customer Workspace + Brand Profile
Workspace creation with tenant isolation, brand profile with voice guidelines, asset library with version control, product catalog with offer definitions, geographic targeting with regional policy selection.

### Epic E2: AI Intake + Free Audit
AI chat agent with business goal questionnaire, automated audit generation, lead scoring model, consent capture with lawful basis recording, audit-to-proposal conversion workflow.

### Epic E3: Strategy Generator
Goal-to-strategy mapping, competitor research integration, single-channel strategy (Google Search) with rationale, budget allocation by campaign/ad group with ROI projections, Gantt-style execution calendar.

### Epic E4: Proposal/SOW Generator
Strategy-to-proposal conversion, pricing model calculator, template-based SOW generation with version control, risk flagging system, e-signature integration.

### Epic E5: Landing Page + Email Generator
Landing page generation with conversion optimization, ad copy generation with A/B variants, email sequence generation with drip logic, brand system validation, compliance validation.

### Epic E6: Approval Workflow
Approval queue with filtering and prioritization, role-based approval routing, approval history with comments, risk-based auto-approval rules, escalation for overdue approvals, approval snapshots.

### Epic E7: Lead Capture + CRM-Lite
Lead capture from landing pages and forms, lead status workflow with stage transitions, single-touch attribution (MVP), suppression list enforcement before contact, lead scoring model, contact identity model with PII separation.

### Epic E8: Analytics Dashboard
Campaign performance dashboard with drill-down, ROI/ROAS calculation with trend analysis, last-click attribution (MVP), recommendation engine with priority scoring, scheduled report generation, touchpoint and session tracking.

### Epic E9: Consent Foundation
Consent banner with granular categories, consent recording with lawful basis/source/proof, unsubscribe handling with suppression lists, consent proof storage, consent enforcement before email sends and ad audience sync.

### Epic E10: Google Ads Integration
OAuth authentication with Google Ads API, campaign creation (search campaigns), performance data sync, bid optimization with budget pacing, error handling with retry logic and DLQ, external ID mapping, rate limit handling with adaptive throttling.

### Epic E11: Durable Workflow Execution
Workflow definition with versioning, workflow execution with state persistence, command execution with idempotency, retry logic with exponential backoff, dead letter queue, rollback plan execution, kill switch at multiple levels, workflow execution status visibility.

### Epic E12: Preflight Campaign Safety
Preflight checklist implementation (tracking, budget, creative, audience, compliance, technical, rollback, approval), preflight check results display, automatic blocking for failed checks, manual override with approval and audit trail, preflight result logging.

### Epic E13: Self-Marketing Tenant Isolation
Separate tenant for platform self-marketing, data isolation between platform and customer tenants, platform marketing campaigns using DMOS engine, case study generation from platform's own data, performance transparency.

---

## Final Recommendation

Treat this document as a strong strategic architecture draft, not as final implementation authority. The product idea is valid, timely, and differentiated, but the document should be revised before coding to:

**P0 - Must Fix Before Implementation**:
1. Add Repo/Platform Alignment Matrix and verify exact Ghatana repo symbols/modules
2. Narrow MVP to one beachhead, one paid channel, one landing page flow, one CRM-lite flow, one reporting loop
3. Add durable workflow, command, outbox/inbox, retry, DLQ, rollback, and kill-switch architecture
4. Replace direct agent execution with permissioned command execution
5. Fix data model with Contact, ConsentBasis, SuppressionList, ExternalConnection, ExternalObjectMapping, Touchpoint, ContentVersion, ApprovalSnapshot, Template/Clause
6. Make consent-first measurement a core architecture section
7. Add preflight campaign launch checklist
8. Remove regulated industries from MVP unless compliance packs are implemented

**P1 - Should Add Before V1**:
Persona/role/tier access model, connector runtime with OAuth scopes/secret storage/rate limiting/health/replay/external ID mapping, experimentation design, self-marketing tenant isolation, content provenance and claim evidence lifecycle, cost controls for AI and ad spend, playbook versioning and promotion workflow.

**P2 - Later Hardening**:
Agency/multi-client mode, marketplace for templates/playbooks/connectors, advanced attribution and media mix modeling, multi-region/data residency, industry compliance packs, advanced AI evaluation and model feedback loops.

**Most Important Architecture Decision**: DMOS should not be a collection of marketing agents. It should be a governed, event-driven growth execution system where agents propose and optimize, policies decide what is safe, workflows execute durably, humans approve only meaningful risk, and every action is measurable, reversible, and auditable.
