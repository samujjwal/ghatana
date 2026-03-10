import { PrismaLibSql } from "@prisma/adapter-libsql";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

import { PrismaClient } from "../generated/prisma";

export { PrismaClient };

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const require = createRequire(import.meta.url);

// Load Prisma dynamically at module initialization
let PrismaClientImpl: any = PrismaClient;

// Lazy-loaded on first use
async function getPrismaClientImpl() {
  return PrismaClientImpl;
}

export type TutorPrismaClient = PrismaClient;

export interface SeedOptions {
  tenantId?: string;
  seedUserId?: string;
}

export const DEFAULT_TENANT_ID = "tenant-stub";
export const DEFAULT_USER_ID = "user-stub";

/**
 * Create a PrismaClient with the libsql driver adapter.
 * This is required for Prisma ORM 7.
 * Uses libsql instead of better-sqlite3 for better Node.js 24 compatibility.
 */
export function createPrismaClient(): PrismaClient {
  // Use the imported PrismaClient class
  const PrismaClientClass = PrismaClientImpl;

  const rootFromDistSrc = path.resolve(__dirname, "..", "..");
  const rootFromSrc = path.resolve(__dirname, "..");
  const packageRoot = existsSync(path.resolve(rootFromDistSrc, "prisma")) ? rootFromDistSrc : rootFromSrc;

  const dbPath = process.env.TUTORPUTOR_DATABASE_URL?.replace("file:", "")
    ?? path.resolve(packageRoot, "prisma", "dev.db");

  const adapter = new PrismaLibSql({ url: `file:${dbPath}` });
  const client = new PrismaClientClass({
    adapter,
    // Enable detailed logging for debugging
    log: process.env.PRISMA_DEBUG === 'true' ? ['query', 'error', 'warn'] : []
  }) as PrismaClient;

  // Log what we got
  console.log("[TutorPutor DB] PrismaClient created");
  console.log("[TutorPutor DB] Database path:", dbPath);
  console.log("[TutorPutor DB] Has learningExperience model:", "learningExperience" in client);

  // Validate that we can connect (but don't execute a query yet)
  client.$executeRawUnsafe("SELECT 1").then(() => {
    console.log("[TutorPutor DB] Database connection verified");
  }).catch((err: Error) => {
    console.warn("[TutorPutor DB] Database connection test failed:", err.message);
    console.warn("[TutorPutor DB] This is normal if the database schema hasn't been created yet");
  });

  return client;
}

export async function seedBaseData(
  prisma: PrismaClient,
  options: SeedOptions = {}
): Promise<void> {
  const tenantId = options.tenantId ?? DEFAULT_TENANT_ID;
  const seedUserId = options.seedUserId ?? DEFAULT_USER_ID;

  await prisma.assessmentAttempt.deleteMany({ where: { tenantId } });
  await prisma.assessmentDraft.deleteMany({ where: { tenantId } });
  await prisma.assessment.deleteMany({ where: { tenantId } });
  await prisma.enrollment.deleteMany({ where: { tenantId } });
  await prisma.module.deleteMany({ where: { tenantId } });

  const modules = buildSeedModules();
  for (const module of modules) {
    await prisma.module.create({
      data: {
        id: module.id,
        tenantId,
        slug: module.slug,
        title: module.title,
        domain: module.domain,
        difficulty: module.difficulty,
        estimatedTimeMinutes: module.estimatedTimeMinutes,
        status: "PUBLISHED",
        description: module.description,
        version: module.version ?? 1,
        tags: {
          create: module.tags.map((label) => ({ label }))
        },
        learningObjectives: {
          create: module.learningObjectives.map((objective) => ({
            label: objective.label,
            taxonomyLevel: objective.taxonomyLevel
          }))
        },
        contentBlocks: {
          create: module.contentBlocks.map((block, index) => ({
            id: block.id,
            orderIndex: block.orderIndex ?? index,
            blockType: block.blockType,
            payload: block.payload
          }))
        },
        prerequisites: {
          create: module.prerequisites.map((prereqId) => ({
            prerequisiteModuleId: prereqId
          }))
        }
      }
    });
  }

  if (modules.length > 0) {
    await prisma.enrollment.create({
      data: {
        tenantId,
        userId: seedUserId,
        moduleId: modules[0].id,
        status: "IN_PROGRESS",
        progressPercent: 30,
        startedAt: new Date(),
        timeSpentSeconds: 1800
      }
    });

    await createSeedAssessment(prisma, {
      module: modules[0]!,
      tenantId,
      createdBy: seedUserId
    });

    await prisma.marketplaceListing.create({
      data: {
        tenantId,
        moduleId: modules[0].id,
        creatorId: seedUserId,
        priceCents: 0,
        status: "ACTIVE",
        visibility: "PUBLIC",
        publishedAt: new Date()
      }
    });

    await prisma.learningEvent.createMany({
      data: [
        {
          tenantId,
          userId: seedUserId,
          moduleId: modules[0].id,
          eventType: "module_viewed",
          timestamp: new Date()
        },
        {
          tenantId,
          userId: seedUserId,
          moduleId: modules[0].id,
          eventType: "module_completed",
          timestamp: new Date()
        }
      ]
    });
  }

  await seedLearningUnits(prisma, { tenantId, createdBy: seedUserId });
  await seedSimulations(prisma, { tenantId });
}

