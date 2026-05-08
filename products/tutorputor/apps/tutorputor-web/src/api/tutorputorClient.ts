import type {
  HybridSearchResponse,
  NextStepSuggestion,
  RelatedAssetsResponse,
  TrackBatchEventsInput,
  TrackExplorerEventInput,
} from "@tutorputor/contracts/v1/content-studio";
import {
  buildAITutorGroundingPayload,
  type AITutorGroundingPayload,
} from "./aiTutorGrounding";
import {
  getStandardHeaders,
  handleResponse,
  standardRequest,
} from "./sharedApiClient";
import { readAccessToken } from "@tutorputor/ui";

// Using native fetch instead of axios due to monorepo aliasing

// Local type definitions
type ModuleId = string;
type EnrollmentId = string;
type EnrollmentStatus = "active" | "completed" | "paused" | "expired";
type ThreadStatus = "OPEN" | "RESOLVED" | "CLOSED";

export interface UserInfo {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
}

// Dashboard types matching what the pages expect
export interface DashboardEnrollment {
  id: string;
  moduleId: string;
  moduleSlug?: string;
  moduleTitle?: string;
  status: EnrollmentStatus;
  progress: number;
  progressPercent: number;
  lastAccessedAt?: string;
  timeSpentSeconds: number;
}

export interface DashboardModuleSummary {
  id: string;
  title: string;
  slug: string;
  description?: string;
  tags: string[];
  estimatedMinutes?: number;
  estimatedTimeMinutes?: number;
  difficulty?: string;
  domain?: string;
  progressPercent?: number;
}

export interface DashboardSummary {
  user: UserInfo;
  currentEnrollments: DashboardEnrollment[];
  recommendedModules: DashboardModuleSummary[];
  stats?: {
    totalEnrollments: number;
    completedModules: number;
    averageProgress: number;
  };
}

export interface LearnerInsights {
  engagementScore: number;
  tier: "high" | "medium" | "low";
  headline: string;
  tips: string[];
  showTeacherCta: boolean;
  computedAt: string;
}

export interface ModuleSummary {
  id: string;
  title: string;
  slug: string;
  description: string;
  thumbnailUrl?: string;
  estimatedMinutes: number;
  difficulty: "beginner" | "intermediate" | "advanced";
  tags?: string[];
}

import type { ContentBlockType } from "../pages/cms/types";

export interface AssessmentChoice {
  id: string;
  text: string;
}

export interface AssessmentItem {
  id: string;
  stem: string;
  type: string;
  choices?: AssessmentChoice[];
}

export interface Assessment {
  id: string;
  title: string;
  description?: string;
  timeLimitMinutes?: number;
  items: AssessmentItem[];
}

export interface AttemptResponse {
  id: string;
}

export interface LearningObjective {
  id: string;
  label: string;
  taxonomyLevel: string;
}

export interface ContentBlock {
  id: string;
  blockType: ContentBlockType;
  orderIndex: number;
  payload: unknown;
  schemaVersion: string;
}

export interface ModuleDetail {
  id: string;
  title: string;
  slug: string;
  description: string;
  domain?: string;
  difficulty: string;
  estimatedTimeMinutes: number;
  learningObjectives: LearningObjective[];
  contentBlocks: ContentBlock[];
  authorId?: string;
  publishedAt?: string;
}

export interface Enrollment {
  id: string;
  moduleId: string;
  moduleSlug?: string;
  moduleTitle?: string;
  userId: string;
  status: EnrollmentStatus;
  progressPercent: number;
  currentSectionId?: string;
  timeSpentSeconds: number;
  enrolledAt: string;
  lastAccessedAt?: string;
  completedAt?: string;
}

export interface TutorResponsePayload {
  answer: string;
  confidence: number;
  sources?: { title: string; url: string }[];
  followUpQuestions?: string[];
}

export interface PathNode {
  id: string;
  title: string;
  type: string;
  estimatedMinutes: number;
  order: number;
}

export interface LearningPath {
  id: string;
  title: string;
  description: string;
  nodes?: PathNode[];
}

export interface NodeProgress {
  nodeId: string;
  status: "not_started" | "in_progress" | "completed";
  completedAt?: string;
}

export interface LearningPathEnrollment {
  id: string;
  userId: string;
  pathId: string;
  path?: LearningPath;
  status: "active" | "completed" | "paused";
  currentNodeId?: string;
  nodeProgress?: NodeProgress[];
  enrolledAt: string;
  completedAt?: string;
}

