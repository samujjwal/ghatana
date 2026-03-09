# Browser Connectors Configuration

## Restricted Connector Types

The browser build (`index.browser.ts`) is restricted to **4 core connector types** only:

| Connector | Type | Transport | Use Case |
|-----------|------|-----------|----------|
| **Http** | `'http'` | `fetch()` API | REST/JSON backends, WebAPIs |
| **WebSocket** | `'websocket'` | Native browser WebSocket | Real-time bidirectional communication |
| **FileSystem** | `'filesystem'` | Chrome Storage API | Extension local data (Chrome Storage) |
| **Native** | `'native'` | `chrome.*` APIs | Browser extension APIs |

## Factory Function

```typescript
import { createConnector } from '@dcmaar/connectors';

// ✅ Valid - browser connectors only
const http = createConnector({ type: 'http', url: 'https://api.example.com' });
const ws = createConnector({ type: 'websocket', url: 'wss://api.example.com' });
const fs = createConnector({ type: 'filesystem', storageKey: 'extension-data' });
const native = createConnector({ type: 'native', api: 'chrome.alarms' });

// ❌ Invalid - Node.js/external lib connectors rejected
// createConnector({ type: 'grpc' });      // Not in browser
// createConnector({ type: 'mqtt' });      // Requires external mqtt.js
// createConnector({ type: 'nats' });      // Requires external nats.js
// createConnector({ type: 'ipc' });       // Node.js only
// createConnector({ type: 'mtls' });      // Requires fs/tls
// createConnector({ type: 'mqtts' });     // Requires fs/tls
```

## Tree-Shaking Benefits

- **Minimal bundle**: Only 4 connector implementations included
- **Unused code eliminated**: Other 6 connectors completely removed by bundler
- **No external dependencies**: No Kafka, gRPC-web, mqtt.js, nats.js added
- **Fast build time**: Fewer modules to transform and optimize

## Exported Utilities

Core utilities included (tree-shaken if unused):

```typescript
// Resilience
export { CircuitBreaker, RetryPolicy };

// Connection Management
export { ConnectionPool };

// Observability
export { MetricsCollector, RateLimiter };

// Error Types
export { ConnectorError, ConnectionError, TimeoutError };
```

## Configuration Types

All 4 connector types share the base `ConnectionOptions`:

```typescript
interface ConnectionOptions {
  type: 'http' | 'websocket' | 'filesystem' | 'native';
  retryPolicy?: RetryPolicy;
  circuitBreaker?: CircuitBreaker;
  connectionPool?: ConnectionPool;
  metricsCollector?: MetricsCollector;
  rateLimiter?: RateLimiter;
}
```

## Migration from Full Connectors

If upgrading from unrestricted connectors, update code:

```typescript
// ❌ Before (may fail in browser)
import { createConnector } from '@dcmaar/connectors';
const connector = createConnector(config);

// ✅ After (guaranteed browser-safe)
import { createConnector } from '@dcmaar/connectors'; // Resolves to index.browser.ts
const connector = createConnector({ type: 'http', ... });
```

## Node.js vs Browser

| Scenario | Use | Notes |
|----------|-----|-------|
| Node.js backend | `@dcmaar/connectors` (main export) | All 10 connectors available |
| Browser/Extension | `@dcmaar/connectors` (browser field) | Restricted to 4 types, tree-shaken |
| Build tool resolves | Vite/webpack `browser` field | Automatically uses index.browser.ts |

## See Also

- [Connectors API Documentation](./README.md)
- [Browser Extension Integration](../../browser-extension-core/README.md)
- [Vite Configuration](../../../apps/guardian/apps/browser-extension/vite.config.ts)
