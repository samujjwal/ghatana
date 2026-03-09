/**
 * Policy Engine - Core business logic for policy evaluation
 * NO PLATFORM-SPECIFIC CODE - runs identically on all platforms
 */

import { Policy, PolicyAction, TimeWindow, AppCategory } from '../types';

export class PolicyEngine {
  private policies: Map<string, Policy> = new Map();
  private categoryCache: Map<string, AppCategory> = new Map();

  /**
   * Load policies into the engine
   */
  loadPolicies(policies: Policy[]): void {
    this.policies.clear();
    policies.forEach(policy => {
      if (policy.enabled) {
        this.policies.set(policy.id, policy);
      }
    });
  }

  /**
   * Get all active policies
   */
  getPolicies(): Policy[] {
    return Array.from(this.policies.values());
  }

  /**
   * Evaluate if an app should be blocked
   * @returns PolicyAction indicating what action to take
   */
  evaluatePolicy(
    appId: string,
    currentTime: Date = new Date(),
    usageToday: number = 0
  ): { action: PolicyAction; policy?: Policy; reason?: string } {
    // Find all policies that apply to this app
    const applicablePolicies = this.findApplicablePolicies(appId);

    if (applicablePolicies.length === 0) {
      return { action: PolicyAction.ALLOW };
    }

    // Evaluate each policy (most restrictive wins)
    for (const policy of applicablePolicies) {
      const result = this.evaluateSinglePolicy(policy, appId, currentTime, usageToday);
      
      if (result.action === PolicyAction.BLOCK) {
        return result; // Block immediately if any policy blocks
      }
    }

    return { action: PolicyAction.ALLOW };
  }

  /**
   * Evaluate a single policy
   */
  private evaluateSinglePolicy(
    policy: Policy,
    appId: string,
    currentTime: Date,
    usageToday: number
  ): { action: PolicyAction; policy: Policy; reason?: string } {
    // Check time-based rules
    if (policy.timeWindows && policy.timeWindows.length > 0) {
      const timeResult = this.evaluateTimeWindows(policy.timeWindows, currentTime);
      
      if (timeResult.shouldBlock) {
        return {
          action: PolicyAction.BLOCK,
          policy,
          reason: policy.blockReason || `App blocked during restricted hours (${timeResult.windowInfo})`
        };
      }
    }

    // Check daily limit
    if (policy.dailyLimitMs && policy.dailyLimitMs > 0) {
      if (usageToday >= policy.dailyLimitMs) {
        return {
          action: PolicyAction.BLOCK,
          policy,
          reason: policy.blockReason || `Daily limit reached (${Math.floor(policy.dailyLimitMs / 60000)} minutes)`
        };
      }
    }

    // Check category-based rules
    if (policy.targetCategories && policy.targetCategories.length > 0) {
      const appCategory = this.getAppCategory(appId);
      
      if (policy.targetCategories.includes(appCategory)) {
        return {
          action: PolicyAction.BLOCK,
          policy,
          reason: policy.blockReason || `App category "${appCategory}" is blocked`
        };
      }
    }

    return { action: PolicyAction.ALLOW, policy };
  }

  /**
   * Evaluate time windows for current time
   */
  private evaluateTimeWindows(
    timeWindows: TimeWindow[],
    currentTime: Date
  ): { shouldBlock: boolean; windowInfo?: string } {
    const dayOfWeek = currentTime.getDay(); // 0-6
    const timeOfDay = currentTime.getHours() * 60 + currentTime.getMinutes(); // minutes from midnight

    for (const window of timeWindows) {
      // Check if current day is in window
      if (!window.daysOfWeek.includes(dayOfWeek)) {
        continue;
      }

      // Check if current time is in window
      const isInWindow = timeOfDay >= window.startMinutes && timeOfDay <= window.endMinutes;

      if (isInWindow) {
        if (window.isBlocked) {
          // Inside blocked window
          return {
            shouldBlock: true,
            windowInfo: this.formatTimeWindow(window)
          };
        } else {
          // Inside allowed window
          return { shouldBlock: false };
        }
      }
    }

    // Outside all windows - check if any window is "allow-only"
    const hasAllowOnlyWindows = timeWindows.some(w => !w.isBlocked);
    
    if (hasAllowOnlyWindows) {
      // If there are "allow-only" windows and we're outside them, block
      return {
        shouldBlock: true,
        windowInfo: 'Outside allowed hours'
      };
    }

    return { shouldBlock: false };
  }

  /**
   * Find all policies applicable to an app
   */
  private findApplicablePolicies(appId: string): Policy[] {
    const applicable: Policy[] = [];

    for (const policy of this.policies.values()) {
      // Check if app is directly targeted
      if (policy.targetApps.includes(appId)) {
        applicable.push(policy);
        continue;
      }

      // Check if app's category is targeted
      if (policy.targetCategories && policy.targetCategories.length > 0) {
        const appCategory = this.getAppCategory(appId);
        if (policy.targetCategories.includes(appCategory)) {
          applicable.push(policy);
        }
      }
    }

    return applicable;
  }

  /**
   * Get app category (with caching)
   */
  private getAppCategory(appId: string): AppCategory {
    if (this.categoryCache.has(appId)) {
      return this.categoryCache.get(appId)!;
    }

    // Category detection logic
    const category = this.detectCategory(appId);
    this.categoryCache.set(appId, category);
    
    return category;
  }

  /**
   * Detect app category from package name
   */
  private detectCategory(appId: string): AppCategory {
    const categoryMap: Record<string, AppCategory> = {
      // Social
      'com.facebook': AppCategory.SOCIAL,
      'com.instagram': AppCategory.SOCIAL,
      'com.twitter': AppCategory.SOCIAL,
      'com.snapchat': AppCategory.SOCIAL,
      'com.tiktok': AppCategory.SOCIAL,
      
      // Streaming
      'com.youtube': AppCategory.STREAMING,
      'com.netflix': AppCategory.STREAMING,
      'com.hulu': AppCategory.STREAMING,
      'com.spotify': AppCategory.STREAMING,
      
      // Gaming
      'com.roblox': AppCategory.GAMING,
      'com.minecraft': AppCategory.GAMING,
      'com.epicgames.fortnite': AppCategory.GAMING,
      
      // Education
      'com.khanacademy': AppCategory.EDUCATION,
      'com.duolingo': AppCategory.EDUCATION,
      
      // Communication
      'com.whatsapp': AppCategory.COMMUNICATION,
      'com.telegram': AppCategory.COMMUNICATION,
      'com.discord': AppCategory.COMMUNICATION,
    };

    return categoryMap[appId.toLowerCase()] || AppCategory.OTHER;
  }

  /**
   * Format time window for display
   */
  private formatTimeWindow(window: TimeWindow): string {
    const formatTime = (minutes: number): string => {
      const hours = Math.floor(minutes / 60);
      const mins = minutes % 60;
      return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`;
    };

    const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const days = window.daysOfWeek.map(d => dayNames[d]).join(', ');
    
    return `${days} ${formatTime(window.startMinutes)}-${formatTime(window.endMinutes)}`;
  }

  /**
   * Clear all policies
   */
  clear(): void {
    this.policies.clear();
    this.categoryCache.clear();
  }
}
