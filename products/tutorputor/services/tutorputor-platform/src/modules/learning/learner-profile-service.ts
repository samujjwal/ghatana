/**
 * Learner Profile Service
 *
 * Production-grade learner personalization primitives shared by learning routes
 * and content generation agents.
 *
 * @doc.type service
 * @doc.purpose Core service for learner profile management and personalization
 * @doc.layer product
 * @doc.pattern Domain Service
 */

import type {
  TutorPrismaClient,
  Prisma,
  LearnerProfile,
  LearnerMastery,
  KnowledgeGap,
  LearnerDifficultyPreference,
  LearnerModalityPreference,
  LearnerPacingPreference,
  KnowledgeGapSeverity,
  KnowledgeGapDetectionMethod,
} from "@tutorputor/core/db";
import { ValidationError, NotFoundError } from "../../core/errors.js";

const DEFAULT_DIFFICULTY: LearnerDifficultyPreference = "MEDIUM";
const DEFAULT_MODALITY: LearnerModalityPreference = "MIXED";
const DEFAULT_PACING: LearnerPacingPreference = "ADAPTIVE";

const SEVERITY_WEIGHT: Record<KnowledgeGapSeverity, number> = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
  CRITICAL: 4,
};

type MasteryDifficulty = "beginner" | "easy" | "medium" | "hard" | "expert";
type RecommendationType = "review" | "prerequisite" | "next" | "challenge";

export interface CreateLearnerProfileInput {
  tenantId: string;
  userId: string;
  preferredDifficulty?: LearnerDifficultyPreference;
  preferredModality?: LearnerModalityPreference;
  preferredPacing?: LearnerPacingPreference;
  preferredSessionMinutes?: number;
}

export interface UpdatePreferencesInput {
  preferredDifficulty?: LearnerDifficultyPreference;
  preferredModality?: LearnerModalityPreference;
  preferredPacing?: LearnerPacingPreference;
  preferredSessionMinutes?: number;
  notificationFrequency?: string;
  changedBy?: "user" | "system" | "ai";
  reason?: string;
}

export interface MasteryUpdateInput {
  conceptId: string;
  correct: boolean;
  confidence?: number;
  timeSpentSeconds?: number;
  hintsUsed?: number;
  attempts?: number;
  modalityUsed?: Exclude<LearnerModalityPreference, "MIXED">;
  sessionStartedAt?: string;
}

export interface KnowledgeGapInput {
  conceptId: string;
  prerequisiteId: string;
  severity?: KnowledgeGapSeverity;
  detectedBy?: KnowledgeGapDetectionMethod;
  evidence?: Record<string, unknown>;
}

export interface RecommendationContext {
  currentConceptId?: string;
  goalConceptId?: string;
  availableTimeMinutes?: number;
}

export interface LearningRecommendation {
  conceptId: string;
  conceptName: string;
  type: RecommendationType;
  reason: string;
  confidence: number;
  estimatedTimeMinutes: number;
  suggestedModality: string;
}

export interface LearnerPersonalizationSnapshot {
  learnerId: string;
  preferredDifficulty: LearnerDifficultyPreference;
  preferredModality: LearnerModalityPreference;
  preferredPacing: LearnerPacingPreference;
  adjustedDifficulty: MasteryDifficulty;
  preferences: string[];
  knowledgeGaps: string[];
  masterySummary: {
    averageMastery: number;
    conceptCount: number;
    lowMasteryConcepts: string[];
  };
  learningStyleScores: {
    visual: number;
    auditory: number;
    kinesthetic: number;
    reading: number;
  };
  sessionPreferences: {
    preferredSessionMinutes: number;
    notificationFrequency: string;
    preferredTimeOfDay: string | null;
  };
}

interface LearnerProfileAggregate {
  profile: LearnerProfile;
  mastery: LearnerMastery[];
  gaps: KnowledgeGap[];
}

interface BayesianUpdateInput {
  prior: number;
  correct: boolean;
  confidence: number;
}

interface BayesianUpdateResult {
  posterior: number;
  nextReviewDays: number;
}

interface LearnerProfileSignalUpdate {
  avgSessionMinutes: number;
  preferredSessionMinutes: number;
  preferredTimeOfDay: string;
  streakDays: number;
  lastActiveAt: Date;
  visualLearningScore: number;
  auditoryLearningScore: number;
  kinestheticLearningScore: number;
  readingLearningScore: number;
}

interface RecommendationCandidate extends LearningRecommendation {
  score: number;
}

