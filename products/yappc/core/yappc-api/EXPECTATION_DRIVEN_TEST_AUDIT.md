# YAPPC API: Expectation-Driven Test Coverage Audit

**Date**: April 2, 2026  
**Repository**: Ghatana/YAPPC  
**Module**: `products/yappc/core/yappc-api`  
**Audit Scope**: HTTP Controllers (Agent, Workflow, Vector)  
**Current Test File**: [YappcApiControllerTest.java](src/test/java/com/ghatana/yappc/api/http/YappcApiControllerTest.java)

---

## Executive Summary

🚨 **CRITICAL MISALIGNMENT DETECTED**

| Metric | Status | Details |
|--------|--------|---------|
| **API Endpoints Defined** | 33+ | AgentRoutes (10+), WorkflowRoutes (15+), VectorRoutes (8+) |
| **Endpoints Covered by Tests** | ~2 | Only `listAgents` and `listWorkflows` have minimal tests |
| **Features Tested** | Constructor validation only | No business logic, no end-to-end flows |
| **Request/Response Validation** | ❌ MISSING | No assertion of data correctness |
| **Error Handling** | ❌ MISSING | No invalid input, boundary, or failure scenarios |
| **Integration Outcomes** | ❌ MISSING | No verification of service calls or persistence |
| **Tenant Isolation** | ❌ MISSING | No multi-tenant correctness validation |
| **Audit Trail** | ❌ MISSING | No audit logging verification |

**Verdict**: ❌ **NOT ALIGNED WITH EXPECTATIONS**

The test suite validates that code *executes* (constructor validation) but does **not validate that the system *behaves correctly*** according to intended features.

---

## Part 1: Expected Behavior Model (Ground Truth)

### 1.1 YAPPC API Purpose and Design Principles

**Purpose**: Provide RESTful HTTP API for AI agents, workflows, and vector/RAG operations in a multi-tenant SaaS platform.

**Core Design Principles**:
- ✅ Multi-tenant isolation (tenant ID required for most operations)
- ✅ Async, non-blocking I/O (ActiveJ Promise-based)
- ✅ Strong input validation at HTTP boundaries
- ✅ Consistent error responses with actionable messages
- ✅ Comprehensive audit logging for security/compliance
- ✅ Deterministic, predictable business outcomes

---

### 1.2 Feature Model: Agent API

#### Feature: Agent Discovery & Listing

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/agents`
- **Purpose**: Discover all registered agents across the system
- **Expected Input**: Optional filters (capability, status)
- **Expected Output**:
  - ✅ HTTP 200 with JSON array of agent metadata
  - ✅ Includes: agent ID, name, description, version, capabilities, health status
  - ✅ Includes pagination metadata (total count, has_more)
  - ✅ Agents sorted alphabetically or by relevance
- **Constraints**:
  - ✅ MUST return only agents the caller has permission to access
  - ✅ MUST include current health status for each agent
  - ✅ MUST validate response schema matches contract
- **Side Effects**:
  - ✅ Log discovery request at DEBUG level
- **Failure Scenarios**:
  - ❌ Invalid query parameters → HTTP 400 with error message
  - ❌ Database unavailable → HTTP 503 with retry-after
  - ❌ No agents found → HTTP 200 with empty array (NOT 404)

**Current Test Coverage**:
```java
// Test exists but is INCOMPLETE
void listAgentsReturnsOkWithAgents() {
    // Problem: Only checks response code
    // Missing: Agent data validation, schema correctness, pagination
}
```

#### Feature: Get Agent Details

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/agents/:name`
- **Purpose**: Retrieve detailed information about a specific agent
- **Expected Input**:
  - ✅ Agent name (path parameter)
  - ✅ Tenant context (from header or session)
- **Expected Output**:
  - ✅ HTTP 200 with complete agent detail (metadata + capabilities + health + metrics)
  - ✅ Includes: InputSchema, OutputSchema, ExecutionTimeout, RetryPolicy
  - ✅ Includes: Last execution time, execution count, error rate
- **Failure Scenarios**:
  - ❌ Agent not found → HTTP 404 with descriptive message
  - ❌ Agent name invalid format → HTTP 400
  - ❌ Caller lacks permission → HTTP 403

**Current Test Coverage**: ❌ **NONE**

#### Feature: Agent Execution

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/agents/:name/execute`
- **Purpose**: Execute an agent with provided inputs
- **Expected Input**:
  - ✅ Agent name (path parameter)
  - ✅ Execution request body with inputs matching agent's InputSchema
  - ✅ Tenant ID (from header)
  - ✅ Optional: timeout override, trace ID for correlation
- **Expected Output**:
  - ✅ HTTP 200 with agent execution result
  - ✅ Includes: execution_id, status, output, execution_time_ms, timestamp
  - ✅ Output validates against agent's OutputSchema
- **Constraints**:
  - ✅ Input validation MUST enforce InputSchema (Zod/similar)
  - ✅ Execution MUST be idempotent if agent supports it
  - ✅ MUST include correlation ID in response for debugging
  - ✅ MUST track execution in audit log with tenant ID, agent name, user
- **Side Effects**:
  - ✅ Store execution record in database
  - ✅ Update agent metrics (execution count, last run time)
  - ✅ Emit execution event to audit log
  - ✅ Emit event to telemetry/observability system
- **Failure Scenarios**:
  - ❌ Input validation fails → HTTP 400 with validation errors per field
  - ❌ Agent not found → HTTP 404
  - ❌ Input mismatch → HTTP 422 (Unprocessable Entity) with schema violation details
  - ❌ Timeout exceeded → HTTP 504 with partial results if available
  - ❌ Agent crashes → HTTP 500 with safe error message
  - ❌ Rate limit exceeded → HTTP 429 with retry-after header

**Current Test Coverage**: ❌ **NONE**

#### Feature: Agent Health Check

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/agents/:name/health` or `GET /api/v1/agents/health`
- **Purpose**: Verify agent availability and readiness
- **Expected Output**:
  - ✅ HTTP 200 with health status
  - ✅ Status: "HEALTHY", "DEGRADED", "UNHEALTHY"
  - ✅ Includes: last_check_time, response_time_ms, error_rate, dependencies
- **Constraints**:
  - ✅ MUST NOT require authentication (health checks are often accessed by load balancers)
  - ✅ MUST respond in < 2 seconds as guarantee
- **Failure Scenarios**:
  - ❌ Agent unreachable → HTTP 503 with reason
  - ❌ Agent timeout → HTTP 504

**Current Test Coverage**: ❌ **NONE**

#### Feature: Capability Discovery

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/agents/capabilities`
- **Purpose**: Discover all capabilities available across all agents
- **Expected Output**:
  - ✅ HTTP 200 with array of capability objects
  - ✅ Each capability: name, description, agents_providing_it, requires_auth
- **Constraints**:
  - ✅ MUST be deduplicated
  - ✅ MUST be sorted alphabetically

**Current Test Coverage**: ❌ **NONE**

#### Feature: Find Agents by Capability

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/agents/by-capability/:capability`
- **Purpose**: Discover which agents provide a specific capability
- **Expected Output**:
  - ✅ HTTP 200 with array of agents having that capability
  - ✅ Each agent: name, version, health_status, rate_limit
- **Failure Scenarios**:
  - ❌ Capability not found → HTTP 404 with helpful message

**Current Test Coverage**: ❌ **NONE**

#### Feature: Copilot Chat

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/agents/copilot/chat`
- **Purpose**: Interactive chat with copilot agent
- **Expected Input**:
  - ✅ Message text
  - ✅ Conversation ID (optional, if resuming)
  - ✅ Tenant ID
  - ✅ User ID
- **Expected Output**:
  - ✅ HTTP 200 with response object
  - ✅ Includes: reply text, conversation_id, timestamp, metadata
  - ✅ Output formatted as markdown
- **Constraints**:
  - ✅ MUST maintain conversation context
  - ✅ MUST handle multi-turn conversations
  - ✅ MUST respect token limits
- **Side Effects**:
  - ✅ Store conversation in database
  - ✅ Emit conversation event
- **Failure Scenarios**:
  - ❌ Empty message → HTTP 400
  - ❌ Conversation not found → HTTP 404
  - ❌ Rate limit → HTTP 429
  - ❌ Token limit exceeded → HTTP 413 with truncation notice

**Current Test Coverage**: ❌ **NONE**

---

### 1.3 Feature Model: Workflow API

#### Feature: Create Workflow

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/workflows`
- **Purpose**: Create a new AI workflow
- **Expected Input**:
  - ✅ Workflow name, description, type
  - ✅ Tenant ID (from header)
  - ✅ Created by (user ID from header/context)
- **Expected Output**:
  - ✅ HTTP 201 (Created) with workflow object
  - ✅ Includes: workflow_id, status (DRAFT), created_at, created_by
- **Constraints**:
  - ✅ Name must be unique within tenant
  - ✅ Name validation: 3-100 chars, alphanumeric+hyphens
  - ✅ Type must be one of: SEQUENTIAL, PARALLEL, HYBRID, AGENTIC
  - ✅ Tenant isolation: workflows created for specific tenant ONLY
- **Side Effects**:
  - ✅ Store workflow in database with status=DRAFT
  - ✅ Emit workflow.created event
  - ✅ Log creation in audit trail (user, tenant, timestamp)
