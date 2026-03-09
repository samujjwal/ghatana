/**
 * Agent Types and Interfaces
 *
 * Core types for the agent system including agent lifecycle, tasks, and results.
 */

import type { IAIService } from '../core/index.js';

/**
 * Agent status lifecycle states
 */
export type AgentStatus =
  | 'idle' // Agent is waiting for tasks
  | 'running' // Agent is executing a task
  | 'paused' // Agent execution is paused
  | 'completed' // Agent completed all tasks
  | 'failed' // Agent encountered an error
  | 'cancelled'; // Agent was cancelled

/**
 * Task priority levels
 */
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical';

/**
 * Task status
 */
export type TaskStatus =
  | 'pending' // Task is queued
  | 'running' // Task is being executed
  | 'completed' // Task completed successfully
  | 'failed' // Task failed
  | 'cancelled' // Task was cancelled
  | 'retrying'; // Task is being retried

/**
 * Agent capability types
 */
export type AgentCapability =
  | 'intent-analysis' // Intent & Problem Framing
  | 'stakeholder-management' // Stakeholder & User Understanding
  | 'context-synthesis' // Context Gathering & Synthesis
  | 'requirements-engineering' // Requirements Engineering
  | 'research-analysis' // Research & Discovery
  | 'experimentation' // Experimentation & Prototyping
  | 'ux-design' // Product & Experience Design
  | 'visual-design' // Visual & Brand Design
  | 'architecture-design' // System & Architecture Design
  | 'project-planning' // Task Decomposition & Estimation
  | 'program-management' // Dependency & Program Coordination
  | 'change-management' // Change & Release Planning
  | 'software-development' // Software Development
  | 'data-engineering' // Data & ML Development
  | 'ml-engineering' // Data & ML Development
  | 'technical-writing' // Content & Documentation Production
  | 'quality-assurance' // Testing & Quality Assurance
  | 'security-engineering' // Security Engineering
  | 'compliance-management' // Privacy, Risk & Compliance
  | 'devops-engineering' // Build, CI/CD & Deployment
  | 'operations-management' // Operations & Monitoring
  | 'incident-response' // Incident Response & Recovery
  | 'customer-support' // Customer & Internal Support
  | 'observability' // Observability & Analytics
  | 'performance-engineering' // Performance & Optimization
  | 'knowledge-management' // Knowledge Management
  | 'innovation-management' // Continuous Improvement
  | 'design-review' // Legacy
  | 'code-review' // Legacy
  | 'code-generation' // Legacy
  | 'refactoring' // Legacy
  | 'testing' // Legacy
  | 'documentation' // Legacy
  | 'accessibility' // Legacy
  | 'performance' // Legacy
  | 'security' // Legacy
  | 'styling'; // Legacy

/**
 * Base task interface
 */
export interface AgentTask<TInput = unknown, TOutput = unknown> {
  id: string;
  type: string;
  priority: TaskPriority;
  status: TaskStatus;
  input: TInput;
  output?: TOutput;
  error?: Error;
  createdAt: Date;
  startedAt?: Date;
  completedAt?: Date;
  retryCount?: number;
  maxRetries?: number;
  metadata?: Record<string, unknown>;
}

/**
 * Task result with confidence and suggestions
 */
export interface TaskResult<TOutput = unknown> {
  success: boolean;
  output?: TOutput;
  confidence: number; // 0-1
  suggestions?: string[];
  warnings?: string[];
  errors?: string[];
  metadata?: Record<string, unknown>;
}

/**
 * Agent configuration
 */
export interface AgentConfig {
  id: string;
  name: string;
  description: string;
  capabilities: AgentCapability[];
  aiService?: IAIService;
  maxConcurrentTasks?: number;
  taskTimeout?: number; // milliseconds
  retryStrategy?: {
    maxRetries: number;
    backoffMs: number;
    exponential: boolean;
  };
  metadata?: Record<string, unknown>;
}

/**
 * Agent event types
 */
export type AgentEventType =
  | 'status-changed'
  | 'task-queued'
  | 'task-started'
  | 'task-progress'
  | 'task-completed'
  | 'task-failed'
  | 'task-cancelled'
  | 'error';

/**
 * Agent event data
 */
export interface AgentEvent<TData = unknown> {
  type: AgentEventType;
  agentId: string;
  timestamp: Date;
  data: TData;
}

/**
 * Agent event listener
 */
export type AgentEventListener<TData = unknown> = (
  event: AgentEvent<TData>
) => void;

/**
 * Agent metrics for monitoring
 */
export interface AgentMetrics {
  tasksQueued: number;
  tasksRunning: number;
  tasksCompleted: number;
  tasksFailed: number;
  tasksCancelled: number;
  averageExecutionTime: number;
  successRate: number;
  lastActivityAt?: Date;
}

/**
 * Base agent interface that all agents must implement
 */
export interface IAgent<TInput = unknown, TOutput = unknown> {
  readonly id: string;
  readonly name: string;
  readonly status: AgentStatus;
  readonly capabilities: AgentCapability[];
  readonly metrics: AgentMetrics;

  /**
   * Initialize the agent
   */
  initialize(): Promise<void>;

  /**
   * Execute a task
   */
  execute(input: TInput): Promise<TaskResult<TOutput>>;

  /**
   * Queue a task for later execution
   */
  queueTask(task: AgentTask<TInput, TOutput>): Promise<string>;

  /**
   * Get task status
   */
  getTask(taskId: string): AgentTask<TInput, TOutput> | undefined;

  /**
   * Cancel a task
   */
  cancelTask(taskId: string): Promise<boolean>;

  /**
   * Pause agent execution
   */
  pause(): Promise<void>;

  /**
   * Resume agent execution
   */
  resume(): Promise<void>;

  /**
   * Stop agent and cleanup
   */
  stop(): Promise<void>;

  /**
   * Subscribe to agent events
   */
  on<TData = unknown>(
    event: AgentEventType,
    listener: AgentEventListener<TData>
  ): void;

  /**
   * Unsubscribe from agent events
   */
  off<TData = unknown>(
    event: AgentEventType,
    listener: AgentEventListener<TData>
  ): void;
}
