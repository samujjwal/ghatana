# YAPPC App Creator Libraries

**Last Updated:** 2026-01-28  
**Current Count:** 29 libraries (target: 25-28)  
**Status:** Phase 1 Complete - 7 libraries archived

---

## Library Inventory

### Core Infrastructure (8)
| Library | Purpose | Status |
|---------|---------|--------|
| `@yappc` | Scoped package container | ✅ Active |
| `config` | Configuration management, feature flags, Result types | ✅ Active |
| `types` | Shared TypeScript types | ✅ Active |
| `utils` | Utility functions | ✅ Active |
| `state` | State management | ✅ Active |
| `store` | Global state store | ✅ Active |
| `api` | API client and utilities | ✅ Active |
| `graphql` | GraphQL client and utilities | ✅ Active |

### AI & ML (3)
| Library | Purpose | Status |
|---------|---------|--------|
| `ai-core` | AI services (consolidated from 4 libs) | ✅ Active |
| `ai-ui` | AI UI components | ✅ Active |
| `ml` | Machine learning utilities | 🟡 Review |

### Design System (2)
| Library | Purpose | Status |
|---------|---------|--------|
| `ui` | Core UI components | ✅ Active |
| `design-tokens` | Design tokens (consolidated from 8 libs) | ✅ Active |

### Canvas & Editor (5)
| Library | Purpose | Status |
|---------|---------|--------|
| `canvas` | Canvas editor (with edgeless mode) | ✅ Active |
| `designer` | Design tools | 🟡 Review |
| `ide` | IDE integration | 🟡 Review |
| `code-editor` | Code editing components | ✅ Active |
| `live-preview-server` | Live preview server | 🟡 Merge into `canvas` |

### Collaboration & Sync (1)
| Library | Purpose | Status |
|---------|---------|--------|
| `crdt` | CRDT (consolidated from 5 libs) | ✅ Active |

### Layout & Responsive (2)
| Library | Purpose | Status |
|---------|---------|--------|
| `layout` | Layout components (consolidated from 2 libs) | ✅ Active |
| `form-generator` | Form generation | 🟡 Review |

### Platform & Infrastructure (2)
| Library | Purpose | Status |
|---------|---------|--------|
| `platform-tools` | Platform utilities (consolidated from 3 libs) | ✅ Active |
| `infrastructure` | Infrastructure components | 🟡 Review |

### Auth & Security (1)
| Library | Purpose | Status |
|---------|---------|--------|
| `auth` | Authentication | ✅ Active |

### Testing & Quality (2)
| Library | Purpose | Status |
|---------|---------|--------|
| `testing` | Testing utilities | ✅ Active |
| `component-traceability` | Component tracking | 🟡 Review |

### Charts & Visualization (1)
| Library | Purpose | Status |
|---------|---------|--------|
| `charts` | Chart components | ✅ Active |

### Development Tools (2)
| Library | Purpose | Status |
|---------|---------|--------|
| `vite-plugin-live-edit` | Vite plugin | ✅ Active |
| `README.md` | Documentation | ✅ Active |

---

## Consolidation Plan

### Target: 36 → 28 Libraries (-8)

**Phase 1: Complete ✅**
1. ✅ Archived `design-system` (merge into `ui` pending)
2. ✅ Archived `visual-style-panel` (merge into `design-tokens` pending)
3. ✅ Archived `realtime-sync-service` and `websocket` (merge into `crdt` pending)
4. ✅ Archived `responsive-breakpoint-editor` (merge into `layout` pending)
5. ✅ Archived `telemetry` and `performance-monitor` (merge into `platform-tools` pending)

**Phase 2: Short Term (Next 2 Weeks)**
6. Merge `live-preview-server` into `canvas` (canvas feature)

**Phase 3: Review (Next Month)**
7. Evaluate `ml` vs `ai-core` for consolidation
8. Evaluate `designer` vs `canvas` for consolidation
9. Evaluate `infrastructure` vs `platform-tools` for consolidation
10. Evaluate `component-traceability` for removal or merge

**Current: 29 libraries (target: 28)**

---

## Consolidation Strategy

### Using Subpath Exports

Instead of separate packages, use subpath exports for clean API:

```json
// design-tokens/package.json
{
  "exports": {
    ".": "./src/index.ts",
    "./visual-panel": "./src/visual-panel/index.ts"
  }
}
```

### Migration Steps

1. **Move Code**: Copy source to target library subdirectory
2. **Update Imports**: Search/replace old imports with new paths
3. **Update package.json**: Add subpath exports
4. **Archive**: Move old library to `.archive/libs-consolidation-2026-01/`
5. **Test**: Verify build and functionality

---

## Dependency Graph

```
ui
├── design-tokens
│   └── types
├── layout
│   └── types
└── utils

ai-core
├── config
├── types
└── utils

canvas
├── ui
├── design-tokens
└── crdt
```

---

## Commands

```bash
# Count libraries
ls -1d libs/* | wc -l

# Find library dependencies
grep -r "from '@yappc/" libs --include="*.ts" --include="*.tsx" | cut -d"'" -f2 | sort | uniq -c | sort -rn

# Check for circular dependencies
pnpm m ls --depth=10
```

---

## Notes

- Libraries use pnpm workspaces for monorepo management
- Each library should have a single responsibility
- Subpath exports enable tree-shaking
- Archive old libraries before deletion (preserve history)
