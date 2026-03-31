import re
import os

# Revert: private mapXxx(arg: Record<string, unknown>): back to private mapXxx(arg: any):
# These are DB boundary mapper functions - any is appropriate here per spec
replacement = (r'\bprivate (\w+)\((\w+): Record<string, unknown>\):', r'private \1(\2: any):')

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

        content = re.sub(replacement[0], replacement[1], original)

        if content != original:
            diff_count = sum(1 for o, n in zip(original.splitlines(), content.splitlines()) if o != n)
            total_changes += diff_count
            files_changed += 1
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  Reverted {diff_count} lines: {fpath}")

print(f"Total: {total_changes} lines changed in {files_changed} files")
