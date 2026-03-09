/**
 * @fileoverview Storage Reporter
 *
 * Stores report data locally in browser storage or IndexedDB
 *
 * @module pipeline/reporters
 * @since 2.0.0
 */

import type { ProcessReporter } from '../ProcessManager';
import type { ProcessExecutionContext, ReportDestination } from '../../contracts/process';
import browser from 'webextension-polyfill';

/**
 * Storage reporter
 */
export class StorageReporter implements ProcessReporter {
  private indexedDbPromise?: Promise<IDBDatabase>;

  /**
   * Reports data by storing it locally
   */
  async report(
    data: any,
    destination: ReportDestination,
    context: ProcessExecutionContext
  ): Promise<void> {
    const { config, format } = destination;
    const {
      storageType = 'local',
      key = 'process-reports',
      maxReports = 100,
    } = config;

    try {
      // Format data
      const formattedData = this.formatData(data, format);

      // Store based on type
      if (storageType === 'local') {
        await this.storeInBrowserStorage(key, formattedData, maxReports);
      } else if (storageType === 'indexeddb') {
        await this.storeInIndexedDB(key, formattedData, maxReports);
      }

      context.logger.debug('Report stored successfully', {
        destination: destination.id,
        storageType,
      });
    } catch (error) {
      context.logger.error('Report storage failed', { error });
      throw error;
    }
  }

  /**
   * Formats data according to format type
   */
  private formatData(data: any, format: string): any {
    switch (format) {
      case 'json':
        return JSON.stringify(data, null, 2);
      case 'csv':
        return this.convertToCSV(data);
      case 'markdown':
        return this.convertToMarkdown(data);
      default:
        return data;
    }
  }

  /**
   * Stores in browser.storage.local
   */
  private async storeInBrowserStorage(
    key: string,
    data: any,
    maxReports: number
  ): Promise<void> {
    const stored = await browser.storage.local.get(key);
    const reports = stored[key] || [];

    reports.push({
      timestamp: Date.now(),
      data,
    });

    // Keep only maxReports
    if (reports.length > maxReports) {
      reports.splice(0, reports.length - maxReports);
    }

    await browser.storage.local.set({ [key]: reports });
  }

  /**
   * Stores in IndexedDB
   */
  private async storeInIndexedDB(
    key: string,
    data: any,
    maxReports: number
  ): Promise<void> {
    if (typeof indexedDB === 'undefined') {
      await this.storeInBrowserStorage(key, data, maxReports);
      return;
    }

    const db = await this.getDatabase();
    const tx = db.transaction('reports', 'readwrite');
    const store = tx.objectStore('reports');

    const existingRecord = await this.getRecord(store, key);
    const reports = existingRecord?.reports ?? [];

    reports.push({
      timestamp: Date.now(),
      data,
    });

    if (reports.length > maxReports) {
      reports.splice(0, reports.length - maxReports);
    }

    await this.putRecord(store, { key, reports });
    await this.waitForTransaction(tx);
  }

  /**
   * Converts data to CSV
   */
  private convertToCSV(data: any): string {
    if (!Array.isArray(data)) {
      data = [data];
    }

    if (data.length === 0) return '';

    const headers = Object.keys(data[0]);
    const rows = data.map((row: any) =>
      headers.map((header) => JSON.stringify(row[header] || '')).join(',')
    );

    return [headers.join(','), ...rows].join('\n');
  }

  /**
   * Converts data to Markdown
   */
  private convertToMarkdown(data: any): string {
    if (typeof data === 'object') {
      return '```json\n' + JSON.stringify(data, null, 2) + '\n```';
    }
    return String(data);
  }

  /**
   * Lazy open IndexedDB database
   */
  private getDatabase(): Promise<IDBDatabase> {
    if (!this.indexedDbPromise) {
      this.indexedDbPromise = new Promise<IDBDatabase>((resolve, reject) => {
        try {
          const request = indexedDB.open('dcmaar-reports', 1);

          request.onerror = () => {
            reject(request.error ?? new Error('Failed to open IndexedDB'));
          };

          request.onupgradeneeded = () => {
            const db = request.result;
            if (!db.objectStoreNames.contains('reports')) {
              db.createObjectStore('reports', { keyPath: 'key' });
            }
          };

          request.onsuccess = () => resolve(request.result);
        } catch (error) {
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      });
    }

    return this.indexedDbPromise;
  }

  /**
   * Retrieve record by key
   */
  private getRecord(
    store: IDBObjectStore,
    key: string
  ): Promise<{ key: string; reports: Array<{ timestamp: number; data: unknown }> } | undefined> {
    return new Promise((resolve, reject) => {
      const request = store.get(key);
      request.onsuccess = () => resolve(request.result as any);
      request.onerror = () => reject(request.error ?? new Error('IndexedDB get failed'));
    });
  }

  /**
   * Persist record
   */
  private putRecord(
    store: IDBObjectStore,
    record: { key: string; reports: Array<{ timestamp: number; data: unknown }> }
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const request = store.put(record);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error ?? new Error('IndexedDB put failed'));
    });
  }

  /**
   * Wait for transaction completion
   */
  private waitForTransaction(tx: IDBTransaction): Promise<void> {
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error('IndexedDB transaction error'));
      tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction aborted'));
    });
  }
}
