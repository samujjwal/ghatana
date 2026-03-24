/**
 * Worker Manager - Web Worker Offloading for Heavy Computation
 * 
 * Provides worker management capabilities:
 * - Layout computation in separate thread
 * - Worker lifecycle management (spawn, terminate, restart)
 * - Message passing with structured cloning and transferables
 * - Error handling and crash recovery
 * - Progress reporting for long-running tasks
 * - Worker pool management for parallel execution
 * 
 * Benefits:
 * - Non-blocking UI during heavy computation
 * - Parallelization of independent tasks
 * - Graceful degradation on worker failure
 * - Efficient memory transfer with transferables
 * 
 * @module workerManager
 */

/**
 * Worker task types
 */
export type WorkerTaskType =
  | 'layout'           // Layout computation (force-directed, hierarchical)
  | 'pathfinding'      // Path calculation for connectors
  | 'clustering'       // Node grouping and clustering
  | 'export'           // Document export (PDF, SVG, PNG)
  | 'validation'       // Schema validation and linting
  | 'analytics';       // Metrics and statistics computation

/**
 * Worker task priority
 */
export type TaskPriority = 'low' | 'normal' | 'high' | 'urgent';

/**
 * Worker task status
 */
export type TaskStatus = 
  | 'pending'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled';

/**
 * Worker task definition
 */
export interface WorkerTask<TInput = unknown, TOutput = unknown> {
  id: string;
  type: WorkerTaskType;
  priority: TaskPriority;
  input: TInput;
  transferables?: Transferable[];  // For zero-copy transfer
  timeout?: number;                 // Task timeout in ms
  retryCount?: number;              // Number of retries on failure
  onProgress?: (progress: TaskProgress) => void;
  onComplete?: (output: TOutput) => void;
  onError?: (error: Error) => void;
}

/**
 * Task progress information
 */
export interface TaskProgress {
  taskId: string;
  progress: number;      // 0-100
  message?: string;
  currentStep?: string;
  totalSteps?: number;
}

/**
 * Worker message types
 */
export type WorkerMessageType =
  | 'task'
  | 'progress'
  | 'result'
  | 'error'
  | 'ping'
  | 'terminate';

/**
 * Message sent to worker
 */
export interface WorkerMessage<T = unknown> {
  type: WorkerMessageType;
  taskId?: string;
  payload?: T;
  transferables?: Transferable[];
}

/**
 * Message received from worker
 */
export interface WorkerResponse<T = unknown> {
  type: WorkerMessageType;
  taskId?: string;
  payload?: T;
  error?: string;
}

/**
 * Worker instance state
 */
export interface WorkerInstance {
  id: string;
  worker: Worker;
  status: 'idle' | 'busy' | 'crashed';
  currentTask?: string;       // Current task ID
  taskCount: number;          // Total tasks processed
  errorCount: number;         // Total errors encountered
  lastActivity: number;       // Timestamp of last activity
  crashCount: number;         // Number of crashes
}

/**
 * Worker pool configuration
 */
export interface WorkerPoolConfig {
  maxWorkers: number;         // Maximum number of workers
  taskQueueSize: number;      // Maximum queued tasks
  workerTimeout: number;      // Worker idle timeout (ms)
  maxRetries: number;         // Max retry attempts per task
  crashThreshold: number;     // Max crashes before permanent failure
}

/**
 * Worker manager state
 */
export interface WorkerManagerState {
  config: WorkerPoolConfig;
  workers: Map<string, WorkerInstance>;
  taskQueue: WorkerTask[];
  activeTasks: Map<string, WorkerTask>;
  completedTasks: Map<string, { result: unknown; timestamp: number }>;
  failedTasks: Map<string, { error: Error; timestamp: number }>;
  statistics: WorkerStatistics;
}

/**
 * Worker statistics
 */
export interface WorkerStatistics {
  totalTasks: number;
  completedTasks: number;
  failedTasks: number;
  cancelledTasks: number;
  averageTaskDuration: number;
  totalWorkerCrashes: number;
  activeWorkers: number;
  idleWorkers: number;
  queuedTasks: number;
}

