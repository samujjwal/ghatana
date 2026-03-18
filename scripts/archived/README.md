# Archived Scripts

These Python scripts were used as one-off migration tools during the 2026-Q1 codebase migration.
They have been archived here for historical reference and should not be executed again.

## Contents

| Script           | Original Purpose                  |
| ---------------- | --------------------------------- |
| `patch.py`       | Proto import path patching        |
| `patch2.py`      | Secondary proto path patching     |
| `patch3.py`      | Package declaration migration     |
| `patch4.py`      | Import rewriting (phase 4)        |
| `patch6.py`      | Gradle module reference rewriting |
| `patch_http.py`  | ActiveJ HTTP import migration     |
| `patch_http2.py` | ActiveJ HTTP v2 import migration  |
| `patch_yappc.py` | YAPPC-specific package rewriting  |

## Status

Archived 2026-Q2 after all migrations confirmed complete. These scripts are no longer maintained
and should not be re-run in their current form. If similar transformations are needed in the
future, write a new script with proper test coverage and documentation.

**Owner:** Platform team (see `.github/CODEOWNERS`)
