/**
 * Java AI Agent HTTP Client
 *
 * Production HTTP client that communicates with the Java AI backend service.
 * Replaces the stub factory previously imported by AIAgentsResolver.
 *
 * All methods implement:
 *  - Typed request/response records
 *  - Explicit HTTP status handling
 *  - Configurable timeouts
 *  - Structured error propagation
 *
 * @doc.type class
 * @doc.purpose Production HTTP client for Java AI agent backend integration
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type {
  CopilotInput,
  QueryParserInput,
  PredictionInput,
  AnomalyInput,
} from '../../stubs/ai/agents/api-client';

// ─── Configuration ────────────────────────────────────────────────────────────

export interface JavaAIClientConfig {
  readonly baseUrl: string;
  readonly timeoutMs: number;
  readonly apiKey?: string;
}

// ─── Response shapes ──────────────────────────────────────────────────────────

export interface AgentMetrics {
  tokensUsed: number;
  latencyMs?: number;
}

export interface AgentError {
  message: string;
  code?: string;
}

export interface CopilotResponse {
  message: string;
  sessionId: string;
  confidence: number;
  data: Record<string, unknown>;
  success: boolean;
  metrics: AgentMetrics;
  error: AgentError | null;
}

export interface QueryParserResponse {
  intent: string;
  entities: Record<string, unknown>;
  data: Record<string, unknown>;
  success: boolean;
  metrics: AgentMetrics;
  error: AgentError | null;
}

export interface PredictionResponse {
  predictions: unknown[];
  accuracy: number;
  data: Record<string, unknown>;
  success: boolean;
  metrics: AgentMetrics;
  error: AgentError | null;
}

export interface AnomalyResponse {
  anomalies: unknown[];
  score: number;
  data: Record<string, unknown>;
  success: boolean;
  metrics: AgentMetrics;
  error: AgentError | null;
}

export interface HealthResponse {
  status: string;
  healthy: boolean;
  latency: number;
  lastCheck: Date;
  dependencies: Record<string, unknown>;
}

export interface AgentRegistryEntry {
  name: string;
  version: string;
  description: string;
  state: string;
  registeredAt: string;
  capabilities: string[];
}

// ─── Internal helpers ─────────────────────────────────────────────────────────

/**
 * Perform a JSON POST request to the Java AI backend with a timeout.
 * Throws a typed Error with full context on any HTTP or network failure.
 */
async function postJson<TBody, TResponse>(
  url: string,
  body: TBody,
  headers: Record<string, string>,
  timeoutMs: number
): Promise<TResponse> {
  const controller = new AbortController();
  const timerId = setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...headers },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    throw new Error(`AI agent request to ${url} failed: ${detail}`);
  } finally {
    clearTimeout(timerId);
  }

  if (!response.ok) {
    let errorBody = '';
    try {
      errorBody = await response.text();
    } catch {
      // best-effort
    }
    throw new Error(
      `AI agent backend returned HTTP ${response.status} for ${url}: ${errorBody}`
    );
  }

  try {
    return (await response.json()) as TResponse;
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    throw new Error(
      `AI agent response from ${url} could not be parsed as JSON: ${detail}`
    );
  }
}

/**
 * Perform a GET request to the Java AI backend.
 */
async function getJson<TResponse>(
  url: string,
  headers: Record<string, string>,
  timeoutMs: number
): Promise<TResponse> {
  const controller = new AbortController();
  const timerId = setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(url, {
      method: 'GET',
      headers,
      signal: controller.signal,
    });
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    throw new Error(`AI registry request to ${url} failed: ${detail}`);
  } finally {
    clearTimeout(timerId);
  }

  if (!response.ok) {
    let errorBody = '';
    try {
      errorBody = await response.text();
    } catch {
      // best-effort
    }
    throw new Error(
      `AI registry backend returned HTTP ${response.status} for ${url}: ${errorBody}`
    );
  }

  try {
    return (await response.json()) as TResponse;
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    throw new Error(
      `AI registry response from ${url} could not be parsed as JSON: ${detail}`
    );
  }
}

// ─── Client implementations ───────────────────────────────────────────────────

/**
 * Creates an HTTP copilot client backed by the Java AI backend.
 */
export function createCopilotClient(config: JavaAIClientConfig) {
  const authHeaders: Record<string, string> = config.apiKey
    ? { 'X-API-Key': config.apiKey }
    : {};

  return {
    async processMessage(input: CopilotInput): Promise<CopilotResponse> {
      return postJson<CopilotInput, CopilotResponse>(
        `${config.baseUrl}/api/v1/agents/copilot/message`,
        input,
        authHeaders,
        config.timeoutMs
      );
    },

    async execute(
      input: CopilotInput,
      context: Record<string, unknown>
    ): Promise<CopilotResponse> {
      return postJson<{ input: CopilotInput; context: Record<string, unknown> }, CopilotResponse>(
        `${config.baseUrl}/api/v1/agents/copilot/execute`,
        { input, context },
        authHeaders,
        config.timeoutMs
      );
    },

    async healthCheck(): Promise<HealthResponse> {
      return getJson<HealthResponse>(
        `${config.baseUrl}/api/v1/agents/copilot/health`,
        authHeaders,
        config.timeoutMs
      );
    },
  };
}

