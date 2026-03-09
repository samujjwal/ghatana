package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

func main() {
	// Generate sample gRPC requests
	requests := []interface{}{
		generateIngestEventsRequest(),
		generateGetEventsRequest(),
		generateGetMetricsRequest(),
	}

	// Create output directory if it doesn't exist
	if err := os.MkdirAll("testdata/requests", 0755); err != nil {
		log.Fatalf("Failed to create requests directory: %v", err)
	}

	// Write requests to files
	for i, req := range requests {
		filename := fmt.Sprintf("testdata/requests/request_%d.json", i+1)
		if err := writeRequestToFile(filename, req); err != nil {
			log.Printf("Failed to write request to %s: %v", filename, err)
		}
	}

	fmt.Println("✅ Sample gRPC requests generated successfully!")
}

func generateIngestEventsRequest() *pb.IngestRequest {
	return &pb.IngestRequest{
		Batch: &pb.EventEnvelopeBatch{
			Envelopes: []*pb.EventEnvelope{
				{
					Meta: &pb.EnvelopeMeta{
						TenantId:      "tenant-1",
						DeviceId:      "device-1",
						SessionId:     "session-1",
						Timestamp:     time.Now().UnixMilli(),
						SchemaVersion: "1.0.0",
						Source:        "test-generator",
					},
					SchemaVersion: "1.0.0",
					Events: []*pb.EventWithMetadata{
						{
							Event: &pb.Event{
								Id:        "event-1",
								Type:      pb.EventType_EVENT_TYPE_USER,
								Source:    "browser_extension",
								Timestamp: timestamppb.Now(),
								Data:      []byte(`{"button_id":"submit","page":"dashboard"}`),
							},
							ActivityType: pb.ActivityType_ACTIVITY_USER_INTERACTION,
							Classification: &pb.ActivityClassification{
								IsAutomated: false,
								Confidence:  0.95,
								ModelVersion: "1.0.0",
								Source:      "test-generator",
							},
						},
					},
				},
			},
		},
		SchemaVersion: "1.0.0",
		Metadata: map[string]string{
			"generator": "scripts/generate-grpc-requests.go",
		},
	}
}

func generateGetEventsRequest() *pb.QueryEventsRequest {
	now := time.Now()
	return &pb.QueryEventsRequest{
		PageSize: 10,
		TimeRange: &pb.TimeRange{
			StartTime: timestamppb.New(now.Add(-24 * time.Hour)),
			EndTime:   timestamppb.New(now),
		},
		Filter: &pb.EventFilter{
			AllOf: []*pb.EventFilterCondition{
				{
					Field:       "event.type",
					StringValue: "EVENT_TYPE_USER",
					Operator:    "=",
				},
			},
		},
	}
}

func generateGetMetricsRequest() *pb.GetMetricsRequest {
	return &pb.GetMetricsRequest{
		Filter: &pb.Filter{
			Filter: &pb.Filter_FieldFilter{
				FieldFilter: &pb.FieldFilter{
					Field:    "metric.name",
					Operator: "eq",
					Value:    "test.metric",
				},
			},
		},
		TimeRange: &pb.TimeRange{
			StartTime: timestamppb.New(time.Now().Add(-24 * time.Hour)),
			EndTime:   timestamppb.Now(),
		},
	}
}

func writeRequestToFile(filename string, req interface{}) error {
	jsonBytes, err := protojson.Marshal(req.(proto.Message))
	if err != nil {
		return fmt.Errorf("failed to marshal request: %v", err)
	}

	var jsonObj map[string]interface{}
	if err := json.Unmarshal(jsonBytes, &jsonObj); err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	file, err := os.Create(filename)
	if err != nil {
		return fmt.Errorf("failed to create file: %v", err)
	}
	defer file.Close()

	encoder := json.NewEncoder(file)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(jsonObj); err != nil {
		return fmt.Errorf("failed to write JSON to file: %v", err)
	}

	return nil
}
