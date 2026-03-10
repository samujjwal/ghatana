Below is a **canonical, implementation-ready v1** for:

* **JSON Schemas** for `AgentDefinition`, `AgentRun`, `MemoryRecord`, and `EventEnvelope`
* **Prisma schema proposal** for a **YAPPC + AEP split**

It is aligned with the requirements-tool direction we already established: PostgreSQL + Prisma, workspaces with one owner and multiple members/roles/personas, project-scoped requirements, versions, AI chat, and audit logging.  It also preserves the sprint direction around GraphQL, requirement CRUD, AI parsing, version history/export, persona-aware prompting, diagrams, and audit/activity tracking. 

---

# 1) Canonical JSON Schemas

## `agent-definition.schema.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://yappc.dev/schemas/agent-definition.schema.json",
  "title": "AgentDefinition",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "agentName",
    "agentType",
    "domain",
    "version",
    "displayName",
    "description",
    "triggers",
    "inputs",
    "outputs",
    "successCriteria",
    "failureHandling",
    "observability",
    "security",
    "execution"
  ],
  "properties": {
    "id": {
      "type": "string",
      "pattern": "^[a-zA-Z0-9._:-]{3,128}$"
    },
    "agentName": {
      "type": "string",
      "pattern": "^[A-Z][A-Za-z0-9]+(Agent|Orchestrator|Unit)$"
    },
    "displayName": {
      "type": "string",
      "minLength": 3,
      "maxLength": 120
    },
    "agentType": {
      "type": "string",
      "enum": [
        "ORCHESTRATOR",
        "CAPABILITY_AGENT",
        "TASK_AGENT",
        "MICRO_AGENT",
        "SYSTEM_AGENT"
      ]
    },
    "domain": {
      "type": "string",
      "enum": [
        "DISCOVERY",
        "IDEATION",
        "STRATEGY",
        "REQUIREMENTS",
        "UX",
        "ARCHITECTURE",
        "ENGINEERING",
        "DATA_AI",
        "RUNTIME",
        "QA",
        "SECURITY",
        "RELEASE",
        "OPERATIONS",
        "ANALYTICS",
        "ENHANCEMENT",
        "GOVERNANCE"
      ]
    },
    "version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$"
    },
    "description": {
      "type": "string",
      "minLength": 10
    },
    "ownerTeam": {
      "type": "string",
      "maxLength": 120
    },
    "tags": {
      "type": "array",
      "items": { "type": "string", "maxLength": 64 },
      "uniqueItems": true
    },
    "parentAgent": {
      "type": "string"
    },
    "triggers": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": {
            "type": "string",
            "enum": [
              "MANUAL",
              "EVENT",
              "SCHEDULE",
              "DEPENDENCY_COMPLETION",
              "POLICY",
              "WEBHOOK"
            ]
          },
          "eventName": { "type": "string", "maxLength": 150 },
          "cron": { "type": "string", "maxLength": 120 },
          "condition": { "type": "string", "maxLength": 500 }
        }
      }
    },
    "inputs": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["name", "type", "required"],
        "properties": {
          "name": { "type": "string", "maxLength": 80 },
          "type": { "type": "string", "maxLength": 80 },
          "required": { "type": "boolean" },
          "description": { "type": "string", "maxLength": 300 },
          "source": {
            "type": "string",
            "enum": [
              "USER_INPUT",
              "EVENT_PAYLOAD",
              "DATABASE",
              "MEMORY",
              "SEARCH",
              "TOOL",
              "MODEL",
              "SYSTEM_CONTEXT"
            ]
          }
        }
      }
    },
    "outputs": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["name", "type"],
        "properties": {
          "name": { "type": "string", "maxLength": 80 },
          "type": { "type": "string", "maxLength": 80 },
          "description": { "type": "string", "maxLength": 300 },
          "emitsEvent": { "type": "boolean", "default": false }
        }
      }
    },
    "successCriteria": {
      "type": "array",
      "items": { "type": "string", "maxLength": 200 },
      "minItems": 1
    },
    "preconditions": {
      "type": "array",
      "items": { "type": "string", "maxLength": 200 }
    },
    "failureHandling": {
      "type": "object",
      "additionalProperties": false,
      "required": ["retryPolicy", "fallbackMode"],
      "properties": {
        "retryPolicy": {
          "type": "string",
          "enum": [
            "NO_RETRY",
            "RETRY_TRANSIENT_ONCE",
            "RETRY_TRANSIENT_TWICE",
            "EXPONENTIAL_BACKOFF",
            "CUSTOM"
          ]
        },
        "maxRetries": {
          "type": "integer",
          "minimum": 0,
          "maximum": 20
        },
        "fallbackMode": {
          "type": "string",
          "enum": [
            "FAIL_FAST",
            "MANUAL_REVIEW",
            "FALLBACK_AGENT",
            "PARTIAL_RESULT",
            "DEAD_LETTER"
          ]
        },
        "fallbackAgent": { "type": "string" },
        "deadLetterTopic": { "type": "string" }
      }
    },
    "observability": {
      "type": "object",
      "additionalProperties": false,
      "required": ["audit", "metrics", "tracing"],
      "properties": {
        "audit": { "type": "boolean" },
        "metrics": {
          "type": "array",
          "items": { "type": "string", "maxLength": 80 }
        },
        "tracing": { "type": "boolean" },
        "logLevel": {
          "type": "string",
          "enum": ["DEBUG", "INFO", "WARN", "ERROR"]
        }
      }
    },
    "security": {
      "type": "object",
      "additionalProperties": false,
      "required": ["dataClassification", "accessScope"],
      "properties": {
        "dataClassification": {
          "type": "string",
          "enum": [
            "PUBLIC",
            "INTERNAL",
            "CONFIDENTIAL",
            "RESTRICTED",
            "SENSITIVE"
          ]
        },
        "accessScope": {
          "type": "string",
          "enum": [
            "GLOBAL",
            "WORKSPACE",
            "PROJECT",
            "USER",
            "RUN_SCOPED"
          ]
        },
        "piiHandling": {
          "type": "string",
          "enum": [
            "NONE",
            "MASK",
            "REDACT",
            "TOKENIZE"
          ]
        },
        "retentionClass": {
          "type": "string",
          "enum": [
            "EPHEMERAL",
            "SHORT",
            "STANDARD",
            "LONG",
            "LEGAL_HOLD"
          ]
        }
      }
    },
    "execution": {
      "type": "object",
      "additionalProperties": false,
      "required": ["mode", "humanInLoop", "timeoutMs"],
      "properties": {
        "mode": {
          "type": "string",
          "enum": [
            "DETERMINISTIC",
            "AI_ASSISTED",
            "HYBRID",
            "HUMAN_APPROVAL_REQUIRED"
          ]
        },
        "humanInLoop": { "type": "boolean" },
        "timeoutMs": {
          "type": "integer",
          "minimum": 100,
          "maximum": 3600000
        },
        "parallelizable": { "type": "boolean", "default": false },
        "idempotent": { "type": "boolean", "default": false }
      }
    },
    "toolContracts": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["toolName", "required"],
        "properties": {
          "toolName": { "type": "string", "maxLength": 120 },
          "required": { "type": "boolean" },
          "schemaRef": { "type": "string", "format": "uri-reference" }
        }
      }
    },
    "modelPolicy": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "allowedModels": {
          "type": "array",
          "items": { "type": "string", "maxLength": 120 }
        },
        "maxTokens": {
          "type": "integer",
          "minimum": 1,
          "maximum": 200000
        },
        "temperature": {
          "type": "number",
          "minimum": 0,
          "maximum": 2
        }
      }
    }
  }
}
```

---

## `agent-run.schema.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://yappc.dev/schemas/agent-run.schema.json",
  "title": "AgentRun",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "runId",
    "agentName",
    "agentVersion",
    "status",
    "triggerType",
    "startedAt",
    "workspaceId",
    "projectId",
    "input",
    "executionMode"
  ],
  "properties": {
    "runId": {
      "type": "string",
      "pattern": "^[a-zA-Z0-9._:-]{8,128}$"
    },
    "parentRunId": { "type": ["string", "null"] },
    "rootRunId": { "type": ["string", "null"] },
    "agentName": { "type": "string" },
    "agentVersion": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$"
    },
    "agentType": {
      "type": "string",
      "enum": [
        "ORCHESTRATOR",
        "CAPABILITY_AGENT",
        "TASK_AGENT",
        "MICRO_AGENT",
        "SYSTEM_AGENT"
      ]
    },
    "status": {
      "type": "string",
      "enum": [
        "REQUESTED",
        "QUEUED",
        "RUNNING",
        "WAITING_FOR_HUMAN",
        "COMPLETED",
        "FAILED",
        "CANCELLED",
        "TIMED_OUT",
        "PARTIAL",
        "ROLLED_BACK"
      ]
    },
    "triggerType": {
      "type": "string",
      "enum": [
        "MANUAL",
        "EVENT",
        "SCHEDULE",
        "DEPENDENCY_COMPLETION",
        "POLICY",
        "WEBHOOK"
      ]
    },
    "workspaceId": { "type": "string" },
    "projectId": { "type": ["string", "null"] },
    "userId": { "type": ["string", "null"] },
    "persona": { "type": ["string", "null"] },
    "correlationId": { "type": ["string", "null"] },
    "causationId": { "type": ["string", "null"] },
    "executionMode": {
      "type": "string",
      "enum": [
        "DETERMINISTIC",
        "AI_ASSISTED",
        "HYBRID",
        "HUMAN_APPROVAL_REQUIRED"
      ]
    },
    "priority": {
      "type": "string",
      "enum": ["LOW", "NORMAL", "HIGH", "URGENT"]
    },
    "input": {
      "type": "object"
    },
    "normalizedInput": {
      "type": ["object", "null"]
    },
    "output": {
      "type": ["object", "null"]
    },
    "resultSummary": {
      "type": ["string", "null"],
      "maxLength": 4000
    },
    "errorCode": { "type": ["string", "null"] },
    "errorMessage": { "type": ["string", "null"] },
    "retryCount": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "startedAt": {
      "type": "string",
      "format": "date-time"
    },
    "endedAt": {
      "type": ["string", "null"],
      "format": "date-time"
    },
    "durationMs": {
      "type": ["integer", "null"],
      "minimum": 0
    },
    "toolInvocations": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["toolName", "status", "startedAt"],
        "properties": {
          "toolName": { "type": "string" },
          "status": {
            "type": "string",
            "enum": ["REQUESTED", "RUNNING", "COMPLETED", "FAILED", "SKIPPED"]
          },
          "requestRef": { "type": ["string", "null"] },
          "responseRef": { "type": ["string", "null"] },
          "startedAt": { "type": "string", "format": "date-time" },
          "endedAt": { "type": ["string", "null"], "format": "date-time" }
        }
      }
    },
    "memoryReads": {
      "type": "array",
      "items": { "type": "string" }
    },
    "memoryWrites": {
      "type": "array",
      "items": { "type": "string" }
    },
    "evaluation": {
      "type": ["object", "null"],
      "additionalProperties": false,
      "properties": {
        "confidence": {
          "type": "number",
          "minimum": 0,
          "maximum": 1
        },
        "qualityScore": {
          "type": "number",
          "minimum": 0,
          "maximum": 1
        },
        "policyPassed": { "type": "boolean" },
        "humanApproved": { "type": ["boolean", "null"] }
      }
    },
    "cost": {
      "type": ["object", "null"],
      "additionalProperties": false,
      "properties": {
        "inputTokens": { "type": "integer", "minimum": 0 },
        "outputTokens": { "type": "integer", "minimum": 0 },
        "cacheHit": { "type": "boolean" },
        "estimatedUsd": { "type": "number", "minimum": 0 }
      }
    },
    "audit": {
      "type": ["object", "null"],
      "additionalProperties": false,
      "properties": {
        "recorded": { "type": "boolean" },
        "auditLogId": { "type": ["string", "null"] }
      }
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "uniqueItems": true
    }
  }
}
```

---

## `memory-record.schema.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://yappc.dev/schemas/memory-record.schema.json",
  "title": "MemoryRecord",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "memoryId",
    "memoryType",
    "scope",
    "workspaceId",
    "status",
    "content",
    "createdAt"
  ],
  "properties": {
    "memoryId": {
      "type": "string",
      "pattern": "^[a-zA-Z0-9._:-]{8,128}$"
    },
    "memoryType": {
      "type": "string",
      "enum": [
        "WORKING",
        "EPISODIC",
        "SEMANTIC",
        "PROCEDURAL",
        "REFLECTION",
        "DECISION",
        "POLICY",
        "KNOWLEDGE"
      ]
    },
    "scope": {
      "type": "string",
      "enum": [
        "GLOBAL",
        "WORKSPACE",
        "PROJECT",
        "RUN",
        "USER"
      ]
    },
    "workspaceId": { "type": "string" },
    "projectId": { "type": ["string", "null"] },
    "runId": { "type": ["string", "null"] },
    "userId": { "type": ["string", "null"] },
    "agentName": { "type": ["string", "null"] },
    "title": {
      "type": ["string", "null"],
      "maxLength": 200
    },
    "summary": {
      "type": ["string", "null"],
      "maxLength": 5000
    },
    "content": {
      "type": "object"
    },
    "contentText": {
      "type": ["string", "null"],
      "maxLength": 50000
    },
    "embeddingRef": {
      "type": ["string", "null"]
    },
    "sourceRefs": {
      "type": "array",
      "items": { "type": "string", "maxLength": 200 }
    },
    "tags": {
      "type": "array",
      "items": { "type": "string", "maxLength": 64 },
      "uniqueItems": true
    },
    "importance": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "status": {
      "type": "string",
      "enum": [
        "ACTIVE",
        "STALE",
        "ARCHIVED",
        "DEPRECATED",
        "DELETED"
      ]
    },
    "retentionClass": {
      "type": "string",
      "enum": [
        "EPHEMERAL",
        "SHORT",
        "STANDARD",
        "LONG",
        "LEGAL_HOLD"
      ]
    },
    "expiresAt": {
      "type": ["string", "null"],
      "format": "date-time"
    },
    "supersedesMemoryId": {
      "type": ["string", "null"]
    },
    "createdAt": {
      "type": "string",
      "format": "date-time"
    },
    "updatedAt": {
      "type": ["string", "null"],
      "format": "date-time"
    },
    "lastAccessedAt": {
      "type": ["string", "null"],
      "format": "date-time"
    },
    "accessCount": {
      "type": "integer",
      "minimum": 0
    },
    "security": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "dataClassification": {
          "type": "string",
          "enum": [
            "PUBLIC",
            "INTERNAL",
            "CONFIDENTIAL",
            "RESTRICTED",
            "SENSITIVE"
          ]
        },
        "piiPresent": { "type": "boolean" },
        "redactionApplied": { "type": "boolean" }
      }
    }
  }
}
```

---

## `event-envelope.schema.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://yappc.dev/schemas/event-envelope.schema.json",
  "title": "EventEnvelope",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "eventId",
    "eventType",
    "eventVersion",
    "timestamp",
    "source",
    "workspaceId",
    "payload"
  ],
  "properties": {
    "eventId": {
      "type": "string",
      "pattern": "^[a-zA-Z0-9._:-]{8,128}$"
    },
    "eventType": {
      "type": "string",
      "pattern": "^[a-z0-9_.-]{3,150}$"
    },
    "eventVersion": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "source": {
      "type": "string",
      "maxLength": 150
    },
    "workspaceId": {
      "type": "string"
    },
    "projectId": {
      "type": ["string", "null"]
    },
    "correlationId": {
      "type": ["string", "null"]
    },
    "causationId": {
      "type": ["string", "null"]
    },
    "actorType": {
      "type": ["string", "null"],
      "enum": [
        "USER",
        "AGENT",
        "SYSTEM",
        "WEBHOOK",
        null
      ]
    },
    "actorId": {
      "type": ["string", "null"]
    },
    "persona": {
      "type": ["string", "null"]
    },
    "entityType": {
      "type": ["string", "null"],
      "maxLength": 120
    },
    "entityId": {
      "type": ["string", "null"]
    },
    "severity": {
      "type": "string",
      "enum": ["DEBUG", "INFO", "WARN", "ERROR", "CRITICAL"]
    },
    "payload": {
      "type": "object"
    },
    "metadata": {
      "type": "object",
      "additionalProperties": true
    },
    "audit": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "record": { "type": "boolean" },
        "immutable": { "type": "boolean" }
      }
    },
    "tags": {
      "type": "array",
      "items": { "type": "string", "maxLength": 64 },
      "uniqueItems": true
    }
  }
}
```

---

# 2) Prisma Schema Proposal for YAPPC + AEP Split

The cleanest split is:

* **YAPPC Prisma schema**: user/workspace/project/requirements/versioning/audit/chat/planning/traceability
* **AEP Prisma schema**: agent registry, runs, steps, tool calls, memory, policy, evaluation, traces
* Optional later:

  * **Data-cloud Prisma schema** for telemetry, embeddings, analytics, experiment data

---

## `yappc.prisma`

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum WorkspaceRole {
  OWNER
  ADMIN
  EDITOR
  CONTRIBUTOR
  VIEWER
}

enum PersonaType {
  PRODUCT_MANAGER
  PRODUCT_OWNER
  BUSINESS_ANALYST
  ARCHITECT
  UX_DESIGNER
  FRONTEND_ENGINEER
  BACKEND_ENGINEER
  QA_ENGINEER
  DEVOPS_ENGINEER
  SRE
  SECURITY_ENGINEER
  DATA_ENGINEER
  AI_ENGINEER
  EXECUTIVE
  COMPLIANCE_OFFICER
  SUPPORT
  CUSTOM
}

enum RequirementType {
  FUNCTIONAL
  NON_FUNCTIONAL
  BUSINESS
  USER_STORY
  CONSTRAINT
  SECURITY
  DATA
  INTEGRATION
  UX
  COMPLIANCE
  OTHER
}

enum RequirementStatus {
  DRAFT
  PROPOSED
  IN_REVIEW
  APPROVED
  REJECTED
  IMPLEMENTED
  DEPRECATED
}

enum RequirementPriority {
  LOW
  MEDIUM
  HIGH
  CRITICAL
}

enum VersionKind {
  SNAPSHOT
  FORK
  RESTORE_POINT
  EXPORT
}

enum DiagramType {
  FLOWCHART
  ERD
  USER_FLOW
  SYSTEM_ARCHITECTURE
  SEQUENCE
  STATE
  OTHER
}

enum AuditActionType {
  CREATE
  UPDATE
  DELETE
  APPROVE
  REJECT
  RESTORE
  EXPORT
  LOGIN
  LOGOUT
  INVITE_MEMBER
  REMOVE_MEMBER
  CHANGE_ROLE
  CHANGE_PERSONA
  OTHER
}

enum TraceLinkType {
  REQUIREMENT_TO_STORY
  REQUIREMENT_TO_TEST
  REQUIREMENT_TO_VERSION
  REQUIREMENT_TO_DIAGRAM
  REQUIREMENT_TO_ARTIFACT
  STORY_TO_TEST
  STORY_TO_VERSION
}

enum ApprovalStatus {
  PENDING
  APPROVED
  REJECTED
  CANCELLED
}

model User {
  id              String             @id @default(cuid())
  email           String             @unique
  username        String?            @unique
  displayName     String?
  passwordHash    String?
  isActive        Boolean            @default(true)
  preferences     Json?
  createdAt       DateTime           @default(now())
  updatedAt       DateTime           @updatedAt

  workspaceMembers WorkspaceMember[]
  projectsCreated  Project[]         @relation("ProjectCreatedBy")
  requirementsCreated Requirement[]  @relation("RequirementCreatedBy")
  requirementVersionsCreated RequirementVersion[] @relation("RequirementVersionCreatedBy")
  versionsCreated   ProjectVersion[] @relation("ProjectVersionCreatedBy")
  aiChats           AIChat[]
  auditLogs         AuditLog[]       @relation("AuditActor")
  approvalsRequested ApprovalRequest[] @relation("ApprovalRequestedBy")
  approvalsDecided   ApprovalRequest[] @relation("ApprovalDecidedBy")
}

model Workspace {
  id              String             @id @default(cuid())
  slug            String             @unique
  name            String
  description     String?
  ownerUserId     String
  settings        Json?
  createdAt       DateTime           @default(now())
  updatedAt       DateTime           @updatedAt

  owner           User               @relation(fields: [ownerUserId], references: [id], onDelete: Restrict)
  members         WorkspaceMember[]
  projects        Project[]
  aiChats         AIChat[]
  auditLogs       AuditLog[]
  approvalRequests ApprovalRequest[]

  @@index([ownerUserId])
}

model WorkspaceMember {
  id              String        @id @default(cuid())
  workspaceId     String
  userId          String
  role            WorkspaceRole
  persona         PersonaType
  customPersona   String?
  joinedAt        DateTime      @default(now())
  invitedByUserId String?
  isActive        Boolean       @default(true)

  workspace       Workspace     @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  user            User          @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@unique([workspaceId, userId])
  @@index([workspaceId, role])
  @@index([userId])
}

model Project {
  id              String             @id @default(cuid())
  workspaceId     String
  name            String
  slug            String
  description     String?
  createdByUserId String?
  settings        Json?
  createdAt       DateTime           @default(now())
  updatedAt       DateTime           @updatedAt
  archivedAt      DateTime?

  workspace       Workspace          @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  createdBy       User?              @relation("ProjectCreatedBy", fields: [createdByUserId], references: [id], onDelete: SetNull)

  requirements    Requirement[]
  versions        ProjectVersion[]
  diagrams        Diagram[]
  aiChats         AIChat[]
  auditLogs       AuditLog[]
  traceLinks      TraceLink[]
  epics           Epic[]
  stories         Story[]
  approvalRequests ApprovalRequest[]

  @@unique([workspaceId, slug])
  @@index([workspaceId])
}

model Requirement {
  id              String              @id @default(cuid())
  workspaceId     String
  projectId       String
  createdByUserId String?
  title           String
  description     String
  type            RequirementType
  status          RequirementStatus   @default(DRAFT)
  priority        RequirementPriority @default(MEDIUM)
  source          String?
  tags            String[]
  rationale       String?
  acceptanceCriteria Json?
  metadata        Json?
  createdAt       DateTime            @default(now())
  updatedAt       DateTime            @updatedAt
  archivedAt      DateTime?

  workspace       Workspace           @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project         Project             @relation(fields: [projectId], references: [id], onDelete: Cascade)
  createdBy       User?               @relation("RequirementCreatedBy", fields: [createdByUserId], references: [id], onDelete: SetNull)

  versions        RequirementVersion[]
  outgoingTraceLinks TraceLink[]      @relation("TraceSourceRequirement")
  incomingTraceLinks TraceLink[]      @relation("TraceTargetRequirement")

  @@index([workspaceId, projectId])
  @@index([projectId, status, type])
}

model RequirementVersion {
  id                String       @id @default(cuid())
  requirementId     String
  versionNumber     Int
  createdByUserId   String?
  changeSummary     String?
  title             String
  description       String
  status            RequirementStatus
  priority          RequirementPriority
  snapshot          Json
  createdAt         DateTime     @default(now())

  requirement       Requirement  @relation(fields: [requirementId], references: [id], onDelete: Cascade)
  createdBy         User?        @relation("RequirementVersionCreatedBy", fields: [createdByUserId], references: [id], onDelete: SetNull)

  @@unique([requirementId, versionNumber])
  @@index([requirementId])
}

model ProjectVersion {
  id                String       @id @default(cuid())
  workspaceId       String
  projectId         String
  kind              VersionKind
  versionLabel      String
  createdByUserId   String?
  summary           String?
  snapshot          Json
  exportFormat      String?
  exportUri         String?
  createdAt         DateTime     @default(now())

  workspace         Workspace    @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project      @relation(fields: [projectId], references: [id], onDelete: Cascade)
  createdBy         User?        @relation("ProjectVersionCreatedBy", fields: [createdByUserId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId])
  @@index([projectId, createdAt])
}

model Diagram {
  id                String       @id @default(cuid())
  workspaceId       String
  projectId         String
  type              DiagramType
  title             String
  description       String?
  sourceRequirementIds String[]
  content           String
  format            String       // mermaid, svg, png, json
  metadata          Json?
  createdAt         DateTime     @default(now())
  updatedAt         DateTime     @updatedAt

  workspace         Workspace    @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project      @relation(fields: [projectId], references: [id], onDelete: Cascade)

  @@index([workspaceId, projectId, type])
}

model AIChat {
  id                String       @id @default(cuid())
  workspaceId       String
  projectId         String?
  userId            String?
  persona           PersonaType?
  title             String?
  messages          Json
  model             String?
  promptTemplate    String?
  metadata          Json?
  createdAt         DateTime     @default(now())
  updatedAt         DateTime     @updatedAt

  workspace         Workspace    @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project?     @relation(fields: [projectId], references: [id], onDelete: SetNull)
  user              User?        @relation(fields: [userId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId])
  @@index([userId])
}

model AuditLog {
  id                String          @id @default(cuid())
  workspaceId       String
  projectId         String?
  actorUserId       String?
  actionType        AuditActionType
  entityType        String
  entityId          String?
  oldValue          Json?
  newValue          Json?
  metadata          Json?
  createdAt         DateTime        @default(now())

  workspace         Workspace       @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project?        @relation(fields: [projectId], references: [id], onDelete: SetNull)
  actor             User?           @relation("AuditActor", fields: [actorUserId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId, createdAt])
  @@index([entityType, entityId])
}

model TraceLink {
  id                    String        @id @default(cuid())
  workspaceId           String
  projectId             String
  type                  TraceLinkType
  sourceRequirementId   String?
  targetRequirementId   String?
  sourceStoryId         String?
  targetStoryId         String?
  sourceArtifactType    String?
  sourceArtifactId      String?
  targetArtifactType    String?
  targetArtifactId      String?
  metadata              Json?
  createdAt             DateTime      @default(now())

  workspace             Workspace     @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project               Project       @relation(fields: [projectId], references: [id], onDelete: Cascade)
  sourceRequirement     Requirement?  @relation("TraceSourceRequirement", fields: [sourceRequirementId], references: [id], onDelete: SetNull)
  targetRequirement     Requirement?  @relation("TraceTargetRequirement", fields: [targetRequirementId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId, type])
}

model Epic {
  id                String      @id @default(cuid())
  workspaceId       String
  projectId         String
  title             String
  description       String?
  priority          Int?
  status            String
  metadata          Json?
  createdAt         DateTime    @default(now())
  updatedAt         DateTime    @updatedAt

  workspace         Workspace   @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project     @relation(fields: [projectId], references: [id], onDelete: Cascade)
  stories           Story[]

  @@index([workspaceId, projectId])
}

model Story {
  id                String      @id @default(cuid())
  workspaceId       String
  projectId         String
  epicId            String?
  title             String
  description       String?
  status            String
  estimate          Int?
  acceptanceCriteria Json?
  metadata          Json?
  createdAt         DateTime    @default(now())
  updatedAt         DateTime    @updatedAt

  workspace         Workspace   @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project     @relation(fields: [projectId], references: [id], onDelete: Cascade)
  epic              Epic?       @relation(fields: [epicId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId])
  @@index([epicId])
}

model ApprovalRequest {
  id                String         @id @default(cuid())
  workspaceId       String
  projectId         String?
  requestedByUserId String?
  decidedByUserId   String?
  entityType        String
  entityId          String
  status            ApprovalStatus @default(PENDING)
  rationale         String?
  decisionNote      String?
  createdAt         DateTime       @default(now())
  decidedAt         DateTime?

  workspace         Workspace      @relation(fields: [workspaceId], references: [id], onDelete: Cascade)
  project           Project?       @relation(fields: [projectId], references: [id], onDelete: SetNull)
  requestedBy       User?          @relation("ApprovalRequestedBy", fields: [requestedByUserId], references: [id], onDelete: SetNull)
  decidedBy         User?          @relation("ApprovalDecidedBy", fields: [decidedByUserId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId, status])
  @@index([entityType, entityId])
}
```

