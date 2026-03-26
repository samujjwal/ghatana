# YAPPC Production-Grade Implementation Plan V5

**Document Version**: 5.0  
**Date**: March 25, 2026  
**Status**: Ready for Implementation  
**Scope**: Full-stack production implementation aligned with existing architecture

---

## Executive Summary

This document provides a comprehensive, production-grade implementation plan for YAPPC (GHATANA App Creator). The plan is built on thorough analysis of:

- Existing codebase architecture and patterns
- Current Prisma schema and GraphQL API structure
- State management patterns (Jotai + StateManager)
- UI component library structure
- AI integration models and services
- Canvas system architecture

### Key Principles

1. **Alignment First**: Use existing patterns, libraries, and conventions
2. **Zero Regressions**: Maintain backward compatibility throughout
3. **Production Quality**: Follow enterprise-grade standards from day one
4. **Incremental Delivery**: Phased implementation with continuous validation

---

## 1. Current Architecture Assessment

### 1.1 Backend Architecture (Verified)

```
┌─────────────────────────────────────────────────────────────┐
│                    API Layer (Fastify)                     │
├─────────────────────────────────────────────────────────────┤
│  GraphQL Schema → Resolvers → Services → Prisma → PostgreSQL │
├─────────────────────────────────────────────────────────────┤
│  AI Service Layer (Java/ActiveJ Agents)                     │
├─────────────────────────────────────────────────────────────┤
│  Agent Registry | Workflow Engine | Canvas Runtime          │
└─────────────────────────────────────────────────────────────┘
```

**Verified Patterns**:

- Fastify + GraphQL + Prisma stack confirmed
- Resolver → Service → Database layering intact
- RBAC and audit patterns exist
- AI service integration with agent execution logging

### 1.2 Frontend Architecture (Verified)

```
┌─────────────────────────────────────────────────────────────┐
│                    Apps (web)                              │
├─────────────────────────────────────────────────────────────┤
│  React + TypeScript + Vite                                 │
├─────────────────────────────────────────────────────────────┤
│  State: Jotai + StateManager (@yappc/state)                │
│  API: GraphQL hooks + TanStack Query                        │
│  UI: @yappc/ui + @ghatana/ui                               │
│  Canvas: @yappc/canvas (extends @ghatana/canvas)          │
├─────────────────────────────────────────────────────────────┤
│  Shared Libraries                                          │
│  ├─ @yappc/state    → StateManager, atoms, hooks         │
│  ├─ @yappc/ui       → UI components, hooks               │
│  ├─ @yappc/canvas   → Canvas system, atoms               │
│  ├─ @yappc/ai       → AI integration                     │
│  └─ @yappc/core     → Types, utilities                   │
└─────────────────────────────────────────────────────────────┘
```

**Verified Patterns**:

- StateManager pattern fully implemented with @doc.\* tags
- Canvas atoms properly registered with persistence
- UI library exports confirmed
- GraphQL code generation configured

### 1.3 Database Schema (Verified)

**Core Entities** (from `apps/api/prisma/schema.prisma`):

| Entity         | Purpose              | AI-Enhanced                                   |
| -------------- | -------------------- | --------------------------------------------- |
| User           | Identity & auth      | Yes (preferences)                             |
| Workspace      | Org container        | Yes (aiSummary, aiTags)                       |
| Project        | Project scope        | Yes (aiSummary, aiNextActions, aiHealthScore) |
| CanvasDocument | Visual diagrams      | Yes (content JSON)                            |
| Page           | Page builder         | Yes (content JSON)                            |
| Workflow       | Process automation   | Yes (aiMode, AIGeneratedPlan)                 |
| Item           | Work tracking        | Yes (aiPriorityScore, riskScore, sentiment)   |
| Phase          | DevSecOps lifecycle  | Yes (healthScore, riskScore, predictions)     |
| AIInsight      | ML recommendations   | Core AI entity                                |
| Prediction     | ML forecasts         | Core AI entity                                |
| AnomalyAlert   | Real-time monitoring | Core AI entity                                |
| CopilotSession | AI conversations     | Core AI entity                                |
| AgentExecution | Audit trail          | Core AI entity                                |

**Key Relationships**:

- Workspace → Projects (1:N ownership, M:N inclusion)
- Project → CanvasDocuments, Pages, Items (1:N)
- Phase → Items (1:N)
- Item → AIInsights, Predictions (1:N)

---

## 2. Implementation Roadmap

### Phase 1: Foundation & Core Infrastructure (Weeks 1-2)

#### 2.1 Backend Core Services Completion

**Target Files**:

```
apps/api/src/
├── services/
│   ├── workspace/          → Workspace service with RBAC
│   ├── project/            → Project lifecycle service
│   ├── canvas/             → Canvas CRUD + versioning
│   ├── ai/                 → AI service integration
│   │   ├── ai.service.ts   → Core AI orchestration
│   │   ├── insights.ts     → AIInsight operations
│   │   ├── predictions.ts  → Prediction engine
│   │   └── copilot.ts      → Copilot session management
│   └── audit/              → Audit logging service
├── graphql/
│   ├── resolvers/
│   │   ├── workspace.resolver.ts
│   │   ├── project.resolver.ts
│   │   ├── canvas.resolver.ts
│   │   └── ai.resolver.ts  → Extends existing ai.resolver.ts
│   └── schemas/
│       ├── workspace.graphql
│       ├── project.graphql
│       └── canvas.graphql
└── middleware/
    ├── auth.ts             → JWT + RBAC enforcement
    └── audit.ts            → Automatic audit logging
```

**Implementation Pattern**:

