/**
 * Agent Service - Integration with DCMaar Agent
 *
 * This service provides methods to connect to and communicate with
 * the DCMaar agent for metrics collection, command execution, and
 * plugin management.
 */

import { invoke } from '@tauri-apps/api/core';

export interface AgentConfig {
  httpUrl: string;
  wsUrl: string;
  ipcUrl?: string;
  apiKey?: string;
  jwtToken?: string;
}

export interface MetricsData {
  cpu: number;
  memory: number;
  disk: number;
  network: {
    bytesIn: number;
    bytesOut: number;
  };
  timestamp: number;
}

export interface PluginInfo {
  id: string;
  name: string;
  version: string;
  description: string;
  status: 'stopped' | 'starting' | 'running' | 'stopping' | 'crashed';
  config: Record<string, any>;
}

export interface CommandRequest {
  command: string;
  args: string[];
  timeout?: number;
}

export interface CommandResponse {
  exitCode: number;
  stdout: string;
  stderr: string;
  duration: number;
}

export class AgentService {
  private config: AgentConfig;
  private wsConnection: WebSocket | null = null;
  private metricsCallbacks: Set<(data: MetricsData) => void> = new Set();
  private eventCallbacks: Set<(event: unknown) => void> = new Set();

  constructor(config: AgentConfig) {
    this.config = config;
  }

  /**
   * Test connection to the agent
   */
  async testConnection(): Promise<boolean> {
    try {
      const response = await fetch(`${this.config.httpUrl}/health`, {
        method: 'GET',
        headers: this.getHeaders(),
      });
      return response.ok;
    } catch (error) {
      console.error('Agent connection test failed:', error);
      return false;
    }
  }

