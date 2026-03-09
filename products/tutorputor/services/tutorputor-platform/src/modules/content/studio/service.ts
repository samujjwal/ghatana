/**
 * Content Studio Service
 *
 * Implements core authoring workflows backed by LearningExperience schema
 * and queue-driven background content generation.
 */

import { Queue } from "bullmq";
import type { PrismaClient } from "@ghatana/tutorputor-db";
import type {
  ContentStudioService,
  CreateExperienceRequest,
  RefineExperienceRequest,
  ExperienceOperationResult,
  ExperienceValidationResult,
  LearningExperience,
  LearningClaim,
} from "@ghatana/tutorputor-contracts/v1";

export type { ContentStudioService };

type GradeRange =
  | "k_2"
  | "grade_3_5"
  | "grade_6_8"
  | "grade_9_12"
  | "undergraduate"
  | "graduate"
  | "professional";

type ExperienceStatus =
  | "draft"
  | "validating"
  | "review"
  | "approved"
  | "published"
  | "archived";

export interface ContentStudioConfig {
  openaiApiKey: string;
  model?: string;
}

export type HealthAwareContentStudioService = ContentStudioService & {
  checkHealth: () => Promise<boolean>;
  updateExperience: (id: string, data: any) => Promise<LearningExperience | null>;
  deleteExperience: (id: string) => Promise<void>;
  generateClaims: (id: string, request: any) => Promise<any>;
  generateTasks: (experienceId: string, claimId: string, request: any) => Promise<any>;
  refineContent: (id: string, request: any) => Promise<LearningExperience | null>;
  adaptGrade: (id: string, request: any) => Promise<LearningExperience | null>;
  getValidationHistory: (id: string) => Promise<any[]>;
  publishExperience: (id: string, userId: string) => Promise<LearningExperience | null>;
  unpublishExperience: (id: string, reason?: string) => Promise<LearningExperience | null>;
  archiveExperience: (id: string) => Promise<LearningExperience | null>;
  addClaim: (id: string, claim: any) => Promise<any>;
  updateClaim: (experienceId: string, claimId: string, data: any) => Promise<any>;
  deleteClaim: (experienceId: string, claimId: string) => Promise<void>;
  addTask: (experienceId: string, claimId: string, task: any) => Promise<any>;
  updateTask: (experienceId: string, claimId: string, taskId: string, data: any) => Promise<any>;
  deleteTask: (experienceId: string, claimId: string, taskId: string) => Promise<void>;
  getExperienceAnalytics: (id: string) => Promise<any>;
  getGenerationProgress: (id: string) => Promise<any>;
};

const CONTENT_QUEUE = "content-generation";
const REDIS_URL = process.env.REDIS_URL || "redis://localhost:6379";

type QueueLike = {
  add: (
    name: string,
    data: Record<string, unknown>,
    opts?: Record<string, unknown>,
  ) => Promise<{ id: string | number | null | undefined }>;
};

let queueSingleton: QueueLike | null = null;

function queueConnectionFromUrl(redisUrl: string): {
  host: string;
  port: number;
  password?: string;
  db?: number;
} {
  const parsed = new URL(redisUrl);
  const dbPath = parsed.pathname?.replace("/", "");
  return {
    host: parsed.hostname,
    port: parseInt(parsed.port || "6379", 10),
    password: parsed.password || undefined,
    db: dbPath ? parseInt(dbPath, 10) || 0 : 0,
  };
}

function getQueue(): QueueLike {
  if (!queueSingleton) {
    const disableQueue =
      process.env.CONTENT_QUEUE_DISABLED === "true" ||
      process.env.NODE_ENV === "test";

    if (disableQueue) {
      queueSingleton = {
        async add(_name, _data, opts) {
          const id = typeof opts?.["jobId"] === "string" ? opts["jobId"] : "noop";
          return { id };
        },
      };
    } else {
      queueSingleton = new Queue(CONTENT_QUEUE, {
        connection: queueConnectionFromUrl(REDIS_URL),
      });
    }
  }
  return queueSingleton;
}

