#!/bin/bash
#
# Documentation Consolidation Script
#
# Moves scattered documentation files from root into organized structure
# Follows the YAPPC Documentation Standard
#
# Usage: ./scripts/organize-docs.sh

set -e

YAPPC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$YAPPC_DIR"

echo "======================================"
echo "Documentation Consolidation"
echo "======================================"
echo ""

# Counter for moved files
MOVED_COUNT=0
KEPT_COUNT=0

# ============================================================================
# STEP 1: Keep Essential Files in Root
# ============================================================================

echo "Step 1: Identifying files to keep in root..."
echo ""

KEEP_IN_ROOT=(
  "README.md"
  "CONTRIBUTING.md"
  "CHANGELOG.md"
  "LICENSE.md"
  "YAPPC_UNIFIED_IMPLEMENTATION_PLAN_2026-01-31.md"
)

for file in "${KEEP_IN_ROOT[@]}"; do
  if [ -f "$file" ]; then
    echo "✓ Keeping in root: $file"
    ((KEPT_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 2: Move Audit Reports to docs/audits/2026-01-31/
# ============================================================================

echo "Step 2: Moving audit reports..."
echo ""

AUDIT_FILES=(
  "CODE_STRUCTURE_AUDIT_2026-01-31.md"
  "YAPPC_COMPREHENSIVE_GAP_ANALYSIS_2026-01-31.md"
  "COMPREHENSIVE_VERIFICATION_REPORT_2026-01-31.md"
  "IMPLEMENTATION_AUDIT_2026-01-31.md"
  "ENGINEERING_IMPLEMENTATION_AUDIT_2026-01-27.md"
  "ENGINEERING_QUALITY_AUDIT_2026-01-27.md"
  "PRINCIPAL_ENGINEER_ANALYSIS_2026-01-27.md"
  "YAPPC_BACKEND_API_AUDIT_REPORT.md"
)

for file in "${AUDIT_FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Moving: $file"
    echo "    To: docs/audits/2026-01-31/"
    mv "$file" "docs/audits/2026-01-31/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 3: Move Architecture Docs to docs/architecture/
# ============================================================================

echo "Step 3: Moving architecture documentation..."
echo ""

ARCHITECTURE_FILES=(
  "API_ARCHITECTURE_DIAGRAMS.md"
  "API_GATEWAY_ARCHITECTURE.md"
  "BACKEND_FRONTEND_INTEGRATION_PLAN.md"
  "SINGLE_PORT_ARCHITECTURE.md"
  "GAP_ANALYSIS.md"
)

for file in "${ARCHITECTURE_FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Moving: $file"
    echo "    To: docs/architecture/"
    mv "$file" "docs/architecture/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 4: Move Development Docs to docs/development/
# ============================================================================

echo "Step 4: Moving development documentation..."
echo ""

DEVELOPMENT_FILES=(
  "CODE_ORGANIZATION_IMPLEMENTATION.md"
  "CODE_ORGANIZATION_REVIEW.md"
  "QUICK_START_INTEGRATION.md"
  "RUN_DEV_GUIDE.md"
  "RUN_DEV_UPDATE_SUMMARY.md"
)

for file in "${DEVELOPMENT_FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Moving: $file"
    echo "    To: docs/development/"
    mv "$file" "docs/development/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 5: Move Deployment Docs to docs/deployment/
# ============================================================================

echo "Step 5: Moving deployment documentation..."
echo ""

DEPLOYMENT_FILES=(
  "README_DOCKER.md"
)

for file in "${DEPLOYMENT_FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Moving: $file"
    echo "    To: docs/deployment/"
    mv "$file" "docs/deployment/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 6: Move API Docs to docs/api/
# ============================================================================

echo "Step 6: Moving API documentation..."
echo ""

API_FILES=(
  "API_CHECKLIST.md"
  "API_OWNERSHIP_MATRIX.md"
  "YAPPC_BACKEND_API_IMPLEMENTATION_PLAN.md"
)

for file in "${API_FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Moving: $file"
    echo "    To: docs/api/"
    mv "$file" "docs/api/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 7: Move Reference Docs to docs/guides/
# ============================================================================

echo "Step 7: Moving reference guides..."
echo ""

GUIDE_FILES=(
  "QUICK_REFERENCE.md"
  "SERVICE_QUICK_REFERENCE.md"
)

for file in "${GUIDE_FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "Moving: $file"
    echo "    To: docs/guides/"
    mv "$file" "docs/guides/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# STEP 8: Archive Implementation Reports to docs/audits/2026-01-31/
# ============================================================================

echo "Step 8: Archiving implementation reports..."
echo ""

IMPLEMENTATION_REPORTS=(
  "AGGRESSIVE_MODERNIZATION_REPORT.md"
  "DOCUMENTATION_CLEANUP_COMPLETE.md"
  "IMPLEMENTATION_COMPLETE.md"
  "IMPLEMENTATION_COMPLETE_FINAL_REPORT.md"
  "IMPLEMENTATION_PROGRESS_TRACKER.md"
  "IMPLEMENTATION_STATUS.md"
  "IMPLEMENTATION_STATUS_WEEK1.md"
  "IMPLEMENTATION_SUMMARY_JAN29.md"
  "INTEGRATION_SUMMARY.md"
  "LEFT_RAIL_IMPLEMENTATION.md"
  "LEFT_RAIL_INTEGRATION.md"
  "LIBRARY_CONSOLIDATION_REPORT.md"
  "MODERNIZATION_COMPLETE.md"
  "NEXT_STEPS.md"
  "PHASE1_CLEANUP_COMPLETE.md"
  "PHASE1_IMPLEMENTATION_COMPLETE.md"
  "PHASE1_WEEK1_STATUS.md"
  "PHASE2_CONSOLIDATION_PLAN.md"
  "PHASE2_PROGRESS_TOKENS_CONSOLIDATION.md"
  "PROGRESS_UPDATE_SESSION5.md"
  "REVIEW_COMPLETE.md"
  "SERVICE_INTEGRATION_CHECKLIST.md"
  "SERVICE_ORGANIZATION.md"
  "SERVICE_ORGANIZATION_FINAL_SUMMARY.md"
  "STRUCTURE_BEFORE_AFTER.md"
  "STRUCTURE_FINALIZATION_COMPLETE.md"
  "STRUCTURE_VERIFICATION.md"
  "TODO_CLEANUP_REPORT.md"
  "UI_UX_IMPLEMENTATION_AUDIT_REPORT.md"
  "WEEK1_COMPLETE.md"
  "YAPPC_CODEBASE_ANALYSIS_REPORT.md"
  "YAPPC_FINAL_REPORT.md"
  "YAPPC_FUTURE_WORK_ROADMAP.md"
)

for file in "${IMPLEMENTATION_REPORTS[@]}"; do
  if [ -f "$file" ]; then
    echo "Archiving: $file"
    echo "       To: docs/audits/2026-01-31/"
    mv "$file" "docs/audits/2026-01-31/"
    ((MOVED_COUNT++))
  fi
done

echo ""

# ============================================================================
# SUMMARY
# ============================================================================

echo "======================================"
echo "✅ Documentation Consolidation Complete"
echo "======================================"
echo ""
echo "Files kept in root: $KEPT_COUNT"
echo "Files moved/archived: $MOVED_COUNT"
echo ""
echo "New structure:"
echo "  docs/architecture/     - Architecture & design docs"
echo "  docs/development/      - Developer guides & setup"
echo "  docs/deployment/       - Deployment & operations"
echo "  docs/api/              - API documentation"
echo "  docs/guides/           - User guides & references"
echo "  docs/audits/2026-01-31/ - Historical reports & audits"
echo ""
echo "Next steps:"
echo "1. Review moved files in their new locations"
echo "2. Create docs/README.md with navigation"
echo "3. Update main README.md with doc links"
echo "4. Update internal documentation links"
echo ""
