# Real-Time Collaboration — Detailed Implementation Plan

**Priority:** P1 MEDIUM  
**Current State:** 75% — CRDT framework exists, presence and cursors work, conflict resolution incomplete, fragile at scale  
**Target State:** Production-grade real-time collaboration with complete CRDT conflict resolution and AI-assisted merge suggestions  
**Estimated Effort:** 4 sprints (~28 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `CollaborationManager.ts` | `frontend/libs/collab/src/` | ✅ Top-level manager |
| `CanvasCollaboration.ts` | Same | ✅ Canvas-specific |
| `DocumentCollaboration.ts` | Same | ✅ Document-specific |
| `PresenceManager.ts` | Same | ✅ User presence tracking |
| `crdt/core/` | Same | ✅ Core CRDT types |
| `crdt/conflict-resolution/` | Same | ⚠️ Incomplete |
| `crdt/ide/` | Same | ✅ IDE-specific CRDT |
| `websocket/index.ts` | Same | ✅ WebSocket client wrapper |
| `CollaborationCursors.tsx` | Same | ✅ Live cursor rendering |
| `PresenceAvatars.tsx` | Same | ✅ User presence avatars |
| `SelectionHighlight.tsx` | Same | ✅ Selection sharing |
| `UserActivityIndicator.tsx` | Same | ✅ Activity indicators |
| `RealTimeService.ts` | `frontend/apps/api/src/services/` | ✅ Server-side service |
| `WebSocketService.ts` | Same | ✅ WebSocket server |
| `canvasCollaboration.ts` | Same | ✅ Canvas collab service |
| `BackendGateway.ts` | `frontend/apps/api/src/middleware/` | ✅ Proxy |
| `WebSocketClient.ts` (AI lib) | `frontend/libs/yappc-ai/src/realtime/` | ✅ AI lib WS client |
| AI-assisted conflict resolution | — | **MISSING** |
| Conflict resolution UI | — | **MISSING** |
| Load testing at scale | — | **MISSING** |
| Message persistence | — | ⚠️ Unclear if collab messages are durable |

### Identified Fragility Points

1. **Conflict resolution module is declared but incomplete** — concurrent edits to the same node may lose data
2. **No fallback when WebSocket disconnects** — user might continue editing without knowing they're offline
3. **Canvas sync under multiple concurrent users** — no load testing done
4. **No operation history** — can't replay operations to rebuild state after reconnection
5. **AI-assisted merge** — not implemented; conflicts require manual resolution

---

## 2. Target Architecture

```
Client A                    Server (Fastify WS)              Client B
   │                               │                             │
   │  UserJoined event             │                             │
   ├───────────────────────────────►                             │
   │                       PresenceManager.addUser()            │
   │                       Broadcast UserJoined to all          │
   │◄───────────────────────────────────────────────────────────┤
   │                               │                             │
   │  LocalOperation(Op)           │                             │
   ├───────────────────────────────►                             │
   │                       OperationBuffer.enqueue(Op)          │
   │                       CRDTEngine.apply(Op) → newState      │
   │                       BroadcastService.broadcast(Op)       │
   │                       OperationLog.append(Op, txId)        │
   │◄────────────────── RemoteOp ──────────────────────────────┤
   │                               │                             │
   │── ConflictDetected? ──────────►                             │
   │                       ConflictDetector.check(Op, state)    │
   │                       If conflict:                         │
   │                         AIConflictResolver.resolve(ops)    │
   │                         → MergedOp                        │
   │                         Broadcast MergeResolution event    │
   │◄─────── MergeResolution ──────────────────────────────────┤
```

---

## 3. CRDT Model

YAPPC uses an **operation-based CRDT** approach where:
- Every edit is an `Operation` with a unique vector clock timestamp
- Operations are commutative and idempotent when applied in any order
- Concurrent operations on the same node require conflict detection

### Operation Types

```typescript
type OperationType =
  | 'INSERT'        // Insert text/node at position
  | 'DELETE'        // Delete text/node by ID (not position — robust to concurrent inserts)
  | 'UPDATE'        // Update a property value
  | 'MOVE'          // Move a node (canvas)
  | 'ANNOTATE';     // Add annotation/comment

interface Operation {
  opId: string;                      // UUID
  type: OperationType;
  clientId: string;                  // who generated this op
  sessionId: string;
  documentId: string;
  vectorClock: Record<string, number>; // client → lamport timestamp
  payload: OperationPayload;
  createdAt: number;                  // local wall clock (for display only; ordering uses vectorClock)
}
```

### Conflict Definition

Two operations `A` and `B` **conflict** if:
1. They are concurrent (neither `A.clock ≥ B.clock` nor `B.clock ≥ A.clock`)
2. They operate on the same target (`documentId` + `targetId`)
3. They have incompatible effect types (e.g., both UPDATE the same field to different values)

---

## 4. Implementation Tasks

### Sprint 1 — Complete CRDT Conflict Resolution (8 days)

#### T1.1 — Implement Full Conflict Detection [MOD] [L]
**File:** `frontend/libs/collab/src/crdt/conflict-resolution/index.ts`

```typescript
export interface ConflictResolutionResult {
  type: 'NO_CONFLICT' | 'AUTO_RESOLVED' | 'NEEDS_AI_RESOLUTION' | 'NEEDS_USER_RESOLUTION';
  resolvedOperation?: Operation;
  conflict?: ConflictInfo;
}

export interface ConflictInfo {
  operationA: Operation;
  operationB: Operation;
  conflictType: 'CONCURRENT_UPDATE' | 'CONCURRENT_DELETE_UPDATE' | 'CANVAS_POSITION';
  description: string;
}

export function detectAndResolve(
  incomingOp: Operation,
  pendingOps: Operation[],
  currentState: DocumentState
): ConflictResolutionResult {
  const concurrent = findConcurrentOps(incomingOp, pendingOps);
  if (concurrent.length === 0) return { type: 'NO_CONFLICT' };
  
  for (const concurrentOp of concurrent) {
    if (canAutoResolve(incomingOp, concurrentOp)) {
      return { type: 'AUTO_RESOLVED', resolvedOperation: autoResolve(incomingOp, concurrentOp) };
    }
    if (requiresAI(incomingOp, concurrentOp)) {
      return { type: 'NEEDS_AI_RESOLUTION', conflict: buildConflictInfo(incomingOp, concurrentOp) };
    }
    return { type: 'NEEDS_USER_RESOLUTION', conflict: buildConflictInfo(incomingOp, concurrentOp) };
  }
}

// Auto-resolve rules:
// INSERT + INSERT at same position → sort by opId (deterministic)
// MOVE + MOVE on canvas → average the positions
// UPDATE different fields of same node → both updates applied (no conflict)
// UPDATE same field → last-writer-wins by vectorClock
function autoResolve(opA: Operation, opB: Operation): Operation { ... }
```

#### T1.2 — Implement `OperationLog` for Reconnection Replay [NEW] [M]
**File:** `frontend/apps/api/src/services/collaboration/OperationLog.ts`

Persists all operations so clients that reconnect can catch up:

```typescript
interface OperationLog {
  append(op: Operation, documentId: string): Promise<void>;
  getOpsSince(documentId: string, fromVectorClock: VectorClock): Promise<Operation[]>;
  pruneOlderThan(documentId: string, maxAge: Duration): Promise<number>;
}
```

Store in Prisma `collaboration_operations` table:

```prisma
model CollaborationOperation {
  opId        String   @id
  documentId  String
  tenantId    String
  clientId    String
  type        String
  vectorClock Json
  payload     Json
  createdAt   DateTime @default(now())
  
  @@index([documentId, createdAt])
}
```

#### T1.3 — Reconnection Replay Protocol [MOD] [M]
**File:** `frontend/libs/collab/src/websocket/index.ts`

On reconnect, client sends its last known vector clock:
```typescript
onReconnect(() => {
  ws.send({ type: 'CATCH_UP', documentId, lastVectorClock: localVectorClock });
});

onMessage((msg) => {
  if (msg.type === 'CATCH_UP_RESPONSE') {
    // Apply all missed ops in order
    for (const op of msg.missedOps) {
      crdtEngine.apply(op);
    }
  }
});
```

#### T1.4 — Offline Mode Detection [NEW] [M]
**File:** `frontend/libs/collab/src/hooks/useCollaborationStatus.ts`

```typescript
interface CollaborationStatus {
  isConnected: boolean;
  isReconnecting: boolean;
  pendingOpsCount: number;
  lastSyncedAt: Date | null;
}

export function useCollaborationStatus(documentId: string): CollaborationStatus {
  const [status, setStatus] = useState<CollaborationStatus>({
    isConnected: false,
    isReconnecting: false,
    pendingOpsCount: 0,
    lastSyncedAt: null,
  });
  // Watch WebSocket state and pending operation queue
  ...
}
```

Show offline banner when disconnected:
```typescript
{!isConnected && (
  <div role="alert" className="bg-yellow-50 border-yellow-200 ...">
    You are working offline. Changes will sync when reconnected.
    {pendingOpsCount > 0 && <span>{pendingOpsCount} change(s) pending</span>}
  </div>
)}
```

---

### Sprint 2 — AI-Assisted Conflict Resolution (7 days)

#### T2.1 — Create `AIConflictResolver` Service (BFF) [NEW] [M]
**File:** `frontend/apps/api/src/services/collaboration/AIConflictResolver.ts`

```typescript
interface ConflictResolutionSuggestion {
  suggestedResolution: Operation;
  explanation: string;
  confidence: number;
  alternativeOptions: Operation[];
}

class AIConflictResolver {
  async resolve(conflict: ConflictInfo, documentContext: string): Promise<ConflictResolutionSuggestion> {
    const prompt = `
      Two collaborators made conflicting edits to the same document.
      
      Edit A by ${conflict.operationA.clientId}:
      ${JSON.stringify(conflict.operationA.payload, null, 2)}
      
      Edit B by ${conflict.operationB.clientId}:
      ${JSON.stringify(conflict.operationB.payload, null, 2)}
      
      Document context:
      ${documentContext}
      
      Suggest the best resolution that preserves intent of both edits.
      Return JSON: {"resolution": {...merged payload...}, "explanation": "...", "confidence": 0.0-1.0}
    `;
    
    const response = await this.aiService.complete({ prompt, workflow: 'conflict_resolution' });
    return parseResolution(response);
  }
}
```

#### T2.2 — Conflict Resolution UI [NEW] [M]
**File:** `frontend/libs/collab/src/components/ConflictResolutionModal.tsx`

```typescript
interface ConflictResolutionModalProps {
  conflict: ConflictInfo;
  aiSuggestion: ConflictResolutionSuggestion | null;
  onResolve: (chosenOp: Operation) => void;
  onDismiss: () => void;
}

const ConflictResolutionModal: React.FC<ConflictResolutionModalProps> = ({
  conflict, aiSuggestion, onResolve, onDismiss
}) => {
  return (
    <Dialog>
      <DialogTitle>Conflicting Edits Detected</DialogTitle>
      <DialogBody>
        <DiffViewer before={conflict.operationA.payload} after={conflict.operationB.payload} />
        
        {aiSuggestion && (
          <section>
            <h3>AI Suggested Resolution ({(aiSuggestion.confidence * 100).toFixed(0)}% confidence)</h3>
            <DiffViewer after={aiSuggestion.suggestedResolution.payload} />
            <p>{aiSuggestion.explanation}</p>
            <button onClick={() => onResolve(aiSuggestion.suggestedResolution)}>
              Accept AI Suggestion
            </button>
          </section>
        )}
        
        <section>
          <h3>Choose manually:</h3>
          <button onClick={() => onResolve(conflict.operationA)}>Keep Edit A</button>
          <button onClick={() => onResolve(conflict.operationB)}>Keep Edit B</button>
        </section>
      </DialogBody>
    </Dialog>
  );
};
```

#### T2.3 — Wire AI Conflict Resolution into CRDT Engine [MOD] [M]
**File:** `frontend/libs/collab/src/CollaborationManager.ts`

When conflict type is `NEEDS_AI_RESOLUTION`:
1. Send conflict to BFF `/api/v1/collab/resolve-conflict`
2. Display `ConflictResolutionModal` with AI suggestion
3. Apply chosen operation; broadcast choice to all collaborators

---

### Sprint 3 — Canvas Collaboration Stability (7 days)

#### T3.1 — Canvas CRDT for Node Positioning [NEW] [L]
Canvas nodes have position (x, y) which is a common conflict target (two users drag the same node).

Auto-resolution strategy: **Weighted average** when concurrent MOVE operations detected:
```typescript
function resolveConcurrentMoves(opA: MoveOperation, opB: MoveOperation): MoveOperation {
  // If both moved the same node: average the positions
  return {
    ...opA,
    payload: {
      x: (opA.payload.x + opB.payload.x) / 2,
      y: (opA.payload.y + opB.payload.y) / 2,
    }
  };
}
```

#### T3.2 — Canvas Collaboration Load Test [NEW] [M]
**File:** `k6-tests/collab-load-test.js`

```javascript
import ws from 'k6/ws';

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // 10 concurrent collaborators
    { duration: '1m', target: 50 },    // ramp to 50
    { duration: '30s', target: 100 },  // peak: 100 concurrent
    { duration: '30s', target: 0 },    // ramp down
  ],
  thresholds: {
    'ws_session_duration': ['p95 < 100'],  // 95th percentile message roundtrip < 100ms
    'ws_msgs_received': ['count > 1000'],   // at least 1000 messages received
  },
};

export default function() {
  ws.connect(`${WS_URL}/collab/document-1`, {}, function (socket) {
    socket.on('open', () => {
      socket.send(JSON.stringify({ type: 'JOIN', clientId: __VU, documentId: 'document-1' }));
    });
    socket.on('message', (data) => {
      // Receive and validate collab ops
    });
    // Simulate random operations
    for (let i = 0; i < 10; i++) {
      socket.send(JSON.stringify(generateRandomOp()));
      sleep(Math.random() * 0.5);
    }
    socket.close();
  });
}
```

Fix any issues surfaced by the load test (expected: WebSocket message queue overflows, presence broadcasting bottlenecks).

#### T3.3 — Presence Heartbeat Optimization [MOD] [M]
**File:** `frontend/libs/collab/src/PresenceManager.ts`

Current approach likely broadcasts full presence on every cursor move. Optimize:
- **Cursor broadcasts**: throttle to 50ms intervals (20/second max)
- **Presence heartbeat**: 10-second interval (user still connected)
- **Idle detection**: after 5min no activity → mark as idle (keep in presence but dim avatar)
- **Disconnect cleanup**: remove from presence after 30s without heartbeat

---

### Sprint 4 — Scalability & Observability (6 days)

#### T4.1 — WebSocket Horizontal Scaling [MOD] [M]
**File:** `frontend/apps/api/src/services/WebSocketService.ts`

Current WebSocket service likely stores connections in memory. For horizontal scaling, use Redis pub/sub:

```typescript
// When server A receives an operation from Client A:
// 1. Apply to local state
// 2. Publish to Redis channel `collab:document:{documentId}`
// 3. Server B (holding Client B's connection) receives from Redis
// 4. Server B broadcasts to Client B

class ScalableWebSocketService {
  private redis: Redis;
  
  async broadcastOperation(documentId: string, op: Operation): Promise<void> {
    await this.redis.publish(`collab:document:${documentId}`, JSON.stringify(op));
  }
  
  subscribeToDocument(documentId: string, onOp: (op: Operation) => void): void {
    this.redis.subscribe(`collab:document:${documentId}`, (message) => {
      onOp(JSON.parse(message));
    });
  }
}
```

#### T4.2 — Collab Health Dashboard [NEW] [M]
Grafana dashboard panels:
- Active collaborative sessions count
- Operations processed per second
- Conflict detection rate (auto-resolved vs AI-resolved vs user-resolved)
- WebSocket connection duration P95
- Presence user count per document
- Operation replay success rate (reconnection health)

---

## 5. Testing Requirements

### Unit Tests

| Test | Key Scenarios |
|------|--------------|
| `ConflictDetectionTest` | No conflict; concurrent UPDATE same field; concurrent MOVE |
| `CRDTAutoResolveTest` | INSERT+INSERT sort; MOVE+MOVE average; UPDATE different fields |
| `OperationLogTest` | Append and replay; prune old ops |
| `AIConflictResolverTest` | AI returns resolution; AI fails → manual fallback |
| `PresenceManagerTest` | Add/remove users; idle timeout; heartbeat cleanup |

### Property Tests (CRDT Invariants)

```typescript
// CRDT properties that must hold for all operation sequences:
// 1. Commutativity: apply(A, apply(B, state)) == apply(B, apply(A, state))
// 2. Idempotency: apply(A, apply(A, state)) == apply(A, state)
// 3. Convergence: all clients reach same state after applying all ops in any order

test.prop([operationSetArbitrary])(
  'CRDT commutativity',
  (ops) => {
    const stateAB = applyAll([ops[0], ops[1]], emptyState);
    const stateBA = applyAll([ops[1], ops[0]], emptyState);
    expect(stateAB).toEqual(stateBA);
  }
);
```

Use `fast-check` for property-based testing.

### Load Tests

| Scenario | Threshold |
|----------|-----------|
| 100 concurrent collaborators on 1 document | P95 message latency < 100ms |
| 10 collaborators, 1000 ops each | All ops converge; 0 data loss |
| Reconnection after 30s offline | Full state recovered within 5s |

---

## 6. Observability

```
yappc_collab_active_sessions_total{document_type}             gauge
yappc_collab_operations_processed_total{type}                 counter
yappc_collab_conflicts_total{resolution_type}                 counter
yappc_collab_conflict_resolution_duration_seconds             histogram
yappc_collab_ws_connections_active                            gauge
yappc_collab_ws_message_roundtrip_seconds                     histogram
yappc_collab_presence_users_per_session                       histogram
yappc_collab_reconnection_replay_ops_count                    histogram
```
