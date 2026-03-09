package query

import (
	"context"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	otelcodes "go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/trace"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

const tracerName = "github.com/samujjwal/dcmaar/apps/server/internal/query"

type tracedService struct {
	next   QueryCore
	tracer trace.Tracer
}

func NewTracedService(svc QueryCore) QueryCore {
	return &tracedService{
		next:   svc,
		tracer: otel.Tracer(tracerName),
	}
}

func (s *tracedService) QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	ctx, span := s.tracer.Start(ctx, "QueryEvents",
		trace.WithAttributes(
			attribute.Int("page_size", int(req.GetPageSize())),
			attribute.String("page_token", req.GetPageToken()),
		))
	defer span.End()

	resp, err := s.next.QueryEvents(ctx, req)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(otelcodes.Error, err.Error())
	}
	return resp, err
}
