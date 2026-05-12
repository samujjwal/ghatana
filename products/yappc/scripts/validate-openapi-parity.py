#!/usr/bin/env python3
"""
Validate OpenAPI ↔ route-manifest.yaml parity.

This script ensures every route in the manifest has a corresponding entry
in the OpenAPI specification with matching path, method, and operationId.

Usage: python scripts/validate-openapi-parity.py
"""

import sys
import re
from pathlib import Path
from typing import Dict, List, Tuple, Set

def parse_yaml_manifest(manifest_path):
    """Parse route-manifest.yaml and extract route entries."""
    routes_by_server = {}
    current_server = None
    current_routes = []
    
    with open(manifest_path, encoding='utf-8') as f:
        in_route = False
        current_route = {}
        in_server_section = False
        
        for line in f:
            stripped = line.strip()
            
            # Skip comments and empty lines
            if not stripped or stripped.startswith('#'):
                continue
            
            # Detect server section
            if stripped.endswith(':') and not line.startswith(' ') and not line.startswith('-'):
                if current_server and current_routes:
                    routes_by_server[current_server] = current_routes
                current_server = stripped.rstrip(':')
                current_routes = []
                in_server_section = True
                in_route = False
                continue
            
            # Skip non-server sections
            if not in_server_section:
                continue
            
            # Skip lines that don't look like route entries
            if not line.startswith('  -') and not line.startswith('    '):
                continue
            
            # Detect route entry start
            if line.startswith('  - method:'):
                if current_route:
                    current_routes.append(current_route)
                current_route = {'method': stripped.split(':', 1)[1].strip()}
                in_route = True
                continue
            
            # Parse route fields
            if in_route and line.startswith('    ') and ':' in line:
                key, value = line.strip().split(':', 1)
                key = key.strip()
                value = value.strip()
                
                if key == 'scopes' and value:
                    if value.startswith('[') and value.endswith(']'):
                        value = value[1:-1]
                        scopes = [s.strip().strip('"\'') for s in value.split(',') if s.strip()]
                        current_route[key] = scopes
                    else:
                        current_route[key] = []
                else:
                    current_route[key] = value
    
        if current_route:
            current_routes.append(current_route)
        
        if current_server and current_routes:
            routes_by_server[current_server] = current_routes
    
    return routes_by_server

def parse_openapi(openapi_path):
    """Parse openapi.yaml and extract route entries."""
    routes = {}
    current_path = None
    current_method = None
    current_operation_id = None
    
    with open(openapi_path, encoding='utf-8') as f:
        for line in f:
            stripped = line.strip()
            
            # Skip comments and empty lines
            if not stripped or stripped.startswith('#'):
                continue
            
            # Detect path definition
            if re.match(r'^/[^#].*:', stripped) and ':' in stripped:
                current_path = stripped.rstrip(':')
                continue
            
            # Detect HTTP method
            if current_path and re.match(r'^(get|post|put|delete|patch|head|options):', stripped):
                current_method = stripped.rstrip(':').upper()
                continue
            
            # Detect operationId
            if current_path and current_method and stripped.startswith('operationId:'):
                current_operation_id = stripped.split(':', 1)[1].strip()
                key = (current_path, current_method)
                routes[key] = current_operation_id
                current_operation_id = None
    
    return routes

