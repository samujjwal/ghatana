//go:build integration
// +build integration

package main

import (
    "context"
    "testing"
    "time"

    "github.com/go-redis/redis/v8"
    pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
    stelemetry "github.com/samujjwal/dcmaar/apps/server/internal/telemetry"
    "go.uber.org/zap"
)

type fakeStorage struct{ saved [][]*pb.EventEnvelope }

func (f *fakeStorage) SaveEvents(ctx context.Context, envs []*pb.EventEnvelope) error {
    // capture a copy
    cp := make([]*pb.EventEnvelope, len(envs))
    copy(cp, envs)
    f.saved = append(f.saved, cp)
    return nil
}

func newRedisClient(t *testing.T) *redis.Client {
    t.Helper()
    r := redis.NewClient(&redis.Options{Addr: "localhost:6379"})
    if err := r.Ping(context.Background()).Err(); err != nil {
        t.Skipf("redis not available: %v (run: make test-env-up)", err)
    }
    _ = r.FlushDB(context.Background()).Err()
    return r
}

func newEvent(id string) *pb.EventWithMetadata {
    return &pb.EventWithMetadata{Event: &pb.Event{Id: id}}
}

func TestIngest_DedupeAcrossRequests_WithRedis(t *testing.T) {
    logger := zap.NewNop()
    metrics, _ := stelemetry.NewMetrics(stelemetry.DefaultMetricsConfig(), logger)
    store := &fakeStorage{}
    rdb := newRedisClient(t)
    s := &ingestServer{logger: logger, storage: store, redis: rdb, metrics: metrics}

    env1 := &pb.EventEnvelope{Meta: &pb.EnvelopeMeta{IdempotencyKey: "key-1"}, Events: []*pb.EventWithMetadata{newEvent("e1"), newEvent("e2")}}

    // First call should persist both events
    if _, err := s.SendEventEnvelopes(context.Background(), &pb.EventEnvelopeBatch{Envelopes: []*pb.EventEnvelope{env1}}); err != nil {
        t.Fatalf("SendEventEnvelopes err: %v", err)
    }
    if len(store.saved) == 0 || len(store.saved[0]) != 1 {
        t.Fatalf("expected 1 envelope saved on first call, got %#v", store.saved)
    }

    // Second call with same idempotency key should be skipped entirely
    if _, err := s.SendEventEnvelopes(context.Background(), &pb.EventEnvelopeBatch{Envelopes: []*pb.EventEnvelope{env1}}); err != nil {
        t.Fatalf("SendEventEnvelopes second err: %v", err)
    }
    snap := metrics.Snapshot()
    if snap["ingest_idempotent_envelopes_skipped_total"] < 1 {
        t.Fatalf("expected idempotent envelope skip counter to increase, snapshot=%v", snap)
    }

    // New envelope with one duplicate event id should dedupe that event only
    env2 := &pb.EventEnvelope{Meta: &pb.EnvelopeMeta{IdempotencyKey: "key-2"}, Events: []*pb.EventWithMetadata{newEvent("e1"), newEvent("e3")}}
    if _, err := s.SendEventEnvelopes(context.Background(), &pb.EventEnvelopeBatch{Envelopes: []*pb.EventEnvelope{env2}}); err != nil {
        t.Fatalf("SendEventEnvelopes third err: %v", err)
    }
    // The last SaveEvents call should contain a single envelope with only e3 remaining
    if len(store.saved) < 3 {
        t.Fatalf("expected at least 3 SaveEvents calls, got %d", len(store.saved))
    }
    last := store.saved[len(store.saved)-1]
    if len(last) != 1 || len(last[0].Events) != 1 || last[0].Events[0].Event.Id != "e3" {
        t.Fatalf("unexpected last saved envelopes: %#v", last)
    }

    snap2 := metrics.Snapshot()
    if snap2["ingest_dedupe_events_total"] < 1 {
        t.Fatalf("expected dedupe events counter to increase, snapshot=%v", snap2)
    }

    // small sleep to ensure Redis TTL operations settle in CI
    time.Sleep(50 * time.Millisecond)
}

