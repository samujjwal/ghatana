/**
 * Repository interface for security anomaly data access.
 *
 * <p><b>Purpose</b><br>
 * Abstraction for persisting and querying detected security anomalies.
 * Enables independent testing via in-memory implementations.
 *
 * <p><b>Query Patterns</b><br>
 * - Find recent anomalies (time range based)
 * - Filter by type and severity
 * - Query by status (DETECTED, INVESTIGATING, etc.)
 * - Aggregate anomalies by type/severity
 *
 * <p><b>Implementation Notes</b><br>
 * - All methods return Promises for async/await composition
 * - Queries are immutable (no side effects)
 * - Supports pagination for large result sets
 * - All operations are type-safe with full TypeScript support
 *
 * @doc.type interface
 * @doc.purpose Data access abstraction for security anomalies
 * @doc.layer product
 * @doc.pattern Repository
 */

import {
  SecurityAnomaly,
  AnomalyType,
  AnomalyStatus,
} from "../models/anomaly/SecurityAnomaly.entity";

/**
 * Query filters for anomaly searches.
 */
export interface AnomalyFilters {
  readonly types?: readonly AnomalyType[];
  readonly statuses?: readonly AnomalyStatus[];
  readonly minSeverity?: number; // 0.0-1.0
  readonly maxSeverity?: number; // 0.0-1.0
  readonly startDate?: Date;
  readonly endDate?: Date;
  readonly resourceIds?: readonly string[];
}

/**
 * Pagination options for large result sets.
 */
export interface PaginationOptions {
  readonly limit: number;
  readonly offset: number;
}

/**
 * Repository interface for SecurityAnomaly persistence.
 */
export interface SecurityAnomalyRepository {
  /**
   * Saves a security anomaly.
   *
   * @param anomaly SecurityAnomaly to save
   * @returns Promise resolving when save completes
   */
  save(anomaly: SecurityAnomaly): Promise<void>;

  /**
   * Retrieves anomaly by ID.
   *
   * @param id Anomaly ID
   * @returns Promise resolving to SecurityAnomaly or null if not found
   */
  findById(id: string): Promise<SecurityAnomaly | null>;

  /**
   * Finds anomalies by type.
   *
   * @param type AnomalyType to query
   * @param pagination Optional pagination
   * @returns Promise resolving to array of matching anomalies
   */
  findByType(
    type: AnomalyType,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]>;

  /**
   * Finds anomalies by status.
   *
   * @param status AnomalyStatus to query
   * @param pagination Optional pagination
   * @returns Promise resolving to array of matching anomalies
   */
  findByStatus(
    status: AnomalyStatus,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]>;

  /**
   * Finds recent anomalies within date range.
   *
   * @param startDate Start of range
   * @param endDate End of range
   * @param pagination Optional pagination
   * @returns Promise resolving to anomalies in range
   */
  findByDateRange(
    startDate: Date,
    endDate: Date,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]>;

  /**
   * Finds anomalies by severity threshold.
   *
   * @param minSeverity Minimum severity (0.0-1.0)
   * @param pagination Optional pagination
   * @returns Promise resolving to anomalies above threshold
   */
  findBySeverity(
    minSeverity: number,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]>;

  /**
   * Queries with complex filters.
   *
   * @param filters Filter criteria
   * @param pagination Optional pagination
   * @returns Promise resolving to matching anomalies
   */
  query(
    filters: AnomalyFilters,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]>;

  /**
   * Counts anomalies matching filters.
   *
   * @param filters Filter criteria
   * @returns Promise resolving to count
   */
  count(filters: AnomalyFilters): Promise<number>;

  /**
   * Retrieves most recent anomalies.
   *
   * @param limit Number of anomalies to return
   * @returns Promise resolving to recent anomalies
   */
  findRecent(limit: number): Promise<SecurityAnomaly[]>;

  /**
   * Aggregates anomaly counts by type.
   *
   * @param filters Optional filters
   * @returns Promise resolving to map of type -> count
   */
  countByType(filters?: AnomalyFilters): Promise<Map<AnomalyType, number>>;

