package main

import (
    "context"
    "encoding/json"
    "flag"
    "fmt"
    "log"
    "math/rand"
    "sync"
    "sync/atomic"
    "time"

    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
    "google.golang.org/protobuf/types/known/timestamppb"

    pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

var (
    eventTypes = []string{
        "click",
        "keypress",
        "file.open",
        "http.request",
        "app.focus",
    }
    applications = []string{"chrome", "vscode", "terminal", "slack", "spotify"}
    users       = []string{"alice", "bob", "charlie", "dave"}
    devices     = []string{"laptop-1", "desktop-1", "vm-1"}
)

type result struct {
    success int64
    failed  int64
}

func main() {
    var (
        eventCount  = flag.Int("events", 10000, "Number of events to send")
        concurrency = flag.Int("concurrency", 100, "Number of concurrent senders")
        serverAddr  = flag.String("server", "localhost:50051", "gRPC server address")
        enableTLS   = flag.Bool("tls", false, "Enable TLS")
    )

    flag.Parse()

    // Set up gRPC connection
    var opts []grpc.DialOption
    if *enableTLS {
        // TODO: Add TLS configuration if needed
    } else {
        opts = append(opts, grpc.WithTransportCredentials(insecure.NewCredentials()))
    }

    conn, err := grpc.Dial(*serverAddr, opts...)
    if err != nil {
        log.Fatalf("Failed to connect to server: %v", err)
    }
    defer conn.Close()

    client := pb.NewIngestServiceClient(conn)

    // Initialize metrics
    var wg sync.WaitGroup
    results := make(chan result, *concurrency)
    startTime := time.Now()

    // Start workers
    for i := 0; i < *concurrency; i++ {
        wg.Add(1)
        go worker(context.Background(), client, *eventCount / *concurrency, results, &wg)
    }

    // Start metrics reporter
    var totalSuccess, totalFailed int64
    go func() {
        ticker := time.NewTicker(1 * time.Second)
        defer ticker.Stop()

        for {
            select {
            case r := <-results:
                atomic.AddInt64(&totalSuccess, r.success)
                atomic.AddInt64(&totalFailed, r.failed)
            case <-ticker.C:
                duration := time.Since(startTime).Seconds()
                if duration == 0 {
                    continue
                }

                success := atomic.LoadInt64(&totalSuccess)
                failed := atomic.LoadInt64(&totalFailed)
                total := success + failed

                log.Printf("Events: %d (%.1f/s), Success: %d (%.1f%%), Failed: %d (%.1f%%)",
                    total,
                    float64(total)/duration,
                    success,
                    float64(success)/float64(total)*100,
                    failed,
                    float64(failed)/float64(total)*100,
                )
            }
        }
    }()

    // Wait for all workers to finish
    wg.Wait()

    // Print final results
    duration := time.Since(startTime).Seconds()
    success := atomic.LoadInt64(&totalSuccess)
    failed := atomic.LoadInt64(&totalFailed)
    total := success + failed

    log.Println("\n=== Load Test Results ===")
    log.Printf("Duration: %.2f seconds", duration)
    log.Printf("Total Events: %d (%.1f events/sec)", total, float64(total)/duration)
    log.Printf("Success: %d (%.1f%%)", success, float64(success)/float64(total)*100)
    log.Printf("Failed: %d (%.1f%%)", failed, float64(failed)/float64(total)*100)
}

func worker(ctx context.Context, client pb.IngestServiceClient, count int, results chan<- result, wg *sync.WaitGroup) {
    defer wg.Done()

    var res result
    rand.Seed(time.Now().UnixNano())

    for i := 0; i < count; i++ {
        eventWithMeta, user, device := generateRandomEvent()

        // Add some jitter to simulate real-world conditions
        time.Sleep(time.Duration(rand.Intn(10)) * time.Millisecond)

        // Create an envelope for the event
        envelope := &pb.EventEnvelope{
            Meta: &pb.EnvelopeMeta{
                TenantId:      "loadtest-tenant",
                DeviceId:      device,
                SessionId:     fmt.Sprintf("%s-session-%d", user, rand.Intn(1000)),
                Timestamp:     time.Now().UnixMilli(),
                SchemaVersion: "1.0.0",
                Source:        "loadtest",
            },
            SchemaVersion: "1.0.0",
            Events:        []*pb.EventWithMetadata{eventWithMeta},
        }

        // Create a batch with the envelope
        batch := &pb.EventEnvelopeBatch{
            Envelopes: []*pb.EventEnvelope{envelope},
        }

        // Send the event batch
        _, err := client.SendEventEnvelopes(ctx, batch)
        if err != nil {
            log.Printf("Failed to send event: %v", err)
            res.failed++
        } else {
            res.success++
        }

        // Send periodic updates
        if i > 0 && i%100 == 0 {
            results <- res
            res = result{}
        }

        // Check if context is done
        select {
        case <-ctx.Done():
            return
        default:
        }
    }

    // Send remaining results
    if res.success > 0 || res.failed > 0 {
        results <- res
    }
}

func generateRandomEvent() (*pb.EventWithMetadata, string, string) {
    eventType := eventTypes[rand.Intn(len(eventTypes))]
    app := applications[rand.Intn(len(applications))]
    user := users[rand.Intn(len(users))]
    device := devices[rand.Intn(len(devices))]

    payload := map[string]string{
        "application": app,
        "user":        user,
        "device":      device,
        "event_type":  eventType,
    }

    if eventType == "file.open" {
        payload["path"] = fmt.Sprintf("/home/%s/documents/doc%d.txt", user, rand.Intn(100))
    }
    if eventType == "http.request" {
        payload["url"] = fmt.Sprintf("https://api.example.com/v1/resource/%d", rand.Intn(1000))
        payload["method"] = []string{"GET", "POST", "PUT", "DELETE"}[rand.Intn(4)]
        payload["status"] = fmt.Sprintf("%d", []int{200, 201, 400, 401, 403, 404, 500}[rand.Intn(7)])
    }

    payloadBytes, _ := json.Marshal(payload)

    evt := &pb.EventWithMetadata{
        Event: &pb.Event{
            Id:        fmt.Sprintf("%d", time.Now().UnixNano()),
            Type:      pb.EventType_EVENT_TYPE_USER,
            Source:    "loadtest",
            Timestamp: timestamppb.Now(),
            Data:      payloadBytes,
        },
        ActivityType: mapActivityType(eventType),
        Classification: &pb.ActivityClassification{
            IsAutomated: false,
            Confidence:  0.9,
            ModelVersion: "loadtest-1",
            Source:      "loadtest",
        },
    }

    return evt, user, device
}

func mapActivityType(eventType string) pb.ActivityType {
    switch eventType {
    case "http.request":
        return pb.ActivityType_ACTIVITY_NETWORK
    case "file.open":
        return pb.ActivityType_ACTIVITY_FILE_OPERATION
    case "app.focus":
        return pb.ActivityType_ACTIVITY_APPLICATION_USAGE
    default:
        return pb.ActivityType_ACTIVITY_USER_INTERACTION
    }
}
