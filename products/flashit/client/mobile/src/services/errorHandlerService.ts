import AsyncStorage from '@react-native-async-storage/async-storage';
import { offlineQueueService, QueuedItem } from './offlineQueue';
import { networkMonitor } from './networkMonitor';

/**
 * Upload Error Handling Service
 * 
 * @doc.type service
 * @doc.purpose Handle upload errors with intelligent retry strategies
 * @doc.layer product
 * @doc.pattern Service
 * 
 * Features:
 * - Categorized error messages with actionable suggestions
 * - Retry strategies (immediate, delayed, manual)
 * - Upload queue prioritization
 * - Error analytics and pattern tracking
 */

const STORAGE_KEYS = {
  ERROR_ANALYTICS: '@errors_analytics',
  RETRY_CONFIG: '@errors_retryConfig',
};

// Error categories
export type ErrorCategory = 
  | 'network'      // Connection issues
  | 'server'       // 5xx errors
  | 'client'       // 4xx errors
  | 'timeout'      // Request timeout
  | 'storage'      // Device storage issues
  | 'auth'         // Authentication issues
  | 'quota'        // Server quota exceeded
  | 'unknown';     // Unclassified errors

// Retry strategies
export type RetryStrategy = 'immediate' | 'delayed' | 'exponential' | 'manual';

export interface UploadError {
  id: string;
  uploadId: string;
  category: ErrorCategory;
  code?: string;
  message: string;
  userMessage: string;
  suggestion: string;
  retryStrategy: RetryStrategy;
  retryDelayMs: number;
  maxRetries: number;
  currentRetry: number;
  timestamp: number;
  isRetryable: boolean;
}

export interface ErrorAnalytics {
  totalErrors: number;
  errorsByCategory: Record<ErrorCategory, number>;
  errorsByHour: Record<string, number>;
  mostCommonErrors: { message: string; count: number }[];
  lastErrorTimestamp: number | null;
  successRate: number;
}

export interface RetryConfig {
  maxRetries: number;
  baseDelayMs: number;
  maxDelayMs: number;
  exponentialBase: number;
}

const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 5,
  baseDelayMs: 5000,      // 5 seconds
  maxDelayMs: 300000,     // 5 minutes
  exponentialBase: 2,
};

// Error message templates
const ERROR_MESSAGES: Record<ErrorCategory, { userMessage: string; suggestion: string }> = {
  network: {
    userMessage: 'Unable to connect to the server',
    suggestion: 'Check your internet connection and try again',
  },
  server: {
    userMessage: 'Server is temporarily unavailable',
    suggestion: 'Please wait a moment. We\'ll automatically retry.',
  },
  client: {
    userMessage: 'Invalid request',
    suggestion: 'Please try again. If the problem persists, contact support.',
  },
  timeout: {
    userMessage: 'Request timed out',
    suggestion: 'Your connection might be slow. We\'ll retry with a smaller file.',
  },
  storage: {
    userMessage: 'Storage error',
    suggestion: 'Free up some space on your device and try again.',
  },
  auth: {
    userMessage: 'Authentication required',
    suggestion: 'Please sign in again to continue uploading.',
  },
  quota: {
    userMessage: 'Storage quota exceeded',
    suggestion: 'You\'ve reached your storage limit. Upgrade your plan or delete some files.',
  },
  unknown: {
    userMessage: 'Something went wrong',
    suggestion: 'Please try again. If the problem persists, contact support.',
  },
};

/**
 * Upload Error Handler Service
 */
class UploadErrorHandlerService {
  private retryConfig: RetryConfig = DEFAULT_RETRY_CONFIG;
  private retryTimers: Map<string, NodeJS.Timeout> = new Map();
  private errorHistory: UploadError[] = [];
  private listeners: Set<(error: UploadError) => void> = new Set();

  /**
   * Initialize error handler
   */
  async init(): Promise<void> {
    // Load retry configuration
    const configRaw = await AsyncStorage.getItem(STORAGE_KEYS.RETRY_CONFIG);
    if (configRaw) {
      this.retryConfig = { ...DEFAULT_RETRY_CONFIG, ...JSON.parse(configRaw) };
    }
  }

