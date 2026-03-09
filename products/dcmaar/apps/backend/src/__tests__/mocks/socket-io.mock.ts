/**
 * Socket.io Mock for Testing
 *
 * Provides a mock Socket.io server and client for testing WebSocket functionality
 */

export interface MockSocket {
  id: string;
  connected: boolean;
  emit: jest.Mock;
  on: jest.Mock;
  off: jest.Mock;
  join: jest.Mock;
  leave: jest.Mock;
  disconnect: jest.Mock;
  data: any;
}

export interface MockNamespace {
  name: string;
  emit: jest.Mock;
  to: jest.Mock;
  in: jest.Mock;
  sockets: Map<string, MockSocket>;
}

export interface MockServer {
  of: jest.Mock;
  emit: jest.Mock;
  to: jest.Mock;
  in: jest.Mock;
  sockets: MockNamespace;
  close: jest.Mock;
  _namespaces: Map<string, MockNamespace>;
}

/**
 * Create a mock socket instance
 */
export function createMockSocket(id: string = 'socket-123'): MockSocket {
  return {
    id,
    connected: true,
    emit: jest.fn().mockReturnThis(),
    on: jest.fn().mockReturnThis(),
    off: jest.fn().mockReturnThis(),
    join: jest.fn().mockReturnThis(),
    leave: jest.fn().mockReturnThis(),
    disconnect: jest.fn(),
    data: {},
  };
}

/**
 * Create a mock namespace
 */
export function createMockNamespace(name: string = '/'): MockNamespace {
  const namespace: MockNamespace = {
    name,
    emit: jest.fn(),
    to: jest.fn().mockReturnThis(),
    in: jest.fn().mockReturnThis(),
    sockets: new Map(),
  };
  return namespace;
}

/**
 * Create a mock Socket.io server
 */
export function createMockServer(): MockServer {
  const mainNamespace = createMockNamespace('/');
  const namespaces = new Map<string, MockNamespace>();
  namespaces.set('/', mainNamespace);

  const server: MockServer = {
    of: jest.fn((namespaceName: string) => {
      if (!namespaces.has(namespaceName)) {
        namespaces.set(namespaceName, createMockNamespace(namespaceName));
      }
      return namespaces.get(namespaceName);
    }),
    emit: jest.fn(),
    to: jest.fn().mockReturnThis(),
    in: jest.fn().mockReturnThis(),
    sockets: mainNamespace,
    close: jest.fn(),
    _namespaces: namespaces,
  };

  return server;
}

/**
 * Helper to simulate socket connection
 */
export function simulateSocketConnection(
  server: MockServer,
  socket: MockSocket,
  namespace: string = '/'
): void {
  const ns = server._namespaces.get(namespace) || server.sockets;
  ns.sockets.set(socket.id, socket);
}

/**
 * Helper to simulate socket disconnection
 */
export function simulateSocketDisconnection(
  server: MockServer,
  socketId: string,
  namespace: string = '/'
): void {
  const ns = server._namespaces.get(namespace) || server.sockets;
  ns.sockets.delete(socketId);
}

/**
 * Helper to get all sockets in a namespace
 */
export function getNamespaceSockets(
  server: MockServer,
  namespace: string = '/'
): MockSocket[] {
  const ns = server._namespaces.get(namespace) || server.sockets;
  return Array.from(ns.sockets.values());
}

export const socketMocks = {
  createMockSocket,
  createMockNamespace,
  createMockServer,
  simulateSocketConnection,
  simulateSocketDisconnection,
  getNamespaceSockets,
};
