import { SemaphoreBulkhead, BulkheadRejectedError, BulkheadQueueTimeoutError } from '../SemaphoreBulkhead';

describe('SemaphoreBulkhead', () => {
  it('executes within capacity without queuing', async () => {
    const bulkhead = new SemaphoreBulkhead({ maxConcurrent: 2, name: 'test' });
    const results = await Promise.all([
      bulkhead.execute(() => Promise.resolve('a')),
      bulkhead.execute(() => Promise.resolve('b')),
    ]);
    expect(results).toEqual(['a', 'b']);
    expect(bulkhead.concurrent).toBe(0);
  });

  it('queues overflow and executes in order', async () => {
    const bulkhead = new SemaphoreBulkhead({ maxConcurrent: 1, maxQueue: 2, queueTimeoutMs: 2000 });
    const order: number[] = [];

    const first = bulkhead.execute(async () => {
      await new Promise(r => setTimeout(r, 50));
      order.push(1);
    });
    const second = bulkhead.execute(async () => { order.push(2); });
    const third  = bulkhead.execute(async () => { order.push(3); });

    await Promise.all([first, second, third]);
    expect(order).toEqual([1, 2, 3]);
  });

  it('rejects when queue is full', async () => {
    const bulkhead = new SemaphoreBulkhead({ maxConcurrent: 1, maxQueue: 1, queueTimeoutMs: 5000 });

    // occupy the slot
    bulkhead.execute(() => new Promise(r => setTimeout(r, 500)));
    // fill the queue
    bulkhead.execute(() => Promise.resolve()).catch(() => {});
    // next must be rejected
    await expect(bulkhead.execute(() => Promise.resolve()))
      .rejects.toBeInstanceOf(BulkheadRejectedError);
  });

  it('times out a waiting caller', async () => {
    const bulkhead = new SemaphoreBulkhead({ maxConcurrent: 1, maxQueue: 1, queueTimeoutMs: 50 });

    // occupy the slot
    bulkhead.execute(() => new Promise(r => setTimeout(r, 500))).catch(() => {});
    // enqueue a caller that will timeout
    await expect(bulkhead.execute(() => Promise.resolve()))
      .rejects.toBeInstanceOf(BulkheadQueueTimeoutError);
  });

  it('releases slot when operation throws', async () => {
    const bulkhead = new SemaphoreBulkhead({ maxConcurrent: 1 });
    await expect(bulkhead.execute(() => Promise.reject(new Error('boom')))).rejects.toThrow('boom');
    expect(bulkhead.concurrent).toBe(0);
    // slot should be free now
    await expect(bulkhead.execute(() => Promise.resolve('ok'))).resolves.toBe('ok');
  });

  it('throws on invalid maxConcurrent', () => {
    expect(() => new SemaphoreBulkhead({ maxConcurrent: 0 })).toThrow(RangeError);
  });
});
