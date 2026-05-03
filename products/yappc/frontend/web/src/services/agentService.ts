import type { Agent } from '../types/agent';
import {
  parseJsonResponse as sharedParseJsonResponse,
  readErrorResponse as sharedReadErrorResponse,
} from '@/lib/http';
import { getAccessToken } from './session/SessionManager';

type TaskCapability = 'design-review' | 'code-review' | 'code-generation';

interface AgentExecutionMetadata {
  agent?: { id: string };
  metrics?: Record<string, unknown>;
  steps?: unknown[];
  artifacts?: unknown[];
  executionTime?: number;
}

interface AgentExecutionResult {
  success: boolean;
  output?: string;
  confidence: number;
  errors?: string[];
  metadata?: AgentExecutionMetadata;
}

/**
 * Service for managing agents and their interactions
 */
class AgentService {
  private baseUrl = '/api/agents';
  private initialized = false;

  constructor() {
    this.initializeAgents();
  }

  /**
   * Initialize YAPPC agents
   */
  private async initializeAgents(): Promise<void> {
    try {
      this.initialized = true;
      console.log('[AgentService] Agent service initialized');
    } catch (error) {
      console.error('[AgentService] Failed to initialize agents:', error);
      throw error;
    }
  }

  /**
   * Fetch all agents
   */
  async getAgents(): Promise<Agent[]> {
    const response = await fetch(this.baseUrl);
    if (!response.ok) {
      throw new Error(await this.readErrorResponse(response, 'Failed to fetch agents'));
    }
    return this.parseJsonResponse<Agent[]>(response, 'get agents');
  }

  /**
   * Fetch a single agent by ID
   */
  async getAgent(id: string): Promise<Agent> {
    const response = await fetch(`${this.baseUrl}/${id}`);
    if (!response.ok) {
      throw new Error(await this.readErrorResponse(response, 'Failed to fetch agent'));
    }
    return this.parseJsonResponse<Agent>(response, 'get agent');
  }

