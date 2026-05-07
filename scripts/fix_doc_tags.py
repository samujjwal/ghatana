#!/usr/bin/env python3
"""
Script to add missing @doc.* tags to Java files that are failing the checkDocTags check.
"""
import re
import os
import sys

BASE_DIR = "/home/samujjwal/Developments/ghatana"

# All files with their missing tags from the build report
VIOLATIONS = {
    "platform/contracts/src/main/java/com/ghatana/contracts/schema/JsonSchemaBundleToPojoGenerator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "platform/contracts/src/main/java/com/ghatana/contracts/schema/ProtoToJsonSchemaGenerator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/finance/src/main/java/com/ghatana/finance/ai/TradeEvent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "platform/java/messaging/src/main/java/com/ghatana/platform/messaging/strategy/QueueConsumerStrategy.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "platform/java/messaging/src/main/java/com/ghatana/platform/messaging/strategy/QueueMessage.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "platform/java/messaging/src/main/java/com/ghatana/platform/messaging/strategy/s3/DefaultS3StorageStrategy.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "platform/java/workflow/src/main/java/com/ghatana/platform/workflow/WorkflowContext.java": ["@doc.pattern"],
    "platform/java/workflow/src/main/java/com/ghatana/platform/workflow/Workflow.java": ["@doc.pattern"],
    "platform/java/workflow/src/main/java/com/ghatana/platform/workflow/WorkflowStep.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/RetentionConfig.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/DecayFunction.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/ExponentialDecay.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/UtilityBasedRetentionManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/RetentionManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/StepDecay.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/RetentionScheduler.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/PowerLawDecay.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/retention/RetentionResult.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/RollbackResult.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/LearningPlane.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/SkillVersion.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/UpdateCandidate.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/EvaluationGateResult.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/TraceGrade.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/evaluation/EvaluationGate.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/evaluation/EvaluationContext.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/evaluation/CompositeEvaluationGate.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/evaluation/SkillPromotionWorkflow.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/evaluation/SafetyEvaluationGate.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/skill/SkillVersionManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/EntrenchmentConflictResolver.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/EpisodicToSemanticConsolidator.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/ConsolidationScheduler.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/LLMFactExtractor.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/ProcedureInducer.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/MemoryConflict.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/ConflictResolver.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/EpisodicToProceduralConsolidator.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/PromotionResult.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/observability/TracedMemoryPlane.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/observability/MemoryMetrics.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/security/MemorySecurityManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/security/MemoryRedactionFilter.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/EnvironmentSnapshot.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskState.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskLifecycleStatus.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskDependency.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskCheckpoint.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/DoneCriteria.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskPhase.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskBlocker.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/taskstate/TaskInvariant.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/ValidityStatus.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/fact/FactVersion.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/fact/EnhancedFact.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/MemoryItemType.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/Provenance.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/working/BoundedWorkingMemory.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/working/WorkingMemory.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/working/WorkingMemoryConfig.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/working/WorkingMemoryEntry.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/MemoryLink.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/Lesson.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/Decision.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/Entity.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/TypedArtifact.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/ErrorArtifact.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/Observation.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/ArtifactType.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/artifact/ToolUse.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/LinkType.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/episode/EnhancedEpisode.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/episode/EpisodeBuilder.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/model/Validity.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/bridge/MemoryAwareContext.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/bridge/DefaultMemoryAwareContext.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/bridge/MemoryAwareBaseAgent.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/HybridRetriever.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/InjectionConfig.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/ContextInjector.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/RetrievalExplanation.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/TimeAwareReranker.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/RetrievalRequest.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/StructuredContextInjector.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/RetrievalResult.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/RetrievalPipeline.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/MemoryStoreAdapter.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/InMemoryMemoryPlane.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/MemoryItemRepository.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/PersistentMemoryPlane.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/LegacyMemoryPlaneAdapter.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/TaskStateRepository.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/procedural/ProceduralMemoryManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/procedural/ProcedureSelector.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/taskstate/TaskStateLifecycleManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/taskstate/ReconcileResult.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/taskstate/TaskStateStore.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/taskstate/Conflict.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryQuery.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlaneStats.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/semantic/SemanticMemoryManager.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/ScoredMemoryItem.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/dispatch/ExecutionTier.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/agent/operators/BaseOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/agent/operators/StreamOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/agent/operators/FilterOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/agent/operators/MapOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/agent/PatternDetectionAgent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/nfa/NFAState.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/nfa/NFAStateType.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/nfa/NFATransition.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/nfa/NFA.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/model/PatternVisitor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/model/IPatternSpec.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/model/PrimaryEventPattern.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/model/AbstractPatternSpec.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/engine/evaluator/ProbabilisticEvaluator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/registry/OperatorRegistry.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/PrimaryEventOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/WithinOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/UntilOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/NotOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/AndOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/RepeatOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/OrOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/SeqOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/builtin/WindowOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/spi/OperatorMetadata.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/spi/Operator.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/operator/spi/ValidationContext.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/compiler/ast/ASTBuilder.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/compiler/ast/AST.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/compiler/ast/ASTNode.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/compiler/dag/DAGBuilder.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/compiler/dag/DAGOptimizer.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/codegen/GeneratedTypeMode.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/codegen/GeneratedTypeKey.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/codegen/EventCloudSerializer.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/codegen/EventClassCompiler.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/codegen/CompileOptions.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/codegen/GEventLike.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/exception/EventClassCompilationException.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/exception/EventCloudSerializationException.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/PatternSpecification.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/PatternStatus.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/PatternWindowSpec.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/DetectionPlan.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/PatternMetadata.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/OperatorSpec.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/model/OperatorDAG.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/mapper/PatternProtoMapper.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/pattern/api/PatternService.java": ["@doc.type", "@doc.purpose", "@doc.layer"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/ExplorationEvent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/EventStreamStatistics.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/CorrelatedEventGroup.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/NormalizedEvent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/TemporalFeatures.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/PreprocessedEventBatch.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/model/PreprocessingConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/preprocessing/impl/BasicDataPreprocessor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/dataexploration/preprocessing/DataPreprocessor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/api/src/main/java/com/ghatana/aep/api/expert/PipelineDesignAssistant.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/aep/config/EnvConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/pipeline/registry/connector/ConnectorOperator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/domain/pipeline/PipelineSpec.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/domain/pipeline/ConnectorSpec.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/domain/pipeline/ConnectorType.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/domain/pipeline/PipelineStageSpec.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/history/EventHistoryMeta.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/history/ContinuousQueryHandle.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/Aggregation.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/WindowSpec.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/AggregationResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/AggregationFunction.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/WindowedQueryPlan.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/WindowedResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/Cursor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/event/query/StoreCapabilities.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/operator/eventcloud/EventCloudSubscriptionConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/operator/eventcloud/reconnect/ExponentialBackoffPolicy.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/operator/eventcloud/ReconnectionState.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/core/operator/eventcloud/ReconnectionStrategy.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/config/agents/AgentConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/config/agents/SequentialAgentConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/config/agents/LoopAgentConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/config/agents/LlmAgentConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/hybrid/SyncStrategy.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/core/StateStoreStats.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/core/StateStore.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointCoordinator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointStorage.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointType.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointMetadata.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointStatus.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointInfo.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointId.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointBarrier.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/checkpoint/CheckpointConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/factory/StateStoreConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/statestore/factory/StateStoreType.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/eventcore/domain/PageCursor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/eventcore/domain/AppendReceipt.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/engine/src/main/java/com/ghatana/eventcore/domain/EventRecord.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/core/operator/spi/OperatorProvider.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/core/pipeline/PipelineId.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/EntityManagerFactoryFactory.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/security/audit/AgentRegistryAuditLogger.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/domain/impl/DefaultAgentExecutionContext.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/domain/impl/DefaultSecurityContext.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/domain/AgentInfo.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/domain/AgentStep.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/audit/AuditModule.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/dto/AgentRegistrationDto.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/store/JpaAgentEntity.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/agent/registry/util/ValidationUtils.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/aep/domain/pipeline/AgentStep.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/aep/domain/models/agent/AgentDomainModel.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/aep/domain/models/agent/AgentInfo.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/registry/src/main/java/com/ghatana/pipeline/registry/repository/PipelineRepository.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/loadbalancer/LoadBalancerModels.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/loadbalancer/AdvancedLoadBalancer.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/distributed/DistributedPatternProcessor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/autoscaling/ScalingOperationModels.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/autoscaling/AutoScalingEngine.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/cluster/ClusterManagementSystem.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/cluster/ClusterManagementModels.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/models/AutoScalingModels.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/scaling/src/main/java/com/ghatana/aep/scaling/models/DistributedModels.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/security/src/main/java/com/ghatana/aep/security/AepAuthFilter.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/cache/PipelineCache.java": ["@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/loader/SpecFormatLoader.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/PipelineRunResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/queue/impl/PostgresExecutionQueue.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/queue/impl/CheckpointAwareExecutionQueue.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/OrchestratorDlqEvent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/executor/AgentExecutionPolicy.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/deployment/service/EventCloudDeploymentEventPublisher.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/OrchestratorPipelineStatus.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/client/PipelineRegistryClient.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/ConsumerOffsetRepository.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/InMemoryCheckpointStore.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/CheckpointStore.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/PipelineCheckpoint.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/StepCheckpoint.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/PipelineCheckpointStatus.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/ConsumerOffsetEntity.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/StepCheckpointEntity.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/PipelineCheckpointRepository.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/AbstractRepository.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/PostgresqlCheckpointStore.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/store/StepCheckpointRepository.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/subsys/TriggerListener.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/models/OrchestratorPipelineEntity.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/models/AgentMetadataEntity.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/orchestrator/models/OrchestratorPipelineState.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/data-cloud/planes/action/server/src/main/java/com/ghatana/aep/server/http/controllers/AepController.java": ["@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/health/HealthMetricsServer.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/observability/MetricsServerInterceptor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/observability/TracingServerInterceptor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/security/InputValidationServerInterceptor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/security/JwtServerInterceptor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/GrpcInterceptorChain.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/resilience/CircuitBreakerServerInterceptor.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/finance/domains/sanctions/src/main/java/com/ghatana/products/finance/domains/sanctions/domain/ScreeningEntityType.java": ["@doc.pattern"],
    "products/finance/domains/sanctions/src/main/java/com/ghatana/products/finance/domains/sanctions/domain/ScreeningDecision.java": ["@doc.pattern"],
    "products/finance/domains/sanctions/src/main/java/com/ghatana/products/finance/domains/sanctions/domain/MatchType.java": ["@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/LlmProvider.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/QualityReport.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/AssessmentItem.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/LearningClaim.java": ["@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/ContentType.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/Domain.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/SimulationManifest.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/ContentGenerationRequest.java": ["@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/CompleteContentPackage.java": ["@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/GenerationConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/ContentExample.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/LearningEvidence.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/AnimationConfig.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/domain/ContentGenerationResponse.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/AiLearningServiceImpl.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/agent/src/main/java/com/ghatana/core/agent/VirtualOrgAgent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/agent/src/main/java/com/ghatana/virtualorg/agent/VirtualOrgAgent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/DepartmentType.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/agent/Agent.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/event/TaskEventFactory.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/task/TaskStatus.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/task/TaskPriority.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/task/Task.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/framework/src/main/java/com/ghatana/virtualorg/framework/task/TaskOrchestrator.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/virtual-org/modules/operator-adapter/src/main/java/com/ghatana/core/operator/adapter/AgentStreamOperatorAdapterFactory.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/cache/RedisEntityCacheAdapter.java": ["@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/TemporalAlignment.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/FrameResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/VisionClientAdapter.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/MultimodalRequest.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/VisualResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/VideoAudioResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/DetectionResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/SttClientAdapter.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/MultimodalResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/MultimodalAnalysisEngine.java": ["@doc.pattern"],
    "products/audio-video/modules/intelligence/multimodal-service/src/main/java/com/ghatana/audio/video/multimodal/engine/AudioResult.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/software-org/engine/modules/domain-model/src/main/java/com/ghatana/softwareorg/domain/devsecops/DevSecOpsStage.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/software-org/engine/modules/domain-model/src/main/java/com/ghatana/softwareorg/domain/devsecops/AgentMetadata.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/software-org/engine/modules/domain-model/src/main/java/com/ghatana/softwareorg/domain/devsecops/DomainOverlay.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/software-org/engine/modules/domain-model/src/main/java/com/ghatana/softwareorg/domain/devsecops/PersonaRef.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
    "products/software-org/engine/modules/domain-model/src/main/java/com/ghatana/softwareorg/domain/devsecops/ProductLifecyclePhase.java": ["@doc.type", "@doc.purpose", "@doc.layer", "@doc.pattern"],
}


def infer_doc_type(content: str) -> str:
    """Infer @doc.type from file content."""
    if re.search(r'\benum\s+\w+', content):
        return "enum"
    if re.search(r'\binterface\s+\w+', content):
        return "interface"
    if re.search(r'\brecord\s+\w+', content):
        return "record"
    return "class"


def infer_doc_layer(rel_path: str) -> str:
    """Infer @doc.layer from the file path."""
    if rel_path.startswith("platform/"):
        return "platform"
    return "product"


def infer_doc_pattern(class_name: str, content: str) -> str:
    """Infer @doc.pattern from class name and content."""
    name = class_name.lower()
    if 'service' in name:
        return "Service"
    if 'repository' in name or 'repo' in name or 'store' in name:
        return "Repository"
    if 'factory' in name:
        return "Factory"
    if 'filter' in name or 'interceptor' in name:
        return "Filter"
    if 'handler' in name:
        return "Handler"
    if 'adapter' in name:
        return "Adapter"
    if 'registry' in name:
        return "Registry"
    if 'manager' in name:
        return "Manager"
    if 'coordinator' in name:
        return "Coordinator"
    if 'scheduler' in name:
        return "Scheduler"
    if 'builder' in name:
        return "Builder"
    if 'config' in name or 'configuration' in name:
        return "Configuration"
    if 'event' in name:
        return "Event"
    if 'result' in name or 'response' in name:
        return "ValueObject"
    if 'entity' in name:
        return "Entity"
    if 'dto' in name:
        return "DTO"
    if 'exception' in name or 'error' in name:
        return "Exception"
    if 'operator' in name:
        return "Operator"
    if 'processor' in name:
        return "Processor"
    if 'agent' in name:
        return "Agent"
    if 'workflow' in name:
        return "Workflow"
    if 'plane' in name:
        return "Component"
    if 'type' in name or 'status' in name or 'phase' in name or 'tier' in name or 'mode' in name:
        return "Enum"
    # Check if it's actually an interface
    if re.search(r'^\s*public\s+interface\s+', content, re.MULTILINE):
        return "Interface"
    return "Component"


def infer_doc_purpose(class_name: str, rel_path: str) -> str:
    """Infer a short purpose description from class name."""
    # Convert CamelCase to words
    words = re.sub(r'(?<=[a-z])(?=[A-Z])', ' ', class_name)
    return f"Provides {words.lower()} functionality."


def get_class_name(content: str) -> str:
    """Extract the primary class/interface/enum/record name."""
    m = re.search(r'(?:public\s+(?:abstract\s+|final\s+|sealed\s+)*)(?:class|interface|record|enum)\s+(\w+)', content)
    if m:
        return m.group(1)
    return "Unknown"


def find_existing_javadoc_end(content: str, class_decl_pos: int) -> int:
    """Find the end of the existing javadoc comment before the class declaration."""
    # Look backwards from class_decl_pos for */
    before = content[:class_decl_pos]
    # Find the last */ before the class declaration
    end = before.rfind('*/')
    if end == -1:
        return -1
    # Check this is actually a javadoc ending (preceded by /** somewhere)
    start = before.rfind('/**')
    if start == -1 or start > end:
        return -1
    # Make sure there's nothing between */ and class declaration except whitespace/annotations
    between = content[end+2:class_decl_pos]
    if re.search(r'\S', between.replace('@', '')):
        # There's non-annotation, non-whitespace content between */ and class - likely not our javadoc
        stripped = between.strip()
        # Allow annotations
        if not re.match(r'^(@\w+(\([^)]*\))?\s*)+$', stripped):
            return -1
    return end


def add_doc_tags(file_path: str, missing_tags: list) -> bool:
    """Add missing @doc tags to a Java file."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except FileNotFoundError:
        print(f"  SKIP (not found): {file_path}")
        return False

    class_name = get_class_name(content)
    rel_path = os.path.relpath(file_path, BASE_DIR)
    doc_type = infer_doc_type(content)
    doc_layer = infer_doc_layer(rel_path)
    doc_pattern = infer_doc_pattern(class_name, content)
    doc_purpose = infer_doc_purpose(class_name, rel_path)

    # Find the class declaration position
    class_decl_match = re.search(
        r'(?:public\s+(?:abstract\s+|final\s+|sealed\s+)*)(?:class|interface|record|enum)\s+\w+',
        content
    )
    if not class_decl_match:
        print(f"  SKIP (no class decl): {file_path}")
        return False

    class_decl_pos = class_decl_match.start()

    # Check if there's an existing javadoc comment
    javadoc_end = find_existing_javadoc_end(content, class_decl_pos)

    if javadoc_end != -1:
        # There's an existing javadoc - add missing tags before */
        tags_to_add = ""
        if "@doc.type" in missing_tags:
            tags_to_add += f" * @doc.type {doc_type}\n"
        if "@doc.purpose" in missing_tags:
            tags_to_add += f" * @doc.purpose {doc_purpose}\n"
        if "@doc.layer" in missing_tags:
            tags_to_add += f" * @doc.layer {doc_layer}\n"
        if "@doc.pattern" in missing_tags:
            tags_to_add += f" * @doc.pattern {doc_pattern}\n"

        # Insert before the */
        new_content = content[:javadoc_end] + tags_to_add + content[javadoc_end:]
    else:
        # No javadoc - create a new one right before the class declaration
        # Find the start of any annotations before the class
        # Find where to insert: before any annotations that precede the class
        insert_pos = class_decl_pos

        # Look backwards to find the start of annotations
        before = content[:class_decl_pos]
        # Find annotations block (consecutive @Xxx annotations)
        annotation_block_match = re.search(r'(?:\s*@\w+(?:\([^)]*\))?\s*)+$', before)
        if annotation_block_match:
            insert_pos = annotation_block_match.start()

        tags_block = "/**\n"
        tags_block += f" * @doc.type {doc_type}\n"
        tags_block += f" * @doc.purpose {doc_purpose}\n"
        tags_block += f" * @doc.layer {doc_layer}\n"
        tags_block += f" * @doc.pattern {doc_pattern}\n"
        tags_block += " */\n"

        new_content = content[:insert_pos] + tags_block + content[insert_pos:]

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)

    return True


def main():
    fixed = 0
    skipped = 0
    for rel_path, missing_tags in VIOLATIONS.items():
        full_path = os.path.join(BASE_DIR, rel_path)
        print(f"Processing: {rel_path}")
        if add_doc_tags(full_path, missing_tags):
            fixed += 1
        else:
            skipped += 1

    print(f"\nDone: {fixed} fixed, {skipped} skipped")


if __name__ == "__main__":
    main()

