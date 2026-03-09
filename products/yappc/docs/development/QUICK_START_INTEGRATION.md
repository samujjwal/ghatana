# Quick Start: API Integration Implementation

**Date**: January 29, 2026  
**For**: Immediate action items to start integrating backend and frontend

---

## 🚀 Today's Actions (1-2 hours)

### Step 1: Verify Current Setup (15 min)

```bash
# 1. Check if services are running
cd /home/samujjwal/Developments/ghatana/products/yappc

# Start backend services
docker-compose up -d postgres redis

# Start Node.js API (Port 7002)
cd app-creator/apps/api
pnpm install
pnpm dev

# Start Java API (Port 7003) - in a new terminal
cd backend/api
./gradlew bootRun

# Start Frontend (Port 7001) - in a new terminal
cd app-creator/apps/web
pnpm install
pnpm dev
```

**Verification**:

```bash
# Test each service
curl http://localhost:7002/health  # Node.js API
curl http://localhost:7003/health  # Java API
curl http://localhost:7001         # Frontend

# Test API routing
curl http://localhost:7002/api/workspaces  # Should work (Node.js)
curl http://localhost:7002/api/rail/components  # Should proxy to Java
```

### Step 2: Document Current Endpoint Status (30 min)

Create a simple spreadsheet or document:

| Endpoint                  | Backend | Implemented | Frontend Uses | Status               |
| ------------------------- | ------- | ----------- | ------------- | -------------------- |
| GET /api/workspaces       | Node.js | ✅          | ✅            | Working              |
| GET /api/projects         | Node.js | ✅          | ✅            | Working              |
| GET /api/rail/components  | Java    | ✅          | ⚠️            | Needs FE integration |
| GET /api/lifecycle/phases | Node.js | ❌          | ❌            | Not implemented      |

### Step 3: Fix One Critical Gap (45 min)

Let's implement the Lifecycle Phases endpoint as a quick win:

#### Backend Implementation

**File**: `app-creator/apps/api/src/routes/lifecycle.ts`

```typescript
import { FastifyInstance } from "fastify";

export default async function lifecycleRoutes(fastify: FastifyInstance) {
  // GET /api/lifecycle/phases - List all lifecycle phases
  fastify.get("/lifecycle/phases", async (request, reply) => {
    return {
      phases: [
        {
          id: "INTENT",
          name: "Intent",
          order: 1,
          description: "Define what to build",
        },
        {
          id: "SHAPE",
          name: "Shape",
          order: 2,
          description: "Design the solution",
        },
        {
          id: "VALIDATE",
          name: "Validate",
          order: 3,
          description: "Validate requirements",
        },
        {
          id: "GENERATE",
          name: "Generate",
          order: 4,
          description: "Generate code",
        },
        { id: "RUN", name: "Run", order: 5, description: "Deploy and run" },
        {
          id: "OBSERVE",
          name: "Observe",
          order: 6,
          description: "Monitor execution",
        },
        {
          id: "IMPROVE",
          name: "Improve",
          order: 7,
          description: "Iterate and improve",
        },
      ],
    };
  });

  // GET /api/lifecycle/projects/:id/current - Get current phase
  fastify.get<{ Params: { id: string } }>(
    "/lifecycle/projects/:id/current",
    async (request, reply) => {
      const { id } = request.params;

      // Query database for project's current phase
      // For now, return mock data
      return {
        projectId: id,
        currentPhase: "SHAPE",
        phaseStartedAt: new Date().toISOString(),
        progress: 45,
      };
    },
  );
}
```

**Register the route in**: `app-creator/apps/api/src/index.ts`

```typescript
// Add to imports
import lifecycleRoutes from "./routes/lifecycle";

// Add to route registration (around line 145)
app.register(lifecycleRoutes, { prefix: "/api" });
```

#### Frontend Integration

**File**: `app-creator/apps/web/src/hooks/api/useLifecycle.ts`

```typescript
import { useQuery } from "@tanstack/react-query";

const API_BASE = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? "http://localhost:7002"}/api`
  : "/api";

export function useLifecyclePhases() {
  return useQuery({
    queryKey: ["lifecycle", "phases"],
    queryFn: async () => {
      const response = await fetch(`${API_BASE}/lifecycle/phases`);
      if (!response.ok) throw new Error("Failed to fetch phases");
      return response.json();
    },
  });
}

