/**
 * Connector health API client.
 *
 * @doc.type api-client
 * @doc.purpose Typed wrappers for DMOS connector-health HTTP endpoints
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';

export interface DmosBridgeStatusResponse {
  status: string;
  reason: string;
  updatedAt: string;
}

interface DmosKernelBridgeCheckResponse {
  status: string;
  component: string;
  bridges?: Record<string, DmosBridgeStatusResponse>;
}

interface DmosHealthChecksResponse {
  kernelBridge?: DmosKernelBridgeCheckResponse;
}

export interface DmosHealthResponse {
  status: string;
  timestamp: string;
  checks?: DmosHealthChecksResponse;
}

export async function getConnectorHealth(): Promise<DmosHealthResponse> {
  return apiRequest<DmosHealthResponse>('/health');
}
