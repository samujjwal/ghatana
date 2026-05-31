/**
 * Data Fabric API service for storage profiles and connectors.
 *
 * Provides HTTP client methods for CRUD operations on storage profiles and data connectors.
 *
 * G9: Updated to use canonical API routes (/api/v1/*), fixed suspicious type mappings,
 * and added 503 surface degradation handling.
 *
 * @doc.type service
 * @doc.purpose Data fabric API integration
 * @doc.layer product
 * @doc.pattern Service
 */

import {
  ConnectorSchema,
  ConnectorTypeSchema,
  CreateConnectorRequestSchema,
  CreateStorageProfileRequestSchema,
  StorageProfileMetricsSchema,
  StorageProfileSchema,
  UpdateConnectorRequestSchema,
  UpdateStorageProfileRequestSchema,
  type Connector,
  type StorageProfile as ContractStorageProfile,
  type StorageProfileMetrics as ContractStorageProfileMetrics,
} from "@/contracts/schemas";
import { apiClient, type ApiError } from "@/lib/api/client";
import { z } from "zod";
import type {
  DataConnector,
  DataConnectorFormInput,
  StorageMetrics,
  StorageProfile,
  StorageProfileFormInput,
  SyncStatistics,
} from "../types";
import { CompressionType, EncryptionType, StorageType } from "../types";

const ConnectorTestResponseSchema = z.object({
  success: z.boolean(),
  message: z.string().optional(),
});

const TriggerSyncResponseSchema = z.object({
  jobId: z.string(),
});

const SyncStatisticsSchema = z.object({
  connectorId: z.string(),
  totalRecords: z.number(),
  lastSyncRecords: z.number(),
  totalDuration: z.number(),
  lastSyncDuration: z.number(),
  errorCount: z.number(),
  lastError: z.string().optional(),
});

const STORAGE_TYPE_BY_CONTRACT: Record<string, StorageType> = {
  s3: StorageType.S3,
  "azure-blob": StorageType.AZURE_BLOB,
  gcs: StorageType.GCS,
  postgresql: StorageType.POSTGRESQL,
  timescaledb: StorageType.POSTGRESQL,
  clickhouse: StorageType.CLICKHOUSE,
  "in-memory": StorageType.IN_MEMORY,
  hdfs: StorageType.HDFS,
  databricks: StorageType.DATABRICKS,
};

function normalizeStorageType(type: string): StorageType {
  return STORAGE_TYPE_BY_CONTRACT[type.toLowerCase()] ?? StorageType.POSTGRESQL;
}

function toStorageProfileStatus(profile: ContractStorageProfile): boolean {
  return profile.status.toLowerCase() === "active";
}

function readString(
  record: Record<string, unknown>,
  key: string,
): string | undefined {
  const value = record[key];
  return typeof value === "string" ? value : undefined;
}

function readBoolean(
  record: Record<string, unknown>,
  key: string,
): boolean | undefined {
  const value = record[key];
  return typeof value === "boolean" ? value : undefined;
}

function mapStorageProfile(profile: ContractStorageProfile): StorageProfile {
  const config = profile.config;
  const encryption: { type?: EncryptionType; keyId?: string } =
    (config.encryption as
      | { type?: EncryptionType; keyId?: string }
      | undefined) ?? { type: EncryptionType.NONE };
  const compression: { type?: CompressionType } = (config.compression as
    | { type?: CompressionType }
    | undefined) ?? { type: CompressionType.NONE };

  return {
    id: profile.id,
    name: profile.name,
    type: normalizeStorageType(profile.type),
    description: readString(config, "description"),
    config,
    encryption: {
      type: encryption.type ?? EncryptionType.NONE,
      keyId: encryption.keyId,
    },
    compression: {
      type: compression.type ?? CompressionType.NONE,
    },
    isDefault: profile.isDefault,
    isActive: toStorageProfileStatus(profile),
    createdAt: profile.createdAt,
    updatedAt: profile.updatedAt,
    tenantId: readString(config, "tenantId") ?? "",
  };
}

function normalizeConnectorStatus(status: string): DataConnector["status"] {
  switch (status.toLowerCase()) {
    case "active":
      return "active";
    case "error":
      return "error";
    case "testing":
      return "testing";
    default:
      return "inactive";
  }
}

function mapConnector(connector: Connector): DataConnector {
  const config = connector.config;

  return {
    id: connector.id,
    name: connector.name,
    sourceType: connector.type,
    storageProfileId: connector.storageProfileId,
    connectionConfig: config,
    syncSchedule: readString(config, "syncSchedule"),
    lastSyncAt: readString(config, "lastSyncAt"),
    status: normalizeConnectorStatus(connector.status),
    statusMessage: readString(config, "statusMessage"),
    isEnabled:
      readBoolean(config, "isEnabled") ??
      connector.status.toLowerCase() !== "inactive",
    createdAt: connector.createdAt,
    updatedAt: connector.updatedAt,
    tenantId: readString(config, "tenantId") ?? "",
  };
}

