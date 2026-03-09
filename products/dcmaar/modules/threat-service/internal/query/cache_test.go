package query

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap/zaptest"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

func TestQueryCache_GetSet(t *testing.T) {
	// Setup
	logger := zaptest.NewLogger(t)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: true,
		Backend: "memory",
		TTL:     5 * time.Minute,
	}, logger)
	require.NoError(t, err)

	// Test data
	req := &pb.QueryEventsRequest{PageSize: 10}

	resp := &pb.QueryEventsResponse{
		Events: []*pb.EventWithMetadata{{Event: &pb.Event{Id: "1", Type: pb.EventType_EVENT_TYPE_USER}}},
	}

	// Test Set
	err = cache.Set(context.Background(), req, resp)
	require.NoError(t, err)
}

func TestQueryCache_Invalidation(t *testing.T) {
	// Setup
	logger := zaptest.NewLogger(t)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: true,
		Backend: "memory",
		TTL:     5 * time.Minute,
	}, logger)
	require.NoError(t, err)

	// Test data
	req := &pb.QueryEventsRequest{PageSize: 10}

	resp := &pb.QueryEventsResponse{
		Events: []*pb.EventWithMetadata{{Event: &pb.Event{Id: "1", Type: pb.EventType_EVENT_TYPE_USER}}},
	}

	// Set and verify
	err = cache.Set(context.Background(), req, resp)
	require.NoError(t, err)

	// Invalidate
	err = cache.Invalidate(context.Background(), req)
	require.NoError(t, err)

	// Should be a miss after invalidation
	cached, err := cache.Get(context.Background(), req)
	require.NoError(t, err)
	assert.Nil(t, cached)
}

func TestQueryCache_CacheKeyStability(t *testing.T) {
	// Test that cache keys are stable and ignore certain fields
	logger := zaptest.NewLogger(t)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: true,
		Backend: "memory",
	}, logger)
	require.NoError(t, err)

	req1 := &pb.QueryEventsRequest{PageToken: "page1"}

	req2 := &pb.QueryEventsRequest{PageToken: "page2"} // Different page token, should be ignored by cacheKey

	key1, err := cache.cacheKey(req1)
	require.NoError(t, err)

	key2, err := cache.cacheKey(req2)
	require.NoError(t, err)

	// Keys should be the same despite different page tokens
	assert.Equal(t, key1, key2)
}

func TestQueryCache_TTL(t *testing.T) {
	// Test TTL functionality
	logger := zaptest.NewLogger(t)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: true,
		Backend: "memory",
		TTL:     100 * time.Millisecond, // Very short TTL for testing
	}, logger)
	require.NoError(t, err)

	req := &pb.QueryEventsRequest{}
	resp := &pb.QueryEventsResponse{Events: []*pb.EventWithMetadata{{Event: &pb.Event{Id: "1"}}}}

	// Set and verify
	err = cache.Set(context.Background(), req, resp)
	require.NoError(t, err)

	// Should be available immediately
	cached, err := cache.Get(context.Background(), req)
	require.NoError(t, err)
	require.NotNil(t, cached)

	// Wait for TTL to expire
	time.Sleep(150 * time.Millisecond)

	// Should be expired now
	cached, err = cache.Get(context.Background(), req)
	require.NoError(t, err)
	assert.Nil(t, cached)
}

func TestQueryCache_Disabled(t *testing.T) {
	// Test that cache is disabled when configured as such
	logger := zaptest.NewLogger(t)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: false, // Cache disabled
	}, logger)
	require.NoError(t, err)

	req := &pb.QueryEventsRequest{}
	resp := &pb.QueryEventsResponse{}

	// Set should do nothing
	err = cache.Set(context.Background(), req, resp)
	require.NoError(t, err)

	// Get should always return nil
	cached, err := cache.Get(context.Background(), req)
	require.NoError(t, err)
	assert.Nil(t, cached)
}

func TestRedisCache_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping Redis integration test in short mode")
	}

	// This test requires a running Redis instance
	logger := zaptest.NewLogger(t)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: true,
		Backend: "redis",
		TTL:     5 * time.Minute,
		RedisConfig: &RedisConfig{
			Addr: "localhost:6379",
			DB:   1, // Use a different DB for testing
		},
	}, logger)
	require.NoError(t, err)

	// Clean up test data
	defer func() {
		// Flush the test DB
		rdb := cache.backend.(*RedisCache)
		rdb.client.FlushDB(context.Background())
	}()

	// Test data
	req := &pb.QueryEventsRequest{}

	resp := &pb.QueryEventsResponse{
		Events: []*pb.EventWithMetadata{{Event: &pb.Event{Id: "redis1", Type: pb.EventType_EVENT_TYPE_USER}}},
	}

	// Test Set
	err = cache.Set(context.Background(), req, resp)
	require.NoError(t, err)

	// Test Get
	cached, err := cache.Get(context.Background(), req)
	require.NoError(t, err)
	require.NotNil(t, cached)
	assert.Equal(t, 1, len(cached.Events))
	assert.Equal(t, "redis1", cached.Events[0].Event.Id)

	// Test Invalidate
	err = cache.Invalidate(context.Background(), req)
	require.NoError(t, err)

	// Should be a miss after invalidation
	cached, err = cache.Get(context.Background(), req)
	require.NoError(t, err)
	assert.Nil(t, cached)
}

func TestQueryCache_HealthCheck(t *testing.T) {
	logger := zaptest.NewLogger(t)

	// Test with memory backend (always healthy)
	cache, err := NewQueryCache(&CacheConfig{
		Enabled: true,
		Backend: "memory",
	}, logger)
	require.NoError(t, err)
	assert.True(t, cache.HealthCheck())

	// Test with disabled cache
	disabledCache, err := NewQueryCache(&CacheConfig{
		Enabled: false,
	}, logger)
	require.NoError(t, err)
	assert.False(t, disabledCache.HealthCheck()) // Disabled cache is not healthy
}
