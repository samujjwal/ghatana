/*
  Warnings:

  - The values [CS_DISCRETE,PHYSICS,CHEMISTRY,BIOLOGY] on the enum `SimulationDomain` will be removed. If these variants are still used in the database, this will fail.
  - The `attachments` column on the `ForumPost` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The `editHistory` column on the `ForumPost` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The `lessonIds` column on the `StudySession` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - The `attachments` column on the `StudySession` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - Changed the type of `simulationManifestIds` on the `ConceptModuleMapping` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `keyLearningPoints` on the `ContentExample` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `learningObjectives` on the `DomainAuthorConcept` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `prerequisites` on the `DomainAuthorConcept` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `competencies` on the `DomainAuthorConcept` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `keywords` on the `DomainAuthorConcept` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `keywords` on the `DomainConcept` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `audienceTags` on the `DomainConcept` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.
  - Changed the type of `tags` on the `SimulationTemplate` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.

*/
-- CreateEnum
CREATE TYPE "ReviewStatus" AS ENUM ('PENDING', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED');

-- CreateEnum
CREATE TYPE "ReviewPriority" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'URGENT');

-- CreateEnum
CREATE TYPE "StripeAccountStatus" AS ENUM ('PENDING', 'ONBOARDING', 'ENABLED', 'RESTRICTED', 'DISABLED');

-- CreateEnum
CREATE TYPE "AnimationDomain" AS ENUM ('MATH', 'SCIENCE', 'TECH', 'ENGINEERING', 'MEDICINE', 'HEALTH', 'BUSINESS', 'MANAGEMENT', 'ECONOMICS', 'COMPUTER_SCIENCE', 'INTERDISCIPLINARY');

-- CreateEnum
CREATE TYPE "AnimationManifestStatus" AS ENUM ('DRAFT', 'REVIEW', 'ACTIVE', 'DEPRECATED');

-- CreateEnum
CREATE TYPE "RemediationStatus" AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'DISMISSED');

-- CreateEnum
CREATE TYPE "PayoutNotificationStatus" AS ENUM ('PENDING', 'SENT', 'DELIVERED', 'FAILED');

-- CreateEnum
CREATE TYPE "SyncOperationType" AS ENUM ('CREATE', 'UPDATE', 'DELETE');

-- CreateEnum
CREATE TYPE "SyncOperationStatus" AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CONFLICT');

-- CreateEnum
CREATE TYPE "ConflictResolutionStrategy" AS ENUM ('SERVER_WINS', 'CLIENT_WINS', 'MANUAL', 'MERGE');

-- CreateEnum
CREATE TYPE "AgeGroup" AS ENUM ('UNDER_13', 'AGE_13_17', 'AGE_18_PLUS', 'ADULT_ONLY');

-- CreateEnum
CREATE TYPE "ContentModerationStatus" AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED', 'AUTO_REJECTED');

-- CreateEnum
CREATE TYPE "ModerationSeverity" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "ModerationAction" AS ENUM ('ALLOW', 'BLOCK', 'FLAG_FOR_REVIEW', 'AUTO_REJECT', 'REQUIRE_PARENTAL_CONSENT');

-- AlterEnum
ALTER TYPE "AssessmentAttemptStatus" ADD VALUE 'PENDING_HUMAN_REVIEW';

-- AlterEnum
ALTER TYPE "GenerationRequestStatus" ADD VALUE 'FAILED_PLANNING';

-- AlterEnum
-- This migration adds more than one value to an enum.
-- With PostgreSQL versions 11 and earlier, this is not possible
-- in a single migration. This can be worked around by creating
-- multiple migrations, each migration adding only one value to
-- the enum.


ALTER TYPE "ModuleDomain" ADD VALUE 'ENGINEERING';
ALTER TYPE "ModuleDomain" ADD VALUE 'MEDICINE';
ALTER TYPE "ModuleDomain" ADD VALUE 'HEALTH';
ALTER TYPE "ModuleDomain" ADD VALUE 'BUSINESS';
ALTER TYPE "ModuleDomain" ADD VALUE 'MANAGEMENT';
ALTER TYPE "ModuleDomain" ADD VALUE 'ECONOMICS';
ALTER TYPE "ModuleDomain" ADD VALUE 'COMPUTER_SCIENCE';
ALTER TYPE "ModuleDomain" ADD VALUE 'INTERDISCIPLINARY';

-- AlterEnum
BEGIN;
CREATE TYPE "SimulationDomain_new" AS ENUM ('MATHEMATICS', 'SCIENCE', 'TECH', 'ENGINEERING', 'MEDICINE', 'HEALTH', 'BUSINESS', 'MANAGEMENT', 'ECONOMICS', 'COMPUTER_SCIENCE', 'INTERDISCIPLINARY');
ALTER TABLE "DomainConcept" ALTER COLUMN "domain" TYPE "SimulationDomain_new" USING ("domain"::text::"SimulationDomain_new");
ALTER TABLE "SimulationManifest" ALTER COLUMN "domain" TYPE "SimulationDomain_new" USING ("domain"::text::"SimulationDomain_new");
ALTER TABLE "SimulationTemplate" ALTER COLUMN "domain" TYPE "SimulationDomain_new" USING ("domain"::text::"SimulationDomain_new");
ALTER TABLE "Curriculum" ALTER COLUMN "domain" TYPE "SimulationDomain_new" USING ("domain"::text::"SimulationDomain_new");
ALTER TABLE "DomainAuthor" ALTER COLUMN "domain" TYPE "SimulationDomain_new" USING ("domain"::text::"SimulationDomain_new");
ALTER TYPE "SimulationDomain" RENAME TO "SimulationDomain_old";
ALTER TYPE "SimulationDomain_new" RENAME TO "SimulationDomain";
DROP TYPE "public"."SimulationDomain_old";
COMMIT;

-- AlterTable
ALTER TABLE "ArtifactManifest" ADD COLUMN     "animationManifestId" TEXT,
ADD COLUMN     "simulationManifestId" TEXT;

-- AlterTable
ALTER TABLE "AssessmentAttempt" ADD COLUMN     "averageConfidence" DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN     "confidenceBreakdown" JSONB;

-- AlterTable
ALTER TABLE "CheckoutSession" ADD COLUMN     "stripeSessionId" TEXT;

