package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/go-redis/redis/v8"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/config"
	iaudit "github.com/samujjwal/dcmaar/apps/server/internal/audit"
	"github.com/samujjwal/dcmaar/apps/server/internal/handlers"
	iid "github.com/samujjwal/dcmaar/apps/server/internal/identity"
	"github.com/samujjwal/dcmaar/apps/server/internal/labeling"
	imw "github.com/samujjwal/dcmaar/apps/server/internal/middleware"
	"github.com/samujjwal/dcmaar/apps/server/internal/miner"
	"github.com/samujjwal/dcmaar/apps/server/internal/nlq"
	ipolicy "github.com/samujjwal/dcmaar/apps/server/internal/policy"
	"github.com/samujjwal/dcmaar/apps/server/internal/query"
	"github.com/samujjwal/dcmaar/apps/server/internal/retention"
	"github.com/samujjwal/dcmaar/apps/server/internal/storage"
	stelemetry "github.com/samujjwal/dcmaar/apps/server/internal/telemetry"
	telctx "github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
)

// statusWriter wraps http.ResponseWriter to capture status and size.
type statusWriter struct {
	http.ResponseWriter
	status int
	bytes  int
}

func (w *statusWriter) WriteHeader(code int) { w.status = code; w.ResponseWriter.WriteHeader(code) }
func (w *statusWriter) Write(b []byte) (int, error) {
	n, err := w.ResponseWriter.Write(b)
	w.bytes += n
	return n, err
}

// Config holds the server configuration.
type Config struct {
	GRPCAddr            string
	HTTPAddr            string
	TLSCertFile         string
	TLSKeyFile          string
	ClickHouseDSN       string
	MaxReceiveMsgSize   int
	Cache               config.CacheConfig
	IdentityTTL         time.Duration
	PolicyTTL           time.Duration
	PolicyDenyByDefault bool
	PolicyUseRego       bool
	RegoPolicyFile      string
	DevHelpers          bool
}

// Server represents the gRPC server.
type Server struct {
	config       *Config
	grpcServer   *grpc.Server
	httpServer   *http.Server
	storage      *storage.Storage
	logger       *zap.Logger
	telemetry    *stelemetry.Telemetry
	metrics      *stelemetry.Metrics
	shutdownOnce sync.Once
	healthServer *health.Server
	// Query service resources
	queryDB     *sql.DB
	redisClient *redis.Client
	cache       *query.QueryCache
	idCache     *iid.Cache
	polCache    *ipolicy.Cache
	// NLQ service
	nlqService nlq.Service
	// Miner service
	minerService *miner.ToilMinerService
	// Retention advisor service
	retentionAdvisor retention.Advisor
	// Labeling service
	labelingService labeling.Service
}

// NewServer creates a new server instance.
func NewServer(cfg *Config, logger *zap.Logger, cache *query.QueryCache) (*Server, error) {
	// Initialize storage
	storage, err := storage.New(cfg.ClickHouseDSN, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize storage: %w", err)
	}

	// Initialize metrics with default config
	metricsConfig := stelemetry.DefaultMetricsConfig()
	metrics, err := stelemetry.NewMetrics(metricsConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize metrics: %w", err)
	}

	s := &Server{
		config:  cfg,
		logger:  logger,
		storage: storage,
		metrics: metrics,
		cache:   cache,
		idCache: iid.NewCache(func() time.Duration {
			if cfg.IdentityTTL > 0 {
				return cfg.IdentityTTL
			}
			return 2 * time.Minute
		}()),
	}

	// Initialize gRPC server with middleware
	opts := []grpc.ServerOption{
		grpc.MaxRecvMsgSize(cfg.MaxReceiveMsgSize),
		grpc.ChainUnaryInterceptor(
			s.identityInterceptor(),
			s.corrIDInterceptor(),
			s.metricsInterceptor(),
		),
	}

	// Add TLS credentials if configured
	if cfg.TLSCertFile != "" && cfg.TLSKeyFile != "" {
		creds, err := credentials.NewServerTLSFromFile(cfg.TLSCertFile, cfg.TLSKeyFile)
		if err != nil {
			return nil, fmt.Errorf("failed to load TLS credentials: %w", err)
		}
		opts = append(opts, grpc.Creds(creds))
	}

	s.grpcServer = grpc.NewServer(opts...)

	// Register services
	s.registerServices()

	// Set up health check server
	healthServer := health.NewServer()
	healthServer.SetServingStatus("", grpc_health_v1.HealthCheckResponse_SERVING)
	grpc_health_v1.RegisterHealthServer(s.grpcServer, healthServer)

	// Enable reflection for gRPC CLI tools
	reflection.Register(s.grpcServer)

	// Set up HTTP server for health checks and metrics
	s.setupHTTPServer()

	return s, nil
}

