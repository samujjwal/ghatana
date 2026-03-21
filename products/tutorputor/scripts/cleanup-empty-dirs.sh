#!/bin/bash
# Script to clean up empty directories in TutorPutor
# Part of Execution Plan item #7: Clean Up Empty Directories

echo "TutorPutor Empty Directory Cleanup Script"
echo "=========================================="
echo ""

# List of empty directories to remove
EMPTY_DIRS=(
  "libs/learning-engine"
  "libs/learning-path"
  "services/tutorputor-sim-author"
  "services/tutorputor-sim-nl"
  "services/tutorputor-sim-runtime"
)

REPO_ROOT="/Users/samujjwal/Development/ghatana/products/tutorputor"

echo "The following directories are empty or contain only build artifacts:"
for dir in "${EMPTY_DIRS[@]}"; do
  full_path="$REPO_ROOT/$dir"
  if [ -d "$full_path" ]; then
    item_count=$(find "$full_path" -type f 2>/dev/null | wc -l)
    echo "  - $dir ($item_count files)"
  else
    echo "  - $dir (does not exist)"
  fi
done

echo ""
echo "Action required:"
echo "  Option 1: Delete empty directories (archived content is in simulation-engine)"
echo "  Option 2: Populate with content from simulation-engine consolidation"
echo "  Option 3: Keep as placeholders for future expansion"
echo ""
echo "Recommendation: DELETE - Content has been consolidated into simulation-engine"
echo ""

# Check if --execute flag is provided
if [ "$1" == "--execute" ]; then
  echo "Executing cleanup..."
  for dir in "${EMPTY_DIRS[@]}"; do
    full_path="$REPO_ROOT/$dir"
    if [ -d "$full_path" ]; then
      echo "  Removing $dir..."
      rm -rf "$full_path"
      echo "    ✓ Removed"
    fi
  done
  echo ""
  echo "Cleanup complete!"
  echo ""
  echo "Next steps:"
  echo "  1. Update pnpm-workspace.yaml to remove deleted directories"
  echo "  2. Update any import references"
  echo "  3. Run pnpm install to clean up"
else
  echo "To execute cleanup, run: $0 --execute"
fi
