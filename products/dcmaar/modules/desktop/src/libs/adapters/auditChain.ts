/**
 * Hash-chained audit log for tamper-evident command history.
 * Each entry includes hash of previous entry (Merkle chain).
 */

export interface AuditEntry {
  id: string;
  timestamp: string;
  action: string;
  actor: string;
  details: unknown;
  previousHash?: string;
  hash?: string;
}

export interface AuditChainConfig {
  storageKey: string;
  maxEntries?: number;
}

export class AuditChain {
  private config: AuditChainConfig;
  private entries: AuditEntry[] = [];

  constructor(config: AuditChainConfig) {
    this.config = {
      maxEntries: 10000,
      ...config,
    };
    this.loadFromStorage();
  }

  async append(entry: Omit<AuditEntry, 'id' | 'previousHash' | 'hash'>): Promise<AuditEntry> {
    const lastEntry = this.entries[this.entries.length - 1];
    const previousHash = lastEntry?.hash;

    const fullEntry: AuditEntry = {
      ...entry,
      id: this.generateId(),
      previousHash,
    };

    fullEntry.hash = await this.computeHash(fullEntry);
    this.entries.push(fullEntry);

    if (this.entries.length > (this.config.maxEntries ?? 10000)) {
      this.entries.shift(); // Remove oldest
    }

    this.saveToStorage();
    return fullEntry;
  }

  verify(): { valid: boolean; brokenAt?: number } {
    for (let i = 1; i < this.entries.length; i++) {
      const entry = this.entries[i];
      const previous = this.entries[i - 1];

      if (entry.previousHash !== previous.hash) {
        return { valid: false, brokenAt: i };
      }
    }

    return { valid: true };
  }

  getEntries(limit?: number): AuditEntry[] {
    if (limit) {
      return this.entries.slice(-limit);
    }
    return [...this.entries];
  }

  export(format: 'json' | 'csv' | 'ndjson'): string {
    switch (format) {
      case 'json':
        return JSON.stringify(this.entries, null, 2);
      case 'csv':
        return this.exportCsv();
      case 'ndjson':
        return this.entries.map((e) => JSON.stringify(e)).join('\n');
      default:
        throw new Error(`Unsupported format: ${format}`);
    }
  }

  private async computeHash(entry: AuditEntry): Promise<string> {
    const data = JSON.stringify({
      id: entry.id,
      timestamp: entry.timestamp,
      action: entry.action,
      actor: entry.actor,
      details: entry.details,
      previousHash: entry.previousHash,
    });

    const buffer = new TextEncoder().encode(data);
    const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
  }

  private loadFromStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }

    try {
      const stored = localStorage.getItem(this.config.storageKey);
      if (stored) {
        this.entries = JSON.parse(stored);
      }
    } catch (error) {
      console.error('Failed to load audit chain:', error);
    }
  }

  private saveToStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }

    try {
      localStorage.setItem(this.config.storageKey, JSON.stringify(this.entries));
    } catch (error) {
      console.error('Failed to save audit chain:', error);
    }
  }

  private exportCsv(): string {
    const headers = ['id', 'timestamp', 'action', 'actor', 'previousHash', 'hash'];
    const rows = this.entries.map((e) => [
      e.id,
      e.timestamp,
      e.action,
      e.actor,
      e.previousHash ?? '',
      e.hash ?? '',
    ]);

    return [
      headers.join(','),
      ...rows.map((r) => r.map((c) => `"${c}"`).join(',')),
    ].join('\n');
  }
}

export const createAuditChain = (config: AuditChainConfig): AuditChain => {
  return new AuditChain(config);
};
