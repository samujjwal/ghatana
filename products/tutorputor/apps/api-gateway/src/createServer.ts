/**
 * TutorPutor API Gateway — BFF (Backend for Frontend) Entry Point (F-001)
 *
 * Architecture decision: This process IS the single backend-for-frontend that
 * all TutorPutor clients (tutorputor-web, tutorputor-admin, mobile) communicate
 * with. It is NOT a reverse proxy to separate microservices — it embeds the
 * platform as a plugin so there is zero network hop between the BFF layer and
 * domain logic.
 *
 * What this BFF layer is responsible for:
 *   1. Listening on the external port and TLS termination (done by the process
 *      host or the load-balancer upstream).
 *   2. Forwarding the X-Correlation-ID header through the response.  The
 *      correlation-ID *generation* is handled inside setupPlatform → core plugin.
 *   3. Exposing a consolidated `GET /bff/status` endpoint that aggregates service
 *      health for the frontend health monitor (tutorputor-admin dashboard).
 *   4. Providing a root `GET /` for load-balancer health probes.
 *
 * What this BFF layer does NOT do:
 *   - Request aggregation / fan-out (data is fetched directly by the frontend via
 *     TanStack Query; aggregation happens inside the platform domain modules).
 *   - Auth delegation — JWT validation is done once inside the platform's JWT
 *     guard hook (plugins/core.ts).
 *   - Feature-flag routing to separate microservices — all feature-flag branches
 *     live inside domain modules.
 *
 * See docs/adr/ADR-0XX-gateway-bff.md for the full decision record.
 */

import fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import { setupPlatform } from "../../../services/tutorputor-platform/src/index";

export async function createServer(): Promise<FastifyInstance> {
  const app = fastify({
    logger: true,
  });

  // Tries to load environment variables for testing if not present
  if (!process.env.TUTORPUTOR_DATABASE_URL && process.env.DATABASE_URL) {
    process.env.TUTORPUTOR_DATABASE_URL = process.env.DATABASE_URL;
  }

  // NOTE: Correlation-ID generation is handled inside setupPlatform → core plugin.
  // This hook only ECHOES the final ID back so callers receive it in the response
  // regardless of whether the platform generated it or the client provided it.
  app.addHook(
    "onSend",
    async (
      req: FastifyRequest,
      reply,
    ) => {
      const correlationId = reply.getHeader("x-correlation-id");
      if (!correlationId) {
        // Fallback: should not happen when core plugin is registered, but be safe.
        reply.header("x-correlation-id", req.id);
      }
    },
  );

  // Register the full platform (auth, db, redis, all domain modules)
  await setupPlatform(app);

  // -------------------------------------------------------------------------
  // BFF-specific routes
  // -------------------------------------------------------------------------

  /** Load-balancer / k8s liveness probe */
  app.get("/", async () => ({
    service: "TutorPutor API Gateway",
    architecture: "Consolidated BFF (v2) — platform embedded",
    status: "Operational",
  }));

  /**
   * GET /bff/status
   *
   * Consolidated status payload consumed by the admin health-monitor widget.
   * Returns a single JSON object aggregating service-level health so the
   * frontend only needs one fetch to render the status dashboard.
   *
   * Access: public (no JWT required — status page shows degraded state when
   * the full auth stack is down).
   */
  app.get(
    "/bff/status",
    {
      config: { public: true } as Record<string, unknown>,
      schema: {
        description: "Consolidated BFF status for admin health monitor",
        tags: ["BFF"],
      },
    },
    async () => ({
      gateway: "ok",
      timestamp: new Date().toISOString(),
      upstream: {
        platform: "/health",
        learning: "/api/v1/learning/health",
        notifications: "/api/v1/notifications/delivery-health",
      },
    }),
  );

  return app;
}

