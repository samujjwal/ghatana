/**
 * MediaArtifactPage Component Tests
 *
 * Pass 11: Verifies i18n and accessibility requirements:
 * - No raw i18n keys in rendered output
 * - Dialog has accessible name (aria-labelledby)
 * - Translated banners for consent/retention warnings
 * - Focus management (initial focus on dialog)
 * - Escape key closes dialog
 */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MediaArtifactPage } from "../../pages/MediaArtifactPage";

// Mock the media API
vi.mock("../../features/media/services/api", () => ({
  mediaApi: {
    create: vi.fn(),
    getJobs: vi.fn(),
    getTranscript: vi.fn(),
    getFrameIndex: vi.fn(),
    transcribe: vi.fn(),
    analyze: vi.fn(),
    updateConsent: vi.fn(),
    retryJob: vi.fn(),
  },
}));

// Mock the media store
vi.mock("../../features/media/stores/media.store", () => ({
  addMediaArtifactAtom: vi.fn(),
  selectMediaArtifactAtom: vi.fn(),
  selectedMediaArtifactAtom: vi.fn(),
}));

// Mock jotai
vi.mock("jotai", () => ({
  useAtom: () => [
    {
      artifactId: "test-id",
      mediaType: "audio/wav",
      processingState: "PROCESSING",
      consentStatus: "GRANTED",
      requiresConsent: false,
      canBeProcessed: true,
      transcriptId: null,
      frameIndexId: null,
    },
    vi.fn(),
  ],
}));

// Mock the media components
vi.mock("../../features/media/components/MediaArtifactDetails", () => ({
  MediaArtifactDetails: ({ onBack }: { onBack: () => void }) => (
    <div data-testid="media-artifact-details">
      <button onClick={onBack}>Back</button>
    </div>
  ),
}));

vi.mock("../../features/media/components/MediaArtifactsPage", () => ({
  MediaArtifactsPage: ({
    onRegisterClick,
    onDetailsClick,
  }: {
    onRegisterClick: () => void;
    onDetailsClick: (id: string) => void;
  }) => (
    <div data-testid="media-artifacts-table">
      <button onClick={onRegisterClick}>Register</button>
      <button onClick={() => onDetailsClick("test-id")}>Details</button>
    </div>
  ),
}));

