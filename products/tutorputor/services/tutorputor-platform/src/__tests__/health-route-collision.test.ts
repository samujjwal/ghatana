/**
 * Health Endpoint Route Collision Tests
 *
 * @doc.type tests
 * @doc.purpose Verify that /health routes registered by multiple modules do not
 *              collide with each other when each module is mounted under a
 *              different URL prefix.
 * @doc.layer product
 * @doc.pattern Test Suite
 *
 * Each module in the TutorPutor platform registers a `GET /health` route
 * *within its own Fastify plugin scope*. When that plugin is registered with a
 * prefix (e.g. `prefix: '/api/auth'`) the effective path becomes
 * `/api/auth/health`.  Without proper scoping, duplicate registrations under
 * the same absolute path trigger a `FST_ERR_DUPLICATE_ROUTES` error.
 *
 * These tests confirm:
 *  1. Each module's health route is reachable at its expected scoped path.
 *  2. No two modules register the same absolute path.
 *  3. The monitoring-level root `/health` route coexists with all module routes.
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import Fastify from "fastify";
import type { FastifyInstance, FastifyPluginAsync } from "fastify";

// ---------------------------------------------------------------------------
// Lightweight stub plugins – mirrors the health route each real module exposes
// ---------------------------------------------------------------------------

function makeHealthPlugin(moduleName: string): FastifyPluginAsync {
    return async function plugin(fastify) {
        fastify.get("/health", async () => ({
            status: "healthy",
            module: moduleName,
        }));
    };
}

// Module registry: prefix → expected module name in the response
const MODULE_CONFIGS: Array<{ prefix: string; moduleName: string }> = [
    { prefix: "/api/auth",           moduleName: "auth" },
    { prefix: "/api/users",          moduleName: "users" },
    { prefix: "/api/learning",       moduleName: "learning" },
    { prefix: "/api/engagement",     moduleName: "engagement" },
    { prefix: "/api/collaboration",  moduleName: "collaboration" },
    { prefix: "/api/tenant",         moduleName: "tenant" },
    { prefix: "/api/integration",    moduleName: "integration" },
    { prefix: "/api/auto-revision",  moduleName: "auto-revision" },
    { prefix: "/api/content-needs",  moduleName: "content-needs" },
    { prefix: "/api/v1/ai",          moduleName: "ai" },
];

// ---------------------------------------------------------------------------
// Test app setup
// ---------------------------------------------------------------------------

let app: FastifyInstance;

beforeAll(async () => {
    app = Fastify({ logger: false });

    // ---- Root-level health route (registered by monitoring module) ----
    app.get("/health", async () => ({ status: "ok", module: "monitoring" }));

    // ---- Per-module /health routes (each scoped to its prefix) ----
    for (const { prefix, moduleName } of MODULE_CONFIGS) {
        // Use fp (fastify-plugin?) pattern with inline async to test Fastify's
        // scope isolation.  The { prefix } option means the plugin's routes are
        // effectively mounted at `<prefix>/health`.
        await app.register(makeHealthPlugin(moduleName), { prefix });
    }

    await app.ready();
});

afterAll(async () => {
    await app.close();
});

// ---------------------------------------------------------------------------
// Test cases
// ---------------------------------------------------------------------------

describe("Health Endpoint Route Collision", () => {
    describe("Root /health (monitoring module)", () => {
        it("GET /health → 200 with module=monitoring", async () => {
            const res = await app.inject({ method: "GET", url: "/health" });

            expect(res.statusCode).toBe(200);
            const body = res.json<{ status: string; module: string }>();
            expect(body.status).toBe("ok");
            expect(body.module).toBe("monitoring");
        });
    });

    describe("Module-scoped /health routes are reachable", () => {
        for (const { prefix, moduleName } of MODULE_CONFIGS) {
            const url = `${prefix}/health`;

            it(`GET ${url} → 200 with module=${moduleName}`, async () => {
                const res = await app.inject({ method: "GET", url });

                expect(res.statusCode).toBe(200);
                const body = res.json<{ status: string; module: string }>();
                expect(body.status).toBe("healthy");
                expect(body.module).toBe(moduleName);
            });
        }
    });

    describe("No path collisions between modules", () => {
        it("All health endpoint paths are unique", () => {
            const allPaths = [
                "/health",
                ...MODULE_CONFIGS.map(({ prefix }) => `${prefix}/health`),
            ];

            const uniquePaths = new Set(allPaths);
            expect(uniquePaths.size).toBe(allPaths.length);
        });

        it("Module paths do not shadow each other (no prefix is a superset of another)", () => {
            const prefixes = MODULE_CONFIGS.map(({ prefix }) => prefix);

            const collisions: string[] = [];
            for (let i = 0; i < prefixes.length; i++) {
                for (let j = 0; j < prefixes.length; j++) {
                    if (i === j) continue;
                    const a = prefixes[i]!;
                    const b = prefixes[j]!;
                    // A collision is when one prefix starts with another plus '/'
                    // causing a routing ambiguity at a sub-path.
                    if (a.startsWith(`${b}/`) || b.startsWith(`${a}/`)) {
                        // Only flag if the two modules both register /health
                        // at the same final path – the deeper prefix wins, but it
                        // can mask the shallower module's /health.
                        collisions.push(`${a} vs ${b}`);
                    }
                }
            }

            // We expect zero nested-prefix collisions for /health specifically.
            // If any exist, each module involved should use a unique sub-path.
            expect(collisions).toHaveLength(0);
        });
    });

    describe("Non-existent module paths return 404", () => {
        it("GET /api/nonexistent/health → 404", async () => {
            const res = await app.inject({
                method: "GET",
                url: "/api/nonexistent/health",
            });
            expect(res.statusCode).toBe(404);
        });
    });

    describe("Root /health after module registration", () => {
        it("Root /health is not overridden by any module prefix", async () => {
            // Re-check after all modules are registered to ensure no module's
            // plugin replaced the root route.
            const res = await app.inject({ method: "GET", url: "/health" });
            expect(res.statusCode).toBe(200);
            const body = res.json<{ module: string }>();
            expect(body.module).toBe("monitoring");
        });
    });
});

/**
 * Regression guard: attempt to register a duplicate route should throw.
 *
 * This test spins up a *separate* app (not the shared `app`) to avoid
 * contaminating other tests.
 */
describe("Duplicate /health registration guard", () => {
    it("registering the same absolute health path twice throws FST_ERR_DUPLICATE_ROUTES", async () => {
        const dupeApp = Fastify({ logger: false });

        dupeApp.get("/api/auth/health", async () => ({ status: "original" }));

        // In Fastify 5, duplicate route registration throws synchronously at
        // call-site rather than deferring to app.ready().
        expect(() => {
            dupeApp.get("/api/auth/health", async () => ({ status: "duplicate" }));
        }).toThrow();

        // Cleanup
        await dupeApp.close().catch(() => undefined);
    });
});
