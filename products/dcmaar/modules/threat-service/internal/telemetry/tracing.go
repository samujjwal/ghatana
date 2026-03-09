package telemetry

import (
	"context"
	"fmt"
	"os"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"
	"go.uber.org/zap"
)

// Tracer provides tracing functionality.
type Tracer struct {
	tracerProvider *sdktrace.TracerProvider
	exporter       sdktrace.SpanExporter
	logger         *zap.Logger
}

// TracingConfig holds the tracing configuration.
type TracingConfig struct {
	Enabled       bool
	ServiceName   string
	ServiceID     string
	Environment   string
	Version       string
	SamplingRatio float64
	OTLPEndpoint  string
}

// DefaultTracingConfig returns the default tracing configuration.
func DefaultTracingConfig() TracingConfig {
	return TracingConfig{
		Enabled:       true,
		ServiceName:   "dcmaar-server",
		ServiceID:     "1",
		Environment:   "development",
		Version:       "0.1.0",
		SamplingRatio: 1.0,
		OTLPEndpoint:  "localhost:4317",
	}
}

// NewTracer creates a new Tracer instance.
func NewTracer(cfg TracingConfig, logger *zap.Logger) (*Tracer, error) {
	if !cfg.Enabled {
		return &Tracer{
			tracerProvider: sdktrace.NewTracerProvider(
				sdktrace.WithSampler(sdktrace.NeverSample()),
			),
		}, nil
	}

	// Create the resource
	res, err := resource.New(
		context.Background(),
		resource.WithAttributes(
			semconv.ServiceNameKey.String(cfg.ServiceName),
			semconv.ServiceInstanceIDKey.String(cfg.ServiceID),
			semconv.ServiceVersionKey.String(cfg.Version),
			semconv.DeploymentEnvironmentKey.String(cfg.Environment),
			semconv.HostNameKey.String(mustHostname()),
		),
		resource.WithProcessRuntimeDescription(),
		resource.WithTelemetrySDK(),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create resource: %w", err)
	}

	// Create the OTLP exporter
	client := otlptracegrpc.NewClient(
		otlptracegrpc.WithEndpoint(cfg.OTLPEndpoint),
		otlptracegrpc.WithInsecure(), // Use WithInsecure only for development
	)

	exporter, err := otlptrace.New(context.Background(), client)
	if err != nil {
		return nil, fmt.Errorf("failed to create OTLP exporter: %w", err)
	}

	// Create the tracer provider
	sampler := sdktrace.ParentBased(
		sdktrace.TraceIDRatioBased(cfg.SamplingRatio),
		sdktrace.WithRemoteParentSampled(nil),
	)

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithResource(res),
		sdktrace.WithSampler(sampler),
		sdktrace.WithBatcher(
			exporter,
			sdktrace.WithMaxExportBatchSize(1000),
			sdktrace.WithMaxQueueSize(10000),
			sdktrace.WithBatchTimeout(5*time.Second),
		),
	)

	// Set global propagator and tracer provider
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	return &Tracer{
		tracerProvider: tp,
		exporter:       exporter,
		logger:         logger,
	}, nil
}

// StartSpan starts a new span with the given name and options.
func (t *Tracer) StartSpan(ctx context.Context, name string, opts ...interface{}) (context.Context, func()) {
	if t == nil || t.tracerProvider == nil {
		return ctx, func() {}
	}

	// Convert custom options to trace options
	var traceOpts []trace.SpanStartOption
	for _, o := range opts {
		switch v := o.(type) {
		case []attribute.KeyValue:
			traceOpts = append(traceOpts, trace.WithAttributes(v...))
		case attribute.KeyValue:
			traceOpts = append(traceOpts, trace.WithAttributes(v))
		}
	}

	ctx, span := otel.Tracer("dcmaar").Start(ctx, name, traceOpts...)
	return ctx, func() { span.End() }
}

// Shutdown shuts down the tracer provider.
func (t *Tracer) Shutdown(ctx context.Context) error {
	if t == nil || t.tracerProvider == nil {
		return nil
	}

	if t.exporter != nil {
		if err := t.exporter.Shutdown(ctx); err != nil {
			return fmt.Errorf("failed to shutdown exporter: %w", err)
		}
	}

	if err := t.tracerProvider.Shutdown(ctx); err != nil {
		return fmt.Errorf("failed to shutdown tracer provider: %w", err)
	}

	return nil
}

// mustHostname returns the hostname or panics.
func mustHostname() string {
	hostname, err := os.Hostname()
	if err != nil {
		panic(fmt.Sprintf("failed to get hostname: %v", err))
	}
	return hostname
}