/**
 * Create default worker pool configuration
 */
export function createWorkerPoolConfig(
  partial: Partial<WorkerPoolConfig> = {}
): WorkerPoolConfig {
  return {
    maxWorkers: partial.maxWorkers ?? navigator.hardwareConcurrency ?? 4,
    taskQueueSize: partial.taskQueueSize ?? 100,
    workerTimeout: partial.workerTimeout ?? 30000,
    maxRetries: partial.maxRetries ?? 3,
    crashThreshold: partial.crashThreshold ?? 5,
  };
}

/**
 * Create initial worker manager state
 */
export function createWorkerManagerState(
  config?: Partial<WorkerPoolConfig>
): WorkerManagerState {
  return {
    config: createWorkerPoolConfig(config),
    workers: new Map(),
    taskQueue: [],
    activeTasks: new Map(),
    completedTasks: new Map(),
    failedTasks: new Map(),
    statistics: {
      totalTasks: 0,
      completedTasks: 0,
      failedTasks: 0,
      cancelledTasks: 0,
      averageTaskDuration: 0,
      totalWorkerCrashes: 0,
      activeWorkers: 0,
      idleWorkers: 0,
      queuedTasks: 0,
    },
  };
}

/**
 * Generate unique task ID
 */
export function generateTaskId(type: WorkerTaskType): string {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 6);
  return `${type}_${timestamp}${random}`;
}

/**
 * Generate unique worker ID
 */
export function generateWorkerId(): string {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 8);
  return `worker_${timestamp}${random}`;
}

/**
 * Spawn a new worker instance
 */
export function spawnWorker(
  state: WorkerManagerState,
  workerScript: string | URL
): { worker: WorkerInstance; state: WorkerManagerState } {
  const workerId = generateWorkerId();
  
  const worker: WorkerInstance = {
    id: workerId,
    worker: new Worker(workerScript, { type: 'module' }),
    status: 'idle',
    taskCount: 0,
    errorCount: 0,
    lastActivity: Date.now(),
    crashCount: 0,
  };
  
  return {
    worker,
    state: {
      ...state,
      workers: new Map([...state.workers, [workerId, worker]]),
      statistics: {
        ...state.statistics,
        activeWorkers: state.workers.size + 1,
      },
    },
  };
}

/**
 * Terminate a worker instance
 */
export function terminateWorker(
  state: WorkerManagerState,
  workerId: string
): WorkerManagerState {
  const worker = state.workers.get(workerId);
  
  if (!worker) {
    return state;
  }
  
  // Terminate the worker
  worker.worker.terminate();
  
  // Remove from workers map
  const newWorkers = new Map(state.workers);
  newWorkers.delete(workerId);
  
  return {
    ...state,
    workers: newWorkers,
    statistics: {
      ...state.statistics,
      activeWorkers: Math.max(0, state.statistics.activeWorkers - 1),
      idleWorkers: Math.max(0, state.statistics.idleWorkers - (worker.status === 'idle' ? 1 : 0)),
    },
  };
}

/**
 * Find an idle worker or spawn a new one if needed
 */
export function getAvailableWorker(
  state: WorkerManagerState,
  workerScript: string | URL
): { worker: WorkerInstance | null; state: WorkerManagerState } {
  // Find idle worker
  for (const [, worker] of state.workers) {
    if (worker.status === 'idle') {
      return { worker, state };
    }
  }
  
  // Spawn new worker if under max limit
  if (state.workers.size < state.config.maxWorkers) {
    return spawnWorker(state, workerScript);
  }
  
  // No workers available
  return { worker: null, state };
}

/**
 * Queue a task for execution
 */
