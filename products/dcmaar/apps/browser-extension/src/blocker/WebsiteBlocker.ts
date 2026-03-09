/**
 * @fileoverview Website Blocker Module
 *
 * Implements category-based website blocking for Guardian parental controls.
 * Blocks websites based on:
 * - Category (social, gaming, streaming, adult, etc.)
 * - Time windows (e.g., block during school hours)
 * - Whitelist/blacklist rules
 * - Parent-defined policies
 */

import browser from "webextension-polyfill";
import { BrowserStorageAdapter } from "@ghatana/dcmaar-browser-extension-core";

/**
 * Website categories for filtering
 */
export enum WebsiteCategory {
  SOCIAL = 'social',
  GAMING = 'gaming',
  STREAMING = 'streaming',
  ADULT = 'adult',
  SHOPPING = 'shopping',
  NEWS = 'news',
  EDUCATION = 'education',
  PRODUCTIVITY = 'productivity',
  COMMUNICATION = 'communication',
  ENTERTAINMENT = 'entertainment',
  OTHER = 'other'
}

/**
 * Time window for blocking rules
 */
export interface TimeWindow {
  /** Days of week (0-6, 0=Sunday) */
  daysOfWeek: number[];
  /** Start time in minutes from midnight (e.g., 480 = 8:00 AM) */
  startMinutes: number;
  /** End time in minutes from midnight (e.g., 900 = 3:00 PM) */
  endMinutes: number;
  /** Whether this window blocks or allows access */
  isBlocked: boolean;
}

/**
 * Blocking policy definition
 */
export interface BlockingPolicy {
  id: string;
  name: string;
  enabled: boolean;
  /** Categories to block */
  blockedCategories: WebsiteCategory[];
  /** Specific domains to block (blacklist) */
  blockedDomains: string[];
  /** Specific domains to always allow (whitelist) */
  allowedDomains: string[];
  /** Time windows when blocking applies */
  timeWindows?: TimeWindow[];
  /** Optional per-day usage limit in minutes for this policy (quota-based rules) */
  dailyLimitMinutes?: number;
  /** Reason shown to user when blocked */
  blockReason?: string;
  createdAt: number;
  updatedAt: number;
}

/**
 * Block event log entry
 */
export interface BlockEvent {
  id: string;
  url: string;
  domain: string;
  category: WebsiteCategory;
  policyId: string;
  timestamp: number;
  reason: string;
}

/**
 * Temporary allow entry for domains that have been temporarily unblocked
 */
export interface TempAllowEntry {
  domain: string;
  expiresAt: number;
  grantedBy: string;
  reason?: string;
}

/**
 * Website categorization database
 * Maps domain patterns to categories
 */
const DOMAIN_CATEGORIES: Record<string, WebsiteCategory> = {
  // Social Media
  'facebook.com': WebsiteCategory.SOCIAL,
  'instagram.com': WebsiteCategory.SOCIAL,
  'twitter.com': WebsiteCategory.SOCIAL,
  'x.com': WebsiteCategory.SOCIAL,
  'tiktok.com': WebsiteCategory.SOCIAL,
  'snapchat.com': WebsiteCategory.SOCIAL,
  'reddit.com': WebsiteCategory.SOCIAL,
  'linkedin.com': WebsiteCategory.SOCIAL,
  'pinterest.com': WebsiteCategory.SOCIAL,
  'tumblr.com': WebsiteCategory.SOCIAL,

  // Gaming
  'twitch.tv': WebsiteCategory.GAMING,
  'steam.com': WebsiteCategory.GAMING,
  'epicgames.com': WebsiteCategory.GAMING,
  'roblox.com': WebsiteCategory.GAMING,
  'minecraft.net': WebsiteCategory.GAMING,
  'chess.com': WebsiteCategory.GAMING,
  'pogo.com': WebsiteCategory.GAMING,

  // Streaming
  'youtube.com': WebsiteCategory.STREAMING,
  'netflix.com': WebsiteCategory.STREAMING,
  'hulu.com': WebsiteCategory.STREAMING,
  'disneyplus.com': WebsiteCategory.STREAMING,
  'primevideo.com': WebsiteCategory.STREAMING,
  'hbomax.com': WebsiteCategory.STREAMING,
  'spotify.com': WebsiteCategory.STREAMING,

  // Shopping
  'amazon.com': WebsiteCategory.SHOPPING,
  'ebay.com': WebsiteCategory.SHOPPING,
  'walmart.com': WebsiteCategory.SHOPPING,
  'target.com': WebsiteCategory.SHOPPING,
  'etsy.com': WebsiteCategory.SHOPPING,

  // Education
  'khanacademy.org': WebsiteCategory.EDUCATION,
  'coursera.org': WebsiteCategory.EDUCATION,
  'edx.org': WebsiteCategory.EDUCATION,
  'udemy.com': WebsiteCategory.EDUCATION,
  'duolingo.com': WebsiteCategory.EDUCATION,
  'wikipedia.org': WebsiteCategory.EDUCATION,

  // Communication
  'gmail.com': WebsiteCategory.COMMUNICATION,
  'outlook.com': WebsiteCategory.COMMUNICATION,
  'slack.com': WebsiteCategory.COMMUNICATION,
  'discord.com': WebsiteCategory.COMMUNICATION,
  'zoom.us': WebsiteCategory.COMMUNICATION,
  'teams.microsoft.com': WebsiteCategory.COMMUNICATION,
};