export function calculateBayesianMasteryUpdate(
  input: BayesianUpdateInput,
): BayesianUpdateResult {
  const slip = 0.1;
  const guess = 0.2;
  const transit = 0.05;
  const boundedPrior = clamp(input.prior, 0.01, 0.99);
  const boundedConfidence = clamp(input.confidence, 0.1, 1);

  const posteriorKnown = input.correct
    ? (boundedPrior * (1 - slip)) /
      ((boundedPrior * (1 - slip)) + ((1 - boundedPrior) * guess))
    : (boundedPrior * slip) /
      ((boundedPrior * slip) + ((1 - boundedPrior) * (1 - guess)));

  const transitioned = posteriorKnown + (1 - posteriorKnown) * transit;
  const posterior = clamp(
    (transitioned * (0.6 + boundedConfidence * 0.4)) +
      (boundedPrior * (1 - (0.6 + boundedConfidence * 0.4))),
    0.01,
    0.99,
  );

  const nextReviewDays =
    posterior >= 0.85 ? 14 : posterior >= 0.65 ? 7 : posterior >= 0.4 ? 3 : 1;

  return { posterior, nextReviewDays };
}

export function createLearnerProfileService(prisma: TutorPrismaClient) {
  return {
    async createProfile(input: CreateLearnerProfileInput) {
      await assertUserExists(prisma, input.tenantId, input.userId);

      const existing = await prisma.learnerProfile.findUnique({
        where: { userId: input.userId },
      });

      if (existing) {
        throw new ValidationError(
          `Learner profile already exists for user ${input.userId}`,
        );
      }

      return prisma.learnerProfile.create({
        data: {
          tenantId: input.tenantId,
          userId: input.userId,
          preferredDifficulty: input.preferredDifficulty ?? DEFAULT_DIFFICULTY,
          preferredModality: input.preferredModality ?? DEFAULT_MODALITY,
          preferredPacing: input.preferredPacing ?? DEFAULT_PACING,
          preferredSessionMinutes: clampInteger(
            input.preferredSessionMinutes ?? 30,
            10,
            180,
          ),
        },
      });
    },

    async getOrCreateProfile(tenantId: string, userId: string) {
      const existing = await prisma.learnerProfile.findUnique({
        where: { userId },
      });

      if (existing) {
        ensureTenantAccess(existing.tenantId, tenantId, userId);
        return existing;
      }

      return this.createProfile({ tenantId, userId });
    },

    async getProfileAggregate(
      tenantId: string,
      userId: string,
    ): Promise<LearnerProfileAggregate> {
      const profile = await this.getOrCreateProfile(tenantId, userId);

      const [mastery, gaps] = await prisma.$transaction([
        prisma.learnerMastery.findMany({
          where: { tenantId, profileId: profile.id },
          orderBy: [{ masteryProbability: "asc" }, { updatedAt: "desc" }],
        }),
        prisma.knowledgeGap.findMany({
          where: { tenantId, profileId: profile.id, status: "OPEN" },
          orderBy: [{ severity: "desc" }, { lastDetectedAt: "desc" }],
        }),
      ]);

      return { profile, mastery, gaps };
    },

    async updatePreferences(
      tenantId: string,
      userId: string,
      input: UpdatePreferencesInput,
    ) {
      const profile = await this.getOrCreateProfile(tenantId, userId);
      const changes = buildPreferenceChanges(profile, input);

      if (Object.keys(changes).length === 0) {
        return profile;
      }

      const updated = await prisma.$transaction(async (tx) => {
        const next = await tx.learnerProfile.update({
          where: { id: profile.id },
          data: {
            preferredDifficulty:
              input.preferredDifficulty ?? profile.preferredDifficulty,
            preferredModality:
              input.preferredModality ?? profile.preferredModality,
            preferredPacing: input.preferredPacing ?? profile.preferredPacing,
            preferredSessionMinutes:
              input.preferredSessionMinutes !== undefined
                ? clampInteger(input.preferredSessionMinutes, 10, 180)
                : profile.preferredSessionMinutes,
            notificationFrequency:
              input.notificationFrequency ?? profile.notificationFrequency,
          },
        });

        await tx.preferenceChange.create({
          data: {
            tenantId,
            profileId: profile.id,
            changedBy: input.changedBy ?? "user",
            changes: changes as Prisma.InputJsonValue,
            ...(input.reason ? { reason: input.reason } : {}),
          },
        });

        return next;
      });

      return updated;
    },

    async updateMastery(
      tenantId: string,
      userId: string,
      input: MasteryUpdateInput,
    ) {
      validateConceptId(input.conceptId);

      const profile = await this.getOrCreateProfile(tenantId, userId);
      const existing = await prisma.learnerMastery.findUnique({
        where: {
          profileId_conceptId: {
            profileId: profile.id,
            conceptId: input.conceptId,
          },
        },
      });

      const prior = existing?.masteryProbability ?? 0.2;
      const confidence =
        input.confidence ??
        deriveConfidenceFromAttempt(existing?.attempts ?? 0, input.hintsUsed ?? 0);
      const update = calculateBayesianMasteryUpdate({
        prior,
        correct: input.correct,
        confidence,
      });
      const attemptsDelta = Math.max(1, input.attempts ?? 1);
      const totalAttempts = (existing?.attempts ?? 0) + attemptsDelta;
      const totalHints = (existing?.hintsUsed ?? 0) + Math.max(0, input.hintsUsed ?? 0);
      const totalTime =
        (existing?.totalTimeSeconds ?? 0) + Math.max(0, input.timeSpentSeconds ?? 0);
      const observedAt = input.sessionStartedAt
        ? new Date(input.sessionStartedAt)
        : new Date();
      const profileSignalUpdate = inferLearnerProfileSignalUpdate({
        profile,
        input,
        observedAt,
      });

      return prisma.$transaction(async (tx) => {
        const mastery = await tx.learnerMastery.upsert({
          where: {
            profileId_conceptId: {
              profileId: profile.id,
              conceptId: input.conceptId,
            },
          },
          create: {
            tenantId,
            profileId: profile.id,
            conceptId: input.conceptId,
            masteryProbability: update.posterior,
            confidenceScore: clamp((confidence + totalAttempts / 10) / 2, 0.2, 0.99),
            attempts: totalAttempts,
            correctAttempts: input.correct ? attemptsDelta : 0,
            incorrectAttempts: input.correct ? 0 : attemptsDelta,
            hintsUsed: totalHints,
            totalTimeSeconds: totalTime,
            lastObservedAt: observedAt,
            nextReviewAt: addDays(update.nextReviewDays),
          },
          update: {
            masteryProbability: update.posterior,
            confidenceScore: clamp((confidence + totalAttempts / 10) / 2, 0.2, 0.99),
            attempts: totalAttempts,
            correctAttempts:
              (existing?.correctAttempts ?? 0) + (input.correct ? attemptsDelta : 0),
            incorrectAttempts:
              (existing?.incorrectAttempts ?? 0) + (input.correct ? 0 : attemptsDelta),
            hintsUsed: totalHints,
            totalTimeSeconds: totalTime,
            lastObservedAt: observedAt,
            nextReviewAt: addDays(update.nextReviewDays),
          },
        });

        await tx.learnerProfile.update({
          where: { id: profile.id },
          data: profileSignalUpdate,
        });

        return mastery;
      });
    },

    async recordKnowledgeGap(
      tenantId: string,
      userId: string,
      input: KnowledgeGapInput,
    ) {
      validateConceptId(input.conceptId);
      validateConceptId(input.prerequisiteId);

      const profile = await this.getOrCreateProfile(tenantId, userId);
      const existing = await prisma.knowledgeGap.findFirst({
        where: {
          tenantId,
          profileId: profile.id,
          conceptId: input.conceptId,
          prerequisiteId: input.prerequisiteId,
          status: "OPEN",
        },
      });

      const severity = input.severity ?? "MEDIUM";
      const detectedBy = input.detectedBy ?? "ADAPTIVE_ANALYSIS";

      if (!existing) {
        return prisma.knowledgeGap.create({
          data: {
            tenantId,
            profileId: profile.id,
            conceptId: input.conceptId,
            prerequisiteId: input.prerequisiteId,
            severity,
            detectedBy,
            ...(input.evidence
              ? { evidence: input.evidence as Prisma.InputJsonValue }
              : {}),
          },
        });
      }

      return prisma.knowledgeGap.update({
        where: { id: existing.id },
        data: {
          severity:
            SEVERITY_WEIGHT[severity] > SEVERITY_WEIGHT[existing.severity]
              ? severity
              : existing.severity,
          detectedBy,
          detectionCount: existing.detectionCount + 1,
          lastDetectedAt: new Date(),
          evidence: mergeEvidence(existing.evidence, input.evidence),
        },
      });
    },

    async getRecommendations(
      tenantId: string,
      userId: string,
      context: RecommendationContext = {},
    ): Promise<LearningRecommendation[]> {
      const { profile, mastery, gaps } = await this.getProfileAggregate(
        tenantId,
        userId,
      );

      const recommendationIds = Array.from(
        new Set([
          ...gaps.map((gap) => gap.prerequisiteId),
          ...mastery.slice(0, 3).map((item) => item.conceptId),
          ...(context.goalConceptId ? [context.goalConceptId] : []),
        ]),
      );

      const conceptMap = await loadConceptNames(prisma, recommendationIds);
      const recommendations = buildLearningRecommendations({
        profile,
        mastery,
        gaps,
        conceptMap,
        context,
      });

      return recommendations.slice(0, 5);
    },

    async getPersonalizationSnapshot(
      tenantId: string,
      userId: string,
      topic?: string,
    ): Promise<LearnerPersonalizationSnapshot> {
      const { profile, mastery, gaps } = await this.getProfileAggregate(
        tenantId,
        userId,
      );

      const averageMastery =
        mastery.length > 0
          ? mastery.reduce((total, item) => total + item.masteryProbability, 0) /
            mastery.length
          : 0.5;
      const lowMasteryConcepts = mastery
        .filter((item) => item.masteryProbability < 0.65)
        .slice(0, 5)
        .map((item) => item.conceptId);

      return {
        learnerId: userId,
        preferredDifficulty: profile.preferredDifficulty,
        preferredModality: profile.preferredModality,
        preferredPacing: profile.preferredPacing,
        adjustedDifficulty: inferDifficulty(profile.preferredDifficulty, averageMastery),
        preferences: buildAgentPreferences(profile),
        knowledgeGaps: selectRelevantKnowledgeGaps(gaps, topic),
        masterySummary: {
          averageMastery: round2(averageMastery),
          conceptCount: mastery.length,
          lowMasteryConcepts,
        },
        learningStyleScores: {
          visual: round2(profile.visualLearningScore),
          auditory: round2(profile.auditoryLearningScore),
          kinesthetic: round2(profile.kinestheticLearningScore),
          reading: round2(profile.readingLearningScore),
        },
        sessionPreferences: {
          preferredSessionMinutes: profile.preferredSessionMinutes,
          notificationFrequency: profile.notificationFrequency,
          preferredTimeOfDay: profile.preferredTimeOfDay,
        },
      };
    },
  };
}

