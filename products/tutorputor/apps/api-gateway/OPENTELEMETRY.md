# OpenTelemetry Configuration

The API Gateway has OpenTelemetry packages installed for distributed tracing. To enable OpenTelemetry, configure it via environment variables:

## Required Environment Variables

```bash
# Service resource attributes
OTEL_SERVICE_NAME=tutorputor-api-gateway
OTEL_SERVICE_VERSION=1.0.0
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=production

# Trace exporter
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf

# Sampling
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1
```

## Correlation-ID Propagation

Correlation-ID middleware is already implemented in both:
- `apps/api-gateway/src/createServer.ts` - API Gateway layer
- `services/tutorputor-platform/src/setup.ts` - Platform service layer

The middleware:
1. Reads `x-correlation-id` header from incoming requests
2. Falls back to Fastify request ID or generates a UUID
3. Sets the correlation ID on the request object
4. Adds `x-correlation-id` header to all responses

This ensures trace continuity across service boundaries.

## Distributed Tracing

The platform service uses Sentry for distributed tracing (configured in `src/core/observability/error-tracking.ts`). To enable Sentry tracing:

```bash
SENTRY_DSN=https://your-sentry-dsn@sentry.io/project-id
SENTRY_TRACES_SAMPLE_RATE=0.1
SENTRY_PROFILES_SAMPLE_RATE=0.1
```

For full OpenTelemetry support, consider adding the NodeSDK initialization in a separate instrumentation file when type compatibility is resolved.