/**
 * Creates an HTTP query-parser client backed by the Java AI backend.
 */
export function createQueryParserClient(config: JavaAIClientConfig) {
  const authHeaders: Record<string, string> = config.apiKey
    ? { 'X-API-Key': config.apiKey }
    : {};

  return {
    async parseQuery(input: QueryParserInput): Promise<QueryParserResponse> {
      return postJson<QueryParserInput, QueryParserResponse>(
        `${config.baseUrl}/api/v1/agents/query-parser/parse`,
        input,
        authHeaders,
        config.timeoutMs
      );
    },

    async execute(
      input: QueryParserInput,
      context: Record<string, unknown>
    ): Promise<QueryParserResponse> {
      return postJson<
        { input: QueryParserInput; context: Record<string, unknown> },
        QueryParserResponse
      >(
        `${config.baseUrl}/api/v1/agents/query-parser/execute`,
        { input, context },
        authHeaders,
        config.timeoutMs
      );
    },

    async healthCheck(): Promise<HealthResponse> {
      return getJson<HealthResponse>(
        `${config.baseUrl}/api/v1/agents/query-parser/health`,
        authHeaders,
        config.timeoutMs
      );
    },
  };
}

/**
 * Creates an HTTP prediction client backed by the Java AI backend.
 */
export function createPredictionClient(config: JavaAIClientConfig) {
  const authHeaders: Record<string, string> = config.apiKey
    ? { 'X-API-Key': config.apiKey }
    : {};

  return {
    async predict(input: PredictionInput): Promise<PredictionResponse> {
      return postJson<PredictionInput, PredictionResponse>(
        `${config.baseUrl}/api/v1/agents/prediction/predict`,
        input,
        authHeaders,
        config.timeoutMs
      );
    },

    async execute(
      input: PredictionInput,
      context: Record<string, unknown>
    ): Promise<PredictionResponse> {
      return postJson<
        { input: PredictionInput; context: Record<string, unknown> },
        PredictionResponse
      >(
        `${config.baseUrl}/api/v1/agents/prediction/execute`,
        { input, context },
        authHeaders,
        config.timeoutMs
      );
    },

    async healthCheck(): Promise<HealthResponse> {
      return getJson<HealthResponse>(
        `${config.baseUrl}/api/v1/agents/prediction/health`,
        authHeaders,
        config.timeoutMs
      );
    },
  };
}

/**
 * Creates an HTTP anomaly-detector client backed by the Java AI backend.
 */
export function createAnomalyDetectorClient(config: JavaAIClientConfig) {
  const authHeaders: Record<string, string> = config.apiKey
    ? { 'X-API-Key': config.apiKey }
    : {};

  return {
    async detectAnomalies(input: AnomalyInput): Promise<AnomalyResponse> {
      return postJson<AnomalyInput, AnomalyResponse>(
        `${config.baseUrl}/api/v1/agents/anomaly-detector/detect`,
        input,
        authHeaders,
        config.timeoutMs
      );
    },

    async execute(
      input: AnomalyInput,
      context: Record<string, unknown>
    ): Promise<AnomalyResponse> {
      return postJson<
        { input: AnomalyInput; context: Record<string, unknown> },
        AnomalyResponse
      >(
        `${config.baseUrl}/api/v1/agents/anomaly-detector/execute`,
        { input, context },
        authHeaders,
        config.timeoutMs
      );
    },

    async healthCheck(): Promise<HealthResponse> {
      return getJson<HealthResponse>(
        `${config.baseUrl}/api/v1/agents/anomaly-detector/health`,
        authHeaders,
        config.timeoutMs
      );
    },
  };
}

/**
 * Fetches the live agent registry from the AEP Java backend.
 * Throws if the registry cannot be reached — callers should surface this error.
 */
export async function fetchAgentRegistry(
  config: JavaAIClientConfig
): Promise<AgentRegistryEntry[]> {
  const authHeaders: Record<string, string> = config.apiKey
    ? { 'X-API-Key': config.apiKey }
    : {};

  const response = await getJson<{ agents: AgentRegistryEntry[] }>(
    `${config.baseUrl}/api/v1/agents`,
    authHeaders,
    config.timeoutMs
  );

  return response.agents;
}
