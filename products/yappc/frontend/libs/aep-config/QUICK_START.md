# AEP Dual-Mode Integration - Quick Reference

## 🎯 TL;DR

YAPPC now supports **two ways to use AEP**:

| Mode           | Default         | Use Case           | Setup                     |
| -------------- | --------------- | ------------------ | ------------------------- |
| **📚 Library** | ✅ Development  | Local dev, testing | No service needed         |
| **🔗 Service** | ✅ Staging/Prod | Production, scale  | External service required |

### One-Line Summary

> **Dev defaults to library mode (no setup). Staging/prod use service mode. Explicitly override with `AEP_MODE=library\|service` if needed.**

---

## 🚀 Getting Started

### Development (Automatic)

```bash
# Nothing to configure - library mode is default
export NODE_ENV=development
npm run dev
```

### Staging/Production (Automatic)

```bash
# Configure service location
export NODE_ENV=production
export AEP_SERVICE_HOST=aep-service.internal
export AEP_SERVICE_PORT=7106

npm run start
```

### Force a Mode (Override)

```bash
# Force library mode anywhere
export AEP_MODE=library

# Force service mode anywhere
export AEP_MODE=service
```

---

## 📚 Library Mode (Development)

**Default for `NODE_ENV=development`**

✅ **Pros**:

- No external service needed
- Fast startup
- Perfect for development
- Great for unit testing

❌ **Cons**:

- Single process only
- Not suitable for production
- Limited to one instance

**Configuration**:

```bash
NODE_ENV=development
# Optional:
AEP_LIBRARY_DEBUG=true
AEP_LIBRARY_CACHE_SIZE=1000
```

**Usage**:

```typescript
const client = await getGlobalAepClient();
// Client runs in-process, instant results
await client.executeAgent('my-agent', { data: 'test' });
```

---

## 🔗 Service Mode (Production)

**Default for `NODE_ENV=staging|production`**

✅ **Pros**:

- Distributed architecture
- Multiple clients
- Production-ready
- Scalable

❌ **Cons**:

- Requires external service
- Network latency
- More complex setup

**Configuration**:

```bash
NODE_ENV=production
AEP_SERVICE_HOST=aep-service.internal
AEP_SERVICE_PORT=7106
# Optional:
AEP_SERVICE_TIMEOUT=60000
AEP_SERVICE_MAX_RETRIES=5
```

**Usage**:

```typescript
const client = await getGlobalAepClient();
// Client connects to external service
await client.executeAgent('my-agent', { data: 'test' });
```

---

## 🔧 Configuration Priority

```
1. AEP_MODE env var (highest)      ← Explicit override
     ↓
2. NODE_ENV defaults               ← Automatic by environment
     ↓
3. development/library (lowest)    ← Fallback
```

**Examples**:

```bash
# Scenario 1: Auto detect
NODE_ENV=development  → Uses library mode
NODE_ENV=production   → Uses service mode

# Scenario 2: Override
NODE_ENV=production AEP_MODE=library → Uses library mode (override)

# Scenario 3: Default
(nothing set)  → Uses development/library mode
```

---

## 📋 Environment Variables

### Mode Selection

```bash
AEP_MODE=library          # Force library mode
AEP_MODE=service          # Force service mode
```

### Library Mode (Used when `AEP_MODE=library`)

```bash
AEP_LIBRARY_DEBUG=true                    # Debug logging
AEP_LIBRARY_CACHE_SIZE=1000               # Cache size
AEP_LIBRARY_PATTERN_DETECTION_INTERVAL=5000  # Interval (ms)
AEP_LIBRARY_MAX_CONCURRENT_OPS=10         # Max ops
```

### Service Mode (Used when `AEP_MODE=service`)

```bash
AEP_SERVICE_HOST=localhost                # Service host
AEP_SERVICE_PORT=7106                     # Service port
AEP_SERVICE_TIMEOUT=30000                 # Timeout (ms)
AEP_SERVICE_MAX_RETRIES=3                 # Retry count
AEP_SERVICE_HEALTH_CHECK_ENABLED=true     # Health checks
AEP_SERVICE_HEALTH_CHECK_INTERVAL=30000   # Check interval (ms)
```