```typescript
// @/apps/api/src/services/workspace/workspace.service.ts
/**
 * Workspace service with RBAC enforcement
 *
 * @doc.type service
 * @doc.purpose Workspace CRUD with ownership validation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { prisma } from "@/db";
import { AuditService } from "@/services/audit/audit.service";
import { RBACService } from "@/services/auth/rbac.service";
import type {
  Workspace,
  CreateWorkspaceInput,
  UpdateWorkspaceInput,
} from "@/types";

export class WorkspaceService {
  constructor(
    private audit: AuditService,
    private rbac: RBACService,
  ) {}

  async create(
    input: CreateWorkspaceInput,
    userId: string,
  ): Promise<Workspace> {
    // Create workspace
    const workspace = await prisma.workspace.create({
      data: {
        name: input.name,
        description: input.description,
        ownerId: userId,
        isDefault: input.isDefault ?? false,
      },
      include: { members: true, ownedProjects: true },
    });

    // Audit log
    await this.audit.log({
      action: "WORKSPACE_CREATED",
      actor: userId,
      resource: `workspace:${workspace.id}`,
      metadata: { name: input.name },
    });

    return workspace;
  }

  async update(
    id: string,
    input: UpdateWorkspaceInput,
    userId: string,
  ): Promise<Workspace> {
    // RBAC check
    await this.rbac.requirePermission(userId, id, "workspace:update");

    const workspace = await prisma.workspace.update({
      where: { id },
      data: input,
    });

    await this.audit.log({
      action: "WORKSPACE_UPDATED",
      actor: userId,
      resource: `workspace:${id}`,
      metadata: input,
    });

    return workspace;
  }
}
```

#### 2.2 Frontend State Layer Completion

**Target Files**:

```
libs/yappc-state/src/
├── store/
│   ├── workspaceAtoms.ts    → Workspace state management
│   ├── projectAtoms.ts      → Project state management
│   ├── canvasAtoms.ts       → Extends existing canvas state
│   └── aiAtoms.ts           → AI interaction state
└── hooks/
    ├── useWorkspace.ts      → Workspace operations
    ├── useProject.ts        → Project operations
    └── useAI.ts             → AI interaction hooks
```

**Implementation Pattern**:

```typescript
// @/libs/yappc-state/src/store/workspaceAtoms.ts
/**
 * Workspace state atoms
 *
 * @doc.type module
 * @doc.purpose Workspace state management
 * @doc.layer product
 */

import { StateManager } from "./StateManager";
import type { Workspace, Project } from "@yappc/core/types";

// Current workspace selection
export const currentWorkspaceIdAtom = StateManager.createPersistentAtom<
  string | null
>("workspace:currentId", null, {
  description: "Currently selected workspace ID",
  storage: "local",
});

// Workspace list (fetched from API)
export const workspacesAtom = StateManager.createAtom<Workspace[]>(
  "workspace:list",
  [],
  "List of user workspaces",
);

// Current workspace derived
export const currentWorkspaceAtom = StateManager.createDerivedAtom(
  "workspace:current",
  (get) => {
    const id = get(currentWorkspaceIdAtom);
    const workspaces = get(workspacesAtom);
    return workspaces.find((w) => w.id === id) || null;
  },
  "Current workspace object",
);

// Workspace projects
export const workspaceProjectsAtom = StateManager.createAtom<Project[]>(
  "workspace:projects",
  [],
  "Projects in current workspace",
);

// Loading states
export const workspaceLoadingAtom = StateManager.createAtom<boolean>(
  "workspace:loading",
  false,
  "Workspace data loading state",
);

// Error state
export const workspaceErrorAtom = StateManager.createAtom<Error | null>(
  "workspace:error",
  null,
  "Workspace operation error",
);
```

#### 2.3 UI Component Foundation

**Target Files**:

```
libs/yappc-ui/src/
├── components/
│   ├── workspace/
│   │   ├── WorkspaceCard.tsx
│   │   ├── WorkspaceList.tsx
│   │   └── WorkspaceSwitcher.tsx
│   ├── project/
│   │   ├── ProjectCard.tsx
│   │   ├── ProjectList.tsx
│   │   └── ProjectCreateDialog.tsx
│   └── layout/
│       ├── AppShell.tsx        → Main application shell
│       ├── Sidebar.tsx           → Navigation sidebar
│       └── Header.tsx            → Top navigation
└── hooks/
    ├── usePermissions.ts         → RBAC permission checks
    └── useAudit.ts               → Audit logging hooks
```

---

### Phase 2: Workspace & Project Implementation (Weeks 3-4)

#### 2.1 Workspace Management Feature

**Backend - GraphQL Schema Extensions**:

```graphql
# apps/api/src/graphql/schemas/workspace.graphql

type Workspace {
  id: ID!
  name: String!
  description: String
  ownerId: String!
  owner: User!
  isDefault: Boolean!
  aiSummary: String
  aiTags: [String!]!
  members: [WorkspaceMember!]!
  projects: [Project!]!
  createdAt: DateTime!
  updatedAt: DateTime!
}

type WorkspaceMember {
  id: ID!
  userId: String!
  user: User!
  role: Role!
  createdAt: DateTime!
}

input CreateWorkspaceInput {
  name: String!
  description: String
  isDefault: Boolean
}

input UpdateWorkspaceInput {
  name: String
  description: String
}

extend type Query {
  workspace(id: ID!): Workspace
  workspaces: [Workspace!]!
  myWorkspaces: [Workspace!]!
}

extend type Mutation {
  createWorkspace(input: CreateWorkspaceInput!): Workspace!
  updateWorkspace(id: ID!, input: UpdateWorkspaceInput!): Workspace!
  deleteWorkspace(id: ID!): Boolean!
  inviteWorkspaceMember(
    workspaceId: ID!
    email: String!
    role: Role!
  ): WorkspaceMember!
  removeWorkspaceMember(workspaceId: ID!, userId: ID!): Boolean!
}
```

**Frontend - Workspace Components**:

```typescript
// @/libs/yappc-ui/src/components/workspace/WorkspaceSwitcher.tsx
/**
 * Workspace switcher component
 *
 * @doc.type component
 * @doc.purpose Allow users to switch between workspaces
 * @doc.layer product
 * @doc.pattern Dropdown Menu
 */

import React from 'react';
import { useGlobalState, useGlobalStateValue } from '@yappc/state';
import { currentWorkspaceAtom, currentWorkspaceIdAtom, workspacesAtom } from '@yappc/state/workspace';
import { useWorkspacesQuery } from '@yappc/api/graphql';

export const WorkspaceSwitcher: React.FC = () => {
  const [currentId, setCurrentId] = useGlobalState(currentWorkspaceIdAtom);
  const workspaces = useGlobalStateValue(workspacesAtom);
  const current = useGlobalStateValue(currentWorkspaceAtom);

  const { loading } = useWorkspacesQuery({
    onCompleted: (data) => {
      // Update state when data loads
    }
  });

  return (
    <DropdownMenu>
      <DropdownMenuTrigger>
        <Button variant="ghost">
          {current?.name || 'Select Workspace'}
          <ChevronDown className="ml-2 h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {workspaces.map((workspace) => (
          <DropdownMenuItem
            key={workspace.id}
            onClick={() => setCurrentId(workspace.id)}
          >
            {workspace.name}
            {workspace.id === currentId && <Check className="ml-2 h-4 w-4" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
```

#### 2.2 Project Management Feature

**Backend - Project Service**:

```typescript
// @/apps/api/src/services/project/project.service.ts

export class ProjectService {
  async create(
    input: CreateProjectInput,
    userId: string,
    workspaceId: string,
  ): Promise<Project> {
    // Validate workspace ownership
    const workspace = await prisma.workspace.findFirst({
      where: { id: workspaceId, ownerId: userId },
    });

    if (!workspace) {
      throw new Error("Workspace not found or not owned by user");
    }

    const project = await prisma.project.create({
      data: {
        name: input.name,
        description: input.description,
        type: input.type,
        ownerWorkspaceId: workspaceId,
        createdById: userId,
        status: "DRAFT",
        lifecyclePhase: "SHAPE",
      },
      include: {
        ownerWorkspace: true,
        createdBy: true,
      },
    });

    // Auto-create default canvas document
    await prisma.canvasDocument.create({
      data: {
        projectId: project.id,
        createdById: userId,
        name: "Main Canvas",
        content: DEFAULT_CANVAS_CONTENT,
      },
    });

    await this.audit.log({
      action: "PROJECT_CREATED",
      actor: userId,
      resource: `project:${project.id}`,
      metadata: { name: input.name, type: input.type },
    });

    return project;
  }

  async generateAIInsights(
    projectId: string,
    userId: string,
  ): Promise<AIInsight[]> {
    // Fetch project context
    const project = await prisma.project.findUnique({
      where: { id: projectId },
      include: {
        documents: true,
        lifecycleItems: true,
      },
    });

    if (!project) throw new Error("Project not found");

    // Call AI service for analysis
    const insights = await this.aiService.analyzeProject(project);

    // Store insights
    const created = await Promise.all(
      insights.map((insight) =>
        prisma.aIInsight.create({
          data: {
            projectId,
            type: insight.type,
            title: insight.title,
            description: insight.description,
            confidence: insight.confidence,
            severity: insight.severity,
            actionable: insight.actionable,
            suggestedAction: insight.suggestedAction,
            agentName: "ProjectAnalyzer",
            modelVersion: "gpt-4",
          },
        }),
      ),
    );

    return created;
  }
}
```

---

### Phase 3: Canvas & Visual System (Weeks 5-6)

#### 3.1 Canvas Document System

**Backend - Canvas Service**:

```typescript
// @/apps/api/src/services/canvas/canvas.service.ts

export class CanvasService {
  async create(
    input: CreateCanvasInput,
    userId: string,
  ): Promise<CanvasDocument> {
    const canvas = await prisma.canvasDocument.create({
      data: {
        projectId: input.projectId,
        createdById: userId,
        name: input.name,
        description: input.description,
        content: input.content || DEFAULT_CANVAS_CONTENT,
      },
    });

    // Create initial version
    await prisma.canvasVersion.create({
      data: {
        canvasId: canvas.id,
        version: 1,
        content: canvas.content,
        changeType: "INITIAL",
        changedBy: userId,
      },
    });

    return canvas;
  }

  async saveVersion(
    canvasId: string,
    content: CanvasContent,
    userId: string,
    changeSummary?: string,
  ): Promise<CanvasVersion> {
    // Get current version
    const current = await prisma.canvasVersion.findFirst({
      where: { canvasId },
      orderBy: { version: "desc" },
    });

    const nextVersion = (current?.version ?? 0) + 1;

    // Create new version
    const version = await prisma.canvasVersion.create({
      data: {
        canvasId,
        version: nextVersion,
        content,
        changeType: "MANUAL_SAVE",
        changedBy: userId,
        changeSummary,
      },
    });

    // Update document content
    await prisma.canvasDocument.update({
      where: { id: canvasId },
      data: { content, updatedAt: new Date() },
    });

    return version;
  }

  async restoreVersion(
    canvasId: string,
    versionNumber: number,
    userId: string,
  ): Promise<CanvasDocument> {
    const version = await prisma.canvasVersion.findUnique({
      where: { canvasId_version: { canvasId, version: versionNumber } },
    });

    if (!version) throw new Error("Version not found");

    // Create restore point
    await prisma.canvasVersion.create({
      data: {
        canvasId,
        version: (await this.getLatestVersionNumber(canvasId)) + 1,
        content: version.content,
        changeType: "RESTORE",
        changedBy: userId,
        changeSummary: `Restored from version ${versionNumber}`,
      },
    });

    // Update document
    return prisma.canvasDocument.update({
      where: { id: canvasId },
      data: { content: version.content, updatedAt: new Date() },
    });
  }
}
```

**Frontend - Canvas State Integration**:

```typescript
// @/libs/yappc-state/src/store/canvasAtoms.ts

import { StateManager } from "./StateManager";
import { atom } from "jotai";
import type {
  CanvasDocument,
  CanvasNode,
  CanvasEdge,
} from "@yappc/canvas/types";

// Current canvas document
export const currentCanvasAtom = StateManager.createAtom<CanvasDocument | null>(
  "canvas:current",
  null,
  "Currently active canvas document",
);

// Canvas nodes (for reactive updates)
export const canvasNodesAtom = StateManager.createDerivedAtom(
  "canvas:nodes",
  (get) => get(currentCanvasAtom)?.content?.nodes || [],
  "Canvas nodes array",
);

// Canvas edges
export const canvasEdgesAtom = StateManager.createDerivedAtom(
  "canvas:edges",
  (get) => get(currentCanvasAtom)?.content?.edges || [],
  "Canvas edges array",
);

// Selected elements
export const selectedCanvasElementsAtom = StateManager.createAtom<string[]>(
  "canvas:selected",
  [],
  "Selected canvas element IDs",
);

// Canvas mode (select, pan, draw, etc.)
export const canvasModeAtom = StateManager.createPersistentAtom(
  "canvas:mode",
  "select" as "select" | "pan" | "draw" | "edit",
  { description: "Current canvas interaction mode", storage: "local" },
);

// History state for undo/redo
export const canvasHistoryStateAtom = StateManager.createAtom(
  "canvas:history",
  {
    canUndo: false,
    canRedo: false,
    undoStack: [] as CanvasDocument[],
    redoStack: [] as CanvasDocument[],
  },
  "Canvas undo/redo state",
);

// Action atom: Update canvas content
export const updateCanvasContentAtom = atom(
  null,
  (get, set, update: Partial<CanvasDocument["content"]>) => {
    const current = get(currentCanvasAtom);
    if (!current) return;

    // Push to undo stack before update
    const history = get(canvasHistoryStateAtom);
    set(canvasHistoryStateAtom, {
      ...history,
      undoStack: [...history.undoStack, current],
      canUndo: true,
    });

    // Update canvas
    set(currentCanvasAtom, {
      ...current,
      content: { ...current.content, ...update },
      updatedAt: new Date(),
    });
  },
);
```

---

### Phase 4: AI Integration & Copilot (Weeks 7-8)

#### 4.1 AI Service Layer

**Backend - AI Orchestration**:

```typescript
// @/apps/api/src/services/ai/ai-orchestrator.ts

export class AIOrchestrator {
  constructor(
    private agentRegistry: AgentRegistry,
    private executionLogger: ExecutionLogger,
    private costTracker: CostTracker,
  ) {}

  async executeAgent(
    agentName: string,
    input: AgentInput,
    context: AgentContext,
  ): Promise<AgentOutput> {
    const startTime = Date.now();
    const requestId = generateUUID();

    try {
      // Log execution start
      await this.executionLogger.start({
        requestId,
        agentName,
        userId: context.userId,
        workspaceId: context.workspaceId,
        input,
      });

      // Get agent from registry
      const agent = await this.agentRegistry.get(agentName);
      if (!agent) {
        throw new Error(`Agent ${agentName} not found`);
      }

      // Execute agent
      const output = await agent.execute(input, context);

      // Calculate metrics
      const latencyMs = Date.now() - startTime;
      const costUSD = this.costTracker.calculate(
        agent.model,
        output.tokensUsed,
      );

      // Log completion
      await this.executionLogger.complete({
        requestId,
        output,
        latencyMs,
        costUSD,
        tokensUsed: output.tokensUsed,
      });

      return output;
    } catch (error) {
      await this.executionLogger.fail({
        requestId,
        error: error instanceof Error ? error.message : "Unknown error",
      });
      throw error;
    }
  }

  async generateProjectInsights(
    projectId: string,
    userId: string,
  ): Promise<AIInsight[]> {
    const context = await this.buildProjectContext(projectId);

    const output = await this.executeAgent(
      "project-analyzer",
      {
        project: context,
        focusAreas: ["health", "risks", "recommendations"],
      },
      { userId, workspaceId: context.workspaceId },
    );

    // Transform agent output to AIInsights
    return output.insights.map((insight: AgentInsight) =>
      this.transformToAIInsight(insight, projectId, userId),
    );
  }
}
```

**Frontend - AI Hooks**:

```typescript
// @/libs/yappc-state/src/hooks/useAI.ts

import { useCallback } from "react";
import { useGlobalState, useGlobalStateValue } from "../store/useGlobalState";
import { useMutation, useQuery } from "@tanstack/react-query";
import { graphqlClient } from "@yappc/api/graphql";

export const useCopilot = () => {
  const [session, setSession] = useGlobalState("copilot:session");
  const messages = useGlobalStateValue("copilot:messages");

  const sendMessage = useMutation({
    mutationFn: async (message: string) => {
      const result = await graphqlClient.sendCopilotMessage({
        sessionId: session?.id,
        message,
        context: buildContext(),
      });
      return result;
    },
    onSuccess: (data) => {
      // Update session with response
      setSession((prev) => ({
        ...prev,
        messages: [...(prev?.messages || []), data.message],
        tokensUsed: data.tokensUsed,
      }));
    },
  });

  return {
    session,
    messages,
    sendMessage: sendMessage.mutate,
    isLoading: sendMessage.isPending,
  };
};

export const useAIInsights = (projectId: string) => {
  return useQuery({
    queryKey: ["ai-insights", projectId],
    queryFn: async () => {
      const result = await graphqlClient.getAIInsights({ projectId });
      return result.aiInsights;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
```

---

### Phase 5: Requirements & Versioning (Weeks 9-10)

#### 5.1 Requirements Management

**Backend - Requirements Service**:

```typescript
// @/apps/api/src/services/requirements/requirements.service.ts

export class RequirementsService {
  async parseNaturalLanguage(
    text: string,
    projectId: string,
    userId: string,
  ): Promise<ParsedRequirement[]> {
    // Call AI agent for parsing
    const output = await this.aiOrchestrator.executeAgent(
      "requirements-parser",
      { text, projectContext: await this.getProjectContext(projectId) },
      { userId, workspaceId: await this.getWorkspaceId(projectId) },
    );

    // Store parsed requirements
    const requirements = await Promise.all(
      output.requirements.map((req: ParsedReq) =>
        prisma.item.create({
          data: {
            title: req.title,
            description: req.description,
            type: this.mapRequirementType(req.type),
            priority: this.mapPriority(req.priority),
            phaseId: await this.getDefaultPhaseId(projectId),
            // AI-enhanced fields
            aiPriorityScore: req.confidence,
          },
        }),
      ),
    );

    return requirements;
  }
}
```

#### 5.2 Versioning System

**Backend - Version Control**:

```typescript
// @/apps/api/src/services/versioning/versioning.service.ts

export class VersioningService {
  async createSnapshot(
    projectId: string,
    userId: string,
    label?: string,
  ): Promise<ProjectSnapshot> {
    const project = await prisma.project.findUnique({
      where: { id: projectId },
      include: {
        documents: true,
        pages: true,
        lifecycleItems: true,
        lifecycleArtifacts: true,
      },
    });

    if (!project) throw new Error("Project not found");

    // Create snapshot
    const snapshot = await prisma.projectSnapshot.create({
      data: {
        projectId,
        createdById: userId,
        label,
        data: project as unknown as Prisma.JsonObject,
        version: await this.getNextVersion(projectId),
      },
    });

    await this.audit.log({
      action: "SNAPSHOT_CREATED",
      actor: userId,
      resource: `project:${projectId}`,
      metadata: { snapshotId: snapshot.id, version: snapshot.version },
    });

    return snapshot;
  }

  async restoreSnapshot(snapshotId: string, userId: string): Promise<Project> {
    const snapshot = await prisma.projectSnapshot.findUnique({
      where: { id: snapshotId },
    });

    if (!snapshot) throw new Error("Snapshot not found");

    // Create restore point first
    await this.createSnapshot(
      snapshot.projectId,
      userId,
      `Pre-restore backup (restoring to v${snapshot.version})`,
    );

    // Restore project data
    const data = snapshot.data as unknown as ProjectData;

    // Update project
    const project = await prisma.project.update({
      where: { id: snapshot.projectId },
      data: {
        name: data.name,
        description: data.description,
        status: data.status,
      },
    });

    // Restore documents
    await Promise.all(
      data.documents.map((doc) =>
        prisma.canvasDocument.upsert({
          where: { id: doc.id },
          create: doc,
          update: { content: doc.content, updatedAt: new Date() },
        }),
      ),
    );

    return project;
  }

  async exportToYAML(
    projectId: string,
    format: "yaml" | "json" | "markdown",
  ): Promise<string> {
    const project = await prisma.project.findUnique({
      where: { id: projectId },
      include: {
        documents: true,
        lifecycleItems: { include: { owners: true, tags: true } },
        lifecycleArtifacts: true,
        lifecycleAIInsights: true,
      },
    });

    if (!project) throw new Error("Project not found");

    switch (format) {
      case "yaml":
        return this.toYAML(project);
      case "json":
        return JSON.stringify(project, null, 2);
      case "markdown":
        return this.toMarkdown(project);
      default:
        throw new Error(`Unsupported format: ${format}`);
    }
  }
}
```

---

### Phase 6: RBAC & Audit (Weeks 11-12)

#### 6.1 RBAC Implementation

**Backend - RBAC Service**:

```typescript
// @/apps/api/src/services/auth/rbac.service.ts

export class RBACService {
  private permissions: Map<string, Permission[]> = new Map([
    ["OWNER", ["*"]], // All permissions
    [
      "ADMIN",
      [
        "workspace:update",
        "workspace:delete",
        "workspace:invite",
        "project:create",
        "project:update",
        "project:delete",
        "canvas:create",
        "canvas:update",
        "canvas:delete",
        "item:create",
        "item:update",
        "item:delete",
        "ai:invoke",
        "audit:read",
      ],
    ],
    [
      "EDITOR",
      [
        "project:create",
        "project:update",
        "canvas:create",
        "canvas:update",
        "item:create",
        "item:update",
        "item:delete",
        "ai:invoke",
      ],
    ],
    ["VIEWER", ["workspace:read", "project:read", "canvas:read", "item:read"]],
  ]);

  async checkPermission(
    userId: string,
    resource: string,
    action: string,
  ): Promise<boolean> {
    // Get user's role for resource
    const role = await this.getUserRole(userId, resource);
    if (!role) return false;

    const permissions = this.permissions.get(role) || [];

    // Check wildcard or specific permission
    return (
      permissions.includes("*") ||
      permissions.includes(`${action}`) ||
      permissions.includes(`${resource}:${action}`)
    );
  }

  async requirePermission(
    userId: string,
    resource: string,
    action: string,
  ): Promise<void> {
    const hasPermission = await this.checkPermission(userId, resource, action);
    if (!hasPermission) {
      throw new Error(
        `Forbidden: User ${userId} lacks ${action} permission for ${resource}`,
      );
    }
  }

  async getUserRole(userId: string, resource: string): Promise<Role | null> {
    // Check if resource is a workspace
    if (resource.startsWith("workspace:")) {
      const workspaceId = resource.split(":")[1];
      const member = await prisma.workspaceMember.findUnique({
        where: { userId_workspaceId: { userId, workspaceId } },
      });
      return member?.role || null;
    }

    // Check if resource is a project
    if (resource.startsWith("project:")) {
      const projectId = resource.split(":")[1];
      const project = await prisma.project.findUnique({
        where: { id: projectId },
        select: { ownerWorkspaceId: true },
      });

      if (!project) return null;

      // Check workspace membership
      return this.getUserRole(userId, `workspace:${project.ownerWorkspaceId}`);
    }

    return null;
  }
}
```

#### 6.2 Audit Logging

**Backend - Audit Service**:

```typescript
// @/apps/api/src/services/audit/audit.service.ts

export class AuditService {
  async log(entry: AuditEntryInput): Promise<AuditLogEntry> {
    return prisma.auditLogEntry.create({
      data: {
        action: entry.action,
        actor: entry.actor,
        actorRole:
          entry.actorRole ||
          (await this.getActorRole(entry.actor, entry.resource)),
        resource: entry.resource,
        severity: entry.severity || "info",
        details: entry.details,
        ipAddress: entry.ipAddress,
        userAgent: entry.userAgent,
        metadata: entry.metadata,
        success: entry.success ?? true,
      },
    });
  }

  async getAuditTrail(
    resource: string,
    options: AuditQueryOptions,
  ): Promise<AuditLogEntry[]> {
    return prisma.auditLogEntry.findMany({
      where: {
        resource: { startsWith: resource },
        timestamp: {
          gte: options.startDate,
          lte: options.endDate,
        },
        ...(options.actions && { action: { in: options.actions } }),
        ...(options.severity && { severity: options.severity }),
      },
      orderBy: { timestamp: "desc" },
      take: options.limit || 100,
    });
  }
}

// Middleware for automatic audit logging
export const auditMiddleware = (auditService: AuditService) => {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const startTime = Date.now();

    // Store original send
    const originalSend = reply.send;

    reply.send = async function (payload) {
      const responseTime = Date.now() - startTime;

      // Log audit entry
      await auditService.log({
        action: `${request.method}_${request.routerPath}`,
        actor: request.user?.id || "anonymous",
        resource: request.params?.id
          ? `${request.routerPath}:${request.params.id}`
          : request.routerPath,
        method: request.method,
        status: reply.statusCode,
        responseTime,
        ipAddress: request.ip,
        userAgent: request.headers["user-agent"],
        success: reply.statusCode < 400,
      });

      return originalSend.call(this, payload);
    };
  };
};
```

---

## 3. File-Level Change Summary

### 3.1 New Files (Backend) - CORRECTED

**Note**: After comprehensive codebase scan, many proposed "NEW" files were found to already exist with complete implementations. See `YAPPC_PLAN_DUPLICATE_ANALYSIS.md` for full details.

#### ✅ EXISTS (Do NOT Create - Already Implemented)

| File                     | Location                           | Status                                       |
| ------------------------ | ---------------------------------- | -------------------------------------------- |
| `auth.service.ts`        | `apps/api/src/services/auth/`      | ✅ Complete - JWT, bcrypt, RBAC              |
| `ai.service.ts`          | `apps/api/src/services/ai/`        | ✅ Complete - insights, predictions, copilot |
| `FlowService.ts`         | `apps/api/src/services/`           | ✅ Complete - workflow engine                |
| `ConfigService.ts`       | `apps/api/src/services/`           | ✅ Complete - personas, domains              |
| `DashboardService.ts`    | `apps/api/src/services/`           | ✅ Complete - dashboard management           |
| `RateLimitingService.ts` | `apps/api/src/services/ratelimit/` | ✅ Complete - rate limiting                  |
| `index.ts` resolvers     | `apps/api/src/graphql/resolvers/`  | ✅ Complete - workspace/project/canvas CRUD  |
| `AIAgentsResolver.ts`    | `apps/api/src/graphql/resolvers/`  | ✅ Complete - AI agents (633 lines)          |
| `workflow.resolver.ts`   | `apps/api/src/graphql/resolvers/`  | ✅ Complete - workflow resolver (576 lines)  |
| `ai.resolver.ts`         | `apps/api/src/graphql/resolvers/`  | ✅ Complete - AI queries/mutations           |

#### ❌ ACTUALLY NEEDED (Create New)

```
apps/api/src/
├── services/
│   ├── audit/
│   │   └── audit.service.ts          [NEW] Enhance for AuditLogEntry model
│   ├── versioning/
│   │   └── versioning.service.ts     [NEW] Project snapshot/restore
│   ├── requirements/
│   │   └── requirements.service.ts   [NEW] NLP parsing for requirements
│   ├── rbac/
│   │   ├── rbac.service.ts           [NEW] Centralized RBAC (extract from auth)
│   │   └── permissions.ts            [NEW] Permission matrix
│   ├── workspace/
│   │   └── workspace.service.ts      [NEW] Extract business logic from resolvers
│   └── project/
│       └── project.service.ts       [NEW] Extract business logic from resolvers
├── graphql/
│   └── resolvers/
│       └── versioning.resolver.ts    [NEW] Snapshot/restore mutations
└── middleware/
    ├── auth.middleware.ts            [EXISTS] JWT validation middleware
    ├── rbac.middleware.ts            [NEW] Permission enforcement
    └── audit.middleware.ts           [NEW] Automatic audit logging
```

### 3.2 New Files (Frontend) - CORRECTED

**Note**: State management infrastructure already exists. Focus on product-specific extensions.

#### ✅ EXISTS (Do NOT Create - Already Implemented)

| File                 | Location                             | Notes                                |
| -------------------- | ------------------------------------ | ------------------------------------ |
| `StateManager.ts`    | `libs/yappc-state/src/store/`        | ✅ Complete (559 lines)              |
| `useGlobalState.ts`  | `libs/yappc-state/src/store/`        | ✅ Complete hooks                    |
| `atoms.ts`           | `libs/yappc-state/src/store/`        | ✅ Complete atoms library            |
| `canvas/atoms.ts`    | `libs/yappc-canvas/src/state/`       | ✅ Complete canvas state (736 lines) |
| `mobile/atoms.ts`    | `libs/yappc-state/src/store/mobile/` | ✅ Mobile state                      |
| `devsecops/hooks.ts` | `libs/yappc-state/src/store/`        | ✅ DevSecOps hooks                   |

#### ❌ ACTUALLY NEEDED (Create New)

**State Atoms:**

