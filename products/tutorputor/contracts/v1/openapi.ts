/**
 * Canonical TypeScript contract surface for tutorputor-api.openapi.yaml.
 *
 * This file is intentionally small and operation-focused: OpenAPI remains the
 * route/schema source, while this module gives server handlers and typed clients
 * stable names to import. `scripts/validate-api-contract-drift.mjs` keeps these
 * exports bound to the OpenAPI operation metadata.
 */

export type ContentGradeLevel =
  | "elementary"
  | "middle-school"
  | "high-school"
  | "undergraduate"
  | "graduate";

export type GeneratedContentFormat = "markdown" | "html" | "json" | "plain-text";

export interface ContentGenerationRequest {
  topic: string;
  gradeLevel: ContentGradeLevel;
  format: GeneratedContentFormat;
  language?: string;
  includeExamples?: boolean;
  exampleCount?: number;
  includeQuiz?: boolean;
}

export interface GeneratedContent {
  id: string;
  topic: string;
  content: string;
  format: GeneratedContentFormat;
  gradeLevel?: ContentGradeLevel;
  createdAt: string;
  updatedAt?: string;
  estimatedReadTime: number;
  exampleCount?: number;
  includesQuiz?: boolean;
  generationTimeMs?: number;
}

export interface ContentLibrary {
  id: string;
  name: string;
  description?: string;
  itemCount: number;
  createdAt?: string;
  updatedAt?: string;
  isPublic?: boolean;
}

export interface CreateLibraryRequest {
  name: string;
  description?: string;
  isPublic?: boolean;
}

export interface Pagination {
  page?: number;
  pageSize?: number;
  totalItems?: number;
  totalPages?: number;
}

export interface ListLibrariesQuery {
  page?: number;
  pageSize?: number;
  search?: string;
}

export interface ListLibrariesResponse {
  items?: ContentLibrary[];
  pagination?: Pagination;
}

export interface LearningPath {
  id: string;
  userId: string;
  name?: string;
  contentSequence: string[];
  progressPercentage: number;
  completedItems?: number;
  lastAccessedAt?: string;
  createdAt?: string;
}

export interface CreateLearningPathRequest {
  name: string;
  contentSequence: string[];
}

export interface ErrorResponse {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  statusCode: number;
  details?: Record<string, unknown>;
  traceId: string;
  timestamp: string;
}

export interface RateLimitError extends ErrorResponse {
  retryAfter?: number;
}

export interface TutorPutorOpenApiService {
  generateContent(input: ContentGenerationRequest): Promise<GeneratedContent>;
  getGeneratedContent(contentId: string): Promise<GeneratedContent>;
  listLibraries(query: ListLibrariesQuery): Promise<ListLibrariesResponse>;
  createLibrary(input: CreateLibraryRequest): Promise<ContentLibrary>;
  getUserLearningPaths(userId: string): Promise<LearningPath[]>;
  createLearningPath(
    userId: string,
    input: CreateLearningPathRequest,
  ): Promise<LearningPath>;
}

export const TUTORPUTOR_OPENAPI_OPERATION_BINDINGS = {
  generateContent: {
    method: "post",
    path: "/content/generate",
    requestType: "ContentGenerationRequest",
    responseType: "GeneratedContent",
    serviceInterface: "TutorPutorOpenApiService",
    serviceMethod: "generateContent",
  },
  getGeneratedContent: {
    method: "get",
    path: "/content/generate/{contentId}",
    requestType: null,
    responseType: "GeneratedContent",
    serviceInterface: "TutorPutorOpenApiService",
    serviceMethod: "getGeneratedContent",
  },
  listLibraries: {
    method: "get",
    path: "/libraries",
    requestType: "ListLibrariesQuery",
    responseType: "ListLibrariesResponse",
    serviceInterface: "TutorPutorOpenApiService",
    serviceMethod: "listLibraries",
  },
  createLibrary: {
    method: "post",
    path: "/libraries",
    requestType: "CreateLibraryRequest",
    responseType: "ContentLibrary",
    serviceInterface: "TutorPutorOpenApiService",
    serviceMethod: "createLibrary",
  },
  getUserLearningPaths: {
    method: "get",
    path: "/learning-paths/{userId}",
    requestType: null,
    responseType: "LearningPath",
    serviceInterface: "TutorPutorOpenApiService",
    serviceMethod: "getUserLearningPaths",
  },
  createLearningPath: {
    method: "post",
    path: "/learning-paths/{userId}",
    requestType: "CreateLearningPathRequest",
    responseType: "LearningPath",
    serviceInterface: "TutorPutorOpenApiService",
    serviceMethod: "createLearningPath",
  },
} as const;
