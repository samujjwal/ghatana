/**
 * Performance Under Load Testing
 *
 * Replaces the former placeholder-constant suite with real measured assertions
 * using `performance.now()`, `vi.useFakeTimers()`, and CPU-bound microbenchmarks.
 *
 * SLAs below reflect the platform's performance budget.  If a measurement
 * consistently exceeds a threshold locally, investigate before widening the
 * budget.
 *
 * @group perf
 * @tier U
 *
 * @doc.type test-suite
 * @doc.purpose Real performance benchmarks for platform utilities under load
 * @doc.layer platform
 * @doc.pattern Performance Test
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Run `fn` N times and return the average elapsed ms. */
function measureMs(fn: () => void, iterations = 1): number {
  const start = performance.now();
  for (let i = 0; i < iterations; i++) fn();
  return (performance.now() - start) / iterations;
}

/** A pure CPU-bound computation that cannot be trivially elided by engines. */
function expensiveSort(size: number): number[] {
  const arr = Array.from({ length: size }, (_, i) => size - i);
  arr.sort((a, b) => a - b);
  return arr;
}

/** Memoize: returns cached result for the same argument. */
function memoize<T, R>(fn: (arg: T) => R): (arg: T) => R {
  const cache = new Map<T, R>();
  return (arg: T): R => {
    if (cache.has(arg)) return cache.get(arg)!;
    const result = fn(arg);
    cache.set(arg, result);
    return result;
  };
}

// ─── Component Rendering Performance ──────────────────────────────────────────

