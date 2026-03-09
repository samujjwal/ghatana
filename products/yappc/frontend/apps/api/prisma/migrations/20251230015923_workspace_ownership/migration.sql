-- CreateEnum
CREATE TYPE "Role" AS ENUM ('ADMIN', 'EDITOR', 'VIEWER');

-- CreateEnum
CREATE TYPE "ProjectType" AS ENUM ('UI', 'BACKEND', 'MOBILE', 'DESKTOP', 'FULL_STACK');

-- CreateEnum
CREATE TYPE "ProjectStatus" AS ENUM ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "WorkflowType" AS ENUM ('BUG_FIX', 'FEATURE', 'INCIDENT', 'REFACTOR', 'MIGRATION', 'SECURITY_UPDATE', 'AI_ASSIST', 'DOCUMENTATION', 'TESTING', 'INFRASTRUCTURE');

-- CreateEnum
CREATE TYPE "WorkflowStatus" AS ENUM ('DRAFT', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "WorkflowStep" AS ENUM ('INTENT', 'CONTEXT', 'PLAN', 'EXECUTE', 'VERIFY', 'OBSERVE', 'LEARN', 'INSTITUTIONALIZE');

-- CreateEnum
CREATE TYPE "StepStatus" AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'REVISITED', 'BLOCKED');

-- CreateEnum
CREATE TYPE "AIMode" AS ENUM ('ASSIST', 'RESTRICTED', 'SIMULATE', 'AGENT_READY');

-- CreateEnum
CREATE TYPE "AuditAction" AS ENUM ('CREATED', 'STEP_STARTED', 'STEP_COMPLETED', 'STEP_REVISITED', 'DATA_UPDATED', 'AI_SUGGESTION_ACCEPTED', 'AI_SUGGESTION_REJECTED', 'STATUS_CHANGED', 'OWNER_CHANGED');

-- CreateEnum
CREATE TYPE "PhaseStatus" AS ENUM ('ACTIVE', 'INACTIVE', 'COMPLETED', 'AT_RISK');

-- CreateEnum
CREATE TYPE "MilestoneStatus" AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'DELAYED', 'AT_RISK');

-- CreateEnum
CREATE TYPE "ItemType" AS ENUM ('FEATURE', 'STORY', 'TASK', 'BUG', 'EPIC', 'SPIKE', 'SECURITY_ISSUE', 'TECH_DEBT');

-- CreateEnum
CREATE TYPE "ItemPriority" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "ItemStatus" AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW', 'COMPLETED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "DependencyType" AS ENUM ('BLOCKS', 'DEPENDS_ON', 'RELATED_TO');

-- CreateEnum
CREATE TYPE "ArtifactType" AS ENUM ('DIAGRAM', 'DOCUMENT', 'CODE', 'TEST', 'DESIGN', 'SCRIPT', 'REPORT', 'PRESENTATION');

-- CreateEnum
CREATE TYPE "IntegrationProvider" AS ENUM ('JIRA', 'GITHUB', 'GITLAB', 'SONARQUBE', 'JENKINS', 'CUSTOM');

-- CreateEnum
CREATE TYPE "KPICategory" AS ENUM ('VELOCITY', 'QUALITY', 'SECURITY', 'OPERATIONS', 'CUSTOM');

-- CreateEnum
CREATE TYPE "TrendDirection" AS ENUM ('UP', 'DOWN', 'NEUTRAL');

-- CreateEnum
CREATE TYPE "InsightType" AS ENUM ('RECOMMENDATION', 'WARNING', 'OPTIMIZATION', 'RISK_ALERT', 'TREND_ANALYSIS', 'ANOMALY', 'PREDICTION', 'SUGGESTION');

-- CreateEnum
CREATE TYPE "InsightSeverity" AS ENUM ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "InsightStatus" AS ENUM ('ACTIVE', 'ACKNOWLEDGED', 'DISMISSED', 'RESOLVED', 'EXPIRED');

-- CreateEnum
CREATE TYPE "PredictionTarget" AS ENUM ('ITEM', 'PHASE', 'MILESTONE', 'WORKFLOW', 'TEAM');

-- CreateEnum
CREATE TYPE "PredictionType" AS ENUM ('DEADLINE_RISK', 'BLOCKER_LIKELIHOOD', 'SCOPE_CREEP', 'RESOURCE_CONSTRAINT', 'QUALITY_RISK', 'VELOCITY_CHANGE', 'COMPLETION_DATE');

-- CreateEnum
CREATE TYPE "AnomalyType" AS ENUM ('VELOCITY', 'PATTERN', 'SECURITY', 'QUALITY', 'RESOURCE');

-- CreateEnum
CREATE TYPE "AnomalySeverity" AS ENUM ('INFO', 'WARNING', 'CRITICAL');

