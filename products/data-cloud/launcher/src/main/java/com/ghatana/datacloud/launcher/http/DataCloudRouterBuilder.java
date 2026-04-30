/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.launcher.http.handlers.*;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpMethod;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for Data-Cloud HTTP routing servlet.
 *
 * <p>Extracts the monolithic router builder from DataCloudHttpServer into domain-specific
 * builder methods for better maintainability. Each domain (entities, events, analytics, etc.)
 * has its own builder method, making it easier to add new routes and understand the structure.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * RoutingServlet router = new DataCloudRouterBuilder(eventloop)
 *     .withHealthRoutes(healthHandler)
 *     .withEntityRoutes(entityHandler, sseHandler, semanticSearchHandler, exportHandler, anomalyHandler, validationHandler)
 *     .withEventRoutes(eventHandler)
 *     .withPipelineRoutes(pipelineCheckpointHandler, workflowExecutionHandler)
 *     .withAlertRoutes(alertingHandler, sseHandler)
 *     .withMemoryRoutes(memoryHandler)
 *     .withBrainRoutes(brainHandler, sseHandler)
 *     .withLearningRoutes(learningHandler)
 *     .withAnalyticsRoutes(analyticsHandler, workflowExecutionHandler)
 *     .withReportingRoutes(analyticsHandler, workflowExecutionHandler)
 *     .withModelRoutes(aiModelHandler)
 *     .withFeatureRoutes(aiModelHandler)
 *     .withSseRoutes(sseHandler)
 *     .withWebSocketRoutes(sseHandler)
 *     .withAiAssistRoutes(aiAssistHandler)
 *     .withVoiceRoutes(voiceHandler)
 *     .withGovernanceRoutes(dataLifecycleHandler)
 *     .withCapabilityRoutes(capabilityRegistryHandler)
 *     .withLineageRoutes(lineageHandler)
 *     .withContextRoutes(contextLayerHandler, collectionContextHandler, semanticSearchHandler)
 *     .withMcpRoutes(mcpToolsHandler)
 *     .withDataProductRoutes(dataProductHandler)
 *     .withAutonomyRoutes(autonomyHandler)
 *     .withAgentCatalogRoutes(agentCatalogHandler)
 *     .withPluginRoutes(pluginInstallHandler)
 *     .withStorageCostRoutes(storageCostHandler, httpSupport)
 *     .withFederatedQueryRoutes(federatedQueryHandler, httpSupport)
 *     .withTierMigrationRoutes(tierMigrationHandler, httpSupport)
 *     .withConnectorRoutes(dataSourceRegistryHandler, httpSupport)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Builder for Data-Cloud HTTP routing servlet with domain-specific route groups
 * @doc.layer product
 * @doc.pattern Builder
 */
public class DataCloudRouterBuilder {

    private static final Logger log = LoggerFactory.getLogger(DataCloudRouterBuilder.class);

    private RoutingServlet.Builder builder;

    /**
     * Creates a new router builder.
     *
     * @param eventloop the eventloop for the routing servlet
     */
    public DataCloudRouterBuilder(Eventloop eventloop) {
        this.builder = RoutingServlet.builder(eventloop);
    }

    /**
     * Adds health and info endpoints.
     */
    public DataCloudRouterBuilder withHealthRoutes(HealthHandler healthHandler) {
        builder
            .with(HttpMethod.GET, "/health", healthHandler::handleHealth)
            .with(HttpMethod.GET, "/health/detail", healthHandler::handleHealthDetail)
            .with(HttpMethod.GET, "/health/deep", healthHandler::handleHealthDeep)
            .with(HttpMethod.GET, "/ready", healthHandler::handleReady)
            .with(HttpMethod.GET, "/live", healthHandler::handleLive)
            .with(HttpMethod.GET, "/info", healthHandler::handleInfo)
            .with(HttpMethod.GET, "/metrics", healthHandler::handleMetrics);
        return this;
    }