---

## `aep.prisma`

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("AEP_DATABASE_URL")
}

enum AgentType {
  ORCHESTRATOR
  CAPABILITY_AGENT
  TASK_AGENT
  MICRO_AGENT
  SYSTEM_AGENT
}

enum AgentDomain {
  DISCOVERY
  IDEATION
  STRATEGY
  REQUIREMENTS
  UX
  ARCHITECTURE
  ENGINEERING
  DATA_AI
  RUNTIME
  QA
  SECURITY
  RELEASE
  OPERATIONS
  ANALYTICS
  ENHANCEMENT
  GOVERNANCE
}

enum TriggerType {
  MANUAL
  EVENT
  SCHEDULE
  DEPENDENCY_COMPLETION
  POLICY
  WEBHOOK
}

enum RunStatus {
  REQUESTED
  QUEUED
  RUNNING
  WAITING_FOR_HUMAN
  COMPLETED
  FAILED
  CANCELLED
  TIMED_OUT
  PARTIAL
  ROLLED_BACK
}

enum ExecutionMode {
  DETERMINISTIC
  AI_ASSISTED
  HYBRID
  HUMAN_APPROVAL_REQUIRED
}

enum ToolCallStatus {
  REQUESTED
  RUNNING
  COMPLETED
  FAILED
  SKIPPED
}

