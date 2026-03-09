/**
 * WebSocket Connection Tests
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { createServer } from 'http';
import type { AddressInfo } from 'net';
import ioClient from 'socket.io-client';
import jwt from 'jsonwebtoken';

import {
  initializeWebSocket,
  WSEvent,
  broadcastToRoom,
  getRoomNames,
  getIO,
} from '../../websocket/server';
import { pool, stopConnectionMetricsInterval } from '../../db';

describe('WebSocket connections', () => {
  const jwtSecret = 'test-websocket-secret';
  let httpServer: ReturnType<typeof createServer>;
  let baseUrl: string;

  beforeAll(async () => {
    process.env.JWT_SECRET = jwtSecret;
    httpServer = createServer();
    initializeWebSocket(httpServer);
    await new Promise<void>((resolve) => {
      httpServer.listen(() => {
        const { port } = httpServer.address() as AddressInfo;
        baseUrl = `http://127.0.0.1:${port}`;
        resolve();
      });
    });
  });

  afterAll(async () => {
    stopConnectionMetricsInterval();
    getIO().close();
    await new Promise<void>((resolve) => {
      httpServer.close(() => resolve());
    });
  });

  const createAuthenticatedClient = (payload: Record<string, any>) => {
    const token = jwt.sign(payload, jwtSecret);
    return ioClient(baseUrl, {
      auth: { token },
      transports: ['websocket'],
      forceNew: true,
      reconnection: false,
    });
  };

  it('authenticates client and emits authenticated event', async () => {
    const socket = createAuthenticatedClient({ userId: 'parent-123', role: 'parent' });

    const authenticatedPayload = await new Promise<any>(resolve => {
      socket.on(WSEvent.AUTHENTICATED, payload => resolve(payload));
    });

    expect(authenticatedPayload.userId).toBe('parent-123');
    expect(authenticatedPayload.role).toBe('parent');
    socket.close();
  });

  it('allows parent to join child namespace when ownership verified', async () => {
    const querySpy = vi
      .spyOn(pool, 'query')
      .mockResolvedValue({ rows: [{ id: 'child-1' }] } as any);

    const socket = createAuthenticatedClient({ userId: 'parent-123', role: 'parent' });

    await new Promise(resolve => socket.on(WSEvent.AUTHENTICATED, resolve));

    const roomJoined = await new Promise<string>(resolve => {
      socket.emit(WSEvent.JOIN_ROOM, { room: getRoomNames.child('child-1') });
      socket.on(WSEvent.ROOM_JOINED, ({ room }) => resolve(room));
    });

    expect(roomJoined).toBe(getRoomNames.child('child-1'));
    expect(querySpy).toHaveBeenCalledWith(
      'SELECT id FROM children WHERE id = $1 AND user_id = $2',
      ['child-1', 'parent-123']
    );

    socket.close();
    querySpy.mockRestore();
  });

  it('broadcasts usage data to parent room members', async () => {
    const socket = createAuthenticatedClient({ userId: 'parent-999', role: 'parent' });
    await new Promise(resolve => socket.on(WSEvent.AUTHENTICATED, resolve));

    const payloadReceived = await new Promise<any>(resolve => {
      socket.on(WSEvent.USAGE_DATA, data => resolve(data));
      broadcastToRoom(getRoomNames.parent('parent-999'), WSEvent.USAGE_DATA, {
        usageSession: { id: 'session-1' },
      });
    });

    expect(payloadReceived.usageSession.id).toBe('session-1');
    socket.close();
  });
});

