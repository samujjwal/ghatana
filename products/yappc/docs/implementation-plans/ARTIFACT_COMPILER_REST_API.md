# YAPPC Artifact Compiler — REST API Endpoints

## Base Path

All artifact compiler routes are prefixed under `/api/v1/yappc/artifact`.

## Authentication

All endpoints (except health checks) require a valid `X-API-Key` header and the `X-API-Version: v1` header.

## Endpoints

### `POST /api/v1/yappc/artifact/graph/ingest`

Ingest an artifact graph (nodes and edges) extracted by the TypeScript scanner into the graph store.

**Request Body**
```json
{
  "productId": "string",
  "tenantId": "string",
  "nodes": [
    {
      "id": "string",
      "type": "string",
      "name": "string",
      "filePath": "string",
      "content": "string",
      "properties": {},
      "tags": ["string"],
      "tenantId": "string",
      "projectId": "string"
    }
  ],
  "edges": [
    {
      "sourceNodeId": "string",
      "targetNodeId": "string",
      "relationshipType": "string",
      "properties": {}
    }
  ]
}
```

**Response**
```json
{
  "success": true,
  "operation": "ingest",
  "result": {
    "nodeCount": 42,
    "edgeCount": 128,
    "versionId": "uuid"
  },
  "message": "Artifact graph ingested and versioned successfully"
}
```

---

### `POST /api/v1/yappc/artifact/graph/analyze`

Run graph analysis algorithms on an artifact graph.

**Request Body**
```json
{
  "productId": "string",
  "tenantId": "string",
  "algorithmTypes": ["centrality", "cycles", "topological", "communities", "reachability"],
  "nodeIds": ["optional-seed-node-ids"]
}
```

**Response** — List of algorithm results
```json
[
  {
    "algorithm": "betweenness-centrality",
    "centralityScores": { "node-1": 0.5, "node-2": 0.25 }
  },
  {
    "algorithm": "strongly-connected-components",
    "cycles": [["a", "b", "c"]]
  },
  {
    "algorithm": "topological-order",
    "topologicalOrder": ["node-1", "node-2", "node-3"]
  },
  {
    "algorithm": "greedy-communities",
    "communities": [["node-1", "node-2"], ["node-3"]],
    "metadata": { "communityCount": 2 }
  },
  {
    "algorithm": "reachability",
    "metadata": { "maxPathLength5": 42 }
  }
]
```

---

### `POST /api/v1/yappc/artifact/graph/merge`

Perform a three-way semantic merge of artifact models.

**Request Body**
```json
{
  "productId": "string",
  "tenantId": "string",
  "baseModel": {},
  "leftModel": {},
  "rightModel": {},
  "resolutionStrategy": "auto-resolve"
}
```

Supported strategies: `left-wins`, `right-wins`, `union`, `auto-resolve`, `manual-review`, `longest`.

**Response**
```json
{
  "success": true,
  "operation": "merge",
  "result": {
    "mergedModel": {},
    "conflicts": [
      {
        "fieldPath": "properties.name",
        "conflictType": "field-level",
        "baseValue": "Base",
        "leftValue": "Left",
        "rightValue": "Right"
      }
    ],
    "fieldProvenance": { "properties.name": "left(strategy=auto-resolve)" },
    "conflictCount": 1
  },
  "message": "Three-way merge completed with 1 conflicts and versioned"
}
```

---

### `POST /api/v1/yappc/artifact/graph/query`

Query the artifact graph for orphaned nodes, dependencies, dependents, or stats.

**Request Body**
```json
{
  "productId": "string",
  "tenantId": "string",
  "queryType": "orphaned",
  "seedIds": ["node-a", "node-b"]
}
```

**Response**
```json
{
  "orphanedNodes": ["node-1", "node-2"],
  "dependencies": { "node-a": ["node-b", "node-c"] },
  "dependents": { "node-b": ["node-a"] },
  "nodeCount": 42,
  "edgeCount": 128,
  "nodeTypeDistribution": { "component": 10, "page": 5 }
}
```

Only the field matching the `queryType` is populated in the response.

---

### `POST /api/v1/yappc/artifact/residual/analyze`

Analyze residual islands flagged by the TypeScript scanner (blocks that could not be extracted).

**Request Body**
```json
{
  "productId": "string",
  "tenantId": "string",
  "residualIslands": [
    {
      "id": "string",
      "kind": "string",
      "sourceLocations": [],
      "confidence": 0.0,
      "regenerationStrategy": "string"
    }
  ]
}
```

**Response**
```json
{
  "success": true,
  "operation": "residual-analysis",
  "result": {
    "islands": [
      {
        "id": "string",
        "analysisStatus": "analyzed",
        "recommendation": "Manual review or AST upgrade required"
      }
    ],
    "count": 1
  },
  "message": "Residual islands analyzed"
}
```

---

## Error Responses

- `400 Bad Request` — Missing required parameters or invalid JSON
- `401 Unauthorized` — Missing or invalid API key
- `406 Not Acceptable` — Unsupported API version
- `500 Internal Server Error` — Server-side processing failure
