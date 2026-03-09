import { describe, it, expectTypeOf } from "vitest";
import type {
  ContentService,
  LearningService,
  AIProxyService,
  AssessmentService,
  CMSService,
  AnalyticsService,
  MarketplaceService,
  DashboardSummary,
  TutorResponsePayload,
  Assessment,
  AssessmentSummary,
  AssessmentAttempt,
  AnalyticsSummary,
  MarketplaceListing
} from "@ghatana/tutorputor-contracts/v1";
import type {
  ModuleDetail,
  ModuleSummary,
  Enrollment,
  TenantId,
  UserId,
  ModuleId,
  AssessmentId,
  AssessmentItemId,
  AssessmentAttemptId,
  ModuleDraftInput
} from "@ghatana/tutorputor-contracts/v1/types";

describe("TutorPutor contracts", () => {
  it("allow implementing a ContentService stub", async () => {
    const service: ContentService = {
      async getModuleBySlug(tenantId, slug, userId) {
        expectTypeOf<TenantId>().toEqualTypeOf(tenantId);
        expectTypeOf<string>().toMatchTypeOf(slug);
        expectTypeOf<UserId | undefined>().toMatchTypeOf(userId);
        const module = createModule();
        const enrollment: Enrollment = {
          id: "enr-1" as Enrollment["id"],
          moduleId: module.id,
          userId: "user-1" as UserId,
          progressPercent: 50,
          status: "IN_PROGRESS",
          timeSpentSeconds: 600
        };
        return { module, enrollment };
      },
      async listModules() {
        return { items: [createModuleSummary()], nextCursor: null };
      }
    };

    const { module } = await service.getModuleBySlug(
      "tenant-1" as TenantId,
      "algebra",
      "user-1" as UserId
    );
    expectTypeOf<ModuleDetail>().toMatchTypeOf(module);
  });

  it("allows describing Dashboard responses", async () => {
    const learningService: LearningService = {
      async getDashboard() {
        const summary: DashboardSummary = {
          user: {
            id: "user-1" as UserId,
            email: "maya@example.com",
            displayName: "Maya",
            role: "student"
          },
          currentEnrollments: [],
          recommendedModules: [createModuleSummary()]
        };
        return summary;
      },
      async enrollInModule() {
        return {
          id: "enr-1" as Enrollment["id"],
          status: "IN_PROGRESS",
          userId: "user-1" as UserId,
          moduleId: "mod-1" as ModuleId,
          progressPercent: 0,
          timeSpentSeconds: 0
        };
      },
      async updateProgress(args) {
        return {
          id: args.enrollmentId,
          status: "IN_PROGRESS",
          userId: "user-1" as UserId,
          moduleId: "mod-1" as ModuleId,
          progressPercent: args.progressPercent,
          timeSpentSeconds: args.timeSpentSecondsDelta
        };
      }
    };

    const dashboard = await learningService.getDashboard(
      "tenant-1" as TenantId,
      "user-1" as UserId
    );
    expectTypeOf(dashboard.user.email).toMatchTypeOf<string>();
  });

  it("describes AI proxy responses", async () => {
    const aiService: AIProxyService = {
      async handleTutorQuery() {
        const response: TutorResponsePayload = {
          answer: "Stub answer",
          citations: [
            {
              id: "mod-1",
              label: "Algebra Foundations",
              type: "module"
            }
          ],
          safety: { blocked: false }
        };
        return response;
      }
    };

    const res = await aiService.handleTutorQuery({
      tenantId: "tenant-1" as TenantId,
      userId: "user-1" as UserId,
      moduleId: "mod-1" as ModuleId,
      question: "What is a variable?"
    });
    expectTypeOf(res.answer).toMatchTypeOf<string>();
  });

  it("allows describing Assessment service contracts", async () => {
    const service: AssessmentService = {
      async listAssessments() {
        return { items: [createAssessmentSummary()], nextCursor: null };
      },
      async getAssessment() {
        return createAssessment();
      },
      async generateAssessmentItems() {
        return { items: [createAssessment().items[0]!], model: "ghatana-edu-author-1" };
      },
      async startAttempt() {
        return createAttempt("IN_PROGRESS");
      },
      async submitAttempt() {
        return createAttempt("GRADED");
      }
    };

    const summary = await service.listAssessments({
      tenantId: "tenant-1" as TenantId,
      limit: 10
    });
    expectTypeOf<AssessmentSummary[]>().toMatchTypeOf(summary.items);

    const assessment = await service.getAssessment({
      tenantId: "tenant-1" as TenantId,
      assessmentId: summary.items[0]!.id,
      userId: "user-1" as UserId
    });
    expectTypeOf<Assessment>().toMatchTypeOf(assessment);
  });

  it("allows describing CMS service contracts", async () => {
    const service: CMSService = {
      async listModules() {
        return { items: [createModuleSummary()], nextCursor: null };
      },
      async createModuleDraft(args) {
        expectTypeOf<ModuleDraftInput>().toMatchTypeOf(args.input);
        return createModule();
      },
      async updateModuleDraft() {
        return createModule();
      },
      async publishModule() {
        return createModule();
      }
    };
    const result = await service.listModules({
      tenantId: "tenant-1" as TenantId
    });
    expectTypeOf<ModuleSummary[]>().toMatchTypeOf(result.items);
  });

  it("allows describing Analytics service contracts", async () => {
    const service: AnalyticsService = {
      async recordEvent() {
        return;
      },
      async getSummary() {
        const summary: AnalyticsSummary = {
          tenantId: "tenant-1" as TenantId,
          totalEvents: 2,
          activeLearners: 1,
          eventsByType: {
            module_viewed: 1,
            module_completed: 1,
            assessment_started: 0,
            assessment_completed: 0,
            ai_tutor_message: 0
          },
          moduleCompletions: []
        };
        return summary;
      }
    };
    const response = await service.getSummary({
      tenantId: "tenant-1" as TenantId
    });
    expectTypeOf(response.totalEvents).toMatchTypeOf<number>();
  });

  it("allows describing Marketplace service contracts", async () => {
    const service: MarketplaceService = {
      async listListings() {
        return { items: [createListing()], nextCursor: null };
      },
      async createListing() {
        return createListing();
      },
      async updateListing() {
        return createListing();
      }
    };
    const { items } = await service.listListings({
      tenantId: "tenant-1" as TenantId
    });
    expectTypeOf<MarketplaceListing[]>().toMatchTypeOf(items);
  });
});

