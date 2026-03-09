import type { Agent } from '../types/agent';
import {
  BaseAgent,
  AgentOrchestrator,
  DesignAgent,
  ReviewAgent,
  CodeAgent,
  type IAgent,
  type AgentTask,
  type TaskResult
} from '@ghatana/yappc-ai/agents';

/**
 * Service for managing agents and their interactions
 */
class AgentService {
  private baseUrl = '/api/agents';
  private orchestrator: AgentOrchestrator;
  private agents: Map<string, IAgent> = new Map();
  private initialized = false;

  constructor() {
    this.orchestrator = new AgentOrchestrator();
    this.initializeAgents();
  }

  /**
   * Initialize YAPPC agents
   */
  private async initializeAgents(): Promise<void> {
    try {
      // Import AI service (placeholder - would be injected)
      const aiService = this.getAIService();

      // Create Design Agent
      const designAgent = new DesignAgent({
        id: 'design-1',
        name: 'Design Reviewer',
        description: 'Reviews UI designs for accessibility and consistency',
        aiService,
      });

      // Create Review Agent
      const reviewAgent = new ReviewAgent({
        id: 'review-1',
        name: 'Code Reviewer',
        description: 'Reviews code for quality and security',
        aiService,
      });

      // Create Code Agent
      const codeAgent = new CodeAgent({
        id: 'code-1',
        name: 'Code Generator',
        description: 'Generates and refactors code',
        aiService,
      });

      // Initialize agents
      await designAgent.initialize();
      await reviewAgent.initialize();
      await codeAgent.initialize();

      // Register with orchestrator
      this.orchestrator.registerAgent(designAgent);
      this.orchestrator.registerAgent(reviewAgent);
      this.orchestrator.registerAgent(codeAgent);

      // Store in local map
      this.agents.set('design-1', designAgent);
      this.agents.set('review-1', reviewAgent);
      this.agents.set('code-1', codeAgent);

      this.initialized = true;
      console.log('[AgentService] YAPPC agents initialized successfully');
    } catch (error) {
      console.error('[AgentService] Failed to initialize agents:', error);
    }
  }

  /**
   * Get AI service (placeholder - would be injected or configured)
   */
  private getAIService(): { generateText: (prompt: string) => Promise<string>; generateCode: (prompt: string) => Promise<string> } {
    // In a real implementation, this would get the configured AI service
    // For now, return a mock that implements the required interface
    return {
      generateText: async (prompt: string) => {
        // Mock AI response
        return `AI response for: ${prompt}`;
      },
      generateCode: async (prompt: string) => {
        return `// Generated code for: ${prompt}\nconsole.log('Hello World');`;
      }
    };
  }

  /**
   * Fetch all agents
   */
  async getAgents(): Promise<Agent[]> {
    const response = await fetch(this.baseUrl);
    if (!response.ok) {
      throw new Error('Failed to fetch agents');
    }
    return response.json();
  }

  /**
   * Fetch a single agent by ID
   */
  async getAgent(id: string): Promise<Agent> {
    const response = await fetch(`${this.baseUrl}/${id}`);
    if (!response.ok) {
      throw new Error('Failed to fetch agent');
    }
    return response.json();
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
      throw new Error('Failed to create agent');
    }

    return response.json();
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
      throw new Error('Failed to update agent');
    }

    return response.json();
  }

  /**
   * Delete an agent
   */
  async deleteAgent(id: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
    });

    if (!response.ok) {
      throw new Error('Failed to delete agent');
    }
  }

  /**
   * Search agents by query
   */
  async searchAgents(query: string): Promise<Agent[]> {
    const response = await fetch(`${this.baseUrl}/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) {
      throw new Error('Search failed');
    }
    return response.json();
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
      const taskType = this.determineTaskType(taskDescription, agentIds);

      // Prepare task input based on task type
      const taskInput = this.prepareTaskInput(taskDescription, taskType);

      // Execute task through orchestrator
      const result = await this.orchestrator.executeTask(taskType, taskInput);

      // Format result for compatibility
      return {
        taskId: this.generateTaskId(),
        status: result.status || 'completed',
        result: result.output || result.result || `Task completed: ${taskDescription}`,
        timestamp: new Date().toISOString(),
        involvedAgents: agentIds,
        executionTime: result.executionTime || 0,
        steps: result.steps || [],
        artifacts: result.artifacts || [],
        metadata: {
          taskType,
          agent: result.agent?.id || 'unknown',
          confidence: result.confidence || 0.8,
          metrics: result.metrics || {},
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
  private determineTaskType(taskDescription: string, agentIds: string[]): string {
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
  private prepareTaskInput(taskDescription: string, taskType: string): Record<string, string> {
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
    // Try localStorage first
    const localToken = localStorage.getItem('auth_token');
    if (localToken) return localToken;

    // Try sessionStorage
    const sessionToken = sessionStorage.getItem('auth_token');
    if (sessionToken) return sessionToken;

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
