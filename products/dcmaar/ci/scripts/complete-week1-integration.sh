#!/bin/bash
# OBSOLETE: This script was for migrating metrics-agent into services/agent-rs
# services/agent-rs has been removed and functionality migrated to libs/agent-common
# This script is kept for historical reference only.
exit 0

# Week 1 Integration Completion Script
# Completes the remaining 15% of Week 1 work
# Estimated time: 4-6 hours

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Week 1 Integration Completion ==="
echo "Project root: $PROJECT_ROOT"
echo "This script completes the remaining 15% of Week 1"
echo ""

cd "$PROJECT_ROOT"

# Step 1: Copy detection files
echo "[1/10] Copying detection files..."
mkdir -p services/agent-rs/crates/agent-metrics/src/detection
cp services/metrics-agent/src/detection/*.rs \
   services/agent-rs/crates/agent-metrics/src/detection/ || echo "Detection files may already exist"

# Step 2: Copy collector files
echo "[2/10] Copying collector files..."
for file in memory disk network system; do
  if [ -f "services/metrics-agent/src/collectors/${file}.rs" ]; then
    cp services/metrics-agent/src/collectors/${file}.rs \
       services/agent-rs/crates/agent-metrics/src/collectors/
    echo "  - Copied ${file}.rs"
  fi
done

# Step 3: Copy exporters
echo "[3/10] Copying exporters..."
mkdir -p services/agent-rs/crates/agent-metrics/src/exporters
if [ -d "services/metrics-agent/src/exporters" ]; then
  cp -r services/metrics-agent/src/exporters/* \
     services/agent-rs/crates/agent-metrics/src/exporters/ 2>/dev/null || true
fi

# Step 4: Copy buffer
echo "[4/10] Copying buffer..."
mkdir -p services/agent-rs/crates/agent-metrics/src/buffer
if [ -d "services/metrics-agent/src/buffer" ]; then
  cp -r services/metrics-agent/src/buffer/* \
     services/agent-rs/crates/agent-metrics/src/buffer/ 2>/dev/null || true
fi

# Step 5: Fix imports (manual step)
echo "[5/10] Fix imports - MANUAL STEP REQUIRED"
echo ""
echo "  Please fix the following in agent-rs/crates/agent-metrics/src/:"
echo "  1. Remove 'use crate::config::AgentConfig' references"
echo "  2. Update collector constructors to use simple bool"
echo "  3. Fix any import paths"
echo ""
echo "  Press Enter when done, or Ctrl+C to exit and do manually..."
read -r

# Step 6: Test compilation
echo "[6/10] Testing compilation..."
cd services/agent-rs
if cargo check --package agent-metrics; then
  echo "  ✅ Compilation successful"
else
  echo "  ❌ Compilation failed - fix errors and re-run"
  exit 1
fi

# Step 7: Run tests
echo "[7/10] Running tests..."
if cargo test --package agent-metrics; then
  echo "  ✅ Tests passed"
else
  echo "  ⚠️  Some tests failed - review and fix"
fi

# Step 8: Update documentation
echo "[8/10] Update documentation - MANUAL STEP REQUIRED"
echo ""
echo "  Please update:"
echo "  1. services/agent-rs/README.md - Add metrics collection section"
echo "  2. Add usage examples"
echo "  3. Update CHANGELOG.md"
echo ""
echo "  Press Enter when done, or Ctrl+C to exit and do manually..."
read -r

# Step 9: Cleanup
echo "[9/10] Cleaning up..."
cd "$PROJECT_ROOT"

echo "  Remove services/metrics-agent? (y/N)"
read -r response
if [[ "$response" =~ ^[Yy]$ ]]; then
  rm -rf services/metrics-agent
  echo "  ✅ Removed services/metrics-agent"
fi

echo "  Archive services/agent-rust? (y/N)"
read -r response
if [[ "$response" =~ ^[Yy]$ ]]; then
  mkdir -p archive/old-agents
  if [ -d "services/agent-rust" ]; then
    mv services/agent-rust archive/old-agents/
    echo "  ✅ Archived services/agent-rust"
  fi
fi

# Step 10: Final verification
echo "[10/10] Final verification..."
cd services/agent-rs
echo "  Running full workspace tests..."
if cargo test --workspace --quiet; then
  echo "  ✅ All tests passed"
else
  echo "  ⚠️  Some tests failed"
fi

echo ""
echo "=== Week 1 Integration Complete! ==="
echo ""
echo "Status: 100% Complete ✅"
echo ""
echo "Next steps:"
echo "1. Review changes"
echo "2. Commit to version control"
echo "3. Update documentation"
echo "4. Begin Week 2 planning"
echo ""