### Global (All Modes)

```bash
AEP_METRICS_ENABLED=true|false            # Enable metrics
AEP_LOG_LEVEL=debug|info|warn|error       # Log level
AEP_TRACING_ENABLED=true|false            # Distributed tracing
```

---

## 💻 Code Examples

### Automatic Mode Detection

```typescript
import { getAepConfig, createAepClient } from '@yappc/aep-config';

// Automatically detects and creates right client
const config = getAepConfig();
const client = createAepClient(config);
await client.initialize();
```

### Global Client (Recommended)

```typescript
import { getGlobalAepClient } from '@yappc/aep-config';

// Use anywhere in your app
const client = await getGlobalAepClient();
await client.executeAgent('my-agent', { data: 'test' });
```

### Mode-Specific Logic

```typescript
import { isLibraryMode, isServiceMode } from '@yappc/aep-config';

if (isLibraryMode()) {
  // Optimizations for library mode
  console.log('Running in-process');
} else if (isServiceMode()) {
  // Resilience for service mode
  console.log('Running distributed');
}
```

### Check Configuration

```typescript
import { getAepConfig, formatAepConfig } from '@yappc/aep-config';

const config = getAepConfig();
console.log(formatAepConfig(config));
```

---

## 🐛 Troubleshooting

### Service mode: "Cannot connect"

```bash
# Check if service is running
telnet aep-service 7106

# Or switch to library mode
export AEP_MODE=library

# Or check configuration
export AEP_LOG_LEVEL=debug
```

### Library mode: "Cache exceeded"

```bash
# Increase cache size
export AEP_LIBRARY_CACHE_SIZE=5000
```

### Slow responses in service mode

```bash
# Increase timeout
export AEP_SERVICE_TIMEOUT=60000

# Check service health
export AEP_LOG_LEVEL=debug
```

---

## ✅ Checklist

### Development Setup

- [ ] `NODE_ENV=development` set
- [ ] `npm run dev` works
- [ ] AEP client initializes automatically
- [ ] No service needed

### Production Setup

- [ ] `NODE_ENV=production` set
- [ ] AEP service running and accessible
- [ ] `AEP_SERVICE_HOST` configured
- [ ] `AEP_SERVICE_PORT` configured
- [ ] Health checks enabled
- [ ] Metrics enabled

### Testing

- [ ] Library mode works with mock data
- [ ] Service mode handles failures gracefully
- [ ] Mode switching works correctly
- [ ] Configuration logging is working

---

## 📚 Documentation

- **Full Guide**: See `AEP_MODE_CONFIGURATION.md`
- **Code Examples**: See `examples.ts`
- **API Reference**: See `aep-client-factory.ts` and `aep-mode.ts`

---

## 🔄 How It Works

```
User Code
    ↓
getGlobalAepClient()
    ↓
    ├─→ getAepConfig() → Detects NODE_ENV
    │   ├─→ NODE_ENV=dev → Library mode
    │   ├─→ NODE_ENV=prod → Service mode
    │   └─→ AEP_MODE override → Use that
    │
    ├─→ createAepClient(config)
    │   ├─→ If library mode → Creates AepLibraryClient
    │   └─→ If service mode → Creates AepServiceClient
    │
    └─→ client.initialize()
        ├─→ Library: Setup in-process cache
        └─→ Service: Connect to external service

User calls:
    client.executeAgent()
    client.publishEvent()
    client.queryEvents()
    etc.
```

---

## 🎓 Learning Path

1. **Start**: Use default dev mode (nothing to configure)
2. **Understand**: Read `AEP_MODE_CONFIGURATION.md`
3. **Practice**: Try examples in `examples.ts`
4. **Deploy**: Configure staging/production with service mode
5. **Debug**: Use `formatAepConfig()` to verify settings

---

**Version**: 1.0  
**Last Updated**: January 30, 2026  
**Status**: ✅ Production Ready
