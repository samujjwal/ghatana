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

import type { SsoService } from "@tutorputor/contracts/v1/services";
import type {
  IdentityProviderConfig,
  TenantId,
  UserId,
  UserRole,
} from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { OidcClient } from "./oidc/OidcClient.js";
import { randomUUID } from "crypto";

// =============================================================================
// Helper Types
// =============================================================================

interface SsoServiceDeps {
  prisma: TutorPrismaClient;
  baseUrl: string; // e.g., https://api.tutorputor.com
  generateAccessToken: (userId: string, tenantId: string) => string;
  generateRefreshToken: (userId: string, tenantId: string) => string;
  onUserAuthenticated?: (args: {
    tenantId: TenantId;
    userId: string;
    isNewUser: boolean;
  }) => Promise<void>;
}

interface ProviderRuntimeConfig {
  discoveryEndpoint: string;
  clientId: string;
  allowedDomains: string[];
  roleMapping: Record<string, string>;
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
  const {
    prisma,
    baseUrl,
    generateAccessToken,
    generateRefreshToken,
    onUserAuthenticated,
  } = deps;

  function getProviderRuntimeConfig(
    provider: Record<string, unknown>,
  ): ProviderRuntimeConfig {
    const parseStringArray = (value: unknown): string[] => {
      if (typeof value === "string") {
        try {
          const parsed = JSON.parse(value) as unknown;
          return Array.isArray(parsed)
            ? parsed.filter((entry): entry is string => typeof entry === "string")
            : [];
        } catch {
          return [];
        }
      }

      return Array.isArray(value)
        ? value.filter((entry): entry is string => typeof entry === "string")
        : [];
    };

    const parseRoleMapping = (value: unknown): Record<string, string> => {
      const parsed =
        typeof value === "string"
          ? (() => {
              try {
                return JSON.parse(value) as unknown;
              } catch {
                return {};
              }
            })()
          : value;

      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return {};
      }

      return Object.fromEntries(
        Object.entries(parsed).filter(
          (entry): entry is [string, string] => typeof entry[1] === "string",
        ),
      );
    };

