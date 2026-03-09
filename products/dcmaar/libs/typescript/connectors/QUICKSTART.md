# @dcmaar/connectors - Quick Reference

**Production-grade secure connectors for DCMAAR platform with pooling, batching, resilience, and observability.**

## Quick Start

### For Backend (Node.js)

```typescript
import { createConnector } from '@dcmaar/connectors';

// All 10 connector types available
const http = createConnector({ type: 'http', url: 'https://api.example.com' });
const ws = createConnector({ type: 'websocket', url: 'wss://api.example.com' });
const grpc = createConnector({ type: 'grpc', host: 'api.example.com' });
const mqtt = createConnector({ type: 'mqtt', brokerUrl: 'mqtt://broker.example.com' });
const fs = createConnector({ type: 'filesystem', path: '/data' });
// ... and 5 more
```

### For Browser/Extension

```typescript
import { createConnector } from '@dcmaar/connectors';

// Only 4 browser-safe connector types (compile-time enforced)
const http = createConnector({ type: 'http', url: 'https://api.example.com' });
const ws = createConnector({ type: 'websocket', url: 'wss://api.example.com' });
const fs = createConnector({ type: 'filesystem', storageKey: 'app-data' });
const native = createConnector({ type: 'native', api: 'chrome.alarms' });

// ❌ These won't compile in browser
// const mqtt = createConnector({ type: 'mqtt' });  // Error!
// const grpc = createConnector({ type: 'grpc' });  // Error!
```

## Available Connectors

| Type | Environment | Transport | Use Case |
|------|-------------|-----------|----------|
| `http` | Both | `fetch()` / Node.js `http` | REST/JSON APIs |
| `websocket` | Both | WebSocket | Real-time bidirectional communication |
| `filesystem` | Both | File System / Chrome Storage | Local data persistence |
| `native` | Both | Native APIs / chrome.* | Platform-specific operations |
| `grpc` | Node.js | gRPC | Typed RPC communication |
| `mqtt` | Node.js | MQTT | IoT message broker |
| `nats` | Node.js | NATS | Distributed messaging |
| `ipc` | Node.js | Node.js IPC | Inter-process communication |
| `mtls` | Node.js | mTLS | Mutual TLS connections |
| `mqtts` | Node.js | MQTT over TLS | Secure MQTT |

## Browser Configuration

### Package.json Export Conditions

```json
{
  "exports": {
    ".": {
      "browser": "./dist/index.browser.js",    // ← Used in browser
      "import": "./dist/index.js",             // ← Used in Node.js
      "require": "./dist/index.js"
    }
  }
}
```

### Vite Configuration

```typescript
// vite.config.ts
export default {
  resolve: {
    alias: {
      '@dcmaar/connectors': '../../path/to/connectors/src/index.browser'
    }
  },
  build: {
    rollupOptions: {
      external: [
        'fs', 'fs/promises', 'path', 'tls', 'crypto', 'url',
        'net', 'http', 'https'
      ]
    }
  }
}
```

### Tree-Shaking Benefits

- ✅ Only 4 connectors bundled (vs 10 in Node.js)
- ✅ No external dependencies (mqtt.js, nats.js, gRPC-web removed)
- ✅ Smaller bundle size
- ✅ Faster build times

## Core Features

### Resilience

```typescript
import { CircuitBreaker, RetryPolicy } from '@dcmaar/connectors';

const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  retryPolicy: {
    maxRetries: 3,
    backoffMs: 1000,
    backoffMultiplier: 2
  },
  circuitBreaker: {
    threshold: 5,
    timeout: 60000
  }
});
```

### Connection Pooling

```typescript
import { ConnectionPool } from '@dcmaar/connectors';

const pool = new ConnectionPool({
  minSize: 5,
  maxSize: 20,
  timeout: 30000
});

const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  connectionPool: pool
});
```

### Rate Limiting

```typescript
import { RateLimiter } from '@dcmaar/connectors';

const limiter = new RateLimiter({
  rps: 100,  // 100 requests per second
  burst: 10  // Allow burst of 10 requests
});

const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  rateLimiter: limiter
});
```

### Observability

```typescript
import { MetricsCollector } from '@dcmaar/connectors';

const metrics = new MetricsCollector({
  serviceName: 'guardian-extension',
  environment: 'production'
});

const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  metricsCollector: metrics
});
```

## Documentation

- 📖 [Browser Connectors Guide](./BROWSER_CONNECTORS.md) - Restrictions and browser usage
- 🏗️ [Architecture & Design](./docs/DESIGN_ARCHITECTURE.md)
- 📚 [Usage Examples](./docs/usage/)
- 🔧 [Operations Guide](./docs/operations/)
- 🎯 [Guidelines](./docs/guidelines/)

## Building

```bash
# Full build
pnpm build

# Watch mode
pnpm build:watch

# Development
pnpm dev

# Testing
pnpm test                    # Unit tests
pnpm test:watch             # Watch mode
pnpm test:coverage          # With coverage
pnpm test:integration       # Integration tests
pnpm test:performance       # Performance tests

# Code quality
pnpm lint                   # Check linting
pnpm lint:fix              # Fix linting issues
pnpm format                # Format code
pnpm typecheck             # Type checking

# Security
pnpm security:audit        # Audit dependencies
pnpm security:check        # Full security check
```

## Installation

```bash
# From workspace (monorepo)
pnpm add @dcmaar/connectors

# From npm
npm install @dcmaar/connectors
```

## Platform Support

- ✅ Node.js 18+ (all 10 connectors)
- ✅ Chrome Extension (4 restricted connectors)
- ✅ Firefox Extension (4 restricted connectors)
- ✅ Edge Extension (4 restricted connectors)
- ✅ Electron (all 10 connectors)
- ✅ React Native (subset with adapters)

## See Also

- [DCMAAR Architecture](../../docs/ARCHITECTURE.md)
- [Guardian Extension Integration](../browser-extension-core/)
- [Backend Services](../../../services/)

## License

MIT