  /**
   * Create a new agent
   */
  async createAgent(agent: Omit<Agent, 'id' | 'lastActive'>): Promise<Agent> {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(agent),
    });

    if (!response.ok) {
      throw new Error(await this.readErrorResponse(response, 'Failed to create agent'));
    }

    return this.parseJsonResponse<Agent>(response, 'create agent');
  }

  /**
   * Update an existing agent
   */
  async updateAgent(id: string, updates: Partial<Agent>): Promise<Agent> {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(updates),
    });

    if (!response.ok) {
      throw new Error(await this.readErrorResponse(response, 'Failed to update agent'));
    }

    return this.parseJsonResponse<Agent>(response, 'update agent');
  }

  /**
   * Delete an agent
   */
  async deleteAgent(id: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
    });

    if (!response.ok) {
      throw new Error(await this.readErrorResponse(response, 'Failed to delete agent'));
    }
  }

  /**
   * Search agents by query
   */
  async searchAgents(query: string): Promise<Agent[]> {
    const response = await fetch(`${this.baseUrl}/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) {
      throw new Error(await this.readErrorResponse(response, 'Search failed'));
    }
    return this.parseJsonResponse<Agent[]>(response, 'search agents');
  }

  private async readErrorResponse(
    response: Response,
    fallback: string
  ): Promise<string> {
    return sharedReadErrorResponse(response, fallback);
  }

  private async parseJsonResponse<T>(
    response: Response,
    context: string
  ): Promise<T> {
    try {
      return await sharedParseJsonResponse<T>(response, `AgentService ${context}`);
    } catch (error) {
      if (error instanceof Error) {
        const message = error.message.replace(
          /^AgentService /,
          'AgentService '
        );
        throw new Error(message);
      }
      throw error;
    }
  }

  /**
   * Execute a task using YAPPC Agent Orchestrator
   * @param taskDescription Description of the task to execute
   * @param agentIds IDs of agents to involve in the task
   */
  async executeTask(taskDescription: string, agentIds: string[]): Promise<unknown> {
    try {
      // Wait for agents to be initialized
      if (!this.initialized) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        if (!this.initialized) {
          throw new Error('Agents not initialized');
        }
      }

      // Determine task type based on description and available agents
      const taskType = this.determineTaskType(taskDescription);

      // Prepare task input based on task type
      const taskInput = this.prepareTaskInput(taskDescription, taskType);

      // Execute task through backend orchestration
      const result = await this.runTask(taskType, taskInput);

      // Format result for compatibility
      return {
        taskId: this.generateTaskId(),
        status: result.success ? 'completed' : 'failed',
        result: result.output || `Task completed: ${taskDescription}`,
        timestamp: new Date().toISOString(),
        involvedAgents: agentIds,
        executionTime: result.metadata?.executionTime || 0,
        steps: result.metadata?.steps || [],
        artifacts: result.metadata?.artifacts || [],
        metadata: {
          taskType,
          agent: result.metadata?.agent?.id || 'backend-orchestrator',
          confidence: result.confidence || 0.8,
          metrics: result.metadata?.metrics || {},
        }
      };
    } catch (error) {
      console.error('[AgentService] YAPPC agent execution failed:', error);

      // Fallback to mock implementation for development
      if (import.meta.env.DEV) {
        console.warn('[AgentService] Falling back to mock implementation in development');
        return this.executeTaskMock(taskDescription, agentIds);
      }

      throw new Error(`Failed to execute task: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }

  /**
   * Determine task type based on description and agents
   */
  private determineTaskType(taskDescription: string): TaskCapability {
    const description = taskDescription.toLowerCase();

    // Design-related tasks
    if (description.includes('design') || description.includes('ui') || description.includes('accessibility')) {
      return 'design-review';
    }

    // Code-related tasks
    if (description.includes('code') || description.includes('review') || description.includes('security')) {
      return 'code-review';
    }

    // Generation tasks
    if (description.includes('generate') || description.includes('create') || description.includes('implement')) {
      return 'code-generation';
    }

    // Default to design review
    return 'design-review';
  }

  /**
   * Prepare task input based on task type
   */
  private prepareTaskInput(
    taskDescription: string,
    taskType: TaskCapability
  ): Record<string, string> {
    switch (taskType) {
      case 'design-review':
        return {
          code: taskDescription,
          styles: '',
          context: 'canvas-design-review',
        };

      case 'code-review':
        return {
          code: taskDescription,
          language: 'typescript',
          context: 'canvas-code-review',
        };

      case 'code-generation':
        return {
          prompt: taskDescription,
          language: 'typescript',
          context: 'canvas-code-generation',
        };

      default:
        return {
          prompt: taskDescription,
          context: 'general-task',
        };
    }
  }

  private async runTask(
    capability: TaskCapability,
    input: Record<string, string>
  ): Promise<AgentExecutionResult> {
    const response = await fetch(`${this.baseUrl}/execute`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.getAuthToken()}`,
      },
      body: JSON.stringify({
        capability,
        input,
      }),
    });

    if (!response.ok) {
      throw new Error(
        await this.readErrorResponse(response, 'Failed to execute agent task')
      );
    }

    return this.parseJsonResponse<AgentExecutionResult>(response, 'execute task');
  }

  /**
   * Mock implementation for development/fallback
   */
  private async executeTaskMock(taskDescription: string, agentIds: string[]): Promise<unknown> {
    console.log(`[AgentService] Mock execution: ${taskDescription} with agents:`, agentIds);

    // Simulate API call with realistic timing
    await new Promise(resolve => setTimeout(resolve, 1500 + Math.random() * 1000));

    return {
      taskId: this.generateTaskId(),
      status: 'completed',
      result: `Mock completed: ${taskDescription}`,
      timestamp: new Date().toISOString(),
      involvedAgents: agentIds,
      executionTime: 1500 + Math.random() * 1000,
      steps: [
        { step: 1, action: 'analyze_task', status: 'completed', duration: 500 },
        { step: 2, action: 'plan_execution', status: 'completed', duration: 300 },
        { step: 3, action: 'execute_task', status: 'completed', duration: 700 },
      ],
      artifacts: [],
      metadata: {
        model: 'mock-gpt-4',
        tokens: { input: 150, output: 200, total: 350 },
        cost: { input: 0.003, output: 0.006, total: 0.009 },
      }
    };
  }

  /**
   * Get authentication token
   */
  private getAuthToken(): string {
    // Centralized token retrieval via SessionManager
    const token = getAccessToken();
    if (token) return token;

    // Try sessionStorage as fallback
    if (typeof sessionStorage !== 'undefined') {
      const sessionToken = sessionStorage.getItem('auth_token');
      if (sessionToken) return sessionToken;
    }

    // Fall back to development token
    if (import.meta.env.DEV) {
      return import.meta.env.VITE_DEV_AUTH_TOKEN || 'dev-token';
    }

    throw new Error('No authentication token available');
  }

  /**
   * Generate unique session ID
   */
  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }

  /**
   * Generate unique task ID
   */
  private generateTaskId(): string {
    return `task_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }

  /**
   * Generate unique request ID
   */
  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }
}

export const agentService = new AgentService();
