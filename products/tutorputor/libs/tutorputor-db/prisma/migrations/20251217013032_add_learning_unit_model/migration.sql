-- CreateTable
CREATE TABLE "Asset" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "filename" TEXT NOT NULL,
    "displayName" TEXT,
    "fileType" TEXT NOT NULL,
    "mimeType" TEXT NOT NULL,
    "fileSize" INTEGER NOT NULL,
    "url" TEXT NOT NULL,
    "thumbnailUrl" TEXT,
    "metadata" JSONB,
    "tags" TEXT,
    "uploadedBy" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'UPLOADING',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "LearningUnit" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "level" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "intent" JSONB NOT NULL,
    "claims" JSONB NOT NULL,
    "evidence" JSONB NOT NULL,
    "tasks" JSONB NOT NULL,
    "artifacts" JSONB NOT NULL,
    "assessment" JSONB NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    "createdBy" TEXT NOT NULL
);

-- CreateIndex
CREATE INDEX "Asset_tenantId_fileType_idx" ON "Asset"("tenantId", "fileType");

-- CreateIndex
CREATE INDEX "Asset_tenantId_status_idx" ON "Asset"("tenantId", "status");

-- CreateIndex
CREATE INDEX "Asset_uploadedBy_idx" ON "Asset"("uploadedBy");

-- CreateIndex
CREATE INDEX "LearningUnit_tenantId_domain_idx" ON "LearningUnit"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "LearningUnit_tenantId_status_idx" ON "LearningUnit"("tenantId", "status");
