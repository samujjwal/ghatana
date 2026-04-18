/**
 * Data Fabric API service for storage profiles and connectors.
 *
 * Provides HTTP client methods for CRUD operations on storage profiles and data connectors.
 *
 * @doc.type service
 * @doc.purpose Data fabric API integration
 * @doc.layer product
 * @doc.pattern Service
 */

import { apiClient } from "@/lib/api/client";
import { z } from 'zod';
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
import {
  CompressionType,
  EncryptionType,
  StorageType,
} from "../types";
import type {
  StorageProfile,
  StorageProfileFormInput,
  DataConnector,
  DataConnectorFormInput,
  StorageMetrics,
  SyncStatistics,
} from "../types";

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
  's3': StorageType.S3,
  'azure-blob': StorageType.AZURE_BLOB,
  'gcs': StorageType.GCS,
  'postgresql': StorageType.POSTGRESQL,
  'timescaledb': StorageType.POSTGRESQL,
  'clickhouse': StorageType.DATABRICKS,
  'in-memory': StorageType.HDFS,
};

function normalizeStorageType(type: string): StorageType {
  return STORAGE_TYPE_BY_CONTRACT[type.toLowerCase()] ?? StorageType.POSTGRESQL;
}

function toStorageProfileStatus(profile: ContractStorageProfile): boolean {
  return profile.status.toLowerCase() === 'active';
}

function readString(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  return typeof value === 'string' ? value : undefined;
}

function readBoolean(record: Record<string, unknown>, key: string): boolean | undefined {
  const value = record[key];
  return typeof value === 'boolean' ? value : undefined;
}

function mapStorageProfile(profile: ContractStorageProfile): StorageProfile {
  const config = profile.config;
  const encryption: { type?: EncryptionType; keyId?: string } =
    (config.encryption as { type?: EncryptionType; keyId?: string } | undefined) ?? { type: EncryptionType.NONE };
  const compression: { type?: CompressionType } =
    (config.compression as { type?: CompressionType } | undefined) ?? { type: CompressionType.NONE };

  return {
    id: profile.id,
    name: profile.name,
    type: normalizeStorageType(profile.type),
    description: readString(config, 'description'),
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
    tenantId: readString(config, 'tenantId') ?? '',
  };
}

function normalizeConnectorStatus(status: string): DataConnector['status'] {
  switch (status.toLowerCase()) {
    case 'active':
      return 'active';
    case 'error':
      return 'error';
    case 'testing':
      return 'testing';
    default:
      return 'inactive';
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
    syncSchedule: readString(config, 'syncSchedule'),
    lastSyncAt: readString(config, 'lastSyncAt'),
    status: normalizeConnectorStatus(connector.status),
    statusMessage: readString(config, 'statusMessage'),
    isEnabled: readBoolean(config, 'isEnabled') ?? connector.status.toLowerCase() !== 'inactive',
    createdAt: connector.createdAt,
    updatedAt: connector.updatedAt,
    tenantId: readString(config, 'tenantId') ?? '',
  };
}

function mapStorageMetrics(profileId: string, metrics: ContractStorageProfileMetrics): StorageMetrics {
  return {
    profileId,
    totalCapacity: metrics.storageTotalBytes,
    usedCapacity: metrics.storageUsedBytes,
    availableCapacity: metrics.storageTotalBytes - metrics.storageUsedBytes,
    lastUpdated: metrics.lastUpdated,
  };
}

function toStorageProfileRequest(input: StorageProfileFormInput): Record<string, unknown> {
  return {
    name: input.name,
    type: input.type.toLowerCase().replace('_', '-'),
    config: {
      ...input.config,
      description: input.description,
      encryption: input.encryption,
      compression: input.compression,
    },
    isDefault: input.isDefault,
  };
}

function toStorageProfileUpdateRequest(input: Partial<StorageProfileFormInput>): Record<string, unknown> {
  return {
    ...(input.name ? { name: input.name } : {}),
    ...(input.config || input.description || input.encryption || input.compression
      ? {
          config: {
            ...(input.config ?? {}),
            ...(input.description ? { description: input.description } : {}),
            ...(input.encryption ? { encryption: input.encryption } : {}),
            ...(input.compression ? { compression: input.compression } : {}),
          },
        }
      : {}),
    ...(typeof input.isDefault === 'boolean' ? { isDefault: input.isDefault } : {}),
  };
}

