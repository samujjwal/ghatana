/**
 * usePersonaSync Hook Tests
 *
 * Tests validate:
 * - WebSocket connection lifecycle
 * - Authentication with JWT token
 * - Workspace room join/leave
 * - Cache invalidation on persona:updated event
 * - Cache reset on persona:deleted event
 * - Auto-reconnection with exponential backoff
 * - Error handling and recovery
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePersonaSync } from '../usePersonaSync';
import type { Socket } from 'socket.io-client';

// Mock socket.io-client - use vi.hoisted() for variables used in mocks
const { mockSocket, mockIo } = vi.hoisted(() => {
    const mockSocket = {
        on: vi.fn(),
        off: vi.fn(),
        emit: vi.fn(),
        connect: vi.fn(),
        disconnect: vi.fn(),
        connected: false,
        id: 'mock-socket-id',
        io: {
            opts: {
                auth: {},
            },
        },
    } as unknown as Socket;

    const mockIo = vi.fn(() => mockSocket);

    return { mockSocket, mockIo };
});

vi.mock('socket.io-client', () => ({
    io: mockIo,
}));

describe('usePersonaSync', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: { retry: false },
            },
        });

        // Reset all mocks
        vi.clearAllMocks();

        // Reset socket state
        mockSocket.connected = false;

        // Mock localStorage
        Storage.prototype.getItem = vi.fn((key) => {
            if (key === 'token') return 'mock-jwt-token';
            return null;
        });

        // Reset event listeners
        const mockOn = mockSocket.on as any;
        mockOn.mockImplementation((event: string, handler: Function) => {
            // Store handlers for later triggering
            if (!mockOn.handlers) mockOn.handlers = {};
            mockOn.handlers[event] = handler;
            return mockSocket;
        });
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    const wrapper = ({ children }: { children: React.ReactNode }) => {
        return React.createElement(QueryClientProvider, { client: queryClient }, children);
    };

    describe('Connection Lifecycle', () => {
        it('should establish WebSocket connection on mount', () => {
            renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            expect(mockIo).toHaveBeenCalledWith(
                expect.any(String),
                expect.objectContaining({
                    auth: {
                        token: 'mock-jwt-token',
                    },
                    reconnection: true,
                    reconnectionDelay: 1000,
                    reconnectionDelayMax: 5000,
                    reconnectionAttempts: 5,
                })
            );
        });

        it('should disconnect WebSocket on unmount', () => {
            const { unmount } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            unmount();

            expect(mockSocket.disconnect).toHaveBeenCalled();
        });

        it('should update connection state when connected', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            expect(result.current.isConnected).toBe(false);

            // Simulate connection - wrap state update in act()
            const mockOn = mockSocket.on as any;
            mockSocket.connected = true;

            await waitFor(() => {
                mockOn.handlers['connect']();
                expect(result.current.isConnected).toBe(true);
            });
        });

        it('should update connection state when disconnected', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            // Start connected
            mockSocket.connected = true;
            const mockOn = mockSocket.on as any;

            await waitFor(() => {
                mockOn.handlers['connect']();
                expect(result.current.isConnected).toBe(true);
            });

            // Simulate disconnection - wrap in waitFor()
            mockSocket.connected = false;

            await waitFor(() => {
                mockOn.handlers['disconnect']('transport close');
                expect(result.current.isConnected).toBe(false);
            });
        });

        it('should handle connection errors', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;
            const error = new Error('Connection failed');

            await waitFor(() => {
                mockOn.handlers['connect_error'](error);
                expect(result.current.error).toEqual(error);
                expect(result.current.isConnected).toBe(false);
            });
        });
    });

    describe('Authentication', () => {
        it('should use JWT token from localStorage', () => {
            renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            expect(mockIo).toHaveBeenCalledWith(
                expect.any(String),
                expect.objectContaining({
                    auth: {
                        token: 'mock-jwt-token',
                    },
                })
            );
        });

        it('should handle missing JWT token', () => {
            Storage.prototype.getItem = vi.fn(() => null);

            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            // Hook should not create socket when token is missing
            expect(mockIo).not.toHaveBeenCalled();
            expect(result.current.isConnected).toBe(false);
        });
    });

    describe('Workspace Room Management', () => {
        it('should join workspace room on connection', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;
            mockSocket.connected = true;

            await waitFor(() => {
                mockOn.handlers['connect']();
                expect(mockSocket.emit).toHaveBeenCalledWith(
                    'persona:join-workspace',
                    'workspace-123'
                );
            });
        });

        it('should handle workspace-joined confirmation', async () => {
            const { result } = renderHook(
                () => usePersonaSync('workspace-123', { debug: true }),
                { wrapper }
            );

            const mockOn = mockSocket.on as any;
            const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => { });

            mockOn.handlers['persona:workspace-joined']({ workspaceId: 'workspace-123' });

            expect(consoleSpy).toHaveBeenCalledWith(
                '[PersonaSync] Joined workspace:',
                'workspace-123'
            ); consoleSpy.mockRestore();
        });

        it('should leave workspace room on unmount', () => {
            const { unmount } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            unmount();

            expect(mockSocket.emit).toHaveBeenCalledWith(
                'persona:leave-workspace',
                'workspace-123'
            );
        });

        it('should rejoin workspace on reconnection', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;

            // Initial connection
            mockSocket.connected = true;

            await waitFor(() => {
                mockOn.handlers['connect']();
                expect(mockSocket.emit).toHaveBeenCalledWith(
                    'persona:join-workspace',
                    'workspace-123'
                );
            });

            vi.clearAllMocks();

            // Disconnect and reconnect
            mockSocket.connected = false;
            await waitFor(() => {
                mockOn.handlers['disconnect']('transport close');
            });

            mockSocket.connected = true;
            await waitFor(() => {
                mockOn.handlers['connect']();
                expect(mockSocket.emit).toHaveBeenCalledWith(
                    'persona:join-workspace',
                    'workspace-123'
                );
            });
        });
    });

    describe('Cache Invalidation', () => {
        it('should invalidate cache on persona:updated event', async () => {
            const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

            renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;
            const updateEvent = {
                workspaceId: 'workspace-123',
                userId: 'user-456',
                activeRoles: ['admin', 'developer'],
                preferences: {},
                timestamp: new Date().toISOString(),
            };

            mockOn.handlers['persona:updated'](updateEvent);

            await waitFor(() => {
                expect(invalidateQueriesSpy).toHaveBeenCalledWith({
                    queryKey: ['personas', 'preferences', 'workspace-123'],
                });
            });
        });

        it('should set cache to null on persona:deleted event', async () => {
            const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');

            renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;
            const deleteEvent = {
                workspaceId: 'workspace-123',
                userId: 'user-456',
                timestamp: new Date().toISOString(),
            };

            mockOn.handlers['persona:deleted'](deleteEvent);

            await waitFor(() => {
                expect(setQueryDataSpy).toHaveBeenCalledWith(
                    ['personas', 'preferences', 'workspace-123'],
                    null
                );
            });
        });

        it('should log cache invalidation in debug mode', async () => {
            const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => { });

            renderHook(() => usePersonaSync('workspace-123', { debug: true }), { wrapper });

            const mockOn = mockSocket.on as any;
            const updateEvent = {
                workspaceId: 'workspace-123',
                userId: 'user-456',
                activeRoles: ['admin'],
                preferences: {},
                timestamp: new Date().toISOString(),
            };

            mockOn.handlers['persona:updated'](updateEvent);

            await waitFor(() => {
                expect(consoleSpy).toHaveBeenCalledWith(
                    '[PersonaSync] Persona updated remotely:',
                    updateEvent
                );
            }); consoleSpy.mockRestore();
        });
    });

    describe('Auto-Reconnection', () => {
        it('should attempt reconnection on disconnect', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;

            // Simulate disconnect
            mockSocket.connected = false;
            mockOn.handlers['disconnect']('transport close');

            await waitFor(() => {
                expect(result.current.isConnected).toBe(false);
            });

            // Reconnection should be automatic (handled by socket.io)
            // We verify it's configured in connection options
            expect(mockIo).toHaveBeenCalledWith(
                expect.any(String),
                expect.objectContaining({
                    reconnection: true,
                    reconnectionAttempts: 5,
                })
            );
        });

        it('should provide manual reconnect function', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            expect(result.current.reconnect).toBeDefined();
            expect(typeof result.current.reconnect).toBe('function');

            // Call manual reconnect
            result.current.reconnect();

            expect(mockSocket.connect).toHaveBeenCalled();
        });

        it('should handle reconnection failure after max attempts', async () => {
            const { result } = renderHook(
                () => usePersonaSync('workspace-123', { maxReconnectAttempts: 3 }),
                { wrapper }
            );

            const mockOn = mockSocket.on as any;
            const error = new Error('Reconnection failed');

            await waitFor(() => {
                mockOn.handlers['connect_error'](error);
                expect(result.current.error).toEqual(error);
            });
        });
    });

    describe('Configuration', () => {
        it('should use custom WebSocket URL', () => {
            const { io } = require('socket.io-client');

            renderHook(() => usePersonaSync('workspace-123', { url: 'http://custom:4000' }), {
                wrapper,
            });

            expect(mockIo).toHaveBeenCalledWith('http://custom:4000', expect.any(Object));
        });

        it('should use default URL from environment', () => {
            renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            // Should use VITE_WS_URL or fallback to localhost:3001
            expect(mockIo).toHaveBeenCalledWith(expect.stringMatching(/^http/), expect.any(Object));
        });

        it('should respect debug flag', async () => {
            const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => { });

            renderHook(() => usePersonaSync('workspace-123', { debug: true }), { wrapper });

            const mockOn = mockSocket.on as any;
            mockSocket.connected = true;

            await waitFor(() => {
                mockOn.handlers['connect']();
                expect(consoleSpy).toHaveBeenCalledWith(
                    '[PersonaSync] Connected, socket ID:',
                    'mock-socket-id'
                );
            });

            consoleSpy.mockRestore();
        });

        it('should not log in production mode', async () => {
            const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => { });

            renderHook(() => usePersonaSync('workspace-123', { debug: false }), { wrapper });

            const mockOn = mockSocket.on as any;
            mockSocket.connected = true;

            await waitFor(() => {
                mockOn.handlers['connect']();
            });

            expect(consoleSpy).not.toHaveBeenCalled();

            consoleSpy.mockRestore();
        });
    });

    describe('Edge Cases', () => {
        it('should handle rapid connect/disconnect cycles', async () => {
            const { result } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;

            // Rapid connect/disconnect - wrap all in waitFor()
            await waitFor(() => {
                mockSocket.connected = true;
                mockOn.handlers['connect']();

                mockSocket.connected = false;
                mockOn.handlers['disconnect']('transport close');

                mockSocket.connected = true;
                mockOn.handlers['connect']();

                expect(result.current.isConnected).toBe(true);
            });
        });

        it('should handle multiple cache invalidations in quick succession', async () => {
            const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

            renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const mockOn = mockSocket.on as any;
            const updateEvent = {
                workspaceId: 'workspace-123',
                userId: 'user-456',
                activeRoles: ['admin'],
                preferences: {},
                timestamp: new Date().toISOString(),
            };

            // Send multiple updates rapidly
            mockOn.handlers['persona:updated'](updateEvent);
            mockOn.handlers['persona:updated']({ ...updateEvent, activeRoles: ['developer'] });
            mockOn.handlers['persona:updated']({ ...updateEvent, activeRoles: ['tech-lead'] });

            await waitFor(() => {
                expect(invalidateQueriesSpy).toHaveBeenCalledTimes(6);
            });
        });

        it('should handle workspace change', () => {
            const { rerender, unmount } = renderHook(
                ({ workspaceId }) => usePersonaSync(workspaceId),
                {
                    wrapper,
                    initialProps: { workspaceId: 'workspace-1' },
                }
            );

            // Simulate connection
            const mockOn = mockSocket.on as any;
            mockSocket.connected = true;
            mockOn.handlers['connect']();

            // Verify joined workspace-1
            expect(mockSocket.emit).toHaveBeenCalledWith(
                'persona:join-workspace',
                'workspace-1'
            );

            vi.clearAllMocks();

            // Change workspace
            rerender({ workspaceId: 'workspace-2' });

            // Socket should reconnect and join new workspace
            mockSocket.connected = true;
            mockOn.handlers['connect']();

            // Should leave old and join new
            expect(mockSocket.emit).toHaveBeenCalledWith(
                'persona:leave-workspace',
                'workspace-1'
            );
            expect(mockSocket.emit).toHaveBeenCalledWith(
                'persona:join-workspace',
                'workspace-2'
            );

            unmount();
        });
    });

    describe('Performance', () => {
        it('should cleanup socket connection on unmount', () => {
            const { unmount } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            unmount();

            expect(mockSocket.emit).toHaveBeenCalledWith(
                'persona:leave-workspace',
                'workspace-123'
            );
            expect(mockSocket.disconnect).toHaveBeenCalled();
        });

        it('should not create multiple socket connections', () => {
            const { io } = require('socket.io-client');

            const { rerender } = renderHook(() => usePersonaSync('workspace-123'), { wrapper });

            const callCount1 = mockIo.mock.calls.length;

            rerender();

            const callCount2 = mockIo.mock.calls.length;

            expect(callCount1).toBe(callCount2); // No new connection
        });
    });
});
