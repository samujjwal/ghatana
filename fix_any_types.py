import re
import os

# Patterns for mechanical replacements
replacements = [
    (r'\bconst (\w+): any = \{', r'const \1: Record<string, unknown> = {'),
    (r'\.map\(\((\w+): any\) =>', r'.map((\1) =>'),
    (r'\.filter\(\((\w+): any\) =>', r'.filter((\1) =>'),
    (r'\.forEach\(\((\w+): any\) =>', r'.forEach((\1) =>'),
    (r'\.find\(\((\w+): any\) =>', r'.find((\1) =>'),
    (r'\.findIndex\(\((\w+): any\) =>', r'.findIndex((\1) =>'),
    (r'\.some\(\((\w+): any\) =>', r'.some((\1) =>'),
    (r'\.every\(\((\w+): any\) =>', r'.every((\1) =>'),
    (r'\((\w+): any, (\w+): any\) =>', r'(\1, \2) =>'),
    (r'\bprivate (\w+)\((\w+): any\):', r'private \1(\2: Record<string, unknown>):'),
    (r'catch \((\w+): any\)', r'catch (\1: unknown)'),
    (r'(_ignored\w*): any\b', r'\1: unknown'),
    (r'Promise<any>', 'Promise<unknown>'),
    (r'Map<string, any>', r'Map<string, Record<string, unknown>>'),
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
            content = re.sub(pattern, repl, content)

        if content != original:
            diff_count = sum(1 for o, n in zip(original.splitlines(), content.splitlines()) if o != n)
            total_changes += diff_count
            files_changed += 1
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  Fixed {diff_count} lines: {fpath}")

print(f"Total: {total_changes} lines changed in {files_changed} files")
