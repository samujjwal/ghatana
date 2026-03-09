/**
 * @fileoverview Category Enrichment Processor
 * 
 * Processor that enriches events with website category information.
 * Uses local domain database and optional external API for categorization.
 * 
 * @module pipeline/processors/CategoryEnrichmentProcessor
 */

import { BaseEventProcessor } from '@ghatana/dcmaar-browser-extension-core';
import { WebsiteCategory } from '../../blocker/WebsiteBlocker';
import type { GuardianEvent, EnrichedEvent, TabActivityEvent, ContentScriptEvent } from '../types';

/**
 * Domain category database
 * Reuses the existing DOMAIN_CATEGORIES from WebsiteBlocker
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
    'threads.net': WebsiteCategory.SOCIAL,
    'mastodon.social': WebsiteCategory.SOCIAL,

    // Gaming
    'twitch.tv': WebsiteCategory.GAMING,
    'steam.com': WebsiteCategory.GAMING,
    'steampowered.com': WebsiteCategory.GAMING,
    'epicgames.com': WebsiteCategory.GAMING,
    'roblox.com': WebsiteCategory.GAMING,
    'minecraft.net': WebsiteCategory.GAMING,
    'chess.com': WebsiteCategory.GAMING,
    'pogo.com': WebsiteCategory.GAMING,
    'itch.io': WebsiteCategory.GAMING,
    'gog.com': WebsiteCategory.GAMING,

    // Streaming
    'youtube.com': WebsiteCategory.STREAMING,
    'netflix.com': WebsiteCategory.STREAMING,
    'hulu.com': WebsiteCategory.STREAMING,
    'disneyplus.com': WebsiteCategory.STREAMING,
    'primevideo.com': WebsiteCategory.STREAMING,
    'hbomax.com': WebsiteCategory.STREAMING,
    'max.com': WebsiteCategory.STREAMING,
    'spotify.com': WebsiteCategory.STREAMING,
    'soundcloud.com': WebsiteCategory.STREAMING,
    'vimeo.com': WebsiteCategory.STREAMING,
    'peacocktv.com': WebsiteCategory.STREAMING,
    'crunchyroll.com': WebsiteCategory.STREAMING,

    // Shopping
    'amazon.com': WebsiteCategory.SHOPPING,
    'ebay.com': WebsiteCategory.SHOPPING,
    'walmart.com': WebsiteCategory.SHOPPING,
    'target.com': WebsiteCategory.SHOPPING,
    'etsy.com': WebsiteCategory.SHOPPING,
    'aliexpress.com': WebsiteCategory.SHOPPING,
    'wish.com': WebsiteCategory.SHOPPING,
    'bestbuy.com': WebsiteCategory.SHOPPING,

    // Education
    'khanacademy.org': WebsiteCategory.EDUCATION,
    'coursera.org': WebsiteCategory.EDUCATION,
    'edx.org': WebsiteCategory.EDUCATION,
    'udemy.com': WebsiteCategory.EDUCATION,
    'duolingo.com': WebsiteCategory.EDUCATION,
    'wikipedia.org': WebsiteCategory.EDUCATION,
    'quizlet.com': WebsiteCategory.EDUCATION,
    'brilliant.org': WebsiteCategory.EDUCATION,
    'codecademy.com': WebsiteCategory.EDUCATION,
    'skillshare.com': WebsiteCategory.EDUCATION,

    // Communication
    'gmail.com': WebsiteCategory.COMMUNICATION,
    'mail.google.com': WebsiteCategory.COMMUNICATION,
    'outlook.com': WebsiteCategory.COMMUNICATION,
    'outlook.live.com': WebsiteCategory.COMMUNICATION,
    'slack.com': WebsiteCategory.COMMUNICATION,
    'discord.com': WebsiteCategory.COMMUNICATION,
    'zoom.us': WebsiteCategory.COMMUNICATION,
    'teams.microsoft.com': WebsiteCategory.COMMUNICATION,
    'meet.google.com': WebsiteCategory.COMMUNICATION,
    'whatsapp.com': WebsiteCategory.COMMUNICATION,
    'web.whatsapp.com': WebsiteCategory.COMMUNICATION,
    'telegram.org': WebsiteCategory.COMMUNICATION,
    'web.telegram.org': WebsiteCategory.COMMUNICATION,

    // News
    'cnn.com': WebsiteCategory.NEWS,
    'bbc.com': WebsiteCategory.NEWS,
    'nytimes.com': WebsiteCategory.NEWS,
    'washingtonpost.com': WebsiteCategory.NEWS,
    'theguardian.com': WebsiteCategory.NEWS,
    'reuters.com': WebsiteCategory.NEWS,
    'apnews.com': WebsiteCategory.NEWS,
    'news.google.com': WebsiteCategory.NEWS,

    // Productivity
    'docs.google.com': WebsiteCategory.PRODUCTIVITY,
    'sheets.google.com': WebsiteCategory.PRODUCTIVITY,
    'slides.google.com': WebsiteCategory.PRODUCTIVITY,
    'drive.google.com': WebsiteCategory.PRODUCTIVITY,
    'notion.so': WebsiteCategory.PRODUCTIVITY,
    'trello.com': WebsiteCategory.PRODUCTIVITY,
    'asana.com': WebsiteCategory.PRODUCTIVITY,
    'monday.com': WebsiteCategory.PRODUCTIVITY,
    'github.com': WebsiteCategory.PRODUCTIVITY,
    'gitlab.com': WebsiteCategory.PRODUCTIVITY,
    'stackoverflow.com': WebsiteCategory.PRODUCTIVITY,
    'figma.com': WebsiteCategory.PRODUCTIVITY,
    'canva.com': WebsiteCategory.PRODUCTIVITY,
};

/**
 * Configuration for CategoryEnrichmentProcessor
 */
