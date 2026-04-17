import * as Sentry from "@sentry/node";
import { nodeProfilingIntegration } from "@sentry/profiling-node";
import type { FastifyInstance, FastifyError } from "fastify";
import { errorHandler } from "../middleware/error-handler.js";

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
    beforeSend(event, _hint) {
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

  app.addHook("onError", (request, _reply, error, done) => {
    if (!error.validation) {
      Sentry.withScope((scope) => {
        if (request.correlationId) {
          scope.setTag("correlation_id", request.correlationId);
        }
        scope.setTag("request_id", request.id);
        scope.setContext("request", {
          method: request.method,
          path: request.url,
        });
        Sentry.captureException(error);
      });
    }

    done();
  });

  app.setErrorHandler((error: FastifyError, request, reply) => {
    errorHandler(error, request, reply);
  });

  // Capture unhandled promise rejections
  process.on("unhandledRejection", (reason, promise) => {
    app.log.error({ reason, promise }, "Unhandled promise rejection");
    Sentry.captureException(reason);
  });

  app.log.info("Error tracking (Sentry) initialized");
}
