import type { AuditPayload, AuditReceipt, AuditStoreClient } from "../types";

export interface HttpAuditClientConfig {
  /** Base URL of the K-07 Java audit service (e.g. http://app-platform:8080) */
  baseUrl: string;
  /** Request timeout in milliseconds. Default: 5000 */
  timeoutMs?: number;
  /** API key / service token for service-to-service auth */
  apiKey?: string;
}

/**
 * HTTP implementation of AuditStoreClient.
 *
 * Sends audit payloads to the K-07 Java REST endpoint via fetch.
 * Uses native Node.js 18+ `fetch`. For Node < 18, provide a global fetch polyfill.
 */
export class HttpAuditClient implements AuditStoreClient {
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly headers: Record<string, string>;

  constructor(config: HttpAuditClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, "");
    this.timeoutMs = config.timeoutMs ?? 5_000;
    this.headers = {
      "Content-Type": "application/json",
      ...(config.apiKey ? { Authorization: `Bearer ${config.apiKey}` } : {}),
    };
  }

  async log(payload: AuditPayload): Promise<AuditReceipt> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await fetch(`${this.baseUrl}/api/v1/audit/log`, {
        method: "POST",
        headers: this.headers,
        body: JSON.stringify(payload),
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`Audit service returned HTTP ${response.status}`);
      }

      return response.json() as Promise<AuditReceipt>;
    } finally {
      clearTimeout(timer);
    }
  }
}
