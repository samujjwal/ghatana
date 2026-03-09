#!/usr/bin/env python3
"""
Documentation Copilot - Automated documentation generation
Implements Capability 6: Documentation Copilot from Horizontal Slice AI Plan #4
"""

import os
import re
import json
import yaml
import argparse
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass
import subprocess
import tempfile

@dataclass
class ProtoMessage:
    """Represents a Protocol Buffer message"""
    name: str
    fields: List[Dict[str, Any]]
    description: str
    package: str
    file_path: str

@dataclass
class APIEndpoint:
    """Represents an API endpoint"""
    method: str
    path: str
    description: str
    request_type: Optional[str]
    response_type: Optional[str]
    parameters: List[Dict[str, Any]]
    examples: List[Dict[str, Any]]

@dataclass
class EventSequence:
    """Represents a canonical event sequence"""
    name: str
    description: str
    steps: List[Dict[str, Any]]
    prerequisites: List[str]
    expected_outcomes: List[str]

class ProtoAnalyzer:
    """Analyzes Protocol Buffer definitions"""
    
    def __init__(self, proto_dir: Path):
        self.proto_dir = proto_dir
        
    def analyze_protos(self) -> List[ProtoMessage]:
        """Analyze all .proto files and extract message definitions"""
        messages = []
        
        for proto_file in self.proto_dir.rglob("*.proto"):
            messages.extend(self._parse_proto_file(proto_file))
        
        return messages
    
    def _parse_proto_file(self, proto_path: Path) -> List[ProtoMessage]:
        """Parse a single .proto file"""
        messages = []
        
        try:
            with open(proto_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Extract package name
            package_match = re.search(r'package\s+([^;]+);', content)
            package = package_match.group(1) if package_match else "unknown"
            
            # Extract messages
            message_pattern = r'message\s+(\w+)\s*\{([^}]+)\}'
            for match in re.finditer(message_pattern, content, re.MULTILINE | re.DOTALL):
                message_name = match.group(1)
                message_body = match.group(2)
                
                # Extract fields
                fields = self._parse_message_fields(message_body)
                
                # Extract comment description
                description = self._extract_message_description(content, message_name)
                
                messages.append(ProtoMessage(
                    name=message_name,
                    fields=fields,
                    description=description,
                    package=package,
                    file_path=str(proto_path)
                ))
                
        except Exception as e:
            print(f"Error parsing {proto_path}: {e}")
        
        return messages
    
    def _parse_message_fields(self, message_body: str) -> List[Dict[str, Any]]:
        """Parse message fields"""
        fields = []
        
        # Field pattern: [repeated] type name = number [options];
        field_pattern = r'(repeated\s+)?(\w+)\s+(\w+)\s*=\s*(\d+)(?:\s*\[([^\]]+)\])?;'
        
        for match in re.finditer(field_pattern, message_body):
            repeated = match.group(1) is not None
            field_type = match.group(2)
            field_name = match.group(3)
            field_number = int(match.group(4))
            options = match.group(5) or ""
            
            fields.append({
                "name": field_name,
                "type": field_type,
                "number": field_number,
                "repeated": repeated,
                "options": options
            })
        
        return fields
    
    def _extract_message_description(self, content: str, message_name: str) -> str:
        """Extract description from comments above message"""
        lines = content.split('\n')
        
        for i, line in enumerate(lines):
            if f'message {message_name}' in line:
                # Look backwards for comments
                description_lines = []
                j = i - 1
                while j >= 0 and (lines[j].strip().startswith('//') or lines[j].strip() == ''):
                    if lines[j].strip().startswith('//'):
                        description_lines.insert(0, lines[j].strip()[2:].strip())
                    j -= 1
                
                return ' '.join(description_lines)
        
        return f"Protocol buffer message for {message_name}"

class APIAnalyzer:
    """Analyzes API endpoints from source code"""
    
    def __init__(self, source_dirs: List[Path]):
        self.source_dirs = source_dirs
    
    def analyze_endpoints(self) -> List[APIEndpoint]:
        """Analyze source code to find API endpoints"""
        endpoints = []
        
        for source_dir in self.source_dirs:
            # Look for Go HTTP handlers
            endpoints.extend(self._analyze_go_endpoints(source_dir))
            
            # Look for TypeScript/React API calls
            endpoints.extend(self._analyze_typescript_endpoints(source_dir))
        
        return endpoints
    
    def _analyze_go_endpoints(self, source_dir: Path) -> List[APIEndpoint]:
        """Analyze Go HTTP handlers"""
        endpoints = []
        
        for go_file in source_dir.rglob("*.go"):
            try:
                with open(go_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Look for HTTP handler patterns
                handler_pattern = r'func\s+(\w+)\s*\([^)]*http\.ResponseWriter[^)]*\)\s*\{'
                route_pattern = r'\.Handle(?:Func)?\s*\(\s*"([^"]+)"\s*,\s*(\w+)'
                
                handlers = re.findall(handler_pattern, content)
                routes = re.findall(route_pattern, content)
                
                for route_path, handler_name in routes:
                    if handler_name in [h for h in handlers]:
                        # Extract method from handler name or route
                        method = self._infer_http_method(handler_name, content)
                        
                        endpoints.append(APIEndpoint(
                            method=method,
                            path=route_path,
                            description=f"HTTP handler: {handler_name}",
                            request_type=None,
                            response_type=None,
                            parameters=[],
                            examples=[]
                        ))
                        
            except Exception as e:
                print(f"Error analyzing {go_file}: {e}")
        
        return endpoints
    
    def _analyze_typescript_endpoints(self, source_dir: Path) -> List[APIEndpoint]:
        """Analyze TypeScript API calls"""
        endpoints = []
        
        for ts_file in source_dir.rglob("*.ts"):
            try:
                with open(ts_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Look for fetch() calls
                fetch_pattern = r'fetch\s*\(\s*[`\'"]([^`\'"]+)[`\'"](?:\s*,\s*\{[^}]*method:\s*[`\'"](\w+)[`\'"][^}]*\})?'
                
                for match in re.finditer(fetch_pattern, content):
                    path = match.group(1)
                    method = match.group(2) or "GET"
                    
                    endpoints.append(APIEndpoint(
                        method=method.upper(),
                        path=path,
                        description=f"Frontend API call from {ts_file.name}",
                        request_type=None,
                        response_type=None,
                        parameters=[],
                        examples=[]
                    ))
                    
            except Exception as e:
                print(f"Error analyzing {ts_file}: {e}")
        
        return endpoints
    
    def _infer_http_method(self, handler_name: str, content: str) -> str:
        """Infer HTTP method from handler name or content"""
        handler_lower = handler_name.lower()
        
        if any(word in handler_lower for word in ['get', 'list', 'fetch', 'read']):
            return "GET"
        elif any(word in handler_lower for word in ['post', 'create', 'add']):
            return "POST"
        elif any(word in handler_lower for word in ['put', 'update', 'modify']):
            return "PUT"
        elif any(word in handler_lower for word in ['delete', 'remove']):
            return "DELETE"
        else:
            return "GET"  # Default

class EventSequenceExtractor:
    """Extracts canonical event sequences from tests and documentation"""
    
    def __init__(self, test_dirs: List[Path]):
        self.test_dirs = test_dirs
    
    def extract_sequences(self) -> List[EventSequence]:
        """Extract event sequences from test files"""
        sequences = []
        
        for test_dir in self.test_dirs:
            sequences.extend(self._extract_from_test_files(test_dir))
        
        return sequences
    
    def _extract_from_test_files(self, test_dir: Path) -> List[EventSequence]:
        """Extract sequences from test files"""
        sequences = []
        
        for test_file in test_dir.rglob("*test*"):
            if test_file.suffix in ['.go', '.rs', '.ts', '.py']:
                sequences.extend(self._parse_test_file(test_file))
        
        return sequences
    
    def _parse_test_file(self, test_path: Path) -> List[EventSequence]:
        """Parse individual test file for sequences"""
        sequences = []
        
        try:
            with open(test_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Look for test functions with descriptive names
            if test_path.suffix == '.go':
                test_pattern = r'func\s+Test(\w+)\s*\([^)]*\)\s*\{'
            elif test_path.suffix == '.rs':
                test_pattern = r'#\[test\]\s*fn\s+test_(\w+)\s*\(\)\s*\{'
            elif test_path.suffix == '.py':
                test_pattern = r'def\s+test_(\w+)\s*\([^)]*\):'
            else:
                return sequences
            
            for match in re.finditer(test_pattern, content):
                test_name = match.group(1)
                
                # Extract test body to find steps
                steps = self._extract_test_steps(content, match.start())
                
                if steps:
                    sequences.append(EventSequence(
                        name=test_name,
                        description=f"Test scenario: {test_name}",
                        steps=steps,
                        prerequisites=[],
                        expected_outcomes=[]
                    ))
                    
        except Exception as e:
            print(f"Error parsing test file {test_path}: {e}")
        
        return sequences
    
    def _extract_test_steps(self, content: str, start_pos: int) -> List[Dict[str, Any]]:
        """Extract steps from test function body"""
        steps = []
        
        # Simple heuristic: look for comments that describe steps
        lines = content[start_pos:].split('\n')
        step_number = 1
        
        for line in lines:
            stripped = line.strip()
            
            # Stop at next function
            if stripped.startswith('func ') or stripped.startswith('def ') or stripped.startswith('fn '):
                break
            
            # Look for step comments
            if stripped.startswith('//') and any(word in stripped.lower() for word in ['step', 'then', 'when', 'given']):
                step_description = stripped[2:].strip()
                steps.append({
                    "number": step_number,
                    "action": "execute",
                    "description": step_description
                })
                step_number += 1
        
        return steps

class MarkdownGenerator:
    """Generates Markdown documentation"""
    
    def generate_api_docs(self, endpoints: List[APIEndpoint]) -> str:
        """Generate API documentation in Markdown"""
        docs = ["# API Documentation\n"]
        
        # Group endpoints by path prefix
        grouped = {}
        for endpoint in endpoints:
            prefix = endpoint.path.split('/')[1] if '/' in endpoint.path else 'root'
            if prefix not in grouped:
                grouped[prefix] = []
            grouped[prefix].append(endpoint)
        
        for prefix, group in grouped.items():
            docs.append(f"## {prefix.title()} API\n")
            
            for endpoint in group:
                docs.append(f"### {endpoint.method} {endpoint.path}\n")
                docs.append(f"{endpoint.description}\n")
                
                if endpoint.parameters:
                    docs.append("**Parameters:**\n")
                    for param in endpoint.parameters:
                        docs.append(f"- `{param['name']}` ({param['type']}): {param.get('description', '')}\n")
                
                if endpoint.examples:
                    docs.append("**Example:**\n")
                    docs.append("```bash")
                    docs.append(f"curl -X {endpoint.method} {endpoint.path}")
                    docs.append("```\n")
                
                docs.append("---\n")
        
        return '\n'.join(docs)
    
    def generate_proto_docs(self, messages: List[ProtoMessage]) -> str:
        """Generate Protocol Buffer documentation"""
        docs = ["# Protocol Buffer Messages\n"]
        
        # Group by package
        packages = {}
        for message in messages:
            if message.package not in packages:
                packages[message.package] = []
            packages[message.package].append(message)
        
        for package, msgs in packages.items():
            docs.append(f"## Package: {package}\n")
            
            for msg in msgs:
                docs.append(f"### {msg.name}\n")
                docs.append(f"{msg.description}\n")
                
                if msg.fields:
                    docs.append("**Fields:**\n")
                    docs.append("| Field | Type | Description |")
                    docs.append("|-------|------|-------------|")
                    
                    for field in msg.fields:
                        field_type = f"repeated {field['type']}" if field['repeated'] else field['type']
                        docs.append(f"| {field['name']} | {field_type} | - |")
                
                docs.append(f"\n**Source:** `{msg.file_path}`\n")
                docs.append("---\n")
        
        return '\n'.join(docs)
    
    def generate_sequence_docs(self, sequences: List[EventSequence]) -> str:
        """Generate event sequence documentation"""
        docs = ["# Event Sequences\n"]
        
        for seq in sequences:
            docs.append(f"## {seq.name}\n")
            docs.append(f"{seq.description}\n")
            
            if seq.prerequisites:
                docs.append("**Prerequisites:**\n")
                for prereq in seq.prerequisites:
                    docs.append(f"- {prereq}\n")
            
            if seq.steps:
                docs.append("**Steps:**\n")
                for step in seq.steps:
                    docs.append(f"{step['number']}. {step['description']}\n")
            
            if seq.expected_outcomes:
                docs.append("**Expected Outcomes:**\n")
                for outcome in seq.expected_outcomes:
                    docs.append(f"- {outcome}\n")
            
            docs.append("---\n")
        
        return '\n'.join(docs)

class JupyterGenerator:
    """Generates Jupyter notebooks"""
    
    def generate_api_notebook(self, endpoints: List[APIEndpoint]) -> Dict[str, Any]:
        """Generate Jupyter notebook for API exploration"""
        cells = []
        
        # Title cell
        cells.append({
            "cell_type": "markdown",
            "metadata": {},
            "source": ["# DCMAAR API Explorer\n", "\n", "Interactive notebook for exploring DCMAAR APIs."]
        })
        
        # Setup cell
        cells.append({
            "cell_type": "code",
            "execution_count": None,
            "metadata": {},
            "outputs": [],
            "source": [
                "import requests\n",
                "import json\n",
                "import pandas as pd\n",
                "\n",
                "# Configuration\n",
                "BASE_URL = 'http://localhost:8080'\n",
                "API_KEY = 'your-api-key-here'\n",
                "\n",
                "headers = {'Authorization': f'Bearer {API_KEY}'}"
            ]
        })
        
        # Generate cells for each endpoint
        for endpoint in endpoints[:5]:  # Limit to first 5 for demo
            # Description cell
            cells.append({
                "cell_type": "markdown",
                "metadata": {},
                "source": [f"## {endpoint.method} {endpoint.path}\n\n{endpoint.description}"]
            })
            
            # Code cell
            if endpoint.method == "GET":
                code = f"""# {endpoint.method} {endpoint.path}
response = requests.get(f'{{BASE_URL}}{endpoint.path}', headers=headers)
print(f'Status: {{response.status_code}}')
if response.status_code == 200:
    data = response.json()
    print(json.dumps(data, indent=2))
else:
    print(response.text)"""
            else:
                code = f"""# {endpoint.method} {endpoint.path}
payload = {{
    # Add your request data here
}}

response = requests.{endpoint.method.lower()}(
    f'{{BASE_URL}}{endpoint.path}', 
    json=payload, 
    headers=headers
)
print(f'Status: {{response.status_code}}')
print(response.text)"""
            
            cells.append({
                "cell_type": "code",
                "execution_count": None,
                "metadata": {},
                "outputs": [],
                "source": code.split('\n')
            })
        
        return {
            "cells": cells,
            "metadata": {
                "kernelspec": {
                    "display_name": "Python 3",
                    "language": "python",
                    "name": "python3"
                },
                "language_info": {
                    "name": "python",
                    "version": "3.11.0"
                }
            },
            "nbformat": 4,
            "nbformat_minor": 4
        }

class DocumentationCopilot:
    """Main documentation copilot class"""
    
    def __init__(self, workspace_root: Path):
        self.workspace_root = workspace_root
        self.proto_analyzer = ProtoAnalyzer(workspace_root / "contracts" / "proto")
        self.api_analyzer = APIAnalyzer([
            workspace_root / "services" / "server",
            workspace_root / "services" / "desktop" / "src"
        ])
        self.sequence_extractor = EventSequenceExtractor([
            workspace_root / "services" / "agent-rs" / "tests",
            workspace_root / "services" / "server" / "tests",
            workspace_root / "tests"
        ])
        self.markdown_generator = MarkdownGenerator()
        self.jupyter_generator = JupyterGenerator()
    
    def generate_documentation(self, output_dir: Path):
        """Generate all documentation"""
        output_dir.mkdir(parents=True, exist_ok=True)
        
        print("Analyzing Protocol Buffers...")
        proto_messages = self.proto_analyzer.analyze_protos()
        
        print("Analyzing API endpoints...")
        api_endpoints = self.api_analyzer.analyze_endpoints()
        
        print("Extracting event sequences...")
        event_sequences = self.sequence_extractor.extract_sequences()
        
        print("Generating documentation...")
        
        # Generate Markdown docs
        api_docs = self.markdown_generator.generate_api_docs(api_endpoints)
        with open(output_dir / "api.md", 'w') as f:
            f.write(api_docs)
        
        proto_docs = self.markdown_generator.generate_proto_docs(proto_messages)
        with open(output_dir / "protocols.md", 'w') as f:
            f.write(proto_docs)
        
        sequence_docs = self.markdown_generator.generate_sequence_docs(event_sequences)
        with open(output_dir / "sequences.md", 'w') as f:
            f.write(sequence_docs)
        
        # Generate Jupyter notebooks
        api_notebook = self.jupyter_generator.generate_api_notebook(api_endpoints)
        with open(output_dir / "api_explorer.ipynb", 'w') as f:
            json.dump(api_notebook, f, indent=2)
        
        # Generate summary
        summary = {
            "generated_at": "2024-12-01T12:00:00Z",
            "proto_messages": len(proto_messages),
            "api_endpoints": len(api_endpoints),
            "event_sequences": len(event_sequences),
            "files_generated": [
                "api.md",
                "protocols.md", 
                "sequences.md",
                "api_explorer.ipynb"
            ]
        }
        
        with open(output_dir / "summary.json", 'w') as f:
            json.dump(summary, f, indent=2)
        
        print(f"Documentation generated in {output_dir}")
        print(f"- {len(proto_messages)} Protocol Buffer messages")
        print(f"- {len(api_endpoints)} API endpoints")
        print(f"- {len(event_sequences)} event sequences")

def main():
    parser = argparse.ArgumentParser(description='Generate documentation from code and contracts')
    parser.add_argument('--workspace', type=Path, default=Path('.'),
                       help='Path to workspace root')
    parser.add_argument('--output', type=Path, default=Path('docs/ai'),
                       help='Output directory for generated docs')
    parser.add_argument('--format', choices=['markdown', 'jupyter', 'both'],
                       default='both', help='Output format')
    
    args = parser.parse_args()
    
    copilot = DocumentationCopilot(args.workspace)
    copilot.generate_documentation(args.output)

if __name__ == "__main__":
    main()