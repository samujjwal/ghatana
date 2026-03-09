package policy

import (
	"context"
	"sync"
	"time"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// LoaderFunc loads a policy when it's not found or expired in cache.
type LoaderFunc func(ctx context.Context, req *pb.PolicyRequest) (*pb.Policy, error)

type cachedItem struct {
	policy  *pb.Policy
	expires time.Time
}

// Cache is a simple TTL-based in-memory cache for policies.
type Cache struct {
	mu     sync.RWMutex
	items  map[string]cachedItem
	ttl    time.Duration
	loader LoaderFunc
}

// NewCache creates a new Cache with the provided TTL and loader.
func NewCache(ttl time.Duration, loader LoaderFunc) *Cache {
	return &Cache{items: make(map[string]cachedItem), ttl: ttl, loader: loader}
}

// key derives a cache key from a policy request. For now, only agent_id is used.
func (c *Cache) key(req *pb.PolicyRequest) string { return req.GetAgentId() }

// Get returns a policy from cache or loads it via loader.
func (c *Cache) Get(ctx context.Context, req *pb.PolicyRequest) (*pb.Policy, error) {
	k := c.key(req)
	now := time.Now()
	c.mu.RLock()
	if it, ok := c.items[k]; ok && now.Before(it.expires) {
		c.mu.RUnlock()
		return it.policy, nil
	}
	c.mu.RUnlock()

	if c.loader == nil {
		return nil, nil
	}
	pol, err := c.loader(ctx, req)
	if err != nil || pol == nil {
		return pol, err
	}
	c.mu.Lock()
	c.items[k] = cachedItem{policy: pol, expires: now.Add(c.ttl)}
	c.mu.Unlock()
	return pol, nil
}

// Invalidate removes a cached policy for a subject.
func (c *Cache) Invalidate(subject string) {
	c.mu.Lock()
	delete(c.items, subject)
	c.mu.Unlock()
}
