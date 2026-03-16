/** Domain types for Content Explorer */

export type ContentType =
  | "lesson"
  | "quiz"
  | "exercise"
  | "explanation"
  | "summary"
  | "flashcard"
  | "simulation";

export type ContentStatus =
  | "draft"
  | "generating"
  | "review"
  | "approved"
  | "published"
  | "archived";

export type DifficultyLevel = "beginner" | "intermediate" | "advanced" | "expert";

export interface ContentItem {
  id: string;
  title: string;
  type: ContentType;
  status: ContentStatus;
  subject: string;
  gradeLevel: string;
  difficulty: DifficultyLevel;
  tags: string[];
  aiGenerated: boolean;
  qualityScore: number | null; // 0-100, null if not yet evaluated
  createdAt: string; // ISO 8601
  updatedAt: string;
  author: string;
  wordCount?: number;
  estimatedMinutes?: number;
  previewText?: string;
}

export interface ContentDetail extends ContentItem {
  body: string;
  learningObjectives: string[];
  prerequisites: string[];
  relatedContentIds: string[];
  generationPrompt?: string;
  reviewNotes?: string;
  qualityReport?: QualityReport;
}

/** ContentItem plus an optionally pre-fetched quality report (used in review queues) */
export type ContentItemWithReport = ContentItem & { qualityReport?: QualityReport };

export interface GenerationRequest {
  type: ContentType;
  subject: string;
  topic: string;
  gradeLevel: string;
  difficulty: DifficultyLevel;
  learningObjectives: string[];
  additionalInstructions?: string;
  existingContentIds?: string[]; // for coherence with existing content
}

export interface GenerationJob {
  jobId: string;
  request: GenerationRequest;
  status: "queued" | "running" | "completed" | "failed";
  progress: number; // 0-100
  resultContentId?: string;
  error?: string;
  startedAt: string;
  completedAt?: string;
}

export interface QualityReport {
  contentId: string;
  /** 0–1 ratio, multiply by 100 for percentage display */
  overallScore: number;
  dimensions: {
    /** 0–1 ratio each */
    accuracy: number;
    clarity: number;
    engagement: number;
    gradeAppropriateness: number;
    curriculumAlignment: number;
  };
  issues: QualityIssue[];
  suggestions: string[];
  feedback?: {
    strengths: string[];
    improvements: string[];
  };
  evaluatedAt: string;
}

export interface QualityIssue {
  severity: "error" | "warning" | "info";
  field: string;
  message: string;
}

export interface ContentFilters {
  search: string;
  types: ContentType[];
  statuses: ContentStatus[];
  subjects: string[];
  gradeLevels: string[];
  difficulties: DifficultyLevel[];
  aiGeneratedOnly: boolean;
  minQualityScore: number | null;
}

export interface ContentMetrics {
  totalItems: number;
  publishedCount: number;
  generatingCount: number;
  reviewPendingCount: number;
  avgQualityScore: number;
  aiGeneratedPercentage: number;
  contentByType: Record<ContentType, number>;
  contentByStatus: Record<ContentStatus, number>;
  recentGenerations: Array<{ date: string; count: number }>; // last 7 days
}

export const EMPTY_FILTERS: ContentFilters = {
  search: "",
  types: [],
  statuses: [],
  subjects: [],
  gradeLevels: [],
  difficulties: [],
  aiGeneratedOnly: false,
  minQualityScore: null,
};
