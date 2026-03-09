# Phase 3: Hybrid Backend Architecture Plan

**Date**: 2024-11-24  
**Architecture**: Java (existing) + Node.js/Fastify (new) + Next.js (frontend)

---

## Architecture Overview

### Existing Structure
```
products/software-org/
├── src/main/java/          # Existing Java backend (Gradle)
├── apps/web/               # React Router v7 Framework Mode frontend (Vite)
└── apps/backend/           # NEW: Node.js/Fastify backend (to be created)
```

### Technology Stack (Following Guardian Pattern)

**Backend (Node.js/Fastify)**:
- **Runtime**: Node.js with TypeScript
- **Framework**: Fastify 5.x (like Guardian backend)
- **Database**: PostgreSQL with Prisma ORM
- **Authentication**: JWT with bcryptjs
- **WebSocket**: Socket.IO via fastify-socket.io
- **Validation**: TypeBox (Fastify standard)
- **Observability**: OpenTelemetry + Prometheus + Sentry
- **Rate Limiting**: @fastify/rate-limit
- **Security**: @fastify/helmet, @fastify/cors

**Frontend (Vite + React Router v7 Framework Mode)**:
- **Framework**: React Router v7 in Framework Mode (not Next.js)
- **Build Tool**: Vite (current setup)
- **Routing**: React Router v7 with Framework Mode
- **State Management**: React Query (TanStack Query) for server state
- **WebSocket**: Socket.IO client
- **API Client**: Fetch API with React Query

**Java Backend**:
- Continues to handle core software-org domain logic
- Node.js backend focuses on persona management (user preferences)

---

## Phase 3 Timeline (Days 9-12)

### Pre-Phase 3: React Router v7 Migration (2-3 hours)
1. Upgrade from React Router v6 to v7
2. Enable Framework Mode with Vite
3. Migrate routes to framework mode structure
4. Update routing patterns and loaders

### Day 9: Backend Setup & Database
1. Create `apps/backend/` following Guardian structure
2. Setup Fastify server with plugins
3. Define Prisma schema for persona preferences
4. Generate and run migrations

### Day 10: REST API Implementation
1. Implement persona config CRUD endpoints
2. Add JWT authentication middleware
3. Add request validation with TypeBox
4. Write API unit tests

### Day 11: Frontend Integration
1. Implement React Query hooks (useUserProfile, usePersonaConfigs)
2. Migrate localStorage to API persistence
3. Update usePersonaComposition hook
4. Fix failing component tests

### Day 12: WebSocket & Testing
1. Implement Socket.IO for real-time sync
2. Add optimistic updates in frontend
3. Write integration tests
4. End-to-end testing

---

## Day 9: Backend Setup (4-6 hours)

### 1. Project Structure (30 min)

```bash
mkdir -p products/software-org/apps/backend/{src,prisma}
cd products/software-org/apps/backend
```

**Directory Structure**:
```
apps/backend/
├── package.json
├── tsconfig.json
├── .env.example
├── .env
├── prisma/
│   ├── schema.prisma
│   └── migrations/
├── src/
│   ├── server.ts                 # Fastify server entry point
│   ├── config/
│   │   └── index.ts              # Environment config
│   ├── db/
│   │   ├── client.ts             # Prisma client singleton
│   │   └── migrate.ts            # Migration runner
│   ├── middleware/
│   │   ├── auth.ts               # JWT authentication
│   │   └── error.ts              # Error handler
│   ├── routes/
│   │   ├── personas.ts           # Persona config routes
│   │   └── workspaces.ts         # Workspace override routes
│   ├── services/
│   │   ├── personaService.ts     # Business logic
│   │   └── workspaceService.ts
│   ├── websocket/
│   │   └── personaSync.ts        # Socket.IO handlers
│   ├── types/
│   │   └── index.ts              # TypeScript types
│   ├── utils/
│   │   ├── logger.ts             # Logging utility
│   │   └── validation.ts         # TypeBox schemas
│   └── __tests__/
│       ├── setup.ts
│       └── routes/
└── vitest.config.ts
```

### 2. Initialize Package.json (15 min)

