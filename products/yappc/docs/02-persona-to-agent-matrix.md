# Persona to Agent Matrix

This matrix maps human roles to primary agent collaborators.

Legend:
- Primary: main collaborating agents
- Secondary: commonly supporting agents
- Approval: approval or governance touchpoints

---

## 1. ExecutiveSponsor
Primary:
- PortfolioOrchestrator
- ProductStrategyOrchestrator
- GovernanceOrchestrator
Secondary:
- ExecutiveStatusSynthesisTaskAgent
- BusinessCaseDraftingTaskAgent
- KPISelectionTaskAgent
Approval:
- RequirementApprovalTaskAgent
- QualityGateDecisionTaskAgent
- RoadmapAdjustmentTaskAgent

---

## 2. ProductManager
Primary:
- DiscoveryOrchestrator
- IdeationOrchestrator
- RequirementsOrchestrator
- ProductIntelligenceOrchestrator
Secondary:
- ValuePropositionTaskAgent
- MVPDefinitionTaskAgent
- RequirementCaptureTaskAgent
- BacklogRefinementTaskAgent
- FeatureAdoptionTaskAgent
Approval:
- StoryRefinementTaskAgent
- SprintScopeTaskAgent

---

## 3. ProductOwner
Primary:
- RequirementsOrchestrator
- DeliveryPlanningOrchestrator
- EnhancementOrchestrator
Secondary:
- AcceptanceCriteriaTaskAgent
- DefinitionOfDoneTaskAgent
- StoryDraftingTaskAgent
- RequirementApprovalTaskAgent
Approval:
- ReleaseReadinessTaskAgent
- ChangeApprovalAgent

---

## 4. BusinessAnalyst
Primary:
- RequirementIntakeCapabilityAgent
- RequirementAnalysisCapabilityAgent
- TraceabilityCapabilityAgent
Secondary:
- RequirementNormalizationTaskAgent
- FunctionalRequirementDraftingTaskAgent
- ConstraintCaptureTaskAgent
- RequirementToStoryTraceTaskAgent
Approval:
- RequirementCompletenessTaskAgent

---

## 5. UXResearcher
Primary:
- PersonaModelingCapabilityAgent
- JourneyDesignCapabilityAgent
- FeedbackOrchestrator
Secondary:
- PersonaDefinitionTaskAgent
- UserJourneyTaskAgent
- InterviewSynthesisTaskAgent
- UserSatisfactionTaskAgent
Approval:
- AccessibilityReviewTaskAgent

---

## 6. UXDesigner
Primary:
- UXOrchestrator
- InteractionDesignOrchestrator
- DesignSystemOrchestrator
Secondary:
- WireframeTaskAgent
- NavigationModelTaskAgent
- ScreenStateTaskAgent
- ErrorStateTaskAgent
- ContentHierarchyTaskAgent
Approval:
- DesignTokenAlignmentTaskAgent

---

## 7. Architect
Primary:
- ArchitectureOrchestrator
- PlatformArchitectureOrchestrator
- PluginArchitectureOrchestrator
- RuntimeArchitectureOrchestrator
Secondary:
- DomainModelTaskAgent
- ServiceBoundaryTaskAgent
- EventSchemaTaskAgent
- AgentMemoryDesignTaskAgent
- ObservabilityPlanTaskAgent
Approval:
- APIContractTaskAgent
- SecurityControlTaskAgent

---

## 8. FrontendEngineer
Primary:
- FrontendEngineeringOrchestrator
- UIImplementationCapabilityAgent
Secondary:
- ReactPageImplementationTaskAgent
- MaterialUICompositionTaskAgent
- RouterFlowImplementationTaskAgent
- FormImplementationTaskAgent
- TableImplementationTaskAgent
- SearchPageUXTaskAgent
Approval:
- UIVisualRegressionTaskAgent
- AccessibilityTestTaskAgent

---

## 9. BackendEngineer
Primary:
- BackendEngineeringOrchestrator
- APIImplementationCapabilityAgent
- PersistenceCapabilityAgent
Secondary:
- GraphQLResolverTaskAgent
- ServiceImplementationTaskAgent
- PrismaModelTaskAgent
- PrismaMigrationTaskAgent
- RBACEnforcementTaskAgent
Approval:
- ContractTestTaskAgent
- QueryOptimizationAdvisor

---

## 10. PlatformEngineer
Primary:
- PlatformArchitectureOrchestrator
- DevOpsOrchestrator
- InfraProvisioningOrchestrator
Secondary:
- DeploymentTopologyTaskAgent
- InfraProvisionTaskAgent
- EnvironmentBootstrapTaskAgent
- SecretRotationTaskAgent
- BackupRecoveryArchitectureTaskAgent
Approval:
- DeploymentTaskAgent
- PostDeployValidationTaskAgent

---

