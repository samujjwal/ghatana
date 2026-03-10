# Event Schema Catalog

This catalog defines canonical events across the lifecycle.
Recommended envelope fields for all events:

- eventId
- eventType
- eventVersion
- timestamp
- workspaceId
- projectId
- correlationId
- causationId
- actorType
- actorId
- persona
- source
- payload
- metadata
- audit
- tags

---

## 1. Workspace / Identity Events
- workspace.created
- workspace.updated
- workspace.archived
- workspace.member_added
- workspace.member_removed
- workspace.role_changed
- workspace.persona_assigned
- project.created
- project.updated
- project.archived
- session.started
- session.ended

Payload examples:
- workspaceId
- ownerId
- memberId
- role
- persona
- projectType
- timestamp

---

## 2. Discovery Events
- discovery.started
- market_scan.completed
- competitor_analysis.completed
- customer_insight.created
- opportunity.identified
- opportunity.scored
- opportunity.rejected
- problem_statement.created

Payload:
- insightRefs
- sourceRefs
- confidence
- segments
- problemStatement
- opportunityScore

---

## 3. Ideation Events
- ideation.started
- concept.generated
- concept.refined
- value_proposition.created
- mvp.scope_defined
- product_narrative.created
- concept.approved
- concept.rejected

Payload:
- conceptId
- summary
- valueHypothesis
- targetPersonas
- featureCandidates
- status

---

## 4. Requirements Events
- requirement.submitted
- requirement.parsed
- requirement.normalized
- requirement.classified
- requirement.linked_to_source
- requirement.duplicate_detected
- requirement.conflict_detected
- requirement.approved
- requirement.rejected
- requirement.version_created
- acceptance_criteria.generated
- traceability.link_created
- spec.exported

Payload:
- requirementId
- projectId
- sourceType
- sourceRef
- requirementType
- priority
- status
- tags
- confidence
- duplicateOf
- dependsOn
- acceptanceCriteriaIds

---

## 5. UX / UI Events
- persona.created
- journey.created
- ux_flow.created
- screen_spec.created
- wireframe.created
- accessibility.issue_detected
- design_token.aligned
- canvas_interaction.updated
- navigation_model.updated

Payload:
- flowId
- screenId
- componentRefs
- accessibilitySeverity
- layoutMetadata
- interactionType

---

## 6. Architecture Events
- domain_model.created
- service_boundary.defined
- api_contract.created
- api_contract.validated
- graphql_schema.created
- event_schema.created
- event_schema.versioned
- plugin_contract.created
- plugin_compatibility.checked
- observability_plan.created
- security_control.defined
- memory_architecture.defined

Payload:
- artifactId
- schemaType
- version
- compatibilityStatus
- serviceName
- eventName
- controlRefs
- memoryTier

---

## 7. Delivery Planning Events
- epic.created
- story.created
- story.refined
- story.approved
- sprint.planned
- sprint.scope_changed
- dependency.recorded
- risk.identified
- milestone.updated

Payload:
- epicId
- storyId
- sprintId
- estimate
- dependencies
- riskLevel
- milestoneDate

---

## 8. Engineering Events
- feature.implementation_started
- feature.implementation_completed
- route.created
- component.created
- service.created
- resolver.created
- migration.generated
- migration.applied
- permission_rule.updated
- audit_log.recorded
- export.generated

Payload:
- module
- fileRefs
- migrationId
- permissionMatrix
- exportType
- resultStatus

---

## 9. AI / Search / Memory Events
- retrieval.requested
- retrieval.completed
- prompt.constructed
- model.invoked
- model.response_received
- ai_output.validated
- hallucination.flagged
- semantic_cache.hit
- semantic_cache.miss
- memory.write_requested
- memory.written
- memory.retrieved
- memory.consolidated
- reflection.completed

Payload:
- query
- contextRefs
- modelName
- tokenUsage
- latencyMs
- confidence
- validationFlags
- memoryKey
- memoryTier
- cacheKey

---

## 10. Testing Events
- test.unit_generated
- test.unit_passed
- test.unit_failed
- test.integration_generated
- test.integration_passed
- test.integration_failed
- test.e2e_generated
- test.e2e_passed
- test.e2e_failed
- contract_test.failed
- coverage.updated
- mutation_score.updated
- quality_gate.passed
- quality_gate.failed

Payload:
- suiteId
- testCaseId
- coveragePercent
- mutationScore
- failureType
- traceLinks
- gateDecision

---

## 11. Security / Compliance Events
- threat_model.created
- vulnerability.detected
- vulnerability.resolved
- secret.exposed
- permission.anomaly_detected
- policy.violation_detected
- audit_evidence.collected
- compliance.mapping_created
- retention_rule.applied
- consent.state_changed
- security_incident.opened
- security_incident.closed

Payload:
- vulnId
- severity
- controlId
- policyId
- evidenceRefs
- incidentId
- retentionClass
- consentState

---

## 12. Release / Deployment Events
- build.started
- build.completed
- artifact.published
- deployment.started
- deployment.completed
- deployment.failed
- smoke_test.passed
- smoke_test.failed
- canary.promoted
- canary.reverted
- rollback.executed
- release.notes_generated

Payload:
- buildId
- artifactVersion
- environment
- deploymentId
- healthStatus
- rollbackRef
- releaseNotesRef

---

## 13. Operations / SRE Events
- monitor.alert_fired
- incident.acknowledged
- incident.classified
- rca.started
- rca.completed
- recovery.started
- recovery.completed
- backup.verified
- slo.burn_detected
- support.ticket_created
- support.ticket_classified
- support.issue_synthesized

Payload:
- incidentId
- alertId
- severity
- impactedServices
- symptomRefs
- rootCause
- recoveryAction
- ticketId
- supportTheme

---

## 14. Product Intelligence / Enhancement Events
- analytics.ingested
- feature_adoption.updated
- funnel.updated
- retention.updated
- feedback.collected
- recommendation.generated
- experiment.started
- experiment.completed
- enhancement.identified
- backlog.reprioritized
- roadmap.adjusted
- decision.captured
- knowledge.canonicalized

Payload:
- metricName
- value
- experimentId
- significance
- recommendationId
- enhancementScore
- decisionRef
- knowledgeRef

---

## 15. Agent Runtime Events
- agent.requested
- agent.started
- agent.progress
- agent.blocked
- agent.completed
- agent.failed
- agent.retried
- agent.escalated
- agent.cancelled
- agent.approved
- agent.rejected
- agent.rolled_back
- tool.requested
- tool.completed
- tool.failed

Payload:
- agentName
- agentType
- parentAgent
- toolName
- inputRef
- outputRef
- status
- failureReason
- retryCount
- humanInLoop

---

## 16. Minimal Event JSON Example

{
  "eventId": "evt_01",
  "eventType": "requirement.normalized",
  "eventVersion": "1.0.0",
  "timestamp": "2026-03-10T15:00:00Z",
  "workspaceId": "ws_123",
  "projectId": "prj_456",
  "correlationId": "corr_789",
  "causationId": "evt_prev",
  "actorType": "agent",
  "actorId": "RequirementNormalizationTaskAgent",
  "persona": "business_analyst",
  "source": "requirements-service",
  "payload": {
    "requirementId": "req_111",
    "requirementType": "functional",
    "priority": "high",
    "confidence": 0.92
  },
  "metadata": {
    "latencyMs": 120
  },
  "audit": {
    "record": true
  },
  "tags": ["requirements", "normalization"]
}