```json
{
  "name": "@software-org/backend",
  "version": "1.0.0",
  "description": "Software-Org Persona Management Backend API",
  "main": "dist/server.js",
  "scripts": {
    "dev": "tsx watch src/server.ts",
    "build": "tsc --skipLibCheck",
    "start": "node dist/server.js",
    "db:generate": "prisma generate",
    "db:migrate": "tsx src/db/migrate.ts",
    "db:push": "prisma db push",
    "db:studio": "prisma studio",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "lint": "eslint src --ext .ts"
  },
  "dependencies": {
    "@fastify/compress": "^8.3.0",
    "@fastify/cookie": "^11.0.2",
    "@fastify/cors": "^11.1.0",
    "@fastify/helmet": "^13.0.2",
    "@fastify/rate-limit": "^10.3.0",
    "@fastify/sensible": "^6.0.3",
    "@opentelemetry/api": "^1.9.0",
    "@opentelemetry/sdk-node": "^0.208.0",
    "@opentelemetry/exporter-prometheus": "^0.208.0",
    "@prisma/client": "^6.2.1",
    "@sentry/node": "^10.26.0",
    "@sinclair/typebox": "^0.34.41",
    "bcryptjs": "^3.0.3",
    "dotenv": "^17.2.3",
    "fastify": "^5.6.2",
    "fastify-socket.io": "^5.1.0",
    "jsonwebtoken": "^9.0.2",
    "pg": "^8.16.3",
    "socket.io": "^5.2.1",
    "zod": "^3.23.8"
  },
  "devDependencies": {
    "@types/bcryptjs": "^3.0.0",
    "@types/jsonwebtoken": "^9.0.7",
    "@types/node": "^22.19.1",
    "@types/pg": "^8.12.1",
    "@vitest/coverage-v8": "^2.1.9",
    "prisma": "^6.2.1",
    "tsx": "^4.19.2",
    "typescript": "^5.7.3",
    "vitest": "^2.1.9"
  }
}
```

### 3. Prisma Schema (30 min)

**File**: `prisma/schema.prisma`

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

// User model (simplified - may integrate with existing auth)
model User {
  id        String   @id @default(uuid())
  email     String   @unique
  name      String?
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt

  // Relations
  personaPreferences PersonaPreference[]
  workspaces         WorkspaceMember[]

  @@map("users")
}

// Workspace model
model Workspace {
  id          String   @id @default(uuid())
  name        String
  slug        String   @unique
  description String?
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  // Relations
  members    WorkspaceMember[]
  overrides  WorkspaceOverride[]

  @@map("workspaces")
}

// Workspace membership
model WorkspaceMember {
  id          String   @id @default(uuid())
  userId      String
  workspaceId String
  role        String   // 'owner', 'admin', 'member', 'viewer'
  joinedAt    DateTime @default(now())

  // Relations
  user      User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  workspace Workspace @relation(fields: [workspaceId], references: [id], onDelete: Cascade)

  @@unique([userId, workspaceId])
  @@index([userId])
  @@index([workspaceId])
  @@map("workspace_members")
}

// User's persona preferences (per workspace)
model PersonaPreference {
  id          String   @id @default(uuid())
  userId      String
  workspaceId String
  activeRoles String[] // ['admin', 'engineer']
  preferences Json     // Complete PersonaConfig JSON
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  // Relations
  user User @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@unique([userId, workspaceId])
  @@index([userId])
  @@index([workspaceId])
  @@map("persona_preferences")
}

// Workspace-level persona overrides (admin-defined defaults)
model WorkspaceOverride {
  id          String   @id @default(uuid())
  workspaceId String
  roleType    String   // 'admin', 'lead', 'engineer', 'viewer'
  overrides   Json     // Partial PersonaConfig overrides
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  // Relations
  workspace Workspace @relation(fields: [workspaceId], references: [id], onDelete: Cascade)

  @@unique([workspaceId, roleType])
  @@index([workspaceId])
  @@map("workspace_overrides")
}
```

### 4. Fastify Server Setup (1 hour)

**File**: `src/server.ts`

```typescript
import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import compress from '@fastify/compress';
import rateLimit from '@fastify/rate-limit';
import sensible from '@fastify/sensible';
import cookie from '@fastify/cookie';
import socketioServer from 'fastify-socket.io';
import { config } from './config';
import { errorHandler } from './middleware/error';
import { authMiddleware } from './middleware/auth';
import personaRoutes from './routes/personas';
import workspaceRoutes from './routes/workspaces';
import { setupPersonaSync } from './websocket/personaSync';

