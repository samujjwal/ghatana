/**
 * Feature Store Platform Client
 *
 * Optional singleton for ingesting and retrieving feature vectors from the
 * Ghatana platform Feature Store. Fully optional — when FEATURE_STORE_URL is
 * absent every call is a no-op (ingestAsync logs and returns, getFeatures
 * returns an empty object).
 *
 * Usage:
 *   import { featureStoreClient } from './feature-store.client.js';
 *   // Fire-and-forget — does not block the request path
 *   featureStoreClient.ingestAsync('acme', 'student-42', { session_count: 5 });
 *   // Awaitable
 *   await featureStoreClient.ingest('acme', 'student-42', { score: 0.87 });
 *   // Read features
 *   const features = await featureStoreClient.getFeatures('acme', 'student-42', ['score']);
 *
 * Environment variables:
 *   FEATURE_STORE_URL — base URL, e.g. http://feature-store:8080
 */

const REQUEST_TIMEOUT_MS = 5_000;

class FeatureStoreClient {
  private static instance: FeatureStoreClient;
  private readonly baseUrl: string | undefined;

  private constructor() {
    this.baseUrl = process.env['FEATURE_STORE_URL'];
    if (!this.baseUrl) {
      console.warn('[FeatureStoreClient] FEATURE_STORE_URL not configured — feature store is disabled');
    }
  }

  static getInstance(): FeatureStoreClient {
    if (!FeatureStoreClient.instance) {
      FeatureStoreClient.instance = new FeatureStoreClient();
    }
    return FeatureStoreClient.instance;
  }

  /**
   * Ingest features asynchronously (fire-and-forget).
   * Errors are swallowed and logged as warnings so the caller is never blocked.
   */
  ingestAsync(tenantId: string, entityId: string, features: Record<string, unknown>): void {
    this.ingest(tenantId, entityId, features).catch((err: unknown) => {
      console.warn(
        `[FeatureStoreClient] ingestAsync failed for entity ${entityId}: ` +
        `${err instanceof Error ? err.message : String(err)}`,
      );
    });
  }

  /**
   * Ingest features synchronously (awaitable).
   * Resolves without error when the feature store is unconfigured.
   */
  async ingest(
    tenantId: string,
    entityId: string,
    features: Record<string, unknown>,
  ): Promise<void> {
    if (!this.baseUrl || Object.keys(features).length === 0) return;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await fetch(
        `${this.baseUrl}/api/v1/features/${encodeURIComponent(tenantId)}/${encodeURIComponent(entityId)}/batch`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ features }),
          signal: controller.signal,
        },
      );
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * Retrieve named features for an entity.
   * Returns an empty object when the feature store is unconfigured or unavailable.
   */
  async getFeatures(
    tenantId: string,
    entityId: string,
    featureNames: string[],
  ): Promise<Record<string, unknown>> {
    if (!this.baseUrl || featureNames.length === 0) return {};

    const params = new URLSearchParams();
    featureNames.forEach((n) => params.append('names', n));
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await fetch(
        `${this.baseUrl}/api/v1/features/${encodeURIComponent(tenantId)}/${encodeURIComponent(entityId)}?${params}`,
        { signal: controller.signal },
      );
      if (!res.ok) {
        console.warn(`[FeatureStoreClient] getFeatures returned HTTP ${res.status}`);
        return {};
      }
      return (await res.json()) as Record<string, unknown>;
    } catch (err: unknown) {
      console.warn(
        `[FeatureStoreClient] getFeatures failed: ${err instanceof Error ? err.message : String(err)}`,
      );
      return {};
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

export const featureStoreClient = FeatureStoreClient.getInstance();
