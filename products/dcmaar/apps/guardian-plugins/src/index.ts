import type { IDataCollector } from "@ghatana/dcmaar-plugin-abstractions";

/**
 * Shared plugin ID constants so manifests and wiring stay in sync.
 */
export const GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID = "guardian-usage-collector";
export const GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID = "guardian-policy-evaluator";
export const GUARDIAN_CONTENT_METRICS_PLUGIN_ID = "guardian-content-metrics";
export const GUARDIAN_CATEGORY_ENRICHMENT_PLUGIN_ID = "guardian-category-enrichment";
export const GUARDIAN_GAMIFICATION_PLUGIN_ID = "guardian-gamification";
export const GUARDIAN_SMART_ALERTS_PLUGIN_ID = "guardian-smart-alerts";

/**
 * Base Guardian plugin metadata shared by all Guardian plugins.
 */
export interface GuardianPluginMetadata extends Record<string, unknown> {
    appId: string;
    pluginId: string;
    capabilities?: string[];
    options?: Record<string, unknown>;
}

export interface GuardianUsageCollectorConfig {
    sampleRate: number;
}

interface GuardianUsageStats {
    totalVisits: number;
    blockedVisits: number;
    totalDurationMs: number;
    lastVisitTimestamp?: number;
}

/**
 * GuardianUsageCollectorPlugin
 *
 * Minimal IDataCollector implementation intended for use from the
 * Guardian browser extension. The initial implementation is a
 * placeholder; it can be extended later to read real usage metrics
 * from shared storage or controllers.
 */
export class GuardianUsageCollectorPlugin implements IDataCollector {
    id = GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID;
    name = "Guardian Usage Collector";
    version = "1.0.0";
    enabled = true;
    metadata?: GuardianPluginMetadata;
    private readonly config: GuardianUsageCollectorConfig;
    private readonly statsByDomain: Map<string, GuardianUsageStats> = new Map();

    constructor(metadata?: GuardianPluginMetadata) {
        this.metadata = metadata;
        const options = metadata?.options as Record<string, unknown> | undefined;
        const rawSampleRate = options?.sampleRate;
        const sampleRate =
            typeof rawSampleRate === "number" && !Number.isNaN(rawSampleRate)
                ? rawSampleRate
                : 1;
        const normalizedSampleRate =
            sampleRate >= 0 && sampleRate <= 1 ? sampleRate : 1;
        this.config = { sampleRate: normalizedSampleRate };
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    async shutdown(): Promise<void> {
        this.statsByDomain.clear();
        return Promise.resolve();
    }

    async execute(
        command: string,
        params?: Record<string, unknown>,
    ): Promise<unknown> {
        if (command !== "recordVisit") {
            if (command === "resetStats") {
                this.statsByDomain.clear();
                return { reset: true };
            }
            return undefined;
        }

        const safeParams: Record<string, unknown> = params ?? {};
        const urlValue = safeParams["url"];
        const blockedValue = safeParams["blocked"];
        const durationValue = safeParams["durationMs"];

        if (typeof urlValue !== "string" || urlValue.length === 0) {
            return { recorded: false, reason: "missing-url" };
        }

        if (this.config.sampleRate < 1 && Math.random() > this.config.sampleRate) {
            return { recorded: false, reason: "sampled-out" };
        }

        const domain = extractDomainFromUrl(urlValue);
        const existing = this.statsByDomain.get(domain) ?? {
            totalVisits: 0,
            blockedVisits: 0,
            totalDurationMs: 0,
            lastVisitTimestamp: undefined,
        };

        const blocked = blockedValue === true;
        const durationMs =
            typeof durationValue === "number" && durationValue > 0
                ? durationValue
                : 0;

        const now = Date.now();

        const updated: GuardianUsageStats = {
            totalVisits: existing.totalVisits + 1,
            blockedVisits: existing.blockedVisits + (blocked ? 1 : 0),
            totalDurationMs: existing.totalDurationMs + durationMs,
            lastVisitTimestamp: now,
        };

        this.statsByDomain.set(domain, updated);

        return {
            recorded: true,
            domain,
            blocked,
            durationMs,
            timestamp: now,
        };
    }

    async collect(_source: string): Promise<Record<string, unknown>> {
        const now = Date.now();
        const summary = this.buildSummary();
        return {
            timestamp: now,
            summary,
        };
    }

    async validate(_source: string): Promise<boolean> {
        return true;
    }

    async getSources(): Promise<string[]> {
        return ["guardian-usage"];
    }

    private buildSummary(): Record<string, unknown> {
        let totalVisits = 0;
        let blockedVisits = 0;
        let totalDurationMs = 0;

        const byDomain: Record<string, unknown> = {};

        for (const [domain, stats] of this.statsByDomain.entries()) {
            totalVisits += stats.totalVisits;
            blockedVisits += stats.blockedVisits;
            totalDurationMs += stats.totalDurationMs;
            byDomain[domain] = {
                totalVisits: stats.totalVisits,
                blockedVisits: stats.blockedVisits,
                totalDurationMs: stats.totalDurationMs,
                lastVisitTimestamp: stats.lastVisitTimestamp,
            };
        }

        return {
            totalVisits,
            blockedVisits,
            totalDurationMs,
            byDomain,
        };
    }
}

/**
 * Policy evaluation result used by the Guardian policy plugin.
 */
export interface GuardianPolicyDecision {
    decision: "allow" | "block" | "warn";
    reasons: string[];
    timestamp: number;
}

export interface GuardianPolicyRuleConfig {
    decision: "allow" | "block" | "warn";
    domains?: string[];
    urlPatterns?: string[];
    categories?: string[];
}

export interface GuardianPolicyConfig {
    defaultDecision: "allow" | "block" | "warn";
    rules: GuardianPolicyRuleConfig[];
}

/**
 * GuardianPolicyEvaluationPlugin
 *
 * Lightweight policy evaluation plugin. For now it exposes a simple
 * `execute("evaluate", params)` contract that always returns an
 * "allow" decision; the intent is that Guardian can later enrich this
 * to use real policy configuration while keeping the same surface.
 */
export class GuardianPolicyEvaluationPlugin implements IDataCollector {
    id = GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID;
    name = "Guardian Policy Evaluator";
    version = "1.0.0";
    enabled = true;
    metadata?: GuardianPluginMetadata;
    private readonly config: GuardianPolicyConfig;

