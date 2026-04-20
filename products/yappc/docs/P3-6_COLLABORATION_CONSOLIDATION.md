# P3-6: Collaboration Implementation Consolidation

**Status:** Documented for Implementation

## Overview

Consolidate collaboration implementations to use `libs/collab + Yjs` as the canonical solution.

## Current State

### Existing Collaboration Implementations

1. **libs/collab** - Canonical collaboration library
   - Location: `frontend/libs/collab/`
   - Purpose: Shared collaboration utilities
   - Status: 27 items (active)

2. **MockCollaborationProvider** - Mock implementation for testing
   - Location: `frontend/web/src/services/collaboration/MockCollaborationProvider.ts`
   - Purpose: Testing stub
   - Status: Should remain for tests

3. **CollaborationSync** - Config persistence sync
   - Location: `frontend/web/src/services/ConfigPersistence/CollaborationSync.ts`
   - Purpose: Syncs collaboration state with config persistence
   - Status: Needs audit for consolidation

4. **CanvasPersistence** - Canvas-specific persistence
   - Location: `frontend/web/src/services/canvas/CanvasPersistence.ts`
   - Purpose: Canvas state persistence with collaboration
   - Status: May use Yjs internally

5. **CollaborativeCanvas** - Collaborative canvas component
   - Location: `frontend/web/src/components/CollaborativeCanvas.tsx`
   - Purpose: Canvas with real-time collaboration
   - Status: Should use libs/collab

## Consolidation Plan

### Step 1: Audit All Collaboration Implementations

Audit the following files for collaboration logic:
- `frontend/web/src/services/collaboration/`
- `frontend/web/src/services/ConfigPersistence/CollaborationSync.ts`
- `frontend/web/src/services/canvas/CanvasPersistence.ts`
- `frontend/web/src/components/CollaborativeCanvas.tsx`
- Any other files using Yjs or collaboration patterns

### Step 2: Standardize on libs/collab

1. **Move shared utilities to libs/collab**
   - Extract common collaboration logic from web/src
   - Add to libs/collab as reusable utilities
   - Export via public API

2. **Update consumers to use libs/collab**
   - Replace direct Yjs usage with libs/collab wrappers
   - Update imports to use `@yappc/collab` (if exported) or local path
   - Ensure consistent patterns across all consumers

3. **Keep MockCollaborationProvider**
   - Retain for testing purposes
   - Ensure it implements same interface as real provider
   - Update if API changes

### Step 3: Remove Duplicate Implementations

1. Identify duplicate collaboration code
2. Consolidate into libs/collab
3. Remove duplicates from web/src
4. Update all imports

### Step 4: Document Canonical Patterns

Create documentation in `libs/collab/README.md`:
- How to use collaboration features
- Supported patterns (CRDT, presence, comments)
- Integration with Yjs
- Testing with mock provider

## Implementation Details

### libs/collab Structure

```
libs/collab/
├── src/
│   ├── provider/          # Collaboration provider interface
│   ├── crdt/              # CRDT utilities (Yjs wrappers)
│   ├── presence/           # User presence tracking
│   ├── comments/          # Comment threading
│   ├── sync/              # Sync utilities
│   └── index.ts          # Public API
├── package.json
└── README.md
```

### Migration Example

**Before (direct Yjs usage):**
```typescript
import * as Y from 'yjs';
const doc = new Y.Doc();
```

**After (using libs/collab):**
```typescript
import { createCollabDoc } from '@yappc/collab';
const doc = createCollabDoc(projectId);
```

## Estimated Effort

8-10 hours total:
- Audit and analysis: 2-3 hours
- Consolidation to libs/collab: 3-4 hours
- Update consumers: 2-3 hours
- Testing: 1 hour

## Dependencies

- Yjs (already in use)
- WebSocket provider (for real-time sync)
- libs/collab (canonical library)

## Risks

- Breaking changes if API changes significantly
- Need to ensure backward compatibility during migration
- Real-time sync may be affected during transition

## Success Criteria

- All collaboration code uses libs/collab + Yjs
- No duplicate collaboration implementations
- Clear documentation for using libs/collab
- All tests pass after consolidation
- Real-time collaboration still functional
