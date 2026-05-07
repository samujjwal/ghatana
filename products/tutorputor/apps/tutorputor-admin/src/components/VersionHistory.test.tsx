import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { VersionHistory } from "./VersionHistory";

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

describe("VersionHistory", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal("confirm", vi.fn(() => true));
  });

  it("loads version history from the canonical content versioning route", async () => {
    const fetchMock = vi.fn(async () =>
      new Response(
        JSON.stringify({
          versions: [
            {
              versionId: "asset-1-v2",
              contentId: "asset-1",
              contentType: "contentAsset",
              versionNumber: 2,
              snapshot: { title: "Current" },
              createdBy: "author-1",
              createdAt: "2026-05-06T10:00:00.000Z",
              changeSummary: "Manual edit",
              isMajorVersion: false,
            },
          ],
        }),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    renderWithQueryClient(
      <VersionHistory
        entityType="contentAsset"
        entityId="asset-1"
        currentVersion={2}
      />,
    );

    await screen.findByText("Version History (1 versions)");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/content/versions?contentId=asset-1&contentType=contentAsset",
    );
    expect(screen.getByText("Version 2")).toBeInTheDocument();
    expect(screen.getByText(/Manual edit/)).toBeInTheDocument();
  });

  it("rolls back through the canonical rollback route", async () => {
    const user = userEvent.setup();
    const onRestore = vi.fn();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            versions: [
              {
                versionId: "asset-1-v2",
                contentId: "asset-1",
                contentType: "contentAsset",
                versionNumber: 2,
                snapshot: { title: "Current" },
                createdBy: "author-1",
                createdAt: "2026-05-06T10:00:00.000Z",
                changeSummary: "Published",
                isMajorVersion: true,
              },
              {
                versionId: "asset-1-v1",
                contentId: "asset-1",
                contentType: "contentAsset",
                versionNumber: 1,
                snapshot: { title: "Previous" },
                createdBy: "author-1",
                createdAt: "2026-05-06T09:00:00.000Z",
                changeSummary: "Initial draft",
                isMajorVersion: false,
              },
            ],
          }),
        ),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ versionNumber: 3 })))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ versions: [] })),
      );
    vi.stubGlobal("fetch", fetchMock);

    renderWithQueryClient(
      <VersionHistory
        entityType="contentAsset"
        entityId="asset-1"
        currentVersion={2}
        tenantId="tenant-1"
        actorId="qa-1"
        onRestore={onRestore}
      />,
    );

    await screen.findByText("Version 1");
    await user.click(screen.getByRole("button", { name: "Restore" }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/content/versions/asset-1-v1/rollback",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            contentId: "asset-1",
            contentType: "contentAsset",
            tenantId: "tenant-1",
            rolledBackBy: "qa-1",
          }),
        }),
      );
    });
    expect(onRestore).toHaveBeenCalledWith(3);
  });
});
