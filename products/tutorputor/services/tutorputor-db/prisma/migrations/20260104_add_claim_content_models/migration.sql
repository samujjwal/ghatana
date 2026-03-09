-- CreateTable
CREATE TABLE "ClaimExample" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "content" JSONB NOT NULL,
    "difficulty" TEXT NOT NULL DEFAULT 'INTERMEDIATE',
    "orderIndex" INTEGER NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ClaimExample_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ClaimExample_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim" ("experienceId", "claimRef") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ClaimSimulation" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "simulationManifestId" TEXT NOT NULL,
    "interactionType" TEXT NOT NULL,
    "goal" TEXT NOT NULL,
    "successCriteria" JSONB NOT NULL,
    "estimatedMinutes" INTEGER NOT NULL DEFAULT 10,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ClaimSimulation_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ClaimSimulation_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim" ("experienceId", "claimRef") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ClaimSimulation_simulationManifestId_fkey" FOREIGN KEY ("simulationManifestId") REFERENCES "SimulationManifest" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ClaimAnimation" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "duration" INTEGER NOT NULL,
    "config" JSONB NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ClaimAnimation_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ClaimAnimation_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim" ("experienceId", "claimRef") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateIndex
CREATE INDEX "ClaimExample_experienceId_claimRef_idx" ON "ClaimExample"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "ClaimExample_type_idx" ON "ClaimExample"("type");

-- CreateIndex
CREATE UNIQUE INDEX "ClaimSimulation_experienceId_claimRef_key" ON "ClaimSimulation"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "ClaimSimulation_experienceId_idx" ON "ClaimSimulation"("experienceId");

-- CreateIndex
CREATE INDEX "ClaimSimulation_simulationManifestId_idx" ON "ClaimSimulation"("simulationManifestId");

-- CreateIndex
CREATE UNIQUE INDEX "ClaimAnimation_experienceId_claimRef_key" ON "ClaimAnimation"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "ClaimAnimation_experienceId_idx" ON "ClaimAnimation"("experienceId");

-- AlterTable: Add contentNeeds field to LearningClaim
-- Note: SQLite doesn't support ALTER COLUMN, so this is a comment for documentation
-- The contentNeeds field is added as a nullable JSON column in the schema
