import AsyncStorage from '@react-native-async-storage/async-storage';
import { 
  errorHandlerService, 
  UploadError, 
  ErrorCategory,
  ErrorAnalytics 
} from '../../src/services/errorHandlerService';
import { offlineQueueService } from '../../src/services/offlineQueue';
import { networkMonitor } from '../../src/services/networkMonitor';

// Mock dependencies
jest.mock('@react-native-async-storage/async-storage');
jest.mock('../../src/services/offlineQueue', () => ({
  offlineQueueService: {
    updateItemStatus: jest.fn(),
    getQueue: jest.fn().mockResolvedValue([]),
    getPendingItems: jest.fn().mockResolvedValue([]),
  },
}));
jest.mock('../../src/services/networkMonitor', () => ({
  networkMonitor: {
    isOnline: jest.fn().mockReturnValue(true),
    subscribe: jest.fn().mockReturnValue(() => {}),
  },
}));

describe('UploadErrorHandlerService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);
    (AsyncStorage.setItem as jest.Mock).mockResolvedValue(undefined);
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('init', () => {
    it('should initialize with default config', async () => {
      await errorHandlerService.init();
      expect(AsyncStorage.getItem).toHaveBeenCalled();
    });

    it('should load custom retry config if available', async () => {
      const customConfig = { maxRetries: 10, baseDelayMs: 10000 };
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(customConfig));

      await errorHandlerService.init();

      expect(AsyncStorage.getItem).toHaveBeenCalled();
    });
  });

  describe('handleError', () => {
    it('should categorize network errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-1',
        'Network request failed',
        undefined
      );

      expect(error.category).toBe('network');
      expect(error.isRetryable).toBe(true);
      expect(error.retryStrategy).toBe('immediate');
    });

    it('should categorize server errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-2',
        'Internal server error',
        500
      );

      expect(error.category).toBe('server');
      expect(error.isRetryable).toBe(true);
      expect(error.retryStrategy).toBe('exponential');
    });

    it('should categorize timeout errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-3',
        'Request timeout',
        undefined
      );

      expect(error.category).toBe('timeout');
      expect(error.isRetryable).toBe(true);
    });

    it('should categorize auth errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-4',
        'Unauthorized',
        401
      );

      expect(error.category).toBe('auth');
      expect(error.isRetryable).toBe(false);
      expect(error.retryStrategy).toBe('manual');
    });

    it('should categorize quota errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-5',
        'Payload too large',
        413
      );

      expect(error.category).toBe('quota');
      expect(error.isRetryable).toBe(false);
    });

    it('should categorize storage errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-6',
        'ENOSPC: no space left on device',
        undefined
      );

      expect(error.category).toBe('storage');
      expect(error.retryStrategy).toBe('manual');
    });

    it('should categorize client errors correctly', async () => {
      const error = await errorHandlerService.handleError(
        'upload-7',
        'Bad request',
        400
      );

      expect(error.category).toBe('client');
      expect(error.retryStrategy).toBe('manual');
    });

    it('should handle unknown errors', async () => {
      const error = await errorHandlerService.handleError(
        'upload-8',
        'Something unexpected happened',
        undefined
      );

      expect(error.category).toBe('unknown');
    });

    it('should include actionable suggestions', async () => {
      const error = await errorHandlerService.handleError(
        'upload-9',
        'Network connection lost',
        undefined
      );

      expect(error.suggestion).toBeTruthy();
      expect(error.userMessage).toBeTruthy();
    });

    it('should update analytics', async () => {
      await errorHandlerService.handleError('upload-10', 'Error', 500);

      expect(AsyncStorage.setItem).toHaveBeenCalledWith(
        '@errors_analytics',
        expect.any(String)
      );
    });

    it('should notify listeners', async () => {
      const listener = jest.fn();
      errorHandlerService.subscribe(listener);

      await errorHandlerService.handleError('upload-11', 'Error', 500);

      expect(listener).toHaveBeenCalled();
    });
  });

  describe('retry strategies', () => {
    it('should schedule immediate retry for network errors', async () => {
      (networkMonitor.isOnline as jest.Mock).mockReturnValue(true);

      const error = await errorHandlerService.handleError(
        'upload-12',
        'Network error',
        undefined
      );

      expect(error.retryStrategy).toBe('immediate');
    });

    it('should schedule exponential backoff for server errors', async () => {
      const error = await errorHandlerService.handleError(
        'upload-13',
        'Server error',
        503
      );

      expect(error.retryStrategy).toBe('exponential');
      expect(error.retryDelayMs).toBeGreaterThan(0);
    });

    it('should require manual retry for auth errors', async () => {
      const error = await errorHandlerService.handleError(
        'upload-14',
        'Forbidden',
        403
      );

      expect(error.retryStrategy).toBe('manual');
      expect(error.isRetryable).toBe(false);
    });
  });

  describe('manualRetry', () => {
    it('should reset upload status to pending', async () => {
      await errorHandlerService.manualRetry('upload-15');

      expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith(
        'upload-15',
        'pending'
      );
    });
  });

  describe('cancelRetry', () => {
    it('should cancel scheduled retry', () => {
      // This test verifies the method exists and doesn't throw
      expect(() => errorHandlerService.cancelRetry('upload-16')).not.toThrow();
    });
  });

  describe('getPrioritizedQueue', () => {
    it('should prioritize items with fewer retries', async () => {
      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([
        { id: '1', type: 'image', metadata: { retryCount: 3, timestamp: 1000 } },
        { id: '2', type: 'image', metadata: { retryCount: 0, timestamp: 2000 } },
        { id: '3', type: 'image', metadata: { retryCount: 1, timestamp: 1500 } },
      ]);

      const queue = await errorHandlerService.getPrioritizedQueue();

      expect(queue[0].id).toBe('2'); // 0 retries
      expect(queue[1].id).toBe('3'); // 1 retry
      expect(queue[2].id).toBe('1'); // 3 retries
    });

    it('should prioritize smaller file types', async () => {
      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([
        { id: '1', type: 'video', metadata: { retryCount: 0, timestamp: 1000 } },
        { id: '2', type: 'text', metadata: { retryCount: 0, timestamp: 1000 } },
        { id: '3', type: 'image', metadata: { retryCount: 0, timestamp: 1000 } },
      ]);

      const queue = await errorHandlerService.getPrioritizedQueue();

      expect(queue[0].id).toBe('2'); // text
      expect(queue[1].id).toBe('3'); // image
      expect(queue[2].id).toBe('1'); // video
    });

    it('should use FIFO for same priority items', async () => {
      (offlineQueueService.getPendingItems as jest.Mock).mockResolvedValue([
        { id: '1', type: 'image', metadata: { retryCount: 0, timestamp: 2000 } },
        { id: '2', type: 'image', metadata: { retryCount: 0, timestamp: 1000 } },
        { id: '3', type: 'image', metadata: { retryCount: 0, timestamp: 1500 } },
      ]);

      const queue = await errorHandlerService.getPrioritizedQueue();

      expect(queue[0].id).toBe('2'); // oldest
      expect(queue[1].id).toBe('3');
      expect(queue[2].id).toBe('1'); // newest
    });
  });

  describe('getAnalytics', () => {
    it('should return stored analytics', async () => {
      const storedAnalytics: ErrorAnalytics = {
        totalErrors: 10,
        errorsByCategory: { network: 5, server: 3, unknown: 2 } as any,
        errorsByHour: { '2024-01-01T10': 5 },
        mostCommonErrors: [{ message: 'Network error', count: 5 }],
        lastErrorTimestamp: Date.now(),
        successRate: 90,
      };
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(storedAnalytics));

      const analytics = await errorHandlerService.getAnalytics();

      expect(analytics.totalErrors).toBe(10);
      expect(analytics.errorsByCategory.network).toBe(5);
    });

    it('should return default analytics if none stored', async () => {
      (AsyncStorage.getItem as jest.Mock).mockResolvedValue(null);

      const analytics = await errorHandlerService.getAnalytics();

      expect(analytics.totalErrors).toBe(0);
      expect(analytics.successRate).toBe(100);
    });
  });

  describe('clearAnalytics', () => {
    it('should clear stored analytics', async () => {
      await errorHandlerService.clearAnalytics();

      expect(AsyncStorage.removeItem).toHaveBeenCalledWith('@errors_analytics');
    });
  });

  describe('getRecentErrors', () => {
    it('should return recent errors', async () => {
      // Add some errors
      await errorHandlerService.handleError('1', 'Error 1', 500);
      await errorHandlerService.handleError('2', 'Error 2', 500);
      await errorHandlerService.handleError('3', 'Error 3', 500);

      const recent = errorHandlerService.getRecentErrors(2);

      expect(recent.length).toBe(2);
      expect(recent[0].uploadId).toBe('3'); // Most recent first
    });
  });

  describe('subscribe', () => {
    it('should notify subscribers of new errors', async () => {
      const listener = jest.fn();
      const unsubscribe = errorHandlerService.subscribe(listener);

      await errorHandlerService.handleError('test', 'Error', 500);

      expect(listener).toHaveBeenCalled();
      unsubscribe();
    });

    it('should allow unsubscribing', async () => {
      const listener = jest.fn();
      const unsubscribe = errorHandlerService.subscribe(listener);
      unsubscribe();

      await errorHandlerService.handleError('test', 'Error', 500);

      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe('getErrorDisplay', () => {
    it('should return display info for network errors', async () => {
      const error = await errorHandlerService.handleError(
        'test',
        'Network error',
        undefined
      );

      const display = errorHandlerService.getErrorDisplay(error);

      expect(display.icon).toBe('📶');
      expect(display.color).toBe('#ff9500');
      expect(display.canRetry).toBe(true);
    });

    it('should return display info for auth errors', async () => {
      const error = await errorHandlerService.handleError(
        'test',
        'Unauthorized',
        401
      );

      const display = errorHandlerService.getErrorDisplay(error);

      expect(display.icon).toBe('🔐');
      expect(display.retryLabel).toBe('Retry Now');
    });

    it('should show appropriate labels for different strategies', async () => {
      const serverError = await errorHandlerService.handleError(
        'test1',
        'Server error',
        500
      );
      const authError = await errorHandlerService.handleError(
        'test2',
        'Auth error',
        401
      );

      expect(errorHandlerService.getErrorDisplay(serverError).retryLabel).toBe('Retrying...');
      expect(errorHandlerService.getErrorDisplay(authError).retryLabel).toBe('Retry Now');
    });
  });
});
