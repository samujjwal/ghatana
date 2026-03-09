import type { SimulationService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
    SimulationLaunchToken,
    ModuleId,
    TenantId,
    UserId
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import { randomBytes } from "crypto";
import Redis from "ioredis";

export type HealthAwareSimulationService = SimulationService & {
    checkHealth: () => Promise<boolean>;
};

/**
 * Creates a Simulation Service for VR/AR content launching.
 * Manages session tokens and validates simulation access.
 * 
 * @doc.type class
 * @doc.purpose Launch and manage VR/AR simulation sessions
 * @doc.layer product
 * @doc.pattern Service
 */
export function createSimulationService(
    prisma: TutorPrismaClient,
    config: { simulationBaseUrl?: string; redisUrl?: string } = {}
): HealthAwareSimulationService {
    const baseUrl = config.simulationBaseUrl || "https://sim.tutorputor.io";
    const redis = new Redis(config.redisUrl || process.env.REDIS_URL || "redis://localhost:6379");

    return {
        async launchSimulation({ tenantId, userId, moduleId, blockId }) {
            // Verify the module exists and user has access
            const enrollment = await prisma.enrollment.findFirst({
                where: {
                    tenantId,
                    userId,
                    moduleId
                }
            });

            if (!enrollment) {
                throw new Error("User is not enrolled in this module");
            }

            // Fetch the module to get the VR block configuration
            const module = await prisma.module.findFirst({
                where: {
                    tenantId,
                    id: moduleId
                }
            });

            if (!module) {
                throw new Error("Module not found");
            }

            // Find the VR simulation block
            const contentBlocks = (module.contentBlocks as Array<{
                id: string;
                blockType: string;
                payload?: { bundleUrl?: string };
            }>) || [];

            const vrBlock = contentBlocks.find(
                (block) => block.id === blockId && block.blockType === "vr_simulation"
            );

            if (!vrBlock) {
                throw new Error("VR simulation block not found");
            }

            const bundleUrl = vrBlock.payload?.bundleUrl || `${baseUrl}/bundles/default.wasm`;

            // Generate secure token
            const token = generateSecureToken();
            const expiresAt = new Date(Date.now() + 30 * 60 * 1000); // 30 minutes

            // Store token in Redis with TTL
            await redis.set(
                `sim:token:${token}`,
                JSON.stringify({
                    tenantId,
                    userId,
                    moduleId,
                    blockId,
                    expiresAt,
                    bundleUrl
                }),
                "EX",
                30 * 60 // 30 minutes
            );

            const launchToken: SimulationLaunchToken = {
                token,
                expiresAt: expiresAt.toISOString(),
                sessionUrl: `${baseUrl}/session/${token}`,
                bundleUrl
            };

            // Log the launch event for analytics
            await prisma.learningEvent.create({
                data: {
                    tenantId,
                    userId,
                    eventType: "VR_SIMULATION_LAUNCH",
                    moduleId,
                    metadata: {
                        blockId,
                        token: token.substring(0, 8) + "..." // Partial token for logging
                    }
                }
            });

            return launchToken;
        },

        async validateToken({ token }) {
            const data = await redis.get(`sim:token:${token}`);

            if (!data) {
                return { valid: false };
            }

            const session = JSON.parse(data);
            const expiresAt = new Date(session.expiresAt);

            if (expiresAt < new Date()) {
                await redis.del(`sim:token:${token}`);
                return { valid: false };
            }

            return {
                valid: true,
                expiresAt: expiresAt.toISOString()
            };
        },

        async checkHealth() {
            await Promise.all([
                prisma.$queryRaw`SELECT 1`,
                redis.ping()
            ]);
            return true;
        }
    };
}

/**
 * Generate a cryptographically secure token.
 */
function generateSecureToken(): string {
    return randomBytes(32).toString("hex");
}