export interface CategoryEnrichmentConfig {
    /** Enable external API categorization for unknown domains */
    enableExternalApi?: boolean;
    /** External API URL for categorization */
    externalApiUrl?: string;
    /** Cache duration for external API results (ms) */
    cacheDuration?: number;
    /** Default category for unknown domains */
    defaultCategory?: WebsiteCategory;
}

const DEFAULT_CONFIG: Required<CategoryEnrichmentConfig> = {
    enableExternalApi: false,
    externalApiUrl: '',
    cacheDuration: 24 * 60 * 60 * 1000, // 24 hours
    defaultCategory: WebsiteCategory.OTHER,
};

/**
 * CategoryEnrichmentProcessor
 * 
 * Enriches events with website category information.
 * Uses a local database for known domains and optionally
 * queries an external API for unknown domains.
 * 
 * @example
 * ```typescript
 * const processor = new CategoryEnrichmentProcessor();
 * const enrichedEvent = await processor.process(tabEvent);
 * console.log(enrichedEvent.category); // 'social'
 * ```
 */
export class CategoryEnrichmentProcessor extends BaseEventProcessor<GuardianEvent, EnrichedEvent> {
    readonly name = 'category-enrichment';

    private readonly config: Required<CategoryEnrichmentConfig>;
    private categoryCache: Map<string, { category: WebsiteCategory; timestamp: number }> = new Map();