export function queueTask<TInput, TOutput>(
  state: WorkerManagerState,
  task: Omit<WorkerTask<TInput, TOutput>, 'id' | 'priority'> & {
    priority?: TaskPriority;
  }
): { taskId: string; state: WorkerManagerState } {
  const taskId = generateTaskId(task.type);
  
  const fullTask: WorkerTask<TInput, TOutput> = {
    ...task,
    id: taskId,
    priority: task.priority ?? 'normal',
  };
  
  // Check queue size limit
  if (state.taskQueue.length >= state.config.taskQueueSize) {
    throw new Error(`Task queue full (max ${state.config.taskQueueSize})`);
  }
  
  // Insert task based on priority
  const newQueue = [...state.taskQueue];
  const priorityOrder = { urgent: 0, high: 1, normal: 2, low: 3 };
  
  let insertIndex = newQueue.length;
  for (let i = 0; i < newQueue.length; i++) {
    if (priorityOrder[fullTask.priority] < priorityOrder[newQueue[i].priority]) {
      insertIndex = i;
      break;
    }
  }
  
  newQueue.splice(insertIndex, 0, fullTask);
  
  return {
    taskId,
    state: {
      ...state,
      taskQueue: newQueue,
      statistics: {
        ...state.statistics,
        totalTasks: state.statistics.totalTasks + 1,
        queuedTasks: newQueue.length,
      },
    },
  };
}

/**
 * Assign task to worker
 */
export function assignTaskToWorker(
  state: WorkerManagerState,
  workerId: string,
  taskId: string
): WorkerManagerState {
  const worker = state.workers.get(workerId);
  const taskIndex = state.taskQueue.findIndex(t => t.id === taskId);
  
  if (!worker || taskIndex === -1) {
    return state;
  }
  
  const task = state.taskQueue[taskIndex];
  
  // Update worker state
  const updatedWorker: WorkerInstance = {
    ...worker,
    status: 'busy',
    currentTask: taskId,
    lastActivity: Date.now(),
  };
  
  // Remove task from queue and add to active
  const newQueue = [...state.taskQueue];
  newQueue.splice(taskIndex, 1);
  
  const newActiveTasks = new Map(state.activeTasks);
  newActiveTasks.set(taskId, task);
  
  const newWorkers = new Map(state.workers);
  newWorkers.set(workerId, updatedWorker);
  
  return {
    ...state,
    workers: newWorkers,
    taskQueue: newQueue,
    activeTasks: newActiveTasks,
    statistics: {
      ...state.statistics,
      queuedTasks: newQueue.length,
      idleWorkers: Math.max(0, state.statistics.idleWorkers - 1),
    },
  };
}

/**
 * Mark task as completed
 */
export function completeTask(
  state: WorkerManagerState,
  taskId: string,
  result: unknown
): WorkerManagerState {
  const task = state.activeTasks.get(taskId);
  
  if (!task) {
    return state;
  }
  
  // Find and update worker
  let updatedState = state;
  for (const [workerId, worker] of state.workers) {
    if (worker.currentTask === taskId) {
      const updatedWorker: WorkerInstance = {
        ...worker,
        status: 'idle',
        currentTask: undefined,
        taskCount: worker.taskCount + 1,
        lastActivity: Date.now(),
      };
      
      const newWorkers = new Map(state.workers);
      newWorkers.set(workerId, updatedWorker);
      
      updatedState = {
        ...state,
        workers: newWorkers,
        statistics: {
          ...state.statistics,
          idleWorkers: state.statistics.idleWorkers + 1,
        },
      };
      break;
    }
  }
  
  // Remove from active tasks and add to completed
  const newActiveTasks = new Map(updatedState.activeTasks);
  newActiveTasks.delete(taskId);
  
  const newCompletedTasks = new Map(updatedState.completedTasks);
  newCompletedTasks.set(taskId, { result, timestamp: Date.now() });
  
  return {
    ...updatedState,
    activeTasks: newActiveTasks,
    completedTasks: newCompletedTasks,
    statistics: {
      ...updatedState.statistics,
      completedTasks: updatedState.statistics.completedTasks + 1,
    },
  };
}

/**
 * Mark task as failed
 */
