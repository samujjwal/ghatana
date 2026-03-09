-- CreateEnum
CREATE TYPE "LifecyclePhase" AS ENUM ('INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'RUN', 'OBSERVE', 'IMPROVE');

-- AlterTable
ALTER TABLE "Project" ADD COLUMN     "lifecyclePhase" "LifecyclePhase" NOT NULL DEFAULT 'SHAPE';