export interface Classroom {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  code: string;
  teacherId: string;
  createdAt: string;
  studentCount?: number;
  enrolledModuleIds?: string[];
}

export interface StudentProgress {
  studentId: string;
  studentName: string;
  overallProgress: number;
  averageScore?: number;
  lastActiveAt?: string;
  completedModules?: number;
  totalModules?: number;
}

// ClassroomProgress kept for future use with aggregate classroom data
// interface ClassroomProgress {
//     classroomId: string;
//     totalStudents: number;
//     averageCompletion: number;
//     studentProgress: StudentProgress[];
// }

export interface Post {
  id: string;
  threadId: string;
  authorId: string;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt?: string;
  isAnswer?: boolean;
  voteCount?: number;
}

export interface Thread {
  id: string;
  tenantId: string;
  moduleId?: string;
  title: string;
  status: ThreadStatus;
  authorId: string;
  authorName?: string;
  content: string;
  posts: Post[];
  createdAt: string;
  resolvedAt?: string;
  replyCount?: number;
}

type AchievementCategory =
  | "learning"
  | "streak"
  | "collaboration"
  | "mastery"
  | "special";

export interface Badge {
  id: string;
  name: string;
  icon: string;
  description?: string;
  rarity: "common" | "rare" | "epic" | "legendary";
}

export interface Achievement {
  id: string;
  badge: Badge;
  earnedAt: string;
  category: AchievementCategory;
}

export interface MarketplaceListingSummary {
  id: string;
  moduleId: string;
  moduleTitle?: string;
  moduleSlug?: string;
  description?: string;
  priceCents: number;
  visibility: string;
  status?: string;
}

export interface MarketplaceCheckoutSession {
  id: string;
  listingId: string;
  amountCents: number;
  status: string;
  paymentUrl?: string;
}

export interface MarketplacePurchase {
  id: string;
  moduleId: string;
  listingId: string;
  amountCents: number;
  purchasedAt?: string;
}

export interface ApiError {
  message: string;
  statusCode: number;
}

export class TutorPutorApiClient {
  private baseURL: string;

  constructor(baseURL: string = "/api") {
    this.baseURL = baseURL;
  }

  private getToken(): string | null {
    return readAccessToken();
  }

  private async request<T>(url: string, options?: RequestInit): Promise<T> {
    const token = this.getToken();
    const headers = getStandardHeaders(token);

    const response = await fetch(`${this.baseURL}${url}`, {
      ...options,
      headers: {
        ...headers,
        ...options?.headers,
      },
    });

    return handleResponse<T>(response);
  }

  async getDashboard(): Promise<DashboardSummary> {
    return await this.request<DashboardSummary>("/v1/learning/dashboard");
  }

  async getMyInsights(): Promise<LearnerInsights> {
    return await this.request<LearnerInsights>("/v1/learning/my-insights");
  }

  async listModules(domain?: string): Promise<{
    items: ModuleSummary[];
    nextCursor?: string | null;
  }> {
    const url = domain ? `/v1/modules?domain=${domain}` : "/v1/modules";
    return await this.request<{
      items: ModuleSummary[];
      nextCursor?: string | null;
    }>(url);
  }

  async getModuleBySlug(slug: string): Promise<{
    module: ModuleDetail;
    userEnrollment: Enrollment | null;
  }> {
    return await this.request<{
      module: ModuleDetail;
      userEnrollment: Enrollment | null;
    }>(`/v1/modules/${slug}`);
  }

  async enrollInModule(
    moduleId: ModuleId,
  ): Promise<{ enrollment: Enrollment }> {
    return await this.request<{ enrollment: Enrollment }>("/v1/learning/enrollments", {
      method: "POST",
      body: JSON.stringify({ moduleId }),
    });
  }

  async updateProgress(
    enrollmentId: EnrollmentId,
    progressPercent: number,
    timeSpentSecondsDelta: number,
  ): Promise<{ enrollment: Enrollment }> {
    return await this.request<{ enrollment: Enrollment }>(
      `/v1/learning/enrollments/${enrollmentId}/progress`,
      {
        method: "PATCH",
        body: JSON.stringify({ progressPercent, timeSpentSecondsDelta }),
      },
    );
  }

