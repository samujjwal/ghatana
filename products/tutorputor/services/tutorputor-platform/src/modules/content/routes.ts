import type { FastifyInstance } from "fastify";
import type { ContentService } from "@ghatana/tutorputor-contracts/v1/services";
import type { UserId } from "@ghatana/tutorputor-contracts/v1/types";

interface ContentRouteConfigs {
    contentService: ContentService;
}

export async function registerContentRoutes(
    app: FastifyInstance,
    deps: ContentRouteConfigs
) {
    app.get("/v1/modules", async (req, reply) => {
        // Tenant ID from header or user context
        const tenantId = (req.headers["x-tenant-id"] as string) || (req.user as any)?.tenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: "Tenant ID required" });
        }

        const userId = (req.user as any)?.id as UserId | undefined;
        const { domain, cursor, query } = (req.query ?? {}) as {
            domain?: string;
            cursor?: string;
            query?: string;
        };

        const result = await deps.contentService.listModules({
            tenantId,
            domain,
            status: "PUBLISHED",
            cursor,
            limit: 20,
            userId,
            query
        });

        return reply.send(result);
    });

    app.get("/v1/modules/:slug", async (req, reply) => {
        const tenantId = (req.headers["x-tenant-id"] as string) || (req.user as any)?.tenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: "Tenant ID required" });
        }

        const userId = (req.user as any)?.id as UserId | undefined;
        const { slug } = req.params as { slug: string };

        const { module, enrollment } = await deps.contentService.getModuleBySlug(
            tenantId,
            slug,
            userId
        );

        return reply.send({
            module,
            userEnrollment: enrollment ?? null
        });
    });
}
