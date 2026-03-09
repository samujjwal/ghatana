#!/usr/bin/env python3
"""Find all files matched by java.exclude patterns in yappc:platform build.gradle.kts"""
import re, os

with open('products/yappc/platform/build.gradle.kts') as f:
    content = f.read()

patterns = re.findall(r'java\.exclude\("(.*?)"\)', content)
base = 'products/yappc/platform/src/main/java'
files = set()

for p in patterns:
    if p.startswith('**/'):
        fname = p[3:]
        if fname.startswith('products/'):
            # directory glob like products/yappc/domain/**/*.java
            subdir = fname.replace('/**/*.java', '')
            for root, dirs, fnames in os.walk(base):
                for fn in fnames:
                    full = os.path.join(root, fn)
                    if subdir in full and fn.endswith('.java'):
                        files.add(full)
        elif fname.startswith('*'):
            # wildcard like *Test.java, *Assessment.java
            suffix = fname[1:]  # e.g. Test.java, Assessment.java
            for root, dirs, fnames in os.walk(base):
                for fn in fnames:
                    if fn.endswith(suffix):
                        files.add(os.path.join(root, fn))
        else:
            # simple filename like SomeClass.java
            for root, dirs, fnames in os.walk(base):
                for fn in fnames:
                    if fn == fname:
                        files.add(os.path.join(root, fn))
    else:
        # path-specific like com/ghatana/.../File.java
        f_path = os.path.join(base, p)
        if os.path.exists(f_path):
            files.add(f_path)

for f in sorted(files):
    print(f)
print(f'---TOTAL: {len(files)}')
