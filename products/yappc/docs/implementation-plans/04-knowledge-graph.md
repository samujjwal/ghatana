# Knowledge Graph & Semantic Layer — Detailed Implementation Plan

**Priority:** P1 HIGH  
**Current State:** Thin — `KnowledgeGraph`, `YAPPCGraphService`, `YAPPCGraphMapper` exist with 1 test; no persistence at scale; no cross-project sharing; no continuous update  
**Target State:** Production-scale, continuously evolving knowledge graph with AI-powered entity discovery and cross-project intelligence  
**Estimated Effort:** 5 sprints (~40 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `KnowledgeGraph.java` | `core/knowledge-graph/src/.../kg/core/` | ✅ Interface/core model |
| `KnowledgeGraphEdge.java` | Same | ✅ Edge model |
| `KnowledgeGraphNode.java` | Same | ✅ Node model |
| `YAPPCGraphService.java` | `core/knowledge-graph/src/.../yappc/knowledge/` | ✅ YAPPC-specific service |
| `YAPPCGraphMapper.java` | Same | ✅ Domain → graph mapper |
| `YAPPCGraphValidator.java` | Same | ✅ Validation |
| `YAPPCGraphNode.java` | Same | ✅ Node model |
| `YAPPCGraphEdge.java` | Same | ✅ Edge model |
| `YAPPCGraphMetadata.java` | Same | ✅ Metadata |
| `YAPPCImpactAnalysis.java` | Same | ✅ Impact analysis |
| `KgCli.java` | `core/cli-tools/` | ✅ CLI tool (`kg search`) |
| `YAPPCGraphServiceTest.java` | `src/test/java/` | ✅ 1 test |
| `useSemanticSearch.ts` | `frontend/libs/yappc-ai/src/hooks/` | ✅ Semantic search hook |
| Persistence at scale | — | **MISSING** — in-memory only |
| Continuous update pipeline | — | **MISSING** |
| Cross-project sharing | — | **MISSING** |
| Entity embedding store | — | **MISSING** |
| Real-time graph update on events | — | **MISSING** |

### Scale Limitation

The current KG is in-memory, which breaks at:
- > 10,000 nodes (OOM risk)
- Multi-tenant (graphs mixed in memory)
- Service restart (graph lost)

---

## 2. Target Architecture

```
Event Sources
  ├── Code commits (via GitHub webhook / AEP)
  ├── Requirement CRUD events
  ├── Phase transitions
  ├── AI outputs (generated code, decisions)
  └── Documentation changes
       │
       ▼
KnowledgeGraphUpdatePipeline (AEP-driven, event-sourced)
  ├── EntityExtractor (AI-powered)
  ├── RelationshipDetector (AI-powered)
  ├── EmbeddingGenerator (VectorSearchService)
  └── ConflictResolver (deduplication)
       │
       ▼
KnowledgeGraphStore
  ├── GraphRepository (JDBC adjacency + JSONB metadata)
  ├── VectorIndex (pgvector or Qdrant sidecar)
  └── SemanticCacheService (for repeated queries)
       │
       ▼
QueryLayer
  ├── KGQueryService (graph traversal queries)
  ├── SemanticSearchService (vector similarity search)
  └── ImpactAnalysisService (what changes if X changes?)
       │
       ▼
Frontend (GraphQL BFF)
  ├── useSemanticSearch hook
  ├── KnowledgeGraphViewer component
  └── ImpactRadar component
```

---

## 3. Data Model

### JDBC Graph Storage

```sql
-- V007__knowledge_graph_tables.sql

CREATE TABLE kg_nodes (
    node_id       VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL,
    project_id    VARCHAR(36),          -- null = cross-project (shared)
    node_type     VARCHAR(50)  NOT NULL, -- REQUIREMENT | CODE_MODULE | CONCEPT | DECISION | PERSON | TOOL
    label         TEXT         NOT NULL,
    properties    JSONB,
    embedding     vector(1536),         -- pgvector; null if not yet embedded
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    source        VARCHAR(100),         -- git_commit | requirement | ai_generated
    source_ref    TEXT                  -- commit hash / requirement ID / etc.
);

CREATE TABLE kg_edges (
    edge_id       VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL,
    from_node_id  VARCHAR(36)  NOT NULL REFERENCES kg_nodes(node_id) ON DELETE CASCADE,
    to_node_id    VARCHAR(36)  NOT NULL REFERENCES kg_nodes(node_id) ON DELETE CASCADE,
    edge_type     VARCHAR(50)  NOT NULL, -- DEPENDS_ON | IMPLEMENTS | TESTS | CONFLICTS_WITH | INHERITS
    weight        DECIMAL(5,3) DEFAULT 1.0,
    properties    JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kg_nodes_tenant_type ON kg_nodes(tenant_id, node_type);
CREATE INDEX idx_kg_nodes_project ON kg_nodes(project_id, tenant_id);
CREATE INDEX idx_kg_edges_from ON kg_edges(from_node_id);
CREATE INDEX idx_kg_edges_to ON kg_edges(to_node_id);
CREATE INDEX idx_kg_nodes_embedding ON kg_nodes USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### Node Types

| Type | Example | Auto-Updated By |
|------|---------|----------------|
| `REQUIREMENT` | "User can log in with Google" | Requirements API |
| `CODE_MODULE` | `core/ai/AIModelRouter.java` | Git commit webhook |
| `CONCEPT` | "Authentication", "Phase Transition" | AI extraction |
| `DECISION` | ADR-001 Service Architecture | ADR parser |
| `PERSON` | Developer role node | Team member action |
| `TEST` | `JwtAuthFilterTest` | CI event |
| `TOOL` | "Ollama", "ActiveJ" | Config parsing |

### Edge Types

| Type | Meaning | Example |
|------|---------|---------|
| `IMPLEMENTS` | Code module implements requirement | `AIModelRouter → LLM_Integration_Requirement` |
| `DEPENDS_ON` | Module depends on another | `ApprovalService → ApprovalRepository` |
| `TESTS` | Test covers module | `JwtAuthFilterTest → JwtAuthFilter` |
| `DECIDED_BY` | Decision governs component | `ADR-001 → ServiceArchitecture` |
| `CONFLICTS_WITH` | Two requirements conflict | `RequirementA ↔ RequirementB` |
| `EVOLVES_FROM` | Requirement evolved | `Req_v2 → Req_v1` |
| `SIMILAR_TO` | Semantic similarity | `feature_A ~ feature_B` (from embedding distance) |

---

## 4. Implementation Tasks

### Sprint 1 — Persistent Graph Store (8 days)

#### T1.1 — Create `KGNodeRepository` Interface [NEW] [M]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/repository/KGNodeRepository.java`

```java
public interface KGNodeRepository {
    Promise<KGNode> save(KGNode node);
    Promise<Optional<KGNode>> findById(String nodeId, String tenantId);
    Promise<List<KGNode>> findByType(String nodeType, String tenantId, int limit);
    Promise<List<KGNode>> findByProject(String projectId, String tenantId);
    Promise<List<KGNode>> findSimilar(float[] embedding, String tenantId, int limit, float threshold);
    Promise<KGNode> update(KGNode node);
    Promise<Void> delete(String nodeId, String tenantId);
    Promise<Integer> countByTenant(String tenantId);
}
```

#### T1.2 — Implement `JdbcKGNodeRepository` [NEW] [L]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/repository/JdbcKGNodeRepository.java`

Key implementation details:
- Use `platform:java:database` ActiveJ JDBC
- `findSimilar()` uses pgvector `<=>` cosine distance operator:
  ```sql
  SELECT * FROM kg_nodes 
  WHERE tenant_id = ? AND embedding IS NOT NULL
  ORDER BY embedding <=> ?::vector 
  LIMIT ?
  ```
- Tenant isolation on every query (all queries include `WHERE tenant_id = ?`)
- Implement `JdbcKGEdgeRepository` similarly

#### T1.3 — Migrate `YAPPCGraphService` to Use Repository [MOD] [M]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/YAPPCGraphService.java`

Replace any in-memory graph with `KGNodeRepository` + `KGEdgeRepository` calls.

---

### Sprint 2 — AI Entity Extraction Pipeline (9 days)

#### T2.1 — Create `EntityExtractor` [NEW] [L]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/extraction/EntityExtractor.java`

```java
/**
 * @doc.type class
 * @doc.purpose Uses AI to extract named entities and concepts from text documents.
 * @doc.layer product
 * @doc.pattern Extractor
 */
public final class EntityExtractor {
    private final YAPPCAIService aiService;
    
    public Promise<List<ExtractedEntity>> extract(String text, String sourceType) {
        String prompt = """
            Extract key entities and concepts from this %s content.
            For each entity, identify: name, type, description, relationships to other entities.
            
            Content:
            %s
            
            Return JSON array:
            [{"name": "...", "type": "REQUIREMENT|CODE_MODULE|CONCEPT|DECISION", 
              "description": "...", "relations": [{"target": "...", "type": "IMPLEMENTS|DEPENDS_ON|..."}]}]
            """.formatted(sourceType, text);
        
        return aiService.complete(AIRequest.of(prompt).withWorkflow("entity_extraction"))
            .map(response -> parseEntities(response));
    }
}
```

#### T2.2 — Create `KGUpdatePipeline` [NEW] [L]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/pipeline/KGUpdatePipeline.java`

Event-driven pipeline triggered by AEP events:

```java
/**
 * @doc.type class
 * @doc.purpose Processes domain events and updates the knowledge graph with extracted entities and relationships.
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public final class KGUpdatePipeline {
    private final EntityExtractor extractor;
    private final EmbeddingGenerator embeddingGenerator;
    private final KGNodeRepository nodeRepository;
    private final KGEdgeRepository edgeRepository;
    private final KGConflictResolver conflictResolver;
    
    public Promise<KGUpdateResult> process(DomainEvent event) {
        return eventToText(event)
            .then(text -> extractor.extract(text, event.sourceType()))
            .then(entities -> deduplicateAndResolve(entities, event.tenantId()))
            .then(resolved -> generateEmbeddings(resolved))
            .then(withEmbeddings -> persistAll(withEmbeddings, event));
    }
    
    private Promise<String> eventToText(DomainEvent event) {
        // Converts RequirementCreated, CodeCommitted, PhaseTransitioned, etc. to text
        return Promise.of(eventTextConverter.convert(event));
    }
}
```

#### T2.3 — Register Pipeline as AEP Event Consumer [MOD] [M]
**File:** `config/pipelines/lifecycle-management-v1.yaml` [MOD]

Add KG update as a downstream action for:
- `RequirementCreated`, `RequirementUpdated`
- `PhaseTransitioned`
- `CodeCommitted` (from Git webhook)
- `ApprovalDecided`
- `AgentTaskCompleted`

#### T2.4 — Create `EmbeddingGenerator` [NEW] [M]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/embedding/EmbeddingGenerator.java`

```java
public final class EmbeddingGenerator {
    private final VectorSearchService vectorService;
    
    public Promise<float[]> generate(String text) {
        return vectorService.embed(text)
            .exceptionally(ex -> new float[1536]);  // zero vector as fallback; scheduled retry later
    }
}
```

Track nodes with `embedding IS NULL` — scheduled job retries embedding generation.

---

### Sprint 3 — Query Layer & Impact Analysis (8 days)

#### T3.1 — Create `KGQueryService` [NEW] [M]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/query/KGQueryService.java`

```java
public final class KGQueryService {
    /** Find all nodes reachable from a starting node within N hops. */
    public Promise<List<KGNode>> traverse(String nodeId, int maxHops, String tenantId) { ... }
    
    /** Semantic search: find nodes by meaning, not exact name. */
    public Promise<List<ScoredNode>> semanticSearch(String query, String tenantId, int limit) { ... }
    
    /** Find all paths between two nodes (for traceability). */
    public Promise<List<List<KGNode>>> findPaths(String fromNodeId, String toNodeId, String tenantId) { ... }
}
```

#### T3.2 — Enhance `YAPPCImpactAnalysis` [MOD] [L]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/YAPPCImpactAnalysis.java`

Implement true graph-traversal impact analysis:

```java
/**
 * Given a change to node X, discover all downstream nodes that may be affected.
 * 
 * Algorithm: BFS from X following IMPLEMENTS, DEPENDS_ON, TESTS edges
 * Returns: ImpactReport with affected nodes ranked by proximity and edge weight
 */
public Promise<ImpactReport> analyze(String changedNodeId, String tenantId) {
    return kgQueryService.traverse(changedNodeId, 3, tenantId)   // max 3 hops
        .then(reachable -> {
            List<AffectedNode> affected = reachable.stream()
                .map(node -> new AffectedNode(node, computeImpactScore(changedNodeId, node)))
                .sorted(Comparator.comparingDouble(AffectedNode::score).reversed())
                .toList();
            return Promise.of(new ImpactReport(changedNodeId, affected));
        });
}
```

#### T3.3 — Expose Impact Analysis via BFF GraphQL [NEW] [M]
**File:** `frontend/apps/api/src/graphql/schemas/knowledge-graph.graphql` [NEW]

```graphql
type KGNode {
  nodeId: ID!
  nodeType: String!
  label: String!
  properties: JSON
  projectId: String
  createdAt: DateTime!
}

type AffectedNode {
  node: KGNode!
  impactScore: Float!
  impactPath: [String!]!
  explanation: String
}

type ImpactReport {
  changedNode: KGNode!
  affectedNodes: [AffectedNode!]!
  totalAffected: Int!
  highImpactCount: Int!
}

type Query {
  semanticSearch(query: String!, tenantId: String!, limit: Int): [KGNode!]!
  impactAnalysis(nodeId: ID!): ImpactReport!
  knowledgeGraph(projectId: String, limit: Int): [KGNode!]!
  nodeTraversal(nodeId: ID!, maxHops: Int): [KGNode!]!
}
```

---

### Sprint 4 — Frontend Visualization (8 days)

#### T4.1 — Knowledge Graph Viewer Component [NEW] [L]
**File:** `frontend/libs/yappc-canvas/src/components/KnowledgeGraphViewer.tsx`

Interactive graph visualization using D3.js or Cytoscape.js (already used in canvas):

```typescript
interface KnowledgeGraphViewerProps {
  projectId?: string;
  tenantId: string;
  onNodeSelect?: (node: KGNode) => void;
  onImpactAnalysisRequest?: (nodeId: string) => void;
  highlightNodeIds?: string[];
}

const KnowledgeGraphViewer: React.FC<KnowledgeGraphViewerProps> = ({
  projectId, tenantId, onNodeSelect, onImpactAnalysisRequest, highlightNodeIds
}) => {
  const { data: nodes, isLoading } = useQuery({
    queryKey: ['kg', 'nodes', projectId, tenantId],
    queryFn: () => fetchKGNodes({ projectId, tenantId }),
  });

  const graphData = useMemo(() => transformToGraphData(nodes ?? []), [nodes]);

  return (
    <div className="relative w-full h-full" role="region" aria-label="Knowledge Graph Visualization">
      {isLoading && <LoadingOverlay />}
      <CytoscapeGraph
        data={graphData}
        onNodeClick={onNodeSelect}
        onNodeRightClick={(node) => onImpactAnalysisRequest?.(node.id())}
        highlightNodes={highlightNodeIds}
        layout="cose-bilkent"
      />
      <GraphLegend />
    </div>
  );
};
```

#### T4.2 — Impact Radar Panel [NEW] [M]
**File:** `frontend/apps/web/src/features/knowledge/ImpactRadarPanel.tsx`

Side panel that shows, for a selected node:
- List of affected nodes (sorted by impact score)
- Visual impact score indicator (0-100%)
- "Why affected" explanation from AI
- Quick links to affected modules/requirements

#### T4.3 — Semantic Search Bar [MOD] [M]
**File:** Update `useSemanticSearch.ts` to call the new BFF GraphQL endpoint

```typescript
export function useSemanticSearch(query: string, tenantId: string) {
  return useQuery({
    queryKey: ['kg', 'semantic-search', query, tenantId],
    queryFn: async () => {
      if (!query || query.length < 3) return [];
      return gqlClient.request<{ semanticSearch: KGNode[] }>(SEMANTIC_SEARCH_QUERY, { query, tenantId, limit: 20 });
    },
    enabled: query.length >= 3,
    debounce: 300,
  });
}
```

---

### Sprint 5 — Cross-Project Knowledge (7 days)

#### T5.1 — Shared Concept Layer [NEW] [M]
Create a tenant-level (not project-level) concept graph that captures patterns shared across projects:

- `project_id = NULL` nodes are shared across the tenant
- AI extracts and deduplicates shared concepts during entity extraction
- Example shared concepts: "Authentication", "Approval Workflow", "Rate Limiting"

#### T5.2 — Knowledge Recommendation Engine [NEW] [L]
**File:** `core/knowledge-graph/src/main/java/com/ghatana/kg/recommendation/KGRecommendationEngine.java`

```java
/**
 * @doc.type class
 * @doc.purpose Recommends relevant knowledge from across projects when working on a requirement or code module.
 * @doc.layer product
 * @doc.pattern Recommendation Engine
 */
public final class KGRecommendationEngine {
    
    /**
     * When a developer works on "Authentication module", recommend:
     * - Similar implementations from other projects
     * - Decisions made by other teams on the same problem
     * - Common pitfalls (from CONFLICTS_WITH edges in past implementations)
     */
    public Promise<List<KGRecommendation>> recommend(String query, String tenantId, String currentProjectId) {
        return nodeRepository.findSimilar(embed(query), tenantId, 20, 0.75f)
            .map(candidates -> candidates.stream()
                .filter(node -> !node.projectId().equals(currentProjectId))  // from other projects
                .map(node -> buildRecommendation(node, query))
                .toList());
    }
}
```

---

## 5. Testing Requirements

| Test | Key Scenarios |
|------|--------------|
| `JdbcKGNodeRepositoryTest` | CRUD, tenant isolation, findSimilar via pgvector |
| `EntityExtractorTest` | AI extracts real entities from text; AI failure → empty list |
| `KGUpdatePipelineTest` | Event processed → nodes created → embeddings generated |
| `KGQueryServiceTest` | Traverse, semantic search, path finding |
| `YAPPCImpactAnalysisTest` | Impact from node X discovers N downstream nodes |
| `KGRecommendationEngineTest` | Cross-project results returned; current project excluded |

### Scale Tests (`k6-tests/`)

```javascript
// k6 load test for semantic search
export default function() {
  const res = http.get(`${BASE_URL}/graphql`, {
    body: JSON.stringify({ query: SEMANTIC_SEARCH_QUERY, variables: { query: 'authentication', limit: 10 } })
  });
  check(res, { 'p95 < 500ms': r => r.timings.duration < 500 });
}
```

---

## 6. Observability

```
yappc_kg_nodes_total{tenant_id, node_type}                    gauge
yappc_kg_edges_total{tenant_id, edge_type}                    gauge
yappc_kg_update_events_processed_total{event_type}            counter
yappc_kg_embedding_generation_duration_seconds                histogram
yappc_kg_embedding_pending_count                              gauge (nodes awaiting embedding)
yappc_kg_semantic_search_latency_seconds                      histogram
yappc_kg_impact_analysis_affected_nodes{range}                histogram
yappc_kg_recommendations_served_total{accepted}               counter
```