    constructor(metadata?: GuardianPluginMetadata) {
        this.metadata = metadata;
        this.config = this.buildConfigFromMetadata(metadata);
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    async shutdown(): Promise<void> {
        return Promise.resolve();
    }

    async execute(
        command: string,
        params?: Record<string, unknown>,
    ): Promise<unknown> {
        if (command !== "evaluate") {
            return undefined;
        }

        const now = Date.now();
        const safeParams: Record<string, unknown> = params ?? {};
        const urlValue = safeParams["url"];
        const categoryValue = safeParams["category"];

        const url = typeof urlValue === "string" ? urlValue : undefined;
        const category =
            typeof categoryValue === "string" ? categoryValue : undefined;
        const domain = url ? extractDomainFromUrl(url) : undefined;

        const decision = this.evaluatePolicy(domain, url, category);
        const reasons = this.computeReasons(decision, domain, url, category);

        const result: GuardianPolicyDecision = {
            decision,
            reasons,
            timestamp: now,
        };

        return result;
    }

    async collect(_source: string): Promise<Record<string, unknown>> {
        // This plugin is command-oriented; `collect` returns a trivial
        // snapshot for now.
        return {
            timestamp: Date.now(),
            status: "idle",
        };
    }

    async validate(_source: string): Promise<boolean> {
        return true;
    }

    async getSources(): Promise<string[]> {
        return ["guardian-policy"];
    }

    private buildConfigFromMetadata(
        metadata?: GuardianPluginMetadata,
    ): GuardianPolicyConfig {
        const options = metadata?.options as Record<string, unknown> | undefined;
        const rawDefaultDecision = options?.defaultDecision;
        const defaultDecision: "allow" | "block" | "warn" =
            rawDefaultDecision === "block" || rawDefaultDecision === "warn"
                ? rawDefaultDecision
                : "allow";

        const rawRules = options?.rules;
        const rules: GuardianPolicyRuleConfig[] = [];
        if (Array.isArray(rawRules)) {
            for (const entry of rawRules) {
                if (!entry || typeof entry !== "object") {
                    continue;
                }
                const record = entry as Record<string, unknown>;
                const decisionValue = record.decision;
                if (
                    decisionValue !== "allow" &&
                    decisionValue !== "block" &&
                    decisionValue !== "warn"
                ) {
                    continue;
                }
                const domains = toStringArray(record.domains);
                const urlPatterns = toStringArray(record.urlPatterns);
                const categories = toStringArray(record.categories);
                rules.push({
                    decision: decisionValue,
                    domains,
                    urlPatterns,
                    categories,
                });
            }
        }

        return { defaultDecision, rules };
    }

    private evaluatePolicy(
        domain?: string,
        url?: string,
        category?: string,
    ): "allow" | "block" | "warn" {
        for (const rule of this.config.rules) {
            const matchesDomain =
                domain && rule.domains
                    ? rule.domains.some((pattern) => matchesDomainPattern(pattern, domain))
                    : false;
            const matchesUrl =
                url && rule.urlPatterns
                    ? rule.urlPatterns.some((pattern) => matchesWildcardPattern(pattern, url))
                    : false;
            const matchesCategory =
                category && rule.categories
                    ? rule.categories.includes(category)
                    : false;

            if (!rule.domains && !rule.urlPatterns && !rule.categories) {
                return rule.decision;
            }

            if (matchesDomain || matchesUrl || matchesCategory) {
                return rule.decision;
            }
        }

        return this.config.defaultDecision;
    }

    private computeReasons(
        decision: "allow" | "block" | "warn",
        domain?: string,
        url?: string,
        category?: string,
    ): string[] {
        const reasons: string[] = [`final-decision=${decision}`];
        for (const rule of this.config.rules) {
            const matchesDomain =
                domain && rule.domains
                    ? rule.domains.some((pattern) => matchesDomainPattern(pattern, domain))
                    : false;
            const matchesUrl =
                url && rule.urlPatterns
                    ? rule.urlPatterns.some((pattern) => matchesWildcardPattern(pattern, url))
                    : false;
            const matchesCategory =
                category && rule.categories
                    ? rule.categories.includes(category)
                    : false;

            if (!rule.domains && !rule.urlPatterns && !rule.categories) {
                reasons.push(`default-rule:${rule.decision}`);
                continue;
            }

            if (!(matchesDomain || matchesUrl || matchesCategory)) {
                continue;
            }

            const parts: string[] = [];
            parts.push(`decision=${rule.decision}`);
            if (rule.domains && rule.domains.length > 0) {
                parts.push(`domains=${rule.domains.join(",")}`);
            }
            if (rule.urlPatterns && rule.urlPatterns.length > 0) {
                parts.push(`urlPatterns=${rule.urlPatterns.join(",")}`);
            }
            if (rule.categories && rule.categories.length > 0) {
                parts.push(`categories=${rule.categories.join(",")}`);
            }
            reasons.push(parts.join(";"));
        }

        if (reasons.length === 0) {
            reasons.push("default-decision");
        }

        return reasons;
    }
}

function extractDomainFromUrl(url: string): string {
    try {
        const parsed = new URL(url);
        return parsed.hostname || "unknown";
    } catch {
        return "unknown";
    }
}

function toStringArray(value: unknown): string[] | undefined {
    if (!Array.isArray(value)) {
        return undefined;
    }
    const result: string[] = [];
    for (const entry of value) {
        if (typeof entry === "string" && entry.length > 0) {
            result.push(entry);
        }
    }
    return result.length > 0 ? result : undefined;
}

function matchesDomainPattern(pattern: string, domain: string): boolean {
    if (!pattern) {
        return false;
    }
    if (pattern === domain) {
        return true;
    }
    if (pattern.startsWith("*")) {
        const suffix = pattern.slice(1);
        return suffix.length > 0 && domain.endsWith(suffix);
    }
    return false;
}

function matchesWildcardPattern(pattern: string, value: string): boolean {
    if (!pattern) {
        return false;
    }
    if (!pattern.includes("*")) {
        return value === pattern;
    }
    const escaped = pattern.replace(/[-/\\^$+?.()|[\]{}]/g, "\\$&");
    const source = `^${escaped.replace(/\*/g, ".*")}$`;
    try {
        const regex = new RegExp(source);
        return regex.test(value);
    } catch {
        return false;
    }
}

// ============================================================================
// ContentMetricsPlugin - Collects page-level metrics from content script
// ============================================================================

/**
 * Page metrics data structure
 */
export interface PageMetrics {
    url: string;
    domain: string;
    title?: string;
    scrollDepth: number;
    clickCount: number;
    formInteractions: number;
    activeTimeMs: number;
    mediaEvents: MediaEventRecord[];
    timestamp: number;
}

/**
 * Media event record
 */
export interface MediaEventRecord {
    mediaType: "video" | "audio";
    platform: string;
    title?: string;
    durationSeconds: number;
    state: "playing" | "paused" | "ended";
    videoId?: string;
    timestamp: number;
}

/**
 * ContentMetricsPlugin
 *
 * Collects and aggregates page-level metrics from content scripts.
 * Implements IDataCollector for integration with the plugin host.
 */
export class ContentMetricsPlugin implements IDataCollector {
    id = GUARDIAN_CONTENT_METRICS_PLUGIN_ID;
    name = "Guardian Content Metrics";
    version = "1.0.0";
    enabled = true;
    metadata?: GuardianPluginMetadata;
    private readonly pageMetrics: Map<string, PageMetrics> = new Map();
    private readonly mediaEvents: MediaEventRecord[] = [];

