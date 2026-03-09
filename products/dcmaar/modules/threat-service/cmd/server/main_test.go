package main

import (
	"context"
	"encoding/json"
	"net/http"
	"os"
	"testing"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/protobuf/types/known/timestamppb"
	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// TestSendEventEnvelopesConditional sends a minimal EventEnvelopeBatch to a locally running server
// and asserts that the HTTP debug endpoint records at least one event.
// This test runs only when RUN_SERVER_TEST=1 is set because it depends on an external server process
// (e.g., started via `make up` or `make smoke`).
func TestSendEventEnvelopesConditional(t *testing.T) {
	if os.Getenv("RUN_SERVER_TEST") != "1" {
		t.Skip("set RUN_SERVER_TEST=1 to run this integration test")
	}

	// Dial gRPC server (insecure dev only)
	conn, err := grpc.Dial("127.0.0.1:50051", grpc.WithInsecure())
	if err != nil {
		t.Fatalf("dial failed: %v", err)
	}
	defer conn.Close()

	client := pb.NewIngestServiceClient(conn)

	// Build a minimal envelope batch
	ev := &pb.EventWithMetadata{
		Event: &pb.Event{
			Id:        "evt-1",
			Type:      pb.EventType_EVENT_TYPE_USER,
			Source:    "test",
			Timestamp: timestamppb.New(time.Now()),
		},
		ActivityType: pb.ActivityType_ACTIVITY_USER_INTERACTION,
	}
	env := &pb.EventEnvelope{
		Meta: &pb.EnvelopeMeta{
			TenantId:      "t1",
			DeviceId:      "dev1",
			SessionId:     "s1",
			Timestamp:     time.Now().UnixMilli(),
			SchemaVersion: "1.0.0",
		},
		Events: []*pb.EventWithMetadata{ev},
	}
	batch := &pb.EventEnvelopeBatch{Envelopes: []*pb.EventEnvelope{env}}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if _, err := client.SendEventEnvelopes(ctx, batch); err != nil {
		t.Fatalf("SendEventEnvelopes error: %v", err)
	}

	// Small delay for server to record
	time.Sleep(200 * time.Millisecond)

	// Query HTTP debug endpoint
	resp, err := http.Get("http://127.0.0.1:5080/debug/events")
	if err != nil {
		t.Fatalf("http get debug events error: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		t.Fatalf("unexpected http status: %d", resp.StatusCode)
	}
	var arr []map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&arr); err != nil {
		t.Fatalf("decode json: %v", err)
	}
	if len(arr) == 0 {
		t.Fatalf("no events recorded in debug buffer")
	}
}
