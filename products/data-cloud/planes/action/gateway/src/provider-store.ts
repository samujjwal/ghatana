/**
 * Provider store port for Kernel lifecycle provider data persistence.
 *
 * @doc.type module
 * @doc.purpose Durable storage abstraction for Kernel provider records
 * @doc.layer product
 * @doc.pattern Repository Port
 */

export interface ProviderRecord {
  readonly id: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly providerType: string;
  readonly providerRef: string;
  readonly data: unknown;
  readonly privacyClassification?: string;
  readonly expiresAt?: string;
  readonly createdAt: string;
  readonly createdBy: string;
}

export interface ProviderScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export interface ProviderStorePort {
  save(record: ProviderRecord): Promise<ProviderRecord>;
  findByRef(scope: ProviderScope, providerRef: string): Promise<ProviderRecord | null>;
  findLatestByProviderType(
    scope: ProviderScope,
    providerType: string,
    filters: Record<string, unknown>
  ): Promise<ProviderRecord | null>;
  listByProviderType(
    scope: ProviderScope,
    providerType: string,
    filters: Record<string, unknown>,
    limit: number
  ): Promise<readonly ProviderRecord[]>;
  deleteExpired(scope: ProviderScope): Promise<number>;
  deleteByRef(scope: ProviderScope, providerRef: string): Promise<boolean>;
  countByProviderType(scope: ProviderScope, providerType: string): Promise<number>;
}

/**
 * In-memory implementation for testing (not production-ready).
 */
export class InMemoryProviderStore implements ProviderStorePort {
  private readonly storage = new Map<string, ProviderRecord>();

  async save(record: ProviderRecord): Promise<ProviderRecord> {
    this.storage.set(record.providerRef, record);
    return record;
  }

  async findByRef(scope: ProviderScope, providerRef: string): Promise<ProviderRecord | null> {
    const record = this.storage.get(providerRef);
    if (!record) return null;
    if (!this.matchesScope(record, scope)) return null;
    if (this.isExpired(record)) return null;
    return record;
  }

  async findLatestByProviderType(
    scope: ProviderScope,
    providerType: string,
    filters: Record<string, unknown>
  ): Promise<ProviderRecord | null> {
    const records = await this.listByProviderType(scope, providerType, filters, 100);
    return records.length > 0 ? records[0] : null;
  }

  async listByProviderType(
    scope: ProviderScope,
    providerType: string,
    filters: Record<string, unknown>,
    limit: number
  ): Promise<readonly ProviderRecord[]> {
    const result: ProviderRecord[] = [];
    for (const record of this.storage.values()) {
      if (!this.matchesScope(record, scope)) continue;
      if (record.providerType !== providerType) continue;
      if (this.isExpired(record)) continue;
      if (this.matchesFilters(record.data, filters)) {
        result.push(record);
      }
    }
    // Sort by creation time descending (newest first)
    result.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return result.slice(0, limit);
  }

  async deleteExpired(scope: ProviderScope): Promise<number> {
    let count = 0;
    const now = new Date().toISOString();
    for (const [ref, record] of this.storage.entries()) {
      if (this.matchesScope(record, scope) && record.expiresAt && record.expiresAt < now) {
        this.storage.delete(ref);
        count++;
      }
    }
    return count;
  }

  async deleteByRef(scope: ProviderScope, providerRef: string): Promise<boolean> {
    const record = this.storage.get(providerRef);
    if (!record) return false;
    if (!this.matchesScope(record, scope)) return false;
    this.storage.delete(providerRef);
    return true;
  }

  async countByProviderType(scope: ProviderScope, providerType: string): Promise<number> {
    let count = 0;
    for (const record of this.storage.values()) {
      if (this.matchesScope(record, scope) && record.providerType === providerType && !this.isExpired(record)) {
        count++;
      }
    }
    return count;
  }

  clear(): void {
    this.storage.clear();
  }

  private isExpired(record: ProviderRecord): boolean {
    return record.expiresAt !== undefined && new Date(record.expiresAt) < new Date();
  }

  private matchesScope(record: ProviderRecord, scope: ProviderScope): boolean {
    return (
      record.tenantId === scope.tenantId &&
      record.workspaceId === scope.workspaceId &&
      record.projectId === scope.projectId
    );
  }

  private matchesFilters(data: unknown, filters: Record<string, unknown>): boolean {
    if (Object.keys(filters).length === 0) return true;
    if (typeof data !== 'object' || data === null) return false;
    const record = data as Record<string, unknown>;
    for (const [key, value] of Object.entries(filters)) {
      const directValue = record[key];
      const metadataValue =
        typeof record.metadata === 'object' && record.metadata !== null
          ? (record.metadata as Record<string, unknown>)[key]
          : undefined;
      if (directValue !== value && metadataValue !== value) return false;
    }
    return true;
  }
}