enum MemoryType {
  WORKING
  EPISODIC
  SEMANTIC
  PROCEDURAL
  REFLECTION
  DECISION
  POLICY
  KNOWLEDGE
}

enum MemoryScope {
  GLOBAL
  WORKSPACE
  PROJECT
  RUN
  USER
}

enum MemoryStatus {
  ACTIVE
  STALE
  ARCHIVED
  DEPRECATED
  DELETED
}

enum PolicyDecisionStatus {
  ALLOW
  DENY
  REQUIRE_HUMAN
  REDACT
  MASK
}

model AgentDefinition {
  id                String        @id @default(cuid())
  agentName         String        @unique
  displayName       String
  agentType         AgentType
  domain            AgentDomain
  version           String
  description       String
  ownerTeam         String?
  tags              String[]
  parentAgentName   String?
  definition        Json
  isActive          Boolean       @default(true)
  createdAt         DateTime      @default(now())
  updatedAt         DateTime      @updatedAt

  runs              AgentRun[]

  @@index([domain, agentType, isActive])
}

model AgentRun {
  id                String         @id @default(cuid())
  runKey            String         @unique
  parentRunId       String?
  rootRunId         String?
  agentDefinitionId String
  agentName         String
  agentVersion      String
  agentType         AgentType
  triggerType       TriggerType
  status            RunStatus
  executionMode     ExecutionMode
  workspaceId       String
  projectId         String?
  userId            String?
  persona           String?
  correlationId     String?
  causationId       String?
  priority          String?
  input             Json
  normalizedInput   Json?
  output            Json?
  resultSummary     String?
  errorCode         String?
  errorMessage      String?
  retryCount        Int            @default(0)
  startedAt         DateTime
  endedAt           DateTime?
  durationMs        Int?
  confidenceScore   Float?
  qualityScore      Float?
  policyPassed      Boolean?
  humanApproved     Boolean?
  inputTokens       Int?
  outputTokens      Int?
  estimatedUsd      Decimal?       @db.Decimal(12, 6)
  cacheHit          Boolean?
  auditLogRef       String?
  createdAt         DateTime       @default(now())
  updatedAt         DateTime       @updatedAt

  agentDefinition   AgentDefinition @relation(fields: [agentDefinitionId], references: [id], onDelete: Restrict)
  parentRun         AgentRun?      @relation("AgentRunHierarchy", fields: [parentRunId], references: [id], onDelete: SetNull)
  childRuns         AgentRun[]     @relation("AgentRunHierarchy")

  steps             AgentRunStep[]
  toolCalls         ToolInvocation[]
  memoryReads       MemoryReadLink[]
  memoryWrites      MemoryWriteLink[]
  policyDecisions   PolicyDecision[]
  evaluations       EvaluationResult[]
  eventTraces       EventTrace[]

  @@index([workspaceId, projectId, status])
  @@index([agentName, startedAt])
  @@index([correlationId])
  @@index([rootRunId])
}

