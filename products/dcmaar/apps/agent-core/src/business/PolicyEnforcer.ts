/**
 * Policy Enforcer - Enforces policies by triggering blocking actions
 * NO PLATFORM-SPECIFIC CODE - delegates to platform implementations
 */

import { PolicyEngine } from './PolicyEngine';
import { UsageTracker } from './UsageTracker';
import { AppInfo, PolicyAction, BlockEvent } from '../types';

export interface IBlockingService {
  showBlockingOverlay(appName: string, reason: string): Promise<void>;
  dismissBlockingOverlay(): Promise<void>;
}

export class PolicyEnforcer {
  private policyEngine: PolicyEngine;
  private usageTracker: UsageTracker;
  private blockingService?: IBlockingService;
  private blockEvents: BlockEvent[] = [];
  private isActive: boolean = false;

  constructor(
    policyEngine: PolicyEngine,
    usageTracker: UsageTracker,
    blockingService?: IBlockingService
  ) {
    this.policyEngine = policyEngine;
    this.usageTracker = usageTracker;
    this.blockingService = blockingService;
  }

  /**
   * Start enforcing policies
   */
  start(): void {
    this.isActive = true;
  }

  /**
   * Stop enforcing policies
   */
  stop(): void {
    this.isActive = false;
    this.blockingService?.dismissBlockingOverlay();
  }

  /**
   * Check if enforcement is active
   */
  isEnforcementActive(): boolean {
    return this.isActive;
  }

  /**
   * Enforce policy for an app
   * Called when app becomes foreground
   */
  async enforcePolicy(app: AppInfo): Promise<boolean> {
    if (!this.isActive) {
      return false; // Not blocking
    }

    // Get current usage for the app
    const usageToday = this.usageTracker.getTodayUsage(app.packageName);

    // Evaluate policy
    const result = this.policyEngine.evaluatePolicy(
      app.packageName,
      new Date(),
      usageToday
    );

    if (result.action === PolicyAction.BLOCK) {
      await this.blockApp(app, result.reason || 'App blocked by policy');
      return true; // Blocking
    } else {
      await this.allowApp(app);
      return false; // Not blocking
    }
  }

  /**
   * Block an app
   */
  private async blockApp(app: AppInfo, reason: string): Promise<void> {
    // Record block event
    const blockEvent: BlockEvent = {
      id: this.generateEventId(),
      appId: app.packageName,
      appName: app.appName,
      policyId: '', // TODO: Get from evaluation result
      reason,
      timestamp: Date.now()
    };

    this.blockEvents.push(blockEvent);

    // Show blocking overlay if service available
    if (this.blockingService) {
      await this.blockingService.showBlockingOverlay(app.appName, reason);
    }
  }

  /**
   * Allow an app (dismiss any blocking overlay)
   */
  private async allowApp(_app: AppInfo): Promise<void> {
    if (this.blockingService) {
      await this.blockingService.dismissBlockingOverlay();
    }
  }

  /**
   * Get all block events
   */
  getBlockEvents(): BlockEvent[] {
    return [...this.blockEvents];
  }

  /**
   * Clear block events (after sync)
   */
  clearBlockEvents(): void {
    this.blockEvents = [];
  }

  /**
   * Generate unique event ID
   */
  private generateEventId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}