    constructor(config: CategoryEnrichmentConfig = {}) {
        super();
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    /**
     * Initialize the processor
     */
    async initialize(): Promise<void> {
        console.debug('[CategoryEnrichmentProcessor] Initialized');
    }

    /**
     * Shutdown the processor
     */
    async shutdown(): Promise<void> {
        this.categoryCache.clear();
        console.debug('[CategoryEnrichmentProcessor] Shutdown');
    }

    /**
     * Check if this processor can handle the event
     */
    canProcess(event: unknown): event is GuardianEvent {
        if (!event || typeof event !== 'object') return false;
        const e = event as Record<string, unknown>;
        return (
            typeof e.type === 'string' &&
            typeof e.timestamp === 'number' &&
            (e.type === 'tab_activity' || e.type === 'page_view' || e.type === 'page_interaction')
        );
    }

    /**
     * Process the event and add category information
     */
    async process(event: GuardianEvent): Promise<EnrichedEvent | null> {
        // Extract domain from event
        const domain = this.extractDomain(event);
        if (!domain) {
            return { ...event };
        }

        // Get category
        const { category, confidence, isKnown } = await this.categorize(domain);

        const enrichedEvent: EnrichedEvent = {
            ...event,
            category,
            categoryConfidence: confidence,
            isKnownDomain: isKnown,
            metadata: {
                ...event.metadata,
                enrichedAt: Date.now(),
            },
        };

        return enrichedEvent;
    }

    /**
     * Extract domain from event
     */
    private extractDomain(event: GuardianEvent): string | null {
        if ('domain' in event && typeof (event as TabActivityEvent | ContentScriptEvent).domain === 'string') {
            return (event as TabActivityEvent | ContentScriptEvent).domain;
        }
        if ('url' in event && typeof (event as TabActivityEvent | ContentScriptEvent).url === 'string') {
            try {
                const url = new URL((event as TabActivityEvent | ContentScriptEvent).url);
                return url.hostname.replace(/^www\./, '');
            } catch {
                return null;
            }
        }
        return null;
    }

    /**
     * Categorize a domain
     */
    private async categorize(domain: string): Promise<{
        category: WebsiteCategory;
        confidence: number;
        isKnown: boolean;
    }> {
        // Check cache first
        const cached = this.categoryCache.get(domain);
        if (cached && Date.now() - cached.timestamp < this.config.cacheDuration) {
            return {
                category: cached.category,
                confidence: 1.0,
                isKnown: true,
            };
        }

        // Check local database (exact match)
        if (domain in DOMAIN_CATEGORIES) {
            const category = DOMAIN_CATEGORIES[domain];
            this.categoryCache.set(domain, { category, timestamp: Date.now() });
            return { category, confidence: 1.0, isKnown: true };
        }

        // Check subdomain match
        for (const [knownDomain, category] of Object.entries(DOMAIN_CATEGORIES)) {
            if (domain.endsWith(`.${knownDomain}`)) {
                this.categoryCache.set(domain, { category, timestamp: Date.now() });
                return { category, confidence: 0.9, isKnown: true };
            }
        }

        // Heuristic categorization based on domain keywords
        const heuristicCategory = this.heuristicCategorize(domain);
        if (heuristicCategory !== this.config.defaultCategory) {
            this.categoryCache.set(domain, { category: heuristicCategory, timestamp: Date.now() });
            return { category: heuristicCategory, confidence: 0.5, isKnown: false };
        }

        // External API (if enabled)
        if (this.config.enableExternalApi && this.config.externalApiUrl) {
            try {
                const apiCategory = await this.fetchExternalCategory(domain);
                if (apiCategory) {
                    this.categoryCache.set(domain, { category: apiCategory, timestamp: Date.now() });
                    return { category: apiCategory, confidence: 0.8, isKnown: true };
                }
            } catch (error) {
                console.warn('[CategoryEnrichmentProcessor] External API error:', error);
            }
        }

        // Default category
        return {
            category: this.config.defaultCategory,
            confidence: 0.1,
            isKnown: false,
        };
    }

    /**
     * Heuristic categorization based on domain keywords
     */
    private heuristicCategorize(domain: string): WebsiteCategory {
        const lowerDomain = domain.toLowerCase();

        if (lowerDomain.includes('edu') || lowerDomain.includes('school') || lowerDomain.includes('learn')) {
            return WebsiteCategory.EDUCATION;
        }
        if (lowerDomain.includes('game') || lowerDomain.includes('play')) {
            return WebsiteCategory.GAMING;
        }
        if (lowerDomain.includes('shop') || lowerDomain.includes('store') || lowerDomain.includes('buy')) {
            return WebsiteCategory.SHOPPING;
        }
        if (lowerDomain.includes('news') || lowerDomain.includes('press')) {
            return WebsiteCategory.NEWS;
        }
        if (lowerDomain.includes('video') || lowerDomain.includes('stream') || lowerDomain.includes('watch')) {
            return WebsiteCategory.STREAMING;
        }
        if (lowerDomain.includes('social') || lowerDomain.includes('chat') || lowerDomain.includes('forum')) {
            return WebsiteCategory.SOCIAL;
        }
        if (lowerDomain.includes('porn') || lowerDomain.includes('xxx') || lowerDomain.includes('adult')) {
            return WebsiteCategory.ADULT;
        }

        return this.config.defaultCategory;
    }

    /**
     * Fetch category from external API
     */
    private async fetchExternalCategory(domain: string): Promise<WebsiteCategory | null> {
        if (!this.config.externalApiUrl) return null;

        try {
            const response = await fetch(`${this.config.externalApiUrl}?domain=${encodeURIComponent(domain)}`);
            if (!response.ok) return null;

            const data = await response.json();
            if (data.category && Object.values(WebsiteCategory).includes(data.category)) {
                return data.category as WebsiteCategory;
            }
        } catch {
            // Ignore fetch errors
        }

        return null;
    }

    /**
     * Get cache statistics
     */
    getCacheStats(): { size: number; hitRate?: number } {
        return {
            size: this.categoryCache.size,
        };
    }

    /**
     * Clear the category cache
     */
    clearCache(): void {
        this.categoryCache.clear();
    }
}
