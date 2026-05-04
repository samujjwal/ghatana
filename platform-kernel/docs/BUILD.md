# Platform Kernel Build Guide

This document describes the two execution modes for building and testing platform-kernel and platform-plugins modules.

## Execution Modes

### Root Mode

Execute from repository root using the composite build configuration:

```bash
# Build all platform-kernel modules
./gradlew :platform-kernel:kernel-core:build
./gradlew :platform-kernel:kernel-plugin:build
./gradlew :platform-kernel:kernel-persistence:build

# Build all platform-plugins modules
./gradlew :platform-plugins:plugin-risk-management:build
./gradlew :platform-plugins:plugin-compliance:build
./gradlew :platform-plugins:plugin-consent:build
./gradlew :platform-plugins:plugin-human-approval:build
./gradlew :platform-plugins:plugin-audit-trail:build
./gradlew :platform-plugins:plugin-billing-ledger:build
./gradlew :platform-plugins:core-observability:build

# Test platform-kernel
./gradlew :platform-kernel:kernel-core:test
./gradlew :platform-kernel:kernel-plugin:test
./gradlew :platform-kernel:kernel-persistence:test

# Test platform-plugins
./gradlew :platform-plugins:plugin-risk-management:test
./gradlew :platform-plugins:plugin-compliance:test
./gradlew :platform-plugins:plugin-consent:test
./gradlew :platform-plugins:plugin-human-approval:test
./gradlew :platform-plugins:plugin-audit-trail:test
./gradlew :platform-plugins:plugin-billing-ledger:test
./gradlew :platform-plugins:core-observability:test

# Check (build + test + verification)
./gradlew :platform-kernel:kernel-core:check
./gradlew :platform-kernel:kernel-plugin:check
./gradlew :platform-kernel:kernel-persistence:check
./gradlew :platform-plugins:plugin-risk-management:check
./gradlew :platform-plugins:plugin-compliance:check
```

### Included-Build Mode

Execute within the platform-kernel or platform-plugins directory using included-build project paths:

```bash
# Build platform-kernel modules
cd platform-kernel
../../gradlew :kernel-core:build
../../gradlew :kernel-plugin:build
../../gradlew :kernel-persistence:build

# Build all platform-kernel modules in one command
../../gradlew :kernel-core:build :kernel-plugin:build :kernel-persistence:build

# Test platform-kernel
../../gradlew :kernel-core:test
../../gradlew :kernel-plugin:test
../../gradlew :kernel-persistence:test

# Check platform-kernel
../../gradlew :kernel-core:check
../../gradlew :kernel-plugin:check
../../gradlew :kernel-persistence:check
```

```bash
# Build platform-plugins modules
cd platform-plugins
../../gradlew :plugin-risk-management:build
../../gradlew :plugin-compliance:build
../../gradlew :plugin-consent:build
../../gradlew :plugin-human-approval:build
../../gradlew :plugin-audit-trail:build
../../gradlew :plugin-ledger:build
../../gradlew :core-observability:build

# Build all platform-plugins modules in one command
../../gradlew :plugin-risk-management:build :plugin-compliance:build :plugin-consent:build :plugin-human-approval:build :plugin-audit-trail:build :plugin-ledger:build :core-observability:build

# Test platform-plugins
../../gradlew :plugin-risk-management:test
../../gradlew :plugin-compliance:test
../../gradlew :plugin-consent:test
../../gradlew :plugin-human-approval:test
../../gradlew :plugin-audit-trail:test
../../gradlew :plugin-ledger:check
../../gradlew :core-observability:test

# Check platform-plugins
../../gradlew :plugin-risk-management:check
../../gradlew :plugin-compliance:check
../../gradlew :plugin-consent:check
../../gradlew :plugin-human-approval:check
../../gradlew :plugin-audit-trail:check
../../gradlew :plugin-ledger:check
../../gradlew :core-observability:check
```

## Important Notes

1. **Included-build mode uses local project paths**: When using included-build mode (e.g., `cd platform-kernel`), dependencies must reference local modules like `project(":kernel-core")` NOT `project(":platform-kernel:kernel-core")`.

2. **Root mode uses full project paths**: When executing from repository root, use full project paths like `:platform-kernel:kernel-core`.

3. **Dependency path corrections**: As of Phase 0.2, kernel-plugin and kernel-persistence have been fixed to use local included-build project paths (`:kernel-core`).

## CI Pipeline

The CI pipeline should execute at least:
- One root-mode verification step
- One included-build-mode verification step

This ensures both execution modes remain functional after changes.
