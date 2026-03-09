package query

import (
	"context"
	"time"

	"golang.org/x/time/rate"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// rateLimitedService enforces a simple token-bucket rate limit per-process.
type rateLimitedService struct {
	next    QueryCore
	limiter *rate.Limiter
}

func NewRateLimitedService(svc QueryCore, requestsPerSecond float64, burst int) QueryCore {
	return &rateLimitedService{
		next:    svc,
		limiter: rate.NewLimiter(rate.Limit(requestsPerSecond), burst),
	}
}

func (s *rateLimitedService) QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	if !s.limiter.Allow() {
		return nil, status.Error(codes.ResourceExhausted, "rate limit exceeded")
	}
	return s.next.QueryEvents(ctx, req)
}

// validatedService performs basic request validation and guards.
type validatedService struct{ next QueryCore }

func NewValidatedService(svc QueryCore) QueryCore { return &validatedService{next: svc} }

func (s *validatedService) QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	if req == nil {
		return nil, status.Error(codes.InvalidArgument, "request cannot be nil")
	}

	// Validate page size upper bound
	if req.PageSize < 0 {
		return nil, status.Error(codes.InvalidArgument, "page size cannot be negative")
	}
	if req.PageSize > 1000 {
		return nil, status.Error(codes.InvalidArgument, "page size cannot exceed 1000")
	}

	// Validate time range ordering and max span
	if tr := req.TimeRange; tr != nil {
		var start, end time.Time
		if tr.StartTime != nil {
			start = tr.StartTime.AsTime()
		}
		if tr.EndTime != nil {
			end = tr.EndTime.AsTime()
		} else {
			end = time.Now()
		}
		if !start.IsZero() && !end.IsZero() && start.After(end) {
			return nil, status.Error(codes.InvalidArgument, "start time must be before end time")
		}
		// Cap to 30 days if start provided
		if !start.IsZero() {
			if end.Sub(start) > 30*24*time.Hour {
				return nil, status.Error(codes.InvalidArgument, "time range cannot exceed 30 days")
			}
		}
	}

	return s.next.QueryEvents(ctx, req)
}
