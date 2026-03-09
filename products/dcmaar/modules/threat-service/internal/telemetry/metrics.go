package telemetry

import (
    "context"
    "encoding/json"
    "net/http"
    "sync"
    "time"

    "github.com/prometheus/client_golang/prometheus"
    "go.uber.org/zap"
)

// Metrics provides application metrics.
type Metrics struct {
    logger  *zap.Logger
    enabled bool
    mu      sync.RWMutex
    counters map[string]uint64

    // Prometheus
    promEnabled bool
    pDedupeEvents   prometheus.Counter
    pIdemEnvelopes  prometheus.Counter
    pPolicyDenied   prometheus.Counter
    // HTTP
    pHTTPRequests   *prometheus.CounterVec
    pHTTPDuration   *prometheus.HistogramVec
    pHTTPRespSize   *prometheus.HistogramVec
}

// MetricsConfig holds the metrics configuration.
type MetricsConfig struct {
	Enabled           bool
	Namespace         string
	Subsystem         string
	CollectPeriod     time.Duration
	HistogramBoundaries []float64
}

// DefaultMetricsConfig returns the default metrics configuration.
func DefaultMetricsConfig() MetricsConfig {
	return MetricsConfig{
		Enabled:       true,
		Namespace:     "dcmaar",
		Subsystem:     "server",
		CollectPeriod: 15 * time.Second,
		HistogramBoundaries: []float64{
			0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 2.5, 5, 10, 30, 60,
		},
	}
}

// NewMetrics creates a new Metrics instance.
func NewMetrics(cfg MetricsConfig, logger *zap.Logger) (*Metrics, error) {
    m := &Metrics{
        logger:    logger,
        enabled:   cfg.Enabled,
        counters:  make(map[string]uint64),
        promEnabled: cfg.Enabled, // mirror for now; can split later via cfg
    }

    if m.promEnabled {
        // Register Prometheus counters in the default registry so promhttp handler can scrape them.
        // Use Namespace/SubSystem to keep metric names tidy if provided.
        ns := cfg.Namespace
        ss := cfg.Subsystem

        m.pDedupeEvents = prometheus.NewCounter(prometheus.CounterOpts{
            Namespace: ns,
            Subsystem: ss,
            Name:      "ingest_dedupe_events_total",
            Help:      "Total number of deduplicated events across requests.",
        })
        m.pIdemEnvelopes = prometheus.NewCounter(prometheus.CounterOpts{
            Namespace: ns,
            Subsystem: ss,
            Name:      "ingest_idempotent_envelopes_skipped_total",
            Help:      "Total number of event envelopes skipped due to idempotency keys.",
        })
        m.pPolicyDenied = prometheus.NewCounter(prometheus.CounterOpts{
            Namespace: ns,
            Subsystem: ss,
            Name:      "policy_denied_total",
            Help:      "Total number of actions denied by policy.",
        })

        // Best-effort registration; log warnings on duplicate registration.
        // It's safe to ignore AlreadyRegistered errors in typical server restarts during dev.
        if err := prometheus.Register(m.pDedupeEvents); err != nil {
            logger.Warn("prometheus register", zap.String("metric", "ingest_dedupe_events_total"), zap.Error(err))
        }
        if err := prometheus.Register(m.pIdemEnvelopes); err != nil {
            logger.Warn("prometheus register", zap.String("metric", "ingest_idempotent_envelopes_skipped_total"), zap.Error(err))
        }
        if err := prometheus.Register(m.pPolicyDenied); err != nil {
            logger.Warn("prometheus register", zap.String("metric", "policy_denied_total"), zap.Error(err))
        }

        // HTTP metrics
        m.pHTTPRequests = prometheus.NewCounterVec(prometheus.CounterOpts{
            Namespace: ns,
            Subsystem: ss,
            Name:      "http_requests_total",
            Help:      "Total number of HTTP requests by method, path, and status.",
        }, []string{"method", "path", "status"})
        m.pHTTPDuration = prometheus.NewHistogramVec(prometheus.HistogramOpts{
            Namespace: ns,
            Subsystem: ss,
            Name:      "http_request_duration_seconds",
            Help:      "HTTP request duration in seconds.",
            Buckets:   cfg.HistogramBoundaries,
        }, []string{"method", "path", "status"})
        m.pHTTPRespSize = prometheus.NewHistogramVec(prometheus.HistogramOpts{
            Namespace: ns,
            Subsystem: ss,
            Name:      "http_response_size_bytes",
            Help:      "Size of HTTP responses in bytes.",
            Buckets:   cfg.HistogramBoundaries,
        }, []string{"method", "path", "status"})
        if err := prometheus.Register(m.pHTTPRequests); err != nil {
            logger.Warn("prometheus register", zap.String("metric", "http_requests_total"), zap.Error(err))
        }
        if err := prometheus.Register(m.pHTTPDuration); err != nil {
            logger.Warn("prometheus register", zap.String("metric", "http_request_duration_seconds"), zap.Error(err))
        }
        if err := prometheus.Register(m.pHTTPRespSize); err != nil {
            logger.Warn("prometheus register", zap.String("metric", "http_response_size_bytes"), zap.Error(err))
        }
    }

    return m, nil
}

