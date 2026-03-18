/**
 * RealtimeClient Integration Tests
 *
 * Tests WebSocket and SSE streaming functionality.
 *
 * @doc.type test
 * @doc.purpose Integration testing for real-time streaming
 * @doc.layer platform
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { WebSocketClient, SSEClient } from '../clients';
import type { StreamEvent, ConnectionConfig } from '../types';

describe('WebSocketClient', () => {
    let client: WebSocketClient;
    let config: ConnectionConfig;

    beforeEach(() => {
        config = {
            url: 'ws://localhost:8080/ws',
            reconnect: true,
            maxReconnectAttempts: 3,
            reconnectInterval: 1000,
        };
        client = new WebSocketClient(config);
    });

    afterEach(() => {
        client.disconnect();
    });

    describe('Connection', () => {
        it('should connect to WebSocket server', async () => {
            // Mock WebSocket
            const mockWS = {
                readyState: WebSocket.OPEN,
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            await client.connect();

            expect(client.isConnected()).toBe(true);
        });

        it('should handle connection failure', async () => {
            const mockWS = {
                readyState: WebSocket.CLOSED,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'error') {
                        handler(new Error('Connection failed'));
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            await expect(client.connect()).rejects.toThrow('Connection failed');
        });

        it('should reconnect automatically on disconnect', async () => {
            let connectCount = 0;
            const mockWS = {
                readyState: WebSocket.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'close') {
                        setTimeout(() => handler(), 100);
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => {
                connectCount++;
                return mockWS;
            }) as any;

            await client.connect();

            // Wait for reconnection
            await new Promise((resolve) => setTimeout(resolve, 1500));

            expect(connectCount).toBeGreaterThan(1);
        });

        it('should stop reconnecting after max attempts', async () => {
            let connectCount = 0;
            const mockWS = {
                readyState: WebSocket.CLOSED,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'error') {
                        handler(new Error('Failed'));
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => {
                connectCount++;
                return mockWS;
            }) as any;

            try {
                await client.connect();
            } catch {
                // Expected
            }

            expect(connectCount).toBeLessThanOrEqual(config.maxReconnectAttempts!);
        });
    });

    describe('Message Handling', () => {
        it('should receive and parse messages', (done) => {
            const testMessage = { type: 'event', data: { id: 1 } };
            const mockWS = {
                readyState: WebSocket.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'message') {
                        setTimeout(() => {
                            handler({
                                data: JSON.stringify(testMessage),
                            });
                        }, 100);
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            client.onMessage((msg) => {
                expect(msg).toEqual(testMessage);
                done();
            });

            client.connect();
        });

        it('should handle binary messages', (done) => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'message') {
                        const buffer = new ArrayBuffer(8);
                        handler({ data: buffer });
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            client.onMessage((msg) => {
                expect(msg).toBeInstanceOf(ArrayBuffer);
                done();
            });

            client.connect();
        });

        it('should handle malformed JSON', () => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'message') {
                        handler({ data: '{invalid json}' });
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            const errorHandler = vi.fn();
            client.onError(errorHandler);
            client.connect();

            // Error handler should be called
            expect(errorHandler).toHaveBeenCalled();
        });
    });

    describe('Send Messages', () => {
        it('should send text messages', async () => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                send: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            await client.connect();
            client.send({ type: 'ping' });

            expect(mockWS.send).toHaveBeenCalledWith(
                JSON.stringify({ type: 'ping' })
            );
        });

        it('should queue messages when disconnected', async () => {
            const mockWS = {
                readyState: WebSocket.CONNECTING,
                send: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            client.send({ type: 'test' });

            // Message should be queued
            expect(mockWS.send).not.toHaveBeenCalled();
        });

        it('should send queued messages on connect', async () => {
            let ws: any;
            global.WebSocket = vi.fn(() => {
                ws = {
                    readyState: WebSocket.CONNECTING,
                    send: vi.fn(),
                    addEventListener: vi.fn((event, handler) => {
                        if (event === 'open') {
                            ws.readyState = WebSocket.OPEN;
                            setTimeout(() => handler(), 100);
                        }
                    }),
                    removeEventListener: vi.fn(),
                    close: vi.fn(),
                };
                return ws;
            }) as any;

            client.send({ type: 'queued' });
            await client.connect();

            await new Promise((resolve) => setTimeout(resolve, 200));

            expect(ws.send).toHaveBeenCalled();
        });
    });

    describe('Subscriptions', () => {
        it('should subscribe to specific topics', async () => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                send: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            await client.connect();
            client.subscribe('topic1');

            expect(mockWS.send).toHaveBeenCalledWith(
                JSON.stringify({
                    type: 'subscribe',
                    topic: 'topic1',
                })
            );
        });

        it('should unsubscribe from topics', async () => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                send: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            await client.connect();
            client.subscribe('topic1');
            client.unsubscribe('topic1');

            expect(mockWS.send).toHaveBeenCalledWith(
                JSON.stringify({
                    type: 'unsubscribe',
                    topic: 'topic1',
                })
            );
        });

        it('should filter messages by topic', (done) => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'message') {
                        setTimeout(() => {
                            handler({
                                data: JSON.stringify({
                                    topic: 'topic1',
                                    data: 'test',
                                }),
                            });
                        }, 100);
                    }
                }),
                send: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            client.onMessage((msg) => {
                if (msg.topic === 'topic1') {
                    done();
                }
            });

            client.connect();
        });
    });

    describe('Backpressure', () => {
        it('should handle backpressure with buffer', async () => {
            const mockWS = {
                readyState: WebSocket.OPEN,
                bufferedAmount: 16384, // Simulated buffer
                send: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.WebSocket = vi.fn(() => mockWS) as any;

            await client.connect();

            // Should not send when buffer is full
            client.send({ type: 'test' });
            expect(mockWS.send).not.toHaveBeenCalled();
        });

        it('should resume sending when buffer clears', async () => {
            let ws: any;
            global.WebSocket = vi.fn(() => {
                ws = {
                    readyState: WebSocket.OPEN,
                    bufferedAmount: 16384,
                    send: vi.fn(),
                    addEventListener: vi.fn(),
                    removeEventListener: vi.fn(),
                    close: vi.fn(),
                };
                return ws;
            }) as any;

            await client.connect();
            client.send({ type: 'test' });

            // Clear buffer
            ws.bufferedAmount = 0;

            await new Promise((resolve) => setTimeout(resolve, 100));

            // Should eventually send
            expect(ws.send).toHaveBeenCalled();
        });
    });
});

describe('SSEClient', () => {
    let client: SSEClient;
    let config: ConnectionConfig;

    beforeEach(() => {
        config = {
            url: 'http://localhost:8080/sse',
            reconnect: true,
        };
        client = new SSEClient(config);
    });

    afterEach(() => {
        client.disconnect();
    });

    describe('Connection', () => {
        it('should connect to SSE endpoint', async () => {
            const mockEventSource = {
                readyState: EventSource.OPEN,
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.EventSource = vi.fn(() => mockEventSource) as any;

            await client.connect();

            expect(client.isConnected()).toBe(true);
        });

        it('should handle SSE errors', async () => {
            const mockEventSource = {
                readyState: EventSource.CLOSED,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'error') {
                        handler(new Error('SSE error'));
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.EventSource = vi.fn(() => mockEventSource) as any;

            const errorHandler = vi.fn();
            client.onError(errorHandler);

            await client.connect();

            expect(errorHandler).toHaveBeenCalled();
        });
    });

    describe('Event Handling', () => {
        it('should receive SSE events', (done) => {
            const testEvent = { data: 'test message' };
            const mockEventSource = {
                readyState: EventSource.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'message') {
                        setTimeout(() => handler(testEvent), 100);
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.EventSource = vi.fn(() => mockEventSource) as any;

            client.onMessage((msg) => {
                expect(msg.data).toBe('test message');
                done();
            });

            client.connect();
        });

        it('should handle custom event types', (done) => {
            const mockEventSource = {
                readyState: EventSource.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'custom-event') {
                        setTimeout(() => handler({ data: 'custom' }), 100);
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.EventSource = vi.fn(() => mockEventSource) as any;

            client.addEventListener('custom-event', (msg) => {
                expect(msg.data).toBe('custom');
                done();
            });

            client.connect();
        });
    });

    describe('Reconnection', () => {
        it('should reconnect on connection loss', async () => {
            let connectCount = 0;
            const mockEventSource = {
                readyState: EventSource.OPEN,
                addEventListener: vi.fn((event, handler) => {
                    if (event === 'error') {
                        setTimeout(() => handler(new Error('Lost connection')), 100);
                    }
                }),
                removeEventListener: vi.fn(),
                close: vi.fn(),
            };
            global.EventSource = vi.fn(() => {
                connectCount++;
                return mockEventSource;
            }) as any;

            await client.connect();

            await new Promise((resolve) => setTimeout(resolve, 1500));

            expect(connectCount).toBeGreaterThan(1);
        });
    });
});
