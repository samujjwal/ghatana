#!/usr/bin/env python3
"""
Update finance domain build.gradle.kts files to include dependencies
from the migrated app-platform code.

Maps:
  :domain-packs:X → :products:finance:domains:X
  :kernel:Y → :platform:java:kernel:modules:Y (where possible)
  :products:app-platform:kernel:Y → :platform:java:kernel:modules:Y
  :products:app-platform:domain-packs:X → :products:finance:domains:X
"""

import re
from pathlib import Path

ROOT = Path("/Users/samujjwal/Development/ghatana")
FINANCE_DOMAINS = ROOT / "products" / "finance" / "domains"

# ── Dependencies to ADD to each finance domain ──────────────────────────
# These are derived from the app-platform build files, translated to finance paths.
# We only add deps that are NOT already present in the finance build files.

DOMAIN_EXTRA_DEPS = {
    "oms": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
            'implementation(project(":products:finance:domains:market-data"))',
        ],
        "external_deps": [
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jedis)",
            "implementation(libs.kafka.clients)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
            "implementation(libs.activej.core)",
            "implementation(libs.activej.http)",
        ],
    },
    "ems": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:market-data"))',
            'implementation(project(":products:finance:domains:reference-data"))',
        ],
        "external_deps": [
            "api(project(\":platform:java:kernel:modules:authentication\"))",
            "api(project(\":platform:java:kernel:modules:event-store\"))",
            "api(project(\":platform:java:kernel:modules:audit\"))",
            "api(project(\":platform:java:kernel:modules:resilience\"))",
            "api(project(\":platform:java:security\"))",
            "api(project(\":platform:java:database\"))",
            "api(project(\":platform:java:http\"))",
            "implementation(libs.activej.eventloop)",
            "implementation(libs.activej.http)",
            "implementation(libs.activej.promise)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jedis)",
            "implementation(libs.kafka.clients)",
            "implementation(libs.micrometer.core)",
        ],
    },
    "compliance": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
        ],
        "external_deps": [
            "api(project(\":platform:java:kernel:modules:authentication\"))",
            "api(project(\":platform:java:kernel:modules:event-store\"))",
            "api(project(\":platform:java:kernel:modules:audit\"))",
            "api(project(\":platform:java:kernel:modules:resilience\"))",
            "api(project(\":platform:java:security\"))",
            "api(project(\":platform:java:database\"))",
            "api(project(\":platform:java:http\"))",
            "implementation(libs.activej.eventloop)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.kafka.clients)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "pms": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
            'implementation(project(":products:finance:domains:market-data"))',
            'implementation(project(":products:finance:domains:pricing"))',
        ],
        "external_deps": [
            "api(project(\":platform:java:kernel:modules:authentication\"))",
            "api(project(\":platform:java:kernel:modules:event-store\"))",
            "api(project(\":platform:java:kernel:modules:audit\"))",
            "api(project(\":platform:java:kernel:modules:resilience\"))",
            "api(project(\":platform:java:security\"))",
            "api(project(\":platform:java:database\"))",
            "api(project(\":platform:java:http\"))",
            "implementation(libs.activej.eventloop)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.hikaricp)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "market-data": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
        ],
        "external_deps": [
            "implementation(libs.activej.http)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jackson.databind)",
            "implementation(libs.jedis)",
            "implementation(libs.kafka.clients)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "post-trade": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:oms"))',
            'implementation(project(":products:finance:domains:ems"))',
            'implementation(project(":products:finance:domains:reference-data"))',
        ],
        "external_deps": [
            "implementation(libs.activej.http)",
            "implementation(libs.activej.promise)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.kafka.clients)",
        ],
    },
    "pricing": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
            'implementation(project(":products:finance:domains:market-data"))',
        ],
        "external_deps": [
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jedis)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "reconciliation": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:post-trade"))',
        ],
        "external_deps": [
            "implementation(libs.activej.promise)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.kafka.clients)",
        ],
    },
    "reference-data": {
        "domain_deps": [],
        "external_deps": [
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jackson.databind)",
            "implementation(libs.jedis)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "regulatory-reporting": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:oms"))',
            'implementation(project(":products:finance:domains:post-trade"))',
            'implementation(project(":products:finance:domains:reference-data"))',
            'implementation(project(":products:aep:platform-bundle"))',
        ],
        "external_deps": [
            "api(project(\":platform:java:governance\"))",
            "implementation(libs.activej.http)",
            "implementation(libs.activej.promise)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jackson.databind)",
            "implementation(libs.jackson.datatype.jsr310)",
            "implementation(platform(libs.jackson.bom))",
            "implementation(libs.pdfbox)",
            "implementation(libs.xbrl.rendering)",
            "implementation(libs.apache.poi)",
            "implementation(libs.okhttp)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "risk": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
            'implementation(project(":products:finance:domains:market-data"))',
            'implementation(project(":products:finance:domains:oms"))',
        ],
        "external_deps": [
            "api(project(\":platform:java:kernel:modules:authentication\"))",
            "api(project(\":platform:java:kernel:modules:event-store\"))",
            "api(project(\":platform:java:kernel:modules:audit\"))",
            "api(project(\":platform:java:kernel:modules:resilience\"))",
            "api(project(\":platform:java:security\"))",
            "api(project(\":platform:java:database\"))",
            "api(project(\":platform:java:http\"))",
            "implementation(libs.activej.eventloop)",
            "implementation(libs.activej.promise)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.hikaricp)",
            "implementation(libs.jedis)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "corporate-actions": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:reference-data"))',
            'implementation(project(":products:finance:domains:oms"))',
            'implementation(project(":products:finance:domains:post-trade"))',
            'implementation(project(":products:aep:platform-bundle"))',
        ],
        "external_deps": [
            "api(project(\":platform:java:governance\"))",
            "implementation(libs.jackson.databind)",
            "implementation(libs.jackson.datatype.jsr310)",
            "implementation(platform(libs.jackson.bom))",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "sanctions": {
        "domain_deps": [],
        "external_deps": [
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.flyway.database.postgresql)",
            "implementation(libs.hikaricp)",
            "implementation(libs.kafka.clients)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
    "surveillance": {
        "domain_deps": [
            'implementation(project(":products:finance:domains:oms"))',
            'implementation(project(":products:finance:domains:ems"))',
            'implementation(project(":products:finance:domains:reference-data"))',
        ],
        "external_deps": [
            "implementation(libs.activej.promise)",
            "implementation(libs.postgresql)",
            "implementation(libs.flyway.core)",
            "implementation(libs.hikaricp)",
            "implementation(libs.micrometer.core)",
            "implementation(libs.slf4j.api)",
        ],
    },
}


def update_build_file(domain: str, extra_deps: dict):
    build_file = FINANCE_DOMAINS / domain / "build.gradle.kts"
    if not build_file.exists():
        print(f"  SKIP: {build_file} not found")
        return

    content = build_file.read_text(encoding="utf-8")

    # Collect deps that are NOT already in the file
    new_domain_deps = []
    for dep in extra_deps.get("domain_deps", []):
        # Extract the key part for matching
        if dep not in content:
            new_domain_deps.append(dep)

    new_external_deps = []
    for dep in extra_deps.get("external_deps", []):
        # Check if already present (strip whitespace for matching)
        dep_key = dep.strip()
        if dep_key not in content:
            new_external_deps.append(dep)

    if not new_domain_deps and not new_external_deps:
        print(f"  {domain}: No new dependencies needed")
        return

    # Find the right place to insert: before the closing } of dependencies block
    # We'll insert before the test dependencies section or before the last }
    
    # Strategy: find the last testImplementation line and insert before it
    lines = content.split("\n")
    insert_idx = None
    
    # Find the first test dependency line
    for i, line in enumerate(lines):
        if "testImplementation" in line or "testRuntimeOnly" in line:
            insert_idx = i
            break
    
    if insert_idx is None:
        # Find closing brace of dependencies block
        in_deps = False
        for i, line in enumerate(lines):
            if "dependencies {" in line or "dependencies{" in line:
                in_deps = True
            if in_deps and line.strip() == "}":
                insert_idx = i
                break

    if insert_idx is None:
        print(f"  {domain}: Could not find insertion point!")
        return

    # Build insertion block
    insertion_lines = []
    if new_domain_deps:
        insertion_lines.append("")
        insertion_lines.append("    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────")
        for dep in new_domain_deps:
            insertion_lines.append(f"    {dep}")
    
    if new_external_deps:
        insertion_lines.append("")
        insertion_lines.append("    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────")
        for dep in new_external_deps:
            insertion_lines.append(f"    {dep}")
    
    insertion_lines.append("")

    # Insert before the test deps / closing brace
    new_lines = lines[:insert_idx] + insertion_lines + lines[insert_idx:]
    new_content = "\n".join(new_lines)
    
    build_file.write_text(new_content, encoding="utf-8")
    print(f"  {domain}: Added {len(new_domain_deps)} domain + {len(new_external_deps)} external deps")


def main():
    print("Updating finance domain build.gradle.kts files...")
    print("=" * 60)
    
    for domain, deps in sorted(DOMAIN_EXTRA_DEPS.items()):
        update_build_file(domain, deps)
    
    print("=" * 60)
    print("Build file updates complete.")


if __name__ == "__main__":
    main()