def validate_parity(manifest_path, openapi_path):
    """Validate manifest ↔ OpenAPI parity."""
    errors = []
    warnings = []
    
    print("Parsing route-manifest.yaml...")
    manifest_routes_by_server = parse_yaml_manifest(manifest_path)
    manifest_routes = {}  # (path, method) -> route dict
    
    for server, routes in manifest_routes_by_server.items():
        print(f"  Found {len(routes)} routes in server: {server}")
        for route in routes:
            method = route.get('method', '').upper()
            path = route.get('path', '')
            operation_id = route.get('operationId', '')
            
            if not method or not path or not operation_id:
                errors.append(f"Route missing required fields: method={method}, path={path}, operationId={operation_id}")
                continue
            
            key = (path, method)
            if key in manifest_routes:
                errors.append(f"Duplicate route in manifest: {method} {path}")
            manifest_routes[key] = route
    
    print(f"\nTotal manifest routes: {len(manifest_routes)}")
    
    print("\nParsing openapi.yaml...")
    openapi_routes = parse_openapi(openapi_path)
    print(f"Total OpenAPI routes: {len(openapi_routes)}")
    
    # Check manifest routes in OpenAPI
    print("\nChecking manifest routes against OpenAPI...")
    for (path, method), route in manifest_routes.items():
        manifest_op_id = route.get('operationId', '')
        if (path, method) not in openapi_routes:
            errors.append(f"Manifest route not in OpenAPI: {method} {path} (operationId: {manifest_op_id})")
        else:
            openapi_op_id = openapi_routes[(path, method)]
            if manifest_op_id != openapi_op_id:
                errors.append(f"operationId mismatch for {method} {path}: manifest={manifest_op_id}, openapi={openapi_op_id}")
    
    # Check for orphaned OpenAPI routes
    print("\nChecking for orphaned OpenAPI routes...")
    for (path, method), openapi_op_id in openapi_routes.items():
        if (path, method) not in manifest_routes:
            warnings.append(f"OpenAPI route not in manifest: {method} {path} (operationId: {openapi_op_id})")
    
    # Check operation naming consistency
    print("\nChecking operationId naming consistency...")
    for (path, method), route in manifest_routes.items():
        operation_id = route.get('operationId', '')
        if not re.match(r'^[a-z][a-zA-Z0-9]*$', operation_id):
            errors.append(f"operationId not camelCase: {operation_id} for {method} {path}")
    
    # Check manifest validation rules
    print("\nChecking manifest validation rules...")
    for (path, method), route in manifest_routes.items():
        auth = route.get('auth', '').lower()
        scopes = route.get('scopes', [])
        boundary = route.get('boundary', '')
        privacy = route.get('privacyClassification', '')
        
        # Validate auth values
        if auth not in ['public', 'required', 'optional']:
            errors.append(f"Invalid auth value '{auth}' for {method} {path}")
        
        # Validate scopes based on auth
        if auth == 'public' and scopes:
            errors.append(f"Public route {method} {path} must have empty scopes, found: {scopes}")
        if auth == 'required' and not scopes:
            errors.append(f"Authenticated route {method} {path} must have non-empty scopes")
        
        # Validate scope format
        for scope in scopes:
            # Allow both resource:permission format and standalone 'admin'
            if not (re.match(r'^[a-z]+:(read|write|admin)$', scope) or scope == 'admin'):
                errors.append(f"Invalid scope format '{scope}' for {method} {path}")
        
        # Validate boundary values
        if boundary not in ['YAPPC', 'DATA_CLOUD_AEP']:
            errors.append(f"Invalid boundary '{boundary}' for {method} {path}")
        
        # Validate privacy classification
        if privacy and privacy not in ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED']:
            errors.append(f"Invalid privacyClassification '{privacy}' for {method} {path}")
        
        # RESTRICTED routes must have audit event
        if privacy == 'RESTRICTED':
            audit_event = route.get('auditEventType', '')
            if not audit_event:
                errors.append(f"RESTRICTED route {method} {path} must have auditEventType")
    
    return errors, warnings

def main():
    script_dir = Path(__file__).parent
    manifest_file = script_dir.parent / 'docs' / 'api' / 'route-manifest.yaml'
    openapi_file = script_dir.parent / 'docs' / 'api' / 'openapi.yaml'
    
    if not manifest_file.exists():
        print(f"ERROR: Manifest file not found: {manifest_file}")
        sys.exit(1)
    
    if not openapi_file.exists():
        print(f"ERROR: OpenAPI file not found: {openapi_file}")
        sys.exit(1)
    
    print(f"Validating parity between:")
    print(f"  Manifest: {manifest_file}")
    print(f"  OpenAPI:  {openapi_file}")
    print()
    
    errors, warnings = validate_parity(manifest_file, openapi_file)
    
    if warnings:
        print(f"\n⚠️  WARNINGS ({len(warnings)}):")
        for warning in warnings:
            print(f"  - {warning}")
    
    if errors:
        print(f"\n❌ ERRORS ({len(errors)}):")
        for error in errors:
            print(f"  - {error}")
        sys.exit(1)
    
    print("\n✅ OpenAPI ↔ manifest parity validation PASSED")
    sys.exit(0)

if __name__ == '__main__':
    main()
