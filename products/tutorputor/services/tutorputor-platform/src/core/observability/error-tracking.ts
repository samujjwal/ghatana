import * as Sentry from "@sentry/node";
import { nodeProfilingIntegration } from "@sentry/profiling-node";
import type { FastifyInstance, FastifyError } from "fastify";

/**
 * Setup error tracking with Sentry.
 *
 * ✅ PRODUCTION-GRADE: Comprehensive error tracking with context
 */
export function setupErrorTracking(app: FastifyInstance) {
  const dsn = process.env.SENTRY_DSN;

  if (!dsn) {
    app.log.warn("SENTRY_DSN not configured, error tracking disabled");
    return;
  }

  Sentry.init({
    dsn,
    environment: process.env.NODE_ENV || "development",
    integrations: [nodeProfilingIntegration()],
    tracesSampleRate: parseFloat(
      process.env.SENTRY_TRACES_SAMPLE_RATE || "0.1",
    ),
    profilesSampleRate: parseFloat(
      process.env.SENTRY_PROFILES_SAMPLE_RATE || "0.1",
    ),
    beforeSend(event, hint) {
      // Filter out sensitive data
      if (event.request) {
        delete event.request.cookies;
        if (event.request.headers) {
          delete event.request.headers.authorization;
          delete event.request.headers.cookie;
        }
      }
      return event;
    },
  });

  // Capture all unhandled errors
  app.setErrorHandler((error: FastifyError, request, reply) => {
    // Don't report validation errors to Sentry
    if (error.validation) {
      app.log.warn({ error, validation: error.validation }, "Validation error");
      return reply.code(400).send({
        error: "Validation Error",
        message: error.message,
        validation: error.validation,
      });
    }

    // Capture in Sentry
    Sentry.captureException(error, {
      tags: {
        method: request.method,
        route: request.routeOptions.url || "unknown",
        tenant_id: (request.headers["x-tenant-id"] as string) || "default",
      },
      user: {
        id: (request as any).user?.id,
        email: (request as any).user?.email,
      },
      extra: {
        query: request.query,
        params: request.params,
        statusCode: error.statusCode,
      },
    });

    app.log.error(
      { error, request: { url: request.url, method: request.method } },
      "Unhandled error",
    );

    // Send appropriate response
    const statusCode = error.statusCode || 500;
    const isDevelopment = process.env.NODE_ENV === "development";

    reply.code(statusCode).send({
      error: error.name || "Internal Server Error",
      message: isDevelopment ? error.message : "An unexpected error occurred",
      ...(isDevelopment && { stack: error.stack }),
    });
  });

  // Capture unhandled promise rejections
  process.on("unhandledRejection", (reason, promise) => {
    app.log.error({ reason, promise }, "Unhandled promise rejection");
    Sentry.captureException(reason);
  });

  app.log.info("Error tracking (Sentry) initialized");
}