-- AlterTable
ALTER TABLE "ClaimAnimation" ADD COLUMN     "evidenceRefs" TEXT[];

-- AlterTable
ALTER TABLE "ClaimExample" ADD COLUMN     "evidenceRefs" TEXT[];

-- AlterTable
ALTER TABLE "ClaimSimulation" ADD COLUMN     "evidenceRefs" TEXT[];

-- AlterTable
ALTER TABLE "Classroom" ADD COLUMN     "deletedAt" TIMESTAMP(3);

-- AlterTable
ALTER TABLE "ConceptModuleMapping" DROP COLUMN "simulationManifestIds",
ADD COLUMN     "simulationManifestIds" JSONB NOT NULL;

-- AlterTable
ALTER TABLE "ContentExample" DROP COLUMN "keyLearningPoints",
ADD COLUMN     "keyLearningPoints" JSONB NOT NULL;

-- AlterTable
ALTER TABLE "DomainAuthorConcept" DROP COLUMN "learningObjectives",
ADD COLUMN     "learningObjectives" JSONB NOT NULL,
DROP COLUMN "prerequisites",
ADD COLUMN     "prerequisites" JSONB NOT NULL,
DROP COLUMN "competencies",
ADD COLUMN     "competencies" JSONB NOT NULL,
DROP COLUMN "keywords",
ADD COLUMN     "keywords" JSONB NOT NULL;

-- AlterTable
ALTER TABLE "DomainConcept" DROP COLUMN "keywords",
ADD COLUMN     "keywords" JSONB NOT NULL,
DROP COLUMN "audienceTags",
ADD COLUMN     "audienceTags" JSONB NOT NULL;

-- AlterTable
ALTER TABLE "Enrollment" ADD COLUMN     "deletedAt" TIMESTAMP(3);

-- AlterTable
ALTER TABLE "ForumPost" DROP COLUMN "attachments",
ADD COLUMN     "attachments" JSONB,
DROP COLUMN "editHistory",
ADD COLUMN     "editHistory" JSONB;

-- AlterTable
ALTER TABLE "KernelPlugin" ADD COLUMN     "algorithm" TEXT DEFAULT 'ed25519',
ADD COLUMN     "codeHash" TEXT,
ADD COLUMN     "publicKey" TEXT,
ADD COLUMN     "publishedAt" TIMESTAMP(3),
ADD COLUMN     "signature" TEXT,
ADD COLUMN     "signedAt" TIMESTAMP(3),
ADD COLUMN     "signerKeyId" TEXT;

-- AlterTable
ALTER TABLE "LearningEvent" ADD COLUMN     "schemaVersion" TEXT NOT NULL DEFAULT '1.0.0';

-- AlterTable
ALTER TABLE "LearningExperience" ADD COLUMN     "animationManifestId" TEXT,
ADD COLUMN     "animationVersion" TEXT;

-- AlterTable
ALTER TABLE "LearningPath" ADD COLUMN     "deletedAt" TIMESTAMP(3);

-- AlterTable
ALTER TABLE "Module" ADD COLUMN     "deletedAt" TIMESTAMP(3);

-- AlterTable
ALTER TABLE "SimulationTemplate" DROP COLUMN "tags",
ADD COLUMN     "tags" JSONB NOT NULL;

-- AlterTable
ALTER TABLE "StudySession" DROP COLUMN "lessonIds",
ADD COLUMN     "lessonIds" JSONB,
DROP COLUMN "attachments",
ADD COLUMN     "attachments" JSONB;

-- AlterTable
ALTER TABLE "User" ADD COLUMN     "emailEncrypted" TEXT,
ADD COLUMN     "lastLoginAt" TIMESTAMP(3),
ADD COLUMN     "passwordHash" TEXT;