function mapStorageMetrics(
  profileId: string,
  metrics: ContractStorageProfileMetrics,
): StorageMetrics {
  return {
    profileId,
    totalCapacity: metrics.storageTotalBytes,
    usedCapacity: metrics.storageUsedBytes,
    availableCapacity: metrics.storageTotalBytes - metrics.storageUsedBytes,
    lastUpdated: metrics.lastUpdated,
  };
}

function toStorageProfileRequest(
  input: StorageProfileFormInput,
): Record<string, unknown> {
  return {
    name: input.name,
    type: input.type.toLowerCase().replace("_", "-"),
    config: {
      ...input.config,
      description: input.description,
      encryption: input.encryption,
      compression: input.compression,
    },
    isDefault: input.isDefault,
  };
}

function toStorageProfileUpdateRequest(
  input: Partial<StorageProfileFormInput>,
): Record<string, unknown> {
  return {
    ...(input.name ? { name: input.name } : {}),
    ...(input.config ||
    input.description ||
    input.encryption ||
    input.compression
      ? {
          config: {
            ...(input.config ?? {}),
            ...(input.description ? { description: input.description } : {}),
            ...(input.encryption ? { encryption: input.encryption } : {}),
            ...(input.compression ? { compression: input.compression } : {}),
          },
        }
      : {}),
    ...(typeof input.isDefault === "boolean"
      ? { isDefault: input.isDefault }
      : {}),
  };
}

function toConnectorRequest(
  input: DataConnectorFormInput,
): Record<string, unknown> {
  return {
    name: input.name,
    type: ConnectorTypeSchema.parse(input.sourceType.toLowerCase()),
    storageProfileId: input.storageProfileId,
    config: {
      ...input.connectionConfig,
      ...(input.syncSchedule ? { syncSchedule: input.syncSchedule } : {}),
      isEnabled: input.isEnabled,
    },
  };
}

function toConnectorUpdateRequest(
  input: Partial<DataConnectorFormInput>,
): Record<string, unknown> {
  return {
    ...(input.name ? { name: input.name } : {}),
    ...(input.connectionConfig ||
    input.syncSchedule ||
    typeof input.isEnabled === "boolean"
      ? {
          config: {
            ...(input.connectionConfig ?? {}),
            ...(input.syncSchedule ? { syncSchedule: input.syncSchedule } : {}),
            ...(typeof input.isEnabled === "boolean"
              ? { isEnabled: input.isEnabled }
              : {}),
          },
        }
      : {}),
  };
}

/**
 * G9: Handle surface degradation/unavailable errors
 * Returns true if the error indicates the feature is temporarily unavailable due to surface status
 */
function isSurfaceDegradedError(error: unknown): error is ApiError {
  const apiError = error as ApiError;
  return (
    apiError?.surfaceDegraded === true || apiError?.surfaceUnavailable === true
  );
}

/**
 * G9: Wrap API calls with surface degradation handling
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
        ? "Data fabric connectors and storage profiles are not available in the current environment"
        : "Data fabric connectors and storage profiles are temporarily unavailable due to degraded surface status";

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
 * Storage profile API client.
 */
export const storageProfileApi = {
  /**
   * Fetch all storage profiles for the current tenant.
   *
   * @returns Promise resolving to array of storage profiles
   */
  async getAll(): Promise<StorageProfile[]> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<ContractStorageProfile[]>(
        "/api/v1/storage-profiles",
      );
      return rawResponse.map((profile) =>
        mapStorageProfile(StorageProfileSchema.parse(profile)),
      );
    });
  },

  /**
   * Fetch a single storage profile by ID.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage profile
   */
  async getById(profileId: string): Promise<StorageProfile> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<ContractStorageProfile>(
        `/api/v1/storage-profiles/${profileId}`,
      );
      return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
    });
  },

  /**
   * Create a new storage profile.
   *
   * @param input - Profile creation form data
   * @returns Promise resolving to created profile
   */
  async create(input: StorageProfileFormInput): Promise<StorageProfile> {
    return withSurfaceDegradationHandling(async () => {
      const request = CreateStorageProfileRequestSchema.parse(
        toStorageProfileRequest(input),
      );
      const rawResponse = await apiClient.post<ContractStorageProfile>(
        "/api/v1/storage-profiles",
        request,
      );
      return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
    });
  },

  /**
   * Update an existing storage profile.
   *
   * @param profileId - Profile identifier
   * @param input - Profile update form data
   * @returns Promise resolving to updated profile
   */
  async update(
    profileId: string,
    input: Partial<StorageProfileFormInput>,
  ): Promise<StorageProfile> {
    return withSurfaceDegradationHandling(async () => {
      const request = UpdateStorageProfileRequestSchema.parse(
        toStorageProfileUpdateRequest(input),
      );
      const rawResponse = await apiClient.put<ContractStorageProfile>(
        `/api/v1/storage-profiles/${profileId}`,
        request,
      );
      return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
    });
  },

  /**
   * Delete a storage profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(profileId: string): Promise<void> {
    return withSurfaceDegradationHandling(async () => {
      await apiClient.delete(`/api/v1/storage-profiles/${profileId}`);
    });
  },

  /**
   * Set a profile as default.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to updated profile
   */
  async setDefault(profileId: string): Promise<StorageProfile> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.post<ContractStorageProfile>(
        `/api/v1/storage-profiles/${profileId}/set-default`,
      );
      return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
    });
  },

  /**
   * Fetch storage metrics for a profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage metrics
   */
  async getMetrics(profileId: string): Promise<StorageMetrics> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<ContractStorageProfileMetrics>(
        `/api/v1/storage-profiles/${profileId}/metrics`,
      );
      return mapStorageMetrics(
        profileId,
        StorageProfileMetricsSchema.parse(rawResponse),
      );
    });
  },
};