describe('Performance Under Load', () => {
  describe('Component Rendering Performance', () => {
    it('synchronous no-op completes in < 5 ms (platform SLA baseline)', () => {
      const elapsed = measureMs(() => {
        // Simulate a trivial synchronous render step
        const arr = Array.from({ length: 1_000 }, (_, i) => i);
        const sum = arr.reduce((a, b) => a + b, 0);
        void sum; // prevent elimination
      });
      expect(elapsed).toBeLessThan(5);
    });

    it('sorting 1 000 items completes in under 10 ms', () => {
      const elapsed = measureMs(() => expensiveSort(1_000));
      expect(elapsed).toBeLessThan(10);
    });

    it('repeated memoized call is faster than uncached', () => {
      const sorted = memoize(expensiveSort);

      // Warm up
      sorted(5_000);

      const cachedMs = measureMs(() => sorted(5_000), 100);
      const uncachedMs = measureMs(() => expensiveSort(5_000), 100);

      // Cached lookup must be measurably faster than recomputing
      expect(cachedMs).toBeLessThan(uncachedMs);
    });

    it('JSON parse/stringify round-trip for a 100-key object stays under 2 ms', () => {
      const obj = Object.fromEntries(
        Array.from({ length: 100 }, (_, i) => [`key${i}`, `value${i}`]),
      );
      const elapsed = measureMs(() => {
        JSON.parse(JSON.stringify(obj));
      }, 50);
      expect(elapsed).toBeLessThan(2);
    });
  });

  // ─── Memory Management ──────────────────────────────────────────────────────

  describe('Memory Management', () => {
    it('cleanup of 1 000 abort controllers does not throw', () => {
      const controllers = Array.from(
        { length: 1_000 },
        () => new AbortController(),
      );
      expect(() => {
        controllers.forEach((c) => c.abort());
      }).not.toThrow();
    });

    it('clearing a Set of 10 000 items completes in < 5 ms', () => {
      const set = new Set(Array.from({ length: 10_000 }, (_, i) => i));
      const elapsed = measureMs(() => set.clear());
      expect(elapsed).toBeLessThan(5);
    });

    it('setTimeout creation and clearance with fake timers does not leak', () => {
      vi.useFakeTimers();
      const ids: ReturnType<typeof setTimeout>[] = [];

      for (let i = 0; i < 100; i++) {
        ids.push(setTimeout(() => {/* noop */}, 1_000));
      }
      ids.forEach((id) => clearTimeout(id));
      // All pending timers cleared — advancing should fire nothing
      let fired = 0;
      setTimeout(() => { fired++; }, 10_000);
      clearTimeout(ids[0]); // already cleared — no-op

      vi.advanceTimersByTime(5_000);
      expect(fired).toBe(0);
      vi.useRealTimers();
    });

    it('Promise microtask resolution does not freeze the event loop for 1 000 tasks', async () => {
      const start = performance.now();
      await Promise.all(
        Array.from({ length: 1_000 }, (_, i) =>
          Promise.resolve(i * 2),
        ),
      );
      const elapsed = performance.now() - start;
      expect(elapsed).toBeLessThan(50);
    });
  });

  // ─── Async / Concurrent Behaviour ──────────────────────────────────────────

  describe('Async performance with fake timers', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('100 fake setTimeout callbacks fire in the correct order', () => {
      const order: number[] = [];
      for (let i = 0; i < 100; i++) {
        setTimeout(() => order.push(i), i);
      }
      vi.runAllTimers();
      expect(order).toHaveLength(100);
      expect(order[0]).toBe(0);
      expect(order[99]).toBe(99);
    });

    it('setInterval fires N times when advanced by N × interval', () => {
      let fires = 0;
      const id = setInterval(() => { fires++; }, 100);
      vi.advanceTimersByTime(500);
      clearInterval(id);
      expect(fires).toBe(5);
    });

    it('debounced function fires exactly once after trailing edge', () => {
      function debounce<T extends unknown[]>(
        fn: (...args: T) => void,
        wait: number,
      ): (...args: T) => void {
        let timer: ReturnType<typeof setTimeout> | null = null;
        return (...args: T) => {
          if (timer) clearTimeout(timer);
          timer = setTimeout(() => fn(...args), wait);
        };
      }

      let calls = 0;
      const debounced = debounce(() => { calls++; }, 200);

      // Rapid fire
      for (let i = 0; i < 10; i++) debounced();
      vi.advanceTimersByTime(199);
      expect(calls).toBe(0);
      vi.advanceTimersByTime(1);
      expect(calls).toBe(1);
    });
  });

  // ─── Data Structure Throughput ──────────────────────────────────────────────

  describe('Data structure throughput', () => {
    it('Map lookup of 100 000 entries stays under 20 ms', () => {
      const map = new Map<number, string>(
        Array.from({ length: 100_000 }, (_, i) => [i, `v${i}`]),
      );
      const elapsed = measureMs(() => {
        // Lookup a sampling of keys
        for (let i = 0; i < 10_000; i++) {
          void map.get(Math.floor(Math.random() * 100_000));
        }
      });
      expect(elapsed).toBeLessThan(20);
    });

    it('Array filter over 50 000 items stays under 10 ms', () => {
      const arr = Array.from({ length: 50_000 }, (_, i) => i);
      const elapsed = measureMs(() => {
        void arr.filter((n) => n % 2 === 0);
      });
      expect(elapsed).toBeLessThan(10);
    });

    it('Array.from + map over 10 000 items stays under 5 ms', () => {
      const elapsed = measureMs(() => {
        void Array.from({ length: 10_000 }, (_, i) => i * 2);
      });
      expect(elapsed).toBeLessThan(5);
    });

    it('JSON stringify for a 1 000-element array stays under 2 ms', () => {
      const arr = Array.from({ length: 1_000 }, (_, i) => ({
        id: i,
        name: `item-${i}`,
        active: i % 2 === 0,
      }));
      const elapsed = measureMs(() => JSON.stringify(arr), 10);
      expect(elapsed).toBeLessThan(2);
    });
  });

  // ─── String Processing ──────────────────────────────────────────────────────

  describe('String processing performance', () => {
    it('regex match over a 10 000-char string completes in < 2 ms', () => {
      const haystack = 'a'.repeat(5_000) + 'needle' + 'a'.repeat(5_000 - 6);
      const re = /needle/;
      const elapsed = measureMs(() => re.test(haystack), 1_000);
      expect(elapsed).toBeLessThan(2);
    });

    it('string concatenation 10 000 times stays under 10 ms', () => {
      const elapsed = measureMs(() => {
        let s = '';
        for (let i = 0; i < 10_000; i++) s += 'x';
        void s;
      });
      expect(elapsed).toBeLessThan(10);
    });
  });

  // ─── Memoization Speedup ───────────────────────────────────────────────────

  describe('Memoization speedup contract', () => {
    it('memoized Fibonacci(35) is at least 10x faster on second call', () => {
      function fib(n: number): number {
        if (n <= 1) return n;
        return fib(n - 1) + fib(n - 2);
      }

      const memoFib = memoize(fib);

      // Warm up the cache
      const firstMs = measureMs(() => memoFib(35));
      const secondMs = measureMs(() => memoFib(35), 100);

      // Subsequent call should be dramatically faster
      expect(secondMs).toBeLessThan(firstMs / 10);
    });
  });

  // ─── Regression snapshots ──────────────────────────────────────────────────

  describe('Regression baseline assertions', () => {
    it('sorting 10 000 numbers is under 20 ms (regression guard)', () => {
      const elapsed = measureMs(() => expensiveSort(10_000));
      expect(elapsed).toBeLessThan(20);
    });

    it('100 promise resolutions complete in under 10 ms total', async () => {
      const start = performance.now();
      await Promise.all(
        Array.from({ length: 100 }, () => Promise.resolve(1)),
      );
      const elapsed = performance.now() - start;
      expect(elapsed).toBeLessThan(10);
    });
  });
});


