import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  createWorkerPoolConfig,
  createWorkerManagerState,
  generateTaskId,
  generateWorkerId,
  spawnWorker,
  terminateWorker,
  getAvailableWorker,
  queueTask,
  assignTaskToWorker,
  completeTask,
  failTask,
  handleWorkerCrash,
  cancelTask,
  getTaskStatus,
  getTaskResult,
  getTaskError,
  getWorkerStatistics,
  cleanupTasks,
  terminateAllWorkers,
  supportsWorkers,
  getWorkerHealth,
  type WorkerManagerState,
  type WorkerInstance,
  type WorkerTask,
} from '../workerManager';

// Mock Worker API
class MockWorker {
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: ((event: ErrorEvent) => void) | null = null;
  
  postMessage(message: unknown, transfer?: Transferable[]) {
    // Mock implementation
  }
  
  terminate() {
    // Mock implementation
  }
}

global.Worker = MockWorker as unknown;
global.navigator = { hardwareConcurrency: 4 } as unknown;

describe('workerManager', () => {
  describe('Configuration', () => {
    it('should create default worker pool config', () => {
      const config = createWorkerPoolConfig();
      
      expect(config.maxWorkers).toBe(4);
      expect(config.taskQueueSize).toBe(100);
      expect(config.workerTimeout).toBe(30000);
      expect(config.maxRetries).toBe(3);
      expect(config.crashThreshold).toBe(5);
    });

    it('should create config with custom values', () => {
      const config = createWorkerPoolConfig({
        maxWorkers: 8,
        taskQueueSize: 50,
        workerTimeout: 60000,
      });
      
      expect(config.maxWorkers).toBe(8);
      expect(config.taskQueueSize).toBe(50);
      expect(config.workerTimeout).toBe(60000);
      expect(config.maxRetries).toBe(3); // default
    });

    it('should use navigator.hardwareConcurrency', () => {
      const originalNav = global.navigator;
      global.navigator = { hardwareConcurrency: 8 } as unknown;
      
      const config = createWorkerPoolConfig();
      expect(config.maxWorkers).toBe(8);
      
      global.navigator = originalNav;
    });
  });

  describe('State Initialization', () => {
    it('should create initial state', () => {
      const state = createWorkerManagerState();
      
      expect(state.workers.size).toBe(0);
      expect(state.taskQueue).toEqual([]);
      expect(state.activeTasks.size).toBe(0);
      expect(state.completedTasks.size).toBe(0);
      expect(state.failedTasks.size).toBe(0);
      expect(state.statistics.totalTasks).toBe(0);
    });

    it('should create state with custom config', () => {
      const state = createWorkerManagerState({ maxWorkers: 2 });
      
      expect(state.config.maxWorkers).toBe(2);
    });
  });

  describe('ID Generation', () => {
    it('should generate unique task IDs', () => {
      const id1 = generateTaskId('layout');
      const id2 = generateTaskId('layout');
      
      expect(id1).toMatch(/^layout_/);
      expect(id2).toMatch(/^layout_/);
      expect(id1).not.toBe(id2);
    });

    it('should include task type in ID', () => {
      const id = generateTaskId('pathfinding');
      expect(id).toMatch(/^pathfinding_/);
    });

    it('should generate unique worker IDs', () => {
      const id1 = generateWorkerId();
      const id2 = generateWorkerId();
      
      expect(id1).toMatch(/^worker_/);
      expect(id2).toMatch(/^worker_/);
      expect(id1).not.toBe(id2);
    });
  });

  describe('Worker Lifecycle', () => {
    it('should spawn a new worker', () => {
      const state = createWorkerManagerState();
      const result = spawnWorker(state, 'worker.js');
      
      expect(result.worker.id).toMatch(/^worker_/);
      expect(result.worker.status).toBe('idle');
      expect(result.worker.taskCount).toBe(0);
      expect(result.worker.errorCount).toBe(0);
      expect(result.worker.crashCount).toBe(0);
      expect(result.state.workers.size).toBe(1);
      expect(result.state.statistics.activeWorkers).toBe(1);
    });

    it('should spawn multiple workers', () => {
      let state = createWorkerManagerState();
      
      const result1 = spawnWorker(state, 'worker.js');
      state = result1.state;
      
      const result2 = spawnWorker(state, 'worker.js');
      state = result2.state;
      
      expect(state.workers.size).toBe(2);
      expect(result1.worker.id).not.toBe(result2.worker.id);
    });

    it('should terminate a worker', () => {
      let state = createWorkerManagerState();
      const { worker, state: newState } = spawnWorker(state, 'worker.js');
      state = newState;
      
      const terminateSpy = vi.spyOn(worker.worker, 'terminate');
      state = terminateWorker(state, worker.id);
      
      expect(terminateSpy).toHaveBeenCalled();
      expect(state.workers.has(worker.id)).toBe(false);
      expect(state.statistics.activeWorkers).toBe(0);
    });

    it('should not fail when terminating non-existent worker', () => {
      const state = createWorkerManagerState();
      const newState = terminateWorker(state, 'nonexistent');
      
      expect(newState).toBe(state);
    });

    it('should terminate all workers', () => {
      const state = createWorkerManagerState();
      
      const { worker: w1, state: s1 } = spawnWorker(state, 'worker.js');
      const { worker: w2, state: s2 } = spawnWorker(s1, 'worker.js');
      
      const spy1 = vi.spyOn(w1.worker, 'terminate');
      const spy2 = vi.spyOn(w2.worker, 'terminate');
      
      const finalState = terminateAllWorkers(s2);
      
      expect(spy1).toHaveBeenCalled();
      expect(spy2).toHaveBeenCalled();
      expect(finalState.workers.size).toBe(0);
      expect(finalState.statistics.activeWorkers).toBe(0);
    });
  });

  describe('Worker Selection', () => {
    it('should return idle worker if available', () => {
      let state = createWorkerManagerState();
      const { worker, state: newState } = spawnWorker(state, 'worker.js');
      state = newState;
      
      const result = getAvailableWorker(state, 'worker.js');
      
      expect(result.worker?.id).toBe(worker.id);
      expect(result.state).toBe(state);
    });

    it('should spawn new worker if none are idle', () => {
      const state = createWorkerManagerState({ maxWorkers: 2 });
      const { worker: w1, state: s1 } = spawnWorker(state, 'worker.js');
      
      // Mark worker as busy
      const busyWorker: WorkerInstance = { ...w1, status: 'busy' };
      const stateWithBusy = {
        ...s1,
        workers: new Map([[w1.id, busyWorker]]),
      };
      
      const result = getAvailableWorker(stateWithBusy, 'worker.js');
      
      expect(result.worker?.id).not.toBe(w1.id);
      expect(result.state.workers.size).toBe(2);
    });

    it('should return null if max workers reached', () => {
      const state = createWorkerManagerState({ maxWorkers: 1 });
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      
      // Mark worker as busy
      const busyWorker: WorkerInstance = { ...worker, status: 'busy' };
      const stateWithBusy = {
        ...s1,
        workers: new Map([[worker.id, busyWorker]]),
      };
      
      const result = getAvailableWorker(stateWithBusy, 'worker.js');
      
      expect(result.worker).toBeNull();
    });
  });

  describe('Task Queuing', () => {
    it('should queue a task', () => {
      const state = createWorkerManagerState();
      
      const result = queueTask(state, {
        type: 'layout',
        input: { nodes: [], edges: [] },
      });
      
      expect(result.taskId).toMatch(/^layout_/);
      expect(result.state.taskQueue.length).toBe(1);
      expect(result.state.taskQueue[0].id).toBe(result.taskId);
      expect(result.state.taskQueue[0].priority).toBe('normal');
      expect(result.state.statistics.totalTasks).toBe(1);
      expect(result.state.statistics.queuedTasks).toBe(1);
    });

    it('should queue tasks with priority', () => {
      const state = createWorkerManagerState();
      
      const { taskId: t1, state: s1 } = queueTask(state, {
        type: 'layout',
        input: {},
        priority: 'low',
      });
      
      const { taskId: t2, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
        priority: 'urgent',
      });
      
      const { taskId: t3, state: s3 } = queueTask(s2, {
        type: 'layout',
        input: {},
        priority: 'normal',
      });
      
      // Should be ordered: urgent, normal, low
      expect(s3.taskQueue[0].id).toBe(t2);
      expect(s3.taskQueue[1].id).toBe(t3);
      expect(s3.taskQueue[2].id).toBe(t1);
    });

    it('should throw error when queue is full', () => {
      const state = createWorkerManagerState({ taskQueueSize: 1 });
      
      const { state: s1 } = queueTask(state, {
        type: 'layout',
        input: {},
      });
      
      expect(() => {
        queueTask(s1, {
          type: 'layout',
          input: {},
        });
      }).toThrow('Task queue full');
    });
  });

  describe('Task Assignment', () => {
    it('should assign task to worker', () => {
      const state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      
      const newState = assignTaskToWorker(s2, worker.id, taskId);
      
      const updatedWorker = newState.workers.get(worker.id);
      expect(updatedWorker?.status).toBe('busy');
      expect(updatedWorker?.currentTask).toBe(taskId);
      expect(newState.taskQueue.length).toBe(0);
      expect(newState.activeTasks.has(taskId)).toBe(true);
      expect(newState.statistics.queuedTasks).toBe(0);
    });

    it('should not assign if worker not found', () => {
      const state = createWorkerManagerState();
      const { taskId, state: newState } = queueTask(state, {
        type: 'layout',
        input: {},
      });
      
      const result = assignTaskToWorker(newState, 'nonexistent', taskId);
      
      expect(result).toBe(newState);
    });

    it('should not assign if task not in queue', () => {
      const state = createWorkerManagerState();
      const { worker, state: newState } = spawnWorker(state, 'worker.js');
      
      const result = assignTaskToWorker(newState, worker.id, 'nonexistent');
      
      expect(result).toBe(newState);
    });
  });

  describe('Task Completion', () => {
    it('should complete a task', () => {
      let state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      state = assignTaskToWorker(s2, worker.id, taskId);
      
      const result = { nodes: [], edges: [] };
      const newState = completeTask(state, taskId, result);
      
      const updatedWorker = newState.workers.get(worker.id);
      expect(updatedWorker?.status).toBe('idle');
      expect(updatedWorker?.currentTask).toBeUndefined();
      expect(updatedWorker?.taskCount).toBe(1);
      expect(newState.activeTasks.has(taskId)).toBe(false);
      expect(newState.completedTasks.has(taskId)).toBe(true);
      expect(newState.completedTasks.get(taskId)?.result).toBe(result);
      expect(newState.statistics.completedTasks).toBe(1);
    });

    it('should not complete if task not active', () => {
      const state = createWorkerManagerState();
      const newState = completeTask(state, 'nonexistent', {});
      
      expect(newState).toBe(state);
    });
  });

  describe('Task Failure', () => {
    it('should fail a task', () => {
      let state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      state = assignTaskToWorker(s2, worker.id, taskId);
      
      const error = new Error('Task failed');
      const newState = failTask(state, taskId, error);
      
      const updatedWorker = newState.workers.get(worker.id);
      expect(updatedWorker?.status).toBe('idle');
      expect(updatedWorker?.errorCount).toBe(1);
      expect(newState.activeTasks.has(taskId)).toBe(false);
      expect(newState.failedTasks.has(taskId)).toBe(true);
      expect(newState.failedTasks.get(taskId)?.error).toBe(error);
      expect(newState.statistics.failedTasks).toBe(1);
    });
  });

  describe('Worker Crashes', () => {
    it('should handle worker crash', () => {
      let state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      state = assignTaskToWorker(s2, worker.id, taskId);
      
      const newState = handleWorkerCrash(state, worker.id);
      
      const updatedWorker = newState.workers.get(worker.id);
      expect(updatedWorker?.status).toBe('crashed');
      expect(updatedWorker?.crashCount).toBe(1);
      expect(newState.failedTasks.has(taskId)).toBe(true);
      expect(newState.statistics.totalWorkerCrashes).toBe(1);
    });

    it('should terminate worker after crash threshold', () => {
      const state = createWorkerManagerState({ crashThreshold: 2 });
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      
      // First crash
      const crashedWorker: WorkerInstance = {
        ...worker,
        crashCount: 1,
        status: 'crashed',
      };
      const s2 = {
        ...s1,
        workers: new Map([[worker.id, crashedWorker]]),
      };
      
      // Second crash should terminate
      const newState = handleWorkerCrash(s2, worker.id);
      
      expect(newState.workers.has(worker.id)).toBe(false);
    });
  });

  describe('Task Cancellation', () => {
    it('should cancel queued task', () => {
      const state = createWorkerManagerState();
      const { taskId, state: newState } = queueTask(state, {
        type: 'layout',
        input: {},
      });
      
      const result = cancelTask(newState, taskId);
      
      expect(result.taskQueue.length).toBe(0);
      expect(result.statistics.cancelledTasks).toBe(1);
    });

    it('should cancel active task', () => {
      let state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      state = assignTaskToWorker(s2, worker.id, taskId);
      
      const postMessageSpy = vi.spyOn(worker.worker, 'postMessage');
      const newState = cancelTask(state, taskId);
      
      expect(postMessageSpy).toHaveBeenCalledWith({
        type: 'terminate',
        taskId,
      });
      expect(newState.activeTasks.has(taskId)).toBe(false);
      expect(newState.statistics.cancelledTasks).toBe(1);
      
      const updatedWorker = newState.workers.get(worker.id);
      expect(updatedWorker?.status).toBe('idle');
    });

    it('should not fail when cancelling non-existent task', () => {
      const state = createWorkerManagerState();
      const newState = cancelTask(state, 'nonexistent');
      
      expect(newState).toBe(state);
    });
  });

  describe('Task Queries', () => {
    it('should get task status', () => {
      const state = createWorkerManagerState();
      
      // Pending
      const { taskId: t1, state: s1 } = queueTask(state, {
        type: 'layout',
        input: {},
      });
      expect(getTaskStatus(s1, t1)).toBe('pending');
      
      // Running
      const { worker, state: s2 } = spawnWorker(s1, 'worker.js');
      const s3 = assignTaskToWorker(s2, worker.id, t1);
      expect(getTaskStatus(s3, t1)).toBe('running');
      
      // Completed
      const s4 = completeTask(s3, t1, {});
      expect(getTaskStatus(s4, t1)).toBe('completed');
      
      // Failed
      const { taskId: t2, state: s5 } = queueTask(s4, {
        type: 'layout',
        input: {},
      });
      const s6 = assignTaskToWorker(s5, worker.id, t2);
      const s7 = failTask(s6, t2, new Error('Failed'));
      expect(getTaskStatus(s7, t2)).toBe('failed');
      
      // Not found
      expect(getTaskStatus(s7, 'nonexistent')).toBeNull();
    });

    it('should get task result', () => {
      let state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      state = assignTaskToWorker(s2, worker.id, taskId);
      
      const result = { nodes: [], edges: [] };
      state = completeTask(state, taskId, result);
      
      expect(getTaskResult(state, taskId)).toBe(result);
      expect(getTaskResult(state, 'nonexistent')).toBeNull();
    });

    it('should get task error', () => {
      let state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      state = assignTaskToWorker(s2, worker.id, taskId);
      
      const error = new Error('Task failed');
      state = failTask(state, taskId, error);
      
      expect(getTaskError(state, taskId)).toBe(error);
      expect(getTaskError(state, 'nonexistent')).toBeNull();
    });
  });

  describe('Statistics', () => {
    it('should get worker statistics', () => {
      const state = createWorkerManagerState();
      
      const { worker: w1, state: s1 } = spawnWorker(state, 'worker.js');
      const { worker: w2, state: s2 } = spawnWorker(s1, 'worker.js');
      
      const { taskId, state: s3 } = queueTask(s2, {
        type: 'layout',
        input: {},
      });
      
      const stats = getWorkerStatistics(s3);
      
      expect(stats.activeWorkers).toBe(2);
      expect(stats.idleWorkers).toBe(2);
      expect(stats.queuedTasks).toBe(1);
      expect(stats.totalTasks).toBe(1);
    });

    it('should track completed and failed tasks', () => {
      const state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      
      // Complete one task
      const { taskId: t1, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      let s3 = assignTaskToWorker(s2, worker.id, t1);
      s3 = completeTask(s3, t1, {});
      
      // Fail another task
      const { taskId: t2, state: s4 } = queueTask(s3, {
        type: 'layout',
        input: {},
      });
      let s5 = assignTaskToWorker(s4, worker.id, t2);
      s5 = failTask(s5, t2, new Error('Failed'));
      
      const stats = getWorkerStatistics(s5);
      expect(stats.completedTasks).toBe(1);
      expect(stats.failedTasks).toBe(1);
    });
  });

  describe('Cleanup', () => {
    it('should cleanup old tasks', () => {
      const state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      
      // Add completed task
      const { taskId: t1, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      let s3 = assignTaskToWorker(s2, worker.id, t1);
      s3 = completeTask(s3, t1, {});
      
      // Manually set old timestamp
      const oldTask = s3.completedTasks.get(t1)!;
      s3.completedTasks.set(t1, {
        ...oldTask,
        timestamp: Date.now() - 7200000, // 2 hours ago
      });
      
      // Cleanup tasks older than 1 hour
      const newState = cleanupTasks(s3, 3600000);
      
      expect(newState.completedTasks.has(t1)).toBe(false);
    });

    it('should keep recent tasks', () => {
      const state = createWorkerManagerState();
      const { worker, state: s1 } = spawnWorker(state, 'worker.js');
      
      const { taskId, state: s2 } = queueTask(s1, {
        type: 'layout',
        input: {},
      });
      let s3 = assignTaskToWorker(s2, worker.id, taskId);
      s3 = completeTask(s3, taskId, {});
      
      const newState = cleanupTasks(s3, 3600000);
      
      expect(newState.completedTasks.has(taskId)).toBe(true);
    });
  });

  describe('Worker Support', () => {
    it('should detect worker support', () => {
      expect(supportsWorkers()).toBe(true);
    });
  });

  describe('Worker Health', () => {
    it('should report healthy worker', () => {
      const worker: WorkerInstance = {
        id: 'w1',
        worker: new MockWorker() as unknown,
        status: 'idle',
        taskCount: 0,
        errorCount: 0,
        lastActivity: Date.now(),
        crashCount: 0,
      };
      
      const health = getWorkerHealth(worker);
      expect(health.healthy).toBe(true);
    });

    it('should report crashed worker as unhealthy', () => {
      const worker: WorkerInstance = {
        id: 'w1',
        worker: new MockWorker() as unknown,
        status: 'crashed',
        taskCount: 0,
        errorCount: 0,
        lastActivity: Date.now(),
        crashCount: 1,
      };
      
      const health = getWorkerHealth(worker);
      expect(health.healthy).toBe(false);
      expect(health.reason).toBe('Worker has crashed');
    });

    it('should report high crash count as unhealthy', () => {
      const worker: WorkerInstance = {
        id: 'w1',
        worker: new MockWorker() as unknown,
        status: 'idle',
        taskCount: 0,
        errorCount: 0,
        lastActivity: Date.now(),
        crashCount: 3,
      };
      
      const health = getWorkerHealth(worker);
      expect(health.healthy).toBe(false);
      expect(health.reason).toContain('High crash count');
    });

    it('should report stuck worker as unhealthy', () => {
      const worker: WorkerInstance = {
        id: 'w1',
        worker: new MockWorker() as unknown,
        status: 'busy',
        taskCount: 0,
        errorCount: 0,
        lastActivity: Date.now() - 120000, // 2 minutes ago
        crashCount: 0,
      };
      
      const health = getWorkerHealth(worker);
      expect(health.healthy).toBe(false);
      expect(health.reason).toContain('stuck');
    });
  });
});