  /**
   * Aggregates anomaly counts by status.
   *
   * @param filters Optional filters
   * @returns Promise resolving to map of status -> count
   */
  countByStatus(filters?: AnomalyFilters): Promise<Map<AnomalyStatus, number>>;

  /**
   * Updates anomaly (replaces existing).
   *
   * @param anomaly Updated anomaly
   * @returns Promise resolving when update completes
   */
  update(anomaly: SecurityAnomaly): Promise<void>;

  /**
   * Deletes anomaly by ID.
   *
   * @param id Anomaly ID to delete
   * @returns Promise resolving to true if deleted, false if not found
   */
  delete(id: string): Promise<boolean>;

  /**
   * Clears all anomalies (for testing).
   *
   * @returns Promise resolving when clear completes
   */
  clear(): Promise<void>;
}

/**
 * In-memory implementation of SecurityAnomalyRepository for testing.
 *
 * <p><b>Purpose</b><br>
 * Provides a fully functional repository implementation without external
 * dependencies, suitable for unit testing and development.
 *
 * <p><b>Storage</b><br>
 * Uses Map for O(1) lookups by ID and in-memory arrays for filtered queries.
 *
 * <p><b>Thread Safety</b><br>
 * Not thread-safe; suitable for testing only.
 *
 * @doc.type class
 * @doc.purpose In-memory anomaly repository implementation for testing
 * @doc.layer product
 * @doc.pattern Repository
 */
