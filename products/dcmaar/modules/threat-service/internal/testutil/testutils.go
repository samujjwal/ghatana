package testutil

import (
	"context"
	"net"
	"testing"
	"time"

	"go.uber.org/zap"
	"go.uber.org/zap/zaptest"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// TestContext returns a context with a test logger
func TestContext(t *testing.T) (context.Context, *zap.Logger) {
	logger := zaptest.NewLogger(t, zaptest.Level(zap.DebugLevel))
	return context.Background(), logger
}

// StartTestServer spins up an in-memory gRPC ingest service suitable for unit tests.
// It returns a client and a cleanup function that must be deferred.
func StartTestServer(t *testing.T) (pb.IngestServiceClient, func()) {
	listener := bufconn.Listen(1024 * 1024)
	grpcServer := grpc.NewServer()
	mock := &mockIngestServer{}
	pb.RegisterIngestServiceServer(grpcServer, mock)

	go func() {
		if err := grpcServer.Serve(listener); err != nil {
			t.Logf("test gRPC server stopped: %v", err)
		}
	}()

	dialer := func(context.Context, string) (net.Conn, error) {
		return listener.Dial()
	}

	conn, err := grpc.DialContext(
		context.Background(),
		"bufnet",
		grpc.WithContextDialer(dialer),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatalf("failed to dial bufconn: %v", err)
	}

	cleanup := func() {
		_ = conn.Close()
		grpcServer.Stop()
		listener.Close()
	}

	return pb.NewIngestServiceClient(conn), cleanup
}

type mockIngestServer struct {
	pb.UnimplementedIngestServiceServer
}

func (s *mockIngestServer) SendEventEnvelopes(ctx context.Context, batch *pb.EventEnvelopeBatch) (*pb.IngestResponse, error) {
	if len(batch.GetEnvelopes()) == 0 {
		return &pb.IngestResponse{Success: false, Message: "no envelopes"}, nil
	}
	return &pb.IngestResponse{Success: true}, nil
}

func (s *mockIngestServer) HealthCheck(ctx context.Context, req *pb.HealthCheckRequest) (*pb.HealthCheckResponse, error) {
	return &pb.HealthCheckResponse{Status: pb.HealthCheckResponse_SERVING}, nil
}

// MustParseDuration parses a duration string or fails the test
func MustParseDuration(t *testing.T, s string) time.Duration {
	d, err := time.ParseDuration(s)
	if err != nil {
		t.Fatalf("failed to parse duration %q: %v", s, err)
	}
	return d
}
