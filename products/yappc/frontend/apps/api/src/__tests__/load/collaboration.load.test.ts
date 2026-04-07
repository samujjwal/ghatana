import { afterEach, describe, expect, it } from 'vitest';

import {
  RealTimeService,
  type CanvasRoom,
  type ServerMessage,
} from '../../services/RealTimeService';

const OPEN_SOCKET_STATE = 1;
const USER_COUNT = 10;
const TOTAL_OPERATIONS = 120;

interface BroadcastMetrics {
  totalDeliveries: number;
  latencySamplesMs: number[];
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
}

interface SyntheticOperation {
  actorId: string;
  nodeId: string;
  conflict: boolean;
}

interface RealTimeServiceInternals {
  joinCanvasRoom: (
    projectId: string,
    userId: string,
    name: string,
    email: string,
    ws: MockSocket
  ) => CanvasRoom;
  broadcastToRoom: (
    room: CanvasRoom,
    message: ServerMessage,
    excludeUserId?: string
  ) => void;
}

class MockSocket {
  public readonly messages: string[] = [];
  public readyState = OPEN_SOCKET_STATE;

  send(payload: string): void {
    this.messages.push(payload);
  }
}

const servicesToShutdown: RealTimeService[] = [];

afterEach(() => {
  for (const service of servicesToShutdown.splice(0)) {
    service.shutdown();
  }
});

function percentile(sortedValues: number[], target: number): number {
  if (sortedValues.length === 0) {
    return 0;
  }

  const index = Math.max(
    0,
    Math.ceil((target / 100) * sortedValues.length) - 1
  );
  return sortedValues[index] ?? 0;
}

function buildSyntheticOperations(
  userCount: number,
  totalOperations: number
): SyntheticOperation[] {
  const operations: SyntheticOperation[] = [];

  for (let index = 0; index < totalOperations; index += 1) {
    const actorId = `user-${(index % userCount) + 1}`;
    const isConflictBurst = index >= totalOperations - 4;
    operations.push({
      actorId,
      nodeId: isConflictBurst ? 'shared-hotspot-node' : `node-${index}`,
      conflict: isConflictBurst,
    });
  }

  return operations;
}

function createRoomHarness(userCount: number): {
  service: RealTimeService;
  room: CanvasRoom;
  sockets: Map<string, MockSocket>;
} {
  const service = new RealTimeService();
  servicesToShutdown.push(service);

  const internals = service as unknown as RealTimeServiceInternals;
  const sockets = new Map<string, MockSocket>();
  let room: CanvasRoom | null = null;

  for (let index = 0; index < userCount; index += 1) {
    const userId = `user-${index + 1}`;
    const socket = new MockSocket();
    sockets.set(userId, socket);
    room = internals.joinCanvasRoom(
      'project-load-test',
      userId,
      `User ${index + 1}`,
      `${userId}@example.com`,
      socket
    );
  }

  if (room === null) {
    throw new Error('Room harness setup failed.');
  }

  return { service, room, sockets };
}

function measureBroadcastFanout(
  room: CanvasRoom,
  service: RealTimeService,
  operations: SyntheticOperation[]
): BroadcastMetrics {
  const internals = service as unknown as RealTimeServiceInternals;
  const latencySamplesMs: number[] = [];

  for (const operation of operations) {
    const message: ServerMessage = {
      type: 'node-update',
      userId: operation.actorId,
      nodeId: operation.nodeId,
      updates: {
        label: `Update ${operation.nodeId}`,
        position: {
          x: latencySamplesMs.length * 3,
          y: latencySamplesMs.length * 2,
        },
      },
    };

    const startedAt = performance.now();
    internals.broadcastToRoom(room, message, operation.actorId);
    latencySamplesMs.push(performance.now() - startedAt);
  }

  const sortedLatencies = [...latencySamplesMs].sort(
    (left, right) => left - right
  );

  return {
    totalDeliveries: operations.length * (room.collaborators.size - 1),
    latencySamplesMs,
    p50Ms: percentile(sortedLatencies, 50),
    p95Ms: percentile(sortedLatencies, 95),
    p99Ms: percentile(sortedLatencies, 99),
  };
}

describe('collaboration load validation', () => {
  it('fanouts node updates to 10 users under the target latency envelope', () => {
    const operations = buildSyntheticOperations(USER_COUNT, TOTAL_OPERATIONS);
    const { service, room, sockets } = createRoomHarness(USER_COUNT);

    const metrics = measureBroadcastFanout(room, service, operations);
    const deliveredMessages = [...sockets.values()].reduce(
      (count, socket) => count + socket.messages.length,
      0
    );
    const conflictRate =
      operations.filter((operation) => operation.conflict).length /
      operations.length;

    expect(room.collaborators.size).toBe(USER_COUNT);
    expect(metrics.totalDeliveries).toBe(TOTAL_OPERATIONS * (USER_COUNT - 1));
    expect(deliveredMessages).toBe(metrics.totalDeliveries);
    expect(metrics.p95Ms).toBeLessThan(500);
    expect(metrics.p99Ms).toBeLessThan(500);
    expect(conflictRate).toBeLessThan(0.05);
  });

  it('delivers every broadcast to every connected collaborator except the sender', () => {
    const { service, room, sockets } = createRoomHarness(USER_COUNT);
    const internals = service as unknown as RealTimeServiceInternals;

    const message: ServerMessage = {
      type: 'cursor-update',
      userId: 'user-1',
      x: 144,
      y: 233,
    };

    internals.broadcastToRoom(room, message, 'user-1');

    expect(sockets.get('user-1')?.messages).toHaveLength(0);
    for (let index = 2; index <= USER_COUNT; index += 1) {
      const socket = sockets.get(`user-${index}`);
      expect(socket?.messages).toHaveLength(1);
      expect(socket?.messages[0]).toContain('cursor-update');
    }
  });
});
