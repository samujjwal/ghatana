#!/usr/bin/env python3
"""
Fix Cross-Module Import Statements

After migrating agent files, some agents reference Input/Output classes
that were migrated to different modules. This script adds the necessary
import statements for cross-module references.
"""

import os
import re
from pathlib import Path
from typing import Set, Dict, List

YAPPC_ROOT = Path("/Users/samujjwal/Development/ghatana/products/yappc")

# Map of class names to their new modules
CLASS_TO_MODULE = {}

def scan_all_classes():
    """Scan all migrated files to build a map of class names to modules."""
    modules = {
        'code': YAPPC_ROOT / 'core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code',
        'architecture': YAPPC_ROOT / 'core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture',
        'testing': YAPPC_ROOT / 'core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing'
    }
    
    for module_name, module_path in modules.items():
        if not module_path.exists():
            continue
        
        for java_file in module_path.glob('*.java'):
            class_name = java_file.stem
            CLASS_TO_MODULE[class_name] = module_name
    
    print(f"📊 Scanned {len(CLASS_TO_MODULE)} classes across 3 modules")

def get_referenced_classes(file_path: Path) -> Set[str]:
    """Extract all class names referenced in a Java file."""
    content = file_path.read_text()
    
    # Find class references (simple heuristic)
    # Look for capitalized words that could be class names
    pattern = r'\b([A-Z][a-zA-Z0-9]*(?:Input|Output|Agent|Specialist))\b'
    matches = re.findall(pattern, content)
    
    return set(matches)

def get_current_package(file_path: Path) -> str:
    """Extract the package declaration from a Java file."""
    content = file_path.read_text()
    match = re.search(r'package\s+([\w.]+);', content)
    return match.group(1) if match else ""

def get_existing_imports(file_path: Path) -> Set[str]:
    """Extract existing import statements."""
    content = file_path.read_text()
    imports = re.findall(r'import\s+([\w.]+);', content)
    return set(imports)

def add_missing_imports(file_path: Path, current_module: str):
    """Add missing import statements for cross-module references."""
    content = file_path.read_text()
    current_package = get_current_package(file_path)
    existing_imports = get_existing_imports(file_path)
    referenced_classes = get_referenced_classes(file_path)
    
    # Determine which imports are needed
    needed_imports = []
    
    for class_name in referenced_classes:
        if class_name not in CLASS_TO_MODULE:
            continue
        
        target_module = CLASS_TO_MODULE[class_name]
        
        # Skip if same module (no import needed)
        if target_module == current_module:
            continue
        
        # Build the import statement
        if target_module == 'code':
            import_stmt = f'com.ghatana.yappc.agents.code.{class_name}'
        elif target_module == 'architecture':
            import_stmt = f'com.ghatana.yappc.agents.architecture.{class_name}'
        elif target_module == 'testing':
            import_stmt = f'com.ghatana.yappc.agents.testing.{class_name}'
        else:
            continue
        
        # Check if import already exists
        if import_stmt not in existing_imports:
            needed_imports.append(import_stmt)
    
    if not needed_imports:
        return False
    
    # Add imports after package declaration
    package_match = re.search(r'(package\s+[\w.]+;)', content)
    if not package_match:
        return False
    
    package_end = package_match.end()
    
    # Find where to insert (after package, before first import or class)
    import_section_match = re.search(r'\nimport\s+', content[package_end:])
    
    if import_section_match:
        # Insert before existing imports
        insert_pos = package_end + import_section_match.start()
    else:
        # Insert after package declaration
        insert_pos = package_end
    
    # Build import statements
    import_lines = '\n'.join(f'import {imp};' for imp in sorted(needed_imports))
    
    # Insert imports
    new_content = content[:insert_pos] + '\n' + import_lines + content[insert_pos:]
    
    file_path.write_text(new_content)
    return True

def main():
    """Main function."""
    print("🔧 Fixing cross-module import statements...")
    print("")
    
    # Scan all classes
    scan_all_classes()
    print("")
    
    modules = {
        'code': YAPPC_ROOT / 'core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code',
        'architecture': YAPPC_ROOT / 'core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture',
        'testing': YAPPC_ROOT / 'core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing'
    }
    
    fixed_count = 0
    
    for module_name, module_path in modules.items():
        if not module_path.exists():
            continue
        
        print(f"📝 Processing {module_name}-specialists...")
        
        for java_file in module_path.glob('*.java'):
            if add_missing_imports(java_file, module_name):
                fixed_count += 1
                print(f"  ✅ Fixed {java_file.name}")
    
    print("")
    print(f"✅ Fixed {fixed_count} files with missing imports!")
    print("")
    print("📋 Next: Run build verification")

if __name__ == '__main__':
    main()
