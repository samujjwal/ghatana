/**
 * Ghatana Platform Feature Store HTTP Client
 *
 * <p><b>Purpose</b><br>
 * Allows DCMAAR to emit device-usage and child-behaviour feature signals to the
 * Ghatana platform Feature Store HTTP service so that platform AI models can
 * leverage DCMAAR's rich telemetry (screen time, app usage, risk events) without
 * coupling DCMAAR's codebase to platform internals.
 *
 * <p><b>API contract</b><br>
 * <ul>
 *   <li>{@code POST /api/v1/features/{tenantId}/{entityId}} — ingest a single feature vector</li>
 *   <li>{@code POST /api/v1/features/{tenantId}/{entityId}/batch} — ingest multiple features</li>
 *   <li>{@code GET  /api/v1/features/{tenantId}/{entityId}?names=a,b,c} — retrieve features</li>
 * </ul>
 *
 * <p><b>Design</b><br>
 * The client is optional — when {@code FEATURE_STORE_URL} is not configured,
 * ingest calls are silently no-oped and retrieval calls return empty maps.
 * This ensures DCMAAR runs fully standalone without the platform feature store.
 *
 * Ingest calls are fire-and-forget by default ({@link ingestAsync}) to avoid
 * blocking DCMAAR's request path on an optional telemetry side-channel.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Fire-and-forget telemetry (recommended for hot paths)
 * featureStoreClient.ingestAsync(tenantId, childId, { screen_time_mins: 45 });
 *
 * // Retrieve features for inference input enrichment
 * const features = await featureStoreClient.getFeatures(tenantId, childId,
 *   ['screen_time_mins', 'risk_score_7d']);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Platform feature store HTTP client for DCMAAR telemetry emission
 * @doc.layer backend
 * @doc.pattern Adapter
 */
import { logger } from '../utils/logger';

const FEATURES_PATH = '/api/v1/features';
const DEFAULT_TIMEOUT_MS = 10_000;

/** A feature vector: featureName → numeric value. */
export type FeatureVector = Record<string, number>;

/** Individual feature as POSTed to the store. */
interface FeaturePayload {
  name: string;
  entityId: string;
  value: number;
  timestamp: string;
}

export class FeatureStoreClient {
  private static instance: FeatureStoreClient;

  private readonly baseUrl: string | null;

  private constructor() {
    this.baseUrl = process.env['FEATURE_STORE_URL'] ?? null;

    if (this.baseUrl) {
      logger.info('FeatureStoreClient initialised', { baseUrl: this.baseUrl });
    } else {
      logger.warn(
        'FEATURE_STORE_URL not configured — feature telemetry emission disabled.',
      );
    }
  }

  public static getInstance(): FeatureStoreClient {
    if (!FeatureStoreClient.instance) {
      FeatureStoreClient.instance = new FeatureStoreClient();
    }
    return FeatureStoreClient.instance;
  }

  // =========================================================================
  // Ingestion
  // =========================================================================

  /**
   * Asynchronously ingests a feature vector without blocking the caller.
   * Errors are logged at WARN level but never thrown.
   *
   * Prefer this for hot request paths where feature telemetry is best-effort.
   *
   * @param tenantId  tenant identifier
   * @param entityId  entity whose features are being updated (e.g. child user ID)
   * @param features  map of featureName → numeric value
   */
  ingestAsync(tenantId: string, entityId: string, features: FeatureVector): void {
    this.ingest(tenantId, entityId, features).catch((err) => {
      logger.warn('FeatureStore async ingest failed', {
        tenantId,
        entityId,
        error: err instanceof Error ? err.message : String(err),
      });
    });
  }

  /**
   * Ingests a feature vector and waits for acknowledgement.
   *
   * @param tenantId  tenant identifier
   * @param entityId  entity identifier
   * @param features  map of featureName → numeric value
   * @returns resolved when the store acknowledges the write; no-op if not configured
   */
  async ingest(tenantId: string, entityId: string, features: FeatureVector): Promise<void> {
    if (!this.baseUrl || Object.keys(features).length === 0) {
      return;
    }

    const timestamp = new Date().toISOString();
    const payload: FeaturePayload[] = Object.entries(features).map(([name, value]) => ({
      name,
      entityId,
      value,
      timestamp,
    }));

    const url = `${this.baseUrl}${FEATURES_PATH}/${encodeURIComponent(tenantId)}/${encodeURIComponent(entityId)}/batch`;

    await this.post(url, payload);
    logger.debug('FeatureStore ingested features', {
      tenantId,
      entityId,
      count: payload.length,
    });
  }

  // =========================================================================
  // Retrieval
  // =========================================================================

  /**
   * Retrieves the latest values for the given feature names for an entity.
   *
   * @param tenantId     tenant identifier
   * @param entityId     entity identifier
   * @param featureNames list of feature names to retrieve
   * @returns map of featureName → value for features that exist; absent features
   *          are omitted (never null values returned)
   */
  async getFeatures(
    tenantId: string,
    entityId: string,
    featureNames: string[],
  ): Promise<FeatureVector> {
    if (!this.baseUrl || featureNames.length === 0) {
      return {};
    }

    const namesParam = featureNames.map(encodeURIComponent).join(',');
    const url = `${this.baseUrl}${FEATURES_PATH}/${encodeURIComponent(tenantId)}/${encodeURIComponent(entityId)}?names=${namesParam}`;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        signal: controller.signal,
      });

      if (!response.ok) {
        logger.warn('FeatureStore GET failed', { status: response.status, url });
        return {};
      }

      const data = (await response.json()) as FeatureVector;
      return data ?? {};
    } catch (err) {
      logger.warn('FeatureStore getFeatures failed — returning empty map', {
        tenantId,
        entityId,
        error: err instanceof Error ? err.message : String(err),
      });
      return {};
    } finally {
      clearTimeout(timeoutId);
    }
  }

  // =========================================================================
  // Diagnostics
  // =========================================================================

  /** Returns {@code true} when the feature store URL is configured. */
  isConfigured(): boolean {
    return this.baseUrl !== null;
  }

  // =========================================================================
  // Internal helpers
  // =========================================================================

  private async post(url: string, body: unknown): Promise<void> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`Feature Store responded ${response.status} for POST ${url}`);
      }
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

export const featureStoreClient = FeatureStoreClient.getInstance();
