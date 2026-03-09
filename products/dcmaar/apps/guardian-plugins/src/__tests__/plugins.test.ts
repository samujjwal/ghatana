/**
 * @fileoverview Guardian Plugins Tests
 * 
 * Unit tests for the Guardian plugins:
 * - ContentMetricsPlugin
 * - CategoryEnrichmentPlugin
 * - GamificationPlugin
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
    ContentMetricsPlugin,
    CategoryEnrichmentPlugin,
    GamificationPlugin,
    WebsiteCategory,
    GUARDIAN_CONTENT_METRICS_PLUGIN_ID,
    GUARDIAN_CATEGORY_ENRICHMENT_PLUGIN_ID,
    GUARDIAN_GAMIFICATION_PLUGIN_ID,
} from '../index';

describe('ContentMetricsPlugin', () => {
    let plugin: ContentMetricsPlugin;

    beforeEach(async () => {
        plugin = new ContentMetricsPlugin();
        await plugin.initialize();
    });

    afterEach(async () => {
        await plugin.shutdown();
    });

    it('should have correct ID and name', () => {
        expect(plugin.id).toBe(GUARDIAN_CONTENT_METRICS_PLUGIN_ID);
        expect(plugin.name).toBe('Guardian Content Metrics');
        expect(plugin.enabled).toBe(true);
    });

    it('should record page metrics', async () => {
        const result = await plugin.execute('recordPageMetrics', {
            url: 'https://example.com',
            title: 'Example Page',
            scrollDepth: 50,
            clickCount: 5,
            formInteractions: 2,
            activeTimeMs: 30000,
        });

        expect(result).toEqual({ recorded: true, url: 'https://example.com' });
    });

    it('should reject page metrics without URL', async () => {
        const result = await plugin.execute('recordPageMetrics', {
            title: 'No URL',
        });

        expect(result).toEqual({ recorded: false, reason: 'missing-url' });
    });

    it('should record media events', async () => {
        const result = await plugin.execute('recordMediaEvent', {
            mediaType: 'video',
            platform: 'youtube',
            title: 'Test Video',
            durationSeconds: 120,
            state: 'playing',
            videoId: 'abc123',
        });

        expect(result).toHaveProperty('recorded', true);
        expect(result).toHaveProperty('event');
    });

    it('should get metrics', async () => {
        await plugin.execute('recordPageMetrics', {
            url: 'https://example.com',
            title: 'Example',
        });

        const result = await plugin.execute('getMetrics', {});

        expect(result).toHaveProperty('pageCount', 1);
        expect(result).toHaveProperty('pages');
    });

    it('should reset metrics', async () => {
        await plugin.execute('recordPageMetrics', {
            url: 'https://example.com',
        });

        await plugin.execute('reset', {});
        const result = await plugin.execute('getMetrics', {});

        expect(result).toHaveProperty('pageCount', 0);
    });

    it('should collect data', async () => {
        const data = await plugin.collect('test');

        expect(data).toHaveProperty('timestamp');
        expect(data).toHaveProperty('pageCount');
        expect(data).toHaveProperty('mediaEventCount');
    });

    it('should return sources', async () => {
        const sources = await plugin.getSources();

        expect(sources).toContain('guardian-content-metrics');
    });
});

describe('CategoryEnrichmentPlugin', () => {
    let plugin: CategoryEnrichmentPlugin;

    beforeEach(async () => {
        plugin = new CategoryEnrichmentPlugin();
        await plugin.initialize();
    });

    afterEach(async () => {
        await plugin.shutdown();
    });

    it('should have correct ID and name', () => {
        expect(plugin.id).toBe(GUARDIAN_CATEGORY_ENRICHMENT_PLUGIN_ID);
        expect(plugin.name).toBe('Guardian Category Enrichment');
        expect(plugin.enabled).toBe(true);
    });

    it('should categorize known social media domain', async () => {
        const result = await plugin.execute('categorize', {
            domain: 'facebook.com',
        });

        expect(result).toEqual({
            success: true,
            domain: 'facebook.com',
            category: WebsiteCategory.SOCIAL,
            confidence: 1.0,
            isKnownDomain: true,
        });
    });

    it('should categorize by URL', async () => {
        const result = await plugin.execute('categorize', {
            url: 'https://youtube.com/watch?v=123',
        });

        expect(result).toHaveProperty('success', true);
        expect(result).toHaveProperty('category', WebsiteCategory.STREAMING);
    });

    it('should categorize gaming domain', async () => {
        const result = await plugin.execute('categorize', {
            domain: 'twitch.tv',
        });

        expect(result).toHaveProperty('category', WebsiteCategory.GAMING);
    });

    it('should categorize education domain', async () => {
        const result = await plugin.execute('categorize', {
            domain: 'khanacademy.org',
        });

        expect(result).toHaveProperty('category', WebsiteCategory.EDUCATION);
    });

    it('should use heuristics for unknown domains', async () => {
        const result = await plugin.execute('categorize', {
            domain: 'supergamesite.com',
        });

        expect(result).toHaveProperty('success', true);
        expect(result).toHaveProperty('category', WebsiteCategory.GAMING);
        expect(result).toHaveProperty('confidence', 0.5);
        expect(result).toHaveProperty('isKnownDomain', false);
    });

    it('should return OTHER for unknown domains', async () => {
        const result = await plugin.execute('categorize', {
            domain: 'randomxyz123.net',
        });

        expect(result).toHaveProperty('category', WebsiteCategory.OTHER);
        expect(result).toHaveProperty('confidence', 0.1);
    });

    it('should handle subdomain matching', async () => {
        const result = await plugin.execute('categorize', {
            domain: 'm.facebook.com',
        });

        expect(result).toHaveProperty('category', WebsiteCategory.SOCIAL);
        expect(result).toHaveProperty('confidence', 0.9);
    });

    it('should reject missing domain', async () => {
        const result = await plugin.execute('categorize', {});

        expect(result).toEqual({ success: false, reason: 'missing-domain' });
    });

    it('should get cache stats', async () => {
        await plugin.execute('categorize', { domain: 'facebook.com' });
        const result = await plugin.execute('getCacheStats', {});

        expect(result).toHaveProperty('cacheSize');
        expect((result as { cacheSize: number }).cacheSize).toBeGreaterThan(0);
    });

    it('should clear cache', async () => {
        await plugin.execute('categorize', { domain: 'facebook.com' });
        await plugin.execute('clearCache', {});
        const result = await plugin.execute('getCacheStats', {});

        expect(result).toHaveProperty('cacheSize', 0);
    });
});

describe('GamificationPlugin', () => {
    let plugin: GamificationPlugin;

    beforeEach(async () => {
        plugin = new GamificationPlugin();
        await plugin.initialize();
    });

    afterEach(async () => {
        await plugin.shutdown();
    });

    it('should have correct ID and name', () => {
        expect(plugin.id).toBe(GUARDIAN_GAMIFICATION_PLUGIN_ID);
        expect(plugin.name).toBe('Guardian Gamification');
        expect(plugin.enabled).toBe(true);
    });

    it('should add safe hours and XP', async () => {
        const result = await plugin.execute('addSafeHours', { hours: 2 });

        expect(result).toHaveProperty('success', true);
        expect(result).toHaveProperty('totalSafeHours', 2);
    });

    it('should add learning minutes', async () => {
        const result = await plugin.execute('addLearningMinutes', { minutes: 30 });

        expect(result).toHaveProperty('success', true);
        expect(result).toHaveProperty('learningMinutes', 30);
    });

    it('should increment streak', async () => {
        const result = await plugin.execute('incrementStreak', {});

        expect(result).toHaveProperty('success', true);
        expect(result).toHaveProperty('streak', 1);
    });

    it('should reset streak', async () => {
        await plugin.execute('incrementStreak', {});
        await plugin.execute('incrementStreak', {});
        const result = await plugin.execute('resetStreak', {});

        expect(result).toHaveProperty('streak', 0);
    });

    it('should record blocked attempts', async () => {
        const result = await plugin.execute('recordBlockedAttempt', {});

        expect(result).toHaveProperty('success', true);
        expect(result).toHaveProperty('blockedAttempts', 1);
    });

    it('should get progress', async () => {
        await plugin.execute('addSafeHours', { hours: 1 });
        await plugin.execute('addLearningMinutes', { minutes: 15 });

        const result = await plugin.execute('getProgress', {});

        expect(result).toHaveProperty('totalSafeHours', 1);
        expect(result).toHaveProperty('learningMinutes', 15);
        expect(result).toHaveProperty('level');
        expect(result).toHaveProperty('xp');
        expect(result).toHaveProperty('achievements');
    });

    it('should unlock achievements', async () => {
        // Add enough safe hours to unlock "Safe Start" achievement
        await plugin.execute('addSafeHours', { hours: 1 });

        const result = await plugin.execute('getAchievements', {});

        expect(result).toHaveProperty('unlocked');
        expect(result).toHaveProperty('available');

        const unlocked = (result as { unlocked: Array<{ id: string }> }).unlocked;
        expect(unlocked.some(a => a.id === 'safe-start')).toBe(true);
    });

    it('should calculate level from XP', async () => {
        // Add enough XP to level up
        await plugin.execute('addSafeHours', { hours: 10 }); // 100 XP
        await plugin.execute('addLearningMinutes', { minutes: 50 }); // 100 XP

        const result = await plugin.execute('getProgress', {});

        expect((result as { level: number }).level).toBeGreaterThanOrEqual(1);
    });

    it('should collect data', async () => {
        await plugin.execute('addSafeHours', { hours: 1 });

        const data = await plugin.collect('test');

        expect(data).toHaveProperty('timestamp');
        expect(data).toHaveProperty('totalSafeHours', 1);
        expect(data).toHaveProperty('level');
        expect(data).toHaveProperty('xp');
    });

    it('should return sources', async () => {
        const sources = await plugin.getSources();

        expect(sources).toContain('guardian-gamification');
    });
});
