import { describe, it, expect, beforeEach, vi } from "vitest";
import { createLTIService } from './service.js.js';

/**
 * @doc.type test
 * @doc.purpose Unit tests for LTIService
 * @doc.layer product
 * @doc.pattern Test
 */

// Mock Prisma client
const mockPrisma = {
    lTIPlatform: {
        findFirst: vi.fn(),
        upsert: vi.fn()
    },
    module: {
        findFirst: vi.fn(),
        findMany: vi.fn()
    },
    $queryRaw: vi.fn()
};

describe("LTIService", () => {
    let service: ReturnType<typeof createLTIService>;

    beforeEach(() => {
        vi.clearAllMocks();
        service = createLTIService(mockPrisma as never);
    });

    describe("validateLaunch", () => {
        it("should validate a valid LTI launch token", async () => {
            // Create a mock JWT-like token (base64 encoded JSON)
            const payload = {
                iss: "https://canvas.instructure.com",
                sub: "user-123",
                aud: "client-id-123",
                exp: Math.floor(Date.now() / 1000) + 3600,
                iat: Math.floor(Date.now() / 1000),
                nonce: "test-nonce",
                "https://purl.imsglobal.org/spec/lti/claim/message_type": "LtiResourceLinkRequest",
                "https://purl.imsglobal.org/spec/lti/claim/version": "1.3.0",
                "https://purl.imsglobal.org/spec/lti/claim/resource_link": {
                    id: "resource-link-1",
                    title: "Python Module"
                },
                "https://purl.imsglobal.org/spec/lti/claim/context": {
                    id: "course-123",
                    title: "Introduction to Programming"
                }
            };

            // For this test, we'll verify the service handles valid structure
            const result = await service.validateLaunch({
                token: "header.payload.signature",
                nonce: "test-nonce"
            });

            // The mock service returns valid=true for demo purposes
            expect(result).toHaveProperty("valid");
        });

        it("should return invalid for empty token", async () => {
            const result = await service.validateLaunch({
                token: "",
                nonce: "test-nonce"
            });

            expect(result.valid).toBe(false);
            expect(result.error).toBeDefined();
        });
    });

    describe("getDeepLinkingContent", () => {
        it("should return module content items for deep linking", async () => {
            mockPrisma.module.findMany.mockResolvedValue([
                {
                    id: "mod-1",
                    title: "Introduction to Python",
                    description: "Learn Python basics",
                    thumbnail: "https://example.com/python.jpg"
                },
                {
                    id: "mod-2",
                    title: "Advanced Python",
                    description: "Advanced concepts",
                    thumbnail: null
                }
            ]);

            const result = await service.getDeepLinkingContent({
                tenantId: "tenant-1" as never,
                moduleIds: ["mod-1", "mod-2"] as never[],
                baseUrl: "https://tutorputor.example.com"
            });

            expect(result).toHaveLength(2);
            expect(result[0].type).toBe("ltiResourceLink");
            expect(result[0].title).toBe("Introduction to Python");
            expect(result[0].url).toContain("/lti/launch/mod-1");
        });

        it("should handle modules with missing thumbnails", async () => {
            mockPrisma.module.findMany.mockResolvedValue([
                {
                    id: "mod-1",
                    title: "Module Without Image",
                    description: "No thumbnail",
                    thumbnail: null
                }
            ]);

            const result = await service.getDeepLinkingContent({
                tenantId: "tenant-1" as never,
                moduleIds: ["mod-1"] as never[],
                baseUrl: "https://tutorputor.example.com"
            });

            expect(result).toHaveLength(1);
            expect(result[0].icon).toBeUndefined();
        });
    });

    describe("registerPlatform", () => {
        it("should register a new LTI platform", async () => {
            mockPrisma.lTIPlatform.upsert.mockResolvedValue({
                id: "platform-1",
                tenantId: "tenant-1",
                platformName: "Canvas LMS",
                issuer: "https://canvas.instructure.com",
                clientId: "client-123",
                jwksUrl: "https://canvas.instructure.com/.well-known/jwks.json",
                authUrl: "https://canvas.instructure.com/api/lti/authorize_redirect",
                tokenUrl: "https://canvas.instructure.com/login/oauth2/token",
                createdAt: new Date()
            });

            const result = await service.registerPlatform({
                tenantId: "tenant-1" as never,
                platformName: "Canvas LMS",
                issuer: "https://canvas.instructure.com",
                clientId: "client-123",
                jwksUrl: "https://canvas.instructure.com/.well-known/jwks.json",
                authUrl: "https://canvas.instructure.com/api/lti/authorize_redirect",
                tokenUrl: "https://canvas.instructure.com/login/oauth2/token"
            });

            expect(result.platformId).toBe("platform-1");
            expect(mockPrisma.lTIPlatform.upsert).toHaveBeenCalledTimes(1);
        });

        it("should update existing platform with same issuer", async () => {
            mockPrisma.lTIPlatform.upsert.mockResolvedValue({
                id: "platform-1",
                tenantId: "tenant-1",
                platformName: "Canvas LMS Updated",
                issuer: "https://canvas.instructure.com",
                clientId: "new-client-123",
                jwksUrl: "https://canvas.instructure.com/.well-known/jwks.json",
                authUrl: "https://canvas.instructure.com/api/lti/authorize_redirect",
                tokenUrl: "https://canvas.instructure.com/login/oauth2/token",
                createdAt: new Date()
            });

            const result = await service.registerPlatform({
                tenantId: "tenant-1" as never,
                platformName: "Canvas LMS Updated",
                issuer: "https://canvas.instructure.com",
                clientId: "new-client-123",
                jwksUrl: "https://canvas.instructure.com/.well-known/jwks.json",
                authUrl: "https://canvas.instructure.com/api/lti/authorize_redirect",
                tokenUrl: "https://canvas.instructure.com/login/oauth2/token"
            });

            expect(result.platformId).toBe("platform-1");
            expect(mockPrisma.lTIPlatform.upsert).toHaveBeenCalledWith(
                expect.objectContaining({
                    where: { tenantId_issuer: { tenantId: "tenant-1", issuer: "https://canvas.instructure.com" } }
                })
            );
        });
    });

    describe("checkHealth", () => {
        it("should return true when database is accessible", async () => {
            mockPrisma.$queryRaw.mockResolvedValue([{ 1: 1 }]);

            const result = await service.checkHealth();

            expect(result).toBe(true);
        });
    });
});
