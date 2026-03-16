#!/usr/bin/env python3
"""
Second pass: handle files skipped by first migration script.
Handles Promise<Void> return, 5-param record, and multi-method AuditPort patterns.
"""
import re
from pathlib import Path

ROOT = Path("/home/samujjwal/Developments/ghatana/products/app-platform")

AUDIT_BUSPORT_IMPORT = "import com.ghatana.platform.audit.AuditBusPort;"
AUDIT_EVENT_IMPORT   = "import com.ghatana.platform.audit.AuditEvent;"
MAP_IMPORT           = "import java.util.Map;"

INNER_AUDIT_PORT_RE = re.compile(
    r'(\s*(?:/\*\*[^*]*(?:\*[^/][^*]*)*\*/\s*)?)'
    r'public\s+interface\s+(?:K07)?AuditPort\s*\{'
    r'[^}]*'
    r'\}',
    re.MULTILINE | re.DOTALL
)

FIELD_TYPE_RE = re.compile(r'(private\s+(?:final\s+)?)(?:K07)?AuditPort(\s+audit)')
PARAM_TYPE_RE = re.compile(r'(?:K07)?AuditPort(\s+audit)')

# Promise<Void> log(action, actor, entityId, entityType, beforeJson, afterJson)
PROMISE_LOG_CALL = re.compile(
    r'audit(?:Port)?\.log\(\s*'
    r'([^,]+),\s*'     # action
    r'([^,]+),\s*'     # actor
    r'([^,]+),\s*'     # entityId
    r'([^,]+),\s*'     # entityType
    r'([^,]+),\s*'     # beforeJson
    r'([^)]+)\s*'      # afterJson
    r'\);'
)

# void record(entityType, entityId, event, actor, detail)
RECORD_5_CALL = re.compile(
    r'audit(?:Port)?\.record\(\s*'
    r'([^,]+),\s*'     # entityType
    r'([^,]+),\s*'     # entityId
    r'([^,]+),\s*'     # event
    r'([^,]+),\s*'     # actor
    r'((?:"(?:\\"|[^"])*"(?:\s*\+\s*[^)]+)?|[^)]+))\s*'  # detail
    r'\);'
)

# Multi-method: logNarrativeDraft(...), logNarrativeApproval(...), logEvidenceLinked(...)
NARRATIVE_DRAFT_CALL = re.compile(
    r'audit(?:Port)?\.logNarrativeDraft\(\s*'
    r'([^,]+),\s*'     # narrativeId
    r'([^,]+),\s*'     # alertId/caId
    r'([^,]+),\s*'     # prompt
    r'([^,]+)'         # draft  (may have more params)
    r'(?:,\s*([^)]+))?\s*'  # optional timestamp
    r'\);'
)

NARRATIVE_APPROVAL_CALL = re.compile(
    r'audit(?:Port)?\.logNarrativeApproval\(\s*'
    r'([^,]+),\s*'   # narrativeId
    r'([^,]+)'       # approver/caId
    r'(?:,\s*([^)]+(?:,\s*[^)]+)*)?)?\s*'  # optional extra params
    r'\);'
)

EVIDENCE_LINKED_CALL = re.compile(
    r'audit(?:Port)?\.logEvidenceLinked\(\s*'
    r'([^,]+),\s*'   # caseId
    r'([^,]+),\s*'   # evidenceId
    r'([^,]+),\s*'   # sourceSystem
    r'([^,]+),\s*'   # contentHash
    r'([^)]+)\s*'    # at
    r'\);'
)


def add_imports(content: str) -> str:
    pkg_line = content.index('\n') + 1
    import_section_start = content.find('import ', pkg_line)
    imports_to_add = []
    if AUDIT_BUSPORT_IMPORT not in content:
        imports_to_add.append(AUDIT_BUSPORT_IMPORT)
    if AUDIT_EVENT_IMPORT not in content:
        imports_to_add.append(AUDIT_EVENT_IMPORT)
    if 'Map.of(' in content and 'import java.util.Map;' not in content and 'import java.util.*' not in content:
        imports_to_add.append(MAP_IMPORT)
    if imports_to_add and import_section_start >= 0:
        content = content[:import_section_start] + '\n'.join(imports_to_add) + '\n' + content[import_section_start:]
    return content


