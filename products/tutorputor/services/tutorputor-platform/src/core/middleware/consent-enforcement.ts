/**
 * Consent Enforcement Middleware
 *
 * Verifies that the requesting user has granted the required consent
 * categories before allowing data-processing operations. Blocks requests
 * that touch personal data without proper consent.
 *
 * @doc.type middleware
 * @doc.purpose Enforce GDPR/CCPA consent requirements
 * @doc.layer security
 * @doc.pattern Middleware
 */

import type { FastifyRequest, FastifyReply, FastifyInstance } from 'fastify';
import type { PrismaClient } from '@ghatana/tutorputor-db';

// =============================================================================
// Types
// =============================================================================

export type ConsentCategory =
    | 'essential'
    | 'analytics'
    | 'ai_processing'
    | 'third_party_sharing'
    | 'marketing';

export interface ConsentRecord {
    userId: string;
    tenantId: string;
    category: ConsentCategory;
    granted: boolean;
    grantedAt: Date | null;
    revokedAt: Date | null;
    version: number;
}

export interface ConsentRequirement {
    /** Route pattern (e.g., '/api/v1/ai/*') */
    pattern: string;
    /** HTTP method (or '*' for all) */
    method: string;
    /** Required consent categories */
    categories: ConsentCategory[];
}

// =============================================================================
// Default route → consent mappings
// =============================================================================

const DEFAULT_REQUIREMENTS: ConsentRequirement[] = [
    { pattern: '/api/v1/ai/*', method: '*', categories: ['ai_processing'] },
    { pattern: '/api/v1/analytics/*', method: '*', categories: ['analytics'] },
    { pattern: '/api/v1/integrations/*', method: '*', categories: ['third_party_sharing'] },
    { pattern: '/api/v1/recommendations/*', method: '*', categories: ['analytics', 'ai_processing'] },
];

// =============================================================================
// Consent Cache (in-memory, TTL-based)
// =============================================================================

interface CachedConsent {
    categories: Set<ConsentCategory>;
    fetchedAt: number;
}

const CACHE_TTL_MS = 60_000; // 1 minute

export class ConsentCache {
    private cache = new Map<string, CachedConsent>();

    private key(tenantId: string, userId: string): string {
        return `${tenantId}:${userId}`;
    }

    get(tenantId: string, userId: string): Set<ConsentCategory> | null {
        const entry = this.cache.get(this.key(tenantId, userId));
        if (!entry) return null;
        if (Date.now() - entry.fetchedAt > CACHE_TTL_MS) {
            this.cache.delete(this.key(tenantId, userId));
            return null;
        }
        return entry.categories;
    }

    set(tenantId: string, userId: string, categories: Set<ConsentCategory>): void {
        this.cache.set(this.key(tenantId, userId), {
            categories,
            fetchedAt: Date.now(),
        });
    }

    invalidate(tenantId: string, userId: string): void {
        this.cache.delete(this.key(tenantId, userId));
    }

    clear(): void {
        this.cache.clear();
    }
}

// =============================================================================
// Middleware
// =============================================================================

export interface ConsentEnforcementOptions {
    prisma: PrismaClient;
    requirements?: ConsentRequirement[];
    /** Skip enforcement for these roles */
    exemptRoles?: string[];
    /** Enable caching */
    enableCache?: boolean;
}

export function createConsentEnforcement(options: ConsentEnforcementOptions) {
    const {
        prisma,
        requirements = DEFAULT_REQUIREMENTS,
        exemptRoles = ['admin', 'superadmin'],
        enableCache = true,
    } = options;

    const cache = enableCache ? new ConsentCache() : null;

    /**
     * Check if a route matches a consent requirement pattern.
     */
    function matchesPattern(routeUrl: string, pattern: string): boolean {
        if (pattern.endsWith('/*')) {
            const prefix = pattern.slice(0, -2);
            return routeUrl.startsWith(prefix);
        }
        return routeUrl === pattern;
    }

    /**
     * Find required consent categories for a request.
     */
    function findRequiredCategories(
        method: string,
        url: string,
    ): ConsentCategory[] {
        const categories = new Set<ConsentCategory>();

        for (const req of requirements) {
            if (req.method !== '*' && req.method.toUpperCase() !== method.toUpperCase()) {
                continue;
            }
            if (matchesPattern(url, req.pattern)) {
                for (const cat of req.categories) {
                    categories.add(cat);
                }
            }
        }

        return [...categories];
    }

    /**
     * Fetch granted consent categories for a user.
     */
    async function fetchGrantedConsent(
        tenantId: string,
        userId: string,
    ): Promise<Set<ConsentCategory>> {
        // Check cache first
        if (cache) {
            const cached = cache.get(tenantId, userId);
            if (cached) return cached;
        }

        const records = await prisma.userConsent.findMany({
            where: { tenantId, userId, granted: true },
            select: { category: true },
        });

        const granted = new Set<ConsentCategory>(
            records.map((r: { category: string }) => r.category as ConsentCategory),
        );

        // Always include essential
        granted.add('essential');

        if (cache) {
            cache.set(tenantId, userId, granted);
        }

        return granted;
    }

    /**
     * Fastify preHandler hook
     */
    async function consentPreHandler(
        request: FastifyRequest,
        reply: FastifyReply,
    ): Promise<void> {
        const user = (request as any).user;
        if (!user?.id || !user?.tenantId) {
            // No authenticated user → skip consent check (auth middleware will handle)
            return;
        }

        // Exempt roles
        if (exemptRoles.includes(user.role)) {
            return;
        }

        const required = findRequiredCategories(request.method, request.url);
        if (required.length === 0) {
            // No consent required for this route
            return;
        }

        const granted = await fetchGrantedConsent(user.tenantId, user.id);

        const missing = required.filter((cat) => !granted.has(cat));
        if (missing.length > 0) {
            reply.status(451).send({
                error: 'Consent Required',
                message: `This action requires consent for: ${missing.join(', ')}`,
                missingConsent: missing,
                consentUrl: '/settings/privacy',
            });
            return;
        }
    }

    return {
        preHandler: consentPreHandler,
        cache,
        findRequiredCategories,
        fetchGrantedConsent,
    };
}
