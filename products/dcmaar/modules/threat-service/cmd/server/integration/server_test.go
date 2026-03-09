package integration_test

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"google.golang.org/protobuf/types/known/timestamppb"
	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/internal/testutil"
)

func TestServer(t *testing.T) {
	// Start test server
	client, cleanup := testutil.StartTestServer(t)
	defer cleanup()
	// Test health check
	t.Run("HealthCheck", func(t *testing.T) {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		resp, err := client.HealthCheck(ctx, &pb.HealthCheckRequest{})
		require.NoError(t, err)
		require.Equal(t, pb.HealthCheckResponse_SERVING, resp.Status)
	})

	// Test event ingestion
	t.Run("IngestEvent", func(t *testing.T) {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		env := &pb.EventEnvelopeBatch{
			Envelopes: []*pb.EventEnvelope{
				{
					Meta: &pb.EnvelopeMeta{
						TenantId:      "tenant-1",
						DeviceId:      "device-1",
						SessionId:     "session-1",
						Timestamp:     time.Now().UnixMilli(),
						SchemaVersion: "1.0.0",
					},
					Events: []*pb.EventWithMetadata{
						{
							Event: &pb.Event{
								Id:        "test-1",
								Type:      pb.EventType_EVENT_TYPE_USER,
								Source:    "test",
								Timestamp: timestamppb.Now(),
								Data:      []byte(`{"key":"value"}`),
							},
						},
					},
				},
			},
		}

		_, err := client.SendEventEnvelopes(ctx, env)
		require.NoError(t, err)
	})
}
