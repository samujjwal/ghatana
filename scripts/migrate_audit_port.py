#!/usr/bin/env python3
"""
Migrate inner AuditPort interfaces to shared AuditBusPort from platform:java:audit.

Handles patterns:
  - Pattern 1: void record(String actorId, String action, String detail) throws Exception
  - Pattern 2: void log(String action, String resourceType, String resourceId, Map<String, Object> details)
  - Pattern 3: Promise<Void> log(String action, ...) -- kept as-is (complex)
  - Pattern 4: Multi-method -- kept as-is (needs manual migration)
  - Pattern 5: void record(5 params) -- handled
  - Pattern 6: void audit(String event, String detail) throws Exception
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path("/home/samujjwal/Developments/ghatana/products/app-platform")

AUDIT_BUSPORT_IMPORT = "import com.ghatana.platform.audit.AuditBusPort;"
AUDIT_EVENT_IMPORT   = "import com.ghatana.platform.audit.AuditEvent;"
MAP_IMPORT           = "import java.util.Map;"

# Regex to find inner AuditPort interface block (single-method only)
INNER_AUDIT_PORT_RE = re.compile(
    r'(\s*(?:/\*\*[^*]*(?:\*[^/][^*]*)*\*/\s*)?)'   # optional javadoc
    r'public\s+interface\s+(?:K07)?AuditPort\s*\{'
    r'[^}]*'                                           # interface body
    r'\}',
    re.MULTILINE | re.DOTALL
)

# Count methods in an AuditPort block
METHOD_COUNT_RE = re.compile(r'(?:void|Promise<Void>)\s+\w+\(')

# Pattern-specific call site replacements
RECORD_3_CALL = re.compile(
    r'audit(?:Port)?\.record\(\s*'
    r'([^,]+),\s*'            # actorId
    r'("(?:\\"|[^"])*"),\s*'  # action string literal
    r'((?:"(?:\\"|[^"])*"(?:\s*\+\s*[^)]+)?|[^)]+))\s*'  # detail expression
    r'\);'
)

LOG_4_CALL = re.compile(
    r'audit(?:Port)?\.log\(\s*'
    r'("(?:\\"|[^"])*"),\s*'  # action
    r'("(?:\\"|[^"])*"),\s*'  # resourceType
    r'([^,]+),\s*'            # resourceId
    r'((?:Map\.of\([^)]*\)|[^)]+))\s*'  # details
    r'\);'
)

AUDIT_2_CALL = re.compile(
    r'audit(?:Port)?\.audit\(\s*'
    r'([^,]+),\s*'            # event
    r'((?:"(?:\\"|[^"])*"(?:\s*\+\s*[^)]+)?|[^)]+))\s*'  # detail
    r'\);'
)

# Simpler multi-line aware patterns for field/param type replacement
FIELD_TYPE_RE = re.compile(r'(private\s+(?:final\s+)?)(?:K07)?AuditPort(\s+audit)')
PARAM_TYPE_RE = re.compile(r'(?:K07)?AuditPort(\s+audit)')


def count_methods(block: str) -> int:
    return len(METHOD_COUNT_RE.findall(block))


def has_promise_return(block: str) -> bool:
    return 'Promise<Void>' in block


def process_file(filepath: Path) -> tuple[bool, str]:
    """Process a single file. Returns (changed, summary)."""
    content = filepath.read_text(encoding='utf-8')

    # Check if already migrated
    if 'AuditBusPort' in content:
        return False, f"SKIP (already migrated): {filepath}"

    # Find inner AuditPort
    match = INNER_AUDIT_PORT_RE.search(content)
    if not match:
        return False, f"SKIP (no inner AuditPort): {filepath}"

    block = match.group(0)
    mc = count_methods(block)

    # Skip multi-method or Promise-returning interfaces
    if mc > 1:
        return False, f"SKIP (multi-method: {mc}): {filepath}"
    if has_promise_return(block):
        return False, f"SKIP (Promise<Void> return): {filepath}"

    original = content

    # 1. Remove inner AuditPort interface definition
    content = INNER_AUDIT_PORT_RE.sub('', content, count=1)

    # 2. Replace field type
    content = FIELD_TYPE_RE.sub(r'\1AuditBusPort\2', content)

    # 3. Replace constructor parameter types
    content = PARAM_TYPE_RE.sub(r'AuditBusPort\1', content)

    # 4. Replace call sites based on method pattern
    if 'void record(String actorId, String action, String detail)' in block or \
       ('void record(' in block and block.count(',') == 2):
        # Pattern 1: record(actorId, action, detail)
        def replace_record_3(m):
            actor = m.group(1).strip()
            event = m.group(2).strip()
            detail = m.group(3).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.principal({actor}).eventType({event})'
                    f'.details(Map.of("detail", {detail})).build());')
        content = RECORD_3_CALL.sub(replace_record_3, content)

    elif 'void log(' in block and 'Map<String, Object>' in block:
        # Pattern 2: log(action, resourceType, resourceId, details)
        def replace_log_4(m):
            action = m.group(1).strip()
            rtype = m.group(2).strip()
            rid = m.group(3).strip()
            details = m.group(4).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType({action}).resourceType({rtype})'
                    f'.resourceId({rid}).details({details}).build());')
        content = LOG_4_CALL.sub(replace_log_4, content)

    elif 'void audit(' in block:
        # Pattern 6: audit(event, detail)
        def replace_audit_2(m):
            event = m.group(1).strip()
            detail = m.group(2).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType({event})'
                    f'.details(Map.of("detail", {detail})).build());')
        content = AUDIT_2_CALL.sub(replace_audit_2, content)

    elif 'void record(' in block and block.count(',') >= 4:
        # Pattern 5: record(entityType, entityId, event, actor, detail) -- skip complex
        return False, f"SKIP (5-param record): {filepath}"

    # 5. Add imports
    pkg_line = content.index('\n') + 1
    import_section_start = content.find('import ', pkg_line)

    imports_to_add = []
    if AUDIT_BUSPORT_IMPORT not in content:
        imports_to_add.append(AUDIT_BUSPORT_IMPORT)
    if AUDIT_EVENT_IMPORT not in content:
        imports_to_add.append(AUDIT_EVENT_IMPORT)
    if 'Map.of(' in content and MAP_IMPORT not in content and 'import java.util.Map;' not in content:
        if 'import java.util.*' not in content:
            imports_to_add.append(MAP_IMPORT)

    if imports_to_add:
        insert_pos = import_section_start
        content = content[:insert_pos] + '\n'.join(imports_to_add) + '\n' + content[insert_pos:]

    if content != original:
        filepath.write_text(content, encoding='utf-8')
        return True, f"MIGRATED: {filepath}"
    return False, f"NO CHANGE: {filepath}"


def find_audit_port_files(root: Path) -> list[Path]:
    """Find all Java files with inner AuditPort interface."""
    results = []
    for path in root.rglob('*.java'):
        if '/test/' in str(path):
            continue
        try:
            text = path.read_text(encoding='utf-8')
            if re.search(r'interface\s+(?:K07)?AuditPort\s*\{', text):
                results.append(path)
        except Exception:
            pass
    return sorted(results)


def main():
    files = find_audit_port_files(ROOT)
    print(f"Found {len(files)} files with inner AuditPort interfaces\n")

    migrated = 0
    skipped = 0
    for f in files:
        changed, msg = process_file(f)
        print(msg)
        if changed:
            migrated += 1
        else:
            skipped += 1

    print(f"\n=== Summary ===")
    print(f"Total:    {len(files)}")
    print(f"Migrated: {migrated}")
    print(f"Skipped:  {skipped}")


if __name__ == '__main__':
    main()
