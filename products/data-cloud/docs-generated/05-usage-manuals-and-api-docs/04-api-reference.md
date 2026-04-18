# Data-Cloud API Reference

**Document ID:** DC-API-001  
**Version:** 1.0  
**Date:** 2026-04-03  
**Evidence Base**: Phase 1 Deep Inspection of products/data-cloud

---

## Executive Summary

Data-Cloud provides a **comprehensive REST API** with **85 endpoints** across **12 functional areas**. The API follows **RESTful principles** with **OpenAPI 3.1 specification**, **multi-tenant authentication**, and **comprehensive error handling**. The API demonstrates **production-ready design** with **proper HTTP semantics**, **consistent response formats**, and **security best practices**.

**Key API Characteristics:**
- **Base URL**: `/api/v1` for all stable endpoints
- **Authentication**: Required `X-Tenant-ID` header + optional bearer token
- **Response Format**: Consistent JSON with error handling
- **Real-time**: WebSocket (`/ws`) and Server-Sent Events support
- **Documentation**: Complete OpenAPI specification with examples

---

## API Overview

### Base Configuration

```
Base URL: https://datacloud.example.com/api/v1
Authentication: X-Tenant-ID header + Bearer token (production)
Content-Type: application/json
Accept: application/json
API Version: 1.0.0-SNAPSHOT
Specification: OpenAPI 3.1
```

### Authentication Headers

```http
# Required Headers
X-Tenant-ID: tenant-12345
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
Accept: application/json

# Optional Headers
X-User-ID: user-67890
X-Request-ID: req-12345
```

### Response Format

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "metadata": {
    "requestId": "req-12345",
    "timestamp": "2026-04-03T12:00:00Z",
    "version": "1.0.0"
  }
}
```

### Error Response Format

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request format",
    "details": [
      {
        "field": "name",
        "message": "Name is required"
      }
    ]
  },
  "metadata": {
    "requestId": "req-12345",
    "timestamp": "2026-04-03T12:00:00Z",
    "version": "1.0.0"
  }
}
```

---

## Entity Management API

### 1. Entity CRUD Operations

#### Create Entity
```http
POST /api/v1/entities
```

**Request:**
```json
{
  "collection": "users",
  "data": {
    "name": "John Doe",
    "email": "john@example.com",
    "age": 30
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "entity-12345",
    "collection": "users",
    "data": {
      "name": "John Doe",
      "email": "john@example.com",
      "age": 30
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "updatedAt": "2026-04-03T12:00:00Z",
    "version": 1
  }
}
```

#### Get Entity
```http
GET /api/v1/entities/{collection}/{id}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "entity-12345",
    "collection": "users",
    "data": {
      "name": "John Doe",
      "email": "john@example.com",
      "age": 30
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "updatedAt": "2026-04-03T12:00:00Z",
    "version": 1
  }
}
```

#### Update Entity
```http
PUT /api/v1/entities/{collection}/{id}
```

**Request:**
```json
{
  "data": {
    "name": "John Smith",
    "email": "john.smith@example.com",
    "age": 31
  }
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "entity-12345",
    "collection": "users",
    "data": {
      "name": "John Smith",
      "email": "john.smith@example.com",
      "age": 31
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "updatedAt": "2026-04-03T12:05:00Z",
    "version": 2
  }
}
```

#### Delete Entity
```http
DELETE /api/v1/entities/{collection}/{id}
```

**Response (204 No Content):**
```json
{
  "success": true,
  "data": null
}
```

### 2. Entity Query Operations

#### Query Entities
```http
GET /api/v1/entities/{collection}?query=...
```

**Query Parameters:**
- `query`: JSON query filter
- `offset`: Pagination offset (default: 0)
- `limit`: Pagination limit (default: 20, max: 100)
- `sort`: Sort field
- `order`: Sort order (ASC|DESC)

**Example:**
```http
GET /api/v1/entities/users?query={"age":{"$gt":25}}&sort=age&order=ASC&limit=10
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "entities": [
      {
        "id": "entity-12345",
        "collection": "users",
        "data": {
          "name": "John Doe",
          "email": "john@example.com",
          "age": 30
        },
        "createdAt": "2026-04-03T12:00:00Z",
        "updatedAt": "2026-04-03T12:00:00Z",
        "version": 1
      }
    ],
    "total": 150,
    "offset": 0,
    "limit": 10
  }
}
```