export async function resetAllData(prisma: PrismaClient): Promise<void> {
  await prisma.assessmentAttempt.deleteMany();
  await prisma.assessmentDraft.deleteMany();
  await prisma.assessment.deleteMany();
  await prisma.enrollment.deleteMany();
  await prisma.module.deleteMany();
  await prisma.learningUnit.deleteMany();
  await prisma.simulationManifest.deleteMany();
}

export async function seedLearningUnits(prisma: PrismaClient, options: { tenantId: string, createdBy: string }) {
  const { tenantId, createdBy } = options;

  const units = [
    {
      id: "lu-newton-laws",
      domain: "PHYSICS",
      level: "FOUNDATIONAL",
      status: "PUBLISHED",
      version: 1,
      intent: {
        goal: "Understand Newton's Laws of Motion",
        audience: "High School Physics Students",
        prerequisites: ["Basic Algebra", "Vectors"]
      },
      claims: [
        { id: "c1", text: "Force equals mass times acceleration (F=ma)", type: "FACT" },
        { id: "c2", text: "For every action, there is an equal and opposite reaction", type: "PRINCIPLE" }
      ],
      evidence: [
        { id: "e1", claimId: "c1", description: "Experimental data from air track glider", source: "Lab Report 101" }
      ],
      tasks: [
        { id: "t1", title: "Calculate Force", description: "Given m=5kg, a=2m/s^2, find F", type: "CALCULATION" }
      ],
      artifacts: {},
      assessment: {
        type: "QUIZ",
        items: []
      }
    },
    {
      id: "lu-photosynthesis",
      domain: "BIOLOGY",
      level: "INTERMEDIATE",
      status: "DRAFT",
      version: 1,
      intent: {
        goal: "Explain the process of photosynthesis",
        audience: "Biology Students",
        prerequisites: ["Cell Biology"]
      },
      claims: [
        { id: "c1", text: "Plants convert light energy into chemical energy", type: "PROCESS" }
      ],
      evidence: [],
      tasks: [],
      artifacts: {},
      assessment: {}
    }
  ];

  for (const unit of units) {
    await prisma.learningUnit.upsert({
      where: { id: unit.id },
      update: {},
      create: {
        id: unit.id,
        tenantId,
        domain: unit.domain,
        level: unit.level,
        status: unit.status,
        version: unit.version,
        intent: unit.intent,
        claims: unit.claims,
        evidence: unit.evidence,
        tasks: unit.tasks,
        artifacts: unit.artifacts,
        assessment: unit.assessment,
        createdBy
      }
    });
  }
  console.log(`✅ Seeded ${units.length} Learning Units`);
}