// Start starts the server.
func (s *Server) Start() error {
	// Start gRPC server
	lis, err := net.Listen("tcp", s.config.GRPCAddr)
	if err != nil {
		return fmt.Errorf("failed to listen: %w", err)
	}

	s.logger.Info("Starting gRPC server", zap.String("addr", s.config.GRPCAddr))

	// Start HTTP server in a goroutine
	go func() {
		s.logger.Info("Starting HTTP server", zap.String("addr", s.config.HTTPAddr))
		if err := s.httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			s.logger.Error("HTTP server error", zap.Error(err))
		}
	}()

	// Start gRPC server in a goroutine
	go func() {
		if err := s.grpcServer.Serve(lis); err != nil {
			s.logger.Error("gRPC server error", zap.Error(err))
		}
	}()

	return nil
}

// Stop gracefully stops the server.
func (s *Server) Stop(ctx context.Context) error {
	var returnErr error

	s.shutdownOnce.Do(func() {
		s.logger.Info("Shutting down server...")

		// Set health check to NOT_SERVING
		s.healthServer.SetServingStatus("", grpc_health_v1.HealthCheckResponse_NOT_SERVING)

		// Stop accepting new connections
		if s.grpcServer != nil {
			s.grpcServer.GracefulStop()
		}

		// Shutdown HTTP server
		if s.httpServer != nil {
			if err := s.httpServer.Shutdown(ctx); err != nil {
				s.logger.Error("Failed to shutdown HTTP server", zap.Error(err))
				returnErr = err
			}
		}

		// Close storage connections
		if s.storage != nil {
			if err := s.storage.Close(); err != nil {
				s.logger.Error("Failed to close storage", zap.Error(err))
				if returnErr == nil {
					returnErr = err
				}
			}
		}

		// Close Redis client
		if s.redisClient != nil {
			if err := s.redisClient.Close(); err != nil {
				s.logger.Error("Failed to close Redis client", zap.Error(err))
				if returnErr == nil {
					returnErr = err
				}
			}
		}

		// Close database connection
		if s.queryDB != nil {
			if err := s.queryDB.Close(); err != nil {
				s.logger.Error("Failed to close database connection", zap.Error(err))
				if returnErr == nil {
					returnErr = err
				}
			}
		}

		// Shutdown telemetry
		if s.telemetry != nil {
			telCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			if err := s.telemetry.Shutdown(telCtx); err != nil {
				s.logger.Error("Failed to shutdown telemetry", zap.Error(err))
				if returnErr == nil {
					returnErr = err
				}
			}
		}

		s.logger.Info("Server stopped")
	})

	return returnErr
}