  async queryTutor(
    question: string,
    grounding?: ModuleId | (Partial<AITutorGroundingPayload> & { moduleId?: ModuleId }),
  ): Promise<{ response: TutorResponsePayload }> {
    const grounded = buildAITutorGroundingPayload(
      typeof grounding === "string" ? { moduleId: grounding } : grounding,
    );
    return await this.request<{
      response: TutorResponsePayload;
    }>("/v1/ai/tutor/query", {
      method: "POST",
      body: JSON.stringify({ question, ...grounded }),
    });
  }

  async generateQuestions(
    moduleId: ModuleId,
    count: number = 5,
    difficulty: "easy" | "medium" | "hard" = "medium",
  ): Promise<{
    questions: Array<{
      question: string;
      options?: string[];
      correctAnswer: string;
      explanation: string;
    }>;
  }> {
    return await this.request<{
      questions: Array<{
        question: string;
        options?: string[];
        correctAnswer: string;
        explanation: string;
      }>;
    }>("/v1/ai/generate-questions", {
      method: "POST",
      body: JSON.stringify({ moduleId, count, difficulty }),
    });
  }

  // ========== Learning Pathways (Adaptive Learning Model) ==========

  async generatePathway(
    goal: string,
    constraints?: { maxModules?: number; maxDurationMinutes?: number },
  ): Promise<LearningPathRecommendation> {
    return await this.request<LearningPathRecommendation>("/v1/learning/pathways/generate", {
      method: "POST",
      body: JSON.stringify({ goal, ...constraints }),
    });
  }

  async createPathway(
    title: string,
    goal: string,
    moduleIds: string[],
  ): Promise<LearningPath> {
    return await this.request<LearningPath>("/v1/learning/pathways", {
      method: "POST",
      body: JSON.stringify({ title, goal, moduleIds }),
    });
  }

  async getActivePathway(): Promise<LearningPath | null> {
    return await this.request<LearningPath>("/v1/learning/pathways/active");
  }

  async advancePathway(completedModuleId: string): Promise<LearningPath> {
    return await this.request<LearningPath>("/v1/learning/pathways/advance", {
      method: "POST",
      body: JSON.stringify({ completedModuleId }),
    });
  }

  // ========== Teacher/Classroom ==========

  async listClassrooms(): Promise<{ classrooms: Classroom[] }> {
    return await this.request<{ classrooms: Classroom[] }>(
      "/v1/teacher/classrooms",
    );
  }

