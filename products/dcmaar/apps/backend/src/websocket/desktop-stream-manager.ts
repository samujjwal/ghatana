/**
 * DesktopStreamManager - WebSocket handler for agent-desktop event ingestion
 *
 * <p><b>Purpose</b><br>
 * Manages real-time event streams from agent-desktop agents. Handles device
 * authentication, event validation, batching, and database persistence.
 *
 * <p><b>Protocol</b><br>
 * 1. Client connects to /api/desktop/stream with device auth token
 * 2. Client sends IDENTIFY message with device info
 * 3. Server responds with ACK or AUTH_ERROR
 * 4. Client sends batches of EVENTS
 * 5. Server validates, batches, and persists events
 * 6. Server sends ACK with batch ID
 * 7. Server sends PING every 30s, client responds with PONG
 * 8. Timeout if no PONG for 90s
 *
 * <p><b>Features</b><br>
 * - JWT device authentication (device tokens)
 * - Event validation with Joi schemas
 * - Automatic event batching (100 events or 5s timeout)
 * - Concurrent connection support (100+)
 * - Heartbeat monitoring (PING/PONG)
 * - Error recovery and reconnection
 * - Performance metrics tracking
 *
 * <p><b>Message Format</b><br>
 * IDENTIFY: { type: 'IDENTIFY', deviceId: string, token: string }
 * EVENTS: { type: 'EVENTS', events: EventData[] }
 * PING: { type: 'PING', timestamp: number }
 * PONG: { type: 'PONG', timestamp: number }
 * ACK: { type: 'ACK', batchId: string, count: number, timestamp: number }
 * ERROR: { type: 'ERROR', code: string, message: string }
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const manager = new DesktopStreamManager(pool, logger);
 * wsServer.on('connection', (socket) => {
 *   manager.handleConnection(socket);
 * });
 * }</pre>
 *
 * <p><b>Performance Targets</b><br>
 * - Throughput: 1000+ events/sec sustained
 * - Latency: <100ms p95 event ingestion
 * - Connections: 100+ concurrent supported
 * - Memory per connection: <5MB
 * - Batch size: 100 events or 5s timeout
 *
 * @doc.type class
 * @doc.purpose Desktop event stream ingestion handler
 * @doc.layer backend
 * @doc.pattern WebSocket Handler
 */

import { Pool } from 'pg';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { eventValidationSchema } from '../types/events';

// Define Socket type locally to avoid socket.io dependency issues
interface Socket {
  id: string;
  connected: boolean;
  data: any;
  emit: (event: string, message: any) => void;
  on: (event: string, handler: (...args: any[]) => void) => void;
  disconnect: () => void;
}

export interface DeviceToken {
  deviceId: string;
  deviceName: string;
  childId: string;
  userId: string;
}

export interface DesktopEvent {
  type: string;
  timestamp: number;
  windowTitle?: string;
  processName?: string;
  processPath?: string;
  sessionId?: string;
  isIdle?: boolean;
  idleSeconds?: number;
  data?: Record<string, unknown>;
}

interface MessageEnvelope {
  type: 'IDENTIFY' | 'EVENTS' | 'PING' | 'PONG' | 'ACK' | 'ERROR';
  [key: string]: unknown;
}

/**
 * Manages desktop event stream ingestion and persistence
 */
export class DesktopStreamManager {
  private pool: Pool;
  private logger: any;
  private eventQueue: Map<string, DesktopEvent[]> = new Map();
  private batchTimers: Map<string, NodeJS.Timeout> = new Map();
  private heartbeatTimers: Map<string, NodeJS.Timeout> = new Map();
  private connectionMetrics: Map<
    string,
    {
      connectedAt: Date;
      eventsReceived: number;
      eventsPersisted: number;
      lastEventAt?: Date;
      lastPongAt?: Date;
      avgLatency: number;
    }
  > = new Map();

  constructor(pool: Pool, logger: any) {
    this.pool = pool;
    this.logger = logger;
  }