/**
 * WebsiteBlocker manages content filtering and blocking
 */
export class WebsiteBlocker {
  private storage = new BrowserStorageAdapter();
  private policies: BlockingPolicy[] = [];
  private blockEvents: BlockEvent[] = [];
  private initialized = false;
  private listenersRegistered = false;
  private refreshIntervalId?: number;
  private alarmRegistered = false;
  private activeRuleIds: number[] = [];
  private nextRuleId = 10000;
  private tempAllows: TempAllowEntry[] = [];
  private tempAllowCleanupInterval?: ReturnType<typeof setInterval>;
  private readonly STORAGE_KEY_POLICIES = 'guardian:policies';
  private readonly STORAGE_KEY_EVENTS = 'guardian:block_events';
  private readonly STORAGE_KEY_TEMP_ALLOWS = 'guardian:temp_allows';

  /**
   * Initialize the blocker by loading policies from storage
   */
  async initialize(): Promise<void> {
    if (this.initialized) {
      return;
    }

    await Promise.all([
      this.loadPolicies(),
      this.loadStoredBlockEvents(),
      this.loadTempAllows(),
    ]);
    this.setupTabListener();
    await this.applyDynamicRules();
    this.startTempAllowCleanup();
    this.initialized = true;
  }

  /**
   * Load blocking policies from IndexedDB
   */
  private async loadPolicies(): Promise<void> {
    const stored = await this.storage.get<BlockingPolicy[]>(this.STORAGE_KEY_POLICIES);
    this.policies = stored || this.getDefaultPolicies();
  }

  /**
   * Load stored block events so history survives restarts
   */
  private async loadStoredBlockEvents(): Promise<void> {
    const stored = await this.storage.get<BlockEvent[]>(this.STORAGE_KEY_EVENTS);
    this.blockEvents = stored || [];
  }

  /**
   * Load temporary allows from storage
   */
  private async loadTempAllows(): Promise<void> {
    const stored = await this.storage.get<TempAllowEntry[]>(this.STORAGE_KEY_TEMP_ALLOWS);
    // Filter out expired entries on load
    const now = Date.now();
    this.tempAllows = (stored || []).filter(entry => entry.expiresAt > now);
    // Save cleaned list back
    if (stored && stored.length !== this.tempAllows.length) {
      await this.storage.set(this.STORAGE_KEY_TEMP_ALLOWS, this.tempAllows);
    }
  }

  /**
   * Start periodic cleanup of expired temp allows
   */
  private startTempAllowCleanup(): void {
    // Clean up every minute
    this.tempAllowCleanupInterval = setInterval(async () => {
      await this.cleanupExpiredTempAllows();
    }, 60000);
  }

  /**
   * Clean up expired temporary allows
   */
  private async cleanupExpiredTempAllows(): Promise<void> {
    const now = Date.now();
    const before = this.tempAllows.length;
    this.tempAllows = this.tempAllows.filter(entry => entry.expiresAt > now);

    if (this.tempAllows.length !== before) {
      await this.storage.set(this.STORAGE_KEY_TEMP_ALLOWS, this.tempAllows);
      // Re-apply dynamic rules to restore blocking for expired domains
      await this.applyDynamicRules();
      console.debug('[WebsiteBlocker] Cleaned up expired temp allows', {
        removed: before - this.tempAllows.length,
        remaining: this.tempAllows.length,
      });
    }
  }

