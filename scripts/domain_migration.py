#!/usr/bin/env python3
"""
Domain Migration Script: app-platform/domain-packs → finance/domains

Phase 1: Copy service implementations from app-platform into finance domains
Phase 2: Rewrite packages from com.ghatana.appplatform.* → com.ghatana.products.finance.domains.*
Phase 3: Update inter-domain imports to use finance package names
Phase 4: Handle risk-engine → risk rename
Phase 5: Copy healthcare to phr
"""

import os
import re
import shutil
from pathlib import Path

ROOT = Path("/Users/samujjwal/Development/ghatana")
APP_PLATFORM = ROOT / "products" / "app-platform" / "domain-packs"
FINANCE_DOMAINS = ROOT / "products" / "finance" / "domains"
PHR_ROOT = ROOT / "products" / "phr"

# Package mapping: app-platform base package → finance base package
# The sub-packages (.domain, .service, .port, .adapter, etc.) are preserved
PACKAGE_MAP = {
    "com.ghatana.appplatform.compliance": "com.ghatana.products.finance.domains.compliance",
    "com.ghatana.appplatform.corporateactions": "com.ghatana.products.finance.domains.corporateactions",
    "com.ghatana.appplatform.ems": "com.ghatana.products.finance.domains.ems",
    "com.ghatana.appplatform.marketdata": "com.ghatana.products.finance.domains.marketdata",
    "com.ghatana.appplatform.oms": "com.ghatana.products.finance.domains.oms",
    "com.ghatana.appplatform.pms": "com.ghatana.products.finance.domains.pms",
    "com.ghatana.appplatform.posttrade": "com.ghatana.products.finance.domains.posttrade",
    "com.ghatana.appplatform.pricing": "com.ghatana.products.finance.domains.pricing",
    "com.ghatana.appplatform.recon": "com.ghatana.products.finance.domains.reconciliation",
    "com.ghatana.appplatform.refdata": "com.ghatana.products.finance.domains.referencedata",
    "com.ghatana.appplatform.reporting": "com.ghatana.products.finance.domains.regulatoryreporting",
    "com.ghatana.appplatform.risk": "com.ghatana.products.finance.domains.risk",
    "com.ghatana.appplatform.sanctions": "com.ghatana.products.finance.domains.sanctions",
    "com.ghatana.appplatform.surveillance": "com.ghatana.products.finance.domains.surveillance",
    # Healthcare goes to phr
    "com.ghatana.appplatform.healthcare": "com.ghatana.phr.healthcare",
}

# Map domain-pack directory name → finance domain directory name
DIR_MAP = {
    "compliance": "compliance",
    "corporate-actions": "corporate-actions",
    "ems": "ems",
    "healthcare": None,  # Goes to phr, handled separately
    "market-data": "market-data",
    "oms": "oms",
    "pms": "pms",
    "post-trade": "post-trade",
    "pricing": "pricing",
    "reconciliation": "reconciliation",
    "reference-data": "reference-data",
    "regulatory-reporting": "regulatory-reporting",
    "risk-engine": "risk",  # Rename!
    "sanctions": "sanctions",
    "surveillance": "surveillance",
}


def package_to_path(package: str) -> str:
    """Convert Java package to directory path."""
    return package.replace(".", "/")


def rewrite_java_file(content: str) -> str:
    """Rewrite package declarations and imports in a Java file."""
    # Sort by longest prefix first to avoid partial matches
    sorted_mappings = sorted(PACKAGE_MAP.items(), key=lambda x: len(x[0]), reverse=True)

    for old_pkg, new_pkg in sorted_mappings:
        # Replace package declarations
        content = content.replace(f"package {old_pkg}", f"package {new_pkg}")
        # Replace import statements
        content = content.replace(f"import {old_pkg}", f"import {new_pkg}")

    return content