- **Failure Scenarios**:
  - ❌ Missing required fields → HTTP 400 with field-level errors
  - ❌ Duplicate name → HTTP 409 with conflict message
  - ❌ Invalid type → HTTP 422 with allowed values
  - ❌ Name too long → HTTP 422 with max length
  - ❌ No tenant ID → HTTP 400

**Current Test Coverage**: ❌ **NONE**

#### Feature: List Workflows

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/workflows?status=:status&limit=20&offset=0`
- **Purpose**: List workflows for a tenant with filtering and pagination
- **Expected Input**:
  - ✅ Tenant ID (from header/context)
  - ✅ Optional status filter: DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED
  - ✅ Optional limit (default 20, max 100)
  - ✅ Optional offset (default 0)
- **Expected Output**:
  - ✅ HTTP 200 with paginated workflow array
  - ✅ Metadata: total_count, count, limit, offset, has_more
  - ✅ Workflows sorted by created_at DESC
  - ✅ Each workflow: id, name, status, created_at, updated_at, created_by
- **Constraints**:
  - ✅ MUST return only workflows for current tenant
  - ✅ MUST respect limit (cap at 100 even if user requests more)
  - ✅ MUST handle offset overflow gracefully (empty array, not error)
  - ✅ MUST validate status parameter against enum
- **Side Effects**:
  - ✅ Log list request at DEBUG level
- **Failure Scenarios**:
  - ❌ Invalid status → HTTP 400 with allowed values
  - ❌ Invalid limit/offset → HTTP 400
  - ❌ Negative offset → HTTP 400
  - ❌ Limit > 100 → Accept but cap at 100

**Current Test Coverage**:
```java
// Test exists but INCOMPLETE
void listWorkflowsReturnsOkAndDelegates() {
    // Only checks response code and verifies service was called
    // Missing: Pagination validation, response structure, filtering logic
    // Missing: Tenant isolation verification
}
```

#### Feature: Get Workflow Details

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/workflows/:id`
- **Purpose**: Retrieve complete workflow definition and state
- **Expected Output**:
  - ✅ HTTP 200 with complete workflow object
  - ✅ Includes: definition (steps/agents/rules), current state, progress, plan history
  - ✅ Includes: created_at, updated_at, started_at, completed_at (if applicable)
  - ✅ Includes: metrics (execution time, agent results, success rate)
- **Constraints**:
  - ✅ MUST verify workflow belongs to current tenant
- **Failure Scenarios**:
  - ❌ Workflow not found → HTTP 404
  - ❌ Workflow belongs to different tenant → HTTP 403

**Current Test Coverage**: ❌ **NONE**

#### Feature: Delete Workflow

**Intended Behavior**:
- **Endpoint**: `DELETE /api/v1/workflows/:id`
- **Purpose**: Delete a workflow (DRAFT only)
- **Expected Output**:
  - ✅ HTTP 204 (No Content) if successful
- **Constraints**:
  - ✅ MUST only allow deletion of DRAFT workflows
  - ✅ MUST prevent deletion of ACTIVE/RUNNING workflows
  - ✅ MUST verify tenant ownership
- **Side Effects**:
  - ✅ Delete workflow from database
  - ✅ Emit workflow.deleted event
  - ✅ Audit log deletion
- **Failure Scenarios**:
  - ❌ Workflow not found → HTTP 404
  - ❌ Workflow in active state → HTTP 409 with state violation message
  - ❌ Different tenant → HTTP 403

**Current Test Coverage**: ❌ **NONE**

#### Feature: Start Workflow

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/workflows/:id/start`
- **Purpose**: Transition workflow from DRAFT to ACTIVE
- **Expected Input**:
  - ✅ Workflow ID
  - ✅ Tenant ID
  - ✅ Optional: initial context/inputs
- **Expected Output**:
  - ✅ HTTP 200 with updated workflow object (status=ACTIVE)
  - ✅ Includes: started_at, current_step, execution_id
- **Constraints**:
  - ✅ Workflow must be in DRAFT status
  - ✅ Must validate workflow definition is complete
  - ✅ Must verify all required inputs present
- **Side Effects**:
  - ✅ Update workflow status to ACTIVE
  - ✅ Create execution record
  - ✅ Emit workflow.started event
  - ✅ Audit log
- **Failure Scenarios**:
  - ❌ Workflow not DRAFT → HTTP 409 (Conflict)
  - ❌ Workflow incomplete → HTTP 422
  - ❌ Missing required inputs → HTTP 400 with missing fields

**Current Test Coverage**: ❌ **NONE**

#### Feature: Pause/Resume/Cancel Workflow

**Intended Behavior** (Pause):
- **Endpoint**: `POST /api/v1/workflows/:id/pause`
- **Purpose**: Pause a running workflow
- **Expected Output**:
  - ✅ HTTP 200 with updated workflow (status=PAUSED)
  - ✅ Current step state preserved
- **Constraints**:
  - ✅ Workflow must be ACTIVE
  - ✅ Cannot pause if already paused/completed
- **Failure Scenarios**:
  - ❌ Workflow not ACTIVE → HTTP 409

**Intended Behavior** (Resume):
- Similar to pause, but transitions PAUSED → ACTIVE

**Intended Behavior** (Cancel):
- **Endpoint**: `POST /api/v1/workflows/:id/cancel`
- **Purpose**: Cancel a running or paused workflow
- **Expected Output**:
  - ✅ HTTP 200 with updated workflow (status=CANCELLED)
  - ✅ Includes: cancellation_reason, cancelled_at
- **Constraints**:
  - ✅ Workflow must be ACTIVE or PAUSED
  - ✅ Cannot cancel if already COMPLETED or CANCELLED
- **Failure Scenarios**:
  - ❌ Workflow not active/paused → HTTP 409

**Current Test Coverage**: ❌ **NONE**

#### Feature: Workflow Step Operations

**Intended Behavior** (Advance Step):
- **Endpoint**: `POST /api/v1/workflows/:id/steps/advance`
- **Purpose**: Advance workflow to next step
- **Expected Output**:
  - ✅ HTTP 200 with updated workflow state
  - ✅ Includes: current_step, results from previous step
- **Constraints**:
  - ✅ Workflow must be ACTIVE
  - ✅ Must validate next step exists
  - ✅ Must execute any agent/rule at current step first
- **Side Effects**:
  - ✅ Execute current step (if agent/rule)
  - ✅ Store step result
  - ✅ Update step state
- **Failure Scenarios**:
  - ❌ No next step → HTTP 409 (workflow complete)
  - ❌ Step execution fails → HTTP 500 with step error

**Intended Behavior** (Go To Step):
- **Endpoint**: `POST /api/v1/workflows/:id/steps/:stepId/goto`
- **Purpose**: Jump to specific step (for backtracking/rerouting)
- **Constraints**:
  - ✅ Must validate target step exists
  - ✅ May have restrictions on which steps you can jump to
- **Failure Scenarios**:
  - ❌ Step not found → HTTP 404
  - ❌ Step invalid for jump → HTTP 422

**Current Test Coverage**: ❌ **NONE**

#### Feature: AI Plan Management

**Intended Behavior** (Generate Plan):
- **Endpoint**: `POST /api/v1/workflows/:id/plans/generate`
- **Purpose**: Generate an AI-driven execution plan for a workflow
- **Expected Output**:
  - ✅ HTTP 200 with generated plan
  - ✅ Includes: plan_id, steps, estimated_duration, agents_needed, success_probability
- **Constraints**:
  - ✅ Workflow must be ACTIVE or PAUSED
  - ✅ Plan expires after N hours unless approved
- **Side Effects**:
  - ✅ Store plan in database
  - ✅ Emit plan.generated event
- **Failure Scenarios**:
  - ❌ Workflow not in appropriate state → HTTP 409
  - ❌ Plan generation fails (no agents available) → HTTP 503

**Intended Behavior** (Approve Plan):
- **Endpoint**: `POST /api/v1/workflows/:workflowId/plans/:planId/approve`
- **Purpose**: Approve a generated plan for execution
- **Expected Output**:
  - ✅ HTTP 200 with plan status=APPROVED
- **Constraints**:
  - ✅ Plan must be in PENDING state
  - ✅ Plan must not be expired
- **Side Effects**:
  - ✅ Mark plan approved
  - ✅ Emit plan.approved event
  - ✅ Potentially auto-start workflow execution
- **Failure Scenarios**:
  - ❌ Plan not found → HTTP 404
  - ❌ Plan expired → HTTP 410 (Gone)
  - ❌ Plan already approved → HTTP 409

**Intended Behavior** (Reject Plan):
- **Endpoint**: `POST /api/v1/workflows/:workflowId/plans/:planId/reject`
- **Purpose**: Reject a generated plan
- **Failure Scenarios**:
  - ❌ Plan not found → HTTP 404
  - ❌ Plan already rejected → HTTP 409

**Intended Behavior** (Modify Plan Steps):
- **Endpoint**: `PUT /api/v1/workflows/:workflowId/plans/:planId/steps`
- **Purpose**: Modify plan steps before approval
- **Expected Input**:
  - ✅ Array of modified steps with new agents/parameters
- **Constraints**:
  - ✅ Plan must not be APPROVED
  - ✅ Must validate new step configuration
- **Failure Scenarios**:
  - ❌ Plan already approved → HTTP 409
  - ❌ Invalid step configuration → HTTP 422

**Current Test Coverage**: ❌ **NONE**

---

### 1.4 Feature Model: Vector/RAG API

#### Feature: Semantic Search

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/vector/search`
- **Purpose**: Perform semantic (embedding-based) search on indexed documents
- **Expected Input**:
  ```json
  {
    "query": "string",
    "limit": 10,
    "threshold": 0.7,
    "filters": { "field": "value" }
  }
  ```