export type LearnerProfileService = ReturnType<typeof createLearnerProfileService>;

async function assertUserExists(
  prisma: TutorPrismaClient,
  tenantId: string,
  userId: string,
) {
  const user = await prisma.user.findFirst({
    where: { id: userId, tenantId },
    select: { id: true },
  });

  if (!user) {
    throw new NotFoundError("User", userId);
  }
}

function ensureTenantAccess(resourceTenantId: string, tenantId: string, userId: string) {
  if (resourceTenantId !== tenantId) {
    throw new ValidationError(
      `Learner profile for ${userId} is not accessible from tenant ${tenantId}`,
    );
  }
}

function buildPreferenceChanges(
  profile: LearnerProfile,
  input: UpdatePreferencesInput,
) {
  const changes: Record<string, { from: unknown; to: unknown }> = {};

  addChange(changes, "preferredDifficulty", profile.preferredDifficulty, input.preferredDifficulty);
  addChange(changes, "preferredModality", profile.preferredModality, input.preferredModality);
  addChange(changes, "preferredPacing", profile.preferredPacing, input.preferredPacing);
  addChange(
    changes,
    "preferredSessionMinutes",
    profile.preferredSessionMinutes,
    input.preferredSessionMinutes,
  );
  addChange(
    changes,
    "notificationFrequency",
    profile.notificationFrequency,
    input.notificationFrequency,
  );

  return changes;
}