def copy_java_sources(src_domain_dir: Path, target_base: Path, old_base_pkg: str, new_base_pkg: str):
    """Copy Java source files from app-platform domain to finance domain, rewriting packages."""
    src_main = src_domain_dir / "src" / "main" / "java"
    src_test = src_domain_dir / "src" / "test" / "java"

    files_copied = 0
    files_skipped = 0

    for src_root_dir, target_subdir in [(src_main, "src/main/java"), (src_test, "src/test/java")]:
        if not src_root_dir.exists():
            continue

        for java_file in src_root_dir.rglob("*.java"):
            # Read content
            content = java_file.read_text(encoding="utf-8")

            # Rewrite packages
            new_content = rewrite_java_file(content)

            # Compute new path based on new package
            # Get the relative path within the source
            rel_path = java_file.relative_to(src_root_dir)

            # The old directory structure follows the old package
            # We need to compute the new directory based on new package
            old_pkg_path = package_to_path(old_base_pkg)
            new_pkg_path = package_to_path(new_base_pkg)

            rel_str = str(rel_path)
            if old_pkg_path in rel_str:
                new_rel_str = rel_str.replace(old_pkg_path, new_pkg_path, 1)
            else:
                # File might be in a sub-package with slightly different path
                new_rel_str = rel_str

            target_file = target_base / target_subdir / new_rel_str

            # Check if target already exists (don't overwrite DomainModule files)
            if target_file.exists():
                existing = target_file.read_text(encoding="utf-8")
                if "DomainModule" in target_file.name or "implements KernelModule" in existing:
                    print(f"  SKIP (keep existing KernelModule): {target_file.name}")
                    files_skipped += 1
                    continue

            # Create directory and write
            target_file.parent.mkdir(parents=True, exist_ok=True)
            target_file.write_text(new_content, encoding="utf-8")
            files_copied += 1

    return files_copied, files_skipped


def migrate_financial_domains():
    """Migrate all financial domain packs to finance/domains."""
    total_copied = 0
    total_skipped = 0

    for domain_dir_name, target_dir_name in DIR_MAP.items():
        if target_dir_name is None:
            continue  # Skip healthcare (handled separately)

        src_dir = APP_PLATFORM / domain_dir_name
        if not src_dir.exists():
            print(f"WARNING: Source {domain_dir_name} not found, skipping")
            continue

        target_dir = FINANCE_DOMAINS / target_dir_name
        if not target_dir.exists():
            print(f"WARNING: Target {target_dir_name} not found in finance/domains, creating it")
            target_dir.mkdir(parents=True, exist_ok=True)

        # Determine old base package
        # Find first Java file to determine the base package
        old_base_pkg = None
        for java_file in (src_dir / "src").rglob("*.java") if (src_dir / "src").exists() else []:
            content = java_file.read_text(encoding="utf-8")
            match = re.search(r"^package\s+(com\.ghatana\.appplatform\.\w+)", content, re.MULTILINE)
            if match:
                old_base_pkg = match.group(1)
                break

        if old_base_pkg is None:
            print(f"WARNING: No Java package found in {domain_dir_name}, skipping")
            continue

        # Find the corresponding new base package
        new_base_pkg = PACKAGE_MAP.get(old_base_pkg)
        if new_base_pkg is None:
            print(f"WARNING: No mapping for {old_base_pkg}, skipping")
            continue

        print(f"\n=== Migrating {domain_dir_name} → finance/domains/{target_dir_name} ===")
        print(f"  Old package: {old_base_pkg}")
        print(f"  New package: {new_base_pkg}")

        copied, skipped = copy_java_sources(src_dir, target_dir, old_base_pkg, new_base_pkg)
        print(f"  Copied: {copied} files, Skipped: {skipped} files")
        total_copied += copied
        total_skipped += skipped

    return total_copied, total_skipped


def migrate_healthcare():
    """Migrate healthcare to products/phr."""
    src_dir = APP_PLATFORM / "healthcare"
    if not src_dir.exists():
        print("WARNING: healthcare domain-pack not found")
        return 0, 0

    # Target: products/phr/domains/healthcare/
    target_dir = PHR_ROOT / "domains" / "healthcare"
    target_dir.mkdir(parents=True, exist_ok=True)

    old_base_pkg = "com.ghatana.appplatform.healthcare"
    new_base_pkg = "com.ghatana.phr.healthcare"

    print(f"\n=== Migrating healthcare → phr/domains/healthcare ===")
    print(f"  Old package: {old_base_pkg}")
    print(f"  New package: {new_base_pkg}")

    copied, skipped = copy_java_sources(src_dir, target_dir, old_base_pkg, new_base_pkg)
    print(f"  Copied: {copied} files, Skipped: {skipped} files")
    return copied, skipped


def main():
    print("=" * 60)
    print("DOMAIN MIGRATION: app-platform → finance/domains + phr")
    print("=" * 60)

    # Phase 1: Migrate financial domains
    print("\n--- Phase 1: Financial Domain Migration ---")
    fin_copied, fin_skipped = migrate_financial_domains()

    # Phase 2: Migrate healthcare
    print("\n--- Phase 2: Healthcare Migration ---")
    hc_copied, hc_skipped = migrate_healthcare()

    total = fin_copied + hc_copied
    skipped = fin_skipped + hc_skipped
    print(f"\n{'=' * 60}")
    print(f"MIGRATION COMPLETE")
    print(f"  Total files copied: {total}")
    print(f"  Total files skipped (kept existing): {skipped}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
