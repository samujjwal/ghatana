#!/bin/bash
# master-cleanup.sh - CAUTIOUS GRADUAL CLEANUP WITH INVESTIGATION
# Investigates before deletion, allows rollback, gradual execution

set -e
cd "$(dirname "$0")/.."

# Parse command line arguments
DRY_RUN=false
INTERACTIVE=true
PHASE="all"

while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --yes)
      INTERACTIVE=false
      shift
      ;;
    --phase)
      PHASE="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--dry-run] [--yes] [--phase <1-8|all>]"
      exit 1
      ;;
  esac
done

if [ "$DRY_RUN" = true ]; then
  echo "🔍 DRY RUN MODE - No files will be deleted"
else
  echo "⚠️  LIVE MODE - Files will be deleted (Ctrl+C to cancel)"
fi
echo ""
echo "📋 CAUTIOUS CLEANUP - Investigation Phase"
echo ""

# Create backup branch
BACKUP_BRANCH="backup/pre-cleanup-$(date +%Y%m%d-%H%M%S)"
echo "📦 Creating backup branch: $BACKUP_BRANCH"
git checkout -b "$BACKUP_BRANCH"
git push origin "$BACKUP_BRANCH" || echo "⚠️  Failed to push backup (continue anyway)"

# Checkout cleanup branch
CLEANUP_BRANCH="refactor/gradual-cleanup"
echo "🌿 Creating cleanup branch: $CLEANUP_BRANCH"
git checkout -b "$CLEANUP_BRANCH" 2>/dev/null || git checkout "$CLEANUP_BRANCH"

echo "📊 Initial stats..."
echo "Lines of code before: $(find apps/web/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 || echo 'N/A')"
echo ""

# Helper function to check if file/folder has active imports
check_imports() {
  local target="$1"
  local name=$(basename "$target")
  
  echo "  🔍 Checking imports for: $name"
  
  # Search for imports in entire codebase
  local import_count=$(grep -r "from.*$name" apps/web/src 2>/dev/null | grep -v "node_modules" | wc -l || echo "0")
  
  if [ "$import_count" -gt 0 ]; then
    echo "    ⚠️  Found $import_count active imports!"
    grep -r "from.*$name" apps/web/src 2>/dev/null | grep -v "node_modules" | head -5
    return 1
  else
    echo "    ✅ No active imports found"
    return 0
  fi
}

# Helper function to confirm deletion
confirm_delete() {
  local message="$1"
  
  if [ "$INTERACTIVE" = false ]; then
    return 0
  fi
  
  echo ""
  read -p "$message (y/N): " -n 1 -r
  echo ""
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    return 0
  else
    return 1
  fi
}

