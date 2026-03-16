import Fastify from 'fastify';
import fastifyWebsocket from '@fastify/websocket';
import fastifyCors from '@fastify/cors';

const fastify = Fastify({
  logger: true
});

// Configure CORS
fastify.register(fastifyCors, {
  origin: '*'
});

// Register WebSocket support
fastify.register(fastifyWebsocket);

/**
 * Endpoint for RSC Data Layer (Phase 3.1)
 * Mapped via Jotai and TanStack Query in the frontend.
 */
fastify.get('/api/state/unified', async (request, reply) => {
  return {
    status: 'success',
    orchestration: {
      activeAgents: 5,
      systemHealth: 'optimal'
    }
  };
});

/**
 * WebSocket Endpoint for Real-Time Event Tailing (Phase 3.3)
 * Bound to data-cloud/event mechanisms for instant agent feedback.
 */
fastify.register(async function (fastify) {
  fastify.get('/tail/events', { websocket: true }, (con, req) => {
    fastify.log.info('Client connected to real-time event tailing');
    
    // Send initial connection success
    con.socket.send(JSON.stringify({ type: 'CONNECTED', message: 'Tailing started' }));

    // Simulate real-time push-based events
    const interval = setInterval(() => {
      if (con.socket.readyState === 1) { // OPEN
        con.socket.send(JSON.stringify({
          type: 'AGENT_EVENT',
          timestamp: new Date().toISOString(),
          data: {
            agentId: 'agent-123',
            event: 'reasoning_cycle_complete',
            confidence: 0.98
          }
        }));
      }
    }, 5000); // Push event every 5 seconds

    con.socket.on('message', message => {
      try {
        const msg = JSON.parse(message.toString());
        fastify.log.info('Received from client', msg);
        if (msg.type === 'SUBSCRIBE') {
          // Additional subscription logic could be implemented here
        }
      } catch (e) {
        fastify.log.error(e, 'Failed to parse WebSocket message');
      }
    });

    con.socket.on('close', () => {
      fastify.log.info('Client disconnected from real-time event tailing');
      clearInterval(interval);
    });
  });
});

const start = async () => {
  try {
    await fastify.listen({ port: 3001, host: '0.0.0.0' });
    fastify.log.info(`API Edge Gateway listening on 3001`);
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
};

start();