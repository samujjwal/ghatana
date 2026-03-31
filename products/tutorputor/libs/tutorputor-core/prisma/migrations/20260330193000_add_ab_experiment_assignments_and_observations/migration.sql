CREATE TABLE "ABExperimentAssignment" (
    "id" TEXT NOT NULL,
    "experimentId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "variant" TEXT NOT NULL,
    "assignedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "metadata" JSONB,

    CONSTRAINT "ABExperimentAssignment_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "ABExperimentObservation" (
    "id" TEXT NOT NULL,
    "experimentId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "variant" TEXT NOT NULL,
    "sessionId" TEXT,
    "assetId" TEXT,
    "metricValue" DOUBLE PRECISION NOT NULL,
    "completed" BOOLEAN,
    "masteryScore" DOUBLE PRECISION,
    "feedbackScore" DOUBLE PRECISION,
    "metadata" JSONB,
    "observedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ABExperimentObservation_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "ABExperimentAssignment_experimentId_userId_key" ON "ABExperimentAssignment"("experimentId", "userId");
CREATE INDEX "ABExperimentAssignment_tenantId_variant_idx" ON "ABExperimentAssignment"("tenantId", "variant");
CREATE INDEX "ABExperimentAssignment_experimentId_variant_idx" ON "ABExperimentAssignment"("experimentId", "variant");
CREATE INDEX "ABExperimentObservation_experimentId_variant_idx" ON "ABExperimentObservation"("experimentId", "variant");
CREATE INDEX "ABExperimentObservation_tenantId_observedAt_idx" ON "ABExperimentObservation"("tenantId", "observedAt");
CREATE INDEX "ABExperimentObservation_experimentId_userId_idx" ON "ABExperimentObservation"("experimentId", "userId");

ALTER TABLE "ABExperimentAssignment"
ADD CONSTRAINT "ABExperimentAssignment_experimentId_fkey"
FOREIGN KEY ("experimentId") REFERENCES "ABExperiment"("id")
ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ABExperimentObservation"
ADD CONSTRAINT "ABExperimentObservation_experimentId_fkey"
FOREIGN KEY ("experimentId") REFERENCES "ABExperiment"("id")
ON DELETE CASCADE ON UPDATE CASCADE;
