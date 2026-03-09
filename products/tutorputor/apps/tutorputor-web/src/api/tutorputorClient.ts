import axios, { type AxiosInstance } from "axios";

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
    questions?: Array<{ question: string; options: string[]; correctIndex: number }>;
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

type AchievementCategory = "learning" | "streak" | "collaboration" | "mastery" | "special";

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

export interface ApiError {
  message: string;
  statusCode: number;
}

export class TutorPutorApiClient {
  private client: AxiosInstance;

  constructor(baseURL: string = "/api") {
    this.client = axios.create({
      baseURL,
      headers: {
        "Content-Type": "application/json"
      }
    });

    // Add request interceptor for auth and tenant headers
    this.client.interceptors.request.use((config) => {
      const token = localStorage.getItem("auth_token");
      const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";

      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      config.headers["X-Tenant-ID"] = tenantId;
      config.headers["X-Correlation-ID"] = crypto.randomUUID();

      return config;
    });
  }

  async getDashboard(): Promise<DashboardSummary> {
    try {
      const response = await this.client.get<DashboardSummary>(
        "/v1/learning/dashboard"
      );
      return response.data;
    } catch (error) {
      // Return mock data when backend is unavailable
      return {
        user: {
          id: "user-1",
          email: "student@example.com",
          displayName: "Student User",
          avatarUrl: undefined
        },
        currentEnrollments: [
          {
            id: "enroll-1",
            moduleId: "mod-1",
            status: "active",
            progress: 65,
            progressPercent: 65,
            lastAccessedAt: new Date().toISOString(),
            timeSpentSeconds: 3600
          }
        ],
        recommendedModules: [
          {
            id: "mod-2",
            title: "Advanced Topics",
            slug: "advanced-topics",
            description: "Dive deeper into complex concepts",
            tags: ["advanced"],
            estimatedMinutes: 120,
            difficulty: "advanced",
            progressPercent: 0
          }
        ],
        stats: {
          totalEnrollments: 5,
          completedModules: 2,
          averageProgress: 45
        }
      };
    }
  }

  async listModules(domain?: string): Promise<{
    items: ModuleSummary[];
    nextCursor?: string | null;
  }> {
    try {
      const response = await this.client.get<{
        items: ModuleSummary[];
        nextCursor?: string | null;
      }>("/v1/modules", {
        params: { domain }
      });
      return response.data;
    } catch (error) {
      // Return mock modules when backend is unavailable
      return {
        items: [
          {
            id: "mod-1",
            title: "Introduction to Algebra",
            slug: "intro-algebra",
            description: "Learn the fundamentals of algebra",
            estimatedMinutes: 180,
            difficulty: "beginner",
            tags: ["math", "algebra"]
          },
          {
            id: "mod-2",
            title: "Calculus Basics",
            slug: "calculus-basics",
            description: "Introduction to differential calculus",
            estimatedMinutes: 240,
            difficulty: "intermediate",
            tags: ["math", "calculus"]
          }
        ],
        nextCursor: null
      };
    }
  }

  async getModuleBySlug(slug: string): Promise<{
    module: ModuleDetail;
    userEnrollment: Enrollment | null;
  }> {
    try {
      const response = await this.client.get<{
        module: ModuleDetail;
        userEnrollment: Enrollment | null;
      }>(`/v1/modules/${slug}`);
      return response.data;
    } catch (error) {
      // Return mock module when backend is unavailable
      return {
        module: {
          id: slug,
          title: `Module: ${slug}`,
          slug,
          description: "This is a placeholder module",
          domain: "general",
          estimatedTimeMinutes: 60,
          difficulty: "beginner"
        } as any,
        userEnrollment: null
      };
    }
  }

  async enrollInModule(moduleId: ModuleId): Promise<{ enrollment: Enrollment }> {
    const response = await this.client.post<{ enrollment: Enrollment }>(
      "/v1/enrollments",
      { moduleId }
    );
    return response.data;
  }

  async updateProgress(
    enrollmentId: EnrollmentId,
    progressPercent: number,
    timeSpentSecondsDelta: number
  ): Promise<{ enrollment: Enrollment }> {
    const response = await this.client.patch<{ enrollment: Enrollment }>(
      `/v1/enrollments/${enrollmentId}/progress`,
      {
        progressPercent,
        timeSpentSecondsDelta
      }
    );
    return response.data;
  }

