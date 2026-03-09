/**
 * EXT-E3-T01 — Options UI (Stub)
 *
 * Legacy UI component - marked for replacement in Phase 6
 * Maintained for backward compatibility only
 */

import browser from 'webextension-polyfill';

export type OptionsConfig = Record<string, unknown>;

export const optionsManager = {
  getConfig: (): OptionsConfig => ({}),
  updateConfig: (_updates: Partial<OptionsConfig>): void => {},
  exportConfig: (): string => '{}',
  importConfig: (_configJson: string): void => {},
  resetConfig: (): void => {},
};

export class OptionsManager {
  private config: OptionsConfig = {};
  private initPromise: Promise<void>;

  constructor() {
    this.initPromise = this.initialize();
  }

  private async initialize(): Promise<void> {
    try {
      this.config = {};
    } catch (error) {
      console.warn('[DCMAAR Options] Stub implementation - no config loaded', error);
    }
  }

  async ready(): Promise<void> {
    await this.initPromise;
  }

  getConfig(): OptionsConfig {
    return this.config;
  }

  updateConfig(_updates: Partial<OptionsConfig>): void {
    // Stub - no-op
  }

  exportConfig(): string {
    return JSON.stringify(this.config, null, 2);
  }

  importConfig(_configJson: string): void {
    // Stub - no-op
  }

  resetConfig(): void {
    this.config = {};
  }

  static async createHtmlOptions(): Promise<string> {
    return '<div>Options UI - Placeholder</div>';
  }
}

export async function initOptions(): Promise<void> {
  // Stub implementation
}

export function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  };
  return text.replace(/[&<>"']/g, (m) => map[m] || m);
}
