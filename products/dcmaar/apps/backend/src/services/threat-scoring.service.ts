import { logger } from "../utils/logger";

// Use Node 18+ global fetch; declare for TypeScript without DOM lib.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const fetch: any;

export interface ThreatAssessmentRequest {
    agentId: string;
    agentType?: string;
    agentStatus?: string;
    agentMetadata?: Record<string, string>;
    eventData?: Record<string, unknown>;
    deviceMetrics?: Record<string, number>;
}

export interface ThreatAssessmentResponse {
    agentId: string;
    threatLevel: string;
    isThreat: boolean;
    suspiciousIndicators: number;
    evidence: string[];
    recommendedAction: string;
    assessedAt: string;
    healthScore?: number | null;
    unhealthy?: boolean | null;
}

const DEFAULT_BASE_URL = process.env.GUARDIAN_THREAT_SERVICE_URL ?? "http://localhost:8090";

/**
 * Best-effort client for the Guardian Java threat/health scoring service.
 *
 * This helper is intentionally resilient: on failure or non-2xx responses it
 * logs a warning and returns null rather than throwing.
 */
export async function assessThreatAndHealth(
    req: ThreatAssessmentRequest
): Promise<ThreatAssessmentResponse | null> {
    const url = `${DEFAULT_BASE_URL}/api/v1/guardian/threat-assessment`;

    try {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                agentId: req.agentId,
                agentType: req.agentType,
                agentStatus: req.agentStatus,
                agentMetadata: req.agentMetadata ?? {},
                eventData: req.eventData ?? {},
                deviceMetrics: req.deviceMetrics ?? {},
            }),
        });

        if (!response || !response.ok) {
            logger.warn("Guardian threat service returned non-OK status", {
                status: response?.status,
                url,
            });
            return null;
        }

        const data = (await response.json()) as ThreatAssessmentResponse;
        logger.info("Guardian threat assessment result", {
            agentId: data.agentId,
            threatLevel: data.threatLevel,
            isThreat: data.isThreat,
            unhealthy: data.unhealthy,
        });
        return data;
    } catch (error) {
        logger.warn("Guardian threat service call failed", {
            url,
            error: error instanceof Error ? error.message : String(error),
        });
        return null;
    }
}