// setupHTTPServer configures the HTTP server for health checks and metrics.
func (s *Server) setupHTTPServer() {
	mux := http.NewServeMux()

	// Health check endpoint
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{
			"status":  "ok",
			"service": "dcmaar-server",
		})
	})

	// Metrics endpoint (Prometheus) and internal counters snapshot
	mux.Handle("/metrics", promhttp.Handler())
	mux.Handle("/metrics-internal", s.metrics.Handler())

	if s.config.DevHelpers {
		// WhoAmI endpoint to reflect identity from middleware (dev/test helper)
		mux.HandleFunc("/whoami", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			type resp struct {
				Subject string   `json:"subject"`
				Roles   []string `json:"roles"`
			}
			id := imw.IdentityFromContext(r.Context())
			if id == nil {
				_ = json.NewEncoder(w).Encode(resp{})
				return
			}
			_ = json.NewEncoder(w).Encode(resp{Subject: id.Subject, Roles: id.Roles})
		})

		// Lightweight policy fetch for Desktop (dev/testing): /policy?subject=<sub>&resource=<res>
		mux.HandleFunc("/policy", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			subject := r.URL.Query().Get("subject")
			resource := r.URL.Query().Get("resource")
			if subject == "" {
				subject = ""
			}
			resources := []string{}
			if resource != "" {
				resources = []string{resource}
			}
			var pol *pb.Policy
			var err error
			ctx := r.Context()
			if s.polCache != nil {
				pol, err = s.polCache.Get(ctx, &pb.PolicyRequest{AgentId: subject})
			} else if s.storage != nil {
				pol, err = s.storage.GetPolicy(ctx, subject, resources)
			}
			if err != nil || pol == nil {
				w.WriteHeader(http.StatusNotFound)
				_ = json.NewEncoder(w).Encode(map[string]string{"error": "policy not found"})
				return
			}
			_ = json.NewEncoder(w).Encode(pol)
		})
	}

	// Register NLQ HTTP endpoints
	// TODO: NLQ handler not implemented - commented out for build
	// if s.nlqService != nil {
	//	nlqHandler := nlq.NewHandler(s.nlqService)
	//	nlqHandler.RegisterRoutes(mux)
	// }

	// Register Miner HTTP endpoints
	if s.minerService != nil {
		minerHandler := handlers.NewMinerHandler(s.minerService)
		minerHandler.RegisterRoutes(mux)
	}

	// Register Retention Advisor HTTP endpoints
	if s.retentionAdvisor != nil {
		retentionHandler := handlers.NewRetentionHandler(s.retentionAdvisor)
		retentionHandler.RegisterRoutes(mux)
	}

	// Register Labeling HTTP endpoints
	// TODO: Fix route registration - commented out for build
	// if s.labelingService != nil {
	//	labelingHandler := handlers.NewLabelingHandlers(s.labelingService)
	//	labelingHandler.RegisterRoutes(mux)
	// }

	// Compose middlewares: RequestID, CorrID, identity, logging, then OTEL handler
	handler := telctx.RequestIDMiddleware(
		telctx.CorrIDMiddleware(
			imw.IdentityMiddleware(s.idCache, s.logger)(
				telctx.RequestIDLoggingMiddleware(s.logger)(
					telctx.CorrIDLoggingMiddleware(s.logger)(
						mux,
					),
				),
			),
		),
	)
	// Wrap in OpenTelemetry HTTP tracing
	otel := otelhttp.NewHandler(handler, "dcmaar-server")

	handler = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		otel.ServeHTTP(sw, r)
		dur := time.Since(start)
		status := sw.status
		if s.metrics != nil {
			s.metrics.RecordRequest(r.Context(), r.Method, r.URL.Path, strconv.Itoa(status), dur, int64(sw.bytes))
		}
	})

	s.httpServer = &http.Server{
		Addr:         s.config.HTTPAddr,
		Handler:      handler,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  15 * time.Second,
	}
}

// registerServices registers the gRPC services.
func (s *Server) registerServices() {
	pb.RegisterIngestServiceServer(s.grpcServer, &ingestServer{logger: s.logger, storage: s.storage, redis: s.redisClient, metrics: s.metrics})
	// Policy cache using storage-backed loader
	ttl := s.config.PolicyTTL
	if ttl <= 0 {
		ttl = 60 * time.Second
	}
	polCache := ipolicy.NewCache(ttl, func(ctx context.Context, req *pb.PolicyRequest) (*pb.Policy, error) {
		return s.storage.GetPolicy(ctx, req.GetAgentId(), []string{})
	})
	s.polCache = polCache
	pb.RegisterPolicyServiceServer(s.grpcServer, &policyServer{logger: s.logger, cache: polCache})
	// Policy evaluator: either Rego or JSON-based
	var evaluator ipolicy.Evaluator
	if s.config.PolicyUseRego {
		evaluator = ipolicy.NewRegoEvaluator(s.logger, s.config.RegoPolicyFile, s.config.PolicyDenyByDefault)
	} else {
		jsonVerifier := ipolicy.NewVerifierWithLoader(s.logger, func(ctx context.Context, subject string, resources []string) (*pb.Policy, error) {
			if polCache == nil {
				return nil, nil
			}
			return polCache.Get(ctx, &pb.PolicyRequest{AgentId: subject})
		})
		jsonVerifier.SetDenyByDefault(s.config.PolicyDenyByDefault)
		evaluator = jsonVerifier
	}
	auditRepo := iaudit.NewRepoWithStorage(s.logger, s.storage)
	pb.RegisterActionServiceServer(s.grpcServer, &actionServer{logger: s.logger, verifier: evaluator, audit: auditRepo, metrics: s.metrics})

	// Initialize a dedicated ClickHouse connection for the query service
	if s.queryDB == nil {
		db, err := sql.Open("clickhouse", s.config.ClickHouseDSN)
		if err != nil {
			s.logger.Fatal("Failed to open ClickHouse for QueryService", zap.Error(err))
		}
		if err := db.Ping(); err != nil {
			s.logger.Fatal("Failed to ping ClickHouse for QueryService", zap.Error(err))
		}
		s.queryDB = db
	}

	// Optional Redis cache (enable with REDIS_URL)
	if s.redisClient == nil {
		if url := os.Getenv("REDIS_URL"); url != "" {
			opt, err := redis.ParseURL(url)
			if err != nil {
				s.logger.Warn("Invalid REDIS_URL, caching disabled", zap.Error(err))
			} else {
				rdb := redis.NewClient(opt)
				if err := rdb.Ping(context.Background()).Err(); err != nil {
					s.logger.Warn("Redis not reachable, caching disabled", zap.Error(err))
					_ = rdb.Close()
				} else {
					s.redisClient = rdb
				}
			}
		}
	}

	// Compose and register the QueryService with middleware
	qsrv := query.NewServer(s.queryDB, s.logger, nil) // TODO: Pass proper redis client if needed
	pb.RegisterQueryServiceServer(s.grpcServer, qsrv)

	// Initialize and configure NLQ service
	nlqService, err := nlq.NewService(s.config.ClickHouseDSN)
	if err != nil {
		s.logger.Fatal("Failed to initialize NLQ service", zap.Error(err))
	}
	s.nlqService = nlqService

	// Initialize and configure Miner service
	minerService, err := miner.NewToilMinerService(s.config.ClickHouseDSN)
	if err != nil {
		s.logger.Fatal("Failed to initialize Miner service", zap.Error(err))
	}
	s.minerService = minerService

	// Initialize and configure Retention Advisor service
	retentionRepo, err := retention.NewClickHouseRepository(s.config.ClickHouseDSN)
	if err != nil {
		s.logger.Fatal("Failed to initialize Retention repository", zap.Error(err))
	}
	retentionAdvisor := retention.NewRetentionAdvisor(retentionRepo)
	s.retentionAdvisor = retentionAdvisor

	// Initialize labeling service
	labelingRepo := labeling.NewInMemoryRepository()
	labelingService := labeling.NewLabelingService(labelingRepo)
	s.labelingService = labelingService
}