function addChange(
  changes: Record<string, { from: unknown; to: unknown }>,
  field: string,
  from: unknown,
  to: unknown,
) {
  if (to !== undefined && from !== to) {
    changes[field] = { from, to };
  }
}

function validateConceptId(conceptId: string) {
  if (!conceptId || conceptId.trim().length === 0) {
    throw new ValidationError("Concept ID is required");
  }
}

function deriveConfidenceFromAttempt(previousAttempts: number, hintsUsed: number) {
  const experienceBoost = Math.min(previousAttempts / 10, 0.35);
  const hintPenalty = Math.min(hintsUsed * 0.05, 0.25);
  return clamp(0.55 + experienceBoost - hintPenalty, 0.25, 0.95);
}

function buildAgentPreferences(profile: LearnerProfile): string[] {
  const preferences = new Set<string>();
  const dominantModality = inferDominantModality(profile);

  switch (profile.preferredModality) {
    case "VISUAL":
      preferences.add("visual-learning");
      break;
    case "AUDITORY":
      preferences.add("audio-preference");
      break;
    case "KINESTHETIC":
      preferences.add("hands-on-learning");
      break;
    case "READING":
      preferences.add("text-preference");
      break;
    case "MIXED":
      preferences.add("multi-modal");
      break;
  }

  switch (profile.preferredPacing) {
    case "SELF_PACED":
      preferences.add("self-paced");
      break;
    case "GUIDED":
      preferences.add("guided-instruction");
      break;
    case "ADAPTIVE":
      preferences.add("adaptive-pacing");
      break;
    case "INTENSIVE":
      preferences.add("intensive-session");
      break;
  }

  if (profile.visualLearningScore >= 0.7) preferences.add("strong-visual-learner");
  if (profile.kinestheticLearningScore >= 0.7) preferences.add("strong-kinesthetic-learner");
  if (profile.readingLearningScore >= 0.7) preferences.add("reading-confidence");
  preferences.add(`dominant-modality:${dominantModality}`);
  preferences.add(`study-time:${profile.preferredTimeOfDay ?? "flexible"}`);
  if (profile.streakDays >= 7) preferences.add("consistent-study-habit");
  if (profile.avgSessionMinutes <= 20) preferences.add("short-session-preference");
  if (profile.avgSessionMinutes >= 45) preferences.add("extended-session-tolerance");

  preferences.add("step-by-step-explanations");
  return Array.from(preferences);
}

