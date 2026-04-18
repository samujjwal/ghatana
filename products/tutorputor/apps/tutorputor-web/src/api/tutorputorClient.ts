import type {
  HybridSearchResponse,
  NextStepSuggestion,
  RelatedAssetsResponse,
  TrackBatchEventsInput,
  TrackExplorerEventInput,
} from "@tutorputor/contracts/v1/content-studio";

// Using native fetch instead of axios due to monorepo aliasing

// Local type definitions
type ModuleId = string;
type EnrollmentId = string;
type EnrollmentStatus = "active" | "completed" | "paused" | "expired";
type ThreadStatus = "OPEN" | "RESOLVED" | "CLOSED";

interface UserInfo {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
}

// Dashboard types matching what the pages expect
interface DashboardEnrollment {
  id: string;
  moduleId: string;
  status: EnrollmentStatus;
  progress: number;
  progressPercent: number;
  lastAccessedAt?: string;
  timeSpentSeconds: number;
}

interface DashboardModuleSummary {
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

interface DashboardSummary {
  user: UserInfo;
  currentEnrollments: DashboardEnrollment[];
  recommendedModules: DashboardModuleSummary[];
  stats?: {
    totalEnrollments: number;
    completedModules: number;
    averageProgress: number;
  };
}

interface ModuleSummary {
  id: string;
  title: string;
  slug: string;
  description: string;
  thumbnailUrl?: string;
  estimatedMinutes: number;
  difficulty: "beginner" | "intermediate" | "advanced";
  tags?: string[];
}

interface LearningObjective {
  id: string;
  label: string;
  taxonomyLevel: string;
}

interface ContentBlock {
  id: string;
  blockType: "text" | "video" | "quiz" | "interactive";
  payload?: {
    markdown?: string;
    videoUrl?: string;
    questions?: Array<{
      question: string;
      options: string[];
      correctIndex: number;
    }>;
  };
}

interface ModuleDetail {
  id: string;
  title: string;
  slug: string;
  description: string;
  domain?: string;
  difficulty: string;
  estimatedTimeMinutes: number;
  learningObjectives: LearningObjective[];
  contentBlocks: ContentBlock[];
}

interface Enrollment {
  id: string;
  moduleId: string;
  userId: string;
  status: EnrollmentStatus;
  progressPercent: number;
  currentSectionId?: string;
  timeSpentSeconds: number;
  enrolledAt: string;
  lastAccessedAt?: string;
  completedAt?: string;
}

interface TutorResponsePayload {
  answer: string;
  confidence: number;
  sources?: { title: string; url: string }[];
  followUpQuestions?: string[];
}

interface PathNode {
  id: string;
  title: string;
  type: string;
  estimatedMinutes: number;
  order: number;
}

interface LearningPath {
  id: string;
  title: string;
  description: string;
  nodes?: PathNode[];
}

interface NodeProgress {
  nodeId: string;
  status: "not_started" | "in_progress" | "completed";
  completedAt?: string;
}

interface LearningPathEnrollment {
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

interface Classroom {
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

interface StudentProgress {
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

interface Post {
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

interface Thread {
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

interface Badge {
  id: string;
  name: string;
  icon: string;
  description?: string;
  rarity: "common" | "rare" | "epic" | "legendary";
}

interface Achievement {
  id: string;
  badge: Badge;
  earnedAt: string;
  category: AchievementCategory;
}

interface MarketplaceListingSummary {
  id: string;
  moduleId: string;
  moduleTitle?: string;
  moduleSlug?: string;
  description?: string;
  priceCents: number;
  visibility: string;
  status?: string;
}

interface MarketplaceCheckoutSession {
  id: string;
  listingId: string;
  amountCents: number;
  status: string;
  paymentUrl?: string;
}

interface MarketplacePurchase {
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

  private getHeaders(): HeadersInit {
    const token = localStorage.getItem("auth_token");
    const tenantId = localStorage.getItem("tenant_id");

    if (!tenantId) {
      throw new Error("Authentication required: No tenant context found");
    }

    const headers: HeadersInit = {
      "Content-Type": "application/json",
      "X-Tenant-ID": tenantId,
      "X-Correlation-ID": crypto.randomUUID(),
    };

    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    return headers;
  }

