import SessionBootstrap from "@/lib/auth/session";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TestWrapper } from "../../test-utils/wrapper";

const { mockCollectionDataClient } = vi.hoisted(() => ({
  mockCollectionDataClient: {
    listRecords: vi.fn(),
  },
}));

vi.mock("@/lib/api/collection-data-client", () => ({
  collectionDataClient: mockCollectionDataClient,
}));

import { useCollectionData } from "@/features/collection/hooks/useCollectionData";

describe("useCollectionData hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    SessionBootstrap.clearTenantId();
  });

  it("loads collection records through the canonical collectionDataClient", async () => {
    mockCollectionDataClient.listRecords.mockResolvedValue({
      items: [
        {
          id: "rec-1",
          collectionId: "orders",
          tenantId: "tenant-a",
          data: { total: 42 },
          createdAt: "2026-04-15T08:00:00Z",
          updatedAt: "2026-04-15T08:00:00Z",
          createdBy: "system",
          updatedBy: "system",
          version: 1,
        },
      ],
      total: 1,
      offset: 0,
      limit: 10,
    });

    const { result } = renderHook(
      () => useCollectionData("orders", "tenant-a", 10),
      {
        wrapper: TestWrapper,
      },
    );

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(mockCollectionDataClient.listRecords).toHaveBeenCalledWith(
      "tenant-a",
      "orders",
      {
        offset: 0,
        limit: 10,
      },
    );
    expect(result.current.records).toHaveLength(1);
    expect(result.current.total).toBe(1);
    expect(result.current.error).toBeNull();
  });

  it("uses the session tenant when one is not passed explicitly", async () => {
    SessionBootstrap.setTenantId("tenant-session");
    mockCollectionDataClient.listRecords.mockResolvedValue({
      items: [],
      total: 0,
      offset: 0,
      limit: 5,
    });

    const { result } = renderHook(
      () => useCollectionData("orders", undefined, 5),
      {
        wrapper: TestWrapper,
      },
    );

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(mockCollectionDataClient.listRecords).toHaveBeenCalledWith(
      "tenant-session",
      "orders",
      {
        offset: 0,
        limit: 5,
      },
    );
  });

  it("fails fast when tenant context is missing", async () => {
    const { result } = renderHook(() => useCollectionData("orders"), {
      wrapper: TestWrapper,
    });

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(mockCollectionDataClient.listRecords).not.toHaveBeenCalled();
    expect(result.current.error).toMatch(/Tenant context is required/i);
  });
});
