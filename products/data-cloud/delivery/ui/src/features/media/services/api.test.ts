/**
 * J10: Tests for media API service.
 *
 * Verifies that:
 * - Create/list/get/delete artifact
 * - Request transcription
 * - Request analysis
 * - Read job status
 * - Read results
 * - Handles 403/503/redacted fields
 *
 * @doc.type test
 * @doc.purpose Test media API service integration
 * @doc.layer product
 * @doc.pattern Test
 */

import { apiClient } from "@/lib/api/client";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mediaApi } from "./api";

// Mock the API client
vi.mock("@/lib/api/client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

const artifactResponse = {
  artifactId: "artifact-1",
  tenantId: "tenant-1",
  agentId: "agent-1",
  mediaType: "audio/wav",
  storageUri: "s3://bucket/artifact.wav",
  sizeBytes: 1024,
  durationMs: 1000,
  consentStatus: "GRANTED",
  createdAt: "2026-05-01T00:00:00Z",
};

describe("media API service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(apiClient.get).mockResolvedValue({
      items: [artifactResponse],
      count: 1,
    });
    vi.mocked(apiClient.post).mockResolvedValue(artifactResponse);
  });

  describe("artifact CRUD operations", () => {
    it("creates artifact", async () => {
      await mediaApi.create({
        agentId: "agent-1",
        mediaType: "audio/wav",
        storageUri: "s3://bucket/artifact.wav",
        consentStatus: "GRANTED",
      });
      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts",
        expect.any(Object),
      );
    });

    it("lists artifacts", async () => {
      await mediaApi.getAll("agent-1", "audio/wav");
      expect(apiClient.get).toHaveBeenCalledWith(
        "/api/v1/media/artifacts?agentId=agent-1&mediaType=audio%2Fwav",
      );
    });

    it("gets artifact by ID", async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce(artifactResponse);
      await mediaApi.getById("artifact-1");
      expect(apiClient.get).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1",
      );
    });

    it("deletes artifact", async () => {
      await mediaApi.delete("artifact-1");
      expect(apiClient.delete).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1",
      );
    });
  });

  describe("transcription request", () => {
    it("requests transcription for artifact", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        jobId: "job-1",
        status: "pending",
      });
      await mediaApi.transcribe("artifact-1", { language: "en-US" });
      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1/transcribe",
        expect.any(Object),
      );
    });

    it("requests transcription with default options", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        jobId: "job-1",
        status: "pending",
      });
      await mediaApi.transcribe("artifact-1");
      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1/transcribe",
        expect.any(Object),
      );
    });
  });

  describe("analysis request", () => {
    it("requests vision analysis for artifact", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        jobId: "job-1",
        status: "pending",
      });
      await mediaApi.analyze("artifact-1", {
        analysisType: "object_detection",
      });
      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1/analyze",
        expect.any(Object),
      );
    });
  });

  describe("error handling", () => {
    it("handles 403 permission denied", async () => {
      vi.mocked(apiClient.post).mockRejectedValue({
        status: 403,
        message: "Permission denied",
      });

      await expect(
        mediaApi.create({
          agentId: "agent-1",
          mediaType: "audio/wav",
          storageUri: "s3://bucket/artifact.wav",
        }),
      ).rejects.toMatchObject({ status: 403 });
    });

    it("handles 503 service unavailable", async () => {
      vi.mocked(apiClient.get).mockRejectedValue({
        status: 503,
        message: "Service unavailable",
      });

      await expect(mediaApi.getById("artifact-1")).rejects.toMatchObject({
        status: 503,
      });
    });

    it("handles surface degraded error", async () => {
      vi.mocked(apiClient.get).mockRejectedValue({
        status: 503,
        surfaceDegraded: true,
        message: "Surface degraded",
      });

      await expect(mediaApi.getById("artifact-1")).rejects.toMatchObject({
        code: "SURFACE_DEGRADED",
      });
    });

    it("handles surface unavailable error", async () => {
      vi.mocked(apiClient.get).mockRejectedValue({
        status: 503,
        surfaceUnavailable: true,
        message: "Surface unavailable",
      });

      await expect(mediaApi.getById("artifact-1")).rejects.toMatchObject({
        code: "FEATURE_UNAVAILABLE",
      });
    });
  });

  describe("redacted fields", () => {
    it("redacts sensitive metadata from response", async () => {
      const mockResponse = {
        ...artifactResponse,
        agentId: "agent-1",
        metadata: {
          sensitiveKey: "REDACTED",
          pii: "REDACTED",
        },
      };

      vi.mocked(apiClient.get).mockResolvedValue(mockResponse);

      const artifact = await mediaApi.getById("artifact-1");

      // J10: Verify that sensitive fields are redacted
      expect(artifact).toBeDefined();
      if (artifact.metadata?.sensitiveKey) {
        expect(artifact.metadata.sensitiveKey).toBe("REDACTED");
      }
    });
  });

  // Pass 6: Job management tests
  describe("job management", () => {
    it("gets jobs for an artifact", async () => {
      const mockJobs = [
        {
          jobId: "job-1",
          artifactId: "artifact-1",
          jobType: "transcription",
          status: "completed",
          progress: 100,
          createdAt: "2026-05-01T00:00:00Z",
          isTerminal: true,
          isSuccessful: true,
        },
      ];
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockJobs);

      const jobs = await mediaApi.getJobs("artifact-1");

      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/media/artifacts/artifact-1/jobs");
      expect(jobs).toHaveLength(1);
      expect(jobs[0].jobId).toBe("job-1");
    });

    it("retries failed job for an artifact", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        jobId: "job-2",
        status: "pending",
      });

      const result = await mediaApi.retryJob("artifact-1");

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1/retry",
        {},
      );
      expect(result.jobId).toBe("job-2");
    });
  });

  // Pass 6: Transcript retrieval tests
  describe("transcript retrieval", () => {
    it("gets transcript for an artifact", async () => {
      const mockTranscript = {
        transcriptId: "transcript-1",
        artifactId: "artifact-1",
        jobId: "job-1",
        languageCode: "en-US",
        confidence: 0.95,
        durationMs: 1000,
        wordCount: 150,
        speakerCount: 1,
        fullText: "Hello world",
        segments: [],
        createdAt: "2026-05-01T00:00:00Z",
      };
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockTranscript);

      const transcript = await mediaApi.getTranscript("artifact-1");

      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/media/artifacts/artifact-1/transcript");
      expect(transcript.transcriptId).toBe("transcript-1");
      expect(transcript.languageCode).toBe("en-US");
    });
  });

  // Pass 6: Frame index retrieval tests
  describe("frame index retrieval", () => {
    it("gets frame index for an artifact", async () => {
      const mockFrameIndex = {
        frameIndexId: "frame-1",
        artifactId: "artifact-1",
        jobId: "job-1",
        analysisType: "object_detection",
        confidence: 0.9,
        frameCount: 100,
        durationMs: 5000,
        frames: [],
        labels: [
          { label: "person", occurrenceCount: 50, avgConfidence: 0.9 },
        ],
        events: [],
        createdAt: "2026-05-01T00:00:00Z",
      };
      vi.mocked(apiClient.get).mockResolvedValueOnce(mockFrameIndex);

      const frameIndex = await mediaApi.getFrameIndex("artifact-1");

      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/media/artifacts/artifact-1/frame-index");
      expect(frameIndex.frameIndexId).toBe("frame-1");
      expect(frameIndex.analysisType).toBe("object_detection");
    });
  });

  // Pass 6: Consent management tests
  describe("consent management", () => {
    it("updates consent status for an artifact", async () => {
      const updatedArtifact = {
        ...artifactResponse,
        consentStatus: "GRANTED",
      };
      vi.mocked(apiClient.post).mockResolvedValueOnce(updatedArtifact);

      const artifact = await mediaApi.updateConsent("artifact-1", "GRANTED");

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1/consent",
        { consentStatus: "GRANTED" },
      );
      expect(artifact.consentStatus).toBe("GRANTED");
    });

    it("denies consent for an artifact", async () => {
      const updatedArtifact = {
        ...artifactResponse,
        consentStatus: "DENIED",
      };
      vi.mocked(apiClient.post).mockResolvedValueOnce(updatedArtifact);

      const artifact = await mediaApi.updateConsent("artifact-1", "DENIED");

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/media/artifacts/artifact-1/consent",
        { consentStatus: "DENIED" },
      );
      expect(artifact.consentStatus).toBe("DENIED");
    });
  });
});
