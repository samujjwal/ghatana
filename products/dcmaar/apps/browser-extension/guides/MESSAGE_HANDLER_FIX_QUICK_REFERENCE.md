# Quick Fix Reference - Message Handler Issues

## Problem

- Popup: `GET_ANALYTICS returned no response`
- Dashboard: `GET_USAGE_SUMMARY error (plugin data): Guardian usage plugin not available`

## Root Cause

Message handlers tried to use non-existent plugins instead of calling real methods:

| Handler           | Was Calling                     | Fixed To Call              |
| ----------------- | ------------------------------- | -------------------------- |
| GET_USAGE_SUMMARY | `getUsageSummaryViaPlugin()` ❌ | `getAnalyticsSummary()` ✅ |
| EVALUATE_POLICY   | `evaluatePolicyWithPlugin()` ❌ | `blocker.shouldBlock()` ✅ |

## What Was Fixed

### 1. GuardianController.ts - GET_USAGE_SUMMARY Handler

```typescript
// BEFORE: Tried to use non-existent plugin
const summary = await this.getUsageSummaryViaPlugin();
if (!summary) return error: "Guardian usage plugin not available"

// AFTER: Direct storage access
const summary = await this.getAnalyticsSummary();
return { success: true, data: summary };
```

### 2. GuardianController.ts - EVALUATE_POLICY Handler

```typescript
// BEFORE: Tried to use non-existent plugin
const decision = await this.evaluatePolicyWithPlugin(url);
if (!decision) return error: "Guardian policy plugin not available"

// AFTER: Use WebsiteBlocker directly
const blockResult = await this.blocker.shouldBlock(url);
return { success: true, data: { decision: blockResult.blocked ? 'block' : 'allow', ... }};
```

### 3. Removed Unused Code

- Deleted 3 unused imports from `@dcmaar/guardian-plugins`
- Commented out 3 unused plugin-based methods
- Result: No more "Unexpected any" type errors

## Why It Works Now

### Data Flow for GET_ANALYTICS / GET_USAGE_SUMMARY

```
Guardian Extension
  ├─ Tab Monitoring (setupTabMonitoring)
  │  └─ Saves to: guardian-usage storage
  ├─ Event Capture (event capture)
  │  └─ Saves to: guardian-events storage
  │
  └─ Message Handler (GET_ANALYTICS)
     └─ Calls getAnalyticsSummary()
        └─ Reads from storage
           ├─ guardian-usage[]
           └─ guardian-events[]
           └─ Returns computed analytics
```

### Data Flow for EVALUATE_POLICY

```
Popup sends EVALUATE_POLICY
  └─ GuardianController.evaluatePolicy()
     └─ Calls blocker.shouldBlock(url)
        ├─ Check whitelist
        ├─ Check blacklist
        ├─ Check category blocking
        └─ Return { blocked, reason, policyId }
           └─ Convert to { decision, reason, policyId }
```

## Build Result

✅ **All 3 browsers compiled successfully**

- dist/chrome - Ready
- dist/firefox - Ready
- dist/edge - Ready

## Next Step

User loads extension and tests:

1. Open popup - should show analytics
2. Open dashboard - should show full analytics
3. Visit websites - should update usage data
4. NO errors about "plugin not available"