```
libs/yappc-state/src/
└── store/
    ├── workspaceAtoms.ts              [NEW] Workspace selection & list state
    ├── projectAtoms.ts                [NEW] Project state within workspace
    └── aiAtoms.ts                     [NEW] AI interaction state (copilot sessions)
```

**Hooks:**

```
libs/yappc-state/src/
└── hooks/
    ├── useWorkspace.ts                [NEW] Workspace CRUD operations
    ├── useProject.ts                  [NEW] Project operations
    └── useAI.ts                       [NEW] AI interaction hooks
```

**UI Components:**

```
libs/yappc-ui/src/
└── components/
    ├── workspace/
    │   ├── WorkspaceCard.tsx          [VERIFY - may exist]
    │   ├── WorkspaceList.tsx          [VERIFY - may exist]
    │   ├── WorkspaceSwitcher.tsx      [VERIFY - may exist]
    │   └── WorkspaceCreateDialog.tsx  [NEW]
    ├── project/
    │   ├── ProjectCard.tsx            [VERIFY - may exist]
    │   ├── ProjectList.tsx            [VERIFY - may exist]
    │   ├── ProjectCreateDialog.tsx    [NEW]
    │   └── ProjectAIInsights.tsx      [NEW - AI insights display]
    ├── canvas/
    │   ├── CanvasEditor.tsx           [NEW - wraps existing canvas]
    │   ├── CanvasToolbar.tsx          [NEW - product-specific toolbar]
    │   ├── CanvasPropertiesPanel.tsx  [NEW - element properties]
    │   └── CanvasVersionHistory.tsx   [NEW - version restore UI]
    ├── ai/
    │   ├── CopilotChat.tsx            [NEW - chat interface]
    │   ├── AIInsightCard.tsx          [NEW - insight display]
    │   └── AIInsightList.tsx          [NEW - insights list]
    └── layout/
        ├── AppShell.tsx               [VERIFY - may exist in apps/web]
        ├── Sidebar.tsx                [VERIFY - may exist]
        └── Header.tsx                 [VERIFY - may exist]
```

### 3.3 Modified Files

```
apps/api/src/
├── graphql/
│   ├── resolvers/
│   │   ├── index.ts                   [MODIFY] Add versioning resolvers
│   │   └── ai.resolver.ts            [MODIFY] Extend if needed
│   └── schema.graphql                [MODIFY] Add versioning types
├── services/
│   └── ai/
│       └── ai.service.ts             [MODIFY] Add versioning support
└── index.ts                          [MODIFY] Register new services

libs/yappc-canvas/src/
└── state/
    └── atoms.ts                      [MODIFY] Add versioning atoms

apps/web/src/
├── App.tsx                           [MODIFY] Add StateProvider if missing
└── main.tsx                          [MODIFY] Initialize new services
```

---

## 4. Architecture Alignment Report

| Layer             | Standard                        | Current Status  | Alignment |
| ----------------- | ------------------------------- | --------------- | --------- |
| Backend Framework | Fastify + GraphQL + Prisma      | Verified        | Full      |
| State Management  | Jotai + StateManager            | Verified        | Full      |
| UI Components     | @yappc/ui + @ghatana/ui         | Verified        | Full      |
| Canvas System     | @yappc/canvas + @ghatana/canvas | Verified        | Full      |
| AI Integration    | AgentRegistry + AI Service      | Verified        | Full      |
| Database          | PostgreSQL + Prisma             | Verified        | Full      |
| RBAC              | Role-based permissions          | Schema ready    | Full      |
| Audit             | AuditLogEntry model             | Schema ready    | Full      |
| UI Components     | @yappc/ui + @ghatana/ui         | ✅ Verified     | Full      |
| Canvas System     | @yappc/canvas + @ghatana/canvas | ✅ Verified     | Full      |
| AI Integration    | AgentRegistry + AI Service      | ✅ Verified     | Full      |
| Database          | PostgreSQL + Prisma             | ✅ Verified     | Full      |
| RBAC              | Role-based permissions          | ✅ Schema ready | Full      |
| Audit             | AuditLogEntry model             | ✅ Schema ready | Full      |

### 4.2 Pattern Consistency

**State Management Pattern (Verified)**:

```typescript
// Standard pattern from StateManager.ts
StateManager.createAtom<T>(key, defaultValue, description);
StateManager.createPersistentAtom<T>(key, defaultValue, options);
StateManager.createDerivedAtom<T>(key, read, description);
```

**Service Layer Pattern (Verified)**:

```typescript
// Standard pattern from ai.resolver.ts
export class XxxService {
  constructor(
    private dependency: Dependency,
    private audit: AuditService,
  ) {}

  async operation(input: Input, userId: string): Promise<Output> {
    // RBAC check
    await this.rbac.requirePermission(userId, resource, action);

    // Business logic
    const result = await this.prisma.model.operation();

    // Audit log
    await this.audit.log({ action, actor: userId, resource });

    return result;
  }
}
```

**GraphQL Resolver Pattern (Verified)**:

```typescript
// Standard pattern from existing resolvers
export const xxxResolvers = {
  Query: {
    async xxx(_parent, args, context) {
      if (!context.userId) throw new Error("Unauthorized");
      return service.getXxx(args);
    },
  },
  Mutation: {
    async xxx(_parent, args, context) {
      if (!context.userId) throw new Error("Unauthorized");
      return service.xxx(args, context.userId);
    },
  },
};
```

### 4.3 Documentation Standards

All new code must include `@doc.*` tags:

````typescript
/**
 * Component/Service description
 *
 * @doc.type component|service|hook|resolver|atom
 * @doc.purpose Brief description of purpose
 * @doc.layer product|platform|core
 * @doc.pattern Design pattern used
 * @example
 * ```typescript
 * // Usage example
 * ```
 */
````

---

## 5. Test Coverage Strategy

### 5.1 Test Structure

