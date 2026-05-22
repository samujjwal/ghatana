# Kernel Failure Recovery Guide

## Overview

This document provides concrete recovery fixes for common adapter, policy, and interaction failures in the Ghatana Kernel. Each failure reason code maps to specific recovery actions.

## Product Interaction Failures

### `product_interaction.tenant_required`

**Description**: Interaction request is missing tenant context.

**Recovery Actions**:
1. Ensure the `tenantId` is set in the `ProductInteractionRequest` context
2. Verify tenant provisioning in the product's kernel bridge configuration
3. Check that the tenant is active and not suspended
4. For platform mode, verify Data Cloud tenant registration

**Verification Command**:
```bash
pnpm kernel <productId> build --dry-run --explain
```

### `product_interaction.policy_denied`

**Description**: Interaction was denied by policy evaluation.

**Recovery Actions**:
1. Review the policy decision reason code for specific denial reason
2. Check if the interaction contract is registered in the policy provider
3. Verify the policy provider has the latest policy rules
4. Ensure the requesting product has the required permissions
5. Check if the interaction requires explicit consent that hasn't been granted

**Verification Command**:
```bash
pnpm kernel <productId> build --dry-run --explain
```

### `product_interaction.purpose_required`

**Description**: Interaction request is missing purpose declaration.

**Recovery Actions**:
1. Add a `purpose` field to the interaction request context
2. Ensure the purpose is declared in the product's interaction contracts
3. Verify the purpose is valid for the contract (e.g., "campaign-activation" for DMOS)
4. Check that the purpose matches the allowed purposes in the policy

**Verification Command**:
```bash
pnpm kernel <productId> build --dry-run --explain
```

### `product_interaction.evidence_required`

**Description**: Interaction requires evidence that wasn't provided.

**Recovery Actions**:
1. Ensure evidence refs are included in the interaction request
2. Verify evidence files exist in the expected location (`.kernel/evidence/`)
3. Check that evidence refs point to valid gate pack YAML files
4. Run the evidence generation step before the interaction
5. Verify evidence is signed and not tampered with

**Verification Command**:
```bash
pnpm kernel <productId> build --dry-run --explain
```

### `product_interaction.evidence_persistence_failed`

**Description**: Failed to persist interaction evidence to storage.

**Recovery Actions**:
1. Check disk space on the evidence storage path
2. Verify write permissions on `.kernel/out/` directory
3. For platform mode, verify Data Cloud provider connectivity
4. Check Data Cloud dataset write permissions
5. Verify evidence writer is properly configured

**Verification Command**:
```bash
ls -la .kernel/out/
# or for platform mode
pnpm kernel <productId> build --mode platform --dry-run
```

### `product_interaction.timeout`

**Description**: Interaction handler exceeded timeout threshold.

**Recovery Actions**:
1. Increase the timeout in the interaction broker configuration
2. Optimize the handler implementation to reduce execution time
3. Check for blocking operations that should be async
4. Verify network connectivity for external service calls
5. Check if the handler is stuck in a retry loop

**Verification Command**:
```bash
pnpm kernel <productId> build --explain
```

### `product_interaction.event_handler_unavailable`

**Description**: Event handler for the interaction is not registered or unavailable.

**Recovery Actions**:
1. Verify the handler is registered in the handler registry
2. Check that the handler module is built and available
3. Ensure the handler implements the correct interface
4. Verify handler health check passes
5. Restart the product's kernel bridge if needed

**Verification Command**:
```bash
pnpm kernel <productId> build --surface <surface> --dry-run
```

### `product_interaction.event_delivery_failed`

**Description**: Failed to deliver event to subscriber.

**Recovery Actions**:
1. Check subscriber endpoint availability and health
2. Verify subscriber authentication credentials
3. Check network connectivity to subscriber
4. Review DLQ for failed events and replay if needed
5. Verify subscriber can handle the event payload

**Verification Command**:
```bash
# Check DLQ
cat .kernel/out/dlq/*.json
# Replay events
pnpm kernel <productId> recover <phase>
```

## Adapter Failures