-- CreateTable
CREATE TABLE "Payout" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "stripeAccountId" TEXT NOT NULL,
    "stripePayoutId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "currency" TEXT NOT NULL DEFAULT 'usd',
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "arrivalDate" TIMESTAMP(3),
    "description" TEXT,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Payout_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UserConsent" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "granted" BOOLEAN NOT NULL DEFAULT true,
    "grantedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "revokedAt" TIMESTAMP(3),
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "UserConsent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Role" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Role_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Permission" (
    "id" TEXT NOT NULL,
    "resource" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Permission_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UserRole" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "roleId" TEXT NOT NULL,
    "assignedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "UserRole_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RolePermission" (
    "id" TEXT NOT NULL,
    "roleId" TEXT NOT NULL,
    "permissionId" TEXT NOT NULL,
    "assignedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RolePermission_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Capability" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "description" TEXT,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "requiresSubscription" BOOLEAN NOT NULL DEFAULT false,
    "requiresWorkerHealthy" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Capability_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TenantCapability" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "capabilityId" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "config" TEXT,
    "enabledAt" TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    "disabledAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TenantCapability_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ABACPolicy" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "effect" TEXT NOT NULL DEFAULT 'allow',
    "priority" INTEGER NOT NULL DEFAULT 0,
    "conditions" JSONB NOT NULL,
    "actions" TEXT[],
    "resourceTypes" TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ABACPolicy_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ABACAttribute" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "entityType" TEXT NOT NULL,
    "entityId" TEXT NOT NULL,
    "attributeName" TEXT NOT NULL,
    "attributeValue" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ABACAttribute_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AIAuditLog" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "modelId" TEXT NOT NULL,
    "modelName" TEXT,
    "modelVersion" TEXT,
    "endpoint" TEXT NOT NULL,
    "requestPayload" TEXT,
    "responsePayload" TEXT,
    "policyDecision" TEXT,
    "latencyMs" INTEGER,
    "success" BOOLEAN NOT NULL DEFAULT true,
    "errorMessage" TEXT,
    "ipAddress" TEXT,
    "userAgent" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AIAuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentEvaluation" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "contentId" TEXT,
    "claimId" TEXT NOT NULL,
    "text" TEXT NOT NULL,
    "isCorrect" BOOLEAN NOT NULL,
    "confidence" DOUBLE PRECISION NOT NULL,
    "matchedSourceId" TEXT,
    "matchedSourceType" TEXT,
    "issues" TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ContentEvaluation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GoldenDataset" (
    "id" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "inputType" TEXT NOT NULL,
    "input" TEXT NOT NULL,
    "expectedOutput" TEXT NOT NULL,
    "qualityMetrics" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "GoldenDataset_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RegressionTestResult" (
    "id" TEXT NOT NULL,
    "entryId" TEXT NOT NULL,
    "goldenDatasetId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "input" TEXT NOT NULL,
    "actualOutput" TEXT NOT NULL,
    "expectedOutput" TEXT NOT NULL,
    "passed" BOOLEAN NOT NULL,
    "qualityDiff" JSONB NOT NULL,
    "regressionDetected" BOOLEAN NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RegressionTestResult_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "QualityBaseline" (
    "id" TEXT NOT NULL,
    "contentId" TEXT NOT NULL,
    "contentType" TEXT NOT NULL,
    "baselineMetrics" JSONB NOT NULL,
    "establishedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "QualityBaseline_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "QualityAlert" (
    "id" TEXT NOT NULL,
    "contentId" TEXT NOT NULL,
    "contentType" TEXT NOT NULL,
    "metricType" TEXT NOT NULL,
    "baselineValue" DOUBLE PRECISION NOT NULL,
    "currentValue" DOUBLE PRECISION NOT NULL,
    "degradation" DOUBLE PRECISION NOT NULL,
    "severity" TEXT NOT NULL,
    "detectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolved" BOOLEAN NOT NULL DEFAULT false,
    "resolvedAt" TIMESTAMP(3),

    CONSTRAINT "QualityAlert_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentGenerationBenchmark" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "metrics" JSONB NOT NULL,
    "baseline" JSONB,
    "regressionDetected" BOOLEAN NOT NULL,
    "regressionDetails" TEXT[],
    "isBaseline" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ContentGenerationBenchmark_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ComplianceEvidence" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "reportType" TEXT NOT NULL,
    "evidence" TEXT NOT NULL,
    "periodStart" TIMESTAMP(3) NOT NULL,
    "periodEnd" TIMESTAMP(3) NOT NULL,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ComplianceEvidence_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TenantPlugin" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "pluginId" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "configuration" TEXT NOT NULL,
    "installedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "activatedAt" TIMESTAMP(3),
    "lastMigratedAt" TIMESTAMP(3),

    CONSTRAINT "TenantPlugin_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentReport" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "entityType" TEXT NOT NULL,
    "entityId" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "details" TEXT,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "reportedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),
    "resolvedBy" TEXT,
    "resolutionDetails" TEXT,

    CONSTRAINT "ContentReport_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Follow" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "followerId" TEXT NOT NULL,
    "followingId" TEXT NOT NULL,
    "followedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Follow_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FactualValidation" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "factText" TEXT NOT NULL,
    "factSource" TEXT,
    "isValid" BOOLEAN NOT NULL,
    "confidence" DOUBLE PRECISION NOT NULL,
    "errorMessage" TEXT,
    "validatedBy" TEXT NOT NULL,
    "validatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "metadata" JSONB,

    CONSTRAINT "FactualValidation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnimationManifest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "domain" "AnimationDomain" NOT NULL,
    "version" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "moduleId" TEXT,
    "manifest" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AnimationManifest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnimationManifestVersion" (
    "id" TEXT NOT NULL,
    "manifestId" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "manifestJson" JSONB NOT NULL,
    "styleConstraints" JSONB NOT NULL,
    "accessibilityTags" JSONB NOT NULL,
    "renderingPlan" JSONB NOT NULL,
    "status" "AnimationManifestStatus" NOT NULL DEFAULT 'DRAFT',
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "confidenceScore" DOUBLE PRECISION,
    "createdBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AnimationManifestVersion_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnimationManifestExtension" (
    "id" TEXT NOT NULL,
    "metadata" JSONB,

    CONSTRAINT "AnimationManifestExtension_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnimationLinkAudit" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "animationManifestId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "beforeVersion" TEXT,
    "afterVersion" TEXT,
    "actorId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AnimationLinkAudit_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "MarketplaceReview" (
    "id" TEXT NOT NULL,
    "kernelId" TEXT NOT NULL,
    "submitterId" TEXT NOT NULL,
    "reviewerId" TEXT,
    "status" "ReviewStatus" NOT NULL DEFAULT 'PENDING',
    "priority" "ReviewPriority" NOT NULL DEFAULT 'MEDIUM',
    "submittedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "assignedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "criteria" TEXT,
    "comments" TEXT,
    "requestedChanges" TEXT,

    CONSTRAINT "MarketplaceReview_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StripeAccount" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "accountId" TEXT NOT NULL,
    "status" "StripeAccountStatus" NOT NULL DEFAULT 'PENDING',
    "country" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "chargesEnabled" BOOLEAN NOT NULL DEFAULT false,
    "payoutsEnabled" BOOLEAN NOT NULL DEFAULT false,
    "platformFeePercent" DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    "onboardingCompletedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "StripeAccount_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "EvidenceEmbedding" (
    "id" TEXT NOT NULL,
    "evidenceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "text" TEXT NOT NULL,
    "vector" JSONB NOT NULL,
    "model" TEXT NOT NULL,
    "dimensions" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "EvidenceEmbedding_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ProvenanceNode" (
    "id" TEXT NOT NULL,
    "generationRequestId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "kind" TEXT NOT NULL,
    "assertionText" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "modelVersion" TEXT NOT NULL,
    "promptHash" TEXT NOT NULL,
    "sourceDocumentRef" TEXT,
    "context" JSONB,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "schemaVersion" TEXT NOT NULL DEFAULT '1.0',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ProvenanceNode_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GenerationReplayManifest" (
    "id" TEXT NOT NULL,
    "generationJobId" TEXT NOT NULL,
    "seed" TEXT NOT NULL,
    "seedSource" TEXT NOT NULL,
    "jobType" TEXT NOT NULL,
    "inputParams" JSONB NOT NULL,
    "outputData" JSONB,
    "replayable" BOOLEAN NOT NULL DEFAULT true,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "GenerationReplayManifest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RemediationQueue" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "artifactId" TEXT NOT NULL,
    "contentType" TEXT NOT NULL,
    "trustScore" DOUBLE PRECISION NOT NULL,
    "publishDecision" TEXT NOT NULL,
    "triggerReason" TEXT NOT NULL,
    "remediationNotes" TEXT,
    "status" "RemediationStatus" NOT NULL DEFAULT 'PENDING',
    "assignedTo" TEXT,
    "startedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "attemptCount" INTEGER NOT NULL DEFAULT 0,
    "lastAttemptAt" TIMESTAMP(3),
    "nextAttemptAt" TIMESTAMP(3),
    "resultArtifactId" TEXT,
    "resultValidationId" TEXT,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RemediationQueue_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TaxTransaction" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "stripeTaxCalculationId" TEXT NOT NULL,
    "stripeTaxTransactionId" TEXT,
    "stripePaymentIntentId" TEXT,
    "amountCents" INTEGER NOT NULL,
    "taxAmountCents" INTEGER NOT NULL,
    "currency" TEXT NOT NULL DEFAULT 'usd',
    "country" TEXT NOT NULL,
    "state" TEXT,
    "postalCode" TEXT,
    "taxBreakdown" JSONB,
    "reference" TEXT,
    "reportedAt" TIMESTAMP(3),
    "reportId" TEXT,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TaxTransaction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PayoutNotification" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "payoutId" TEXT NOT NULL,
    "stripePayoutId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "notificationType" TEXT NOT NULL,
    "status" "PayoutNotificationStatus" NOT NULL DEFAULT 'PENDING',
    "emailSent" BOOLEAN NOT NULL DEFAULT false,
    "emailSentAt" TIMESTAMP(3),
    "pushSent" BOOLEAN NOT NULL DEFAULT false,
    "pushSentAt" TIMESTAMP(3),
    "subject" TEXT,
    "body" TEXT,
    "failureReason" TEXT,
    "actionRequired" BOOLEAN NOT NULL DEFAULT false,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "PayoutNotification_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "OfflineSyncQueue" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "operationType" "SyncOperationType" NOT NULL,
    "operationStatus" "SyncOperationStatus" NOT NULL DEFAULT 'PENDING',
    "resourceType" TEXT NOT NULL,
    "resourceId" TEXT NOT NULL,
    "resourceVersion" INTEGER NOT NULL DEFAULT 1,
    "payload" JSONB NOT NULL,
    "serverVersion" JSONB,
    "clientVersion" JSONB,
    "conflictDetected" BOOLEAN NOT NULL DEFAULT false,
    "conflictStrategy" "ConflictResolutionStrategy",
    "conflictReason" TEXT,
    "resolvedAt" TIMESTAMP(3),
    "resolvedBy" TEXT,
    "attemptCount" INTEGER NOT NULL DEFAULT 0,
    "lastAttemptAt" TIMESTAMP(3),
    "nextAttemptAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "completedAt" TIMESTAMP(3),
    "errorMessage" TEXT,

    CONSTRAINT "OfflineSyncQueue_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SyncConflictLog" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "syncQueueId" TEXT NOT NULL,
    "resourceType" TEXT NOT NULL,
    "resourceId" TEXT NOT NULL,
    "conflictType" TEXT NOT NULL,
    "conflictReason" TEXT NOT NULL,
    "clientPayload" JSONB NOT NULL,
    "serverPayload" JSONB NOT NULL,
    "mergedPayload" JSONB,
    "resolutionStrategy" "ConflictResolutionStrategy" NOT NULL,
    "resolvedBy" TEXT NOT NULL,
    "resolvedAt" TIMESTAMP(3) NOT NULL,
    "resolutionNotes" TEXT,
    "dataLossRisk" BOOLEAN NOT NULL DEFAULT false,
    "userNotified" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SyncConflictLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UserAgeVerification" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "birthDate" TIMESTAMP(3) NOT NULL,
    "ageGroup" "AgeGroup" NOT NULL,
    "verifiedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "verifiedMethod" TEXT NOT NULL,
    "verifiedBy" TEXT,
    "parentalConsentGiven" BOOLEAN NOT NULL DEFAULT false,
    "parentalConsentAt" TIMESTAMP(3),
    "parentalConsentExpiresAt" TIMESTAMP(3),
    "parentId" TEXT,
    "restrictedFeatures" JSONB,
    "requiresSupervision" BOOLEAN NOT NULL DEFAULT false,
    "dataRetentionLimited" BOOLEAN NOT NULL DEFAULT false,
    "marketingOptOut" BOOLEAN NOT NULL DEFAULT true,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "UserAgeVerification_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentModerationQueue" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT,
    "contentType" TEXT NOT NULL,
    "contentId" TEXT NOT NULL,
    "contentText" TEXT NOT NULL,
    "contentMetadata" JSONB,
    "status" "ContentModerationStatus" NOT NULL DEFAULT 'PENDING',
    "severity" "ModerationSeverity" NOT NULL,
    "action" "ModerationAction" NOT NULL,
    "confidenceScore" DOUBLE PRECISION,
    "flaggedReasons" TEXT[],
    "detectedPatterns" JSONB,
    "reviewedBy" TEXT,
    "reviewedAt" TIMESTAMP(3),
    "reviewNotes" TEXT,
    "overrideReason" TEXT,
    "appealedBy" TEXT,
    "appealedAt" TIMESTAMP(3),
    "appealReason" TEXT,
    "appealStatus" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ContentModerationQueue_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ChildSafetyPolicy" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "ageGroup" "AgeGroup" NOT NULL,
    "appliesTo" TEXT[],
    "requireParentalConsent" BOOLEAN NOT NULL DEFAULT false,
    "requireAdultSupervision" BOOLEAN NOT NULL DEFAULT false,
    "limitCommunicationHours" BOOLEAN NOT NULL DEFAULT false,
    "allowedHoursStart" INTEGER,
    "allowedHoursEnd" INTEGER,
    "blockProfanity" BOOLEAN NOT NULL DEFAULT true,
    "blockPersonalInfoSharing" BOOLEAN NOT NULL DEFAULT true,
    "blockExternalLinks" BOOLEAN NOT NULL DEFAULT true,
    "blockMediaSharing" BOOLEAN NOT NULL DEFAULT false,
    "autoModerateChat" BOOLEAN NOT NULL DEFAULT true,
    "autoModerateForums" BOOLEAN NOT NULL DEFAULT true,
    "requireModeratorApproval" BOOLEAN NOT NULL DEFAULT false,
    "enableActivityLogging" BOOLEAN NOT NULL DEFAULT true,
    "alertOnViolation" BOOLEAN NOT NULL DEFAULT true,
    "violationAlertRecipients" TEXT[],
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "effectiveAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ChildSafetyPolicy_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SafetyViolationLog" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "policyId" TEXT,
    "violationType" TEXT NOT NULL,
    "severity" "ModerationSeverity" NOT NULL,
    "contentType" TEXT NOT NULL,
    "contentId" TEXT NOT NULL,
    "contentText" TEXT NOT NULL,
    "contextMetadata" JSONB,
    "actionTaken" TEXT NOT NULL,
    "actionTakenBy" TEXT,
    "actionTakenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "parentNotified" BOOLEAN NOT NULL DEFAULT false,
    "parentNotifiedAt" TIMESTAMP(3),
    "parentId" TEXT,
    "resolved" BOOLEAN NOT NULL DEFAULT false,
    "resolvedAt" TIMESTAMP(3),
    "resolvedBy" TEXT,
    "resolutionNotes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SafetyViolationLog_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Payout_stripePayoutId_key" ON "Payout"("stripePayoutId");

-- CreateIndex
CREATE INDEX "Payout_tenantId_stripeAccountId_idx" ON "Payout"("tenantId", "stripeAccountId");

-- CreateIndex
CREATE INDEX "Payout_tenantId_status_idx" ON "Payout"("tenantId", "status");

-- CreateIndex
CREATE INDEX "Payout_stripePayoutId_idx" ON "Payout"("stripePayoutId");

-- CreateIndex
CREATE INDEX "UserConsent_tenantId_userId_idx" ON "UserConsent"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "UserConsent_tenantId_userId_granted_idx" ON "UserConsent"("tenantId", "userId", "granted");

-- CreateIndex
CREATE UNIQUE INDEX "UserConsent_tenantId_userId_category_key" ON "UserConsent"("tenantId", "userId", "category");

-- CreateIndex
CREATE INDEX "Role_tenantId_idx" ON "Role"("tenantId");

-- CreateIndex
CREATE UNIQUE INDEX "Role_tenantId_name_key" ON "Role"("tenantId", "name");

-- CreateIndex
CREATE INDEX "Permission_resource_idx" ON "Permission"("resource");

-- CreateIndex
CREATE UNIQUE INDEX "Permission_resource_action_key" ON "Permission"("resource", "action");

-- CreateIndex
CREATE INDEX "UserRole_tenantId_userId_idx" ON "UserRole"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "UserRole_tenantId_roleId_idx" ON "UserRole"("tenantId", "roleId");

-- CreateIndex
CREATE UNIQUE INDEX "UserRole_tenantId_userId_roleId_key" ON "UserRole"("tenantId", "userId", "roleId");

-- CreateIndex
CREATE INDEX "RolePermission_roleId_idx" ON "RolePermission"("roleId");

-- CreateIndex
CREATE INDEX "RolePermission_permissionId_idx" ON "RolePermission"("permissionId");

-- CreateIndex
CREATE UNIQUE INDEX "RolePermission_roleId_permissionId_key" ON "RolePermission"("roleId", "permissionId");

-- CreateIndex
CREATE INDEX "Capability_category_idx" ON "Capability"("category");

-- CreateIndex
CREATE INDEX "Capability_enabled_idx" ON "Capability"("enabled");

-- CreateIndex
CREATE UNIQUE INDEX "Capability_name_key" ON "Capability"("name");

-- CreateIndex
CREATE INDEX "TenantCapability_tenantId_capabilityId_idx" ON "TenantCapability"("tenantId", "capabilityId");

-- CreateIndex
CREATE INDEX "TenantCapability_capabilityId_idx" ON "TenantCapability"("capabilityId");

-- CreateIndex
CREATE INDEX "ABACPolicy_tenantId_enabled_idx" ON "ABACPolicy"("tenantId", "enabled");

-- CreateIndex
CREATE INDEX "ABACPolicy_priority_idx" ON "ABACPolicy"("priority");

-- CreateIndex
CREATE UNIQUE INDEX "ABACPolicy_tenantId_name_key" ON "ABACPolicy"("tenantId", "name");

-- CreateIndex
CREATE INDEX "ABACAttribute_tenantId_entityType_idx" ON "ABACAttribute"("tenantId", "entityType");

-- CreateIndex
CREATE INDEX "ABACAttribute_entityType_entityId_idx" ON "ABACAttribute"("entityType", "entityId");

-- CreateIndex
CREATE UNIQUE INDEX "ABACAttribute_tenantId_entityType_entityId_attributeName_key" ON "ABACAttribute"("tenantId", "entityType", "entityId", "attributeName");

-- CreateIndex
CREATE INDEX "AIAuditLog_tenantId_createdAt_idx" ON "AIAuditLog"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "AIAuditLog_tenantId_userId_createdAt_idx" ON "AIAuditLog"("tenantId", "userId", "createdAt");

-- CreateIndex
CREATE INDEX "AIAuditLog_userId_createdAt_idx" ON "AIAuditLog"("userId", "createdAt");

-- CreateIndex
CREATE INDEX "AIAuditLog_modelId_createdAt_idx" ON "AIAuditLog"("modelId", "createdAt");

-- CreateIndex
CREATE INDEX "AIAuditLog_endpoint_createdAt_idx" ON "AIAuditLog"("endpoint", "createdAt");

-- CreateIndex
CREATE INDEX "AIAuditLog_createdAt_idx" ON "AIAuditLog"("createdAt");

-- CreateIndex
CREATE INDEX "ContentEvaluation_tenantId_moduleId_idx" ON "ContentEvaluation"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "ContentEvaluation_contentId_idx" ON "ContentEvaluation"("contentId");

-- CreateIndex
CREATE INDEX "ContentEvaluation_isCorrect_idx" ON "ContentEvaluation"("isCorrect");

-- CreateIndex
CREATE INDEX "GoldenDataset_moduleId_idx" ON "GoldenDataset"("moduleId");

-- CreateIndex
CREATE INDEX "GoldenDataset_inputType_idx" ON "GoldenDataset"("inputType");

-- CreateIndex
CREATE UNIQUE INDEX "GoldenDataset_moduleId_inputType_input_key" ON "GoldenDataset"("moduleId", "inputType", "input");

-- CreateIndex
CREATE INDEX "RegressionTestResult_goldenDatasetId_idx" ON "RegressionTestResult"("goldenDatasetId");

-- CreateIndex
CREATE INDEX "RegressionTestResult_moduleId_idx" ON "RegressionTestResult"("moduleId");

-- CreateIndex
CREATE INDEX "RegressionTestResult_passed_idx" ON "RegressionTestResult"("passed");

-- CreateIndex
CREATE INDEX "RegressionTestResult_regressionDetected_idx" ON "RegressionTestResult"("regressionDetected");

-- CreateIndex
CREATE INDEX "RegressionTestResult_timestamp_idx" ON "RegressionTestResult"("timestamp");

-- CreateIndex
CREATE UNIQUE INDEX "QualityBaseline_contentId_key" ON "QualityBaseline"("contentId");

-- CreateIndex
CREATE INDEX "QualityBaseline_contentType_idx" ON "QualityBaseline"("contentType");

-- CreateIndex
CREATE INDEX "QualityAlert_contentId_idx" ON "QualityAlert"("contentId");

-- CreateIndex
CREATE INDEX "QualityAlert_contentType_idx" ON "QualityAlert"("contentType");

-- CreateIndex
CREATE INDEX "QualityAlert_severity_idx" ON "QualityAlert"("severity");

-- CreateIndex
CREATE INDEX "QualityAlert_resolved_idx" ON "QualityAlert"("resolved");

-- CreateIndex
CREATE INDEX "QualityAlert_detectedAt_idx" ON "QualityAlert"("detectedAt");

-- CreateIndex
CREATE INDEX "ContentGenerationBenchmark_tenantId_idx" ON "ContentGenerationBenchmark"("tenantId");

-- CreateIndex
CREATE INDEX "ContentGenerationBenchmark_isBaseline_idx" ON "ContentGenerationBenchmark"("isBaseline");

-- CreateIndex
CREATE INDEX "ContentGenerationBenchmark_regressionDetected_idx" ON "ContentGenerationBenchmark"("regressionDetected");

-- CreateIndex
CREATE INDEX "ContentGenerationBenchmark_createdAt_idx" ON "ContentGenerationBenchmark"("createdAt");

-- CreateIndex
CREATE INDEX "ComplianceEvidence_tenantId_idx" ON "ComplianceEvidence"("tenantId");

-- CreateIndex
CREATE INDEX "ComplianceEvidence_reportType_idx" ON "ComplianceEvidence"("reportType");

-- CreateIndex
CREATE INDEX "ComplianceEvidence_generatedAt_idx" ON "ComplianceEvidence"("generatedAt");

-- CreateIndex
CREATE INDEX "TenantPlugin_tenantId_idx" ON "TenantPlugin"("tenantId");

-- CreateIndex
CREATE INDEX "TenantPlugin_pluginId_idx" ON "TenantPlugin"("pluginId");

-- CreateIndex
CREATE INDEX "TenantPlugin_status_idx" ON "TenantPlugin"("status");

-- CreateIndex
CREATE UNIQUE INDEX "TenantPlugin_tenantId_pluginId_key" ON "TenantPlugin"("tenantId", "pluginId");

-- CreateIndex
CREATE INDEX "ContentReport_tenantId_status_idx" ON "ContentReport"("tenantId", "status");

-- CreateIndex
CREATE INDEX "ContentReport_tenantId_userId_idx" ON "ContentReport"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "ContentReport_entityType_entityId_idx" ON "ContentReport"("entityType", "entityId");

-- CreateIndex
CREATE INDEX "Follow_tenantId_followerId_idx" ON "Follow"("tenantId", "followerId");

-- CreateIndex
CREATE INDEX "Follow_tenantId_followingId_idx" ON "Follow"("tenantId", "followingId");

-- CreateIndex
CREATE UNIQUE INDEX "Follow_followerId_followingId_key" ON "Follow"("followerId", "followingId");

-- CreateIndex
CREATE INDEX "FactualValidation_experienceId_idx" ON "FactualValidation"("experienceId");

-- CreateIndex
CREATE INDEX "FactualValidation_claimRef_idx" ON "FactualValidation"("claimRef");

-- CreateIndex
CREATE INDEX "FactualValidation_tenantId_idx" ON "FactualValidation"("tenantId");

-- CreateIndex
CREATE UNIQUE INDEX "FactualValidation_experienceId_claimRef_factText_key" ON "FactualValidation"("experienceId", "claimRef", "factText");

-- CreateIndex
CREATE INDEX "AnimationManifest_tenantId_domain_idx" ON "AnimationManifest"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "AnimationManifest_tenantId_moduleId_idx" ON "AnimationManifest"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "AnimationManifestVersion_manifestId_status_idx" ON "AnimationManifestVersion"("manifestId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "AnimationManifestVersion_manifestId_version_key" ON "AnimationManifestVersion"("manifestId", "version");

-- CreateIndex
CREATE INDEX "AnimationLinkAudit_experienceId_createdAt_idx" ON "AnimationLinkAudit"("experienceId", "createdAt");

-- CreateIndex
CREATE INDEX "AnimationLinkAudit_animationManifestId_createdAt_idx" ON "AnimationLinkAudit"("animationManifestId", "createdAt");

-- CreateIndex
CREATE INDEX "MarketplaceReview_kernelId_idx" ON "MarketplaceReview"("kernelId");

-- CreateIndex
CREATE INDEX "MarketplaceReview_reviewerId_idx" ON "MarketplaceReview"("reviewerId");

-- CreateIndex
CREATE INDEX "MarketplaceReview_status_idx" ON "MarketplaceReview"("status");

-- CreateIndex
CREATE INDEX "MarketplaceReview_priority_idx" ON "MarketplaceReview"("priority");

-- CreateIndex
CREATE UNIQUE INDEX "StripeAccount_userId_key" ON "StripeAccount"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "StripeAccount_accountId_key" ON "StripeAccount"("accountId");

-- CreateIndex
CREATE INDEX "StripeAccount_accountId_idx" ON "StripeAccount"("accountId");

-- CreateIndex
CREATE INDEX "StripeAccount_status_idx" ON "StripeAccount"("status");

-- CreateIndex
CREATE UNIQUE INDEX "EvidenceEmbedding_evidenceId_key" ON "EvidenceEmbedding"("evidenceId");

-- CreateIndex
CREATE INDEX "EvidenceEmbedding_claimRef_idx" ON "EvidenceEmbedding"("claimRef");

-- CreateIndex
CREATE INDEX "EvidenceEmbedding_evidenceId_idx" ON "EvidenceEmbedding"("evidenceId");

-- CreateIndex
CREATE INDEX "ProvenanceNode_generationRequestId_tenantId_idx" ON "ProvenanceNode"("generationRequestId", "tenantId");

-- CreateIndex
CREATE INDEX "ProvenanceNode_tenantId_claimRef_idx" ON "ProvenanceNode"("tenantId", "claimRef");

-- CreateIndex
CREATE INDEX "ProvenanceNode_tenantId_kind_idx" ON "ProvenanceNode"("tenantId", "kind");

-- CreateIndex
CREATE INDEX "GenerationReplayManifest_generationJobId_idx" ON "GenerationReplayManifest"("generationJobId");

-- CreateIndex
CREATE INDEX "GenerationReplayManifest_seedSource_idx" ON "GenerationReplayManifest"("seedSource");

-- CreateIndex
CREATE INDEX "GenerationReplayManifest_generatedAt_idx" ON "GenerationReplayManifest"("generatedAt");

-- CreateIndex
CREATE INDEX "RemediationQueue_tenantId_status_idx" ON "RemediationQueue"("tenantId", "status");

-- CreateIndex
CREATE INDEX "RemediationQueue_tenantId_experienceId_idx" ON "RemediationQueue"("tenantId", "experienceId");

-- CreateIndex
CREATE INDEX "RemediationQueue_tenantId_artifactId_idx" ON "RemediationQueue"("tenantId", "artifactId");

-- CreateIndex
CREATE INDEX "RemediationQueue_status_nextAttemptAt_idx" ON "RemediationQueue"("status", "nextAttemptAt");

-- CreateIndex
CREATE INDEX "RemediationQueue_createdAt_idx" ON "RemediationQueue"("createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "TaxTransaction_stripeTaxTransactionId_key" ON "TaxTransaction"("stripeTaxTransactionId");

-- CreateIndex
CREATE INDEX "TaxTransaction_tenantId_createdAt_idx" ON "TaxTransaction"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "TaxTransaction_tenantId_country_state_idx" ON "TaxTransaction"("tenantId", "country", "state");

-- CreateIndex
CREATE INDEX "TaxTransaction_tenantId_reportId_idx" ON "TaxTransaction"("tenantId", "reportId");

-- CreateIndex
CREATE INDEX "TaxTransaction_stripeTaxTransactionId_idx" ON "TaxTransaction"("stripeTaxTransactionId");

-- CreateIndex
CREATE INDEX "TaxTransaction_createdAt_idx" ON "TaxTransaction"("createdAt");

-- CreateIndex
CREATE INDEX "PayoutNotification_tenantId_userId_idx" ON "PayoutNotification"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "PayoutNotification_tenantId_status_idx" ON "PayoutNotification"("tenantId", "status");

-- CreateIndex
CREATE INDEX "PayoutNotification_stripePayoutId_idx" ON "PayoutNotification"("stripePayoutId");

-- CreateIndex
CREATE INDEX "PayoutNotification_createdAt_idx" ON "PayoutNotification"("createdAt");

-- CreateIndex
CREATE INDEX "OfflineSyncQueue_tenantId_userId_deviceId_idx" ON "OfflineSyncQueue"("tenantId", "userId", "deviceId");

-- CreateIndex
CREATE INDEX "OfflineSyncQueue_tenantId_operationStatus_idx" ON "OfflineSyncQueue"("tenantId", "operationStatus");

-- CreateIndex
CREATE INDEX "OfflineSyncQueue_resourceType_resourceId_idx" ON "OfflineSyncQueue"("resourceType", "resourceId");

-- CreateIndex
CREATE INDEX "OfflineSyncQueue_conflictDetected_operationStatus_idx" ON "OfflineSyncQueue"("conflictDetected", "operationStatus");

-- CreateIndex
CREATE INDEX "OfflineSyncQueue_nextAttemptAt_idx" ON "OfflineSyncQueue"("nextAttemptAt");

-- CreateIndex
CREATE INDEX "SyncConflictLog_tenantId_userId_deviceId_idx" ON "SyncConflictLog"("tenantId", "userId", "deviceId");

-- CreateIndex
CREATE INDEX "SyncConflictLog_syncQueueId_idx" ON "SyncConflictLog"("syncQueueId");

-- CreateIndex
CREATE INDEX "SyncConflictLog_resourceType_resourceId_idx" ON "SyncConflictLog"("resourceType", "resourceId");

-- CreateIndex
CREATE INDEX "SyncConflictLog_createdAt_idx" ON "SyncConflictLog"("createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "UserAgeVerification_userId_key" ON "UserAgeVerification"("userId");

-- CreateIndex
CREATE INDEX "UserAgeVerification_tenantId_ageGroup_idx" ON "UserAgeVerification"("tenantId", "ageGroup");

-- CreateIndex
CREATE UNIQUE INDEX "UserAgeVerification_tenantId_userId_key" ON "UserAgeVerification"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "ContentModerationQueue_tenantId_status_idx" ON "ContentModerationQueue"("tenantId", "status");

-- CreateIndex
CREATE INDEX "ContentModerationQueue_contentType_contentId_idx" ON "ContentModerationQueue"("contentType", "contentId");

-- CreateIndex
CREATE INDEX "ContentModerationQueue_status_severity_idx" ON "ContentModerationQueue"("status", "severity");

-- CreateIndex
CREATE INDEX "ContentModerationQueue_createdAt_idx" ON "ContentModerationQueue"("createdAt");

-- CreateIndex
CREATE INDEX "ChildSafetyPolicy_tenantId_ageGroup_idx" ON "ChildSafetyPolicy"("tenantId", "ageGroup");

-- CreateIndex
CREATE INDEX "ChildSafetyPolicy_tenantId_isActive_idx" ON "ChildSafetyPolicy"("tenantId", "isActive");

-- CreateIndex
CREATE INDEX "SafetyViolationLog_tenantId_userId_idx" ON "SafetyViolationLog"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "SafetyViolationLog_tenantId_violationType_idx" ON "SafetyViolationLog"("tenantId", "violationType");

-- CreateIndex
CREATE INDEX "SafetyViolationLog_policyId_idx" ON "SafetyViolationLog"("policyId");

-- CreateIndex
CREATE INDEX "SafetyViolationLog_createdAt_idx" ON "SafetyViolationLog"("createdAt");

-- CreateIndex
CREATE INDEX "ArtifactManifest_simulationManifestId_idx" ON "ArtifactManifest"("simulationManifestId");

-- CreateIndex
CREATE INDEX "ArtifactManifest_animationManifestId_idx" ON "ArtifactManifest"("animationManifestId");

-- CreateIndex
CREATE INDEX "Assessment_tenantId_createdAt_idx" ON "Assessment"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "Assessment_tenantId_updatedAt_idx" ON "Assessment"("tenantId", "updatedAt");

-- CreateIndex
CREATE INDEX "Assessment_tenantId_status_type_idx" ON "Assessment"("tenantId", "status", "type");

-- CreateIndex
CREATE INDEX "Classroom_tenantId_deletedAt_idx" ON "Classroom"("tenantId", "deletedAt");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_status_idx" ON "Enrollment"("tenantId", "status");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_createdAt_idx" ON "Enrollment"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_startedAt_idx" ON "Enrollment"("tenantId", "startedAt");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_completedAt_idx" ON "Enrollment"("tenantId", "completedAt");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_status_moduleId_idx" ON "Enrollment"("tenantId", "status", "moduleId");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_deletedAt_idx" ON "Enrollment"("tenantId", "deletedAt");

-- CreateIndex
CREATE INDEX "LearningEvent_tenantId_timestamp_idx" ON "LearningEvent"("tenantId", "timestamp");

-- CreateIndex
CREATE INDEX "LearningEvent_schemaVersion_idx" ON "LearningEvent"("schemaVersion");

-- CreateIndex
CREATE INDEX "MarketplaceListing_tenantId_createdAt_idx" ON "MarketplaceListing"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "MarketplaceListing_tenantId_publishedAt_idx" ON "MarketplaceListing"("tenantId", "publishedAt");

-- CreateIndex
CREATE INDEX "Module_tenantId_createdAt_idx" ON "Module"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "Module_tenantId_publishedAt_idx" ON "Module"("tenantId", "publishedAt");

-- CreateIndex
CREATE INDEX "Module_tenantId_updatedAt_idx" ON "Module"("tenantId", "updatedAt");

-- CreateIndex
CREATE INDEX "Module_tenantId_status_domain_idx" ON "Module"("tenantId", "status", "domain");

-- CreateIndex
CREATE INDEX "Module_tenantId_status_difficulty_idx" ON "Module"("tenantId", "status", "difficulty");

-- CreateIndex
CREATE INDEX "Module_tenantId_deletedAt_idx" ON "Module"("tenantId", "deletedAt");

-- CreateIndex
CREATE INDEX "Purchase_tenantId_purchasedAt_idx" ON "Purchase"("tenantId", "purchasedAt");

-- AddForeignKey
ALTER TABLE "UserConsent" ADD CONSTRAINT "UserConsent_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "UserRole" ADD CONSTRAINT "UserRole_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "UserRole" ADD CONSTRAINT "UserRole_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "Role"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RolePermission" ADD CONSTRAINT "RolePermission_roleId_fkey" FOREIGN KEY ("roleId") REFERENCES "Role"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RolePermission" ADD CONSTRAINT "RolePermission_permissionId_fkey" FOREIGN KEY ("permissionId") REFERENCES "Permission"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TenantCapability" ADD CONSTRAINT "TenantCapability_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TenantCapability" ADD CONSTRAINT "TenantCapability_capabilityId_fkey" FOREIGN KEY ("capabilityId") REFERENCES "Capability"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RegressionTestResult" ADD CONSTRAINT "RegressionTestResult_goldenDatasetId_fkey" FOREIGN KEY ("goldenDatasetId") REFERENCES "GoldenDataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TenantPlugin" ADD CONSTRAINT "TenantPlugin_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningExperience" ADD CONSTRAINT "LearningExperience_animationManifestId_fkey" FOREIGN KEY ("animationManifestId") REFERENCES "AnimationManifest"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AnimationManifest" ADD CONSTRAINT "AnimationManifest_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AnimationManifestVersion" ADD CONSTRAINT "AnimationManifestVersion_manifestId_fkey" FOREIGN KEY ("manifestId") REFERENCES "AnimationManifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AnimationManifestExtension" ADD CONSTRAINT "AnimationManifestExtension_id_fkey" FOREIGN KEY ("id") REFERENCES "AnimationManifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AnimationLinkAudit" ADD CONSTRAINT "AnimationLinkAudit_animationManifestId_fkey" FOREIGN KEY ("animationManifestId") REFERENCES "AnimationManifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "MarketplaceReview" ADD CONSTRAINT "MarketplaceReview_kernelId_fkey" FOREIGN KEY ("kernelId") REFERENCES "KernelPlugin"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ArtifactManifest" ADD CONSTRAINT "ArtifactManifest_simulationManifestId_fkey" FOREIGN KEY ("simulationManifestId") REFERENCES "SimulationManifest"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ArtifactManifest" ADD CONSTRAINT "ArtifactManifest_animationManifestId_fkey" FOREIGN KEY ("animationManifestId") REFERENCES "AnimationManifest"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ProvenanceNode" ADD CONSTRAINT "ProvenanceNode_generationRequestId_fkey" FOREIGN KEY ("generationRequestId") REFERENCES "GenerationRequest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "GenerationReplayManifest" ADD CONSTRAINT "GenerationReplayManifest_generationJobId_fkey" FOREIGN KEY ("generationJobId") REFERENCES "GenerationJob"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "UserAgeVerification" ADD CONSTRAINT "UserAgeVerification_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ContentModerationQueue" ADD CONSTRAINT "ContentModerationQueue_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SafetyViolationLog" ADD CONSTRAINT "SafetyViolationLog_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SafetyViolationLog" ADD CONSTRAINT "SafetyViolationLog_policyId_fkey" FOREIGN KEY ("policyId") REFERENCES "ChildSafetyPolicy"("id") ON DELETE SET NULL ON UPDATE CASCADE;