model AgentRunStep {
  id                String       @id @default(cuid())
  runId             String
  stepOrder         Int
  name              String
  stepType          String
  status            RunStatus
  input             Json?
  output            Json?
  errorCode         String?
  errorMessage      String?
  startedAt         DateTime
  endedAt           DateTime?
  durationMs        Int?
  metadata          Json?

  run               AgentRun     @relation(fields: [runId], references: [id], onDelete: Cascade)

  @@unique([runId, stepOrder])
  @@index([runId, status])
}

model ToolDefinition {
  id                String       @id @default(cuid())
  toolName          String       @unique
  displayName       String
  description       String
  schemaRef         String?
  config            Json?
  isActive          Boolean      @default(true)
  createdAt         DateTime     @default(now())
  updatedAt         DateTime     @updatedAt

  invocations       ToolInvocation[]
}

model ToolInvocation {
  id                String         @id @default(cuid())
  runId             String
  toolDefinitionId  String?
  toolName          String
  status            ToolCallStatus
  requestPayload    Json?
  responsePayload   Json?
  errorCode         String?
  errorMessage      String?
  startedAt         DateTime
  endedAt           DateTime?
  durationMs        Int?
  metadata          Json?

  run               AgentRun       @relation(fields: [runId], references: [id], onDelete: Cascade)
  toolDefinition    ToolDefinition? @relation(fields: [toolDefinitionId], references: [id], onDelete: SetNull)

  @@index([runId, status])
  @@index([toolName, startedAt])
}

