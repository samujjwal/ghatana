package testutil

import (
	"time"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// TestEvent creates a new test event with default values
func TestEvent() *pb.Event {
	return &pb.Event{
		Id:        "test-event-123",
		Type:      pb.EventType_EVENT_TYPE_USER,
		Source:    "test",
		Timestamp: timestamppb.New(time.Now()),
		Data:      []byte(`{"key":"value"}`),
	}
}

// TestEvents creates a slice of test events
func TestEvents(count int) []*pb.Event {
	events := make([]*pb.Event, 0, count)
	for i := 0; i < count; i++ {
		event := TestEvent()
		event.Id = event.Id + "-" + string(rune('a'+i))
		events = append(events, event)
	}
	return events
}
