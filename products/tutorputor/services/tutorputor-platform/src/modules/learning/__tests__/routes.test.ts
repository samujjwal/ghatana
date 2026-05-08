import Fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import learningRoutes from "../routes.js";

/**
 * @doc.type test
 * @doc.purpose Verifies learner profile learning routes delegate correctly and enforce admin-only personalization access
 * @doc.layer product
 * @doc.pattern Test
 */

type LearningRoutesOptions = Parameters<typeof learningRoutes>[1];

type MockPrisma = {
  simulationManifest: {
    findMany: ReturnType<typeof vi.fn>;
  };
  assessmentAttempt: {
    findFirst: ReturnType<typeof vi.fn>;
  };
};

type MockAssessmentService = {
  listAssessments: ReturnType<typeof vi.fn>;
  getAssessment: ReturnType<typeof vi.fn>;
  generateAssessmentItems: ReturnType<typeof vi.fn>;
  startAttempt: ReturnType<typeof vi.fn>;
  submitAttempt: ReturnType<typeof vi.fn>;
};

type MockLearningService = {
  getDashboard: ReturnType<typeof vi.fn>;
  enrollInModule: ReturnType<typeof vi.fn>;
  updateProgress: ReturnType<typeof vi.fn>;
  checkHealth: ReturnType<typeof vi.fn>;
};

type MockAnalyticsService = {
  recordEvent: ReturnType<typeof vi.fn>;
  getSummary: ReturnType<typeof vi.fn>;
  getAdvancedAnalytics: ReturnType<typeof vi.fn>;
  getAtRiskStudents: ReturnType<typeof vi.fn>;
  checkHealth: ReturnType<typeof vi.fn>;
};

type MockSessionAdaptationEngine = {
  processEvent: ReturnType<typeof vi.fn>;
  getCurrentAdaptation: ReturnType<typeof vi.fn>;
};

function getHeaderValue(
  request: FastifyRequest,
  headerName: "x-tenant-id" | "x-user-id" | "x-user-role",
): string | null {
  const headerValue = request.headers[headerName];
  if (!headerValue) {
    return null;
  }

  return Array.isArray(headerValue) ? headerValue[0] ?? null : headerValue;
}

