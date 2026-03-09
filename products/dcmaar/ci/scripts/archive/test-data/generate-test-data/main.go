package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"os"
	"time"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

func main() {
	// Initialize random seed
	rand.Seed(time.Now().UnixNano())

	// Generate test events
	events := generateTestEvents(5)

	// Create output directory if it doesn't exist
	if err := os.MkdirAll("testdata", 0755); err != nil {
		log.Fatalf("Failed to create testdata directory: %v", err)
	}

	// Write events to a JSON file
	writeEventsToFile("testdata/events.json", events)

	// Generate test metrics
	metrics := generateTestMetrics(5)
	writeMetricsToFile("testdata/metrics.json", metrics)

	fmt.Println("✅ Test data generated successfully!")
}

func generateTestEvents(count int) []*pb.EventWithMetadata {
	var events []*pb.EventWithMetadata
	now := time.Now()

	// Sample data
	activityTypes := []pb.ActivityType{
		pb.ActivityType_ACTIVITY_USER_INTERACTION,
		pb.ActivityType_ACTIVITY_APPLICATION_USAGE,
		pb.ActivityType_ACTIVITY_FILE_OPERATION,
		pb.ActivityType_ACTIVITY_NETWORK,
	}

	for i := 0; i < count; i++ {
		event := &pb.EventWithMetadata{
			Event: &pb.Event{
				Id:        fmt.Sprintf("event-%d", i+1),
				Type:      pb.EventType_EVENT_TYPE_USER,
				Source:    "generator",
				Timestamp: timestamppb.New(now.Add(-time.Duration(rand.Intn(24)) * time.Hour)),
				Data:      []byte(fmt.Sprintf(`{"key": "value-%d"}`, i+1)),
			},
			ActivityType: activityTypes[rand.Intn(len(activityTypes))],
			Classification: &pb.ActivityClassification{
				IsAutomated: rand.Float32() > 0.5,
				Confidence:  rand.Float32(),
				Source:      "test-generator",
			},
		}

		events = append(events, event)
	}

	return events
}

func generateTestMetrics(count int) []*pb.Metric {
	var metrics []*pb.Metric
	now := time.Now()

	metricTypes := []string{"gauge", "counter", "histogram"}
	units := []string{"ms", "count", "bytes", "%"}

	for i := 0; i < count; i++ {
		metric := &pb.Metric{
			Name:      fmt.Sprintf("test.metric.%d", i+1),
			Value:     rand.Float64() * 100,
			Timestamp: timestamppb.New(now.Add(-time.Duration(rand.Intn(24)) * time.Hour)),
			Unit:      units[rand.Intn(len(units))],
			Type:      metricTypes[rand.Intn(len(metricTypes))],
			Tags: map[string]string{
				"environment": "test",
				"host":        "test-host",
			},
		}

		metrics = append(metrics, metric)
	}

	return metrics
}

func writeEventsToFile(filename string, events []*pb.EventWithMetadata) {
	f, err := os.Create(filename)
	if err != nil {
		log.Fatalf("Failed to create file %s: %v", filename, err)
	}
	defer f.Close()

	// Convert events to JSON
	var jsonEvents []map[string]interface{}
	for _, event := range events {
		jsonBytes, err := protojson.Marshal(event)
		if err != nil {
			log.Printf("Failed to marshal event: %v", err)
			continue
		}

		var jsonEvent map[string]interface{}
		if err := json.Unmarshal(jsonBytes, &jsonEvent); err != nil {
			log.Printf("Failed to unmarshal event JSON: %v", err)
			continue
		}

		jsonEvents = append(jsonEvents, jsonEvent)
	}

	encoder := json.NewEncoder(f)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(jsonEvents); err != nil {
		log.Fatalf("Failed to write events to file: %v", err)
	}
}

func writeMetricsToFile(filename string, metrics []*pb.Metric) {
	f, err := os.Create(filename)
	if err != nil {
		log.Fatalf("Failed to create file %s: %v", filename, err)
	}
	defer f.Close()

	// Convert metrics to JSON
	var jsonMetrics []map[string]interface{}
	for _, metric := range metrics {
		jsonBytes, err := protojson.Marshal(metric)
		if err != nil {
			log.Printf("Failed to marshal metric: %v", err)
			continue
		}

		var jsonMetric map[string]interface{}
		if err := json.Unmarshal(jsonBytes, &jsonMetric); err != nil {
			log.Printf("Failed to unmarshal metric JSON: %v", err)
			continue
		}

		jsonMetrics = append(jsonMetrics, jsonMetric)
	}

	encoder := json.NewEncoder(f)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(jsonMetrics); err != nil {
		log.Fatalf("Failed to write metrics to file: %v", err)
	}
}
