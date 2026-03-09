/**
 * SSO Service Implementation
 *
 * Implements Enterprise SSO (OIDC/SAML).
 * Migrated from tutorputor-sso/src/service.ts
 * 
 * @doc.type service
 * @doc.purpose Authorization and SSO logic
 * @doc.layer product
 * @doc.pattern Service
 */

import type { SsoService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
    IdentityProviderConfig,
    SsoLoginRequest,
    SsoLoginResult,
    RoleMappingConfig,
    PaginatedResult,
    PaginationArgs,
    TenantId,
    UserId,
    UserRole,
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import { OidcClient, type OidcVerifiedClaims } from "./oidc/OidcClient.js";
import { randomUUID } from "crypto";

// =============================================================================
// Helper Types
// =============================================================================

interface SsoServiceDeps {
    prisma: TutorPrismaClient;
    baseUrl: string; // e.g., https://api.tutorputor.com
    generateAccessToken: (userId: string, tenantId: string) => string;
    generateRefreshToken: (userId: string, tenantId: string) => string;
}

// =============================================================================
// Cache & State (Replacements for Redis in MVP/Migration)
// =============================================================================

const oidcClientCache = new Map<string, OidcClient>();
const stateCache = new Map<
    string,
    {
        providerId: string;
        tenantId: TenantId;
        codeVerifier: string;
        nonce: string;
        redirectUri?: string;
        expiresAt: number;
    }
>();

// =============================================================================
// Implementation
// =============================================================================

export function createSsoService(deps: SsoServiceDeps): SsoService {
    const { prisma, baseUrl, generateAccessToken, generateRefreshToken } = deps;

    async function getOidcClient(provider: any): Promise<OidcClient> {
        if (oidcClientCache.has(provider.id)) {
            return oidcClientCache.get(provider.id)!;
        }

        // Parse config
        const config = typeof provider.config === "string"
            ? JSON.parse(provider.config)
            : provider.config;

        const client = new OidcClient({
            discoveryEndpoint: config.discoveryEndpoint,
            clientId: config.clientId,
            clientSecret: config.clientSecret,
            redirectUri: `${baseUrl}/api/auth/sso/callback/${provider.id}`,
            scopes: config.scopes,
            expectedAudience: config.clientId
        });

        oidcClientCache.set(provider.id, client);
        return client;
    }

    // --- Mappers ---

    function mapDbProviderToConfig(dbProvider: any): IdentityProviderConfig {
        return {
            id: dbProvider.id,
            tenantId: dbProvider.tenantId,
            displayName: dbProvider.displayName,
            type: dbProvider.type as "oidc" | "saml",
            enabled: dbProvider.enabled,
            // @ts-ignore
            config: typeof dbProvider.config === "string" ? JSON.parse(dbProvider.config) : (dbProvider.config as any),
            // @ts-ignore
            attributeMapping: typeof dbProvider.attributeMapping === "string" ? JSON.parse(dbProvider.attributeMapping) : (dbProvider.attributeMapping as any),
            // @ts-ignore
            roleMapping: typeof dbProvider.roleMapping === "string" ? JSON.parse(dbProvider.roleMapping) : (dbProvider.roleMapping as any),
            createdAt: dbProvider.createdAt.toISOString(),
            updatedAt: dbProvider.updatedAt.toISOString(),
        } as unknown as IdentityProviderConfig;
    }

    return {
        // -------------------------------------------------------------------------
        // Provider Management
        // -------------------------------------------------------------------------

        // @ts-ignore
        async listProviders({ tenantId }) {
            // @ts-ignore - Prisma typing might need adjustment based on generated client
            const providers = await prisma.identityProvider.findMany({
                where: { tenantId },
                orderBy: { displayName: "asc" }
            });
            return providers.map(mapDbProviderToConfig);
        },

        async getLoginProviders({ tenantSlug }) {
            const tenant = await prisma.tenant.findUnique({ where: { slug: tenantSlug } });
            if (!tenant) return [];

            // @ts-ignore
            const providers = await prisma.identityProvider.findMany({
                where: { tenantId: tenant.id, enabled: true },
                select: { id: true, displayName: true, type: true, iconUrl: true }
            });
            return providers as any[];
        },

        async createProvider({ tenantId, config }) {
            // @ts-ignore
            const created = await prisma.identityProvider.create({
                data: {
                    tenantId,
                    displayName: config.displayName,
                    type: config.type,
                    enabled: config.enabled,
                    config: JSON.stringify((config as any).config),
                    attributeMapping: JSON.stringify((config as any).attributeMapping || {}),
                    roleMapping: JSON.stringify((config as any).roleMapping || []),
                    iconUrl: (config as any).iconUrl
                }
            });
            return mapDbProviderToConfig(created);
        },

        async updateProvider({ tenantId, providerId, updates }: any) {
            // @ts-ignore
            const current = await prisma.identityProvider.findFirst({ where: { id: providerId, tenantId } });
            if (!current) throw new Error("Provider not found");

            const data: any = {};
            if (updates.displayName) data.displayName = updates.displayName;
            if (updates.enabled !== undefined) data.enabled = updates.enabled;
            if (updates.config) data.config = JSON.stringify(updates.config);
            if (updates.attributeMapping) data.attributeMapping = JSON.stringify(updates.attributeMapping);
            if (updates.roleMapping) data.roleMapping = JSON.stringify(updates.roleMapping);

            // @ts-ignore
            const updated = await prisma.identityProvider.update({
                where: { id: providerId },
                data
            });
            // Clear cache
            oidcClientCache.delete(providerId);
            return mapDbProviderToConfig(updated);
        },

        async deleteProvider({ tenantId, providerId }) {
            // @ts-ignore
            const current = await prisma.identityProvider.findFirst({ where: { id: providerId, tenantId } });
            if (!current) throw new Error("Provider not found");

            // @ts-ignore
            await prisma.identityProvider.delete({ where: { id: providerId } });
            oidcClientCache.delete(providerId);
        },

        async testProvider({ tenantId, providerId }) {
            // Placeholder check
            // @ts-ignore
            const provider = await prisma.identityProvider.findFirst({ where: { id: providerId, tenantId } });
            if (!provider) return { success: false, message: "Provider not found" };

            if (provider.type === "oidc") {
                try {
                    const client = await getOidcClient(provider);
                    await client.initialize();
                    return { success: true, message: "Discovery successful" };
                } catch (e: any) {
                    return { success: false, message: e.message };
                }
            }
            return { success: true, message: "SAML test not implemented" };
        },

        // -------------------------------------------------------------------------
        // Login Flow
        // -------------------------------------------------------------------------

        async initiateLogin(args) {
            const { tenantId, providerId, redirectUri } = args;
            // @ts-ignore
            const provider = await prisma.identityProvider.findFirst({
                where: { id: providerId, tenantId, enabled: true }
            });

            if (!provider) throw new Error("Provider not found or disabled");

            if (provider.type === "oidc") {
                const client = await getOidcClient(provider);
                const state = randomUUID();
                const nonce = randomUUID();
                const { url, codeVerifier } = await client.generateAuthUrl(state, nonce);

                // Store state
                stateCache.set(state, {
                    providerId,
                    tenantId,
                    codeVerifier: codeVerifier || "",
                    nonce,
                    redirectUri,
                    expiresAt: Date.now() + 600000 // 10 mins
                });

                return { redirectUrl: url, state };
            }

            throw new Error("SAML not fully implemented in this version");
        },

        async handleCallback(args) {
            const { providerId, code, state } = args;

            // 1. Validate State
            const storedState = stateCache.get(state);
            if (!storedState) throw new Error("Invalid or expired state");
            stateCache.delete(state);

            if (storedState.providerId !== providerId) throw new Error("Provider mismatch");

            // 2. Get Provider
            // @ts-ignore
            const provider = await prisma.identityProvider.findUnique({ where: { id: providerId } });
            if (!provider) throw new Error("Provider not found");

            let externalUser: { id: string; email: string; name?: string; role?: string } | null = null;
            let oidcClaims: Record<string, any> | null = null;

            // 3. Exchange Code (OIDC)
            if (provider.type === "oidc") {
                const client = await getOidcClient(provider);
                const tokens = await client.exchangeCode(code, storedState.codeVerifier);
                oidcClaims = await client.verifyIdToken(tokens.id_token, storedState.nonce) as Record<string, any>;

                externalUser = {
                    id: oidcClaims.sub,
                    email: oidcClaims.email || "",
                    name: oidcClaims.name
                };
            } else {
                throw new Error("SAML callback not supported");
            }

            if (!externalUser || !externalUser.email) {
                throw new Error("Could not identify user from IdP");
            }

            // 4. Link or Create User
            // Find user by email within tenant
            // @ts-ignore
            let user = await prisma.user.findFirst({
                where: { tenantId: storedState.tenantId, email: externalUser.email }
            });

            if (!user) {
                // Derive role from OIDC claims using configurable role mapping.
                // Priority: role claim from IdP config > default from provider config > fallback "student"
                const providerConfig = typeof provider.config === "string"
                    ? JSON.parse(provider.config)
                    : provider.config;
                const roleMapping: Record<string, string> = providerConfig?.roleMapping || {};
                const roleClaimPath: string = providerConfig?.roleClaimPath || "role";

                // Extract role from IdP claims (e.g., oidcClaims.role, oidcClaims.groups[0])
                const rawRoleClaim = oidcClaims?.[roleClaimPath];
                const claimedRoles: string[] = Array.isArray(rawRoleClaim)
                    ? rawRoleClaim
                    : rawRoleClaim ? [String(rawRoleClaim)] : [];

                // Map IdP role values to TutorPutor roles
                let mappedRole: string = providerConfig?.defaultRole || "student";
                for (const claimed of claimedRoles) {
                    if (roleMapping[claimed]) {
                        mappedRole = roleMapping[claimed];
                        break;
                    }
                }

                // @ts-ignore
                user = await prisma.user.create({
                    data: {
                        tenantId: storedState.tenantId,
                        email: externalUser.email,
                        displayName: externalUser.name || externalUser.email.split("@")[0],
                        role: mappedRole,
                        passwordHash: "", // SSO users don't have passwords
                        status: "active"
                    }
                });
            }

            // 5. Generate Session
            const accessToken = generateAccessToken(user.id, user.tenantId);
            const refreshToken = generateRefreshToken(user.id, user.tenantId);

            return {
                success: true,
                accessToken,
                refreshToken,
                user: {
                    id: user.id as UserId,
                    email: user.email,
                    displayName: user.displayName,
                    role: (user as any).role as UserRole,
                    tenantId: user.tenantId as TenantId
                },
                redirectUri: storedState.redirectUri
            };
        },

        // async handleSamlAcs(args: any) {
        //     throw new Error("Not implemented");
        // },

        // async logout(args: any) {
        //      return { success: true };
        // },

        // -------------------------------------------------------------------------
        // User Linking (Stubbed for now)
        // -------------------------------------------------------------------------
        async listLinkedUsers() { return { items: [], totalCount: 0, hasMore: false }; },
        async unlinkUser() { return; },

        // Stubs for missing interface methods
        // @ts-ignore
        async getProvider(ignored: any) { return null; },
        // @ts-ignore
        async handleSamlCallback(ignored: any) { return {} as any; },
        // @ts-ignore
        async getUserLinks(ignored: any) { return []; },
        // @ts-ignore
        async syncUserFromIdp(ignored: any) { return {} as any; },
        // @ts-ignore
        async getLoginProviders(ignored: any) { return []; },
        // @ts-ignore
        async handleOidcCallback(ignored: any) { return {} as any; },
        // @ts-ignore
        async validateToken(ignored: any) { return true; },
        // @ts-ignore
        async getLogoutUrl(ignored: any) { return ""; },
    };
}
