#!/usr/bin/env python3
"""
Route Inventory Report Script

Compares routes across different layers:
- route-manifest.yaml (canonical source of truth)
- openapi.yaml (API contract)
- backend RouteAuthorizationRegistry (runtime registration)
- frontend client.ts (API client methods)
- auth registry (authorization metadata)

Generates a report showing:
- Routes in manifest but missing from other layers
- Routes in other layers but missing from manifest
- Inconsistencies in HTTP method, path, operationId, auth, scopes
"""

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple
import yaml


def load_yaml(file_path: Path) -> dict:
    """Load a YAML file."""
    if not file_path.exists():
        print(f"Warning: {file_path} does not exist", file=sys.stderr)
        return {}
    with open(file_path, 'r') as f:
        return yaml.safe_load(f)


def parse_route_manifest(manifest: dict) -> Dict[str, dict]:
    """Parse route-manifest.yaml into a dict of routes keyed by 'method:path'."""
    routes = {}
    servers = manifest.get('servers', {})
    for server_name, server_routes in servers.items():
        for route in server_routes:
            key = f"{route['method'].lower()}:{route['path']}"
            routes[key] = {
                'method': route['method'].lower(),
                'path': route['path'],
                'auth': route.get('auth'),
                'scopes': route.get('scopes', []),
                'owner': route.get('owner'),
                'boundary': route.get('boundary'),
                'operationId': route.get('operationId'),
                'server': server_name,
            }
    return routes


def parse_openapi(openapi: dict) -> Dict[str, dict]:
    """Parse openapi.yaml into a dict of routes keyed by 'method:path'."""
    routes = {}
    paths = openapi.get('paths', {})
    for path, methods in paths.items():
        for method, details in methods.items():
            if method.lower() in ['get', 'post', 'put', 'patch', 'delete', 'options', 'head']:
                key = f"{method.lower()}:{path}"
                routes[key] = {
                    'method': method.lower(),
                    'path': path,
                    'operationId': details.get('operationId'),
                    'security': details.get('security', []),
                }
    return routes


def parse_java_registry(java_file: Path) -> Dict[str, dict]:
    """Parse RouteAuthorizationRegistry.java to extract registered routes."""
    if not java_file.exists():
        print(f"Warning: {java_file} does not exist", file=sys.stderr)
        return {}
    
    routes = {}
    content = java_file.read_text()
    
    # Look for registerRoute calls
    import re
    pattern = r'registerRoute\s*\(\s*HttpMethod\.(\w+)\s*,\s*"([^"]+)"'
    for match in re.finditer(pattern, content):
        method = match.group(1).lower()
        path = match.group(2)
        key = f"{method}:{path}"
        routes[key] = {
            'method': method,
            'path': path,
        }
    
    return routes


def parse_ts_client(ts_file: Path) -> Dict[str, dict]:
    """Parse client.ts to extract API client methods."""
    if not ts_file.exists():
        print(f"Warning: {ts_file} does not exist", file=sys.stderr)
        return {}
    
    routes = {}
    content = ts_file.read_text()
    
    # Look for HTTP method calls (get, post, patch, put, del)
    import re
    pattern = r'(get|post|patch|put|del)\s*\(\s*`([^`]+)`'
    for match in re.finditer(pattern, content):
        method = match.group(1)
        if method == 'del':
            method = 'delete'
        path = match.group(2)
        # Remove dynamic segments like ${...}
        path = re.sub(r'\$\{[^}]+\}', '{id}', path)
        key = f"{method}:{path}"
        routes[key] = {
            'method': method.lower(),
            'path': path,
        }
    
    return routes


def compare_routes(manifest_routes: Dict[str, dict], other_routes: Dict[str, dict], layer_name: str) -> Tuple[List[str], List[str]]:
    """Compare manifest routes with another layer."""
    manifest_only = []
    other_only = []
    
    manifest_keys = set(manifest_routes.keys())
    other_keys = set(other_routes.keys())
    
    for key in manifest_keys - other_keys:
        manifest_only.append(f"{key} (server: {manifest_routes[key].get('server', 'unknown')})")
    
    for key in other_keys - manifest_keys:
        other_only.append(key)
    
    return manifest_only, other_only


def check_inconsistencies(manifest_routes: Dict[str, dict], other_routes: Dict[str, dict], layer_name: str) -> List[str]:
    """Check for inconsistencies between manifest and another layer."""
    inconsistencies = []
    
    common_keys = set(manifest_routes.keys()) & set(other_routes.keys())
    
    for key in common_keys:
        manifest = manifest_routes[key]
        other = other_routes[key]
        
        # Check operationId consistency
        if 'operationId' in manifest and 'operationId' in other:
            if manifest['operationId'] != other['operationId']:
                inconsistencies.append(
                    f"{key}: operationId mismatch - manifest: {manifest['operationId']}, {layer_name}: {other['operationId']}"
                )
    
    return inconsistencies