    /**
     * Adds entity CRUD and related endpoints.
     */
    public DataCloudRouterBuilder withEntityRoutes(
            EntityCrudHandler entityHandler,
            SseStreamingHandler sseHandler,
            SemanticSearchHandler semanticSearchHandler,
            EntityExportHandler exportHandler,
            EntityAnomalyHandler anomalyHandler,
            EntityValidationHandler validationHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/collections", entityHandler::handleListCollections)
            .with(HttpMethod.POST, "/api/v1/collections/:collection/metadata", entityHandler::handleUpsertCollectionMetadata)
            .with(HttpMethod.POST, "/api/v1/entities/:collection", entityHandler::handleSaveEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/stream", sseHandler::handleEntityCdcStream)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/search", entityHandler::handleFullTextSearch)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/similar", semanticSearchHandler::handleSimilarEntities)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/query/stream", sseHandler::handleStreamingQuerySse)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id", entityHandler::handleGetEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id/history", entityHandler::handleGetEntityAsOf)
            .with(HttpMethod.GET, "/api/v1/entities/:collection", entityHandler::handleQueryEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/:id", entityHandler::handleDeleteEntity)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/batch", entityHandler::handleBatchSaveEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/batch", entityHandler::handleBatchDeleteEntities)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/export", exportHandler::handleExportEntities)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/export", exportHandler::handleExportEntitiesWithApproval)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/anomalies", anomalyHandler::handleDetectAnomalies)
            .with(HttpMethod.GET, "/api/v1/anomalies", anomalyHandler::handleQueryAnomalies)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/validate", validationHandler::handleValidateEntity)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/validate/batch", validationHandler::handleBatchValidateEntities);
        return this;
    }

    /**
     * Adds event endpoints.
     */
    public DataCloudRouterBuilder withEventRoutes(EventHandler eventHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/events", eventHandler::handleAppendEvent)
            .with(HttpMethod.GET, "/api/v1/events", eventHandler::handleQueryEvents)
            .with(HttpMethod.GET, "/api/v1/events/:offset", eventHandler::handleGetEventByOffset);
        return this;
    }

    /**
     * Adds pipeline registry and execution endpoints.
     */
    public DataCloudRouterBuilder withPipelineRoutes(
            PipelineCheckpointHandler pipelineCheckpointHandler,
            WorkflowExecutionHandler workflowExecutionHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/pipelines", pipelineCheckpointHandler::handleListPipelines)
            .with(HttpMethod.POST, "/api/v1/pipelines", pipelineCheckpointHandler::handleSavePipeline)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId", pipelineCheckpointHandler::handleGetPipeline)
            .with(HttpMethod.PUT, "/api/v1/pipelines/:pipelineId", pipelineCheckpointHandler::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId", pipelineCheckpointHandler::handleDeletePipeline)
            .with(HttpMethod.POST, "/api/v1/pipelines/:pipelineId/execute", workflowExecutionHandler::handleExecutePipeline)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId/executions", workflowExecutionHandler::handleListExecutions)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId/executions/:executionId", workflowExecutionHandler::handleGetWorkflowExecution)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId/executions/:executionId/logs", workflowExecutionHandler::handleExecutionLogs)
            .with(HttpMethod.POST, "/api/v1/pipelines/:pipelineId/executions/:executionId/cancel", workflowExecutionHandler::handleCancelExecution);
        return this;
    }

    /**
     * Adds checkpoint management endpoints.
     */
    public DataCloudRouterBuilder withCheckpointRoutes(PipelineCheckpointHandler pipelineCheckpointHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/checkpoints", pipelineCheckpointHandler::handleListCheckpoints)
            .with(HttpMethod.POST, "/api/v1/checkpoints", pipelineCheckpointHandler::handleSaveCheckpoint)
            .with(HttpMethod.GET, "/api/v1/checkpoints/:checkpointId", pipelineCheckpointHandler::handleGetCheckpoint)
            .with(HttpMethod.DELETE, "/api/v1/checkpoints/:checkpointId", pipelineCheckpointHandler::handleDeleteCheckpoint);
        return this;
    }

    /**
     * Adds alert management endpoints.
     */
    public DataCloudRouterBuilder withAlertRoutes(AlertingHandler alertingHandler, SseStreamingHandler sseHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/alerts", alertingHandler::handleListAlerts)
            .with(HttpMethod.POST, "/api/v1/alerts/:id/acknowledge", alertingHandler::handleAcknowledgeAlert)
            .with(HttpMethod.POST, "/api/v1/alerts/:id/resolve", alertingHandler::handleResolveAlert)
            .with(HttpMethod.POST, "/api/v1/alerts/:id/escalate", alertingHandler::handleEscalateAlert)
            .with(HttpMethod.POST, "/api/v1/alerts/:id/auto-remediate", alertingHandler::handleAutoRemediate)
            .with(HttpMethod.POST, "/api/v1/alerts/:id/remediate", alertingHandler::handleRemediateAlert)
            .with(HttpMethod.POST, "/api/v1/alerts/:id/remediate/rollback", alertingHandler::handleRollbackRemediation)
            .with(HttpMethod.GET, "/api/v1/alerts/:id/remediations", alertingHandler::handleListRemediations)
            .with(HttpMethod.GET, "/api/v1/alerts/groups", alertingHandler::handleListAlertGroups)
            .with(HttpMethod.POST, "/api/v1/alerts/groups/:groupId/resolve", alertingHandler::handleResolveGroup)
            .with(HttpMethod.GET, "/api/v1/alerts/suggestions", alertingHandler::handleListResolutionSuggestions)
            .with(HttpMethod.POST, "/api/v1/alerts/suggestions/:suggestionId/apply", alertingHandler::handleApplySuggestion)
            .with(HttpMethod.GET, "/api/v1/alerts/rules", alertingHandler::handleListAlertRules)
            .with(HttpMethod.POST, "/api/v1/alerts/rules", alertingHandler::handleCreateAlertRule)
            .with(HttpMethod.PUT, "/api/v1/alerts/rules/:ruleId", alertingHandler::handleUpdateAlertRule)
            .with(HttpMethod.DELETE, "/api/v1/alerts/rules/:ruleId", alertingHandler::handleDeleteAlertRule)
            .with(HttpMethod.GET, "/api/v1/alerts/stream", sseHandler::handleSseStream);
        return this;
    }

    /**
     * Adds agent memory plane endpoints.
     */
    public DataCloudRouterBuilder withMemoryRoutes(MemoryPlaneHandler memoryHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/memory", memoryHandler::handleListMemory)
            .with(HttpMethod.POST, "/api/v1/memory/:agentId", memoryHandler::handleStoreMemory)
            .with(HttpMethod.GET, "/api/v1/memory/:agentId", memoryHandler::handleGetAgentMemory)
            .with(HttpMethod.GET, "/api/v1/memory/:agentId/:tier", memoryHandler::handleGetAgentMemoryByTier)
            .with(HttpMethod.POST, "/api/v1/memory/:agentId/search", memoryHandler::handleSearchAgentMemory)
            .with(HttpMethod.DELETE, "/api/v1/memory/:agentId/:memoryId", memoryHandler::handleDeleteMemory)
            .with(HttpMethod.PUT, "/api/v1/memory/:agentId/:memoryId/retain", memoryHandler::handleRetainMemory);
        return this;
    }

    /**
     * Adds brain (attention, salience, patterns) endpoints.
     */
    public DataCloudRouterBuilder withBrainRoutes(BrainHandler brainHandler, SseStreamingHandler sseHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/brain/health", brainHandler::handleBrainHealth)
            .with(HttpMethod.GET, "/api/v1/brain/config", brainHandler::handleBrainConfig)
            .with(HttpMethod.GET, "/api/v1/brain/stats", brainHandler::handleBrainStats)
            .with(HttpMethod.GET, "/api/v1/brain/workspace", brainHandler::handleBrainWorkspace)
            .with(HttpMethod.GET, "/api/v1/brain/workspace/stream", sseHandler::handleBrainWorkspaceStream)
            .with(HttpMethod.POST, "/api/v1/brain/attention/elevate", brainHandler::handleBrainAttentionElevate)
            .with(HttpMethod.GET, "/api/v1/brain/attention/thresholds", brainHandler::handleBrainAttentionThresholds)
            .with(HttpMethod.PUT, "/api/v1/brain/attention/thresholds", brainHandler::handleBrainAttentionThresholdsUpdate)
            .with(HttpMethod.GET, "/api/v1/brain/patterns", brainHandler::handleBrainPatterns)
            .with(HttpMethod.POST, "/api/v1/brain/patterns/match", brainHandler::handleBrainPatternsMatch)
            .with(HttpMethod.GET, "/api/v1/brain/salience/:itemId", brainHandler::handleBrainSalience);
        return this;
    }

    /**
     * Adds learning and review endpoints.
     */
    public DataCloudRouterBuilder withLearningRoutes(LearningHandler learningHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/learning/trigger", learningHandler::handleLearningTrigger)
            .with(HttpMethod.GET, "/api/v1/learning/status", learningHandler::handleLearningStatus)
            .with(HttpMethod.GET, "/api/v1/learning/review", learningHandler::handleLearningReviewQueue)
            .with(HttpMethod.POST, "/api/v1/learning/review/:reviewId/approve", learningHandler::handleLearningReviewApprove)
            .with(HttpMethod.POST, "/api/v1/learning/review/:reviewId/reject", learningHandler::handleLearningReviewReject)
            .with(HttpMethod.DELETE, "/api/v1/learning/review/completed", learningHandler::handlePurgeCompletedReviews);
        return this;
    }

    /**
     * Adds analytics query endpoints.
     */
    public DataCloudRouterBuilder withAnalyticsRoutes(AnalyticsHandler analyticsHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/analytics/query", analyticsHandler::handleAnalyticsQuery)
            .with(HttpMethod.GET, "/api/v1/analytics/query/:queryId", analyticsHandler::handleAnalyticsGetResult)
            .with(HttpMethod.GET, "/api/v1/analytics/query/:queryId/plan", analyticsHandler::handleAnalyticsGetPlan)
            .with(HttpMethod.POST, "/api/v1/analytics/query/:queryId/cancel", analyticsHandler::handleAnalyticsCancelQuery)
            .with(HttpMethod.POST, "/api/v1/analytics/aggregate", analyticsHandler::handleAnalyticsAggregate)
            .with(HttpMethod.POST, "/api/v1/analytics/explain", analyticsHandler::handleAnalyticsExplain);
        return this;
    }

    /**
     * Adds reporting endpoints.
     */
    public DataCloudRouterBuilder withReportingRoutes(AnalyticsHandler analyticsHandler, WorkflowExecutionHandler workflowExecutionHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/reports", analyticsHandler::handleCreateReport)
            .with(HttpMethod.GET, "/api/v1/reports", analyticsHandler::handleListReports)
            .with(HttpMethod.GET, "/api/v1/reports/:reportId", analyticsHandler::handleGetReport)
            .with(HttpMethod.GET, "/api/v1/executions/:executionId", workflowExecutionHandler::handleGetExecution)
            .with(HttpMethod.GET, "/api/v1/executions/:executionId/logs", workflowExecutionHandler::handleExecutionLogs)
            .with(HttpMethod.POST, "/api/v1/executions/:executionId/cancel", workflowExecutionHandler::handleCancelExecution)
            .with(HttpMethod.POST, "/api/v1/executions/:executionId/retry", workflowExecutionHandler::handleRetryExecution)
            .with(HttpMethod.POST, "/api/v1/executions/:executionId/rollback", workflowExecutionHandler::handleRollbackExecution)
            // P2.2: Event-backed checkpoints for durable workflow execution
            .with(HttpMethod.POST, "/api/v1/executions/:executionId/checkpoint", workflowExecutionHandler::handleCreateCheckpoint)
            .with(HttpMethod.GET, "/api/v1/executions/:executionId/checkpoints", workflowExecutionHandler::handleListCheckpoints)
            .with(HttpMethod.POST, "/api/v1/executions/:executionId/restore", workflowExecutionHandler::handleRestoreCheckpoint);
        return this;
    }

    /**
     * Adds AI model registry endpoints.
     */
    public DataCloudRouterBuilder withModelRoutes(AiModelHandler aiModelHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/models", aiModelHandler::handleListAiModels)
            .with(HttpMethod.POST, "/api/v1/models", aiModelHandler::handleRegisterAiModel)
            .with(HttpMethod.GET, "/api/v1/models/:modelName", aiModelHandler::handleGetAiModel)
            .with(HttpMethod.POST, "/api/v1/models/:modelName/promote", aiModelHandler::handlePromoteAiModel);
        return this;
    }

    /**
     * Adds feature store endpoints.
     */
    public DataCloudRouterBuilder withFeatureRoutes(AiModelHandler aiModelHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/features", aiModelHandler::handleIngestFeature)
            .with(HttpMethod.GET, "/api/v1/features/:entityId", aiModelHandler::handleGetFeatures);
        return this;
    }

    /**
     * Adds SSE streaming endpoints.
     */
    public DataCloudRouterBuilder withSseRoutes(SseStreamingHandler sseHandler) {
        builder
            .with(HttpMethod.GET, "/events/stream", sseHandler::handleSseStream)
            .with(HttpMethod.GET, "/api/v1/learning/stream", sseHandler::handleLearningStream);
        return this;
    }

    /**
     * Adds WebSocket endpoints.
     */
    public DataCloudRouterBuilder withWebSocketRoutes(SseStreamingHandler sseHandler) {
        builder.withWebSocket("/ws", sseHandler::handleWebSocketConnection);
        return this;
    }

    /**
     * Adds AI assist endpoints.
     */
    public DataCloudRouterBuilder withAiAssistRoutes(AiAssistHandler aiAssistHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/entities/:collection/suggest", aiAssistHandler::handleEntitySuggest)
            .with(HttpMethod.POST, "/api/v1/analytics/suggest", aiAssistHandler::handleAnalyticsSuggest)
            .with(HttpMethod.POST, "/api/v1/pipelines/draft", aiAssistHandler::handlePipelineDraft)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/infer-schema", aiAssistHandler::handleInferSchema)
            .with(HttpMethod.POST, "/api/v1/analytics/automate", aiAssistHandler::handleAnalyticsAutomate)
            .with(HttpMethod.POST, "/api/v1/brain/explain", aiAssistHandler::handleBrainExplain)
            .with(HttpMethod.POST, "/api/v1/ai/context/rank", aiAssistHandler::handleContextRank)
            .with(HttpMethod.POST, "/api/v1/ai/quality/drift-detect", aiAssistHandler::handleContractDrift)
            .with(HttpMethod.POST, "/api/v1/operations/anomaly-group", aiAssistHandler::handleAnomalyGroup)
            .with(HttpMethod.POST, "/api/v1/operations/forecast", aiAssistHandler::handleCapacityForecast)
            .with(HttpMethod.POST, "/api/v1/ai/next-action", aiAssistHandler::handleNextBestAction)
            .with(HttpMethod.POST, "/api/v1/connectors/suggest-mapping", aiAssistHandler::handleSuggestConnectorMapping)
            .with(HttpMethod.POST, "/api/v1/connectors/:connectorId/sync-health", aiAssistHandler::handleConnectorSyncHealth)
            .with(HttpMethod.GET, "/api/v1/ai/quality-summary", aiAssistHandler::handleAiQualitySummary)
            .with(HttpMethod.POST, "/api/v1/query/nlq", aiAssistHandler::handleNaturalLanguageQuery)
            .with(HttpMethod.POST, "/api/v1/pipelines/:pipelineId/optimise-hint", aiAssistHandler::handlePipelineOptimiseHint)
            .with(HttpMethod.POST, "/api/v1/governance/recommend", aiAssistHandler::handleGovernanceRecommend)
            // DC-AUD-008: Cross-surface AI operation routes expected by the UI client
            .with(HttpMethod.POST, "/api/v1/ai/suggestions", aiAssistHandler::handleAiSuggestions)
            .with(HttpMethod.POST, "/api/v1/ai/suggestions/:id/apply", aiAssistHandler::handleApplyAiSuggestion)
            .with(HttpMethod.GET, "/api/v1/ai/correlations", aiAssistHandler::handleAiCorrelations)
            // P2.1: AI action audit log for learning-loop telemetry
            .with(HttpMethod.GET, "/api/v1/ai/actions", aiAssistHandler::handleListAiActions)
            // P2.6: AI suggestion feedback for learning loop
            .with(HttpMethod.POST, "/api/v1/ai/suggestions/:id/feedback", aiAssistHandler::handleAiSuggestionFeedback)
            .with(HttpMethod.GET, "/api/v1/ai/feedback", aiAssistHandler::handleListAiFeedback)
            // P1.6: RAG feedback loop and agent memory retention policies
            .with(HttpMethod.POST, "/api/v1/ai/rag-feedback", aiAssistHandler::handleRagFeedback)
            .with(HttpMethod.POST, "/api/v1/ai/memory/retention", aiAssistHandler::handleMemoryRetention)
            // P2.2: Workflow risk analysis and DAG validation endpoints
            .with(HttpMethod.POST, "/api/v1/workflows/analyze-risk", aiAssistHandler::handleAnalyzeWorkflowRisk)
            .with(HttpMethod.POST, "/api/v1/workflows/validate", aiAssistHandler::handleWorkflowValidate)
            .with(HttpMethod.GET, "/api/v1/ai/advisories/workflows/:workflowId", aiAssistHandler::handleAiWorkflowAdvisory)
            .with(HttpMethod.GET, "/api/v1/ai/advisories/quality/:collectionId", aiAssistHandler::handleAiQualityAdvisory)
            .with(HttpMethod.GET, "/api/v1/ai/advisories/fabric/:collectionId", aiAssistHandler::handleAiFabricAdvisory);
        return this;
    }

    /**
     * Adds voice gateway endpoints.
     */
    public DataCloudRouterBuilder withVoiceRoutes(VoiceGatewayHandler voiceHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/voice/intent", voiceHandler::handleVoiceIntent)
            .with(HttpMethod.GET, "/api/v1/voice/intents", voiceHandler::handleListIntents)
            .with(HttpMethod.POST, "/api/v1/voice/intent/classify", voiceHandler::handleClassifyOnly);
        return this;
    }

    /**
     * Adds governance and data lifecycle endpoints.
     */
    public DataCloudRouterBuilder withGovernanceRoutes(DataLifecycleHandler dataLifecycleHandler) {
        builder
            .with(HttpMethod.POST, "/api/v1/governance/retention/classify", dataLifecycleHandler::handleClassifyRetention)
            .with(HttpMethod.GET, "/api/v1/governance/retention/policy", dataLifecycleHandler::handleGetRetentionPolicy)
            .with(HttpMethod.POST, "/api/v1/governance/retention/purge", dataLifecycleHandler::handlePurge)
            .with(HttpMethod.POST, "/api/v1/governance/privacy/redact", dataLifecycleHandler::handleRedact)
            .with(HttpMethod.GET, "/api/v1/governance/privacy/pii-fields", dataLifecycleHandler::handleListPiiFields)
            .with(HttpMethod.GET, "/api/v1/governance/privacy/verify", dataLifecycleHandler::handleVerifyRedaction)
            .with(HttpMethod.GET, "/api/v1/governance/compliance/summary", dataLifecycleHandler::handleComplianceSummary)
            .with(HttpMethod.GET, "/api/v1/governance/inventory", dataLifecycleHandler::handleGovernanceInventory);
        return this;
    }

    /**
     * Adds capability registry endpoints.
     */
    public DataCloudRouterBuilder withCapabilityRoutes(CapabilityRegistryHandler capabilityRegistryHandler) {
        builder.with(HttpMethod.GET, "/api/v1/capabilities", capabilityRegistryHandler::handleCapabilities);
        return this;
    }

    /**
     * Adds entity lineage graph endpoints.
     */
    public DataCloudRouterBuilder withLineageRoutes(LineageHandler lineageHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/lineage/:collection", lineageHandler::handleGetLineage)
            .with(HttpMethod.GET, "/api/v1/lineage/:collection/impact", lineageHandler::handleGetImpact);
        return this;
    }

    /**
     * Adds tenant-scoped context layer endpoints.
     */
    public DataCloudRouterBuilder withContextRoutes(
            ContextLayerHandler contextLayerHandler,
            CollectionContextHandler collectionContextHandler,
            SemanticSearchHandler semanticSearchHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/context", contextLayerHandler::handleGetContext)
            .with(HttpMethod.GET, "/api/v1/context/:collection", collectionContextHandler::handleGetCollectionContext)
            .with(HttpMethod.GET, "/api/v1/context/:collection/lineage/trust", collectionContextHandler::handleTrustCenterLineage)
            .with(HttpMethod.PUT, "/api/v1/context", contextLayerHandler::handlePutContext)
            .with(HttpMethod.DELETE, "/api/v1/context/keys/:key", contextLayerHandler::handleDeleteContextKey)
            .with(HttpMethod.GET, "/api/v1/context/snapshot", contextLayerHandler::handleGetSnapshot)
            .with(HttpMethod.POST, "/api/v1/context/:collection/rag", semanticSearchHandler::handleCollectionRag)
            .with(HttpMethod.POST, "/api/v1/context/:collection/rag-policy-check", collectionContextHandler::handleRagPolicyCheck);
        return this;
    }

    /**
     * Adds MCP tool discovery and invocation endpoints.
     */
    public DataCloudRouterBuilder withMcpRoutes(McpToolsHandler mcpToolsHandler) {
        builder
            .with(HttpMethod.GET, "/mcp/v1/tools", mcpToolsHandler::handleListTools)
            .with(HttpMethod.POST, "/mcp/v1/tools", mcpToolsHandler::handleToolCall);
        return this;
    }

    /**
     * Adds data product catalog endpoints.
     */
    public DataCloudRouterBuilder withDataProductRoutes(DataProductHandler dataProductHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/data-products", dataProductHandler::handleListDataProducts)
            .with(HttpMethod.POST, "/api/v1/data-products", dataProductHandler::handlePublishDataProduct)
            .with(HttpMethod.POST, "/api/v1/data-products/:productId/subscribe", dataProductHandler::handleSubscribe)
            // P1.3: SLA monitoring with automated degradation alerts
            .with(HttpMethod.POST, "/api/v1/data-products/:productId/sla-monitor", dataProductHandler::handleMonitorSla)
            // P1.3: Contract compatibility check for schema/SLA changes
            .with(HttpMethod.POST, "/api/v1/data-products/:productId/contract-check", dataProductHandler::handleCheckContractCompatibility);
        return this;
    }

    /**
     * Adds autonomy management endpoints.
     */
    public DataCloudRouterBuilder withAutonomyRoutes(AutonomyHandler autonomyHandler) {
        builder
            .with(HttpMethod.PUT, "/api/v1/autonomy/level", autonomyHandler::handleSetGlobalLevel)
            .with(HttpMethod.GET, "/api/v1/autonomy/level", autonomyHandler::handleGetGlobalLevel)
            .with(HttpMethod.GET, "/api/v1/autonomy/domains", autonomyHandler::handleListDomains)
            .with(HttpMethod.GET, "/api/v1/autonomy/domains/:domain", autonomyHandler::handleGetDomain)
            .with(HttpMethod.GET, "/api/v1/autonomy/logs", autonomyHandler::handleGetLogs)
            // P2.3: Expose automation plan to human operators
            .with(HttpMethod.GET, "/api/v1/autonomy/plan/:actionType", autonomyHandler::handleGetAutonomyPlan)
            // P2.6: Update autonomy policies from operator feedback patterns
            .with(HttpMethod.POST, "/api/v1/autonomy/feedback-policy", autonomyHandler::handleUpdatePolicyFromFeedback);
        return this;
    }

    /**
     * Adds agent catalog runtime endpoints.
     */
    public DataCloudRouterBuilder withAgentCatalogRoutes(AgentCatalogHandler agentCatalogHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/agents/catalog", agentCatalogHandler::handleListCatalog)
            .with(HttpMethod.GET, "/api/v1/agents/catalog/:id", agentCatalogHandler::handleGetAgent);
        return this;
    }

    /**
     * Adds plugin lifecycle management endpoints.
     */
    public DataCloudRouterBuilder withPluginRoutes(PluginInstallHandler pluginInstallHandler) {
        if (pluginInstallHandler == null) return this;
        builder
            .with(HttpMethod.GET, "/api/v1/plugins", pluginInstallHandler::handleListPlugins)
            .with(HttpMethod.GET, "/api/v1/plugins/:id", pluginInstallHandler::handleGetPlugin)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/enable", pluginInstallHandler::handleEnablePlugin)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/disable", pluginInstallHandler::handleDisablePlugin)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/upgrade", pluginInstallHandler::handleUpgradePlugin)
            .with(HttpMethod.GET, "/api/v1/plugins/marketplace", pluginInstallHandler::handleMarketplaceCatalog)
            .with(HttpMethod.GET, "/api/v1/plugins/:id/sandbox", pluginInstallHandler::handlePluginSandboxStatus)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/validate", pluginInstallHandler::handleValidatePluginSchema)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/conformance", pluginInstallHandler::handlePluginConformanceTest);
        return this;
    }

    /**
     * Adds storage cost estimation endpoints.
     */
    public DataCloudRouterBuilder withStorageCostRoutes(StorageCostHandler storageCostHandler, HttpHandlerSupport httpSupport) {
        if (storageCostHandler != null) {
            builder
                .with(HttpMethod.GET, "/api/v1/queries/estimate", storageCostHandler::handleEstimateQuery)
                .with(HttpMethod.GET, "/api/v1/collections/:id/cost-report", storageCostHandler::handleCollectionCostReport);
        } else {
            builder
                .with(HttpMethod.GET, "/api/v1/queries/estimate", req -> Promise.of(httpSupport.errorResponse(503, "Analytics engine not available")))
                .with(HttpMethod.GET, "/api/v1/collections/:id/cost-report", req -> Promise.of(httpSupport.errorResponse(503, "Analytics engine not available")));
        }
        return this;
    }

    /**
     * Adds federated Trino query endpoint.
     */
    public DataCloudRouterBuilder withFederatedQueryRoutes(FederatedQueryHandler federatedQueryHandler, HttpHandlerSupport httpSupport) {
        if (federatedQueryHandler != null) {
            builder.with(HttpMethod.POST, "/api/v1/queries/federated", federatedQueryHandler::handleFederatedQuery);
        } else {
            builder.with(HttpMethod.POST, "/api/v1/queries/federated", req -> Promise.of(httpSupport.errorResponse(503, "Analytics engine not available")));
        }
        return this;
    }

    /**
     * Adds manual tier migration endpoint.
     */
    public DataCloudRouterBuilder withTierMigrationRoutes(TierMigrationHandler tierMigrationHandler, HttpHandlerSupport httpSupport) {
        if (tierMigrationHandler != null) {
            builder.with(HttpMethod.POST, "/api/v1/collections/:id/migrate", tierMigrationHandler::handleMigrateCollection);
        } else {
            builder.with(HttpMethod.POST, "/api/v1/collections/:id/migrate", req -> Promise.of(httpSupport.errorResponse(503, "Tier migration schedulers are not configured")));
        }
        return this;
    }

    /**
     * Adds connector registry endpoints for external data sources (P1.1).
     */
    public DataCloudRouterBuilder withConnectorRoutes(DataSourceRegistryHandler dataSourceRegistryHandler, HttpHandlerSupport httpSupport) {
        if (dataSourceRegistryHandler != null) {
            builder
                .with(HttpMethod.GET,  "/api/v1/connectors", dataSourceRegistryHandler::handleListConnections)
                .with(HttpMethod.POST, "/api/v1/connectors", dataSourceRegistryHandler::handleRegisterConnection)
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId", dataSourceRegistryHandler::handleGetConnection)
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/test", dataSourceRegistryHandler::handleTestConnection)
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/enable", dataSourceRegistryHandler::handleEnableConnection)
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/disable", dataSourceRegistryHandler::handleDisableConnection)
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/rotate-credentials", dataSourceRegistryHandler::handleRotateCredentials)
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId/health", dataSourceRegistryHandler::handleGetHealth)
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId/schema", dataSourceRegistryHandler::handleGetSchema)
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/sync", dataSourceRegistryHandler::handleTriggerSync)
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId/sync/status", dataSourceRegistryHandler::handleGetSyncStatus);
        } else {
            builder
                .with(HttpMethod.GET,  "/api/v1/connectors", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.POST, "/api/v1/connectors", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/test", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/enable", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/disable", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/rotate-credentials", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId/health", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId/schema", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.POST, "/api/v1/connectors/:connectionId/sync", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")))
                .with(HttpMethod.GET,  "/api/v1/connectors/:connectionId/sync/status", req -> Promise.of(httpSupport.errorResponse(503, "Connector registry not available")));
        }
        return this;
    }

    /**
     * Adds admin settings CRUD endpoints (GH-90000 / ADMIN-004).
     */
    public DataCloudRouterBuilder withSettingsRoutes(SettingsHandler settingsHandler) {
        builder
            .with(HttpMethod.GET, "/api/v1/settings", settingsHandler::handleGetGeneralSettings)
            .with(HttpMethod.POST, "/api/v1/settings", settingsHandler::handleUpdateGeneralSettings)
            .with(HttpMethod.GET, "/api/v1/settings/security", settingsHandler::handleGetSecuritySettings)
            .with(HttpMethod.POST, "/api/v1/settings/security", settingsHandler::handleUpdateSecuritySettings)
            .with(HttpMethod.GET, "/api/v1/settings/keys", settingsHandler::handleListApiKeys)
            .with(HttpMethod.POST, "/api/v1/settings/keys", settingsHandler::handleCreateApiKey)
            .with(HttpMethod.GET, "/api/v1/settings/keys/:id", settingsHandler::handleGetApiKey)
            .with(HttpMethod.POST, "/api/v1/settings/keys/:id/rotate", settingsHandler::handleRotateApiKey)
            .with(HttpMethod.DELETE, "/api/v1/settings/keys/:id/revoke", settingsHandler::handleRevokeApiKey)
            .with(HttpMethod.GET, "/api/v1/settings/profile", settingsHandler::handleGetProfile)
            .with(HttpMethod.PATCH, "/api/v1/settings/profile", settingsHandler::handleUpdateProfile)
            .with(HttpMethod.GET, "/api/v1/settings/preferences", settingsHandler::handleGetPreferences)
            .with(HttpMethod.PATCH, "/api/v1/settings/preferences", settingsHandler::handleUpdatePreferences)
            .with(HttpMethod.GET, "/api/v1/settings/notifications", settingsHandler::handleGetNotificationPreferences)
            .with(HttpMethod.PATCH, "/api/v1/settings/notifications", settingsHandler::handleUpdateNotificationPreferences)
            // P3.5: Admin approval workflow for sensitive settings changes
            .with(HttpMethod.POST, "/api/v1/settings/approval-request", settingsHandler::handleRequestApproval)
            .with(HttpMethod.GET, "/api/v1/settings/approvals", settingsHandler::handleListApprovals)
            .with(HttpMethod.POST, "/api/v1/settings/approvals/:id/approve", settingsHandler::handleApproveRequest)
            .with(HttpMethod.POST, "/api/v1/settings/approvals/:id/reject", settingsHandler::handleRejectRequest);
        return this;
    }

    /**
     * Adds compliance endpoints for legal holds and evidence package generation (P3.6).
     */
    public DataCloudRouterBuilder withComplianceRoutes(ComplianceHandler complianceHandler) {
        if (complianceHandler == null) return this;
        builder
            .with(HttpMethod.POST, "/api/v1/compliance/legal-holds", complianceHandler::handleApplyLegalHold)
            .with(HttpMethod.GET, "/api/v1/compliance/legal-holds", complianceHandler::handleListLegalHolds)
            .with(HttpMethod.POST, "/api/v1/compliance/legal-holds/:id/extend", complianceHandler::handleExtendLegalHold)
            .with(HttpMethod.POST, "/api/v1/compliance/legal-holds/:id/release", complianceHandler::handleReleaseLegalHold)
            .with(HttpMethod.POST, "/api/v1/compliance/evidence-package", complianceHandler::handleGenerateEvidencePackage)
            .with(HttpMethod.GET, "/api/v1/compliance/posture", complianceHandler::handleCompliancePosture);
        return this;
    }

    /**
     * Adds sovereign profile and air-gapped policy endpoints (P3.3).
     */
    public DataCloudRouterBuilder withConformanceRoutes(ProviderConformanceHandler conformanceHandler) {
        if (conformanceHandler == null) return this;
        builder
            .with(HttpMethod.GET, "/api/v1/conformance", conformanceHandler::handleFullConformance)
            .with(HttpMethod.GET, "/api/v1/conformance/entity-store", conformanceHandler::handleEntityStoreConformance)
            .with(HttpMethod.GET, "/api/v1/conformance/event-log-store", conformanceHandler::handleEventLogStoreConformance);
        return this;
    }

    public DataCloudRouterBuilder withSovereignProfileRoutes(SovereignProfileHandler sovereignProfileHandler) {
        if (sovereignProfileHandler == null) return this;
        builder
            .with(HttpMethod.GET, "/api/v1/sovereign/profile", sovereignProfileHandler::handleGetSovereignProfile)
            .with(HttpMethod.PUT, "/api/v1/sovereign/profile", sovereignProfileHandler::handleUpdateSovereignProfile)
            .with(HttpMethod.GET, "/api/v1/sovereign/models", sovereignProfileHandler::handleGetModelPolicy)
            .with(HttpMethod.GET, "/api/v1/sovereign/audit", sovereignProfileHandler::handleGetSovereignAudit)
            .with(HttpMethod.GET, "/api/v1/sovereign/backup", sovereignProfileHandler::handleGetBackupStatus)
            .with(HttpMethod.POST, "/api/v1/sovereign/backup", sovereignProfileHandler::handleTriggerBackup)
            .with(HttpMethod.POST, "/api/v1/sovereign/restore", sovereignProfileHandler::handleRestoreBackup)
            .with(HttpMethod.GET, "/api/v1/sovereign/data-subject-controls", sovereignProfileHandler::handleGetDataSubjectControls)
            .with(HttpMethod.PUT, "/api/v1/sovereign/data-subject-controls", sovereignProfileHandler::handleUpdateDataSubjectControls)
            .with(HttpMethod.GET, "/api/v1/sovereign/conformance", sovereignProfileHandler::handleRunConformanceTests)
            .with(HttpMethod.GET, "/api/v1/sovereign/data-residency", sovereignProfileHandler::handleGetDataResidencyAudit)
            .with(HttpMethod.POST, "/api/v1/sovereign/validate-transfer", sovereignProfileHandler::handleValidateTransfer)
            .with(HttpMethod.GET, "/api/v1/sovereign/region-policy", sovereignProfileHandler::handleGetRegionPolicy);
        return this;
    }

    /**
     * Builds the routing servlet.
     *
     * @return the configured routing servlet
     */
    public RoutingServlet build() {
        log.info("Data-Cloud router built with {} route groups", 28);
        return builder.build();
    }
}
