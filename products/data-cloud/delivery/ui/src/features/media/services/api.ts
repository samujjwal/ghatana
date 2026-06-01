/**
 * Media API service for media artifacts.
 *
 * Provides HTTP client methods for CRUD operations on media artifacts,
 * transcription requests, and vision analysis.
 *
 * Pass 6: Audio-video first-class modality with full lifecycle support:
 * - Job management and tracking
 * - Transcript retrieval
 * - Frame index retrieval
 * - Consent management
 * - Retry processing
 *
 * WS3: Uses generated OpenAPI types as the single source of truth for API contracts.
 * Zod schemas are kept for runtime validation at UI boundaries.
 *
 * @doc.type service
 * @doc.purpose Media artifact API integration
 * @doc.layer product
 * @doc.pattern Service
 */

import type { components, operations } from "@/generated/api/data-cloud";
import { apiClient, type ApiError } from "@/lib/api/client";
import { z } from "zod";

// WS3: Use generated OpenAPI types as the source of truth
type MediaArtifact = components["schemas"]["MediaArtifact"];
type MediaArtifactCreateRequest = components["schemas"]["MediaArtifactCreateRequest"];
type MediaProcessingJob = components["schemas"]["MediaProcessingJob"];
type Transcript = components["schemas"]["Transcript"];
type FrameIndex = components["schemas"]["FrameIndex"];

// WS3: Keep Zod schemas for runtime validation at UI boundaries
const MediaArtifactCreateRequestSchema = z.object({
  agentId: z.string(),
  mediaType: z.string(),
  storageUri: z.string(),
  sizeBytes: z.number().optional(),
  checksum: z.string().optional(),
  durationMs: z.number().optional(),
  originToolId: z.string().optional(),
  correlationId: z.string().optional(),
  metadata: z.record(z.string(), z.string()).optional(),
  consentStatus: z.union([z.literal("GRANTED"), z.literal("DENIED"), z.literal("PENDING"), z.literal("NOT_REQUIRED")]).optional(),
  retentionPolicy: z.string().optional(),
  retentionUntil: z.string().optional(),
});

const MediaArtifactSchema = z.object({
  artifactId: z.string(),
  tenantId: z.string(),
  agentId: z.string(),
  mediaType: z.string(),
  storageUri: z.string(),
  sizeBytes: z.number(),
  checksum: z.string().optional(),
  durationMs: z.number(),
  originToolId: z.string().optional(),
  correlationId: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(), // WS3: Accept unknown values, API may return various types
  createdAt: z.string(),
  processingState: z.union([z.literal("REGISTERED"), z.literal("CONSENT_PENDING"), z.literal("CONSENT_DENIED"), z.literal("QUEUED"), z.literal("PROCESSING"), z.literal("TRANSCRIBED"), z.literal("ANALYZED"), z.literal("INDEXED"), z.literal("FAILED")]).optional(),
  requiresConsent: z.boolean().optional(),
  canBeProcessed: z.boolean().optional(),
  lastError: z.string().optional(),
  transcriptId: z.string().optional(),
  frameIndexId: z.string().optional(),
  consentStatus: z.union([z.literal("GRANTED"), z.literal("DENIED"), z.literal("PENDING"), z.literal("NOT_REQUIRED")]).optional(),
  retentionPolicy: z.string().optional(),
  retentionUntil: z.string().optional(),
}).passthrough(); // WS3: Allow extra fields from generated types

const TranscriptionRequestSchema = z.object({
  languageCode: z.string().default("en-US"),
});

const AnalysisRequestSchema = z.object({
  analysisType: z.string().default("OBJECT_DETECTION"),
});

const TranscriptionResponseSchema = z.object({
  jobId: z.string(),
  artifactId: z.string(),
  status: z.string(),
  message: z.string(),
  languageCode: z.string().optional(),
});

const AnalysisResponseSchema = z.object({
  jobId: z.string(),
  artifactId: z.string(),
  status: z.string(),
  message: z.string(),
  analysisType: z.string().optional(),
});

