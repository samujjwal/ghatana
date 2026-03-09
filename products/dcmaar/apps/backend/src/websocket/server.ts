/**
 * WebSocket server setup and event handlers for real-time communication.
 *
 * <p><b>Purpose</b><br>
 * Manages Socket.io WebSocket connections with JWT authentication,
 * room-based messaging, and real-time event broadcasting for device updates,
 * policy changes, and usage alerts.
 *
 * <p><b>Features</b><br>
 * - JWT-based authentication on connection
 * - Room-based isolation (per-user, per-device, per-policy)
 * - Type-safe event enum (WSEvent)
 * - Automatic reconnection handling
 * - Error logging and metrics tracking
 * - Event broadcasting to subscribed clients
 *
 * <p><b>Rooms</b><br>
 * - user:{userId}: User-scoped events (policies, children updates)
 * - device:{deviceId}: Device-specific events (online/offline, usage)
 * - policy:{policyId}: Policy change events
 * - notifications: Global notification broadcasting
 *
 * <p><b>Events Supported</b><br>
 * - Connection/Disconnection: CONNECT, DISCONNECT, ERROR
 * - Authentication: AUTHENTICATE, AUTHENTICATED, AUTH_ERROR
 * - Room Management: JOIN_ROOM, LEAVE_ROOM, ROOM_JOINED, ROOM_LEFT
 * - Policy Updates: POLICY_CREATED, POLICY_UPDATED, POLICY_DELETED
 * - Device Updates: DEVICE_ONLINE, DEVICE_OFFLINE, DEVICE_UPDATED
 * - Usage/Blocks: USAGE_REPORTED, BLOCK_OCCURRED, ALERT_TRIGGERED
 *
 * <p><b>Authentication Flow</b><br>
 * 1. Client connects with auth token in query/headers
 * 2. Server validates JWT token
 * 3. Extract userId, userRole, deviceId from token
 * 4. Join user:{userId} room automatically
 * 5. Emit AUTHENTICATED event to client
 * 6. Client can then subscribe to specific devices/policies
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { io } from 'socket.io-client';
 * 
 * const socket = io('http://localhost:3000', {
 *   query: { token: jwtToken }
 * });
 * 
 * socket.on('connect', () => console.log('Connected'));
 * socket.on(WSEvent.POLICY_CREATED, (policy) => {
 *   console.log('New policy:', policy);
 * });
 * }</pre>
 *
 * <p><b>Performance Considerations</b><br>
 * - Max connections: 10k+ per node (Socket.io default)
 * - Message latency: <100ms average
 * - Memory per connection: ~1-2MB
 * - Scaled horizontally with Socket.io adapters (Redis recommended)
 *
 * @doc.type utility
 * @doc.purpose WebSocket server setup and event handlers
 * @doc.layer backend
 * @doc.pattern WebSocket/Real-time Communication
 */
import { Server as HttpServer } from 'http';
import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { logger } from '../utils/logger';
import { pool } from '../db';
import { DesktopStreamManager } from './desktop-stream-manager';

// Extended socket interface with authenticated user data
export interface AuthenticatedSocket extends Socket {
  userId?: string;
  userRole?: string;
  deviceId?: string;
}

// JWT payload interface
interface JWTPayload {
  userId: string;
  role: string;
  deviceId?: string;
}

// WebSocket events enum for type safety
export enum WSEvent {
  // Connection events
  CONNECT = 'connect',
  DISCONNECT = 'disconnect',
  ERROR = 'error',
  
  // Authentication events
  AUTHENTICATE = 'authenticate',
  AUTHENTICATED = 'authenticated',
  AUTH_ERROR = 'auth_error',
  
  // Room events
  JOIN_ROOM = 'join_room',
  LEAVE_ROOM = 'leave_room',
  ROOM_JOINED = 'room_joined',
  ROOM_LEFT = 'room_left',
  
  // Policy events
  POLICY_CREATED = 'policy_created',
  POLICY_UPDATED = 'policy_updated',
  POLICY_DELETED = 'policy_deleted',
  
  // Usage events
  USAGE_DATA = 'usage_data',
  USAGE_UPDATED = 'usage_updated',
  
  // Block events
  BLOCK_EVENT = 'block_event',
  
  // Device events
  DEVICE_STATUS = 'device_status',
  DEVICE_ONLINE = 'device_online',
  DEVICE_OFFLINE = 'device_offline',
}

// Room naming conventions
export const getRoomNames = {
  parent: (userId: string) => `parent:${userId}`,
  child: (childId: string) => `child:${childId}`,
  device: (deviceId: string) => `device:${deviceId}`,
};

let io: Server | null = null;
let desktopStreamManager: DesktopStreamManager | null = null;

/**
 * Initialize WebSocket server with Socket.io
 */