export async function seedSimulations(prisma: PrismaClient, options: { tenantId: string }) {
  const { tenantId } = options;

  const manifests = [
    {
      id: "sim-projectile-motion",
      domain: "PHYSICS",
      version: "1.0.0",
      title: "Projectile Motion Lab",
      description: "Explore how launch angle and initial velocity affect projectile trajectory.",
      manifest: {
        entities: [
          { id: "cannon", type: "launcher", position: { x: 0, y: 0 } },
          { id: "projectile", type: "particle", mass: 1 }
        ],
        constraints: [
          { type: "gravity", value: 9.8 }
        ],
        variables: [
          { name: "angle", min: 0, max: 90, default: 45 },
          { name: "velocity", min: 0, max: 100, default: 50 }
        ]
      }
    },
    {
      id: "sim-circuit-builder",
      domain: "PHYSICS",
      version: "1.0.0",
      title: "DC Circuit Builder",
      description: "Build and analyze simple DC circuits with resistors and batteries.",
      manifest: {
        entities: [
          { id: "battery", type: "source", voltage: 9 },
          { id: "resistor", type: "load", resistance: 100 }
        ],
        constraints: [],
        variables: []
      }
    }
  ];

  for (const sim of manifests) {
    // @ts-ignore - Enum type mismatch might occur with string literals
    await prisma.simulationManifest.upsert({
      where: { id: sim.id },
      update: {},
      create: {
        id: sim.id,
        tenantId,
        domain: sim.domain as any,
        version: sim.version,
        title: sim.title,
        description: sim.description,
        manifest: sim.manifest
      }
    });
  }
  console.log(`✅ Seeded ${manifests.length} Simulation Manifests`);
}


function buildSeedModules() {
  return [
    {
      id: "mod-algebra",
      slug: "algebra-foundations",
      title: "Algebra Foundations",
      domain: "MATH" as const,
      difficulty: "INTRO" as const,
      estimatedTimeMinutes: 240,
      status: "PUBLISHED" as const,
      description:
        "Learn the basics of algebra, variables, and linear equations.",
      version: 1,
      tags: ["variables", "equations"],
      learningObjectives: [
        {
          label: "Understand variables and constants",
          taxonomyLevel: "understand"
        },
        {
          label: "Solve single-variable equations",
          taxonomyLevel: "apply"
        }
      ],
      contentBlocks: [
        {
          id: "block-algebra-1",
          blockType: "text",
          orderIndex: 0,
          payload: {
            markdown: "Welcome to Algebra Foundations!"
          }
        },
        {
          id: "block-algebra-2",
          blockType: "exercise",
          orderIndex: 1,
          payload: {
            prompt: "Solve for x: 2x + 5 = 15"
          }
        }
      ],
      prerequisites: []
    },
    {
      id: "mod-statistics",
      slug: "statistics-basics",
      title: "Statistics Basics",
      domain: "MATH" as const,
      difficulty: "INTRO" as const,
      estimatedTimeMinutes: 180,
      status: "PUBLISHED" as const,
      description:
        "Explore descriptive statistics and probability fundamentals.",
      version: 1,
      tags: ["probability", "data"],
      learningObjectives: [
        {
          label: "Summarize data using mean, median, and mode",
          taxonomyLevel: "understand"
        }
      ],
      contentBlocks: [
        {
          id: "block-statistics-1",
          blockType: "text",
          orderIndex: 0,
          payload: {
            markdown: "Statistics help us reason about data."
          }
        }
      ],
      prerequisites: []
    }
  ];
}

interface SeedAssessmentOptions {
  module: ReturnType<typeof buildSeedModules>[number];
  tenantId: string;
  createdBy: string;
}

async function createSeedAssessment(
  prisma: PrismaClient,
  options: SeedAssessmentOptions
): Promise<void> {
  const { module, tenantId, createdBy } = options;
  const assessmentId = `${module.id}-diagnostic`;

  await prisma.assessment.create({
    data: {
      id: assessmentId,
      tenantId,
      moduleId: module.id,
      title: `${module.title} Diagnostic`,
      type: "QUIZ",
      status: "PUBLISHED",
      passingScore: 70,
      attemptsAllowed: 3,
      timeLimitMinutes: 20,
      createdBy,
      updatedBy: createdBy,
      objectives: {
        create: module.learningObjectives.map((objective) => ({
          label: objective.label,
          taxonomyLevel: objective.taxonomyLevel
        }))
      },
      items: {
        create: [
          {
            orderIndex: 0,
            itemType: "multiple_choice_single",
            prompt: "Solve for x: 2x + 5 = 15",
            choices: [
              { id: "choice-a", label: "x = 3" },
              { id: "choice-b", label: "x = 5", isCorrect: true },
              { id: "choice-c", label: "x = 7" }
            ],
            points: 10
          },
          {
            orderIndex: 1,
            itemType: "short_answer",
            prompt: "Describe what a variable represents in algebra.",
            rubric: "Explain that it is a symbol representing an unknown value",
            points: 10
          }
        ]
      }
    }
  });
}
