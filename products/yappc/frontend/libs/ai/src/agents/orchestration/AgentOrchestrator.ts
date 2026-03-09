/**
 * Agent Orchestrator
 *
 * Coordinates multiple agents to work together on complex tasks.
 * Responsibilities:
 * - Agent registration and discovery
 * - Task routing to appropriate agents
 * - Multi-agent workflows
 * - Result aggregation
 * - Conflict resolution
 */

import type {
  IAgent,
  AgentTask,
  TaskResult,
  AgentCapability,
  AgentMetrics,
} from '../types';

/**
 * Workflow step definition
 */
export interface WorkflowStep {
  id: string;
  agentCapability: AgentCapability;
  input: unknown;
  dependsOn?: string[]; // IDs of steps that must complete first
  optional?: boolean;
}

/**
 * Workflow definition
 */
export interface Workflow {
  id: string;
  name: string;
  steps: WorkflowStep[];
  parallelSteps?: string[][]; // Groups of steps that can run in parallel
}

/**
 * Workflow execution result
 */
export interface WorkflowResult {
  workflowId: string;
  success: boolean;
  stepResults: Map<string, TaskResult>;
  errors?: string[];
  duration: number; // milliseconds
}

/**
 * Agent Orchestrator implementation
 */
export class AgentOrchestrator {
  private _agents: Map<string, IAgent> = new Map();
  private _capabilityIndex: Map<AgentCapability, Set<string>> = new Map();
  private _workflows: Map<string, Workflow> = new Map();
  private _activeWorkflows: Map<string, Promise<WorkflowResult>> = new Map();

  /**
   * Register an agent
   */
  registerAgent(agent: IAgent): void {
    this._agents.set(agent.id, agent);

    // Index by capabilities
    agent.capabilities.forEach((capability) => {
      if (!this._capabilityIndex.has(capability)) {
        this._capabilityIndex.set(capability, new Set());
      }
      this._capabilityIndex.get(capability)!.add(agent.id);
    });
  }

  /**
   * Unregister an agent
   */
  unregisterAgent(agentId: string): boolean {
    const agent = this._agents.get(agentId);
    if (!agent) return false;

    // Remove from capability index
    agent.capabilities.forEach((capability) => {
      const agents = this._capabilityIndex.get(capability);
      if (agents) {
        agents.delete(agentId);
      }
    });

    return this._agents.delete(agentId);
  }

  /**
   * Find agents by capability
   */
  findAgentsByCapability(capability: AgentCapability): IAgent[] {
    const agentIds = this._capabilityIndex.get(capability);
    if (!agentIds) return [];

    return Array.from(agentIds)
      .map((id) => this._agents.get(id))
      .filter((agent): agent is IAgent => agent !== undefined);
  }

  /**
   * Get best agent for a capability (based on metrics)
   */
  getBestAgent(capability: AgentCapability): IAgent | undefined {
    const agents = this.findAgentsByCapability(capability);
    if (agents.length === 0) return undefined;

    // Sort by success rate and average execution time
    return agents.sort((a, b) => {
      const scoreA = this.calculateAgentScore(a.metrics);
      const scoreB = this.calculateAgentScore(b.metrics);
      return scoreB - scoreA;
    })[0];
  }

  /**
   * Execute a single task with the best available agent
   */
  async executeTask<TInput, TOutput>(
    capability: AgentCapability,
    input: TInput
  ): Promise<TaskResult<TOutput>> {
    const agent = this.getBestAgent(capability);

    if (!agent) {
      return {
        success: false,
        confidence: 0,
        errors: [`No agent available with capability: ${capability}`],
      };
    }

    return agent.execute(input) as Promise<TaskResult<TOutput>>;
  }

  /**
   * Register a workflow
   */
  registerWorkflow(workflow: Workflow): void {
    // Validate workflow
    this.validateWorkflow(workflow);
    this._workflows.set(workflow.id, workflow);
  }

  /**
   * Execute a workflow
   */
  async executeWorkflow<TOutput = unknown>(
    workflowId: string
  ): Promise<WorkflowResult> {
    const workflow = this._workflows.get(workflowId);
    if (!workflow) {
      throw new Error(`Workflow not found: ${workflowId}`);
    }

    // Check if already running
    if (this._activeWorkflows.has(workflowId)) {
      return this._activeWorkflows.get(workflowId)!;
    }

    const execution = this.runWorkflow(workflow);
    this._activeWorkflows.set(workflowId, execution);

    try {
      const result = await execution;
      return result;
    } finally {
      this._activeWorkflows.delete(workflowId);
    }
  }

