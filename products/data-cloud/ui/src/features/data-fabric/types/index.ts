/**
 * Data Fabric types and interfaces.
 *
 * Defines storage profiles, connectors, and related data structures for the data fabric system.
 */

/**
 * Storage system types supported by the data fabric.
 */
export enum StorageType {
  S3 = "S3",
  AZURE_BLOB = "AZURE_BLOB",
  GCS = "GCS",
  POSTGRESQL = "POSTGRESQL",
  MONGODB = "MONGODB",
  SNOWFLAKE = "SNOWFLAKE",
  DATABRICKS = "DATABRICKS",
  HDFS = "HDFS",
}

/**
 * Encryption types for data at rest.
 */
export enum EncryptionType {
  NONE = "NONE",
  AES_256 = "AES_256",
  KMS = "KMS",
  MANAGED = "MANAGED",
}

/**
 * Compression algorithms supported.
 */
export enum CompressionType {
  NONE = "NONE",
  GZIP = "GZIP",
  SNAPPY = "SNAPPY",
  ZSTD = "ZSTD",
}

/**
 * Storage profile representing a configured storage backend.
 *
 * @doc.type interface
 * @doc.purpose Storage configuration container
 * @doc.layer product
 */
export interface StorageProfile {
  /** Unique identifier for the profile */
  id: string;
  /** User-friendly name */
  name: string;
  /** Type of storage system */
  type: StorageType;
  /** Description of the profile */
  description?: string;
  /** Storage-specific configuration */
  config: Record<string, unknown>;
  /** Encryption settings */
  encryption: {
    type: EncryptionType;
    keyId?: string;
  };
  /** Compression settings */
  compression: {
    type: CompressionType;
  };
  /** Whether this is the default profile */
  isDefault: boolean;
  /** Whether this profile is active */
  isActive: boolean;
  /** Creation timestamp */
  createdAt: string;
  /** Last update timestamp */
  updatedAt: string;
  /** Tenant ID */
  tenantId: string;
}

/**
 * Connector configuration linking data sources to storage profiles.
 *
 * @doc.type interface
 * @doc.purpose Data connector definition
 * @doc.layer product
 */
export interface DataConnector {
  /** Unique identifier for the connector */
  id: string;
  /** User-friendly name */
  name: string;
  /** Source type (e.g., database, API, file system) */
  sourceType: string;
  /** Target storage profile ID */
  storageProfileId: string;
  /** Connection configuration */
  connectionConfig: Record<string, unknown>;
  /** Sync schedule (cron format) */
  syncSchedule?: string;
  /** Last successful sync timestamp */
  lastSyncAt?: string;
  /** Connection status */
  status: "active" | "inactive" | "error" | "testing";
  /** Status message */
  statusMessage?: string;
  /** Whether connector is enabled */
  isEnabled: boolean;
  /** Creation timestamp */
  createdAt: string;
  /** Last update timestamp */
  updatedAt: string;
  /** Tenant ID */
  tenantId: string;
}

/**
 * Storage profile form input.
 *
 * @doc.type interface
 * @doc.purpose Storage profile creation/update form
 * @doc.layer product
 */
export interface StorageProfileFormInput {
  name: string;
  type: StorageType;
  description?: string;
  config: Record<string, unknown>;
  encryption: {
    type: EncryptionType;
    keyId?: string;
  };
  compression: {
    type: CompressionType;
  };
  isDefault: boolean;
}

/**
 * Connector form input.
 *
 * @doc.type interface
 * @doc.purpose Data connector creation/update form
 * @doc.layer product
 */
export interface DataConnectorFormInput {
  name: string;
  sourceType: string;
  storageProfileId: string;
  connectionConfig: Record<string, unknown>;
  syncSchedule?: string;
  isEnabled: boolean;
}

/**
 * Storage capacity metrics.
 *
 * @doc.type interface
 * @doc.purpose Storage usage information
 * @doc.layer product
 */
export interface StorageMetrics {
  /** Profile ID */
  profileId: string;
  /** Total capacity in bytes */
  totalCapacity: number;
  /** Used capacity in bytes */
  usedCapacity: number;
  /** Available capacity in bytes */
  availableCapacity: number;
  /** Last updated timestamp */
  lastUpdated: string;
}

/**
 * Sync statistics for a connector.
 *
 * @doc.type interface
 * @doc.purpose Connector sync metrics
 * @doc.layer product
 */
export interface SyncStatistics {
  /** Connector ID */
  connectorId: string;
  /** Total records synced */
  totalRecords: number;
  /** Records synced in last sync */
  lastSyncRecords: number;
  /** Total sync duration in seconds */
  totalDuration: number;
  /** Last sync duration in seconds */
  lastSyncDuration: number;
  /** Number of errors during syncs */
  errorCount: number;
  /** Last error message */
  lastError?: string;
}