/* TODO: Re-enable when middleware dependencies are fixed
// interceptorLogger adapts zap logger to interceptor logger.
func interceptorLogger(l *zap.Logger) logging.Logger {
	return logging.LoggerFunc(func(_ context.Context, lvl logging.Level, msg string, fields ...any) {
		f := make([]zap.Field, 0, len(fields)/2)
		for i := 0; i < len(fields); i += 2 {
			f = append(f, zap.Any(fields[i].(string), fields[i+1]))
		}

		switch lvl {
		case logging.LevelDebug:
			l.Debug(msg, f...)
		case logging.LevelInfo:
			l.Info(msg, f...)
		case logging.LevelWarn:
			l.Warn(msg, f...)
		case logging.LevelError:
			l.Error(msg, f...)
		default:
			l.Info(msg, f...)
		}
	})
}

// recoveryInterceptor returns a new unary server interceptor that recovers from panics.
func (s *Server) recoveryInterceptor() grpc.UnaryServerInterceptor {
	return recovery.UnaryServerInterceptor(
		recovery.WithRecoveryHandler(func(p interface{}) (err error) {
			s.logger.Error("recovered from panic",
				zap.Any("panic", p),
				zap.Stack("stack"),
			)
			return status.Errorf(codes.Internal, "internal error")
		}),
	)
}

// loggingInterceptor returns a new unary server interceptor that logs requests.
func (s *Server) loggingInterceptor() grpc.UnaryServerInterceptor {
	return logging.UnaryServerInterceptor(interceptorLogger(s.logger),
		logging.WithLogOnEvents(
			logging.StartCall,
			logging.FinishCall,
			logging.PayloadReceived,
			logging.PayloadSent,
		),
	)
}
*/

// metricsInterceptor returns a new unary server interceptor that records metrics.
func (s *Server) metricsInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		start := time.Now()

		// Get client IP for logging
		p, _ := peer.FromContext(ctx)
		clientIP := "unknown"
		if p != nil {
			clientIP = p.Addr.String()
		}

		// Call the handler
		resp, err := handler(ctx, req)

		// Record metrics
		duration := time.Since(start)
		statusCode := codes.Unknown
		if st, ok := status.FromError(err); ok {
			statusCode = st.Code()
		}

		s.logger.Info("request processed",
			zap.String("method", info.FullMethod),
			zap.String("client", clientIP),
			zap.Duration("duration", duration),
			zap.String("status", statusCode.String()),
		)

		return resp, err
	}
}

