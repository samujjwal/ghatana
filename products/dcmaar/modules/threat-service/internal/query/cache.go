package query

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/jellydator/ttlcache/v2"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
)

type cachedService struct {
	next   QueryCore
	cache  *QueryCache
	logger *zap.Logger
}

func NewCachedService(svc QueryCore, cache *QueryCache, logger *zap.Logger) QueryCore {
	return &cachedService{
		next:   svc,
		cache:  cache,
		logger: logger,
	}
}

func (s *cachedService) QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "query.cache"))

	// Avoid caching very large pages
	if req.GetPageSize() > 100 || s.cache == nil {
		return s.next.QueryEvents(ctx, req)
	}

	resp, err := s.cache.Get(ctx, req)
	if err != nil {
		logger.Error("cache get failed", zap.Error(err))
		return s.next.QueryEvents(ctx, req)
	}

	if resp != nil {
		return resp, nil
	}

	// Miss -> delegate
	resp, err = s.next.QueryEvents(ctx, req)
	if err != nil {
		return nil, err
	}

	// Store
	if err := s.cache.Set(ctx, req, resp); err != nil {
		logger.Error("cache set failed", zap.Error(err))
	}

	return resp, nil
}

func (q *QueryCache) loggerFromContext(ctx context.Context) *zap.Logger {
	return telemetry.LoggerFromContextOr(ctx, q.logger).With(zap.String("component", "query.cache"))
}

type CacheConfig struct {
	Enabled     bool
	Backend     string
	TTL         time.Duration
	RedisConfig *RedisConfig
}

type RedisConfig struct {
	Addr     string
	Password string
	DB       int
}

type QueryCache struct {
	backend CacheBackend
	logger  *zap.Logger
	config  *CacheConfig
	mu      sync.RWMutex
	healthy bool
}

func NewQueryCache(config *CacheConfig, logger *zap.Logger) (*QueryCache, error) {
	if !config.Enabled {
		return &QueryCache{backend: nil, logger: logger, config: config}, nil
	}

	var backend CacheBackend
	var err error

	switch strings.ToLower(config.Backend) {
	case "redis":
		if config.RedisConfig == nil {
			return nil, errors.New("Redis config is required for Redis backend")
		}
		backend, err = NewRedisCache(
			config.RedisConfig.Addr,
			config.RedisConfig.Password,
			config.RedisConfig.DB,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to initialize Redis cache: %w", err)
		}

	case "memory":
		fallthrough
	default:
		backend = NewInMemoryCache(config.TTL, logger)
	}

	cache := &QueryCache{
		backend: backend,
		logger:  logger.Named("query.cache"),
		config:  config,
		healthy: true,
	}

	// Start health check goroutine
	if config.Enabled && config.Backend == "redis" {
		go cache.healthCheck()
	}

	return cache, nil
}

func (q *QueryCache) Get(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	if !q.config.Enabled || q.backend == nil {
		return nil, nil
	}

	key, err := q.cacheKey(req)
	if err != nil {
		q.loggerFromContext(ctx).Error("failed to generate cache key", zap.Error(err))
		return nil, nil
	}

	cached, err := q.backend.Get(ctx, key)
	if err != nil {
		q.loggerFromContext(ctx).Error("cache get failed", zap.Error(err))
		return nil, nil
	}

	if len(cached) == 0 {
		return nil, nil
	}

	var resp pb.QueryEventsResponse
	if err := json.Unmarshal(cached, &resp); err != nil {
		q.loggerFromContext(ctx).Error("failed to unmarshal cached response", zap.Error(err))
		return nil, nil
	}

	// Update cache metrics
	cacheHits.Inc()

	return &resp, nil
}

func (q *QueryCache) Set(ctx context.Context, req *pb.QueryEventsRequest, resp *pb.QueryEventsResponse) error {
	if !q.config.Enabled || q.backend == nil {
		return nil
	}

	// Skip caching for large result sets
	if len(resp.Events) > 1000 {
		return nil
	}

	key, err := q.cacheKey(req)
	if err != nil {
		return fmt.Errorf("failed to generate cache key: %w", err)
	}

	data, err := json.Marshal(resp)
	if err != nil {
		return fmt.Errorf("failed to marshal response: %w", err)
	}

	ttl := q.config.TTL

	if err := q.backend.Set(ctx, key, data, ttl); err != nil {
		return fmt.Errorf("failed to set cache: %w", err)
	}

	// Update cache metrics
	cacheSets.Inc()

	return nil
}

func (q *QueryCache) Invalidate(ctx context.Context, req *pb.QueryEventsRequest) error {
	if !q.config.Enabled || q.backend == nil {
		return nil
	}

	key, err := q.cacheKey(req)
	if err != nil {
		return fmt.Errorf("failed to generate cache key: %w", err)
	}

	if err := q.backend.Delete(ctx, key); err != nil {
		return fmt.Errorf("failed to invalidate cache: %w", err)
	}

	// Update cache metrics
	cacheInvalidations.Inc()

	return nil
}

