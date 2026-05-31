import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createStore, Provider as JotaiProvider } from "jotai";
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mediaApi } from "../services/api";
import type { MediaArtifact } from "../types";
import { MediaArtifactsPage } from "./MediaArtifactsPage";

const { toastMock } = vi.hoisted(() => ({
  toastMock: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

vi.mock("sonner", () => ({
  toast: toastMock,
}));

vi.mock("../services/api", () => ({
  mediaApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
    transcribe: vi.fn(),
    analyze: vi.fn(),
  },
}));

const createMockArtifact = (
  overrides: Partial<MediaArtifact> = {},
): MediaArtifact => ({
  artifactId: "artifact-1",
  tenantId: "tenant-1",
  sizeBytes: 1024,
  durationMs: 1000,
  agentId: "agent-1",
  mediaType: "audio/wav",
  storageUri: "s3://bucket/artifact.wav",
  consentStatus: "GRANTED",
  createdAt: "2026-05-01T00:00:00Z",
  ...overrides,
});

describe("MediaArtifactsPage", () => {
  let queryClient: QueryClient;
  let jotaiStore: ReturnType<typeof createStore>;
  let confirmMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    jotaiStore = createStore();
    confirmMock = vi.fn(() => true);
    vi.stubGlobal("confirm", confirmMock);
    vi.clearAllMocks();
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [],
      count: 0,
    });
    vi.mocked(mediaApi.transcribe).mockResolvedValue({
      jobId: "job-1",
      status: "pending",
    });
    vi.mocked(mediaApi.analyze).mockResolvedValue({
      jobId: "job-1",
      status: "pending",
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const renderWithProviders = (component: React.ReactNode) =>
    render(
      <QueryClientProvider client={queryClient}>
        <JotaiProvider store={jotaiStore}>{component}</JotaiProvider>
      </QueryClientProvider>,
    );

  it("shows loading state while fetching artifacts", () => {
    vi.mocked(mediaApi.getAll).mockImplementationOnce(
      () =>
        new Promise(() => {
          return undefined;
        }),
    );

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    expect(
      screen.getByRole("status", { name: "mediaArtifacts.loading" }),
    ).toBeInTheDocument();
  });

  it("shows empty state when no artifacts exist", async () => {
    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    expect(
      await screen.findByText("mediaArtifacts.noArtifactsTitle"),
    ).toBeInTheDocument();
    expect(screen.getByText("mediaArtifacts.noArtifactsDescription")).toBeInTheDocument();
  });

  it("shows error state when the API fails", async () => {
    vi.mocked(mediaApi.getAll).mockRejectedValue(new Error("API error"));

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    expect(await screen.findByText("mediaArtifacts.error")).toBeInTheDocument();
    expect(screen.getByText("API error")).toBeInTheDocument();
  });

  it("shows unauthorized state when permission is denied", async () => {
    vi.mocked(mediaApi.getAll).mockRejectedValue({
      status: 403,
      message: "Permission denied",
    });

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    expect(
      await screen.findByText("mediaArtifacts.unauthorized"),
    ).toBeInTheDocument();
  });

  it("shows unavailable state when the service is unavailable", async () => {
    vi.mocked(mediaApi.getAll).mockRejectedValue({
      status: 503,
      surfaceUnavailable: true,
      message: "Service unavailable",
    });

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    expect(
      await screen.findByText("mediaArtifacts.unavailable"),
    ).toBeInTheDocument();
  });

  it("calls onRegisterClick when the primary register action is selected", async () => {
    const onRegisterClick = vi.fn();

    renderWithProviders(
      <MediaArtifactsPage
        onRegisterClick={onRegisterClick}
        onDetailsClick={vi.fn()}
      />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: "mediaArtifacts.registerArtifact",
      }),
    );

    expect(onRegisterClick).toHaveBeenCalledTimes(1);
  });

  it("opens artifact details when an artifact row is selected", async () => {
    const onDetailsClick = vi.fn();
    const artifact = createMockArtifact();
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [artifact],
      count: 1,
    });

    renderWithProviders(
      <MediaArtifactsPage
        onRegisterClick={vi.fn()}
        onDetailsClick={onDetailsClick}
      />,
    );

    fireEvent.click(await screen.findByText(artifact.artifactId));

    expect(onDetailsClick).toHaveBeenCalledWith(artifact);
  });

  it("triggers transcription for audio artifacts", async () => {
    const artifact = createMockArtifact();
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [artifact],
      count: 1,
    });

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: `Transcribe artifact ${artifact.artifactId}`,
      }),
    );

    await waitFor(() => {
      expect(mediaApi.transcribe).toHaveBeenCalledWith(artifact.artifactId);
    });
    expect(toastMock.success).toHaveBeenCalledWith(
      "mediaArtifacts.transcribeSuccess (Job ID: job-1)",
    );
  });

  it("triggers analysis for image artifacts", async () => {
    const artifact = createMockArtifact({
      mediaType: "image/jpeg",
      storageUri: "s3://bucket/artifact.jpg",
    });
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [artifact],
      count: 1,
    });

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: `Analyze artifact ${artifact.artifactId}`,
      }),
    );

    await waitFor(() => {
      expect(mediaApi.analyze).toHaveBeenCalledWith(artifact.artifactId, {
        analysisType: "object_detection",
      });
    });
    expect(toastMock.success).toHaveBeenCalledWith(
      "mediaArtifacts.analyzeSuccess (Job ID: job-1)",
    );
  });

  it("deletes an artifact after confirmation", async () => {
    const artifact = createMockArtifact();
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [artifact],
      count: 1,
    });
    vi.mocked(mediaApi.delete).mockResolvedValue(undefined);

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: `Delete artifact ${artifact.artifactId}`,
      }),
    );

    await waitFor(() => {
      expect(mediaApi.delete).toHaveBeenCalledWith(artifact.artifactId);
    });
    expect(confirmMock).toHaveBeenCalledWith("mediaArtifacts.deleteConfirm");
    expect(toastMock.success).toHaveBeenCalledWith(
      "mediaArtifacts.deleteSuccess",
    );
  });

  it("shows transcription only for audio artifacts", async () => {
    const artifact = createMockArtifact({
      mediaType: "application/pdf",
      storageUri: "s3://bucket/artifact.pdf",
    });
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [artifact],
      count: 1,
    });

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    await screen.findByText(artifact.artifactId);

    expect(
      screen.queryByRole("button", {
        name: `Transcribe artifact ${artifact.artifactId}`,
      }),
    ).not.toBeInTheDocument();
  });

  it("shows analysis only for image and video artifacts", async () => {
    const artifact = createMockArtifact({
      mediaType: "audio/wav",
      storageUri: "s3://bucket/artifact.wav",
    });
    vi.mocked(mediaApi.getAll).mockResolvedValue({
      items: [artifact],
      count: 1,
    });

    renderWithProviders(
      <MediaArtifactsPage onRegisterClick={vi.fn()} onDetailsClick={vi.fn()} />,
    );

    await screen.findByText(artifact.artifactId);

    expect(
      screen.queryByRole("button", {
        name: `Analyze artifact ${artifact.artifactId}`,
      }),
    ).not.toBeInTheDocument();
  });
});