export class InMemorySecurityAnomalyRepository
  implements SecurityAnomalyRepository
{
  private readonly _anomalies: Map<string, SecurityAnomaly> = new Map();

  /**
   * Saves a security anomaly.
   *
   * @param anomaly SecurityAnomaly to save
   * @returns Promise resolving immediately
   */
  async save(anomaly: SecurityAnomaly): Promise<void> {
    this._anomalies.set(anomaly.id, anomaly);
  }

  /**
   * Retrieves anomaly by ID.
   *
   * @param id Anomaly ID
   * @returns Anomaly or null
   */
  async findById(id: string): Promise<SecurityAnomaly | null> {
    return this._anomalies.get(id) || null;
  }

  /**
   * Finds anomalies by type.
   *
   * @param type AnomalyType to query
   * @param pagination Optional pagination
   * @returns Matching anomalies
   */
  async findByType(
    type: AnomalyType,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]> {
    let results = Array.from(this._anomalies.values()).filter(
      (a) => a.type === type
    );

    if (pagination) {
      results = results.slice(pagination.offset, pagination.offset + pagination.limit);
    }

    return results;
  }

  /**
   * Finds anomalies by status.
   *
   * @param status AnomalyStatus to query
   * @param pagination Optional pagination
   * @returns Matching anomalies
   */
  async findByStatus(
    status: AnomalyStatus,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]> {
    let results = Array.from(this._anomalies.values()).filter(
      (a) => a.status === status
    );

    if (pagination) {
      results = results.slice(pagination.offset, pagination.offset + pagination.limit);
    }

    return results;
  }

  /**
   * Finds anomalies by date range.
   *
   * @param startDate Start of range
   * @param endDate End of range
   * @param pagination Optional pagination
   * @returns Anomalies in range
   */
  async findByDateRange(
    startDate: Date,
    endDate: Date,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]> {
    let results = Array.from(this._anomalies.values()).filter((a) => {
      const detectedAt = a.detectedAt.getTime();
      return detectedAt >= startDate.getTime() && detectedAt <= endDate.getTime();
    });

    if (pagination) {
      results = results.slice(pagination.offset, pagination.offset + pagination.limit);
    }

    return results;
  }

  /**
   * Finds anomalies by severity threshold.
   *
   * @param minSeverity Minimum severity (0.0-1.0)
   * @param pagination Optional pagination
   * @returns Anomalies above threshold
   */
  async findBySeverity(
    minSeverity: number,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]> {
    let results = Array.from(this._anomalies.values()).filter(
      (a) => a.severity >= minSeverity
    );

    if (pagination) {
      results = results.slice(pagination.offset, pagination.offset + pagination.limit);
    }

    return results;
  }

  /**
   * Queries with complex filters.
   *
   * @param filters Filter criteria
   * @param pagination Optional pagination
   * @returns Matching anomalies
   */
  async query(
    filters: AnomalyFilters,
    pagination?: PaginationOptions
  ): Promise<SecurityAnomaly[]> {
    let results = Array.from(this._anomalies.values());

    // Apply type filter
    if (filters.types && filters.types.length > 0) {
      results = results.filter((a) =>
        filters.types!.includes(a.type)
      );
    }

    // Apply status filter
    if (filters.statuses && filters.statuses.length > 0) {
      results = results.filter((a) =>
        filters.statuses!.includes(a.status)
      );
    }

    // Apply severity filter
    if (filters.minSeverity !== undefined) {
      results = results.filter((a) => a.severity >= filters.minSeverity!);
    }
    if (filters.maxSeverity !== undefined) {
      results = results.filter((a) => a.severity <= filters.maxSeverity!);
    }

    // Apply date range filter
    if (filters.startDate && filters.endDate) {
      results = results.filter((a) => {
        const detectedAt = a.detectedAt.getTime();
        return (
          detectedAt >= filters.startDate!.getTime() &&
          detectedAt <= filters.endDate!.getTime()
        );
      });
    }

    // Apply resource ID filter
    if (filters.resourceIds && filters.resourceIds.length > 0) {
      results = results.filter((a) =>
        filters.resourceIds!.some((rid) =>
          a.relatedResourceIds.includes(rid)
        )
      );
    }

    // Apply pagination
    if (pagination) {
      results = results.slice(pagination.offset, pagination.offset + pagination.limit);
    }

    return results;
  }

  /**
   * Counts anomalies matching filters.
   *
   * @param filters Filter criteria
   * @returns Count
   */
  async count(filters: AnomalyFilters): Promise<number> {
    return (await this.query(filters)).length;
  }

  /**
   * Retrieves most recent anomalies.
   *
   * @param limit Number of anomalies to return
   * @returns Recent anomalies
   */
  async findRecent(limit: number): Promise<SecurityAnomaly[]> {
    const sorted = Array.from(this._anomalies.values()).sort((a, b) =>
      b.detectedAt.getTime() - a.detectedAt.getTime()
    );
    return sorted.slice(0, limit);
  }

  /**
   * Aggregates anomaly counts by type.
   *
   * @param filters Optional filters
   * @returns Map of type -> count
   */
  async countByType(filters?: AnomalyFilters): Promise<Map<AnomalyType, number>> {
    const results = await this.query(filters || {});
    const counts = new Map<AnomalyType, number>();

    for (const anomaly of results) {
      counts.set(anomaly.type, (counts.get(anomaly.type) || 0) + 1);
    }

    return counts;
  }

  /**
   * Aggregates anomaly counts by status.
   *
   * @param filters Optional filters
   * @returns Map of status -> count
   */
  async countByStatus(filters?: AnomalyFilters): Promise<Map<AnomalyStatus, number>> {
    const results = await this.query(filters || {});
    const counts = new Map<AnomalyStatus, number>();

    for (const anomaly of results) {
      counts.set(anomaly.status, (counts.get(anomaly.status) || 0) + 1);
    }

    return counts;
  }

  /**
   * Updates anomaly (replaces existing).
   *
   * @param anomaly Updated anomaly
   * @returns Promise resolving when update completes
   */
  async update(anomaly: SecurityAnomaly): Promise<void> {
    this._anomalies.set(anomaly.id, anomaly);
  }

  /**
   * Deletes anomaly by ID.
   *
   * @param id Anomaly ID to delete
   * @returns true if deleted, false if not found
   */
  async delete(id: string): Promise<boolean> {
    return this._anomalies.delete(id);
  }

  /**
   * Clears all anomalies.
   *
   * @returns Promise resolving immediately
   */
  async clear(): Promise<void> {
    this._anomalies.clear();
  }
}
