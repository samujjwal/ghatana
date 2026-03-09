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

import axios from "axios";
import type {
  StorageProfile,
  StorageProfileFormInput,
  DataConnector,
  DataConnectorFormInput,
  StorageMetrics,
  SyncStatistics,
} from "../types";

const API_BASE = "/api/v1/data-fabric";

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
    const response = await axios.get<StorageProfile[]>(
      `${API_BASE}/profiles`
    );
    return response.data;
  },

  /**
   * Fetch a single storage profile by ID.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage profile
   */
  async getById(profileId: string): Promise<StorageProfile> {
    const response = await axios.get<StorageProfile>(
      `${API_BASE}/profiles/${profileId}`
    );
    return response.data;
  },

  /**
   * Create a new storage profile.
   *
   * @param input - Profile creation form data
   * @returns Promise resolving to created profile
   */
  async create(input: StorageProfileFormInput): Promise<StorageProfile> {
    const response = await axios.post<StorageProfile>(
      `${API_BASE}/profiles`,
      input
    );
    return response.data;
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
    const response = await axios.put<StorageProfile>(
      `${API_BASE}/profiles/${profileId}`,
      input
    );
    return response.data;
  },

  /**
   * Delete a storage profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(profileId: string): Promise<void> {
    await axios.delete(`${API_BASE}/profiles/${profileId}`);
  },

  /**
   * Set a profile as default.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to updated profile
   */
  async setDefault(profileId: string): Promise<StorageProfile> {
    const response = await axios.post<StorageProfile>(
      `${API_BASE}/profiles/${profileId}/set-default`
    );
    return response.data;
  },

  /**
   * Fetch storage metrics for a profile.
   *
   * @param profileId - Profile identifier
   * @returns Promise resolving to storage metrics
   */
  async getMetrics(profileId: string): Promise<StorageMetrics> {
    const response = await axios.get<StorageMetrics>(
      `${API_BASE}/profiles/${profileId}/metrics`
    );
    return response.data;
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
    const response = await axios.get<DataConnector[]>(
      `${API_BASE}/connectors`
    );
    return response.data;
  },

  /**
   * Fetch a single data connector by ID.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to data connector
   */
  async getById(connectorId: string): Promise<DataConnector> {
    const response = await axios.get<DataConnector>(
      `${API_BASE}/connectors/${connectorId}`
    );
    return response.data;
  },

  /**
   * Create a new data connector.
   *
   * @param input - Connector creation form data
   * @returns Promise resolving to created connector
   */
  async create(input: DataConnectorFormInput): Promise<DataConnector> {
    const response = await axios.post<DataConnector>(
      `${API_BASE}/connectors`,
      input
    );
    return response.data;
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
    const response = await axios.put<DataConnector>(
      `${API_BASE}/connectors/${connectorId}`,
      input
    );
    return response.data;
  },

  /**
   * Delete a data connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when deletion complete
   */
  async delete(connectorId: string): Promise<void> {
    await axios.delete(`${API_BASE}/connectors/${connectorId}`);
  },

  /**
   * Test a connector connection.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to test result
   */
  async test(connectorId: string): Promise<{ success: boolean; message?: string }> {
    const response = await axios.post<{ success: boolean; message?: string }>(
      `${API_BASE}/connectors/${connectorId}/test`
    );
    return response.data;
  },

  /**
   * Trigger a manual sync for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving when sync starts
   */
  async triggerSync(connectorId: string): Promise<{ jobId: string }> {
    const response = await axios.post<{ jobId: string }>(
      `${API_BASE}/connectors/${connectorId}/sync`
    );
    return response.data;
  },

  /**
   * Fetch sync statistics for a connector.
   *
   * @param connectorId - Connector identifier
   * @returns Promise resolving to sync statistics
   */
  async getSyncStatistics(connectorId: string): Promise<SyncStatistics> {
    const response = await axios.get<SyncStatistics>(
      `${API_BASE}/connectors/${connectorId}/statistics`
    );
    return response.data;
  },

  /**
   * Get connectors for a specific storage profile.
   *
   * @param profileId - Storage profile identifier
   * @returns Promise resolving to array of connectors
   */
  async getByProfile(profileId: string): Promise<DataConnector[]> {
    const response = await axios.get<DataConnector[]>(
      `${API_BASE}/connectors?profileId=${profileId}`
    );
    return response.data;
  },
};