    constructor(metadata?: GuardianPluginMetadata) {
        this.metadata = metadata;
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    async shutdown(): Promise<void> {
        this.pageMetrics.clear();
        this.mediaEvents.length = 0;
        return Promise.resolve();
    }

    async execute(
        command: string,
        params?: Record<string, unknown>,
    ): Promise<unknown> {
        const safeParams: Record<string, unknown> = params ?? {};

        switch (command) {
            case "recordPageMetrics": {
                const url = safeParams["url"];
                if (typeof url !== "string") {
                    return { recorded: false, reason: "missing-url" };
                }
                const metrics: PageMetrics = {
                    url,
                    domain: extractDomainFromUrl(url),
                    title: typeof safeParams["title"] === "string" ? safeParams["title"] : undefined,
                    scrollDepth: typeof safeParams["scrollDepth"] === "number" ? safeParams["scrollDepth"] : 0,
                    clickCount: typeof safeParams["clickCount"] === "number" ? safeParams["clickCount"] : 0,
                    formInteractions: typeof safeParams["formInteractions"] === "number" ? safeParams["formInteractions"] : 0,
                    activeTimeMs: typeof safeParams["activeTimeMs"] === "number" ? safeParams["activeTimeMs"] : 0,
                    mediaEvents: [],
                    timestamp: Date.now(),
                };
                this.pageMetrics.set(url, metrics);
                return { recorded: true, url };
            }

            case "recordMediaEvent": {
                const event: MediaEventRecord = {
                    mediaType: safeParams["mediaType"] === "audio" ? "audio" : "video",
                    platform: typeof safeParams["platform"] === "string" ? safeParams["platform"] : "unknown",
                    title: typeof safeParams["title"] === "string" ? safeParams["title"] : undefined,
                    durationSeconds: typeof safeParams["durationSeconds"] === "number" ? safeParams["durationSeconds"] : 0,
                    state: safeParams["state"] === "paused" ? "paused" : safeParams["state"] === "ended" ? "ended" : "playing",
                    videoId: typeof safeParams["videoId"] === "string" ? safeParams["videoId"] : undefined,
                    timestamp: Date.now(),
                };
                this.mediaEvents.push(event);
                // Keep only last 100 events
                if (this.mediaEvents.length > 100) {
                    this.mediaEvents.shift();
                }
                return { recorded: true, event };
            }

            case "getMetrics": {
                return {
                    pageCount: this.pageMetrics.size,
                    mediaEventCount: this.mediaEvents.length,
                    pages: Array.from(this.pageMetrics.values()),
                    recentMedia: this.mediaEvents.slice(-10),
                };
            }

            case "reset": {
                this.pageMetrics.clear();
                this.mediaEvents.length = 0;
                return { reset: true };
            }

            default:
                return undefined;
        }
    }

    async collect(_source: string): Promise<Record<string, unknown>> {
        return {
            timestamp: Date.now(),
            pageCount: this.pageMetrics.size,
            mediaEventCount: this.mediaEvents.length,
        };
    }

    async validate(_source: string): Promise<boolean> {
        return true;
    }

    async getSources(): Promise<string[]> {
        return ["guardian-content-metrics"];
    }
}

// ============================================================================
// CategoryEnrichmentPlugin - Enriches events with website category
// ============================================================================

/**
 * Website category enum
 */
export enum WebsiteCategory {
    SOCIAL = "social",
    GAMING = "gaming",
    STREAMING = "streaming",
    ADULT = "adult",
    SHOPPING = "shopping",
    NEWS = "news",
    EDUCATION = "education",
    PRODUCTIVITY = "productivity",
    COMMUNICATION = "communication",
    ENTERTAINMENT = "entertainment",
    OTHER = "other",
}

/**
 * Category enrichment result
 */
export interface CategoryEnrichmentResult {
    domain: string;
    category: WebsiteCategory;
    confidence: number;
    isKnownDomain: boolean;
}

/**
 * CategoryEnrichmentPlugin
 *
 * Enriches domains with category information using a local database
 * and optional external API lookup.
 */
export class CategoryEnrichmentPlugin implements IDataCollector {
    id = GUARDIAN_CATEGORY_ENRICHMENT_PLUGIN_ID;
    name = "Guardian Category Enrichment";
    version = "1.0.0";
    enabled = true;
    metadata?: GuardianPluginMetadata;
    private readonly categoryCache: Map<string, CategoryEnrichmentResult> = new Map();