export function inferDominantModality(
  profile: Pick<
    LearnerProfile,
    | "preferredModality"
    | "visualLearningScore"
    | "auditoryLearningScore"
    | "kinestheticLearningScore"
    | "readingLearningScore"
  >,
): string {
  const scores = [
    { modality: "visual", value: profile.visualLearningScore },
    { modality: "auditory", value: profile.auditoryLearningScore },
    { modality: "kinesthetic", value: profile.kinestheticLearningScore },
    { modality: "reading", value: profile.readingLearningScore },
  ].sort((left, right) => right.value - left.value);

  if (scores[0] && scores[1] && Math.abs(scores[0].value - scores[1].value) < 0.05) {
    return profile.preferredModality.toLowerCase();
  }

  return scores[0]?.modality ?? profile.preferredModality.toLowerCase();
}

export function inferLearnerProfileSignalUpdate(input: {
  profile: Pick<
    LearnerProfile,
    | "avgSessionMinutes"
    | "preferredSessionMinutes"
    | "preferredTimeOfDay"
    | "streakDays"
    | "lastActiveAt"
    | "visualLearningScore"
    | "auditoryLearningScore"
    | "kinestheticLearningScore"
    | "readingLearningScore"
  >;
  input: MasteryUpdateInput;
  observedAt: Date;
}): LearnerProfileSignalUpdate {
  const observedSessionMinutes = clamp(
    (input.input.timeSpentSeconds ?? input.profile.preferredSessionMinutes * 60) / 60,
    5,
    180,
  );
  const avgSessionMinutes = round2(
    input.profile.avgSessionMinutes * 0.7 + observedSessionMinutes * 0.3,
  );
  const preferredSessionMinutes = roundToFive(
    clamp(avgSessionMinutes, 10, 180),
  );
  const timeOfDay = inferTimeOfDayBucket(input.observedAt);
  const streakDays = inferStreakDays(
    input.profile.lastActiveAt,
    input.profile.streakDays,
    input.observedAt,
  );
  const modalityScores = inferLearningStyleScores({
    visualLearningScore: input.profile.visualLearningScore,
    auditoryLearningScore: input.profile.auditoryLearningScore,
    kinestheticLearningScore: input.profile.kinestheticLearningScore,
    readingLearningScore: input.profile.readingLearningScore,
    ...(input.input.modalityUsed
      ? { modalityUsed: input.input.modalityUsed }
      : {}),
    hintsUsed: input.input.hintsUsed ?? 0,
    attempts: input.input.attempts ?? 1,
    timeSpentSeconds: input.input.timeSpentSeconds ?? 0,
    confidence: input.input.confidence ?? 0.6,
  });

  return {
    avgSessionMinutes,
    preferredSessionMinutes,
    preferredTimeOfDay:
      input.profile.preferredTimeOfDay === timeOfDay
        ? input.profile.preferredTimeOfDay
        : timeOfDay,
    streakDays,
    lastActiveAt: input.observedAt,
    ...modalityScores,
  };
}

