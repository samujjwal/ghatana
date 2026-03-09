/*
  Warnings:

  - Added the required column `updatedAt` to the `alerts` table without a default value. This is not possible if the table is not empty.

*/
-- DropIndex
DROP INDEX "alerts_tenantId_idx";

-- AlterTable
ALTER TABLE "alerts" ADD COLUMN     "acknowledgedAt" TIMESTAMP(3),
ADD COLUMN     "acknowledgedBy" TEXT,
ADD COLUMN     "metadata" JSONB,
ADD COLUMN     "relatedIncidents" TEXT[],
ADD COLUMN     "resolvedAt" TIMESTAMP(3),
ADD COLUMN     "resolvedBy" TEXT,
ADD COLUMN     "snoozedUntil" TIMESTAMP(3),
ADD COLUMN     "status" TEXT NOT NULL DEFAULT 'active',
ADD COLUMN     "title" TEXT,
ADD COLUMN     "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- CreateTable
CREATE TABLE "log_entries" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "level" TEXT NOT NULL,
    "source" TEXT NOT NULL,
    "message" TEXT NOT NULL,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "log_entries_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "log_entries_tenantId_level_timestamp_idx" ON "log_entries"("tenantId", "level", "timestamp");

-- CreateIndex
CREATE INDEX "log_entries_tenantId_source_timestamp_idx" ON "log_entries"("tenantId", "source", "timestamp");

-- CreateIndex
CREATE INDEX "log_entries_timestamp_idx" ON "log_entries"("timestamp" DESC);

-- CreateIndex
CREATE INDEX "alerts_tenantId_status_idx" ON "alerts"("tenantId", "status");

-- CreateIndex
CREATE INDEX "alerts_tenantId_severity_createdAt_idx" ON "alerts"("tenantId", "severity", "createdAt");

-- CreateIndex
CREATE INDEX "alerts_source_createdAt_idx" ON "alerts"("source", "createdAt");