const server = Fastify({
  logger: {
    level: config.logLevel,
    transport: config.isDevelopment
      ? { target: 'pino-pretty', options: { colorize: true } }
      : undefined,
  },
});

// Register plugins
async function registerPlugins() {
  // Security & optimization
  await server.register(helmet, { contentSecurityPolicy: false });
  await server.register(compress, { global: true });
  await server.register(cors, {
    origin: config.corsOrigin,
    credentials: true,
  });
  await server.register(rateLimit, {
    max: 100,
    timeWindow: '1 minute',
  });
  await server.register(sensible);
  await server.register(cookie, {
    secret: config.cookieSecret,
  });

  // Socket.IO
  await server.register(socketioServer, {
    cors: {
      origin: config.corsOrigin,
      credentials: true,
    },
  });
}

// Register routes
async function registerRoutes() {
  // Health check (no auth)
  server.get('/health', async () => ({ status: 'healthy', timestamp: new Date() }));

  // API routes (with auth)
  server.register(personaRoutes, { prefix: '/api/personas' });
  server.register(workspaceRoutes, { prefix: '/api/workspaces' });
}

// Setup WebSocket handlers
async function setupWebSocket() {
  setupPersonaSync(server.io);
}

// Start server
async function start() {
  try {
    await registerPlugins();
    await registerRoutes();
    await setupWebSocket();

    // Error handler
    server.setErrorHandler(errorHandler);

    const address = await server.listen({
      port: config.port,
      host: config.host,
    });

    server.log.info(`🚀 Server listening at ${address}`);
    server.log.info(`🔌 WebSocket ready for connections`);
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
}

// Graceful shutdown
process.on('SIGTERM', async () => {
  server.log.info('SIGTERM received, shutting down gracefully');
  await server.close();
  process.exit(0);
});

start();
```

### 5. Environment Config (15 min)

**File**: `src/config/index.ts`

```typescript
import { config as dotenvConfig } from 'dotenv';

dotenvConfig();

export const config = {
  // Server
  env: process.env.NODE_ENV || 'development',
  isDevelopment: process.env.NODE_ENV !== 'production',
  port: parseInt(process.env.PORT || '3001', 10),
  host: process.env.HOST || '0.0.0.0',
  logLevel: process.env.LOG_LEVEL || 'info',

  // Database
  databaseUrl: process.env.DATABASE_URL || 'postgresql://user:pass@localhost:5432/softwareorg',

  // Auth
  jwtSecret: process.env.JWT_SECRET || 'your-secret-key-change-in-production',
  jwtExpiry: process.env.JWT_EXPIRY || '7d',
  cookieSecret: process.env.COOKIE_SECRET || 'your-cookie-secret',

  // CORS
  corsOrigin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:3000'],

  // Sentry
  sentryDsn: process.env.SENTRY_DSN,
  sentryEnvironment: process.env.SENTRY_ENVIRONMENT || 'development',
} as const;
```

**File**: `.env.example`

```bash
NODE_ENV=development
PORT=3001
HOST=0.0.0.0
LOG_LEVEL=info

DATABASE_URL=postgresql://user:password@localhost:5432/softwareorg

JWT_SECRET=your-jwt-secret-change-in-production
JWT_EXPIRY=7d
COOKIE_SECRET=your-cookie-secret-change-in-production

CORS_ORIGIN=http://localhost:3000

SENTRY_DSN=
SENTRY_ENVIRONMENT=development
```

### 6. Database Client (15 min)

**File**: `src/db/client.ts`

```typescript
import { PrismaClient } from '@prisma/client';
import { config } from '../config';

const prismaClientSingleton = () => {
  return new PrismaClient({
    log: config.isDevelopment ? ['query', 'error', 'warn'] : ['error'],
  });
};

declare global {
  // eslint-disable-next-line no-var
  var prismaGlobal: undefined | ReturnType<typeof prismaClientSingleton>;
}

export const prisma = globalThis.prismaGlobal ?? prismaClientSingleton();

if (config.isDevelopment) {
  globalThis.prismaGlobal = prisma;
}

// Graceful shutdown
process.on('beforeExit', async () => {
  await prisma.$disconnect();
});
```

**File**: `src/db/migrate.ts`

```typescript
import { execSync } from 'child_process';

async function migrate() {
  try {
    console.log('Running Prisma migrations...');
    execSync('npx prisma migrate deploy', { stdio: 'inherit' });
    console.log('✅ Migrations complete');
  } catch (error) {
    console.error('❌ Migration failed:', error);
    process.exit(1);
  }
}

migrate();
```

---

## Day 10: REST API Implementation (6-8 hours)

### 1. Authentication Middleware (1 hour)

**File**: `src/middleware/auth.ts`

```typescript
import { FastifyRequest, FastifyReply } from 'fastify';
import jwt from 'jsonwebtoken';
import { config } from '../config';

export interface AuthUser {
  id: string;
  email: string;
}

declare module 'fastify' {
  interface FastifyRequest {
    user?: AuthUser;
  }
}

export async function authMiddleware(
  request: FastifyRequest,
  reply: FastifyReply
) {
  try {
    const authHeader = request.headers.authorization;
    
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.unauthorized('Missing or invalid authorization header');
    }

    const token = authHeader.slice(7);
    const decoded = jwt.verify(token, config.jwtSecret) as AuthUser;
    
    request.user = decoded;
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError) {
      return reply.unauthorized('Token expired');
    }
    return reply.unauthorized('Invalid token');
  }
}
```

### 2. Validation Schemas (1 hour)

**File**: `src/utils/validation.ts`

```typescript
import { Type, Static } from '@sinclair/typebox';

