import type { ContentGenerationResult } from '@/hooks/useContentGeneration';

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type ContentType = 'module' | 'claim' | 'example' | 'simulation' | 'assessment' | 'lesson' | 'quiz' | 'exercise' | 'explanation' | 'summary' | 'flashcard';
export type Difficulty = 'INTRO' | 'INTERMEDIATE' | 'ADVANCED';

export interface ContentMetadata {
  id: string;
  title: string;
  type: ContentType;
  status: ContentStatus;
  difficulty?: Difficulty;
  domain?: string;
  tags?: string[];
  createdAt: string;
  updatedAt: string;
  authorId?: string;
}

export interface ModuleContent extends ContentMetadata {
  type: 'module';
  slug: string;
  description?: string;
  estimatedTimeMinutes?: number;
  learningObjectives?: string[];
}

export interface ClaimContent extends ContentMetadata {
  type: 'claim';
  statement: string;
  evidence?: string[];
  confidence?: number;
}

export interface ExampleContent extends ContentMetadata {
  type: 'example';
  description: string;
  code?: string;
  explanation?: string;
}

export interface SimulationContent extends ContentMetadata {
  type: 'simulation';
  manifestId?: string;
  description?: string;
}

export type Content = ModuleContent | ClaimContent | ExampleContent | SimulationContent;

export type DifficultyLevel = Difficulty;

export interface ContentMetrics {
  total: number;
  published: number;
  draft: number;
  archived: number;
  totalItems?: number;
  publishedCount?: number;
  avgQualityScore?: number;
  aiGeneratedPercentage?: number;
  contentByType?: Record<string, number>;
  contentByStatus?: Record<string, number>;
  recentGenerations?: ContentGenerationResult[];
}

export interface QualityReport {
  score: number;
  issues: string[];
  suggestions: string[];
}

export interface ContentItemWithReport extends ContentMetadata {
  qualityReport?: QualityReport;
}
