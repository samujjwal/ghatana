/**
 * Backend Gateway Middleware
 *
 * Routes requests to the appropriate backend service:
 * - Local routes: handled by Node.js API
 * - Java backend routes: proxied to Java service
 *
 * This ensures the frontend always talks to a single API endpoint (port 7002)
 * and is unaware of multiple backend services.
 *
 * @doc.type middleware
 * @doc.purpose API gateway and service router
 * @doc.layer platform
 * @doc.pattern Proxy, Middleware
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fetch from 'node-fetch';

// Java backend service location
const JAVA_BACKEND = process.env.JAVA_BACKEND_URL || 'http://localhost:7003';

/**
 * Routes that should be proxied to the Java backend
 */
const JAVA_BACKEND_ROUTES = [
  '/api/rail',
  '/api/agents',
  '/api/metrics',
  '/api/health/deep', // Deep health checks go to Java
];

/**
 * Determine if a request should be proxied to the Java backend
 */
function shouldProxyToJava(path: string): boolean {
  return JAVA_BACKEND_ROUTES.some((route) => path.startsWith(route));
}

/**
 * Proxy request to Java backend
 */
async function proxyToJavaBackend(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  try {
    const targetUrl = new URL(JAVA_BACKEND + request.url);

    const proxyResponse = await fetch(targetUrl.toString(), {
      method: request.method as unknown,
      headers: {
        ...request.headers,
        host: targetUrl.hostname,
      },
      body:
        request.method !== 'GET' && request.method !== 'HEAD'
          ? JSON.stringify(await request.json().catch(() => ({})))
          : undefined,
    } as unknown);

    const contentType = proxyResponse.headers.get('content-type');
    const body = await proxyResponse.text();

    // Forward the response
    reply.code(proxyResponse.status);

    if (contentType) {
      reply.header('content-type', contentType);
    }

    reply.send(body);
  } catch (error) {
    console.error('[Gateway] Error proxying to Java backend:', error);
    reply.status(502).send({
      error: 'Bad Gateway',
      message: 'Failed to reach backend service',
      details: error instanceof Error ? error.message : String(error),
    });
  }
}

/**
 * Register the backend gateway middleware
 *
 * Uses onRequest hook which runs BEFORE route matching,
 * allowing us to intercept and proxy Java backend requests
 * without interfering with local route handlers.
 */
export async function registerBackendGateway(fastify: FastifyInstance) {
  console.log('[Gateway] Registering backend gateway middleware');
  console.log('[Gateway] Java backend URL:', JAVA_BACKEND);
  console.log('[Gateway] Routes to proxy:', JAVA_BACKEND_ROUTES.join(', '));

  // Use onRequest hook (runs before route matching) instead of preHandler
  fastify.addHook('onRequest', async (request, reply) => {
    if (shouldProxyToJava(request.url)) {
      console.log(
        '[Gateway] Proxying',
        request.method,
        request.url,
        '→ Java backend'
      );
      await proxyToJavaBackend(request, reply);
      // Return early - this request has been handled
      return;
    }
    // Request does not match Java backend routes, continue to normal route handling
  });

  console.log('[Gateway] Middleware registered successfully');
}
