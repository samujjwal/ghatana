# TutorPutor End-to-End Verification Guide

**Objective**: Verify the complete flow from Node.js Platform -> gRPC -> Java AI Agent -> Mock LLM.

## 1. Prerequisites

- Docker & Docker Compose installed
- Port 50051 (Java) and 3000 (Node) available

## 2. Startup

```bash
# From workspace root
docker-compose -f docker-compose.tutorputor.yml up -d --build
```

## 3. Verify Connectivity

### A. Check Container Status

```bash
docker ps | grep tutorputor
# Expected: tutorputor-platform (healthy), tutorputor-ai-agents (Up)
```

### B. View Logs

```bash
# Follow Java logs to see startup "Listening on 50051"
docker logs -f tutorputor-ai-agents
```

## 4. Execute Test Requests

### Test Case 1: Generate Learning Path (Success)

**Request:**

```bash
curl -X POST http://localhost:3000/api/learning/pathways/generate \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Calculus",
    "goal": "Understand Limits",
    "learnerLevel": "Intermediate"
  }'
```

**Expected Response (JSON):**

```json
{
  "pathId": "lp-...",
  "nodes": [
    { "title": "Introduction to Calculus", "type": "VIDEO" },
    { "title": "Advanced Calculus", "type": "READING" }
  ]
}
```

**Verification:**

- Node Logs: `[AiClient] Generating learning path for Calculus...` -> `Success`
- Java Logs: `Received GenerateLearningPath request...`

### Test Case 2: Circuit Breaker / Fallback

**Action:** Stop the Java Service

```bash
docker stop tutorputor-ai-agents
```

**Request:** Repeat the curl command from Test Case 1.

**Expected Response (JSON):**

```json
{
  "pathId": "fallback-...",
  "title": "Standard Calculus Path",
  "description": "Generated via fallback template (AI Unavailable)"
}
```

**Verification:**

- Node Logs: `[AiClient] Error connecting...` -> `Circuit Breaker Open` -> `Fallback executed`.

## 5. Troubleshooting

- **gRPC Error 14 (Unavailable)**: The Java service is not reachable. Check `docker network inspect tutorputor-net`.
- **gRPC Error 12 (Unimplemented)**: The proto contract matches, but the method name/signature in Java doesn't match the client expectation.