  /**
   * Handle an upload error
   */
  async handleError(
    uploadId: string,
    error: Error | string,
    statusCode?: number
  ): Promise<UploadError> {
    const errorMessage = typeof error === 'string' ? error : error.message;
    const category = this.categorizeError(errorMessage, statusCode);
    const templates = ERROR_MESSAGES[category];
    
    const uploadError: UploadError = {
      id: `error_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      uploadId,
      category,
      code: statusCode?.toString(),
      message: errorMessage,
      userMessage: templates.userMessage,
      suggestion: templates.suggestion,
      retryStrategy: this.getRetryStrategy(category),
      retryDelayMs: this.calculateRetryDelay(category, 0),
      maxRetries: this.getMaxRetries(category),
      currentRetry: 0,
      timestamp: Date.now(),
      isRetryable: this.isRetryable(category),
    };

    // Store error
    this.errorHistory.push(uploadError);
    if (this.errorHistory.length > 100) {
      this.errorHistory.shift();
    }

    // Update analytics
    await this.updateAnalytics(uploadError);

    // Notify listeners
    this.notifyListeners(uploadError);

    // Schedule retry if applicable
    if (uploadError.isRetryable && uploadError.retryStrategy !== 'manual') {
      await this.scheduleRetry(uploadError);
    }

    console.log(`[ErrorHandler] Error handled: ${category} - ${errorMessage}`);
    return uploadError;
  }

  /**
   * Categorize error based on message and status code
   */
  private categorizeError(message: string, statusCode?: number): ErrorCategory {
    const lowerMessage = message.toLowerCase();

    // Network errors
    if (
      lowerMessage.includes('network') ||
      lowerMessage.includes('connection') ||
      lowerMessage.includes('offline') ||
      lowerMessage.includes('no internet') ||
      lowerMessage.includes('dns') ||
      lowerMessage.includes('socket')
    ) {
      return 'network';
    }

    // Timeout
    if (
      lowerMessage.includes('timeout') ||
      lowerMessage.includes('timed out') ||
      lowerMessage.includes('aborted')
    ) {
      return 'timeout';
    }

    // Storage
    if (
      lowerMessage.includes('storage') ||
      lowerMessage.includes('disk') ||
      lowerMessage.includes('space') ||
      lowerMessage.includes('enospc')
    ) {
      return 'storage';
    }

    // Status code based categorization
    if (statusCode) {
      if (statusCode === 401 || statusCode === 403) {
        return 'auth';
      }
      if (statusCode === 413 || statusCode === 507) {
        return 'quota';
      }
      if (statusCode >= 400 && statusCode < 500) {
        return 'client';
      }
      if (statusCode >= 500) {
        return 'server';
      }
    }

    return 'unknown';
  }

  /**
   * Get retry strategy for error category
   */
  private getRetryStrategy(category: ErrorCategory): RetryStrategy {
    switch (category) {
      case 'network':
        return 'immediate'; // Retry as soon as network is back
      case 'server':
        return 'exponential'; // Back off to reduce load
      case 'timeout':
        return 'delayed'; // Wait then retry
      case 'client':
        return 'manual'; // User needs to fix something
      case 'auth':
        return 'manual'; // User needs to re-authenticate
      case 'quota':
        return 'manual'; // User needs to free space
      case 'storage':
        return 'manual'; // User needs to free space
      default:
        return 'delayed';
    }
  }

  /**
   * Get max retries for error category
   */
  private getMaxRetries(category: ErrorCategory): number {
    switch (category) {
      case 'network':
        return 10; // Keep trying for network issues
      case 'server':
        return 5;  // Limited retries for server issues
      case 'timeout':
        return 3;  // Few retries for timeouts
      case 'client':
      case 'auth':
      case 'quota':
      case 'storage':
        return 0;  // No automatic retries
      default:
        return 3;
    }
  }

  /**
   * Check if error is retryable
   */
  private isRetryable(category: ErrorCategory): boolean {
    return ['network', 'server', 'timeout', 'unknown'].includes(category);
  }

  /**
   * Calculate retry delay with exponential backoff
   */
  private calculateRetryDelay(category: ErrorCategory, retryCount: number): number {
    const { baseDelayMs, maxDelayMs, exponentialBase } = this.retryConfig;

    switch (category) {
      case 'network':
        // Quick retry for network issues
        return Math.min(baseDelayMs, 5000);
      
      case 'server':
        // Exponential backoff for server issues
        const delay = baseDelayMs * Math.pow(exponentialBase, retryCount);
        return Math.min(delay, maxDelayMs);
      
      case 'timeout':
        // Moderate delay for timeouts
        return baseDelayMs * (retryCount + 1);
      
      default:
        return baseDelayMs;
    }
  }

  /**
   * Schedule a retry for an upload
   */
  private async scheduleRetry(error: UploadError): Promise<void> {
    const { uploadId, retryDelayMs, currentRetry, maxRetries, retryStrategy } = error;

    if (currentRetry >= maxRetries) {
      console.log(`[ErrorHandler] Max retries reached for ${uploadId}`);
      await offlineQueueService.updateItemStatus(uploadId, 'failed', error.message);
      return;
    }

    // Clear existing timer if any
    const existingTimer = this.retryTimers.get(uploadId);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    // For network errors, wait for network to come back
    if (error.category === 'network') {
      this.waitForNetwork(uploadId, error);
      return;
    }

    console.log(`[ErrorHandler] Scheduling retry for ${uploadId} in ${retryDelayMs}ms`);

    const timer = setTimeout(async () => {
      this.retryTimers.delete(uploadId);
      await this.retryUpload(uploadId, currentRetry + 1);
    }, retryDelayMs);

    this.retryTimers.set(uploadId, timer);
  }

  /**
   * Wait for network to come back before retrying
   */
  private waitForNetwork(uploadId: string, error: UploadError): void {
    const unsubscribe = networkMonitor.subscribe((state) => {
      if (state.isConnected) {
        unsubscribe();
        console.log(`[ErrorHandler] Network restored, retrying ${uploadId}`);
        this.retryUpload(uploadId, error.currentRetry + 1);
      }
    });
  }

  /**
   * Retry an upload
   */
  async retryUpload(uploadId: string, retryCount: number): Promise<void> {
    console.log(`[ErrorHandler] Retrying upload ${uploadId} (attempt ${retryCount})`);
    await offlineQueueService.updateItemStatus(uploadId, 'pending');
    
    // Update retry count in metadata
    const queue = await offlineQueueService.getQueue();
    const item = queue.find(i => i.id === uploadId);
    if (item) {
      item.metadata.retryCount = retryCount;
      item.metadata.lastAttempt = Date.now();
    }
  }

  /**
   * Manually retry a failed upload
   */
  async manualRetry(uploadId: string): Promise<void> {
    console.log(`[ErrorHandler] Manual retry requested for ${uploadId}`);
    await this.retryUpload(uploadId, 0);
  }

  /**
   * Cancel scheduled retry
   */
  cancelRetry(uploadId: string): void {
    const timer = this.retryTimers.get(uploadId);
    if (timer) {
      clearTimeout(timer);
      this.retryTimers.delete(uploadId);
      console.log(`[ErrorHandler] Cancelled retry for ${uploadId}`);
    }
  }

  /**
   * Get prioritized upload queue
   * Priority: Manual retries > Fresh uploads > Auto retries
   */
  async getPrioritizedQueue(): Promise<QueuedItem[]> {
    const queue = await offlineQueueService.getPendingItems();
    
    return queue.sort((a, b) => {
      // Priority 1: Items with fewer retries
      if (a.metadata.retryCount !== b.metadata.retryCount) {
        return a.metadata.retryCount - b.metadata.retryCount;
      }
      
      // Priority 2: Smaller files first (for quick wins)
      // We don't have size in metadata, so use type as proxy
      const typeOrder: Record<string, number> = {
        text: 1,
        image: 2,
        audio: 3,
        video: 4,
      };
      const aOrder = typeOrder[a.type] || 5;
      const bOrder = typeOrder[b.type] || 5;
      if (aOrder !== bOrder) {
        return aOrder - bOrder;
      }
      
      // Priority 3: Older items first (FIFO)
      return a.metadata.timestamp - b.metadata.timestamp;
    });
  }

  /**
   * Update error analytics
   */
  private async updateAnalytics(error: UploadError): Promise<void> {
    try {
      const analyticsRaw = await AsyncStorage.getItem(STORAGE_KEYS.ERROR_ANALYTICS);
      const analytics: ErrorAnalytics = analyticsRaw 
        ? JSON.parse(analyticsRaw)
        : {
            totalErrors: 0,
            errorsByCategory: {},
            errorsByHour: {},
            mostCommonErrors: [],
            lastErrorTimestamp: null,
            successRate: 100,
          };

      // Update counts
      analytics.totalErrors++;
      analytics.errorsByCategory[error.category] = 
        (analytics.errorsByCategory[error.category] || 0) + 1;
      
      // Track by hour
      const hour = new Date(error.timestamp).toISOString().slice(0, 13);
      analytics.errorsByHour[hour] = (analytics.errorsByHour[hour] || 0) + 1;

      // Update most common errors
      const existing = analytics.mostCommonErrors.find(e => e.message === error.message);
      if (existing) {
        existing.count++;
      } else {
        analytics.mostCommonErrors.push({ message: error.message, count: 1 });
      }
      analytics.mostCommonErrors.sort((a, b) => b.count - a.count);
      analytics.mostCommonErrors = analytics.mostCommonErrors.slice(0, 10);

      analytics.lastErrorTimestamp = error.timestamp;

      await AsyncStorage.setItem(STORAGE_KEYS.ERROR_ANALYTICS, JSON.stringify(analytics));
    } catch (e) {
      console.error('[ErrorHandler] Failed to update analytics:', e);
    }
  }

  /**
   * Get error analytics
   */
  async getAnalytics(): Promise<ErrorAnalytics> {
    try {
      const analyticsRaw = await AsyncStorage.getItem(STORAGE_KEYS.ERROR_ANALYTICS);
      if (analyticsRaw) {
        return JSON.parse(analyticsRaw);
      }
    } catch (e) {
      console.error('[ErrorHandler] Failed to get analytics:', e);
    }

    return {
      totalErrors: 0,
      errorsByCategory: {} as Record<ErrorCategory, number>,
      errorsByHour: {},
      mostCommonErrors: [],
      lastErrorTimestamp: null,
      successRate: 100,
    };
  }

  /**
   * Clear error analytics
   */
  async clearAnalytics(): Promise<void> {
    await AsyncStorage.removeItem(STORAGE_KEYS.ERROR_ANALYTICS);
    this.errorHistory = [];
  }

  /**
   * Get recent errors
   */
  getRecentErrors(limit: number = 10): UploadError[] {
    return this.errorHistory.slice(-limit).reverse();
  }

  /**
   * Subscribe to new errors
   */
  subscribe(listener: (error: UploadError) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Notify listeners
   */
  private notifyListeners(error: UploadError): void {
    this.listeners.forEach(listener => listener(error));
  }

  /**
   * Get actionable error info for UI
   */
  getErrorDisplay(error: UploadError): {
    icon: string;
    color: string;
    title: string;
    message: string;
    suggestion: string;
    canRetry: boolean;
    retryLabel: string;
  } {
    const icons: Record<ErrorCategory, string> = {
      network: '📶',
      server: '🖥️',
      client: '⚠️',
      timeout: '⏱️',
      storage: '💾',
      auth: '🔐',
      quota: '📊',
      unknown: '❓',
    };

    const colors: Record<ErrorCategory, string> = {
      network: '#ff9500',
      server: '#ff6b00',
      client: '#ff3b30',
      timeout: '#ff9500',
      storage: '#ff3b30',
      auth: '#007aff',
      quota: '#ff3b30',
      unknown: '#888',
    };

    return {
      icon: icons[error.category],
      color: colors[error.category],
      title: error.userMessage,
      message: error.message,
      suggestion: error.suggestion,
      canRetry: error.isRetryable || error.retryStrategy === 'manual',
      retryLabel: error.retryStrategy === 'manual' ? 'Retry Now' : 'Retrying...',
    };
  }
}

// Singleton instance
export const errorHandlerService = new UploadErrorHandlerService();