    // Local domain category database
    private static readonly DOMAIN_CATEGORIES: Record<string, WebsiteCategory> = {
        // Social Media
        "facebook.com": WebsiteCategory.SOCIAL,
        "instagram.com": WebsiteCategory.SOCIAL,
        "twitter.com": WebsiteCategory.SOCIAL,
        "x.com": WebsiteCategory.SOCIAL,
        "tiktok.com": WebsiteCategory.SOCIAL,
        "reddit.com": WebsiteCategory.SOCIAL,
        "linkedin.com": WebsiteCategory.SOCIAL,
        // Gaming
        "twitch.tv": WebsiteCategory.GAMING,
        "steam.com": WebsiteCategory.GAMING,
        "roblox.com": WebsiteCategory.GAMING,
        "minecraft.net": WebsiteCategory.GAMING,
        // Streaming
        "youtube.com": WebsiteCategory.STREAMING,
        "netflix.com": WebsiteCategory.STREAMING,
        "spotify.com": WebsiteCategory.STREAMING,
        "hulu.com": WebsiteCategory.STREAMING,
        // Education
        "khanacademy.org": WebsiteCategory.EDUCATION,
        "coursera.org": WebsiteCategory.EDUCATION,
        "wikipedia.org": WebsiteCategory.EDUCATION,
        "duolingo.com": WebsiteCategory.EDUCATION,
        // Communication
        "gmail.com": WebsiteCategory.COMMUNICATION,
        "slack.com": WebsiteCategory.COMMUNICATION,
        "discord.com": WebsiteCategory.COMMUNICATION,
        "zoom.us": WebsiteCategory.COMMUNICATION,
        // Shopping
        "amazon.com": WebsiteCategory.SHOPPING,
        "ebay.com": WebsiteCategory.SHOPPING,
        // News
        "cnn.com": WebsiteCategory.NEWS,
        "bbc.com": WebsiteCategory.NEWS,
        "nytimes.com": WebsiteCategory.NEWS,
    };

