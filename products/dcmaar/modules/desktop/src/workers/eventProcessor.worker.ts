// Worker: eventProcessor.worker.ts
// Receives individual events from the main thread, buffers them and emits
// batched messages back to the main thread to avoid frequent main-thread work.

type IncomingMsg =
  | { type: 'event'; payload: unknown }
  | { type: 'raw'; payload: string | any }
  | { type: 'flush' };

const buffer: unknown[] = [];
const FLUSH_THRESHOLD = 100;
const FLUSH_INTERVAL_MS = 200;

let flushTimer: number | null = null;

function scheduleFlush() {
  if (flushTimer != null) return;
  flushTimer = self.setTimeout(() => {
    flushTimer = null;
    flushNow();
  }, FLUSH_INTERVAL_MS) as unknown as number;
}

function flushNow() {
  if (buffer.length === 0) return;
  const batch = buffer.splice(0, buffer.length);

  // Post the batch back to the main thread using the shape expected by the bridge.
  // Main thread will append the batch to the store in one update.
  try {
    self.postMessage({ type: 'batch', events: batch });
  } catch {
    // If posting fails, fall back to posting with payload field for backward compat.
    self.postMessage({ type: 'batch', payload: { batch } });
  }
}

self.onmessage = (ev: MessageEvent<IncomingMsg>) => {
  const msg = ev.data;
  if (msg.type === 'event') {
    buffer.push(msg.payload);
    if (buffer.length >= FLUSH_THRESHOLD) {
      flushNow();
    } else {
      scheduleFlush();
    }
  } else if (msg.type === 'raw') {
    try {
      const raw = msg.payload;
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      const now = Date.now();

      // Handle lightweight pong/handshake messages by notifying main thread
      if (data && data.type === 'pong' && data.id) {
        // Use the shape { type: 'pong', pong: { timestamp } } expected by the bridge
        self.postMessage({ type: 'pong', pong: { timestamp: now } });
        return;
      }

      if (data && data.type === 'handshake' && data.success) {
        // Use the shape { type: 'handshake', handshake: {...} }
        self.postMessage({ type: 'handshake', handshake: { success: true } });
        return;
      }

      // JSON-RPC extension events
      if (data && data.jsonrpc === '2.0' && data.method === 'extension.event' && data.params) {
        const ext = { ...data.params, timestamp: data.params.timestamp || now };
        buffer.push(ext);
        if (buffer.length >= FLUSH_THRESHOLD) flushNow();
        else scheduleFlush();
        return;
      }

      // Other messages: treat as custom events
      if (data && data.type !== 'ping' && data.type !== 'pong') {
        const custom = { type: 'custom', data, timestamp: now };
        buffer.push(custom);
        if (buffer.length >= FLUSH_THRESHOLD) flushNow();
        else scheduleFlush();
        return;
      }
    } catch (err) {
      // Ignore parse errors here; main thread can still process if needed
      self.postMessage({ type: 'error', payload: { message: String(err) } });
    }
  } else if (msg.type === 'flush') {
    flushNow();
  }
};

export {};
