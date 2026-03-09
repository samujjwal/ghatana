# AEP Mode Configuration for YAPPC

## Overview

YAPPC supports two modes for using the Agentic Event Processor (AEP):

### 📚 Library Mode (Development Default)

- **AEP runs as an in-process library**
- No external service needed
- Perfect for local development and rapid iteration
- Zero network latency
- **Default for `NODE_ENV=development`**

### 🔗 Service Mode (Production)

- **AEP runs as an external microservice**
- Distributed architecture
- Production-ready reliability
- Supports multiple clients
- **Default for `NODE_ENV=staging` and `NODE_ENV=production`**

---

## Quick Start

### Development (Automatic Library Mode)

```bash
# No configuration needed - just set NODE_ENV
export NODE_ENV=development

# YAPPC automatically uses AEP in library mode
npm run dev
```

### Production (Automatic Service Mode)

```bash
# YAPPC automatically uses AEP in service mode
export NODE_ENV=production
export AEP_SERVICE_HOST=aep-service.internal
export AEP_SERVICE_PORT=7106

npm run start
```

---

## Configuration

### Environment Variables

All AEP configuration is done via environment variables. See `.env.example` for all options.

#### Mode Selection (Highest Priority)

```bash
# 1. Explicit mode override (highest priority)
AEP_MODE=library  # or 'service'

# 2. NODE_ENV default (if AEP_MODE not set)
# NODE_ENV=development → library mode (default)
# NODE_ENV=staging → service mode (default)
# NODE_ENV=production → service mode (default)

# 3. Development (fallback)
# If nothing is set, defaults to development/library mode
```

#### Library Mode Options

```bash
# Only used when AEP_MODE=library or NODE_ENV=development
AEP_LIBRARY_DEBUG=true|false              # Enable debug logging
AEP_LIBRARY_CACHE_SIZE=1000               # Number of cached items
AEP_LIBRARY_PATTERN_DETECTION_INTERVAL=5000  # Detection interval (ms)
AEP_LIBRARY_MAX_CONCURRENT_OPS=10         # Max concurrent operations
```

#### Service Mode Options

```bash
# Only used when AEP_MODE=service
AEP_SERVICE_HOST=aep-service              # Service hostname or IP
AEP_SERVICE_PORT=7106                     # Service port
AEP_SERVICE_TIMEOUT=30000                 # Connection timeout (ms)
AEP_SERVICE_MAX_RETRIES=3                 # Retry attempts
AEP_SERVICE_HEALTH_CHECK_ENABLED=true     # Enable health checks
AEP_SERVICE_HEALTH_CHECK_INTERVAL=30000   # Health check interval (ms)
```

#### Global Options (All Modes)

```bash
AEP_METRICS_ENABLED=true|false           # Enable metrics collection
AEP_LOG_LEVEL=debug|info|warn|error       # Log level
AEP_TRACING_ENABLED=true|false            # Enable distributed tracing
```

---

## Usage

### TypeScript/JavaScript

```typescript
import {
  getAepConfig,
  createAepClient,
  getGlobalAepClient,
} from '@yappc/aep-config';

// Automatic mode detection
const config = getAepConfig();
const client = createAepClient(config);
await client.initialize();

// Or use global instance
const client = await getGlobalAepClient();
await client.executeAgent('my-agent', { input: 'data' });
```

### React Component

```tsx
import { useEffect, useState } from 'react';
import { getGlobalAepClient, AepMode } from '@yappc/aep-config';

export function MyComponent() {
  const [mode, setMode] = useState<AepMode | null>(null);

  useEffect(() => {
    getGlobalAepClient().then((client) => {
      setMode(client.getMode());
    });
  }, []);

  return <div>AEP Mode: {mode}</div>;
}
```

---

## Configuration Examples

### Example 1: Development with Library Mode

```bash
# .env.local
NODE_ENV=development
AEP_LIBRARY_DEBUG=true
AEP_LOG_LEVEL=debug
```

**Result**: AEP runs in-process with debug logging

### Example 2: Staging with Service Mode

```bash
# .env.staging
NODE_ENV=staging
AEP_SERVICE_HOST=aep-staging.internal
AEP_SERVICE_PORT=7106
AEP_SERVICE_HEALTH_CHECK_ENABLED=true
AEP_METRICS_ENABLED=true
```

**Result**: YAPPC connects to external AEP service with health checks

### Example 3: Production with Service Mode

