/*
  Warnings:

  - A unique constraint covering the columns `[projectId,name]` on the table `CanvasDocument` will be added. If there are existing duplicate values, this will fail.

*/
-- CreateIndex
CREATE UNIQUE INDEX "CanvasDocument_projectId_name_key" ON "CanvasDocument"("projectId", "name");
