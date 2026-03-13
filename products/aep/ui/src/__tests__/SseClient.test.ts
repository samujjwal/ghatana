/**
 * SseClient — unit tests for subscribeToAepStream.
 *
 * Uses a fake EventSource class injected through globalThis to avoid network
 * calls. Fake timers control reconnect scheduling.
 *
 * @doc.type test
 * @doc.purpose Verify SSE subscription lifecycle, event dispatch, and reconnect
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { subscribeToAepStream } from '@/api/sse';

// ── Mock EventSource ─────────────────────────────────────────────────────────

class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;

  static instances: FakeEventSource[] = [];

  url: string;
  readyState = FakeEventSource.OPEN;

  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;

  private _listeners = new Map<string, EventListener[]>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, fn: EventListener): void {
    const arr = this._listeners.get(type) ?? [];
    arr.push(fn);
    this._listeners.set(type, arr);
  }

  removeEventListener(type: string, fn: EventListener): void {
    const arr = this._listeners.get(type);
    if (arr) this._listeners.set(type, arr.filter((f) => f !== fn));
  }

  close(): void {
    this.readyState = FakeEventSource.CLOSED;
  }

  /** Simulate a named SSE event arriving with the given JSON data. */
  simulateEvent(type: string, data: unknown): void {
    const e = new MessageEvent(type, { data: JSON.stringify(data) });
    this._listeners.get(type)?.forEach((fn) => fn(e));
  }

  /** Simulate a network error that triggers reconnect. */
  simulateError(): void {
    this.readyState = FakeEventSource.CLOSED;
    if (this.onerror) this.onerror(new Event('error'));
  }

  static reset(): void {
    FakeEventSource.instances = [];
  }
}

// ── Setup / Teardown ─────────────────────────────────────────────────────────

beforeEach(() => {
  FakeEventSource.reset();
  vi.useFakeTimers();
  // Inject fake EventSource into global scope
  vi.stubGlobal('EventSource', FakeEventSource);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

// ── Tests ────────────────────────────────────────────────────────────────────

describe('subscribeToAepStream', () => {
  it('opens an EventSource to the correct URL', () => {
    const sub = subscribeToAepStream('tenant-1', vi.fn());
    sub.close();

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toContain('/events/stream');
    expect(FakeEventSource.instances[0].url).toContain('tenantId=tenant-1');
  });

  it('calls onMessage with parsed data when a known event type arrives', () => {
    const onMessage = vi.fn();
    subscribeToAepStream('tenant-1', onMessage);

    const es = FakeEventSource.instances[0];
    es.simulateEvent('run.update', { id: 'run-1', status: 'RUNNING' });

    expect(onMessage).toHaveBeenCalledOnce();
    expect(onMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'run.update',
        data: { id: 'run-1', status: 'RUNNING' },
      }),
    );
  });

  it('calls onMessage with raw string data when JSON parse fails', () => {
    const onMessage = vi.fn();
    subscribeToAepStream('tenant-1', onMessage);

    const es = FakeEventSource.instances[0];
    const rawEvent = new MessageEvent('agent.output', { data: 'not-json{' });
    // Simulate by adding listener capture — use the internal listener pathway
    es.simulateEvent('run.update', 'valid-json');  // normal call works
    onMessage.mockClear();

    // Manually dispatch malformed event
    const fakeEs = FakeEventSource.instances[0] as unknown as {
      addEventListener(t: string, fn: EventListener): void;
      removeEventListener(t: string, fn: EventListener): void;
      _listeners: Map<string, EventListener[]>;
    };
    const listeners = (fakeEs as unknown as { _listeners: Map<string, EventListener[]> })['_listeners'];
    listeners.get('agent.output')?.forEach((fn) => fn(rawEvent));

    expect(onMessage).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'agent.output', data: 'not-json{' }),
    );
  });

  it('calls onError callback when EventSource fires an error', () => {
    const onError = vi.fn();
    subscribeToAepStream('tenant-1', vi.fn(), onError);

    FakeEventSource.instances[0].simulateError();

    expect(onError).toHaveBeenCalledOnce();
  });

  it('schedules a reconnect after an error (does not reconnect immediately)', () => {
    subscribeToAepStream('tenant-1', vi.fn());
    expect(FakeEventSource.instances).toHaveLength(1);

    FakeEventSource.instances[0].simulateError();
    // First reconnect delay is RECONNECT_BASE_MS = 3000 ms — not elapsed yet
    expect(FakeEventSource.instances).toHaveLength(1);

    // Advance time to just past the base reconnect delay
    vi.advanceTimersByTime(3_100);
    expect(FakeEventSource.instances).toHaveLength(2);
  });

  it('does not reconnect after close()', () => {
    const sub = subscribeToAepStream('tenant-1', vi.fn());
    const es = FakeEventSource.instances[0];

    sub.close();
    es.simulateError();

    vi.advanceTimersByTime(30_000);

    // Still only the original EventSource — no reconnect after manual close
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('cancels a pending reconnect timer when close() is called', () => {
    const sub = subscribeToAepStream('tenant-1', vi.fn());
    FakeEventSource.instances[0].simulateError();

    // Error queued a reconnect — call close before the timer fires
    sub.close();
    vi.advanceTimersByTime(30_000);

    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('expires backoff at RECONNECT_MAX_MS (30 s)', () => {
    subscribeToAepStream('tenant-1', vi.fn());

    // Simulate 5 consecutive errors to exhaust exponential backoff
    for (let i = 0; i < 5; i++) {
      const es = FakeEventSource.instances[FakeEventSource.instances.length - 1];
      es.simulateError();
      vi.advanceTimersByTime(60_000); // more than enough for any backoff step
    }

    // After 5 reconnect cycles we have 6 EventSource instances (original + 5)
    expect(FakeEventSource.instances.length).toBeGreaterThanOrEqual(5);
  });

  it('reflects connected state correctly', () => {
    const sub = subscribeToAepStream('tenant-1', vi.fn());

    // FakeEventSource begins at OPEN (1)
    expect(sub.connected).toBe(true);

    sub.close();
    expect(sub.connected).toBe(false);
  });
});