export function inferLearningStyleScores(input: {
  visualLearningScore: number;
  auditoryLearningScore: number;
  kinestheticLearningScore: number;
  readingLearningScore: number;
  modalityUsed?: Exclude<LearnerModalityPreference, "MIXED">;
  hintsUsed: number;
  attempts: number;
  timeSpentSeconds: number;
  confidence: number;
}) {
  const scores = {
    visualLearningScore: input.visualLearningScore,
    auditoryLearningScore: input.auditoryLearningScore,
    kinestheticLearningScore: input.kinestheticLearningScore,
    readingLearningScore: input.readingLearningScore,
  };

  if (input.modalityUsed) {
    const directBoost = 0.12 + clamp(input.confidence, 0.1, 1) * 0.08;
    switch (input.modalityUsed) {
      case "VISUAL":
        scores.visualLearningScore += directBoost;
        break;
      case "AUDITORY":
        scores.auditoryLearningScore += directBoost;
        break;
      case "KINESTHETIC":
        scores.kinestheticLearningScore += directBoost;
        break;
      case "READING":
        scores.readingLearningScore += directBoost;
        break;
    }
  }

  if (input.attempts >= 2) {
    scores.kinestheticLearningScore += 0.05;
  }
  if (input.hintsUsed >= 2) {
    scores.visualLearningScore += 0.04;
    scores.readingLearningScore += 0.03;
  }
  if (input.timeSpentSeconds >= 900) {
    scores.readingLearningScore += 0.04;
  }
  if (input.timeSpentSeconds > 0 && input.timeSpentSeconds <= 300 && input.confidence >= 0.75) {
    scores.visualLearningScore += 0.03;
  }

  return normalizeLearningStyleScores(scores);
}

