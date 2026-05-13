#!/usr/bin/env python3
"""
Generate GeneratedRouteRegistry.java from route-manifest.yaml.

Usage: python scripts/generate-route-registry.py
"""

import sys
import re
from pathlib import Path

def to_upper(s):
    return s.upper() if s else None

def to_snake_case(s):
    if not s:
        return None
    # Convert camelCase to snake_case
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', s)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()

def format_scopes(scopes):
    if not scopes:
        return ""
    quoted = [f'"{s}"' for s in scopes]
    return ", ".join(quoted)

def generate_header():
    return """package com.ghatana.yappc.api.generated;

/**
 * AUTO-GENERATED - DO NOT EDIT
 * Generated from docs/api/route-manifest.yaml
 * Run: python scripts/generate-route-registry.py
 */
import com.ghatana.yappc.governance.route.AuthMode;
import com.ghatana.yappc.governance.route.Boundary;
import com.ghatana.yappc.governance.route.PrivacyClassification;
import com.ghatana.yappc.governance.route.RouteEntry;
import com.ghatana.yappc.governance.route.RouteManifest;
import java.util.List;
import java.util.Set;

public final class GeneratedRouteRegistry {
    private static final RouteManifest MANIFEST = new RouteManifest();
    
    static {
        initializeManifest();
    }
    
    private static void initializeManifest() {
"""

def generate_footer():
    return """    }
    
    public static RouteManifest getManifest() {
        return MANIFEST;
    }
}
"""

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
            
            # Detect server section (lines ending with : at start of line)
            if stripped.endswith(':') and not line.startswith(' ') and not line.startswith('-'):
                if current_route:
                    current_routes.append(current_route)
                    current_route = {}
                if current_server and current_routes:
                    routes_by_server[current_server] = current_routes
                current_server = stripped.rstrip(':')
                current_routes = []
                in_server_section = True
                in_route = False
                continue
            
            # Skip non-server sections (schema, examples, etc.)
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
                
                # Handle list fields (scopes)
                if key == 'scopes' and value:
                    if value.startswith('[') and value.endswith(']'):
                        value = value[1:-1]
                        scopes = [s.strip().strip('"\'') for s in value.split(',') if s.strip()]
                        current_route[key] = scopes
                    else:
                        current_route[key] = []
                else:
                    current_route[key] = value
    
        # Don't forget the last route
        if current_route:
            current_routes.append(current_route)
        
        # Don't forget the last server
        if current_server and current_routes:
            routes_by_server[current_server] = current_routes
    
    return routes_by_server

def generate_initializer(routes_by_server):
    sb = []
    
    for server, routes in routes_by_server.items():
        for route in routes:
            method = to_upper(route.get('method'))
            path = route.get('path')
            auth = to_upper(route.get('auth'))
            scopes = route.get('scopes', [])
            owner = route.get('owner')
            boundary = to_upper(route.get('boundary'))
            operation_id = route.get('operationId')
            
            # Default auditEventType to UPPER_SNAKE_CASE of operationId
            audit_event_type = route.get('auditEventType', to_snake_case(operation_id).upper())
            
            # Default privacyClassification
            if auth == 'PUBLIC':
                privacy = 'PUBLIC'
            else:
                privacy = route.get('privacyClassification', 'INTERNAL')
            privacy = to_upper(privacy)
            
            sb.append(f'''        MANIFEST.addRoute("{server}", new RouteEntry(
            "{method}",
            "{path}",
            AuthMode.{auth},
            Set.of({format_scopes(scopes)}),
            "{owner}",
            Boundary.{boundary},
            "{operation_id}",
            "{audit_event_type}",
            PrivacyClassification.{privacy}
        ));''')
    
    return '\n'.join(sb)

def main():
    script_dir = Path(__file__).parent
    manifest_file = script_dir.parent / 'docs' / 'api' / 'route-manifest.yaml'
    output_file = script_dir.parent / 'core' / 'yappc-services' / 'src' / 'generated' / 'java' / 'com' / 'ghatana' / 'yappc' / 'api' / 'generated' / 'GeneratedRouteRegistry.java'
    
    if not manifest_file.exists():
        print(f"ERROR: Manifest file not found: {manifest_file}")
        sys.exit(1)
    
    print(f"Generating route registry from: {manifest_file}")
    
    routes_by_server = parse_yaml_manifest(manifest_file)
    
    java_code = generate_header()
    java_code += generate_initializer(routes_by_server)
    java_code += generate_footer()
    
    # Ensure output directory exists
    output_file.parent.mkdir(parents=True, exist_ok=True)
    
    # Write generated file
    output_file.write_text(java_code)
    
    print(f"Generated route registry to: {output_file}")
    print(f"Total routes: {sum(len(routes) for routes in routes_by_server.values())}")

if __name__ == '__main__':
    main()