#### Count Entities
```http
GET /api/v1/entities/{collection}/count?query=...
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "count": 150
  }
}
```

### 3. Bulk Operations

#### Bulk Create
```http
POST /api/v1/entities/bulk
```

**Request:**
```json
{
  "entities": [
    {
      "collection": "users",
      "data": {
        "name": "User 1",
        "email": "user1@example.com"
      }
    },
    {
      "collection": "users",
      "data": {
        "name": "User 2",
        "email": "user2@example.com"
      }
    }
  ]
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "created": 2,
    "failed": 0,
    "results": [
      {
        "id": "entity-12346",
        "status": "created"
      },
      {
        "id": "entity-12347",
        "status": "created"
      }
    ]
  }
}
```

#### Bulk Delete
```http
DELETE /api/v1/entities/bulk
```

**Request:**
```json
{
  "entities": [
    {
      "collection": "users",
      "id": "entity-12346"
    },
    {
      "collection": "users",
      "id": "entity-12347"
    }
  ]
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "deleted": 2,
    "failed": 0,
    "results": [
      {
        "id": "entity-12346",
        "status": "deleted"
      },
      {
        "id": "entity-12347",
        "status": "deleted"
      }
    ]
  }
}
```

---

## Collection Management API

_Reality note: the current frontend collection experience uses entity-backed collection metadata endpoints under `/api/v1/entities/dc_collections`. Historical `/api/v1/collections` CRUD examples below should be treated as deprecated compatibility documentation; operator cost-report and migration routes remain registered separately._

### 1. Collection CRUD Operations

#### Create Collection
```http
POST /api/v1/entities/dc_collections
```

**Request:**
```json
{
  "name": "users",
  "displayName": "User Profiles",
  "description": "User profile information",
  "schema": {
    "fields": [
      {
        "name": "name",
        "type": "STRING",
        "required": true,
        "indexed": true
      },
      {
        "name": "email",
        "type": "STRING",
        "required": true,
        "indexed": true
      },
      {
        "name": "age",
        "type": "NUMBER",
        "required": false,
        "indexed": false
      }
    ]
  },
  "storageProfile": {
    "tier": "HOT",
    "ttlDays": 365
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "collection-12345",
    "name": "users",
    "displayName": "User Profiles",
    "description": "User profile information",
    "schema": {
      "fields": [
        {
          "name": "name",
          "type": "STRING",
          "required": true,
          "indexed": true
        },
        {
          "name": "email",
          "type": "STRING",
          "required": true,
          "indexed": true
        },
        {
          "name": "age",
          "type": "NUMBER",
          "required": false,
          "indexed": false
        }
      ]
    },
    "storageProfile": {
      "tier": "HOT",
      "ttlDays": 365
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "updatedAt": "2026-04-03T12:00:00Z",
    "recordCount": 0
  }
}
```

#### Get Collection
```http
GET /api/v1/entities/dc_collections/{collectionId}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "collection-12345",
    "name": "users",
    "displayName": "User Profiles",
    "description": "User profile information",
    "schema": {
      "fields": [
        {
          "name": "name",
          "type": "STRING",
          "required": true,
          "indexed": true
        },
        {
          "name": "email",
          "type": "STRING",
          "required": true,
          "indexed": true
        },
        {
          "name": "age",
          "type": "NUMBER",
          "required": false,
          "indexed": false
        }
      ]
    },
    "storageProfile": {
      "tier": "HOT",
      "ttlDays": 365
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "updatedAt": "2026-04-03T12:00:00Z",
    "recordCount": 150
  }
}
```

#### List Collections
```http
GET /api/v1/entities/dc_collections?limit=20&offset=0
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "collections": [
      {
        "id": "collection-12345",
        "name": "users",
        "displayName": "User Profiles",
        "description": "User profile information",
        "recordCount": 150,
        "createdAt": "2026-04-03T12:00:00Z"
      }
    ],
    "total": 5,
    "page": 0,
    "size": 20,
    "totalPages": 1
  }
}
```