// Persona config schemas (matching frontend Zod schemas)
export const PersonaConfigSchema = Type.Object({
  taglines: Type.Array(Type.String()),
  keywords: Type.Array(Type.String()),
  colors: Type.Object({
    primary: Type.String(),
    secondary: Type.String(),
    accent: Type.String(),
  }),
  layout: Type.Object({
    defaultView: Type.String(),
    sidebarPosition: Type.Union([Type.Literal('left'), Type.Literal('right')]),
  }),
  widgets: Type.Array(Type.Object({
    id: Type.String(),
    type: Type.String(),
    position: Type.Object({
      x: Type.Number(),
      y: Type.Number(),
    }),
    size: Type.Object({
      width: Type.Number(),
      height: Type.Number(),
    }),
    config: Type.Record(Type.String(), Type.Unknown()),
  })),
  permissions: Type.Array(Type.String()),
});

export const UpdatePersonaPreferenceSchema = Type.Object({
  activeRoles: Type.Array(Type.String()),
  preferences: PersonaConfigSchema,
});

export const WorkspaceOverrideSchema = Type.Object({
  roleType: Type.String(),
  overrides: Type.Partial(PersonaConfigSchema),
});

export type PersonaConfig = Static<typeof PersonaConfigSchema>;
export type UpdatePersonaPreference = Static<typeof UpdatePersonaPreferenceSchema>;
export type WorkspaceOverride = Static<typeof WorkspaceOverrideSchema>;
```

### 3. Persona Service (1.5 hours)

**File**: `src/services/personaService.ts`

```typescript
import { prisma } from '../db/client';
import type { PersonaConfig } from '../utils/validation';

export class PersonaService {
  async getUserPersonaPreference(userId: string, workspaceId: string) {
    return prisma.personaPreference.findUnique({
      where: {
        userId_workspaceId: { userId, workspaceId },
      },
    });
  }

  async upsertPersonaPreference(
    userId: string,
    workspaceId: string,
    activeRoles: string[],
    preferences: PersonaConfig
  ) {
    return prisma.personaPreference.upsert({
      where: {
        userId_workspaceId: { userId, workspaceId },
      },
      update: {
        activeRoles,
        preferences,
        updatedAt: new Date(),
      },
      create: {
        userId,
        workspaceId,
        activeRoles,
        preferences,
      },
    });
  }

  async deletePersonaPreference(userId: string, workspaceId: string) {
    return prisma.personaPreference.delete({
      where: {
        userId_workspaceId: { userId, workspaceId },
      },
    });
  }

  async getWorkspaceOverrides(workspaceId: string) {
    return prisma.workspaceOverride.findMany({
      where: { workspaceId },
    });
  }