  /**
   * Add a temporary allow for a domain
   */
  async addTempAllow(domain: string, durationMinutes: number, grantedBy: string, reason?: string): Promise<TempAllowEntry> {
    const normalizedDomain = domain.toLowerCase().replace(/^www\./, '');
    const expiresAt = Date.now() + (durationMinutes * 60 * 1000);

    // Remove any existing temp allow for this domain
    this.tempAllows = this.tempAllows.filter(e => e.domain !== normalizedDomain);

    const entry: TempAllowEntry = {
      domain: normalizedDomain,
      expiresAt,
      grantedBy,
      reason,
    };

    this.tempAllows.push(entry);
    await this.storage.set(this.STORAGE_KEY_TEMP_ALLOWS, this.tempAllows);

    console.debug('[WebsiteBlocker] Added temp allow', { domain: normalizedDomain, durationMinutes, expiresAt });

    return entry;
  }

  /**
   * Remove a temporary allow for a domain
   */
  async removeTempAllow(domain: string): Promise<boolean> {
    const normalizedDomain = domain.toLowerCase().replace(/^www\./, '');
    const before = this.tempAllows.length;
    this.tempAllows = this.tempAllows.filter(e => e.domain !== normalizedDomain);

    if (this.tempAllows.length !== before) {
      await this.storage.set(this.STORAGE_KEY_TEMP_ALLOWS, this.tempAllows);
      await this.applyDynamicRules();
      return true;
    }
    return false;
  }

  /**
   * Get all active temporary allows
   */
  getTempAllows(): TempAllowEntry[] {
    const now = Date.now();
    return this.tempAllows.filter(e => e.expiresAt > now);
  }

  /**
   * Check if a domain has an active temporary allow
   */
  private hasTempAllow(domain: string): boolean {
    const normalizedDomain = domain.toLowerCase().replace(/^www\./, '');
    const now = Date.now();
    return this.tempAllows.some(e => e.domain === normalizedDomain && e.expiresAt > now);
  }

  /**
   * Get a mapping from category to the list of domains in that category.
   * This is used by the UI to explain what each category covers.
   */
  getCategoryDomains(): Record<WebsiteCategory, string[]> {
    const map: Partial<Record<WebsiteCategory, string[]>> = {};

    for (const [domain, category] of Object.entries(DOMAIN_CATEGORIES)) {
      if (!map[category]) {
        map[category] = [];
      }
      map[category]!.push(domain);
    }

    return map as Record<WebsiteCategory, string[]>;
  }

