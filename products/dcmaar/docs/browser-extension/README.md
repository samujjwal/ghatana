# Guardian Browser Extension Documentation

This directory contains **Guardian product-specific documentation** for the browser extension implementation.

## Organization

### 🏗️ Core Documentation Structure

- **docs/** - Main structured documentation (migrated from framework)
  - **browser-extension/** - Guardian-specific browser extension docs
    - **guardian-core/** - Core implementation patterns and architecture
      - DESIGN_ARCHITECTURE.md - Guardian extension architecture
      - guidelines/ - Coding standards, testing, operations
      - usage/ - User manual, technical reference, troubleshooting, roadmap
      - operations/ - Deployment and operations guides

- **GUARDIAN_BROWSER_EXTENSION_MONITORING.md** - Monitoring, telemetry, and analytics
- **DESIGN_ARCHITECTURE.md** - High-level Guardian architecture overview
- **GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md** - API contracts and interfaces
- **LINUX_INSTALLERS_IMPLEMENTATION.md** - Linux installer implementation guide

### 📋 Session Notes & Work History

- **session-notes/** - Development sessions and completion reports
  - Contains 26 session summaries, completion reports, and fix summaries
  - Reference for understanding implementation journey and known issues
  - Use for troubleshooting recurring problems

### 📚 Quick Reference Guides

- **guides/** - Quick-start guides, debugging, and how-tos
  - QUICK_START.md - Get the extension running in 5 minutes
  - DEBUG_GUIDE.md - Debugging strategies and tools
  - CONSOLE_MESSAGES_GUIDE.md - Understanding console output
  - HOW_TO_UNBLOCK_YOUTUBE.md - YouTube-specific unblocking guide
  - QUICK_REFERENCE.md - Common tasks and commands
  - And more...

### 🔧 Technical Deep Dives

- **technical-docs/** - Architecture, data flow, and implementation details
  - ARCHITECTURE_DIAGRAM.md - Visual architecture and component relationships
  - MESSAGE_FLOW_ARCHITECTURE.md - Background/content/popup communication patterns
  - DATA_FLOW_VERIFICATION.md - How data flows through the extension
  - BUILD_AND_RUNTIME_FIXES.md - Known build issues and fixes
  - ROOT_CAUSE_ANALYSIS.md - Analysis of major bugs found and fixed
  - CODE_DIFF_DETAILED.md - Detailed changes and their rationale

### 📖 Reference & Utilities

- **reference/** - Features, tracking, and utility documentation
  - DOCUMENTATION_INDEX.md - Complete index of all documentation
  - FEATURE_COMPARISON.md - Feature matrix across browsers
  - DASHBOARD_VISUAL_REFERENCE.md - Dashboard component guide
  - SETTINGS_VERIFICATION_CHECKLIST.md - Settings validation
  - YOUTUBE_UNBLOCK_IMPLEMENTATION.md - YouTube blocking workarounds
  - And more...

## When to Use This Documentation

✅ **Use this documentation for:**

- **Guardian-specific features** (Dashboard, Settings, etc.)
- **Guardian product requirements** and constraints
- **Guardian implementation details** and workarounds
- **Guardian-specific APIs** and contracts
- **Understanding Guardian development history** (session notes)

❌ **Don't use this for:**

- Generic browser extension patterns → See `/products/dcmaar/docs/extension/`
- Framework-level architecture → See framework docs
- Device-Health or other products → See product-specific docs

## Quick Links

### Getting Started

1. **New to Guardian?** → `guides/QUICK_START.md`
2. **Understanding architecture?** → `technical-docs/ARCHITECTURE_DIAGRAM.md`
3. **Debugging issues?** → `guides/DEBUG_GUIDE.md`
4. **Complete feature list?** → `reference/DOCUMENTATION_INDEX.md`

### Key Features

- **Dashboard** → `reference/DASHBOARD_VISUAL_REFERENCE.md`
- **Settings** → `reference/SETTINGS_VERIFICATION_CHECKLIST.md`
- **YouTube Blocking** → `reference/YOUTUBE_UNBLOCK_IMPLEMENTATION.md`
- **Web Usage Tracking** → Search in session-notes

### Development

- **Message Flow** → `technical-docs/MESSAGE_FLOW_ARCHITECTURE.md`
- **Data Flow** → `technical-docs/DATA_FLOW_VERIFICATION.md`
- **Build Issues** → `technical-docs/BUILD_AND_RUNTIME_FIXES.md`
- **Testing Strategy** → `docs/browser-extension/guardian-core/guidelines/TESTING.md`

## Session Notes Reference

The `session-notes/` folder contains work history from development sessions:

| Note Type                 | Count | Purpose                           |
| ------------------------- | ----- | --------------------------------- |
| Completion Reports        | 8     | Major milestone summaries         |
| Fix Summaries             | 18+   | Specific bug fixes and solutions  |
| Checklists & Verification | 3     | Testing and validation procedures |

**Use session notes to:**

- Understand what bugs were fixed and how
- Reference recurring issues
- See the implementation timeline
- Learn about workarounds and why they exist

## Key Implementation Dates

- **Session 11-12**: Message handler and data binding fixes
- **Session 12-13**: Dashboard data flow fixes
- **Recent**: Documentation reorganization and consolidation

## Architecture Overview

```
Guardian Browser Extension
├── Dashboard (Web Usage Analytics)
├── Settings (Parental Controls)
├── Content Scripts (Page Activity Tracking)
├── Background Service Worker (Event Processing)
├── Popup (Quick Status)
└── Data Storage (IndexedDB + Sync Storage)
```

See `technical-docs/ARCHITECTURE_DIAGRAM.md` for detailed component diagrams.

## Documentation Maintenance

### When to Add New Docs

- When implementing new Guardian features
- When discovering new issues or workarounds
- When updating API contracts
- When changing deployment procedures

### When to Update Existing Docs

- When feature behavior changes
- When tests or patterns are updated
- When session work provides new insights
- When documentation becomes outdated

### Archive Policy

Session notes and work summaries are kept for:

- Historical reference
- Troubleshooting recurring issues
- Understanding implementation rationale

Archive older sessions to `session-notes/archive/` when folder size exceeds 500K.

## Related Documentation

### Framework Documentation

- `/products/dcmaar/docs/extension/` - Generic framework patterns
- `/products/dcmaar/framework/browser-extension/` - Framework source

### Core Libraries

- `/products/dcmaar/libs/typescript/browser-extension-core/` - Core library
- `/products/dcmaar/libs/typescript/browser-extension-ui/` - UI components
- `/products/dcmaar/libs/typescript/plugin-extension/` - Plugin system

### Other Products

- Device-Health: (To be created)
- Other apps: (As created)

---

**Last Updated**: November 24, 2025  
**Owned By**: Guardian Product Team  
**Maintained By**: DCMAAR Platform Team
