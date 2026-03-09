package ingest

import (
	"context"
	"sync"
	"time"

	"golang.org/x/time/rate"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// RateLimiter implements a token bucket rate limiter.
type RateLimiter struct {
	limiter   *rate.Limiter
	createdAt time.Time
}

// NewRateLimiter creates a new rate limiter that allows r requests per second with a burst of b.
func NewRateLimiter(r rate.Limit, b int) *RateLimiter {
	return &RateLimiter{
		limiter:   rate.NewLimiter(r, b),
		createdAt: time.Now(),
	}
}

// Limit returns true if the request should be rate limited.
func (r *RateLimiter) Limit() bool {
	return !r.limiter.Allow()
}

// RateLimiterMap manages rate limiters per client.
type RateLimiterMap struct {
	mu       sync.Mutex
	rate     rate.Limit
	burst    int
	limiters map[string]*RateLimiter
}

// NewRateLimiterMap creates a new RateLimiterMap with the given rate and burst.
func NewRateLimiterMap(rate rate.Limit, burst int) *RateLimiterMap {
	return &RateLimiterMap{
		rate:     rate,
		burst:    burst,
		limiters: make(map[string]*RateLimiter),
	}
}

// Get returns a rate limiter for the given key, creating one if it doesn't exist.
func (r *RateLimiterMap) Get(key string) *RateLimiter {
	r.mu.Lock()
	defer r.mu.Unlock()

	limiter, exists := r.limiters[key]
	if !exists {
		limiter = NewRateLimiter(r.rate, r.burst)
		r.limiters[key] = limiter
	}

	return limiter
}

// Cleanup removes old rate limiters that haven't been used in the given duration.
func (r *RateLimiterMap) Cleanup(olderThan time.Duration) {
	r.mu.Lock()
	defer r.mu.Unlock()

	for key, limiter := range r.limiters {
		// If the limiter was created more than olderThan ago, remove it
		if time.Since(limiter.createdAt) > olderThan {
			delete(r.limiters, key)
		}
	}
}

// UnaryRateLimitInterceptor returns a new unary server interceptor that performs rate limiting.
func UnaryRateLimitInterceptor(limiters *RateLimiterMap) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		// Skip rate limiting for health checks
		if info.FullMethod == "/dcmaar.IngestService/Health" {
			return handler(ctx, req)
		}

		// Get client IP
		p, ok := peer.FromContext(ctx)
		if !ok {
			return nil, status.Error(codes.Internal, "could not get peer info")
		}

		// Use IP:port as the rate limit key
		key := p.Addr.String()

		// Get or create rate limiter for this client
		limiter := limiters.Get(key)

		// Check if request is allowed
		if limiter.Limit() {
			return nil, status.Error(codes.ResourceExhausted, "rate limit exceeded")
		}

		// Call the handler
		return handler(ctx, req)
	}
}

// RequestSizeUnaryServerInterceptor returns a new unary server interceptor that enforces a maximum request size.
func RequestSizeUnaryServerInterceptor(maxSize int) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		// Skip size check for health checks
		if info.FullMethod == "/dcmaar.IngestService/Health" {
			return handler(ctx, req)
		}

		// Calculate the size of the request
		size := proto.Size(req.(proto.Message))
		if size > maxSize {
			return nil, status.Errorf(
				codes.InvalidArgument,
				"request is too large: %d > %d",
				size,
				maxSize,
			)
		}

		return handler(ctx, req)
	}
}

// ValidationUnaryServerInterceptor validates incoming requests.
func ValidationUnaryServerInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req interface{}, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
		// Skip validation for health checks
		if info.FullMethod == "/dcmaar.IngestService/Health" {
			return handler(ctx, req)
		}

		// Validate the request
		if v, ok := req.(interface{ Validate() error }); ok {
			if err := v.Validate(); err != nil {
				return nil, status.Error(codes.InvalidArgument, err.Error())
			}
		}

		return handler(ctx, req)
	}
}
