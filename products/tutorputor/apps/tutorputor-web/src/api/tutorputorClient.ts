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
  private baseURL: string;

  constructor(baseURL: string = "/api") {
    this.baseURL = baseURL;
  }

  private getHeaders(): HeadersInit {
    const token = localStorage.getItem("auth_token");
    const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
    
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
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }

  async getDashboard(): Promise<DashboardSummary> {
    try {
      return await this.request<DashboardSummary>("/v1/learning/dashboard");
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
      const url = domain ? `/v1/modules?domain=${domain}` : '/v1/modules';
      return await this.request<{
        items: ModuleSummary[];
        nextCursor?: string | null;
      }>(url);
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
      return await this.request<{
        module: ModuleDetail;
        userEnrollment: Enrollment | null;
      }>(`/v1/modules/${slug}`);
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
    return await this.request<{ enrollment: Enrollment }>(
      "/v1/enrollments",
      { method: 'POST', body: JSON.stringify({ moduleId }) }
    );
  }

  async updateProgress(
    enrollmentId: EnrollmentId,
    progressPercent: number,
    timeSpentSecondsDelta: number
  ): Promise<{ enrollment: Enrollment }> {
    return await this.request<{ enrollment: Enrollment }>(
      `/v1/enrollments/${enrollmentId}/progress`,
      {
        method: 'PATCH',
        body: JSON.stringify({ progressPercent, timeSpentSecondsDelta })
      }
    );
  }

  async queryTutor(
    question: string,
    moduleId?: ModuleId
  ): Promise<{ response: TutorResponsePayload }> {
    return await this.request<{
      response: TutorResponsePayload;
    }>("/v1/ai/tutor/query", {
      method: 'POST',
      body: JSON.stringify({ question, moduleId })
    });
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
    return await this.request<{
      questions: Array<{
        question: string;
        options?: string[];
        correctAnswer: string;
        explanation: string;
      }>;
    }>("/v1/ai/generate-questions", {
      method: 'POST',
      body: JSON.stringify({ moduleId, count, difficulty })
    });
  }

  // ========== Learning Pathways ==========

  async recommendPath(goals: string[], currentSkills?: string[]): Promise<LearningPath> {
    return await this.request<LearningPath>("/v1/pathways/recommend", {
      method: 'POST',
      body: JSON.stringify({ goals, currentSkills })
    });
  }

  async enrollInPath(pathId: string): Promise<LearningPathEnrollment> {
    return await this.request<LearningPathEnrollment>("/v1/pathways/enroll", {
      method: 'POST',
      body: JSON.stringify({ pathId })
    });
  }

  async getPathEnrollment(pathId: string): Promise<LearningPathEnrollment> {
    return await this.request<LearningPathEnrollment>(`/v1/pathways/${pathId}`);
  }

  async updatePathProgress(pathId: string, nodeId: string, status: string): Promise<void> {
    await this.request(`/v1/pathways/${pathId}/progress`, {
      method: 'PATCH',
      body: JSON.stringify({ nodeId, status })
    });
  }

  async listPathEnrollments(): Promise<{ enrollments: LearningPathEnrollment[] }> {
    try {
      return await this.request<{ enrollments: LearningPathEnrollment[] }>("/v1/pathways");
    } catch (error) {
      // Return empty enrollments when backend is unavailable
      return { enrollments: [] };
    }
  }

  // ========== Teacher/Classroom ==========

  async listClassrooms(): Promise<{ classrooms: Classroom[] }> {
    try {
      return await this.request<{ classrooms: Classroom[] }>("/v1/teacher/classrooms");
    } catch (error) {
      // Return empty classrooms when backend is unavailable
      return { classrooms: [] };
    }
  }

  async createClassroom(data: { name: string; description?: string }): Promise<Classroom> {
    return await this.request<Classroom>("/v1/teacher/classrooms", {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }

  async getClassroom(classroomId: string): Promise<Classroom> {
    return await this.request<Classroom>(`/v1/teacher/classrooms/${classroomId}`);
  }

  async getClassroomProgress(classroomId: string): Promise<{ progress: StudentProgress[] }> {
    return await this.request<{ progress: StudentProgress[] }>(
      `/v1/teacher/classrooms/${classroomId}/progress`
    );
  }

  async addStudentToClassroom(classroomId: string, studentId: string): Promise<void> {
    await this.request(`/v1/teacher/classrooms/${classroomId}/students`, {
      method: 'POST',
      body: JSON.stringify({ studentId })
    });
  }

  // ========== Collaboration ==========

  async listThreads(moduleId?: string): Promise<{ threads: Thread[] }> {
    try {
      const url = moduleId ? `/v1/collaboration/threads?moduleId=${moduleId}` : '/v1/collaboration/threads';
      return await this.request<{ threads: Thread[] }>(url);
    } catch (error) {
      // Return empty threads when backend is unavailable
      return { threads: [] };
    }
  }

  async getThread(threadId: string): Promise<{ thread: Thread; posts: Post[] }> {
    try {
      return await this.request<{ thread: Thread; posts: Post[] }>(
        `/v1/collaboration/threads/${threadId}`
      );
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
    return await this.request<Thread>("/v1/collaboration/threads", {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }

  async createPost(threadId: string, content: string, parentId?: string): Promise<Post> {
    return await this.request<Post>(`/v1/collaboration/threads/${threadId}/posts`, {
      method: 'POST',
      body: JSON.stringify({ content, parentId })
    });
  }

  async voteOnPost(postId: string, vote: "up" | "down"): Promise<void> {
    await this.request(`/v1/collaboration/posts/${postId}/vote`, {
      method: 'POST',
      body: JSON.stringify({ vote })
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
    try {
      return await this.request("/v1/gamification/progress");
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
    const url = period ? `/v1/gamification/leaderboard?period=${period}` : '/v1/gamification/leaderboard';
    return await this.request(url);
  }

  async getUserAchievements(): Promise<{ achievements: Achievement[] }> {
    return await this.request<{ achievements: Achievement[] }>(
      "/v1/gamification/achievements"
    );
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
      const params = new URLSearchParams({ q: query, ...filters });
      return await this.request(`/v1/search?${params}`);
    } catch (error) {
      // Return empty results when backend is unavailable
      return { results: [], total: 0, facets: {} };
    }
  }

  async getSearchSuggestions(query: string): Promise<{
    suggestions: Array<{ text: string; type: string; id?: string }>;
  }> {
    return await this.request(`/v1/search/autocomplete?q=${encodeURIComponent(query)}`);
  }
}

export const apiClient = new TutorPutorApiClient();

/**
 * Simplified client for direct HTTP calls (e.g., for CMS pages)
 * This provides get/post/patch/delete methods using native fetch
 */
function getHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  const tenantId = localStorage.getItem("tenant_id") || "tenant-stub";
  
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
  get: async <T = unknown>(url: string, params?: object): Promise<{ data: T }> => {
    const queryString = params ? `?${new URLSearchParams(params as Record<string, string>)}` : '';
    const response = await fetch(`/api/v1${url}${queryString}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    const data = await response.json();
    return { data };
  },
  post: async <T = unknown>(url: string, data?: object): Promise<{ data: T }> => {
    const response = await fetch(`/api/v1${url}`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    const responseData = await response.json();
    return { data: responseData };
  },
  patch: async <T = unknown>(url: string, data?: object): Promise<{ data: T }> => {
    const response = await fetch(`/api/v1${url}`, {
      method: 'PATCH',
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    const responseData = await response.json();
    return { data: responseData };
  },
  delete: async <T = unknown>(url: string): Promise<{ data: T }> => {
    const response = await fetch(`/api/v1${url}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    const data = await response.json();
    return { data };
  },
};



