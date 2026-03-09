#!/usr/bin/env python3
"""Check if any compiled file imports a class from an excluded file."""
import os

# Read excluded files
with open('/tmp/excluded_files.txt') as f:
    excluded = set()
    for line in f:
        line = line.strip()
        if line.startswith('---') or not line:
            continue
        excluded.add(line)

# Get all Java files in platform src
base = 'products/yappc/platform/src/main/java'
all_java = set()
for root, dirs, fnames in os.walk(base):
    for fn in fnames:
        if fn.endswith('.java'):
            all_java.add(os.path.join(root, fn))

compiled = all_java - excluded
print(f'Total: {len(all_java)}, Excluded: {len(excluded)}, Compiled: {len(compiled)}')

# Extract class names from excluded files (only the unique-to-platform ones)
excluded_classes = set()
for fp in excluded:
    cn = os.path.basename(fp).replace('.java', '')
    excluded_classes.add(cn)

# Check if any compiled file imports an excluded class by same-package usage
# Since everything is in com.ghatana.yappc.platform, same-package classes are used without import
issues = []
for cf in sorted(compiled):
    with open(cf) as fh:
        content = fh.read()
    for ec in excluded_classes:
        # Check both imports and direct usage (same package)
        if ec in content:
            # Skip if it's just the class referring to itself or a substring match
            cf_class = os.path.basename(cf).replace('.java', '')
            if ec == cf_class:
                continue
            # Check for word boundary usage
            import re
            if re.search(r'\b' + re.escape(ec) + r'\b', content):
                # Verify it's not just a comment or string
                for i, line in enumerate(content.split('\n')):
                    stripped = line.strip()
                    if stripped.startswith('//') or stripped.startswith('*'):
                        continue
                    if re.search(r'\b' + re.escape(ec) + r'\b', stripped):
                        issues.append((os.path.basename(cf), ec, stripped[:100]))
                        break

seen = set()
for cf, ec, line in sorted(issues):
    key = (cf, ec)
    if key not in seen:
        seen.add(key)
        print(f'  {cf} uses {ec}: {line}')

print(f'\nTotal unique (compiled_file, excluded_class) pairs: {len(seen)}')
