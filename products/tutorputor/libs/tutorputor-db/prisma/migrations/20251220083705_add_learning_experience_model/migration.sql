-- CreateTable
CREATE TABLE "LearningExperience" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT,
    "title" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "conceptId" TEXT,
    "intentProblem" TEXT NOT NULL,
    "intentMotivation" TEXT NOT NULL,
    "intentMisconceptions" JSONB NOT NULL,
    "targetGrades" JSONB NOT NULL,
    "curriculumAlignment" JSONB,
    "gradeAdaptations" JSONB NOT NULL,
    "simulationManifestId" TEXT,
    "assessmentConfig" JSONB NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "estimatedTimeMinutes" INTEGER NOT NULL DEFAULT 30,
    "createdBy" TEXT NOT NULL,
    "lastEditedBy" TEXT,
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "LearningExperience_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "LearningExperience_simulationManifestId_fkey" FOREIGN KEY ("simulationManifestId") REFERENCES "SimulationManifest" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "LearningClaim" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "text" TEXT NOT NULL,
    "bloomLevel" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "prerequisites" JSONB,
    "gradeOverrides" JSONB,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "LearningClaim_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "LearningEvidence" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "evidenceRef" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "observables" JSONB NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "LearningEvidence_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ExperienceTask" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "taskRef" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "evidenceRef" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "config" JSONB NOT NULL,
    "gradeOverrides" JSONB,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ExperienceTask_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ValidationRecord" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "authorityScore" INTEGER NOT NULL,
    "accuracyScore" INTEGER NOT NULL,
    "usefulnessScore" INTEGER NOT NULL,
    "harmlessnessScore" INTEGER NOT NULL,
    "accessibilityScore" INTEGER NOT NULL,
    "gradefitScore" INTEGER NOT NULL,
    "overallStatus" TEXT NOT NULL,
    "issues" JSONB NOT NULL,
    "suggestions" JSONB,
    "validatedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ValidationRecord_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "AIGenerationLog" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "experienceId" TEXT,
    "operation" TEXT NOT NULL,
    "provider" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "inputTokens" INTEGER NOT NULL,
    "outputTokens" INTEGER NOT NULL,
    "costUsd" REAL NOT NULL,
    "latencyMs" INTEGER NOT NULL,
    "success" BOOLEAN NOT NULL,
    "errorMessage" TEXT,
    "requestHash" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateIndex
CREATE UNIQUE INDEX "LearningExperience_moduleId_key" ON "LearningExperience"("moduleId");

-- CreateIndex
CREATE INDEX "LearningExperience_tenantId_status_idx" ON "LearningExperience"("tenantId", "status");

-- CreateIndex
CREATE INDEX "LearningExperience_tenantId_domain_idx" ON "LearningExperience"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "LearningExperience_createdBy_idx" ON "LearningExperience"("createdBy");

-- CreateIndex
CREATE INDEX "LearningClaim_experienceId_idx" ON "LearningClaim"("experienceId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningClaim_experienceId_claimRef_key" ON "LearningClaim"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "LearningEvidence_experienceId_idx" ON "LearningEvidence"("experienceId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningEvidence_experienceId_evidenceRef_key" ON "LearningEvidence"("experienceId", "evidenceRef");

-- CreateIndex
CREATE INDEX "ExperienceTask_experienceId_idx" ON "ExperienceTask"("experienceId");

-- CreateIndex
CREATE UNIQUE INDEX "ExperienceTask_experienceId_taskRef_key" ON "ExperienceTask"("experienceId", "taskRef");

-- CreateIndex
CREATE INDEX "ValidationRecord_experienceId_idx" ON "ValidationRecord"("experienceId");

-- CreateIndex
CREATE INDEX "AIGenerationLog_tenantId_createdAt_idx" ON "AIGenerationLog"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "AIGenerationLog_userId_createdAt_idx" ON "AIGenerationLog"("userId", "createdAt");

-- CreateIndex
CREATE INDEX "AIGenerationLog_experienceId_idx" ON "AIGenerationLog"("experienceId");
