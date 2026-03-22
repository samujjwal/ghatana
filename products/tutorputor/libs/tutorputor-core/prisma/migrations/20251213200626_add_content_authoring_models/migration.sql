-- CreateTable
CREATE TABLE "Tenant" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "name" TEXT NOT NULL,
    "subdomain" TEXT NOT NULL,
    "subscriptionTier" TEXT NOT NULL DEFAULT 'FREE',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "TenantSettings" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "allowPublicRegistration" BOOLEAN NOT NULL DEFAULT false,
    "requireEmailVerification" BOOLEAN NOT NULL DEFAULT true,
    "defaultUserRole" TEXT NOT NULL DEFAULT 'student',
    "maxUsersPerClassroom" INTEGER NOT NULL DEFAULT 50,
    "enabledFeatures" TEXT NOT NULL DEFAULT '[]',
    "enabledDomainPacks" TEXT NOT NULL DEFAULT '[]',
    "simulationQuotas" TEXT NOT NULL DEFAULT '{}',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "TenantSettings_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'student',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "User_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "IdentityProvider" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'oidc',
    "displayName" TEXT NOT NULL,
    "discoveryEndpoint" TEXT NOT NULL,
    "clientId" TEXT NOT NULL,
    "clientSecret" TEXT,
    "allowedDomains" TEXT NOT NULL DEFAULT '[]',
    "enabled" BOOLEAN NOT NULL DEFAULT false,
    "status" TEXT NOT NULL DEFAULT 'pending_verification',
    "roleMapping" TEXT,
    "lastSuccessfulAuthAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "IdentityProvider_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "SsoUserLink" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "userId" TEXT NOT NULL,
    "providerId" TEXT NOT NULL,
    "externalId" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "displayName" TEXT,
    "avatarUrl" TEXT,
    "lastClaims" TEXT,
    "linkedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastLoginAt" DATETIME,
    CONSTRAINT "SsoUserLink_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "SsoUserLink_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "IdentityProvider" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "DataExportRequest" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "requestedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "estimatedCompletionAt" DATETIME,
    "completedAt" DATETIME,
    "downloadUrl" TEXT,
    "expiresAt" DATETIME
);

-- CreateTable
CREATE TABLE "DataDeletionRequest" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'scheduled',
    "requestedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "scheduledDeletionAt" DATETIME NOT NULL,
    "completedAt" DATETIME,
    "retentionDays" INTEGER NOT NULL DEFAULT 30
);

-- CreateTable
CREATE TABLE "DeletionVerification" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "userId" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "expiresAt" DATETIME NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "AuditLog" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "actorId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "resourceType" TEXT NOT NULL,
    "resourceId" TEXT NOT NULL,
    "outcome" TEXT NOT NULL DEFAULT 'success',
    "timestamp" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ipAddress" TEXT,
    "userAgent" TEXT,
    "metadata" TEXT NOT NULL DEFAULT '{}'
);

-- CreateTable
CREATE TABLE "ClassroomMember" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "classroomId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'student',
    "joinedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "LearningPathEnrollment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "pathId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "currentNodeIndex" INTEGER NOT NULL DEFAULT 0,
    "startedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" DATETIME
);

-- CreateTable
CREATE TABLE "DomainAuthor" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "author" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "publishedAt" DATETIME,
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "DomainAuthorConcept" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "domainId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "level" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL DEFAULT 0,
    "learningObjectives" TEXT NOT NULL,
    "prerequisites" TEXT NOT NULL,
    "competencies" TEXT NOT NULL,
    "keywords" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "DomainAuthorConcept_domainId_fkey" FOREIGN KEY ("domainId") REFERENCES "DomainAuthor" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "SimulationDefinition" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "conceptId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "manifest" JSONB NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL DEFAULT 15,
    "interactivityLevel" TEXT NOT NULL DEFAULT 'medium',
    "purpose" TEXT NOT NULL,
    "previewConfig" JSONB,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "SimulationDefinition_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainAuthorConcept" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VisualizationDefinition" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "conceptId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "config" JSONB NOT NULL,
    "dataSource" TEXT NOT NULL DEFAULT 'simulation',
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "VisualizationDefinition_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainAuthorConcept" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ContentExample" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "conceptId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "problemStatement" TEXT NOT NULL,
    "solutionContent" TEXT NOT NULL,
    "keyLearningPoints" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL DEFAULT 20,
    "orderIndex" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ContentExample_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainAuthorConcept" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VisualizationSnapshot" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "exampleId" TEXT NOT NULL,
    "stepNumber" INTEGER NOT NULL,
    "stepDescription" TEXT NOT NULL,
    "config" JSONB NOT NULL,
    "data" JSONB,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "VisualizationSnapshot_exampleId_fkey" FOREIGN KEY ("exampleId") REFERENCES "ContentExample" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_SimulationTemplate" (
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
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
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
INSERT INTO "new_SimulationTemplate" ("authorAvatarUrl", "authorId", "authorName", "conceptId", "createdAt", "description", "difficulty", "domain", "id", "isPremium", "isVerified", "license", "manifestId", "moduleId", "organization", "publishedAt", "slug", "statsAvgTimeMinutes", "statsCompletionRate", "statsFavorites", "statsRating", "statsRatingCount", "statsUses", "statsViews", "tags", "tenantId", "thumbnailUrl", "title", "updatedAt", "version") SELECT "authorAvatarUrl", "authorId", "authorName", "conceptId", "createdAt", "description", "difficulty", "domain", "id", "isPremium", "isVerified", "license", "manifestId", "moduleId", "organization", "publishedAt", "slug", "statsAvgTimeMinutes", "statsCompletionRate", "statsFavorites", "statsRating", "statsRatingCount", "statsUses", "statsViews", "tags", "tenantId", "thumbnailUrl", "title", "updatedAt", "version" FROM "SimulationTemplate";
DROP TABLE "SimulationTemplate";
ALTER TABLE "new_SimulationTemplate" RENAME TO "SimulationTemplate";
CREATE INDEX "SimulationTemplate_tenantId_domain_idx" ON "SimulationTemplate"("tenantId", "domain");
CREATE INDEX "SimulationTemplate_tenantId_difficulty_idx" ON "SimulationTemplate"("tenantId", "difficulty");
CREATE INDEX "SimulationTemplate_tenantId_isPremium_idx" ON "SimulationTemplate"("tenantId", "isPremium");
CREATE INDEX "SimulationTemplate_tenantId_isVerified_idx" ON "SimulationTemplate"("tenantId", "isVerified");
CREATE INDEX "SimulationTemplate_tenantId_status_idx" ON "SimulationTemplate"("tenantId", "status");
CREATE UNIQUE INDEX "SimulationTemplate_tenantId_slug_key" ON "SimulationTemplate"("tenantId", "slug");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

