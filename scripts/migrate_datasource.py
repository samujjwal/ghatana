#!/usr/bin/env python3
"""
Replace HikariDataSource field/constructor-parameter types with javax.sql.DataSource
in main source files (NOT test files).

Test files are excluded since they legitimately create HikariDataSource instances.
"""
import re
from pathlib import Path

ROOT = Path("/home/samujjwal/Developments/ghatana/products/app-platform")

HIKARI_IMPORT = "import com.zaxxer.hikari.HikariDataSource;"
DATASOURCE_IMPORT = "import javax.sql.DataSource;"

# Field declarations: private final HikariDataSource ds;
FIELD_RE = re.compile(r'(private\s+(?:final\s+)?)HikariDataSource(\s+\w+)')
# Constructor/method parameters: HikariDataSource dataSource
PARAM_RE = re.compile(r'(?<!\bnew\s)HikariDataSource(\s+(?!\.)\w+)')
# Type in casts or generics: (HikariDataSource) or <HikariDataSource>
# Don't replace: new HikariDataSource(...) or .getClass() == HikariDataSource
NEW_HIKARI_RE = re.compile(r'new\s+HikariDataSource')


def process_file(filepath: Path) -> tuple[bool, str]:
    content = filepath.read_text(encoding='utf-8')

    # Skip test files
    if '/test/' in str(filepath):
        return False, f"SKIP (test): {filepath.name}"

    # Skip if no HikariDataSource usage
    if 'HikariDataSource' not in content:
        return False, f"SKIP (no HikariDataSource): {filepath.name}"

    # Skip if file creates HikariDataSource instances (composition root)
    if NEW_HIKARI_RE.search(content):
        return False, f"SKIP (creates HikariDataSource): {filepath.name}"

    original = content

    # 1. Replace import
    if HIKARI_IMPORT in content:
        if DATASOURCE_IMPORT not in content:
            content = content.replace(HIKARI_IMPORT, DATASOURCE_IMPORT)
        else:
            # Both imports exist; just remove Hikari import
            content = content.replace(HIKARI_IMPORT + '\n', '')

    # 2. Replace field types
    content = FIELD_RE.sub(r'\1DataSource\2', content)

    # 3. Replace parameter types (but not new HikariDataSource)
    content = PARAM_RE.sub(r'DataSource\1', content)

    # 4. Replace any remaining standalone "HikariDataSource" references
    # (e.g., in type annotations, local variable types) but not in comments
    remaining = re.sub(
        r'(?<!new\s)(?<!\w)HikariDataSource(?!\w)',
        'DataSource',
        content
    )
    # Only apply if it doesn't break something
    if 'new DataSource' not in remaining:
        content = remaining

    if content != original:
        filepath.write_text(content, encoding='utf-8')
        return True, f"MIGRATED: {filepath.name}"
    return False, f"NO CHANGE: {filepath.name}"


def main():
    files = []
    for path in sorted(ROOT.rglob('*.java')):
        try:
            text = path.read_text(encoding='utf-8')
            if 'HikariDataSource' in text:
                files.append(path)
        except Exception:
            pass

    print(f"Found {len(files)} files with HikariDataSource\n")
    migrated = 0
    skipped = 0
    for f in files:
        changed, msg = process_file(f)
        if changed:
            migrated += 1
        else:
            skipped += 1
        if changed or 'creates' in msg:
            print(msg)

    print(f"\n=== Summary ===")
    print(f"Total: {len(files)}")
    print(f"Migrated: {migrated}")
    print(f"Skipped: {skipped}")


if __name__ == '__main__':
    main()
