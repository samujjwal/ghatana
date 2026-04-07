import { FastifyPluginAsync } from 'fastify';

type FrontendErrorPayload = {
  source: 'react-error-boundary' | 'window-error' | 'unhandled-rejection';
  message: string;
  stack?: string;
  componentStack?: string;
  route?: string;
  userAgent?: string;
  timestamp?: string;
};

const telemetryRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.post<{ Body: FrontendErrorPayload }>(
    '/telemetry/frontend-errors',
    async (request, reply) => {
      const payload = request.body;

      if (
        !payload ||
        typeof payload.message !== 'string' ||
        payload.message.trim().length === 0
      ) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'message is required',
        });
      }

      request.log.error(
        {
          event: 'frontend.error',
          source: payload.source,
          message: payload.message,
          route: payload.route,
          userAgent: payload.userAgent,
          timestamp: payload.timestamp,
          userId: request.user?.userId,
          role: request.user?.role,
          stack: payload.stack,
          componentStack: payload.componentStack,
        },
        'Frontend runtime error reported'
      );

      return reply.status(202).send({ accepted: true });
    }
  );
};

export default telemetryRoutes;