const MediaArtifactListResponseSchema = z.object({
  items: z.array(MediaArtifactSchema),
  count: z.number(),
});

type TranscriptionRequest = z.infer<typeof TranscriptionRequestSchema>;
type AnalysisRequest = z.infer<typeof AnalysisRequestSchema>;
type TranscriptionResponse = z.infer<typeof TranscriptionResponseSchema>;
type AnalysisResponse = z.infer<typeof AnalysisResponseSchema>;
type MediaArtifactListResponse = z.infer<typeof MediaArtifactListResponseSchema>;

// Pass 6: Job response schema (kept for runtime validation)
const JobSchema = z.object({
  jobId: z.string(),
  artifactId: z.string(),
  jobType: z.string(),
  status: z.string(),
  parameters: z.record(z.string(), z.unknown()).optional(),
  resultId: z.string().optional(),
  errorMessage: z.string().optional(),
  progress: z.number().min(0).max(100).default(0),
  createdAt: z.string(),
  startedAt: z.string().optional(),
  completedAt: z.string().optional(),
  isTerminal: z.boolean(),
  isSuccessful: z.boolean(),
});

export type Job = z.infer<typeof JobSchema>;
// WS3: Transcript and FrameIndex types come from generated OpenAPI types

/**
 * G17: Handle surface degradation/unavailable errors
 * Returns true if the error indicates the feature is temporarily unavailable due to surface status
 */
function isSurfaceDegradedError(error: unknown): error is ApiError {
  const apiError = error as ApiError;
  return (
    apiError?.surfaceDegraded === true || apiError?.surfaceUnavailable === true
  );
}

/**
 * G17: Wrap API calls with surface degradation handling
 * Throws a more user-friendly error when the surface is degraded/unavailable
 */
async function withSurfaceDegradationHandling<T>(
  apiCall: () => Promise<T>,
): Promise<T> {
  try {
    return await apiCall();
  } catch (error) {
    if (isSurfaceDegradedError(error)) {
      const apiError = error as ApiError;
      const message = apiError.surfaceUnavailable
        ? "Media artifact services are not available in the current environment"
        : "Media artifact services are temporarily unavailable due to degraded surface status";

      throw {
        ...apiError,
        message,
        code: apiError.surfaceUnavailable
          ? "FEATURE_UNAVAILABLE"
          : "SURFACE_DEGRADED",
      } as ApiError;
    }
    throw error;
  }
}

/**
 * Media artifact API client.
 */
