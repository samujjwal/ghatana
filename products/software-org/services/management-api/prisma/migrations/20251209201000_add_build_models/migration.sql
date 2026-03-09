-- CreateTable
CREATE TABLE "build_workflows" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "description" TEXT,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "ownerTeamId" TEXT,
    "trigger" JSONB NOT NULL DEFAULT '{}',
    "steps" JSONB NOT NULL DEFAULT '[]',
    "configuration" JSONB NOT NULL DEFAULT '{}',
    "createdBy" TEXT,
    "activatedAt" TIMESTAMP(3),
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "build_workflows_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "build_workflow_services" (
    "id" TEXT NOT NULL,
    "workflowId" TEXT NOT NULL,
    "serviceId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "build_workflow_services_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "build_workflow_policies" (
    "id" TEXT NOT NULL,
    "workflowId" TEXT NOT NULL,
    "policyId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "build_workflow_policies_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "build_agents" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "description" TEXT,
    "type" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "personaId" TEXT,
    "tools" JSONB NOT NULL DEFAULT '[]',
    "guardrails" JSONB NOT NULL DEFAULT '{}',
    "configuration" JSONB NOT NULL DEFAULT '{}',
    "createdBy" TEXT,
    "activatedAt" TIMESTAMP(3),
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "build_agents_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "build_agent_services" (
    "id" TEXT NOT NULL,
    "agentId" TEXT NOT NULL,
    "serviceId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "build_agent_services_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "build_workflows_tenantId_idx" ON "build_workflows"("tenantId");

-- CreateIndex
CREATE INDEX "build_workflows_status_idx" ON "build_workflows"("status");

-- CreateIndex
CREATE INDEX "build_workflows_ownerTeamId_idx" ON "build_workflows"("ownerTeamId");

-- CreateIndex
CREATE UNIQUE INDEX "build_workflows_tenantId_slug_key" ON "build_workflows"("tenantId", "slug");

-- CreateIndex
CREATE INDEX "build_workflow_services_workflowId_idx" ON "build_workflow_services"("workflowId");

-- CreateIndex
CREATE INDEX "build_workflow_services_serviceId_idx" ON "build_workflow_services"("serviceId");

-- CreateIndex
CREATE UNIQUE INDEX "build_workflow_services_workflowId_serviceId_key" ON "build_workflow_services"("workflowId", "serviceId");

-- CreateIndex
CREATE INDEX "build_workflow_policies_workflowId_idx" ON "build_workflow_policies"("workflowId");

-- CreateIndex
CREATE INDEX "build_workflow_policies_policyId_idx" ON "build_workflow_policies"("policyId");

-- CreateIndex
CREATE UNIQUE INDEX "build_workflow_policies_workflowId_policyId_key" ON "build_workflow_policies"("workflowId", "policyId");

-- CreateIndex
CREATE INDEX "build_agents_tenantId_idx" ON "build_agents"("tenantId");

-- CreateIndex
CREATE INDEX "build_agents_status_idx" ON "build_agents"("status");

-- CreateIndex
CREATE INDEX "build_agents_type_idx" ON "build_agents"("type");

-- CreateIndex
CREATE INDEX "build_agents_personaId_idx" ON "build_agents"("personaId");

-- CreateIndex
CREATE UNIQUE INDEX "build_agents_tenantId_slug_key" ON "build_agents"("tenantId", "slug");

-- CreateIndex
CREATE INDEX "build_agent_services_agentId_idx" ON "build_agent_services"("agentId");

-- CreateIndex
CREATE INDEX "build_agent_services_serviceId_idx" ON "build_agent_services"("serviceId");

-- CreateIndex
CREATE UNIQUE INDEX "build_agent_services_agentId_serviceId_key" ON "build_agent_services"("agentId", "serviceId");

-- AddForeignKey
ALTER TABLE "build_workflows" ADD CONSTRAINT "build_workflows_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_workflows" ADD CONSTRAINT "build_workflows_ownerTeamId_fkey" FOREIGN KEY ("ownerTeamId") REFERENCES "teams"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_workflow_services" ADD CONSTRAINT "build_workflow_services_workflowId_fkey" FOREIGN KEY ("workflowId") REFERENCES "build_workflows"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_workflow_services" ADD CONSTRAINT "build_workflow_services_serviceId_fkey" FOREIGN KEY ("serviceId") REFERENCES "services"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_workflow_policies" ADD CONSTRAINT "build_workflow_policies_workflowId_fkey" FOREIGN KEY ("workflowId") REFERENCES "build_workflows"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_workflow_policies" ADD CONSTRAINT "build_workflow_policies_policyId_fkey" FOREIGN KEY ("policyId") REFERENCES "policies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_agents" ADD CONSTRAINT "build_agents_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_agents" ADD CONSTRAINT "build_agents_personaId_fkey" FOREIGN KEY ("personaId") REFERENCES "personas"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_agent_services" ADD CONSTRAINT "build_agent_services_agentId_fkey" FOREIGN KEY ("agentId") REFERENCES "build_agents"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "build_agent_services" ADD CONSTRAINT "build_agent_services_serviceId_fkey" FOREIGN KEY ("serviceId") REFERENCES "services"("id") ON DELETE CASCADE ON UPDATE CASCADE;