-- CreateEnum
CREATE TYPE "SessionStatus" AS ENUM ('ACTIVE', 'COMPLETED', 'ABANDONED', 'ERROR');

-- CreateEnum
CREATE TYPE "SessionFeedback" AS ENUM ('HELPFUL', 'NOT_HELPFUL', 'NEUTRAL');

-- CreateEnum
CREATE TYPE "ExecutionStatus" AS ENUM ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED');

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "avatar" TEXT,
    "role" "Role" NOT NULL DEFAULT 'EDITOR',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Workspace" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "ownerId" TEXT NOT NULL,
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "aiSummary" TEXT,
    "aiTags" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Workspace_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkspaceMember" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "workspaceId" TEXT NOT NULL,
    "role" "Role" NOT NULL DEFAULT 'EDITOR',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkspaceMember_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Project" (
    "id" TEXT NOT NULL,
    "ownerWorkspaceId" TEXT NOT NULL,
    "createdById" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "type" "ProjectType" NOT NULL,
    "status" "ProjectStatus" NOT NULL DEFAULT 'DRAFT',
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "aiSummary" TEXT,
    "aiNextActions" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "aiHealthScore" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Project_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkspaceProject" (
    "id" TEXT NOT NULL,
    "workspaceId" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "addedById" TEXT NOT NULL,
    "aiInclusionReason" TEXT,
    "addedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WorkspaceProject_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CanvasDocument" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "createdById" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "content" JSONB NOT NULL DEFAULT '{"nodes": [], "edges": [], "viewport": {"zoom": 1, "x": 0, "y": 0}}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CanvasDocument_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Page" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "createdById" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "path" TEXT NOT NULL,
    "layout" TEXT NOT NULL DEFAULT 'flex',
    "content" JSONB NOT NULL DEFAULT '{"components": []}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Page_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Workflow" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "workflowType" "WorkflowType" NOT NULL,
    "currentStep" "WorkflowStep" NOT NULL DEFAULT 'INTENT',
    "status" "WorkflowStatus" NOT NULL DEFAULT 'DRAFT',
    "aiMode" "AIMode" NOT NULL DEFAULT 'ASSIST',
    "ownerId" TEXT NOT NULL,
    "ownerName" TEXT NOT NULL,
    "projectId" TEXT,
    "templateId" TEXT,
    "steps" JSONB NOT NULL DEFAULT '{}',
    "metrics" JSONB NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Workflow_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkflowContributor" (
    "id" TEXT NOT NULL,
    "workflowId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "userName" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'CONTRIBUTOR',
    "joinedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WorkflowContributor_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkflowAudit" (
    "id" TEXT NOT NULL,
    "workflowId" TEXT NOT NULL,
    "action" "AuditAction" NOT NULL,
    "step" "WorkflowStep",
    "userId" TEXT NOT NULL,
    "userName" TEXT NOT NULL,
    "details" JSONB,
    "previousValue" JSONB,
    "newValue" JSONB,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WorkflowAudit_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WorkflowTemplate" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "workflowType" "WorkflowType" NOT NULL,
    "defaultIntent" JSONB NOT NULL DEFAULT '{}',
    "requiredFields" JSONB NOT NULL DEFAULT '{}',
    "defaultRisks" JSONB NOT NULL DEFAULT '[]',
    "defaultMetrics" JSONB NOT NULL DEFAULT '[]',
    "isSystem" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "WorkflowTemplate_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Phase" (
    "id" TEXT NOT NULL,
    "key" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "order" INTEGER NOT NULL,
    "color" TEXT NOT NULL,
    "icon" TEXT,
    "status" "PhaseStatus" NOT NULL DEFAULT 'ACTIVE',
    "healthScore" DOUBLE PRECISION,
    "riskScore" DOUBLE PRECISION,
    "predictedEndDate" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Phase_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Milestone" (
    "id" TEXT NOT NULL,
    "phaseId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "dueDate" TIMESTAMP(3) NOT NULL,
    "status" "MilestoneStatus" NOT NULL DEFAULT 'PENDING',
    "ownerId" TEXT,
    "progress" INTEGER NOT NULL DEFAULT 0,
    "predictedCompletionDate" TIMESTAMP(3),
    "riskScore" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Milestone_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Item" (
    "id" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "type" "ItemType" NOT NULL,
    "priority" "ItemPriority" NOT NULL DEFAULT 'MEDIUM',
    "status" "ItemStatus" NOT NULL DEFAULT 'NOT_STARTED',
    "phaseId" TEXT NOT NULL,
    "workflowId" TEXT,
    "progress" INTEGER NOT NULL DEFAULT 0,
    "startDate" TIMESTAMP(3),
    "dueDate" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "estimatedHours" INTEGER,
    "actualHours" INTEGER,
    "aiPriorityScore" DOUBLE PRECISION,
    "riskScore" DOUBLE PRECISION,
    "predictedDueDate" TIMESTAMP(3),
    "sentimentScore" DOUBLE PRECISION,
    "parentId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Item_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItemOwner" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'OWNER',

    CONSTRAINT "ItemOwner_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItemTag" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "tag" TEXT NOT NULL,

    CONSTRAINT "ItemTag_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItemDependency" (
    "id" TEXT NOT NULL,
    "dependentId" TEXT NOT NULL,
    "blockingId" TEXT NOT NULL,
    "type" "DependencyType" NOT NULL DEFAULT 'BLOCKS',

    CONSTRAINT "ItemDependency_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItemComment" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "sentimentScore" DOUBLE PRECISION,
    "sentimentLabel" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ItemComment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Artifact" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "type" "ArtifactType" NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "url" TEXT,
    "content" TEXT,
    "format" TEXT,
    "version" TEXT,
    "createdById" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Artifact_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItemIntegration" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "provider" "IntegrationProvider" NOT NULL,
    "externalId" TEXT NOT NULL,
    "externalUrl" TEXT,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "syncedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ItemIntegration_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PhaseKPI" (
    "id" TEXT NOT NULL,
    "phaseId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "category" "KPICategory" NOT NULL,
    "value" DOUBLE PRECISION NOT NULL,
    "unit" TEXT,
    "target" DOUBLE PRECISION,
    "warningThreshold" DOUBLE PRECISION,
    "criticalThreshold" DOUBLE PRECISION,
    "trendDirection" "TrendDirection",
    "trendPercentage" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "PhaseKPI_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AIInsight" (
    "id" TEXT NOT NULL,
    "itemId" TEXT,
    "phaseId" TEXT,
    "workflowId" TEXT,
    "type" "InsightType" NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "confidence" DOUBLE PRECISION NOT NULL,
    "severity" "InsightSeverity" NOT NULL DEFAULT 'INFO',
    "status" "InsightStatus" NOT NULL DEFAULT 'ACTIVE',
    "actionable" BOOLEAN NOT NULL DEFAULT true,
    "suggestedAction" JSONB,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "agentName" TEXT NOT NULL,
    "modelVersion" TEXT,
    "acknowledged" BOOLEAN NOT NULL DEFAULT false,
    "acknowledgedBy" TEXT,
    "acknowledgedAt" TIMESTAMP(3),
    "feedbackScore" INTEGER,
    "feedbackComment" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3),

    CONSTRAINT "AIInsight_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Prediction" (
    "id" TEXT NOT NULL,
    "targetType" "PredictionTarget" NOT NULL,
    "targetId" TEXT NOT NULL,
    "type" "PredictionType" NOT NULL,
    "probability" DOUBLE PRECISION NOT NULL,
    "timeline" TEXT,
    "affectedItems" JSONB NOT NULL DEFAULT '[]',
    "suggestedMitigation" JSONB NOT NULL DEFAULT '[]',
    "confidence" DOUBLE PRECISION NOT NULL,
    "modelName" TEXT NOT NULL,
    "modelVersion" TEXT NOT NULL,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "actualOutcome" BOOLEAN,
    "outcomeRecordedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3),

    CONSTRAINT "Prediction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PhasePrediction" (
    "id" TEXT NOT NULL,
    "phaseId" TEXT NOT NULL,
    "predictedEndDate" TIMESTAMP(3) NOT NULL,
    "confidenceInterval" DOUBLE PRECISION NOT NULL,
    "riskFactors" JSONB NOT NULL DEFAULT '[]',
    "recommendations" JSONB NOT NULL DEFAULT '[]',
    "modelVersion" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PhasePrediction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnomalyAlert" (
    "id" TEXT NOT NULL,
    "type" "AnomalyType" NOT NULL,
    "severity" "AnomalySeverity" NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "affectedItems" JSONB NOT NULL DEFAULT '[]',
    "baselineValue" DOUBLE PRECISION NOT NULL,
    "currentValue" DOUBLE PRECISION NOT NULL,
    "deviationPercent" DOUBLE PRECISION NOT NULL,
    "suggestedActions" JSONB NOT NULL DEFAULT '[]',
    "confidence" DOUBLE PRECISION NOT NULL,
    "modelVersion" TEXT NOT NULL,
    "acknowledged" BOOLEAN NOT NULL DEFAULT false,
    "acknowledgedBy" TEXT,
    "acknowledgedAt" TIMESTAMP(3),
    "resolved" BOOLEAN NOT NULL DEFAULT false,
    "resolvedAt" TIMESTAMP(3),
    "falsePositive" BOOLEAN NOT NULL DEFAULT false,
    "detectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AnomalyAlert_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ItemEmbedding" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "embedding" BYTEA NOT NULL,
    "dimensions" INTEGER NOT NULL,
    "contentHash" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ItemEmbedding_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UserAIPreferences" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "enableAISuggestions" BOOLEAN NOT NULL DEFAULT true,
    "enablePredictions" BOOLEAN NOT NULL DEFAULT true,
    "enableCopilot" BOOLEAN NOT NULL DEFAULT true,
    "preferredModel" TEXT NOT NULL DEFAULT 'gpt-4',
    "temperature" DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    "maxTokens" INTEGER NOT NULL DEFAULT 2048,
    "autoAcceptLow" BOOLEAN NOT NULL DEFAULT false,
    "notificationLevel" TEXT NOT NULL DEFAULT 'all',
    "customPromptPrefix" TEXT,
    "excludedAgents" JSONB NOT NULL DEFAULT '[]',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "UserAIPreferences_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CopilotSession" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "title" TEXT,
    "status" "SessionStatus" NOT NULL DEFAULT 'ACTIVE',
    "context" JSONB NOT NULL DEFAULT '{}',
    "messages" JSONB NOT NULL DEFAULT '[]',
    "actionsExecuted" JSONB NOT NULL DEFAULT '[]',
    "tokensUsed" INTEGER NOT NULL DEFAULT 0,
    "modelUsed" TEXT NOT NULL,
    "persona" TEXT,
    "costUSD" DOUBLE PRECISION,
    "satisfactionRating" INTEGER,
    "feedback" "SessionFeedback",
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "endedAt" TIMESTAMP(3),

    CONSTRAINT "CopilotSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AgentExecution" (
    "id" TEXT NOT NULL,
    "agentName" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "workspaceId" TEXT,
    "input" JSONB NOT NULL,
    "output" JSONB,
    "status" "ExecutionStatus" NOT NULL,
    "latencyMs" INTEGER NOT NULL,
    "tokensUsed" INTEGER,
    "modelVersion" TEXT,
    "errorMessage" TEXT,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMP(3),

    CONSTRAINT "AgentExecution_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActivityLog" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "itemId" TEXT,
    "phaseId" TEXT,
    "workflowId" TEXT,
    "action" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ActivityLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AIGeneratedPlan" (
    "id" TEXT NOT NULL,
    "workflowId" TEXT NOT NULL,
    "intent" TEXT NOT NULL,
    "context" JSONB NOT NULL DEFAULT '{}',
    "tasks" JSONB NOT NULL DEFAULT '[]',
    "estimatedDuration" TEXT NOT NULL,
    "riskFactors" JSONB NOT NULL DEFAULT '[]',
    "dependencies" JSONB NOT NULL DEFAULT '[]',
    "suggestedAssignments" JSONB NOT NULL DEFAULT '[]',
    "alternativePlans" JSONB NOT NULL DEFAULT '[]',
    "confidence" DOUBLE PRECISION NOT NULL,
    "reasoning" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "accepted" BOOLEAN NOT NULL DEFAULT false,
    "acceptedAt" TIMESTAMP(3),

    CONSTRAINT "AIGeneratedPlan_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VectorEmbedding" (
    "id" TEXT NOT NULL,
    "itemId" TEXT NOT NULL,
    "embedding" DOUBLE PRECISION[],
    "dimensions" INTEGER NOT NULL,
    "model" TEXT NOT NULL,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "VectorEmbedding_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AIMetric" (
    "id" TEXT NOT NULL,
    "agentName" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "operation" TEXT NOT NULL,
    "tokensUsed" INTEGER NOT NULL,
    "latencyMs" INTEGER NOT NULL,
    "costUSD" DOUBLE PRECISION NOT NULL,
    "success" BOOLEAN NOT NULL,
    "errorMessage" TEXT,
    "userId" TEXT,
    "sessionId" TEXT,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AIMetric_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AuditLogEntry" (
    "id" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "action" TEXT NOT NULL,
    "actor" TEXT NOT NULL,
    "actorRole" TEXT NOT NULL,
    "resource" TEXT,
    "severity" TEXT NOT NULL DEFAULT 'info',
    "details" TEXT,
    "ipAddress" TEXT,
    "userAgent" TEXT,
    "method" TEXT,
    "status" INTEGER,
    "responseTime" INTEGER,
    "snapshotId" TEXT,
    "environment" TEXT,
    "approvalRequestId" TEXT,
    "reason" TEXT,
    "success" BOOLEAN,
    "error" TEXT,
    "metadata" JSONB,
    "tenantId" TEXT,

    CONSTRAINT "AuditLogEntry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RateLimitConfig" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "tier" TEXT NOT NULL DEFAULT 'free',
    "userTiers" JSONB NOT NULL,
    "enableAuditLog" BOOLEAN NOT NULL DEFAULT true,
    "maxAuditLogEntries" INTEGER NOT NULL DEFAULT 10000,
    "alertConfig" JSONB,
    "upgradedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RateLimitConfig_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UpgradeRequest" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "tier" TEXT NOT NULL,
    "requestedTier" TEXT NOT NULL,
    "currentTier" TEXT NOT NULL,
    "reason" TEXT,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "processedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "UpgradeRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ComplianceAssessment" (
    "id" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "framework" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "riskScore" DOUBLE PRECISION,
    "findings" JSONB,
    "controls" JSONB,
    "gaps" JSONB,
    "auditTrail" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ComplianceAssessment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RemediationPlan" (
    "id" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "totalEffort" INTEGER,
    "completionTarget" TIMESTAMP(3),
    "riskScore" DOUBLE PRECISION,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RemediationPlan_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RemediationStep" (
    "id" TEXT NOT NULL,
    "planId" TEXT NOT NULL,
    "controlId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "priority" TEXT NOT NULL DEFAULT 'MEDIUM',
    "estimatedEffort" INTEGER,
    "owner" TEXT,
    "deadline" TIMESTAMP(3),
    "status" TEXT NOT NULL DEFAULT 'OPEN',
    "evidence" TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RemediationStep_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ComplianceReport" (
    "id" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "summary" TEXT,
    "generatedBy" TEXT NOT NULL,
    "format" TEXT NOT NULL DEFAULT 'PDF',
    "url" TEXT,
    "framework" TEXT,
    "metrics" JSONB,
    "filename" TEXT,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ComplianceReport_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ReportSchedule" (
    "id" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "frequency" TEXT NOT NULL,
    "recipients" TEXT[],
    "config" JSONB,
    "nextRun" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ReportSchedule_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FlowState" (
    "id" TEXT NOT NULL,
    "flowId" TEXT NOT NULL,
    "currentState" TEXT NOT NULL,
    "context" JSONB NOT NULL DEFAULT '{}',
    "history" JSONB NOT NULL DEFAULT '[]',
    "artifacts" JSONB NOT NULL DEFAULT '[]',
    "activeTasks" JSONB NOT NULL DEFAULT '[]',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "FlowState_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "_StepDependencies" (
    "A" TEXT NOT NULL,
    "B" TEXT NOT NULL,

    CONSTRAINT "_StepDependencies_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

-- CreateIndex
CREATE INDEX "User_email_idx" ON "User"("email");

-- CreateIndex
CREATE INDEX "Workspace_ownerId_idx" ON "Workspace"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "Workspace_ownerId_isDefault_key" ON "Workspace"("ownerId", "isDefault");

-- CreateIndex
CREATE INDEX "WorkspaceMember_workspaceId_idx" ON "WorkspaceMember"("workspaceId");

-- CreateIndex
CREATE UNIQUE INDEX "WorkspaceMember_userId_workspaceId_key" ON "WorkspaceMember"("userId", "workspaceId");

-- CreateIndex
CREATE INDEX "Project_ownerWorkspaceId_idx" ON "Project"("ownerWorkspaceId");

-- CreateIndex
CREATE INDEX "Project_createdById_idx" ON "Project"("createdById");

-- CreateIndex
CREATE UNIQUE INDEX "Project_ownerWorkspaceId_isDefault_key" ON "Project"("ownerWorkspaceId", "isDefault");

-- CreateIndex
CREATE INDEX "WorkspaceProject_workspaceId_idx" ON "WorkspaceProject"("workspaceId");

-- CreateIndex
CREATE INDEX "WorkspaceProject_projectId_idx" ON "WorkspaceProject"("projectId");

-- CreateIndex
CREATE UNIQUE INDEX "WorkspaceProject_workspaceId_projectId_key" ON "WorkspaceProject"("workspaceId", "projectId");

-- CreateIndex
CREATE INDEX "CanvasDocument_projectId_idx" ON "CanvasDocument"("projectId");

-- CreateIndex
CREATE INDEX "CanvasDocument_createdById_idx" ON "CanvasDocument"("createdById");

-- CreateIndex
CREATE INDEX "Page_projectId_idx" ON "Page"("projectId");

-- CreateIndex
CREATE INDEX "Page_createdById_idx" ON "Page"("createdById");

-- CreateIndex
CREATE UNIQUE INDEX "Page_projectId_path_key" ON "Page"("projectId", "path");

-- CreateIndex
CREATE INDEX "Workflow_ownerId_idx" ON "Workflow"("ownerId");

-- CreateIndex
CREATE INDEX "Workflow_projectId_idx" ON "Workflow"("projectId");

-- CreateIndex
CREATE INDEX "Workflow_status_idx" ON "Workflow"("status");

-- CreateIndex
CREATE INDEX "Workflow_workflowType_idx" ON "Workflow"("workflowType");

-- CreateIndex
CREATE INDEX "WorkflowContributor_workflowId_idx" ON "WorkflowContributor"("workflowId");

-- CreateIndex
CREATE INDEX "WorkflowContributor_userId_idx" ON "WorkflowContributor"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "WorkflowContributor_workflowId_userId_key" ON "WorkflowContributor"("workflowId", "userId");

-- CreateIndex
CREATE INDEX "WorkflowAudit_workflowId_idx" ON "WorkflowAudit"("workflowId");

-- CreateIndex
CREATE INDEX "WorkflowAudit_userId_idx" ON "WorkflowAudit"("userId");

-- CreateIndex
CREATE INDEX "WorkflowAudit_action_idx" ON "WorkflowAudit"("action");

-- CreateIndex
CREATE INDEX "WorkflowTemplate_workflowType_idx" ON "WorkflowTemplate"("workflowType");

-- CreateIndex
CREATE INDEX "WorkflowTemplate_isSystem_idx" ON "WorkflowTemplate"("isSystem");

-- CreateIndex
CREATE UNIQUE INDEX "Phase_key_key" ON "Phase"("key");

-- CreateIndex
CREATE INDEX "Phase_key_idx" ON "Phase"("key");

-- CreateIndex
CREATE INDEX "Phase_order_idx" ON "Phase"("order");

-- CreateIndex
CREATE INDEX "Milestone_phaseId_idx" ON "Milestone"("phaseId");

-- CreateIndex
CREATE INDEX "Milestone_status_idx" ON "Milestone"("status");

-- CreateIndex
CREATE INDEX "Item_phaseId_idx" ON "Item"("phaseId");

-- CreateIndex
CREATE INDEX "Item_type_idx" ON "Item"("type");

-- CreateIndex
CREATE INDEX "Item_status_idx" ON "Item"("status");

-- CreateIndex
CREATE INDEX "Item_priority_idx" ON "Item"("priority");

-- CreateIndex
CREATE INDEX "Item_workflowId_idx" ON "Item"("workflowId");

-- CreateIndex
CREATE INDEX "ItemOwner_itemId_idx" ON "ItemOwner"("itemId");

-- CreateIndex
CREATE INDEX "ItemOwner_userId_idx" ON "ItemOwner"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ItemOwner_itemId_userId_key" ON "ItemOwner"("itemId", "userId");

-- CreateIndex
CREATE INDEX "ItemTag_itemId_idx" ON "ItemTag"("itemId");

-- CreateIndex
CREATE INDEX "ItemTag_tag_idx" ON "ItemTag"("tag");

-- CreateIndex
CREATE UNIQUE INDEX "ItemTag_itemId_tag_key" ON "ItemTag"("itemId", "tag");

-- CreateIndex
CREATE INDEX "ItemDependency_dependentId_idx" ON "ItemDependency"("dependentId");

-- CreateIndex
CREATE INDEX "ItemDependency_blockingId_idx" ON "ItemDependency"("blockingId");

-- CreateIndex
CREATE UNIQUE INDEX "ItemDependency_dependentId_blockingId_key" ON "ItemDependency"("dependentId", "blockingId");

-- CreateIndex
CREATE INDEX "ItemComment_itemId_idx" ON "ItemComment"("itemId");

-- CreateIndex
CREATE INDEX "ItemComment_userId_idx" ON "ItemComment"("userId");

-- CreateIndex
CREATE INDEX "Artifact_itemId_idx" ON "Artifact"("itemId");

-- CreateIndex
CREATE INDEX "Artifact_type_idx" ON "Artifact"("type");

-- CreateIndex
CREATE INDEX "ItemIntegration_itemId_idx" ON "ItemIntegration"("itemId");

-- CreateIndex
CREATE INDEX "ItemIntegration_provider_idx" ON "ItemIntegration"("provider");

-- CreateIndex
CREATE UNIQUE INDEX "ItemIntegration_itemId_provider_externalId_key" ON "ItemIntegration"("itemId", "provider", "externalId");

-- CreateIndex
CREATE INDEX "PhaseKPI_phaseId_idx" ON "PhaseKPI"("phaseId");

-- CreateIndex
CREATE INDEX "PhaseKPI_category_idx" ON "PhaseKPI"("category");

-- CreateIndex
CREATE INDEX "AIInsight_itemId_idx" ON "AIInsight"("itemId");

-- CreateIndex
CREATE INDEX "AIInsight_phaseId_idx" ON "AIInsight"("phaseId");

-- CreateIndex
CREATE INDEX "AIInsight_type_idx" ON "AIInsight"("type");

-- CreateIndex
CREATE INDEX "AIInsight_status_idx" ON "AIInsight"("status");

-- CreateIndex
CREATE INDEX "AIInsight_agentName_idx" ON "AIInsight"("agentName");

-- CreateIndex
CREATE INDEX "Prediction_targetType_targetId_idx" ON "Prediction"("targetType", "targetId");

-- CreateIndex
CREATE INDEX "Prediction_type_idx" ON "Prediction"("type");

-- CreateIndex
CREATE INDEX "Prediction_probability_idx" ON "Prediction"("probability");

-- CreateIndex
CREATE INDEX "PhasePrediction_phaseId_idx" ON "PhasePrediction"("phaseId");

-- CreateIndex
CREATE INDEX "AnomalyAlert_type_idx" ON "AnomalyAlert"("type");

-- CreateIndex
CREATE INDEX "AnomalyAlert_severity_idx" ON "AnomalyAlert"("severity");

-- CreateIndex
CREATE INDEX "AnomalyAlert_acknowledged_idx" ON "AnomalyAlert"("acknowledged");

-- CreateIndex
CREATE INDEX "ItemEmbedding_itemId_idx" ON "ItemEmbedding"("itemId");

-- CreateIndex
CREATE INDEX "ItemEmbedding_model_idx" ON "ItemEmbedding"("model");

-- CreateIndex
CREATE UNIQUE INDEX "ItemEmbedding_itemId_model_key" ON "ItemEmbedding"("itemId", "model");

-- CreateIndex
CREATE UNIQUE INDEX "UserAIPreferences_userId_key" ON "UserAIPreferences"("userId");

-- CreateIndex
CREATE INDEX "UserAIPreferences_userId_idx" ON "UserAIPreferences"("userId");

-- CreateIndex
CREATE INDEX "CopilotSession_userId_idx" ON "CopilotSession"("userId");

-- CreateIndex
CREATE INDEX "CopilotSession_status_idx" ON "CopilotSession"("status");

-- CreateIndex
CREATE UNIQUE INDEX "AgentExecution_requestId_key" ON "AgentExecution"("requestId");

-- CreateIndex
CREATE INDEX "AgentExecution_agentName_idx" ON "AgentExecution"("agentName");

-- CreateIndex
CREATE INDEX "AgentExecution_userId_idx" ON "AgentExecution"("userId");

-- CreateIndex
CREATE INDEX "AgentExecution_status_idx" ON "AgentExecution"("status");

-- CreateIndex
CREATE INDEX "AgentExecution_startedAt_idx" ON "AgentExecution"("startedAt");

-- CreateIndex
CREATE INDEX "ActivityLog_userId_idx" ON "ActivityLog"("userId");

-- CreateIndex
CREATE INDEX "ActivityLog_itemId_idx" ON "ActivityLog"("itemId");

-- CreateIndex
CREATE INDEX "ActivityLog_phaseId_idx" ON "ActivityLog"("phaseId");

-- CreateIndex
CREATE INDEX "ActivityLog_timestamp_idx" ON "ActivityLog"("timestamp");

-- CreateIndex
CREATE UNIQUE INDEX "AIGeneratedPlan_workflowId_key" ON "AIGeneratedPlan"("workflowId");

-- CreateIndex
CREATE INDEX "AIGeneratedPlan_workflowId_idx" ON "AIGeneratedPlan"("workflowId");

-- CreateIndex
CREATE INDEX "AIGeneratedPlan_generatedAt_idx" ON "AIGeneratedPlan"("generatedAt");

-- CreateIndex
CREATE UNIQUE INDEX "VectorEmbedding_itemId_key" ON "VectorEmbedding"("itemId");

-- CreateIndex
CREATE INDEX "VectorEmbedding_itemId_idx" ON "VectorEmbedding"("itemId");

-- CreateIndex
CREATE INDEX "AIMetric_agentName_idx" ON "AIMetric"("agentName");

-- CreateIndex
CREATE INDEX "AIMetric_model_idx" ON "AIMetric"("model");

-- CreateIndex
CREATE INDEX "AIMetric_operation_idx" ON "AIMetric"("operation");

-- CreateIndex
CREATE INDEX "AIMetric_timestamp_idx" ON "AIMetric"("timestamp");

-- CreateIndex
CREATE INDEX "AuditLogEntry_timestamp_idx" ON "AuditLogEntry"("timestamp");

-- CreateIndex
CREATE INDEX "AuditLogEntry_actor_idx" ON "AuditLogEntry"("actor");

-- CreateIndex
CREATE INDEX "AuditLogEntry_action_idx" ON "AuditLogEntry"("action");

-- CreateIndex
CREATE INDEX "AuditLogEntry_resource_idx" ON "AuditLogEntry"("resource");

-- CreateIndex
CREATE INDEX "AuditLogEntry_severity_idx" ON "AuditLogEntry"("severity");

-- CreateIndex
CREATE UNIQUE INDEX "RateLimitConfig_userId_key" ON "RateLimitConfig"("userId");

-- CreateIndex
CREATE INDEX "UpgradeRequest_userId_idx" ON "UpgradeRequest"("userId");

-- CreateIndex
CREATE INDEX "FlowState_flowId_idx" ON "FlowState"("flowId");

-- CreateIndex
CREATE INDEX "FlowState_currentState_idx" ON "FlowState"("currentState");

-- CreateIndex
CREATE INDEX "_StepDependencies_B_index" ON "_StepDependencies"("B");

-- AddForeignKey
ALTER TABLE "WorkspaceMember" ADD CONSTRAINT "WorkspaceMember_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkspaceMember" ADD CONSTRAINT "WorkspaceMember_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "Workspace"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Project" ADD CONSTRAINT "Project_ownerWorkspaceId_fkey" FOREIGN KEY ("ownerWorkspaceId") REFERENCES "Workspace"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Project" ADD CONSTRAINT "Project_createdById_fkey" FOREIGN KEY ("createdById") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkspaceProject" ADD CONSTRAINT "WorkspaceProject_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "Workspace"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkspaceProject" ADD CONSTRAINT "WorkspaceProject_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CanvasDocument" ADD CONSTRAINT "CanvasDocument_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CanvasDocument" ADD CONSTRAINT "CanvasDocument_createdById_fkey" FOREIGN KEY ("createdById") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Page" ADD CONSTRAINT "Page_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Page" ADD CONSTRAINT "Page_createdById_fkey" FOREIGN KEY ("createdById") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Workflow" ADD CONSTRAINT "Workflow_templateId_fkey" FOREIGN KEY ("templateId") REFERENCES "WorkflowTemplate"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkflowContributor" ADD CONSTRAINT "WorkflowContributor_workflowId_fkey" FOREIGN KEY ("workflowId") REFERENCES "Workflow"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "WorkflowAudit" ADD CONSTRAINT "WorkflowAudit_workflowId_fkey" FOREIGN KEY ("workflowId") REFERENCES "Workflow"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Milestone" ADD CONSTRAINT "Milestone_phaseId_fkey" FOREIGN KEY ("phaseId") REFERENCES "Phase"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Item" ADD CONSTRAINT "Item_parentId_fkey" FOREIGN KEY ("parentId") REFERENCES "Item"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Item" ADD CONSTRAINT "Item_phaseId_fkey" FOREIGN KEY ("phaseId") REFERENCES "Phase"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemOwner" ADD CONSTRAINT "ItemOwner_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemOwner" ADD CONSTRAINT "ItemOwner_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemTag" ADD CONSTRAINT "ItemTag_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemDependency" ADD CONSTRAINT "ItemDependency_dependentId_fkey" FOREIGN KEY ("dependentId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemDependency" ADD CONSTRAINT "ItemDependency_blockingId_fkey" FOREIGN KEY ("blockingId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemComment" ADD CONSTRAINT "ItemComment_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Artifact" ADD CONSTRAINT "Artifact_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemIntegration" ADD CONSTRAINT "ItemIntegration_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PhaseKPI" ADD CONSTRAINT "PhaseKPI_phaseId_fkey" FOREIGN KEY ("phaseId") REFERENCES "Phase"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AIInsight" ADD CONSTRAINT "AIInsight_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PhasePrediction" ADD CONSTRAINT "PhasePrediction_phaseId_fkey" FOREIGN KEY ("phaseId") REFERENCES "Phase"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ItemEmbedding" ADD CONSTRAINT "ItemEmbedding_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "UserAIPreferences" ADD CONSTRAINT "UserAIPreferences_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AIGeneratedPlan" ADD CONSTRAINT "AIGeneratedPlan_workflowId_fkey" FOREIGN KEY ("workflowId") REFERENCES "Workflow"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VectorEmbedding" ADD CONSTRAINT "VectorEmbedding_itemId_fkey" FOREIGN KEY ("itemId") REFERENCES "Item"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RemediationPlan" ADD CONSTRAINT "RemediationPlan_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "ComplianceAssessment"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RemediationStep" ADD CONSTRAINT "RemediationStep_planId_fkey" FOREIGN KEY ("planId") REFERENCES "RemediationPlan"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ComplianceReport" ADD CONSTRAINT "ComplianceReport_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "ComplianceAssessment"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ReportSchedule" ADD CONSTRAINT "ReportSchedule_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "ComplianceAssessment"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_StepDependencies" ADD CONSTRAINT "_StepDependencies_A_fkey" FOREIGN KEY ("A") REFERENCES "RemediationStep"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_StepDependencies" ADD CONSTRAINT "_StepDependencies_B_fkey" FOREIGN KEY ("B") REFERENCES "RemediationStep"("id") ON DELETE CASCADE ON UPDATE CASCADE;