export const mediaApi = {
  /**
   * Create a new media artifact.
   *
   * @param input - Artifact creation form data
   * @returns Promise resolving to created artifact
   */
  async create(
    input: MediaArtifactCreateRequest,
  ): Promise<MediaArtifact> {
    return withSurfaceDegradationHandling(async () => {
      const request = MediaArtifactCreateRequestSchema.parse(input);
      const rawResponse = await apiClient.post<MediaArtifact>(
        "/api/v1/media/artifacts",
        request,
      );
      // WS3: Return raw response as generated type, skip Zod parse for responses
      return rawResponse;
    });
  },

  /**
   * Fetch all media artifacts for the current tenant.
   *
   * @param agentId - Optional filter by agent ID
   * @param mediaType - Optional filter by media type
   * @returns Promise resolving to paginated list of media artifacts
   */
  async getAll(
    agentId?: string,
    mediaType?: string,
  ): Promise<MediaArtifactListResponse> {
    return withSurfaceDegradationHandling(async () => {
      const params = new URLSearchParams();
      if (agentId) params.append("agentId", agentId);
      if (mediaType) params.append("mediaType", mediaType);

      const queryString = params.toString();
      const url = queryString
        ? `/api/v1/media/artifacts?${queryString}`
        : "/api/v1/media/artifacts";

      const rawResponse = await apiClient.get<MediaArtifactListResponse>(url);
      return MediaArtifactListResponseSchema.parse(rawResponse);
    });
  },

  /**
   * Fetch a single media artifact by ID.
   *
   * @param artifactId - Artifact identifier
   * @returns Promise resolving to media artifact
   */
  async getById(artifactId: string): Promise<MediaArtifact> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<MediaArtifact>(
        `/api/v1/media/artifacts/${artifactId}`,
      );
      // WS3: Return raw response as generated type, skip Zod parse for responses
      return rawResponse;
    });
  },

  /**
   * Delete a media artifact.
   *
   * @param artifactId - Artifact identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(artifactId: string): Promise<void> {
    return withSurfaceDegradationHandling(async () => {
      await apiClient.delete(`/api/v1/media/artifacts/${artifactId}`);
    });
  },

  /**
   * Request transcription for an audio artifact.
   *
   * @param artifactId - Artifact identifier
   * @param input - Transcription request options
   * @returns Promise resolving to transcription job response
   */
  async transcribe(
    artifactId: string,
    input: TranscriptionRequest = { languageCode: "en-US" },
  ): Promise<TranscriptionResponse> {
    return withSurfaceDegradationHandling(async () => {
      const request = TranscriptionRequestSchema.parse(input);
      const rawResponse = await apiClient.post<TranscriptionResponse>(
        `/api/v1/media/artifacts/${artifactId}/transcribe`,
        request,
      );
      return TranscriptionResponseSchema.parse(rawResponse);
    });
  },

  /**
   * Request vision analysis for an image/video artifact.
   *
   * @param artifactId - Artifact identifier
   * @param input - Analysis request options
   * @returns Promise resolving to analysis job response
   */
  async analyze(
    artifactId: string,
    input: AnalysisRequest,
  ): Promise<AnalysisResponse> {
    return withSurfaceDegradationHandling(async () => {
      const request = AnalysisRequestSchema.parse(input);
      const rawResponse = await apiClient.post<AnalysisResponse>(
        `/api/v1/media/artifacts/${artifactId}/analyze`,
        request,
      );
      return AnalysisResponseSchema.parse(rawResponse);
    });
  },

  /**
   * Pass 6: Get processing jobs for an artifact.
   *
   * @param artifactId - Artifact identifier
   * @returns Promise resolving to array of jobs
   */
  async getJobs(artifactId: string): Promise<Job[]> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<unknown[]>(
        `/api/v1/media/artifacts/${artifactId}/jobs`,
      );
      return z.array(JobSchema).parse(rawResponse);
    });
  },

  /**
   * Pass 6: Get transcript for an audio artifact.
   *
   * @param artifactId - Artifact identifier
   * @returns Promise resolving to transcript
   */
  async getTranscript(artifactId: string): Promise<Transcript> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<Transcript>(
        `/api/v1/media/artifacts/${artifactId}/transcript`,
      );
      return rawResponse;
    });
  },

  /**
   * Pass 6: Get frame index for an image/video artifact.
   *
   * @param artifactId - Artifact identifier
   * @returns Promise resolving to frame index
   */
  async getFrameIndex(artifactId: string): Promise<FrameIndex> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<FrameIndex>(
        `/api/v1/media/artifacts/${artifactId}/frame-index`,
      );
      return rawResponse;
    });
  },

  /**
   * Pass 6: Update consent status for an artifact.
   *
   * @param artifactId - Artifact identifier
   * @param consentStatus - New consent status (GRANTED, DENIED, PENDING, NOT_REQUIRED)
   * @returns Promise resolving to updated artifact
   */
  async updateConsent(
    artifactId: string,
    consentStatus: string,
  ): Promise<MediaArtifact> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.post<MediaArtifact>(
        `/api/v1/media/artifacts/${artifactId}/consent`,
        { consentStatus },
      );
      // WS3: Return raw response as generated type, skip Zod parse for responses
      return rawResponse;
    });
  },

  /**
   * Pass 6: Retry failed processing for an artifact.
   *
   * @param artifactId - Artifact identifier
   * @returns Promise resolving to retry response
   */
  async retryJob(artifactId: string): Promise<{ jobId: string; status: string }> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.post<{ jobId: string; status: string }>(
        `/api/v1/media/artifacts/${artifactId}/retry`,
        {},
      );
      return rawResponse;
    });
  },
};
