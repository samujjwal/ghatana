import type { FastifyRequest, FastifyReply } from "fastify";

/**
 * Extract Tenant ID from request headers.
 * Populated by API Gateway or Auth Middleware.
 */
export function getTenantId(req: FastifyRequest): string {
    const tenantId = req.headers["x-tenant-id"];
    if (!tenantId) {
        // In dev, fallback or throw. For now, strict.
        throw new Error("Missing x-tenant-id header");
    }
    return (Array.isArray(tenantId) ? tenantId[0] : tenantId) as string;
}

/**
 * Extract User ID from request headers.
 * Populated by API Gateway or Auth Middleware.
 */
export function getUserId(req: FastifyRequest): string {
    const userId = req.headers["x-user-id"];
    if (!userId) {
        throw new Error("Missing x-user-id header");
    }
    return (Array.isArray(userId) ? userId[0] : userId) as string;
}

/**
 * Enforce role authorization.
 * @param req Fastify Request
 * @param allowedRoles Array of allowed roles
 */
export function requireRole(req: FastifyRequest, allowedRoles: string[]) {
    const userRole = req.headers["x-user-role"];
    const role = Array.isArray(userRole) ? userRole[0] : userRole;

    if (!role || !allowedRoles.includes(role)) {
        throw new Error(`Insufficient permissions. Required one of: ${allowedRoles.join(", ")}`);
    }
}

/**
 * Helper to handle service errors consistently.
 */
export async function respondWithErrors<T>(
    reply: FastifyReply,
    fn: () => Promise<T>,
): Promise<void> {
    try {
        const result = await fn();
        reply.send(result);
    } catch (error) {
        const err = error as any;
        const statusCode = err.statusCode || 500;
        const message = err.message || "Internal Server Error";
        const code = err.code || "INTERNAL_ERROR";

        reply.code(statusCode).send({
            error: message,
            code: code
        });
    }
}
