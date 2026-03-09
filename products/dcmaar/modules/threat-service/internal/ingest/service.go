package ingest

import (
	"context"
	"fmt"
	"time"

	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
	"github.com/samujjwal/dcmaar/apps/server/internal/storage"
)

// Service implements the IngestService gRPC service.
type Service struct {
	pb.UnimplementedIngestServiceServer
	logger  *zap.Logger
	storage *storage.Storage
}

// NewService creates a new ingest service instance.
func NewService(logger *zap.Logger, storage *storage.Storage) *Service {
	return &Service{
		logger:  logger.Named("ingest"),
		storage: storage,
	}
}

// processEventEnvelope processes a single event envelope, validating and normalizing it.
func (s *Service) processEventEnvelope(env *pb.EventEnvelope) (*pb.EventEnvelope, error) {
	if env == nil || env.Meta == nil {
		return nil, fmt.Errorf("envelope or metadata is nil")
	}

	// Set default timestamp if not provided
	if env.Meta.Timestamp == 0 {
		env.Meta.Timestamp = time.Now().UnixMilli()
	}

	// Set default source if not provided
	if env.Meta.Source == "" {
		env.Meta.Source = "agent"
	}

	// Process each event in the envelope
	for _, event := range env.Events {
		if event == nil || event.Event == nil {
			return nil, fmt.Errorf("event is nil")
		}

		// Set default event ID if not provided
		if event.Event.Id == "" {
			event.Event.Id = generateEventID()
		}

		// Set default timestamp if not provided
		if event.Event.Timestamp == nil {
			event.Event.Timestamp = timestamppb.New(time.UnixMilli(env.Meta.Timestamp))
		}

		// Device ID and Session ID are handled at envelope level, not event level

		// Process browser events
		if event.Browser != nil {
			// Ensure required browser event fields
			if event.Browser.Url != "" && event.Browser.Domain == "" {
				// Extract domain from URL if not provided
				event.Browser.Domain = extractDomain(event.Browser.Url)
			}
		}
	}

	return env, nil
}

// IngestEvents handles incoming event batches.
func (s *Service) IngestEvents(ctx context.Context, batch *pb.EventEnvelopeBatch) (*pb.EventAck, error) {
	start := time.Now()
	resp := &pb.EventAck{
		BatchId:    batch.GetBatchId(),
		ReceivedAt: time.Now().UnixNano(),
	}

	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "ingest"))

	if len(batch.GetEnvelopes()) == 0 {
		resp.Message = "no envelopes provided"
		return resp, nil
	}

	// Process each envelope in the batch
	var validEnvelopes []*pb.EventEnvelope
	var browserEvents []*pb.EventWithMetadata

	for _, env := range batch.GetEnvelopes() {
		processedEnv, err := s.processEventEnvelope(env)
		if err != nil {
			logger.Warn("failed to process event envelope",
				zap.Error(err),
				zap.String("batch_id", batch.GetBatchId()))
			continue
		}

		// Extract browser events for optimized storage
		for _, event := range processedEnv.Events {
			if event.Browser != nil {
				browserEvents = append(browserEvents, event)
			}
		}

		validEnvelopes = append(validEnvelopes, processedEnv)
	}

	// Save all valid events
	if len(validEnvelopes) > 0 {
		if err := s.storage.SaveEvents(ctx, validEnvelopes); err != nil {
			logger.Error("failed to save events",
				zap.Error(err),
				zap.String("batch_id", batch.GetBatchId()))
			return nil, status.Error(codes.Internal, "failed to process events")
		}
	}

	// Save browser events to optimized storage
	if len(browserEvents) > 0 {
		if err := s.storage.SaveBrowserEvents(ctx, browserEvents, validEnvelopes); err != nil {
			logger.Warn("failed to save browser events",
				zap.Error(err),
				zap.String("batch_id", batch.GetBatchId()))
			// Don't fail the entire request if browser events fail
		}
	}

	// Update response
	resp.Success = true
	resp.Message = fmt.Sprintf("processed %d envelopes with %d browser events",
		len(validEnvelopes), len(browserEvents))

	logger.Debug("processed event batch",
		zap.String("batch_id", batch.GetBatchId()),
		zap.Int("envelopes", len(validEnvelopes)),
		zap.Int("browser_events", len(browserEvents)),
		zap.Duration("duration", time.Since(start)))

	return resp, nil
}

// StreamEvents implements the streaming events endpoint.
func (s *Service) StreamEvents(stream pb.IngestService_StreamEventsServer) error {
	logger := telemetry.LoggerFromContextOr(stream.Context(), s.logger).With(zap.String("component", "ingest"))

	for {
		env, err := stream.Recv()
		if err != nil {
			if err.Error() == "EOF" {
				// For bidirectional streaming, just return normally
				return nil
			}
			logger.Error("error receiving stream", zap.Error(err))
			return err
		}

		// Process the envelope
		processedEnv, err := s.processEventEnvelope(env)
		if err != nil {
			logger.Warn("failed to process streamed event envelope", zap.Error(err))
			continue
		}

		// Save the event
		if err := s.storage.SaveEvents(stream.Context(), []*pb.EventEnvelope{processedEnv}); err != nil {
			logger.Error("failed to save streamed event", zap.Error(err))
			return status.Error(codes.Internal, "failed to process event")
		}

		// Process browser events if present
		var browserEvents []*pb.EventWithMetadata
		for _, event := range processedEnv.Events {
			if event.Browser != nil {
				browserEvents = append(browserEvents, event)
			}
		}

		if len(browserEvents) > 0 {
			if err := s.storage.SaveBrowserEvents(stream.Context(), browserEvents, []*pb.EventEnvelope{processedEnv}); err != nil {
				logger.Warn("failed to save streamed browser events", zap.Error(err))
			}
		}
	}
}

// Health implements the health check endpoint.
func (s *Service) Health(ctx context.Context, req *pb.HealthCheckRequest) (*pb.HealthCheckResponse, error) {
	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "ingest"))

	// Check storage health
	if err := s.storage.HealthCheck(ctx); err != nil {
		logger.Warn("storage health check failed", zap.Error(err))
		return &pb.HealthCheckResponse{
			Status: pb.HealthCheckResponse_NOT_SERVING,
		}, nil
	}

	return &pb.HealthCheckResponse{
		Status: pb.HealthCheckResponse_SERVING,
	}, nil
}

// generateEventID generates a unique event ID.
func generateEventID() string {
	// TODO: Implement a proper ID generation strategy
	// This is a simple implementation for demonstration
	return fmt.Sprintf("evt_%s_%d", time.Now().Format("20060102150405"), time.Now().UnixNano()%10000)
}

// extractDomain extracts the domain from a URL.
func extractDomain(url string) string {
	// TODO: Implement proper domain extraction
	// This is a simplified implementation
	if url == "" {
		return ""
	}
	// Remove protocol
	domain := url
	if i := len(domain) - 1; i >= 0 && domain[0:i] == "//" {
		domain = domain[i+1:]
	}
	// Remove path and query
	if i := len(domain) - 1; i >= 0 && domain[i] == '/' {
		domain = domain[:i]
	}
	// Remove port
	if i := len(domain) - 1; i >= 0 && domain[i] == ':' {
		domain = domain[:i]
	}
	return domain
}