export function useProjectPhase(projectId: string) {
  return useQuery({
    queryKey: ["lifecycle", "project", projectId, "current"],
    queryFn: async () => {
      const response = await fetch(
        `${API_BASE}/lifecycle/projects/${projectId}/current`,
      );
      if (!response.ok) throw new Error("Failed to fetch current phase");
      return response.json();
    },
    enabled: !!projectId,
  });
}
```

**Test it in a component**:

```typescript
// In any component
import { useLifecyclePhases } from '@/hooks/api/useLifecycle';

function MyComponent() {
  const { data: phases, isLoading } = useLifecyclePhases();

  if (isLoading) return <div>Loading...</div>;

  return (
    <div>
      <h2>Lifecycle Phases</h2>
      <ul>
        {phases?.phases.map((phase) => (
          <li key={phase.id}>{phase.name} - {phase.description}</li>
        ))}
      </ul>
    </div>
  );
}
```

---

## 📋 This Week's Priorities

### Monday-Tuesday: Documentation & Planning

**Create API Ownership Document**:

```markdown
# API Ownership Matrix

## Node.js Backend Endpoints

- /api/workspaces/\* - OWNER: @team-nodejs
- /api/projects/\* - OWNER: @team-nodejs
- /api/canvas/\* - OWNER: @team-nodejs
- /api/lifecycle/\* - OWNER: @team-nodejs

## Java Backend Endpoints

- /api/rail/\* - OWNER: @team-java
- /api/agents/\* - OWNER: @team-java
- /api/requirements/\* - OWNER: @team-java
- /api/ai/\* - OWNER: @team-java

## Action Items

1. Review this document
2. Assign actual team members
3. Create GitHub issues for each endpoint
4. Start implementation
```

### Wednesday-Thursday: Implementation

**Priority Endpoints to Complete**:

1. **Lifecycle API** (Critical)
   - [ ] Implement phase transition endpoint
   - [ ] Implement artifact CRUD endpoints
   - [ ] Implement gate validation endpoint

2. **Canvas AI** (High Priority)
   - [ ] Implement AI suggestion endpoint
   - [ ] Implement AI validation endpoint

3. **Agent Integration** (High Priority)
   - [ ] Ensure agent endpoints are accessible from frontend
   - [ ] Create frontend hooks for agent execution

### Friday: Testing & Review

**Testing Checklist**:

- [ ] All new endpoints work via curl
- [ ] All new endpoints work from frontend
- [ ] Error handling works correctly
- [ ] Response format is consistent
- [ ] Documentation is updated

---

## 🔧 Quick Fixes for Common Issues

### Issue 1: CORS Errors

**Symptom**: Browser console shows CORS error

**Fix in**: `app-creator/apps/api/src/index.ts`

```typescript
// @ts-ignore - CORS plugin type issue
app.register(cors, {
  origin: true, // Allow all origins in dev
  methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
  credentials: true,
});
```

### Issue 2: API Gateway Not Proxying

**Symptom**: 404 errors for Java endpoints

**Fix in**: `app-creator/apps/api/src/index.ts`

Check the catch-all route around line 151:

```typescript
// Ensure this comes AFTER all other route registrations
app.all<{ Params: { "*": string } }>("/api/*", async (request, reply) => {
  const javaBackendUrl =
    process.env.JAVA_BACKEND_URL || "http://localhost:7003";
  const targetUrl = new URL(javaBackendUrl + request.url);

  console.log("[Gateway] Proxying to:", targetUrl.toString());

  // ... rest of proxy logic
});
```

### Issue 3: Database Connection Errors

**Symptom**: 503 errors from workspace endpoint

**Fix**:

1. Check PostgreSQL is running:

   ```bash
   docker-compose ps postgres
   ```

2. Check connection string in `.env`:

   ```bash
   DATABASE_URL=postgresql://ghatana:ghatana123@localhost:5432/yappc
   ```

3. Run migrations:
   ```bash
   cd app-creator/apps/api
   npx prisma migrate dev
   ```

---

## 📊 Progress Tracking

### Daily Standup Template

```
Yesterday:
- ✅ Verified all services are running
- ✅ Documented current endpoint status
- ✅ Implemented lifecycle/phases endpoint