/**
 * Data connector API client.
 */
export const dataConnectorApi = {
  /**
   * Fetch all data connectors for the current tenant.
   *
   * @returns Promise resolving to array of data connectors
   */
  async getAll(): Promise<DataConnector[]> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse =
        await apiClient.get<Connector[]>("/api/v1/connectors");
      return rawResponse.map((connector) =>
        mapConnector(ConnectorSchema.parse(connector)),
      );
    });
  },

  /**
   * Fetch a single data connector by ID.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to data connector
   */
  async getById(connectorId: string): Promise<DataConnector> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<Connector>(
        `/api/v1/connectors/${connectorId}`,
      );
      return mapConnector(ConnectorSchema.parse(rawResponse));
    });
  },

  /**
   * Create a new data connector.
   *
   * @param input - Connector creation form data
   * @returns Promise resolving to created connector
   */
  async create(input: DataConnectorFormInput): Promise<DataConnector> {
    return withSurfaceDegradationHandling(async () => {
      const request = CreateConnectorRequestSchema.parse(
        toConnectorRequest(input),
      );
      const rawResponse = await apiClient.post<Connector>(
        "/api/v1/connectors",
        request,
      );
      return mapConnector(ConnectorSchema.parse(rawResponse));
    });
  },

  /**
   * Update an existing data connector.
   *
   * @param connectorId - Connector identifier
   * @param input - Connector update form data
   * @returns Promise resolving to updated connector
   */
  async update(
    connectorId: string,
    input: Partial<DataConnectorFormInput>,
  ): Promise<DataConnector> {
    return withSurfaceDegradationHandling(async () => {
      const request = UpdateConnectorRequestSchema.parse(
        toConnectorUpdateRequest(input),
      );
      const rawResponse = await apiClient.put<Connector>(
        `/api/v1/connectors/${connectorId}`,
        request,
      );
      return mapConnector(ConnectorSchema.parse(rawResponse));
    });
  },

  /**
   * Delete a data connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(connectorId: string): Promise<void> {
    return withSurfaceDegradationHandling(async () => {
      await apiClient.delete(`/api/v1/connectors/${connectorId}`);
    });
  },

  /**
   * Test a connector connection.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to test result
   */
  async test(
    connectorId: string,
  ): Promise<{ success: boolean; message?: string }> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.post<{
        success: boolean;
        message?: string;
      }>(`/api/v1/connectors/${connectorId}/test`);
      return ConnectorTestResponseSchema.parse(rawResponse);
    });
  },

  /**
   * Trigger a manual sync for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when sync starts
   */
  async triggerSync(connectorId: string): Promise<{ jobId: string }> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.post<{ jobId: string }>(
        `/api/v1/connectors/${connectorId}/sync`,
      );
      return TriggerSyncResponseSchema.parse(rawResponse);
    });
  },

  /**
   * Fetch sync statistics for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to sync statistics
   */
  async getSyncStatistics(connectorId: string): Promise<SyncStatistics> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<SyncStatistics>(
        `/api/v1/connectors/${connectorId}/statistics`,
      );
      return SyncStatisticsSchema.parse(rawResponse);
    });
  },

  /**
   * Get connectors for a specific storage profile.
   *
   * @param profileId - Storage profile identifier
   * @returns Promise resolving to array of connectors
   */
  async getByProfile(profileId: string): Promise<DataConnector[]> {
    return withSurfaceDegradationHandling(async () => {
      const rawResponse = await apiClient.get<Connector[]>(
        "/api/v1/connectors",
        {
          params: { profileId },
        },
      );
      return rawResponse.map((connector) =>
        mapConnector(ConnectorSchema.parse(connector)),
      );
    });
  },
};
