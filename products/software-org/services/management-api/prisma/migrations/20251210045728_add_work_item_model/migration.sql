-- CreateTable
CREATE TABLE "work_items" (
    "id" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "status" TEXT NOT NULL DEFAULT 'TODO',
    "priority" TEXT NOT NULL DEFAULT 'MEDIUM',
    "stageKey" TEXT NOT NULL,
    "assigneeId" TEXT,
    "tenantId" TEXT,
    "departmentId" TEXT,
    "workflowId" TEXT,
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "dueDate" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "work_items_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "work_items_type_idx" ON "work_items"("type");

-- CreateIndex
CREATE INDEX "work_items_status_idx" ON "work_items"("status");

-- CreateIndex
CREATE INDEX "work_items_priority_idx" ON "work_items"("priority");

-- CreateIndex
CREATE INDEX "work_items_stageKey_idx" ON "work_items"("stageKey");

-- CreateIndex
CREATE INDEX "work_items_assigneeId_idx" ON "work_items"("assigneeId");

-- CreateIndex
CREATE INDEX "work_items_tenantId_idx" ON "work_items"("tenantId");

-- CreateIndex
CREATE INDEX "work_items_departmentId_idx" ON "work_items"("departmentId");

-- CreateIndex
CREATE INDEX "work_items_workflowId_idx" ON "work_items"("workflowId");

-- AddForeignKey
ALTER TABLE "work_items" ADD CONSTRAINT "work_items_departmentId_fkey" FOREIGN KEY ("departmentId") REFERENCES "departments"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "work_items" ADD CONSTRAINT "work_items_workflowId_fkey" FOREIGN KEY ("workflowId") REFERENCES "workflows"("id") ON DELETE SET NULL ON UPDATE CASCADE;
