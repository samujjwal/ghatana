//go:build ignore

// Architecture clarification (Milestone 5):
// This server is not intended to accept direct ingest from the browser extension or desktop.
// All ingest flows via the agent/daemon using the existing IngestService envelope RPCs.
// This file is excluded from builds and retained only for reference.

package handlers

import (
	"context"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// ExtensionIngestServer implements pb.ExtensionIngestServiceServer
// Note: gRPC server options (deadlines<=3s, max msg size<=4MB) should be enforced
// at server bootstrap per WSRF-API-002.
// Note: This implementation is disabled via build tag and should not be used.
type ExtensionIngestServer struct {
	pb.UnimplementedExtensionIngestServiceServer
	AuthValidator AuthValidator
	EventSaver    EventSaver
}

// AuthValidator validates extension auth context
type AuthValidator interface {
	ValidateExtensionAuth(ctx context.Context, auth *pb.ExtensionAuth) (extensionID string, err error)
}

// EventSaver persists envelopes to storage
type EventSaver interface {
	SaveExtensionEnvelopes(ctx context.Context, extensionID string, batch *pb.EventEnvelopeBatch) (itemsProcessed int, itemsRejected int, err error)
}

func (s *ExtensionIngestServer) SendExtensionEvents(ctx context.Context, req *pb.ExtensionIngestRequest) (*pb.IngestResponse, error) {
	return &pb.IngestResponse{Success: false, Message: "disabled: use agent/daemon IngestService"}, nil
}