function toConnectorRequest(input: DataConnectorFormInput): Record<string, unknown> {
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

function toConnectorUpdateRequest(input: Partial<DataConnectorFormInput>): Record<string, unknown> {
  return {
    ...(input.name ? { name: input.name } : {}),
    ...(input.connectionConfig || input.syncSchedule || typeof input.isEnabled === 'boolean'
      ? {
          config: {
            ...(input.connectionConfig ?? {}),
            ...(input.syncSchedule ? { syncSchedule: input.syncSchedule } : {}),
            ...(typeof input.isEnabled === 'boolean' ? { isEnabled: input.isEnabled } : {}),
          },
        }
      : {}),
  };
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
    const rawResponse = await apiClient.get<ContractStorageProfile[]>('/data-fabric/profiles');
    return rawResponse.map((profile) => mapStorageProfile(StorageProfileSchema.parse(profile)));
  },

  /**
   * Fetch a single storage profile by ID.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage profile
   */
  async getById(profileId: string): Promise<StorageProfile> {
    const rawResponse = await apiClient.get<ContractStorageProfile>(`/data-fabric/profiles/${profileId}`);
    return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
  },

  /**
   * Create a new storage profile.
   *
   * @param input - Profile creation form data
   * @returns Promise resolving to created profile
   */
  async create(input: StorageProfileFormInput): Promise<StorageProfile> {
    const request = CreateStorageProfileRequestSchema.parse(toStorageProfileRequest(input));
    const rawResponse = await apiClient.post<ContractStorageProfile>('/data-fabric/profiles', request);
    return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
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
    input: Partial<StorageProfileFormInput>
  ): Promise<StorageProfile> {
    const request = UpdateStorageProfileRequestSchema.parse(toStorageProfileUpdateRequest(input));
    const rawResponse = await apiClient.put<ContractStorageProfile>(`/data-fabric/profiles/${profileId}`, request);
    return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
  },

  /**
   * Delete a storage profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(profileId: string): Promise<void> {
    await apiClient.delete(`/data-fabric/profiles/${profileId}`);
  },

  /**
   * Set a profile as default.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to updated profile
   */
  async setDefault(profileId: string): Promise<StorageProfile> {
    const rawResponse = await apiClient.post<ContractStorageProfile>(`/data-fabric/profiles/${profileId}/set-default`);
    return mapStorageProfile(StorageProfileSchema.parse(rawResponse));
  },

  /**
   * Fetch storage metrics for a profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage metrics
   */
  async getMetrics(profileId: string): Promise<StorageMetrics> {
    const rawResponse = await apiClient.get<ContractStorageProfileMetrics>(`/data-fabric/profiles/${profileId}/metrics`);
    return mapStorageMetrics(profileId, StorageProfileMetricsSchema.parse(rawResponse));
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
    const rawResponse = await apiClient.get<Connector[]>('/data-fabric/connectors');
    return rawResponse.map((connector) => mapConnector(ConnectorSchema.parse(connector)));
  },

  /**
   * Fetch a single data connector by ID.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to data connector
   */
  async getById(connectorId: string): Promise<DataConnector> {
    const rawResponse = await apiClient.get<Connector>(`/data-fabric/connectors/${connectorId}`);
    return mapConnector(ConnectorSchema.parse(rawResponse));
  },

  /**
   * Create a new data connector.
   *
   * @param input - Connector creation form data
   * @returns Promise resolving to created connector
   */
  async create(input: DataConnectorFormInput): Promise<DataConnector> {
    const request = CreateConnectorRequestSchema.parse(toConnectorRequest(input));
    const rawResponse = await apiClient.post<Connector>('/data-fabric/connectors', request);
    return mapConnector(ConnectorSchema.parse(rawResponse));
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
    input: Partial<DataConnectorFormInput>
  ): Promise<DataConnector> {
    const request = UpdateConnectorRequestSchema.parse(toConnectorUpdateRequest(input));
    const rawResponse = await apiClient.put<Connector>(`/data-fabric/connectors/${connectorId}`, request);
    return mapConnector(ConnectorSchema.parse(rawResponse));
  },

  /**
   * Delete a data connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(connectorId: string): Promise<void> {
    await apiClient.delete(`/data-fabric/connectors/${connectorId}`);
  },

  /**
   * Test a connector connection.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to test result
   */
  async test(connectorId: string): Promise<{ success: boolean; message?: string }> {
    const rawResponse = await apiClient.post<{ success: boolean; message?: string }>(
      `/data-fabric/connectors/${connectorId}/test`
    );
    return ConnectorTestResponseSchema.parse(rawResponse);
  },

  /**
   * Trigger a manual sync for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when sync starts
   */
  async triggerSync(connectorId: string): Promise<{ jobId: string }> {
    const rawResponse = await apiClient.post<{ jobId: string }>(
      `/data-fabric/connectors/${connectorId}/sync`
    );
    return TriggerSyncResponseSchema.parse(rawResponse);
  },

  /**
   * Fetch sync statistics for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to sync statistics
   */
  async getSyncStatistics(connectorId: string): Promise<SyncStatistics> {
    const rawResponse = await apiClient.get<SyncStatistics>(
      `/data-fabric/connectors/${connectorId}/statistics`
    );
    return SyncStatisticsSchema.parse(rawResponse);
  },

  /**
   * Get connectors for a specific storage profile.
   *
   * @param profileId - Storage profile identifier
   * @returns Promise resolving to array of connectors
   */
  async getByProfile(profileId: string): Promise<DataConnector[]> {
    const rawResponse = await apiClient.get<Connector[]>('/data-fabric/connectors', {
      params: { profileId },
    });
    return rawResponse.map((connector) => mapConnector(ConnectorSchema.parse(connector)));
  },
};