model MemoryRecord {
  id                String        @id @default(cuid())
  memoryKey         String        @unique
  memoryType        MemoryType
  scope             MemoryScope
  workspaceId       String
  projectId         String?
  runId             String?
  userId            String?
  agentName         String?
  title             String?
  summary           String?
  content           Json
  contentText       String?
  embeddingRef      String?
  sourceRefs        String[]
  tags              String[]
  importance        Float         @default(0.5)
  confidence        Float         @default(0.5)
  status            MemoryStatus  @default(ACTIVE)
  retentionClass    String
  expiresAt         DateTime?
  supersedesId      String?
  lastAccessedAt    DateTime?
  accessCount       Int           @default(0)
  securityMeta      Json?
  createdAt         DateTime      @default(now())
  updatedAt         DateTime      @updatedAt

  run               AgentRun?     @relation(fields: [runId], references: [id], onDelete: SetNull)
  supersedes        MemoryRecord? @relation("MemorySupersession", fields: [supersedesId], references: [id], onDelete: SetNull)
  supersededBy      MemoryRecord[] @relation("MemorySupersession")

  readLinks         MemoryReadLink[]
  writeLinks        MemoryWriteLink[]

  @@index([workspaceId, projectId, memoryType, status])
  @@index([agentName])
}

model MemoryReadLink {
  id                String       @id @default(cuid())
  runId             String
  memoryRecordId    String
  createdAt         DateTime     @default(now())

  run               AgentRun     @relation(fields: [runId], references: [id], onDelete: Cascade)
  memoryRecord      MemoryRecord @relation(fields: [memoryRecordId], references: [id], onDelete: Cascade)

  @@unique([runId, memoryRecordId])
  @@index([memoryRecordId])
}

