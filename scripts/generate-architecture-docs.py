#!/usr/bin/env python3
"""
Generate architecture documentation and dependency graphs for Ghatana monorepo.

Produces:
  - Architecture overview (modules, boundaries, responsibilities)
  - Dependency matrix (CSV format)
  - Module dependency graph (DOT/Graphviz format)
  - Capability matrix (products vs features)

Usage:
  python3 generate-architecture-docs.py [--format {md,html,dot,csv}] [--output-dir docs/generated]
"""

import os
import json
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple
from dataclasses import dataclass
from collections import defaultdict


@dataclass
class Module:
    """Represents a build module with metadata."""
    name: str
    path: Path
    type: str  # 'kernel', 'plugin', 'product', 'platform', 'service'
    dependencies: List[str]
    description: str = ""
    owner: str = ""


class ArchitectureAnalyzer:
    """Analyzes Ghatana monorepo structure and generates documentation."""

    def __init__(self, repo_root: Path):
        self.repo_root = repo_root
        self.modules: Dict[str, Module] = {}
        self.products: Dict[str, List[str]] = {}  # product -> capabilities
        self.plugins: List[str] = []
        self.kernel_modules: List[str] = []

    def scan_gradle_modules(self):
        """Scan build.gradle.kts and settings.gradle.kts to find modules."""
        settings_file = self.repo_root / "settings.gradle.kts"
        
        if not settings_file.exists():
            print(f"Warning: settings.gradle.kts not found at {settings_file}", file=sys.stderr)
            return

        with open(settings_file, 'r') as f:
            content = f.read()

        # Extract include() statements
        import re
        includes = re.findall(r"include\(['\"]([^'\"]+)['\"]\)", content)

        for include in includes:
            module_path = self.repo_root / include.replace(':', '/')
            build_file = module_path / "build.gradle.kts"

            if build_file.exists():
                self._analyze_module(include, module_path, build_file)

    def _analyze_module(self, module_name: str, module_path: Path, build_file: Path):
        """Analyze a single module's build.gradle.kts."""
        with open(build_file, 'r') as f:
            content = f.read()

        # Categorize module
        module_type = self._categorize_module(module_name, module_path)
        
        # Extract dependencies
        dependencies = self._extract_dependencies(content)

        # Determine owner
        owner = self._extract_owner(module_path)

        # Create module record
        self.modules[module_name] = Module(
            name=module_name,
            path=module_path,
            type=module_type,
            dependencies=dependencies,
            owner=owner
        )

        # Track by category
        if module_type == 'kernel':
            self.kernel_modules.append(module_name)
        elif module_type == 'plugin':
            self.plugins.append(module_name)
        elif module_type == 'product':
            self._extract_product_capabilities(module_name, module_path)

    def _categorize_module(self, name: str, path: Path) -> str:
        """Determine module type from path."""
        if 'kernel' in name:
            return 'kernel'
        elif 'plugin' in name:
            return 'plugin'
        elif 'products' in str(path):
            return 'product'
        elif 'platform' in name:
            return 'platform'
        else:
            return 'service'

    def _extract_dependencies(self, gradle_content: str) -> List[str]:
        """Extract inter-project dependencies from build.gradle.kts."""
        import re
        # Match patterns like implementation(project(":platform:java:core"))
        deps = re.findall(r"project\(['\"]([^'\"]+)['\"]\)", gradle_content)
        return deps

    def _extract_owner(self, module_path: Path) -> str:
        """Try to extract module owner from README or metadata."""
        readme = module_path / "README.md"
        if readme.exists():
            with open(readme, 'r') as f:
                for line in f:
                    if 'owner' in line.lower() or 'maintained by' in line.lower():
                        return line.strip()
        return "unspecified"

    def _extract_product_capabilities(self, product_name: str, product_path: Path):
        """Extract capabilities from product manifest."""
        manifest = product_path / "domain-pack-manifest.yaml"
        if manifest.exists():
            import yaml
            try:
                with open(manifest, 'r') as f:
                    data = yaml.safe_load(f)
                    capabilities = data.get('capabilities', [])
                    self.products[product_name] = [c.get('name', '') for c in capabilities]
            except:
                self.products[product_name] = []

    def generate_dependency_matrix(self) -> str:
        """Generate CSV dependency matrix."""
        modules = sorted(self.modules.keys())
        
        lines = ["module"] + modules
        result = [",".join(lines)]

        for module in modules:
            deps = self.modules[module].dependencies
            row = [module]
            for other in modules:
                row.append("1" if other in deps else "0")
            result.append(",".join(row))

        return "\n".join(result)

    def generate_dependency_graph_dot(self) -> str:
        """Generate Graphviz DOT format dependency graph."""
        lines = [
            'digraph GhatanaArchitecture {',
            '  graph [rankdir=LR];',
            '  node [shape=box, style=filled];',
        ]

        # Color nodes by type
        color_map = {
            'kernel': 'lightblue',
            'plugin': 'lightgreen',
            'product': 'lightyellow',
            'platform': 'lightcyan',
            'service': 'lightgray',
        }

        # Add nodes
        for name, module in self.modules.items():
            color = color_map.get(module.type, 'white')
            lines.append(f'  "{name}" [fillcolor={color}, label="{name}"];')

        # Add edges
        for name, module in self.modules.items():
            for dep in module.dependencies:
                lines.append(f'  "{dep}" -> "{name}";')

        lines.append('}')
        return "\n".join(lines)

    def generate_markdown_overview(self) -> str:
        """Generate Markdown architecture overview."""
        lines = [
            '# Ghatana Architecture Overview',
            '',
            f'**Generated:** {__import__("datetime").datetime.now().isoformat()}',
            '',
            '## Module Summary',
            f'- **Total Modules:** {len(self.modules)}',
            f'- **Kernel Modules:** {len(self.kernel_modules)}',
            f'- **Plugins:** {len(self.plugins)}',
            f'- **Products:** {len(self.products)}',
            '',
            '## Kernel Modules',
            '',
        ]

        for name in sorted(self.kernel_modules):
            module = self.modules[name]
            lines.append(f'### {name}')
            lines.append(f'- **Owner:** {module.owner}')
            lines.append(f'- **Type:** {module.type}')
            if module.dependencies:
                lines.append(f'- **Dependencies:** {", ".join(module.dependencies)}')
            lines.append('')

        lines.extend(['## Plugins', ''])

        for name in sorted(self.plugins):
            module = self.modules[name]
            lines.append(f'### {name}')
            lines.append(f'- **Owner:** {module.owner}')
            if module.dependencies:
                lines.append(f'- **Dependencies:** {", ".join(module.dependencies)}')
            lines.append('')

        lines.extend(['## Products', ''])

        for product, capabilities in sorted(self.products.items()):
            lines.append(f'### {product}')
            lines.append(f'- **Capabilities:** {", ".join(capabilities) if capabilities else "N/A"}')
            if product in self.modules:
                deps = self.modules[product].dependencies
                if deps:
                    lines.append(f'- **Uses:** {", ".join(deps)}')
            lines.append('')

        lines.extend([
            '## Architecture Rules',
            '',
            '### Boundary Enforcement',
            '',
            '- Kernel modules must not depend on products',
            '- Plugins must not depend on other plugin implementations',
            '- Products may depend on kernel + plugins',
            '- All async code uses ActiveJ Promise (not CompletableFuture)',
            '',
            '### Plugin Responsibilities',
            '',
            '- **Audit Trail:** Records all events for compliance audit',
            '- **Billing Ledger:** Double-entry accounting for transactions',
            '- **Compliance:** Policy evaluation and enforcement',
            '- **Consent:** Patient/user consent management',
            '- **Fraud Detection:** Anomaly detection for suspicious patterns',
            '- **Risk Management:** Risk scoring and evaluation',
            '- **Human Approval:** Workflow with human review gates',
            '',
        ])

        return "\n".join(lines)

    def generate_capability_matrix(self) -> str:
        """Generate product capability matrix."""
        all_capabilities = set()
        for caps in self.products.values():
            all_capabilities.update(caps)

        capabilities = sorted(all_capabilities)

        lines = ['| Product | ' + ' | '.join(capabilities) + ' |']
        lines.append('|' + '---|' * (len(capabilities) + 1))

        for product in sorted(self.products.keys()):
            product_caps = self.products[product]
            row = f'| {product} |'
            for cap in capabilities:
                row += ' ✓ |' if cap in product_caps else ' - |'
            lines.append(row)

        return "\n".join(lines)


