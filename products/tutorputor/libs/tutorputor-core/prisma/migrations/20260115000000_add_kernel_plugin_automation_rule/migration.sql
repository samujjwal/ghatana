-- CreateTable
CREATE TABLE "KernelPlugin" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "pluginId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "description" TEXT,
    "author" TEXT,
    "kernelType" TEXT NOT NULL,
    "capabilities" TEXT NOT NULL DEFAULT '[]',
    "dependencies" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateIndex
CREATE UNIQUE INDEX "KernelPlugin_pluginId_key" ON "KernelPlugin"("pluginId");

-- CreateIndex
CREATE INDEX "KernelPlugin_tenantId_kernelType_idx" ON "KernelPlugin"("tenantId", "kernelType");

-- CreateIndex
CREATE INDEX "KernelPlugin_tenantId_idx" ON "KernelPlugin"("tenantId");

-- CreateTable
CREATE TABLE "AutomationRule" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "trigger" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateIndex
CREATE INDEX "AutomationRule_tenantId_experienceId_idx" ON "AutomationRule"("tenantId", "experienceId");

-- CreateIndex
CREATE INDEX "AutomationRule_tenantId_enabled_idx" ON "AutomationRule"("tenantId", "enabled");