  private async request<T>(url: string, options?: RequestInit): Promise<T> {
    const response = await fetch(`${this.baseURL}${url}`, {
      ...options,
      headers: {
        ...this.getHeaders(),
        ...options?.headers,
      },
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      const error = new Error(
        errorBody?.error ||
          errorBody?.message ||
          `HTTP ${response.status}: ${response.statusText}`,
      ) as Error & { statusCode: number };
      error.statusCode = response.status;
      throw error;
    }

    return response.json();
  }

  async getDashboard(): Promise<DashboardSummary> {
    return await this.request<DashboardSummary>("/v1/learning/dashboard");
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
    return await this.request<{ enrollment: Enrollment }>("/v1/enrollments", {
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
      `/v1/enrollments/${enrollmentId}/progress`,
      {
        method: "PATCH",
        body: JSON.stringify({ progressPercent, timeSpentSecondsDelta }),
      },
    );
  }

  async queryTutor(
    question: string,
    moduleId?: ModuleId,
  ): Promise<{ response: TutorResponsePayload }> {
    return await this.request<{
      response: TutorResponsePayload;
    }>("/v1/ai/tutor/query", {
      method: "POST",
      body: JSON.stringify({ question, moduleId }),
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

  // ========== Learning Pathways ==========

  async recommendPath(
    goals: string[],
    currentSkills?: string[],
  ): Promise<LearningPath> {
    return await this.request<LearningPath>("/v1/pathways/recommend", {
      method: "POST",
      body: JSON.stringify({ goals, currentSkills }),
    });
  }

  async enrollInPath(pathId: string): Promise<LearningPathEnrollment> {
    return await this.request<LearningPathEnrollment>("/v1/pathways/enroll", {
      method: "POST",
      body: JSON.stringify({ pathId }),
    });
  }

  async getPathEnrollment(pathId: string): Promise<LearningPathEnrollment> {
    return await this.request<LearningPathEnrollment>(`/v1/pathways/${pathId}`);
  }

  async updatePathProgress(
    pathId: string,
    nodeId: string,
    status: string,
  ): Promise<void> {
    await this.request(`/v1/pathways/${pathId}/progress`, {
      method: "PATCH",
      body: JSON.stringify({ nodeId, status }),
    });
  }

  async listPathEnrollments(): Promise<{
    enrollments: LearningPathEnrollment[];
  }> {
    return await this.request<{ enrollments: LearningPathEnrollment[] }>(
      "/v1/pathways",
    );
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
    return await this.request("/v1/gamification/progress");
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
      ? `/v1/gamification/leaderboard?period=${period}`
      : "/v1/gamification/leaderboard";
    return await this.request(url);
  }

  async getUserAchievements(): Promise<{ achievements: Achievement[] }> {
    return await this.request<{ achievements: Achievement[] }>(
      "/v1/gamification/achievements",
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

    return await this.request<HybridSearchResponse>(`/search?${params}`);
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
    }>(`/v1/recommendations/personalized?limit=${limit}`);
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
    }>(`/v1/analytics/summary?period=${period}`);
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
    }>(`/v1/analytics/usage-trends?period=${period}`);
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
    }>>("/v1/analytics/at-risk");
  }
}

export const apiClient = new TutorPutorApiClient();

/**
 * Simplified client for direct HTTP calls (e.g., for CMS pages)
 * This provides get/post/patch/delete methods using native fetch
 */
function getHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  const tenantId = localStorage.getItem("tenant_id");

  if (!tenantId) {
    throw new Error("Authentication required: No tenant context found");
  }

  const headers: HeadersInit = {
    "Content-Type": "application/json",
    "X-Tenant-ID": tenantId,
    "X-Correlation-ID": crypto.randomUUID(),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return headers;
}

export const tutorputorClient = {
  get: async <T = unknown>(
    url: string,
    params?: object,
  ): Promise<{ data: T }> => {
    const queryString = params
      ? `?${new URLSearchParams(params as Record<string, string>)}`
      : "";
    const response = await fetch(`/api/v1${url}${queryString}`, {
      method: "GET",
      headers: getHeaders(),
    });
    const data = await response.json();
    return { data };
  },
  post: async <T = unknown>(
    url: string,
    data?: object,
  ): Promise<{ data: T }> => {
    const response = await fetch(`/api/v1${url}`, {
      method: "POST",
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    const responseData = await response.json();
    return { data: responseData };
  },
  patch: async <T = unknown>(
    url: string,
    data?: object,
  ): Promise<{ data: T }> => {
    const response = await fetch(`/api/v1${url}`, {
      method: "PATCH",
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    const responseData = await response.json();
    return { data: responseData };
  },
  delete: async <T = unknown>(url: string): Promise<{ data: T }> => {
    const response = await fetch(`/api/v1${url}`, {
      method: "DELETE",
      headers: getHeaders(),
    });
    const data = await response.json();
    return { data };
  },
};