---

## Event Streaming API

### 1. Event Operations

#### Append Event
```http
POST /api/v1/events
```

**Request:**
```json
{
  "type": "user.created",
  "payload": {
    "userId": "user-12345",
    "email": "john@example.com",
    "timestamp": "2026-04-03T12:00:00Z"
  },
  "headers": {
    "source": "user-service",
    "version": "1.0"
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "offset": 12345,
    "timestamp": "2026-04-03T12:00:00Z",
    "eventType": "user.created"
  }
}
```

#### Query Events
```http
GET /api/v1/events?eventTypes=user.created,user.updated&startTime=2026-04-03T00:00:00Z&endTime=2026-04-03T23:59:59Z&limit=100
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "events": [
      {
        "eventType": "user.created",
        "payload": {
          "userId": "user-12345",
          "email": "john@example.com",
          "timestamp": "2026-04-03T12:00:00Z"
        },
        "headers": {
          "source": "user-service",
          "version": "1.0"
        },
        "timestamp": "2026-04-03T12:00:00Z",
        "offset": 12345
      }
    ],
    "total": 50,
    "limit": 100
  }
}
```

#### Tail Events (Server-Sent Events)
```http
GET /api/v1/events/tail?eventTypes=user.created&fromOffset=12345
```

**Response (200 OK, text/event-stream):**
```
data: {"eventType":"user.created","payload":{"userId":"user-12346"},"timestamp":"2026-04-03T12:01:00Z","offset":12346}

data: {"eventType":"user.updated","payload":{"userId":"user-12345"},"timestamp":"2026-04-03T12:02:00Z","offset":12347}
```

---

## Analytics API

### 1. Query Operations

#### Execute SQL Query
```http
POST /api/v1/analytics/query
```

**Request:**
```json
{
  "query": "SELECT name, COUNT(*) as count FROM users WHERE age > 25 GROUP BY name ORDER BY count DESC",
  "parameters": [],
  "limit": 100
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "columns": ["name", "count"],
    "rows": [
      ["John Doe", 5],
      ["Jane Smith", 3]
    ],
    "totalRows": 2,
    "executionTime": "45ms"
  }
}
```

#### Natural Language Query
```http
POST /api/v1/analytics/natural-language
```

**Request:**
```json
{
  "query": "Show me the top 10 users by age",
  "context": {
    "collection": "users"
  }
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "translatedQuery": "SELECT name, age FROM users ORDER BY age DESC LIMIT 10",
    "results": {
      "columns": ["name", "age"],
      "rows": [
        ["John Doe", 45],
        ["Jane Smith", 42]
      ]
    },
    "confidence": 0.95
  }
}
```

### 2. Report Operations

#### Generate Report
```http
POST /api/v1/analytics/reports
```

**Request:**
```json
{
  "name": "User Age Distribution",
  "description": "Distribution of user ages",
  "query": "SELECT age, COUNT(*) as count FROM users GROUP BY age ORDER BY age",
  "type": "chart",
  "chartConfig": {
    "type": "bar",
    "xAxis": "age",
    "yAxis": "count"
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "report-12345",
    "name": "User Age Distribution",
    "description": "Distribution of user ages",
    "type": "chart",
    "chartConfig": {
      "type": "bar",
      "xAxis": "age",
      "yAxis": "count"
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "status": "ready"
  }
}
```

#### Get Report
```http
GET /api/v1/analytics/reports/{reportId}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "report-12345",
    "name": "User Age Distribution",
    "description": "Distribution of user ages",
    "type": "chart",
    "chartData": {
      "labels": ["25", "30", "35", "40", "45"],
      "datasets": [
        {
          "label": "Count",
          "data": [10, 15, 20, 12, 8]
        }
      ]
    },
    "lastGenerated": "2026-04-03T12:00:00Z",
    "status": "ready"
  }
}
```

---

## AI/ML Platform API

### 1. Feature Store Operations

#### Ingest Features
```http
POST /api/v1/features/ingest
```

