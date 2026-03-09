/**
 * Realtime WebSocket Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Test real-time WebSocket functionality
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { useRealtime } from '../useRealtime';

// Mock socket.io-client
const mockSocket = {
  on: jest.fn(),
  off: jest.fn(),
  emit: jest.fn(),
  connect: jest.fn(),
  disconnect: jest.fn(),
  connected: false,
};

jest.mock('socket.io-client', () => ({
  io: jest.fn(() => mockSocket),
}));

describe('useRealtime', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockSocket.connected = false;
    mockSocket.on.mockReset();
    mockSocket.emit.mockReset();
  });

  describe('initialization', () => {
    it('should initialize with disconnected state', () => {
      const { result } = renderHook(() => 
        useRealtime({ autoConnect: false })
      );

      expect(result.current.state.isConnected).toBe(false);
      expect(result.current.state.isConnecting).toBe(false);
      expect(result.current.state.error).toBeNull();
    });

    it('should not auto-connect without auth token', () => {
      renderHook(() => useRealtime({ autoConnect: true }));

      // Should not attempt connection without token
      expect(mockSocket.on).not.toHaveBeenCalled();
    });
  });

  describe('connection', () => {
    it('should connect with auth token', async () => {
      const onConnect = jest.fn();
      
      const { result } = renderHook(() => 
        useRealtime({ 
          authToken: 'test-token',
          autoConnect: false,
          onConnect,
        })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate connection event
      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      expect(result.current.state.isConnected).toBe(true);
      expect(onConnect).toHaveBeenCalled();
    });

    it('should handle connection error', async () => {
      const onError = jest.fn();
      
      const { result } = renderHook(() => 
        useRealtime({ 
          authToken: 'test-token',
          autoConnect: false,
          onError,
        })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate connection error
      const errorHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect_error'
      )?.[1];
      
      if (errorHandler) {
        act(() => {
          errorHandler(new Error('Connection failed'));
        });
      }

      expect(result.current.state.error).toBe('Connection failed');
      expect(onError).toHaveBeenCalledWith('Connection failed');
    });

    it('should disconnect properly', async () => {
      const onDisconnect = jest.fn();
      
      const { result } = renderHook(() => 
        useRealtime({ 
          authToken: 'test-token',
          autoConnect: false,
          onDisconnect,
        })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate connection
      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.disconnect();
      });

      expect(mockSocket.disconnect).toHaveBeenCalled();
      expect(result.current.state.isConnected).toBe(false);
    });
  });

  describe('sphere/moment joining', () => {
    it('should join sphere', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate connection
      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.joinSphere('sphere-123');
      });

      expect(mockSocket.emit).toHaveBeenCalledWith('join_sphere', { sphereId: 'sphere-123' });
    });

    it('should leave sphere', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.joinSphere('sphere-123');
        result.current.controls.leaveSphere('sphere-123');
      });

      expect(mockSocket.emit).toHaveBeenCalledWith('leave_sphere', { sphereId: 'sphere-123' });
    });

    it('should join moment', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.joinMoment('moment-456');
      });

      expect(mockSocket.emit).toHaveBeenCalledWith('join_moment', { momentId: 'moment-456' });
    });
  });

  describe('presence updates', () => {
    it('should handle presence updates', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate presence update
      const presenceHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'presence_update'
      )?.[1];
      
      if (presenceHandler) {
        act(() => {
          presenceHandler({
            userId: 'user-1',
            displayName: 'Test User',
            status: 'active',
            lastActivity: new Date().toISOString(),
          });
        });
      }

      expect(result.current.presence.has('user-1')).toBe(true);
      expect(result.current.presence.get('user-1')?.displayName).toBe('Test User');
    });

    it('should send presence updates', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.updatePresence({ status: 'idle' });
      });

      expect(mockSocket.emit).toHaveBeenCalledWith('presence_update', { status: 'idle' });
    });
  });

  describe('typing indicators', () => {
    it('should handle typing indicators', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate typing indicator
      const typingHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'typing_indicator'
      )?.[1];
      
      if (typingHandler) {
        act(() => {
          typingHandler({
            userId: 'user-1',
            displayName: 'Test User',
            momentId: 'moment-123',
            isTyping: true,
          });
        });
      }

      expect(result.current.typingUsers).toHaveLength(1);
      expect(result.current.typingUsers[0].displayName).toBe('Test User');
    });

    it('should send typing indicator', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.sendTypingIndicator('moment-123', true);
      });

      expect(mockSocket.emit).toHaveBeenCalledWith('typing_indicator', { 
        momentId: 'moment-123', 
        isTyping: true 
      });
    });
  });

  describe('moment updates', () => {
    it('should handle moment created event', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      // Simulate moment created
      const momentHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'moment_created'
      )?.[1];
      
      if (momentHandler) {
        act(() => {
          momentHandler({
            id: 'moment-123',
            sphereId: 'sphere-1',
            content: 'New moment',
            updatedBy: 'user-1',
            timestamp: new Date().toISOString(),
          });
        });
      }

      expect(result.current.momentUpdates).toHaveLength(1);
      expect(result.current.momentUpdates[0].id).toBe('moment-123');
    });
  });

  describe('reactions', () => {
    it('should send reaction', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      act(() => {
        result.current.controls.sendReaction('moment-123', '👍', 'add');
      });

      expect(mockSocket.emit).toHaveBeenCalledWith('reaction', { 
        momentId: 'moment-123', 
        reaction: '👍',
        action: 'add'
      });
    });
  });

  describe('reconnection', () => {
    it('should rejoin rooms after reconnection', async () => {
      const { result } = renderHook(() => 
        useRealtime({ authToken: 'test-token', autoConnect: false })
      );

      act(() => {
        result.current.controls.connect();
      });

      // First connection
      const connectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'connect'
      )?.[1];
      
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      // Join some rooms
      act(() => {
        result.current.controls.joinSphere('sphere-123');
        result.current.controls.joinMoment('moment-456');
      });

      // Simulate disconnect and reconnect
      const disconnectHandler = mockSocket.on.mock.calls.find(
        (call: string[]) => call[0] === 'disconnect'
      )?.[1];
      
      if (disconnectHandler) {
        act(() => {
          mockSocket.connected = false;
          disconnectHandler('io client disconnect');
        });
      }

      // Clear previous emit calls
      mockSocket.emit.mockClear();

      // Reconnect
      if (connectHandler) {
        act(() => {
          mockSocket.connected = true;
          connectHandler();
        });
      }

      // Should have rejoined the rooms
      expect(mockSocket.emit).toHaveBeenCalledWith('join_sphere', { sphereId: 'sphere-123' });
      expect(mockSocket.emit).toHaveBeenCalledWith('join_moment', { momentId: 'moment-456' });
    });
  });
});