    constructor(metadata?: GuardianPluginMetadata) {
        this.metadata = metadata;
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    async shutdown(): Promise<void> {
        this.categoryCache.clear();
        return Promise.resolve();
    }

    async execute(
        command: string,
        params?: Record<string, unknown>,
    ): Promise<unknown> {
        const safeParams: Record<string, unknown> = params ?? {};

        switch (command) {
            case "categorize": {
                const url = safeParams["url"];
                const domain = typeof url === "string" ? extractDomainFromUrl(url) :
                    typeof safeParams["domain"] === "string" ? safeParams["domain"] : undefined;

                if (!domain) {
                    return { success: false, reason: "missing-domain" };
                }

                const result = this.categorize(domain);
                return { success: true, ...result };
            }

            case "getCacheStats": {
                return {
                    cacheSize: this.categoryCache.size,
                };
            }

            case "clearCache": {
                this.categoryCache.clear();
                return { cleared: true };
            }

            default:
                return undefined;
        }
    }

    private categorize(domain: string): CategoryEnrichmentResult {
        // Check cache
        const cached = this.categoryCache.get(domain);
        if (cached) {
            return cached;
        }

        // Check exact match
        const exactMatch = CategoryEnrichmentPlugin.DOMAIN_CATEGORIES[domain];
        if (exactMatch) {
            const result: CategoryEnrichmentResult = {
                domain,
                category: exactMatch,
                confidence: 1.0,
                isKnownDomain: true,
            };
            this.categoryCache.set(domain, result);
            return result;
        }

        // Check subdomain match
        for (const [knownDomain, category] of Object.entries(CategoryEnrichmentPlugin.DOMAIN_CATEGORIES)) {
            if (domain.endsWith(`.${knownDomain}`)) {
                const result: CategoryEnrichmentResult = {
                    domain,
                    category,
                    confidence: 0.9,
                    isKnownDomain: true,
                };
                this.categoryCache.set(domain, result);
                return result;
            }
        }

        // Heuristic categorization
        const heuristicCategory = this.heuristicCategorize(domain);
        const result: CategoryEnrichmentResult = {
            domain,
            category: heuristicCategory,
            confidence: heuristicCategory === WebsiteCategory.OTHER ? 0.1 : 0.5,
            isKnownDomain: false,
        };
        this.categoryCache.set(domain, result);
        return result;
    }

    private heuristicCategorize(domain: string): WebsiteCategory {
        const lower = domain.toLowerCase();
        if (lower.includes("edu") || lower.includes("school") || lower.includes("learn")) {
            return WebsiteCategory.EDUCATION;
        }
        if (lower.includes("game") || lower.includes("play")) {
            return WebsiteCategory.GAMING;
        }
        if (lower.includes("shop") || lower.includes("store") || lower.includes("buy")) {
            return WebsiteCategory.SHOPPING;
        }
        if (lower.includes("news") || lower.includes("press")) {
            return WebsiteCategory.NEWS;
        }
        if (lower.includes("video") || lower.includes("stream") || lower.includes("watch")) {
            return WebsiteCategory.STREAMING;
        }
        if (lower.includes("social") || lower.includes("chat") || lower.includes("forum")) {
            return WebsiteCategory.SOCIAL;
        }
        if (lower.includes("porn") || lower.includes("xxx") || lower.includes("adult")) {
            return WebsiteCategory.ADULT;
        }
        return WebsiteCategory.OTHER;
    }

    async collect(_source: string): Promise<Record<string, unknown>> {
        return {
            timestamp: Date.now(),
            cacheSize: this.categoryCache.size,
        };
    }

    async validate(_source: string): Promise<boolean> {
        return true;
    }

    async getSources(): Promise<string[]> {
        return ["guardian-category-enrichment"];
    }
}

// ============================================================================
// GamificationPlugin - Tracks achievements and rewards
// ============================================================================

/**
 * Achievement definition
 */
export interface Achievement {
    id: string;
    name: string;
    description: string;
    icon: string;
    category: "safety" | "productivity" | "learning" | "streak";
    threshold: number;
    unlockedAt?: number;
}

/**
 * User progress data
 */
export interface UserProgress {
    totalSafeHours: number;
    productiveStreak: number;
    learningMinutes: number;
    blockedAttempts: number;
    achievements: Achievement[];
    level: number;
    xp: number;
}

/**
 * GamificationPlugin
 *
 * Tracks user achievements, rewards, and progress for child engagement.
 */
export class GamificationPlugin implements IDataCollector {
    id = GUARDIAN_GAMIFICATION_PLUGIN_ID;
    name = "Guardian Gamification";
    version = "1.0.0";
    enabled = true;
    metadata?: GuardianPluginMetadata;