  async createClassroom(data: {
    name: string;
    description?: string;
  }): Promise<Classroom> {
    return await this.request<Classroom>("/v1/teacher/classrooms", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async getClassroom(classroomId: string): Promise<Classroom> {
    return await this.request<Classroom>(
      `/v1/teacher/classrooms/${classroomId}`,
    );
  }

  async getClassroomProgress(
    classroomId: string,
  ): Promise<{ progress: StudentProgress[] }> {
    return await this.request<{ progress: StudentProgress[] }>(
      `/v1/teacher/classrooms/${classroomId}/progress`,
    );
  }

  async addStudentToClassroom(
    classroomId: string,
    studentId: string,
  ): Promise<void> {
    await this.request(`/v1/teacher/classrooms/${classroomId}/students`, {
      method: "POST",
      body: JSON.stringify({ studentId }),
    });
  }

  // ========== Collaboration ==========

  async listThreads(moduleId?: string): Promise<{ threads: Thread[] }> {
    const url = moduleId
      ? `/v1/collaboration/threads?moduleId=${moduleId}`
      : "/v1/collaboration/threads";
    return await this.request<{ threads: Thread[] }>(url);
  }

  async getThread(
    threadId: string,
  ): Promise<{ thread: Thread; posts: Post[] }> {
    return await this.request<{ thread: Thread; posts: Post[] }>(
      `/v1/collaboration/threads/${threadId}`,
    );
  }

  async createThread(data: {
    moduleId: string;
    title: string;
    content: string;
  }): Promise<Thread> {
    return await this.request<Thread>("/v1/collaboration/threads", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async createPost(
    threadId: string,
    content: string,
    parentId?: string,
  ): Promise<Post> {
    return await this.request<Post>(
      `/v1/collaboration/threads/${threadId}/posts`,
      {
        method: "POST",
        body: JSON.stringify({ content, parentId }),
      },
    );
  }

  async voteOnPost(postId: string, vote: "up" | "down"): Promise<void> {
    await this.request(`/v1/collaboration/posts/${postId}/vote`, {
      method: "POST",
      body: JSON.stringify({ vote }),
    });
  }

  // ========== Gamification ==========

  async getGamificationProgress(): Promise<{
    totalPoints: number;
    currentStreak: number;
    level: number;
    xpToNextLevel: number;
    badges: Achievement[];
  }> {
    return await this.request("/v1/engagement/gamification/progress");
  }

  async getLeaderboard(period?: string): Promise<{
    leaderboard: Array<{
      rank: number;
      userId: string;
      displayName: string;
      points: number;
      badges: number;
    }>;
  }> {
    const url = period
      ? `/v1/engagement/gamification/leaderboard?period=${period}`
      : "/v1/engagement/gamification/leaderboard";
    return await this.request(url);
  }

  async getUserAchievements(): Promise<{ achievements: Achievement[] }> {
    return await this.request<{ achievements: Achievement[] }>(
      "/v1/engagement/gamification/achievements",
    );
  }

  // ========== Marketplace ===========

  async listMarketplaceListings(filters?: {
    status?: string;
    visibility?: string;
    limit?: number;
  }): Promise<{
    items: MarketplaceListingSummary[];
    nextCursor?: string | null;
  }> {
    const params = new URLSearchParams();
    if (filters?.status) params.set("status", filters.status);
    if (filters?.visibility) params.set("visibility", filters.visibility);
    if (typeof filters?.limit === "number") {
      params.set("limit", String(filters.limit));
    }

    const query = params.toString();
    const path = query
      ? `/v1/integration/marketplace/listings?${query}`
      : "/v1/integration/marketplace/listings";

    return await this.request<{
      items: MarketplaceListingSummary[];
      nextCursor?: string | null;
    }>(path);
  }

  async createMarketplaceCheckoutSession(args: {
    listingId: string;
    successUrl?: string;
    cancelUrl?: string;
  }): Promise<MarketplaceCheckoutSession> {
    return await this.request<MarketplaceCheckoutSession>(
      "/v1/integration/billing/checkout",
      {
        method: "POST",
        body: JSON.stringify(args),
      },
    );
  }

  async verifyMarketplaceCheckout(
    sessionId: string,
  ): Promise<MarketplaceCheckoutSession> {
    return await this.request<MarketplaceCheckoutSession>(
      "/v1/integration/billing/verify",
      {
        method: "POST",
        body: JSON.stringify({ sessionId }),
      },
    );
  }

  async listMarketplacePurchases(): Promise<{
    items: MarketplacePurchase[];
    nextCursor?: string | null;
  }> {
    return await this.request<{
      items: MarketplacePurchase[];
      nextCursor?: string | null;
    }>("/v1/integration/billing/purchases");
  }

  // ========== Search ==========

  async search(
    query: string,
    filters?: Record<string, string>,
  ): Promise<HybridSearchResponse> {
    const params = new URLSearchParams({ q: query });

    for (const [key, value] of Object.entries(filters ?? {})) {
      if (value) {
        params.set(key, value);
      }
    }

    return await this.request<HybridSearchResponse>(`/v1/search?${params}`);
  }

  async getSearchSuggestions(query: string): Promise<{
    suggestions: Array<{ text: string; type: string; id?: string }>;
  }> {
    const result = await this.search(query, { limit: "5" });

    return {
      suggestions: result.results.slice(0, 5).map((item) => ({
        text: item.asset.title,
        type: item.asset.assetType,
        id: item.asset.id,
      })),
    };
  }

  async getAssetRecommendations(
    assetId: string,
    limit: number = 4,
  ): Promise<RelatedAssetsResponse> {
    const result = await this.request<{ data: RelatedAssetsResponse }>(
      `/assets/${assetId}/recommendations?limit=${limit}`,
    );
    return result.data;
  }

  async getNextSteps(
    assetId: string,
    limit: number = 4,
  ): Promise<NextStepSuggestion[]> {
    const result = await this.request<{ data: NextStepSuggestion[] }>(
      `/assets/${assetId}/next-steps?limit=${limit}`,
    );
    return result.data;
  }

  async getPersonalizedRecommendations(limit: number = 6): Promise<{
    modules: Array<{
      id: string;
      title: string;
      slug: string;
      description?: string;
      domain?: string;
      difficultyLevel?: string;
      estimatedTimeMinutes?: number;
      tags: string[];
      isAiRecommended: boolean;
      recommendationReason?: string;
      matchScore: number;
    }>;
    reasoning: {
      basedOn: string;
      userLevel: string;
      suggestedDomains: string[];
    };
  }> {
    const result = await this.request<{
      data: {
        modules: Array<{
          id: string;
          title: string;
          slug: string;
          description?: string;
          domain?: string;
          difficultyLevel?: string;
          estimatedTimeMinutes?: number;
          tags: string[];
          isAiRecommended: boolean;
          recommendationReason?: string;
          matchScore: number;
        }>;
        reasoning: {
          basedOn: string;
          userLevel: string;
          suggestedDomains: string[];
        };
      };
    }>(`/recommendations/personalized?limit=${limit}`);
    return result.data;
  }

  async trackExplorerEvent(input: TrackExplorerEventInput): Promise<void> {
    await this.request("/telemetry/events", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  async trackExplorerEvents(input: TrackBatchEventsInput): Promise<void> {
    await this.request("/telemetry/events/batch", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  // ========== Analytics ===========

  async getAnalyticsSummary(period: "daily" | "weekly" | "monthly" = "weekly"): Promise<{
    totalEvents: number;
    activeLearners: number;
    eventsByType: Record<string, number>;
  }> {
    return await this.request<{
      totalEvents: number;
      activeLearners: number;
      eventsByType: Record<string, number>;
    }>(`/v1/learning/analytics/summary?period=${period}`);
  }

  async getUsageTrends(period: "daily" | "weekly" | "monthly" = "weekly"): Promise<{
    periods: Array<{
      periodStart: string;
      eventCount: number;
    }>;
  }> {
    return await this.request<{
      periods: Array<{
        periodStart: string;
        eventCount: number;
      }>;
    }>(`/v1/learning/analytics/advanced?period=${period}`);
  }

  async getAtRiskStudents(): Promise<Array<{
    userId: string;
    displayName: string;
    riskLevel: string;
    riskFactors?: string[];
  }>> {
    return await this.request<Array<{
      userId: string;
      displayName: string;
      riskLevel: string;
      riskFactors?: string[];
    }>>("/v1/learning/analytics/risk");
  }

  /**
   * Fetch a specific assessment by ID.
   */
  async getAssessment(assessmentId: string): Promise<Assessment> {
    return await this.request<Assessment>(`/v1/learning/assessments/${assessmentId}`);
  }

  /**
   * Start a new attempt for the given assessment.
   * Returns the attempt record including the server-generated attempt ID.
   */
  async startAssessmentAttempt(assessmentId: string): Promise<AttemptResponse> {
    return await this.request<AttemptResponse>(`/v1/learning/assessments/${assessmentId}/attempt`, {
      method: "POST",
    });
  }

  /**
   * Submit the learner's responses for an in-progress attempt.
   */
  async submitAssessmentAttempt(
    attemptId: string,
    responses: Record<string, { type: string; selectedChoiceIds?: string[]; answer?: string }>,
  ): Promise<void> {
    await this.request<void>(`/v1/learning/attempts/${attemptId}/submit`, {
      method: "POST",
      body: JSON.stringify({ responses }),
    });
  }
}

export const apiClient = new TutorPutorApiClient();

/**
 * Simplified client for direct HTTP calls.
 * This provides get/post/patch/delete methods using the shared API utilities.
 * Uses the same header logic and error handling as TutorPutorApiClient.
 */
export const tutorputorClient = {
  get: async <T = unknown>(
    url: string,
    params?: object,
  ): Promise<{ data: T }> => {
    const queryString = params
      ? `?${new URLSearchParams(params as Record<string, string>)}`
      : "";
    const token = readAccessToken();
    const data = await standardRequest<T>(`/api/v1${url}${queryString}`, {
      method: "GET",
      token,
    });
    return { data };
  },
  post: async <T = unknown>(
    url: string,
    data?: object,
  ): Promise<{ data: T }> => {
    const token = readAccessToken();
    const responseData = await standardRequest<T>(`/api/v1${url}`, {
      method: "POST",
      token,
      body: data,
    });
    return { data: responseData };
  },
  patch: async <T = unknown>(
    url: string,
    data?: object,
  ): Promise<{ data: T }> => {
    const token = readAccessToken();
    const responseData = await standardRequest<T>(`/api/v1${url}`, {
      method: "PATCH",
      token,
      body: data,
    });
    return { data: responseData };
  },
  delete: async <T = unknown>(url: string): Promise<{ data: T }> => {
    const token = readAccessToken();
    const responseData = await standardRequest<T>(`/api/v1${url}`, {
      method: "DELETE",
      token,
    });
    return { data: responseData };
  },
};