**Request:**
```json
{
  "entityId": "user-12345",
  "features": {
    "age": 30,
    "income": 75000,
    "credit_score": 720,
    "last_login": "2026-04-03T12:00:00Z"
  },
  "timestamp": "2026-04-03T12:00:00Z"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "featureId": "feature-12345",
    "entityId": "user-12345",
    "timestamp": "2026-04-03T12:00:00Z",
    "status": "ingested"
  }
}
```

#### Get Features
```http
GET /api/v1/features/{entityId}?featureNames=age,income,credit_score
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "entityId": "user-12345",
    "features": {
      "age": 30,
      "income": 75000,
      "credit_score": 720
    },
    "lastUpdated": "2026-04-03T12:00:00Z"
  }
}
```

### 2. Model Registry Operations

#### Register Model
```http
POST /api/v1/models
```

**Request:**
```json
{
  "name": "user_churn_predictor",
  "version": "1.0.0",
  "description": "Predicts user churn probability",
  "modelType": "classification",
  "framework": "sklearn",
  "metadata": {
    "accuracy": 0.85,
    "precision": 0.82,
    "recall": 0.88,
    "features": ["age", "income", "credit_score"]
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "model-12345",
    "name": "user_churn_predictor",
    "version": "1.0.0",
    "description": "Predicts user churn probability",
    "modelType": "classification",
    "framework": "sklearn",
    "metadata": {
      "accuracy": 0.85,
      "precision": 0.82,
      "recall": 0.88,
      "features": ["age", "income", "credit_score"]
    },
    "createdAt": "2026-04-03T12:00:00Z",
    "status": "registered"
  }
}
```

#### Predict with Model
```http
POST /api/v1/models/{modelId}/predict
```

**Request:**
```json
{
  "features": {
    "age": 30,
    "income": 75000,
    "credit_score": 720
  }
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "prediction": 0.15,
    "confidence": 0.92,
    "explanation": {
      "feature_importance": {
        "credit_score": 0.45,
        "age": 0.35,
        "income": 0.20
      }
    },
    "modelVersion": "1.0.0",
    "timestamp": "2026-04-03T12:00:00Z"
  }
}
```

---

## Brain API (AI Assistance)

Note: these brain endpoints remain part of the documented backend surface, but the current UI no longer treats historical dashboard/brain routes as primary discovery. Operator-facing insight flows are consolidated under `/insights`.

### 1. Brain Status

#### Get Brain Status
```http
GET /api/v1/brain/status
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "status": "active",
    "capabilities": [
      "natural_language_queries",
      "recommendations",
      "anomaly_detection",
      "trend_analysis"
    ],
    "models": {
      "nlp": "gpt-4",
      "recommendation": "collaborative_filtering",
      "anomaly": "isolation_forest"
    },
    "lastUpdated": "2026-04-03T12:00:00Z"
  }
}
```

### 2. AI Assistance

#### Get Recommendations
```http
POST /api/v1/brain/recommendations
```

**Request:**
```json
{
  "context": {
    "userId": "user-12345",
    "page": "data_explorer",
    "collection": "users"
  },
  "type": "query_suggestions"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "recommendations": [
      {
        "type": "query",
        "text": "Show users with age > 30",
        "explanation": "Common query pattern for this collection"
      },
      {
        "type": "query",
        "text": "Group users by age range",
        "explanation": "Useful for demographic analysis"
      }
    ],
    "confidence": 0.87
  }
}
```

#### Explain Query
```http
POST /api/v1/brain/explain
```

**Request:**
```json
{
  "query": "SELECT * FROM users WHERE age > 30",
  "context": {
    "collection": "users"
  }
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "explanation": "This query retrieves all user records where the age field is greater than 30. It will scan the entire users collection and filter based on the age field.",
    "optimization_suggestions": [
      "Add an index on the age field for better performance",
      "Consider adding a LIMIT clause if you don't need all results"
    ],
    "estimated_cost": "medium"
  }
}
```

---

## Memory API (Agent Memory)

### 1. Memory Operations

#### Store Memory
```http
POST /api/v1/memory
```