Today:
- 🔄 Implementing lifecycle phase transition
- 🔄 Creating frontend hooks for lifecycle
- 🔄 Writing tests for new endpoints

Blockers:
- ⚠️ Need clarification on gate validation logic
- ⚠️ Waiting for Java team to expose agent health endpoint
```

### Week 1 Goals

| Goal                      | Status         | Owner      | Due       |
| ------------------------- | -------------- | ---------- | --------- |
| Complete lifecycle API    | 🔄 In Progress | @you       | Thu       |
| Integrate agent endpoints | ⚠️ Waiting     | @java-team | Fri       |
| Update API documentation  | ⚠️ Not Started | @you       | Fri       |
| Write E2E tests           | ⚠️ Not Started | @qa-team   | Next week |

---

## 🎯 Success Metrics for Week 1

**Goals**:

- ✅ All services running and accessible
- ✅ At least 3 new endpoints implemented
- ✅ Frontend successfully consuming new endpoints
- ✅ Documentation updated
- ✅ No critical bugs in production

**Measurements**:

```bash
# Check API health
curl http://localhost:7002/health
curl http://localhost:7003/health

# Count implemented endpoints
grep -r "fastify.get\|fastify.post" app-creator/apps/api/src/routes/ | wc -l

# Check frontend API usage
grep -r "fetch.*\/api\/" app-creator/apps/web/src/ | wc -l
```

---

## 📚 Reference Links

### Documentation

- [Full Integration Plan](./BACKEND_FRONTEND_INTEGRATION_PLAN.md)
- [API Checklist](./API_CHECKLIST.md)
- [Architecture Diagrams](./API_ARCHITECTURE_DIAGRAMS.md)
- [API Gateway Architecture](./API_GATEWAY_ARCHITECTURE.md)

### Code Locations

- **Node.js API**: `app-creator/apps/api/src/`
- **Java API**: `backend/api/src/main/java/`
- **Frontend**: `app-creator/apps/web/src/`
- **API Routes**: `app-creator/apps/api/src/routes/`
- **Frontend Hooks**: `app-creator/apps/web/src/hooks/`

### Tools

- **API Testing**: `curl`, Postman, Bruno
- **Database**: pgAdmin, DBeaver
- **Logs**: `docker-compose logs -f [service]`
- **Metrics**: Prometheus at http://localhost:9090
- **Tracing**: Jaeger at http://localhost:16686

---

## 🤝 Getting Help

### When You're Stuck

1. **Check the logs**:

   ```bash
   docker-compose logs -f nodejs-api
   docker-compose logs -f java-api
   ```

2. **Check the documentation**:
   - Read the integration plan
   - Check the API checklist
   - Review architecture diagrams

3. **Test in isolation**:

   ```bash
   # Test backend directly
   curl -X POST http://localhost:7002/api/lifecycle/phases

   # Test through gateway
   curl http://localhost:7002/api/rail/components
   ```

4. **Ask for help**:
   - Post in team Slack channel
   - Create GitHub issue with details
   - Schedule pairing session

### Debug Checklist

- [ ] Service is running (check `docker-compose ps`)
- [ ] Port is not in use (`lsof -i :7002`)
- [ ] Environment variables are set (`.env` file)
- [ ] Database is accessible (`psql -h localhost -U ghatana yappc`)
- [ ] CORS is configured correctly
- [ ] Route is registered in `index.ts`
- [ ] Request/response format is correct
- [ ] Error handling is in place

---

## 🎉 Quick Wins

### Easy Implementations (1-2 hours each)

1. **Add Health Check Details**
   - Endpoint: `GET /health/detailed`
   - Return: Database status, Redis status, memory usage
   - Impact: Better monitoring

2. **Add Workspace Analytics**
   - Endpoint: `GET /api/workspaces/:id/analytics`
   - Return: Project count, member count, activity stats
   - Impact: Better dashboard

3. **Add Project Tags**
   - Endpoint: `PUT /api/projects/:id/tags`
   - Function: Add/remove tags from project
   - Impact: Better organization

4. **Add Canvas Thumbnails**
   - Endpoint: `GET /api/canvas/:id/thumbnail`
   - Function: Return thumbnail image of canvas
   - Impact: Better UX

---

**Start here, and good luck! 🚀**

Remember: Start small, test frequently, document as you go!
