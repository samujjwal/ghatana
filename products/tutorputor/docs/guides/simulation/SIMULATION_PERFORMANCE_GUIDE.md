# Simulation System Performance Optimization Guide

## Overview

This guide provides comprehensive performance optimization strategies for the TutorPutor Simulation System, covering client-side rendering, server-side execution, and data management.

## Performance Targets

| Metric | Target | Critical Threshold |
|--------|--------|-------------------|
| Initial Load Time | < 2s | < 5s |
| Time to Interactive | < 3s | < 6s |
| Frame Rate (2D) | 60 FPS | 30 FPS |
| Frame Rate (3D) | 30 FPS | 15 FPS |
| Step Execution | < 50ms | < 200ms |
| Session Creation | < 500ms | < 2s |
| API Response Time | < 200ms | < 1s |

## Client-Side Optimizations

### 1. Renderer Optimization

#### Lazy Loading
```typescript
// Lazy load domain-specific renderers
const PhysicsRenderer = lazy(() => import('./renderers/PhysicsRenderer'));
const ChemistryRenderer = lazy(() => import('./renderers/ChemistryRenderer'));

// Use Suspense for loading states
<Suspense fallback={<LoadingSpinner />}>
  <PhysicsRenderer manifest={manifest} />
</Suspense>
```

#### Canvas Optimization
```typescript
// Use OffscreenCanvas for background rendering
const offscreen = canvas.transferControlToOffscreen();
worker.postMessage({ canvas: offscreen }, [offscreen]);

// Implement viewport culling
function shouldRenderEntity(entity: SimEntity, viewport: Viewport): boolean {
  return (
    entity.x >= viewport.x &&
    entity.x <= viewport.x + viewport.width &&
    entity.y >= viewport.y &&
    entity.y <= viewport.y + viewport.height
  );
}

// Use requestAnimationFrame efficiently
let animationFrameId: number;
function render() {
  // Only render visible entities
  const visibleEntities = entities.filter(e => shouldRenderEntity(e, viewport));
  renderEntities(visibleEntities);
  
  animationFrameId = requestAnimationFrame(render);
}
```

#### Entity Pooling
```typescript
class EntityPool {
  private pool: SimEntity[] = [];
  
  acquire(): SimEntity {
    return this.pool.pop() || this.createEntity();
  }
  
  release(entity: SimEntity): void {
    this.resetEntity(entity);
    this.pool.push(entity);
  }
}
```

### 2. State Management

#### Memoization
```typescript
// Memoize expensive computations
const processedEntities = useMemo(() => {
  return entities.map(e => processEntity(e));
}, [entities]);

// Use React.memo for component optimization
export const SimulationEntity = React.memo(({ entity }) => {
  return <div>{entity.label}</div>;
}, (prev, next) => prev.entity.id === next.entity.id);
```

#### Batched Updates
```typescript
// Batch state updates
import { unstable_batchedUpdates } from 'react-dom';

unstable_batchedUpdates(() => {
  setEntities(newEntities);
  setStepIndex(newIndex);
  setKeyframe(newKeyframe);
});
```

### 3. Asset Optimization

#### Image Optimization
- Use WebP format with fallbacks
- Implement responsive images
- Lazy load images below the fold
- Use CSS sprites for icons

#### Code Splitting
```typescript
// Route-based code splitting
const SimulationPlayer = lazy(() => import('./SimulationPlayer'));
const SimulationEditor = lazy(() => import('./SimulationEditor'));

// Component-based code splitting
const Physics3DRenderer = lazy(() => import('./Physics3DRenderer'));
```

## Server-Side Optimizations

### 1. Session Management

#### Redis Optimization
```typescript
// Use Redis pipelines for batch operations
const pipeline = redis.pipeline();
pipeline.get(`session:${sessionId}`);
pipeline.get(`kernel:${kernelId}`);
const results = await pipeline.exec();

// Implement session pooling
class SessionPool {
  private activeSessions = new Map<string, Session>();
  private maxSessions = 1000;
  
  async getSession(id: string): Promise<Session> {
    if (this.activeSessions.size >= this.maxSessions) {
      await this.evictOldestSession();
    }
    return this.activeSessions.get(id);
  }
}
```

#### Kernel State Compression
```typescript
// Compress kernel state before storing
import { gzip, ungzip } from 'node:zlib';
import { promisify } from 'node:util';

const gzipAsync = promisify(gzip);
const ungzipAsync = promisify(ungzip);

async function serializeKernel(kernel: Kernel): Promise<string> {
  const json = JSON.stringify(kernel.getState());
  const compressed = await gzipAsync(Buffer.from(json));
  return compressed.toString('base64');
}
```

### 2. Database Optimization

#### Query Optimization
```sql
-- Add indexes for common queries
CREATE INDEX idx_simulation_tenant_domain ON simulation_manifest(tenant_id, domain);
CREATE INDEX idx_simulation_lifecycle ON simulation_manifest((manifest->>'lifecycle'->>'status'));
CREATE INDEX idx_session_user_created ON simulation_session(user_id, created_at DESC);

-- Use partial indexes for active sessions
CREATE INDEX idx_active_sessions ON simulation_session(user_id) 
WHERE status = 'active';
```

#### Connection Pooling
```typescript
// Configure Prisma connection pool
const prisma = new PrismaClient({
  datasources: {
    db: {
      url: process.env.DATABASE_URL,
    },
  },
  // Connection pool settings
  __internal: {
    engine: {
      connection_limit: 10,
      pool_timeout: 10,
    },
  },
});
```

