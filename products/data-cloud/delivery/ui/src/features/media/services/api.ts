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
 * G17: Contract-backed media service for audio-video modality.
 *
 * @doc.type service
 * @doc.purpose Media artifact API integration
 * @doc.layer product
 * @doc.pattern Service
 */

import {
  AnalysisRequestSchema,
  AnalysisResponseSchema,
  MediaArtifactCreateRequestSchema,
  MediaArtifactListResponseSchema,
  MediaArtifactSchema,
  TranscriptionRequestSchema,
  TranscriptionResponseSchema,
  type AnalysisRequest,
  type AnalysisResponse,
  type MediaArtifact as ContractMediaArtifact,
  type MediaArtifactCreateRequest,
  type MediaArtifactListResponse,
  type TranscriptionRequest,
  type TranscriptionResponse,
} from "@/contracts/schemas";
import { apiClient, type ApiError } from "@/lib/api/client";
import { z } from "zod";

// Pass 6: Job response schema
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

// Pass 6: Transcript segment schema
const TranscriptSegmentSchema = z.object({
  segmentId: z.string(),
  startMs: z.number(),
  endMs: z.number(),
  speakerId: z.string().optional(),
  text: z.string(),
  confidence: z.number(),
});

// Pass 6: Transcript response schema
const TranscriptSchema = z.object({
  transcriptId: z.string(),
  artifactId: z.string(),
  jobId: z.string(),
  languageCode: z.string(),
  confidence: z.number(),
  durationMs: z.number(),
  wordCount: z.number(),
  speakerCount: z.number(),
  fullText: z.string(),
  segments: z.array(TranscriptSegmentSchema),
  metadata: z.record(z.string(), z.unknown()).optional(),
  createdAt: z.string(),
});

// Pass 6: Frame index label schema
const FrameIndexLabelSchema = z.object({
  label: z.string(),
  occurrenceCount: z.number(),
  avgConfidence: z.number(),
});

// Pass 6: Frame index event schema
const FrameIndexEventSchema = z.object({
  eventType: z.string(),
  startMs: z.number(),
  endMs: z.number(),
  description: z.string(),
  confidence: z.number(),
});

// Pass 6: Frame index frame schema
const FrameIndexFrameSchema = z.object({
  frameMs: z.number(),
  labels: z.array(z.string()),
  boundingBoxes: z.record(z.string(), z.array(z.number())).optional(),
  confidence: z.number(),
});

// Pass 6: Frame index response schema
const FrameIndexSchema = z.object({
  frameIndexId: z.string(),
  artifactId: z.string(),
  jobId: z.string(),
  analysisType: z.string(),
  confidence: z.number(),
  frameCount: z.number(),
  durationMs: z.number(),
  frames: z.array(FrameIndexFrameSchema),
  labels: z.array(FrameIndexLabelSchema),
  events: z.array(FrameIndexEventSchema),
  metadata: z.record(z.string(), z.unknown()).optional(),
  createdAt: z.string(),
});

export type Job = z.infer<typeof JobSchema>;
export type Transcript = z.infer<typeof TranscriptSchema>;
export type FrameIndex = z.infer<typeof FrameIndexSchema>;
export type FrameIndexLabel = z.infer<typeof FrameIndexLabelSchema>;
export type FrameIndexEvent = z.infer<typeof FrameIndexEventSchema>;

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
  ): Promise<ContractMediaArtifact> {
    return withSurfaceDegradationHandling(async () => {
      const request = MediaArtifactCreateRequestSchema.parse(input);
      const rawResponse = await apiClient.post<ContractMediaArtifact>(
        "/api/v1/media/artifacts",
        request,
      );
      return MediaArtifactSchema.parse(rawResponse);
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
  async getById(artifactId: string): Promise<ContractMediaArtifact> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<ContractMediaArtifact>(
        `/api/v1/media/artifacts/${artifactId}`,
      );
      return MediaArtifactSchema.parse(rawResponse);
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
    input: TranscriptionRequest = {},
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
      const rawResponse = await apiClient.get<unknown>(
        `/api/v1/media/artifacts/${artifactId}/transcript`,
      );
      return TranscriptSchema.parse(rawResponse);
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
      const rawResponse = await apiClient.get<unknown>(
        `/api/v1/media/artifacts/${artifactId}/frame-index`,
      );
      return FrameIndexSchema.parse(rawResponse);
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
  ): Promise<ContractMediaArtifact> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.post<ContractMediaArtifact>(
        `/api/v1/media/artifacts/${artifactId}/consent`,
        { consentStatus },
      );
      return MediaArtifactSchema.parse(rawResponse);
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