- **Expected Output**:
  - ✅ HTTP 200 with search results
  - ✅ Includes: hits array with [id, text, score, metadata, source_id]
  - ✅ Results ordered by relevance (score DESC)
- **Constraints**:
  - ✅ Query must not be empty (min 1 char, max 2048)
  - ✅ Limit must be 1-100 (default 10)
  - ✅ Threshold must be 0.0-1.0 (default 0.7)
  - ✅ Query must be semantically valid
- **Side Effects**:
  - ✅ Log search request with query, filters, result count
- **Failure Scenarios**:
  - ❌ Empty query → HTTP 400
  - ❌ Invalid threshold → HTTP 422
  - ❌ Limit > 100 → Accept but cap at 100
  - ❌ No results → HTTP 200 with empty array (NOT 404)

**Current Test Coverage**: ❌ **NONE**

#### Feature: Hybrid Search

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/vector/search/hybrid`
- **Purpose**: Combine semantic and keyword search
- **Expected Input**:
  ```json
  {
    "query": "string",
    "keywords": ["word1", "word2"],
    "limit": 10,
    "threshold": 0.7,
    "keyword_boost": 0.2,
    "filters": {}
  }
  ```
- **Expected Output**:
  - ✅ HTTP 200 with merged results (semantic + keyword)
  - ✅ Results re-ranked by combined score
- **Constraints**:
  - ✅ keyword_boost must be 0.0-1.0
- **Failure Scenarios**:
  - ❌ No semantic query AND no keywords → HTTP 400
  - ❌ Invalid boost → HTTP 422

**Current Test Coverage**: ❌ **NONE**

#### Feature: Find Similar Documents

**Intended Behavior**:
- **Endpoint**: `GET /api/v1/vector/similar/:id`
- **Purpose**: Find documents similar to a specific doc
- **Expected Input**:
  - ✅ Document ID
  - ✅ Optional limit, threshold query params
- **Expected Output**:
  - ✅ HTTP 200 with similar documents array
  - ✅ Includes: source_id, similar array, count
- **Failure Scenarios**:
  - ❌ Document not found → HTTP 404
  - ❌ Document not indexed → HTTP 422

**Current Test Coverage**: ❌ **NONE**

#### Feature: Index Document

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/vector/index`
- **Purpose**: Index a document for vector search
- **Expected Input**:
  ```json
  {
    "id": "doc-id",
    "text": "document content",
    "metadata": { "source": "...", "timestamp": "..." },
    "tenant_id": "..."
  }
  ```
- **Expected Output**:
  - ✅ HTTP 201 (Created) with indexing status
  - ✅ Includes: document_id, indexed_at, vector_id
- **Constraints**:
  - ✅ Document ID must be unique within tenant
  - ✅ Text must not be empty, min 1 char, max 100K chars
  - ✅ Text will be embedded using embedding service
  - ✅ Tenant isolation: only accessible by that tenant
- **Side Effects**:
  - ✅ Generate embedding for text
  - ✅ Store vector in vector DB
  - ✅ Store metadata in document store
  - ✅ Emit document.indexed event
- **Failure Scenarios**:
  - ❌ Duplicate ID → HTTP 409
  - ❌ Text too long → HTTP 413
  - ❌ Missing required field → HTTP 400
  - ❌ Invalid tenant → HTTP 403
  - ❌ Embedding service unavailable → HTTP 503

**Current Test Coverage**: ❌ **NONE**

#### Feature: Batch Index Documents

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/vector/index/batch`
- **Purpose**: Index multiple documents in one request (async)
- **Expected Input**:
  - ✅ Array of documents to index
- **Expected Output**:
  - ✅ HTTP 202 (Accepted) with batch job ID
  - ✅ Includes: batch_id, status (QUEUED), estimated_completion_time
- **Constraints**:
  - ✅ Max 1000 documents per batch
  - ✅ Total text size max 10MB
- **Side Effects**:
  - ✅ Queue batch job for async processing
  - ✅ Return batch_id for status polling
- **Failure Scenarios**:
  - ❌ Too many documents → HTTP 413
  - ❌ Total size exceeds limit → HTTP 413
  - ❌ Invalid documents in batch → HTTP 422 with error details

**Current Test Coverage**: ❌ **NONE**

#### Feature: Delete Document

**Intended Behavior**:
- **Endpoint**: `DELETE /api/v1/vector/index/:id`
- **Purpose**: Remove document from index
- **Expected Output**:
  - ✅ HTTP 204 (No Content)
- **Side Effects**:
  - ✅ Delete vector from vector DB
  - ✅ Delete metadata
  - ✅ Emit document.deleted event
- **Failure Scenarios**:
  - ❌ Document not found → HTTP 404
  - ❌ Different tenant owns document → HTTP 403

**Current Test Coverage**: ❌ **NONE**

#### Feature: RAG (Retrieval-Augmented Generation)

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/vector/rag`
- **Purpose**: Retrieve relevant documents and augment with LLM generation
- **Expected Input**:
  ```json
  {
    "query": "user question",
    "limit": 5,
    "threshold": 0.7,
    "generator": "model-name",
    "temperature": 0.7
  }
  ```
- **Expected Output**:
  - ✅ HTTP 200 with RAG result
  - ✅ Includes: retrieved_documents, generated_response, sources, confidence
- **Constraints**:
  - ✅ Retrieved documents MUST cite sources
  - ✅ Generated response must include proper citations
  - ✅ Temperature 0.0-1.0
  - ✅ Generator model must exist and be available
- **Side Effects**:
  - ✅ Emit rag.executed event with query, results, tokens used
  - ✅ Track token usage for billing/monitoring
- **Failure Scenarios**:
  - ❌ Generator model not found → HTTP 404
  - ❌ Query empty → HTTP 400
  - ❌ LLM generation fails → HTTP 503 with fallback (return just retrieved docs)
  - ❌ No documents found for query → HTTP 200 with retrieved_documents=[], note

**Current Test Coverage**: ❌ **NONE**

#### Feature: RAG Chat (Multi-turn)

**Intended Behavior**:
- **Endpoint**: `POST /api/v1/vector/rag/chat`
- **Purpose**: Multi-turn conversation with RAG
- **Expected Input**:
  ```json
  {
    "conversation_id": "optional",
    "message": "user message",
    "generator": "model-name",
    "max_tokens": 1000
  }
  ```
- **Expected Output**:
  - ✅ HTTP 200 with response
  - ✅ Includes: conversation_id, reply, sources, tokens_used, timestamp
- **Constraints**:
  - ✅ Previous messages MUST be included in context
  - ✅ Conversation max 50 turns or 50K tokens
  - ✅ Context window preservation across turns
- **Side Effects**:
  - ✅ Store conversation in database
  - ✅ Emit conversation.message event
- **Failure Scenarios**:
  - ❌ Conversation not found → HTTP 404
  - ❌ Conversation too old → HTTP 410 (Gone) with reason
  - ❌ Max tokens exceeded → HTTP 413

**Current Test Coverage**: ❌ **NONE**

---

## Part 2: Implementation vs Expectation Gap Analysis

### Summary Table: All Endpoints