    private progress: UserProgress = {
        totalSafeHours: 0,
        productiveStreak: 0,
        learningMinutes: 0,
        blockedAttempts: 0,
        achievements: [],
        level: 1,
        xp: 0,
    };

    private static readonly ACHIEVEMENTS: Omit<Achievement, "unlockedAt">[] = [
        { id: "safe-start", name: "Safe Start", description: "Complete your first safe browsing hour", icon: "🛡️", category: "safety", threshold: 1 },
        { id: "safe-day", name: "Safe Day", description: "Browse safely for 8 hours", icon: "🏆", category: "safety", threshold: 8 },
        { id: "learning-explorer", name: "Learning Explorer", description: "Spend 30 minutes on educational sites", icon: "📚", category: "learning", threshold: 30 },
        { id: "focus-master", name: "Focus Master", description: "Maintain a 3-day productive streak", icon: "🎯", category: "streak", threshold: 3 },
        { id: "week-warrior", name: "Week Warrior", description: "Maintain a 7-day productive streak", icon: "⭐", category: "streak", threshold: 7 },
        { id: "self-control", name: "Self Control", description: "Avoid 10 blocked sites", icon: "💪", category: "productivity", threshold: 10 },
    ];

    constructor(metadata?: GuardianPluginMetadata) {
        this.metadata = metadata;
    }

