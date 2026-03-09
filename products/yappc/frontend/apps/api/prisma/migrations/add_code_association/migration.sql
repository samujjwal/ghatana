-- Migration: Add CodeAssociation model for linking code implementations to artifacts
-- Date: 2026-01-17

-- Create CodeAssociation table
CREATE TABLE "CodeAssociation" (
    "id" TEXT NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    "artifactId" TEXT NOT NULL,
    "codeArtifactId" TEXT NOT NULL,
    "relationship" TEXT NOT NULL,
    "metadata" JSONB DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CodeAssociation_artifactId_fkey" FOREIGN KEY ("artifactId") REFERENCES "Artifact"("id") ON DELETE CASCADE,
    CONSTRAINT "CodeAssociation_codeArtifactId_fkey" FOREIGN KEY ("codeArtifactId") REFERENCES "Artifact"("id") ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX "CodeAssociation_artifactId_idx" ON "CodeAssociation"("artifactId");
CREATE INDEX "CodeAssociation_codeArtifactId_idx" ON "CodeAssociation"("codeArtifactId");
CREATE INDEX "CodeAssociation_relationship_idx" ON "CodeAssociation"("relationship");

-- Unique constraint: prevent duplicate associations
CREATE UNIQUE INDEX "CodeAssociation_artifactId_codeArtifactId_relationship_key" 
    ON "CodeAssociation"("artifactId", "codeArtifactId", "relationship");

COMMENT ON TABLE "CodeAssociation" IS 'Links code implementations to business artifacts';
COMMENT ON COLUMN "CodeAssociation"."relationship" IS 'Type: IMPLEMENTATION, TEST, DOCUMENTATION, MOCK';
