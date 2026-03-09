/**
 * Base Agent Implementation
 *
 * Abstract base class that provides common agent functionality including:
 * - Task queue management
 * - Event system
 * - Retry logic
 * - Metrics tracking
 * - Lifecycle management
 */

import type {
  IAgent,
  AgentConfig,
  AgentStatus,
  AgentTask,
  TaskResult,
  AgentEventType,
  AgentEventListener,
  AgentEvent,
  AgentMetrics,
  AgentCapability,
  TaskPriority,
} from './types';
import type { IAIService } from '../../core/index.js';

/**
 * Abstract base agent class
 *
 * Provides common functionality for all agent types.
 * Subclasses must implement the `executeTask` method.
 */
export abstract class BaseAgent<TInput = unknown, TOutput = unknown>
  implements IAgent<TInput, TOutput> {
  public readonly id: string;
  public readonly name: string;
  public readonly capabilities: AgentCapability[];

  protected _status: AgentStatus = 'idle';
  protected _tasks: Map<string, AgentTask<TInput, TOutput>> = new Map();
  protected _eventListeners: Map<AgentEventType, Set<AgentEventListener>> =
    new Map();
  protected _aiService?: IAIService;
  protected _config: AgentConfig;
  protected _metrics: AgentMetrics = {
    tasksQueued: 0,
    tasksRunning: 0,
    tasksCompleted: 0,
    tasksFailed: 0,
    tasksCancelled: 0,
    averageExecutionTime: 0,
    successRate: 0,
  };

  private _executionQueue: string[] = [];
  private _isProcessingQueue = false;
  private _executionTimes: number[] = [];

  /**
   *
   */
  constructor(config: AgentConfig) {
    this.id = config.id;
    this.name = config.name;
    this.capabilities = config.capabilities;
    this._config = config;
    this._aiService = config.aiService;
  }

  /**
   * Get current agent status
   */
  get status(): AgentStatus {
    return this._status;
  }

  /**
   * Get agent metrics
   */
  get metrics(): AgentMetrics {
    return { ...this._metrics };
  }

  /**
   * Initialize the agent
   */
  async initialize(): Promise<void> {
    this._status = 'idle';
    this.emit('status-changed', { status: this._status });
  }

  /**
   * Execute a task immediately
   */
  async execute(input: TInput): Promise<TaskResult<TOutput>> {
    const task: AgentTask<TInput, TOutput> = {
      id: this.generateTaskId(),
      type: 'immediate',
      priority: 'high',
      status: 'pending',
      input,
      createdAt: new Date(),
      maxRetries: this._config.retryStrategy?.maxRetries ?? 3,
      retryCount: 0,
    };

    return this.executeTaskWithRetry(task);
  }

  /**
   * Queue a task for later execution
   */
  async queueTask(task: AgentTask<TInput, TOutput>): Promise<string> {
    this._tasks.set(task.id, task);
    this._executionQueue.push(task.id);
    this._metrics.tasksQueued++;

    this.emit('task-queued', { task });
    this.processQueue();

    return task.id;
  }

  /**
   * Get task by ID
   */
  getTask(taskId: string): AgentTask<TInput, TOutput> | undefined {
    return this._tasks.get(taskId);
  }

  /**
   * Cancel a task
   */
  async cancelTask(taskId: string): Promise<boolean> {
    const task = this._tasks.get(taskId);
    if (!task) return false;

    if (task.status === 'running') {
      // Can't cancel running tasks immediately
      return false;
    }

    task.status = 'cancelled';
    task.completedAt = new Date();
    this._metrics.tasksCancelled++;

    // Remove from queue
    const queueIndex = this._executionQueue.indexOf(taskId);
    if (queueIndex !== -1) {
      this._executionQueue.splice(queueIndex, 1);
    }

    this.emit('task-cancelled', { task });
    return true;
  }

  /**
   * Pause agent execution
   */
  async pause(): Promise<void> {
    if (this._status !== 'running') return;

    this._status = 'paused';
    this.emit('status-changed', { status: this._status });
  }

  /**
   * Resume agent execution
   */
  async resume(): Promise<void> {
    if (this._status !== 'paused') return;

    this._status = 'idle';
    this.emit('status-changed', { status: this._status });
    this.processQueue();
  }

  /**
   * Stop agent and cleanup
   */
  async stop(): Promise<void> {
    this._status = 'completed';
    this._executionQueue = [];
    this.emit('status-changed', { status: this._status });
  }

  /**
   * Subscribe to agent events
   */
  on<TData = unknown>(
    event: AgentEventType,
    listener: AgentEventListener<TData>
  ): void {
    if (!this._eventListeners.has(event)) {
      this._eventListeners.set(event, new Set());
    }
    this._eventListeners.get(event)!.add(listener as AgentEventListener);
  }

  /**
   * Unsubscribe from agent events
   */
  off<TData = unknown>(
    event: AgentEventType,
    listener: AgentEventListener<TData>
  ): void {
    const listeners = this._eventListeners.get(event);
    if (listeners) {
      listeners.delete(listener as AgentEventListener);
    }
  }

  /**
   * Abstract method that subclasses must implement
   */
  protected abstract executeTask(input: TInput): Promise<TaskResult<TOutput>>;

  /**
   * Execute task with retry logic
   */
  private async executeTaskWithRetry(
    task: AgentTask<TInput, TOutput>
  ): Promise<TaskResult<TOutput>> {
    const startTime = Date.now();
    task.status = 'running';
    task.startedAt = new Date();
    this._metrics.tasksRunning++;

    this.emit('task-started', { task });

    try {
      const result = await this.executeWithTimeout(task);

      task.status = 'completed';
      task.completedAt = new Date();
      task.output = result.output;
      this._metrics.tasksRunning--;
      this._metrics.tasksCompleted++;

      const executionTime = Date.now() - startTime;
      this.updateMetrics(executionTime, true);

      this.emit('task-completed', { task, result });

      return result;
    } catch (error) {
      const shouldRetry = task.retryCount! < (task.maxRetries ?? 3);

      if (shouldRetry) {
        task.status = 'retrying';
        task.retryCount = (task.retryCount ?? 0) + 1;

        // Exponential backoff
        const backoffMs = this._config.retryStrategy?.exponential
          ? (this._config.retryStrategy?.backoffMs ?? 1000) *
          Math.pow(2, task.retryCount)
          : (this._config.retryStrategy?.backoffMs ?? 1000);

        await new Promise((resolve) => setTimeout(resolve, backoffMs));

        return this.executeTaskWithRetry(task);
      }

      task.status = 'failed';
      task.completedAt = new Date();
      task.error = error as Error;
      this._metrics.tasksRunning--;
      this._metrics.tasksFailed++;

      const executionTime = Date.now() - startTime;
      this.updateMetrics(executionTime, false);

      this.emit('task-failed', { task, error });

      return {
        success: false,
        confidence: 0,
        errors: [(error as Error).message],
      };
    }
  }

  /**
   * Execute task with timeout
   */
  private async executeWithTimeout(
    task: AgentTask<TInput, TOutput>
  ): Promise<TaskResult<TOutput>> {
    const timeout = this._config.taskTimeout ?? 60000; // 1 minute default

    return Promise.race([
      this.executeTask(task.input),
      new Promise<TaskResult<TOutput>>((_, reject) =>
        setTimeout(() => reject(new Error('Task execution timeout')), timeout)
      ),
    ]);
  }

  /**
   * Process task queue
   */
  private async processQueue(): Promise<void> {
    if (
      this._isProcessingQueue ||
      this._status === 'paused' ||
      this._status === 'completed'
    ) {
      return;
    }

    this._isProcessingQueue = true;

    while (this._executionQueue.length > 0) {
      if (this._status === 'paused' || this._status === 'completed') {
        break;
      }

      // Check concurrent task limit
      const maxConcurrent = this._config.maxConcurrentTasks ?? 1;
      if (this._metrics.tasksRunning >= maxConcurrent) {
        break;
      }

      // Get next task (priority-based)
      const taskId = this.getNextTask();
      if (!taskId) break;

      const task = this._tasks.get(taskId);
      if (!task || task.status !== 'pending') continue;

      // Execute task in background
      this.executeTaskWithRetry(task).then(() => {
        this.processQueue(); // Continue processing
      });
    }

    this._isProcessingQueue = false;
  }

  /**
   * Get next task from queue based on priority
   */
  private getNextTask(): string | undefined {
    if (this._executionQueue.length === 0) return undefined;

    // Sort by priority: critical > high > medium > low
    const priorityOrder: TaskPriority[] = ['critical', 'high', 'medium', 'low'];

    let bestIndex = 0;
    let bestPriority = 4; // lowest

    for (let i = 0; i < this._executionQueue.length; i++) {
      const taskId = this._executionQueue[i];
      const task = this._tasks.get(taskId);
      if (!task || task.status !== 'pending') continue;

      const priority = priorityOrder.indexOf(task.priority);
      if (priority < bestPriority) {
        bestPriority = priority;
        bestIndex = i;
      }
    }

    const [taskId] = this._executionQueue.splice(bestIndex, 1);
    return taskId;
  }

  /**
   * Update agent metrics
   */
  private updateMetrics(executionTime: number, success: boolean): void {
    this._executionTimes.push(executionTime);

    // Keep only last 100 execution times
    if (this._executionTimes.length > 100) {
      this._executionTimes.shift();
    }

    // Calculate average
    this._metrics.averageExecutionTime =
      this._executionTimes.reduce((sum, time) => sum + time, 0) /
      this._executionTimes.length;

    // Calculate success rate
    const totalCompleted =
      this._metrics.tasksCompleted + this._metrics.tasksFailed;
    this._metrics.successRate =
      totalCompleted > 0 ? this._metrics.tasksCompleted / totalCompleted : 0;

    this._metrics.lastActivityAt = new Date();
  }

  /**
   * Emit an event
   */
  protected emit<TData = unknown>(type: AgentEventType, data: TData): void {
    const event: AgentEvent<TData> = {
      type,
      agentId: this.id,
      timestamp: new Date(),
      data,
    };

    const listeners = this._eventListeners.get(type);
    if (listeners) {
      listeners.forEach((listener) => {
        try {
          listener(event);
        } catch (error) {
          console.error(`Error in event listener for ${type}:`, error);
        }
      });
    }
  }

  /**
   * Generate unique task ID
   */
  private generateTaskId(): string {
    return `${this.id}-task-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}
