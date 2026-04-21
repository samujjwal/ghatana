CREATE TYPE "LifecyclePhase_new" AS ENUM (
  'INTENT',
  'CONTEXT',
  'PLAN',
  'EXECUTE',
  'VERIFY',
  'OBSERVE',
  'LEARN',
  'INSTITUTIONALIZE'
);

ALTER TABLE "Project"
  ALTER COLUMN "lifecyclePhase" DROP DEFAULT,
  ALTER COLUMN "lifecyclePhase" TYPE "LifecyclePhase_new"
  USING (
    CASE "lifecyclePhase"::text
      WHEN 'SHAPE' THEN 'CONTEXT'
      WHEN 'VALIDATE' THEN 'PLAN'
      WHEN 'GENERATE' THEN 'EXECUTE'
      WHEN 'RUN' THEN 'VERIFY'
      WHEN 'IMPROVE' THEN 'LEARN'
      ELSE "lifecyclePhase"::text
    END
  )::"LifecyclePhase_new",
  ALTER COLUMN "lifecyclePhase" SET DEFAULT 'INTENT';

ALTER TABLE "LifecycleArtifact"
  ALTER COLUMN "phase" DROP DEFAULT,
  ALTER COLUMN "phase" TYPE "LifecyclePhase_new"
  USING (
    CASE "phase"::text
      WHEN 'SHAPE' THEN 'CONTEXT'
      WHEN 'VALIDATE' THEN 'PLAN'
      WHEN 'GENERATE' THEN 'EXECUTE'
      WHEN 'RUN' THEN 'VERIFY'
      WHEN 'IMPROVE' THEN 'LEARN'
      ELSE "phase"::text
    END
  )::"LifecyclePhase_new",
  ALTER COLUMN "phase" SET DEFAULT 'INTENT';

ALTER TABLE "LifecycleItem"
  ALTER COLUMN "phase" DROP DEFAULT,
  ALTER COLUMN "phase" TYPE "LifecyclePhase_new"
  USING (
    CASE "phase"::text
      WHEN 'SHAPE' THEN 'CONTEXT'
      WHEN 'VALIDATE' THEN 'PLAN'
      WHEN 'GENERATE' THEN 'EXECUTE'
      WHEN 'RUN' THEN 'VERIFY'
      WHEN 'IMPROVE' THEN 'LEARN'
      ELSE "phase"::text
    END
  )::"LifecyclePhase_new",
  ALTER COLUMN "phase" SET DEFAULT 'INTENT';

ALTER TABLE "LifecycleAIInsight"
  ALTER COLUMN "phase" DROP DEFAULT,
  ALTER COLUMN "phase" TYPE "LifecyclePhase_new"
  USING (
    CASE "phase"::text
      WHEN 'SHAPE' THEN 'CONTEXT'
      WHEN 'VALIDATE' THEN 'PLAN'
      WHEN 'GENERATE' THEN 'EXECUTE'
      WHEN 'RUN' THEN 'VERIFY'
      WHEN 'IMPROVE' THEN 'LEARN'
      ELSE "phase"::text
    END
  )::"LifecyclePhase_new",
  ALTER COLUMN "phase" SET DEFAULT 'INTENT';

DROP TYPE "LifecyclePhase";
ALTER TYPE "LifecyclePhase_new" RENAME TO "LifecyclePhase";