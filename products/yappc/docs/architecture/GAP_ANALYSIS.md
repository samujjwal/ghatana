# YAPPC Implementation Gap Analysis

**Date**: January 29, 2026  
**Status**: 🔴 Critical Issues Found  
**Priority**: Fix Immediately

---

## 🔴 CRITICAL ISSUES

### 1. AI Core Library Still Uses Port 8080 (6 files)

**Impact**: HIGH - AI features won't work with Gateway architecture

**Files**:

- `libs/ai-core/src/hooks/useAICopilot.ts` (line 154)
- `libs/ai-core/src/hooks/useAIInsights.ts` (line 173)
- `libs/ai-core/src/hooks/useRecommendations.ts` (line 222)
- `libs/ai-core/src/hooks/usePredictions.ts` (line 189)
- `libs/ai-core/src/hooks/useSemanticSearch.ts` (line 230)
- `libs/ai-core/src/hooks/useAnomalyAlerts.ts` (line 218)

**Problem**: All default to `http://localhost:8080` instead of Gateway port 7002

**Fix Required**: Change all to use Gateway port 7002

---

### 2. WebSocket URL Points to Java Backend (Port 7003)

**Impact**: HIGH - Real-time features bypass Gateway

**File**: `apps/web/src/hooks/useRealTimeCollaboration.ts` (line 49)

**Problem**:

```typescript
wsUrl = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:7003";
```

**Should Be**:

```typescript
wsUrl = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:7002";
```

**Reason**: WebSocket should go through Gateway, not directly to Java backend

---

### 3. E2E Integration Test Points to Wrong Port

**Impact**: MEDIUM - Tests will fail

**File**: `e2e/integration.spec.ts` (line 22)

**Problem**:

```typescript
apiBase: process.env.API_BASE_URL || "http://localhost:7003";
```

**Should Be**:

```typescript
apiBase: process.env.API_BASE_URL || "http://localhost:7002";
```

---

### 4. Vitest Config Still Uses Port 3000

**Impact**: MEDIUM - Unit tests may fail

**Files**:

- `apps/web/vitest.config.ts` (line 79)
- `apps/web/vitest.config.js` (line 75)

**Problem**: Mock server config points to 3000 instead of 7002

---

## 🟡 MEDIUM PRIORITY ISSUES

### 5. Mock API Server Comments Outdated

**Impact**: LOW - Confusing for developers

**Files**:

- `apps/web/mock-api-server.js` (line 14) - Says port 3000
- `apps/api/mock-server.ts` (line 141) - Says port 7003

**Fix**: Update comments to reflect Gateway architecture

---

### 6. UI Component Hardcoded Port Examples

**Impact**: LOW - Examples misleading

**Files**:

- `apps/web/src/components/canvas/content/ConfigBrowserCanvas.tsx` (line 54)
- `apps/web/src/components/canvas/content/ConfigEditorCanvas.tsx` (line 102)
- `libs/canvas/src/components/OpenAPIGeneratorDialog.tsx` (line 95, 256)

**Problem**: Example config shows `http://localhost:3000` instead of 7002

---

### 7. Test Mocks Use Port 3000

**Impact**: LOW - Tests work but misleading

**Files**:

- `libs/ui/src/test/mocks.ts` (line 86-87)
- `libs/canvas/src/services/OpenAPIService.ts` (line 170)

---

## 🟢 LOW PRIORITY / DOCUMENTATION

### 8. JSDoc Comments Reference Wrong Ports

**Impact**: VERY LOW - Documentation only

**Files**:

- `libs/ai-core/src/agents/api-client.ts` (line 30)
- `libs/graphql/src/apolloClient.ts` (line 79)

**Fix**: Update JSDoc to reference port 7002

---

### 9. Archive Directory Still Referenced

**Impact**: NONE - Files already deleted

**Files**: `.archive/` directory contains old patterns but we deleted it ✅

---

## 📊 SUMMARY

| Priority    | Issues | Status      | Action Required     |
| ----------- | ------ | ----------- | ------------------- |
| 🔴 Critical | 4      | ❌ Unfixed  | Fix immediately     |
| 🟡 Medium   | 3      | ❌ Unfixed  | Fix this week       |
| 🟢 Low      | 2      | ⚠️ Optional | Fix when convenient |

---

## 🔧 FIX PLAN

### Immediate (Now)

1. ✅ Fix AI Core library hooks (6 files) - Port 8080 → 7002
2. ✅ Fix WebSocket URL - Port 7003 → 7002
3. ✅ Fix E2E integration test - Port 7003 → 7002
4. ✅ Fix Vitest configs - Port 3000 → 7002

