import os

root_dir = 'src/generated/prisma'
target = '@prisma/client/runtime/library'
replacement = '@prisma/client/runtime/client'

print(f"Scanning {root_dir}...")
for subdir, dirs, files in os.walk(root_dir):
    for file in files:
        if file.endswith('.ts'):
            filepath = os.path.join(subdir, file)
            try:
                with open(filepath, 'r') as f:
                    content = f.read()
                
                if target in content:
                    print(f'Fixing {filepath}')
                    new_content = content.replace(target, replacement)
                    with open(filepath, 'w') as f:
                        f.write(new_content)
            except Exception as e:
                print(f"Error processing {filepath}: {e}")
print("Done.")