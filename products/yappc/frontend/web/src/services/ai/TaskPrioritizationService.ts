/**
 * Task Prioritization Service
 *
 * Provides AI-powered task prioritization based on multiple factors:
 * - Deadline urgency
 * - Task dependencies
 * - User context and preferences
 * - Project criticality
 * - Resource availability
 *
 * @doc.type service
 * @doc.purpose AI-powered task prioritization
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export interface Task {
  id: string;
  title: string;
  description?: string;
  type: 'Design' | 'Code' | 'Deploy' | 'Review' | 'Test';
  priority: 'High' | 'Urgent' | 'Medium' | 'Low';
  status: 'pending' | 'in-progress' | 'blocked' | 'completed' | 'skipped';
  dueDate?: string;
  dependencies?: string[]; // Task IDs this task depends on
  assignee?: string;
  project: string;
  projectId: string;
  estimatedEffort?: number; // in hours
  actualEffort?: number; // in hours
  tags?: string[];
  metadata?: Record<string, unknown>;
}

export interface PrioritizationFactors {
  deadlineUrgency: number; // 0-1
  dependencyDepth: number; // 0-1
  projectCriticality: number; // 0-1
  userCapacity: number; // 0-1
  taskComplexity: number; // 0-1
  riskLevel: number; // 0-1
}

export interface PrioritizedTask extends Task {
  score: number; // 0-100
  factors: PrioritizationFactors;
  recommendedPriority: 'High' | 'Urgent' | 'Medium' | 'Low';
  reasoning: string;
}

export interface PrioritizationRequest {
  tasks: Task[];
  userId?: string;
  context?: {
    currentProject?: string;
    availableHours?: number;
    upcomingDeadlines?: string[];
    teamCapacity?: number;
  };
}

export interface PrioritizationResponse {
  prioritizedTasks: PrioritizedTask[];
  metadata: {
    timestamp: string;
    algorithm: 'ai-hybrid';
    factors: string[];
  };
}

// ============================================================================
// Prioritization Algorithms
// ============================================================================

/**
 * Calculate deadline urgency (0-1)
 */
function calculateDeadlineUrgency(task: Task, upcomingDeadlines?: string[]): number {
  if (!task.dueDate) return 0.3; // Default medium urgency for tasks without deadlines

  const now = new Date();
  const due = new Date(task.dueDate);
  const daysUntilDue = (due.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);

  // Overdue
  if (daysUntilDue < 0) return 1.0;

  // Due within 1 day
  if (daysUntilDue <= 1) return 0.9;

  // Due within 3 days
  if (daysUntilDue <= 3) return 0.7;

  // Due within 1 week
  if (daysUntilDue <= 7) return 0.5;

  // Due within 2 weeks
  if (daysUntilDue <= 14) return 0.3;

  // More than 2 weeks
  return 0.1;
}

/**
 * Calculate dependency depth (0-1)
 * Higher means more dependencies (more critical to unblock)
 */
function calculateDependencyDepth(task: Task, allTasks: Task[]): number {
  if (!task.dependencies || task.dependencies.length === 0) return 0;

  // Count how many other tasks depend on this task
  const dependents = allTasks.filter(t => t.dependencies?.includes(task.id)).length;
  const maxDependents = Math.max(allTasks.length, 1);

  // Normalize to 0-1
  return Math.min(dependents / maxDependents, 1.0);
}

/**
 * Calculate project criticality (0-1)
 * Mock implementation - in production, use actual project data
 */
function calculateProjectCriticality(task: Task): number {
  // In production, this would be based on project tier, stakeholder importance, etc.
  // For now, use a simple heuristic based on task type
  const criticalityMap: Record<Task['type'], number> = {
    Deploy: 0.9,
    Code: 0.7,
    Design: 0.5,
    Review: 0.6,
    Test: 0.7,
  };

  return criticalityMap[task.type] || 0.5;
}

/**
 * Calculate user capacity (0-1)
 * Higher means user has more capacity available
 */
function calculateUserCapacity(task: Task, context?: PrioritizationRequest['context']): number {
  if (!context?.availableHours) return 0.5;

  const estimatedEffort = task.estimatedEffort || 4; // Default 4 hours
  const capacityRatio = context.availableHours / estimatedEffort;

  // Normalize to 0-1
  return Math.min(capacityRatio, 1.0);
}

/**
 * Calculate task complexity (0-1)
 * Higher means more complex (should be prioritized or deprioritized based on strategy)
 */
