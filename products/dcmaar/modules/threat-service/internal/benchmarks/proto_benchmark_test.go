package benchmarks

import (
	"testing"
	"time"

	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// BenchmarkProtoSerialization benchmarks protobuf serialization operations
func BenchmarkProtoSerialization(b *testing.B) {
	envelope := createTestEventEnvelope()

	b.Run("SerializeSingle", func(b *testing.B) {
		b.ReportAllocs()
		for i := 0; i < b.N; i++ {
			data, err := proto.Marshal(envelope)
			if err != nil {
				b.Fatal(err)
			}
			_ = data
		}
	})
}

// Helper functions
func createTestEventEnvelope() *pb.EventEnvelope {
	return &pb.EventEnvelope{
		Meta: &pb.EnvelopeMeta{
			TenantId:      "test-tenant-123",
			DeviceId:      "device-456",
			SessionId:     "session-789",
			Timestamp:     time.Now().UnixMilli(),
			SchemaVersion: "1.0.0",
		},
		Events: []*pb.EventWithMetadata{
			{
				Event: &pb.Event{
					Id:        "event-1",
					Type:      pb.EventType_EVENT_TYPE_USER,
					Source:    "benchmark",
					Timestamp: timestamppb.Now(),
					Data:      []byte(`{"message":"hello"}`),
				},
				ActivityType: pb.ActivityType_ACTIVITY_USER_INTERACTION,
			},
		},
	}
}
