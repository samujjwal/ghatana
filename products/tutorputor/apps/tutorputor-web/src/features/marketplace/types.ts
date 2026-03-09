/**
 * Marketplace Types
 *
 * Type definitions for the simulation template marketplace.
 *
 * @doc.type module
 * @doc.purpose Type definitions for marketplace feature
 * @doc.layer product
 * @doc.pattern Types
 */

// Local type definitions (mirroring contracts to avoid build order issues)
export type SimulationDomain =
  | "PHYSICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ECONOMICS"
  | "CS_DISCRETE"
  | "MATH"
  | "ENGINEERING";

// Placeholder for manifest until contracts are built
export interface SimulationManifest {
  id: string;
  title: string;
  domain: SimulationDomain;
  // ... other fields defined in contracts
}

export type DifficultyLevel = "beginner" | "intermediate" | "advanced" | "expert";
export type SortDirection = "asc" | "desc";

// =============================================================================
// Template Types
// =============================================================================

export interface SimulationTemplate {
  id: string;
  title: string;
  description: string;
  domain: SimulationDomain;
  difficulty: TemplateDifficulty;
  tags: string[];
  thumbnailUrl: string;
  manifestId: string;
  manifest?: SimulationManifest;

  // Metadata
  author: TemplateAuthor;
  publishedAt: string;
  updatedAt: string;
  version: string;

  // Stats
  stats: TemplateStats;

  // Access
  isPremium: boolean;
  isVerified: boolean;
  license: TemplateLicense;
}

export type TemplateDifficulty = "beginner" | "intermediate" | "advanced" | "expert";

export interface TemplateAuthor {
  id: string;
  name: string;
  avatarUrl?: string;
  isVerified: boolean;
  organization?: string;
}

export interface TemplateStats {
  views: number;
  uses: number;
  favorites: number;
  rating: number;
  ratingCount: number;
  completionRate: number;
  avgTimeMinutes: number;
}

export type TemplateLicense = "free" | "cc-by" | "cc-by-sa" | "cc-by-nc" | "proprietary";

// =============================================================================
// Filter & Sort Types
// =============================================================================

export interface TemplateFilters {
  domains?: SimulationDomain[];
  difficulties?: TemplateDifficulty[];
  tags?: string[];
  isPremium?: boolean;
  isVerified?: boolean;
  minRating?: number;
  search?: string;
}

export type TemplateSortField =
  | "popularity"
  | "rating"
  | "newest"
  | "mostUsed"
  | "alphabetical";

export type TemplateSortOrder = "asc" | "desc";

export interface TemplateSort {
  field: TemplateSortField;
  order: TemplateSortOrder;
}

// =============================================================================
// Collection Types
// =============================================================================

export interface TemplateCollection {
  id: string;
  title: string;
  description: string;
  coverImageUrl: string;
  templateIds: string[];
  curatorId: string;
  curatorName: string;
  isOfficial: boolean;
  createdAt: string;
}

// =============================================================================
// User Interaction Types
// =============================================================================

export interface TemplateFavorite {
  templateId: string;
  userId: string;
  createdAt: string;
}

export interface TemplateRating {
  templateId: string;
  userId: string;
  rating: number;
  review?: string;
  createdAt: string;
  updatedAt: string;
}

// =============================================================================
// API Response Types
// =============================================================================

export interface TemplatesResponse {
  templates: SimulationTemplate[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}

export interface TemplateDetailResponse {
  template: SimulationTemplate;
  relatedTemplates: SimulationTemplate[];
  userFavorited: boolean;
  userRating?: number;
}