// RecordRequest records an HTTP request (no-op implementation).
func (m *Metrics) RecordRequest(ctx context.Context, method, path, status string, duration time.Duration, size int64) {
    if m == nil || !m.enabled {
        return
    }
    if !m.promEnabled {
        return
    }
    if m.pHTTPRequests != nil {
        m.pHTTPRequests.WithLabelValues(method, path, status).Inc()
    }
    if m.pHTTPDuration != nil {
        m.pHTTPDuration.WithLabelValues(method, path, status).Observe(duration.Seconds())
    }
    if m.pHTTPRespSize != nil {
        m.pHTTPRespSize.WithLabelValues(method, path, status).Observe(float64(size))
    }
}

// RecordResponse records an HTTP response (no-op implementation).
func (m *Metrics) RecordResponse(ctx context.Context, method, path, status string, size int64) {
    if m == nil || !m.enabled {
        return
    }
    if !m.promEnabled {
        return
    }
    // no-op; metrics recorded in RecordRequest
}

// RecordError records an error (no-op implementation).
func (m *Metrics) RecordError(ctx context.Context, err error) {
	if m == nil || !m.enabled {
		return
	}
	// No-op implementation - metrics disabled for now
}

// Handler returns a no-op HTTP handler for metrics.
func (m *Metrics) Handler() http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        w.Header().Set("Content-Type", "application/json")
        snap := m.Snapshot()
        _ = json.NewEncoder(w).Encode(snap)
    })
}

// Shutdown shuts down the metrics provider (no-op implementation).
func (m *Metrics) Shutdown(ctx context.Context) error {
    return nil
}

// Inc increments an arbitrary counter by delta.
func (m *Metrics) Inc(name string, delta uint64) {
    if m == nil || !m.enabled {
        return
    }
    m.mu.Lock()
    m.counters[name] += delta
    m.mu.Unlock()

    if !m.promEnabled {
        return
    }

    // Mirror known counters into Prometheus
    switch name {
    case "ingest_dedupe_events_total":
        if m.pDedupeEvents != nil { m.pDedupeEvents.Add(float64(delta)) }
    case "ingest_idempotent_envelopes_skipped_total":
        if m.pIdemEnvelopes != nil { m.pIdemEnvelopes.Add(float64(delta)) }
    case "policy_denied_total":
        if m.pPolicyDenied != nil { m.pPolicyDenied.Add(float64(delta)) }
    }
}

// Snapshot returns a copy of current counters.
func (m *Metrics) Snapshot() map[string]uint64 {
    out := make(map[string]uint64)
    if m == nil {
        return out
    }
    m.mu.RLock()
    for k, v := range m.counters {
        out[k] = v
    }
    m.mu.RUnlock()
    return out
}

// Convenience helpers
func (m *Metrics) IncDedupeEvents(n int)          { if n > 0 { m.Inc("ingest_dedupe_events_total", uint64(n)) } }
func (m *Metrics) IncIdempotentEnvelopes(n int)   { if n > 0 { m.Inc("ingest_idempotent_envelopes_skipped_total", uint64(n)) } }
func (m *Metrics) IncPolicyDenied()               { m.Inc("policy_denied_total", 1) }
