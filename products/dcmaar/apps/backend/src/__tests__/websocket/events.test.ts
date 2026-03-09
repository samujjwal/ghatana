/**
 * WebSocket Event Broadcasting Tests
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
import { createServer } from 'http';
import type { AddressInfo } from 'net';
import ioClient from 'socket.io-client';
import jwt from 'jsonwebtoken';

import { initializeWebSocket, broadcastToAll, WSEvent, getIO } from '../../websocket/server';
import { stopConnectionMetricsInterval } from '../../db';

describe('WebSocket broadcast helpers', () => {
  const jwtSecret = 'event-secret';
  let server: ReturnType<typeof createServer>;
  let baseUrl: string;

  beforeAll(async () => {
    process.env.JWT_SECRET = jwtSecret;
    server = createServer();
    initializeWebSocket(server);
    await new Promise<void>((resolve) => {
      server.listen(() => {
        const { port } = server.address() as AddressInfo;
        baseUrl = `http://127.0.0.1:${port}`;
        resolve();
      });
    });
  });

  afterAll(async () => {
    stopConnectionMetricsInterval();
    getIO().close();
    await new Promise<void>((resolve) => {
      server.close(() => resolve());
    });
  });

  it('broadcasts events to all connected clients', async () => {
    const token = jwt.sign({ userId: 'parent-1', role: 'parent' }, jwtSecret);
    const socket = ioClient(baseUrl, {
      auth: { token },
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise(resolve => socket.on(WSEvent.AUTHENTICATED, resolve));

    const received = await new Promise<any>(resolve => {
      socket.on(WSEvent.BLOCK_EVENT, data => resolve(data));
      broadcastToAll(WSEvent.BLOCK_EVENT, { blockEvent: { id: 'block-1' } });
    });

    expect(received.blockEvent.id).toBe('block-1');
    socket.close();
  });
});

