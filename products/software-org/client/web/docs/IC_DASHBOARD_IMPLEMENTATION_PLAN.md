# Persona Dashboard E2E Implementation Plan

## Executive Summary

This document outlines a comprehensive plan to implement a fully functional **Persona Dashboard** with end-to-end actionable features. The architecture treats **all personas uniformly** - whether human or automated agent - with consistent interfaces for task execution, lifecycle management, and growth tracking.

### Core Principles

1. **Persona Agnostic** - Human and agent personas share the same interfaces
2. **Complete Task Flows** - All designated tasks completable from within the dashboard
3. **Embedded Tools** - Canvas, hooks, and integrations built into the workflow
4. **Unified Lifecycle** - Time-off, growth, and capacity apply to all personas
5. **DevSecOps Native** - Full lifecycle from intake to learn, with tools at each phase

---

## Phase 0: Unified Persona Model

### 0.1 Persona Type Definition

All personas (human or agent) share a common interface:

```typescript
// src/types/persona.types.ts

/**
 * Persona execution mode - human or automated
 */
export type PersonaExecutionMode = "human" | "agent" | "hybrid";

/**
 * Unified Persona interface - agnostic to human/agent
 */
export interface Persona {
  id: string;
  name: string;
  role: PersonaId; // 'engineer' | 'lead' | 'sre' | 'security' | 'admin' | 'viewer'
  executionMode: PersonaExecutionMode;

  // Capacity & Availability (applies to both human and agent)
  capacity: PersonaCapacity;
  availability: PersonaAvailability;

  // Growth & Development (agents have skill upgrades, humans have career growth)
  growth: PersonaGrowth;

  // DevSecOps flow configuration
  flow: DevSecOpsFlowConfig;

  // Tool integrations available to this persona
  tools: PersonaToolConfig[];

  // Current work context
  activeWorkItems: WorkItem[];
  currentPhase: DevSecOpsPhaseId | null;
}

/**
 * Capacity applies to both humans and agents
 * - Humans: work hours, PTO, meetings
 * - Agents: compute budget, rate limits, concurrent tasks
 */
export interface PersonaCapacity {
  // Available capacity (0-100%)
  available: number;
  // Allocated capacity (0-100%)
  allocated: number;
  // Capacity type
  type: "hours" | "compute" | "tasks";
  // Daily/weekly limits
  dailyLimit: number;
  weeklyLimit: number;
  // Current utilization
  currentUtilization: number;
}

/**
 * Availability schedule - same for humans and agents
 * - Humans: work schedule, time-off, holidays
 * - Agents: maintenance windows, scheduled downtime, upgrades
 */
export interface PersonaAvailability {
  // Current status
  status: "available" | "busy" | "away" | "offline" | "maintenance";
  // Schedule (work hours for humans, uptime for agents)
  schedule: AvailabilitySchedule;
  // Planned unavailability (PTO for humans, maintenance for agents)
  plannedAbsences: PlannedAbsence[];
  // Next available time
  nextAvailable: Date | null;
}

/**
 * Growth tracking - unified for humans and agents
 * - Humans: skills, certifications, career level
 * - Agents: model versions, capability upgrades, fine-tuning
 */
export interface PersonaGrowth {
  // Current level/version
  level: string;
  // Progress to next level (0-100%)
  progressToNext: number;
  // Active goals/upgrades
  activeGoals: GrowthGoal[];
  // Completed milestones
  completedMilestones: GrowthMilestone[];
  // Skills/capabilities
  capabilities: Capability[];
}
```

### 0.2 Unified Work Item Model

Work items are the same regardless of who executes them:

```typescript
/**
 * Work item that can be executed by any persona
 */
export interface WorkItem {
  id: string;
  title: string;
  description: string;

  // DevSecOps context
  phase: DevSecOpsPhaseId;
  stepId: string;

  // Assignment
  assignee: Persona;
  collaborators: Persona[];

  // Status
  status: WorkItemStatus;
  priority: Priority;

  // Execution context
  executionContext: ExecutionContext;

  // Tools required for this work item
  requiredTools: ToolRequirement[];

  // Embedded actions - what can be done from dashboard
  availableActions: WorkItemAction[];
}

/**
 * Execution context - provides all tools needed to complete work
 */
export interface ExecutionContext {
  // Canvas for visual work (diagrams, flows, etc.)
  canvas?: CanvasContext;
  // Terminal for command execution
  terminal?: TerminalContext;
  // Editor for code/document editing
  editor?: EditorContext;
  // External tool integrations
  integrations: IntegrationContext[];
  // AI assistance
  aiAssistant?: AIAssistantContext;
}
```

---

## Phase 1: Foundation & Data Layer

### 1.1 Define Persona Domain Model

Create comprehensive TypeScript types for persona work management:

```
src/types/persona.types.ts
```

**Core Entities:**

- `Persona` - Unified human/agent representation
- `WorkItem` - Tasks with full lifecycle and embedded tools
- `ExecutionContext` - Tools, canvas, terminal for task completion
- `Capacity` - Work capacity (hours for humans, compute for agents)
- `Availability` - Schedule and time-off (maintenance for agents)
- `Growth` - Skills/capabilities progression
- `Notification` - System notifications
- `Approval` - Pending approvals

**Work Item Status Flow (aligned with DevSecOps):**

```
intake → plan → build → verify → review → staging → deploy → operate → learn
```

**Priority Levels:**

```
critical → high → medium → low
```

**Work Item Types by Persona:**
| Persona | Work Item Types |
|---------|-----------------|
| Engineer | story, bug, spike, tech-debt, code-review |
| Lead | approval, planning, review, retrospective |
| SRE | incident, deployment, monitoring, postmortem |
| Security | vulnerability, audit, compliance, scan |
| Admin | configuration, access, integration, policy |

### 1.2 Create IC State Management

```
src/state/jotai/ic.atoms.ts
```

**Atoms to Create:**

- `icTasksAtom` - All tasks for current IC
- `icTaskFiltersAtom` - Filter state (status, priority, type, date range)
- `icSelectedTaskAtom` - Currently selected task for detail view
- `icTimeEntriesAtom` - Time tracking entries
- `icMeetingsAtom` - Calendar meetings
- `icGrowthGoalsAtom` - Career goals
- `icNotificationsAtom` - Notifications
- `icWorkSessionAtom` - Active work session state
- `icDashboardLayoutAtom` - Dashboard widget preferences

### 1.3 Create IC API Service

```
src/services/icApi.ts
```