| Endpoint | HTTP Method | Purpose | Expected Behavior | Current Implementation | Current Tests | Gap Assessment |
|----------|-------------|---------|------------------|----------------------|-----------------|-----------------|
| **AGENT API** | | | | | | |
| `/api/v1/agents` | GET | List agents | Full list with metadata, pagination | ✅ Implemented | ✅ Exists (minimal) | Missing: response validation, pagination, filtering |
| `/api/v1/agents/:name` | GET | Get agent details | Complete agent info | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/:name/health` | GET | Health check | Health status + response time | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/health` | GET | All agents health | Health status for all | Likely implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/capabilities` | GET | List capabilities | All available capabilities | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/by-capability/*` | GET | Find by capability | Agents with capability | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/:name/execute` | POST | Execute agent | Async execution with validation | ✅ Implemented | ❌ None | **CRITICAL**: No input validation, no outcome verification |
| `/api/v1/agents/copilot/chat` | POST | Copilot chat | Interactive conversation | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/search` | POST | Search agents | Find agents by query | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/agents/predict` | POST | Predictions | Get predictions from agent | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| **WORKFLOW API** | | | | | | |
| `/api/v1/workflows` | POST | Create workflow | Create with validation | ✅ Implemented | ❌ None | **CRITICAL**: No validation testing |
| `/api/v1/workflows` | GET | List workflows | Paginated list by tenant | ✅ Implemented | ⚠️ Partial | Missing: pagination validation, filtering |
| `/api/v1/workflows/:id` | GET | Get workflow | Complete workflow state | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/workflows/:id` | DELETE | Delete workflow | Delete DRAFT only | ✅ Implemented | ❌ None | **CRITICAL**: No state validation |
| `/api/v1/workflows/:id/start` | POST | Start workflow | Transition DRAFT→ACTIVE | ✅ Implemented | ❌ None | **CRITICAL**: No state machine testing |
| `/api/v1/workflows/:id/pause` | POST | Pause workflow | Transition ACTIVE→PAUSED | ✅ Implemented | ❌ None | **CRITICAL**: No state machine testing |
| `/api/v1/workflows/:id/resume` | POST | Resume workflow | Transition PAUSED→ACTIVE | ✅ Implemented | ❌ None | **CRITICAL**: No state machine testing |
| `/api/v1/workflows/:id/cancel` | POST | Cancel workflow | Transition to CANCELLED | ✅ Implemented | ❌ None | **CRITICAL**: No state machine testing |
| `/api/v1/workflows/:id/steps/advance` | POST | Advance step | Move to next step | ✅ Implemented | ❌ None | **CRITICAL**: No state progression testing |
| `/api/v1/workflows/:id/steps/:stepId/goto` | POST | Goto step | Jump to specific step | ✅ Implemented | ❌ None | **CRITICAL**: No validation testing |
| `/api/v1/workflows/:id/plans/generate` | POST | Generate plan | AI-driven planning | ✅ Implemented | ❌ None | **CRITICAL**: No outcome verification |
| `/api/v1/workflows/:workflowId/plans/:planId/approve` | POST | Approve plan | Approve generated plan | ✅ Implemented | ❌ None | **CRITICAL**: No state validation |
| `/api/v1/workflows/:workflowId/plans/:planId/reject` | POST | Reject plan | Reject plan | ✅ Implemented | ❌ None | **CRITICAL**: No state validation |
| `/api/v1/workflows/:workflowId/plans/:planId/steps` | PUT | Modify plan | Modify plan steps | ✅ Implemented | ❌ None | **CRITICAL**: No validation testing |
| `/api/v1/workflows/:id/route` | POST | Route workflow | Route to next step | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| **VECTOR API** | | | | | | |
| `/api/v1/vector/search` | POST | Semantic search | Find by embedding | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/vector/search/hybrid` | POST | Hybrid search | Semantic + keyword | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/vector/similar/:id` | GET | Find similar | Find similar docs | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/vector/index` | POST | Index document | Add to vector DB | ✅ Implemented | ❌ None | **CRITICAL**: No persistence testing |
| `/api/v1/vector/index/batch` | POST | Batch index | Async batch index | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/vector/index/:id` | DELETE | Delete index | Remove from vector DB | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |
| `/api/v1/vector/rag` | POST | RAG generation | Retrieval + generation | ✅ Implemented | ❌ None | **CRITICAL**: No outcome verification |
| `/api/v1/vector/rag/chat` | POST | RAG chat | Multi-turn RAG | ✅ Implemented | ❌ None | **CRITICAL**: No test coverage |

---

## Part 3: Test Quality Evaluation

### Current Test Assessment

**File**: [YappcApiControllerTest.java](src/test/java/com/ghatana/yappc/api/http/YappcApiControllerTest.java)

**Total Tests**: ~13

| Test Category | Count | Assessment | Issues |
|---|---|---|---|
| Constructor validation | 12 | ✅ Valid | Necessary but insufficient |
| Basic null checks | 12 | ✅ Valid | Validates preconditions only |
| Endpoint behavior | 1 | ⚠️ Incomplete | `listAgents`, `listWorkflows` check code only, not outcomes |
| Input validation | 0 | ❌ NONE | Missing completely |
| State transitions | 0 | ❌ NONE | Missing completely |
| Error handling | 0 | ❌ NONE | Missing completely |
| Response schema validation | 0 | ❌ NONE | Missing completely |
| Data correctness | 0 | ❌ NONE | Missing completely |
| Tenant isolation | 0 | ❌ NONE | Missing completely |
| Audit logging | 0 | ❌ NONE | Missing completely |

### Critical Issues Identified

#### ❌ Issue #1: Tests Validate Code Execution, Not Behavior

**Current Test**:
```java
@Test
void listAgentsReturnsOkWithAgents() {
    when(agentRegistry.getAllMetadata()).thenReturn(List.of());
    AgentController controller = new AgentController(agentRegistry, objectMapper);
    HttpRequest request = HttpRequest.get("http://localhost/api/v1/agents").build();

    HttpResponse response = runPromise(() -> controller.listAgents(request));

    assertThat(response.getCode()).isEqualTo(200);  // ← Only checks code!
    verify(agentRegistry).getAllMetadata();         // ← Only checks it was called!
}
```

**Problem**:
- ✅ Does test: "response code is 200"
- ✅ Does test: "Registry.getAllMetadata() was called"
- ❌ Does NOT test: Agent data is present in response
- ❌ Does NOT test: Response has correct JSON structure
- ❌ Does NOT test: Agent metadata is correctly transformed
- ❌ Does NOT test: Agent list is properly serialized

**Expectation**: Test must validate the **actual business outcome**:
- Response contains agents with correct fields
- Agents are properly transformed and serialized
- Pagination metadata is present
- Response matches API contract

#### ❌ Issue #2: No Input Validation Testing

**Example**: `executeAgent` endpoint should validate:
- Input matches agent's InputSchema
- Required fields are present
- Field values pass validation rules
- Invalid input returns HTTP 400 with field-level errors

**Current**: ❌ No tests for this

**Expected**: Multiple test cases:
- Valid input → execution proceeds
- Missing required field → HTTP 400 with field name
- Invalid field value → HTTP 400 with validation error
- Input schema violation → HTTP 422

#### ❌ Issue #3: No Error Scenario Coverage

**Example**: `deleteWorkflow` should:
- ✅ Delete DRAFT workflows
- ❌ Reject deletion of ACTIVE workflows → HTTP 409
- ❌ Return 404 for non-existent workflows
- ❌ Return 403 for workflows from different tenant

**Current**: ❌ No error tests

#### ❌ Issue #4: No Tenant Isolation Verification

**Critical Security Gap**:
- Workflows/documents created by Tenant A MUST NOT be visible/modifiable by Tenant B
- Current tests do NOT verify tenant isolation
- This is a **production security risk**

#### ❌ Issue #5: No State Machine Validation

**Workflow State Machine**:
- DRAFT → ACTIVE (start)
- ACTIVE → PAUSED (pause)
- PAUSED → ACTIVE (resume)
- ACTIVE/PAUSED → CANCELLED (cancel)
- Invalid transitions should return HTTP 409

**Current Tests**: ❌ None verify state transitions

#### ❌ Issue #6: No Persistence/Integration Verification

**Example**: `createWorkflow` should:
- ✅ Create workflow in database
- ✅ Emit workflow.created event
- ✅ Log to audit trail

**Current Tests**: ❌ Do not verify any side effects

#### ❌ Issue #7: No Response Schema Validation

**Problem**: Tests do not verify response matches expected JSON schema
- Missing fields
- Wrong data types
- Invalid values
- Missing nested objects

**Current**: Mocks return empty lists; no response inspection

---

## Part 4: Missing Test Coverage (Expectation-Based)

### Critical (P0) Missing Tests

| Feature | Scenario | Expected Outcome | Test Type | Why It Matters |
|---------|----------|------------------|-----------|-----------------|
| Agent Execution | Valid input | Execute succeeds, output matches schema | Behavior | Core feature; users depend on correct outcomes |
| Agent Execution | Invalid input | HTTP 400 with validation errors | Error handling | Data integrity; prevents malformed requests |
| Agent Execution | Missing input field | HTTP 400 with field errors | Input validation | Users need clear feedback on invalid input |
| Workflow Creation | Duplicate name | HTTP 409 Conflict | Constraint validation | Data integrity; prevents invalid state |
| Workflow Creation | Invalid type | HTTP 422 with allowed values | Input validation | Contract compliance |
| Workflow Start | Not DRAFT state | HTTP 409 Conflict | State machine | Prevents invalid transitions |
| Workflow Start | Complete workflow created | Status transitions to ACTIVE | Behavior | Core feature |
| Workflow Pause | ACTIVE workflow | Status transitions to PAUSED | Behavior | Core feature |
| Workflow Resume | PAUSED workflow | Status transitions to ACTIVE | Behavior | Core feature |
| Workflow Cancel | ACTIVE or PAUSED | Status transitions to CANCELLED | Behavior | Core feature |
| Step Advance | Valid next step | Advances to next step | Behavior | Core feature |
| Step Goto | Invalid step ID | HTTP 404 or 422 | Error handling | Prevents crashes |
| Plan Generation | Successful plan | Generated plan returned with valid structure | Behavior | Core feature |
| Plan Approval | Plan not found | HTTP 404 | Error handling | Prevents crashes |
| Plan Approval | Plan expired | HTTP 410 Gone | Constraint checking | Data integrity |
| Vector Index | Duplicate ID | HTTP 409 Conflict | Constraint validation | Data integrity |
| Vector Index | Text too long | HTTP 413 Payload Too Large | Constraint checking | Prevents resource exhaustion |
| RAG Execution | Valid query | Retrieval + generation succeeds | Behavior | Core feature |
| RAG Execution | LLM unavailable | HTTP 503 or fallback | Failure handling | System resilience |
| Tenant Isolation | Different tenant access | HTTP 403 Forbidden | Security | **CRITICAL**: Multi-tenant safety |
| List Workflows | Pagination limit exceeded | Cap at max, don't fail | Constraint handling | User-friendly behavior |
| List Workflows | Filters applied | Only matching workflows returned | Filtering logic | Feature expectation |

