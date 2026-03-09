# WebSocket Migration Guide: @ghatana/realtime

> **Document Version:** 1.0.0  
> **Created:** 2025-12-03  
> **Status:** ✅ COMPLETE  
> **Migration Priority:** High

---

## Overview

This guide documents the migration from custom WebSocket implementation to the platform-standard `@ghatana/realtime` library, following the reuse-first policy from `.github/copilot-instructions.md`.

---

## Why Migrate?

### Benefits of @ghatana/realtime

✅ **Production-Grade**: Battle-tested WebSocket client with automatic reconnection  
✅ **Type-Safe**: Full TypeScript support with generic message types  
✅ **State Management**: Built-in connection state tracking  
✅ **Message Queueing**: Automatic message queueing during disconnections  
✅ **Heartbeat Monitoring**: Automatic connection health checks  
✅ **Exponential Backoff**: Smart reconnection with exponential backoff  
✅ **Platform Standard**: Consistent with other Ghatana products  
✅ **Well-Documented**: Comprehensive JSDoc and examples  

### Issues with Custom Implementation

❌ **Duplicate Code**: Reimplements functionality available in platform library  
❌ **Limited Features**: Missing message queueing and advanced reconnection  
❌ **Maintenance Burden**: Requires ongoing maintenance and bug fixes  
❌ **Inconsistency**: Different patterns across products  

---

## Migration Steps

### Step 1: Add Dependency

**File:** `apps/web/package.json`

```json
{
  "dependencies": {
    "@ghatana/realtime": "workspace:*"
  }
}
```

**Command:**
```bash
pnpm install
```

---

### Step 2: Import New Hook

**Before:**
```typescript
import { useWebSocket, useHitlWebSocket } from '@/hooks/useWebSocket';
```

**After:**
```typescript
import { 
  useRealtimeWebSocket, 
  useHitlRealtimeWebSocket 
} from '@/hooks/useRealtimeWebSocket';
```

---

### Step 3: Update Hook Usage

#### Basic WebSocket

**Before:**
```typescript
const { data, isConnected, error } = useWebSocket<MyData>(
  'ws://api.example.com/stream',
  'message-type',
  (message) => console.log('Received:', message)
);
```

**After:**
```typescript
const { data, isConnected, error } = useRealtimeWebSocket<MyData>({
  url: 'ws://api.example.com/stream',
  messageType: 'message-type',
  onMessage: (message) => console.log('Received:', message)
});
```

#### HITL WebSocket

**Before:**
```typescript
const { actions, isConnected, error } = useHitlWebSocket();
```

**After:**
```typescript
const { actions, isConnected, error } = useHitlRealtimeWebSocket();
```

---

### Step 4: Update Type Imports

**Before:**
```typescript
import { WebSocketMessage, WebSocketState } from '@/hooks/useWebSocket';
```

**After:**
```typescript
import { 
  WebSocketMessage, 
  WebSocketConnectionState 
} from '@ghatana/realtime';
```

---

## API Comparison

### Hook Options

| Feature | Old API | New API |
|---------|---------|---------|
| URL | `url` (positional) | `options.url` |
| Message Type | `messageType` (positional) | `options.messageType` |
| Callback | `onMessage` (positional) | `options.onMessage` |
| Reconnect Attempts | Not configurable | `options.maxReconnectAttempts` |
| Reconnect Delay | Not configurable | `options.reconnectDelay` |
| Heartbeat Interval | Fixed 30s | `options.heartbeatInterval` |
| Connection Timeout | Not configurable | `options.connectionTimeout` |
| Auto-connect | Always true | `options.autoConnect` |

### Return Values

| Feature | Old API | New API |
|---------|---------|---------|
| Data | `data` | `data` |
| Connected | `isConnected` | `isConnected` |
| Loading | `isLoading` | `isConnecting` |
| Error | `error` (string) | `error` (Error object) |
| Last Update | `lastUpdate` (string) | `lastUpdate` (Date) |
| Send | Not available | `send(message)` |
| Connect | Not available | `connect()` |
| Disconnect | Not available | `disconnect()` |
| Connection State | Not available | `connectionState` |
| Reconnecting | Not available | `isReconnecting` |

---

## Migration Examples

### Example 1: HITL Console

**File:** `features/hitl/HitlConsole.tsx`

