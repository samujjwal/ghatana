/**
 * @fileoverview Pipeline Types Tests
 * 
 * Unit tests for the Guardian event pipeline types.
 * These tests verify type structures without requiring
 * browser extension environment.
 */

import { describe, it, expect } from 'vitest';
import type {
    GuardianEvent,
    TabActivityEvent,
    ContentScriptEvent,
    PageMetadata,
    InteractionData,
    DailyUsage,
    DomainUsage,
    EnrichedEvent,
    PolicyEvaluatedEvent,
} from '../pipeline/types';

describe('Pipeline Types', () => {
    describe('TabActivityEvent', () => {
        it('should have correct structure', () => {
            const event: TabActivityEvent = {
                id: 'test-1',
                type: 'tab_activity',
                timestamp: Date.now(),
                source: 'test',
                tabId: 1,
                url: 'https://example.com',
                domain: 'example.com',
                title: 'Example',
                action: 'navigate',
            };

            expect(event.type).toBe('tab_activity');
            expect(event.tabId).toBe(1);
            expect(event.action).toBe('navigate');
        });

        it('should support optional duration', () => {
            const event: TabActivityEvent = {
                id: 'test-2',
                type: 'tab_activity',
                timestamp: Date.now(),
                source: 'test',
                tabId: 1,
                url: 'https://example.com',
                domain: 'example.com',
                title: 'Example',
                action: 'close',
                duration: 30000,
            };

            expect(event.duration).toBe(30000);
        });
    });

    describe('ContentScriptEvent', () => {
        it('should have correct structure for page_view', () => {
            const event: ContentScriptEvent = {
                id: 'content-1',
                type: 'page_view',
                timestamp: Date.now(),
                source: 'content-script',
                tabId: 1,
                url: 'https://example.com',
                domain: 'example.com',
                pageMetadata: {
                    title: 'Example Page',
                    description: 'A test page',
                },
            };

            expect(event.type).toBe('page_view');
            expect(event.pageMetadata?.title).toBe('Example Page');
        });

        it('should support interaction data', () => {
            const event: ContentScriptEvent = {
                id: 'content-2',
                type: 'page_interaction',
                timestamp: Date.now(),
                source: 'content-script',
                tabId: 1,
                url: 'https://example.com',
                domain: 'example.com',
                interactions: {
                    scrollDepth: 75,
                    clickCount: 5,
                    formInteractions: 2,
                    activeTime: 30000,
                    hasScrolled: true,
                },
            };

            expect(event.interactions?.scrollDepth).toBe(75);
            expect(event.interactions?.hasScrolled).toBe(true);
        });

        it('should support media tracking', () => {
            const event: ContentScriptEvent = {
                id: 'content-3',
                type: 'media_event',
                timestamp: Date.now(),
                source: 'content-script',
                tabId: 1,
                url: 'https://youtube.com/watch?v=123',
                domain: 'youtube.com',
                media: {
                    mediaType: 'video',
                    platform: 'youtube',
                    title: 'Test Video',
                    duration: 120,
                    state: 'playing',
                    videoId: '123',
                },
            };

            expect(event.media?.platform).toBe('youtube');
            expect(event.media?.state).toBe('playing');
        });
    });

    describe('PageMetadata', () => {
        it('should have correct structure', () => {
            const metadata: PageMetadata = {
                title: 'Test Page',
                description: 'A test description',
                ogTitle: 'OG Title',
                ogDescription: 'OG Description',
                ogImage: 'https://example.com/image.jpg',
                canonicalUrl: 'https://example.com/page',
                keywords: ['test', 'example'],
                language: 'en',
            };

            expect(metadata.title).toBe('Test Page');
            expect(metadata.keywords).toContain('test');
        });
    });

    describe('DailyUsage', () => {
        it('should have correct structure', () => {
            const usage: DailyUsage = {
                date: '2024-01-15',
                totalTime: 3600000,
                categories: {
                    social: 1800000,
                    education: 1200000,
                    other: 600000,
                },
                domains: {
                    'facebook.com': {
                        time: 1800000,
                        visits: 5,
                        category: 'social',
                        lastVisit: Date.now(),
                    },
                },
                hourlyActivity: Array(24).fill(0),
            };

            expect(usage.date).toBe('2024-01-15');
            expect(usage.totalTime).toBe(3600000);
            expect(usage.categories.social).toBe(1800000);
            expect(usage.domains['facebook.com'].visits).toBe(5);
        });
    });

    describe('EnrichedEvent', () => {
        it('should extend GuardianEvent with category', () => {
            const event: EnrichedEvent = {
                id: 'enriched-1',
                type: 'tab_activity',
                timestamp: Date.now(),
                source: 'test',
                category: 'social' as any,
                categoryConfidence: 1.0,
                isKnownDomain: true,
            };

            expect(event.category).toBe('social');
            expect(event.categoryConfidence).toBe(1.0);
        });
    });

    describe('PolicyEvaluatedEvent', () => {
        it('should include policy decision', () => {
            const event: PolicyEvaluatedEvent = {
                id: 'policy-1',
                type: 'tab_activity',
                timestamp: Date.now(),
                source: 'test',
                policyDecision: 'block',
                policyId: 'policy-123',
                policyReason: 'Social media blocked during school hours',
                actionTaken: true,
            };

            expect(event.policyDecision).toBe('block');
            expect(event.actionTaken).toBe(true);
        });

        it('should support allow decision', () => {
            const event: PolicyEvaluatedEvent = {
                id: 'policy-2',
                type: 'tab_activity',
                timestamp: Date.now(),
                source: 'test',
                policyDecision: 'allow',
            };

            expect(event.policyDecision).toBe('allow');
            expect(event.actionTaken).toBeUndefined();
        });
    });
});

describe('Type Guards', () => {
    it('should distinguish event types', () => {
        const tabEvent: TabActivityEvent = {
            id: 'tab-1',
            type: 'tab_activity',
            timestamp: Date.now(),
            source: 'test',
            tabId: 1,
            url: 'https://example.com',
            domain: 'example.com',
            title: 'Example',
            action: 'navigate',
        };

        const contentEvent: ContentScriptEvent = {
            id: 'content-1',
            type: 'page_view',
            timestamp: Date.now(),
            source: 'content-script',
            tabId: 1,
            url: 'https://example.com',
            domain: 'example.com',
        };

        expect(tabEvent.type).toBe('tab_activity');
        expect(contentEvent.type).toBe('page_view');
        expect('action' in tabEvent).toBe(true);
        expect('action' in contentEvent).toBe(false);
    });
});
