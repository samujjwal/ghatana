export interface AgentConfig {
  id: string;
  name: string;
  type: string;
  enabled: boolean;
  settings: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export interface ConfigDiff {
  added: string[];
  removed: string[];
  changed: Record<string, { from: any; to: any }>;
}

export interface AgentStatus {
  id: string;
  name: string;
  status: 'running' | 'stopped' | 'error';
  lastHeartbeat?: string;
  version?: string;
  metrics?: Record<string, any>;
}

export const adminClient = {
  getAgentConfig: async (id: string): Promise<AgentConfig> => ({} as AgentConfig),
  updateAgentConfig: async (id: string, config: Partial<AgentConfig>): Promise<AgentConfig> => ({} as AgentConfig),
  getAgentStatus: async (id: string): Promise<AgentStatus> => ({} as AgentStatus),
  // Add other methods as needed
};

export const ADMIN_QUERY_KEYS = {
  AGENT_CONFIG: 'agentConfig',
  AGENT_STATUS: 'agentStatus',
  // Add other keys as needed
} as const;