### `adapter.execution_failed`

**Description**: Adapter execution failed with an error.

**Recovery Actions**:
1. Review adapter logs for specific error details
2. Check that adapter dependencies are installed
3. Verify adapter configuration is correct
4. Check for resource constraints (memory, disk space)
5. Rebuild the adapter if necessary

**Verification Command**:
```bash
pnpm kernel <productId> build --surface <surface> --dry-run --explain
```

### `adapter.surface_not_found`

**Description**: Target surface not found in product configuration.

**Recovery Actions**:
1. Verify surface ID is correct in product manifest
2. Check that surface is defined in the product's surfaces configuration
3. Ensure surface is not disabled in the environment
4. Verify surface adapter is registered

**Verification Command**:
```bash
pnpm kernel <productId> build --surface <surface> --explain
```

### `adapter.contract_violation`

**Description**: Adapter violated its contract.

**Recovery Actions**:
1. Review adapter contract definition
2. Ensure adapter output matches expected schema
3. Check for missing required fields in adapter output
4. Verify adapter version compatibility
5. Update adapter to match contract requirements

**Verification Command**:
```bash
pnpm kernel <productId> build --surface <surface> --dry-run
```

## Gate Failures

### `gate.denied`

**Description**: Gate evaluation failed.

**Recovery Actions**:
1. Review gate configuration for required conditions
2. Check gate provider health and connectivity
3. Verify gate evidence is available and valid
4. Check if gate requires manual approval
5. Review gate evaluation logs for specific failure

**Verification Command**:
```bash
pnpm kernel <productId> build --dry-run --explain
```

### `gate.timeout`

**Description**: Gate evaluation exceeded timeout.

**Recovery Actions**:
1. Increase gate timeout in configuration
2. Optimize gate provider implementation
3. Check for blocking operations in gate evaluation
4. Verify external service connectivity for gate
5. Check gate provider resource constraints

**Verification Command**:
```bash
pnpm kernel <productId> build --dry-run --explain
```

## Lifecycle Failures

### `lifecycle.phase_blocked`

**Description**: Lifecycle phase is blocked by prerequisite failures.

**Recovery Actions**:
1. Run `pnpm kernel <productId> recover <phase>` to get recovery guidance
2. Resolve blocking reasons listed in the plan
3. Complete prerequisite phases first
4. Fix any failed gates or interactions
5. Retry the phase after blockers are resolved

**Verification Command**:
```bash
pnpm kernel <productId> recover <phase>
pnpm kernel <productId> <phase> --dry-run
```

### `lifecycle.surface_hash_mismatch`

**Description**: Surface hash doesn't match expected value.

**Recovery Actions**:
1. Verify surface source hasn't changed unexpectedly
2. Rebuild the surface to generate new hash
3. Check for corrupted surface artifacts
4. Verify hash calculation is deterministic
5. Update hash in product manifest if change is intentional

**Verification Command**:
```bash
pnpm kernel <productId> build --surface <surface> --dry-run
```

### `lifecycle.dependency_missing`

**Description**: Required dependency is not available.

**Recovery Actions**:
1. Install missing dependency
2. Check dependency version constraints
3. Verify dependency is in the correct workspace
4. Check for circular dependencies
5. Update dependency resolver configuration

**Verification Command**:
```bash
pnpm install
pnpm kernel <productId> build --dry-run
```

## General Recovery Commands

### Check Plan
```bash
pnpm kernel <productId> <phase> --dry-run --explain
```

### Get Recovery Guidance
```bash
pnpm kernel <productId> recover <phase>
```

### Check Status
```bash
pnpm kernel status <productId>
```

### View Run History
```bash
cat .kernel/out/products/<productId>/<phase>/latest/lifecycle-result.json
```

### View Evidence
```bash
cat .kernel/evidence/<productId>/*.yaml
```

## Enforcement

The `check:kernel-recovery-mapping` script enforces that:
1. All reason codes have documented recovery actions
2. Recovery actions are actionable and specific
3. Verification commands are provided for each failure
4. The mapping is kept in sync with code changes
