import type { LTIService } from "@tutorputor/contracts/v1/services";
import type {
  LTIDeepLinkingContent,
  LTILaunchPayload,
  LTIValidationResult,
} from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import * as jose from "jose";

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
  prisma: TutorPrismaClient,
): HealthAwareLTIService {
  return {
    async validateLaunch({ token, nonce }): Promise<LTIValidationResult> {
      try {
        // Check for nonce reuse (replay attack prevention)
        if (usedNonces.has(nonce)) {
          return { valid: false, error: "Nonce has already been used" };
        }

        // Decode header to get issuer for JWKS lookup (without verification)
        const decoded = jose.decodeJwt(token);
        const issuer = decoded.iss;
        if (!issuer) {
          return { valid: false, error: "Missing issuer claim" };
        }

        // Look up platform registration to get JWKS URL
        const platform = await prisma.lTIPlatform.findFirst({
          where: { issuer },
        });

        if (!platform) {
          return { valid: false, error: "Platform not registered" };
        }

        // Verify JWT signature using platform's JWKS endpoint
        const jwks = jose.createRemoteJWKSet(new URL(platform.jwksUrl));
        const { payload: verified } = await jose.jwtVerify(token, jwks, {
          issuer: platform.issuer,
          audience: platform.clientId,
        });

        const context = verified[
          "https://purl.imsglobal.org/spec/lti/claim/context"
        ] as LTILaunchPayload["context"] | undefined;
        const resourceLink = verified[
          "https://purl.imsglobal.org/spec/lti/claim/resource_link"
        ] as LTILaunchPayload["resourceLink"] | undefined;
        if (!context || !resourceLink) {
          return { valid: false, error: "Missing required LTI launch claims" };
        }

        // Extract LTI payload from verified claims
        const payload: LTILaunchPayload = {
          iss: verified.iss!,
          sub: verified.sub!,
          aud:
            typeof verified.aud === "string" ? verified.aud : verified.aud![0]!,
          exp: verified.exp,
          iat: verified.iat,
          nonce: typeof verified.nonce === "string" ? verified.nonce : "",
          context,
          resourceLink,
          ...(Array.isArray(
            verified["https://purl.imsglobal.org/spec/lti/claim/roles"],
          )
            ? {
                roles: verified[
                  "https://purl.imsglobal.org/spec/lti/claim/roles"
                ] as string[],
              }
            : {}),
        };

        // Validate nonce matches
        if (payload.nonce !== nonce) {
          return { valid: false, error: "Nonce mismatch" };
        }

        // Mark nonce as used
        usedNonces.add(nonce);
        // Clean up old nonces (simplified - in production use TTL in Redis)
        if (usedNonces.size > 10000) {
          usedNonces.clear();
        }

        return { valid: true, payload };
      } catch (error) {
        if (error instanceof jose.errors.JWTExpired) {
          return { valid: false, error: "Token has expired" };
        }
        if (error instanceof jose.errors.JWSSignatureVerificationFailed) {
          return { valid: false, error: "Invalid token signature" };
        }
        return {
          valid: false,
          error: error instanceof Error ? error.message : "Validation failed",
        };
      }
    },

    async getDeepLinkingContent({ tenantId, moduleIds, baseUrl }) {
      // Fetch modules
      const modules = await prisma.module.findMany({
        where: {
          tenantId,
          id: { in: moduleIds },
          status: "PUBLISHED",
        },
      });

      // Generate deep linking content for each module
      const content: LTIDeepLinkingContent[] = modules.map((module) => ({
        type: "ltiResourceLink",
        title: module.title,
        url: `${baseUrl}/lti/launch/${module.id}`,
        custom: {
          module_id: module.id,
          module_slug: module.slug,
          difficulty: module.difficulty,
          domain: module.domain,
        },
      }));

      return content;
    },

    async registerPlatform({
      tenantId,
      platformName,
      issuer,
      clientId,
      jwksUrl,
      authUrl,
      tokenUrl,
    }) {
      // Upsert platform (update if issuer already exists for tenant)
      const platform = await prisma.lTIPlatform.upsert({
        where: {
          tenantId_issuer: { tenantId, issuer },
        },
        update: {
          platformName,
          clientId,
          jwksUrl,
          authUrl,
          tokenUrl,
        },
        create: {
          tenantId,
          platformName,
          issuer,
          clientId,
          jwksUrl,
          authUrl,
          tokenUrl,
        },
      });

      return { platformId: platform.id };
    },

    async checkHealth() {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    },
  };
}

// =============================================================================
// Helper Functions
// =============================================================================
