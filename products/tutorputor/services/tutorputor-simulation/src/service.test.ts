import { describe, it, expect, vi, beforeEach } from "vitest";
import { createSimulationService } from "./service";
import Redis from 'ioredis';

// Mock ioredis
vi.mock('ioredis', () => {
    return {
        default: class Redis {
            private static data = new Map();
            constructor() { }
            async get(key: string) { return Redis.data.get(key); }
            async set(key: string, value: string) { Redis.data.set(key, value); return 'OK'; }
            async del(key: string) { Redis.data.delete(key); return 1; }
            async expire(key: string, seconds: number) { return 1; }
            async ping() { return 'PONG'; }
            on(event: string, callback: Function) { }
            static clear() { Redis.data.clear(); }
        }
    };
});

// Mock Prisma client
const mockPrisma = {
    enrollment: {
        findFirst: vi.fn()
    },
    module: {
        findFirst: vi.fn()
    },
    learningEvent: {
        create: vi.fn()
    },
    $queryRaw: vi.fn()
};

describe("SimulationService", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        (Redis as any).clear();
    });

    describe("launchSimulation", () => {
        it("should generate a launch token for enrolled user with VR block", async () => {
            // GIVEN
            const service = createSimulationService(mockPrisma as never);
            const args = {
                tenantId: "tenant-1",
                userId: "user-1",
                moduleId: "module-1",
                blockId: "block-vr-1"
            };

            mockPrisma.enrollment.findFirst.mockResolvedValue({
                id: "enrollment-1",
                tenantId: args.tenantId,
                userId: args.userId,
                moduleId: args.moduleId
            });

            mockPrisma.module.findFirst.mockResolvedValue({
                id: args.moduleId,
                tenantId: args.tenantId,
                contentBlocks: [
                    {
                        id: "block-vr-1",
                        blockType: "vr_simulation",
                        payload: {
                            bundleUrl: "https://cdn.example.com/sim.wasm"
                        }
                    }
                ]
            });

            mockPrisma.learningEvent.create.mockResolvedValue({ id: "event-1" });

            // WHEN
            const result = await service.launchSimulation(args);

            // THEN
            expect(result.token).toBeDefined();
            expect(result.token.length).toBeGreaterThan(32);
            expect(result.sessionUrl).toContain(result.token);
            expect(result.bundleUrl).toBe("https://cdn.example.com/sim.wasm");
            expect(new Date(result.expiresAt).getTime()).toBeGreaterThan(Date.now());
        });

        it("should throw error if user not enrolled", async () => {
            // GIVEN
            const service = createSimulationService(mockPrisma as never);
            mockPrisma.enrollment.findFirst.mockResolvedValue(null);

            // WHEN/THEN
            await expect(
                service.launchSimulation({
                    tenantId: "tenant-1",
                    userId: "user-1",
                    moduleId: "module-1",
                    blockId: "block-1"
                })
            ).rejects.toThrow("User is not enrolled in this module");
        });

        it("should throw error if VR block not found", async () => {
            // GIVEN
            const service = createSimulationService(mockPrisma as never);

            mockPrisma.enrollment.findFirst.mockResolvedValue({ id: "e-1" });
            mockPrisma.module.findFirst.mockResolvedValue({
                id: "module-1",
                contentBlocks: [
                    { id: "block-text", blockType: "text", payload: {} }
                ]
            });

            // WHEN/THEN
            await expect(
                service.launchSimulation({
                    tenantId: "tenant-1",
                    userId: "user-1",
                    moduleId: "module-1",
                    blockId: "block-vr-missing"
                })
            ).rejects.toThrow("VR simulation block not found");
        });
    });

    describe("validateToken", () => {
        it("should validate a recently generated token", async () => {
            // GIVEN
            const service = createSimulationService(mockPrisma as never);

            mockPrisma.enrollment.findFirst.mockResolvedValue({ id: "e-1" });
            mockPrisma.module.findFirst.mockResolvedValue({
                id: "module-1",
                contentBlocks: [
                    { id: "block-vr", blockType: "vr_simulation", payload: {} }
                ]
            });
            mockPrisma.learningEvent.create.mockResolvedValue({ id: "event-1" });

            const launchResult = await service.launchSimulation({
                tenantId: "tenant-1",
                userId: "user-1",
                moduleId: "module-1",
                blockId: "block-vr"
            });

            // WHEN
            const validation = await service.validateToken({ token: launchResult.token });

            // THEN
            expect(validation.valid).toBe(true);
            expect(validation.expiresAt).toBeDefined();
        });

        it("should reject invalid token", async () => {
            // GIVEN
            const service = createSimulationService(mockPrisma as never);

            // WHEN
            const result = await service.validateToken({ token: "invalid-token" });

            // THEN
            expect(result.valid).toBe(false);
        });
    });

    describe("checkHealth", () => {
        it("should return true when database is accessible", async () => {
            // GIVEN
            const service = createSimulationService(mockPrisma as never);
            mockPrisma.$queryRaw.mockResolvedValue([{ 1: 1 }]);

            // WHEN
            const result = await service.checkHealth();

            // THEN
            expect(result).toBe(true);
        });
    });
});
