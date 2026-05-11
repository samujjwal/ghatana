#!/usr/bin/env python3
"""
Script to align OpenAPI operationIds with route-manifest.yaml.
The manifest is the source of truth.
"""

import re
import sys
from pathlib import Path
from typing import Dict, Tuple

def parse_manifest(manifest_path: Path) -> Dict[Tuple[str, str], str]:
    """Parse route-manifest.yaml and extract (method, path) -> operationId mappings."""
    routes = {}
    current_server = "unknown"
    current_route = {}
    in_route_entry = False
    
    with open(manifest_path) as f:
        for line in f:
            trimmed = line.strip()
            
            # Skip comments and empty lines
            if trimmed.startswith("#") or not trimmed:
                continue
            
            # Detect server section
            if trimmed.endswith(":") and not line.startswith(" ") and not trimmed.startswith("-"):
                current_server = trimmed.rstrip(":").strip()
                continue
            
            # Detect route entry start
            if trimmed.startswith("- "):
                if current_route and in_route_entry:
                    method = current_route.get("method", "").upper()
                    path = current_route.get("path", "")
                    operation_id = current_route.get("operationId", "")
                    if method and path and operation_id:
                        routes[(method, path)] = operation_id
                
                current_route = {}
                in_route_entry = True
                
                # Parse "- key: value" format
                parts = trimmed[2:].split(":", 1)
                if len(parts) == 2:
                    current_route[parts[0].strip()] = parts[1].strip()
                continue
            
            # Parse route fields
            if in_route_entry and line.startswith("  ") and ":" in line:
                parts = line.strip().split(":", 1)
                if len(parts) == 2:
                    current_route[parts[0].strip()] = parts[1].strip()
    
    # Don't forget the last route
    if current_route and in_route_entry:
        method = current_route.get("method", "").upper()
        path = current_route.get("path", "")
        operation_id = current_route.get("operationId", "")
        if method and path and operation_id:
            routes[(method, path)] = operation_id
    
    return routes

def update_openapi(openapi_path: Path, manifest_routes: Dict[Tuple[str, str], str]):
    """Update operationIds in openapi.yaml to match manifest."""
    with open(openapi_path) as f:
        lines = f.readlines()
    
    current_path = ""
    current_method = ""
    updated_lines = []
    changes_made = 0
    
    for line in lines:
        trimmed = line.strip()
        
        # Detect path definition
        if re.match(r'^/[^#].*:', trimmed):
            current_path = trimmed.rstrip(":")
            updated_lines.append(line)
            continue
        
        # Detect HTTP method
        if current_path and re.match(r'^(get|post|put|delete|patch|head|options):', trimmed):
            current_method = trimmed.rstrip(":").upper()
            updated_lines.append(line)
            continue
        
        # Detect operationId
        if current_path and current_method and trimmed.startswith("operationId:"):
            key = (current_method, current_path)
            if key in manifest_routes:
                new_operation_id = manifest_routes[key]
                old_operation_id = trimmed.split(":", 1)[1].strip()
                
                if new_operation_id != old_operation_id:
                    # Update the operationId
                    indent = len(line) - len(line.lstrip())
                    new_line = " " * indent + f"operationId: {new_operation_id}\n"
                    updated_lines.append(new_line)
                    changes_made += 1
                    print(f"Updated {current_method} {current_path}: '{old_operation_id}' -> '{new_operation_id}'")
                else:
                    updated_lines.append(line)
            else:
                updated_lines.append(line)
                print(f"WARNING: No manifest entry for {current_method} {current_path}")
            
            current_method = ""  # Reset after capturing
            continue
        
        updated_lines.append(line)
    
    if changes_made > 0:
        with open(openapi_path, "w") as f:
            f.writelines(updated_lines)
        print(f"\nTotal changes made: {changes_made}")
    else:
        print("No changes needed - operationIds already aligned")

if __name__ == "__main__":
    manifest_path = Path(__file__).parent.parent / "docs" / "api" / "route-manifest.yaml"
    openapi_path = Path(__file__).parent.parent / "docs" / "api" / "openapi.yaml"
    
    if not manifest_path.exists():
        print(f"Error: Manifest not found at {manifest_path}")
        sys.exit(1)
    
    if not openapi_path.exists():
        print(f"Error: OpenAPI spec not found at {openapi_path}")
        sys.exit(1)
    
    print("Parsing route-manifest.yaml...")
    manifest_routes = parse_manifest(manifest_path)
    print(f"Found {len(manifest_routes)} routes in manifest")
    
    print("\nUpdating openapi.yaml...")
    update_openapi(openapi_path, manifest_routes)
