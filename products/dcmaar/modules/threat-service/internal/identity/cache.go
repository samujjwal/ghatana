package identity

import (
    "sync"
    "time"
)

// Identity represents a resolved subject identity and attributes.
type Identity struct {
    Subject string
    Roles   []string
    // Add more attributes (tenants, scopes, etc.) as needed.
}

type entry struct {
    id      *Identity
    expires time.Time
}

// Cache provides a simple TTL-based identity cache.
type Cache struct {
    mu    sync.RWMutex
    items map[string]entry
    ttl   time.Duration
}

// NewCache creates an identity cache with the given TTL.
func NewCache(ttl time.Duration) *Cache {
    return &Cache{items: make(map[string]entry), ttl: ttl}
}

// Get returns an identity for a subject if cached and not expired.
func (c *Cache) Get(subject string) *Identity {
    now := time.Now()
    c.mu.RLock()
    if e, ok := c.items[subject]; ok && now.Before(e.expires) {
        c.mu.RUnlock()
        return e.id
    }
    c.mu.RUnlock()
    return nil
}

// Set stores an identity for a subject with TTL.
func (c *Cache) Set(subject string, id *Identity) {
    c.mu.Lock()
    c.items[subject] = entry{id: id, expires: time.Now().Add(c.ttl)}
    c.mu.Unlock()
}

// Invalidate removes identity for a subject.
func (c *Cache) Invalidate(subject string) {
    c.mu.Lock()
    delete(c.items, subject)
    c.mu.Unlock()
}

