import re
import os

# Round 3: Fix 'as any' patterns that are NOT Prisma-related
# Skip: (this.prisma as any), (app.prisma as any), ({ tutor: true } as any)
# Skip: (claim as any).*, (entity as any).* when accessing dot-notation (needs specific type)

replacements = [
    # response.json() as any → as unknown
    (r'await response\.json\(\) as any\b', r'await response.json() as unknown'),
    (r'await res\.json\(\) as any\b', r'await res.json() as unknown'),
    (r'\.json\(\) as any\b', r'.json() as unknown'),
    # request.body as any → as Record<string, unknown>
    (r'\brequest\.body as any\b', r'request.body as Record<string, unknown>'),
    # request.query as any → as Record<string, unknown>
    (r'\brequest\.query as any\b', r'request.query as Record<string, unknown>'),
    # request.params as any → as Record<string, unknown>
    (r'\brequest\.params as any\b', r'request.params as Record<string, unknown>'),
    # providers as any → as unknown[]  
    # More specific: } as any; at end of object literal → } as unknown (will likely cause TS errors but that's ok)
    # {} as any → {} as unknown
    (r'\{\} as any\b', r'{} as unknown'),
    # as any[] → as unknown[]
    (r' as any\[\]', r' as unknown[]'),
    # as unknown as any → as unknown (double cast chain cleanup)
    (r' as unknown as any\b', r' as unknown'),
    # "STRING" as any → "STRING" (remove unnecessary cast)
    (r'"([A-Z_]+)" as any\b', r'"\1"'),
    # Fastify: app.prisma as any — keep (it's correct for Fastify decoration)
    # const prisma = (request.server as FastifyInstance & { prisma: any }).prisma
    # → keep these (Fastify type decoration pattern)
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