**Endpoints:**
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/ic/tasks` | Fetch all tasks with filters |
| POST | `/ic/tasks` | Create new task |
| PUT | `/ic/tasks/:id` | Update task |
| PATCH | `/ic/tasks/:id/status` | Change task status |
| DELETE | `/ic/tasks/:id` | Delete task |
| GET | `/ic/tasks/:id/history` | Task activity history |
| POST | `/ic/tasks/:id/comment` | Add comment |
| GET | `/ic/time-entries` | Get time entries |
| POST | `/ic/time-entries` | Log time |
| GET | `/ic/meetings` | Get meetings |
| POST | `/ic/meetings/:id/rsvp` | RSVP to meeting |
| GET | `/ic/growth` | Get growth goals |
| PUT | `/ic/growth/:id` | Update goal progress |
| GET | `/ic/metrics` | Get personal metrics |
| GET | `/ic/notifications` | Get notifications |
| PATCH | `/ic/notifications/:id/read` | Mark notification read |
| POST | `/ic/work-session/start` | Start work session |
| POST | `/ic/work-session/end` | End work session |

### 1.4 Create Mock Data

```
src/config/mockICData.ts
```

Comprehensive mock data for development:

- 50+ sample tasks across all domains
- Time entries for the past month
- Meetings for current week
- Growth goals with progress
- Notifications
- Metrics data

---

## Phase 2: Dashboard Components

### 2.1 Dashboard Layout Structure

```
ICDashboard
├── WelcomeHeader
│   ├── Greeting
│   ├── CurrentDate
│   └── WorkSessionToggle
├── KPIRow
│   ├── TasksInProgress
│   ├── TasksDueToday
│   ├── MeetingsToday
│   └── WeeklyCompletion
├── MainContent (2-column)
│   ├── LeftColumn
│   │   ├── MyTasksCard (actionable)
│   │   ├── TimeTrackingCard
│   │   └── QuickActionsCard
│   └── RightColumn
│       ├── TodayScheduleCard
│       ├── NotificationsCard
│       └── GrowthProgressCard
└── AIInsightsCard
```

### 2.2 Component Implementation

#### 2.2.1 MyTasksCard (Priority)

**Features:**

- List of top 5 tasks sorted by priority/due date
- Click task → Navigate to task detail page
- Status chip with color coding
- Priority indicator
- Due date with overdue highlighting
- Quick actions: Start, Complete, Snooze
- "View All Tasks" → Navigate to `/ic/tasks`

**Actions:**

- Click task row → `/ic/tasks/:taskId`
- Start button → Changes status to `in-progress`, starts timer
- Complete button → Opens completion modal
- View All → `/ic/tasks`

#### 2.2.2 TodayScheduleCard

**Features:**

- Today's meetings chronologically
- Meeting title, time, duration
- Attendee avatars
- Join button (if virtual)
- RSVP status

**Actions:**

- Click meeting → Opens meeting detail modal
- Join button → Opens meeting link
- RSVP buttons → Updates attendance status
- View Calendar → `/ic/meetings`

#### 2.2.3 QuickActionsCard

**Actions Grid:**
| Action | Icon | Behavior |
|--------|------|----------|
| Start Work Session | ▶️ | Toggles work session, shows timer |
| Log Time | ⏱️ | Opens time entry modal |
| Create Task | ➕ | Opens task creation modal |
| Request Time Off | 🏖️ | Opens PTO request modal |
| Update Status | 📝 | Opens status update modal |
| Submit Expense | 💰 | Opens expense form |
| Request Review | 👀 | Opens review request modal |
| Report Issue | 🐛 | Opens issue report modal |

#### 2.2.4 NotificationsCard

**Features:**

- Unread notifications with badges
- Notification types: task, mention, approval, system
- Mark as read on click
- Bulk mark all as read

**Actions:**

- Click notification → Navigate to relevant page
- Mark read → Updates notification state
- View All → `/ic/notifications`

#### 2.2.5 GrowthProgressCard

**Features:**

- Current level/title
- Progress to next level
- Active goals with completion %
- Skills being developed

**Actions:**

- Click goal → Opens goal detail
- View Growth Plan → `/ic/growth`

#### 2.2.6 TimeTrackingCard

**Features:**

- Today's logged hours
- Weekly total
- Active timer (if work session active)
- Recent time entries

**Actions:**

- Start/Stop timer
- Log manual entry
- View timesheet → `/ic/timesheet`

#### 2.2.7 AIInsightsCard

**Features:**

- Personalized recommendations
- Productivity tips
- Overdue task warnings
- Meeting preparation reminders
- Skill development suggestions

---

## Phase 2.5: Embedded Task Execution (Critical)

This is the **core differentiator** - tasks are not just tracked, they are **executed** from within the dashboard with all necessary tools embedded.

### 2.5.1 Execution Context Architecture

Every work item has an `ExecutionContext` that provides all tools needed to complete the work:

```typescript
/**
 * Execution context embedded in work item view
 */
interface WorkItemExecutionContext {
  // The work item being executed
  workItem: WorkItem;

  // Current DevSecOps phase and step
  currentStep: FlowStep;

