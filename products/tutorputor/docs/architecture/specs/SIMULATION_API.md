# Simulation API Documentation

## Overview

The TutorPutor Simulation API provides a comprehensive system for creating, managing, and executing interactive educational simulations. The API supports:

- **AI-Powered Authoring**: Generate simulations from natural language prompts
- **Manual Authoring**: Create and refine simulations programmatically
- **Runtime Execution**: Run simulations with playback controls and safety guardrails
- **Evidence-Based Learning**: Capture telemetry and analytics for assessment
- **Lifecycle Management**: Track simulation status from draft to published

## Base URL

```
https://api.tutorputor.com/api/v1/simulations
```

For local development:
```
http://localhost:3000/api/v1/simulations
```

## Authentication

All API requests require authentication via Bearer token:

```http
Authorization: Bearer <your-token>
```

## API Endpoints

### Authoring Endpoints

#### Create Simulation

Create a new simulation manifest manually.

```http
POST /api/v1/simulations
```

**Request Body:**
```json
{
  "tenantId": "string",
  "userId": "string",
  "manifest": {
    "title": "Binary Search Visualization",
    "domain": "CS_DISCRETE",
    "canvas": {
      "width": 800,
      "height": 600,
      "backgroundColor": "#ffffff"
    },
    "playback": {
      "defaultSpeed": 1.0,
      "allowSpeedControl": true
    },
    "initialEntities": [...],
    "steps": [...],
    "schemaVersion": "1.0.0",
    "version": "1.0.0"
  }
}
```

**Response:**
```json
{
  "simulationId": "sim_abc123",
  "manifest": {
    "id": "sim_abc123",
    "title": "Binary Search Visualization",
    "lifecycle": {
      "status": "draft",
      "createdBy": "userId",
      "validatedAt": 1704225600000
    },
    "safety": {
      "parameterBounds": { "enforced": true },
      "executionLimits": {
        "maxSteps": 1000,
        "maxRuntimeMs": 60000
      }
    },
    ...
  }
}
```

**Status Codes:**
- `200 OK` - Simulation created successfully
- `400 Bad Request` - Invalid manifest
- `401 Unauthorized` - Missing or invalid token
- `429 Too Many Requests` - Rate limit exceeded

---

#### Generate Simulation from Natural Language

Generate a simulation manifest using AI from a natural language prompt.

```http
POST /api/v1/simulations/generate
```

**Request Body:**
```json
{
  "tenantId": "string",
  "userId": "string",
  "prompt": "Create a simulation showing binary search on a sorted array",
  "domain": "CS_DISCRETE",
  "constraints": {
    "maxEntities": 10,
    "maxSteps": 20
  }
}
```

**Response:**
```json
{
  "manifest": {
    "id": "sim_xyz789",
    "title": "Binary Search Simulation",
    "domain": "CS_DISCRETE",
    "lifecycle": {
      "status": "draft",
      "createdBy": "ai",
      "validatedAt": null
    },
    ...
  },
  "confidence": 0.85,
  "needsReview": false,
  "suggestions": [
    "Consider adding more intermediate steps",
    "Add accessibility metadata"
  ]
}
```

**Rate Limits:**
- 10 requests per minute per user

**Status Codes:**
- `200 OK` - Manifest generated successfully
- `400 Bad Request` - Invalid prompt or constraints
- `429 Too Many Requests` - Rate limit exceeded

---

#### Retrieve Simulation

Get a simulation manifest by ID.

```http
GET /api/v1/simulations/:id
```

**Response:**
```json
{
  "id": "sim_abc123",
  "manifest": {...},
  "metadata": {
    "domain": "CS_DISCRETE",
    "title": "Binary Search Visualization",
    "version": "1.0.0",
    "createdAt": "2024-01-02T12:00:00Z",
    "updatedAt": "2024-01-02T12:30:00Z"
  }
}
```

**Status Codes:**
- `200 OK` - Simulation found
- `404 Not Found` - Simulation not found

---

#### Refine Simulation

Refine an existing manifest using natural language.

```http
POST /api/v1/simulations/refine
```

**Request Body:**
```json
{
  "tenantId": "string",
  "userId": "string",
  "manifest": {...},
  "refinement": "Add more intermediate steps and slow down the animation",
  "targetSteps": [0, 1, 2]
}
```

**Response:**
```json
{
  "manifest": {...},
  "confidence": 0.92,
  "needsReview": false,
  "suggestions": []
}
```

---

### Runtime Endpoints

#### Create Session

Create a new simulation session for execution.

```http
POST /api/v1/simulations/sessions
```

