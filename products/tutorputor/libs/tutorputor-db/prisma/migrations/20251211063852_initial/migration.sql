-- CreateTable
CREATE TABLE "SimulationManifest" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "moduleId" TEXT,
    "manifest" JSONB NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "SimulationManifest_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "SimulationTemplate" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL,
    "tags" TEXT NOT NULL,
    "thumbnailUrl" TEXT,
    "license" TEXT NOT NULL DEFAULT 'FREE',
    "isPremium" BOOLEAN NOT NULL DEFAULT false,
    "isVerified" BOOLEAN NOT NULL DEFAULT false,
    "version" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "authorName" TEXT,
    "authorAvatarUrl" TEXT,
    "organization" TEXT,
    "statsViews" INTEGER NOT NULL DEFAULT 0,
    "statsUses" INTEGER NOT NULL DEFAULT 0,
    "statsFavorites" INTEGER NOT NULL DEFAULT 0,
    "statsRating" REAL NOT NULL DEFAULT 0,
    "statsRatingCount" INTEGER NOT NULL DEFAULT 0,
    "statsCompletionRate" REAL NOT NULL DEFAULT 0,
    "statsAvgTimeMinutes" INTEGER NOT NULL DEFAULT 0,
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    "conceptId" TEXT,
    "moduleId" TEXT,
    "manifestId" TEXT,
    CONSTRAINT "SimulationTemplate_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainConcept" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "SimulationTemplate_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "SimulationTemplate_manifestId_fkey" FOREIGN KEY ("manifestId") REFERENCES "SimulationManifest" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateIndex
CREATE INDEX "SimulationManifest_tenantId_domain_idx" ON "SimulationManifest"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "SimulationManifest_tenantId_moduleId_idx" ON "SimulationManifest"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_domain_idx" ON "SimulationTemplate"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_difficulty_idx" ON "SimulationTemplate"("tenantId", "difficulty");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_isPremium_idx" ON "SimulationTemplate"("tenantId", "isPremium");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_isVerified_idx" ON "SimulationTemplate"("tenantId", "isVerified");

-- CreateIndex
CREATE UNIQUE INDEX "SimulationTemplate_tenantId_slug_key" ON "SimulationTemplate"("tenantId", "slug");
