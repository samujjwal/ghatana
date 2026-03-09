# DCMAAR Browser Extension Documentation Index

Master index for all browser extension documentation across the DCMAAR platform.

## Documentation Structure

```
products/dcmaar/
├── docs/
│   └── extension/                          # Generic Framework Documentation
│       ├── README.md                       # Framework overview
│       ├── *.md                            # Framework design & implementation
│       ├── guidelines/                     # Coding standards, testing
│       ├── operations/                     # Deployment & operations
│       ├── usage/                          # User manuals & reference
│       └── libs/                           # Shared library documentation
│           ├── browser-extension-core/
│           ├── browser-extension-ui/
│           └── plugin-extension/
│
├── apps/
│   └── guardian/
│       └── docs/
│           └── browser-extension/          # Guardian Product Documentation
│               ├── README.md               # Guardian overview
│               ├── *.md                    # Guardian-specific docs
│               ├── guides/                 # Quick-start guides
│               ├── technical-docs/         # Architecture & deep-dives
│               ├── session-notes/          # Development history
│               ├── reference/              # Features & utilities
│               └── guardian-core/          # Structured core docs
│
├── framework/
│   └── browser-extension/                  # Framework Source Code
│       └── src/                            # Framework implementation
│
└── libs/
    └── typescript/
        ├── browser-extension-core/         # Core Library Source
        ├── browser-extension-ui/           # UI Library Source
        └── plugin-extension/               # Plugin System Source
```

## Quick Navigation

### I Want to...

#### Build a New Extension / Understand the Framework

1. **Framework Architecture**: `/products/dcmaar/docs/extension/BROWSER_EXTENSION_FRAMEWORK_ARCHITECTURE.md`
2. **Implementation Plan**: `/products/dcmaar/docs/extension/BROWSER_EXTENSION_IMPLEMENTATION_PLAN.md`
3. **Plugin Model**: `/products/dcmaar/docs/extension/PLUGIN_MODEL_AND_APPS.md`
4. **Coding Guidelines**: `/products/dcmaar/docs/extension/guidelines/CODING.md`

#### Use Guardian Extension

1. **Quick Start**: `/products/dcmaar/apps/guardian/docs/browser-extension/guides/QUICK_START.md`
2. **Dashboard Guide**: `/products/dcmaar/apps/guardian/docs/browser-extension/reference/DASHBOARD_VISUAL_REFERENCE.md`
3. **Settings Reference**: `/products/dcmaar/apps/guardian/docs/browser-extension/reference/SETTINGS_VERIFICATION_CHECKLIST.md`

#### Debug Guardian Issues

1. **Debug Guide**: `/products/dcmaar/apps/guardian/docs/browser-extension/guides/DEBUG_GUIDE.md`
2. **Known Issues**: `/products/dcmaar/docs/extension/usage/KNOWN_ISSUES_TROUBLESHOOTING.md`
3. **Console Messages**: `/products/dcmaar/apps/guardian/docs/browser-extension/guides/CONSOLE_MESSAGES_GUIDE.md`
4. **Build Issues**: `/products/dcmaar/apps/guardian/docs/browser-extension/technical-docs/BUILD_AND_RUNTIME_FIXES.md`

#### Develop Guardian Features

1. **Architecture Overview**: `/products/dcmaar/apps/guardian/docs/browser-extension/technical-docs/ARCHITECTURE_DIAGRAM.md`
2. **Message Flow**: `/products/dcmaar/apps/guardian/docs/browser-extension/technical-docs/MESSAGE_FLOW_ARCHITECTURE.md`
3. **Data Flow**: `/products/dcmaar/apps/guardian/docs/browser-extension/technical-docs/DATA_FLOW_VERIFICATION.md`
4. **Testing Strategy**: `/products/dcmaar/docs/extension/guidelines/TESTING.md`

#### Deploy to Production

1. **Operations Guide**: `/products/dcmaar/docs/extension/operations/OPERATIONS.md`
2. **Monitoring**: `/products/dcmaar/apps/guardian/docs/browser-extension/GUARDIAN_BROWSER_EXTENSION_MONITORING.md`
3. **Deployment Checklist**: `/products/dcmaar/docs/extension/operations/OPERATIONS.md`

#### Understand Linux Installers

- **Implementation**: `/products/dcmaar/apps/guardian/docs/browser-extension/LINUX_INSTALLERS_IMPLEMENTATION.md`

#### Use Plugin System

1. **Framework**: `/products/dcmaar/docs/extension/PLUGIN_MODEL_AND_APPS.md`
2. **Plugin Extension Lib**: `/products/dcmaar/docs/extension/libs/plugin-extension/DESIGN_ARCHITECTURE.md`
3. **Development Guide**: `/products/dcmaar/docs/extension/libs/plugin-extension/guidelines/CODING.md`

## Documentation Categories

### 🏗️ Framework Documentation (Generic)

Located: `/products/dcmaar/docs/extension/`

**Purpose**: Reusable patterns, architecture, and guidance applicable to ANY browser extension product.