**Before:**
```typescript
import { useHitlWebSocket } from '@/hooks/useWebSocket';

function HitlConsole() {
  const { actions, isConnected, error } = useHitlWebSocket();

  if (!isConnected) {
    return <div>Connecting...</div>;
  }

  if (error) {
    return <div>Error: {error}</div>;
  }

  return <ActionList actions={actions} />;
}
```

**After:**
```typescript
import { useHitlRealtimeWebSocket } from '@/hooks/useRealtimeWebSocket';

function HitlConsole() {
  const { actions, isConnected, error } = useHitlRealtimeWebSocket();

  if (!isConnected) {
    return <div>Connecting...</div>;
  }

  if (error) {
    return <div>Error: {error.message}</div>;
  }

  return <ActionList actions={actions} />;
}
```

---

### Example 2: Department Events

**File:** `hooks/useDepartmentEvents.ts`

**Before:**
```typescript
import { useWebSocket } from './useWebSocket';

export function useDepartmentEvents(departmentId: string) {
  const wsUrl = `ws://localhost:8080/api/v1/departments/${departmentId}/stream`;
  
  const { data, isConnected, error } = useWebSocket<DepartmentEvent[]>(
    wsUrl,
    'department-event'
  );

  return {
    events: data || [],
    isConnected,
    error,
  };
}
```

**After:**
```typescript
import { useDepartmentRealtimeWebSocket } from './useRealtimeWebSocket';

export function useDepartmentEvents(departmentId: string) {
  const { events, isConnected, error } = useDepartmentRealtimeWebSocket(departmentId);

  return {
    events,
    isConnected,
    error,
  };
}
```

---

### Example 3: Real-Time Metrics

**File:** `features/monitoring/hooks/useRealTimeMetrics.ts`

**Before:**
```typescript
import { useWebSocket } from '@/hooks/useWebSocket';

export function useRealTimeMetrics() {
  const wsUrl = 'ws://localhost:8080/api/v1/metrics/stream';
  
  const { data, isConnected } = useWebSocket<MetricsData>(
    wsUrl,
    'metrics-update'
  );

  return {
    metrics: data || {},
    isConnected,
  };
}
```

**After:**
```typescript
import { useMetricsRealtimeWebSocket } from '@/hooks/useRealtimeWebSocket';

export function useRealTimeMetrics() {
  const { metrics, isConnected, error } = useMetricsRealtimeWebSocket();

  return {
    metrics,
    isConnected,
    error,
  };
}
```

---

### Example 4: Custom WebSocket with Advanced Options

**Before:**
```typescript
import { useWebSocket } from '@/hooks/useWebSocket';

function MyComponent() {
  const { data, isConnected } = useWebSocket<MyData>(
    'ws://api.example.com/stream',
    'my-message-type'
  );

  // No control over reconnection behavior
}
```

**After:**
```typescript
import { useRealtimeWebSocket } from '@/hooks/useRealtimeWebSocket';

function MyComponent() {
  const { data, isConnected, send, disconnect } = useRealtimeWebSocket<MyData>({
    url: 'ws://api.example.com/stream',
    messageType: 'my-message-type',
    maxReconnectAttempts: 10,  // Custom reconnection
    reconnectDelay: 2000,       // 2 second base delay
    heartbeatInterval: 60000,   // 1 minute heartbeat
    autoConnect: true,          // Auto-connect on mount
  });

  // Can manually send messages
  const handleSend = () => {
    send({ type: 'my-action', payload: { data: 'test' } });
  };

  // Can manually disconnect
  const handleDisconnect = () => {
    disconnect();
  };
}
```

---

## Files to Migrate

### High Priority (Core Features)

1. ✅ `hooks/useWebSocket.ts` - Deprecated with migration guide
2. ⏳ `features/hitl/HitlConsole.tsx` - HITL action stream
3. ⏳ `hooks/useDepartmentEvents.ts` - Department event stream
4. ⏳ `features/monitoring/hooks/useRealTimeMetrics.ts` - Metrics stream
5. ⏳ `features/monitoring/hooks/useMonitoringOrchestration.ts` - Monitoring orchestration

### Medium Priority (Secondary Features)

6. ⏳ `lib/hooks/usePersonaSync.ts` - Persona synchronization
7. ⏳ `hooks/useAudit.ts` - Audit log stream
8. ⏳ `services/api/workflowsApi.ts` - Workflow execution stream
9. ⏳ `services/api/workItemsApi.ts` - Work item updates

### Low Priority (Optional Features)

10. ⏳ `services/api/monitoringApi.ts` - Monitoring API
11. ⏳ Test files using WebSocket mocks

---

## Testing Strategy

### Unit Tests

```typescript
import { renderHook, waitFor } from '@testing-library/react';
import { useRealtimeWebSocket } from './useRealtimeWebSocket';

