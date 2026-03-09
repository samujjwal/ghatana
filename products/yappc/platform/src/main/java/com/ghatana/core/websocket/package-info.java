/**
 * @ghatana/activej-websocket - WebSocket Support for ActiveJ
 *
 * Production-grade WebSocket server support for ActiveJ-based services
 * in the Ghatana Hybrid Backend architecture.
 *
 * <p><b>Purpose</b><br>
 * Provides WebSocket infrastructure for real-time streaming from Java/ActiveJ
 * core domain services to the Node/Fastify UI layer and frontend clients.
 *
 * <p><b>Components</b><br>
 * - {@link WebSocketConnection} - Connection wrapper with send/receive
 * - {@link WebSocketConnectionManager} - Connection lifecycle management
 * - {@link WebSocketMessage} - Standard message envelope
 * - {@link WebSocketEndpoint} - HTTP endpoint for WebSocket upgrade
 * - {@link StreamPublisher} - High-level event publishing API
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Create connection manager
 * WebSocketConnectionManager manager = new WebSocketConnectionManager();
 *
 * // 2. Create WebSocket endpoint
 * WebSocketEndpoint endpoint = WebSocketEndpoint.builder()
 *     .connectionManager(manager)
 *     .build();
 *
 * // 3. Add to HTTP server
 * HttpServer server = HttpServerBuilder.create()
 *     .withPort(8080)
 *     .addRoute(HttpMethod.GET, "/ws", endpoint.asServlet(reactor))
 *     .build();
 *
 * // 4. Create publisher for sending events
 * StreamPublisher publisher = StreamPublisher.builder()
 *     .connectionManager(manager)
 *     .build();
 * publisher.startHeartbeat();
 *
 * // 5. Publish events
 * publisher.publish("events/orders", orderEvent);
 * publisher.publishToTenant("events/orders", "tenant-123", orderEvent);
 * }</pre>
 *
 * @see WebSocketConnection
 * @see WebSocketConnectionManager
 * @see WebSocketMessage
 * @see WebSocketEndpoint
 * @see StreamPublisher
 */
package com.ghatana.core.websocket;
