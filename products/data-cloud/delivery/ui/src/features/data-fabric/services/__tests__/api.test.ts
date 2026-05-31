import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("@/lib/api/client", () => ({
  apiClient: mockApiClient,
}));

import { CompressionType, EncryptionType, StorageType } from "../../types";
import { dataConnectorApi, storageProfileApi } from "../api";

describe("data fabric api service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("parses and maps storage profile responses into the local feature model", async () => {
    mockApiClient.get.mockResolvedValueOnce([
      {
        id: "profile-1",
        name: "Primary Warehouse",
        type: "postgresql",
        isDefault: true,
        status: "active",
        config: {
          description: "Tenant warehouse",
          tenantId: "tenant-a",
          encryption: { type: "AES_256", keyId: "kms-key" },
          compression: { type: "GZIP" },
          database: "warehouse",
        },
        createdAt: "2026-04-15T10:00:00Z",
        updatedAt: "2026-04-15T10:05:00Z",
      },
    ]);
    mockApiClient.get.mockResolvedValueOnce({
      storageUsedBytes: 25,
      storageTotalBytes: 100,
      readOpsPerSec: 5,
      writeOpsPerSec: 2,
      latencyP99Ms: 12,
      lastUpdated: "2026-04-15T10:06:00Z",
    });

    const profiles = await storageProfileApi.getAll();
    const metrics = await storageProfileApi.getMetrics("profile-1");

    expect(profiles).toEqual([
      expect.objectContaining({
        id: "profile-1",
        type: "POSTGRESQL",
        description: "Tenant warehouse",
        isActive: true,
        tenantId: "tenant-a",
        encryption: { type: "AES_256", keyId: "kms-key" },
        compression: { type: "GZIP" },
      }),
    ]);
    expect(metrics).toEqual({
      profileId: "profile-1",
      totalCapacity: 100,
      usedCapacity: 25,
      availableCapacity: 75,
      lastUpdated: "2026-04-15T10:06:00Z",
    });
  });

  it("maps storage profile create requests to the shared contract", async () => {
    mockApiClient.post.mockResolvedValueOnce({
      id: "profile-2",
      name: "Archive Storage",
      type: "azure-blob",
      isDefault: false,
      status: "inactive",
      config: {
        description: "Archive blob store",
        encryption: { type: "MANAGED" },
        compression: { type: "ZSTD" },
      },
      createdAt: "2026-04-15T11:00:00Z",
      updatedAt: "2026-04-15T11:00:00Z",
    });

    const created = await storageProfileApi.create({
      name: "Archive Storage",
      type: StorageType.AZURE_BLOB,
      description: "Archive blob store",
      config: { container: "archive" },
      encryption: { type: EncryptionType.MANAGED },
      compression: { type: CompressionType.ZSTD },
      isDefault: false,
    });

    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/api/v1/storage-profiles",
      {
        name: "Archive Storage",
        type: "azure-blob",
        config: {
          container: "archive",
          description: "Archive blob store",
          encryption: { type: "MANAGED" },
          compression: { type: "ZSTD" },
        },
        isDefault: false,
      },
    );
    expect(created).toMatchObject({
      type: "AZURE_BLOB",
      isActive: false,
    });
  });

  it("parses and maps connector responses and requests", async () => {
    mockApiClient.get.mockResolvedValueOnce([
      {
        id: "connector-1",
        name: "Orders JDBC",
        type: "jdbc",
        storageProfileId: "profile-1",
        status: "error",
        config: {
          tenantId: "tenant-a",
          syncSchedule: "0 * * * *",
          lastSyncAt: "2026-04-15T09:45:00Z",
          statusMessage: "Connection timed out",
          isEnabled: true,
          jdbcUrl: "jdbc:postgresql://warehouse/orders",
        },
        createdAt: "2026-04-15T09:00:00Z",
        updatedAt: "2026-04-15T09:45:00Z",
      },
    ]);
    mockApiClient.post.mockResolvedValueOnce({
      id: "connector-2",
      name: "Webhook Feed",
      type: "http-webhook",
      storageProfileId: "profile-1",
      status: "active",
      config: {
        isEnabled: true,
      },
      createdAt: "2026-04-15T12:00:00Z",
      updatedAt: "2026-04-15T12:00:00Z",
    });

    const connectors = await dataConnectorApi.getAll();
    const created = await dataConnectorApi.create({
      name: "Webhook Feed",
      sourceType: "http-webhook",
      storageProfileId: "profile-1",
      connectionConfig: { endpoint: "https://example.test/hook" },
      syncSchedule: "*/15 * * * *",
      isEnabled: true,
    });

    expect(connectors).toEqual([
      expect.objectContaining({
        id: "connector-1",
        sourceType: "jdbc",
        status: "error",
        statusMessage: "Connection timed out",
        isEnabled: true,
        tenantId: "tenant-a",
      }),
    ]);
    expect(mockApiClient.post).toHaveBeenCalledWith("/api/v1/connectors", {
      name: "Webhook Feed",
      type: "http-webhook",
      storageProfileId: "profile-1",
      config: {
        endpoint: "https://example.test/hook",
        syncSchedule: "*/15 * * * *",
        isEnabled: true,
      },
    });
    expect(created).toMatchObject({
      id: "connector-2",
      sourceType: "http-webhook",
      status: "active",
    });
  });

  it("validates connector test, sync trigger, and sync statistics helper responses", async () => {
    mockApiClient.post.mockResolvedValueOnce({
      success: true,
      message: "Connection verified",
    });
    mockApiClient.post.mockResolvedValueOnce({ jobId: "job-42" });
    mockApiClient.get.mockResolvedValueOnce({
      connectorId: "connector-1",
      totalRecords: 1000,
      lastSyncRecords: 120,
      totalDuration: 3600,
      lastSyncDuration: 180,
      errorCount: 1,
      lastError: "Temporary timeout",
    });

    const testResult = await dataConnectorApi.test("connector-1");
    const syncResult = await dataConnectorApi.triggerSync("connector-1");
    const statistics = await dataConnectorApi.getSyncStatistics("connector-1");

    expect(testResult).toEqual({
      success: true,
      message: "Connection verified",
    });
    expect(syncResult).toEqual({ jobId: "job-42" });
    expect(statistics).toEqual({
      connectorId: "connector-1",
      totalRecords: 1000,
      lastSyncRecords: 120,
      totalDuration: 3600,
      lastSyncDuration: 180,
      errorCount: 1,
      lastError: "Temporary timeout",
    });
  });
});
