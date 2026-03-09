PRAGMA foreign_keys=OFF;

CREATE TABLE "Module" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PUBLISHED',
    "description" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "authorId" TEXT,
    "updatedBy" TEXT,
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "ModuleTag" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "moduleId" TEXT NOT NULL,
    "label" TEXT NOT NULL,
    CONSTRAINT "ModuleTag_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "ModuleLearningObjective" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "moduleId" TEXT NOT NULL,
    "label" TEXT NOT NULL,
    "taxonomyLevel" TEXT NOT NULL,
    CONSTRAINT "ModuleLearningObjective_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "ModuleContentBlock" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "moduleId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "blockType" TEXT NOT NULL,
    "payload" TEXT NOT NULL,
    CONSTRAINT "ModuleContentBlock_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "ModulePrerequisite" (
    "moduleId" TEXT NOT NULL,
    "prerequisiteModuleId" TEXT NOT NULL,
    CONSTRAINT "ModulePrerequisite_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ModulePrerequisite_prerequisiteModuleId_fkey" FOREIGN KEY ("prerequisiteModuleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    PRIMARY KEY ("moduleId", "prerequisiteModuleId")
);

CREATE TABLE "ModuleRevision" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "moduleId" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "snapshot" TEXT NOT NULL,
    "createdBy" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ModuleRevision_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "Enrollment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'NOT_STARTED',
    "progressPercent" INTEGER NOT NULL DEFAULT 0,
    "startedAt" DATETIME,
    "completedAt" DATETIME,
    "timeSpentSeconds" INTEGER NOT NULL DEFAULT 0,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Enrollment_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "Assessment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'QUIZ',
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "passingScore" INTEGER NOT NULL DEFAULT 80,
    "attemptsAllowed" INTEGER,
    "timeLimitMinutes" INTEGER,
    "createdBy" TEXT NOT NULL,
    "updatedBy" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Assessment_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "AssessmentObjective" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "assessmentId" TEXT NOT NULL,
    "label" TEXT NOT NULL,
    "taxonomyLevel" TEXT NOT NULL,
    CONSTRAINT "AssessmentObjective_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "AssessmentItem" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "assessmentId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "itemType" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "stimulus" TEXT,
    "choices" TEXT,
    "modelAnswer" TEXT,
    "rubric" TEXT,
    "points" INTEGER NOT NULL DEFAULT 10,
    "metadata" TEXT,
    CONSTRAINT "AssessmentItem_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "AssessmentAttempt" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'IN_PROGRESS',
    "responses" TEXT,
    "scorePercent" INTEGER,
    "feedback" TEXT,
    "startedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "submittedAt" DATETIME,
    "gradedAt" DATETIME,
    "timeSpentSeconds" INTEGER,
    CONSTRAINT "AssessmentAttempt_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "AssessmentDraft" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "createdBy" TEXT NOT NULL,
    "payload" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "LearningEvent" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT,
    "eventType" TEXT NOT NULL,
    "payload" TEXT,
    "timestamp" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "MarketplaceListing" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "creatorId" TEXT NOT NULL,
    "priceCents" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "visibility" TEXT NOT NULL DEFAULT 'PUBLIC',
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "MarketplaceListing_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "Module_tenantId_slug_key" ON "Module"("tenantId", "slug");
CREATE INDEX "Module_tenantId_domain_idx" ON "Module"("tenantId", "domain");
CREATE INDEX "Module_tenantId_status_idx" ON "Module"("tenantId", "status");

CREATE UNIQUE INDEX "ModuleTag_moduleId_label_key" ON "ModuleTag"("moduleId", "label");

CREATE INDEX "ModuleRevision_moduleId_version_idx" ON "ModuleRevision"("moduleId", "version");

CREATE INDEX "Enrollment_tenantId_userId_idx" ON "Enrollment"("tenantId", "userId");
CREATE UNIQUE INDEX "Enrollment_tenantId_userId_moduleId_key" ON "Enrollment"("tenantId", "userId", "moduleId");

CREATE INDEX "Assessment_tenantId_moduleId_idx" ON "Assessment"("tenantId", "moduleId");
CREATE INDEX "Assessment_tenantId_status_idx" ON "Assessment"("tenantId", "status");
CREATE INDEX "AssessmentAttempt_tenantId_userId_idx" ON "AssessmentAttempt"("tenantId", "userId");
CREATE INDEX "AssessmentAttempt_tenantId_assessmentId_idx" ON "AssessmentAttempt"("tenantId", "assessmentId");
CREATE INDEX "AssessmentDraft_tenantId_moduleId_idx" ON "AssessmentDraft"("tenantId", "moduleId");
CREATE INDEX "AssessmentDraft_tenantId_createdBy_idx" ON "AssessmentDraft"("tenantId", "createdBy");

CREATE INDEX "LearningEvent_tenantId_eventType_idx" ON "LearningEvent"("tenantId", "eventType");
CREATE INDEX "LearningEvent_tenantId_userId_idx" ON "LearningEvent"("tenantId", "userId");

CREATE INDEX "MarketplaceListing_tenantId_status_idx" ON "MarketplaceListing"("tenantId", "status");
CREATE INDEX "MarketplaceListing_tenantId_moduleId_idx" ON "MarketplaceListing"("tenantId", "moduleId");
CREATE INDEX "MarketplaceListing_tenantId_creatorId_idx" ON "MarketplaceListing"("tenantId", "creatorId");

PRAGMA foreign_keys=ON;