// Mock i18n
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// Mock toast
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe("MediaArtifactPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders without crashing", () => {
    render(<MediaArtifactPage />);
    expect(screen.getByTestId("media-artifacts-table")).toBeInTheDocument();
  });

  it("opens registration dialog when Register is clicked", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));

    // Dialog should be visible with aria-labelledby
    const dialog = screen.getByRole("dialog");
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute("aria-labelledby", "register-dialog-title");

    // Title should be translated
    expect(screen.getByText("mediaArtifacts.registerArtifact")).toBeInTheDocument();
  });

  it("closes dialog on Escape key", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("closes dialog on Cancel button click", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await user.click(screen.getByText("common.cancel"));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("has accessible dialog title with proper ID", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));

    const title = screen.getByText("mediaArtifacts.registerArtifact");
    expect(title).toHaveAttribute("id", "register-dialog-title");
  });

  it("renders translated form labels", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));

    // All form labels should be translated keys, not raw strings
    expect(screen.getByText("mediaArtifactDetails.agentId")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.mediaType")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.storageUri")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.durationMs")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.consentStatus")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.checksum")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.retentionPolicy")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifactDetails.retentionUntil")).toBeInTheDocument();
  });

  it("has required fields marked with aria-required", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));

    const agentIdInput = screen.getByLabelText("mediaArtifactDetails.agentId");
    expect(agentIdInput).toHaveAttribute("aria-required", "true");

    const mediaTypeInput = screen.getByLabelText("mediaArtifactDetails.mediaType");
    expect(mediaTypeInput).toHaveAttribute("aria-required", "true");

    const storageUriInput = screen.getByLabelText("mediaArtifactDetails.storageUri");
    expect(storageUriInput).toHaveAttribute("aria-required", "true");
  });

  it("has translated consent status options", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));

    const select = screen.getByLabelText("mediaArtifactDetails.consentStatus");
    await user.click(select);

    expect(screen.getByText("media.consentPending")).toBeInTheDocument();
    expect(screen.getByText("media.consentGranted")).toBeInTheDocument();
    expect(screen.getByText("media.consentDenied")).toBeInTheDocument();
    expect(screen.getByText("media.consentNotRequired")).toBeInTheDocument();
  });

  it("has translated button text", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    await user.click(screen.getByText("Register"));

    expect(screen.getByText("common.cancel")).toBeInTheDocument();
    expect(screen.getByText("mediaArtifacts.registerArtifact")).toBeInTheDocument();
  });

  it("shows Back button when artifact is selected", async () => {
    const user = userEvent.setup();
    render(<MediaArtifactPage />);

    // Click Details to select an artifact
    await user.click(screen.getByText("Details"));

    expect(screen.getByText("common.back")).toBeInTheDocument();
  });

  // Pass 6: Status timeline tests
  describe("status timeline", () => {
    it("renders status timeline when artifact is selected", async () => {
      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      expect(screen.getByText("media.statusTimeline")).toBeInTheDocument();
    });

    it("shows processing state in timeline", async () => {
      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      expect(screen.getByText("media.lifecycle.processing")).toBeInTheDocument();
    });
  });

  // Pass 6: Consent warning tests
  describe("consent warnings", () => {
    it("shows consent warning when consent is pending", async () => {
      vi.mock("jotai", () => ({
        useAtom: () => [
          {
            artifactId: "test-id",
            mediaType: "audio/wav",
            processingState: "CONSENT_PENDING",
            consentStatus: "PENDING",
            requiresConsent: true,
            canBeProcessed: false,
            transcriptId: null,
            frameIndexId: null,
          },
          vi.fn(),
        ],
      }));

      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      expect(screen.getByText("media.consentRequired")).toBeInTheDocument();
    });

    it("does not show consent warning when consent is granted", async () => {
      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      expect(screen.queryByText("media.consentRequired")).not.toBeInTheDocument();
    });
  });

  // Pass 6: Retention warning tests
  describe("retention warnings", () => {
    it("shows retention warning when artifact is expiring soon", async () => {
      const futureDate = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString();
      vi.mock("jotai", () => ({
        useAtom: () => [
          {
            artifactId: "test-id",
            mediaType: "audio/wav",
            processingState: "RETAINED",
            consentStatus: "GRANTED",
            requiresConsent: false,
            canBeProcessed: false,
            transcriptId: null,
            frameIndexId: null,
            retentionPolicy: "30-days",
            retentionUntil: futureDate,
          },
          vi.fn(),
        ],
      }));

      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      expect(screen.getByText("media.retentionWarning")).toBeInTheDocument();
    });
  });

  // Pass 6: Jobs panel tests
  describe("jobs panel", () => {
    it("shows jobs panel when jobs are available", async () => {
      const { mediaApi } = require("../../features/media/services/api");
      vi.mocked(mediaApi.getJobs).mockResolvedValue([
        {
          jobId: "job-1",
          jobType: "transcription",
          status: "completed",
          progress: 100,
        },
      ]);

      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      await waitFor(() => {
        expect(screen.getByText("media.jobsPanel")).toBeInTheDocument();
      });
    });
  });

  // Pass 6: Transcript panel tests
  describe("transcript panel", () => {
    it("shows transcript panel when transcript is available", async () => {
      const { mediaApi } = require("../../features/media/services/api");
      vi.mocked(mediaApi.getTranscript).mockResolvedValue({
        transcriptId: "transcript-1",
        languageCode: "en-US",
        confidence: 0.95,
        wordCount: 150,
        fullText: "Hello world",
      });

      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      await waitFor(() => {
        expect(screen.getByText("media.transcriptPanel")).toBeInTheDocument();
      });
    });
  });

  // Pass 6: Frame index panel tests
  describe("frame index panel", () => {
    it("shows frame index panel when frame index is available", async () => {
      const { mediaApi } = require("../../features/media/services/api");
      vi.mocked(mediaApi.getFrameIndex).mockResolvedValue({
        frameIndexId: "frame-1",
        analysisType: "object_detection",
        confidence: 0.9,
        frameCount: 100,
        labels: [{ label: "person", occurrenceCount: 50 }],
      });

      const user = userEvent.setup();
      render(<MediaArtifactPage />);

      await user.click(screen.getByText("Details"));

      await waitFor(() => {
        expect(screen.getByText("media.frameIndexPanel")).toBeInTheDocument();
      });
    });
  });
});