  /**
   * Update declarative net request rules to mirror current policies
   */
  private async applyDynamicRules(): Promise<void> {
    const chromeApi: typeof chrome | undefined =
      typeof chrome !== 'undefined' ? chrome : undefined;

    const dnr = chromeApi?.declarativeNetRequest;
    if (!dnr?.updateDynamicRules) {
      return;
    }

    try {
      const rules: chrome.declarativeNetRequest.Rule[] = [];
      const newRuleIds: number[] = [];

      // Ensure rule IDs remain unique across extension restarts by
      // syncing with any existing dynamic rules that may still be
      // registered from previous runs.
      let existingRuleIds: number[] = [];
      let ruleIdBase = this.nextRuleId;

      if (typeof dnr.getDynamicRules === 'function') {
        try {
          const existingRules = await dnr.getDynamicRules();
          existingRuleIds = existingRules.map((r) => r.id);
          if (existingRuleIds.length > 0) {
            const maxExistingId = Math.max(...existingRuleIds);
            if (maxExistingId >= ruleIdBase) {
              ruleIdBase = maxExistingId + 1;
            }
          }
        } catch (e) {
          console.debug(
            'WebsiteBlocker: getDynamicRules failed, continuing with local rule IDs',
            e
          );
        }
      }

      let ruleId = ruleIdBase;

      for (const policy of this.policies) {
        if (!policy.enabled) continue;

        const hasDailyLimit =
          typeof policy.dailyLimitMinutes === 'number' &&
          policy.dailyLimitMinutes > 0;
        const hasTimeWindows = !!(policy.timeWindows && policy.timeWindows.length > 0);

        // Quota-only policies (daily limit without time windows) should not
        // create hard blocking DNR rules; they are enforced via the
        // usage/quota logic in the pipeline instead.
        if (hasDailyLimit && !hasTimeWindows) {
          continue;
        }

        // If policy has time windows, only apply rules when currently in a blocked window
        if (policy.timeWindows && policy.timeWindows.length > 0) {
          const now = new Date();
          const inBlockedWindow = this.isInTimeWindow(now, policy.timeWindows);
          if (!inBlockedWindow) continue;
        }
        const domainPatterns = new Set<string>();

        policy.blockedDomains.forEach((domain) => domainPatterns.add(domain));

        for (const [domain, category] of Object.entries(DOMAIN_CATEGORIES)) {
          if (policy.blockedCategories.includes(category)) {
            domainPatterns.add(domain);
          }
        }

        domainPatterns.forEach((domain) => {
          const id = ruleId++;
          const urlFilter = domain.startsWith('*.') ? domain : `||${domain}`;

          rules.push({
            id,
            priority: 1,
            action: { type: 'block' } as chrome.declarativeNetRequest.RuleAction,
            condition: {
              urlFilter,
              resourceTypes: ['main_frame'],
            },
          });

          newRuleIds.push(id);
        });
      }

      await dnr.updateDynamicRules({
        removeRuleIds:
          existingRuleIds.length > 0 ? existingRuleIds : this.activeRuleIds,
        addRules: rules,
      });

      this.activeRuleIds = newRuleIds;
      this.nextRuleId = ruleId;
    } catch (error) {
      const message =
        error instanceof Error ? error.message : String(error ?? "");

      // Chrome may report "Rule with id <N> does not have a unique ID" if
      // there are legacy or static rules with overlapping IDs. This is not
      // fatal for core blocking (we also block via shouldBlock/tab listener),
      // so we log it at debug level to avoid noisy warnings.
      if (message.includes('does not have a unique ID')) {
        console.debug(
          'WebsiteBlocker: dynamic rules conflict detected, skipping update',
          { message }
        );
        return;
      }

      console.warn('WebsiteBlocker: failed to update dynamic rules', error);
    }
  }

  /**
   * Save policies to storage
   */
  async savePolicies(policies: BlockingPolicy[]): Promise<void> {
    this.policies = policies;
    await this.storage.set(this.STORAGE_KEY_POLICIES, policies);
    await this.applyDynamicRules();
  }

  /**
   * Get default policies for new installations
   */
  private getDefaultPolicies(): BlockingPolicy[] {
    const now = Date.now();
    return [
      {
        id: 'default-school-hours',
        name: 'Block Social Media During School',
        enabled: true,
        blockedCategories: [WebsiteCategory.SOCIAL, WebsiteCategory.GAMING, WebsiteCategory.STREAMING],
        blockedDomains: [],
        allowedDomains: [],
        timeWindows: [
          {
            daysOfWeek: [1, 2, 3, 4, 5], // Monday-Friday
            startMinutes: 480, // 8:00 AM
            endMinutes: 900, // 3:00 PM
            isBlocked: true
          }
        ],
        blockReason: 'Focus on your studies! This site is blocked during school hours.',
        createdAt: now,
        updatedAt: now
      }
    ];
  }