function calculateTaskComplexity(task: Task): number {
  // Simple heuristic based on task type and effort
  const complexityMap: Record<Task['type'], number> = {
    Deploy: 0.7,
    Code: 0.6,
    Design: 0.5,
    Review: 0.3,
    Test: 0.4,
  };

  const baseComplexity = complexityMap[task.type] || 0.5;
  const effortFactor = Math.min((task.estimatedEffort || 4) / 8, 1.0); // Normalize effort

  return (baseComplexity + effortFactor) / 2;
}

/**
 * Calculate risk level (0-1)
 * Higher means higher risk (should be prioritized)
 */
function calculateRiskLevel(task: Task): number {
  let risk = 0.3; // Base risk

  // Increase risk for blocked tasks
  if (task.status === 'blocked') risk += 0.4;

  // Increase risk for overdue tasks
  if (task.dueDate) {
    const now = new Date();
    const due = new Date(task.dueDate);
    if (due < now) risk += 0.3;
  }

  // Increase risk for deploy tasks
  if (task.type === 'Deploy') risk += 0.2;

  return Math.min(risk, 1.0);
}

/**
 * Generate reasoning for prioritization
 */
function generateReasoning(task: PrioritizedTask): string {
  const reasons: string[] = [];

  if (task.factors.deadlineUrgency > 0.7) {
    reasons.push('approaching deadline');
  }
  if (task.factors.dependencyDepth > 0.5) {
    reasons.push('blocks other tasks');
  }
  if (task.factors.projectCriticality > 0.7) {
    reasons.push('high project impact');
  }
  if (task.factors.riskLevel > 0.7) {
    reasons.push('high risk');
  }

  if (reasons.length === 0) {
    return 'Standard priority based on task type and effort';
  }

  return `Prioritized due to: ${reasons.join(', ')}`;
}

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Prioritize tasks using AI-hybrid algorithm
 */
export async function prioritizeTasks(
  request: PrioritizationRequest
): Promise<PrioritizationResponse> {
  const { tasks, context } = request;

  // Calculate factors for each task
  const prioritizedTasks: PrioritizedTask[] = tasks.map(task => {
    const deadlineUrgency = calculateDeadlineUrgency(task, context?.upcomingDeadlines);
    const dependencyDepth = calculateDependencyDepth(task, tasks);
    const projectCriticality = calculateProjectCriticality(task);
    const userCapacity = calculateUserCapacity(task, context);
    const taskComplexity = calculateTaskComplexity(task);
    const riskLevel = calculateRiskLevel(task);

    const factors: PrioritizationFactors = {
      deadlineUrgency,
      dependencyDepth,
      projectCriticality,
      userCapacity,
      taskComplexity,
      riskLevel,
    };

    // Calculate weighted score
    // Weights can be tuned based on business priorities
    const score =
      deadlineUrgency * 0.25 +
      dependencyDepth * 0.20 +
      projectCriticality * 0.20 +
      userCapacity * 0.10 +
      taskComplexity * 0.10 +
      riskLevel * 0.15;

    // Determine recommended priority
    let recommendedPriority: 'High' | 'Urgent' | 'Medium' | 'Low';
    if (score >= 0.8) recommendedPriority = 'Urgent';
    else if (score >= 0.6) recommendedPriority = 'High';
    else if (score >= 0.4) recommendedPriority = 'Medium';
    else recommendedPriority = 'Low';

    const reasoning = generateReasoning({ ...task, score, factors, recommendedPriority, reasoning: '' });

    return {
      ...task,
      score: score * 100,
      factors,
      recommendedPriority,
      reasoning,
    };
  });

  // Sort by score (highest first)
  prioritizedTasks.sort((a, b) => b.score - a.score);

  return {
    prioritizedTasks,
    metadata: {
      timestamp: new Date().toISOString(),
      algorithm: 'ai-hybrid',
      factors: [
        'deadlineUrgency',
        'dependencyDepth',
        'projectCriticality',
        'userCapacity',
        'taskComplexity',
        'riskLevel',
      ],
    },
  };
}

/**
 * Get task dependencies graph
 */
export function getTaskDependencies(tasks: Task[]): Map<string, string[]> {
  const graph = new Map<string, string[]>();

  tasks.forEach(task => {
    const dependencies = task.dependencies || [];
    graph.set(task.id, dependencies);
  });

  return graph;
}

/**
 * Get tasks that depend on a given task
 */
export function getTaskDependents(taskId: string, tasks: Task[]): Task[] {
  return tasks.filter(task => task.dependencies?.includes(taskId));
}

/**
 * Check if a task can be started (all dependencies completed)
 */
export function canStartTask(task: Task, tasks: Task[]): boolean {
  if (!task.dependencies || task.dependencies.length === 0) return true;

  const dependencyTasks = tasks.filter(t => task.dependencies?.includes(t.id));
  return dependencyTasks.every(t => t.status === 'completed');
}
