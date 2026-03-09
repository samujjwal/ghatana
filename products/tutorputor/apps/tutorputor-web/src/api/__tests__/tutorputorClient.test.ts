/**
 * Test suite for TutorPutorApiClient
 *
 * @doc.type tests
 * @doc.purpose Unit tests for the API client
 * @doc.layer product
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import axios from "axios";

// Mock axios
vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => ({
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    })),
  },
}));

// Import after mocking
import { TutorPutorApiClient, apiClient } from "../tutorputorClient";

describe("TutorPutorApiClient", () => {
  let client: TutorPutorApiClient;
  let mockAxiosInstance: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();

    // Get the mock instance
    mockAxiosInstance = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    };

    (axios.create as ReturnType<typeof vi.fn>).mockReturnValue(
      mockAxiosInstance,
    );
    client = new TutorPutorApiClient("/api");
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("constructor", () => {
    it("creates axios instance with correct base URL", () => {
      new TutorPutorApiClient("/api");

      expect(axios.create).toHaveBeenCalledWith({
        baseURL: "/api",
        headers: {
          "Content-Type": "application/json",
        },
      });
    });

    it("sets up request interceptor", () => {
      const client = new TutorPutorApiClient();

      expect(mockAxiosInstance.interceptors.request.use).toHaveBeenCalled();
    });
  });

  describe("getDashboard", () => {
    it("fetches dashboard data from correct endpoint", async () => {
      const mockData = {
        user: { id: "1", email: "test@test.com", displayName: "Test" },
        currentEnrollments: [],
        recommendedModules: [],
        stats: { totalEnrollments: 0, completedModules: 0, averageProgress: 0 },
      };

      mockAxiosInstance.get.mockResolvedValueOnce({ data: mockData });

      const result = await client.getDashboard();

      expect(mockAxiosInstance.get).toHaveBeenCalledWith(
        "/v1/learning/dashboard",
      );
      expect(result).toEqual(mockData);
    });

    it("returns fallback data when API fails", async () => {
      mockAxiosInstance.get.mockRejectedValueOnce(new Error("Network error"));

      const result = await client.getDashboard();

      // Should return mock data
      expect(result.user).toBeDefined();
      expect(result.currentEnrollments).toBeDefined();
      expect(result.recommendedModules).toBeDefined();
    });
  });

  describe("listModules", () => {
    it("fetches modules without domain filter", async () => {
      const mockData = {
        items: [{ id: "mod-1", title: "Test Module", slug: "test" }],
        nextCursor: null,
      };

      mockAxiosInstance.get.mockResolvedValueOnce({ data: mockData });

      const result = await client.listModules();

      expect(mockAxiosInstance.get).toHaveBeenCalledWith("/v1/modules", {
        params: { domain: undefined },
      });
      expect(result.items).toHaveLength(1);
    });

    it("fetches modules with domain filter", async () => {
      const mockData = {
        items: [{ id: "mod-1", title: "Physics Module", slug: "physics-1" }],
        nextCursor: null,
      };

      mockAxiosInstance.get.mockResolvedValueOnce({ data: mockData });

      const result = await client.listModules("physics");

      expect(mockAxiosInstance.get).toHaveBeenCalledWith("/v1/modules", {
        params: { domain: "physics" },
      });
    });

    it("returns fallback modules when API fails", async () => {
      mockAxiosInstance.get.mockRejectedValueOnce(new Error("API error"));

      const result = await client.listModules();

      expect(result.items).toBeDefined();
      expect(result.items.length).toBeGreaterThan(0);
    });
  });

  describe("getModuleBySlug", () => {
    it("fetches module by slug", async () => {
      const mockData = {
        module: {
          id: "mod-1",
          title: "Test Module",
          slug: "test-module",
          description: "A test module",
          difficulty: "beginner",
          estimatedTimeMinutes: 60,
          learningObjectives: [],
          contentBlocks: [],
        },
        userEnrollment: null,
      };

      mockAxiosInstance.get.mockResolvedValueOnce({ data: mockData });

      const result = await client.getModuleBySlug("test-module");

      expect(mockAxiosInstance.get).toHaveBeenCalledWith(
        "/v1/modules/test-module",
      );
      expect(result.module.slug).toBe("test-module");
    });

    it("returns fallback module when API fails", async () => {
      mockAxiosInstance.get.mockRejectedValueOnce(new Error("Not found"));

      const result = await client.getModuleBySlug("unknown-module");

      expect(result.module).toBeDefined();
      expect(result.module.id).toBe("unknown-module");
    });
  });

  describe("enrollInModule", () => {
    it("creates enrollment for module", async () => {
      const mockEnrollment = {
        id: "enroll-1",
        moduleId: "mod-1",
        userId: "user-1",
        status: "active",
        progressPercent: 0,
        timeSpentSeconds: 0,
        enrolledAt: new Date().toISOString(),
      };

      mockAxiosInstance.post.mockResolvedValueOnce({ data: mockEnrollment });

      const result = await client.enrollInModule("mod-1");

      expect(mockAxiosInstance.post).toHaveBeenCalledWith("/v1/enrollments", {
        moduleId: "mod-1",
      });
    });

    it("returns fallback enrollment when API fails", async () => {
      mockAxiosInstance.post.mockRejectedValueOnce(new Error("Server error"));

      // enrollInModule does not catch errors, so this should throw
      await expect(client.enrollInModule("mod-1")).rejects.toThrow(
        "Server error",
      );
    });
  });

  describe("updateProgress", () => {
    it("updates enrollment progress", async () => {
      const mockEnrollment = {
        id: "enroll-1",
        moduleId: "mod-1",
        userId: "user-1",
        status: "active",
        progressPercent: 50,
        currentSectionId: "section-2",
        timeSpentSeconds: 1800,
        enrolledAt: new Date().toISOString(),
      };

      mockAxiosInstance.patch.mockResolvedValueOnce({
        data: { enrollment: mockEnrollment },
      });

      const result = await client.updateProgress("enroll-1", 50, 1800);

      expect(mockAxiosInstance.patch).toHaveBeenCalledWith(
        "/v1/enrollments/enroll-1/progress",
        {
          progressPercent: 50,
          timeSpentSecondsDelta: 1800,
        },
      );
    });
  });

  describe("queryTutor", () => {
    it("sends question to tutor endpoint", async () => {
      const mockResponse = {
        response: {
          answer: "Photosynthesis is the process...",
          confidence: 0.95,
          sources: [{ title: "Biology Textbook", url: "https://..." }],
          followUpQuestions: ["What is chlorophyll?"],
        },
      };

      mockAxiosInstance.post.mockResolvedValueOnce({ data: mockResponse });

      const result = await client.queryTutor(
        "What is photosynthesis?",
        "mod-biology",
      );

      expect(mockAxiosInstance.post).toHaveBeenCalledWith(
        "/v1/ai/tutor/query",
        expect.objectContaining({
          question: "What is photosynthesis?",
          moduleId: "mod-biology",
        }),
      );
    });

    it("sends question without moduleId", async () => {
      const mockResponse = {
        response: {
          answer: "General answer",
          confidence: 0.9,
        },
      };

      mockAxiosInstance.post.mockResolvedValueOnce({ data: mockResponse });

      await client.queryTutor("What is 2+2?");

      expect(mockAxiosInstance.post).toHaveBeenCalledWith(
        "/v1/ai/tutor/query",
        expect.objectContaining({
          question: "What is 2+2?",
        }),
      );
    });
  });

  describe("listPathEnrollments", () => {
    it("fetches all path enrollments", async () => {
      const mockEnrollments = {
        enrollments: [
          { id: "path-1", userId: "user-1", pathId: "p-1", status: "active" },
          {
            id: "path-2",
            userId: "user-1",
            pathId: "p-2",
            status: "completed",
          },
        ],
      };

      mockAxiosInstance.get.mockResolvedValueOnce({ data: mockEnrollments });

      const result = await client.listPathEnrollments();

      expect(mockAxiosInstance.get).toHaveBeenCalledWith("/v1/pathways");
      expect(result.enrollments).toHaveLength(2);
    });

    it("returns empty array when API fails", async () => {
      mockAxiosInstance.get.mockRejectedValueOnce(new Error("Server error"));

      const result = await client.listPathEnrollments();

      expect(result.enrollments).toEqual([]);
    });
  });

  describe("getPathEnrollment", () => {
    it("fetches path enrollment details", async () => {
      const mockEnrollment = {
        id: "pe-1",
        userId: "user-1",
        pathId: "path-1",
        status: "active",
        currentNodeId: "node-3",
        enrolledAt: new Date().toISOString(),
      };

      mockAxiosInstance.get.mockResolvedValueOnce({ data: mockEnrollment });

      const result = await client.getPathEnrollment("path-1");

      expect(mockAxiosInstance.get).toHaveBeenCalledWith("/v1/pathways/path-1");
    });
  });

  describe("enrollInPath", () => {
    it("creates path enrollment", async () => {
      const mockEnrollment = {
        id: "pe-1",
        userId: "user-1",
        pathId: "path-1",
        status: "active",
        enrolledAt: new Date().toISOString(),
      };

      mockAxiosInstance.post.mockResolvedValueOnce({ data: mockEnrollment });

      const result = await client.enrollInPath("path-1");

      expect(mockAxiosInstance.post).toHaveBeenCalledWith(
        "/v1/pathways/enroll",
        { pathId: "path-1" },
      );
    });
  });

  describe("Teacher API", () => {
    describe("listClassrooms", () => {
      it("fetches teacher's classrooms", async () => {
        const mockClassrooms = {
          classrooms: [
            { id: "c1", name: "Class 1", code: "ABC123", teacherId: "t1" },
          ],
        };

        mockAxiosInstance.get.mockResolvedValueOnce({ data: mockClassrooms });

        const result = await client.listClassrooms();

        expect(mockAxiosInstance.get).toHaveBeenCalledWith(
          "/v1/teacher/classrooms",
        );
      });

      it("returns empty array when API fails", async () => {
        mockAxiosInstance.get.mockRejectedValueOnce(new Error("Error"));

        const result = await client.listClassrooms();

        expect(result.classrooms).toEqual([]);
      });
    });

    describe("createClassroom", () => {
      it("creates new classroom", async () => {
        const mockClassroom = {
          id: "c1",
          name: "New Class",
          description: "Description",
          code: "XYZ789",
          teacherId: "t1",
          createdAt: new Date().toISOString(),
        };

        mockAxiosInstance.post.mockResolvedValueOnce({ data: mockClassroom });

        const result = await client.createClassroom({
          name: "New Class",
          description: "Description",
        });

        expect(mockAxiosInstance.post).toHaveBeenCalledWith(
          "/v1/teacher/classrooms",
          { name: "New Class", description: "Description" },
        );
      });
    });

    describe("getClassroomProgress", () => {
      it("fetches students progress in classroom", async () => {
        const mockProgress = {
          progress: [
            {
              studentId: "s1",
              studentName: "Student 1",
              overallProgress: 75,
            },
          ],
        };

        mockAxiosInstance.get.mockResolvedValueOnce({ data: mockProgress });

        const result = await client.getClassroomProgress("c1");

        expect(mockAxiosInstance.get).toHaveBeenCalledWith(
          "/v1/teacher/classrooms/c1/progress",
        );
      });
    });
  });

  describe("Collaboration API", () => {
    describe("listThreads", () => {
      it("fetches threads for a module", async () => {
        const mockThreads = {
          threads: [
            {
              id: "t1",
              title: "Question",
              moduleId: "mod-1",
              status: "OPEN",
              authorId: "u1",
              content: "Content",
              posts: [],
              createdAt: new Date().toISOString(),
            },
          ],
        };

        mockAxiosInstance.get.mockResolvedValueOnce({ data: mockThreads });

        const result = await client.listThreads("mod-1");

        expect(mockAxiosInstance.get).toHaveBeenCalledWith(
          "/v1/collaboration/threads",
          {
            params: { moduleId: "mod-1" },
          },
        );
      });
    });

    describe("createThread", () => {
      it("creates new thread", async () => {
        const mockThread = {
          id: "t1",
          title: "New Question",
          content: "My question...",
          moduleId: "mod-1",
          status: "OPEN",
          authorId: "u1",
          posts: [],
          createdAt: new Date().toISOString(),
        };

        mockAxiosInstance.post.mockResolvedValueOnce({ data: mockThread });

        const result = await client.createThread({
          title: "New Question",
          content: "My question...",
          moduleId: "mod-1",
        });

        expect(mockAxiosInstance.post).toHaveBeenCalledWith(
          "/v1/collaboration/threads",
          {
            title: "New Question",
            content: "My question...",
            moduleId: "mod-1",
          },
        );
      });
    });

    describe("createPost", () => {
      it("adds post to thread", async () => {
        const mockPost = {
          id: "p1",
          threadId: "t1",
          authorId: "u1",
          authorName: "User",
          content: "My reply",
          createdAt: new Date().toISOString(),
        };

        mockAxiosInstance.post.mockResolvedValueOnce({ data: mockPost });

        const result = await client.createPost("t1", "My reply");

        expect(mockAxiosInstance.post).toHaveBeenCalledWith(
          "/v1/collaboration/threads/t1/posts",
          { content: "My reply", parentId: undefined },
        );
      });
    });
  });

  describe("Gamification API", () => {
    describe("getUserAchievements", () => {
      it("fetches user achievements", async () => {
        const mockAchievements = {
          achievements: [
            {
              id: "a1",
              badge: {
                id: "b1",
                name: "First Steps",
                icon: "🏆",
                rarity: "common",
              },
              earnedAt: new Date().toISOString(),
              category: "learning",
            },
          ],
        };

        mockAxiosInstance.get.mockResolvedValueOnce({ data: mockAchievements });

        const result = await client.getUserAchievements();

        expect(mockAxiosInstance.get).toHaveBeenCalledWith(
          "/v1/gamification/achievements",
        );
      });
    });

    describe("getLeaderboard", () => {
      it("fetches leaderboard", async () => {
        const mockLeaderboard = {
          leaderboard: [
            { userId: "u1", displayName: "User 1", points: 1000, rank: 1 },
            { userId: "u2", displayName: "User 2", points: 900, rank: 2 },
          ],
        };

        mockAxiosInstance.get.mockResolvedValueOnce({ data: mockLeaderboard });

        const result = await client.getLeaderboard();

        expect(mockAxiosInstance.get).toHaveBeenCalledWith(
          "/v1/gamification/leaderboard",
          { params: undefined },
        );
      });
    });
  });

  describe("Request Interceptor", () => {
    it("adds auth token from localStorage", () => {
      window.localStorage.setItem("auth_token", "test-token-123");

      // The interceptor was set up in constructor
      const interceptorFn =
        mockAxiosInstance.interceptors.request.use.mock.calls[0][0];

      const config = { headers: {} };
      const result = interceptorFn(config);

      expect(window.localStorage.getItem).toHaveBeenCalledWith("auth_token");
    });

    it("adds tenant ID from localStorage or uses default", () => {
      // The interceptor was set up in constructor
      const interceptorFn =
        mockAxiosInstance.interceptors.request.use.mock.calls[0][0];

      const config = { headers: {} };
      const result = interceptorFn(config);

      expect(window.localStorage.getItem).toHaveBeenCalledWith("tenant_id");
    });
  });
});

describe("apiClient singleton", () => {
  it("exports a singleton instance", async () => {
    // apiClient should be an instance of TutorPutorApiClient
    expect(apiClient).toBeDefined();
    expect(typeof apiClient.getDashboard).toBe("function");
    expect(typeof apiClient.listModules).toBe("function");
    expect(typeof apiClient.queryTutor).toBe("function");
  });
});