// identityInterceptor extracts subject/roles from Authorization metadata (Bearer JWT) and adds identity to context.
func (s *Server) identityInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		// Pull authorization from incoming metadata
		if md, ok := metadata.FromIncomingContext(ctx); ok {
			vals := md.Get("authorization")
			if len(vals) > 0 {
				token := vals[0]
				if strings.HasPrefix(strings.ToLower(token), "bearer ") {
					token = strings.TrimSpace(token[len("bearer "):])
					sub, roles := iid.ParseSubjectRoles(token)
					if sub != "" {
						id := &iid.Identity{Subject: sub, Roles: roles}
						// Attach identity and enrich logger
						lg := telctx.LoggerFromContext(ctx).With(zap.String("subject", sub))
						ctx = imw.ContextWithIdentity(ctx, id)
						ctx = telctx.ContextWithLogger(ctx, lg)
					}
				}
			}
		}
		return handler(ctx, req)
	}
}

// corrIDInterceptor picks x-corr-id from gRPC metadata, attaches it to context and request-scoped logger.
func (s *Server) corrIDInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		if md, ok := metadata.FromIncomingContext(ctx); ok {
			vals := md.Get("x-corr-id")
			if len(vals) > 0 {
				corr := vals[0]
				if corr != "" {
					// Store corr_id in context and enrich logger
					ctx = telctx.ContextWithCorrID(ctx, corr)
					lg := telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("corr_id", corr))
					ctx = telctx.ContextWithLogger(ctx, lg)
				}
			}
		}
		return handler(ctx, req)
	}
}

// parseJWTSubjectRoles decodes a JWT payload without signature verification.
// parseJWTSubjectRoles migrated to internal/identity.ParseSubjectRoles

// --- Ingest Service Implementation ---

type ingestServer struct {
	pb.UnimplementedIngestServiceServer
	logger  *zap.Logger
	storage eventStorage
	redis   *redis.Client
	metrics *stelemetry.Metrics
}

// eventStorage is the minimal storage dependency used by ingest for saving events.
type eventStorage interface {
	SaveEvents(ctx context.Context, envelopes []*pb.EventEnvelope) error
}

// SendMetrics handles incoming metric batches.
func (s *ingestServer) SendMetrics(ctx context.Context, in *pb.MetricBatch) (*pb.IngestResponse, error) {
	if len(in.GetMetrics()) == 0 {
		return nil, status.Error(codes.InvalidArgument, "no metrics provided")
	}

	// TODO: Implement metrics storage - currently commented out due to API mismatches
	telctx.LoggerFromContextOr(ctx, s.logger).Debug("Metrics received but storage not yet implemented", zap.Int("count", len(in.GetMetrics())))

	return &pb.IngestResponse{Success: true}, nil
}

// SendEvents handles incoming event batches.
func (s *ingestServer) SendEvents(ctx context.Context, in *pb.EventBatch) (*pb.IngestResponse, error) {
	if len(in.GetEvents()) == 0 {
		return nil, status.Error(codes.InvalidArgument, "no events provided")
	}

	// TODO: Fix envelope structure - currently commented out due to API mismatches
	// EventEnvelope expects Meta field and Events slice, not Metadata and Event
	telctx.LoggerFromContextOr(ctx, s.logger).Debug("Events received but envelope conversion needs fixing", zap.Int("count", len(in.GetEvents())))

	// TODO: Re-enable once envelope structure is fixed
	// if err := s.storage.SaveEvents(ctx, envelopes); err != nil {
	// 	s.logger.Error("Failed to save events", zap.Error(err))
	// 	return nil, status.Errorf(codes.Internal, "failed to save events: %v", err)
	// }

	return &pb.IngestResponse{Success: true}, nil
}

// SendMetricEnvelopes handles envelope-based metrics with metadata.
func (s *ingestServer) SendMetricEnvelopes(ctx context.Context, in *pb.MetricEnvelopeBatch) (*pb.IngestResponse, error) {
	if len(in.GetEnvelopes()) == 0 {
		return nil, status.Error(codes.InvalidArgument, "no metric envelopes provided")
	}

	logger := telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "ingest.grpc"))
	// TODO: Implement SaveMetrics method in storage
	logger.Debug("Metric envelopes received but SaveMetrics not implemented", zap.Int("count", len(in.GetEnvelopes())))

	return &pb.IngestResponse{Success: true}, nil
}