function generateSlug(title: string): string {
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

function inferDomain(title: string, description?: string): "MATH" | "SCIENCE" | "TECH" {
  const text = `${title} ${description || ""}`.toLowerCase();
  if (/\b(math|algebra|calculus|geometry|statistics|equation|number)\b/.test(text)) {
    return "MATH";
  }
  if (/\b(physics|chemistry|biology|science|force|energy|cell|molecule)\b/.test(text)) {
    return "SCIENCE";
  }
  return "TECH";
}

function normalizeGradeRange(raw: string | null | undefined): GradeRange {
  if (!raw) return "grade_6_8";
  const normalized = raw.toLowerCase();
  if (normalized === "k_2") return "k_2";
  if (normalized === "grade_3_5") return "grade_3_5";
  if (normalized === "grade_6_8") return "grade_6_8";
  if (normalized === "grade_9_12") return "grade_9_12";
  if (normalized === "undergraduate") return "undergraduate";
  if (normalized === "graduate") return "graduate";
  return "professional";
}

function toGradeEnum(grade: GradeRange): string {
  const mapping: Record<GradeRange, string> = {
    k_2: "K_2",
    grade_3_5: "GRADE_3_5",
    grade_6_8: "GRADE_6_8",
    grade_9_12: "GRADE_9_12",
    undergraduate: "UNDERGRADUATE",
    graduate: "GRADUATE",
    professional: "GRADUATE",
  };
  return mapping[grade] || "GRADE_6_8";
}

function fromPrismaStatus(status: string): ExperienceStatus {
  const map: Record<string, ExperienceStatus> = {
    DRAFT: "draft",
    REVIEW: "review",
    PUBLISHED: "published",
    ARCHIVED: "archived",
  };
  return map[status] || "draft";
}

function toPrismaStatus(status: string): string {
  const map: Record<string, string> = {
    draft: "DRAFT",
    validating: "REVIEW",
    review: "REVIEW",
    approved: "REVIEW",
    published: "PUBLISHED",
    archived: "ARCHIVED",
  };
  return map[status] || "DRAFT";
}

function defaultGradeAdaptation(gradeRange: GradeRange): any {
  const defaults: Record<GradeRange, any> = {
    k_2: {
      gradeRange: "k_2",
      mathLevel: "arithmetic",
      rigorLevel: "conceptual",
      scaffoldingLevel: "high",
      vocabularyComplexity: 2,
      readingLevel: 1,
      prerequisiteConcepts: [],
    },
    grade_3_5: {
      gradeRange: "grade_3_5",
      mathLevel: "arithmetic",
      rigorLevel: "procedural",
      scaffoldingLevel: "high",
      vocabularyComplexity: 4,
      readingLevel: 4,
      prerequisiteConcepts: [],
    },
    grade_6_8: {
      gradeRange: "grade_6_8",
      mathLevel: "arithmetic",
      rigorLevel: "procedural",
      scaffoldingLevel: "medium",
      vocabularyComplexity: 6,
      readingLevel: 7,
      prerequisiteConcepts: [],
    },
    grade_9_12: {
      gradeRange: "grade_9_12",
      mathLevel: "algebra",
      rigorLevel: "analytical",
      scaffoldingLevel: "medium",
      vocabularyComplexity: 8,
      readingLevel: 10,
      prerequisiteConcepts: [],
    },
    undergraduate: {
      gradeRange: "undergraduate",
      mathLevel: "calculus",
      rigorLevel: "analytical",
      scaffoldingLevel: "low",
      vocabularyComplexity: 9,
      readingLevel: 13,
      prerequisiteConcepts: [],
    },
    graduate: {
      gradeRange: "graduate",
      mathLevel: "calculus",
      rigorLevel: "synthesis",
      scaffoldingLevel: "low",
      vocabularyComplexity: 10,
      readingLevel: 16,
      prerequisiteConcepts: [],
    },
    professional: {
      gradeRange: "professional",
      mathLevel: "calculus",
      rigorLevel: "synthesis",
      scaffoldingLevel: "none",
      vocabularyComplexity: 10,
      readingLevel: 16,
      prerequisiteConcepts: [],
    },
  };

  return defaults[gradeRange];
}

function safeJsonArray(value: unknown): any[] {
  return Array.isArray(value) ? value : [];
}

function bloomToContract(value: string): string {
  return value.toLowerCase();
}

function bloomFromInput(value: string | undefined): string {
  if (!value) return "UNDERSTAND";
  const up = value.toUpperCase();
  const valid = ["REMEMBER", "UNDERSTAND", "APPLY", "ANALYZE", "EVALUATE", "CREATE"];
  return valid.includes(up) ? up : "UNDERSTAND";
}

function extractPrimaryGrade(experience: any): GradeRange {
  const grades = safeJsonArray(experience.targetGrades);
  if (grades.length === 0) return "grade_6_8";
  return normalizeGradeRange(String(grades[0]));
}

async function mapExperience(prisma: PrismaClient, experienceId: string): Promise<LearningExperience | null> {
  const exp = await prisma.learningExperience.findUnique({
    where: { id: experienceId },
    include: {
      claims: {
        orderBy: { orderIndex: "asc" },
      },
      evidences: true,
      experienceTasks: true,
    },
  });

  if (!exp) return null;

  const gradeRange = extractPrimaryGrade(exp);
  const gradeAdaptations = safeJsonArray(exp.gradeAdaptations);
  const selectedGradeAdaptation = gradeAdaptations[0] || defaultGradeAdaptation(gradeRange);

  const claims: LearningClaim[] = (exp.claims || []).map((claim: any) => {
    const claimRef = claim.claimRef ?? claim.id;
    const claimId = claim.id ?? claim.claimRef;
    const claimText = claim.text ?? claim.statement ?? "";
    const bloomLevel = bloomToContract(claim.bloomLevel || "UNDERSTAND");

    const evidences = Array.isArray(claim.evidence)
      ? claim.evidence
      : (exp.evidences || []).filter((e: any) => e.claimRef === claimRef);
    const tasks = Array.isArray(claim.tasks)
      ? claim.tasks
      : (exp.experienceTasks || []).filter((task: any) => task.claimRef === claimRef);

    return {
      id: claimId,
      text: claimText,
      bloom: bloomLevel,
      bloomLevel,
      experienceId: exp.id,
      orderIndex: claim.orderIndex ?? 0,
      masteryThreshold: 0.7,
      evidenceRequirements: evidences.map((e: any) => ({
        id: e.id ?? e.evidenceRef,
        claimRef: e.claimRef ?? claimRef,
        type: e.type,
        description: e.description,
        observables: e.observables ?? e.contentDelivery ?? [],
      })),
      tasks: tasks.map((task: any) => ({
        id: task.id ?? task.taskRef,
        type: task.type,
        claimRef: task.claimRef ?? claimRef,
        evidenceRef: task.evidenceRef ?? task.evidenceType ?? null,
        prompt: task.prompt,
      })),
      contentNeeds: claim.contentNeeds || undefined,
    };
  }) as any;

  return {
    id: exp.id,
    tenantId: exp.tenantId,
    slug: exp.conceptId || generateSlug(exp.title),
    title: exp.title,
    description: exp.intentProblem || exp.intentMotivation || "",
    status: fromPrismaStatus(exp.status),
    version: exp.version,
    gradeAdaptation: selectedGradeAdaptation,
    claims,
    estimatedTimeMinutes: exp.estimatedTimeMinutes,
    keywords: [],
    moduleId: exp.moduleId || undefined,
    simulationId: exp.simulationManifestId || undefined,
    authorId: exp.createdBy,
    createdAt: exp.createdAt,
    updatedAt: exp.updatedAt,
  } as any;
}

export function createContentStudioService(
  prisma: PrismaClient,
  _config: ContentStudioConfig,
): HealthAwareContentStudioService {
  const queue = getQueue();

  async function checkHealth(): Promise<boolean> {
    try {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    } catch {
      return false;
    }
  }

  async function createExperience(
    request: CreateExperienceRequest,
  ): Promise<ExperienceOperationResult> {
    try {
      const gradeRange = normalizeGradeRange(request.gradeRange);
      const adaptation = defaultGradeAdaptation(gradeRange);
      const conceptId = generateSlug(request.title);
      const domain = inferDomain(request.title, request.description);

      const experience = await prisma.learningExperience.create({
        data: {
          tenantId: request.tenantId,
          moduleId: request.moduleId || null,
          title: request.title,
          domain,
          conceptId,
          intentProblem: request.description,
          intentMotivation: request.description,
          intentMisconceptions: [],
          targetGrades: [toGradeEnum(gradeRange)],
          gradeAdaptations: [adaptation],
          assessmentConfig: {
            passingThreshold: 0.7,
            minEvidencePerClaim: 1,
            adaptive: true,
          },
          status: "DRAFT",
          version: 1,
          estimatedTimeMinutes: 30,
          createdBy: request.authorId || "system",
          lastEditedBy: request.authorId || "system",
        } as any,
      });

      try {
        await queue.add(
          "generate-claims",
          {
            experienceId: experience.id,
            tenantId: experience.tenantId,
            topic: experience.title,
            title: experience.title,
            domain: experience.domain,
            gradeLevel: toGradeEnum(gradeRange),
            targetGrades: [toGradeEnum(gradeRange)],
            maxClaims: 5,
          },
          {
            jobId: `generate-claims:${experience.id}`,
            attempts: 3,
            backoff: { type: "exponential", delay: 2000 },
            removeOnComplete: 100,
            removeOnFail: 200,
          },
        );
      } catch (queueError) {
        await prisma.learningExperience.delete({ where: { id: experience.id } }).catch(() => undefined);
        throw new Error(
          `Failed to enqueue background claim generation: ${
            queueError instanceof Error ? queueError.message : String(queueError)
          }`,
        );
      }

      const mapped = await mapExperience(prisma, experience.id);
      return {
        success: true,
        experience: mapped || undefined,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  async function getExperience(experienceId: string): Promise<LearningExperience | null> {
    return mapExperience(prisma, experienceId);
  }

  async function listExperiences(filters: {
    tenantId: string;
    status?: ExperienceStatus;
    gradeRange?: GradeRange;
    authorId?: string;
    limit?: number;
    offset?: number;
  }): Promise<{ experiences: LearningExperience[]; total: number }> {
    const where: any = { tenantId: filters.tenantId };

    if (filters.status) {
      where.status = toPrismaStatus(filters.status);
    }
    if (filters.authorId) {
      where.createdBy = filters.authorId;
    }

    const [rows, total] = await Promise.all([
      prisma.learningExperience.findMany({
        where,
        orderBy: { updatedAt: "desc" },
        take: filters.limit || 20,
        skip: filters.offset || 0,
        select: { id: true },
      }),
      prisma.learningExperience.count({ where }),
    ]);

    const experiences: LearningExperience[] = [];
    for (const row of rows) {
      const mapped = await mapExperience(prisma, row.id);
      if (!mapped) continue;
      if (filters.gradeRange && mapped.gradeAdaptation?.gradeRange !== filters.gradeRange) {
        continue;
      }
      experiences.push(mapped);
    }

    return { experiences, total: filters.gradeRange ? experiences.length : total };
  }

  async function updateExperience(id: string, data: any): Promise<LearningExperience | null> {
    const existing = await prisma.learningExperience.findUnique({ where: { id } });
    if (!existing) return null;

    const updateData: any = {
      lastEditedBy: data?.userId || existing.lastEditedBy || existing.createdBy,
    };

    if (typeof data?.title === "string" && data.title.trim()) {
      updateData.title = data.title.trim();
      if (!existing.conceptId) {
        updateData.conceptId = generateSlug(data.title.trim());
      }
    }

    if (typeof data?.description === "string") {
      updateData.intentProblem = data.description;
      updateData.intentMotivation = data.description;
    }

    if (typeof data?.status === "string") {
      updateData.status = toPrismaStatus(data.status);
    }

    if (typeof data?.estimatedTimeMinutes === "number" && data.estimatedTimeMinutes > 0) {
      updateData.estimatedTimeMinutes = data.estimatedTimeMinutes;
    }

    if (typeof data?.gradeRange === "string") {
      const range = normalizeGradeRange(data.gradeRange);
      updateData.targetGrades = [toGradeEnum(range)];
      updateData.gradeAdaptations = [defaultGradeAdaptation(range)];
    }

    await prisma.learningExperience.update({
      where: { id },
      data: updateData,
    });

    return mapExperience(prisma, id);
  }

  async function deleteExperience(id: string): Promise<void> {
    await prisma.learningExperience.delete({ where: { id } });
  }

  async function generateClaims(id: string, request: any): Promise<any> {
    const experience = await prisma.learningExperience.findUnique({ where: { id } });
    if (!experience) {
      throw new Error("Experience not found");
    }

    const primaryGrade = normalizeGradeRange(String(safeJsonArray(experience.targetGrades)[0] || "grade_6_8"));
    const maxClaims =
      typeof request?.maxClaims === "number" && request.maxClaims > 0
        ? request.maxClaims
        : 5;

    const job = await queue.add(
      "generate-claims",
      {
        experienceId: experience.id,
        tenantId: experience.tenantId,
        topic: request?.topic || experience.title,
        title: experience.title,
        domain: experience.domain,
        gradeLevel: toGradeEnum(primaryGrade),
        targetGrades: safeJsonArray(experience.targetGrades),
        maxClaims,
      },
      {
        jobId: `generate-claims:${experience.id}`,
        attempts: 3,
        backoff: { type: "exponential", delay: 2000 },
        removeOnComplete: 100,
        removeOnFail: 200,
      },
    );

    return {
      status: "queued",
      jobId: job.id,
      experienceId: id,
    };
  }

  async function generateTasks(
    experienceId: string,
    claimId: string,
    _request: any,
  ): Promise<any> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });
    if (!claim) {
      return { tasks: [] };
    }

    const tasks = await prisma.experienceTask.findMany({
      where: {
        experienceId,
        claimRef: claim.claimRef,
      },
      orderBy: { orderIndex: "asc" },
    });

    return { tasks };
  }

  async function refineContent(id: string, request: any): Promise<LearningExperience | null> {
    const current = await prisma.learningExperience.findUnique({ where: { id } });
    if (!current) return null;

    const note =
      request?.refinementPrompt ||
      request?.prompt ||
      "Refined by auto pipeline";

    await prisma.learningExperience.update({
      where: { id },
      data: {
        intentMotivation: `${current.intentMotivation}\n\nRefinement: ${note}`.slice(0, 5000),
        version: { increment: 1 },
        lastEditedBy: request?.userId || "auto-refiner",
      },
    });

    return mapExperience(prisma, id);
  }

  async function refineExperience(request: RefineExperienceRequest): Promise<ExperienceOperationResult> {
    const experience = await refineContent(request.experienceId, request);
    if (!experience) {
      return { success: false, error: "Experience not found" };
    }
    return { success: true, experience };
  }

  async function adaptGrade(id: string, request: any): Promise<LearningExperience | null> {
    const target = normalizeGradeRange(String(request?.gradeRange || request?.targetGrade || "grade_6_8"));
    await prisma.learningExperience.update({
      where: { id },
      data: {
        targetGrades: [toGradeEnum(target)],
        gradeAdaptations: [defaultGradeAdaptation(target)],
        version: { increment: 1 },
      } as any,
    });

    return mapExperience(prisma, id);
  }

  async function adaptGradeLevel(experienceId: string, targetGrade: GradeRange): Promise<ExperienceOperationResult> {
    const experience = await adaptGrade(experienceId, { targetGrade });
    if (!experience) {
      return { success: false, error: "Experience not found" };
    }
    return { success: true, experience };
  }

  async function validateExperience(id: string, _request?: any): Promise<ExperienceValidationResult> {
    const experience = await prisma.learningExperience.findUnique({
      where: { id },
      include: {
        claims: true,
        evidences: true,
        experienceTasks: true,
      },
    });

    if (!experience) {
      throw new Error("Experience not found");
    }

    const claimCount = experience.claims.length;
    const evidenceCount = experience.evidences.length;
    const taskCount = experience.experienceTasks.length;

    const completeness = claimCount === 0 ? 20 : Math.min(100, Math.round(((evidenceCount + taskCount) / Math.max(claimCount, 1)) * 30));
    const baseScore = claimCount === 0 ? 35 : 70;
    const score = Math.min(100, baseScore + Math.round(completeness / 3));
    const canPublish = claimCount > 0 && score >= 70;
    const overallStatus = canPublish ? "PASS" : score >= 50 ? "WARN" : "FAIL";

    await prisma.validationRecord.create({
      data: {
        experienceId: id,
        authorityScore: score,
        accuracyScore: score,
        usefulnessScore: score,
        harmlessnessScore: score,
        accessibilityScore: score,
        gradefitScore: score,
        overallStatus,
        issues: canPublish
          ? []
          : [{
              severity: "warning",
              message: "Insufficient generated content for one or more claims",
            }],
        suggestions: canPublish ? [] : ["Generate claims/examples/tasks before publishing"],
      } as any,
    });

    return {
      status: canPublish ? "valid" : score >= 50 ? "warnings" : "invalid",
      canPublish,
      checks: [],
      score,
      pillarScores: {
        educational: score,
        experiential: score,
        safety: score,
        technical: score,
        accessibility: score,
      } as any,
      validatedAt: new Date(),
    };
  }

  async function getValidationHistory(id: string): Promise<any[]> {
    const rows = await prisma.validationRecord.findMany({
      where: { experienceId: id },
      orderBy: { validatedAt: "desc" },
      take: 20,
    });

    return rows;
  }

  async function publishExperience(id: string, userId: string): Promise<LearningExperience | null> {
    await prisma.learningExperience.update({
      where: { id },
      data: {
        status: "PUBLISHED",
        publishedAt: new Date(),
        lastEditedBy: userId || "publisher",
      },
    });

    return mapExperience(prisma, id);
  }

  async function unpublishExperience(id: string, _reason?: string): Promise<LearningExperience | null> {
    await prisma.learningExperience.update({
      where: { id },
      data: {
        status: "REVIEW",
      },
    });
    return mapExperience(prisma, id);
  }

  async function archiveExperience(id: string): Promise<LearningExperience | null> {
    await prisma.learningExperience.update({
      where: { id },
      data: {
        status: "ARCHIVED",
      },
    });
    return mapExperience(prisma, id);
  }

  async function addClaim(id: string, claim: any): Promise<any> {
    const latest = await prisma.learningClaim.findFirst({
      where: { experienceId: id },
      orderBy: { orderIndex: "desc" },
    });

    const orderIndex = (latest?.orderIndex ?? -1) + 1;
    const claimRef = claim?.claimRef || `C${orderIndex + 1}`;

    const created = await prisma.learningClaim.create({
      data: {
        experienceId: id,
        claimRef,
        text: claim?.text || claim?.statement || "New claim",
        bloomLevel: bloomFromInput(claim?.bloomLevel || claim?.bloom),
        orderIndex,
        contentNeeds: claim?.contentNeeds || null,
      } as any,
    });

    return created;
  }

  async function updateClaim(experienceId: string, claimId: string, data: any): Promise<any> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });
    if (!claim) {
      throw new Error("Claim not found");
    }

    return prisma.learningClaim.update({
      where: { id: claim.id },
      data: {
        text: data?.text || data?.statement || claim.text,
        bloomLevel: data?.bloomLevel || data?.bloom ? bloomFromInput(data?.bloomLevel || data?.bloom) : claim.bloomLevel,
        contentNeeds: data?.contentNeeds ?? claim.contentNeeds,
      } as any,
    });
  }

  async function deleteClaim(experienceId: string, claimId: string): Promise<void> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });

    if (!claim) return;

    await prisma.learningClaim.delete({
      where: { id: claim.id },
    });
  }

  async function addTask(experienceId: string, claimId: string, task: any): Promise<any> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });
    if (!claim) {
      throw new Error("Claim not found");
    }

    const latest = await prisma.experienceTask.findFirst({
      where: { experienceId },
      orderBy: { orderIndex: "desc" },
    });
    const orderIndex = (latest?.orderIndex ?? -1) + 1;

    const taskRef = task?.taskRef || `T${orderIndex + 1}`;
    const evidenceRef = task?.evidenceRef || `E${orderIndex + 1}`;

    return prisma.experienceTask.create({
      data: {
        experienceId,
        taskRef,
        type: task?.type || "explanation",
        claimRef: claim.claimRef,
        evidenceRef,
        prompt: task?.prompt || task?.instructions || "",
        orderIndex,
        config: task?.config || {},
      } as any,
    });
  }

  async function updateTask(
    experienceId: string,
    _claimId: string,
    taskId: string,
    data: any,
  ): Promise<any> {
    const task = await prisma.experienceTask.findFirst({
      where: {
        experienceId,
        OR: [{ id: taskId }, { taskRef: taskId }],
      },
    });

    if (!task) {
      throw new Error("Task not found");
    }

    return prisma.experienceTask.update({
      where: { id: task.id },
      data: {
        type: data?.type || task.type,
        prompt: data?.prompt || data?.instructions || task.prompt,
        config: data?.config ?? task.config,
      } as any,
    });
  }

  async function deleteTask(
    experienceId: string,
    _claimId: string,
    taskId: string,
  ): Promise<void> {
    const task = await prisma.experienceTask.findFirst({
      where: {
        experienceId,
        OR: [{ id: taskId }, { taskRef: taskId }],
      },
    });

    if (!task) return;
    await prisma.experienceTask.delete({ where: { id: task.id } });
  }

  async function getExperienceAnalytics(id: string): Promise<any> {
    return prisma.experienceAnalytics.findUnique({
      where: { experienceId: id },
    });
  }

  async function getGenerationProgress(id: string): Promise<any> {
    const exp = await prisma.learningExperience.findUnique({
      where: { id },
      include: {
        claims: {
          include: {
            examples: true,
            simulations: true,
            animations: true,
          },
        },
      },
    });

    if (!exp) {
      throw new Error("Experience not found");
    }

    const totalClaims = exp.claims.length;
    const claimsProcessed = exp.claims.filter((claim: any) => {
      return (
        (claim.examples?.length || 0) > 0 ||
        (claim.simulations?.length || 0) > 0 ||
        (claim.animations?.length || 0) > 0
      );
    }).length;

    const contentCounts = exp.claims.reduce(
      (acc: any, claim: any) => {
        acc.examples += claim.examples?.length || 0;
        acc.simulations += claim.simulations?.length || 0;
        acc.animations += claim.animations?.length || 0;
        return acc;
      },
      { examples: 0, simulations: 0, animations: 0 },
    );

    const isComplete = totalClaims > 0 && claimsProcessed >= totalClaims;
    const percentComplete = totalClaims === 0 ? 0 : Math.round((claimsProcessed / totalClaims) * 100);

    return {
      experienceId: id,
      status: isComplete ? "complete" : totalClaims === 0 ? "queued" : "in_progress",
      totalClaims,
      claimsProcessed,
      percentComplete,
      contentCounts,
      isComplete,
      updatedAt: exp.updatedAt.toISOString(),
    };
  }

  async function getSuggestions(_experienceId: string): Promise<any> {
    return {
      content: [
        "Add at least one evidence-backed task per claim",
        "Increase concrete examples for early-grade learners",
      ],
      confidence: 0.8,
      explanation: "Generated from heuristic analysis",
      tokensUsed: 0,
      processingTimeMs: 0,
    };
  }

  return {
    checkHealth,
    createExperience,
    getExperience,
    listExperiences,
    updateExperience,
    deleteExperience,
    generateClaims,
    generateTasks,
    refineContent,
    refineExperience,
    adaptGrade,
    adaptGradeLevel,
    validateExperience: validateExperience as any,
    getValidationHistory,
    publishExperience: publishExperience as any,
    unpublishExperience,
    archiveExperience,
    addClaim,
    updateClaim,
    deleteClaim,
    addTask,
    updateTask,
    deleteTask,
    getExperienceAnalytics,
    getGenerationProgress,
    getSuggestions,
  } as any;
}