  // Available tools for this step
  tools: {
    // Canvas for visual work (architecture diagrams, flow charts)
    canvas?: {
      enabled: boolean;
      template?: string;
      artifacts: CanvasArtifact[];
    };

    // Terminal for command execution
    terminal?: {
      enabled: boolean;
      allowedCommands: string[];
      environment: Record<string, string>;
    };

    // Code editor for inline editing
    editor?: {
      enabled: boolean;
      language: string;
      files: FileContext[];
    };

    // External integrations
    integrations: {
      vcs?: VCSIntegration; // GitHub, GitLab
      ci?: CIIntegration; // Jenkins, GitHub Actions
      observability?: ObservabilityIntegration; // Datadog, Grafana
      security?: SecurityIntegration; // Snyk, SonarQube
    };

    // AI assistant for guidance
    aiAssistant: {
      enabled: boolean;
      context: string;
      suggestedActions: AIAction[];
    };
  };

  // Actions available at this step
  actions: WorkItemAction[];

  // Next step in the flow
  nextStep?: FlowStep;
}
```

### 2.5.2 Embedded Tool Components

| Tool          | Component              | Purpose                              |
| ------------- | ---------------------- | ------------------------------------ |
| Canvas        | `<WorkItemCanvas />`   | Visual diagrams, architecture, flows |
| Terminal      | `<WorkItemTerminal />` | Run commands, scripts, deployments   |
| Editor        | `<WorkItemEditor />`   | Edit code, configs, docs inline      |
| VCS           | `<VCSPanel />`         | View PRs, commits, branches          |
| CI            | `<CIPipelinePanel />`  | View/trigger builds, tests           |
| Observability | `<MetricsPanel />`     | View logs, metrics, traces           |
| Security      | `<SecurityPanel />`    | View vulnerabilities, compliance     |
| AI            | `<AIAssistantPanel />` | Get suggestions, automate            |

### 2.5.3 Complete DevSecOps Flow Execution

Each phase has specific tools and actions:

| Phase       | Tools                   | Actions                             |
| ----------- | ----------------------- | ----------------------------------- |
| **Intake**  | AI Assistant            | Accept story, clarify requirements  |
| **Plan**    | Canvas, Editor          | Create design, estimate, break down |
| **Build**   | Editor, Terminal, VCS   | Write code, commit, push            |
| **Verify**  | CI, Terminal            | Run tests, fix failures             |
| **Review**  | VCS, Editor             | Review PRs, address feedback        |
| **Staging** | CI, Observability       | Deploy to staging, validate         |
| **Deploy**  | CI, Terminal            | Promote to production               |
| **Operate** | Observability, Security | Monitor, respond to incidents       |
| **Learn**   | AI Assistant, Editor    | Document learnings, retrospective   |

### 2.5.4 Work Item Detail Page with Embedded Execution

```
WorkItemDetailPage
├── Header
│   ├── Title, Status, Priority
│   ├── DevSecOps Phase Indicator
│   └── Quick Actions (status change, assign)
├── FlowStrip
│   └── Visual progress through DevSecOps phases
├── ExecutionPanel (main content)
│   ├── ToolTabs
│   │   ├── Canvas Tab
│   │   ├── Terminal Tab
│   │   ├── Editor Tab
│   │   ├── Integrations Tab
│   │   └── AI Assistant Tab
│   └── ActiveTool (renders selected tool)
├── ContextSidebar
│   ├── Description
│   ├── Acceptance Criteria
│   ├── Linked Items
│   ├── Comments
│   └── Activity Log
└── ActionBar
    ├── Primary Action (e.g., "Move to Review")
    ├── Secondary Actions
    └── More Options