// SendEventEnvelopes handles envelope-based events with metadata.
func (s *ingestServer) SendEventEnvelopes(ctx context.Context, in *pb.EventEnvelopeBatch) (*pb.IngestResponse, error) {
	if len(in.GetEnvelopes()) == 0 {
		return nil, status.Error(codes.InvalidArgument, "no event envelopes provided")
	}

	logger := telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "ingest.grpc"))

	// Envelope-level idempotency
	envelopes := in.GetEnvelopes()
	if s.redis != nil {
		var kept []*pb.EventEnvelope
		skipped := 0
		for _, env := range envelopes {
			if env == nil || env.Meta == nil || env.Meta.IdempotencyKey == "" {
				kept = append(kept, env)
				continue
			}
			key := "idem:env:" + env.Meta.IdempotencyKey
			ok, err := s.redis.SetNX(ctx, key, 1, 24*time.Hour).Result()
			if err != nil || ok {
				kept = append(kept, env)
			} else {
				skipped++
			}
		}
		if skipped > 0 {
			logger.Info("skipped idempotent envelopes", zap.Int("skipped", skipped))
			if s.metrics != nil {
				s.metrics.IncIdempotentEnvelopes(skipped)
			}
		}
		envelopes = kept
	}

	// Dedupe across requests using Redis if available
	filtered := envelopes
	if s.redis != nil {
		var out []*pb.EventEnvelope
		dedup := 0
		for _, env := range filtered {
			if env == nil {
				continue
			}
			if len(env.Events) == 0 {
				continue
			}
			kept := make([]*pb.EventWithMetadata, 0, len(env.Events))
			for _, e := range env.Events {
				if e == nil || e.Event == nil || e.Event.Id == "" {
					kept = append(kept, e)
					continue
				}
				key := "dedupe:event:" + e.Event.Id
				ok, err := s.redis.SetNX(ctx, key, 1, 24*time.Hour).Result()
				if err != nil || ok {
					kept = append(kept, e)
				} else {
					dedup++
				}
			}
			if len(kept) > 0 {
				// Create a new envelope to avoid copying lock values
				ne := &pb.EventEnvelope{
					Meta:   env.Meta,
					Events: kept,
				}
				if env.ValidationErrors != nil {
					ne.ValidationErrors = env.ValidationErrors
				}
				if env.SchemaVersion != "" {
					ne.SchemaVersion = env.SchemaVersion
				}
				if env.IdempotencyKey != "" {
					ne.IdempotencyKey = env.IdempotencyKey
				}
				if env.SourceOs != "" {
					ne.SourceOs = env.SourceOs
				}
				if env.SourceArch != "" {
					ne.SourceArch = env.SourceArch
				}
				if env.Metadata != nil {
					ne.Metadata = env.Metadata
				}
				out = append(out, ne)
			}
		}
		if dedup > 0 {
			logger.Info("deduped events", zap.Int("deduped", dedup))
			if s.metrics != nil {
				s.metrics.IncDedupeEvents(dedup)
			}
		}
		filtered = out
	}

	if err := s.storage.SaveEvents(ctx, filtered); err != nil {
		logger.Error("Failed to save event envelopes", zap.Error(err))
		return nil, status.Errorf(codes.Internal, "failed to save event envelopes: %v", err)
	}

	return &pb.IngestResponse{Success: true}, nil
}

// --- Policy Service Implementation ---

type policyServer struct {
	pb.UnimplementedPolicyServiceServer
	logger *zap.Logger
	cache  *ipolicy.Cache
}

// GetPolicy retrieves the current policy for the given request.
func (s *policyServer) GetPolicy(ctx context.Context, req *pb.PolicyRequest) (*pb.Policy, error) {
	logger := telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "policy"))
	logger.Info("GetPolicy", zap.String("agent_id", req.GetAgentId()))
	// TODO: Implement policy retrieval from storage
	// For now, return a default policy
	return &pb.Policy{
		Version:       "1.0",
		Data:          []byte(`{"rules": [{"effect": "allow", "resources": ["*"]}]}`),
		SchemaVersion: 1,
	}, nil
}

// --- Action Service Implementation ---

type actionServer struct {
	pb.UnimplementedActionServiceServer
	logger   *zap.Logger
	verifier ipolicy.Evaluator
	audit    *iaudit.Repo
	metrics  *stelemetry.Metrics
}

// SubmitAction processes an action request and returns an action ID.
func (s *actionServer) SubmitAction(ctx context.Context, in *pb.ActionSubmitRequest) (*pb.ActionSubmitResponse, error) {
	// Policy evaluation
	subject := ""
	if id := imw.IdentityFromContext(ctx); id != nil {
		subject = id.Subject
	}
	if s.verifier != nil {
		decision := s.verifier.EvaluateAction(ctx, subject, func() []string {
			if id := imw.IdentityFromContext(ctx); id != nil {
				return id.Roles
			}
			return nil
		}(), in.GetCommand(), in.GetArgs(), nil)
		if !decision.Allow {
			if s.metrics != nil {
				s.metrics.IncPolicyDenied()
			}
			telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "actions")).Warn("Action denied",
				zap.String("subject", subject), zap.String("command", in.GetCommand()), zap.Strings("args", in.GetArgs()), zap.String("reason", decision.Reason))
			return nil, status.Error(codes.PermissionDenied, "action denied by policy")
		}
	}
	// Accept action
	actionID := fmt.Sprintf("action-%d", time.Now().UnixNano())
	telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "actions")).Info("Action submitted",
		zap.String("action_id", actionID),
		zap.String("command", in.GetCommand()),
		zap.Strings("args", in.GetArgs()),
	)
	if s.audit != nil {
		_ = s.audit.Append(ctx, iaudit.Event{Subject: subject, Action: in.GetCommand(), Result: "submitted", Details: map[string]string{"action_id": actionID}})
	}
	return &pb.ActionSubmitResponse{ActionId: actionID}, nil
}