export function failTask(
  state: WorkerManagerState,
  taskId: string,
  error: Error
): WorkerManagerState {
  const task = state.activeTasks.get(taskId);
  
  if (!task) {
    return state;
  }
  
  // Find and update worker
  let updatedState = state;
  for (const [workerId, worker] of state.workers) {
    if (worker.currentTask === taskId) {
      const updatedWorker: WorkerInstance = {
        ...worker,
        status: 'idle',
        currentTask: undefined,
        errorCount: worker.errorCount + 1,
        lastActivity: Date.now(),
      };
      
      const newWorkers = new Map(state.workers);
      newWorkers.set(workerId, updatedWorker);
      
      updatedState = {
        ...state,
        workers: newWorkers,
        statistics: {
          ...state.statistics,
          idleWorkers: state.statistics.idleWorkers + 1,
        },
      };
      break;
    }
  }
  
  // Remove from active tasks and add to failed
  const newActiveTasks = new Map(updatedState.activeTasks);
  newActiveTasks.delete(taskId);
  
  const newFailedTasks = new Map(updatedState.failedTasks);
  newFailedTasks.set(taskId, { error, timestamp: Date.now() });
  
  return {
    ...updatedState,
    activeTasks: newActiveTasks,
    failedTasks: newFailedTasks,
    statistics: {
      ...updatedState.statistics,
      failedTasks: updatedState.statistics.failedTasks + 1,
    },
  };
}

/**
 * Handle worker crash
 */
export function handleWorkerCrash(
  state: WorkerManagerState,
  workerId: string
): WorkerManagerState {
  const worker = state.workers.get(workerId);
  
  if (!worker) {
    return state;
  }
  
  const crashCount = worker.crashCount + 1;
  
  // Mark worker as crashed
  const updatedWorker: WorkerInstance = {
    ...worker,
    status: 'crashed',
    crashCount,
  };
  
  const newWorkers = new Map(state.workers);
  newWorkers.set(workerId, updatedWorker);
  
  let updatedState: WorkerManagerState = {
    ...state,
    workers: newWorkers,
    statistics: {
      ...state.statistics,
      totalWorkerCrashes: state.statistics.totalWorkerCrashes + 1,
    },
  };
  
  // If task was running, fail it
  if (worker.currentTask) {
    updatedState = failTask(
      updatedState,
      worker.currentTask,
      new Error(`Worker ${workerId} crashed`)
    );
    
    // Restore crashed status (failTask sets it to idle)
    const updatedWorkerFromFail = updatedState.workers.get(workerId);
    if (updatedWorkerFromFail) {
      const crashedWorker: WorkerInstance = {
        ...updatedWorkerFromFail,
        status: 'crashed',
        crashCount,
      };
      const newWorkers = new Map(updatedState.workers);
      newWorkers.set(workerId, crashedWorker);
      updatedState = {
        ...updatedState,
        workers: newWorkers,
      };
    }
  }
  
  // If crash threshold exceeded, terminate worker
  if (crashCount >= state.config.crashThreshold) {
    updatedState = terminateWorker(updatedState, workerId);
  }
  
  return updatedState;
}

/**
 * Cancel a task
 */
export function cancelTask(
  state: WorkerManagerState,
  taskId: string
): WorkerManagerState {
  // Check if task is in queue
  const queueIndex = state.taskQueue.findIndex(t => t.id === taskId);
  
  if (queueIndex !== -1) {
    const newQueue = [...state.taskQueue];
    newQueue.splice(queueIndex, 1);
    
    return {
      ...state,
      taskQueue: newQueue,
      statistics: {
        ...state.statistics,
        cancelledTasks: state.statistics.cancelledTasks + 1,
        queuedTasks: newQueue.length,
      },
    };
  }
  
  // Check if task is active
  if (state.activeTasks.has(taskId)) {
    // Find worker running the task
    for (const [workerId, worker] of state.workers) {
      if (worker.currentTask === taskId) {
        // Send terminate message to worker
        worker.worker.postMessage({
          type: 'terminate',
          taskId,
        } as WorkerMessage);
        
        // Mark worker as idle
        const updatedWorker: WorkerInstance = {
          ...worker,
          status: 'idle',
          currentTask: undefined,
          lastActivity: Date.now(),
        };
        
        const newWorkers = new Map(state.workers);
        newWorkers.set(workerId, updatedWorker);
        
        const newActiveTasks = new Map(state.activeTasks);
        newActiveTasks.delete(taskId);
        
        return {
          ...state,
          workers: newWorkers,
          activeTasks: newActiveTasks,
          statistics: {
            ...state.statistics,
            cancelledTasks: state.statistics.cancelledTasks + 1,
            idleWorkers: state.statistics.idleWorkers + 1,
          },
        };
      }
    }
  }
  
  return state;
}