**Request Body:**
```json
{
  "simulationId": "sim_abc123"
}
```

Or provide manifest directly:
```json
{
  "manifest": {...}
}
```

**Response:**
```json
{
  "sessionId": "session_def456",
  "simulationId": "sim_abc123",
  "totalSteps": 15
}
```

**Session Quotas:**
- Maximum 10 concurrent sessions per user

**Status Codes:**
- `200 OK` - Session created
- `403 Forbidden` - Cannot execute archived simulation
- `404 Not Found` - Simulation not found
- `429 Too Many Requests` - Session quota exceeded

---

#### Execute Step

Execute a step in the simulation (forward or backward).

```http
POST /api/v1/simulations/sessions/:sessionId/step
```

**Request Body:**
```json
{
  "direction": "forward"
}
```

**Response:**
```json
{
  "keyframe": {
    "time": 1000,
    "entities": [
      {
        "id": "node-1",
        "type": "node",
        "x": 100,
        "y": 100,
        "value": 5,
        "visual": {
          "fillColor": "#ff0000",
          "strokeColor": "#000000"
        }
      }
    ],
    "annotations": []
  }
}
```

**Rate Limits:**
- 100 requests per minute per user

---

#### Seek to Step

Jump to a specific step in the simulation.

```http
POST /api/v1/simulations/sessions/:sessionId/seek
```

**Request Body:**
```json
{
  "stepIndex": 5
}
```

**Response:**
```json
{
  "keyframe": {...}
}
```

---

#### Terminate Session

End a simulation session and release resources.

```http
DELETE /api/v1/simulations/sessions/:sessionId
```

**Response:**
```json
{
  "success": true
}
```

---

### Discovery Endpoints

#### List Available Kernels

Get a list of available simulation kernels (domains).

```http
GET /api/v1/simulations/kernels
```

**Response:**
```json
{
  "kernels": [
    {
      "domain": "CS_DISCRETE",
      "available": true
    },
    {
      "domain": "PHYSICS",
      "available": true
    },
    {
      "domain": "CHEMISTRY",
      "available": true
    },
    {
      "domain": "BIOLOGY",
      "available": true
    },
    {
      "domain": "MEDICINE",
      "available": true
    }
  ]
}
```

---

## Data Models

### SimulationManifest

The complete specification for a simulation.

```typescript
interface SimulationManifest {
  id: SimulationId;
  version: string;
  title: string;
  description?: string;
  domain: SimulationDomain;
  
  // Author & review
  authorId: UserId;
  tenantId: TenantId;
  needsReview?: boolean;
  reviewNotes?: string;
  
  // Canvas & playback
  canvas: CanvasConfig;
  playback: PlaybackConfig;
  
  // Initial state
  initialEntities: SimEntity[];
  
  // Simulation sequence
  steps: SimulationStep[];
  
  // Lifecycle tracking
  lifecycle?: SimulationLifecycle;
  
  // Safety constraints
  safety?: SimulationSafety;
  
  // Deterministic replay
  replay?: SimulationReplay;
  
  // Evidence-Centered Design metadata
  ecd?: ECDMetadata;
  
  // Rendering capabilities
  rendering?: RenderingCapabilities;
  
  // Compliance metadata
  compliance?: ComplianceMetadata;
  
  // Timestamps
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
  
  schemaVersion: string;
}
```

### SimulationLifecycle

Tracks the lifecycle status of a simulation.

```typescript
interface SimulationLifecycle {
  status: 'draft' | 'validated' | 'published' | 'archived';
  createdBy: 'userId' | 'ai' | 'template';
  validatedAt?: number;
  publishedAt?: number;
}
```

### SimulationSafety

Safety constraints for execution.

```typescript
interface SimulationSafety {
  parameterBounds: {
    enforced: boolean;
    maxIterations?: number;
  };
  executionLimits: {
    maxSteps: number;
    maxRuntimeMs: number;
  };
}
```

### ECDMetadata

Evidence-Centered Design metadata for assessment.

```typescript
interface ECDMetadata {
  claims: Array<{
    id: string;
    description: string;
    evidenceIds: string[];
  }>;
  evidence: Array<{
    id: string;
    source: 'telemetry.parameterChange' | 'telemetry.timeOnTask' | 'grading.stateComparison';
    tolerance?: number;
    requiredForClaim: string[];
  }>;
  tasks: Array<{
    id: string;
    type: 'prediction' | 'manipulation' | 'explanation' | 'design' | 'diagnosis';
    claimIds: string[];
  }>;
}
```

---

## Simulation Domains

Supported simulation domains:

