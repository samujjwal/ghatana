# Unified Left Rail - Integration Status

**Date**: January 27, 2026
**Status**: Integrated with Service Layer & Test Coverage

---

## 🚀 Architecture Overview

The Unified Left Rail has been upgraded from a mock-UI implementation to a fully integrated service-backed architecture.

### 1. Service Layer (`RailDataService`)

- Centralized data fetching in `services/rail/RailServiceClient.ts`.
- **Hybrid Mode**: Supports seamless switching between Mock data (default) and Real API endpoints (`/api/rail/*`).
- **Caching**: Implemented 60-second in-memory caching for performance optimization.
- **Type Safety**: Shared types in `components/canvas/unified/panel-types.ts`.

### 2. Panel Integration

All 9 panels now consume `railService`:

| Panel              | Endpoint          | Caching                   |
| ------------------ | ----------------- | ------------------------- |
| **Components**     | `/components`     | ✅ Yes (query-based)      |
| **Infrastructure** | `/infrastructure` | ✅ Yes                    |
| **Files**          | `/files?path=...` | ✅ Yes (path-based)       |
| **Data Sources**   | `/datasources`    | ✅ Yes                    |
| **History**        | `/history`        | ❌ No (Real-time)         |
| **AI**             | `/ai/suggestions` | ❌ No (Context-sensitive) |
| **Favorites**      | `/favorites`      | ✅ Yes                    |

### 3. Testing Strategy

- **Unit/Integration Test**: `components/canvas/unified/__tests__/UnifiedLeftRail.test.tsx`
- **Methodology**: Uses `vitest` + `testing-library` to mount the Rail, mock the `railService`, and verify user interactions (tab switching, async loading).

### 4. Customization & Rules

- **Role-Based Visibility**: Panels are dynamically shown based on `rail-config.ts` rules (e.g., Infrastructure panel only visible for `Architect` role or `architecture` mode).
- **Mode Adaptation**: Default panels and asset categories shift based on `brainstorm`, `design`, `code`, etc.

---

## 🛠️ How to Enable Real Backend

1. Open `src/services/rail/RailServiceClient.ts`
2. Update the singleton initialization:
   ```typescript
   // Switch second argument to false
   export const railService = new RailDataService(undefined, false);
   ```
3. Ensure backend is running at `/api/rail` (or configure base URL).

## ✅ Verification Checklist

- [x] **Real backend API connections**: Service layer implemented.
- [x] **User interaction testing**: Jest/Vitest tests added.
- [x] **Customization per role/mode**: `rail-config.ts` validated.
- [x] **Performance optimization**: Service-side caching enabled.
- [x] **Feature expansion**: Registry pattern allows easy addition of new panels.