function createModuleSummary(): ModuleSummary {
  return {
    id: "mod-1" as ModuleId,
    slug: "algebra-foundations",
    title: "Algebra Foundations",
    domain: "MATH",
    difficulty: "INTRO",
    estimatedTimeMinutes: 240,
    tags: ["algebra"],
    status: "PUBLISHED"
  };
}

function createAssessmentSummary(): AssessmentSummary {
  return {
    id: "asm-1" as AssessmentId,
    moduleId: "mod-1" as ModuleId,
    title: "Algebra Foundations Quiz",
    type: "QUIZ",
    status: "DRAFT",
    version: 1,
    passingScore: 80,
    attemptsAllowed: 2,
    timeLimitMinutes: 30
  };
}

function createAssessment(): Assessment {
  return {
    ...createAssessmentSummary(),
    createdBy: "user-editor" as UserId,
    updatedBy: "user-editor" as UserId,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    objectives: [
      {
        id: "obj-1",
        label: "Solve linear equations",
        taxonomyLevel: "apply"
      }
    ],
    items: [
      {
        id: "item-1" as AssessmentItemId,
        type: "multiple_choice_single",
        prompt: "What is the solution to 2x = 10?",
        choices: [
          { id: "choice-a", label: "x = 2" },
          { id: "choice-b", label: "x = 4" },
          { id: "choice-c", label: "x = 5", isCorrect: true }
        ],
        points: 10
      }
    ]
  };
}

function createAttempt(status: AssessmentAttempt["status"]): AssessmentAttempt {
  return {
    id: "attempt-1" as AssessmentAttemptId,
    assessmentId: "asm-1" as AssessmentId,
    tenantId: "tenant-1" as TenantId,
    userId: "user-1" as UserId,
    status,
    responses: {
      ["item-1" as AssessmentItemId]: {
        type: "multiple_choice",
        selectedChoiceIds: ["choice-c"]
      }
    },
    scorePercent: status === "GRADED" ? 100 : undefined,
    startedAt: new Date().toISOString()
  };
}

function createListing(): MarketplaceListing {
  return {
    id: "listing-1" as MarketplaceListing["id"],
    tenantId: "tenant-1" as TenantId,
    moduleId: "mod-1" as ModuleId,
    creatorId: "user-1" as UserId,
    status: "ACTIVE",
    visibility: "PUBLIC",
    priceCents: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };
}

function createModule(): ModuleDetail {
  return {
    ...createModuleSummary(),
    description: "Learn algebra basics",
    learningObjectives: [
      {
        id: "obj-1",
        label: "Understand variables",
        taxonomyLevel: "understand"
      }
    ],
    contentBlocks: [
      {
        id: "block-1",
        orderIndex: 0,
        blockType: "text",
        payload: { markdown: "Hello" }
      }
    ],
    prerequisites: [],
    version: 1
  };
}