export function buildLearningRecommendations(input: {
  profile: LearnerProfile;
  mastery: LearnerMastery[];
  gaps: KnowledgeGap[];
  conceptMap: Map<string, string>;
  context?: RecommendationContext;
}): LearningRecommendation[] {
  const dominantModality = inferDominantModality(input.profile);
  const timeBudget = input.context?.availableTimeMinutes ?? input.profile.preferredSessionMinutes;
  const candidates: RecommendationCandidate[] = [];

  for (const gap of input.gaps.slice(0, 4)) {
    const estimatedTimeMinutes = estimateRecommendationTime(
      "prerequisite",
      timeBudget,
    );
    const score =
      mapGapSeverityToConfidence(gap.severity) * 0.5 +
      scoreConceptRelevance(gap.conceptId, input.context) * 0.2 +
      scoreConceptRelevance(gap.prerequisiteId, input.context) * 0.2 +
      scoreTimeFit(estimatedTimeMinutes, timeBudget) * 0.1;

    candidates.push({
      conceptId: gap.prerequisiteId,
      conceptName: input.conceptMap.get(gap.prerequisiteId) ?? gap.prerequisiteId,
      type: "prerequisite",
      reason: `Prerequisite gap detected for ${gap.conceptId} with ${gap.severity.toLowerCase()} urgency`,
      confidence: mapGapSeverityToConfidence(gap.severity),
      estimatedTimeMinutes,
      suggestedModality: dominantModality,
      score,
    });
  }

  for (const item of input.mastery
    .filter((entry) => entry.masteryProbability < 0.7)
    .slice(0, 4)) {
    const estimatedTimeMinutes = estimateRecommendationTime("review", timeBudget);
    const reviewUrgency = scoreReviewUrgency(item.nextReviewAt);
    const score =
      clamp(1 - item.masteryProbability, 0.35, 0.98) * 0.45 +
      reviewUrgency * 0.25 +
      scoreConceptRelevance(item.conceptId, input.context) * 0.2 +
      scoreTimeFit(estimatedTimeMinutes, timeBudget) * 0.1;

    candidates.push({
      conceptId: item.conceptId,
      conceptName: input.conceptMap.get(item.conceptId) ?? item.conceptId,
      type: "review",
      reason: `Mastery is ${(item.masteryProbability * 100).toFixed(0)}% and review is ${describeReviewUrgency(item.nextReviewAt)}`,
      confidence: clamp(1 - item.masteryProbability, 0.35, 0.95),
      estimatedTimeMinutes,
      suggestedModality: dominantModality,
      score,
    });
  }

  if (
    input.context?.goalConceptId &&
    !candidates.some((item) => item.conceptId === input.context?.goalConceptId)
  ) {
    const estimatedTimeMinutes = estimateRecommendationTime("next", timeBudget);
    candidates.push({
      conceptId: input.context.goalConceptId,
      conceptName:
        input.conceptMap.get(input.context.goalConceptId) ??
        input.context.goalConceptId,
      type: "next",
      reason: "Matches the learner's current stated goal",
      confidence: 0.68,
      estimatedTimeMinutes,
      suggestedModality: dominantModality,
      score:
        0.45 +
        scoreConceptRelevance(input.context.goalConceptId, input.context) * 0.35 +
        scoreTimeFit(estimatedTimeMinutes, timeBudget) * 0.2,
    });
  }

  const challengeCandidate = input.mastery
    .filter((item) => item.masteryProbability >= 0.85)
    .sort((left, right) => right.masteryProbability - left.masteryProbability)[0];

  const hasUrgentRemediation =
    input.gaps.some(
      (gap) => gap.severity === "HIGH" || gap.severity === "CRITICAL",
    ) ||
    input.mastery.some((item) => item.masteryProbability < 0.6);

  if (challengeCandidate && timeBudget >= 20 && !hasUrgentRemediation) {
    candidates.push({
      conceptId: challengeCandidate.conceptId,
      conceptName:
        input.conceptMap.get(challengeCandidate.conceptId) ??
        challengeCandidate.conceptId,
      type: "challenge",
      reason: "Strong mastery suggests readiness for a higher-difficulty challenge",
      confidence: clamp(challengeCandidate.masteryProbability, 0.55, 0.95),
      estimatedTimeMinutes: Math.min(30, Math.max(20, Math.round(timeBudget * 0.8))),
      suggestedModality: dominantModality,
      score: challengeCandidate.masteryProbability * 0.7 + 0.2,
    });
  }

  const deduped = new Map<string, RecommendationCandidate>();
  for (const candidate of candidates) {
    const existing = deduped.get(candidate.conceptId);
    if (!existing || existing.score < candidate.score) {
      deduped.set(candidate.conceptId, candidate);
    }
  }

  return Array.from(deduped.values())
    .sort((left, right) => right.score - left.score)
    .map(({ score: _score, ...recommendation }) => recommendation);
}

function inferDifficulty(
  preferredDifficulty: LearnerDifficultyPreference,
  averageMastery: number,
): MasteryDifficulty {
  if (averageMastery <= 0.25) return "beginner";
  if (averageMastery <= 0.45) return "easy";
  if (averageMastery <= 0.7) return preferredDifficulty.toLowerCase() as MasteryDifficulty;
  if (averageMastery <= 0.85) return "hard";
  return preferredDifficulty === "EXPERT" ? "expert" : "hard";
}

function selectRelevantKnowledgeGaps(gaps: KnowledgeGap[], topic?: string) {
  const normalizedTopic = topic?.trim().toLowerCase();
  const relevant = normalizedTopic
    ? gaps.filter(
        (gap) =>
          gap.conceptId.toLowerCase().includes(normalizedTopic) ||
          gap.prerequisiteId.toLowerCase().includes(normalizedTopic),
      )
    : gaps;

  return relevant.slice(0, 5).map((gap) => gap.prerequisiteId);
}

function mapGapSeverityToConfidence(severity: KnowledgeGapSeverity) {
  switch (severity) {
    case "LOW":
      return 0.45;
    case "MEDIUM":
      return 0.6;
    case "HIGH":
      return 0.8;
    case "CRITICAL":
      return 0.95;
  }
}

function normalizeLearningStyleScores(input: {
  visualLearningScore: number;
  auditoryLearningScore: number;
  kinestheticLearningScore: number;
  readingLearningScore: number;
}) {
  const bounded = {
    visualLearningScore: clamp(input.visualLearningScore, 0.05, 1),
    auditoryLearningScore: clamp(input.auditoryLearningScore, 0.05, 1),
    kinestheticLearningScore: clamp(input.kinestheticLearningScore, 0.05, 1),
    readingLearningScore: clamp(input.readingLearningScore, 0.05, 1),
  };
  const total =
    bounded.visualLearningScore +
    bounded.auditoryLearningScore +
    bounded.kinestheticLearningScore +
    bounded.readingLearningScore;

  return {
    visualLearningScore: round2(bounded.visualLearningScore / total),
    auditoryLearningScore: round2(bounded.auditoryLearningScore / total),
    kinestheticLearningScore: round2(bounded.kinestheticLearningScore / total),
    readingLearningScore: round2(bounded.readingLearningScore / total),
  };
}