  /**
   * Check if a URL should be blocked (synchronous version for web request interception)
   *
   * Precedence:
   * 1. Explicit domain blocks (blockedDomains) always win over allows.
   * 2. Explicit allows (allowedDomains) override category-based blocks.
   * 3. Category blocks apply when there is no explicit allow for the domain.
   */
  private shouldBlockSync(url: string): { blocked: boolean; reason?: string; policyId?: string } {
    try {
      const urlObj = new URL(url);
      const domain = this.extractDomain(urlObj.hostname);

      // Check for temporary allow first (highest priority)
      if (this.hasTempAllow(domain)) {
        return { blocked: false };
      }

      const category = this.categorizeUrl(domain);
      const now = new Date();
      const minutesFromMidnight = now.getHours() * 60 + now.getMinutes();

      let domainBlock: { reason: string; policyId: string } | null = null;
      let categoryBlock: { reason: string; policyId: string } | null = null;
      let isWhitelisted = false;
      let timeRemainingMinutes: number | undefined;

      for (const policy of this.policies) {
        if (!policy.enabled) continue;

        const hasDailyLimit =
          typeof policy.dailyLimitMinutes === 'number' &&
          policy.dailyLimitMinutes > 0;
        const hasTimeWindows = !!(policy.timeWindows && policy.timeWindows.length > 0);

        // Quota-only policies (daily limit without time windows) are handled
        // via the quota logic in the pipeline and should not create immediate
        // hard blocks here.
        const isQuotaOnlyPolicy = hasDailyLimit && !hasTimeWindows;

        // Track explicit domain blocks (highest priority)
        if (!isQuotaOnlyPolicy && !domainBlock && this.isDomainInList(domain, policy.blockedDomains)) {
          domainBlock = {
            reason: policy.blockReason || 'This website is blocked',
            policyId: policy.id,
          };
        }

        // Track explicit allows
        if (this.isDomainInList(domain, policy.allowedDomains)) {
          isWhitelisted = true;
        }

        // Track category-based blocks (non-quota policies only)
        if (!isQuotaOnlyPolicy && policy.blockedCategories.includes(category)) {
          let inBlockedWindow = true;

          if (policy.timeWindows && policy.timeWindows.length > 0) {
            inBlockedWindow = false;

            for (const window of policy.timeWindows) {
              if (!window.isBlocked) continue;
              if (!window.daysOfWeek.includes(now.getDay())) continue;

              if (minutesFromMidnight >= window.startMinutes && minutesFromMidnight < window.endMinutes) {
                inBlockedWindow = true;

                const remaining = window.endMinutes - minutesFromMidnight;
                if (remaining > 0) {
                  if (typeof timeRemainingMinutes !== 'number' || remaining < timeRemainingMinutes) {
                    timeRemainingMinutes = remaining;
                  }
                }

                break;
              }
            }

            if (!inBlockedWindow) continue;
          }

          if (!categoryBlock) {
            categoryBlock = {
              reason: policy.blockReason || `${category} websites are blocked`,
              policyId: policy.id,
            };
          }
        }
      }

      // 1. Explicit domain block wins over everything
      if (domainBlock) {
        return { blocked: true, reason: domainBlock.reason, policyId: domainBlock.policyId };
      }

      // 2. Explicit allow overrides category-based blocks
      if (isWhitelisted) {
        return { blocked: false };
      }

      // 3. Category-based block when no explicit allow
      if (categoryBlock) {
        return { blocked: true, reason: categoryBlock.reason, policyId: categoryBlock.policyId };
      }

      return { blocked: false };
    } catch (error) {
      console.error('Error checking block status:', error);
      return { blocked: false };
    }
  }