### High Priority (P1) Missing Tests

| Feature | Scenario | Test Type | Count |
|---------|----------|-----------|-------|
| Response schema validation | All endpoints | Contract validation | 33+ |
| Error response format | All error codes | Response format | 50+ |
| Audit logging | All state-changing operations | Observability | 15+ |
| Health status codes | All endpoints when services unavailable | Resilience | 15+ |
| Timeout handling | Long-running operations | Resilience | 5+ |
| Rate limiting | Too many requests | Constraint checking | 3+ |
| Concurrent operations | Multiple parallel requests | Concurrency | 10+ |

---

## Part 5: Required Test Plan (Expectation-Driven)

### Testing Strategy

**Principle**: Tests must validate whether the system **does what it's supposed to**, not whether it **executes** as coded.

**Test Levels**:
1. **Unit** (Controller level): Input validation, response formatting
2. **Integration** (Controller + Service): Business logic, state transitions, persistence
3. **API E2E** (Full HTTP stack): Request → Response → Persistence → Events
4. **Security** (Authorization, tenant isolation)

### 5.1 Agent API Test Plan

#### A. Agent Listing Tests

```java
@Nested
class AgentListingTests {
    
    @Test
    void listAgentsReturnsValidSchema() {
        // GIVEN: Registry with agents
        List<AgentMetadata> agents = Arrays.asList(
            new AgentMetadata("agent-1", "Agent 1", "...", ...),
            new AgentMetadata("agent-2", "Agent 2", "...", ...)
        );
        when(agentRegistry.getAllMetadata()).thenReturn(agents);
        
        // WHEN: List agents
        AgentController controller = new AgentController(agentRegistry, objectMapper);
        HttpResponse response = runPromise(() -> controller.listAgents(request));
        
        // THEN: Response contains agents with all required fields
        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = parseJson(response);
        List<Map> agentList = (List) body.get("agents");
        
        // Validate schema
        assertThat(agentList).hasSize(2);
        assertThat(agentList.get(0))
            .containsKeys("id", "name", "description", "version", "capabilities", "health_status")
            .doesNotContainKeys("internal_field_1", "internal_field_2");
        
        // Validate data correctness
        assertThat(agentList.get(0).get("name")).isEqualTo("Agent 1");
        assertThat(agentList.get(0).get("capabilities")).isInstanceOf(List.class);
    }
    
    @Test
    void listAgentsIncludesPagination() {
        // THEN: Response includes pagination metadata
        Map body = parseJson(response);
        assertThat(body).containsKeys("agents", "total");
        assertThat(body.get("total")).isEqualTo(2);
    }
    
    @Test
    void listAgentsEmptyRegistry() {
        // GIVEN: Empty registry
        when(agentRegistry.getAllMetadata()).thenReturn(List.of());
        
        // THEN: Returns 200 with empty array (not 404)
        assertThat(response.getCode()).isEqualTo(200);
        Map body = parseJson(response);
        assertThat(body.get("agents")).isEqualTo(List.of());
    }
}
```

#### B. Get Agent Details Tests

```java
@Nested
class GetAgentDetailsTests {
    
    @Test
    void getAgentReturnsCompleteDetails() {
        // GIVEN: Agent exists in registry
        AIAgent<?, ?> agent = mockAgent("copilot", InputType.STRING, OutputType.STRING);
        when(agentRegistry.get(AgentName.of("copilot"))).thenReturn(agent);
        
        // WHEN: Get agent details
        HttpResponse response = runPromise(() -> controller.getAgent(request));
        
        // THEN: Response includes all agent details
        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> detail = parseJson(response);
        assertThat(detail)
            .containsKeys("id", "name", "description", "input_schema", "output_schema",
                         "execution_timeout_ms", "retry_policy", "health", "metrics");
    }
    
    @Test
    void getAgentNotFound() {
        // GIVEN: Agent not in registry
        when(agentRegistry.get(any())).thenReturn(null);
        
        // THEN: Returns 404 with descriptive message
        HttpResponse response = runPromise(() -> controller.getAgent(request));
        assertThat(response.getCode()).isEqualTo(404);
        Map<String, String> body = parseJson(response);
        assertThat(body.get("error")).contains("not found").contains("copilot");
    }
    
    @Test
    void getAgentInvalidNameFormat() {
        // GIVEN: Invalid agent name
        HttpRequest invalidRequest = HttpRequest.get("http://localhost/api/v1/agents/!!!invalid!!!").build();
        
        // THEN: Returns 400
        HttpResponse response = runPromise(() -> controller.getAgent(invalidRequest));
        assertThat(response.getCode()).isEqualTo(400);
    }
}
```

#### C. Execute Agent Tests

```java
@Nested
class ExecuteAgentTests {
    
    @Test
    void executeAgentWithValidInput() {
        // GIVEN: Valid execution request
        ExecuteRequest execReq = new ExecuteRequest("input text", null);
        HttpRequest request = postRequest("/api/v1/agents/copilot/execute", execReq);
        
        // AND: Agent executes successfully
        ExecutionResult result = new ExecutionResult("output text", ExecutionStatus.SUCCESS);
        when(agentRegistry.get(any())).thenReturn(mockAgent);
        when(mockAgent.execute(any())).thenReturn(Promise.of(result));
        
        // WHEN: Execute agent
        HttpResponse response = runPromise(() -> controller.executeAgent(request));
        
        // THEN: Returns 200 with result
        assertThat(response.getCode()).isEqualTo(200);
        Map body = parseJson(response);
        
        // Validate output schema
        assertThat(body)
            .containsKeys("execution_id", "status", "output", "execution_time_ms", "timestamp");
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("output")).isEqualTo("output text");
        assertThat((long) body.get("execution_time_ms")).isGreaterThanOrEqualTo(0);
        
        // Validate outcome: output matches expected schema
        assertThat(body.get("output")).isInstanceOf(String.class);
    }
    
    @Test
    void executeAgentInvalidInput() {
        // GIVEN: Input doesn't match agent's schema
        ExecuteRequest badReq = new ExecuteRequest(123, null);  // Wrong type
        
        // THEN: Returns 400 with validation error
        HttpResponse response = runPromise(() -> controller.executeAgent(request));
        assertThat(response.getCode()).isEqualTo(400);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("validation").contains("input");
    }
    
    @Test
    void executeAgentMissingRequiredInput() {
        // GIVEN: Required field missing
        ExecuteRequest req = new ExecuteRequest(null, null);
        
        // THEN: Returns 400 with field error
        HttpResponse response = runPromise(() -> controller.executeAgent(request));
        assertThat(response.getCode()).isEqualTo(400);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("required").contains("input");
    }
    
    @Test
    void executeAgentTimeout() {
        // GIVEN: Agent execution times out
        when(mockAgent.execute(any())).thenReturn(
            Promise.setTimeout(10000, new TimeoutException("Execution exceeded 10s"))
        );
        
        // THEN: Returns 504 Gateway Timeout
        HttpResponse response = runPromise(() -> controller.executeAgent(request));
        assertThat(response.getCode()).isEqualTo(504);
    }
    
    @Test
    void executeAgentCrash() {
        // GIVEN: Agent crashes
        when(mockAgent.execute(any())).thenReturn(
            Promise.ofException(new RuntimeException("Agent crashed"))
        );
        
        // THEN: Returns 500 with safe error message
        HttpResponse response = runPromise(() -> controller.executeAgent(request));
        assertThat(response.getCode()).isEqualTo(500);
        Map body = parseJson(response);
        assertThat(body.get("error")).doesNotContain("crashed"); // Don't expose internal error
        assertThat(body.get("error")).contains("execution failed");
    }
    
    @Test
    void executeAgentEmitsAuditLog() {
        // GIVEN: Valid execution
        // WHEN: Agent executes
        runPromise(() -> controller.executeAgent(request));
        
        // THEN: Audit log is updated
        verify(auditLogger).log(argThat(event ->
            event.getAction().equals("AGENT_EXECUTED") &&
            event.getAgent().equals("copilot") &&
            event.getTenant().equals("tenant-001")
        ));
    }
}
```

### 5.2 Workflow API Test Plan

#### A. Create Workflow Tests

