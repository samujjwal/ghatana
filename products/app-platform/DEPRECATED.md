# DEPRECATED — App-Platform

> **Status:** DEPRECATED as of 2026-03-21  
> **Replacement:** `products/yappc/` and `products/finance/`  
> **Removal Target:** When archive is complete

## Why Deprecated

App-platform has been fully superseded by:
- **YAPPC** (`products/yappc/`) — Product creation platform
- **Finance** (`products/finance/`) — Finance domain modules (migrated from app-platform kernel packs)

## Current State

- **Not in build**: Zero includes in `settings.gradle.kts`
- **No active dependencies**: No other modules reference app-platform
- **915 files remaining**: Legacy code preserved for reference only
- **25 kernel modules**: All orphaned (IAM, Rules Engine, Calendar, Event Store, Ledger, etc.)

## Migration History

| Original Module | Migrated To | Date |
|----------------|-------------|------|
| Finance/Compliance domain packs | `products/finance/*` | 2026-03-21 |
| Platform capabilities | `platform/java/*` | 2025-2026 |
| Product features | `products/yappc/*` | 2025-2026 |

## Action Items

- [ ] Archive documentation to `docs/archive/app-platform-legacy/`
- [ ] Delete directory after 6-month archive period
