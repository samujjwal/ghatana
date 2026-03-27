#!/usr/bin/env python3
"""
YAPPC Module Migration Script

Migrates files from monolithic modules to focused domain modules.
Handles both agent and scaffold migrations with intelligent categorization.
"""

import os
import shutil
import re
from pathlib import Path
from typing import Dict, List, Tuple

# Configuration
YAPPC_ROOT = Path(__file__).parent.parent
DRY_RUN = False

# Agent categorization patterns
AGENT_CATEGORIES = {
    'testing': [
        'Test', 'Qa', 'Quality', 'Validate', 'Verify', 'Coverage',
        'E2e', 'Integration', 'Unit', 'Smoke', 'Benchmark', 'Load', 'Stress'
    ],
    'architecture': [
        'Architect', 'Design', 'Pattern', 'Structure', 'Model', 'Doc',
        'Diagram', 'Blueprint', 'Plan', 'Spec', 'System', 'Component',
        'Module', 'Service', 'Cloud', 'Security', 'Performance', 'Scale'
    ],
    'code': [
        'Code', 'Implement', 'Refactor', 'Generate', 'Review', 'Debug',
        'Optimize', 'Format', 'Lint', 'Style', 'React', 'Java', 'Python',
        'TypeScript', 'Frontend', 'Backend', 'Api', 'Db', 'Database', 'Query'
    ]
}

# Scaffold categorization patterns
SCAFFOLD_CATEGORIES = {
    'templates': [
        'Template', 'Parser', 'Render', 'Loader', 'Reader',
        'Mustache', 'Handlebars', 'Velocity', 'Freemarker', 'Layout', 'Format'
    ],
    'generators': [
        'Generator', 'Builder', 'Creator', 'Factory', 'Emitter',
        'Java', 'TypeScript', 'Python', 'React', 'Spring', 'Source', 'Class'
    ],
    'engine': [
        'Engine', 'Orchestrat', 'Coordinat', 'Workflow', 'Pipeline',
        'Manager', 'Service', 'Controller', 'Handler', 'Processor', 'Executor'
    ]
}

def categorize_file(filename: str, categories: Dict[str, List[str]]) -> str:
    """Categorize a file based on its name."""
    # Check each category in priority order
    for category, patterns in categories.items():
        for pattern in patterns:
            if pattern.lower() in filename.lower():
                return category
    # Default to first category
    return list(categories.keys())[-1]

def update_package_declaration(file_path: Path, old_package: str, new_package: str):
    """Update package declaration in a Java file."""
    if not file_path.exists():
        return
    
    content = file_path.read_text()
    updated = re.sub(
        rf'package {re.escape(old_package)}([;\s])',
        f'package {new_package}\\1',
        content
    )
    
    if not DRY_RUN:
        file_path.write_text(updated)

def update_imports(root_dir: Path, old_package: str, new_packages: Dict[str, str]):
    """Update import statements across all Java files."""
    for java_file in root_dir.rglob('*.java'):
        if not java_file.is_file():
            continue
        
        content = java_file.read_text()
        modified = False
        
        for category, new_package in new_packages.items():
            pattern = rf'import {re.escape(old_package)}\.([^;]+);'
            if re.search(pattern, content):
                content = re.sub(pattern, f'import {new_package}.\\1;', content)
                modified = True
        
        if modified and not DRY_RUN:
            java_file.write_text(content)