  async queryTutor(
    question: string,
    moduleId?: ModuleId
  ): Promise<{ response: TutorResponsePayload }> {
    const response = await this.client.post<{
      response: TutorResponsePayload;
    }>("/v1/ai/tutor/query", {
      question,
      moduleId
    });
    return response.data;
  }

  async generateQuestions(
    moduleId: ModuleId,
    count: number = 5,
    difficulty: "easy" | "medium" | "hard" = "medium"
  ): Promise<{
    questions: Array<{
      question: string;
      options?: string[];
      correctAnswer: string;
      explanation: string;
    }>;
  }> {
    const response = await this.client.post<{
      questions: Array<{
        question: string;
        options?: string[];
        correctAnswer: string;
        explanation: string;
      }>;
    }>("/v1/ai/generate-questions", {
      moduleId,
      count,
      difficulty
    });
    return response.data;
  }

  // ========== Learning Pathways ==========

  async recommendPath(goals: string[], currentSkills?: string[]): Promise<LearningPath> {
    const response = await this.client.post<LearningPath>("/v1/pathways/recommend", {
      goals,
      currentSkills
    });
    return response.data;
  }

  async enrollInPath(pathId: string): Promise<LearningPathEnrollment> {
    const response = await this.client.post<LearningPathEnrollment>("/v1/pathways/enroll", {
      pathId
    });
    return response.data;
  }

  async getPathEnrollment(pathId: string): Promise<LearningPathEnrollment> {
    const response = await this.client.get<LearningPathEnrollment>(`/v1/pathways/${pathId}`);
    return response.data;
  }

  async updatePathProgress(pathId: string, nodeId: string, status: string): Promise<void> {
    await this.client.patch(`/v1/pathways/${pathId}/progress`, { nodeId, status });
  }

  async listPathEnrollments(): Promise<{ enrollments: LearningPathEnrollment[] }> {
    try {
      const response = await this.client.get<{ enrollments: LearningPathEnrollment[] }>("/v1/pathways");
      return response.data;
    } catch (error) {
      // Return empty enrollments when backend is unavailable
      return { enrollments: [] };
    }
  }

  // ========== Teacher/Classroom ==========

  async listClassrooms(): Promise<{ classrooms: Classroom[] }> {
    try {
      const response = await this.client.get<{ classrooms: Classroom[] }>("/v1/teacher/classrooms");
      return response.data;
    } catch (error) {
      // Return empty classrooms when backend is unavailable
      return { classrooms: [] };
    }
  }

  async createClassroom(data: { name: string; description?: string }): Promise<Classroom> {
    const response = await this.client.post<Classroom>("/v1/teacher/classrooms", data);
    return response.data;
  }

  async getClassroom(classroomId: string): Promise<Classroom> {
    const response = await this.client.get<Classroom>(`/v1/teacher/classrooms/${classroomId}`);
    return response.data;
  }

  async getClassroomProgress(classroomId: string): Promise<{ progress: StudentProgress[] }> {
    const response = await this.client.get<{ progress: StudentProgress[] }>(
      `/v1/teacher/classrooms/${classroomId}/progress`
    );
    return response.data;
  }

  async addStudentToClassroom(classroomId: string, studentId: string): Promise<void> {
    await this.client.post(`/v1/teacher/classrooms/${classroomId}/students`, { studentId });
  }

  // ========== Collaboration ==========

  async listThreads(moduleId?: string): Promise<{ threads: Thread[] }> {
    try {
      const response = await this.client.get<{ threads: Thread[] }>("/v1/collaboration/threads", {
        params: moduleId ? { moduleId } : undefined
      });
      return response.data;
    } catch (error) {
      // Return empty threads when backend is unavailable
      return { threads: [] };
    }
  }

  async getThread(threadId: string): Promise<{ thread: Thread; posts: Post[] }> {
    try {
      const response = await this.client.get<{ thread: Thread; posts: Post[] }>(
        `/v1/collaboration/threads/${threadId}`
      );
      return response.data;
    } catch (error) {
      // Return empty thread and posts when backend is unavailable
      return {
        thread: {
          id: threadId,
          tenantId: "",
          title: "Thread",
          status: "OPEN",
          authorId: "",
          content: "",
          posts: [],
          createdAt: new Date().toISOString()
        },
        posts: []
      };
    }
  }

