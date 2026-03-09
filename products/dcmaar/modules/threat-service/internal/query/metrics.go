package query

import (
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

var (
	queryDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "query_duration_seconds",
		Help:    "Duration of query executions",
		Buckets: prometheus.DefBuckets,
	}, []string{"method", "status"})

	queryCount = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "query_requests_total",
		Help: "Total number of query requests",
	}, []string{"method", "status"})

	queryErrors = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "query_errors_total",
		Help: "Total number of query errors",
	}, []string{"method", "error_type"})

	queryResultCount = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "query_results_returned",
		Help:    "Number of results returned by queries",
		Buckets: []float64{1, 10, 50, 100, 500, 1000, 5000, 10000},
	}, []string{"method"})
)