```bash
# .env.production
NODE_ENV=production
AEP_SERVICE_HOST=${AEP_SERVICE_HOST}        # From deployment
AEP_SERVICE_PORT=7106
AEP_SERVICE_TIMEOUT=60000
AEP_SERVICE_MAX_RETRIES=5
AEP_METRICS_ENABLED=true
AEP_TRACING_ENABLED=true
```

**Result**: Production-ready AEP integration with resilience

### Example 4: Force Library Mode in Staging (Testing)

```bash
# .env.staging.local
NODE_ENV=staging
AEP_MODE=library  # Override to use library mode for testing
```

**Result**: Even in staging, uses library mode

---

## Mode Comparison

| Aspect                 | Library Mode       | Service Mode             |
| ---------------------- | ------------------ | ------------------------ |
| **External Service**   | ❌ Not needed      | ✅ Required              |
| **Startup Time**       | ⚡ Fast            | 🐢 Slower (network init) |
| **Development**        | ✅ Perfect         | ⚠️ Need service running  |
| **Production**         | ⚠️ Not recommended | ✅ Ideal                 |
| **Scalability**        | Single process     | ✅ Distributed           |
| **Network Latency**    | None               | Variable                 |
| **Testing**            | ✅ Easy            | ⚠️ Requires mocking      |
| **Default for `dev`**  | ✅ Yes             | No                       |
| **Default for `prod`** | No                 | ✅ Yes                   |

---

## Debugging

### Check Current Mode

```typescript
import { getAepConfig, formatAepConfig } from '@yappc/aep-config';

const config = getAepConfig();
console.log(formatAepConfig(config));
```

Output:

```
🔧 AEP Configuration
  Mode: library
  Environment: development
  Library Settings:
    - Cache Size: 1000
    - Pattern Detection: 5000ms
    - Max Concurrent Ops: 10
    - Debug Mode: ON
  Global Settings:
    - Metrics: OFF
    - Log Level: debug
    - Tracing: OFF
```

### Enable Debug Logging

```bash
# Enable verbose logging
export AEP_LOG_LEVEL=debug
export AEP_LIBRARY_DEBUG=true  # For library mode
```

### Check Service Connectivity

```typescript
const client = await getGlobalAepClient();
const health = await client.getHealth();
console.log('AEP Health:', health);
```

---

## Troubleshooting

### Error: "Cannot connect to AEP service"

**Cause**: Service mode is enabled but AEP service is not running

**Solution**:

1. Check `AEP_SERVICE_HOST` and `AEP_SERVICE_PORT`
2. Verify AEP service is running: `telnet aep-service 7106`
3. Or switch to library mode: `export AEP_MODE=library`

### Error: "Cache size exceeded"

**Cause**: In library mode, cache is full

**Solution**: Increase cache size

```bash
export AEP_LIBRARY_CACHE_SIZE=5000
```

### Error: "Service timeout"

**Cause**: AEP service is slow to respond

**Solution**: Increase timeout

```bash
export AEP_SERVICE_TIMEOUT=60000
```

---

## Best Practices

### ✅ DO

- Use **library mode for development** (default behavior)
- Use **service mode for production** (default behavior)
- Test mode switching with explicit `AEP_MODE` override
- Enable health checks in production
- Monitor metrics in service mode

### ❌ DON'T

- Don't use service mode in development without good reason
- Don't disable health checks in production
- Don't hardcode service addresses in code
- Don't set extreme timeout values

---

## Migration Path

### From No AEP → Library Mode (Dev)

```bash
# Already default - just use it
NODE_ENV=development
npm run dev
```

### From Library Mode → Service Mode

```bash
# Start AEP service
docker run ghatana/aep:latest

# Configure YAPPC
export NODE_ENV=production
export AEP_SERVICE_HOST=localhost
export AEP_SERVICE_PORT=7106

# Deploy
npm run start
```

---

## API Reference

### `getAepConfig(config?: AepConfig): AepConfig`

Get AEP configuration for current environment

### `createAepClient(config?: AepConfig): AepClient`

Create AEP client for current mode (library or service)

### `getGlobalAepClient(): Promise<AepClient>`

Get or create global AEP client instance

### `isLibraryMode(config?: AepConfig): boolean`

Check if running in library mode

### `isServiceMode(config?: AepConfig): boolean`

Check if running in service mode

### `formatAepConfig(config: AepConfig): string`

Format configuration for display/logging

---

## Resources

- **AEP Documentation**: https://ghatana.dev/aep
- **YAPPC Integration Guide**: See `libs/aep-config/examples.ts`
- **Configuration File**: See `.env.example`

---

**Last Updated**: January 30, 2026  
**AEP Version**: 2.0+  
**YAPPC Version**: 1.0+