**Request:**
```json
{
  "agentId": "agent-12345",
  "type": "episodic",
  "content": {
    "event": "user_interaction",
    "details": "User asked about data quality metrics",
    "timestamp": "2026-04-03T12:00:00Z",
    "context": "data_explorer_page"
  },
  "metadata": {
    "importance": 0.8,
    "tags": ["user_query", "data_quality"]
  }
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "memoryId": "memory-12345",
    "agentId": "agent-12345",
    "type": "episodic",
    "timestamp": "2026-04-03T12:00:00Z",
    "status": "stored"
  }
}
```

#### Retrieve Memory
```http
GET /api/v1/memory/{agentId}?type=episodic&limit=10
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "memories": [
      {
        "memoryId": "memory-12345",
        "type": "episodic",
        "content": {
          "event": "user_interaction",
          "details": "User asked about data quality metrics",
          "timestamp": "2026-04-03T12:00:00Z",
          "context": "data_explorer_page"
        },
        "metadata": {
          "importance": 0.8,
          "tags": ["user_query", "data_quality"]
        },
        "timestamp": "2026-04-03T12:00:00Z"
      }
    ],
    "total": 1,
    "limit": 10
  }
}
```

#### Search Memory
```http
POST /api/v1/memory/search
```

**Request:**
```json
{
  "agentId": "agent-12345",
  "query": "data quality metrics",
  "type": "episodic",
  "limit": 5
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "results": [
      {
        "memoryId": "memory-12345",
        "type": "episodic",
        "content": {
          "event": "user_interaction",
          "details": "User asked about data quality metrics",
          "timestamp": "2026-04-03T12:00:00Z",
          "context": "data_explorer_page"
        },
        "score": 0.95,
        "timestamp": "2026-04-03T12:00:00Z"
      }
    ],
    "total": 1,
    "query": "data quality metrics"
  }
}
```

---

## Governance API

Note: the current UI Trust Center consumes read-oriented governance and guided action flows such as retention classification, purge preview, redaction, and audit visibility. Full policy/role CRUD remains a longer-term target.

### 1. Access Control

#### Check Permissions
```http
GET /api/v1/governance/permissions?resource=users&action=read
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "allowed": true,
    "permissions": [
      "users:read",
      "users:query",
      "users:export"
    ],
    "denied": [
      "users:delete",
      "users:admin"
    ]
  }
}
```

#### Get Audit Log
```http
GET /api/v1/governance/audit?startTime=2026-04-03T00:00:00Z&endTime=2026-04-03T23:59:59Z&limit=100
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "events": [
      {
        "eventId": "audit-12345",
        "userId": "user-67890",
        "action": "entity.read",
        "resource": "users/entity-12345",
        "timestamp": "2026-04-03T12:00:00Z",
        "result": "success",
        "metadata": {
          "ip": "192.168.1.100",
          "userAgent": "Mozilla/5.0..."
        }
      }
    ],
    "total": 50,
    "limit": 100
  }
}
```

---

## Real-time API

### 1. WebSocket Connection

#### Connect to WebSocket
```javascript
// WebSocket connection
const ws = new WebSocket('ws://localhost:8080/ws');

// Set tenant ID in headers
ws.onopen = function() {
  ws.send(JSON.stringify({
    type: 'auth',
    tenantId: 'tenant-12345',
    token: 'your-jwt-token'
  }));
};

// Receive real-time updates
ws.onmessage = function(event) {
  const message = JSON.parse(event.data);
  console.log('Received:', message);
};
```

**WebSocket Message Format:**
```json
{
  "type": "entity.created",
  "collection": "users",
  "id": "entity-12345",
  "data": {
    "name": "John Doe",
    "email": "john@example.com"
  },
  "timestamp": "2026-04-03T12:00:00Z"
}
```

### 2. Server-Sent Events

#### Subscribe to Events
```http
GET /api/v1/events/subscribe?eventTypes=entity.created,entity.updated
```

**Response (200 OK, text/event-stream):**
```
data: {"type":"entity.created","collection":"users","id":"entity-12345","data":{"name":"John Doe"},"timestamp":"2026-04-03T12:00:00Z"}

data: {"type":"entity.updated","collection":"users","id":"entity-12345","data":{"name":"John Smith"},"timestamp":"2026-04-03T12:05:00Z"}
```

---

## Utility API

### 1. Health Checks

#### Health Check
```http
GET /health
```

