/**
 * Core Agent type definition
 * Represents an autonomous agent in the DCMAAR system
 */

export interface Agent {
  id: string;
  name: string;
  description?: string;
  version: string;
  status: 'active' | 'inactive' | 'paused' | 'error';
  capabilities: string[];
  config: Record<string, unknown>;
  createdAt: Date;
  updatedAt: Date;
}

export interface AgentFactory {
  createAgent(config: Partial<Agent>): Agent;
  createAgentAsync(config: Partial<Agent>): Promise<Agent>;
}