**When to Use**:

- Building a new product extension
- Understanding framework-level patterns
- Implementing core framework features
- Developing shared libraries

**Key Files**:

- BROWSER_EXTENSION_FRAMEWORK_ARCHITECTURE.md
- BROWSER_EXTENSION_IMPLEMENTATION_PLAN.md
- PLUGIN_MODEL_AND_APPS.md
- guidelines/_, operations/_, usage/\*

### 🎯 Product Documentation (Guardian)

Located: `/products/dcmaar/apps/guardian/docs/browser-extension/`

**Purpose**: Guardian-specific features, APIs, and implementation details.

**When to Use**:

- Working on Guardian extension
- Understanding Guardian-specific behavior
- Debugging Guardian issues
- Developing Guardian features

**Key Sections**:

- guides/ - Quick-start and how-to guides
- technical-docs/ - Architecture and deep-dives
- reference/ - Features and utilities
- session-notes/ - Development history
- guardian-core/ - Structured core documentation

### 📚 Library Documentation

Located: `/products/dcmaar/docs/extension/libs/`

**Purpose**: Shared browser extension libraries and reusable components.

**Libraries**:

- **browser-extension-core**: Core utilities and abstractions
- **browser-extension-ui**: Reusable UI components
- **plugin-extension**: Plugin framework and system

## File Organization Rules

### Framework Docs Belong Here

✅ Generic architectural patterns  
✅ Reusable implementation guides  
✅ Framework-wide testing strategies  
✅ Shared library documentation  
✅ Generic plugin development

### Product Docs (Guardian) Belong Here

✅ Guardian-specific features  
✅ Guardian API contracts  
✅ Guardian deployment procedures  
✅ Guardian troubleshooting  
✅ Guardian development history

### When in Doubt

- **Reusable across products?** → Framework docs
- **Guardian-specific?** → Guardian docs
- **New product?** → Create `apps/{product}/docs/browser-extension/`

## Documentation Best Practices

### For Framework Documentation

1. Use generic examples (or multiple product examples)
2. Avoid product-specific implementation details
3. Focus on patterns and principles
4. Link to product-specific examples in product docs

### For Product Documentation

1. Reference framework docs for context
2. Document product-specific behavior
3. Keep development history for reference
4. Link to related framework and library docs

### Cross-Linking

Always include "Related Documentation" sections pointing to:

- Framework documentation (if product-specific doc)
- Product documentation (if framework doc)
- Other related products (when applicable)

## Adding New Documentation

### New Framework Feature

1. Add to `/products/dcmaar/docs/extension/`
2. Update category folder (guidelines/, operations/, usage/, libs/)
3. Link from main README.md
4. Update this index

### New Product (e.g., Device-Health)

1. Create `/products/dcmaar/apps/{product}/docs/browser-extension/`
2. Copy structure from Guardian as template:
   - guides/
   - technical-docs/
   - reference/
   - session-notes/
3. Create product-specific README.md
4. Link from this index
5. Update framework docs with product link

### Product-Specific Session Notes

- Automatically goes to `session-notes/` folder
- Keep for historical reference
- Archive when folder > 500K
- Link from main README

## Maintenance

### Quarterly Review

- [ ] Check for broken links
- [ ] Archive old session notes if needed
- [ ] Update version references
- [ ] Verify all products documented

### When Documentation Gets Stale

- Update or mark as "Needs Review"
- Include date and owner information
- Link to replacement documentation
- Archive outdated versions

### Documentation Ownership

| Area           | Owner                 | Review Frequency |
| -------------- | --------------------- | ---------------- |
| Framework docs | Platform Team         | Monthly          |
| Guardian docs  | Guardian Product Team | Monthly          |
| Library docs   | Core Team             | Quarterly        |
| Session notes  | Development Team      | Per session      |

## Statistics

### Framework Documentation

- Files: 10+ (core architecture + guidelines + operations + usage + libs)
- Coverage: All browser extension products
- Status: ✅ Comprehensive

### Guardian Documentation

- Framework Core: 8 files (inherited from framework)
- Product-Specific: 57+ files
  - Guides: 11 files
  - Technical Docs: 8 files
  - Reference: 12 files
  - Session Notes: 26+ files
- Status: ✅ Very Detailed

### Total Coverage

- **Products**: 1 (Guardian) + Framework
- **Shared Libraries**: 3 (core, UI, plugin)
- **Documentation Files**: 70+
- **Total Size**: ~600KB

## Future Expansion

### Planned Products

- [ ] Device-Health Extension - (TBD)
- [ ] Other DCMAAR Products - (TBD)

### Framework Improvements

- [ ] Video tutorials
- [ ] Interactive examples
- [ ] API reference (auto-generated)
- [ ] Architecture diagrams (SVG)

---

**Last Updated**: November 24, 2025  
**Status**: ✅ Comprehensive Index  
**Owner**: DCMAAR Platform Team  
**Maintenance**: Quarterly Review + On-Demand Updates
