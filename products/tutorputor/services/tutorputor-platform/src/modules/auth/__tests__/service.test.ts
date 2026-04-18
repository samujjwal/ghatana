import { describe, it, expect, vi, beforeEach } from "vitest";
import type { TenantId } from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";

vi.mock("jose", () => ({
    SignJWT: class {
        setProtectedHeader = vi.fn().mockReturnThis();
        setIssuedAt = vi.fn().mockReturnThis();
        setExpirationTime = vi.fn().mockReturnThis();
        sign = vi.fn().mockResolvedValue("signed-jwt");
    },
    jwtVerify: vi.fn().mockResolvedValue({ payload: { sub: "user-1", tenantId: "tenant-1" } }),
    importJWK: vi.fn().mockResolvedValue({}),
    createRemoteJWKSet: vi.fn().mockReturnValue(vi.fn()),
    decodeJwt: vi.fn().mockReturnValue({ sub: "user-1", email: "alice@example.com" }),
}));

vi.mock("openid-client", () => ({
    Issuer: {
        discover: vi.fn().mockResolvedValue({
            Client: class {
                authorizationUrl = vi.fn().mockReturnValue("https://idp.example.com/auth");
                exchangeCode = vi.fn().mockResolvedValue({ id_token: "tok" });
                verifyIdToken = vi.fn().mockResolvedValue({ sub: "user-1", email: "alice@example.com" });
            },
        }),
    },
}));

vi.mock("../oidc/OidcClient.js", () => ({
    OidcClient: class {
        constructor(_config: unknown) {}

        async initialize(): Promise<void> {
            return;
        }

        async generateAuthUrl(state: string, _nonce: string): Promise<{
            url: string;
            codeVerifier: string;
        }> {
            return {
                url: `https://idp.example.com/auth?state=${state}`,
                codeVerifier: "code-verifier",
            };
        }

        async exchangeCode(_code: string, _codeVerifier: string): Promise<{ id_token: string }> {
            return { id_token: "id-token" };
        }

        async verifyIdToken(_idToken: string, _nonce: string): Promise<Record<string, unknown>> {
            return {
                sub: "oidc-user-1",
                email: "alice@example.com",
                name: "Alice",
                role: "learner",
            };
        }
    },
}));

import { createSsoService } from "../service.js";

