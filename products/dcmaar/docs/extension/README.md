# Browser Extension Framework Documentation

This directory contains **generic, reusable browser extension framework documentation** for the DCMAAR platform.

## Organization

### 📋 Framework Documentation

- **BROWSER_EXTENSION_FRAMEWORK_ARCHITECTURE.md** - Core architectural patterns and design principles
- **BROWSER_EXTENSION_IMPLEMENTATION_PLAN.md** - Framework implementation roadmap and phases
- **PLUGIN_MODEL_AND_APPS.md** - Plugin architecture and how apps integrate with extensions

### 📚 Guidelines & Best Practices

- **guidelines/** - Coding standards, testing patterns, and development guidelines
  - CODING.md - Naming conventions, code organization, patterns
  - TESTING.md - Testing strategies, unit/integration tests, E2E testing

### 🛠️ Operations & Deployment

- **operations/** - Deployment, monitoring, and operations documentation
  - OPERATIONS.md - Build process, deployment guides, troubleshooting

### 📖 User & Technical Reference

- **usage/** - User manuals, technical references, and future work
  - USER_MANUAL.md - End-user documentation and feature guides
  - TECHNICAL_REFERENCE.md - API references, schemas, technical deep-dives
  - KNOWN_ISSUES_TROUBLESHOOTING.md - Common issues and solutions
  - FUTURE_BACKLOG.md - Planned features and improvements

### 📦 Library Documentation

- **libs/** - Documentation for shared browser extension libraries
  - **browser-extension-core/** - Core utilities and abstractions
  - **browser-extension-ui/** - Reusable UI components and patterns
  - **plugin-extension/** - Plugin framework and plugin development guide

## When to Use This Documentation

✅ **Use this documentation when:**

- You're building a **new product extension** (Guardian, Device-Health, etc.)
- You need to understand **framework-level patterns** and architecture
- You're implementing **core framework features**
- You need **generic plugin patterns** applicable across products
- You're developing **shared library components**

❌ **Don't use this for:**

- Product-specific implementation details (see product-specific docs)
- Guardian-specific features or APIs
- Device-Health or other app-specific behavior

## Related Documentation

### Product-Specific Documentation

- **Guardian App**: `/products/dcmaar/apps/guardian/docs/browser-extension/`
- **Device-Health App**: (Add when created)
- **Other Products**: (Add as created)

### Other Framework Documentation

- Framework architecture: `framework/browser-extension/`
- Shared libraries: `libs/typescript/browser-extension-*/`
- Plugin abstractions: `libs/typescript/plugin-abstractions/`

## Getting Started

1. **First time?** Start with `BROWSER_EXTENSION_FRAMEWORK_ARCHITECTURE.md`
2. **Building an extension?** Read `BROWSER_EXTENSION_IMPLEMENTATION_PLAN.md`
3. **Need patterns?** Check `guidelines/` for coding standards
4. **Developing plugins?** See `PLUGIN_MODEL_AND_APPS.md` and `libs/plugin-extension/`
5. **Deployment?** Visit `operations/OPERATIONS.md`

## Documentation Maintenance

This documentation is maintained at the **framework level** and applies to all browser extension products.

### When to Update

- When framework patterns or architecture changes
- When new shared libraries are created
- When generic plugin model evolves
- When deployment/operations procedures change

### When NOT to Update

- For product-specific features → Update product-specific docs
- For Guardian-specific behavior → Update Guardian docs
- For app-specific APIs → Update app-specific docs

---

**Last Updated**: November 24, 2025  
**Owned By**: DCMAAR Platform Team  
**Related Teams**: Guardian Product, Device-Health Product, Core Framework Team