describe('useRealtimeWebSocket', () => {
  it('should connect and receive messages', async () => {
    const { result } = renderHook(() =>
      useRealtimeWebSocket({
        url: 'ws://localhost:8080/test',
        messageType: 'test-message',
      })
    );

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Test message handling
    // ...
  });
});
```

### Integration Tests

```typescript
describe('HITL Console Integration', () => {
  it('should display real-time actions', async () => {
    render(<HitlConsole />);

    await waitFor(() => {
      expect(screen.getByText('Connecting...')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('Action 1')).toBeInTheDocument();
    });
  });
});
```

---

## Rollback Plan

If issues arise during migration, the old implementation is still available:

1. **Keep Old Hooks**: `useWebSocket.ts` is deprecated but functional
2. **Gradual Migration**: Migrate one feature at a time
3. **Feature Flags**: Use environment variables to toggle implementations
4. **Testing**: Thoroughly test each migrated feature before proceeding

---

## Common Issues & Solutions

### Issue 1: Connection Errors

**Problem:** WebSocket fails to connect after migration

**Solution:**
```typescript
const { isConnected, error, connect } = useRealtimeWebSocket({
  url: wsUrl,
  maxReconnectAttempts: 5,
  connectionTimeout: 15000, // Increase timeout
});

// Manual retry
useEffect(() => {
  if (error && !isConnected) {
    console.error('Connection error:', error);
    // Optionally retry
    setTimeout(() => connect(), 5000);
  }
}, [error, isConnected, connect]);
```

### Issue 2: Message Format Mismatch

**Problem:** Messages not being received after migration

**Solution:** Ensure message format matches @ghatana/realtime expectations:
```typescript
// Server should send:
{
  type: 'message-type',
  payload: { /* your data */ },
  timestamp: 1234567890,
  id: 'unique-id'
}
```

### Issue 3: Multiple Connections

**Problem:** Multiple WebSocket connections being created

**Solution:** Use singleton pattern or context:
```typescript
// Create a WebSocket context
const WebSocketContext = createContext<WebSocketClient | null>(null);

// Provide at app level
function App() {
  const client = useMemo(() => new WebSocketClient({ url: wsUrl }), [wsUrl]);
  
  return (
    <WebSocketContext.Provider value={client}>
      {children}
    </WebSocketContext.Provider>
  );
}
```

---

## Verification Checklist

After migration, verify:

- [ ] All WebSocket connections work correctly
- [ ] Reconnection behavior is as expected
- [ ] Message handling is correct
- [ ] Error handling works properly
- [ ] No console errors or warnings
- [ ] Performance is acceptable
- [ ] All tests pass
- [ ] Documentation is updated

---

## Next Steps

1. ✅ Add @ghatana/realtime dependency
2. ✅ Create new useRealtimeWebSocket hook
3. ✅ Deprecate old useWebSocket hook
4. ⏳ Migrate HITL Console
5. ⏳ Migrate Department Events
6. ⏳ Migrate Real-Time Metrics
7. ⏳ Migrate remaining features
8. ⏳ Remove deprecated code (after 2 sprints)

---

## Resources

- **@ghatana/realtime Documentation**: `libs/typescript/realtime/README.md`
- **WebSocket Client Source**: `libs/typescript/realtime/src/client.ts`
- **React Hooks Source**: `libs/typescript/realtime/src/hooks/useWebSocket.ts`
- **Enhancement Plan**: `docs/SOFTWARE_ORG_ANALYSIS_AND_ENHANCEMENT_PLAN.md`
- **Coding Standards**: `.github/copilot-instructions.md`

---

**Last Updated**: 2025-12-03  
**Migration Status**: Phase 1.2 Complete (Hook created, old hooks deprecated)  
**Next Review**: 2025-12-04
