# 🧹 Cautious Cleanup Guide

## Overview

This guide provides a **gradual, investigative approach** to cleaning up deprecated code in the app-creator codebase. Each script includes safety mechanisms like import checking, manual confirmations, and rollback capabilities.

## 🛡️ Safety Features

- ✅ **Dry-run mode** - Test without deleting anything
- ✅ **Import checking** - Warns if files are still being used
- ✅ **Manual confirmations** - Approve each deletion
- ✅ **Automatic backups** - Creates backup branches
- ✅ **Phase-by-phase** - Execute one phase at a time
- ✅ **Full rollback** - Easy recovery if needed

## 📋 Execution Scripts

### 1. master-cleanup.sh (Main Script)

**Purpose**: Investigates and removes deprecated code in 8 phases

**Usage**:
```bash
# DRY RUN (recommended first)
./scripts/master-cleanup.sh --dry-run

# Interactive mode (asks for confirmation at each step)
./scripts/master-cleanup.sh

# Non-interactive mode (skips confirmations)
./scripts/master-cleanup.sh --yes

# Execute specific phase only
./scripts/master-cleanup.sh --phase 1    # Dead routes
./scripts/master-cleanup.sh --phase 4    # grapes-origin (LARGE)

# Combine options
./scripts/master-cleanup.sh --dry-run --phase 4
```

**8 Cleanup Phases**:

| Phase | Target | Size | Risk |
|-------|--------|------|------|
| 1 | Dead routes (lifecycle, journey, home) | ~10K lines | 🟢 Low |
| 2 | Backup files (*.bak, *.old) | ~500 lines | 🟢 Low |
| 3 | Deprecated components (devsecops, CanvasToolbar) | ~15K lines | 🟡 Medium |
| 4 | grapes-origin folder | 🔴 **50K+ lines** | 🔴 High |
| 5 | page-designer route | ~8K lines | 🟡 Medium |
| 6 | workflows route | ~5K lines | 🟡 Medium |
| 7 | Legacy atom files | ~2K lines | 🟡 Medium |
| 8 | Deprecated libraries (page-builder*) | ~5K lines | 🟡 Medium |

### 2. fix-imports-after-cleanup.sh

**Purpose**: Automatically fixes imports after files are deleted

**Usage**:
```bash
./scripts/fix-imports-after-cleanup.sh
```

**What it fixes**:
- `canvas-atoms` imports → `@yappc/canvas`
- `auth` atom imports → `@yappc/ui/state`
- Removes imports to deleted routes
- Removes imports to deleted components

### 3. update-routes-config.sh

**Purpose**: Updates `routes.ts` to remove deleted route definitions

**Usage**:
```bash
./scripts/update-routes-config.sh
```

**Creates backup**: `routes.ts.backup`

### 4. validate-cleanup.sh

**Purpose**: Validates cleanup was successful

**Usage**:
```bash
./scripts/validate-cleanup.sh
```

**Checks**:
- ✅ Deleted files are gone
- ✅ No legacy imports remain
- ✅ Build succeeds
- ✅ LOC reduced to target
- ✅ Route count reduced

## 🚀 Recommended Workflow

### Step 1: Dry Run (Risk-Free)
```bash
cd /Users/samujjwal/Development/ghatana/products/yappc/app-creator

# Test full cleanup (no actual deletion)
./scripts/master-cleanup.sh --dry-run

# Review what would be deleted
git status  # Should show no changes
```

### Step 2: Phase-by-Phase Execution

**Start with low-risk phases:**

```bash
# Phase 1: Dead routes (safe)
./scripts/master-cleanup.sh --phase 1
pnpm build  # Verify still builds
git add -A && git commit -m "refactor: cleanup phase 1 - dead routes"

# Phase 2: Backup files (safe)
./scripts/master-cleanup.sh --phase 2
pnpm build
git commit -am "refactor: cleanup phase 2 - backup files"

# Phase 3: Deprecated components (check imports carefully)
./scripts/master-cleanup.sh --phase 3
pnpm build 2>&1 | tee build-errors.log
# Fix any import errors manually
./scripts/fix-imports-after-cleanup.sh
pnpm build
git commit -am "refactor: cleanup phase 3 - deprecated components"
```