/**
 * Get task status
 */
export function getTaskStatus(
  state: WorkerManagerState,
  taskId: string
): TaskStatus | null {
  if (state.taskQueue.some(t => t.id === taskId)) {
    return 'pending';
  }
  
  if (state.activeTasks.has(taskId)) {
    return 'running';
  }
  
  if (state.completedTasks.has(taskId)) {
    return 'completed';
  }
  
  if (state.failedTasks.has(taskId)) {
    return 'failed';
  }
  
  return null;
}

/**
 * Get task result
 */
export function getTaskResult<T = unknown>(
  state: WorkerManagerState,
  taskId: string
): T | null {
  const completed = state.completedTasks.get(taskId);
  return completed ? completed.result : null;
}

/**
 * Get task error
 */
export function getTaskError(
  state: WorkerManagerState,
  taskId: string
): Error | null {
  const failed = state.failedTasks.get(taskId);
  return failed ? failed.error : null;
}

/**
 * Get worker statistics
 */
export function getWorkerStatistics(
  state: WorkerManagerState
): WorkerStatistics {
  return {
    ...state.statistics,
    activeWorkers: Array.from(state.workers.values()).filter(w => w.status !== 'crashed').length,
    idleWorkers: Array.from(state.workers.values()).filter(w => w.status === 'idle').length,
    queuedTasks: state.taskQueue.length,
  };
}

/**
 * Cleanup old completed/failed tasks
 */
export function cleanupTasks(
  state: WorkerManagerState,
  maxAge: number = 3600000 // 1 hour
): WorkerManagerState {
  const now = Date.now();
  
  const newCompletedTasks = new Map(state.completedTasks);
  for (const [taskId, { timestamp }] of newCompletedTasks) {
    if (now - timestamp > maxAge) {
      newCompletedTasks.delete(taskId);
    }
  }
  
  const newFailedTasks = new Map(state.failedTasks);
  for (const [taskId, { timestamp }] of newFailedTasks) {
    if (now - timestamp > maxAge) {
      newFailedTasks.delete(taskId);
    }
  }
  
  return {
    ...state,
    completedTasks: newCompletedTasks,
    failedTasks: newFailedTasks,
  };
}

/**
 * Terminate all workers
 */
export function terminateAllWorkers(
  state: WorkerManagerState
): WorkerManagerState {
  // Terminate all workers
  for (const worker of state.workers.values()) {
    worker.worker.terminate();
  }
  
  return {
    ...state,
    workers: new Map(),
    statistics: {
      ...state.statistics,
      activeWorkers: 0,
      idleWorkers: 0,
    },
  };
}

/**
 * Check if worker manager supports workers
 */
export function supportsWorkers(): boolean {
  return typeof Worker !== 'undefined';
}

/**
 * Create transferable for large data
 */
export function createTransferable(data: ArrayBuffer | MessagePort | ImageBitmap): Transferable {
  return data as Transferable;
}

/**
 * Get worker health status
 */
export function getWorkerHealth(worker: WorkerInstance): {
  healthy: boolean;
  reason?: string;
} {
  if (worker.status === 'crashed') {
    return { healthy: false, reason: 'Worker has crashed' };
  }
  
  if (worker.crashCount >= 3) {
    return { healthy: false, reason: `High crash count (${worker.crashCount})` };
  }
  
  const timeSinceActivity = Date.now() - worker.lastActivity;
  if (timeSinceActivity > 60000 && worker.status === 'busy') {
    return { healthy: false, reason: 'Worker appears stuck (no activity for 60s)' };
  }
  
  return { healthy: true };
}