  async createThread(data: { moduleId: string; title: string; content: string }): Promise<Thread> {
    const response = await this.client.post<Thread>("/v1/collaboration/threads", data);
    return response.data;
  }

  async createPost(threadId: string, content: string, parentId?: string): Promise<Post> {
    const response = await this.client.post<Post>(`/v1/collaboration/threads/${threadId}/posts`, {
      content,
      parentId
    });
    return response.data;
  }

  async voteOnPost(postId: string, vote: "up" | "down"): Promise<void> {
    await this.client.post(`/v1/collaboration/posts/${postId}/vote`, { vote });
  }

  // ========== Gamification ==========

  async getGamificationProgress(): Promise<{
    totalPoints: number;
    currentStreak: number;
    level: number;
    xpToNextLevel: number;
    badges: Achievement[];
  }> {
    try {
      const response = await this.client.get("/v1/gamification/progress");
      return response.data;
    } catch (error) {
      // Return mock progress when backend is unavailable
      return {
        totalPoints: 0,
        currentStreak: 0,
        level: 1,
        xpToNextLevel: 1000,
        badges: []
      };
    }
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
    const response = await this.client.get("/v1/gamification/leaderboard", {
      params: period ? { period } : undefined
    });
    return response.data;
  }

  async getUserAchievements(): Promise<{ achievements: Achievement[] }> {
    const response = await this.client.get<{ achievements: Achievement[] }>(
      "/v1/gamification/achievements"
    );
    return response.data;
  }

  // ========== Search ==========

  async search(query: string, filters?: Record<string, string>): Promise<{
    results: Array<{
      id: string;
      type: string;
      title: string;
      description: string;
      score: number;
    }>;
    total: number;
    facets: Record<string, Array<{ value: string; count: number }>>;
  }> {
    try {
      const response = await this.client.get("/v1/search", {
        params: { q: query, ...filters }
      });
      return response.data;
    } catch (error) {
      // Return empty results when backend is unavailable
      return { results: [], total: 0, facets: {} };
    }
  }

  async getSearchSuggestions(query: string): Promise<{
    suggestions: Array<{ text: string; type: string; id?: string }>;
  }> {
    const response = await this.client.get("/v1/search/autocomplete", {
      params: { q: query }
    });
    return response.data;
  }
}

export const apiClient = new TutorPutorApiClient();

/**
 * Simplified client for direct HTTP calls (e.g., for CMS pages)
 * This provides get/post/patch/delete methods that return axios responses
 */
export const tutorputorClient = {
  get: async <T = unknown>(url: string, params?: object): Promise<{ data: T }> => {
    const token = localStorage.getItem("auth_token");
    const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
    const response = await axios.get<T>(`/api/v1${url}`, {
      params,
      headers: {
        "Content-Type": "application/json",
        Authorization: token ? `Bearer ${token}` : undefined,
        "X-Tenant-ID": tenantId,
        "X-Correlation-ID": crypto.randomUUID(),
      },
    });
    return response;
  },
  post: async <T = unknown>(url: string, data?: object): Promise<{ data: T }> => {
    const token = localStorage.getItem("auth_token");
    const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
    const response = await axios.post<T>(`/api/v1${url}`, data, {
      headers: {
        "Content-Type": "application/json",
        Authorization: token ? `Bearer ${token}` : undefined,
        "X-Tenant-ID": tenantId,
        "X-Correlation-ID": crypto.randomUUID(),
      },
    });
    return response;
  },
  patch: async <T = unknown>(url: string, data?: object): Promise<{ data: T }> => {
    const token = localStorage.getItem("auth_token");
    const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
    const response = await axios.patch<T>(`/api/v1${url}`, data, {
      headers: {
        "Content-Type": "application/json",
        Authorization: token ? `Bearer ${token}` : undefined,
        "X-Tenant-ID": tenantId,
        "X-Correlation-ID": crypto.randomUUID(),
      },
    });
    return response;
  },
  delete: async <T = unknown>(url: string): Promise<{ data: T }> => {
    const token = localStorage.getItem("auth_token");
    const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
    const response = await axios.delete<T>(`/api/v1${url}`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: token ? `Bearer ${token}` : undefined,
        "X-Tenant-ID": tenantId,
        "X-Correlation-ID": crypto.randomUUID(),
      },
    });
    return response;
  },
};



