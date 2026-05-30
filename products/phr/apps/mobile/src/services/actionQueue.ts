/**
 * Low-connectivity action queue
 *
 * Queues actions when offline or in low connectivity, and syncs them when
 * connectivity is restored. Used for FCHV and patient actions that need to
 * be persisted and retried.
 *
 * @doc.type service
 * @doc.purpose Queue and sync actions for low-connectivity scenarios
 * @doc.layer mobile
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import type { MobileSession } from '../types';
import { getTelemetry } from './mobileDiagnostics';

const ACTION_QUEUE_KEY = 'phr_action_queue';
const MAX_QUEUE_SIZE = 100;
const MAX_RETRIES = 3;

export type ActionType = 
  | 'create_consent'
  | 'revoke_consent'
  | 'book_appointment'
  | 'cancel_appointment'
  | 'submit_vitals'
  | 'report_symptom'
  | 'update_profile'
  | 'fchv_visit_log'
  | 'fchv_report_case';

export interface QueuedAction {
  id: string;
  type: ActionType;
  payload: Record<string, unknown>;
  timestamp: string;
  retryCount: number;
  status: 'pending' | 'syncing' | 'failed' | 'completed';
  error?: string;
}

export interface ActionQueueStats {
  total: number;
  pending: number;
  syncing: number;
  failed: number;
  completed: number;
}

/**
 * Load the action queue from storage
 */
export async function loadActionQueue(): Promise<QueuedAction[]> {
  try {
    const data = await AsyncStorage.getItem(ACTION_QUEUE_KEY);
    if (!data) return [];
    return JSON.parse(data) as QueuedAction[];
  } catch (error) {
    // G11-T09: Use telemetry wrapper instead of console.error
    getTelemetry().incrementCounter('phr.mobile.action_queue.load_failed');
    return [];
  }
}

/**
 * Save the action queue to storage
 */
async function saveActionQueue(queue: QueuedAction[]): Promise<void> {
  try {
    await AsyncStorage.setItem(ACTION_QUEUE_KEY, JSON.stringify(queue));
  } catch (error) {
    // G11-T09: Use telemetry wrapper instead of console.error
    getTelemetry().incrementCounter('phr.mobile.action_queue.save_failed');
    throw error;
  }
}

/**
 * Add an action to the queue
 */
export async function enqueueAction(
  type: ActionType,
  payload: Record<string, unknown>
): Promise<QueuedAction> {
  const queue = await loadActionQueue();
  
  // Enforce max queue size
  if (queue.length >= MAX_QUEUE_SIZE) {
    const oldestPending = queue.find(a => a.status === 'pending');
    if (oldestPending) {
      const index = queue.indexOf(oldestPending);
      queue.splice(index, 1);
    } else {
      throw new Error('Action queue is full and no pending actions to remove');
    }
  }

  const action: QueuedAction = {
    id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    type,
    payload,
    timestamp: new Date().toISOString(),
    retryCount: 0,
    status: 'pending',
  };

  queue.push(action);
  await saveActionQueue(queue);
  return action;
}

/**
 * Get queue statistics
 */
export async function getActionQueueStats(): Promise<ActionQueueStats> {
  const queue = await loadActionQueue();
  return {
    total: queue.length,
    pending: queue.filter(a => a.status === 'pending').length,
    syncing: queue.filter(a => a.status === 'syncing').length,
    failed: queue.filter(a => a.status === 'failed').length,
    completed: queue.filter(a => a.status === 'completed').length,
  };
}

/**
 * Get pending actions
 */
export async function getPendingActions(): Promise<QueuedAction[]> {
  const queue = await loadActionQueue();
  return queue.filter(a => a.status === 'pending');
}

/**
 * Get failed actions
 */
export async function getFailedActions(): Promise<QueuedAction[]> {
  const queue = await loadActionQueue();
  return queue.filter(a => a.status === 'failed');
}

/**
 * Clear completed actions from the queue
 */
export async function clearCompletedActions(): Promise<void> {
  const queue = await loadActionQueue();
  const filtered = queue.filter(a => a.status !== 'completed');
  await saveActionQueue(filtered);
}

/**
 * Clear all actions from the queue
 */
export async function clearActionQueue(): Promise<void> {
  await AsyncStorage.removeItem(ACTION_QUEUE_KEY);
}

/**
 * Retry a failed action
 */
export async function retryAction(actionId: string): Promise<void> {
  const queue = await loadActionQueue();
  const action = queue.find(a => a.id === actionId);
  
  if (!action) {
    throw new Error(`Action ${actionId} not found`);
  }

  if (action.retryCount >= MAX_RETRIES) {
    throw new Error(`Action ${actionId} has exceeded max retries`);
  }

  action.status = 'pending';
  action.retryCount += 1;
  action.error = undefined;
  
  await saveActionQueue(queue);
}

/**
 * Delete an action from the queue
 */
export async function deleteAction(actionId: string): Promise<void> {
  const queue = await loadActionQueue();
  const filtered = queue.filter(a => a.id !== actionId);
  await saveActionQueue(filtered);
}

/**
 * Sync action executor interface
 */
export interface ActionSyncExecutor {
  (action: QueuedAction, session: MobileSession): Promise<void>;
}

/**
 * Sync pending actions
 */
export async function syncActions(
  session: MobileSession,
  executor: ActionSyncExecutor
): Promise<{ synced: number; failed: number }> {
  const queue = await loadActionQueue();
  const pendingActions = queue.filter(a => a.status === 'pending');
  
  if (pendingActions.length === 0) {
    return { synced: 0, failed: 0 };
  }

  let synced = 0;
  let failed = 0;

  for (const action of pendingActions) {
    action.status = 'syncing';
  }
  await saveActionQueue(queue);

  for (const action of pendingActions) {
    try {
      await executor(action, session);
      action.status = 'completed';
      synced++;
    } catch (error) {
      action.status = 'failed';
      action.error = error instanceof Error ? error.message : String(error);
      action.retryCount += 1;
      failed++;
    }
  }

  await saveActionQueue(queue);
  return { synced, failed };
}

/**
 * Prune old completed actions (older than 7 days)
 */
export async function pruneOldActions(): Promise<number> {
  const queue = await loadActionQueue();
  const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
  
  const pruned = queue.filter(action => {
    if (action.status !== 'completed') return true;
    const actionDate = new Date(action.timestamp);
    return actionDate > sevenDaysAgo;
  });

  const removedCount = queue.length - pruned.length;
  if (removedCount > 0) {
    await saveActionQueue(pruned);
  }

  return removedCount;
}