// ---------------------------------------------------------------------------
// SSO Service factory tests
// ---------------------------------------------------------------------------
describe("createSsoService", () => {
    const tenantId = "tenant-1" as TenantId;
    let redis: {
        get: ReturnType<typeof vi.fn>;
        set: ReturnType<typeof vi.fn>;
        expire: ReturnType<typeof vi.fn>;
        del: ReturnType<typeof vi.fn>;
    };
    let prisma: {
        identityProvider: {
            findFirst: ReturnType<typeof vi.fn>;
            create: ReturnType<typeof vi.fn>;
            findUnique: ReturnType<typeof vi.fn>;
            findMany: ReturnType<typeof vi.fn>;
            update: ReturnType<typeof vi.fn>;
            delete: ReturnType<typeof vi.fn>;
        };
        tenant: {
            findFirst: ReturnType<typeof vi.fn>;
        };
        user: {
            findFirst: ReturnType<typeof vi.fn>;
            create: ReturnType<typeof vi.fn>;
        };
    };
    let onUserAuthenticated: (args: {
        tenantId: TenantId;
        userId: string;
        isNewUser: boolean;
    }) => Promise<void>;
    let ssoService: ReturnType<typeof createSsoService>;

    beforeEach(() => {
        const redisState = new Map<string, string>();
        redis = {
            get: vi.fn((key: string) => Promise.resolve(redisState.get(key) ?? null)),
            set: vi.fn((key: string, value: string) => {
                redisState.set(key, value);
                return Promise.resolve("OK");
            }),
            expire: vi.fn(() => Promise.resolve(1)),
            del: vi.fn((key: string) => {
                redisState.delete(key);
                return Promise.resolve(1);
            }),
        };
        prisma = {
            identityProvider: {
                findFirst: vi.fn().mockResolvedValue(null),
                create: vi.fn(),
                findUnique: vi.fn().mockResolvedValue(null),
                findMany: vi.fn().mockResolvedValue([]),
                update: vi.fn(),
                delete: vi.fn(),
            },
            tenant: {
                findFirst: vi.fn().mockResolvedValue(null),
            },
            user: {
                findFirst: vi.fn().mockResolvedValue(null),
                create: vi.fn(),
            },
        };
        onUserAuthenticated = vi.fn().mockResolvedValue(undefined) as unknown as (
            args: {
                tenantId: TenantId;
                userId: string;
                isNewUser: boolean;
            }
        ) => Promise<void>;
        ssoService = createSsoService({
            prisma: prisma as unknown as TutorPrismaClient,
            baseUrl: "https://app.example.com",
            redis,
            generateAccessToken: vi.fn().mockReturnValue("access-token"),
            generateRefreshToken: vi.fn().mockReturnValue("refresh-token"),
            onUserAuthenticated,
        });
    });

    const makeDbProvider = (overrides: any = {}) => ({
        id: "prov-1",
        tenantId: "tenant-1",
        displayName: "Test IDP",
        type: "oidc",
        enabled: true,
        discoveryEndpoint: "https://idp.example.com/.well-known/openid-configuration",
        clientId: "client-id",
        clientSecret: "secret",
        allowedDomains: JSON.stringify(["example.com"]),
        attributeMapping: null,
        roleMapping: JSON.stringify({ learner: "student" }),
        createdAt: new Date("2024-01-01"),
        updatedAt: new Date("2024-01-01"),
        ...overrides,
    });

    describe("listProviders", () => {
        it("returns mapped providers for tenant", async () => {
            prisma.identityProvider.findMany.mockResolvedValue([makeDbProvider()]);
            const result = await ssoService.listProviders({ tenantId });
            expect(Array.isArray(result)).toBe(true);
            expect(result).toHaveLength(1);
            expect(result[0].id).toBe("prov-1");
            expect(result[0].tenantId).toBe("tenant-1");
        });

        it("returns empty array when no providers configured", async () => {
            prisma.identityProvider.findMany.mockResolvedValue([]);
            const result = await ssoService.listProviders({ tenantId: "tenant-empty" as TenantId });
            expect(result).toHaveLength(0);
        });
    });

    describe("getProvider", () => {
        it("returns null when provider not found", async () => {
            prisma.identityProvider.findUnique.mockResolvedValue(null);
            const result = await ssoService.getProvider({ tenantId, providerId: "missing" });
            expect(result).toBeNull();
        });
    });

    describe("initiateLogin", () => {
        it("throws when provider not found", async () => {
            prisma.identityProvider.findFirst.mockResolvedValue(null);
            await expect(
                ssoService.initiateLogin({ tenantId, providerId: "missing" })
            ).rejects.toThrow();
        });

        it("persists transient OIDC state in Redis with a TTL", async () => {
            prisma.identityProvider.findFirst.mockResolvedValue(makeDbProvider());

            const login = await ssoService.initiateLogin({
                tenantId,
                providerId: "prov-1",
                redirectUri: "https://app.example.com/admin",
            });

            expect(login.state).toBeTruthy();
            expect(redis.set).toHaveBeenCalledTimes(1);
            expect(redis.expire).toHaveBeenCalledWith(
                `sso:oidc:state:${login.state}`,
                600,
            );
        });
    });

    describe("handleCallback", () => {
        it("throws when state is not found (invalid state)", async () => {
            await expect(
                ssoService.handleCallback({
                    tenantId,
                    providerId: "prov-1",
                    code: "code-123",
                    state: "invalid-state",
                })
            ).rejects.toThrow();
        });

        it("initializes learner profile hook for newly provisioned users", async () => {
            const provider = makeDbProvider();
            prisma.identityProvider.findFirst.mockResolvedValue(provider);
            prisma.identityProvider.findUnique.mockResolvedValue(provider);
            prisma.user.findFirst.mockResolvedValue(null);
            prisma.user.create.mockResolvedValue({
                id: "user-new-1",
                tenantId: "tenant-1",
                email: "alice@example.com",
                displayName: "Alice",
                role: "student",
            });

            const login = await ssoService.initiateLogin({
                tenantId,
                providerId: "prov-1",
            });

            await ssoService.handleCallback({
                tenantId,
                providerId: "prov-1",
                code: "code-123",
                state: login.state,
            });

            expect(redis.del).toHaveBeenCalledWith(`sso:oidc:state:${login.state}`);
            expect(onUserAuthenticated).toHaveBeenCalledWith({
                tenantId,
                userId: "user-new-1",
                isNewUser: true,
            });
        });

        it("does not create user and marks callback as existing user", async () => {
            const provider = makeDbProvider();
            prisma.identityProvider.findFirst.mockResolvedValue(provider);
            prisma.identityProvider.findUnique.mockResolvedValue(provider);
            prisma.user.findFirst.mockResolvedValue({
                id: "user-existing-1",
                tenantId: "tenant-1",
                email: "alice@example.com",
                displayName: "Alice",
                role: "student",
            });

            const login = await ssoService.initiateLogin({
                tenantId,
                providerId: "prov-1",
            });

            await ssoService.handleCallback({
                tenantId,
                providerId: "prov-1",
                code: "code-123",
                state: login.state,
            });

            expect(prisma.user.create).not.toHaveBeenCalled();
            expect(onUserAuthenticated).toHaveBeenCalledWith({
                tenantId,
                userId: "user-existing-1",
                isNewUser: false,
            });
        });
    });
});