### This Week

5. Update UI component examples to port 7002
6. Update test mocks to port 7002
7. Update mock server comments

### Optional

8. Update JSDoc comments
9. Verify no other hardcoded ports remain

---

## 🎯 ROOT CAUSES

### Why These Were Missed

1. **AI Core Library**: Searched for "app-creator/apps" but missed "app-creator/libs"
2. **WebSocket**: Different env var pattern (NEXT_PUBLIC_WS_URL)
3. **Tests**: Focused on .env files, missed inline defaults
4. **UI Components**: Example data, not actual config

### Prevention

- [x] Broader grep search patterns needed
- [x] Check all libs/\*\* directories
- [x] Search for ws:// URLs
- [x] Review test files more thoroughly

---

## 📝 DETAILED FIX INSTRUCTIONS

### Fix 1: AI Core Hooks (CRITICAL)

**Pattern**: All hooks use same default structure

```typescript
// OLD (Wrong)
baseUrl = "http://localhost:8080";

// NEW (Correct)
baseUrl = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? "http://localhost:7002"}`
  : "";
```

**Files to Update**:

1. `libs/ai-core/src/hooks/useAICopilot.ts:154`
2. `libs/ai-core/src/hooks/useAIInsights.ts:173`
3. `libs/ai-core/src/hooks/useRecommendations.ts:222`
4. `libs/ai-core/src/hooks/usePredictions.ts:189`
5. `libs/ai-core/src/hooks/useSemanticSearch.ts:230`
6. `libs/ai-core/src/hooks/useAnomalyAlerts.ts:218`

### Fix 2: WebSocket (CRITICAL)

**File**: `apps/web/src/hooks/useRealTimeCollaboration.ts:49`

```typescript
// OLD
wsUrl = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:7003";

// NEW
wsUrl = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:7002";
```

### Fix 3: E2E Test (CRITICAL)

**File**: `e2e/integration.spec.ts:22`

```typescript
// OLD
apiBase: process.env.API_BASE_URL || "http://localhost:7003";

// NEW
apiBase: process.env.API_BASE_URL || "http://localhost:7002";
```

### Fix 4: Vitest Configs (CRITICAL)

**Files**:

- `apps/web/vitest.config.ts:79`
- `apps/web/vitest.config.js:75`

```typescript
// OLD
url: "http://localhost:3000";

// NEW
url: "http://localhost:7002";
```

---

## ✅ VERIFICATION CHECKLIST

After fixes:

- [ ] All AI Core hooks use port 7002
- [ ] WebSocket connects to Gateway (7002)
- [ ] E2E tests use Gateway (7002)
- [ ] Vitest configs use Gateway (7002)
- [ ] No references to port 8080 (except Java backend internal)
- [ ] No references to port 3000 (except comments/examples)
- [ ] Run full grep search to verify

---

## 🧪 TEST PLAN

After fixes, test:

1. **AI Features**:

   ```bash
   # Test AI hooks connect to Gateway
   curl http://localhost:7002/api/ai/suggestions
   ```

2. **WebSocket**:

   ```bash
   # Check WebSocket connects to Gateway
   wscat -c ws://localhost:7002/canvas/test-project
   ```

3. **E2E Tests**:

   ```bash
   npm run test:e2e
   ```

4. **Unit Tests**:
   ```bash
   npm run test
   ```

---

## 📈 COMPLETION METRICS

**Before Fixes**:

- Critical Issues: 4 ❌
- Medium Issues: 3 ⚠️
- Low Issues: 2 ℹ️

**After Fixes** (Target):

- Critical Issues: 0 ✅
- Medium Issues: 0 ✅
- Low Issues: 0 ✅

**Coverage**:

- Files Checked: 50+
- Patterns Searched: 5
- Directories Scanned: app-creator/\*\*

---

## 🚨 RISK ASSESSMENT

### If Not Fixed

**Critical Issues**:

- AI features completely broken ❌
- Real-time collaboration bypasses security ❌
- Tests fail in CI/CD ❌
- Inconsistent architecture ❌

**Medium Issues**:

- Developer confusion 🤔
- Wrong examples in docs 📝
- Harder to debug 🐛

### After Fixing

- ✅ 100% single-port architecture
- ✅ All features work through Gateway
- ✅ Consistent patterns throughout
- ✅ Tests pass reliably
- ✅ Production-ready

---

**Status**: Gap analysis complete  
**Next Action**: Execute fixes immediately  
**ETA**: 30 minutes to fix all critical issues
