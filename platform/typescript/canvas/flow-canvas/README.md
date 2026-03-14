# @ghatana/flow-canvas

Platform flow/topology canvas built on [`@xyflow/react`](https://reactflow.dev).

Provides ready-to-use components for the **4-tier EventCloud topology** (HOT → WARM → COLD → ARCHIVE) and the **AEP agent network** view.

---

## Install

```bash
# within the monorepo — pnpm workspace
pnpm add @ghatana/flow-canvas
```

Peer deps (already in the workspace): `react ^19`, `react-dom ^19`.

---

## Quick start

```tsx
import { FlowCanvas, useNodesState, useEdgesState } from '@ghatana/flow-canvas';
import '@xyflow/react/dist/style.css';
import type { FlowNode, FlowEdge } from '@ghatana/flow-canvas';

const initialNodes: FlowNode[] = [
  {
    id: 'hot-redis',
    type: 'hotTier',
    position: { x: 0, y: 80 },
    data: { label: 'Redis Cluster', status: 'healthy', metrics: { throughput: 42000, latencyMs: 2 } },
  },
  {
    id: 'warm-pg',
    type: 'warmTier',
    position: { x: 250, y: 80 },
    data: { label: 'PostgreSQL', status: 'healthy', metrics: { throughput: 8000 } },
  },
  {
    id: 'cold-s3',
    type: 'coldTier',
    position: { x: 500, y: 80 },
    data: { label: 'S3 Analytics', status: 'healthy', metrics: { throughput: 1200 } },
  },
  {
    id: 'archive-glacier',
    type: 'archiveTier',
    position: { x: 750, y: 80 },
    data: { label: 'Glacier Archive', status: 'inactive' },
  },
];

const initialEdges: FlowEdge[] = [
  { id: 'e1', source: 'hot-redis',  target: 'warm-pg',       type: 'dataFlow', data: { throughput: 42000 } },
  { id: 'e2', source: 'warm-pg',    target: 'cold-s3',       type: 'dataFlow', data: { throughput: 8000 } },
  { id: 'e3', source: 'cold-s3',    target: 'archive-glacier', type: 'dataFlow', data: { throughput: 120, animated: false } },
];

export function DataFabricTopology() {
  const [nodes, , onNodesChange] = useNodesState(initialNodes);
  const [edges, , onEdgesChange] = useEdgesState(initialEdges);

  return (
    // Container MUST have an explicit height
    <div style={{ height: 400, border: '1px solid #e2e8f0', borderRadius: 8 }}>
      <FlowCanvas
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        ariaLabel="EventCloud tier topology"
      />
    </div>
  );
}
```

---

## Node types

| Type          | Component       | Use case                          |
|:--------------|:----------------|:----------------------------------|
| `hotTier`     | `HotTierNode`   | In-memory / real-time ingest tier |
| `warmTier`    | `WarmTierNode`  | PostgreSQL / recent-history tier  |
| `coldTier`    | `ColdTierNode`  | Block storage / analytical tier   |
| `archiveTier` | `ArchiveTierNode` | Deep storage / compliance tier  |
| `agent`       | `AgentNode`     | AEP agent in agent network view   |

### Node data

All tier nodes accept `TierNodeData`:

```ts
interface TierNodeData {
  label: string;
  status?: 'healthy' | 'warning' | 'error' | 'inactive' | 'processing' | 'pending';
  metrics?: { throughput?: number; latencyMs?: number; errorRate?: number; };
  description?: string;
}
```

`AgentNode` accepts `AgentNodeData`:

```ts
interface AgentNodeData {
  label: string;
  agentType: string;          // e.g. 'LLM', 'RULE', 'ML'
  status?: 'active' | 'idle' | 'error' | 'training';
  capabilities?: string[];
  memoryCount?: number;
}
```

---

## Edge types

| Type       | Component       | Use case                             |
|:-----------|:----------------|:-------------------------------------|
| `dataFlow` | `DataFlowEdge`  | Animated data-flow between tier nodes |

```ts
interface DataFlowEdgeData {
  label?: string;
  throughput?: number;   // events/sec — shown as label
  animated?: boolean;    // default true
}
```

---

## FlowCanvas props

| Prop                  | Type                     | Default       | Description                                     |
|:----------------------|:-------------------------|:--------------|:------------------------------------------------|
| `nodes`               | `FlowNode[]`             | required      | Nodes to render                                 |
| `edges`               | `FlowEdge[]`             | required      | Edges to render                                 |
| `onNodesChange`       | `OnNodesChange`          | —             | Node change handler (controlled mode)           |
| `onEdgesChange`       | `OnEdgesChange`          | —             | Edge change handler (controlled mode)           |
| `onConnect`           | `OnConnect`              | —             | New connection handler                          |
| `nodesDraggable`      | `boolean`                | `true`        | Allow user to drag nodes                        |
| `additionalNodeTypes` | `Record<string, ...>`    | —             | Extra node types merged with built-ins          |
| `additionalEdgeTypes` | `Record<string, ...>`    | —             | Extra edge types merged with built-ins          |
| `controls`            | `FlowControlsProps\|false` | `{}`        | Controls config; `false` hides controls         |
| `showBackground`      | `boolean`                | `true`        | Show dot-grid background                        |
| `className`           | `string`                 | —             | CSS class on root container                     |
| `style`               | `CSSProperties`          | —             | Inline style (add explicit `height`)            |
| `ariaLabel`           | `string`                 | `'Flow diagram canvas'` | Accessibility label            |