```java
@Nested
class CreateWorkflowTests {
    
    @Test
    void createWorkflowSuccessfully() {
        // GIVEN: Valid creation request
        CreateWorkflowDto dto = new CreateWorkflowDto(
            "New Workflow",
            "A test workflow",
            WorkflowType.SEQUENTIAL,
            "user-12345"
        );
        HttpRequest request = postRequest("/api/v1/workflows", dto);
        request = request.withHeader("X-Tenant-ID", "tenant-001");
        
        // AND: Service creates workflow
        AiWorkflowInstance created = new AiWorkflowInstance(
            UUID.randomUUID().toString(),
            "New Workflow",
            "A test workflow",
            WorkflowType.SEQUENTIAL,
            WorkflowStatus.DRAFT,
            "tenant-001",
            "user-12345"
        );
        when(workflowService.createWorkflow(any())).thenReturn(Promise.of(created));
        
        // WHEN: Create workflow
        HttpResponse response = runPromise(() -> controller.createWorkflow(request));
        
        // THEN: Returns 201 Created with workflow
        assertThat(response.getCode()).isEqualTo(201);
        Map body = parseJson(response);
        
        // Validate response schema
        assertThat(body)
            .containsKeys("id", "name", "description", "status", "created_at", "created_by");
        assertThat(body.get("status")).isEqualTo("DRAFT");
        assertThat(body.get("name")).isEqualTo("New Workflow");
    }
    
    @Test
    void createWorkflowDuplicateName() {
        // GIVEN: Workflow with same name exists
        when(workflowService.createWorkflow(any())).thenReturn(
            Promise.ofException(new ConflictException("Workflow already exists"))
        );
        
        // THEN: Returns 409 Conflict
        HttpResponse response = runPromise(() -> controller.createWorkflow(request));
        assertThat(response.getCode()).isEqualTo(409);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("already exists");
    }
    
    @Test
    void createWorkflowInvalidType() {
        // GIVEN: Invalid workflow type
        CreateWorkflowDto dto = new CreateWorkflowDto("Name", "Desc", "INVALID_TYPE", "user");
        
        // THEN: Returns 422 Unprocessable Entity
        HttpResponse response = runPromise(() -> controller.createWorkflow(request));
        assertThat(response.getCode()).isEqualTo(422);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("Invalid type");
        assertThat(body.get("allowed_values")).asList().contains("SEQUENTIAL", "PARALLEL", "HYBRID");
    }
    
    @Test
    void createWorkflowMissingName() {
        // GIVEN: Name is missing
        CreateWorkflowDto dto = new CreateWorkflowDto(null, "Description", "SEQUENTIAL", "user");
        
        // THEN: Returns 400 Bad Request
        HttpResponse response = runPromise(() -> controller.createWorkflow(request));
        assertThat(response.getCode()).isEqualTo(400);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("name").contains("required");
    }
    
    @Test
    void createWorkflowNameTooLong() {
        // GIVEN: Name exceeds max length (100 chars)
        String longName = "a".repeat(101);
        CreateWorkflowDto dto = new CreateWorkflowDto(longName, "Description", "SEQUENTIAL", "user");
        
        // THEN: Returns 422 with length constraint
        HttpResponse response = runPromise(() -> controller.createWorkflow(request));
        assertThat(response.getCode()).isEqualTo(422);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("too long").contains("100");
    }
    
    @Test
    void createWorkflowEmitEvent() {
        // GIVEN: Valid creation
        // WHEN: Create workflow
        runPromise(() -> controller.createWorkflow(request));
        
        // THEN: Event is emitted
        verify(eventPublisher).publish(argThat(event ->
            event.getType().equals("workflow.created") &&
            event.getWorkflowId().equals(createdWorkflow.getId())
        ));
    }
}
```

#### B. Workflow State Machine Tests

```java
@Nested
class WorkflowStateTransitionTests {
    
    @Test
    void startDraftWorkflow() {
        // GIVEN: Workflow in DRAFT state
        AiWorkflowInstance draft = workflowInState("workflow-1", WorkflowStatus.DRAFT);
        when(workflowService.getWorkflow("workflow-1", "tenant-001"))
            .thenReturn(Promise.of(Optional.of(draft)));
        when(workflowService.startWorkflow("workflow-1", "tenant-001"))
            .thenReturn(Promise.of(workflowInState("workflow-1", WorkflowStatus.ACTIVE)));
        
        // WHEN: Start workflow
        HttpRequest request = postRequest("/api/v1/workflows/workflow-1/start", null);
        HttpResponse response = runPromise(() -> controller.startWorkflow(request, "workflow-1"));
        
        // THEN: Transitions to ACTIVE
        assertThat(response.getCode()).isEqualTo(200);
        Map body = parseJson(response);
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        assertThat(body.get("started_at")).isNotNull();
    }
    
    @Test
    void cannotStartActiveWorkflow() {
        // GIVEN: Workflow already ACTIVE
        AiWorkflowInstance active = workflowInState("workflow-1", WorkflowStatus.ACTIVE);
        when(workflowService.getWorkflow("workflow-1", "tenant-001"))
            .thenReturn(Promise.of(Optional.of(active)));
        
        // THEN: Returns 409 Conflict
        HttpResponse response = runPromise(() -> controller.startWorkflow(request, "workflow-1"));
        assertThat(response.getCode()).isEqualTo(409);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("not in DRAFT state");
    }
    
    @Test
    void pauseActiveWorkflow() {
        // GIVEN: Workflow ACTIVE
        // THEN: Transitions to PAUSED
        // [Similar test structure...]
    }
    
    @Test
    void resumePausedWorkflow() {
        // GIVEN: Workflow PAUSED
        // THEN: Transitions to ACTIVE
        // [Similar test structure...]
    }
    
    @Test
    void cancelActiveWorkflow() {
        // GIVEN: Workflow ACTIVE or PAUSED
        // THEN: Transitions to CANCELLED
        Map body = parseJson(response);
        assertThat(body.get("status")).isEqualTo("CANCELLED");
        assertThat(body.get("cancelled_at")).isNotNull();
    }
    
    @Test
    void cannotCancelCompletedWorkflow() {
        // GIVEN: Workflow already COMPLETED
        // THEN: Returns 409
        // [Similar test structure...]
    }
}
```

### 5.3 Vector/RAG API Test Plan

#### A. Semantic Search Tests

```java
@Nested
class SemanticSearchTests {
    
    @Test
    void semanticSearchSuccessful() {
        // GIVEN: Valid search request
        SearchDto dto = new SearchDto("What is machine learning?", 10, 0.7, null);
        
        // AND: Service returns results
        List<SearchHit> hits = Arrays.asList(
            new SearchHit("doc-1", "Machine learning is...", 0.95,
                Map.of("source", "wikipedia")),
            new SearchHit("doc-2", "ML models are...", 0.82,
                Map.of("source", "textbook"))
        );
        when(searchService.search(any())).thenReturn(Promise.of(hits));
        
        // WHEN: Search
        HttpResponse response = runPromise(() -> controller.search(request));
        
        // THEN: Returns 200 with hits
        assertThat(response.getCode()).isEqualTo(200);
        Map body = parseJson(response);
        
        // Validate schema
        List<Map> resultHits = (List) body.get("hits");
        assertThat(resultHits).hasSize(2);
        assertThat(resultHits.get(0))
            .containsKeys("id", "text", "score", "metadata");
        
        // Validate correctness
        assertThat(resultHits.get(0).get("score")).isEqualTo(0.95);
        assertThat(resultHits.get(1).get("score")).isEqualTo(0.82);  // Order preserved
    }
    
    @Test
    void semanticSearchEmptyQuery() {
        // GIVEN: Empty query
        SearchDto dto = new SearchDto("", 10, 0.7, null);
        
        // THEN: Returns 400
        HttpResponse response = runPromise(() -> controller.search(request));
        assertThat(response.getCode()).isEqualTo(400);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("query").contains("required");
    }
    
    @Test
    void semanticSearchInvalidThreshold() {
        // GIVEN: Threshold out of range
        SearchDto dto = new SearchDto("query", 10, 1.5, null);  // > 1.0
        
        // THEN: Returns 422
        HttpResponse response = runPromise(() -> controller.search(request));
        assertThat(response.getCode()).isEqualTo(422);
    }
    
    @Test
    void semanticSearchLimitCapped() {
        // GIVEN: Limit > 100
        SearchDto dto = new SearchDto("query", 150, 0.7, null);
        
        // WHEN: Search
        runPromise(() -> controller.search(request));
        
        // THEN: Service called with limit capped at 100
        verify(searchService).search(argThat(req -> req.limit == 100));
    }
    
    @Test
    void semanticSearchNoResults() {
        // GIVEN: Query matches no documents
        when(searchService.search(any())).thenReturn(Promise.of(List.of()));
        
        // THEN: Returns 200 with empty array (not 404)
        HttpResponse response = runPromise(() -> controller.search(request));
        assertThat(response.getCode()).isEqualTo(200);
        Map body = parseJson(response);
        assertThat(body.get("hits")).isEqualTo(List.of());
    }
}
```

#### B. Index Document Tests