**Proceed to high-risk phase with caution:**

```bash
# Phase 4: grapes-origin (LARGE - 50K+ lines!)
# Double-check no active imports first
grep -r "grapes-origin" apps/web/src | grep -v node_modules

# If clean, proceed
./scripts/master-cleanup.sh --phase 4
pnpm build
./scripts/fix-imports-after-cleanup.sh
pnpm build
git commit -am "refactor: cleanup phase 4 - grapes-origin"
```

**Continue with remaining phases:**

```bash
# Phases 5-8
for phase in 5 6 7 8; do
  echo "=== Starting Phase $phase ==="
  ./scripts/master-cleanup.sh --phase $phase
  pnpm build || echo "Build failed - fix manually"
  git commit -am "refactor: cleanup phase $phase"
done
```

### Step 3: Post-Cleanup

```bash
# Fix any remaining import issues
./scripts/fix-imports-after-cleanup.sh

# Update routes config
./scripts/update-routes-config.sh

# Validate everything
./scripts/validate-cleanup.sh

# Final build
pnpm build

# Run tests
pnpm test

# Final commit
git add -A
git commit -m "refactor: complete gradual cleanup - all phases"
```

## 🔄 Rollback Instructions

If something goes wrong at any phase:

```bash
# Check backup branch name (created automatically)
git branch | grep backup

# Rollback to backup
git checkout backup/pre-cleanup-YYYYMMDD-HHMMSS

# Or create a new branch from backup
git checkout -b fix/restore-from-backup backup/pre-cleanup-YYYYMMDD-HHMMSS
```

## ⚠️ Important Notes

### Before You Start

1. **Commit all current work** - Don't run cleanup with uncommitted changes
2. **Run dry-run first** - Always test with `--dry-run` flag
3. **Start with low-risk phases** - Build confidence gradually
4. **Check imports manually** - Verify grep results before Phase 4

### During Cleanup

- ✅ Review each confirmation prompt carefully
- ✅ Test build after each phase
- ✅ Commit after each successful phase (atomic commits)
- ✅ Don't proceed if build fails - fix imports first

### After Cleanup

- ✅ Run full validation script
- ✅ Test all routes manually
- ✅ Run full test suite
- ✅ Check for any console warnings

## 📊 Expected Results

| Metric | Before | After | Reduction |
|--------|--------|-------|-----------|
| Lines of Code | ~150K | ~70K | **-80K (53%)** |
| Routes | 40+ | ~15 | **-25 (62%)** |
| Deprecated Files | 20+ | 0 | **-100%** |
| Legacy Layers | 5+ | 0 | **-100%** |
| Build Warnings | Many | 0 | **-100%** |

## 🆘 Troubleshooting

### "Build failed after cleanup"
```bash
# Check for missing imports
pnpm build 2>&1 | grep "Cannot find module"

# Fix imports automatically
./scripts/fix-imports-after-cleanup.sh

# Or manually fix specific imports
grep -r "old-import-name" apps/web/src
# Replace with correct import
```

### "Found active imports during cleanup"
```bash
# Script will show you the files using it
# Option 1: Update those files to use new imports
# Option 2: Skip deletion of that file (press 'N')
```

### "Want to undo a phase"
```bash
# Rollback last commit
git reset --hard HEAD~1

# Or rollback to specific commit
git log --oneline | head -10
git reset --hard <commit-hash>
```

## 💡 Tips

- Start on a **Friday afternoon** - gives you weekend to fix issues
- Execute **one phase per day** for very large codebases
- Keep backup branch for **at least 2 weeks**
- Document any manual fixes in commit messages
- Celebrate after each phase! 🎉

---

**Questions?** Review the architectural plan: [ARCHITECTURAL_REVIEW_AND_STABILIZATION_PLAN.md](../ARCHITECTURAL_REVIEW_AND_STABILIZATION_PLAN.md)
