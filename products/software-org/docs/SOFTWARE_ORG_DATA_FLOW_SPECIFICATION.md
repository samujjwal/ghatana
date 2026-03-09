# Software Organization: Data Flow Specification

> **Version:** 1.0.0  
> **Date:** December 2, 2025  

---

## 1. Input/Output Specifications by Persona

### 1.1 Owner Persona

| Operation | Input | Output | Events |
|-----------|-------|--------|--------|
| View Org | - | OrgConfig, OrgGraph | - |
| Create Dept | DepartmentConfig | Department, ApprovalId | org.dept.created |
| Delete Dept | deptId | success/failure | org.dept.deleted |
| Approve Change | approvalId, decision | ApprovalResult | org.approval.completed |
| Assign Executive | personId, role | Assignment | org.role.assigned |

### 1.2 Executive Persona

| Operation | Input | Output | Events |
|-----------|-------|--------|--------|
| View Depts | - | Department[] | - |
| Create Team | TeamConfig | Team, ApprovalId | org.team.created |
| Set Budget | deptId, amount | Budget | budget.updated |
| Approve Hire | hiringId, decision | HiringResult | hiring.approved |

### 1.3 Manager Persona

| Operation | Input | Output | Events |
|-----------|-------|--------|--------|
| View Team | teamId | Team, Members | - |
| Assign Task | taskId, assigneeId | Assignment | task.assigned |
| Approve PTO | ptoId, decision | PTOResult | pto.approved |
| Review IC | personId, review | ReviewResult | review.submitted |

### 1.4 Admin Persona

| Operation | Input | Output | Events |
|-----------|-------|--------|--------|
| View Audit | filters | AuditEntry[] | - |
| Manage Perms | personId, perms | PermResult | perms.updated |
| System Config | config | ConfigResult | system.configured |

### 1.5 IC Persona

| Operation | Input | Output | Events |
|-----------|-------|--------|--------|
| View Tasks | - | WorkItem[] | - |
| Update Task | taskId, status | Task | task.updated |
| Log Time | taskId, hours | TimeEntry | time.logged |
| Request Review | taskId | ReviewRequest | review.requested |

---

## 2. API Data Flow

### 2.1 Org Configuration Flow

```
GET /api/v1/org/config
  → Node.js: Load from cache or DB
  → Java: Validate config schema
  → Response: OrgConfig JSON

PUT /api/v1/org/config
  → Node.js: Validate request
  → Java: Validate business rules
  → DB: Persist changes
  → EventCloud: org.config.updated
  → WebSocket: Broadcast to clients
```

### 2.2 Hierarchy Operation Flow

```
POST /api/v1/org/hierarchy/move
  Input: { nodeId, targetParentId }
  
  → Validate permissions (persona.service)
  → Check hierarchy rules (Java domain)
  → Create approval if required
  → If auto-approved:
    → Persist change (Prisma)
    → Emit event (EventCloud)
    → Broadcast (WebSocket)
  → Response: { success, approvalId?, impact }
```

### 2.3 Persona Preference Flow

```
GET /api/v1/personas/:workspaceId/preferences
  → Authenticate user
  → Load from Prisma
  → Enrich with Java role definitions
  → Response: PersonaPreference

PUT /api/v1/personas/:workspaceId/preferences
  Input: { activeRoles, preferences }
  
  → Validate with Java domain
  → Persist to Prisma
  → Emit persona.updated event
  → Broadcast via WebSocket
  → Response: PersonaPreference
```

---

## 3. Event Specifications

### 3.1 Organization Events

| Event | Payload | Subscribers |
|-------|---------|-------------|
| org.dept.created | { deptId, name, parentId } | UI, Audit |
| org.dept.updated | { deptId, changes } | UI, Audit |
| org.dept.deleted | { deptId } | UI, Audit |
| org.team.created | { teamId, deptId, name } | UI, Audit |
| org.restructure.proposed | { proposalId, changes } | Approvers |
| org.restructure.approved | { proposalId } | UI, Audit |

### 3.2 Task Events

| Event | Payload | Subscribers |
|-------|---------|-------------|
| task.created | { taskId, assigneeId } | IC, Manager |
| task.assigned | { taskId, assigneeId } | IC |
| task.updated | { taskId, status } | Manager |
| task.completed | { taskId } | Manager, Metrics |

### 3.3 Persona Events

| Event | Payload | Subscribers |
|-------|---------|-------------|
| persona.updated | { userId, workspaceId } | UI |
| role.assigned | { personId, role } | Audit |
| role.revoked | { personId, role } | Audit |

---

## 4. State Management

### 4.1 Jotai Atoms

```typescript
// Core atoms
currentPersonaAtom      // Current user persona
orgConfigAtom           // Organization configuration
orgGraphAtom            // Visualization data

// Derived atoms
personaTypeAtom         // Derived from currentPersonaAtom
personaPermissionsAtom  // Derived from currentPersonaAtom
isOwnerAtom             // Boolean check
canRestructureAtom      // Permission check
```

### 4.2 React Query Keys

```typescript
// Query key structure
['org', 'config']                    // Org config
['org', 'graph']                     // Org graph
['org', 'departments']               // All departments
['org', 'departments', deptId]       // Single department
['persona', workspaceId]             // Persona preference
['roles']                            // Role definitions
['approvals', status]                // Approval queue
```

---

## 5. Validation Rules

### 5.1 Hierarchy Validation

| Rule | Condition | Error |
|------|-----------|-------|
| max-depth | depth <= 4 | "Maximum hierarchy depth exceeded" |
| min-team | members >= 2 | "Team must have at least 2 members" |
| max-reports | reports <= 10 | "Maximum direct reports exceeded" |
| no-cycles | !hasCycle(graph) | "Circular hierarchy detected" |

### 5.2 Role Validation

| Rule | Condition | Error |
|------|-----------|-------|
| max-roles | roles.length <= 5 | "Maximum 5 roles allowed" |
| min-roles | roles.length >= 1 | "At least 1 role required" |
| compatible | !hasConflict(roles) | "Incompatible role combination" |

---

## 6. Security Boundaries

### 6.1 Permission Matrix

| Action | Owner | Executive | Manager | Admin | IC |
|--------|-------|-----------|---------|-------|-----|
| Create Dept | ✅ | ❌ | ❌ | ❌ | ❌ |
| Create Team | ✅ | ✅ | ❌ | ❌ | ❌ |
| Assign Task | ✅ | ✅ | ✅ | ❌ | ❌ |
| View Audit | ✅ | ❌ | ❌ | ✅ | ❌ |
| Manage Perms | ✅ | ❌ | ❌ | ✅ | ❌ |
| Update Task | ✅ | ✅ | ✅ | ❌ | ✅ |

### 6.2 Data Access Scopes

| Persona | Scope | Data Access |
|---------|-------|-------------|
| Owner | organization | All data |
| Executive | department | Department + children |
| Manager | team | Team + members |
| Admin | system | System config, audit |
| IC | self | Own tasks, team view |

---

**End of Document**
