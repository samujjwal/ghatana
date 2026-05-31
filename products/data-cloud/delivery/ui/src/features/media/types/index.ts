/**
 * Media feature types.
 *
 * Domain types for media artifacts, transcription, and vision analysis.
 *
 * G18: Media types for audio-video modality.
 *
 * @doc.type types
 * @doc.purpose Media artifact domain types
 * @doc.layer product
 * @doc.pattern Type Definitions
 */

import type {
  ConsentStatus,
  MediaArtifact as ContractMediaArtifact,
  MediaType,
} from "@/contracts/schemas";

/**
 * Media artifact domain type.
 * Extends the contract type with UI-specific fields.
 */
export interface MediaArtifact extends ContractMediaArtifact {
  /** UI-specific computed field for display name */
  displayName?: string;
  /** UI-specific computed field for formatted size */
  formattedSize?: string;
  /** UI-specific computed field for formatted duration */
  formattedDuration?: string;
  /** Whether this artifact is currently being processed */
  isProcessing?: boolean;
  /** Current processing job ID if any */
  processingJobId?: string;
}

/**
 * Media artifact form input type.
 */
export interface MediaArtifactFormInput {
  agentId: string;
  mediaType: MediaType;
  storageUri: string;
  checksum?: string;
  durationMs?: number;
  originToolId?: string;
  correlationId?: string;
  metadata?: Record<string, string>;
  consentStatus?: ConsentStatus;
  retentionPolicy?: string;
  retentionUntil?: string;
}

/**
 * Transcription job status.
 */
export type TranscriptionJobStatus =
  | "pending"
  | "processing"
  | "completed"
  | "failed";

/**
 * Transcription job type.
 */
export interface TranscriptionJob {
  jobId: string;
  artifactId: string;
  status: TranscriptionJobStatus;
  language?: string;
  format?: "text" | "srt" | "vtt";
  includeTimestamps?: boolean;
  estimatedDurationMs?: number;
  createdAt: string;
  completedAt?: string;
  result?: string;
  error?: string;
}

/**
 * Vision analysis job status.
 */
export type AnalysisJobStatus =
  | "pending"
  | "processing"
  | "completed"
  | "failed";

/**
 * Vision analysis job type.
 */
export interface AnalysisJob {
  jobId: string;
  artifactId: string;
  status: AnalysisJobStatus;
  analysisType:
    | "object_detection"
    | "scene_classification"
    | "face_detection"
    | "custom";
  parameters?: Record<string, unknown>;
  estimatedDurationMs?: number;
  createdAt: string;
  completedAt?: string;
  result?: Record<string, unknown>;
  error?: string;
}

/**
 * Media artifact filter options.
 */
export interface MediaArtifactFilters {
  agentId?: string;
  mediaType?: MediaType;
  consentStatus?: ConsentStatus;
  dateFrom?: string;
  dateTo?: string;
}

/**
 * Media artifact sort options.
 */
export type MediaArtifactSortField =
  | "createdAt"
  | "sizeBytes"
  | "durationMs"
  | "name";

export interface MediaArtifactSort {
  field: MediaArtifactSortField;
  direction: "asc" | "desc";
}

/**
 * Media artifact pagination options.
 */
export interface MediaArtifactPagination {
  page: number;
  pageSize: number;
}

/**
 * Media artifact list query options.
 */
export interface MediaArtifactListQuery {
  filters?: MediaArtifactFilters;
  sort?: MediaArtifactSort;
  pagination?: MediaArtifactPagination;
}
