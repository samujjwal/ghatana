package telemetry

import (
	"context"
	"time"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
	"go.uber.org/zap"
)

// Telemetry provides application-wide telemetry functionality.
type Telemetry struct {
	metrics *Metrics
	tracer  *Tracer
	logger  *zap.Logger
}

// Config holds the telemetry configuration.
type Config struct {
	Metrics MetricsConfig
	Tracing TracingConfig
}

// DefaultConfig returns the default telemetry configuration.
func DefaultConfig() Config {
	return Config{
		Metrics: DefaultMetricsConfig(),
		Tracing: DefaultTracingConfig(),
	}
}

// New creates a new Telemetry instance.
func New(cfg Config, logger *zap.Logger) (*Telemetry, error) {
	// Initialize metrics
	metrics, err := NewMetrics(cfg.Metrics, logger)
	if err != nil {
		return nil, err
	}

	// Initialize tracing
	tracer, err := NewTracer(cfg.Tracing, logger)
	if err != nil {
		return nil, err
	}

	return &Telemetry{
		metrics: metrics,
		tracer:  tracer,
		logger:  logger,
	}, nil
}

// Metrics returns the metrics instance.
func (t *Telemetry) Metrics() *Metrics {
	return t.metrics
}

// Tracer returns the tracer instance.
func (t *Telemetry) Tracer() *Tracer {
	return t.tracer
}

// Shutdown shuts down the telemetry components.
func (t *Telemetry) Shutdown(ctx context.Context) error {
	var err error

	// Shutdown metrics
	if t.metrics != nil {
		if e := t.metrics.Shutdown(ctx); e != nil {
			t.logger.Error("failed to shutdown metrics", zap.Error(e))
			err = e
		}
	}

	// Shutdown tracing
	if t.tracer != nil {
		if e := t.tracer.Shutdown(ctx); e != nil {
			t.logger.Error("failed to shutdown tracer", zap.Error(e))
			err = e
		}
	}

	return err
}

// StartSpan starts a new span with the given name and options.
func (t *Telemetry) StartSpan(ctx context.Context, name string, attrs ...interface{}) (context.Context, func()) {
	if t == nil || t.tracer == nil {
		return ctx, func() {}
	}

	return t.tracer.StartSpan(ctx, name, attrs...)
}

// RecordError records an error with the given attributes.
func (t *Telemetry) RecordError(ctx context.Context, err error, attrs ...attribute.KeyValue) {
	if t == nil || t.metrics == nil {
		return
	}

	t.metrics.RecordError(ctx, err)
}

// RecordRequest records an HTTP request.
func (t *Telemetry) RecordRequest(ctx context.Context, method, path, status string, duration time.Duration, size int64) {
	if t == nil || t.metrics == nil {
		return
	}

	t.metrics.RecordRequest(ctx, method, path, status, duration, size)
}

// RecordResponse records an HTTP response.
func (t *Telemetry) RecordResponse(ctx context.Context, method, path, status string, size int64) {
	if t == nil || t.metrics == nil {
		return
	}

	t.metrics.RecordResponse(ctx, method, path, status, size)
}

// Logger returns a logger with tracing information.
func (t *Telemetry) Logger(ctx context.Context) *zap.Logger {
	if t == nil || t.logger == nil {
		return zap.NewNop()
	}

	// Get the current span context from the context
	if span := trace.SpanFromContext(ctx); span != nil {
		spanCtx := span.SpanContext()
		if spanCtx.HasTraceID() {
			traceID := spanCtx.TraceID().String()
			return t.logger.With(zap.String("trace_id", traceID))
		}
	}

	return t.logger
}