// GetActionStatus retrieves the status of a previously submitted action.
func (s *actionServer) GetActionStatus(ctx context.Context, in *pb.ActionStatusRequest) (*pb.ActionStatusResponse, error) {
	// TODO: Implement action status retrieval from storage
	// For now, return a completed status
	telctx.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "actions")).Debug("GetActionStatus",
		zap.String("action_id", in.GetActionId()),
	)
	return &pb.ActionStatusResponse{
		ActionId: in.GetActionId(),
		State:    pb.ActionState_ACTION_STATE_COMPLETED,
		Message:  "Action completed successfully",
	}, nil
}

func initCache(cfg config.CacheConfig, logger *zap.Logger, metrics *stelemetry.Metrics) (*query.QueryCache, error) {
	// Create a basic cache config
	cacheConfig := &query.CacheConfig{
		Enabled: cfg.Enabled,
		TTL:     cfg.TTL,
	}

	logger.Info("Initializing cache", zap.Bool("enabled", cfg.Enabled))
	return query.NewQueryCache(cacheConfig, logger)
}

func initMetrics(cfg config.MetricsConfig, logger *zap.Logger) (*stelemetry.Metrics, error) {
	if !cfg.Enabled {
		logger.Info("Metrics are disabled")
		return stelemetry.NewMetrics(stelemetry.MetricsConfig{Enabled: false}, logger)
	}

	// Create metrics configuration
	metricsCfg := stelemetry.MetricsConfig{
		Enabled:       true,
		Namespace:     cfg.Namespace,
		Subsystem:     cfg.Subsystem,
		CollectPeriod: 15 * time.Second,
		HistogramBoundaries: []float64{
			0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 2.5, 5, 10, 30, 60,
		},
	}

	// Initialize metrics
	metrics, err := stelemetry.NewMetrics(metricsCfg, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize metrics: %w", err)
	}

	logger.Info("Metrics initialized",
		zap.String("namespace", cfg.Namespace),
		zap.String("subsystem", cfg.Subsystem),
	)

	return metrics, nil
}

func initTracer(cfg config.TracingConfig, logger *zap.Logger) (*sdktrace.TracerProvider, error) {
	if !cfg.Enabled {
		// Return a no-op tracer provider if tracing is disabled
		tp := sdktrace.NewTracerProvider(
			sdktrace.WithSampler(sdktrace.NeverSample()),
		)
		otel.SetTracerProvider(tp)
		return tp, nil
	}

	// Create resource with service name and attributes
	resAttrs := []attribute.KeyValue{
		semconv.ServiceNameKey.String(cfg.ServiceName),
		semconv.ServiceVersionKey.String("1.0.0"),
	}

	// Add resource attributes from config
	for k, v := range cfg.ResourceAttributes {
		resAttrs = append(resAttrs, attribute.String(k, v))
	}

	res, err := resource.New(
		context.Background(),
		resource.WithAttributes(resAttrs...),
		resource.WithProcessRuntimeDescription(),
		resource.WithTelemetrySDK(),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create resource: %w", err)
	}

	// Set up OTLP exporter
	opts := []otlptracegrpc.Option{
		otlptracegrpc.WithEndpoint(cfg.Endpoint),
	}

	if cfg.Insecure {
		opts = append(opts, otlptracegrpc.WithInsecure())
	}

	exp, err := otlptrace.New(
		context.Background(),
		otlptracegrpc.NewClient(opts...),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create OTLP exporter: %w", err)
	}

	// Create tracer provider with batch processor
	tp := sdktrace.NewTracerProvider(
		sdktrace.WithResource(res),
		sdktrace.WithSampler(sdktrace.ParentBased(
			sdktrace.TraceIDRatioBased(cfg.SamplingRate),
			sdktrace.WithRemoteParentSampled(nil),
		)),
		sdktrace.WithBatcher(
			exp,
			sdktrace.WithMaxExportBatchSize(cfg.Batch.MaxExportBatchSize),
			sdktrace.WithMaxQueueSize(cfg.Batch.MaxQueueSize),
			sdktrace.WithBatchTimeout(cfg.Batch.Timeout),
		),
	)

	// Set global tracer provider and propagator
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	logger.Info("Tracing initialized",
		zap.String("service", cfg.ServiceName),
		zap.String("endpoint", cfg.Endpoint),
		zap.Bool("insecure", cfg.Insecure),
		zap.Float64("sampling_rate", cfg.SamplingRate),
	)

	return tp, nil
}