    return {
      discoveryEndpoint: String(provider.discoveryEndpoint ?? ""),
      clientId: String(provider.clientId ?? ""),
      allowedDomains: parseStringArray(provider.allowedDomains),
      roleMapping: parseRoleMapping(provider.roleMapping),
    };
  }

  async function getOidcClient(provider: Record<string, unknown>): Promise<OidcClient> {
    const providerId = String(provider.id ?? "");
    if (oidcClientCache.has(providerId)) {
      return oidcClientCache.get(providerId)!;
    }

    const config = getProviderRuntimeConfig(provider);

    const client = new OidcClient({
      discoveryEndpoint: config.discoveryEndpoint,
      clientId: config.clientId,
      clientSecret: "",
      redirectUri: `${baseUrl}/api/auth/sso/callback/${providerId}`,
      scopes: ["openid", "profile", "email"],
      expectedAudience: config.clientId,
    });

    oidcClientCache.set(providerId, client);
    return client;
  }

  // --- Mappers ---

  function mapDbProviderToConfig(dbProvider: Record<string, unknown>): IdentityProviderConfig {
    const config = getProviderRuntimeConfig(dbProvider);

    return {
      id: String(dbProvider.id ?? ""),
      tenantId: String(dbProvider.tenantId ?? "") as TenantId,
      displayName: String(dbProvider.displayName ?? ""),
      type: dbProvider.type as "oidc" | "saml",
      enabled: Boolean(dbProvider.enabled),
      config,
      attributeMapping: {},
      roleMapping: config.roleMapping,
      createdAt: (dbProvider.createdAt as Date).toISOString(),
      updatedAt: (dbProvider.updatedAt as Date).toISOString(),
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
        orderBy: { displayName: "asc" },
      });
      return providers.map(mapDbProviderToConfig);
    },

    async getLoginProviders({ tenantSlug }) {
      const tenant = await prisma.tenant.findFirst({
        where: { subdomain: tenantSlug },
      });
      if (!tenant) return [];

      // @ts-ignore
      const providers = await prisma.identityProvider.findMany({
        where: { tenantId: tenant.id, enabled: true },
        select: { id: true, displayName: true, type: true },
      });
      return providers.map((provider) => ({
        id: String(provider.id),
        displayName: String(provider.displayName),
        type: provider.type as "oidc" | "saml",
      }));
    },

    async createProvider({ tenantId, config }) {
      const oidcConfig = config.oidcConfig ?? {};

      // @ts-ignore
      const created = await prisma.identityProvider.create({
        data: {
          tenantId,
          displayName: config.displayName,
          type: config.type,
          enabled: config.enabled,
          discoveryEndpoint: config.discoveryEndpoint,
          clientId: config.clientId,
          clientSecret: null,
          allowedDomains: JSON.stringify(config.allowedDomains ?? []),
          roleMapping: JSON.stringify(config.roleMapping ?? {}),
        },
      });
      return mapDbProviderToConfig(created);
    },

    async updateProvider({
      tenantId,
      providerId,
      patch,
    }: {
      tenantId: TenantId;
      providerId: string;
      patch: Partial<
        Omit<IdentityProviderConfig, "id" | "tenantId" | "createdAt">
      >;
    }) {
      // @ts-ignore
      const current = await prisma.identityProvider.findFirst({
        where: { id: providerId, tenantId },
      });
      if (!current) throw new Error("Provider not found");

      const data: Record<string, unknown> = {};
      if (patch.displayName) data.displayName = patch.displayName;
      if (patch.enabled !== undefined) data.enabled = patch.enabled;
      if (patch.discoveryEndpoint) {
        data.discoveryEndpoint = patch.discoveryEndpoint;
      }
      if (patch.clientId) {
        data.clientId = patch.clientId;
      }
      if (patch.allowedDomains) {
        data.allowedDomains = JSON.stringify(patch.allowedDomains);
      }
      if (patch.roleMapping) {
        data.roleMapping = JSON.stringify(patch.roleMapping);
      }

      // @ts-ignore
      const updated = await prisma.identityProvider.update({
        where: { id: providerId },
        data,
      });
      // Clear cache
      oidcClientCache.delete(providerId);
      return mapDbProviderToConfig(updated);
    },

    async deleteProvider({ tenantId, providerId }) {
      // @ts-ignore
      const current = await prisma.identityProvider.findFirst({
        where: { id: providerId, tenantId },
      });
      if (!current) throw new Error("Provider not found");

      // @ts-ignore
      await prisma.identityProvider.delete({ where: { id: providerId } });
      oidcClientCache.delete(providerId);
    },

    async testProvider({ tenantId, providerId }) {
      // Placeholder check
      // @ts-ignore
      const provider = await prisma.identityProvider.findFirst({
        where: { id: providerId, tenantId },
      });
      if (!provider) return { success: false, message: "Provider not found" };

      if (provider.type === "oidc") {
        try {
          const client = await getOidcClient(provider);
          await client.initialize();
          return { success: true, message: "Discovery successful" };
        } catch (e: unknown) {
          return { success: false, message: e instanceof Error ? e.message : String(e) };
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
        where: { id: providerId, tenantId, enabled: true },
      });

      if (!provider) throw new Error("Provider not found or disabled");

      if (provider.type === "oidc") {
        const client = await getOidcClient(provider);
        const state = randomUUID();
        const nonce = randomUUID();
        const { url, codeVerifier } = await client.generateAuthUrl(
          state,
          nonce,
        );

        // Store state
        const stateEntry: {
          providerId: string;
          tenantId: TenantId;
          codeVerifier: string;
          nonce: string;
          redirectUri?: string;
          expiresAt: number;
        } = {
          providerId,
          tenantId,
          codeVerifier: codeVerifier || "",
          nonce,
          expiresAt: Date.now() + 600000, // 10 mins
        };
        if (redirectUri) {
          stateEntry.redirectUri = redirectUri;
        }
        stateCache.set(state, stateEntry);

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

      if (storedState.providerId !== providerId)
        throw new Error("Provider mismatch");

      // 2. Get Provider
      // @ts-ignore
      const provider = await prisma.identityProvider.findUnique({
        where: { id: providerId },
      });
      if (!provider) throw new Error("Provider not found");

      let externalUser: {
        id: string;
        email: string;
        name?: string;
        role?: string;
      } | null = null;
      let oidcClaims: Record<string, any> | null = null;

      // 3. Exchange Code (OIDC)
      if (provider.type === "oidc") {
        const client = await getOidcClient(provider);
        const tokens = await client.exchangeCode(
          code,
          storedState.codeVerifier,
        );
        oidcClaims = (await client.verifyIdToken(
          tokens.id_token,
          storedState.nonce,
        )) as Record<string, any>;

        externalUser = {
          id: oidcClaims.sub,
          email: oidcClaims.email || "",
          name: oidcClaims.name,
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
        where: { tenantId: storedState.tenantId, email: externalUser.email },
      });

      let isNewUser = false;
      if (!user) {
        // Derive role from OIDC claims using configurable role mapping.
        // Priority: role claim from IdP config > default from provider config > fallback "student"
        const providerConfig = getProviderRuntimeConfig(provider);
        const roleMapping: Record<string, string> =
          providerConfig?.roleMapping || {};
        const roleClaimPath = "role";

        // Extract role from IdP claims (e.g., oidcClaims.role, oidcClaims.groups[0])
        const rawRoleClaim = oidcClaims?.[roleClaimPath];
        const claimedRoles: string[] = Array.isArray(rawRoleClaim)
          ? rawRoleClaim
          : rawRoleClaim
            ? [String(rawRoleClaim)]
            : [];

        // Map IdP role values to TutorPutor roles
        let mappedRole = "student";
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
            displayName:
              externalUser.name ??
              externalUser.email.split("@")[0] ??
              externalUser.email,
            role: mappedRole,
          },
        });
        isNewUser = true;
      }

      if (onUserAuthenticated) {
        await onUserAuthenticated({
          tenantId: storedState.tenantId,
          userId: String(user.id),
          isNewUser,
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
          role: (user as { role?: UserRole }).role as UserRole,
          tenantId: user.tenantId as TenantId,
        },
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
    async listLinkedUsers() {
      return { items: [], totalCount: 0, hasMore: false };
    },
    async unlinkUser() {
      return;
    },

    // Stubs for missing interface methods
    // @ts-ignore
    async getProvider(_ignored: unknown) {
      return null;
    },
    // @ts-ignore
    async handleSamlCallback(_ignored: unknown) {
      return {} as unknown;
    },
    // @ts-ignore
    async getUserLinks(_ignored: unknown) {
      return [];
    },
    // @ts-ignore
    async syncUserFromIdp(_ignored: unknown) {
      return {} as unknown;
    },
    // @ts-ignore
    async handleOidcCallback(_ignored: unknown) {
      return {} as unknown;
    },
    // @ts-ignore
    async validateToken(_ignored: unknown) {
      return true;
    },
    // @ts-ignore
    async getLogoutUrl(_ignored: unknown) {
      return "";
    },
  };
}
