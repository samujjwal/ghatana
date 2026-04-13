#!/usr/bin/env python3
"""Check the current migration state of all Java modules in the monorepo."""

import os
import sys

ROOT = '/home/samujjwal/Developments/ghatana'

needs_migration = []
already_done = []
no_build_file = []

for dirpath, dirnames, filenames in os.walk(ROOT):
    # Skip build outputs, hidden dirs, buildSrc, build-logic itself
    dirnames[:] = [d for d in dirnames if d not in
                   ('build', '.gradle', 'node_modules', '.git', '.idea', '.kotlin')]

    if 'buildSrc' in dirpath or 'build-logic' in dirpath:
        continue

    if 'build.gradle.kts' in filenames:
        f = os.path.join(dirpath, 'build.gradle.kts')
        rel = os.path.relpath(f, ROOT)

        try:
            with open(f, encoding='utf-8', errors='ignore') as fp:
                content = fp.read()
        except Exception:
            continue

        # Check for migrated plugins
        if ('id("java-module")' in content or
            'id("java-application")' in content or
            'id("protobuf-module")' in content or
            'id("finance-domain-module")' in content or
            'id("integration-test-profile")' in content):
            already_done.append(rel)
        elif 'id("java-library")' in content or ('"application"' in content and 'java' in content.lower()):
            # Has sources? Check src directories
            project_dir = os.path.dirname(f)
            has_sources = (
                os.path.exists(os.path.join(project_dir, 'src/main/java')) or
                os.path.exists(os.path.join(project_dir, 'src/main/kotlin')) or
                os.path.exists(os.path.join(project_dir, 'src/test/java')) or
                os.path.exists(os.path.join(project_dir, 'src/test/kotlin'))
            )

            has_app = ('"application"' in content or 'id("application")' in content)

            if has_sources:
                needs_migration.append((rel, 'java-application' if has_app else 'java-module'))

print("=== ALREADY MIGRATED (build-logic plugins) ===")
for m in sorted(already_done):
    print(f"  [DONE] {m}")

print(f"\nTotal done: {len(already_done)}")

print("\n=== NEEDS MIGRATION (still using java-library/application) ===")
for m, target in sorted(needs_migration):
    print(f"  [MIGRATE -> {target}] {m}")

print(f"\nTotal needing migration: {len(needs_migration)}")
print(f"\nTotal with explicit build-logic: {len(already_done)}")