### 3. Caching Strategy

#### Multi-Layer Caching
```typescript
// L1: In-memory cache (LRU)
import LRU from 'lru-cache';

const manifestCache = new LRU<string, SimulationManifest>({
  max: 500,
  ttl: 1000 * 60 * 5, // 5 minutes
});

// L2: Redis cache
async function getManifest(id: string): Promise<SimulationManifest> {
  // Check L1
  let manifest = manifestCache.get(id);
  if (manifest) return manifest;
  
  // Check L2
  const cached = await redis.get(`manifest:${id}`);
  if (cached) {
    manifest = JSON.parse(cached);
    manifestCache.set(id, manifest);
    return manifest;
  }
  
  // Fetch from database
  manifest = await fetchFromDatabase(id);
  
  // Populate caches
  manifestCache.set(id, manifest);
  await redis.setex(`manifest:${id}`, 300, JSON.stringify(manifest));
  
  return manifest;
}
```

#### Cache Invalidation
```typescript
// Invalidate on updates
async function updateManifest(id: string, updates: Partial<SimulationManifest>) {
  await prisma.simulationManifest.update({ where: { id }, data: updates });
  
  // Invalidate caches
  manifestCache.delete(id);
  await redis.del(`manifest:${id}`);
}
```

## Kernel-Specific Optimizations

### Physics Kernel (Matter.js)

```typescript
// Optimize Matter.js engine
const engine = Engine.create({
  enableSleeping: true,
  positionIterations: 6,
  velocityIterations: 4,
  constraintIterations: 2,
});

// Use composite for grouping
const composite = Composite.create();
Composite.add(composite, bodies);

// Implement spatial hashing
Matter.Grid.create({
  buckets: {},
  pairs: {},
  pairsList: [],
});
```

### Chemistry Kernel (NGL)

```typescript
// Optimize molecular rendering
const stage = new NGL.Stage('viewport', {
  backgroundColor: 'white',
  quality: 'medium', // Use 'low' for mobile
  sampleLevel: 1, // Reduce for better performance
});

// Use representations efficiently
structure.addRepresentation('cartoon', {
  quality: 'low',
  radiusScale: 0.5,
});
```

## Monitoring & Profiling

### Performance Metrics

```typescript
// Track render performance
const observer = new PerformanceObserver((list) => {
  for (const entry of list.getEntries()) {
    if (entry.entryType === 'measure') {
      console.log(`${entry.name}: ${entry.duration}ms`);
    }
  }
});
observer.observe({ entryTypes: ['measure'] });

// Measure step execution
performance.mark('step-start');
await executeStep(step);
performance.mark('step-end');
performance.measure('step-execution', 'step-start', 'step-end');
```

### Memory Profiling

```typescript
// Monitor memory usage
if (performance.memory) {
  const used = performance.memory.usedJSHeapSize;
  const limit = performance.memory.jsHeapSizeLimit;
  const percentage = (used / limit) * 100;
  
  if (percentage > 80) {
    console.warn('High memory usage:', percentage.toFixed(2) + '%');
    // Trigger cleanup
    cleanupUnusedResources();
  }
}
```

## Best Practices

### 1. Entity Count Limits
- **2D Simulations**: Max 100 entities
- **3D Simulations**: Max 50 entities
- **Mobile**: Reduce by 50%

### 2. Step Complexity
- **Max steps per simulation**: 200
- **Max actions per step**: 10
- **Target step duration**: 1000ms

### 3. Canvas Dimensions
- **Desktop**: Max 2048x2048
- **Tablet**: Max 1024x1024
- **Mobile**: Max 800x600

### 4. Asset Sizes
- **Images**: < 500KB each
- **3D Models**: < 2MB each
- **Total assets**: < 10MB per simulation

### 5. Network Optimization
- Enable gzip compression
- Use CDN for static assets
- Implement HTTP/2
- Use service workers for offline support

## Troubleshooting

### Slow Rendering
1. Check entity count
2. Verify canvas size
3. Profile render loop
4. Check for memory leaks
5. Optimize entity updates

### High Memory Usage
1. Implement entity pooling
2. Clear unused resources
3. Reduce cache sizes
4. Check for circular references
5. Use WeakMap for metadata

### Slow API Responses
1. Check database indexes
2. Verify cache hit rates
3. Profile slow queries
4. Optimize serialization
5. Implement pagination

## Performance Checklist

- [ ] Lazy load domain renderers
- [ ] Implement viewport culling
- [ ] Use entity pooling
- [ ] Enable Redis caching
- [ ] Add database indexes
- [ ] Compress kernel state
- [ ] Implement code splitting
- [ ] Optimize images (WebP)
- [ ] Use service workers
- [ ] Monitor performance metrics
- [ ] Set up alerts for degradation
- [ ] Profile regularly
- [ ] Load test before deployment

## Resources

- [Web Performance Working Group](https://www.w3.org/webperf/)
- [Chrome DevTools Performance](https://developer.chrome.com/docs/devtools/performance/)
- [React Performance Optimization](https://react.dev/learn/render-and-commit)
- [Redis Best Practices](https://redis.io/docs/manual/patterns/)
- [Prisma Performance](https://www.prisma.io/docs/guides/performance-and-optimization)
