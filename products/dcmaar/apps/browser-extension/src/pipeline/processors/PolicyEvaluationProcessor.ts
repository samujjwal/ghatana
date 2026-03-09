/**
 * @fileoverview Policy Evaluation Processor
 * 
 * Processor that evaluates events against blocking policies.
 * Integrates with WebsiteBlocker for policy decisions.
 * 
 * @module pipeline/processors/PolicyEvaluationProcessor
 */

import { BaseEventProcessor, BrowserStorageAdapter } from '@ghatana/dcmaar-browser-extension-core';
import type { WebsiteBlocker, BlockingPolicy, WebsiteCategory } from '../../blocker/WebsiteBlocker';
import type {
    EnrichedEvent,
    PolicyEvaluatedEvent,
    TabActivityEvent,
    ContentScriptEvent,
    DailyUsage,
} from '../types';

/**
 * Configuration for PolicyEvaluationProcessor
 */
export interface PolicyEvaluationConfig {
    /** Whether to take blocking action (redirect to block page) */
    enforceBlocking?: boolean;
    /** Block page URL */
    blockPageUrl?: string;
    /** Whether to log block events */
    logBlockEvents?: boolean;
}

const DEFAULT_CONFIG: Required<PolicyEvaluationConfig> = {
    enforceBlocking: true,
    blockPageUrl: 'src/pages/blocked.html',
    logBlockEvents: true,
};

/**
 * PolicyEvaluationProcessor
 * 
 * Evaluates events against blocking policies and optionally
 * takes enforcement action (redirecting to block page).
 * 
 * This processor acts as the single source of truth for policy
 * decisions in the pipeline, replacing inline logic in the controller.
 * 
 * @example
 * ```typescript
 * const blocker = new WebsiteBlocker();
 * await blocker.initialize();
 * 
 * const processor = new PolicyEvaluationProcessor(blocker);
 * const result = await processor.process(enrichedEvent);
 * 
 * if (result.policyDecision === 'block') {
 *   console.log('Blocked:', result.policyReason);
 * }
 * ```
 */
export class PolicyEvaluationProcessor extends BaseEventProcessor<EnrichedEvent, PolicyEvaluatedEvent> {
    readonly name = 'policy-evaluation';

    private readonly config: Required<PolicyEvaluationConfig>;
    private readonly blocker: WebsiteBlocker;
    private policies: BlockingPolicy[] = [];
    private readonly storage = new BrowserStorageAdapter();
    private readonly STORAGE_KEY_PREFIX = 'guardian';