- `CS_DISCRETE` - Discrete algorithms and data structures
- `PHYSICS` - Physics simulations (mechanics, kinematics)
- `CHEMISTRY` - Chemical reactions and molecular structures
- `BIOLOGY` - Biological systems and processes
- `MEDICINE` - Pharmacokinetics and medical simulations
- `ECONOMICS` - Economic models and systems
- `ENGINEERING` - Engineering systems
- `MATHEMATICS` - Mathematical visualizations

---

## Error Handling

All errors follow a consistent format:

```json
{
  "error": "Error message",
  "statusCode": 400,
  "details": {
    "field": "Additional context"
  }
}
```

### Common Error Codes

- `400 Bad Request` - Invalid input or validation error
- `401 Unauthorized` - Missing or invalid authentication
- `403 Forbidden` - Operation not allowed (e.g., archived simulation)
- `404 Not Found` - Resource not found
- `429 Too Many Requests` - Rate limit or quota exceeded
- `500 Internal Server Error` - Server error

---

## Rate Limits

| Endpoint | Limit |
|----------|-------|
| Generate Simulation | 10 requests/min per user |
| Execute Step | 100 requests/min per user |
| Create Session | 10 concurrent sessions per user |
| Other Endpoints | 60 requests/min per user |

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
X-RateLimit-Reset: 2024-01-02T12:01:00Z
```

---

## Best Practices

### 1. Use Lifecycle Status

Always check and respect lifecycle status:
- `draft` - Under development, may change
- `validated` - Reviewed and approved
- `published` - Production-ready
- `archived` - Deprecated, cannot execute

### 2. Implement Safety Constraints

Always define safety constraints:
```json
{
  "safety": {
    "parameterBounds": { "enforced": true },
    "executionLimits": {
      "maxSteps": 1000,
      "maxRuntimeMs": 60000
    }
  }
}
```

### 3. Include ECD Metadata

For evidence-based learning, include ECD metadata:
```json
{
  "ecd": {
    "claims": [...],
    "evidence": [...],
    "tasks": [...]
  }
}
```

### 4. Handle Rate Limits

Implement exponential backoff for rate limit errors:
```javascript
async function withRetry(fn, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await fn();
    } catch (error) {
      if (error.statusCode === 429 && i < maxRetries - 1) {
        await sleep(Math.pow(2, i) * 1000);
        continue;
      }
      throw error;
    }
  }
}
```

### 5. Clean Up Sessions

Always terminate sessions when done:
```javascript
try {
  // Use session
  await executeSteps(sessionId);
} finally {
  await terminateSession(sessionId);
}
```

---

## Examples

### Complete Workflow Example

```javascript
// 1. Generate simulation from prompt
const generated = await fetch('/api/v1/simulations/generate', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    tenantId: 'my-tenant',
    userId: 'user-123',
    prompt: 'Show binary search on array [1,3,5,7,9]',
    domain: 'CS_DISCRETE'
  })
});

const { manifest, confidence } = await generated.json();

// 2. Refine if needed
if (confidence < 0.9) {
  const refined = await fetch('/api/v1/simulations/refine', {
    method: 'POST',
    body: JSON.stringify({
      manifest,
      refinement: 'Add more detailed step descriptions'
    })
  });
  manifest = (await refined.json()).manifest;
}

// 3. Save simulation
const saved = await fetch('/api/v1/simulations', {
  method: 'POST',
  body: JSON.stringify({
    tenantId: 'my-tenant',
    userId: 'user-123',
    manifest
  })
});

const { simulationId } = await saved.json();

// 4. Create session
const session = await fetch('/api/v1/simulations/sessions', {
  method: 'POST',
  body: JSON.stringify({ simulationId })
});

const { sessionId, totalSteps } = await session.json();

// 5. Execute simulation
for (let i = 0; i < totalSteps; i++) {
  const step = await fetch(`/api/v1/simulations/sessions/${sessionId}/step`, {
    method: 'POST',
    body: JSON.stringify({ direction: 'forward' })
  });
  
  const { keyframe } = await step.json();
  renderKeyframe(keyframe);
  await sleep(1000);
}

// 6. Clean up
await fetch(`/api/v1/simulations/sessions/${sessionId}`, {
  method: 'DELETE'
});
```

---

## Support

For API support:
- Documentation: https://docs.tutorputor.com/simulation-api
- GitHub Issues: https://github.com/tutorputor/simulation-engine/issues
- Email: api-support@tutorputor.com

---

## Changelog

### v1.0.0 (2024-01-02)
- Initial release
- AI-powered generation
- Lifecycle management
- Safety guardrails
- ECD metadata support
- Runtime execution with playback controls
