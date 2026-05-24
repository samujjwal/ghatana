# Phase 1 CI Update Requirements

## Status: Documented - Manual Application Required

The following changes to `.github/workflows/data-cloud-ci.yml` are needed to complete Phase 1 (Make build coverage complete).

### Required Changes

1. **Add module classification validation step** (after "Install UI dependencies"):

```yaml
# Phase 1: Validate all Data-Cloud modules are classified
- name: Validate Data-Cloud module classification
  run: node scripts/list-data-cloud-gradle-modules.mjs --validate
```

2. **Update compile step to use all active modules**:
   Replace the current selective module list with comprehensive coverage using the script:

```yaml
# Backend: Compile all modules (Phase 1: comprehensive coverage)
- name: Compile backend modules
  run: |
    ./gradlew $(node scripts/list-data-cloud-gradle-modules.mjs --check-tasks) --no-daemon
```

3. **Update test step to use all active modules**:

```yaml
# Backend: Unit Tests
- name: Run backend unit tests
  run: |
    ./gradlew $(node scripts/list-data-cloud-gradle-modules.mjs --release-blocking --check-tasks | sed 's/compileJava/test/g') --no-daemon
```

### Rationale

- The current CI only compiles/tests a subset of Data-Cloud modules
- Phase 1 requires strict all-active-module coverage
- Using the `list-data-cloud-gradle-modules.mjs` script ensures the module list stays in sync with `config/generated/settings-gradle-includes.kts`
- This prevents drift between generated settings and CI coverage

### Alternative: Hardcode Full Module List

If dynamic script execution is not preferred, hardcode the full module list in the DC_MODULES env var:

```yaml
env:
  DC_MODULES: >-
    :products:data-cloud:planes:shared-spi
    :products:data-cloud:planes:data:entity
    :products:data-cloud:planes:event:core
    :products:data-cloud:planes:event:store
    :products:data-cloud:planes:operations:config
    :products:data-cloud:planes:intelligence:analytics
    :products:data-cloud:planes:intelligence:feature-ingest
    :products:data-cloud:planes:governance:core
    :products:data-cloud:delivery:runtime-composition
    :products:data-cloud:delivery:api
    :products:data-cloud:delivery:launcher
    :products:data-cloud:delivery:sdk
    :products:data-cloud:contracts
    :products:data-cloud:extensions:plugins
    :products:data-cloud:extensions:agent-registry
    :products:data-cloud:extensions:agent-catalog
    :products:data-cloud:extensions:kernel-bridge
    :products:data-cloud:delivery:api-contract-tests
    :products:data-cloud:integration-tests
    :products:data-cloud:planes:action
    :products:data-cloud:planes:action:operator-contracts
    :products:data-cloud:planes:action:central-runtime
    :products:data-cloud:planes:action:engine
    :products:data-cloud:planes:action:registry
    :products:data-cloud:planes:action:analytics
    :products:data-cloud:planes:action:security
    :products:data-cloud:planes:action:event-bridge
    :products:data-cloud:planes:action:agent-runtime
    :products:data-cloud:planes:action:api
    :products:data-cloud:planes:action:scaling
    :products:data-cloud:planes:action:observability
    :products:data-cloud:planes:action:orchestrator
    :products:data-cloud:planes:action:server
    :products:data-cloud:planes:action:identity
    :products:data-cloud:planes:action:compliance
    :products:data-cloud:planes:action:kernel-bridge
```

Then use `$DC_MODULES` in the compile/test steps.

### Similar Changes Needed for Release Workflow

The same comprehensive module coverage should be applied to `.github/workflows/data-cloud-release.yml`.