def main():
    repo_root = Path(__file__).parent.parent
    
    print(f"Analyzing Ghatana architecture from: {repo_root}")

    analyzer = ArchitectureAnalyzer(repo_root)
    analyzer.scan_gradle_modules()

    # Generate outputs
    print("\nGenerating documentation...")

    output_dir = repo_root / "docs" / "generated"
    output_dir.mkdir(parents=True, exist_ok=True)

    # 1. Dependency Matrix (CSV)
    matrix = analyzer.generate_dependency_matrix()
    matrix_file = output_dir / "dependency-matrix.csv"
    with open(matrix_file, 'w') as f:
        f.write(matrix)
    print(f"✓ Dependency matrix: {matrix_file}")

    # 2. Dependency Graph (DOT)
    dot_graph = analyzer.generate_dependency_graph_dot()
    dot_file = output_dir / "dependency-graph.dot"
    with open(dot_file, 'w') as f:
        f.write(dot_graph)
    print(f"✓ Dependency graph: {dot_file}")

    # 3. Architecture Overview (Markdown)
    overview = analyzer.generate_markdown_overview()
    overview_file = output_dir / "ARCHITECTURE.md"
    with open(overview_file, 'w') as f:
        f.write(overview)
    print(f"✓ Architecture overview: {overview_file}")

    # 4. Capability Matrix
    capability_matrix = analyzer.generate_capability_matrix()
    capability_file = output_dir / "CAPABILITY_MATRIX.md"
    with open(capability_file, 'w') as f:
        f.write("# Product Capability Matrix\n\n")
        f.write(capability_matrix)
    print(f"✓ Capability matrix: {capability_file}")

    print(f"\n✓ All documentation generated to: {output_dir}")


if __name__ == '__main__':
    main()