func main() {
	// Load configuration
	cfg, err := config.LoadConfig(os.Getenv("CONFIG_FILE"))
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// Initialize logger
	logger, err := newLogger(cfg.Logging)
	if err != nil {
		log.Fatalf("Failed to initialize logger: %v", err)
	}
	defer logger.Sync()

	// Initialize telemetry
	tel, err := stelemetry.New(stelemetry.Config{
		Tracing: stelemetry.TracingConfig{
			Enabled:       cfg.Tracing.Enabled,
			ServiceName:   cfg.Tracing.ServiceName,
			ServiceID:     "1",          // TODO: Make this configurable
			Environment:   "production", // TODO: Make this configurable
			Version:       "1.0.0",      // TODO: Get from build info
			SamplingRatio: cfg.Tracing.SamplingRate,
			OTLPEndpoint:  cfg.Tracing.Endpoint,
		},
		Metrics: stelemetry.MetricsConfig{
			Enabled:   cfg.Metrics.Enabled,
			Namespace: cfg.Metrics.Namespace,
			Subsystem: cfg.Metrics.Subsystem,
		},
	}, logger)

	if err != nil {
		logger.Fatal("Failed to initialize telemetry", zap.Error(err))
	}

	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := tel.Shutdown(ctx); err != nil {
			logger.Error("Failed to shutdown telemetry", zap.Error(err))
		}
	}()

	// Initialize cache
	cache, err := initCache(cfg.Cache, logger, tel.Metrics())
	if err != nil {
		logger.Fatal("Failed to initialize cache", zap.Error(err))
	}

	// Create server
	serverConfig := &Config{
		GRPCAddr:    fmt.Sprintf("%s:%d", cfg.Server.Host, cfg.Server.Port),
		HTTPAddr:    fmt.Sprintf("%s:%d", cfg.Server.Host, cfg.Metrics.Port),
		TLSCertFile: "", // Set from config if needed
		TLSKeyFile:  "", // Set from config if needed
		ClickHouseDSN: fmt.Sprintf("tcp://%s:%d?username=%s&password=%s&database=%s",
			cfg.Database.Host,
			cfg.Database.Port,
			cfg.Database.User,
			cfg.Database.Password,
			cfg.Database.Name,
		),
		MaxReceiveMsgSize:   10 * 1024 * 1024, // 10MB
		Cache:               cfg.Cache,
		IdentityTTL:         cfg.Identity.TTL,
		PolicyTTL:           cfg.Policy.TTL,
		PolicyDenyByDefault: cfg.Policy.DenyByDefault,
		PolicyUseRego:       cfg.Policy.UseRego,
		RegoPolicyFile:      cfg.Policy.RegoPolicyFile,
		DevHelpers:          (cfg.Features.Debug || cfg.Features.DevHelpers),
	}

	srv, err := NewServer(serverConfig, logger, cache)
	if err != nil {
		logger.Fatal("Failed to create server", zap.Error(err))
	}

	// Set global tracer provider and propagator
	// TODO: Expose TracerProvider method in telemetry package
	// otel.SetTracerProvider(tel.Tracer().TracerProvider())
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	// Start server
	if err := srv.Start(); err != nil {
		logger.Fatal("Failed to start server", zap.Error(err))
	}

	// Wait for interrupt signal to gracefully shut down the server
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	logger.Info("Shutting down server...")

	// Gracefully stop the server
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := srv.Stop(ctx); err != nil {
		logger.Error("Error stopping server", zap.Error(err))
	}

	logger.Info("Server stopped")
}

func newLogger(cfg config.LoggingConfig) (*zap.Logger, error) {
	// Use production config as default
	zapCfg := zap.NewProductionConfig()

	// Set log level
	var level zapcore.Level
	if err := level.UnmarshalText([]byte(cfg.Level)); err != nil {
		return nil, fmt.Errorf("invalid log level: %w", err)
	}
	zapCfg.Level = zap.NewAtomicLevelAt(level)

	// Set output format
	if cfg.Format == "console" {
		zapCfg.Encoding = "console"
	} else {
		zapCfg.Encoding = "json"
	}

	return zapCfg.Build()
}