**Response (200 OK):**
```json
{
  "status": "healthy",
  "timestamp": "2026-04-03T12:00:00Z",
  "version": "1.0.0",
  "checks": {
    "database": "healthy",
    "kafka": "healthy",
    "redis": "healthy",
    "clickhouse": "healthy"
  }
}
```

#### Readiness Check
```http
GET /ready
```

**Response (200 OK):**
```json
{
  "status": "ready",
  "timestamp": "2026-04-03T12:00:00Z",
  "checks": {
    "database": "ready",
    "kafka": "ready",
    "redis": "ready",
    "clickhouse": "ready"
  }
}
```

#### Liveness Check
```http
GET /live
```

**Response (200 OK):**
```json
{
  "status": "alive",
  "timestamp": "2026-04-03T12:00:00Z"
}
```

### 2. System Information

#### Get System Info
```http
GET /info
```

**Response (200 OK):**
```json
{
  "application": {
    "name": "Data-Cloud",
    "version": "1.0.0",
    "build": "2026-04-03T10:00:00Z"
  },
  "runtime": {
    "java": "21.0.2",
    "framework": "ActiveJ 6.0",
    "os": "Linux",
    "arch": "x86_64"
  },
  "features": {
    "multi_tenant": true,
    "event_sourcing": true,
    "ai_ml": true,
    "real_time": true
  }
}
```

#### Get Metrics
```http
GET /metrics
```

**Response (200 OK, text/plain):**
```
# HELP http_requests_total Total HTTP requests
# TYPE http_requests_total counter
http_requests_total{method="GET",status="200"} 1234
http_requests_total{method="POST",status="201"} 567

# HELP jvm_memory_used_bytes JVM memory usage
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap"} 536870912
jvm_memory_used_bytes{area="nonheap"} 67108864
```

---

## Error Handling

### Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Permission denied |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource conflict |
| `RATE_LIMITED` | 429 | Rate limit exceeded |
| `INTERNAL_ERROR` | 500 | Internal server error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

### Error Response Examples

#### Validation Error
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request format",
    "details": [
      {
        "field": "name",
        "message": "Name is required"
      },
      {
        "field": "email",
        "message": "Invalid email format"
      }
    ]
  }
}
```

#### Not Found Error
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "NOT_FOUND",
    "message": "Entity not found",
    "details": {
      "entityId": "entity-12345",
      "collection": "users"
    }
  }
}
```

#### Rate Limit Error
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "RATE_LIMITED",
    "message": "Rate limit exceeded",
    "details": {
      "limit": 100,
      "window": "1m",
      "retryAfter": 30
    }
  }
}
```

---

## API Usage Examples

### JavaScript/TypeScript Client

```typescript
// API client setup
class DataCloudClient {
  private baseUrl: string;
  private tenantId: string;
  private token?: string;

  constructor(baseUrl: string, tenantId: string, token?: string) {
    this.baseUrl = baseUrl;
    this.tenantId = tenantId;
    this.token = token;
  }

  private async request<T>(
    path: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const headers = {
      'Content-Type': 'application/json',
      'X-Tenant-ID': this.tenantId,
      ...(this.token && { Authorization: `Bearer ${this.token}` }),
      ...options.headers,
    };

    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error?.message || 'Request failed');
    }

    return response.json();
  }

  // Entity operations
  async createEntity(collection: string, data: any) {
    return this.request('/api/v1/entities', {
      method: 'POST',
      body: JSON.stringify({ collection, data }),
    });
  }

  async getEntity(collection: string, id: string) {
    return this.request(`/api/v1/entities/${collection}/${id}`);
  }

  async queryEntities(collection: string, query: any, options: any = {}) {
    const params = new URLSearchParams({
      query: JSON.stringify(query),
      ...options,
    });
    return this.request(`/api/v1/entities/${collection}?${params}`);
  }

  // Analytics operations
  async executeQuery(sql: string, parameters: any[] = []) {
    return this.request('/api/v1/analytics/query', {
      method: 'POST',
      body: JSON.stringify({ query: sql, parameters }),
    });
  }

  // Real-time operations
  connectWebSocket() {
    const ws = new WebSocket(`${this.baseUrl.replace('http', 'ws')}/ws`);
    
    ws.onopen = () => {
      ws.send(JSON.stringify({
        type: 'auth',
        tenantId: this.tenantId,
        token: this.token,
      }));
    };

    return ws;
  }
}