## 11. DataEngineer
Primary:
- DataOrchestrator
- DataIngestionCapabilityAgent
- SearchOrchestrator
Secondary:
- DataIngestionTaskAgent
- DataValidationTaskAgent
- DataNormalizationTaskAgent
- SearchIndexBuildTaskAgent
- SchemaEvolutionTaskAgent
Approval:
- IndexFreshnessChecker
- FieldDistributionUnit

---

## 12. AIEngineer / MLEngineer
Primary:
- AIOrchestrator
- RetrievalCapabilityAgent
- PromptingCapabilityAgent
- ModelEvaluationCapabilityAgent
- MemoryOrchestrator
Secondary:
- PromptConstructionTaskAgent
- ModelRoutingTaskAgent
- AIOutputValidationTaskAgent
- DriftDetectionTaskAgent
- ReflectionTaskAgent
- MemoryConsolidationTaskAgent
Approval:
- HallucinationPatternUnit
- ConfidenceThresholdUnit
- CostBudgetTaskAgent

---

## 13. QAEngineer
Primary:
- QAOrchestrator
- VerificationOrchestrator
- QualityGateOrchestrator
Secondary:
- UnitTestGenerationTaskAgent
- IntegrationTestGenerationTaskAgent
- EndToEndTestGenerationTaskAgent
- EdgeCaseTaskAgent
- ReleaseReadinessTaskAgent
Approval:
- QualityGateDecisionTaskAgent

---

## 14. SecurityEngineer
Primary:
- SecurityOrchestrator
- RiskOrchestrator
- PrivacyOrchestrator
Secondary:
- ThreatModelTaskAgent
- VulnerabilityScanTaskAgent
- SecretsExposureTaskAgent
- RBACReviewTaskAgent
- PolicyComplianceTaskAgent
Approval:
- SecurityIncidentTaskAgent
- ControlGapTaskAgent

---

## 15. DevOpsEngineer
Primary:
- DevOpsOrchestrator
- ReleaseOrchestrator
- EnvironmentOrchestrator
Secondary:
- CIWorkflowTaskAgent
- DeploymentTaskAgent
- ConfigPromotionTaskAgent
- SmokeTestTaskAgent
- RollbackTaskAgent
Approval:
- CanaryDecisionTaskAgent
- BlueGreenSwitchTaskAgent

---

## 16. SRE
Primary:
- SREOrchestrator
- IncidentManagementOrchestrator
- ReliabilityCapabilityAgent
Secondary:
- ServiceMonitoringTaskAgent
- AlertTriageTaskAgent
- RootCauseAnalysisTaskAgent
- ErrorBudgetTaskAgent
- DRReadinessTaskAgent
Approval:
- RecoveryExecutionTaskAgent
- AvailabilityReportTaskAgent

---

## 17. ComplianceOfficer
Primary:
- ComplianceOrchestrator
- PrivacyOrchestrator
Secondary:
- DataRetentionTaskAgent
- RegulatoryMappingTaskAgent
- AuditEvidenceTaskAgent
- PolicyComplianceTaskAgent
Approval:
- LegalReviewPreparationTaskAgent

---

## 18. SupportLead / CustomerSuccessLead
Primary:
- SupportOrchestrator
- FeedbackOrchestrator
Secondary:
- SupportTicketRoutingTaskAgent
- SupportIssueSynthesisTaskAgent
- FeedbackSynthesisTaskAgent
- UserSatisfactionTaskAgent
Approval:
- EnhancementOpportunityTaskAgent

---

## 19. Customer / EndUser
Primary:
- RequirementCaptureTaskAgent
- FeedbackSynthesisTaskAgent
- SessionPatternTaskAgent
Secondary:
- UserSatisfactionTaskAgent
- SupportIntentClassifier
Approval:
- none, unless designated reviewer

---

## 20. Auditor / Regulator
Primary:
- GovernanceOrchestrator
- ComplianceOrchestrator
Secondary:
- AuditEvidenceTaskAgent
- RegulatoryMappingTaskAgent
- AuditRecorder
Approval:
- evidence acceptance / compliance sign-off

---

## 21. Suggested Persona Bundles for YAPPC-style Workspace

### Workspace Owner
- ExecutiveSponsor + ProductOwner + Architect oversight
Associated agents:
- GovernanceOrchestrator
- RequirementApprovalTaskAgent
- ChangeApprovalAgent
- AuditArchitectureTaskAgent

### Product Builder
- ProductManager + BusinessAnalyst + UXDesigner
Associated agents:
- RequirementsOrchestrator
- UXOrchestrator
- BacklogRefinementTaskAgent
- AcceptanceCriteriaTaskAgent

### Engineering Lead
- Architect + BackendEngineer + FrontendEngineer + PlatformEngineer
Associated agents:
- ArchitectureOrchestrator
- EngineeringOrchestrator
- ReleaseOrchestrator
- QualityGateOrchestrator

### Operations Lead
- SRE + DevOpsEngineer + SupportLead
Associated agents:
- OperationsOrchestrator
- IncidentManagementOrchestrator
- SupportOrchestrator
- EnhancementOrchestrator