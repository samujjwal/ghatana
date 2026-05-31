import { useQuery } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DataConnectorsPage } from "../../../features/data-fabric/components/DataConnectorsPage";
import { dataConnectorApi } from "../../../features/data-fabric/services/api";
import { TestWrapper } from "../../test-utils/wrapper";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock("../../../features/data-fabric/services/api", () => ({
  dataConnectorApi: {
    getAll: vi.fn(),
    delete: vi.fn(),
    triggerSync: vi.fn(),
    getSyncStatistics: vi.fn(),
  },
}));

const mockConnector = {
  id: "connector-1",
  name: "Orders Connector",
  sourceType: "postgres",
  storageProfileId: "profile-1",
  connectionConfig: { host: "localhost" },
  syncSchedule: "0 * * * *",
  lastSyncAt: "2026-05-28T10:00:00Z",
  status: "active" as const,
  isEnabled: true,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-28T10:00:00Z",
  tenantId: "tenant-a",
};

const mockGetAll = vi.mocked(dataConnectorApi.getAll);
const mockTriggerSync = vi.mocked(dataConnectorApi.triggerSync);
const mockGetSyncStatistics = vi.mocked(dataConnectorApi.getSyncStatistics);
const mockToastSuccess = vi.mocked(toast.success);
const mockToastError = vi.mocked(toast.error);

function CollectionsProbe({
  queryFn,
}: {
  queryFn: () => Promise<readonly string[]>;
}) {
  useQuery({
    queryKey: ["collections"],
    queryFn,
  });
  return <div data-testid="collections-probe" />;
}

describe("DataConnectorsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal(
      "confirm",
      vi.fn(() => true),
    );
    mockGetAll.mockResolvedValue([mockConnector]);
  });

  it("triggers connector sync and refreshes sync statistics after a successful run", async () => {
    const user = userEvent.setup();
    mockTriggerSync.mockResolvedValue({ jobId: "job-001" });
    mockGetSyncStatistics.mockResolvedValue({
      connectorId: "connector-1",
      totalRecords: 1000,
      lastSyncRecords: 100,
      totalDuration: 120,
      lastSyncDuration: 12,
      errorCount: 0,
    });

    render(
      <DataConnectorsPage onCreateClick={vi.fn()} onEditClick={vi.fn()} />,
      { wrapper: TestWrapper },
    );

    expect(await screen.findByText("Orders Connector")).toBeInTheDocument();

    await user.click(screen.getByTitle("Trigger sync"));

    await waitFor(() => {
      expect(mockTriggerSync).toHaveBeenCalledWith("connector-1");
      expect(mockGetSyncStatistics).toHaveBeenCalledWith("connector-1");
      expect(mockToastSuccess).toHaveBeenCalledWith(
        "Sync triggered successfully (Job ID: job-001)",
      );
    });
  });

  it("invalidates active collection queries after successful connector sync", async () => {
    const user = userEvent.setup();
    const collectionsProbeQuery = vi.fn().mockResolvedValue(["col-1"]);

    mockTriggerSync.mockResolvedValue({ jobId: "job-002" });
    mockGetSyncStatistics.mockResolvedValue({
      connectorId: "connector-1",
      totalRecords: 1000,
      lastSyncRecords: 100,
      totalDuration: 120,
      lastSyncDuration: 12,
      errorCount: 0,
    });

    render(
      <>
        <CollectionsProbe queryFn={collectionsProbeQuery} />
        <DataConnectorsPage onCreateClick={vi.fn()} onEditClick={vi.fn()} />
      </>,
      { wrapper: TestWrapper },
    );

    await screen.findByTestId("collections-probe");
    await screen.findByText("Orders Connector");
    await waitFor(() => {
      expect(collectionsProbeQuery).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByTitle("Trigger sync"));

    await waitFor(() => {
      expect(collectionsProbeQuery).toHaveBeenCalledTimes(2);
    });
  });

  it("surfaces sync failures and does not request sync statistics when trigger fails", async () => {
    const user = userEvent.setup();
    mockTriggerSync.mockRejectedValue(new Error("sync failed"));

    render(
      <DataConnectorsPage onCreateClick={vi.fn()} onEditClick={vi.fn()} />,
      { wrapper: TestWrapper },
    );

    expect(await screen.findByText("Orders Connector")).toBeInTheDocument();

    await user.click(screen.getByTitle("Trigger sync"));

    await waitFor(() => {
      expect(mockTriggerSync).toHaveBeenCalledWith("connector-1");
      expect(mockGetSyncStatistics).not.toHaveBeenCalled();
      expect(mockToastError).toHaveBeenCalledWith(
        "Failed to trigger sync: sync failed",
      );
    });
  });
});
