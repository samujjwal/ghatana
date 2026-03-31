import re
import os

# Round 2: More targeted patterns
replacements = [
    # function params named 'row', 'record', 'item', 'data' → Record<string, unknown>
    (r'\bfunction (\w+)\((\w+): any\):', r'function \1(\2: Record<string, unknown>):'),
    (r'\bfunction (\w+)\((\w+): any, (\w+): any\):', r'function \1(\2: Record<string, unknown>, \3: Record<string, unknown>):'),
    # Private/protected methods with (arg: any) → already done in round 1 for 'private'
    # Also do protected and public
    (r'\bprotected (\w+)\((\w+): any\):', r'protected \1(\2: Record<string, unknown>):'),
    # async function params
    (r'\basync (\w+)\((\w+): any\):', r'async \1(\2: Record<string, unknown>):'),
    # const result: any = await fetch → unknown
    (r'\bconst (\w+): any = await (\w+\.)?json\(\)', r'const \1: unknown = await \2json()'),
    (r'\bconst (\w+): any = await response\.json\(\)', r'const \1: unknown = await response.json()'),
    (r'\bconst (\w+): any = await res\.json\(\)', r'const \1: unknown = await res.json()'),
    # let events: any[] → unknown[]
    (r'\blet (\w+): any\[\]', r'let \1: unknown[]'),
    (r'\bconst (\w+): any\[\]', r'const \1: unknown[]'),
    # : any[] return types on private functions → : unknown[]
    # ): any[] { → ): unknown[] {
    (r'\): any\[\] \{', r'): unknown[] {'),
    # Promise<any[]> → Promise<unknown[]>
    (r'Promise<any\[\]>', r'Promise<unknown[]>'),
    # action: any → action: Record<string, unknown>
    # Only in function parameter position
    # interface prop: any → prop: unknown (for optional and required)
    # These are too risky to do globally — skip
    # .map((e: any, index: number) => → .map((e, index) =>
    (r'\.map\(\((\w+): any, (\w+): \w+\) =>', r'.map((\1, \2) =>'),
    # .forEach((x: any, y: number) => → .forEach((x, y) =>
    (r'\.forEach\(\((\w+): any, (\w+): \w+\) =>', r'.forEach((\1, \2) =>'),
    # states.forEach((state: any, _clientId: any) =>
    (r'\.forEach\(\((\w+): any, (\w+): any\) =>', r'.forEach((\1, \2) =>'),
    # (manifest: any) => → (manifest: unknown) =>
    # Only for non-map/filter callbacks: function args
    # Reduce: (acc: any, item) or (acc, item: any) staying
    (r'\((\w+): any\) => \{', r'(\1: unknown) => {'),
    (r'\((\w+): any\) =>', r'(\1: unknown) =>'),
    # ): any; in interfaces → ): unknown;
    (r'\): any;$', r'): unknown;'),
    # : any; in interface properties → : unknown;
    (r'(\s+\w+\??): any;$', r'\1: unknown;'),
]

tutorputor_dir = 'products/tutorputor'
extensions = ('.ts', '.tsx')

total_changes = 0
files_changed = 0

for root, dirs, files in os.walk(tutorputor_dir):
    dirs[:] = [d for d in dirs if d not in {'dist', 'node_modules', 'generated', 'build', '__tests__'}]

    skip_this = any(s in root for s in ['dist', 'node_modules', 'generated', 'build'])
    if skip_this:
        continue

    for fname in files:
        if not any(fname.endswith(ext) for ext in extensions):
            continue
        if '.test.' in fname or '.spec.' in fname:
            continue

        fpath = os.path.join(root, fname)
        with open(fpath, 'r', encoding='utf-8') as f:
            original = f.read()

        content = original
        for pattern, repl in replacements:
            content = re.sub(pattern, repl, content, flags=re.MULTILINE)

        if content != original:
            diff_count = sum(1 for o, n in zip(original.splitlines(), content.splitlines()) if o != n)
            total_changes += diff_count
            files_changed += 1
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  Fixed {diff_count} lines: {fpath}")

print(f"Total: {total_changes} lines changed in {files_changed} files")