  /**
   * Check if a URL should be blocked
   *
   * Precedence:
   * 1. Explicit domain blocks (blockedDomains) always win over allows.
   * 2. Explicit allows (allowedDomains) override category-based blocks.
   * 3. Category blocks apply when there is no explicit allow for the domain.
   *
   * For category-based blocks that are active only during specific time windows,
   * this method also returns an approximate number of minutes remaining in the
   * current blocked window so the UI can show "time left" messaging.
   */
  async shouldBlock(url: string): Promise<{ blocked: boolean; reason?: string; policyId?: string; timeRemainingMinutes?: number }> {
    try {
      const urlObj = new URL(url);
      const domain = this.extractDomain(urlObj.hostname);

      // Check for temporary allow first (highest priority)
      if (this.hasTempAllow(domain)) {
        return { blocked: false };
      }

      const category = this.categorizeUrl(domain);
      const now = new Date();
      const minutesFromMidnight = now.getHours() * 60 + now.getMinutes();

      let domainBlock: { reason: string; policyId: string } | null = null;
      let categoryBlock: { reason: string; policyId: string } | null = null;
      let isWhitelisted = false;
      let timeRemainingMinutes: number | undefined;

      for (const policy of this.policies) {
        if (!policy.enabled) continue;

        const hasDailyLimit =
          typeof policy.dailyLimitMinutes === 'number' &&
          policy.dailyLimitMinutes > 0;
        const hasTimeWindows = !!(policy.timeWindows && policy.timeWindows.length > 0);

        // Quota-only policies (daily limit without time windows) are handled
        // via the quota logic in the pipeline and should not create immediate
        // hard blocks here.
        const isQuotaOnlyPolicy = hasDailyLimit && !hasTimeWindows;

        // Track explicit domain blocks (highest priority)
        if (!isQuotaOnlyPolicy && !domainBlock && this.isDomainInList(domain, policy.blockedDomains)) {
          domainBlock = {
            reason: policy.blockReason || 'Domain blocked by policy',
            policyId: policy.id,
          };
        }

        // Track explicit allows
        if (this.isDomainInList(domain, policy.allowedDomains)) {
          isWhitelisted = true;
        }

        // Track category-based blocks
        if (policy.blockedCategories.includes(category)) {
          let inBlockedWindow = true;

          if (policy.timeWindows && policy.timeWindows.length > 0) {
            inBlockedWindow = false;

            for (const window of policy.timeWindows) {
              if (!window.isBlocked) continue;
              if (!window.daysOfWeek.includes(now.getDay())) continue;

              if (minutesFromMidnight >= window.startMinutes && minutesFromMidnight < window.endMinutes) {
                inBlockedWindow = true;

                const remaining = window.endMinutes - minutesFromMidnight;
                if (remaining > 0) {
                  if (typeof timeRemainingMinutes !== 'number' || remaining < timeRemainingMinutes) {
                    timeRemainingMinutes = remaining;
                  }
                }

                break;
              }
            }

            if (!inBlockedWindow) continue;
          }

          if (!categoryBlock) {
            categoryBlock = {
              reason: policy.blockReason || `${category} websites are blocked`,
              policyId: policy.id,
            };
          }
        }
      }

      // 1. Explicit domain block wins over everything
      if (domainBlock) {
        await this.logBlockEvent(url, domain, category, domainBlock.policyId, domainBlock.reason);
        return { blocked: true, reason: domainBlock.reason, policyId: domainBlock.policyId };
      }

      // 2. Explicit allow overrides category-based blocks
      if (isWhitelisted) {
        return { blocked: false };
      }

      // 3. Category-based block when no explicit allow
      if (categoryBlock) {
        await this.logBlockEvent(url, domain, category, categoryBlock.policyId, categoryBlock.reason);
        return {
          blocked: true,
          reason: categoryBlock.reason,
          policyId: categoryBlock.policyId,
          timeRemainingMinutes,
        };
      }

      return { blocked: false };
    } catch (error) {
      console.error('Error checking block status:', error);
      return { blocked: false };
    }
  }

  /**
   * Check if current time is within a blocked time window
   */
  private isInTimeWindow(now: Date, windows: TimeWindow[]): boolean {
    const dayOfWeek = now.getDay();
    const minutesFromMidnight = now.getHours() * 60 + now.getMinutes();

    return windows.some(window => {
      // Check if today is included in this window
      if (!window.daysOfWeek.includes(dayOfWeek)) return false;

      // Check if current time is within the window
      if (window.isBlocked) {
        return minutesFromMidnight >= window.startMinutes &&
          minutesFromMidnight < window.endMinutes;
      }

      return false;
    });
  }

  /**
   * Extract base domain from hostname
   */
  private extractDomain(hostname: string): string {
    // Remove www. prefix
    const withoutWww = hostname.replace(/^www\./, '');

    // For known TLDs, extract the main domain
    const parts = withoutWww.split('.');
    if (parts.length >= 2) {
      return parts.slice(-2).join('.');
    }

    return withoutWww;
  }

  /**
   * Categorize a URL based on domain
   */
  private categorizeUrl(domain: string): WebsiteCategory {
    // Check exact match
    if (domain in DOMAIN_CATEGORIES) {
      return DOMAIN_CATEGORIES[domain];
    }

    // Check subdomain match
    for (const [knownDomain, category] of Object.entries(DOMAIN_CATEGORIES)) {
      if (domain.endsWith(knownDomain)) {
        return category;
      }
    }

    // Check for keywords in domain
    if (domain.includes('edu')) return WebsiteCategory.EDUCATION;
    if (domain.includes('game')) return WebsiteCategory.GAMING;
    if (domain.includes('porn') || domain.includes('xxx')) return WebsiteCategory.ADULT;

    return WebsiteCategory.OTHER;
  }

