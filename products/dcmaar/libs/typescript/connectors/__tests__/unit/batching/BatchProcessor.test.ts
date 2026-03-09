import { BatchProcessor, createBatchProcessor, createSimpleBatchProcessor } from '../../../src/batching/BatchProcessor';

describe('BatchProcessor', () => {
  let batcher: BatchProcessor<any, any>;

  afterEach(async () => {
    if (!batcher) {
      return;
    }

    if (!batcher['isDestroyed']) {
      try {
        await batcher.flush();
      } catch {
        // ignore
      }

      try {
        await batcher.destroy();
      } catch {
        // ignore
      }
    }

    batcher = undefined as any;
  });

  describe('Constructor & Configuration', () => {
    it('should initialize with valid config', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });
      expect(batcher).toBeDefined();
    });

    it('should apply default concurrency', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });
      const stats = batcher.getStats();
      expect(stats.concurrency).toBe(1);
    });

    it('should apply custom concurrency', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
        concurrency: 5,
      });
      const stats = batcher.getStats();
      expect(stats.concurrency).toBe(5);
    });

    it('should apply default preserveOrder', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });
      expect(batcher['config'].preserveOrder).toBe(true);
    });

    it('should accept custom preserveOrder', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
        preserveOrder: false,
      });
      expect(batcher['config'].preserveOrder).toBe(false);
    });

    it('should throw error for invalid maxBatchSize', () => {
      expect(() => {
        new BatchProcessor({
          maxBatchSize: 0,
          maxWaitTime: 1000,
          processBatch: async (items) => items,
        });
      }).toThrow('maxBatchSize must be >= 1');
    });

    it('should throw error for negative maxBatchSize', () => {
      expect(() => {
        new BatchProcessor({
          maxBatchSize: -1,
          maxWaitTime: 1000,
          processBatch: async (items) => items,
        });
      }).toThrow('maxBatchSize must be >= 1');
    });

    it('should throw error for negative maxWaitTime', () => {
      expect(() => {
        new BatchProcessor({
          maxBatchSize: 10,
          maxWaitTime: -1,
          processBatch: async (items) => items,
        });
      }).toThrow('maxWaitTime must be >= 0');
    });

    it('should throw error for invalid concurrency', () => {
      expect(() => {
        new BatchProcessor({
          maxBatchSize: 10,
          maxWaitTime: 1000,
          processBatch: async (items) => items,
          concurrency: 0,
        });
      }).toThrow('concurrency must be >= 1');
    });

    it('should accept zero maxWaitTime', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 0,
        processBatch: async (items) => items,
      });
      expect(batcher).toBeDefined();
    });

    it('should accept custom onError handler', () => {
      const errorHandler = jest.fn();
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
        onError: errorHandler,
      });
      expect(batcher['config'].onError).toBe(errorHandler);
    });
  });

  describe('add() method', () => {
    it('should add item and return result', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 100,
        processBatch: async (items) => items.map(x => x * 2),
      });

      const result = await batcher.add(5);
      expect(result).toBe(10);
    });

    it('should queue multiple items', async () => {
      jest.useFakeTimers();

      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      const promise1 = batcher.add(1);
      const promise2 = batcher.add(2);
      const promise3 = batcher.add(3);

      expect(batcher.getStats().queueSize).toBe(3);

      jest.advanceTimersByTime(1001);
      await Promise.all([promise1, promise2, promise3]);

      jest.useRealTimers();
    });

    it('should process immediately when batch is full', async () => {
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 10000, // Long wait to ensure size triggers
        processBatch: processFn,
      });

      await Promise.all([
        batcher.add(1),
        batcher.add(2),
        batcher.add(3),
      ]);

      expect(processFn).toHaveBeenCalledWith([1, 2, 3]);
    });

    it('should emit itemAdded event', async () => {
      jest.useFakeTimers();

      const handler = jest.fn();
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      batcher.on('itemAdded', handler);
      const promise = batcher.add(1);

      expect(handler).toHaveBeenCalledWith({
        queueSize: 1,
        timestamp: expect.any(Number),
      });

      jest.advanceTimersByTime(1001);
      await promise;
      jest.useRealTimers();
    });

    it('should throw error if destroyed', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      await batcher.destroy();

      await expect(batcher.add(1)).rejects.toThrow('BatchProcessor has been destroyed');
    });

    it('should handle async processing', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async (items) => {
          await new Promise(resolve => setTimeout(resolve, 50));
          return items.map(x => x + 1);
        },
      });

      const result = await batcher.add(10);
      expect(result).toBe(11);
    });
  });

  describe('addMany() method', () => {
    it('should add multiple items', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 100,
        processBatch: async (items) => items,
      });

      const results = await batcher.addMany([1, 2, 3, 4, 5]);
      expect(results).toEqual([1, 2, 3, 4, 5]);
    });

    it('should handle empty array', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 100,
        processBatch: async (items) => items,
      });

      const results = await batcher.addMany([]);
      expect(results).toEqual([]);
    });

    it('should split into multiple batches if needed', async () => {
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 100,
        processBatch: processFn,
      });

      await batcher.addMany([1, 2, 3, 4, 5]);

      // Should be called at least twice (3 + 2 items)
      expect(processFn.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });

  describe('Batch Processing - Time-based', () => {
    it('should process batch after maxWaitTime', async () => {
      jest.useFakeTimers();
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 1000,
        processBatch: processFn,
      });

      const promise = batcher.add(1);

      expect(processFn).not.toHaveBeenCalled();

      jest.advanceTimersByTime(1001);
      await promise;

      expect(processFn).toHaveBeenCalledWith([1]);

      jest.useRealTimers();
    });

    it('should reset timer on batch full', async () => {
      jest.useFakeTimers();
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 2,
        maxWaitTime: 1000,
        processBatch: processFn,
      });

      const promise1 = batcher.add(1);
      jest.advanceTimersByTime(500);

      const promise2 = batcher.add(2);
      // Should process immediately, not wait remaining 500ms

      await Promise.all([promise1, promise2]);

      expect(processFn).toHaveBeenCalledWith([1, 2]);

      jest.useRealTimers();
    });

    it('should restart timer for remaining items', async () => {
      jest.useFakeTimers();
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 2,
        maxWaitTime: 1000,
        processBatch: processFn,
      });

      const promise1 = batcher.add(1);
      const promise2 = batcher.add(2);
      const promise3 = batcher.add(3);

      // First batch processes immediately
      await Promise.all([promise1, promise2]);

      expect(processFn).toHaveBeenCalledTimes(1);

      // Second batch waits for timer
      jest.advanceTimersByTime(1001);
      await promise3;

      expect(processFn).toHaveBeenCalledTimes(2);

      jest.useRealTimers();
    });
  });

  describe('Batch Processing - Size-based', () => {
    it('should process when maxBatchSize reached', async () => {
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 10000,
        processBatch: processFn,
      });

      await Promise.all([
        batcher.add(1),
        batcher.add(2),
        batcher.add(3),
      ]);

      expect(processFn).toHaveBeenCalledWith([1, 2, 3]);
    });

    it('should respect maxBatchSize limit', async () => {
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: processFn,
      });

      await batcher.addMany([1, 2, 3, 4, 5, 6, 7, 8]);

      // Should create multiple batches
      const allItems = processFn.mock.calls.flat(2);
      expect(allItems).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    });
  });

  describe('Concurrency Control', () => {
    it('should process batches concurrently', async () => {
      let activeProcessing = 0;
      let maxConcurrent = 0;

      batcher = new BatchProcessor({
        maxBatchSize: 2,
        maxWaitTime: 100,
        concurrency: 3,
        processBatch: async (items) => {
          activeProcessing++;
          maxConcurrent = Math.max(maxConcurrent, activeProcessing);
          await new Promise(resolve => setTimeout(resolve, 100));
          activeProcessing--;
          return items;
        },
      });

      await batcher.addMany([1, 2, 3, 4, 5, 6]);

      expect(maxConcurrent).toBeLessThanOrEqual(3);
      expect(maxConcurrent).toBeGreaterThan(1);
    });

    it('should respect concurrency limit', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 1,
        maxWaitTime: 100,
        concurrency: 2,
        processBatch: async (items) => {
          await new Promise(resolve => setTimeout(resolve, 50));
          return items;
        },
      });

      const promises = [1, 2, 3, 4, 5].map(i => batcher.add(i));

      // Check that processing doesn't exceed concurrency
      expect(batcher.getStats().processing).toBeLessThanOrEqual(2);

      await Promise.all(promises);
    });

    it('should handle sequential processing (concurrency=1)', async () => {
      const order: number[] = [];

      batcher = new BatchProcessor({
        maxBatchSize: 1,
        maxWaitTime: 100,
        concurrency: 1,
        processBatch: async (items) => {
          order.push(items[0]);
          await new Promise(resolve => setTimeout(resolve, 10));
          return items;
        },
      });

      await batcher.addMany([1, 2, 3]);

      expect(order).toEqual([1, 2, 3]);
    });
  });

  describe('Error Handling', () => {
    it('should reject promises on batch error', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async () => {
          throw new Error('Processing failed');
        },
      });

      await expect(batcher.add(1)).rejects.toThrow('Processing failed');
    });

    it('should call onError handler on failure', async () => {
      const errorHandler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async () => {
          throw new Error('Batch error');
        },
        onError: errorHandler,
      });

      try {
        await batcher.add(1);
      } catch (error) {
        // Expected
      }

      expect(errorHandler).toHaveBeenCalledWith(
        expect.any(Error),
        [1]
      );
    });

    it('should emit batchError event', async () => {
      const errorHandler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async () => {
          throw new Error('Test error');
        },
      });

      batcher.on('batchError', errorHandler);

      try {
        await batcher.add(1);
      } catch (error) {
        // Expected
      }

      expect(errorHandler).toHaveBeenCalledWith({
        batchSize: 1,
        error: expect.any(Error),
        timestamp: expect.any(Number),
      });
    });

    it('should reject all items in failed batch', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 100,
        processBatch: async () => {
          throw new Error('Batch failed');
        },
      });

      const promises = [
        batcher.add(1),
        batcher.add(2),
        batcher.add(3),
      ];

      await expect(Promise.all(promises)).rejects.toThrow('Batch failed');
    });

    it('should handle result count mismatch', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 100,
        processBatch: async (items) => {
          // Return wrong number of results
          return items.slice(0, items.length - 1);
        },
      });

      await expect(batcher.add(1)).rejects.toThrow('returned 0 results for 1 items');
    });

    it('should handle non-Error exceptions', async () => {
      const errorHandler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async () => {
          throw 'String error';
        },
        onError: errorHandler,
      });

      try {
        await batcher.add(1);
      } catch (error) {
        // Expected
      }

      expect(errorHandler).toHaveBeenCalledWith(
        expect.any(Error),
        [1]
      );
    });

    it('should emit error event if no onError handler', async () => {
      const errorHandler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async () => {
          throw new Error('Test error');
        },
      });

      batcher.on('error', errorHandler);

      try {
        await batcher.add(1);
      } catch (error) {
        // Expected
      }

      expect(errorHandler).toHaveBeenCalled();
    });
  });

  describe('flush() method', () => {
    it('should process all queued items immediately', async () => {
      jest.useFakeTimers();
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 10000,
        processBatch: processFn,
      });

      batcher.add(1);
      batcher.add(2);
      batcher.add(3);

      expect(processFn).not.toHaveBeenCalled();

      await batcher.flush();

      expect(processFn).toHaveBeenCalledWith([1, 2, 3]);

      jest.useRealTimers();
    });

    it('should cancel pending timer', async () => {
      jest.useFakeTimers();

      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 10000,
        processBatch: async (items) => items,
      });

      batcher.add(1);
      expect(batcher['timer']).not.toBeNull();

      await batcher.flush();
      expect(batcher['timer']).toBeNull();

      jest.useRealTimers();
    });

    it('should handle empty queue', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      await expect(batcher.flush()).resolves.toBeUndefined();
    });
  });

  describe('getStats() method', () => {
    it('should return current statistics', () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        concurrency: 3,
        processBatch: async (items) => items,
      });

      const stats = batcher.getStats();

      expect(stats).toEqual({
        queueSize: 0,
        processing: 0,
        maxBatchSize: 10,
        maxWaitTime: 1000,
        concurrency: 3,
      });
    });

    it('should reflect current queue size', async () => {
      jest.useFakeTimers();

      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 10000,
        processBatch: async (items) => items,
      });

      batcher.add(1);
      batcher.add(2);

      const stats = batcher.getStats();
      expect(stats.queueSize).toBe(2);

      jest.useRealTimers();
    });
  });

  describe('destroy() method', () => {
    it('should cleanup resources', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      await batcher.destroy();

      expect(batcher['isDestroyed']).toBe(true);
      expect(batcher['timer']).toBeNull();
    });

    it('should reject pending items', async () => {
      jest.useFakeTimers();

      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 10000,
        processBatch: async (items) => items,
      });

      const promise = batcher.add(1);
      await batcher.destroy();

      await expect(promise).rejects.toThrow('BatchProcessor is being destroyed');

      jest.useRealTimers();
    });

    it('should wait for ongoing processing', async () => {
      let processing = false;
      let completed = false;

      batcher = new BatchProcessor({
        maxBatchSize: 1,
        maxWaitTime: 100,
        processBatch: async (items) => {
          processing = true;
          await new Promise(resolve => setTimeout(resolve, 200));
          processing = false;
          completed = true;
          return items;
        },
      });

      const promise = batcher.add(1);

      // Wait a bit for processing to start
      await new Promise(resolve => setTimeout(resolve, 50));

      const destroyPromise = batcher.destroy();

      // Should wait for processing
      expect(processing).toBe(true);
      expect(batcher['isDestroyed']).toBe(true);

      await Promise.all([promise, destroyPromise]);
      expect(completed).toBe(true);
    });

    it('should emit destroy event', async () => {
      const handler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      batcher.on('destroy', handler);
      await batcher.destroy();

      expect(handler).toHaveBeenCalled();
    });

    it('should be idempotent', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      await batcher.destroy();
      await expect(batcher.destroy()).resolves.toBeUndefined();
    });

    it('should remove all listeners', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      batcher.on('itemAdded', jest.fn());
      batcher.on('batchComplete', jest.fn());

      await batcher.destroy();

      expect(batcher.listenerCount('itemAdded')).toBe(0);
      expect(batcher.listenerCount('batchComplete')).toBe(0);
    });
  });

  describe('Events', () => {
    it('should emit batchStart event', async () => {
      const handler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 100,
        processBatch: async (items) => items,
      });

      batcher.on('batchStart', handler);
      await batcher.addMany([1, 2, 3]);

      expect(handler).toHaveBeenCalledWith({
        batchSize: 3,
        timestamp: expect.any(Number),
      });
    });

    it('should emit batchComplete event', async () => {
      const handler = jest.fn();

      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 100,
        processBatch: async (items) => items,
      });

      batcher.on('batchComplete', handler);
      await batcher.addMany([1, 2, 3]);

      expect(handler).toHaveBeenCalledWith({
        batchSize: 3,
        duration: expect.any(Number),
        timestamp: expect.any(Number),
      });
    });
  });

  describe('Helper Functions', () => {
    describe('createBatchProcessor', () => {
      it('should create batch processor', () => {
        batcher = createBatchProcessor({
          maxBatchSize: 10,
          maxWaitTime: 1000,
          processBatch: async (items) => items,
        });

        expect(batcher).toBeInstanceOf(BatchProcessor);
      });

      it('should work correctly', async () => {
        batcher = createBatchProcessor({
          maxBatchSize: 5,
          maxWaitTime: 100,
          processBatch: async (items) => items,
        });

        const result = await batcher.add(42);
        expect(result).toBe(42);
      });
    });

    describe('createSimpleBatchProcessor', () => {
      it('should create batch processor with minimal config', () => {
        batcher = createSimpleBatchProcessor(
          10,
          1000,
          async (items) => items
        );

        expect(batcher).toBeInstanceOf(BatchProcessor);
      });

      it('should work correctly', async () => {
        batcher = createSimpleBatchProcessor(
          5,
          100,
          async (items) => items.map(x => x * 2)
        );

        const result = await batcher.add(5);
        expect(result).toBe(10);
      });
    });
  });

  describe('Edge Cases', () => {
    it('should handle single item batches', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 1,
        maxWaitTime: 100,
        processBatch: async (items) => items,
      });

      const result = await batcher.add(42);
      expect(result).toBe(42);
    });

    it('should handle very large batches', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 1000,
        maxWaitTime: 1000,
        processBatch: async (items) => items,
      });

      const items = Array.from({ length: 500 }, (_, i) => i);
      const results = await batcher.addMany(items);

      expect(results).toEqual(items);
    });

    it('should handle rapid additions', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 100,
        processBatch: async (items) => items,
      });

      const promises = [];
      for (let i = 0; i < 50; i++) {
        promises.push(batcher.add(i));
      }

      const results = await Promise.all(promises);
      expect(results.length).toBe(50);
    });

    it('should handle zero wait time', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 10,
        maxWaitTime: 0,
        processBatch: async (items) => items,
      });

      const result = await batcher.add(1);
      expect(result).toBe(1);
    });

    it('should handle async errors in processBatch', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: async () => {
          await new Promise(resolve => setTimeout(resolve, 10));
          throw new Error('Async error');
        },
      });

      await expect(batcher.add(1)).rejects.toThrow('Async error');
    });
  });

  describe('Complex Scenarios', () => {
    it('should handle mixed batch sizes', async () => {
      const processFn = jest.fn(async (items) => items);

      batcher = new BatchProcessor({
        maxBatchSize: 5,
        maxWaitTime: 100,
        processBatch: processFn,
      });

      // First full batch
      await batcher.addMany([1, 2, 3, 4, 5]);

      // Partial batch
      await batcher.addMany([6, 7]);

      expect(processFn).toHaveBeenCalled();
    });

    it('should maintain order with multiple batches', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 3,
        maxWaitTime: 100,
        preserveOrder: true,
        processBatch: async (items) => items.map(x => x * 2),
      });

      const input = [1, 2, 3, 4, 5, 6];
      const results = await batcher.addMany(input);

      expect(results).toEqual([2, 4, 6, 8, 10, 12]);
    });

    it('should handle concurrent adds during processing', async () => {
      batcher = new BatchProcessor({
        maxBatchSize: 2,
        maxWaitTime: 100,
        processBatch: async (items) => {
          await new Promise(resolve => setTimeout(resolve, 50));
          return items;
        },
      });

      const promises: Promise<any>[] = [];

      // Add items while previous batches are processing
      for (let i = 0; i < 10; i++) {
        promises.push(batcher.add(i));
        if (i % 2 === 0) {
          await new Promise(resolve => setTimeout(resolve, 10));
        }
      }

      const results = await Promise.all(promises);
      expect(results.length).toBe(10);
    });
  });
});