describe("learning routes", () => {
  let app: FastifyInstance;
  let prisma: MockPrisma;
  let learningService: MockLearningService;
  let analyticsService: MockAnalyticsService;
  let sessionAdaptationEngine: MockSessionAdaptationEngine;
  let assessmentService: MockAssessmentService;
  let learnerProfileService: {
    getOrCreateProfile: ReturnType<typeof vi.fn>;
    updatePreferences: ReturnType<typeof vi.fn>;
    updateMastery: ReturnType<typeof vi.fn>;
    recordKnowledgeGap: ReturnType<typeof vi.fn>;
    getRecommendations: ReturnType<typeof vi.fn>;
    getPersonalizationSnapshot: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    prisma = {
      simulationManifest: {
        findMany: vi.fn().mockResolvedValue([]),
      },
      assessmentAttempt: {
        findFirst: vi.fn(),
      },
    };

    learnerProfileService = {
      getOrCreateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      updateMastery: vi.fn(),
      recordKnowledgeGap: vi.fn(),
      getRecommendations: vi.fn(),
      getPersonalizationSnapshot: vi.fn(),
    };

    learningService = {
      getDashboard: vi.fn(),
      enrollInModule: vi.fn(),
      updateProgress: vi.fn(),
      checkHealth: vi.fn(),
    };

    analyticsService = {
      recordEvent: vi.fn(),
      getSummary: vi.fn(),
      getAdvancedAnalytics: vi.fn(),
      getAtRiskStudents: vi.fn(),
      checkHealth: vi.fn(),
    };

    sessionAdaptationEngine = {
      processEvent: vi.fn(),
      getCurrentAdaptation: vi.fn(),
    };

    assessmentService = {
      listAssessments: vi.fn(),
      getAssessment: vi.fn(),
      generateAssessmentItems: vi.fn(),
      startAttempt: vi.fn(),
      submitAttempt: vi.fn(),
    };

    const options: LearningRoutesOptions = {
      learningService:
        learningService as unknown as LearningRoutesOptions["learningService"],
      pathwaysService: {} as LearningRoutesOptions["pathwaysService"],
      assessmentService:
        assessmentService as unknown as LearningRoutesOptions["assessmentService"],
      analyticsService:
        analyticsService as unknown as LearningRoutesOptions["analyticsService"],
      learnerProfileService:
        learnerProfileService as unknown as LearningRoutesOptions["learnerProfileService"],
      contentVariationService:
        {} as LearningRoutesOptions["contentVariationService"],
      sessionAdaptationEngine:
        sessionAdaptationEngine as unknown as LearningRoutesOptions["sessionAdaptationEngine"],
    };

    app = Fastify({
      logger: false,
    });
    app.setValidatorCompiler(() => {
      return (value: unknown) => ({ value });
    });
    app.addHook("onRequest", async (request) => {
      const tenantId = getHeaderValue(request, "x-tenant-id");
      const userId = getHeaderValue(request, "x-user-id");

      if (!tenantId || !userId) {
        return;
      }

      const role = getHeaderValue(request, "x-user-role") ?? "student";
      (
        request as FastifyRequest & {
          user?: {
            tenantId: string;
            sub: string;
            userId: string;
            role: string;
          };
        }
      ).user = {
        tenantId,
        sub: userId,
        userId,
        role,
      };
    });
    app.decorate("prisma", prisma as never);
    await app.register(learningRoutes, options);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("returns learner dashboard data for the authenticated tenant context", async () => {
    learningService.getDashboard.mockResolvedValue({
      user: {
        id: "user-1",
        email: "student@example.com",
        displayName: "Student User",
      },
      currentEnrollments: [],
      recommendedModules: [],
    });

    const response = await app.inject({
      method: "GET",
      url: "/learning/dashboard",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      currentEnrollments: [],
      recommendedModules: [],
    });
    expect(learningService.getDashboard).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
    );
  });

  it("returns 401 when dashboard tenant context is missing", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/learning/dashboard",
      headers: {
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(401);
    expect(response.json()).toMatchObject({
      code: "UNAUTHORIZED",
    });
    expect(learningService.getDashboard).not.toHaveBeenCalled();
  });

  it("forwards learner pacing constraints when updating enrollment progress", async () => {
    learnerProfileService.getPersonalizationSnapshot.mockResolvedValue({
      learnerId: "user-1",
      preferredDifficulty: "MEDIUM",
      preferredModality: "MIXED",
      preferredPacing: "GUIDED",
      adjustedDifficulty: "beginner",
      preferences: [],
      knowledgeGaps: [],
      masterySummary: {
        averageMastery: 0.42,
        conceptCount: 3,
        lowMasteryConcepts: ["fractions"],
      },
      learningStyleScores: {
        visual: 0.25,
        auditory: 0.25,
        kinesthetic: 0.25,
        reading: 0.25,
      },
      sessionPreferences: {
        preferredSessionMinutes: 30,
        notificationFrequency: "daily",
        preferredTimeOfDay: null,
      },
    });
    learningService.updateProgress.mockResolvedValue({
      id: "enr-1",
      progressPercent: 45,
      status: "IN_PROGRESS",
    });

    const response = await app.inject({
      method: "PATCH",
      url: "/enrollments/enr-1/progress",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        progressPercent: 45,
        timeSpentSecondsDelta: 900,
      },
    });

    expect(response.statusCode).toBe(200);
    expect(learningService.updateProgress).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      userId: "user-1",
      enrollmentId: "enr-1",
      progressPercent: 45,
      timeSpentSecondsDelta: 900,
      constraints: {
        preferredPacing: "GUIDED",
        preferredSessionMinutes: 30,
        adjustedDifficulty: "beginner",
      },
    });
  });

  it("maps pacing validation failures on progress updates to 400", async () => {
    learnerProfileService.getPersonalizationSnapshot.mockResolvedValue({
      learnerId: "user-1",
      preferredDifficulty: "MEDIUM",
      preferredModality: "MIXED",
      preferredPacing: "ADAPTIVE",
      adjustedDifficulty: "medium",
      preferences: [],
      knowledgeGaps: [],
      masterySummary: {
        averageMastery: 0.75,
        conceptCount: 4,
        lowMasteryConcepts: [],
      },
      learningStyleScores: {
        visual: 0.25,
        auditory: 0.25,
        kinesthetic: 0.25,
        reading: 0.25,
      },
      sessionPreferences: {
        preferredSessionMinutes: 25,
        notificationFrequency: "daily",
        preferredTimeOfDay: null,
      },
    });
    learningService.updateProgress.mockRejectedValue({
      message:
        "Progress update advances too quickly for the learner pacing profile.",
      statusCode: 400,
      code: "VALIDATION_ERROR",
    });

    const response = await app.inject({
      method: "PATCH",
      url: "/enrollments/enr-1/progress",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        progressPercent: 85,
        timeSpentSecondsDelta: 60,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error:
        "Progress update advances too quickly for the learner pacing profile.",
      code: "VALIDATION_ERROR",
    });
  });

  it("processes session events with tenant-scoped learner context", async () => {
    sessionAdaptationEngine.processEvent.mockResolvedValue({
      sessionId: "session-1",
      assetId: "asset-1",
      adapted: true,
      trigger: "REPEATED_ERRORS",
      reason: "Detected repeated incorrect responses",
      recommendation: "Lower the cognitive load and retry with a simpler worked example.",
      variant: {
        variantId: "variant-1",
        assetId: "asset-1",
        family: "difficulty",
        key: "easy",
        title: "Simplified walkthrough",
        summary: "A simpler path through the concept.",
        blocks: [],
        manifests: [],
        metadata: {
          sourceAssetId: "asset-1",
          generatedAt: "2026-04-01T00:00:00.000Z",
          strategy: "simplify-text",
        },
      },
      observedSignals: {
        recentEvents: 3,
        incorrectStreak: 3,
        hintRate: 0,
        rapidGuessCount: 0,
        inactivityMs: 0,
      },
      createdAt: "2026-04-01T00:00:00.000Z",
    });

    const response = await app.inject({
      method: "POST",
      url: "/sessions/session-1/events",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        assetId: "asset-1",
        eventType: "ANSWER_SUBMITTED",
        correct: false,
        responseLatencyMs: 6400,
      },
    });

    expect(response.statusCode).toBe(200);
    expect(sessionAdaptationEngine.processEvent).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      userId: "user-1",
      sessionId: "session-1",
      assetId: "asset-1",
      eventType: "ANSWER_SUBMITTED",
      correct: false,
      responseLatencyMs: 6400,
    });
    expect(analyticsService.recordEvent).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      event: {
        type: "ai_tutor_message",
        userId: "user-1",
        timestamp: undefined,
        payload: {
          source: "session_adaptation",
          sessionId: "session-1",
          assetId: "asset-1",
          trigger: "REPEATED_ERRORS",
          reason: "Detected repeated incorrect responses",
          recommendation:
            "Lower the cognitive load and retry with a simpler worked example.",
          eventType: "ANSWER_SUBMITTED",
          variant: {
            variantId: "variant-1",
            family: "difficulty",
            key: "easy",
            strategy: "simplify-text",
          },
          observedSignals: {
            recentEvents: 3,
            incorrectStreak: 3,
            hintRate: 0,
            rapidGuessCount: 0,
            inactivityMs: 0,
          },
          adapted: true,
        },
      },
    });
  });

  it("does not emit adaptation analytics when no adaptation occurs", async () => {
    sessionAdaptationEngine.processEvent.mockResolvedValue({
      sessionId: "session-1",
      assetId: "asset-1",
      adapted: false,
      reason: "No adaptation needed",
      observedSignals: {
        recentEvents: 1,
        incorrectStreak: 0,
        hintRate: 0,
        rapidGuessCount: 0,
        inactivityMs: 0,
      },
      createdAt: "2026-04-01T00:00:00.000Z",
    });

    const response = await app.inject({
      method: "POST",
      url: "/sessions/session-1/events",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        assetId: "asset-1",
        eventType: "CHECKPOINT",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(analyticsService.recordEvent).not.toHaveBeenCalled();
  });

  it("loads current adaptation with tenant and learner scope", async () => {
    sessionAdaptationEngine.getCurrentAdaptation.mockResolvedValue({
      sessionId: "session-1",
      assetId: "asset-1",
      adapted: false,
      reason: "No adaptation needed",
      observedSignals: {
        recentEvents: 1,
        incorrectStreak: 0,
        hintRate: 0,
        rapidGuessCount: 0,
        inactivityMs: 0,
      },
      createdAt: "2026-04-01T00:00:00.000Z",
    });

    const response = await app.inject({
      method: "GET",
      url: "/sessions/session-1/adaptation?assetId=asset-1",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(sessionAdaptationEngine.getCurrentAdaptation).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
      "session-1",
      "asset-1",
    );
  });

  it("returns the current learner profile", async () => {
    learnerProfileService.getOrCreateProfile.mockResolvedValue({
      id: "profile-1",
      tenantId: "tenant-1",
      userId: "user-1",
      preferredDifficulty: "MEDIUM",
    });

    const response = await app.inject({
      method: "GET",
      url: "/learning/profile",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      id: "profile-1",
      userId: "user-1",
    });
    expect(learnerProfileService.getOrCreateProfile).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
    );
  });

  it("updates learner preferences with an audit actor", async () => {
    learnerProfileService.updatePreferences.mockResolvedValue({
      id: "profile-1",
      preferredDifficulty: "HARD",
      changedBy: "user",
    });

    const response = await app.inject({
      method: "PATCH",
      url: "/learning/profile/preferences",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        preferredDifficulty: "HARD",
        reason: "Faster practice pace",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(learnerProfileService.updatePreferences).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
      {
        preferredDifficulty: "HARD",
        reason: "Faster practice pace",
        changedBy: "user",
      },
    );
  });

  it("records learner mastery evidence", async () => {
    learnerProfileService.updateMastery.mockResolvedValue({
      id: "mastery-1",
      conceptId: "concept-1",
      confidence: 0.82,
    });

    const response = await app.inject({
      method: "POST",
      url: "/learning/profile/mastery",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        conceptId: "concept-1",
        correct: true,
        confidence: 0.82,
        timeSpentSeconds: 45,
      },
    });

    expect(response.statusCode).toBe(201);
    expect(learnerProfileService.updateMastery).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
      expect.objectContaining({
        conceptId: "concept-1",
        correct: true,
        confidence: 0.82,
      }),
    );
  });

  it("records learner knowledge gaps", async () => {
    learnerProfileService.recordKnowledgeGap.mockResolvedValue({
      id: "gap-1",
      conceptId: "fractions",
      severity: "HIGH",
    });

    const response = await app.inject({
      method: "POST",
      url: "/learning/profile/knowledge-gaps",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        conceptId: "fractions",
        prerequisiteId: "division",
        severity: "HIGH",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(learnerProfileService.recordKnowledgeGap).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
      expect.objectContaining({
        conceptId: "fractions",
        prerequisiteId: "division",
        severity: "HIGH",
      }),
    );
  });

  it("forwards typed recommendation context", async () => {
    learnerProfileService.getRecommendations.mockResolvedValue([
      {
        conceptId: "fractions",
        reason: "recent knowledge gap",
      },
    ]);

    const response = await app.inject({
      method: "GET",
      url: "/learning/profile/recommendations?currentConceptId=fractions&goalConceptId=algebra&availableTimeMinutes=25",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      recommendations: [
        {
          conceptId: "fractions",
        },
      ],
    });
    expect(learnerProfileService.getRecommendations).toHaveBeenCalledWith(
      "tenant-1",
      "user-1",
      {
        currentConceptId: "fractions",
        goalConceptId: "algebra",
        availableTimeMinutes: 25,
      },
    );
  });

  it("rejects personalization access without an admin role", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/learning/learners/learner-1/personalization",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({
      error: "Forbidden",
    });
    expect(
      learnerProfileService.getPersonalizationSnapshot,
    ).not.toHaveBeenCalled();
  });

  it("returns personalization snapshots for admins and forwards topic filters", async () => {
    learnerProfileService.getPersonalizationSnapshot.mockResolvedValue({
      learnerId: "learner-1",
      adjustedDifficulty: "medium",
      topicFocus: "fractions",
    });

    const response = await app.inject({
      method: "GET",
      url: "/learning/learners/learner-1/personalization?topic=fractions",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "admin-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      learnerId: "learner-1",
      topicFocus: "fractions",
    });
    expect(
      learnerProfileService.getPersonalizationSnapshot,
    ).toHaveBeenCalledWith("tenant-1", "learner-1", "fractions");
  });

  it("starts assessment attempts for the current learner", async () => {
    assessmentService.startAttempt.mockResolvedValue({
      id: "attempt-1",
      status: "IN_PROGRESS",
      assessmentId: "assessment-1",
    });

    const response = await app.inject({
      method: "POST",
      url: "/assessments/assessment-1/attempt",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      id: "attempt-1",
      status: "IN_PROGRESS",
    });
    expect(assessmentService.startAttempt).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      assessmentId: "assessment-1",
      userId: "user-1",
    });
  });

  it("lists assessments and forwards tenant-scoped filters", async () => {
    assessmentService.listAssessments.mockResolvedValue({
      items: [
        {
          id: "assessment-1",
          moduleId: "module-1",
          status: "PUBLISHED",
        },
      ],
      nextCursor: "assessment-2",
    });

    const response = await app.inject({
      method: "GET",
      url: "/assessments?moduleId=module-1&status=PUBLISHED&limit=5&cursor=assessment-0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      nextCursor: "assessment-2",
      items: [
        {
          id: "assessment-1",
        },
      ],
    });
    expect(assessmentService.listAssessments).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      moduleId: "module-1",
      status: "PUBLISHED",
      limit: "5",
      cursor: "assessment-0",
    });
  });

  it("returns assessment details for the current tenant and user", async () => {
    assessmentService.getAssessment.mockResolvedValue({
      id: "assessment-1",
      title: "Fractions Quiz",
      status: "PUBLISHED",
    });

    const response = await app.inject({
      method: "GET",
      url: "/assessments/assessment-1",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      id: "assessment-1",
      title: "Fractions Quiz",
    });
    expect(assessmentService.getAssessment).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      assessmentId: "assessment-1",
      userId: "user-1",
    });
  });

  it("returns 404 when an assessment is missing", async () => {
    assessmentService.getAssessment.mockRejectedValue(
      new Error("Assessment not found"),
    );

    const response = await app.inject({
      method: "GET",
      url: "/assessments/missing-assessment",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({
      message: "Assessment not found",
    });
  });

  it("generates assessment items for teachers and applies route defaults", async () => {
    assessmentService.generateAssessmentItems.mockResolvedValue({
      items: [
        {
          id: "draft-item-1",
          type: "multiple_choice",
        },
      ],
    });

    const response = await app.inject({
      method: "POST",
      url: "/assessments/generate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        moduleId: "module-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      items: [
        {
          id: "draft-item-1",
        },
      ],
    });
    expect(assessmentService.generateAssessmentItems).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      userId: "teacher-1",
      moduleId: "module-1",
      count: 5,
      difficulty: "INTERMEDIATE",
      objectiveIds: [],
    });
  });

  it("maps typed generation validation failures without leaking a 500", async () => {
    assessmentService.generateAssessmentItems.mockRejectedValue({
      message: "Module not found for assessment generation.",
      statusCode: 400,
      code: "ASSESSMENT_VALIDATION_ERROR",
    });

    const response = await app.inject({
      method: "POST",
      url: "/assessments/generate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        moduleId: "missing-module",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "Module not found for assessment generation.",
      code: "ASSESSMENT_VALIDATION_ERROR",
    });
  });

  it("submits assessment attempts for grading with confidence-based marking", async () => {
    assessmentService.submitAttempt.mockResolvedValue({
      id: "attempt-1",
      status: "GRADED",
      scorePercent: 80,
      averageConfidence: 0.75,
    });

    const response = await app.inject({
      method: "POST",
      url: "/attempts/attempt-1/submit",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        responses: {
          "item-1": {
            type: "multiple_choice",
            selectedChoiceIds: ["choice-a"],
            confidence: 0.8,
          },
        },
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      id: "attempt-1",
      status: "GRADED",
      scorePercent: 80,
    });
    expect(assessmentService.submitAttempt).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      attemptId: "attempt-1",
      userId: "user-1",
      responses: {
        "item-1": {
          type: "multiple_choice",
          selectedChoiceIds: ["choice-a"],
          confidence: 0.8,
        },
      },
    });
  });

  it("previews simulation-backed assessment items for teachers", async () => {
    prisma.simulationManifest.findMany.mockResolvedValue([
      {
        id: "sim-1",
        title: "Fraction Lab",
        description: "Explore numerator and denominator changes.",
        domain: "math",
        moduleId: "module-1",
        manifest: {
          interactionType: "parameter_exploration",
          steps: [
            {
              actions: [
                { parameterId: "numerator", action: "increase_value" },
              ],
            },
          ],
        },
      },
    ]);

    const response = await app.inject({
      method: "POST",
      url: "/assessments/simulations/preview",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        moduleId: "module-1",
        count: 1,
        difficulty: "INTRO",
        objectiveLabels: ["fraction comparison"],
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      total: 1,
      items: [
        {
          type: "simulation_interaction",
        },
      ],
    });
  });

  it("scores simulation-backed assessment responses", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/assessments/simulations/score",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        item: {
          id: "sim-item-1",
          points: 10,
          metadata: {
            simulationManifestId: "sim-1",
            simulationTitle: "Gas Law Explorer",
            simulationDomain: "CHEMISTRY",
            questionType: "prediction",
            interactionType: "parameter_exploration",
            expectedParameters: ["pressure", "temperature"],
            expectedOutcomeKeywords: [
              "adjust pressure",
              "adjust temperature",
            ],
          },
        },
        response: {
          type: "simulation_interaction",
          trace: {
            interactions: [
              {
                type: "parameter_change",
                parameterId: "pressure",
                predictedOutcome: "volume decreases",
                observedOutcome: "volume decreases",
              },
              {
                type: "parameter_change",
                parameterId: "temperature",
                predictedOutcome: "volume increases",
                observedOutcome: "volume increases",
              },
            ],
            summary:
              "I had to adjust pressure and adjust temperature to see the volume change.",
          },
        },
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      earnedPoints: expect.any(Number),
      feedback: {
        itemId: "sim-item-1",
        scorePercent: expect.any(Number),
        needsReview: false,
      },
    });
  });

  it("returns 404 when simulation insights are requested for another learner", async () => {
    prisma.assessmentAttempt.findFirst.mockResolvedValue(null);

    const response = await app.inject({
      method: "GET",
      url: "/attempts/attempt-404/simulation-insights",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(404);
    expect(response.json()).toMatchObject({
      message: "Attempt not found for this user.",
    });
    expect(prisma.assessmentAttempt.findFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          id: "attempt-404",
          tenantId: "tenant-1",
          userId: "user-1",
        },
      }),
    );
  });

  it("summarizes simulation-backed attempt insights for the current learner", async () => {
    prisma.assessmentAttempt.findFirst.mockResolvedValue({
      id: "attempt-1",
      tenantId: "tenant-1",
      userId: "user-1",
      responses: {
        "item-1": {
          type: "simulation_interaction",
          trace: {
            interactions: [
              {
                type: "parameter_change",
                parameterId: "numerator",
                predictedOutcome: "increase",
                observedOutcome: "increase",
              },
            ],
            durationMs: 1200,
            summary: "Observed increase after changing numerator.",
          },
        },
      },
      feedback: [
        {
          itemId: "item-1",
          scorePercent: 88,
          strengths: ["Captured the key change."],
          improvements: ["Explain the denominator effect too."],
        },
      ],
      assessment: {
        items: [
          {
            id: "item-1",
            itemType: "simulation_interaction",
            metadata: {
              simulationManifestId: "sim-1",
              simulationTitle: "Fraction Lab",
              questionType: "prediction",
              interactionType: "parameter_exploration",
              expectedParameters: ["numerator"],
              expectedOutcomeKeywords: ["increase"],
            },
          },
        ],
      },
    });

    const response = await app.inject({
      method: "GET",
      url: "/attempts/attempt-1/simulation-insights",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      totalSimulationItems: 1,
      completedSimulationItems: 1,
      averageScorePercent: 88,
      insights: [
        {
          itemId: "item-1",
          simulationManifestId: "sim-1",
          simulationTitle: "Fraction Lab",
          scorePercent: 88,
          interactionCount: 1,
          durationMs: 1200,
        },
      ],
    });
  });
});