/**
 * Extension Bridge Hook - Manages WebSocket connection with Jotai atoms
 * Replaces extensionStore.ts WebSocket logic with Jotai state updates
 */

import { useEffect, useRef, useCallback } from 'react';
import { useSetAtom } from 'jotai';
import {
  extensionConnectedAtom,
  extensionLatencyAtom,
  extensionLastPingTimeAtom,
  addExtensionEventsAtom,
  ExtensionEvent,
} from '../atoms/extensionAtoms';

const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:3001';
const PING_INTERVAL = 30000; // 30 seconds
const RECONNECT_DELAY_MIN = 1000; // 1 second
const RECONNECT_DELAY_MAX = 30000; // 30 seconds
const MAX_RECONNECT_ATTEMPTS = 10; // Stop after 10 attempts
// Backwards-compat: expose a global alias so older HMRed code that looks for
// `RECONNECT_DELAY` doesn't throw during fast refresh cycles.
(globalThis as any).RECONNECT_DELAY = RECONNECT_DELAY_MIN;

export function useExtensionBridge() {
  const wsRef = useRef<WebSocket | null>(null);
  const workerRef = useRef<Worker | null>(null);
  const workerReadyRef = useRef<boolean>(false);
  const pingIntervalRef = useRef<number | undefined>(undefined);
  const reconnectTimeoutRef = useRef<number | undefined>(undefined);
  const fallbackBufferRef = useRef<ExtensionEvent[]>([]);
  const rawMessageQueueRef = useRef<string[]>([]);
  const flushTimeoutRef = useRef<number | undefined>(undefined);
  const reconnectAttemptsRef = useRef<number>(0);

  const setConnected = useSetAtom(extensionConnectedAtom);
  const setLatency = useSetAtom(extensionLatencyAtom);
  const setLastPingTime = useSetAtom(extensionLastPingTimeAtom);
  const addEvents = useSetAtom(addExtensionEventsAtom);

  // Initialize Web Worker
  useEffect(() => {
    // Avoid creating blob workers when the browser is offline or CSP will block blob workers
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      // Defer worker creation until online
      return;
    }

    try {
      const worker = new Worker(new URL('../workers/eventProcessor.worker.ts', import.meta.url), {
        type: 'module',
      });

      worker.onmessage = event => {
        const { type, events, pong, handshake } = event.data;

        if (type === 'batch' && events) {
          // Batch of processed events from worker
          addEvents(events);
          // Worker processed successfully
          workerReadyRef.current = true;
        } else if (type === 'pong' && pong) {
          // Pong response from worker
          const latency = Date.now() - pong.timestamp;
          setLatency(latency);
          setLastPingTime(null);
        } else if (type === 'handshake' && handshake) {
          // Handshake processed
          console.log('[Extension Bridge] Handshake received:', handshake);
        }
      };

      worker.onerror = error => {
        console.error('[Extension Bridge] Worker error:', error);
        // mark worker not ready so we fallback to queued parsing
        workerReadyRef.current = false;
      };

      workerRef.current = worker;
      workerReadyRef.current = true;

      return () => {
        try {
          worker.terminate();
        } catch {
          // ignore termination errors
        }
        workerRef.current = null;
        workerReadyRef.current = false;
      };
    } catch (error) {
      // If worker creation fails (CSP, blob is blocked, etc.) we fall back to main-thread parsing.
      console.error('[Extension Bridge] Failed to initialize worker:', error);
      workerRef.current = null;
      workerReadyRef.current = false;
    }
    // Intentionally ignoring addEvents etc. in deps to keep effect stable; atoms are stable.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fallback processing (when worker unavailable)
  const processFallbackEvent = useCallback(
    (data: string) => {
      try {
        const parsed = JSON.parse(data);

        if (parsed.type === 'pong') {
          const latency = Date.now() - parsed.timestamp;
          setLatency(latency);
          setLastPingTime(null);
          return;
        }

        if (parsed.type === 'handshake') {
          console.log('[Extension Bridge] Handshake received:', parsed);
          return;
        }

        if (parsed.type === 'extension.event') {
          const event: ExtensionEvent = {
            ...parsed.data,
            id:
              parsed.data.id || `event_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
            timestamp: parsed.data.timestamp || Date.now(),
          };

          fallbackBufferRef.current.push(event);

          if (flushTimeoutRef.current) {
            clearTimeout(flushTimeoutRef.current);
          }

          if (fallbackBufferRef.current.length >= 200) {
            // Flush immediately
            addEvents([...fallbackBufferRef.current]);
            fallbackBufferRef.current = [];
          } else {
            // Schedule flush
            flushTimeoutRef.current = window.setTimeout(() => {
              if (fallbackBufferRef.current.length > 0) {
                addEvents([...fallbackBufferRef.current]);
                fallbackBufferRef.current = [];
              }
            }, 120);
          }
        }
      } catch (error) {
        console.error('[Extension Bridge] Fallback parse error:', error);
      }
    },
    [addEvents, setLatency, setLastPingTime]
  );

  // Schedule deferred flush of raw message queue using requestIdleCallback or setTimeout
  const scheduleRawQueueFlush = useCallback(() => {
    const flush = () => {
      const q = rawMessageQueueRef.current.splice(0, rawMessageQueueRef.current.length);
      q.forEach(data => {
        try {
          processFallbackEvent(data);
        } catch (err) {
          console.error('[Extension Bridge] Deferred parse error:', err);
        }
      });
    };

    if (typeof (window as any).requestIdleCallback === 'function') {
      (window as any).requestIdleCallback(() => flush(), { timeout: 250 });
    } else {
      if (flushTimeoutRef.current) clearTimeout(flushTimeoutRef.current);
      flushTimeoutRef.current = window.setTimeout(flush, 120);
    }
  }, [processFallbackEvent]);

  // WebSocket message handler
  const handleMessage = useCallback(
    (event: MessageEvent) => {
      // If worker available and ready, forward to worker
      if (workerRef.current && workerReadyRef.current) {
        try {
          workerRef.current.postMessage({ type: 'raw', payload: event.data });
          return;
        } catch (err) {
          console.error('[Extension Bridge] Worker post error:', err);
          workerReadyRef.current = false;
        }
      }

      // Otherwise enqueue for deferred parsing on idle
      try {
        rawMessageQueueRef.current.push(String(event.data));
      } catch (err) {
        console.error('[Extension Bridge] Queue push failed, parsing immediately:', err);
        processFallbackEvent(String(event.data));
        return;
      }

      scheduleRawQueueFlush();
    },
    [processFallbackEvent, scheduleRawQueueFlush]
  );

  // Send ping
  const sendPing = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      const timestamp = Date.now();
      setLastPingTime(timestamp);
      wsRef.current.send(JSON.stringify({ type: 'ping', timestamp }));
    }
  }, [setLastPingTime]);

  // Calculate exponential backoff delay
  const getReconnectDelay = useCallback(() => {
    const attempts = reconnectAttemptsRef.current;
    const delay = Math.min(RECONNECT_DELAY_MIN * Math.pow(2, attempts), RECONNECT_DELAY_MAX);
    return delay;
  }, []);

  // Connect to WebSocket
  const connect = useCallback(() => {
    // Stop if max attempts reached
    if (reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) {
      console.warn('[Extension Bridge] Max reconnect attempts reached. Extension not available.');
      return;
    }

    // Do not attempt to connect while offline; wait for the 'online' event.
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      console.log('[Extension Bridge] Offline - deferring connection until online');
      return;
    }

    if (wsRef.current?.readyState === WebSocket.OPEN) {
      return; // Already connected
    }

    try {
      const ws = new WebSocket(WS_URL);

      ws.onopen = () => {
        console.log('[Extension Bridge] WebSocket connected');
        setConnected(true);

        // Reset reconnect attempts on successful connection
        reconnectAttemptsRef.current = 0;

        // Send initial handshake
        ws.send(
          JSON.stringify({
            type: 'handshake',
            clientType: 'admin_dashboard',
            version: '1.0.0',
          })
        );

        // Start ping interval
        if (pingIntervalRef.current) {
          clearInterval(pingIntervalRef.current);
        }
        pingIntervalRef.current = window.setInterval(sendPing, PING_INTERVAL);
      };

      ws.onmessage = handleMessage;

      ws.onerror = error => {
        console.error('[Extension Bridge] WebSocket error:', error);
      };

      ws.onclose = () => {
        console.log('[Extension Bridge] WebSocket closed');
        setConnected(false);

        if (pingIntervalRef.current) {
          clearInterval(pingIntervalRef.current);
        }

        // Increment retry counter and schedule reconnect with exponential backoff
        reconnectAttemptsRef.current++;
        const delay = getReconnectDelay();

        if (reconnectAttemptsRef.current < MAX_RECONNECT_ATTEMPTS) {
          console.log(
            `[Extension Bridge] Reconnecting in ${delay}ms (attempt ${reconnectAttemptsRef.current}/${MAX_RECONNECT_ATTEMPTS})...`
          );
          reconnectTimeoutRef.current = window.setTimeout(() => {
            connect();
          }, delay);
        }
      };

      wsRef.current = ws;
    } catch (error) {
      console.error('[Extension Bridge] Connection failed:', error);
      setConnected(false);

      // Increment retry counter and schedule reconnect
      reconnectAttemptsRef.current++;
      const delay = getReconnectDelay();

      if (reconnectAttemptsRef.current < MAX_RECONNECT_ATTEMPTS) {
        reconnectTimeoutRef.current = window.setTimeout(connect, delay);
      }
    }
  }, [setConnected, sendPing, handleMessage, getReconnectDelay]);

  // Disconnect
  const disconnect = useCallback(() => {
    if (pingIntervalRef.current) {
      clearInterval(pingIntervalRef.current);
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    setConnected(false);
  }, [setConnected]);

  // Auto-connect on mount
  useEffect(() => {
    // If online, connect immediately. If offline, wait for the browser 'online' event.
    if (typeof navigator !== 'undefined' && navigator.onLine) {
      connect();
    } else if (typeof window !== 'undefined') {
      const onOnline = () => connect();
      window.addEventListener('online', onOnline);

      return () => {
        window.removeEventListener('online', onOnline);
      };
    }

    return () => {
      disconnect();
      if (flushTimeoutRef.current) {
        clearTimeout(flushTimeoutRef.current);
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [connect, disconnect]);

  return {
    connect,
    disconnect,
    sendPing,
  };
}