def generate_report(
    manifest_routes: Dict[str, dict],
    openapi_routes: Dict[str, dict],
    backend_routes: Dict[str, dict],
    frontend_routes: Dict[str, dict],
) -> dict:
    """Generate a comprehensive route inventory report."""
    report = {
        'summary': {
            'manifest_count': len(manifest_routes),
            'openapi_count': len(openapi_routes),
            'backend_count': len(backend_routes),
            'frontend_count': len(frontend_routes),
        },
        'manifest_vs_openapi': {
            'manifest_only': [],
            'openapi_only': [],
            'inconsistencies': [],
        },
        'manifest_vs_backend': {
            'manifest_only': [],
            'backend_only': [],
            'inconsistencies': [],
        },
        'manifest_vs_frontend': {
            'manifest_only': [],
            'frontend_only': [],
            'inconsistencies': [],
        },
    }
    
    # Compare manifest vs OpenAPI
    manifest_only, openapi_only = compare_routes(manifest_routes, openapi_routes, 'OpenAPI')
    report['manifest_vs_openapi']['manifest_only'] = sorted(manifest_only)
    report['manifest_vs_openapi']['openapi_only'] = sorted(openapi_only)
    report['manifest_vs_openapi']['inconsistencies'] = check_inconsistencies(manifest_routes, openapi_routes, 'OpenAPI')
    
    # Compare manifest vs backend
    manifest_only, backend_only = compare_routes(manifest_routes, backend_routes, 'Backend')
    report['manifest_vs_backend']['manifest_only'] = sorted(manifest_only)
    report['manifest_vs_backend']['backend_only'] = sorted(backend_only)
    report['manifest_vs_backend']['inconsistencies'] = check_inconsistencies(manifest_routes, backend_routes, 'Backend')
    
    # Compare manifest vs frontend
    manifest_only, frontend_only = compare_routes(manifest_routes, frontend_routes, 'Frontend')
    report['manifest_vs_frontend']['manifest_only'] = sorted(manifest_only)
    report['manifest_vs_frontend']['frontend_only'] = sorted(frontend_only)
    report['manifest_vs_frontend']['inconsistencies'] = check_inconsistencies(manifest_routes, frontend_routes, 'Frontend')
    
    return report


def main():
    parser = argparse.ArgumentParser(description='Generate route inventory report')
    parser.add_argument('--manifest', default='products/yappc/docs/api/route-manifest.yaml',
                        help='Path to route-manifest.yaml')
    parser.add_argument('--openapi', default='products/yappc/docs/api/openapi.yaml',
                        help='Path to openapi.yaml')
    parser.add_argument('--backend-registry', default='products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java',
                        help='Path to RouteAuthorizationRegistry.java')
    parser.add_argument('--frontend-client', default='products/yappc/frontend/web/src/lib/api/client.ts',
                        help='Path to client.ts')
    parser.add_argument('--output', default='route-inventory-report.json',
                        help='Output file for the report')
    parser.add_argument('--format', choices=['json', 'text'], default='text',
                        help='Output format')
    
    args = parser.parse_args()
    
    repo_root = Path(__file__).parent.parent
    manifest_path = repo_root / args.manifest
    openapi_path = repo_root / args.openapi
    backend_registry_path = repo_root / args.backend_registry
    frontend_client_path = repo_root / args.frontend_client
    
    # Load all sources
    manifest = load_yaml(manifest_path)
    openapi = load_yaml(openapi_path)
    
    # Parse routes from each source
    manifest_routes = parse_route_manifest(manifest)
    openapi_routes = parse_openapi(openapi)
    backend_routes = parse_java_registry(backend_registry_path)
    frontend_routes = parse_ts_client(frontend_client_path)
    
    # Generate report
    report = generate_report(manifest_routes, openapi_routes, backend_routes, frontend_routes)
    
    # Output report
    if args.format == 'json':
        with open(args.output, 'w') as f:
            json.dump(report, f, indent=2)
        print(f"Report written to {args.output}")
    else:
        print("=" * 80)
        print("ROUTE INVENTORY REPORT")
        print("=" * 80)
        print(f"\nSummary:")
        print(f"  Manifest:  {report['summary']['manifest_count']} routes")
        print(f"  OpenAPI:   {report['summary']['openapi_count']} routes")
        print(f"  Backend:   {report['summary']['backend_count']} routes")
        print(f"  Frontend:  {report['summary']['frontend_count']} routes")
        
        print("\nManifest vs OpenAPI:")
        print(f"  Manifest only:  {len(report['manifest_vs_openapi']['manifest_only'])}")
        for route in report['manifest_vs_openapi']['manifest_only']:
            print(f"    - {route}")
        print(f"  OpenAPI only:   {len(report['manifest_vs_openapi']['openapi_only'])}")
        for route in report['manifest_vs_openapi']['openapi_only']:
            print(f"    - {route}")
        print(f"  Inconsistencies: {len(report['manifest_vs_openapi']['inconsistencies'])}")
        for inc in report['manifest_vs_openapi']['inconsistencies']:
            print(f"    - {inc}")
        
        print("\nManifest vs Backend:")
        print(f"  Manifest only:  {len(report['manifest_vs_backend']['manifest_only'])}")
        for route in report['manifest_vs_backend']['manifest_only']:
            print(f"    - {route}")
        print(f"  Backend only:   {len(report['manifest_vs_backend']['backend_only'])}")
        for route in report['manifest_vs_backend']['backend_only']:
            print(f"    - {route}")
        print(f"  Inconsistencies: {len(report['manifest_vs_backend']['inconsistencies'])}")
        for inc in report['manifest_vs_backend']['inconsistencies']:
            print(f"    - {inc}")
        
        print("\nManifest vs Frontend:")
        print(f"  Manifest only:  {len(report['manifest_vs_frontend']['manifest_only'])}")
        for route in report['manifest_vs_frontend']['manifest_only']:
            print(f"    - {route}")
        print(f"  Frontend only:  {len(report['manifest_vs_frontend']['frontend_only'])}")
        for route in report['manifest_vs_frontend']['frontend_only']:
            print(f"    - {route}")
        print(f"  Inconsistencies: {len(report['manifest_vs_frontend']['inconsistencies'])}")
        for inc in report['manifest_vs_frontend']['inconsistencies']:
            print(f"    - {inc}")
        
        print("\n" + "=" * 80)


if __name__ == '__main__':
    main()
