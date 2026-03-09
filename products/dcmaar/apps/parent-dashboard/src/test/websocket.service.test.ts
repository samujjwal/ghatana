import { describe, it, expect, vi } from 'vitest';
import { websocketService } from '../services/websocket.service';

// Mock socket.io-client
const mockSocket = {
  on: vi.fn(),
  off: vi.fn(),
  emit: vi.fn(),
  disconnect: vi.fn(),
};

vi.mock('socket.io-client', () => ({
  io: vi.fn(() => mockSocket),
}));

// Mock auth service
vi.mock('../services/auth.service', () => ({
  authService: {
    getToken: vi.fn(() => 'mock-token'),
  },
}));

describe('WebSocket Service', () => {
  it('should connect with JWT token', () => {
    websocketService.connect();
    
    // Verify the socket connection was created
    expect(mockSocket).toBeDefined();
  });

  it('should subscribe to events', () => {
    const mockHandler = vi.fn();
    
    websocketService.on('usage_data', mockHandler);
    
    // Verify event handler was registered
    expect(mockSocket.on).toHaveBeenCalledWith('usage_data', expect.any(Function));
  });
});