function inferTimeOfDayBucket(observedAt: Date) {
  const hour = observedAt.getUTCHours();
  if (hour < 6) return "late-night";
  if (hour < 12) return "morning";
  if (hour < 17) return "afternoon";
  if (hour < 22) return "evening";
  return "night";
}

function inferStreakDays(
  lastActiveAt: Date | null,
  streakDays: number,
  observedAt: Date,
) {
  if (!lastActiveAt) {
    return 1;
  }

  const previous = stripTime(lastActiveAt).getTime();
  const current = stripTime(observedAt).getTime();
  const deltaDays = Math.round((current - previous) / 86_400_000);

  if (deltaDays <= 0) {
    return Math.max(1, streakDays);
  }
  if (deltaDays === 1) {
    return streakDays + 1;
  }
  return 1;
}

function stripTime(date: Date) {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function roundToFive(value: number) {
  return Math.round(value / 5) * 5;
}

function estimateRecommendationTime(
  type: RecommendationType,
  timeBudget: number,
) {
  const baseline =
    type === "prerequisite" ? 20 : type === "challenge" ? 25 : type === "next" ? 18 : 15;
  return Math.min(Math.max(10, baseline), Math.max(10, Math.round(timeBudget)));
}

function scoreTimeFit(estimatedTimeMinutes: number, timeBudget: number) {
  if (timeBudget <= 0) return 0.5;
  const delta = Math.abs(estimatedTimeMinutes - timeBudget);
  return clamp(1 - delta / Math.max(timeBudget, 10), 0.2, 1);
}

function scoreConceptRelevance(
  conceptId: string,
  context: RecommendationContext | undefined,
) {
  let score = 0;
  if (context?.currentConceptId && conceptId === context.currentConceptId) {
    score += 0.35;
  }
  if (context?.goalConceptId && conceptId === context.goalConceptId) {
    score += 0.45;
  }
  return clamp(score, 0, 1);
}

function scoreReviewUrgency(nextReviewAt: Date | null) {
  if (!nextReviewAt) {
    return 0.5;
  }

  const deltaMs = nextReviewAt.getTime() - Date.now();
  const deltaDays = deltaMs / 86_400_000;
  if (deltaDays <= 0) return 1;
  if (deltaDays <= 1) return 0.9;
  if (deltaDays <= 3) return 0.75;
  if (deltaDays <= 7) return 0.6;
  return 0.35;
}

function describeReviewUrgency(nextReviewAt: Date | null) {
  if (!nextReviewAt) return "due soon";
  const deltaMs = nextReviewAt.getTime() - Date.now();
  const deltaDays = deltaMs / 86_400_000;
  if (deltaDays <= 0) return "overdue";
  if (deltaDays <= 1) return "due today";
  if (deltaDays <= 3) return "due in the next few days";
  if (deltaDays <= 7) return "upcoming this week";
  return "scheduled later";
}

async function loadConceptNames(
  prisma: TutorPrismaClient,
  conceptIds: string[],
): Promise<Map<string, string>> {
  if (conceptIds.length === 0) {
    return new Map<string, string>();
  }

  const concepts = await prisma.domainAuthorConcept.findMany({
    where: { id: { in: conceptIds } },
    select: { id: true, name: true },
  });

  return new Map(concepts.map((concept) => [concept.id, concept.name]));
}

function mergeEvidence(
  currentEvidence: unknown,
  incoming: Record<string, unknown> | undefined,
): Prisma.InputJsonValue {
  const base: Prisma.JsonObject =
    typeof currentEvidence === "object" &&
    currentEvidence !== null &&
    !Array.isArray(currentEvidence)
      ? (currentEvidence as Prisma.JsonObject)
      : {};

  return incoming
    ? ({ ...base, ...incoming } as Prisma.InputJsonValue)
    : (base as Prisma.InputJsonValue);
}

function addDays(days: number) {
  const next = new Date();
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function clampInteger(value: number, min: number, max: number) {
  return Math.round(clamp(value, min, max));
}

function round2(value: number) {
  return Math.round(value * 100) / 100;
}
