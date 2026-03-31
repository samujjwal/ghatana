import re
import os

# Round 4: Fix entity/data/config/manifest as any → as Record<string, unknown>
# And specific patterns
replacements = [
    # (x as any).prop → (x as Record<string, unknown>).prop
    # Be careful: only when followed by ) or when bound to a variable
    # const bond = entity as any; → const bond = entity as Record<string, unknown>;
    (r'\bconst (\w+) = (\w+) as any;', r'const \1 = \2 as Record<string, unknown>;'),
    # manifest.domainMetadata as any → as Record<string, unknown>
    (r'\.domainMetadata as any\b', r'.domainMetadata as Record<string, unknown>'),
    # (config as any)?. → (config as Record<string, unknown>)?.
    (r'\(config as any\)\?\.', r'(config as Record<string, unknown>)?.'),
    (r'\(data as any\)\.', r'(data as Record<string, unknown>).'),
    (r'\(entity as any\)\.([\w]+)', r'(entity as Record<string, unknown>).\1'),
    (r'\(energyProfile as any\)\.', r'(energyProfile as Record<string, unknown>).'),
    # (result as any)?.usage → (result as Record<string, unknown>)?.usage
    (r'\(result as any\)\?\.', r'(result as Record<string, unknown>)?.'),
    # (navigation as any).historyAction → (navigation as Record<string, unknown>).historyAction
    (r'\(navigation as any\)\.', r'(navigation as Record<string, unknown>).'),
    # property as any = value (CSS style access) → property as keyof CSSStyleDeclaration
    (r'\[property as any\]', r'[property as keyof CSSStyleDeclaration]'),
    # (e as any).value → (e as { value: unknown }).value
    (r'\(e as any\)\.value\b', r'(e as { value: unknown }).value'),
    # entities as any, → entities as unknown,
    (r'\bentities as any,', r'entities as unknown,'),
    (r'\bentities as any;', r'entities as unknown;'),
    # tenantId as any, userId as any, moduleId as any → remove cast (string is fine)
    # These are params being passed to a function expecting a branded type — use 'as unknown as BrandedType' or just pass directly
    # For now: x as any → x as unknown
    (r'\btenantId as any\b', r'tenantId as unknown'),
    (r'\buserId as any\b', r'userId as unknown'),
    (r'\bmoduleId as any\b', r'moduleId as unknown'),
    # } as any; at end of object → } as unknown;
    # (These are object literals being cast — need unknown as safe alternative)
    # Only do ^\s+} as any; (end of block)
    # finishReason: x as any → as string (it's typically a string enum)
    (r'finishReason: (\w[^\s,\n]+) as any\b', r'finishReason: \1 as string'),
    # domain: domain as any → domain: domain (if domain is already the right type, cast not needed)
    (r': domain as any\b', r': domain as unknown'),
    # providers as any[] → providers as unknown[]
    (r'\bproviders as any\b', r'providers as unknown'),
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