    async initialize(): Promise<void> {
        return Promise.resolve();
    }

    async shutdown(): Promise<void> {
        return Promise.resolve();
    }

    async execute(
        command: string,
        params?: Record<string, unknown>,
    ): Promise<unknown> {
        const safeParams: Record<string, unknown> = params ?? {};

        switch (command) {
            case "addSafeHours": {
                const hours = typeof safeParams["hours"] === "number" ? safeParams["hours"] : 0;
                this.progress.totalSafeHours += hours;
                this.progress.xp += Math.floor(hours * 10);
                this.checkAchievements();
                this.updateLevel();
                return { success: true, totalSafeHours: this.progress.totalSafeHours };
            }

            case "addLearningMinutes": {
                const minutes = typeof safeParams["minutes"] === "number" ? safeParams["minutes"] : 0;
                this.progress.learningMinutes += minutes;
                this.progress.xp += Math.floor(minutes * 2);
                this.checkAchievements();
                this.updateLevel();
                return { success: true, learningMinutes: this.progress.learningMinutes };
            }

            case "incrementStreak": {
                this.progress.productiveStreak += 1;
                this.progress.xp += 50;
                this.checkAchievements();
                this.updateLevel();
                return { success: true, streak: this.progress.productiveStreak };
            }

            case "resetStreak": {
                this.progress.productiveStreak = 0;
                return { success: true, streak: 0 };
            }

            case "recordBlockedAttempt": {
                this.progress.blockedAttempts += 1;
                this.progress.xp += 5;
                this.checkAchievements();
                this.updateLevel();
                return { success: true, blockedAttempts: this.progress.blockedAttempts };
            }

            case "getProgress": {
                return { ...this.progress };
            }

            case "getAchievements": {
                return {
                    unlocked: this.progress.achievements,
                    available: GamificationPlugin.ACHIEVEMENTS.filter(
                        (a) => !this.progress.achievements.some((u) => u.id === a.id)
                    ),
                };
            }

            default:
                return undefined;
        }
    }

    private checkAchievements(): void {
        for (const achievement of GamificationPlugin.ACHIEVEMENTS) {
            if (this.progress.achievements.some((a) => a.id === achievement.id)) {
                continue;
            }

            let unlocked = false;
            switch (achievement.category) {
                case "safety":
                    unlocked = this.progress.totalSafeHours >= achievement.threshold;
                    break;
                case "learning":
                    unlocked = this.progress.learningMinutes >= achievement.threshold;
                    break;
                case "streak":
                    unlocked = this.progress.productiveStreak >= achievement.threshold;
                    break;
                case "productivity":
                    unlocked = this.progress.blockedAttempts >= achievement.threshold;
                    break;
            }

            if (unlocked) {
                this.progress.achievements.push({
                    ...achievement,
                    unlockedAt: Date.now(),
                });
                this.progress.xp += 100;
            }
        }
    }

    private updateLevel(): void {
        // Simple level calculation: level = floor(sqrt(xp / 100)) + 1
        this.progress.level = Math.floor(Math.sqrt(this.progress.xp / 100)) + 1;
    }

    async collect(_source: string): Promise<Record<string, unknown>> {
        return {
            timestamp: Date.now(),
            ...this.progress,
        };
    }

    async validate(_source: string): Promise<boolean> {
        return true;
    }

    async getSources(): Promise<string[]> {
        return ["guardian-gamification"];
    }
}
