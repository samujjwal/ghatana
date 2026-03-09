-- DropIndex: Remove faulty unique constraint that prevents multiple non-default projects
DROP INDEX "Project_ownerWorkspaceId_isDefault_key";

-- CreateIndex: Partial unique index - only ONE default project allowed per workspace
-- Multiple non-default projects are allowed
CREATE UNIQUE INDEX "Project_ownerWorkspaceId_isDefault_unique" 
ON "Project"("ownerWorkspaceId", "isDefault") 
WHERE "isDefault" = true;