-- CreateIndex
CREATE UNIQUE INDEX "Tenant_subdomain_key" ON "Tenant"("subdomain");

-- CreateIndex
CREATE INDEX "Tenant_subdomain_idx" ON "Tenant"("subdomain");

-- CreateIndex
CREATE UNIQUE INDEX "TenantSettings_tenantId_key" ON "TenantSettings"("tenantId");

-- CreateIndex
CREATE INDEX "User_tenantId_role_idx" ON "User"("tenantId", "role");

-- CreateIndex
CREATE UNIQUE INDEX "User_tenantId_email_key" ON "User"("tenantId", "email");

-- CreateIndex
CREATE INDEX "IdentityProvider_tenantId_enabled_idx" ON "IdentityProvider"("tenantId", "enabled");

-- CreateIndex
CREATE INDEX "SsoUserLink_userId_idx" ON "SsoUserLink"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "SsoUserLink_providerId_externalId_key" ON "SsoUserLink"("providerId", "externalId");

-- CreateIndex
CREATE INDEX "DataExportRequest_tenantId_status_idx" ON "DataExportRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "DataExportRequest_tenantId_userId_idx" ON "DataExportRequest"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "DataDeletionRequest_tenantId_status_idx" ON "DataDeletionRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "DataDeletionRequest_tenantId_userId_idx" ON "DataDeletionRequest"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "DeletionVerification_token_key" ON "DeletionVerification"("token");

-- CreateIndex
CREATE INDEX "DeletionVerification_userId_idx" ON "DeletionVerification"("userId");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_action_idx" ON "AuditLog"("tenantId", "action");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_resourceType_idx" ON "AuditLog"("tenantId", "resourceType");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_actorId_idx" ON "AuditLog"("tenantId", "actorId");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_timestamp_idx" ON "AuditLog"("tenantId", "timestamp");

-- CreateIndex
CREATE INDEX "ClassroomMember_classroomId_idx" ON "ClassroomMember"("classroomId");

-- CreateIndex
CREATE INDEX "ClassroomMember_userId_idx" ON "ClassroomMember"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ClassroomMember_classroomId_userId_key" ON "ClassroomMember"("classroomId", "userId");

-- CreateIndex
CREATE INDEX "LearningPathEnrollment_tenantId_userId_idx" ON "LearningPathEnrollment"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningPathEnrollment_pathId_userId_key" ON "LearningPathEnrollment"("pathId", "userId");

-- CreateIndex
CREATE INDEX "DomainAuthor_tenantId_status_idx" ON "DomainAuthor"("tenantId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "DomainAuthor_tenantId_domain_key" ON "DomainAuthor"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "DomainAuthorConcept_domainId_level_idx" ON "DomainAuthorConcept"("domainId", "level");

-- CreateIndex
CREATE UNIQUE INDEX "DomainAuthorConcept_domainId_name_key" ON "DomainAuthorConcept"("domainId", "name");

-- CreateIndex
CREATE UNIQUE INDEX "SimulationDefinition_conceptId_key" ON "SimulationDefinition"("conceptId");

-- CreateIndex
CREATE INDEX "SimulationDefinition_conceptId_idx" ON "SimulationDefinition"("conceptId");

-- CreateIndex
CREATE UNIQUE INDEX "VisualizationDefinition_conceptId_key" ON "VisualizationDefinition"("conceptId");

-- CreateIndex
CREATE INDEX "VisualizationDefinition_conceptId_idx" ON "VisualizationDefinition"("conceptId");

-- CreateIndex
CREATE INDEX "ContentExample_conceptId_difficulty_idx" ON "ContentExample"("conceptId", "difficulty");

-- CreateIndex
CREATE UNIQUE INDEX "ContentExample_conceptId_title_key" ON "ContentExample"("conceptId", "title");

-- CreateIndex
CREATE INDEX "VisualizationSnapshot_exampleId_idx" ON "VisualizationSnapshot"("exampleId");

-- CreateIndex
CREATE UNIQUE INDEX "VisualizationSnapshot_exampleId_stepNumber_key" ON "VisualizationSnapshot"("exampleId", "stepNumber");