def process_file(filepath: Path) -> tuple[bool, str]:
    content = filepath.read_text(encoding='utf-8')
    if 'AuditBusPort' in content and 'interface' not in content.split('AuditBusPort')[0].split('\n')[-1]:
        # Check if already fully migrated
        match = INNER_AUDIT_PORT_RE.search(content)
        if not match:
            return False, f"SKIP (already done): {filepath.name}"

    match = INNER_AUDIT_PORT_RE.search(content)
    if not match:
        return False, f"SKIP (no inner AuditPort): {filepath.name}"

    block = match.group(0)
    original = content

    # Remove inner interface
    content = INNER_AUDIT_PORT_RE.sub('', content, count=1)
    # Replace types
    content = FIELD_TYPE_RE.sub(r'\1AuditBusPort\2', content)
    content = PARAM_TYPE_RE.sub(r'AuditBusPort\1', content)

    # Determine pattern from block
    if 'Promise<Void> log(' in block and block.count(',') >= 5:
        # Promise<Void> log(action, actor, entityId, entityType, before, after)
        def replace_promise_log(m):
            action = m.group(1).strip()
            actor = m.group(2).strip()
            eid = m.group(3).strip()
            etype = m.group(4).strip()
            before = m.group(5).strip()
            after = m.group(6).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType({action}).principal({actor})'
                    f'.resourceId({eid}).resourceType({etype})'
                    f'.details(Map.of("before", String.valueOf({before}), "after", String.valueOf({after})))'
                    f'.build());')
        content = PROMISE_LOG_CALL.sub(replace_promise_log, content)

    if 'void record(' in block and block.count(',') >= 4:
        # 5-param: record(entityType, entityId, event, actor, detail)
        def replace_record_5(m):
            etype = m.group(1).strip()
            eid = m.group(2).strip()
            event = m.group(3).strip()
            actor = m.group(4).strip()
            detail = m.group(5).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType({event}).principal({actor})'
                    f'.resourceType({etype}).resourceId({eid})'
                    f'.details(Map.of("detail", {detail}))'
                    f'.build());')
        content = RECORD_5_CALL.sub(replace_record_5, content)

    if 'logNarrativeDraft' in block:
        def replace_draft(m):
            nid = m.group(1).strip()
            aid = m.group(2).strip()
            prompt = m.group(3).strip()
            draft = m.group(4).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType("NARRATIVE_DRAFT")'
                    f'.resourceType("narrative").resourceId({nid})'
                    f'.details(Map.of("alertId", {aid}, "prompt", {prompt}, "draft", {draft}))'
                    f'.build());')
        content = NARRATIVE_DRAFT_CALL.sub(replace_draft, content)

    if 'logNarrativeApproval' in block:
        def replace_approval(m):
            nid = m.group(1).strip()
            approver = m.group(2).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType("NARRATIVE_APPROVED")'
                    f'.resourceType("narrative").resourceId({nid})'
                    f'.principal({approver})'
                    f'.build());')
        content = NARRATIVE_APPROVAL_CALL.sub(replace_approval, content)

    if 'logEvidenceLinked' in block:
        def replace_evidence(m):
            cid = m.group(1).strip()
            eid = m.group(2).strip()
            src = m.group(3).strip()
            hash_val = m.group(4).strip()
            return (f'audit.emit(AuditEvent.builder()'
                    f'.eventType("EVIDENCE_LINKED")'
                    f'.resourceType("case").resourceId({cid})'
                    f'.details(Map.of("evidenceId", {eid}, "sourceSystem", {src}, "contentHash", {hash_val}))'
                    f'.build());')
        content = EVIDENCE_LINKED_CALL.sub(replace_evidence, content)

    content = add_imports(content)

    if content != original:
        filepath.write_text(content, encoding='utf-8')
        return True, f"MIGRATED: {filepath.name}"
    return False, f"NO CHANGE: {filepath.name}"


def main():
    files = []
    for path in sorted(ROOT.rglob('*.java')):
        if '/test/' in str(path):
            continue
        try:
            text = path.read_text(encoding='utf-8')
            if re.search(r'interface\s+(?:K07)?AuditPort\s*\{', text):
                files.append(path)
        except Exception:
            pass

    print(f"Found {len(files)} remaining files with inner AuditPort\n")
    migrated = 0
    for f in files:
        changed, msg = process_file(f)
        print(msg)
        if changed:
            migrated += 1
    print(f"\nMigrated: {migrated}/{len(files)}")


if __name__ == '__main__':
    main()