```java
@Nested
class IndexDocumentTests {
    
    @Test
    void indexDocumentSuccessfully() {
        // GIVEN: Valid document
        IndexDocumentDto dto = new IndexDocumentDto(
            "doc-123",
            "This is a document.",
            Map.of("source", "file.pdf", "author", "John")
        );
        
        // AND: Service confirms indexing
        when(ragService.indexDocument(any())).thenReturn(
            Promise.of(new IndexResult("doc-123", "vec-456", Instant.now()))
        );
        
        // WHEN: Index document
        HttpResponse response = runPromise(() -> controller.indexDocument(request));
        
        // THEN: Returns 201 Created
        assertThat(response.getCode()).isEqualTo(201);
        Map body = parseJson(response);
        assertThat(body)
            .containsKeys("document_id", "vector_id", "indexed_at");
        assertThat(body.get("document_id")).isEqualTo("doc-123");
    }
    
    @Test
    void indexDocumentDuplicate() {
        // GIVEN: Document with same ID exists
        when(ragService.indexDocument(any())).thenReturn(
            Promise.ofException(new ConflictException("Document already indexed"))
        );
        
        // THEN: Returns 409 Conflict
        HttpResponse response = runPromise(() -> controller.indexDocument(request));
        assertThat(response.getCode()).isEqualTo(409);
    }
    
    @Test
    void indexDocumentTextTooLong() {
        // GIVEN: Text exceeds 100KB
        IndexDocumentDto dto = new IndexDocumentDto(
            "doc-123",
            "a".repeat(101000),  // 101KB
            null
        );
        
        // THEN: Returns 413 Payload Too Large
        HttpResponse response = runPromise(() -> controller.indexDocument(request));
        assertThat(response.getCode()).isEqualTo(413);
    }
    
    @Test
    void indexDocumentPersisted() {
        // GIVEN: Valid document
        // WHEN: Index document
        runPromise(() -> controller.indexDocument(request));
        
        // THEN: Document stored in database
        verify(ragService).indexDocument(argThat(req ->
            req.documentId.equals("doc-123") &&
            req.text.equals("This is a document.")
        ));
    }
}
```

### 5.4 Security & Cross-Cutting Tests

#### A. Tenant Isolation Tests

```java
@Nested
class TenantIsolationTests {
    
    @Test
    void workflowNotAccessibleByOtherTenant() {
        // GIVEN: Workflow created by tenant-001
        AiWorkflowInstance workflow = create WorkflowForTenant("tenant-001");
        
        // AND: User from tenant-002 requests it
        HttpRequest request = getRequest("/api/v1/workflows/workflow-1");
        request = request.withHeader("X-Tenant-ID", "tenant-002");
        
        // WHEN: Get workflow
        HttpResponse response = runPromise(() -> controller.getWorkflow(request, "workflow-1"));
        
        // THEN: Returns 403 Forbidden (not 404, to avoid leaking existence
        assertThat(response.getCode()).isEqualTo(403);
        Map body = parseJson(response);
        assertThat(body.get("error")).contains("not authorized");
    }
    
    @Test
    void deleteWorkflowNotAccessibleByOtherTenant() {
        // GIVEN: Workflow from tenant-001
        // AND: Request from tenant-002
        HttpRequest request = deleteRequest("/api/v1/workflows/workflow-1");
        request = request.withHeader("X-Tenant-ID", "tenant-002");
        
        // THEN: Returns 403
        HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "workflow-1"));
        assertThat(response.getCode()).isEqualTo(403);
    }
    
    @Test
    void listWorkflowsFilteredByTenant() {
        // GIVEN: Multiple workflows, some for tenant-001, some for tenant-002
        List<AiWorkflowInstance> allWorkflows = Arrays.asList(
            workflowForTenant("tenant-001"),
            workflowForTenant("tenant-001"),
            workflowForTenant("tenant-002")
        );
        when(workflowService.listWorkflows("tenant-001", null, 20, 0))
            .thenReturn(Promise.of(allWorkflows.stream()
                .filter(w -> w.getTenantId().equals("tenant-001"))
                .collect(toList())));
        
        // WHEN: tenant-001 lists workflows
        HttpRequest request = getRequest("/api/v1/workflows");
        request = request.withHeader("X-Tenant-ID", "tenant-001");
        HttpResponse response = runPromise(() -> controller.listWorkflows(request));
        
        // THEN: Returns only tenant-001's workflows
        Map body = parseJson(response);
        List<Map> workflows = (List) body.get("workflows");
        assertThat(workflows).hasSize(2);
        assertThat(workflows).allMatch(w -> w.get("tenant_id").equals("tenant-001"));
    }
}
```

#### B. Audit Logging Tests

```java
@Nested
class AuditLoggingTests {
    
    @Test
    void executeAgentLogsToAuditTrail() {
        // GIVEN: Agent execution request
        // WHEN: Execute agent
        runPromise(() -> controller.executeAgent(request));
        
        // THEN: Audit log entry created
        verify(auditLogger).log(argThat(event ->
            event.getAction().equals("AGENT_EXECUTED") &&
            event.getAgent().equals("copilot") &&
            event.getTenant().equals("tenant-001") &&
            event.getUser().equals("user-123") &&
            event.getTimestamp() != null &&
            event.getResult().equals("SUCCESS")
        ));
    }
    
    @Test
    void createWorkflowLogsAuditEvent() {
        // THEN: Audit log entry created with correct fields
        verify(auditLogger).log(argThat(event ->
            event.getAction().equals("WORKFLOW_CREATED") &&
            event.getWorkflowId().equals(createdWorkflow.getId())
        ));
    }
    
    @Test
    void deleteWorkflowLogsAuditEvent() {
        // THEN: Audit log entry for deletion with reason
        verify(auditLogger).log(argThat(event ->
            event.getAction().equals("WORKFLOW_DELETED")
        ));
    }
}
```

#### C. Error Response Format Tests

```java
@Nested
class ErrorResponseFormatTests {
    
    @Test
    void errorResponseHasCorrectFormat() {
        // GIVEN: Request that triggers error
        // WHEN: Error occurs
        HttpResponse response = runPromise(() -> controller.getAgent(invalidRequest));
        
        // THEN: Error response has standard format
        Map<String, Object> body = parseJson(response);
        assertThat(body).containsKeys("error", "error_code", "timestamp");
        assertThat(body.get("error")).isInstanceOf(String.class);
        assertThat(body.get("error_code")).isInstanceOf(String.class);
        assertThat(body.get("timestamp")).isInstanceOf(String.class);  // ISO 8601
        
        // AND: Not exposing internal details
        assertThat(body.get("error")).doesNotContain("NullPointerException");
        assertThat(body.get("error")).doesNotContain("at com.ghatana");
    }
    
    @Test
    void validationErrorIncludesFieldDetails() {
        // GIVEN: Request with validation error
        // THEN: Error response includes field-level details
        Map<String, Object> body = parseJson(response);
        Map<String, Object> details = (Map) body.get("validation_errors");
        assertThat(details)
            .containsKeys("name", "type")
            .containsEntry("name", "Name must be 3-100 characters")
            .containsEntry("type", "Type must be one of: SEQUENTIAL, PARALLEL, HYBRID");
    }
}
```

---

## Part 6: Invariant & Guarantee Validation

### Data Integrity Invariants

| Invariant | Test Scenario | Validation | Test Type |
|-----------|---|---|---|
| **Workflow Name Uniqueness** | Create 2 workflows with same name in same tenant | 2nd creation fails with 409 | Constraint |
| **Tenant Isolation** | Workflow from tenant-A not accessible by tenant-B | Access returns 403 | Security |
| **Status Valid Transitions** | Invalid state transition | Returns 409 with reason | State machine |
| **No Lost Events** | Workflow state change | Event emitted before response | Event ordering |
| **Idempotent Operations** | Execute workflow twice with same ID | Both succeed, same result UUID | Idempotency |
| **Atomic Creation** | Create workflow fails mid-process | No partial workflow persisted | Atomicity |
| **Concurrent Modifications** | Two users modify workflow simultaneously | Last write wins or conflict error | Concurrency |
| **Agent Health Accuracy** | Agent metrics updated after execution | Health status reflects current state | Freshness |
| **Vector Index Consistency** | Document indexed and immediately searched | Found in first search | Consistency |
| **Audit Trail Completeness** | Every state-changing operation | Audit log entry created | Auditability |

---

## Part 7: Edge Case & Failure Coverage

| Category | Scenario | Expected Behavior | Test |
|----------|----------|---|---|
| **Input Boundaries** | Empty string query | HTTP 400 | ✅ |
| **Input Boundaries** | Max length exceeded | HTTP 413/422 | ✅ |
| **Input Boundaries** | Null required field | HTTP 400 | ✅ |
| **Input Boundaries** | Invalid UUID format | HTTP 400 | ✅ |
| **Concurrency** | 10 simultaneous executions | All succeed or properly queued | Concurrency test |
| **Timeout** | Execution > timeout limit | HTTP 504 with partial result | Timeout test |
| **Service Failure** | Database unavailable | HTTP 503 with retry-after | Resilience test |
| **Service Failure** | Embedding service down | HTTP 503 or graceful degradation | Resilience test |
| **Rate Limiting** | 1000 requests/sec | 429 after threshold | Load test |
| **State Inconsistency** | Workflow deleted mid-execution | Execution fails with 410 | Race condition test |
| **Transaction Rollback** | Event publication fails | Workflow creation rolled back | Transaction test |

---

## Part 8: Assertion Standards (STRICT)

### ✅ GOOD Test Assertions

```java
// Assertion #1: Response code alone (insufficient)
assertThat(response.getCode()).isEqualTo(200);  // + More assertions needed!

// Assertion #2: Response body structure
Map body = parseJson(response);
assertThat(body).containsKeys("agents", "total");

// Assertion #3: Response data correctness
assertThat(body.get("agents")).asList()
    .extracting("name", "health_status")
    .contains(
        tuple("copilot",  "HEALTHY"),
        tuple("refactor", "HEALTHY")
    );

// Assertion #4: Outcome verification
List<Agent> agents = (List) body.get("agents");
assertThat(agents)
    .allMatch(a -> a.getHealth() != null)
    .allMatch(a -> a.getName() != null)
    .isSorted(comparingByName());

// Assertion #5: Side effect verification
verify(agentRegistry).getAllMetadata();
verify(auditLogger).log(any());

// Assertion #6: State change verification
Workflow before = getWorkflow("w1");
startWorkflow("w1");
Workflow after = getWorkflow("w1");
assertThat(after.getStatus()).isEqualTo(ACTIVE);
assertThat(after.getStartedAt()).isAfter(before.getCreatedAt());

// Assertion #7: Error response validation
assertThat(errorResponse)
    .containsKeys("error", "error_code", "timestamp")
    .doesNotContainKey("stack_trace")
    .doesNotContainKey("internal_id");
```

