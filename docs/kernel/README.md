# Kernel Documentation

This directory contains the canonical documentation for the Kernel Product Lifecycle Platform.

## Table of Contents

### Core Documentation

- **[Vision](./00-VISION.md)** - Kernel vision and goals
- **[Architecture](./01-ARCHITECTURE.md)** - Kernel architecture and design
- **[Product Lifecycle](./02-PRODUCT_LIFECYCLE.md)** - Product lifecycle phases and profiles
- **[Toolchain Adapters](./03-TOOLCHAIN_ADAPTERS.md)** - Toolchain adapter specification
- **[Artifacts](./04-ARTIFACTS.md)** - Artifact contracts and management
- **[Deployment](./05-DEPLOYMENT.md)** - Deployment contracts and targets
- **[Plugin Platform](./06-PLUGIN_PLATFORM.md)** - Plugin lifecycle and registry
- **[Conformance](./07-CONFORMANCE.md)** - Conformance specifications and checks
- **[Security, Privacy, Observability](./08-SECURITY_PRIVACY_OBSERVABILITY.md)** - Security, privacy, and observability gates
- **[Product Developer Guide](./09-PRODUCT_DEVELOPER_GUIDE.md)** - Guide for product developers
- **[Power User Extension Guide](./10-POWER_USER_EXTENSION_GUIDE.md)** - Guide for power users
- **[Migration Guide](./11-MIGRATION_GUIDE.md)** - Migration guide for existing products

### Existing Documentation (Legacy)

The following documentation files have been moved to the legacy/ directory for reference:

- [KERNEL_CONSUMPTION_GUIDE.md](./legacy/KERNEL_CONSUMPTION_GUIDE.md) - Product consumption patterns
- [KERNEL_PRODUCT_BOUNDARY.md](./legacy/KERNEL_PRODUCT_BOUNDARY.md) - Product boundary contract
- [KERNEL_PURITY_RULES.md](./legacy/KERNEL_PURITY_RULES.md) - Kernel purity rules
- [PRODUCT_CONFORMANCE_SPEC.md](./legacy/PRODUCT_CONFORMANCE_SPEC.md) - Product conformance specification
- [PRODUCT_MANIFEST_SPEC.md](./legacy/PRODUCT_MANIFEST_SPEC.md) - Product manifest specification
- [PRODUCT_LIFECYCLE_CONTRACT.md](./legacy/PRODUCT_LIFECYCLE_CONTRACT.md) - Product lifecycle contract
- [PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md](./legacy/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md) - Toolchain adapter specification
- [PRODUCT_ARTIFACT_CONTRACT.md](./legacy/PRODUCT_ARTIFACT_CONTRACT.md) - Artifact contracts
- [PRODUCT_ENVIRONMENT_CONTRACT.md](./legacy/PRODUCT_ENVIRONMENT_CONTRACT.md) - Environment contracts
- [PRODUCT_DEPLOYMENT_CONTRACT.md](./legacy/PRODUCT_DEPLOYMENT_CONTRACT.md) - Deployment contracts
- [PRODUCT_RELEASE_PROMOTION_CONTRACT.md](./legacy/PRODUCT_RELEASE_PROMOTION_CONTRACT.md) - Release and promotion contracts
- [PLUGIN_LIFECYCLE_CONTRACT.md](./PLUGIN_LIFECYCLE_CONTRACT.md) - Plugin lifecycle contract

## Quick Start

For product developers, start with the [Product Developer Guide](./09-PRODUCT_DEVELOPER_GUIDE.md).

For power users extending Kernel, start with the [Power User Extension Guide](./10-POWER_USER_EXTENSION_GUIDE.md).

For migrating existing products to the lifecycle platform, see the [Migration Guide](./11-MIGRATION_GUIDE.md).

## Documentation Maintenance

All documentation changes should follow the canonical structure defined above. When adding new documentation:

1. Add it to the appropriate numbered section (00-VISION.md through 11-MIGRATION_GUIDE.md)
2. Update this README.md to link to the new document
3. Run `pnpm check:kernel-doc-truth` to verify documentation consistency
