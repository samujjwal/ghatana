import type { LTIService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
    LTIDeepLinkingContent,
    LTILaunchPayload,
    LTIValidationResult,
    ModuleId,
    TenantId
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";

export type HealthAwareLTIService = LTIService & {
    checkHealth: () => Promise<boolean>;
};

// Nonce storage for replay attack prevention (in production, use Redis)
const usedNonces = new Set<string>();

/**
 * Creates an LTI Service for LMS integration (Canvas, Blackboard, Google Classroom).
 * Implements LTI 1.3 launch validation and deep linking.
 * 
 * @doc.type class
 * @doc.purpose Integrate with external LMS platforms via LTI 1.3
 * @doc.layer product
 * @doc.pattern Service
 */
export function createLTIService(
    prisma: TutorPrismaClient
): HealthAwareLTIService {
    return {
        async validateLaunch({ token, nonce }): Promise<LTIValidationResult> {
            try {
                // Check for nonce reuse (replay attack prevention)
                if (usedNonces.has(nonce)) {
                    return { valid: false, error: "Nonce has already been used" };
                }

                // Parse JWT token (in production, verify signature with platform's JWKS)
                const payload = parseJWT(token);

                if (!payload) {
                    return { valid: false, error: "Invalid token format" };
                }

                // Validate required claims
                if (!payload.iss || !payload.sub || !payload.aud) {
                    return { valid: false, error: "Missing required claims" };
                }

                // Check token expiration
                const now = Math.floor(Date.now() / 1000);
                if (payload.exp && payload.exp < now) {
                    return { valid: false, error: "Token has expired" };
                }

                // Validate nonce matches
                if (payload.nonce !== nonce) {
                    return { valid: false, error: "Nonce mismatch" };
                }

                // Look up platform registration
                const platform = await prisma.lTIPlatform.findFirst({
                    where: { issuer: payload.iss }
                });

                if (!platform) {
                    return { valid: false, error: "Platform not registered" };
                }

                // Validate audience
                if (payload.aud !== platform.clientId) {
                    return { valid: false, error: "Invalid audience" };
                }

                // Mark nonce as used
                usedNonces.add(nonce);
                // Clean up old nonces (simplified - in production use TTL in Redis)
                if (usedNonces.size > 10000) {
                    usedNonces.clear();
                }

                return { valid: true, payload };
            } catch (error) {
                return {
                    valid: false,
                    error: error instanceof Error ? error.message : "Validation failed"
                };
            }
        },

        async getDeepLinkingContent({ tenantId, moduleIds, baseUrl }) {
            // Fetch modules
            const modules = await prisma.module.findMany({
                where: {
                    tenantId,
                    id: { in: moduleIds },
                    status: "PUBLISHED"
                }
            });

            // Generate deep linking content for each module
            const content: LTIDeepLinkingContent[] = modules.map(module => ({
                type: "ltiResourceLink",
                title: module.title,
                url: `${baseUrl}/lti/launch/${module.id}`,
                custom: {
                    module_id: module.id,
                    module_slug: module.slug,
                    difficulty: module.difficulty,
                    domain: module.domain
                }
            }));

            return content;
        },

        async registerPlatform({ tenantId, platformName, issuer, clientId, jwksUrl, authUrl, tokenUrl }) {
            // Upsert platform (update if issuer already exists for tenant)
            const platform = await prisma.lTIPlatform.upsert({
                where: {
                    tenantId_issuer: { tenantId, issuer }
                },
                update: {
                    platformName,
                    clientId,
                    jwksUrl,
                    authUrl,
                    tokenUrl
                },
                create: {
                    tenantId,
                    platformName,
                    issuer,
                    clientId,
                    jwksUrl,
                    authUrl,
                    tokenUrl
                }
            });

            return { platformId: platform.id };
        },

        async checkHealth() {
            await prisma.$queryRaw`SELECT 1`;
            return true;
        }
    };
}

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Parse a JWT token without verification (for structure validation).
 * In production, use a proper JWT library with signature verification.
 */
function parseJWT(token: string): LTILaunchPayload | null {
    try {
        const parts = token.split(".");
        if (parts.length !== 3) {
            return null;
        }

        const payload = JSON.parse(
            Buffer.from(parts[1], "base64url").toString("utf-8")
        );

        return {
            iss: payload.iss,
            sub: payload.sub,
            aud: payload.aud,
            exp: payload.exp,
            iat: payload.iat,
            nonce: payload.nonce,
            context: payload["https://purl.imsglobal.org/spec/lti/claim/context"],
            resourceLink: payload["https://purl.imsglobal.org/spec/lti/claim/resource_link"],
            roles: payload["https://purl.imsglobal.org/spec/lti/claim/roles"]
        };
    } catch {
        return null;
    }
}
