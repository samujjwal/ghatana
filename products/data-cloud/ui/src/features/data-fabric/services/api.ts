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
import type {
  StorageProfile,
  StorageProfileFormInput,
  DataConnector,
  DataConnectorFormInput,
  StorageMetrics,
  SyncStatistics,
} from "../types";

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
    return apiClient.get<StorageProfile[]>('/data-fabric/profiles');
  },

  /**
   * Fetch a single storage profile by ID.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage profile
   */
  async getById(profileId: string): Promise<StorageProfile> {
    return apiClient.get<StorageProfile>(`/data-fabric/profiles/${profileId}`);
  },

  /**
   * Create a new storage profile.
   *
   * @param input - Profile creation form data
   * @returns Promise resolving to created profile
   */
  async create(input: StorageProfileFormInput): Promise<StorageProfile> {
    return apiClient.post<StorageProfile>('/data-fabric/profiles', input);
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
    return apiClient.put<StorageProfile>(`/data-fabric/profiles/${profileId}`, input);
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
    return apiClient.post<StorageProfile>(`/data-fabric/profiles/${profileId}/set-default`);
  },

  /**
   * Fetch storage metrics for a profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage metrics
   */
  async getMetrics(profileId: string): Promise<StorageMetrics> {
    return apiClient.get<StorageMetrics>(`/data-fabric/profiles/${profileId}/metrics`);
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
    return apiClient.get<DataConnector[]>('/data-fabric/connectors');
  },

  /**
   * Fetch a single data connector by ID.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to data connector
   */
  async getById(connectorId: string): Promise<DataConnector> {
    return apiClient.get<DataConnector>(`/data-fabric/connectors/${connectorId}`);
  },

  /**
   * Create a new data connector.
   *
   * @param input - Connector creation form data
   * @returns Promise resolving to created connector
   */
  async create(input: DataConnectorFormInput): Promise<DataConnector> {
    return apiClient.post<DataConnector>('/data-fabric/connectors', input);
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
    return apiClient.put<DataConnector>(`/data-fabric/connectors/${connectorId}`, input);
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
    return apiClient.post<{ success: boolean; message?: string }>(
      `/data-fabric/connectors/${connectorId}/test`
    );
  },

  /**
   * Trigger a manual sync for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when sync starts
   */
  async triggerSync(connectorId: string): Promise<{ jobId: string }> {
    return apiClient.post<{ jobId: string }>(
      `/data-fabric/connectors/${connectorId}/sync`
    );
  },

  /**
   * Fetch sync statistics for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to sync statistics
   */
  async getSyncStatistics(connectorId: string): Promise<SyncStatistics> {
    return apiClient.get<SyncStatistics>(
      `/data-fabric/connectors/${connectorId}/statistics`
    );
  },

  /**
   * Get connectors for a specific storage profile.
   *
   * @param profileId - Storage profile identifier
   * @returns Promise resolving to array of connectors
   */
  async getByProfile(profileId: string): Promise<DataConnector[]> {
    return apiClient.get<DataConnector[]>(
      `/data-fabric/connectors?profileId=${profileId}`
    );
  },
};