```

### 2.5.5 Example: Engineer Story Flow

**Step 1: Intake** (Dashboard → Story Card → Click)

- View story details
- AI suggests clarifying questions
- Accept story → moves to Plan

**Step 2: Plan** (Embedded Canvas)

- Open Canvas tool
- Create architecture diagram
- AI suggests affected services
- Save plan → moves to Build

**Step 3: Build** (Embedded Editor + Terminal)

- View linked repository
- Open Editor for code changes
- Run local tests in Terminal
- Commit and push via VCS panel
- → moves to Verify

**Step 4: Verify** (Embedded CI Panel)

- View CI pipeline status
- See test results inline
- Fix failures in Editor
- All green → moves to Review

**Step 5: Review** (Embedded VCS Panel)

- View PR status
- See review comments
- Address feedback in Editor
- Approved → moves to Staging

**Step 6: Staging** (Embedded Observability)

- View staging deployment
- Check metrics and logs
- Validate behavior
- → moves to Deploy

**Step 7: Deploy** (Embedded CI + Observability)

- Trigger production deploy
- Monitor rollout
- Check error rates
- → moves to Operate

**Step 8: Operate** (Embedded Observability)

- Monitor production metrics
- Respond to alerts
- Close story when stable
- → moves to Learn

**Step 9: Learn** (Embedded AI + Editor)

- AI generates retrospective summary
- Document learnings
- Update knowledge base
- → Story Complete

---

## Phase 3: Detail Pages

### 3.1 Tasks Page (`/ic/tasks`)

**Features:**

- Kanban board view (default)
- List view toggle
- Calendar view toggle
- Advanced filters (status, priority, type, date, tags)
- Search
- Bulk actions
- Create new task
- Drag-and-drop status changes

**Task Detail Panel:**

- Full task information
- Activity history
- Comments
- Time entries
- Attachments
- Related tasks
- Status change buttons
- Edit button
- Delete button

### 3.2 Task Detail Page (`/ic/tasks/:taskId`)

**Sections:**

- Header: Title, status, priority, assignee
- Description: Rich text with markdown
- Details: Due date, estimate, tags, project
- Activity: Timeline of changes
- Comments: Discussion thread
- Time: Logged time entries
- Attachments: Files
- Related: Linked tasks

**Actions:**

- Change status (with workflow validation)
- Edit task
- Log time
- Add comment
- Attach file
- Link related task
- Delete task

### 3.3 Meetings Page (`/ic/meetings`)

**Features:**

- Calendar view (week/month)
- List view
- Meeting details
- RSVP management
- Create meeting
- Meeting notes

### 3.4 Growth Page (`/ic/growth`)

**Features:**

- Career level progression
- Active goals
- Completed goals
- Skills matrix
- Learning resources
- Feedback history
- 1:1 notes

### 3.5 Metrics Page (`/ic/metrics`)

**Features:**

- Task completion rate
- Average cycle time
- Code review stats (for devs)
- Test coverage (for QA)
- Sprint velocity
- Time distribution
- Comparison to team average

### 3.6 Timesheet Page (`/ic/timesheet`)

**Features:**

- Weekly timesheet view
- Daily breakdown
- Project allocation
- Export to CSV
- Submit for approval

### 3.7 Settings Page (`/ic/settings`)

**Features:**

- Notification preferences
- Dashboard layout customization
- Theme preferences
- Integration settings
- Profile settings

---

## Phase 4: Domain-Specific Features

### 4.1 Developer Features

**Additional Task Types:**

- Pull Request reviews
- Code review assignments
- Tech debt items
- Spike investigations

**Additional Pages:**

- `/ic/code-reviews` - PR review queue
- `/ic/deployments` - Deployment history

**Dashboard Widgets:**

- PR review queue
- Build status
- Code coverage trend

### 4.2 QA Features

**Additional Task Types:**

- Test case execution
- Bug verification
- Automation tasks
- Regression cycles

**Additional Pages:**

- `/ic/test-runs` - Test execution
- `/ic/bugs` - Bug tracking

**Dashboard Widgets:**

- Test execution status
- Bug trends
- Coverage metrics

### 4.3 Security Features

**Additional Task Types:**

- Vulnerability assessment
- Security audit
- Compliance check
- Penetration test

**Additional Pages:**

- `/ic/vulnerabilities` - Security findings
- `/ic/compliance` - Compliance status

**Dashboard Widgets:**

- Open vulnerabilities
- Compliance score
- Security alerts

### 4.4 Design Features

**Additional Task Types:**

- Design mockup
- Prototype
- User research
- Design review

**Additional Pages:**

- `/ic/designs` - Design library
- `/ic/research` - User research

**Dashboard Widgets:**

- Design review queue
- Prototype feedback
- Research insights

---

## Phase 5: Implementation Order

### Sprint 1: Foundation (Week 1-2)

1. **Day 1-2:** Create IC types and interfaces
2. **Day 3-4:** Implement IC atoms and state management
3. **Day 5-6:** Create IC API service with mock data
4. **Day 7-8:** Create React Query hooks for data fetching
5. **Day 9-10:** Unit tests for state and services

**Deliverables:**

- `src/types/ic.types.ts`
- `src/state/jotai/ic.atoms.ts`
- `src/services/icApi.ts`
- `src/config/mockICData.ts`
- `src/hooks/useICTasks.ts`
- `src/hooks/useICMeetings.ts`
- `src/hooks/useICGrowth.ts`

### Sprint 2: Dashboard Core (Week 3-4)

1. **Day 1-3:** Refactor ICDashboard with new components
2. **Day 4-5:** Implement MyTasksCard with actions
3. **Day 6-7:** Implement TodayScheduleCard
4. **Day 8-9:** Implement QuickActionsCard
5. **Day 10:** Integration testing

**Deliverables:**

- Refactored `ICDashboard.tsx`
- `MyTasksCard.tsx`
- `TodayScheduleCard.tsx`
- `QuickActionsCard.tsx`
- Working navigation from dashboard

### Sprint 3: Tasks Feature (Week 5-6)

1. **Day 1-3:** Tasks list page with Kanban
2. **Day 4-5:** Task detail page
3. **Day 6-7:** Task creation/edit modals
4. **Day 8-9:** Status change workflow
5. **Day 10:** E2E testing

**Deliverables:**

- `ICTasksPage.tsx` (Kanban + List views)
- `ICTaskDetailPage.tsx`
- `TaskCreateModal.tsx`
- `TaskEditModal.tsx`
- Working task CRUD operations

### Sprint 4: Supporting Features (Week 7-8)

1. **Day 1-2:** Meetings page
2. **Day 3-4:** Growth page
3. **Day 5-6:** Metrics page
4. **Day 7-8:** Timesheet page
5. **Day 9-10:** Settings page

**Deliverables:**

- `ICMeetingsPage.tsx`
- `ICGrowthPage.tsx`
- `ICMetricsPage.tsx`
- `ICTimesheetPage.tsx`
- `ICSettingsPage.tsx`

### Sprint 5: Polish & Domain Features (Week 9-10)

1. **Day 1-3:** Domain-specific widgets
2. **Day 4-5:** AI insights integration
3. **Day 6-7:** Notifications system
4. **Day 8-9:** Performance optimization
5. **Day 10:** Final testing and documentation

---

## Phase 6: Routes Configuration

### Updated Routes

```typescript
// IC routes in routes.ts
layout("./routes/ic/layout.tsx", [
    route("/ic", "./routes/ic/dashboard.tsx"),
    route("/ic/tasks", "./routes/ic/tasks.tsx"),
    route("/ic/tasks/:taskId", "./routes/ic/task-detail.tsx"),
    route("/ic/meetings", "./routes/ic/meetings.tsx"),
    route("/ic/meetings/:meetingId", "./routes/ic/meeting-detail.tsx"),
    route("/ic/growth", "./routes/ic/growth.tsx"),
    route("/ic/metrics", "./routes/ic/metrics.tsx"),
    route("/ic/timesheet", "./routes/ic/timesheet.tsx"),
    route("/ic/notifications", "./routes/ic/notifications.tsx"),
    route("/ic/settings", "./routes/ic/settings.tsx"),
    // Domain-specific routes
    route("/ic/code-reviews", "./routes/ic/code-reviews.tsx"),
    route("/ic/test-runs", "./routes/ic/test-runs.tsx"),
    route("/ic/vulnerabilities", "./routes/ic/vulnerabilities.tsx"),
]),
```

### Updated Navigation

```typescript
// ICLayout.tsx navigation sections
const icSections = [
  {
    title: "Work",
    items: [
      { id: "dashboard", label: "Dashboard", icon: "📊", path: "/ic" },
      {
        id: "tasks",
        label: "My Tasks",
        icon: "✅",
        path: "/ic/tasks",
        badge: taskCount,
      },
      {
        id: "timesheet",
        label: "Timesheet",
        icon: "⏱️",
        path: "/ic/timesheet",
      },
    ],
  },
  {
    title: "Collaborate",
    items: [
      {
        id: "meetings",
        label: "Meetings",
        icon: "📅",
        path: "/ic/meetings",
        badge: meetingCount,
      },
      {
        id: "notifications",
        label: "Notifications",
        icon: "🔔",
        path: "/ic/notifications",
        badge: unreadCount,
      },
    ],
  },
  {
    title: "Growth",
    items: [
      { id: "growth", label: "Growth Plan", icon: "📈", path: "/ic/growth" },
      { id: "metrics", label: "My Metrics", icon: "🎯", path: "/ic/metrics" },
    ],
  },
  {
    title: "Settings",
    items: [
      { id: "settings", label: "Settings", icon: "⚙️", path: "/ic/settings" },
    ],
  },
];
```

---

## Phase 7: File Structure

```
src/
├── types/
│   └── ic.types.ts                 # IC domain types
├── state/
│   └── jotai/
│       └── ic.atoms.ts             # IC state atoms
├── services/
│   └── icApi.ts                    # IC API service
├── config/
│   └── mockICData.ts               # Mock data for development
├── hooks/
│   ├── useICTasks.ts               # Tasks hook
│   ├── useICMeetings.ts            # Meetings hook
│   ├── useICGrowth.ts              # Growth hook
│   ├── useICMetrics.ts             # Metrics hook
│   ├── useICTimeTracking.ts        # Time tracking hook
│   └── useICNotifications.ts       # Notifications hook
├── components/
│   └── ic/
│       ├── dashboard/
│       │   ├── MyTasksCard.tsx
│       │   ├── TodayScheduleCard.tsx
│       │   ├── QuickActionsCard.tsx
│       │   ├── NotificationsCard.tsx
│       │   ├── GrowthProgressCard.tsx
│       │   ├── TimeTrackingCard.tsx
│       │   └── AIInsightsCard.tsx
│       ├── tasks/
│       │   ├── TaskKanban.tsx
│       │   ├── TaskList.tsx
│       │   ├── TaskCard.tsx
│       │   ├── TaskDetail.tsx
│       │   ├── TaskCreateModal.tsx
│       │   ├── TaskEditModal.tsx
│       │   └── TaskFilters.tsx
│       ├── meetings/
│       │   ├── MeetingCalendar.tsx
│       │   ├── MeetingList.tsx
│       │   └── MeetingDetail.tsx
│       ├── growth/
│       │   ├── GrowthTimeline.tsx
│       │   ├── GoalCard.tsx
│       │   └── SkillsMatrix.tsx
│       ├── metrics/
│       │   ├── MetricsDashboard.tsx
│       │   └── MetricChart.tsx
│       └── timesheet/
│           ├── TimesheetGrid.tsx
│           └── TimeEntryModal.tsx
├── pages/
│   └── ic/
│       ├── ICDashboard.tsx         # Refactored dashboard
│       ├── ICTasksPage.tsx         # Tasks with Kanban
│       ├── ICTaskDetailPage.tsx    # Single task view
│       ├── ICMeetingsPage.tsx      # Meetings calendar
│       ├── ICGrowthPage.tsx        # Growth plan
│       ├── ICMetricsPage.tsx       # Personal metrics
│       ├── ICTimesheetPage.tsx     # Time tracking
│       ├── ICNotificationsPage.tsx # Notifications
│       └── ICSettingsPage.tsx      # Settings
└── app/
    └── routes/
        └── ic/
            ├── layout.tsx
            ├── dashboard.tsx
            ├── tasks.tsx
            ├── task-detail.tsx
            ├── meetings.tsx
            ├── growth.tsx
            ├── metrics.tsx
            ├── timesheet.tsx
            ├── notifications.tsx
            └── settings.tsx
