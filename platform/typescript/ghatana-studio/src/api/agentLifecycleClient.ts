/**
 * agentLifecycleClient - typed Studio client for agentic lifecycle actions.
 *
 * @doc.type module
 * @doc.purpose Validated Studio API boundary for agent-proposed lifecycle actions
 * @doc.layer platform
 * @doc.pattern Client
 */

import { ApiClient } from '@ghatana/api';
import {
  type AgentLifecycleActionRequest,
  type AgentLifecycleActionResult,
  AgentLifecycleActionRequestSchema,
  AgentLifecycleActionResultSchema,
} from '@ghatana/kernel-product-contracts';

export interface AgentLifecycleClientOptions {
  readonly baseUrl?: string;
  readonly apiClient?: ApiClient;
  readonly tenantId?: string;
  readonly workspaceId?: string;
  readonly projectId?: string;
  readonly correlationIdFactory?: () => string;
  readonly authToken?: string;
}

export interface AgentLifecycleClient {
  submitAction(request: AgentLifecycleActionRequest): Promise<AgentLifecycleActionResult>;
}

export function createAgentLifecycleClient(
  options: AgentLifecycleClientOptions = {},
): AgentLifecycleClient {
  return new DefaultAgentLifecycleClient(options);
}

class DefaultAgentLifecycleClient implements AgentLifecycleClient {
  private readonly apiClient: ApiClient;
  private readonly tenantId?: string;
  private readonly workspaceId?: string;
  private readonly projectId?: string;
  private readonly correlationIdFactory: () => string;
  private readonly authToken?: string;

  constructor(options: AgentLifecycleClientOptions) {
    this.apiClient = options.apiClient ?? new ApiClient({ baseUrl: options.baseUrl });
    this.tenantId = options.tenantId;
    this.workspaceId = options.workspaceId;
    this.projectId = options.projectId;
    this.correlationIdFactory =
      options.correlationIdFactory ?? (() => `studio-agent-${Date.now()}`);
    this.authToken = options.authToken;
  }

  async submitAction(request: AgentLifecycleActionRequest): Promise<AgentLifecycleActionResult> {
    const parsedRequest = AgentLifecycleActionRequestSchema.parse(request);
    const response = await this.apiClient.post<AgentLifecycleActionResult>(
      '/api/v1/agentic/lifecycle-actions',
      {
        headers: this.buildHeaders(parsedRequest.correlationId),
        body: parsedRequest,
        schema: AgentLifecycleActionResultSchema,
      },
    );
    return response.data;
  }

  private buildHeaders(correlationId?: string): Record<string, string> {
    return {
      'X-Correlation-ID': correlationId ?? this.correlationIdFactory(),
      ...(this.tenantId !== undefined ? { 'X-Ghatana-Tenant-Id': this.tenantId } : {}),
      ...(this.workspaceId !== undefined
        ? { 'X-Ghatana-Workspace-Id': this.workspaceId }
        : {}),
      ...(this.projectId !== undefined ? { 'X-Ghatana-Project-Id': this.projectId } : {}),
      ...(this.authToken !== undefined ? { Authorization: `Bearer ${this.authToken}` } : {}),
    };
  }
}