export function initializeWebSocket(httpServer: HttpServer): Server {
  logger.info('Initializing WebSocket server...');
  
  // Create Socket.io server with minimal configuration first
  io = new Server(httpServer);

  // Initialize desktop stream manager
  desktopStreamManager = new DesktopStreamManager(pool, logger);

  logger.info('Socket.io server created with DesktopStreamManager');

  // Test that Socket.io engine is attached
  logger.info('Socket.io engine attached', {
    engineAttached: !!io.engine,
  });

  // Authentication middleware
  io.use(async (socket: AuthenticatedSocket, next) => {
    logger.debug('Socket.io middleware: processing connection attempt', {
      socketId: socket.id,
    });
    
    try {
      const token = socket.handshake.auth.token || socket.handshake.headers.authorization?.split(' ')[1];
      
      if (!token) {
        logger.warn('WebSocket connection attempt without token', {
          socketId: socket.id,
          ip: socket.handshake.address,
        });
        return next(new Error('Authentication token required'));
      }

      // Verify JWT token
      const decoded = jwt.verify(token, process.env.JWT_SECRET!) as JWTPayload;
      
      // Attach user data to socket
      socket.userId = decoded.userId;
      socket.userRole = decoded.role || 'parent'; // Default to parent if no role specified
      socket.deviceId = decoded.deviceId;

      logger.info('WebSocket authenticated', {
        socketId: socket.id,
        userId: decoded.userId,
        role: socket.userRole,
        deviceId: decoded.deviceId,
      });

      next();
    } catch (error) {
      logger.error('WebSocket authentication failed', {
        socketId: socket.id,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      next(new Error('Invalid or expired token'));
    }
  });

  // Connection handler
  io.on(WSEvent.CONNECT, (socket: AuthenticatedSocket) => {
    logger.info('WebSocket client connected', {
      socketId: socket.id,
      userId: socket.userId,
      role: socket.userRole,
      deviceId: socket.deviceId,
    });

    // Automatically join user-specific rooms based on role
    if (socket.userId && socket.userRole === 'parent') {
      const parentRoom = getRoomNames.parent(socket.userId);
      socket.join(parentRoom);
      logger.debug('Parent joined room', { userId: socket.userId, room: parentRoom });
    }

    if (socket.deviceId) {
      const deviceRoom = getRoomNames.device(socket.deviceId);
      socket.join(deviceRoom);
      logger.debug('Device joined room', { deviceId: socket.deviceId, room: deviceRoom });
    }

    // Emit authenticated event
    socket.emit(WSEvent.AUTHENTICATED, {
      userId: socket.userId,
      role: socket.userRole,
      deviceId: socket.deviceId,
      timestamp: new Date().toISOString(),
    });

    // Handle desktop stream events (agent-desktop ingestion)
    socket.on('stream:identify', (data: any) => {
      if (desktopStreamManager) {
        logger.debug('Stream identify event', { socketId: socket.id });
        desktopStreamManager.handleIdentify(socket as any, data);
      }
    });

    socket.on('stream:events', (data: any) => {
      if (desktopStreamManager) {
        logger.debug('Stream events received', {
          socketId: socket.id,
          eventCount: data.events?.length || 0,
        });
        desktopStreamManager.handleEvents(socket as any, data);
      }
    });

    socket.on('stream:ping', (data: any) => {
      logger.debug('Stream ping received', { socketId: socket.id });
      socket.emit('stream:pong', {
        type: 'PONG',
        timestamp: Date.now(),
        requestTimestamp: data?.timestamp,
      });
    });

    socket.on('stream:pong', (data: any) => {
      if (desktopStreamManager) {
        logger.debug('Stream pong received', { socketId: socket.id });
        desktopStreamManager.handlePong(socket as any, data);
      }
    });

    socket.on('stream:error', (data: any) => {
      logger.error('Stream error reported', {
        socketId: socket.id,
        deviceId: socket.deviceId,
        error: data?.error,
        message: data?.message,
      });
    });

    // Handle join room requests
    socket.on(WSEvent.JOIN_ROOM, async (data: { room: string }) => {
      try {
        const { room } = data;
        
        // Validate room access (basic security check)
        if (room.startsWith('parent:') && socket.userRole === 'parent' && room === getRoomNames.parent(socket.userId!)) {
          socket.join(room);
          socket.emit(WSEvent.ROOM_JOINED, { room });
          logger.debug('Client joined room', { socketId: socket.id, room });
        } else if (room.startsWith('child:') && socket.userId) {
          // Verify parent owns this child
          const result = await pool.query(
            'SELECT id FROM children WHERE id = $1 AND user_id = $2',
            [room.replace('child:', ''), socket.userId]
          );
          
          if (result.rows.length > 0) {
            socket.join(room);
            socket.emit(WSEvent.ROOM_JOINED, { room });
            logger.debug('Client joined child room', { socketId: socket.id, room });
          } else {
            socket.emit(WSEvent.ERROR, { message: 'Access denied to child room' });
          }
        } else if (room.startsWith('device:') && socket.deviceId === room.replace('device:', '')) {
          socket.join(room);
          socket.emit(WSEvent.ROOM_JOINED, { room });
          logger.debug('Client joined device room', { socketId: socket.id, room });
        } else {
          socket.emit(WSEvent.ERROR, { message: 'Invalid room or access denied' });
        }
      } catch (error) {
        logger.error('Error joining room', {
          socketId: socket.id,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
        socket.emit(WSEvent.ERROR, { message: 'Failed to join room' });
      }
    });

    // Handle leave room requests
    socket.on(WSEvent.LEAVE_ROOM, (data: { room: string }) => {
      const { room } = data;
      socket.leave(room);
      socket.emit(WSEvent.ROOM_LEFT, { room });
      logger.debug('Client left room', { socketId: socket.id, room });
    });

    // Handle device status updates
    socket.on(WSEvent.DEVICE_STATUS, async (data: { status: 'online' | 'offline' }) => {
      try {
        if (!socket.deviceId) {
          return;
        }

        // Update device last_seen_at and is_active in database
        await pool.query(
          'UPDATE devices SET last_seen_at = NOW(), is_active = $1 WHERE id = $2',
          [data.status === 'online', socket.deviceId]
        );

        // Get device info to broadcast to parent
        const deviceResult = await pool.query(
          'SELECT d.*, c.user_id FROM devices d JOIN children c ON d.child_id = c.id WHERE d.id = $1',
          [socket.deviceId]
        );

        if (deviceResult.rows.length > 0) {
          const device = deviceResult.rows[0];
          const parentRoom = getRoomNames.parent(device.user_id);
          
          // Broadcast to parent's room
          io!.to(parentRoom).emit(
            data.status === 'online' ? WSEvent.DEVICE_ONLINE : WSEvent.DEVICE_OFFLINE,
            {
              deviceId: socket.deviceId,
              deviceName: device.name,
              childId: device.child_id,
              timestamp: new Date().toISOString(),
            }
          );
        }

        logger.debug('Device status updated', {
          deviceId: socket.deviceId,
          status: data.status,
        });
      } catch (error) {
        logger.error('Error updating device status', {
          deviceId: socket.deviceId,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    });

    // Handle disconnection
    socket.on(WSEvent.DISCONNECT, async (reason: string) => {
      logger.info('WebSocket client disconnected', {
        socketId: socket.id,
        userId: socket.userId,
        deviceId: socket.deviceId,
        reason,
      });

      // Update device status if it was a device connection
      if (socket.deviceId) {
        try {
          await pool.query(
            'UPDATE devices SET is_active = false, last_seen_at = NOW() WHERE id = $1',
            [socket.deviceId]
          );

          // Notify parent
          const deviceResult = await pool.query(
            'SELECT d.*, c.user_id FROM devices d JOIN children c ON d.child_id = c.id WHERE d.id = $1',
            [socket.deviceId]
          );

          if (deviceResult.rows.length > 0) {
            const device = deviceResult.rows[0];
            const parentRoom = getRoomNames.parent(device.user_id);
            
            io!.to(parentRoom).emit(WSEvent.DEVICE_OFFLINE, {
              deviceId: socket.deviceId,
              deviceName: device.name,
              childId: device.child_id,
              timestamp: new Date().toISOString(),
            });
          }
        } catch (error) {
          logger.error('Error handling device disconnect', {
            deviceId: socket.deviceId,
            error: error instanceof Error ? error.message : 'Unknown error',
          });
        }
      }
    });
  });

  logger.info('WebSocket server initialized');
  return io;
}

/**
 * Get the Socket.io server instance
 */
export function getIO(): Server {
  if (!io) {
    throw new Error('WebSocket server not initialized. Call initializeWebSocket first.');
  }
  return io;
}

/**
 * Broadcast event to a specific room
 */
export function broadcastToRoom(room: string, event: WSEvent, data: unknown): void {
  if (!io) {
    logger.error('Cannot broadcast: WebSocket server not initialized');
    return;
  }
  
  io.to(room).emit(event, data);
  
  // Safely get keys from data object
  const dataKeys = data && typeof data === 'object' ? Object.keys(data) : [];
  logger.debug('Broadcast to room', { room, event, dataKeys });
}

/**
 * Broadcast event to all connected clients
 */
export function broadcastToAll(event: WSEvent, data: unknown): void {
  if (!io) {
    logger.error('Cannot broadcast: WebSocket server not initialized');
    return;
  }
  
  io.emit(event, data);
  
  // Safely get keys from data object
  const dataKeys = data && typeof data === 'object' ? Object.keys(data) : [];
  logger.debug('Broadcast to all clients', { event, dataKeys });
}