    constructor(blocker: WebsiteBlocker, config: PolicyEvaluationConfig = {}) {
        super();
        this.blocker = blocker;
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    /**
     * Initialize the processor
     */
    async initialize(): Promise<void> {
        // Load policies from blocker
        this.policies = this.blocker.getPolicies();
        console.debug('[PolicyEvaluationProcessor] Initialized with', this.policies.length, 'policies');
    }

    /**
     * Shutdown the processor
     */
    async shutdown(): Promise<void> {
        this.policies = [];
        console.debug('[PolicyEvaluationProcessor] Shutdown');
    }

    /**
     * Check if this processor can handle the event
     */
    canProcess(event: unknown): event is EnrichedEvent {
        if (!event || typeof event !== 'object') return false;
        const e = event as Record<string, unknown>;
        return (
            typeof e.type === 'string' &&
            typeof e.timestamp === 'number' &&
            (e.type === 'tab_activity' || e.type === 'page_view' || e.type === 'page_interaction')
        );
    }

    /**
     * Process the event and evaluate against policies
     */
    async process(event: EnrichedEvent): Promise<PolicyEvaluatedEvent | null> {
        const url = this.extractUrl(event);
        const domain = this.extractDomain(event);
        const category = event.category;

        if (!url || !domain) {
            // Pass through events without URL/domain
            return {
                ...event,
                policyDecision: 'allow',
                policyReason: 'No URL to evaluate',
            };
        }

        // Evaluate policy
        const evaluation = await this.evaluatePolicy(url, domain, category);

        const policyEvent: PolicyEvaluatedEvent = {
            ...event,
            policyDecision: evaluation.decision,
            policyId: evaluation.policyId,
            policyReason: evaluation.reason,
            actionTaken: false,
            metadata: {
                ...event.metadata,
                policyEvaluatedAt: Date.now(),
                quotaRemainingMinutes: evaluation.remainingMinutes,
                quotaLimitMinutes: evaluation.limitMinutes,
            },
        };

        // Take enforcement action if blocking
        if (evaluation.decision === 'block' && this.config.enforceBlocking) {
            const actionTaken = await this.enforceBlock(event, evaluation.reason);
            policyEvent.actionTaken = actionTaken;
        }

        return policyEvent;
    }

    /**
     * Evaluate URL against policies
     *
     * Precedence:
     * 1. Explicit domain blocks (blockedDomains) always win over allows.
     * 2. Explicit allows (allowedDomains) override category-based blocks.
     * 3. Category blocks apply when there is no explicit allow for the domain.
     */
    private async evaluatePolicy(
        _url: string,
        domain: string,
        category?: WebsiteCategory
    ): Promise<{
        decision: 'allow' | 'block' | 'warn';
        policyId?: string;
        reason?: string;
        remainingMinutes?: number;
        limitMinutes?: number;
    }> {
        // Refresh policies
        this.policies = this.blocker.getPolicies();

        const now = new Date();

        let domainBlock: { policyId: string; reason: string } | null = null;
        let categoryBlock: { policyId: string; reason: string } | null = null;
        let allowPolicyId: string | undefined;
        let quotaPolicy: { policyId: string; reason: string; limitMinutes: number } | null = null;

        for (const policy of this.policies) {
            if (!policy.enabled) continue;

            const hasDailyLimit =
                typeof policy.dailyLimitMinutes === 'number' &&
                policy.dailyLimitMinutes > 0;
            const hasTimeWindows = !!(policy.timeWindows && policy.timeWindows.length > 0);

            // Track explicit domain blocks (highest priority)
            if (!domainBlock && this.isDomainInList(domain, policy.blockedDomains)) {
                domainBlock = {
                    policyId: policy.id,
                    reason: policy.blockReason || 'Domain blocked by policy',
                };
            }

            // Track explicit allows
            if (!allowPolicyId && this.isDomainInList(domain, policy.allowedDomains)) {
                allowPolicyId = policy.id;
            }

            // Quota-based policies (dailyLimitMinutes) are evaluated separately
            const isQuotaOnlyPolicy = hasDailyLimit && !hasTimeWindows;

            // Track category-based blocks for non-quota policies
            if (category && policy.blockedCategories.includes(category)) {
                if (hasTimeWindows) {
                    const inBlockedWindow = this.isInTimeWindow(now, policy.timeWindows!);
                    if (!inBlockedWindow) continue;

                    if (!categoryBlock) {
                        categoryBlock = {
                            policyId: policy.id,
                            reason: policy.blockReason || `${category} websites are blocked`,
                        };
                    }
                } else if (isQuotaOnlyPolicy) {
                    const limitMinutes = policy.dailyLimitMinutes ?? 0;
                    if (limitMinutes > 0) {
                        if (!quotaPolicy || limitMinutes < quotaPolicy.limitMinutes) {
                            quotaPolicy = {
                                policyId: policy.id,
                                reason:
                                    policy.blockReason ||
                                    `${category} websites have a daily time limit`,
                                limitMinutes,
                            };
                        }
                    }
                }
            }
        }

        // 1. Explicit domain block wins over everything
        if (domainBlock) {
            return {
                decision: 'block',
                policyId: domainBlock.policyId,
                reason: domainBlock.reason,
            };
        }

        // 2. Explicit allow overrides category-based blocks
        if (allowPolicyId) {
            return { decision: 'allow', policyId: allowPolicyId, reason: 'Whitelisted' };
        }

        // 3. Quota-based rules (warn while time remains, block when exhausted)
        if (quotaPolicy) {
            const usedMinutes = await this.getDomainUsageMinutes(domain);
            const remainingMinutes = Math.max(
                0,
                quotaPolicy.limitMinutes - usedMinutes,
            );

            if (remainingMinutes <= 0) {
                return {
                    decision: 'block',
                    policyId: quotaPolicy.policyId,
                    reason:
                        quotaPolicy.reason ||
                        'Daily time limit reached for this website',
                    remainingMinutes: 0,
                    limitMinutes: quotaPolicy.limitMinutes,
                };
            }

            return {
                decision: 'warn',
                policyId: quotaPolicy.policyId,
                reason:
                    quotaPolicy.reason ||
                    'Limited daily time remaining for this website',
                remainingMinutes,
                limitMinutes: quotaPolicy.limitMinutes,
            };
        }

        // 4. Category-based block when no explicit allow and no quota
        if (categoryBlock) {
            return {
                decision: 'block',
                policyId: categoryBlock.policyId,
                reason: categoryBlock.reason,
            };
        }

        return { decision: 'allow' };
    }

    /**
     * Get today's usage in minutes for a domain based on DailyUsage aggregates.
     */
    private async getDomainUsageMinutes(domain: string): Promise<number> {
        try {
            const today = new Date();
            const dateKey = today.toISOString().split('T')[0];
            const key = `${this.STORAGE_KEY_PREFIX}:daily:${dateKey}`;
            const daily = await this.storage.get<DailyUsage | null>(key);

            if (!daily || !daily.domains[domain]) {
                return 0;
            }

            const usageMs = daily.domains[domain].time || 0;
            return Math.floor(usageMs / 60000);
        } catch (error) {
            console.warn(
                '[PolicyEvaluationProcessor] Failed to read domain usage for quota evaluation',
                error,
            );
            return 0;
        }
    }

    /**
     * Check if domain matches any pattern in the list
     */
    private isDomainInList(domain: string, list: string[]): boolean {
        return list.some((pattern) => {
            if (pattern === domain) return true;
            if (pattern.startsWith('*.')) {
                const baseDomain = pattern.substring(2);
                return domain === baseDomain || domain.endsWith('.' + baseDomain);
            }
            return domain.endsWith('.' + pattern);
        });
    }

    /**
     * Check if current time is within a blocked time window
     */
    private isInTimeWindow(
        now: Date,
        windows: Array<{ daysOfWeek: number[]; startMinutes: number; endMinutes: number; isBlocked: boolean }>
    ): boolean {
        const dayOfWeek = now.getDay();
        const minutesFromMidnight = now.getHours() * 60 + now.getMinutes();

        return windows.some((window) => {
            if (!window.daysOfWeek.includes(dayOfWeek)) return false;
            if (window.isBlocked) {
                return minutesFromMidnight >= window.startMinutes && minutesFromMidnight < window.endMinutes;
            }
            return false;
        });
    }

    /**
     * Enforce blocking by redirecting to block page
     */
    private async enforceBlock(event: EnrichedEvent, reason?: string): Promise<boolean> {
        const tabId = this.extractTabId(event);
        if (!tabId) return false;

        const url = this.extractUrl(event);
        if (!url) return false;

        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;
        if (!chromeApi?.tabs?.update) return false;

        try {
            const blockPageUrl = chromeApi.runtime.getURL(this.config.blockPageUrl) +
                `?url=${encodeURIComponent(url)}&reason=${encodeURIComponent(reason || 'blocked')}`;

            await chromeApi.tabs.update(tabId, { url: blockPageUrl });
            return true;
        } catch (error) {
            console.warn('[PolicyEvaluationProcessor] Failed to redirect to block page:', error);
            return false;
        }
    }

    /**
     * Extract URL from event
     */
    private extractUrl(event: EnrichedEvent): string | null {
        if ('url' in event && typeof (event as TabActivityEvent | ContentScriptEvent).url === 'string') {
            return (event as TabActivityEvent | ContentScriptEvent).url;
        }
        return null;
    }

    /**
     * Extract domain from event
     */
    private extractDomain(event: EnrichedEvent): string | null {
        if ('domain' in event && typeof (event as TabActivityEvent | ContentScriptEvent).domain === 'string') {
            return (event as TabActivityEvent | ContentScriptEvent).domain;
        }
        return null;
    }

    /**
     * Extract tab ID from event
     */
    private extractTabId(event: EnrichedEvent): number | null {
        if ('tabId' in event && typeof (event as TabActivityEvent | ContentScriptEvent).tabId === 'number') {
            return (event as TabActivityEvent | ContentScriptEvent).tabId;
        }
        return null;
    }

    /**
     * Refresh policies from blocker
     */
    refreshPolicies(): void {
        this.policies = this.blocker.getPolicies();
    }

    /**
     * Get current policy count
     */
    getPolicyCount(): number {
        return this.policies.length;
    }
}
