# Lifecycle Phase to Agent Matrix

This maps lifecycle stages to key orchestrators, main agents, and outputs.

---

## Phase 0: Workspace / Foundation Setup
Orchestrators:
- PlatformArchitectureOrchestrator
- EnvironmentOrchestrator
- GovernanceOrchestrator

Main Agents:
- EnvironmentBootstrapTaskAgent
- TenantIsolationTaskAgent
- RBACArchitectureTaskAgent
- AuditArchitectureTaskAgent
- SecretManagementCapabilityAgent

Outputs:
- workspace structure
- role/persona model
- baseline environment
- auditability foundation

---

## Phase 1: Discovery
Orchestrators:
- DiscoveryOrchestrator
- MarketResearchOrchestrator
- CustomerInsightOrchestrator

Main Agents:
- MarketTrendScanTaskAgent
- CompetitorFeatureComparisonTaskAgent
- InterviewSynthesisTaskAgent
- ProblemStatementTaskAgent
- OpportunityHypothesisTaskAgent

Outputs:
- problem map
- market insight pack
- opportunity list
- research evidence

---

## Phase 2: Ideation
Orchestrators:
- IdeationOrchestrator
- ProductFramingOrchestrator

Main Agents:
- ProductConceptTaskAgent
- ValuePropositionTaskAgent
- ProductNarrativeTaskAgent
- MVPDefinitionTaskAgent
- DifferentiationReviewTaskAgent

Outputs:
- product concepts
- value proposition
- MVP scope
- product framing brief

---

## Phase 3: Strategy & Roadmap
Orchestrators:
- ProductStrategyOrchestrator
- DeliveryPlanningOrchestrator

Main Agents:
- OKRDefinitionTaskAgent
- KPISelectionTaskAgent
- InitiativeSequencingTaskAgent
- RoadmapPlanningAgent
- BudgetEnvelopeTaskAgent

Outputs:
- roadmap
- KPI set
- milestones
- investment priorities

---

## Phase 4: Requirements & Specification
Orchestrators:
- RequirementsOrchestrator
- SpecificationOrchestrator
- TraceabilityOrchestrator

Main Agents:
- RequirementCaptureTaskAgent
- RequirementNormalizationTaskAgent
- FunctionalRequirementDraftingTaskAgent
- NFRDraftingTaskAgent
- AcceptanceCriteriaTaskAgent
- RequirementToStoryTraceTaskAgent
- RequirementToTestTraceTaskAgent

Outputs:
- structured requirements
- acceptance criteria
- traceability graph
- versioned specs

---

## Phase 5: UX / UI Design
Orchestrators:
- UXOrchestrator
- UIOrchestrator
- DesignSystemOrchestrator

Main Agents:
- PersonaDefinitionTaskAgent
- UserJourneyTaskAgent
- WireframeTaskAgent
- NavigationModelTaskAgent
- AccessibilityReviewTaskAgent
- CanvasToolingUXTaskAgent

Outputs:
- user journeys
- screen flows
- wireframes
- interaction specs
- design constraints

---

## Phase 6: Architecture & Technical Design
Orchestrators:
- ArchitectureOrchestrator
- PluginArchitectureOrchestrator
- RuntimeArchitectureOrchestrator

Main Agents:
- DomainModelTaskAgent
- ServiceBoundaryTaskAgent
- APIContractTaskAgent
- EventSchemaTaskAgent
- PluginLifecycleTaskAgent
- AgentMemoryDesignTaskAgent
- ObservabilityPlanTaskAgent

Outputs:
- HLD / LLD
- API contracts
- event contracts
- plugin contracts
- runtime architecture

---

## Phase 7: Engineering Planning
Orchestrators:
- AgileExecutionOrchestrator
- DeliveryPlanningOrchestrator

Main Agents:
- EpicCreationTaskAgent
- StoryDraftingTaskAgent
- TaskBreakdownTaskAgent
- SprintScopeTaskAgent
- DeliveryRiskTaskAgent

