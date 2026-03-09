package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"path/filepath"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

func main() {
	// Validate events
	eventFiles, err := filepath.Glob("testdata/*.events.json")
	if err != nil {
		log.Fatalf("Failed to find event files: %v", err)
	}

	for _, file := range eventFiles {
		fmt.Printf("🔍 Validating %s...\n", file)
		events, err := readEventsFromFile(file)
		if err != nil {
			log.Printf("❌ Failed to read events from %s: %v", file, err)
			continue
		}

		for i, event := range events {
			if err := event.Validate(); err != nil {
				log.Printf("❌ Event %d in %s is invalid: %v", i, file, err)
			} else {
				fmt.Printf("✅ Event %d in %s is valid\n", i, file)
			}
		}
	}

	// Validate metrics
	metricFiles, err := filepath.Glob("testdata/*.metrics.json")
	if err != nil {
		log.Fatalf("Failed to find metric files: %v", err)
	}

	for _, file := range metricFiles {
		fmt.Printf("🔍 Validating %s...\n", file)
		metrics, err := readMetricsFromFile(file)
		if err != nil {
			log.Printf("❌ Failed to read metrics from %s: %v", file, err)
			continue
		}

		for i, metric := range metrics {
			if err := metric.Validate(); err != nil {
				log.Printf("❌ Metric %d in %s is invalid: %v", i, file, err)
			} else {
				fmt.Printf("✅ Metric %d in %s is valid\n", i, file)
			}
		}
	}
}

func readEventsFromFile(filename string) ([]*pb.EventWithMetadata, error) {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		return nil, fmt.Errorf("failed to read file: %v", err)
	}

	var jsonEvents []map[string]interface{}
	if err := json.Unmarshal(data, &jsonEvents); err != nil {
		return nil, fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	var events []*pb.EventWithMetadata
	for _, jsonEvent := range jsonEvents {
		jsonBytes, err := json.Marshal(jsonEvent)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal JSON: %v", err)
		}

		event := &pb.EventWithMetadata{}
		if err := protojson.Unmarshal(jsonBytes, event); err != nil {
			return nil, fmt.Errorf("failed to unmarshal event: %v", err)
		}

		events = append(events, event)
	}

	return events, nil
}

func readMetricsFromFile(filename string) ([]*pb.Metric, error) {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		return nil, fmt.Errorf("failed to read file: %v", err)
	}

	var jsonMetrics []map[string]interface{}
	if err := json.Unmarshal(data, &jsonMetrics); err != nil {
		return nil, fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	var metrics []*pb.Metric
	for _, jsonMetric := range jsonMetrics {
		jsonBytes, err := json.Marshal(jsonMetric)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal JSON: %v", err)
		}

		metric := &pb.Metric{}
		if err := protojson.Unmarshal(jsonBytes, metric); err != nil {
			return nil, fmt.Errorf("failed to unmarshal metric: %v", err)
		}

		metrics = append(metrics, metric)
	}

	return metrics, nil
}

// Helper function to validate proto messages
func validateProtoMessage(m proto.Message) error {
	if v, ok := m.(interface{ Validate() error }); ok {
		return v.Validate()
	}
	return nil
}