```

---

## Phase 8: Success Criteria

### Functional Requirements

- [ ] All dashboard items are clickable and navigate correctly
- [ ] Tasks can be created, edited, and deleted
- [ ] Task status can be changed via drag-drop or buttons
- [ ] Time can be logged against tasks
- [ ] Meetings show with RSVP functionality
- [ ] Notifications are real-time and actionable
- [ ] Growth goals can be tracked and updated
- [ ] Metrics display accurate data
- [ ] All quick actions produce appropriate results

### Non-Functional Requirements

- [ ] Page load time < 2 seconds
- [ ] Smooth animations and transitions
- [ ] Responsive design (mobile-friendly)
- [ ] Accessible (WCAG AA compliant)
- [ ] Dark mode support
- [ ] Offline capability for viewing cached data

### Testing Requirements

- [ ] Unit tests for all hooks and services
- [ ] Integration tests for API calls
- [ ] E2E tests for critical user flows
- [ ] Visual regression tests for components

---

## Appendix A: Task Status Workflow

```
┌─────────┐     ┌──────┐     ┌─────────────┐     ┌────────┐     ┌──────┐
│ Backlog │ ──► │ Todo │ ──► │ In Progress │ ──► │ Review │ ──► │ Done │
└─────────┘     └──────┘     └─────────────┘     └────────┘     └──────┘
                                   │                  │
                                   ▼                  ▼
                              ┌─────────┐        ┌─────────┐
                              │ Blocked │        │ Blocked │
                              └─────────┘        └─────────┘
