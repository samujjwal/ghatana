CREATE TYPE "LearnerDifficultyPreference" AS ENUM ('BEGINNER', 'EASY', 'MEDIUM', 'HARD', 'EXPERT');
CREATE TYPE "LearnerModalityPreference" AS ENUM ('VISUAL', 'AUDITORY', 'KINESTHETIC', 'READING', 'MIXED');
CREATE TYPE "LearnerPacingPreference" AS ENUM ('SELF_PACED', 'GUIDED', 'ADAPTIVE', 'INTENSIVE');
CREATE TYPE "KnowledgeGapSeverity" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE "KnowledgeGapDetectionMethod" AS ENUM ('ASSESSMENT', 'PREREQUISITE_CHECK', 'ADAPTIVE_ANALYSIS', 'LEARNER_REPORTED', 'AI_PREDICTION');
CREATE TYPE "LearnerPathwayStatus" AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ABANDONED');

CREATE TABLE "LearnerProfile" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "preferredDifficulty" "LearnerDifficultyPreference" NOT NULL DEFAULT 'MEDIUM',
    "preferredModality" "LearnerModalityPreference" NOT NULL DEFAULT 'MIXED',
    "preferredPacing" "LearnerPacingPreference" NOT NULL DEFAULT 'ADAPTIVE',
    "preferredSessionMinutes" INTEGER NOT NULL DEFAULT 30,
    "notificationFrequency" TEXT NOT NULL DEFAULT 'daily',
    "visualLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "auditoryLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "kinestheticLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "readingLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "avgSessionMinutes" DOUBLE PRECISION NOT NULL DEFAULT 30,
    "preferredTimeOfDay" TEXT,
    "streakDays" INTEGER NOT NULL DEFAULT 0,
    "lastActiveAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LearnerProfile_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "LearnerMastery" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "masteryProbability" DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    "confidenceScore" DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "correctAttempts" INTEGER NOT NULL DEFAULT 0,
    "incorrectAttempts" INTEGER NOT NULL DEFAULT 0,
    "hintsUsed" INTEGER NOT NULL DEFAULT 0,
    "totalTimeSeconds" INTEGER NOT NULL DEFAULT 0,
    "lastObservedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "nextReviewAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LearnerMastery_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "KnowledgeGap" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "prerequisiteId" TEXT NOT NULL,
    "severity" "KnowledgeGapSeverity" NOT NULL DEFAULT 'MEDIUM',
    "detectedBy" "KnowledgeGapDetectionMethod" NOT NULL DEFAULT 'ADAPTIVE_ANALYSIS',
    "status" TEXT NOT NULL DEFAULT 'OPEN',
    "evidence" JSONB,
    "detectionCount" INTEGER NOT NULL DEFAULT 1,
    "lastDetectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "KnowledgeGap_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "PreferenceChange" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "changedBy" TEXT NOT NULL DEFAULT 'user',
    "reason" TEXT,
    "changes" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PreferenceChange_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "LearningPathway" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "goal" TEXT NOT NULL,
    "status" "LearnerPathwayStatus" NOT NULL DEFAULT 'ACTIVE',
    "recommendedModules" JSONB NOT NULL DEFAULT '[]',
    "currentModuleId" TEXT,
    "progressPercent" INTEGER NOT NULL DEFAULT 0,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LearningPathway_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "LearnerProfile_userId_key" ON "LearnerProfile"("userId");
CREATE INDEX "LearnerProfile_tenantId_userId_idx" ON "LearnerProfile"("tenantId", "userId");
CREATE INDEX "LearnerProfile_tenantId_updatedAt_idx" ON "LearnerProfile"("tenantId", "updatedAt");

CREATE UNIQUE INDEX "LearnerMastery_profileId_conceptId_key" ON "LearnerMastery"("profileId", "conceptId");
CREATE INDEX "LearnerMastery_tenantId_conceptId_idx" ON "LearnerMastery"("tenantId", "conceptId");
CREATE INDEX "LearnerMastery_tenantId_masteryProbability_idx" ON "LearnerMastery"("tenantId", "masteryProbability");

CREATE UNIQUE INDEX "KnowledgeGap_profileId_conceptId_prerequisiteId_status_key" ON "KnowledgeGap"("profileId", "conceptId", "prerequisiteId", "status");
CREATE INDEX "KnowledgeGap_tenantId_conceptId_idx" ON "KnowledgeGap"("tenantId", "conceptId");
CREATE INDEX "KnowledgeGap_tenantId_severity_idx" ON "KnowledgeGap"("tenantId", "severity");

CREATE INDEX "PreferenceChange_tenantId_profileId_idx" ON "PreferenceChange"("tenantId", "profileId");
CREATE INDEX "PreferenceChange_tenantId_createdAt_idx" ON "PreferenceChange"("tenantId", "createdAt");

CREATE INDEX "LearningPathway_tenantId_profileId_idx" ON "LearningPathway"("tenantId", "profileId");
CREATE INDEX "LearningPathway_tenantId_status_idx" ON "LearningPathway"("tenantId", "status");

ALTER TABLE "LearnerProfile"
    ADD CONSTRAINT "LearnerProfile_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES "User"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "LearnerMastery"
    ADD CONSTRAINT "LearnerMastery_profileId_fkey"
    FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "KnowledgeGap"
    ADD CONSTRAINT "KnowledgeGap_profileId_fkey"
    FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "PreferenceChange"
    ADD CONSTRAINT "PreferenceChange_profileId_fkey"
    FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "LearningPathway"
    ADD CONSTRAINT "LearningPathway_profileId_fkey"
    FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;