model MemoryWriteLink {
  id                String       @id @default(cuid())
  runId             String
  memoryRecordId    String
  createdAt         DateTime     @default(now())

  run               AgentRun     @relation(fields: [runId], references: [id], onDelete: Cascade)
  memoryRecord      MemoryRecord @relation(fields: [memoryRecordId], references: [id], onDelete: Cascade)

  @@unique([runId, memoryRecordId])
  @@index([memoryRecordId])
}

model PolicyDecision {
  id                String               @id @default(cuid())
  runId             String
  policyName        String
  decision          PolicyDecisionStatus
  rationale         String?
  inputSnapshot     Json?
  outputSnapshot    Json?
  createdAt         DateTime             @default(now())

  run               AgentRun             @relation(fields: [runId], references: [id], onDelete: Cascade)

  @@index([runId, decision])
  @@index([policyName])
}

model EvaluationResult {
  id                String       @id @default(cuid())
  runId             String
  evaluatorName     String
  score             Float?
  confidence        Float?
  result            Json
  createdAt         DateTime     @default(now())

  run               AgentRun     @relation(fields: [runId], references: [id], onDelete: Cascade)

  @@index([runId])
}

model EventTrace {
  id                String       @id @default(cuid())
  runId             String?
  eventId           String       @unique
  eventType         String
  eventVersion      String
  source            String
  workspaceId       String
  projectId         String?
  correlationId     String?
  causationId       String?
  actorType         String?
  actorId           String?
  entityType        String?
  entityId          String?
  severity          String?
  payload           Json
  metadata          Json?
  occurredAt        DateTime

  run               AgentRun?    @relation(fields: [runId], references: [id], onDelete: SetNull)

  @@index([workspaceId, projectId, eventType])
  @@index([correlationId])
  @@index([occurredAt])
}
```

---

# 3) Recommended boundary between the two schemas

## YAPPC should own

* `User`
* `Workspace`
* `WorkspaceMember`
* `Project`
* `Requirement`
* `RequirementVersion`
* `ProjectVersion`
* `Diagram`
* `AIChat`
* `AuditLog`
* `TraceLink`
* `Epic`
* `Story`
* `ApprovalRequest`

This matches the product/workspace/requirements/version/audit emphasis already defined in your planning docs.

## AEP should own

* `AgentDefinition`
* `AgentRun`
* `AgentRunStep`
* `ToolDefinition`
* `ToolInvocation`
* `MemoryRecord`
* `MemoryReadLink`
* `MemoryWriteLink`
* `PolicyDecision`
* `EvaluationResult`
* `EventTrace`

This keeps orchestration, memory, tool use, policy, evaluation, and execution tracing inside the runtime instead of polluting the product-domain schema.

---

# 4) Integration notes

Use these cross-system references as plain IDs rather than hard DB foreign keys across databases:

* `workspaceId`
* `projectId`
* `userId`
* `auditLogRef`
* `eventId`
* `correlationId`

That gives you:

* looser coupling
* independent deployability
* cleaner future split into separate services

---
