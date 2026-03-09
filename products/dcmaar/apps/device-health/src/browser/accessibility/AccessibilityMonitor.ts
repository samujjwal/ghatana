/**
 * @fileoverview Accessibility Monitor
 *
 * Provides lightweight wrapper around axe-core (when available)
 * to run periodic accessibility audits within content scripts.
 *
 * @module browser/accessibility/AccessibilityMonitor
 */

export interface AccessibilityViolation {
  id: string;
  impact?: string;
  description?: string;
  helpUrl?: string;
  nodes?: Array<Record<string, unknown>>;
}

export interface AccessibilityResult {
  url: string;
  timestamp: number;
  violations: AccessibilityViolation[];
  raw?: unknown;
}

type ViolationListener = (violations: AccessibilityViolation[]) => void;

/**
 * Accessibility Monitor using axe-core (if present)
 */
export class AccessibilityMonitor {
  private axeCore: any;
  private auditInterval?: ReturnType<typeof setInterval>;
  private listeners = new Set<ViolationListener>();

  /**
   * Run an accessibility audit
   */
  async runAudit(url?: string): Promise<AccessibilityResult | undefined> {
    if (!this.isInPageContext()) {
      return undefined;
    }

    await this.ensureAxeCore();

    const targetUrl = url ?? window.location.href;
    const axe = this.axeCore;

    if (!axe || typeof axe.run !== 'function') {
      return {
        url: targetUrl,
        timestamp: Date.now(),
        violations: [],
      };
    }

    try {
      const results = await axe.run(document);
      const violations: AccessibilityViolation[] = (results?.violations ?? []).map(
        (violation: any) => ({
          id: violation.id,
          impact: violation.impact,
          description: violation.description,
          helpUrl: violation.helpUrl,
          nodes: violation.nodes,
        })
      );

      if (violations.length > 0) {
        this.listeners.forEach((listener) => listener(violations));
      }

      return {
        url: targetUrl,
        timestamp: Date.now(),
        violations,
        raw: results,
      };
    } catch (error) {
      console.warn('[AccessibilityMonitor] Audit failed:', error);
      return {
        url: targetUrl,
        timestamp: Date.now(),
        violations: [],
      };
    }
  }

  /**
   * Start periodic audits
   */
  startPeriodicAudits(intervalMs: number): void {
    if (!this.isInPageContext()) {
      return;
    }

    this.stopPeriodicAudits();

    this.auditInterval = setInterval(() => {
      void this.runAudit().catch((error) => {
        console.warn('[AccessibilityMonitor] Periodic audit failed:', error);
      });
    }, intervalMs);
  }

  /**
   * Stop periodic audits
   */
  stopPeriodicAudits(): void {
    if (this.auditInterval) {
      clearInterval(this.auditInterval);
      this.auditInterval = undefined;
    }
  }

  /**
   * Inject axe-core if available
   */
  async injectAxeCore(): Promise<void> {
    await this.ensureAxeCore();
  }

  /**
   * Is axe-core present
   */
  isAxeCoreAvailable(): boolean {
    return Boolean(this.axeCore || (globalThis as { axe?: unknown }).axe);
  }

  /**
   * Register violation listener
   */
  onViolationFound(listener: ViolationListener): void {
    this.listeners.add(listener);
  }

  /**
   * Remove listener
   */
  offViolationFound(listener: ViolationListener): void {
    this.listeners.delete(listener);
  }

  /**
   * Check if running in page context
   */
  private isInPageContext(): boolean {
    try {
      return typeof window !== 'undefined' && typeof document !== 'undefined';
    } catch {
      return false;
    }
  }

  /**
   * Ensure axe-core is available
   */
  private async ensureAxeCore(): Promise<void> {
    if (!this.isInPageContext()) {
      return;
    }

    if (this.axeCore) {
      return;
    }

    const globalWithAxe = globalThis as { axe?: unknown };
    if (globalWithAxe.axe) {
      this.axeCore = globalWithAxe.axe;
      return;
    }

    // Axe is optional; if not available we log once
    if (!this.axeCore) {
      console.warn(
        '[AccessibilityMonitor] axe-core not detected. Provide axe-core via content script to enable audits.'
      );
    }
  }
}
