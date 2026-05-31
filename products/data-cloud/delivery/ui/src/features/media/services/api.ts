/**
 * Media API service for media artifacts.
 *
 * Provides HTTP client methods for CRUD operations on media artifacts,
 * transcription requests, and vision analysis.
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
};
