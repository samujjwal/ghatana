#!/usr/bin/env python3
"""
Audit deleted files from yappc:platform to verify feature preservation.
For each deleted file, check if an equivalent exists in another module.
"""
import os
import re

BASE = '/Users/samujjwal/Development/ghatana-new'

# Read list of deleted files
with open('/tmp/excluded_files_clean.txt') as f:
    deleted = [line.strip() for line in f if line.strip() and not line.startswith('---')]

print(f"Total deleted files: {len(deleted)}")
print()

# For each deleted file, search for the same classname in other modules
unique_features = []  # files with no equivalent elsewhere
preserved = []        # files with equivalent elsewhere

for filepath in deleted:
    classname = os.path.basename(filepath).replace('.java', '')
    filename = os.path.basename(filepath)
    
    # Search for the same filename outside of yappc/platform
    found_elsewhere = []
    for root, dirs, files in os.walk(os.path.join(BASE, 'products')):
        # Skip platform module and build dirs
        if 'yappc/platform' in root:
            continue
        if '/build/' in root or '/.gradle/' in root:
            continue
        if filename in files:
            found_elsewhere.append(os.path.join(root, filename))
    
    # Also search platform/java (shared platform modules)
    for root, dirs, files in os.walk(os.path.join(BASE, 'platform')):
        if '/build/' in root or '/.gradle/' in root:
            continue
        if filename in files:
            found_elsewhere.append(os.path.join(root, filename))
    
    if found_elsewhere:
        preserved.append((filepath, found_elsewhere))
    else:
        unique_features.append(filepath)

print(f"=== PRESERVED (exist in other modules): {len(preserved)} ===")
print()

print(f"=== UNIQUE TO PLATFORM (NO equivalent found): {len(unique_features)} ===")
for f in sorted(unique_features):
    classname = os.path.basename(f).replace('.java', '')
    print(f"  UNIQUE: {classname} ({f})")