  /**
   * Get current metrics from agent
   */
  async getMetrics(): Promise<MetricsData> {
    try {
      const response = await fetch(`${this.config.httpUrl}/api/v1/metrics`, {
        method: 'GET',
        headers: this.getHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch metrics: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to get metrics:', error);
      throw error;
    }
  }

  /**
   * Connect to WebSocket for real-time metrics
   */
  connectMetricsStream(onMetrics: (data: MetricsData) => void): () => void {
    this.metricsCallbacks.add(onMetrics);

    if (!this.wsConnection) {
      this.initializeWebSocket();
    }

    // Return cleanup function
    return () => {
      this.metricsCallbacks.delete(onMetrics);
      if (this.metricsCallbacks.size === 0 && this.eventCallbacks.size === 0) {
        this.disconnectWebSocket();
      }
    };
  }

  /**
   * Connect to WebSocket for real-time events
   */
  connectEventStream(onEvent: (event: unknown) => void): () => void {
    this.eventCallbacks.add(onEvent);

    if (!this.wsConnection) {
      this.initializeWebSocket();
    }

    // Return cleanup function
    return () => {
      this.eventCallbacks.delete(onEvent);
      if (this.metricsCallbacks.size === 0 && this.eventCallbacks.size === 0) {
        this.disconnectWebSocket();
      }
    };
  }

  /**
   * List all plugins
   */
  async listPlugins(status?: string): Promise<PluginInfo[]> {
    try {
      const url = new URL(`${this.config.httpUrl}/api/v1/plugins`);
      if (status) {
        url.searchParams.append('status', status);
      }

      const response = await fetch(url.toString(), {
        method: 'GET',
        headers: this.getHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to list plugins: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to list plugins:', error);
      throw error;
    }
  }

  /**
   * Get plugin details
   */
  async getPlugin(pluginId: string): Promise<PluginInfo> {
    try {
      const response = await fetch(
        `${this.config.httpUrl}/api/v1/plugins/${pluginId}`,
        {
          method: 'GET',
          headers: this.getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to get plugin: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to get plugin:', error);
      throw error;
    }
  }

  /**
   * Install a plugin
   */
  async installPlugin(
    source: string,
    config?: Record<string, any>
  ): Promise<PluginInfo> {
    try {
      const response = await fetch(`${this.config.httpUrl}/api/v1/plugins`, {
        method: 'POST',
        headers: this.getHeaders(),
        body: JSON.stringify({ source, config }),
      });

      if (!response.ok) {
        throw new Error(`Failed to install plugin: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to install plugin:', error);
      throw error;
    }
  }

  /**
   * Uninstall a plugin
   */
  async uninstallPlugin(pluginId: string): Promise<void> {
    try {
      const response = await fetch(
        `${this.config.httpUrl}/api/v1/plugins/${pluginId}`,
        {
          method: 'DELETE',
          headers: this.getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to uninstall plugin: ${response.statusText}`);
      }
    } catch (error) {
      console.error('Failed to uninstall plugin:', error);
      throw error;
    }
  }

  /**
   * Start a plugin
   */
  async startPlugin(pluginId: string): Promise<PluginInfo> {
    try {
      const response = await fetch(
        `${this.config.httpUrl}/api/v1/plugins/${pluginId}/start`,
        {
          method: 'POST',
          headers: this.getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to start plugin: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to start plugin:', error);
      throw error;
    }
  }

  /**
   * Stop a plugin
   */
  async stopPlugin(pluginId: string): Promise<PluginInfo> {
    try {
      const response = await fetch(
        `${this.config.httpUrl}/api/v1/plugins/${pluginId}/stop`,
        {
          method: 'POST',
          headers: this.getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to stop plugin: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to stop plugin:', error);
      throw error;
    }
  }

  /**
   * Update a plugin configuration
   */
  async updatePlugin(
    pluginId: string,
    config: Record<string, any>
  ): Promise<PluginInfo> {
    try {
      const response = await fetch(
        `${this.config.httpUrl}/api/v1/plugins/${pluginId}`,
        {
          method: 'PUT',
          headers: this.getHeaders(),
          body: JSON.stringify({ config }),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to update plugin: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to update plugin:', error);
      throw error;
    }
  }

  /**
   * Execute a command via agent
   */
  async executeCommand(request: CommandRequest): Promise<CommandResponse> {
    try {
      // Use Tauri's invoke to execute command securely
      const result = await invoke<CommandResponse>('execute_command', {
        command: request.command,
        args: request.args,
        timeout: request.timeout || 30000,
      });

      return result;
    } catch (error) {
      console.error('Failed to execute command:', error);
      throw error;
    }
  }

  /**
   * Get agent health status
   */
  async getHealth(): Promise<{
    status: string;
    version: string;
    uptime: number;
    queueDepth: number;
    queueCapacity: number;
    queueHighWatermark: number;
    queueLowWatermark: number;
  }> {
    try {
      const response = await fetch(`${this.config.httpUrl}/health`, {
        method: 'GET',
        headers: this.getHeaders(),
      });

      if (!response.ok) {
        throw new Error(`Failed to get health: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('Failed to get health:', error);
      throw error;
    }
  }

  /**
   * Update agent configuration
   */
  async updateConfig(config: Partial<AgentConfig>): Promise<void> {
    this.config = { ...this.config, ...config };

    // Reconnect WebSocket if URL changed
    if (config.wsUrl && this.wsConnection) {
      this.disconnectWebSocket();
      this.initializeWebSocket();
    }
  }

  /**
   * Disconnect from agent
   */
  disconnect(): void {
    this.disconnectWebSocket();
    this.metricsCallbacks.clear();
    this.eventCallbacks.clear();
  }

  // Private methods

  private getHeaders(): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    if (this.config.jwtToken) {
      headers['Authorization'] = `Bearer ${this.config.jwtToken}`;
    } else if (this.config.apiKey) {
      headers['X-API-Key'] = this.config.apiKey;
    }

    return headers;
  }

  private initializeWebSocket(): void {
    try {
      const wsUrl = `${this.config.wsUrl}/ws/metrics`;
      this.wsConnection = new WebSocket(wsUrl);

      this.wsConnection.onopen = () => {
        console.log('WebSocket connected to agent');
      };

      this.wsConnection.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);

          switch (message.type) {
            case 'metrics':
              this.metricsCallbacks.forEach((callback) => callback(message.data));
              break;
            case 'event':
              this.eventCallbacks.forEach((callback) => callback(message));
              break;
            case 'ping':
              // Send pong
              this.wsConnection?.send(
                JSON.stringify({ type: 'pong', timestamp: Date.now() })
              );
              break;
          }
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      };

      this.wsConnection.onerror = (error) => {
        console.error('WebSocket error:', error);
      };

      this.wsConnection.onclose = () => {
        console.log('WebSocket disconnected from agent');
        this.wsConnection = null;

        // Attempt reconnection if there are active callbacks
        if (
          this.metricsCallbacks.size > 0 ||
          this.eventCallbacks.size > 0
        ) {
          setTimeout(() => this.initializeWebSocket(), 5000);
        }
      };
    } catch (error) {
      console.error('Failed to initialize WebSocket:', error);
    }
  }

  private disconnectWebSocket(): void {
    if (this.wsConnection) {
      this.wsConnection.close();
      this.wsConnection = null;
    }
  }
}

// Singleton instance
let agentServiceInstance: AgentService | null = null;

/**
 * Get or create the agent service instance
 */
export function getAgentService(config?: AgentConfig): AgentService {
  if (!agentServiceInstance && config) {
    agentServiceInstance = new AgentService(config);
  } else if (!agentServiceInstance) {
    // Use default config from environment
    agentServiceInstance = new AgentService({
      httpUrl: import.meta.env.VITE_AGENT_HTTP_URL || 'http://localhost:8080',
      wsUrl: import.meta.env.VITE_AGENT_WS_URL || 'ws://localhost:8080',
    });
  }

  return agentServiceInstance;
}

export default AgentService;