```
apps/api/src/
├── services/
│   ├── workspace/
│   │   └── workspace.service.test.ts
│   ├── project/
│   │   └── project.service.test.ts
│   └── ai/
│       └── ai-orchestrator.test.ts
└── graphql/
    └── resolvers/
        └── workspace.resolver.test.ts

libs/yappc-state/src/
└── store/
    └── workspaceAtoms.test.ts

libs/yappc-ui/src/
└── components/
    └── workspace/
        └── WorkspaceCard.test.tsx
```

### 5.2 Test Requirements

| Layer         | Coverage Target | Testing Approach               |
| ------------- | --------------- | ------------------------------ |
| Services      | 90%+            | Unit tests with mocked Prisma  |
| Resolvers     | 85%+            | Integration tests with test DB |
| State Atoms   | 80%+            | Unit tests with Jotai utils    |
| UI Components | 80%+            | React Testing Library + Jest   |
| E2E           | Core flows      | Playwright for critical paths  |

### 5.3 Critical Test Paths

1. **Workspace Flow**: Create → Update → Invite → Delete
2. **Project Flow**: Create → Add canvas → AI insights → Version → Export
3. **Canvas Flow**: Create → Edit → Save version → Restore → Export
4. **AI Flow**: Copilot chat → Generate insights → Accept/reject
5. **RBAC Flow**: Viewer access denied → Editor can edit → Admin can delete
6. **Audit Flow**: Action → Audit log created → Audit trail query

---

## 6. Risks & Mitigations

### 6.1 Technical Risks

| Risk                               | Likelihood | Impact | Mitigation                                     |
| ---------------------------------- | ---------- | ------ | ---------------------------------------------- |
| AI service latency                 | Medium     | High   | Implement caching, circuit breakers            |
| Canvas performance with large docs | Medium     | High   | Virtualization, lazy loading                   |
| Database migration complexity      | Low        | High   | Phased migrations, rollback plan               |
| State sync issues                  | Medium     | Medium | Cross-tab sync validation, conflict resolution |
| RBAC edge cases                    | Medium     | Medium | Comprehensive permission matrix testing        |

### 6.2 Business Risks

| Risk                   | Likelihood | Impact | Mitigation                               |
| ---------------------- | ---------- | ------ | ---------------------------------------- |
| Feature scope creep    | High       | Medium | Strict sprint boundaries, MVP focus      |
| Integration delays     | Medium     | Medium | Early integration testing, mocks         |
| User adoption friction | Medium     | High   | Onboarding flows, progressive disclosure |

### 6.3 Mitigation Strategies

1. **Circuit Breakers for AI**: Implement fallback responses when AI service is slow
2. **Optimistic UI**: Update UI immediately, rollback on error
3. **Feature Flags**: Enable gradual rollout of new features
4. **Comprehensive Logging**: Detailed logs for debugging production issues
5. **Automated Rollback**: Database migration rollback scripts

---

## 7. Success Metrics

### 7.1 Technical Metrics

| Metric             | Target     | Measurement           |
| ------------------ | ---------- | --------------------- |
| Test coverage      | 85%+       | Jest coverage reports |
| API response time  | <200ms p95 | APM monitoring        |
| AI response time   | <3s p95    | Agent execution logs  |
| Canvas render time | <50ms      | Performance profiling |
| Error rate         | <0.1%      | Error tracking        |

### 7.2 User Experience Metrics

| Metric                           | Target        | Measurement       |
| -------------------------------- | ------------- | ----------------- |
| Workspace setup time             | <2 minutes    | User analytics    |
| Project creation to first canvas | <30 seconds   | User analytics    |
| AI insight relevance             | >70% accepted | Feedback tracking |
| Version restore success          | >99%          | Audit logs        |

---

## 8. Implementation Checklist

### Pre-Implementation

- [ ] Review and approve this plan
- [ ] Set up feature branch strategy
- [ ] Configure CI/CD for new services
- [ ] Set up monitoring and alerting

### Phase 1 (Weeks 1-2)

- [ ] Backend core services scaffold
- [ ] Database migrations created
- [ ] State atoms implemented
- [ ] Basic API tests passing

### Phase 2 (Weeks 3-4)

- [ ] Workspace UI complete
- [ ] Project UI complete
- [ ] End-to-end workspace flow working
- [ ] RBAC middleware active

### Phase 3 (Weeks 5-6)

- [ ] Canvas editor functional
- [ ] Version history working
- [ ] Canvas export working
- [ ] Performance targets met

### Phase 4 (Weeks 7-8)

- [ ] Copilot chat functional
- [ ] AI insights generating
- [ ] Cost tracking active
- [ ] Feedback loop working

### Phase 5 (Weeks 9-10)

- [ ] Requirements parsing working
- [ ] Version snapshots working
- [ ] Export formats working
- [ ] Import functionality ready

### Phase 6 (Weeks 11-12)

- [ ] RBAC fully enforced
- [ ] Audit logging complete
- [ ] Admin dashboard functional
- [ ] Compliance reports ready

### Final (Week 13)

- [ ] All tests passing
- [ ] Documentation complete
- [ ] Security audit passed
- [ ] Production deployment

---

## 9. References

### Key Documents

1. `YAPPC_COMPREHENSIVE_IMPLEMENTATION_PLAN.md` - Strategic roadmap
2. `CORRECTIVE_ACTION_PLAN.md` - AEP integration corrections
3. `apps/api/prisma/schema.prisma` - Database schema
4. `apps/api/src/graphql/schema.graphql` - API schema
5. `libs/yappc-state/src/store/StateManager.ts` - State patterns

### External References

- [Prisma Documentation](https://www.prisma.io/docs)
- [Jotai Documentation](https://jotai.org/docs)
- [Fastify Documentation](https://www.fastify.io/docs)
- [GraphQL Best Practices](https://graphql.org/learn/best-practices/)

---

**Document Owner**: YAPPC Engineering Team  
**Review Schedule**: Weekly during implementation  
**Approval Status**: Pending Leadership Review

---

_This plan is a living document. Updates should be tracked through version control and communicated to all stakeholders._