func (q *QueryCache) InvalidatePrefix(ctx context.Context, prefix string) error {
	// Only Redis supports pattern-based invalidation
	if q.backend == nil || q.config.Backend != "redis" {
		return nil
	}

	rdb, ok := q.backend.(*RedisCache)
	if !ok {
		return nil
	}

	iter := rdb.client.Scan(ctx, 0, prefix+"*", 0).Iterator()
	for iter.Next(ctx) {
		if err := q.backend.Delete(ctx, iter.Val()); err != nil {
			q.loggerFromContext(ctx).Error("failed to delete cache key",
				zap.String("key", iter.Val()),
				zap.Error(err))
		}
	}

	if err := iter.Err(); err != nil {
		return fmt.Errorf("failed to scan cache keys: %w", err)
	}

	return nil
}

func (q *QueryCache) HealthCheck() bool {
	q.mu.RLock()
	defer q.mu.RUnlock()
	return q.healthy
}

func (q *QueryCache) healthCheck() {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for range ticker.C {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		err := q.backend.Ping(ctx)
		cancel()

		q.mu.Lock()
		if err != nil && q.healthy {
			q.healthy = false
			q.logger.Error("cache backend is unhealthy", zap.Error(err))
		} else if err == nil && !q.healthy {
			q.healthy = true
			q.logger.Info("cache backend is healthy")
		}
		q.mu.Unlock()
	}
}

func (q *QueryCache) cacheKey(req *pb.QueryEventsRequest) (string, error) {
	// Create a stable representation of the request
	reqCopy := proto.Clone(req).(*pb.QueryEventsRequest)
	// Clear fields that shouldn't affect caching
	reqCopy.PageToken = ""

	data, err := protojson.Marshal(reqCopy)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request: %w", err)
	}

	// Create a hash of the request
	h := sha256.New()
	h.Write(data)
	hash := hex.EncodeToString(h.Sum(nil))

	return fmt.Sprintf("query:%s", hash), nil
}

type CacheBackend interface {
	Get(ctx context.Context, key string) ([]byte, error)
	Set(ctx context.Context, key string, value []byte, ttl time.Duration) error
	Delete(ctx context.Context, key string) error
	Ping(ctx context.Context) error
	Close() error
}

type RedisCache struct {
	client *redis.Client
}

func NewRedisCache(addr, password string, db int) (*RedisCache, error) {
	client := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("failed to connect to Redis: %w", err)
	}

	return &RedisCache{client: client}, nil
}

func (r *RedisCache) Get(ctx context.Context, key string) ([]byte, error) {
	val, err := r.client.Get(ctx, key).Bytes()
	if err == redis.Nil {
		return nil, nil
	}
	return val, err
}

func (r *RedisCache) Set(ctx context.Context, key string, value []byte, ttl time.Duration) error {
	return r.client.Set(ctx, key, value, ttl).Err()
}

func (r *RedisCache) Delete(ctx context.Context, key string) error {
	return r.client.Del(ctx, key).Err()
}

func (r *RedisCache) Ping(ctx context.Context) error {
	return r.client.Ping(ctx).Err()
}

func (r *RedisCache) Close() error {
	return r.client.Close()
}

type InMemoryCache struct {
	cache  *ttlcache.Cache
	logger *zap.Logger
}

func NewInMemoryCache(defaultTTL time.Duration, logger *zap.Logger) *InMemoryCache {
	cache := ttlcache.NewCache()
	cache.SetTTL(defaultTTL)
	cache.SkipTTLExtensionOnHit(false)

	return &InMemoryCache{
		cache:  cache,
		logger: logger.Named("cache.in-memory"),
	}
}

func (i *InMemoryCache) Get(_ context.Context, key string) ([]byte, error) {
	val, err := i.cache.Get(key)
	if err != nil {
		if errors.Is(err, ttlcache.ErrNotFound) {
			return nil, nil
		}
		return nil, err
	}

	b, ok := val.([]byte)
	if !ok {
		i.logger.Error("invalid cache value type", zap.Any("value", val))
		return nil, fmt.Errorf("invalid cache value type: %T", val)
	}

	return b, nil
}

func (i *InMemoryCache) Set(_ context.Context, key string, value []byte, ttl time.Duration) error {
	if ttl <= 0 {
		ttl = time.Hour // Default TTL if not specified
	}
	return i.cache.SetWithTTL(key, value, ttl)
}

func (i *InMemoryCache) Delete(_ context.Context, key string) error {
	return i.cache.Remove(key)
}

func (i *InMemoryCache) Ping(_ context.Context) error {
	return nil // Always available
}

func (i *InMemoryCache) Close() error {
	i.cache.Close()
	return nil
}

var (
	cacheHits = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "query_cache_hits_total",
		Help: "Total number of cache hits",
	})

	cacheMisses = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "query_cache_misses_total",
		Help: "Total number of cache misses",
	})

	cacheSets = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "query_cache_sets_total",
		Help: "Total number of cache sets",
	})

	cacheInvalidations = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "query_cache_invalidations_total",
		Help: "Total number of cache invalidations",
	})

	cacheErrors = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "query_cache_errors_total",
			Help: "Total number of cache errors by type",
		},
		[]string{"type"},
	)

	cacheLatency = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "query_cache_latency_seconds",
			Help:    "Latency of cache operations",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"operation"},
	)
)

func init() {
	prometheus.MustRegister(
		cacheHits,
		cacheMisses,
		cacheSets,
		cacheInvalidations,
		cacheErrors,
		cacheLatency,
	)
}