### ❌ BAD Test Assertions (AVOID)

```java
// Bad #1: Only checking code
assertThat(response.getCode()).isEqualTo(200);  // Doesn't verify outcome!

// Bad #2: Mocking without outcome verification
when(service.execute(any())).thenReturn(result);
controller.execute(request);
verify(service).execute(any());  // Only checks it was called, not that outcome is correct!

// Bad #3: Snapshot assertion without field validation
assertThat(response).matches(json("agent-response-snapshot.json"));  // What changed? Unknown!

// Bad #4: Generic assertions
assertThat(body).isNotNull();  // Too weak
assertThat(agents).isNotEmpty();  // Doesn't validate correctness

// Bad #5: Checking implementation detail instead of outcome
verify(agentRegistry, times(1)).getAllMetadata();  // Implementation detail!
// Instead, verify: response contains agents with correct data

// Bad #6: Silent failures
try {
    controller.execute(request);
    // No assertion!
} catch (Exception e) {
    // Ignored!
}
```

---

## Part 9: Prioritized Remediation Plan

### Phase 1: Critical Misalignment (Week 1)

**Goal**: Fix tests that validate wrong behavior

| Task | Priority | Effort | Owner | Tests Affected |
|------|----------|--------|-------|---|
| Fix `listAgents` test to validate response schema | P0 | 2h | TBD | 2 |
| Fix `listWorkflows` test to validate response schema | P0 | 2h | TBD | 2 |
| Add `Agent not found` error test | P0 | 1h | TBD | 1 |
| Add `Workflow not found` error test | P0 | 1h | TBD | 1 |
| Add tenant isolation security tests | P0 | 3h | TBD | 5+ |

**Deliverable**: Existing tests now validate **correct outcomes**, not just code execution.

### Phase 2: Core Endpoints (Week 2-3)

**Goal**: Comprehensive coverage of primary user workflows

| Feature | Tests Needed | Effort | Owner |
|---------|--|--|--|
| Agent Execution | Execute + Validation + Error + Timeout + Crash + Audit | 15h | TBD |
| Workflow Creation | Create + Duplicate + Invalid + Schema + Event | 12h | TBD |
| Workflow State Machine | Start + Pause + Resume + Cancel | 10h | TBD |
| Vector Search | Search + Empty Query + Invalid Threshold + No Results | 8h | TBD |
| Vector Index | Index + Duplicate + Too Long + Persist | 8h | TBD |

**Subtotal**: ~53 hours, ~35 new test cases

### Phase 3: Integration & API Contract (Week 4-5)

**Goal**: Full end-to-end validation of API contracts and integration with services

| Feature | Tests Needed | Effort | Owner |
|---------|--|--|--|
| RAG Operations | RAG + Chat + Failure Handling | 10h | TBD |
| Plan Management | Generate + Approve + Reject + Modify | 12h | TBD |
| Workflow Steps | Advance + Goto + Multiple Steps | 8h | TBD |
| Concurrent Operations | Parallel executions, race conditions | 10h | TBD |
| Error Response Format | All error codes, response structure | 6h | TBD |
| Audit Logging | All operations logged correctly | 8h | TBD |

**Subtotal**: ~54 hours, ~40 new test cases

### Phase 4: Hardening (Week 6)

**Goal**: Edge cases, boundary conditions, failure scenarios

| Category | Tests Needed | Effort |
|----------|--|--|
| Input Boundaries | Empty, null, oversized, invalid format | 8h |
| Timeout & Resilience | Service failures, recovery | 6h |
| Rate Limiting | 429 responses when exceeded | 4h |
| Concurrency | Thread safety, race conditions | 6h |
| Data Consistency | Transactions, rollback scenarios | 6h |

**Subtotal**: ~30 hours, ~25 new test cases

**Total Plan**:
- **Phase 1**: Fix existing 2 tests + add 12 new tests = 14 tests
- **Phase 2**: 35 new tests
- **Phase 3**: 40 new tests
- **Phase 4**: 25 new tests
- **Grand Total**: ~112 new tests, 14 fixed tests = **~130 tests total**

**Timeline**: 6 weeks, ~150-170 hours (assuming 1-2 engineers)

---

## Part 10: Final Judgment & Recommendations

### Current Test Alignment Score

| Category | Alignment | Evidence |
|---|---|---|
| **Features Tested** | ❌ 5/100 | Only 2 endpoints barely tested |
| **Correct Outcomes** | ❌ 10/100 | Tests validate code runs, not that results are correct |
| **Error Handling** | ❌ 0/100 | No error scenario tests |
| **Integration** | ❌ 0/100 | No service interaction verification |
| **Security** | ❌ 0/100 | No tenant isolation tests |
| **Data Integrity** | ❌ 5/100 | Only constructor null checks |

**Overall Alignment**: ❌ **NOT ALIGNED** (5/100)

### Final Verdict

```
┌─────────────────────────────────────────────────────────┐
│  YAPPC API TEST SUITE: NOT PRODUCTION-READY            │
│                                                         │
│  ❌ Tests do NOT validate intended behavior            │
│  ❌ 30+ endpoints have NO meaningful test coverage     │
│  ❌ No error scenario validation                       │
│  ❌ No tenant isolation security testing               │
│  ❌ No data integrity verification                     │
│  ❌ False confidence: All tests pass, but behavior     │
│     is untested                                        │
│                                                         │
│  RISK LEVEL: 🔴 CRITICAL                             │
│                                                         │
│  Tests prove code EXECUTES. They DO NOT prove          │
│  code BEHAVES CORRECTLY when deployed to production.   │
└─────────────────────────────────────────────────────────┘
```

### Required Actions Before Production

**Blocking Issues** (Fix before any deployment):

1. ✅ **Tenant isolation must be tested and verified** — Without this, multi-tenant data leaks are possible
2. ✅ **Core workflows must have end-to-end tests** — Agent execution, workflow creation/transitions
3. ✅ **Error scenarios must be covered** — Invalid input, missing resources, service failures
4. ✅ **Response schema validation required** — Prevents API contract violations

**High-Priority Issues** (Fix before production, with risk acceptance):

1. Input validation coverage
2. State machine transition tests
3. Audit logging verification
4. Timeout/resilience scenarios

**Recommendation**: **DO NOT DEPLOY** until Phase 1 (Critical Misalignment) is resolved.

---

## Appendix: Test Implementation Checklist

### Per-Endpoint Testing Checklist

For each endpoint, verify these test categories:

#### Agent Endpoints
- [ ] Agent List: Schema validation, pagination, filtering
- [ ] Get Agent: Found, not found, invalid format
- [ ] Execute Agent: Valid input, invalid input, timeout, error
- [ ] Agent Health: Current status, response time, degraded/unhealthy states
- [ ] Capabilities: Full list, deduplication, sorting
- [ ] Find by Capability: Found, not found, multiple results
- [ ] Copilot Chat: Single turn, multi-turn, conversation context
- [ ] Search: Query processing, filtering
- [ ] Predict: Prediction generation, confidence scoring

#### Workflow Endpoints
- [ ] Create: Valid, duplicate, invalid type, missing fields, name length
- [ ] List: Pagination,  filtering by status, sorting
- [ ] Get: Found, not found, tenant isolation
- [ ] Delete: Draft only, not draft (409), missing (404)
- [ ] Start: DRAFT→ACTIVE, not draft (409)
- [ ] Pause: ACTIVE→PAUSED, not active (409)
- [ ] Resume: PAUSED→ACTIVE, not paused (409)
- [ ] Cancel: ACTIVE/PAUSED→CANCELLED, already completed (409)
- [ ] Advance Step: Success, no next step (409), execution failure
- [ ] Goto Step: Valid step, invalid step (404/422)
- [ ] Generate Plan: Valid plan, incomplete workflow (422)
- [ ] Approve Plan: Found, expired (410), already approved (409)
- [ ] Reject Plan: Found, already rejected (409)
- [ ] Modify Plan: Before approval only, invalid steps (422)

#### Vector Endpoints
- [ ] Search: Valid query, empty (400), invalid threshold (422), no results (200)
- [ ] Hybrid Search: Both semantic and keyword, boost validation
- [ ] Find Similar: Document found, not found (404), not indexed (422)
- [ ] Index: Success, duplicate (409), too long (413), persist
- [ ] Batch Index: Multiple documents, async job, status polling
- [ ] Delete: Success, not found (404), different tenant (403)
- [ ] RAG: Retrieve + generate, citations, fallback if LLM unavailable
- [ ] RAG Chat: Multi-turn, context preservation, conversation management

---

**Document Version**: 1.0  
**Last Updated**: April 2, 2026  
**Next Review**: After Phase 1 remediation