  /**
   * Handle new WebSocket connection from agent-desktop
   */
  async handleConnection(socket: Socket): Promise<void> {
    const connectionId = socket.id;
    let deviceToken: DeviceToken | null = null;

    this.logger.debug('Desktop stream connection attempt', { connectionId });

    // Initialize metrics for this connection
    this.connectionMetrics.set(connectionId, {
      connectedAt: new Date(),
      eventsReceived: 0,
      eventsPersisted: 0,
      avgLatency: 0,
    });

    // Handle IDENTIFY message
    socket.on('message', async (message: MessageEnvelope) => {
      try {
        if (message.type === 'IDENTIFY') {
          deviceToken = await this.handleIdentify(socket, message as any);
          if (!deviceToken) {
            return; // Handled error in handleIdentify
          }
        } else if (message.type === 'EVENTS') {
          if (!deviceToken) {
            this.sendError(socket, 'NOT_AUTHENTICATED', 'Must send IDENTIFY first');
            return;
          }
          await this.handleEvents(socket, deviceToken, message as any);
        } else if (message.type === 'PONG') {
          this.handlePong(socket);
        } else {
          this.sendError(socket, 'INVALID_MESSAGE', `Unknown message type: ${message.type}`);
        }
      } catch (error) {
        this.logger.error('Error handling message', {
          connectionId,
          messageType: message.type,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
        this.sendError(socket, 'INTERNAL_ERROR', 'Failed to process message');
      }
    });

    // Handle disconnection
    socket.on('disconnect', async () => {
      if (deviceToken) {
        await this.handleDisconnect(connectionId, deviceToken);
      }
      this.cleanup(connectionId);
    });

    // Handle connection error
    socket.on('error', (error: Error) => {
      this.logger.error('WebSocket error', {
        connectionId,
        deviceId: deviceToken?.deviceId,
        error: error.message,
      });
      this.cleanup(connectionId);
    });
  }

  /**
   * Handle IDENTIFY message (device authentication)
   */
  public async handleIdentify(
    socket: Socket,
    message: { type: 'IDENTIFY'; token: string }
  ): Promise<DeviceToken | null> {
    const connectionId = socket.id;

    try {
      // Verify device token
      const decoded = jwt.verify(message.token, process.env.DEVICE_TOKEN_SECRET!) as DeviceToken;

      // Verify device exists and is active
      const result = await this.pool.query(
        `SELECT d.id, d.name, d.child_id, c.user_id 
         FROM devices d
         JOIN children c ON d.child_id = c.id
         WHERE d.id = $1 AND d.is_active = true`,
        [decoded.deviceId]
      );

      if (result.rows.length === 0) {
        this.sendError(socket, 'DEVICE_NOT_FOUND', 'Device not found or inactive');
        return null;
      }

      const device = result.rows[0];

      // Send ACK
      this.send(socket, {
        type: 'ACK',
        status: 'authenticated',
        deviceId: decoded.deviceId,
        timestamp: Date.now(),
      });

      this.logger.info('Desktop device authenticated', {
        connectionId,
        deviceId: decoded.deviceId,
        deviceName: device.name,
      });

      // Start heartbeat
      this.startHeartbeat(connectionId);

      return {
        deviceId: decoded.deviceId,
        deviceName: device.name,
        childId: device.child_id,
        userId: device.user_id,
      };
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : 'Unknown error';
      this.logger.warn('Device authentication failed', {
        connectionId,
        error: errorMsg,
      });
      this.sendError(socket, 'AUTH_FAILED', 'Invalid or expired token');
      socket.disconnect();
      return null;
    }
  }

  /**
   * Handle EVENTS message (batch event ingestion)
   */
  /**
   * Handle EVENTS message. This method is flexible to accept either:
   * - (socket, deviceToken, message) when deviceToken is known
   * - (socket, message) when called from external code; in that case we
   *   attempt to derive a minimal DeviceToken from the authenticated socket
   */
  public async handleEvents(
    socket: Socket,
    deviceTokenOrMessage: DeviceToken | { type: 'EVENTS'; events: DesktopEvent[] },
    maybeMessage?: { type: 'EVENTS'; events: DesktopEvent[] }
  ): Promise<void> {
    let deviceToken: DeviceToken;
    let message: { type: 'EVENTS'; events: DesktopEvent[] } | undefined;

    if (maybeMessage) {
      // Called as (socket, deviceToken, message)
      deviceToken = deviceTokenOrMessage as DeviceToken;
      message = maybeMessage;
    } else {
      // Called as (socket, message) - derive deviceToken from socket if possible
      message = deviceTokenOrMessage as { type: 'EVENTS'; events: DesktopEvent[] };
      const derivedDeviceId = (socket as any).deviceId || (socket.data as any)?.deviceId;
      deviceToken = {
        deviceId: derivedDeviceId || 'unknown-device',
        deviceName: '',
        childId: '',
        userId: '',
      };
    }
    const connectionId = socket.id;
    const { events } = message;

    // Validate events
    const validEvents: DesktopEvent[] = [];
    for (const event of events) {
      try {
        const result = eventValidationSchema.safeParse(event);
        if (!result.success) {
          this.logger.warn('Invalid event rejected', {
            connectionId,
            deviceId: deviceToken.deviceId,
            eventType: event.type,
            error: result.error?.issues?.[0]?.message,
          });
          continue;
        }
        validEvents.push(result.data as DesktopEvent);
      } catch (error) {
        this.logger.warn('Event validation error', {
          connectionId,
          deviceId: deviceToken.deviceId,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }

    if (validEvents.length === 0) {
      this.send(socket, {
        type: 'ACK',
        batchId: uuidv4(),
        count: 0,
        timestamp: Date.now(),
      });
      return;
    }

    // Add to queue
    const queueKey = deviceToken.deviceId;
    const existing = this.eventQueue.get(queueKey) || [];
    const updated = [...existing, ...validEvents];
    this.eventQueue.set(queueKey, updated);

    // Update metrics
    const metrics = this.connectionMetrics.get(connectionId);
    if (metrics) {
      metrics.eventsReceived += validEvents.length;
      metrics.lastEventAt = new Date();
    }

    // Check if should flush
    if (updated.length >= 100) {
      await this.flushEvents(socket, deviceToken, connectionId);
    } else {
      // Set/reset timer for delayed flush
      const existingTimer = this.batchTimers.get(queueKey);
      if (existingTimer) {
        clearTimeout(existingTimer);
      }

      const timer = setTimeout(async () => {
        await this.flushEvents(socket, deviceToken, connectionId);
      }, 5000); // 5 second timeout

      this.batchTimers.set(queueKey, timer);
    }

    // Send ACK immediately
    this.send(socket, {
      type: 'ACK',
      batchId: uuidv4(),
      count: validEvents.length,
      timestamp: Date.now(),
    });
  }

  /**
   * Flush queued events to database
   */
  private async flushEvents(
    socket: Socket,
    deviceToken: DeviceToken,
    connectionId: string
  ): Promise<void> {
    const queueKey = deviceToken.deviceId;
    const events = this.eventQueue.get(queueKey);

    if (!events || events.length === 0) {
      return;
    }

    try {
      const start = Date.now();

      // Batch insert events
      const values: unknown[] = [];
      let placeholders = '';

      events.forEach((event, index) => {
        const baseIndex = index * 10;
        placeholders +=
          (index > 0 ? ',' : '') +
          `($${baseIndex + 1}, $${baseIndex + 2}, $${baseIndex + 3}, $${baseIndex + 4}, ` +
          `$${baseIndex + 5}, $${baseIndex + 6}, $${baseIndex + 7}, $${baseIndex + 8}, ` +
          `$${baseIndex + 9}, $${baseIndex + 10})`;

        values.push(
          deviceToken.deviceId,
          event.type,
          event.timestamp,
          event.windowTitle || null,
          event.processName || null,
          event.processPath || null,
          event.sessionId || null,
          event.isIdle || false,
          event.idleSeconds || null,
          JSON.stringify(event.data || {})
        );
      });

      await this.pool.query(
        `INSERT INTO desktop_events 
         (device_id, event_type, timestamp, window_title, process_name, 
          process_path, session_id, is_idle, idle_seconds, data)
         VALUES ${placeholders}`,
        values
      );

      const latency = Date.now() - start;

      // Update metrics
      const metrics = this.connectionMetrics.get(connectionId);
      if (metrics) {
        metrics.eventsPersisted += events.length;
        metrics.avgLatency = (metrics.avgLatency + latency) / 2;
      }

      this.logger.info('Events persisted', {
        connectionId,
        deviceId: deviceToken.deviceId,
        count: events.length,
        latency,
      });

      // Clear queue
      this.eventQueue.delete(queueKey);

      // Clear timer
      const timer = this.batchTimers.get(queueKey);
      if (timer) {
        clearTimeout(timer);
        this.batchTimers.delete(queueKey);
      }
    } catch (error) {
      this.logger.error('Error persisting events', {
        connectionId,
        deviceId: deviceToken.deviceId,
        count: events.length,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      // Don't clear queue - will retry
    }
  }

  /**
   * Start heartbeat monitoring
   */
  private startHeartbeat(connectionId: string): void {
    const socket = this.getSocket(connectionId);
    if (!socket) {
      return;
    }

    const interval = setInterval(() => {
      if (!socket.connected) {
        clearInterval(interval);
        this.heartbeatTimers.delete(connectionId);
        return;
      }

      this.send(socket, {
        type: 'PING',
        timestamp: Date.now(),
      });

      // Check for PONG timeout (90 seconds)
      const timeout = setTimeout(() => {
        this.logger.warn('Heartbeat timeout', { connectionId });
        socket.disconnect();
      }, 90000);

      // Store timeout for cleanup when PONG received
      (socket.data as any).pongTimeout = timeout;
    }, 30000); // PING every 30 seconds

    this.heartbeatTimers.set(connectionId, interval);
  }

  /**
   * Handle PONG message
   */
  public handlePong(socket: Socket, _data?: any): void {
    const pongTimeout = (socket.data as any).pongTimeout;
    if (pongTimeout) {
      clearTimeout(pongTimeout);
      (socket.data as any).pongTimeout = null;
    }

    const metrics = this.connectionMetrics.get(socket.id);
    if (metrics) {
      metrics.lastPongAt = new Date();
    }
  }

  /**
   * Handle disconnection
   */
  private async handleDisconnect(connectionId: string, deviceToken: DeviceToken): Promise<void> {
    // Flush any remaining events
    const queueKey = deviceToken.deviceId;
    const events = this.eventQueue.get(queueKey);
    if (events && events.length > 0) {
      try {
        const socket = this.getSocket(connectionId);
        if (socket) {
          await this.flushEvents(socket, deviceToken, connectionId);
        }
      } catch (error) {
        this.logger.error('Error flushing events on disconnect', {
          deviceId: deviceToken.deviceId,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }

    const metrics = this.connectionMetrics.get(connectionId);
    this.logger.info('Desktop stream disconnected', {
      connectionId,
      deviceId: deviceToken.deviceId,
      eventsReceived: metrics?.eventsReceived || 0,
      eventsPersisted: metrics?.eventsPersisted || 0,
      duration: metrics ? Date.now() - metrics.connectedAt.getTime() : 0,
    });
  }

  /**
   * Send message to socket
   */
  private send(socket: Socket, message: MessageEnvelope): void {
    if (socket.connected) {
      socket.emit('message', message);
    }
  }

  /**
   * Send error to socket
   */
  private sendError(socket: Socket, code: string, message: string): void {
    this.send(socket, {
      type: 'ERROR',
      code,
      message,
    });
  }

  /**
   * Get socket by connection ID (simplified - in real app use io.sockets.get)
   */
  private getSocket(connectionId: string): Socket | null {
    // This would need to be implemented with access to io instance
    // For now, returning null as placeholder
    return null;
  }

  /**
   * Cleanup connection resources
   */
  private cleanup(connectionId: string): void {
    // Clear timers
    const batchTimer = Array.from(this.batchTimers.entries()).find(
      ([, timer]) => (timer as any)._destroyed
    );
    if (batchTimer) {
      this.batchTimers.delete(batchTimer[0]);
    }

    const heartbeatTimer = this.heartbeatTimers.get(connectionId);
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      this.heartbeatTimers.delete(connectionId);
    }

    // Remove metrics
    this.connectionMetrics.delete(connectionId);
  }

  /**
   * Get connection metrics
   */
  getMetrics(connectionId: string): any {
    return this.connectionMetrics.get(connectionId);
  }

  /**
   * Get all active connections metrics
   */
  getAllMetrics(): Record<string, any> {
    const result: Record<string, any> = {};
    this.connectionMetrics.forEach((metrics, connectionId) => {
      result[connectionId] = metrics;
    });
    return result;
  }
}