  async upsertWorkspaceOverride(
    workspaceId: string,
    roleType: string,
    overrides: Partial<PersonaConfig>
  ) {
    return prisma.workspaceOverride.upsert({
      where: {
        workspaceId_roleType: { workspaceId, roleType },
      },
      update: {
        overrides,
        updatedAt: new Date(),
      },
      create: {
        workspaceId,
        roleType,
        overrides,
      },
    });
  }
}
```

### 4. Persona Routes (2 hours)

**File**: `src/routes/personas.ts`

```typescript
import { FastifyPluginAsync } from 'fastify';
import { authMiddleware } from '../middleware/auth';
import { PersonaService } from '../services/personaService';
import { UpdatePersonaPreferenceSchema } from '../utils/validation';

const personaRoutes: FastifyPluginAsync = async (server) => {
  const personaService = new PersonaService();

  // All routes require authentication
  server.addHook('onRequest', authMiddleware);

  // GET /api/personas/:workspaceId - Get user's persona preferences
  server.get<{
    Params: { workspaceId: string };
  }>('/:workspaceId', async (request, reply) => {
    const { workspaceId } = request.params;
    const userId = request.user!.id;

    const preference = await personaService.getUserPersonaPreference(userId, workspaceId);

    if (!preference) {
      return reply.notFound('No persona preference found');
    }

    return {
      activeRoles: preference.activeRoles,
      preferences: preference.preferences,
      updatedAt: preference.updatedAt,
    };
  });

  // PUT /api/personas/:workspaceId - Update user's persona preferences
  server.put<{
    Params: { workspaceId: string };
    Body: { activeRoles: string[]; preferences: any };
  }>(
    '/:workspaceId',
    {
      schema: {
        body: UpdatePersonaPreferenceSchema,
      },
    },
    async (request, reply) => {
      const { workspaceId } = request.params;
      const { activeRoles, preferences } = request.body;
      const userId = request.user!.id;

      const updated = await personaService.upsertPersonaPreference(
        userId,
        workspaceId,
        activeRoles,
        preferences
      );

      // Emit WebSocket event for real-time sync
      server.io.to(`workspace:${workspaceId}`).emit('persona:updated', {
        userId,
        activeRoles,
        updatedAt: updated.updatedAt,
      });

      return {
        activeRoles: updated.activeRoles,
        preferences: updated.preferences,
        updatedAt: updated.updatedAt,
      };
    }
  );

  // DELETE /api/personas/:workspaceId - Reset to defaults
  server.delete<{
    Params: { workspaceId: string };
  }>('/:workspaceId', async (request, reply) => {
    const { workspaceId } = request.params;
    const userId = request.user!.id;

    await personaService.deletePersonaPreference(userId, workspaceId);

    // Emit WebSocket event
    server.io.to(`workspace:${workspaceId}`).emit('persona:deleted', { userId });

    return { message: 'Persona preference reset to defaults' };
  });
};

export default personaRoutes;
```

---

## Day 11: Frontend Integration (4-6 hours)

### 1. React Query Setup (30 min)

**File**: `apps/web/src/lib/api/client.ts`

```typescript
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3001';

export async function apiFetch<T>(
  endpoint: string,
  options?: RequestInit
): Promise<T> {
  const token = localStorage.getItem('auth_token');

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Request failed' }));
    throw new Error(error.message || `HTTP ${response.status}`);
  }

  return response.json();
}
```

### 2. Persona API Hooks (1.5 hours)

**File**: `apps/web/src/hooks/usePersonaConfigs.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api/client';
import type { PersonaConfig } from '@/schemas/persona.schema';

interface PersonaPreference {
  activeRoles: string[];
  preferences: PersonaConfig;
  updatedAt: Date;
}

export function usePersonaConfigs(workspaceId: string) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['personaPreferences', workspaceId],
    queryFn: () =>
      apiFetch<PersonaPreference>(`/api/personas/${workspaceId}`),
    enabled: !!workspaceId,
  });

  const updateMutation = useMutation({
    mutationFn: (data: { activeRoles: string[]; preferences: PersonaConfig }) =>
      apiFetch<PersonaPreference>(`/api/personas/${workspaceId}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['personaPreferences', workspaceId],
      });
    },
  });

  const resetMutation = useMutation({
    mutationFn: () =>
      apiFetch(`/api/personas/${workspaceId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['personaPreferences', workspaceId],
      });
    },
  });

  return {
    configs: query.data?.preferences,
    activeRoles: query.data?.activeRoles || [],
    isLoading: query.isLoading,
    error: query.error,
    updateConfigs: updateMutation.mutate,
    resetConfigs: resetMutation.mutate,
    isUpdating: updateMutation.isPending,
  };
}
```

---

## Day 12: WebSocket & Testing (4-6 hours)

### 1. Socket.IO Backend (1.5 hours)

**File**: `src/websocket/personaSync.ts`

```typescript
import { Server as SocketIOServer } from 'socket.io';
import jwt from 'jsonwebtoken';
import { config } from '../config';
import type { AuthUser } from '../middleware/auth';