```

## Appendix B: Quick Action Flows

### Start Work Session

1. Click "Start Work Session"
2. System records start time
3. Timer appears in header
4. Optional: Select task to work on
5. Click "End Session" to stop
6. Time entry created automatically

### Create Task

1. Click "Create Task"
2. Modal opens with form
3. Fill: Title, Description, Priority, Due Date, Tags
4. Click "Create"
5. Task added to backlog
6. Navigate to task detail (optional)

### Log Time

1. Click "Log Time"
2. Modal opens
3. Select task (or general)
4. Enter hours/minutes
5. Add notes (optional)
6. Click "Log"
7. Time entry created

### Request Time Off

1. Click "Request Time Off"
2. Modal opens
3. Select dates
4. Select type (vacation, sick, personal)
5. Add notes
6. Click "Submit"
7. Request sent to manager

---

## Appendix C: Notification Types

| Type                | Icon | Action               |
| ------------------- | ---- | -------------------- |
| task_assigned       | 📋   | Navigate to task     |
| task_mentioned      | 💬   | Navigate to comment  |
| task_due_soon       | ⏰   | Navigate to task     |
| task_overdue        | 🚨   | Navigate to task     |
| meeting_reminder    | 📅   | Navigate to meeting  |
| review_requested    | 👀   | Navigate to review   |
| approval_needed     | ✅   | Navigate to approval |
| goal_milestone      | 🎯   | Navigate to goal     |
| system_announcement | 📢   | Show announcement    |

---

## Next Steps

1. **Review this plan** with stakeholders
2. **Prioritize features** based on IC feedback
3. **Begin Sprint 1** with foundation work
4. **Iterate** based on user testing

---

_Document Version: 1.0_
_Last Updated: December 2025_
_Author: Cascade AI Assistant_