def migrate_agents():
    """Migrate agent specialist files."""
    print("🚀 Migrating Agent Files")
    print("=" * 50)
    
    source_dir = YAPPC_ROOT / 'core/agents/specialists/src/main/java/com/ghatana/yappc/agent/specialists'
    source_test_dir = YAPPC_ROOT / 'core/agents/specialists/src/test/java/com/ghatana/yappc/agent/specialists'
    
    destinations = {
        'code': YAPPC_ROOT / 'core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code',
        'architecture': YAPPC_ROOT / 'core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture',
        'testing': YAPPC_ROOT / 'core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing'
    }
    
    test_destinations = {
        'code': YAPPC_ROOT / 'core/agents/code-specialists/src/test/java/com/ghatana/yappc/agents/code',
        'architecture': YAPPC_ROOT / 'core/agents/architecture-specialists/src/test/java/com/ghatana/yappc/agents/architecture',
        'testing': YAPPC_ROOT / 'core/agents/testing-specialists/src/test/java/com/ghatana/yappc/agents/testing'
    }
    
    counts = {'code': 0, 'architecture': 0, 'testing': 0}
    
    # Migrate main source files
    if source_dir.exists():
        for java_file in source_dir.glob('*.java'):
            category = categorize_file(java_file.name, AGENT_CATEGORIES)
            dest_dir = destinations[category]
            
            print(f"  {'📝' if category == 'code' else '🏛️' if category == 'architecture' else '🧪'} {java_file.name} → {category}-specialists")
            
            if not DRY_RUN:
                dest_dir.mkdir(parents=True, exist_ok=True)
                shutil.move(str(java_file), str(dest_dir / java_file.name))
                
                # Update package declaration
                new_file = dest_dir / java_file.name
                update_package_declaration(
                    new_file,
                    'com.ghatana.yappc.agent.specialists',
                    f'com.ghatana.yappc.agents.{category}'
                )
            
            counts[category] += 1
    
    # Migrate test files
    if source_test_dir.exists():
        for java_file in source_test_dir.glob('*.java'):
            category = categorize_file(java_file.name, AGENT_CATEGORIES)
            dest_dir = test_destinations[category]
            
            if not DRY_RUN:
                dest_dir.mkdir(parents=True, exist_ok=True)
                shutil.move(str(java_file), str(dest_dir / java_file.name))
                
                # Update package declaration
                new_file = dest_dir / java_file.name
                update_package_declaration(
                    new_file,
                    'com.ghatana.yappc.agent.specialists',
                    f'com.ghatana.yappc.agents.{category}'
                )
    
    print(f"\n📊 Agent Migration Summary:")
    print(f"  Code Specialists: {counts['code']} files")
    print(f"  Architecture Specialists: {counts['architecture']} files")
    print(f"  Testing Specialists: {counts['testing']} files")
    print(f"  Total: {sum(counts.values())} files\n")
    
    return counts

def migrate_scaffold():
    """Migrate scaffold files."""
    print("🚀 Migrating Scaffold Files")
    print("=" * 50)
    
    source_dir = YAPPC_ROOT / 'core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold'
    
    destinations = {
        'engine': YAPPC_ROOT / 'core/scaffold/engine/src/main/java/com/ghatana/yappc/scaffold/engine',
        'generators': YAPPC_ROOT / 'core/scaffold/generators/src/main/java/com/ghatana/yappc/scaffold/generators',
        'templates': YAPPC_ROOT / 'core/scaffold/templates/src/main/java/com/ghatana/yappc/scaffold/templates'
    }
    
    counts = {'engine': 0, 'generators': 0, 'templates': 0}
    
    if source_dir.exists():
        for java_file in source_dir.glob('*.java'):
            category = categorize_file(java_file.name, SCAFFOLD_CATEGORIES)
            dest_dir = destinations[category]
            
            print(f"  {'⚙️' if category == 'engine' else '🏭' if category == 'generators' else '📄'} {java_file.name} → {category}")
            
            if not DRY_RUN:
                dest_dir.mkdir(parents=True, exist_ok=True)
                shutil.move(str(java_file), str(dest_dir / java_file.name))
                
                # Update package declaration
                new_file = dest_dir / java_file.name
                update_package_declaration(
                    new_file,
                    'com.ghatana.yappc.scaffold',
                    f'com.ghatana.yappc.scaffold.{category}'
                )
            
            counts[category] += 1
    
    print(f"\n📊 Scaffold Migration Summary:")
    print(f"  Engine: {counts['engine']} files")
    print(f"  Generators: {counts['generators']} files")
    print(f"  Templates: {counts['templates']} files")
    print(f"  Total: {sum(counts.values())} files\n")
    
    return counts

def main():
    """Main migration function."""
    global DRY_RUN
    
    import sys
    if '--dry-run' in sys.argv:
        DRY_RUN = True
        print("🔍 Running in DRY RUN mode - no files will be moved\n")
    
    # Migrate agents
    agent_counts = migrate_agents()
    
    # Migrate scaffold
    scaffold_counts = migrate_scaffold()
    
    print("✅ Migration Complete!")
    print("\n📋 Next Steps:")
    print("  1. Run: ./gradlew clean build")
    print("  2. Run: ./gradlew test")
    print("  3. Update CORE_ARCHITECTURE.md")
    print("  4. Remove old module directories if empty")
    
    if DRY_RUN:
        print("\n⚠️  This was a DRY RUN - no files were moved")

if __name__ == '__main__':
    main()
