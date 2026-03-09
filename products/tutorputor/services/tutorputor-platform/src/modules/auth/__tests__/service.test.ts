import { describe, it, expect, vi, beforeEach } from "vitest";

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

import { createSsoService } from "../service.js";

// ---------------------------------------------------------------------------
// SSO Service factory tests
// ---------------------------------------------------------------------------
describe("createSsoService", () => {
    let prisma: any;
    let ssoService: ReturnType<typeof createSsoService>;

    beforeEach(() => {
        prisma = {
            identityProvider: {
                findFirst: vi.fn().mockResolvedValue(null),
                create: vi.fn(),
                findUnique: vi.fn().mockResolvedValue(null),
                findMany: vi.fn().mockResolvedValue([]),
            },
            user: {
                findFirst: vi.fn().mockResolvedValue(null),
                create: vi.fn(),
            },
            oauthState: {
                create: vi.fn(),
                findUnique: vi.fn().mockResolvedValue(null),
                delete: vi.fn(),
            },
        };
        ssoService = createSsoService({
            prisma,
            baseUrl: "https://app.example.com",
            generateAccessToken: vi.fn().mockReturnValue("access-token"),
            generateRefreshToken: vi.fn().mockReturnValue("refresh-token"),
        });
    });

    const makeDbProvider = (overrides: any = {}) => ({
        id: "prov-1",
        tenantId: "tenant-1",
        displayName: "Test IDP",
        type: "oidc",
        enabled: true,
        config: JSON.stringify({
            issuerUrl: "https://idp.example.com",
            clientId: "client-id",
            clientSecret: "secret",
            callbackUrl: "https://app.example.com/callback",
        }),
        attributeMapping: null,
        roleMapping: null,
        createdAt: new Date("2024-01-01"),
        updatedAt: new Date("2024-01-01"),
        ...overrides,
    });

    describe("listProviders", () => {
        it("returns mapped providers for tenant", async () => {
            prisma.identityProvider.findMany.mockResolvedValue([makeDbProvider()]);
            const result = await ssoService.listProviders({ tenantId: "tenant-1" });
            expect(Array.isArray(result)).toBe(true);
            expect(result).toHaveLength(1);
            expect(result[0].id).toBe("prov-1");
            expect(result[0].tenantId).toBe("tenant-1");
        });

        it("returns empty array when no providers configured", async () => {
            prisma.identityProvider.findMany.mockResolvedValue([]);
            const result = await ssoService.listProviders({ tenantId: "tenant-empty" });
            expect(result).toHaveLength(0);
        });
    });

    describe("getProvider", () => {
        it("returns null when provider not found", async () => {
            prisma.identityProvider.findUnique.mockResolvedValue(null);
            const result = await ssoService.getProvider({ tenantId: "tenant-1", providerId: "missing" });
            expect(result).toBeNull();
        });
    });

    describe("initiateLogin", () => {
        it("throws when provider not found", async () => {
            prisma.identityProvider.findUnique.mockResolvedValue(null);
            await expect(
                ssoService.initiateLogin({ tenantId: "tenant-1", providerId: "missing" })
            ).rejects.toThrow();
        });
    });

    describe("handleCallback", () => {
        it("throws when oauthState is not found (invalid state)", async () => {
            prisma.oauthState.findUnique.mockResolvedValue(null);
            await expect(
                ssoService.handleCallback({
                    providerId: "prov-1",
                    code: "code-123",
                    state: "invalid-state",
                })
            ).rejects.toThrow();
        });
    });
});
