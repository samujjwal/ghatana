import AsyncStorage from '@react-native-async-storage/async-storage';

const QUEUE_KEY = '@flashit:offline_queue';

export interface QueuedItem {
  id: string;
  type: 'audio' | 'image' | 'video' | 'text';
  uri?: string;
  content?: string;
  metadata: {
    timestamp: number;
    sphereId?: string;
    tags?: string[];
    emotion?: string;
    retryCount: number;
    lastAttempt?: number;
  };
  status: 'pending' | 'uploading' | 'failed' | 'completed';
}

/**
 * Offline Queue Service
 * 
 * @doc.type service
 * @doc.purpose Manage offline capture queue with AsyncStorage
 * @doc.layer product
 * @doc.pattern Service
 */
class OfflineQueueService {
  /**
   * Add item to offline queue
   */
  async enqueue(item: Omit<QueuedItem, 'id' | 'status' | 'metadata'> & { metadata?: Partial<QueuedItem['metadata']> }): Promise<string> {
    try {
      const id = `queue_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      const queuedItem: QueuedItem = {
        ...item,
        id,
        status: 'pending',
        metadata: {
          timestamp: Date.now(),
          retryCount: 0,
          ...item.metadata,
        },
      };

      const queue = await this.getQueue();
      queue.push(queuedItem);
      await this.saveQueue(queue);

      return id;
    } catch (error) {
      console.error('Error enqueueing item:', error);
      throw error;
    }
  }

  /**
   * Get all queued items
   */
  async getQueue(): Promise<QueuedItem[]> {
    try {
      const queueJson = await AsyncStorage.getItem(QUEUE_KEY);
      return queueJson ? JSON.parse(queueJson) : [];
    } catch (error) {
      console.error('Error getting queue:', error);
      return [];
    }
  }

  /**
   * Get pending items
   */
  async getPendingItems(): Promise<QueuedItem[]> {
    const queue = await this.getQueue();
    return queue.filter((item) => item.status === 'pending' || item.status === 'failed');
  }

  /**
   * Update item status
   */
  async updateItemStatus(id: string, status: QueuedItem['status'], error?: string): Promise<void> {
    try {
      const queue = await this.getQueue();
      const index = queue.findIndex((item) => item.id === id);
      
      if (index !== -1) {
        queue[index].status = status;
        if (status === 'failed') {
          queue[index].metadata.retryCount++;
          queue[index].metadata.lastAttempt = Date.now();
        }
        await this.saveQueue(queue);
      }
    } catch (error) {
      console.error('Error updating item status:', error);
    }
  }

  /**
   * Remove item from queue
   */
  async removeItem(id: string): Promise<void> {
    try {
      const queue = await this.getQueue();
      const filteredQueue = queue.filter((item) => item.id !== id);
      await this.saveQueue(filteredQueue);
    } catch (error) {
      console.error('Error removing item:', error);
    }
  }

  /**
   * Clear completed items
   */
  async clearCompleted(): Promise<void> {
    try {
      const queue = await this.getQueue();
      const activeQueue = queue.filter((item) => item.status !== 'completed');
      await this.saveQueue(activeQueue);
    } catch (error) {
      console.error('Error clearing completed items:', error);
    }
  }

  /**
   * Get queue statistics
   */
  async getStats(): Promise<{
    total: number;
    pending: number;
    uploading: number;
    failed: number;
    completed: number;
  }> {
    const queue = await this.getQueue();
    return {
      total: queue.length,
      pending: queue.filter((item) => item.status === 'pending').length,
      uploading: queue.filter((item) => item.status === 'uploading').length,
      failed: queue.filter((item) => item.status === 'failed').length,
      completed: queue.filter((item) => item.status === 'completed').length,
    };
  }

  /**
   * Save queue to storage
   */
  private async saveQueue(queue: QueuedItem[]): Promise<void> {
    try {
      await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
    } catch (error) {
      console.error('Error saving queue:', error);
      throw error;
    }
  }

  /**
   * Clear entire queue (use with caution)
   */
  async clearAll(): Promise<void> {
    try {
      await AsyncStorage.removeItem(QUEUE_KEY);
    } catch (error) {
      console.error('Error clearing queue:', error);
    }
  }
}

export const offlineQueueService = new OfflineQueueService();
