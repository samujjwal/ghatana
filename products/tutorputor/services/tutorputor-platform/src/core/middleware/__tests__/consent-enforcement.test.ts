/**
 * Consent Enforcement Middleware Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test consent checking, caching, route matching
 * @doc.layer security
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
    createConsentEnforcement,
    ConsentCache,
    type ConsentCategory,
} from '../consent-enforcement';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        userConsent: {
            findMany: vi.fn().mockResolvedValue([]),
        },
    } as unknown as PrismaClient;
}

function makeRequest(overrides: Record<string, unknown> = {}): any {
    return {
        method: 'GET',
        url: '/api/v1/modules',
        user: { id: 'u1', tenantId: 't1', role: 'learner' },
        ...overrides,
    };
}

function makeReply(): any {
    const reply = {
        status: vi.fn().mockReturnThis(),
        send: vi.fn().mockReturnThis(),
    };
    return reply;
}

describe('ConsentCache', () => {
    it('returns null for unknown keys', () => {
        const cache = new ConsentCache();
        expect(cache.get('t1', 'u1')).toBeNull();
    });

    it('stores and retrieves categories', () => {
        const cache = new ConsentCache();
        cache.set('t1', 'u1', new Set(['analytics', 'essential'] as ConsentCategory[]));

        const result = cache.get('t1', 'u1');
        expect(result).not.toBeNull();
        expect(result!.has('analytics')).toBe(true);
    });

    it('invalidates cache entry', () => {
        const cache = new ConsentCache();
        cache.set('t1', 'u1', new Set(['analytics'] as ConsentCategory[]));
        cache.invalidate('t1', 'u1');

        expect(cache.get('t1', 'u1')).toBeNull();
    });

    it('clear removes all entries', () => {
        const cache = new ConsentCache();
        cache.set('t1', 'u1', new Set(['analytics'] as ConsentCategory[]));
        cache.set('t1', 'u2', new Set(['ai_processing'] as ConsentCategory[]));
        cache.clear();

        expect(cache.get('t1', 'u1')).toBeNull();
        expect(cache.get('t1', 'u2')).toBeNull();
    });
});

describe('createConsentEnforcement', () => {
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
    });

    // =========================================================================
    // Route matching
    // =========================================================================
    describe('findRequiredCategories', () => {
        it('returns ai_processing for /api/v1/ai/* routes', () => {
            const { findRequiredCategories } = createConsentEnforcement({ prisma });
            const cats = findRequiredCategories('POST', '/api/v1/ai/generate');
            expect(cats).toContain('ai_processing');
        });

        it('returns analytics for /api/v1/analytics/* routes', () => {
            const { findRequiredCategories } = createConsentEnforcement({ prisma });
            const cats = findRequiredCategories('GET', '/api/v1/analytics/dashboard');
            expect(cats).toContain('analytics');
        });

        it('returns empty for routes without consent requirements', () => {
            const { findRequiredCategories } = createConsentEnforcement({ prisma });
            const cats = findRequiredCategories('GET', '/api/v1/modules');
            expect(cats).toHaveLength(0);
        });

        it('returns multiple categories for /api/v1/recommendations/*', () => {
            const { findRequiredCategories } = createConsentEnforcement({ prisma });
            const cats = findRequiredCategories('GET', '/api/v1/recommendations/next');
            expect(cats).toContain('analytics');
            expect(cats).toContain('ai_processing');
        });
    });

    // =========================================================================
    // preHandler
    // =========================================================================
    describe('preHandler', () => {
        it('skips for unauthenticated requests', async () => {
            const { preHandler } = createConsentEnforcement({ prisma });
            const reply = makeReply();

            await preHandler(makeRequest({ user: null }), reply);

            expect(reply.status).not.toHaveBeenCalled();
        });

        it('skips for exempt roles (admin)', async () => {
            const { preHandler } = createConsentEnforcement({ prisma });
            const reply = makeReply();

            await preHandler(
                makeRequest({ url: '/api/v1/ai/generate', user: { id: 'u1', tenantId: 't1', role: 'admin' } }),
                reply,
            );

            expect(reply.status).not.toHaveBeenCalled();
        });

        it('skips for routes without consent requirements', async () => {
            const { preHandler } = createConsentEnforcement({ prisma });
            const reply = makeReply();

            await preHandler(makeRequest({ url: '/api/v1/modules' }), reply);

            expect(reply.status).not.toHaveBeenCalled();
        });

        it('blocks when required consent is missing', async () => {
            (prisma.userConsent.findMany as any).mockResolvedValue([]); // No consent granted
            const { preHandler } = createConsentEnforcement({ prisma, enableCache: false });
            const reply = makeReply();

            await preHandler(
                makeRequest({ url: '/api/v1/ai/generate', method: 'POST' }),
                reply,
            );

            expect(reply.status).toHaveBeenCalledWith(451);
            expect(reply.send).toHaveBeenCalledWith(
                expect.objectContaining({
                    error: 'Consent Required',
                    missingConsent: expect.arrayContaining(['ai_processing']),
                }),
            );
        });

        it('allows when consent is granted', async () => {
            (prisma.userConsent.findMany as any).mockResolvedValue([
                { category: 'ai_processing' },
            ]);
            const { preHandler } = createConsentEnforcement({ prisma, enableCache: false });
            const reply = makeReply();

            await preHandler(
                makeRequest({ url: '/api/v1/ai/generate', method: 'POST' }),
                reply,
            );

            expect(reply.status).not.toHaveBeenCalled();
        });

        it('always includes essential consent', async () => {
            const { fetchGrantedConsent } = createConsentEnforcement({ prisma, enableCache: false });

            const granted = await fetchGrantedConsent('t1', 'u1');
            expect(granted.has('essential')).toBe(true);
        });
    });

    // =========================================================================
    // Caching
    // =========================================================================
    describe('caching', () => {
        it('caches consent after first fetch', async () => {
            (prisma.userConsent.findMany as any).mockResolvedValue([
                { category: 'ai_processing' },
            ]);
            const { fetchGrantedConsent } = createConsentEnforcement({ prisma, enableCache: true });

            // First call
            await fetchGrantedConsent('t1', 'u1');
            const firstCallCount = (prisma.userConsent.findMany as any).mock.calls.length;

            // Second call (cached)
            await fetchGrantedConsent('t1', 'u1');
            expect((prisma.userConsent.findMany as any).mock.calls.length).toBe(firstCallCount);
        });
    });
});
