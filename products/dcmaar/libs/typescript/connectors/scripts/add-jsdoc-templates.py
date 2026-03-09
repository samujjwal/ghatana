#!/usr/bin/env python3

"""
JSDoc Template Generator for DCMAAR Connectors

This script automatically adds JSDoc comment templates to TypeScript files
that are missing documentation. It analyzes the code structure and generates
appropriate templates for classes, methods, and properties.

Usage:
    python3 add-jsdoc-templates.py [--dry-run] [--file path/to/file.ts]
"""

import re
import sys
import argparse
from pathlib import Path
from typing import List, Dict, Tuple

class JSDocGenerator:
    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        
    def generate_class_doc(self, class_name: str, extends: str = None) -> str:
        """Generate JSDoc template for a class"""
        extends_text = f"\n * @extends {extends}" if extends else ""
        return f"""/**
 * [TODO: Add one-line description of {class_name}]
 * 
 * [TODO: Add detailed description explaining purpose, use cases, and key features]
 * 
 * **Key Features:**
 * - [TODO: Feature 1]
 * - [TODO: Feature 2]
 * 
 * **When to use:**
 * - [TODO: Use case 1]
 * - [TODO: Use case 2]
 * 
 * @class {class_name}{extends_text}
 * 
 * @example
 * ```typescript
 * // TODO: Add basic usage example
 * const instance = new {class_name}(config);
 * ```
 */"""

    def generate_method_doc(self, method_name: str, params: List[str], 
                           is_async: bool, is_private: bool) -> str:
        """Generate JSDoc template for a method"""
        param_docs = "\n".join([f" * @param {{TODO}} {p} - [TODO: Description]" 
                                for p in params])
        returns_doc = " * @returns {Promise<TODO>} [TODO: Description]" if is_async else " * @returns {TODO} [TODO: Description]"
        
        visibility = "private" if is_private else "public"
        
        return f"""/**
 * [TODO: Add one-line description of what {method_name} does]
 * 
 * [TODO: Add detailed description explaining behavior and side effects]
 * 
 * **How it works:**
 * 1. [TODO: Step 1]
 * 2. [TODO: Step 2]
 * 
 * **Why this method exists:**
 * - [TODO: Reason 1]
 * - [TODO: Reason 2]
 * 
{param_docs}
 * 
{returns_doc}
 * 
 * @throws {{Error}} [TODO: When error occurs]
 * 
 * @example
 * ```typescript
 * // TODO: Add usage example
 * const result = await instance.{method_name}({', '.join(params)});
 * ```
 */"""

    def generate_property_doc(self, prop_name: str, prop_type: str = "TODO") -> str:
        """Generate JSDoc template for a property"""
        return f"""/**
 * [TODO: Description of what {prop_name} represents]
 * 
 * **Why this field exists:**
 * - [TODO: Reason 1]
 * 
 * @type {{{prop_type}}}
 * @readonly
 */"""

    def parse_method_signature(self, line: str) -> Tuple[str, List[str], bool, bool]:
        """Parse method signature to extract name, parameters, async flag, and visibility"""
        is_async = 'async' in line
        is_private = 'private' in line or line.strip().startswith('private')
        
        # Extract method name
        match = re.search(r'(async\s+)?(\w+)\s*\(', line)
        if not match:
            return None, [], False, False
            
        method_name = match.group(2)
        
        # Extract parameters
        param_match = re.search(r'\((.*?)\)', line)
        params = []
        if param_match:
            param_str = param_match.group(1)
            if param_str.strip():
                # Simple parameter extraction (doesn't handle complex types)
                params = [p.split(':')[0].strip() for p in param_str.split(',')]
        
        return method_name, params, is_async, is_private

    def needs_documentation(self, lines: List[str], index: int) -> bool:
        """Check if the line at index needs documentation"""
        # Look backwards for existing JSDoc
        for i in range(max(0, index - 10), index):
            if '/**' in lines[i]:
                # Check if this JSDoc is for our line
                for j in range(i, min(index + 1, len(lines))):
                    if '*/' in lines[j]:
                        # JSDoc ends before our line, so we need docs
                        if j < index - 1:
                            return True
                        return False
                return False
        return True

    def process_file(self, file_path: Path) -> bool:
        """Process a single TypeScript file"""
        print(f"Processing: {file_path}")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        new_lines = []
        i = 0
        changes_made = False
        
        while i < len(lines):
            line = lines[i]
            
            # Check for class declaration
            if re.match(r'\s*export\s+class\s+\w+', line):
                if self.needs_documentation(lines, i):
                    match = re.search(r'class\s+(\w+)(?:\s+extends\s+(\w+))?', line)
                    if match:
                        class_name = match.group(1)
                        extends = match.group(2)
                        doc = self.generate_class_doc(class_name, extends)
                        new_lines.append(doc + '\n')
                        changes_made = True
            
            # Check for method declaration
            elif re.search(r'(public|private|protected)?\s*(async\s+)?\w+\s*\([^)]*\)\s*[:{]', line):
                if self.needs_documentation(lines, i):
                    method_name, params, is_async, is_private = self.parse_method_signature(line)
                    if method_name and method_name not in ['constructor']:
                        doc = self.generate_method_doc(method_name, params, is_async, is_private)
                        new_lines.append(doc + '\n')
                        changes_made = True
            
            # Check for property declaration
            elif re.match(r'\s*(public|private|protected|readonly)\s+\w+\s*:', line):
                if self.needs_documentation(lines, i):
                    match = re.search(r'(public|private|protected|readonly)\s+(\w+)\s*:\s*([^;=]+)', line)
                    if match:
                        prop_name = match.group(2)
                        prop_type = match.group(3).strip()
                        doc = self.generate_property_doc(prop_name, prop_type)
                        new_lines.append(doc + '\n')
                        changes_made = True
            
            new_lines.append(line)
            i += 1
        
        if changes_made and not self.dry_run:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.writelines(new_lines)
            print(f"  ✅ Added documentation templates")
        elif changes_made:
            print(f"  ℹ️  Would add documentation templates (dry-run)")
        else:
            print(f"  ✓ No changes needed")
        
        return changes_made

    def process_directory(self, directory: Path):
        """Process all TypeScript files in a directory"""
        ts_files = list(directory.rglob('*.ts'))
        ts_files = [f for f in ts_files if not f.name.endswith('.test.ts') 
                    and not f.name.endswith('.spec.ts')
                    and not f.name.endswith('.d.ts')]
        
        print(f"\nFound {len(ts_files)} TypeScript files to process\n")
        
        total_changes = 0
        for file_path in ts_files:
            if self.process_file(file_path):
                total_changes += 1
        
        print(f"\n{'Would modify' if self.dry_run else 'Modified'} {total_changes} file(s)")

def main():
    parser = argparse.ArgumentParser(description='Add JSDoc templates to TypeScript files')
    parser.add_argument('--dry-run', action='store_true', 
                       help='Show what would be changed without modifying files')
    parser.add_argument('--file', type=str, 
                       help='Process a single file instead of the entire src directory')
    parser.add_argument('--dir', type=str, default='../src',
                       help='Directory to process (default: ../src)')
    
    args = parser.parse_args()
    
    generator = JSDocGenerator(dry_run=args.dry_run)
    
    if args.file:
        file_path = Path(args.file)
        if not file_path.exists():
            print(f"Error: File not found: {file_path}")
            sys.exit(1)
        generator.process_file(file_path)
    else:
        src_dir = Path(__file__).parent / args.dir
        if not src_dir.exists():
            print(f"Error: Directory not found: {src_dir}")
            sys.exit(1)
        generator.process_directory(src_dir)

if __name__ == '__main__':
    main()