  /**
   * Check if domain matches any pattern in the list
   */
  private isDomainInList(domain: string, list: string[]): boolean {
    return list.some(pattern => {
      // Exact match
      if (pattern === domain) return true;

      // Wildcard match (*.example.com)
      if (pattern.startsWith('*.')) {
        const baseDomain = pattern.substring(2);
        return domain === baseDomain || domain.endsWith('.' + baseDomain);
      }

      // Subdomain match
      return domain.endsWith('.' + pattern);
    });
  }

  /**
   * Log a block event
   */
  private async logBlockEvent(url: string, domain: string, category: WebsiteCategory, policyId: string, reason: string): Promise<void> {
    const event: BlockEvent = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      url,
      domain,
      category,
      policyId,
      timestamp: Date.now(),
      reason
    };

    this.blockEvents.push(event);

    // Keep only last 1000 events
    if (this.blockEvents.length > 1000) {
      this.blockEvents = this.blockEvents.slice(-1000);
    }

    await this.storage.set(this.STORAGE_KEY_EVENTS, this.blockEvents);
  }

  /**
   * Get recent block events
   */
  async getBlockEvents(limit = 100): Promise<BlockEvent[]> {
    const stored = await this.storage.get<BlockEvent[]>(this.STORAGE_KEY_EVENTS);
    const events = stored || [];
    return events.slice(-limit);
  }

  /**
   * Setup listener for tab navigation
   */
  private setupTabListener(): void {
    if (this.listenersRegistered) {
      return;
    }

    try {
      // Use webextension-polyfill for better API compatibility
      // Note: Manifest V3 doesn't support webRequest API, use declarativeNetRequest instead
      const hasTabsApi = typeof browser !== 'undefined' && browser.tabs;

      if (!hasTabsApi) {
        console.warn('WebsiteBlocker: browser.tabs API not available, skipping listeners');
        return;
      }

      // Listen for tab updates (navigation)
      // Manifest V3 requires using declarativeNetRequest instead of webRequest blocking
      browser.tabs.onUpdated.addListener(async (tabId, changeInfo) => {
        if (changeInfo.url) {
          const result = await this.shouldBlock(changeInfo.url);
          if (result.blocked) {
            // Redirect to blocked page with metadata for the UI
            let blockedUrl = browser.runtime.getURL('src/pages/blocked.html') +
              `?url=${encodeURIComponent(changeInfo.url)}&reason=${encodeURIComponent(result.reason || 'blocked')}`;

            if (result.policyId) {
              blockedUrl += `&policyId=${encodeURIComponent(result.policyId)}`;
            }

            if (typeof result.timeRemainingMinutes === 'number') {
              blockedUrl += `&timeRemainingMinutes=${encodeURIComponent(String(result.timeRemainingMinutes))}`;
            }

            try {
              await browser.tabs.update(tabId, { url: blockedUrl });
            } catch (error) {
              console.warn('WebsiteBlocker: Failed to update tab', { tabId, error });
            }
          }
        }
      });

      // Start periodic refresh of dynamic declarativeNetRequest rules
      // This applies time-windowed policies without blocking webRequest listeners
      // (which are not available in Manifest V3)
      this.startRuleRefreshInterval();

      this.listenersRegistered = true;
      console.debug('WebsiteBlocker: Tab listeners registered successfully');
    } catch (error) {
      console.warn('WebsiteBlocker: Failed to setup tab listeners', error);
    }
  }

  /**
   * Start a periodic timer to refresh dynamic DNR rules.
   * Runs every 60 seconds (configurable if needed) to apply
   * time-windowed policies without blocking webRequest listeners.
   */
  private startRuleRefreshInterval(): void {
    const chromeApi: typeof chrome | undefined =
      typeof chrome !== 'undefined' ? chrome : undefined;

    // Prefer chrome.alarms in service worker (MV3) to keep background
    // work scheduled even when the service worker is idle. Fallback to
    // a global interval when alarms are not available (e.g., tests or
    // non-service-worker environments).
    if (chromeApi?.alarms && chromeApi.alarms.create) {
      try {
        // Clear existing alarm (no-op if not present)
        chromeApi.alarms.clear('guardian-refresh-rules', () => { });

        // Create a repeating alarm every 1 minute
        chromeApi.alarms.create('guardian-refresh-rules', { periodInMinutes: 1 });

        if (!this.alarmRegistered && chromeApi.alarms.onAlarm?.addListener) {
          chromeApi.alarms.onAlarm.addListener((alarm) => {
            if (alarm?.name === 'guardian-refresh-rules') {
              this.applyDynamicRules().catch((e) =>
                console.warn('WebsiteBlocker: periodic rule refresh failed', e)
              );
            }
          });
          this.alarmRegistered = true;
        }

        // Run an initial refresh immediately
        this.applyDynamicRules().catch((e) =>
          console.warn('WebsiteBlocker: initial rule refresh failed', e)
        );
        return;
      } catch (e) {
        console.warn('WebsiteBlocker: alarms API failed, falling back to interval', e);
      }
    }

    // Clear any existing interval
    if (this.refreshIntervalId) {
      clearInterval(this.refreshIntervalId);
    }

    // Refresh immediately and then every minute (fallback)
    this.applyDynamicRules().catch((e) => console.warn('WebsiteBlocker: initial rule refresh failed', e));

    const globalWithInterval = globalThis as typeof globalThis & {
      setInterval(handler: () => void, timeout: number): number;
    };

    this.refreshIntervalId = globalWithInterval.setInterval(() => {
      this.applyDynamicRules().catch((e) => console.warn('WebsiteBlocker: periodic rule refresh failed', e));
    }, 60 * 1000);
  }

  /**
   * Get all current policies (READ-ONLY)
   */
  getPolicies(): BlockingPolicy[] {
    return [...this.policies];
  }

  /**
   * INTERNAL: Add a new policy
   * NOTE: This is only used internally for default policies.
   * Policies should be managed from parent dashboard and synced via API.
   * @internal
   */

  private async addPolicyInternal(policy: Omit<BlockingPolicy, 'id' | 'createdAt' | 'updatedAt'>): Promise<BlockingPolicy> {
    const now = Date.now();
    const newPolicy: BlockingPolicy = {
      ...policy,
      id: `policy-${now}-${Math.random().toString(36).substr(2, 9)}`,
      createdAt: now,
      updatedAt: now
    };

    this.policies.push(newPolicy);
    await this.savePolicies(this.policies);
    return newPolicy;
  }

  /**
   * INTERNAL: Update an existing policy
   * NOTE: Policies should be managed from parent dashboard and synced via API.
   * @internal
   */

  private async updatePolicyInternal(id: string, updates: Partial<BlockingPolicy>): Promise<boolean> {
    const index = this.policies.findIndex(p => p.id === id);
    if (index === -1) return false;

    this.policies[index] = {
      ...this.policies[index],
      ...updates,
      id, // Preserve ID
      updatedAt: Date.now()
    };

    await this.savePolicies(this.policies);
    return true;
  }

  /**
   * INTERNAL: Delete a policy
   * NOTE: Policies should be managed from parent dashboard and synced via API.
   * @internal
   */

  private async deletePolicyInternal(id: string): Promise<boolean> {
    const initialLength = this.policies.length;
    this.policies = this.policies.filter(p => p.id !== id);

    if (this.policies.length < initialLength) {
      await this.savePolicies(this.policies);
      return true;
    }

    return false;
  }

  /**
   * Sync policies from backend API (Parent Dashboard)
   * This is the ONLY way policies should be updated in the extension.
   * Children cannot modify policies locally.
   */
  async syncPoliciesFromBackend(apiUrl: string, deviceId: string): Promise<void> {
    try {
      const response = await fetch(`${apiUrl}/api/policies?deviceId=${deviceId}`);
      if (!response.ok) throw new Error('Failed to fetch policies');

      const policies: BlockingPolicy[] = await response.json();
      await this.savePolicies(policies);
    } catch (error) {
      // In development or offline scenarios, backend may be unreachable.
      // Log as a warning and swallow to avoid noisy hard errors in the
      // extension console.
      console.warn('Failed to sync policies from backend:', error);
    }
  }
}

export default WebsiteBlocker;
