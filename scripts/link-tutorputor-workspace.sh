#!/bin/bash
# Create workspace symlinks for all @tutorputor/* dependencies
# This works around the pnpm linking issue

cd "$(dirname "$0")/.." || exit 1

TUTORPUTOR_ROOT="products/tutorputor"

# Build a temp file mapping package names to directories
MAPFILE=$(mktemp)
trap "rm -f $MAPFILE" EXIT

for pkg_json in $TUTORPUTOR_ROOT/*/package.json $TUTORPUTOR_ROOT/*/*/package.json; do
  [ -f "$pkg_json" ] || continue
  name=$(jq -r '.name // empty' "$pkg_json" 2>/dev/null)
  dir=$(dirname "$pkg_json")
  case "$name" in
    @tutorputor/*) echo "$name|$dir" >> "$MAPFILE" ;;
  esac
done

count=$(wc -l < "$MAPFILE" | tr -d ' ')
echo "Found $count @tutorputor packages"

# For each package, create symlinks for its @tutorputor dependencies
for pkg_json in $TUTORPUTOR_ROOT/*/package.json $TUTORPUTOR_ROOT/*/*/package.json; do
  [ -f "$pkg_json" ] || continue
  consumer_dir=$(dirname "$pkg_json")

  deps=$(jq -r '(.dependencies // {}) + (.devDependencies // {}) | to_entries[] | select(.key | startswith("@tutorputor/")) | .key' "$pkg_json" 2>/dev/null)

  for dep in $deps; do
    target_dir=$(grep "^${dep}|" "$MAPFILE" | head -1 | cut -d'|' -f2)
    if [ -n "$target_dir" ]; then
      pkg_name="${dep#@tutorputor/}"
      symlink_dir="$consumer_dir/node_modules/@tutorputor"
      symlink_path="$symlink_dir/$pkg_name"

      if [ ! -L "$symlink_path" ] || [ ! -e "$symlink_path" ]; then
        mkdir -p "$symlink_dir"
        rel_path=$(python3 -c "import os.path; print(os.path.relpath('$target_dir', '$symlink_dir'))")
        ln -sf "$rel_path" "$symlink_path"
        echo "  Linked: $dep -> $rel_path (in $consumer_dir)"
      fi
    fi
  done
done
echo "Done!"