// Usage example
const client = new DataCloudClient(
  'https://datacloud.example.com',
  'tenant-12345',
  'your-jwt-token'
);

// Create an entity
const user = await client.createEntity('users', {
  name: 'John Doe',
  email: 'john@example.com',
  age: 30,
});

// Query entities
const users = await client.queryEntities('users', { age: { $gt: 25 } });

// Execute analytics query
const results = await client.executeQuery(
  'SELECT name, COUNT(*) as count FROM users GROUP BY name'
);
```

### Python Client

```python
import requests
import json
import websocket
import threading

class DataCloudClient:
    def __init__(self, base_url: str, tenant_id: str, token: str = None):
        self.base_url = base_url
        self.tenant_id = tenant_id
        self.token = token
        self.session = requests.Session()
        
        if token:
            self.session.headers.update({
                'Authorization': f'Bearer {token}'
            })
        
        self.session.headers.update({
            'Content-Type': 'application/json',
            'X-Tenant-ID': tenant_id
        })

    def _request(self, method: str, path: str, **kwargs):
        url = f"{self.base_url}{path}"
        response = self.session.request(method, url, **kwargs)
        response.raise_for_status()
        return response.json()

    def create_entity(self, collection: str, data: dict):
        return self._request('POST', '/api/v1/entities', 
                           json={'collection': collection, 'data': data})

    def get_entity(self, collection: str, entity_id: str):
        return self._request('GET', f'/api/v1/entities/{collection}/{entity_id}')

    def query_entities(self, collection: str, query: dict, **params):
        params['query'] = json.dumps(query)
        return self._request('GET', f'/api/v1/entities/{collection}', 
                           params=params)

    def execute_query(self, sql: str, parameters: list = None):
        return self._request('POST', '/api/v1/analytics/query',
                           json={'query': sql, 'parameters': parameters or []})

# Usage example
client = DataCloudClient(
    'https://datacloud.example.com',
    'tenant-12345',
    'your-jwt-token'
)

# Create an entity
user = client.create_entity('users', {
    'name': 'John Doe',
    'email': 'john@example.com',
    'age': 30
})

# Query entities
users = client.query_entities('users', {'age': {'$gt': 25}})

# Execute analytics query
results = client.execute_query(
    'SELECT name, COUNT(*) as count FROM users GROUP BY name'
)
```

---

## Rate Limiting

### Rate Limit Headers

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1640995200
X-RateLimit-Retry-After: 60
```

### Rate Limits by Endpoint

| Endpoint | Limit | Window |
|----------|-------|--------|
| Entity CRUD | 1000 requests/hour | 1 hour |
| Analytics Query | 100 requests/hour | 1 hour |
| Event Append | 10000 requests/hour | 1 hour |
| Bulk Operations | 100 requests/hour | 1 hour |
| AI/ML Operations | 500 requests/hour | 1 hour |

---

## API Versioning

### Version Strategy

- **Current Version**: v1.0.0
- **Version Format**: Semantic versioning (major.minor.patch)
- **Backward Compatibility**: Maintained within major versions
- **Depreciation**: 6-month notice for breaking changes
- **Sunset**: 12-month notice for version removal

### Version Headers

```http
API-Version: 1.0.0
Supported-Versions: 1.0.0
Deprecated-Versions: none
```

---

## SDK Support

### Official SDKs

| Language | Package | Version |
|----------|---------|---------|
| Java | `com.ghatana:datacloud-sdk` | 1.0.0 |
| TypeScript | `@ghatana/datacloud-sdk` | 1.0.0 |
| Python | `ghatana-datacloud-sdk` | 1.0.0 |

### SDK Installation

```bash
# Java (Gradle)
implementation 'com.ghatana:datacloud-sdk:1.0.0'

# TypeScript/JavaScript
npm install @ghatana/datacloud-sdk

# Python
pip install ghatana-datacloud-sdk
```

---

*This API reference represents the current state of the Data-Cloud API as of April 3, 2026. It should be updated as the API evolves.*