  /**
   * Run workflow execution
   */
  private async runWorkflow(workflow: Workflow): Promise<WorkflowResult> {
    const startTime = Date.now();
    const stepResults = new Map<string, TaskResult>();
    const errors: string[] = [];
    const completedSteps = new Set<string>();

    try {
      // Build dependency graph
      const stepMap = new Map(workflow.steps.map((step) => [step.id, step]));
      const dependencyGraph = this.buildDependencyGraph(workflow.steps);

      // Execute steps in topological order
      while (completedSteps.size < workflow.steps.length) {
        const readySteps = this.getReadySteps(
          workflow.steps,
          completedSteps,
          dependencyGraph
        );

        if (readySteps.length === 0) {
          // Check if there are incomplete steps
          const incompleteSteps = workflow.steps.filter(
            (step) => !completedSteps.has(step.id) && !step.optional
          );

          if (incompleteSteps.length > 0) {
            errors.push(
              `Deadlock detected: steps ${incompleteSteps.map((s) => s.id).join(', ')} cannot proceed`
            );
            break;
          }

          break; // All remaining steps are optional and can't proceed
        }

        // Execute ready steps in parallel
        const stepPromises = readySteps.map(async (step) => {
          try {
            const agent = this.getBestAgent(step.agentCapability);
            if (!agent) {
              if (!step.optional) {
                errors.push(
                  `No agent found for capability: ${step.agentCapability} (step: ${step.id})`
                );
              }
              return;
            }

            const result = await agent.execute(step.input);
            stepResults.set(step.id, result);

            if (!result.success && !step.optional) {
              errors.push(
                `Step ${step.id} failed: ${result.errors?.join(', ')}`
              );
            }
          } catch (error) {
            if (!step.optional) {
              errors.push(`Step ${step.id} error: ${(error as Error).message}`);
            }
          } finally {
            completedSteps.add(step.id);
          }
        });

        await Promise.all(stepPromises);
      }

      const duration = Date.now() - startTime;
      const success =
        errors.length === 0 &&
        workflow.steps
          .filter((s) => !s.optional)
          .every((s) => completedSteps.has(s.id));

      return {
        workflowId: workflow.id,
        success,
        stepResults,
        errors: errors.length > 0 ? errors : undefined,
        duration,
      };
    } catch (error) {
      return {
        workflowId: workflow.id,
        success: false,
        stepResults,
        errors: [...errors, (error as Error).message],
        duration: Date.now() - startTime,
      };
    }
  }

  /**
   * Build dependency graph
   */
  private buildDependencyGraph(
    steps: WorkflowStep[]
  ): Map<string, Set<string>> {
    const graph = new Map<string, Set<string>>();

    steps.forEach((step) => {
      if (!graph.has(step.id)) {
        graph.set(step.id, new Set());
      }

      if (step.dependsOn) {
        step.dependsOn.forEach((depId) => {
          graph.get(step.id)!.add(depId);
        });
      }
    });

    return graph;
  }

  /**
   * Get steps that are ready to execute
   */
  private getReadySteps(
    steps: WorkflowStep[],
    completed: Set<string>,
    graph: Map<string, Set<string>>
  ): WorkflowStep[] {
    return steps.filter((step) => {
      // Already completed
      if (completed.has(step.id)) return false;

      // Check dependencies
      const deps = graph.get(step.id);
      if (!deps || deps.size === 0) return true;

      // All dependencies completed
      return Array.from(deps).every((depId) => completed.has(depId));
    });
  }

  /**
   * Validate workflow definition
   */
  private validateWorkflow(workflow: Workflow): void {
    const stepIds = new Set(workflow.steps.map((s) => s.id));

    // Check for duplicate step IDs
    if (stepIds.size !== workflow.steps.length) {
      throw new Error('Workflow has duplicate step IDs');
    }

    // Check dependencies exist
    workflow.steps.forEach((step) => {
      if (step.dependsOn) {
        step.dependsOn.forEach((depId) => {
          if (!stepIds.has(depId)) {
            throw new Error(
              `Step ${step.id} depends on non-existent step: ${depId}`
            );
          }
        });
      }
    });

    // Check for circular dependencies
    this.detectCircularDependencies(workflow.steps);
  }

  /**
   * Detect circular dependencies
   */
  private detectCircularDependencies(steps: WorkflowStep[]): void {
    const graph = this.buildDependencyGraph(steps);
    const visited = new Set<string>();
    const recursionStack = new Set<string>();

    const hasCycle = (stepId: string): boolean => {
      visited.add(stepId);
      recursionStack.add(stepId);

      const deps = graph.get(stepId) || new Set();
      for (const depId of deps) {
        if (!visited.has(depId)) {
          if (hasCycle(depId)) return true;
        } else if (recursionStack.has(depId)) {
          return true;
        }
      }

      recursionStack.delete(stepId);
      return false;
    };

    for (const step of steps) {
      if (!visited.has(step.id)) {
        if (hasCycle(step.id)) {
          throw new Error('Workflow has circular dependencies');
        }
      }
    }
  }

  /**
   * Calculate agent score for selection
   */
  private calculateAgentScore(metrics: AgentMetrics): number {
    const successWeight = 0.6;
    const speedWeight = 0.4;

    const successScore = metrics.successRate * 100;

    // Normalize execution time (lower is better)
    // Assume 5000ms is "slow" and 0ms is "fast"
    const speedScore = Math.max(0, 100 - metrics.averageExecutionTime / 50);

    return successScore * successWeight + speedScore * speedWeight;
  }

  /**
   * Get all registered agents
   */
  getAgents(): IAgent[] {
    return Array.from(this._agents.values());
  }

  /**
   * Get agent by ID
   */
  getAgent(agentId: string): IAgent | undefined {
    return this._agents.get(agentId);
  }

  /**
   * Get all registered workflows
   */
  getWorkflows(): Workflow[] {
    return Array.from(this._workflows.values());
  }

  /**
   * Get overall orchestrator metrics
   */
  getMetrics(): {
    totalAgents: number;
    activeWorkflows: number;
    capabilityCoverage: Record<AgentCapability, number>;
  } {
    const capabilityCoverage: Record<string, number> = {};

    this._capabilityIndex.forEach((agents, capability) => {
      capabilityCoverage[capability] = agents.size;
    });

    return {
      totalAgents: this._agents.size,
      activeWorkflows: this._activeWorkflows.size,
      capabilityCoverage: capabilityCoverage as Record<AgentCapability, number>,
    };
  }

  /**
   * Stop all agents
   */
  async stopAll(): Promise<void> {
    const stopPromises = Array.from(this._agents.values()).map((agent) =>
      agent.stop()
    );
    await Promise.all(stopPromises);
  }
}