export function setupPersonaSync(io: SocketIOServer) {
  // Authentication middleware for Socket.IO
  io.use((socket, next) => {
    const token = socket.handshake.auth.token;
    
    if (!token) {
      return next(new Error('Authentication error'));
    }

    try {
      const decoded = jwt.verify(token, config.jwtSecret) as AuthUser;
      socket.data.user = decoded;
      next();
    } catch (err) {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket) => {
    const user = socket.data.user as AuthUser;
    console.log(`User ${user.id} connected`);

    // Join workspace rooms
    socket.on('join:workspace', (workspaceId: string) => {
      socket.join(`workspace:${workspaceId}`);
      console.log(`User ${user.id} joined workspace ${workspaceId}`);
    });

    // Leave workspace rooms
    socket.on('leave:workspace', (workspaceId: string) => {
      socket.leave(`workspace:${workspaceId}`);
    });

    socket.on('disconnect', () => {
      console.log(`User ${user.id} disconnected`);
    });
  });
}
```

### 2. Socket.IO Frontend Hook (1 hour)

**File**: `apps/web/src/hooks/usePersonaSync.ts`

```typescript
import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { io, Socket } from 'socket.io-client';

let socket: Socket | null = null;

export function usePersonaSync(workspaceId: string, enabled = true) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled || !workspaceId) return;

    const token = localStorage.getItem('auth_token');
    if (!token) return;

    // Initialize Socket.IO connection
    if (!socket) {
      socket = io(process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3001', {
        auth: { token },
      });
    }

    // Join workspace room
    socket.emit('join:workspace', workspaceId);

    // Listen for persona updates from other users
    socket.on('persona:updated', (data: { userId: string; activeRoles: string[] }) => {
      console.log('Persona updated by another user:', data);
      
      // Invalidate query to refetch latest data
      queryClient.invalidateQueries({
        queryKey: ['personaPreferences', workspaceId],
      });
    });

    socket.on('persona:deleted', (data: { userId: string }) => {
      console.log('Persona deleted by another user:', data);
      
      queryClient.invalidateQueries({
        queryKey: ['personaPreferences', workspaceId],
      });
    });

    return () => {
      socket?.emit('leave:workspace', workspaceId);
      socket?.off('persona:updated');
      socket?.off('persona:deleted');
    };
  }, [workspaceId, enabled, queryClient]);
}
```

---

## Integration with Java Backend (Optional)

If persona data needs to be shared with Java backend:

### Option 1: Shared PostgreSQL Database
- Both backends access same `persona_preferences` table
- Node.js handles CRUD via Prisma
- Java reads via JDBC/JPA for read-only access

### Option 2: Event-Driven Integration
- Node.js publishes persona change events to Kafka/RabbitMQ
- Java backend subscribes and updates internal cache
- Loose coupling, eventual consistency

### Option 3: REST API Gateway
- Java backend calls Node.js API via HTTP
- Node.js is source of truth for persona data
- Simple, synchronous integration

**Recommendation**: Start with **Option 1** (shared database) for simplicity.

---

## Next Steps

1. **Review this plan** - Confirm architecture approach
2. **Setup backend** - Create apps/backend/ following this structure
3. **Implement Day 9** - Backend foundation
4. **Implement Day 10** - REST APIs
5. **Implement Day 11** - Frontend integration
6. **Implement Day 12** - WebSocket + testing

**Total Estimated Time**: 18-26 hours (Days 9-12)

---

## Questions to Answer

1. ✅ **Backend Framework**: Node.js/Fastify (confirmed - following Guardian pattern)
2. ✅ **Database**: PostgreSQL with Prisma ORM
3. ✅ **WebSocket**: Socket.IO via fastify-socket.io
4. ⏸️ **Authentication**: JWT (need to confirm user auth system exists)
5. ⏸️ **Integration**: How does Java backend access persona data? (Shared DB recommended)

**Ready to proceed with Day 9 setup?**