Outputs:
- epics
- stories
- sprint plans
- dependency map
- delivery risk register

---

## Phase 8: Implementation
Orchestrators:
- EngineeringOrchestrator
- FrontendEngineeringOrchestrator
- BackendEngineeringOrchestrator

Main Agents:
- ReactPageImplementationTaskAgent
- GraphQLResolverTaskAgent
- ServiceImplementationTaskAgent
- PrismaModelTaskAgent
- AuthFlowImplementationTaskAgent
- RequirementModuleImplementationTaskAgent
- AuditLogModuleImplementationTaskAgent

Outputs:
- working code
- modules/services
- DB schema/migrations
- integrated features

---

## Phase 9: AI / Search / Memory Integration
Orchestrators:
- AIOrchestrator
- SearchOrchestrator
- AgentRuntimeOrchestrator

Main Agents:
- PromptConstructionTaskAgent
- RetrievalContextTaskAgent
- ModelRoutingTaskAgent
- SemanticCachingTaskAgent
- MemoryWriteTaskAgent
- MemoryRetrievalTaskAgent

Outputs:
- AI-enabled flows
- search index
- context pipeline
- memory policy implementation

---

## Phase 10: Testing & Validation
Orchestrators:
- QAOrchestrator
- VerificationOrchestrator
- QualityGateOrchestrator

Main Agents:
- UnitTestGenerationTaskAgent
- IntegrationTestGenerationTaskAgent
- EndToEndTestGenerationTaskAgent
- ContractTestTaskAgent
- AccessibilityTestTaskAgent
- ReleaseReadinessTaskAgent

Outputs:
- test suites
- validation reports
- coverage results
- release gate decision

---

## Phase 11: Security / Compliance Review
Orchestrators:
- SecurityOrchestrator
- ComplianceOrchestrator
- RiskOrchestrator

Main Agents:
- ThreatModelTaskAgent
- VulnerabilityScanTaskAgent
- RBACReviewTaskAgent
- PolicyComplianceTaskAgent
- AuditEvidenceTaskAgent

Outputs:
- security review
- compliance evidence
- control gap list
- risk disposition

---

## Phase 12: Release / Deploy
Orchestrators:
- ReleaseOrchestrator
- DevOpsOrchestrator

Main Agents:
- BuildTaskAgent
- DeploymentTaskAgent
- PostDeployValidationTaskAgent
- SmokeTestTaskAgent
- ReleaseNoteTaskAgent
- RollbackTaskAgent

Outputs:
- release package
- deployed system
- release notes
- rollback point

---

## Phase 13: Operations / Observe
Orchestrators:
- OperationsOrchestrator
- SREOrchestrator
- SupportOrchestrator

Main Agents:
- ServiceMonitoringTaskAgent
- AlertTriageTaskAgent
- RootCauseAnalysisTaskAgent
- SupportTicketRoutingTaskAgent
- AvailabilityReportTaskAgent

Outputs:
- service telemetry
- incidents / RCA
- support synthesis
- reliability metrics

---

## Phase 14: Product Intelligence / Learn
Orchestrators:
- ProductIntelligenceOrchestrator
- FeedbackOrchestrator
- ExperimentationOrchestrator

Main Agents:
- FeatureAdoptionTaskAgent
- FunnelAnalysisTaskAgent
- FeedbackSynthesisTaskAgent
- ExperimentResultTaskAgent
- RecommendationTaskAgent

Outputs:
- insight reports
- experiment outcomes
- adoption trends
- improvement recommendations

---

## Phase 15: Enhancement / Iteration
Orchestrators:
- EnhancementOrchestrator
- ProductStrategyOrchestrator

Main Agents:
- EnhancementOpportunityTaskAgent
- BacklogRefinementTaskAgent
- TechnicalDebtPrioritizationTaskAgent
- RoadmapAdjustmentTaskAgent
- KnowledgeCanonicalizationTaskAgent

Outputs:
- enhancement backlog
- reprioritized roadmap
- institutionalized learning
- next iteration scope