# Helper function to safely delete
safe_delete() {
  local target="$1"
  local description="$2"
  
  if [ ! -e "$target" ]; then
    echo "    ℹ️  Already deleted or doesn't exist: $target"
    return 0
  fi
  
  echo "  📦 Target: $target"
  echo "  📝 Description: $description"
  
  # Count lines to be deleted
  local lines=0
  if [ -f "$target" ]; then
    lines=$(wc -l < "$target" 2>/dev/null || echo "0")
  elif [ -d "$target" ]; then
    lines=$(find "$target" -name "*.ts" -o -name "*.tsx" 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
  fi
  echo "  📊 Lines: $lines"
  
  # Check for active imports
  if ! check_imports "$target"; then
    echo "  ❌ SKIPPED - Has active imports (fix imports first)"
    return 1
  fi
  
  # Confirm deletion
  if ! confirm_delete "  Delete $target?"; then
    echo "  ⏭️  SKIPPED by user"
    return 1
  fi
  
  # Execute deletion
  if [ "$DRY_RUN" = true ]; then
    echo "  🔍 DRY RUN - Would delete: $target"
  else
    rm -rf "$target"
    echo "  ✅ DELETED"
  fi
  
  return 0
}

# ============================================================================
# SECTION 1: INVESTIGATE & REMOVE DEAD ROUTES (gradual)
# ============================================================================
if [ "$PHASE" = "all" ] || [ "$PHASE" = "1" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 1: Dead Route Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  
  ROUTES_TO_INVESTIGATE=(
    "apps/web/src/routes/lifecycle/|Legacy lifecycle routes"
    "apps/web/src/routes/journey.tsx|Legacy journey route"
    "apps/web/src/routes/journey-loading.tsx|Journey loading state"
    "apps/web/src/routes/home.tsx|Old home route"
    "apps/web/src/routes/canvas-redirect.tsx|Canvas redirect route"
  )
  
  DELETED_COUNT=0
  SKIPPED_COUNT=0
  
  for route_info in "${ROUTES_TO_INVESTIGATE[@]}"; do
    IFS='|' read -r route_path route_desc <<< "$route_info"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if safe_delete "$route_path" "$route_desc"; then
      DELETED_COUNT=$((DELETED_COUNT + 1))
    else
      SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
    fi
    echo ""
  done
  
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "📊 Phase 1 Summary: Deleted $DELETED_COUNT, Skipped $SKIPPED_COUNT"
  echo ""
fi

# ============================================================================
# SECTION 2: INVESTIGATE & CLEAN BACKUP/TEMP FILES (safe)
# ============================================================================
if [ "$PHASE" = "all" ] || [ "$PHASE" = "2" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 2: Backup Files Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  
  echo "🔍 Searching for backup files..."
  BACKUP_FILES=$(find apps/web/src -name "*.bak*" -o -name "*.old" -o -name "*.backup" -o -name "*-old.*" 2>/dev/null || true)
  
  if [ -z "$BACKUP_FILES" ]; then
    echo "  ✅ No backup files found"
  else
    echo "  Found backup files:"
    echo "$BACKUP_FILES"
    echo ""
    
    if confirm_delete "Delete all backup files?"; then
      if [ "$DRY_RUN" = true ]; then
        echo "  🔍 DRY RUN - Would delete backup files"
      else
        find apps/web/src -name "*.bak*" -delete 2>/dev/null || true
        find apps/web/src -name "*.old" -delete 2>/dev/null || true
        find apps/web/src -name "*.backup" -delete 2>/dev/null || true
        find apps/web/src -name "*-old.*" -delete 2>/dev/null || true
        echo "  ✅ Backup files cleaned"
      fi
    else
      echo "  ⏭️  Skipped backup file cleanup"
    fi
  fi
  echo ""
fi

# ============================================================================
# SECTION 3: INVESTIGATE & REMOVE DEPRECATED COMPONENTS (gradual)
# ============================================================================
if [ "$PHASE" = "all" ] || [ "$PHASE" = "3" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 3: Component Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  
  COMPONENTS_TO_INVESTIGATE=(
    "apps/web/src/components/canvas/devsecops/|DevSecOps canvas components"
    "apps/web/src/components/canvas/toolbar/CanvasToolbar.tsx|Old canvas toolbar"
    "apps/web/src/components/canvas/toolbar/NodeTypePicker.tsx|Old node picker"
    "apps/web/src/components/workflow/WorkflowShell.tsx|Legacy workflow shell"
    "apps/web/src/components/workflow/StepRail.tsx|Workflow step rail"
    "apps/web/src/components/tasks/TaskExecutionGrid.tsx|Task execution grid"
    "apps/web/src/components/navigation/BreadcrumbBar.tsx|Old breadcrumb bar"
  )
  
  DELETED_COUNT=0
  SKIPPED_COUNT=0
  
  for component_info in "${COMPONENTS_TO_INVESTIGATE[@]}"; do
    IFS='|' read -r component_path component_desc <<< "$component_info"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if safe_delete "$component_path" "$component_desc"; then
      DELETED_COUNT=$((DELETED_COUNT + 1))
    else
      SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
    fi
    echo ""
  done
  
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "📊 Phase 3 Summary: Deleted $DELETED_COUNT, Skipped $SKIPPED_COUNT"
  echo ""
fi

# ============================================================================
# SECTION 4: INVESTIGATE GRAPES-ORIGIN (50K+ lines - MAJOR)
# ============================================================================
if [ "$PHASE" = "all" ] || [ "$PHASE" = "4" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 4: GrapesJS Origin Investigation (MAJOR)"
  echo "═══════════════════════════════════════"
  echo ""
  
  TARGET="apps/web/src/grapes-origin/"
  
  if [ -d "$TARGET" ]; then
    echo "⚠️  WARNING: This is a LARGE deletion (50K+ lines)"
    echo ""
    safe_delete "$TARGET" "Legacy GrapesJS origin code"
  else
    echo "  ℹ️  grapes-origin/ not found or already deleted"
  fi
  echo ""
fi

# ============================================================================
# REMAINING PHASES 5-8: Continue gradual investigation
# ============================================================================
if [ "$PHASE" = "all" ] || [ "$PHASE" = "5" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 5: Page Designer Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  safe_delete "apps/web/src/routes/page-designer.tsx" "Page designer route"
  safe_delete "apps/web/src/routes/page-designer/" "Page designer folder"
  echo ""
fi

if [ "$PHASE" = "all" ] || [ "$PHASE" = "6" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 6: Workflows Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  safe_delete "apps/web/src/routes/workflows/" "Deprecated workflows route"
  echo ""
fi

if [ "$PHASE" = "all" ] || [ "$PHASE" = "7" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 7: Legacy Atoms Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  safe_delete "apps/web/src/components/canvas/canvas-atoms.ts" "Canvas atoms (legacy)"
  safe_delete "libs/ui/src/canvas-atoms.ts" "UI canvas atoms (legacy)"
  safe_delete "libs/store/src/atoms/auth.ts" "Auth atoms (legacy)"
  safe_delete "libs/canvas/src/migration/legacy-atoms.ts" "Migration legacy atoms"
  echo ""
fi

if [ "$PHASE" = "all" ] || [ "$PHASE" = "8" ]; then
  echo "═══════════════════════════════════════"
  echo "📋 PHASE 8: Deprecated Libraries Investigation"
  echo "═══════════════════════════════════════"
  echo ""
  safe_delete "libs/page-builder-ui/" "Page builder UI library"
  safe_delete "libs/page-builder/" "Page builder library"
  echo ""
fi

# ============================================================================
# CLEANUP COMPLETE - SHOW STATS
# ============================================================================
echo ""
echo "═══════════════════════════════════════"
echo "📊 Final Statistics"
echo "═══════════════════════════════════════"
echo "Lines of code after: $(find apps/web/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 || echo 'N/A')"
echo "Files changed: $(git status --short 2>/dev/null | wc -l || echo '0')"
echo ""

if [ "$DRY_RUN" = true ]; then
  echo "🔍 DRY RUN COMPLETE - No files were actually deleted"
  echo ""
  echo "To execute for real, run: $0 (without --dry-run)"
else
  echo "✅ CAUTIOUS CLEANUP COMPLETE!"
  echo ""
  echo "⚠️  Next steps:"
  echo "   1. Review changes: git status"
  echo "   2. Test build: pnpm build"
  echo "   3. If build succeeds, run: ./scripts/fix-imports-after-cleanup.sh"
  echo "   4. Continue with remaining phases if needed"
  echo "   5. Commit when ready: git add -A && git commit -m 'refactor: gradual cleanup phase X'"
  echo ""
  echo "📋 To run specific phases:"
  echo "   $0 --phase 1  # Just dead routes"
  echo "   $0 --phase 4  # Just grapes-origin (LARGE)"
  echo ""
  echo "💡 To rollback: git checkout $BACKUP_BRANCH"
fi
echo ""
echo "═══════════════════════════════════════"
