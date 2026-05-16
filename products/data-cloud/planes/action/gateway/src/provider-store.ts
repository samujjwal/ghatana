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

export interface ProviderStorePort {
  save(record: ProviderRecord): Promise<ProviderRecord>;
  findByRef(tenantId: string, providerRef: string): Promise<ProviderRecord | null>;
  findLatestByProviderType(
    tenantId: string,
    providerType: string,
    filters: Record<string, unknown>
  ): Promise<ProviderRecord | null>;
  listByProviderType(
    tenantId: string,
    providerType: string,
    filters: Record<string, unknown>,
    limit: number
  ): Promise<readonly ProviderRecord[]>;
  deleteExpired(tenantId: string): Promise<number>;
  deleteByRef(tenantId: string, providerRef: string): Promise<boolean>;
  countByProviderType(tenantId: string, providerType: string): Promise<number>;
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

  async findByRef(tenantId: string, providerRef: string): Promise<ProviderRecord | null> {
    const record = this.storage.get(providerRef);
    if (!record) return null;
    if (record.tenantId !== tenantId) return null;
    if (this.isExpired(record)) return null;
    return record;
  }

  async findLatestByProviderType(
    tenantId: string,
    providerType: string,
    filters: Record<string, unknown>
  ): Promise<ProviderRecord | null> {
    const records = await this.listByProviderType(tenantId, providerType, filters, 100);
    return records.length > 0 ? records[0] : null;
  }

  async listByProviderType(
    tenantId: string,
    providerType: string,
    filters: Record<string, unknown>,
    limit: number
  ): Promise<readonly ProviderRecord[]> {
    const result: ProviderRecord[] = [];
    for (const record of this.storage.values()) {
      if (record.tenantId !== tenantId) continue;
      if (record.providerType !== providerType) continue;
      if (this.isExpired(record)) continue;
      if (this.matchesFilters(record.data, filters)) {
        result.push(record);
        if (result.length >= limit) break;
      }
    }
    // Sort by creation time descending (newest first)
    result.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return result;
  }

  async deleteExpired(tenantId: string): Promise<number> {
    let count = 0;
    const now = new Date().toISOString();
    for (const [ref, record] of this.storage.entries()) {
      if (record.tenantId === tenantId && record.expiresAt && record.expiresAt < now) {
        this.storage.delete(ref);
        count++;
      }
    }
    return count;
  }

  async deleteByRef(tenantId: string, providerRef: string): Promise<boolean> {
    const record = this.storage.get(providerRef);
    if (!record) return false;
    if (record.tenantId !== tenantId) return false;
    this.storage.delete(providerRef);
    return true;
  }

  async countByProviderType(tenantId: string, providerType: string): Promise<number> {
    let count = 0;
    for (const record of this.storage.values()) {
      if (record.tenantId === tenantId && record.providerType === providerType && !this.isExpired(record)) {
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

  private matchesFilters(data: unknown, filters: Record<string, unknown>): boolean {
    if (Object.keys(filters).length === 0) return true;
    if (typeof data !== 'object' || data === null) return false;
    const record = data as Record<string, unknown>;
    for (const [key, value] of Object.entries(filters)) {
      if (record[key] !== value) return false;
    }
    return true;
  }
}
