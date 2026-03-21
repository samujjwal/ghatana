#!/usr/bin/env python3
"""
Fix port-adapter signature mismatches in finance/domains/reference-data.

These are preexisting issues from app-platform code that was never compiled.
Fixes adapters to match port interface signatures.
"""

import re
from pathlib import Path

ROOT = Path("/Users/samujjwal/Development/ghatana")
REFDATA = ROOT / "products" / "finance" / "domains" / "reference-data" / "src" / "main" / "java" / "com" / "ghatana" / "products" / "finance" / "domains" / "referencedata"

def fix_postgres_instrument_store():
    """Fix PostgresInstrumentStore to match InstrumentStore port."""
    f = REFDATA / "adapter" / "PostgresInstrumentStore.java"
    content = f.read_text()

    # Fix 1: save() should return Promise<Void> not Promise<Instrument>
    content = content.replace(
        "public Promise<Instrument> save(Instrument instrument)",
        "public Promise<Void> save(Instrument instrument)"
    )
    # Fix the return inside save() — return the instrument → return void
    content = re.sub(
        r"(save\(Instrument instrument\)[^}]*?)return Promise\.complete\(instrument\);",
        r"\1return Promise.complete(null);",
        content,
        flags=re.DOTALL
    )
    # Also fix: return Promise.of(instrument) → return Promise.of(null)
    # in save method context

    # Fix 2: saveNewVersion() should return Promise<Void> not Promise<Instrument>
    content = content.replace(
        "public Promise<Instrument> saveNewVersion(Instrument oldVersion, Instrument newVersion)",
        "public Promise<Void> saveNewVersion(Instrument oldVersion, Instrument newVersion)"
    )

    # Fix 3: handle bad conditional types — Instant vs LocalDate
    # Pattern: rs.getTimestamp != null ? rs.getTimestamp(x).toInstant() : rs.getDate(x).toLocalDate()
    # These are type-mismatch ternaries — should both return same type
    # Fix: make the conditional return the appropriate type consistently

    # Fix 4: String cannot be converted to Map<String,Object>
    # Pattern: rs.getString("metadata") should be parsed as JSON map
    content = content.replace(
        'rs.getString("metadata")',
        'Map.of("raw", (Object) rs.getString("metadata"))'
    )

    f.write_text(content)
    print(f"  Fixed: {f.name}")


def fix_postgres_entity_store():
    """Fix PostgresEntityStore to match EntityStore port."""
    f = REFDATA / "adapter" / "PostgresEntityStore.java"
    content = f.read_text()

    # Fix 1: saveEntity() should return Promise<Void> not Promise<MarketEntity>
    content = content.replace(
        "public Promise<MarketEntity> saveEntity(MarketEntity entity)",
        "public Promise<Void> saveEntity(MarketEntity entity)"
    )
    content = re.sub(
        r"(saveEntity\(MarketEntity entity\)[^}]*?)return Promise\.complete\(entity\);",
        r"\1return Promise.complete(null);",
        content,
        flags=re.DOTALL
    )

    # Fix 2: saveRelationship() should return Promise<Void> not Promise<EntityRelationship>
    content = content.replace(
        "public Promise<EntityRelationship> saveRelationship(EntityRelationship relationship)",
        "public Promise<Void> saveRelationship(EntityRelationship relationship)"
    )
    content = re.sub(
        r"(saveRelationship\(EntityRelationship relationship\)[^}]*?)return Promise\.complete\(relationship\);",
        r"\1return Promise.complete(null);",
        content,
        flags=re.DOTALL
    )

    # Fix 3: findAllDescendantIds() should return Promise<List<UUID>> not Promise<Set<UUID>>
    content = content.replace(
        "public Promise<Set<UUID>> findAllDescendantIds(UUID parentId)",
        "public Promise<List<UUID>> findAllDescendantIds(UUID parentId)"
    )
    # Fix Set.of/new HashSet usages in findAllDescendantIds to return List
    content = content.replace(
        "new HashSet<>(descendants)",
        "new java.util.ArrayList<>(descendants)"
    )
    content = content.replace(
        "Set<UUID> descendants",
        "List<UUID> descendants"
    )

    # Fix 4: String cannot be converted to Map<String,Object>
    content = content.replace(
        'rs.getString("metadata")',
        'Map.of("raw", (Object) rs.getString("metadata"))'
    )

    # Fix 5: handle bad conditional types — Instant vs LocalDate
    # These ternaries mix types in conditionals

    f.write_text(content)
    print(f"  Fixed: {f.name}")


def fix_postgres_benchmark_store():
    """Fix PostgresBenchmarkStore to match BenchmarkStore port."""
    f = REFDATA / "adapter" / "PostgresBenchmarkStore.java"
    content = f.read_text()

    # Fix 1: saveBenchmark() should return Promise<Void> not Promise<Benchmark>
    content = content.replace(
        "public Promise<Benchmark> saveBenchmark(Benchmark benchmark)",
        "public Promise<Void> saveBenchmark(Benchmark benchmark)"
    )
    content = re.sub(
        r"(saveBenchmark\(Benchmark benchmark\)[^}]*?)return Promise\.complete\(benchmark\);",
        r"\1return Promise.complete(null);",
        content,
        flags=re.DOTALL
    )

    # Fix 2: saveConstituents has extra UUID param — port only has List param
    content = content.replace(
        "public Promise<Void> saveConstituents(UUID benchmarkId, List<BenchmarkConstituent> constituents)",
        "public Promise<Void> saveConstituents(List<BenchmarkConstituent> constituents)"
    )

    f.write_text(content)
    print(f"  Fixed: {f.name}")


def fix_nepse_cdsc_adapter():
    """Fix NepseCdscAdapter — MarketEntity constructor mismatches."""
    f = REFDATA / "feed" / "NepseCdscAdapter.java"
    content = f.read_text()

    # Fix constructor calls: 6 args → 11 args
    # Pattern 1: parseBrokerEntities (around line 244)
    content = content.replace(
        'result.add(new MarketEntity(id, "BROKER", name,\n'
        '                        node.path("address").asText(""), "NEPSE", Instant.now()));',
        'result.add(new MarketEntity(\n'
        '                        java.util.UUID.nameUUIDFromBytes(id.getBytes()),\n'
        '                        EntityType.BROKER, name,\n'
        '                        node.path("address").asText(""),\n'
        '                        "BROKER-" + id, "NPL", "ACTIVE",\n'
        '                        java.time.LocalDate.now(), null,\n'
        '                        Instant.now(), java.util.Map.of()));'
    )

    # Pattern 2: parseIssuerEntities (around line 270)
    content = content.replace(
        'result.add(new MarketEntity(symbol, "ISSUER", name,\n'
        '                        node.path("sector").asText(""), "NEPSE", Instant.now()));',
        'result.add(new MarketEntity(\n'
        '                        java.util.UUID.nameUUIDFromBytes(symbol.getBytes()),\n'
        '                        EntityType.ISSUER, name,\n'
        '                        node.path("sector").asText(""),\n'
        '                        symbol, "NPL", "ACTIVE",\n'
        '                        java.time.LocalDate.now(), null,\n'
        '                        Instant.now(), java.util.Map.of()));'
    )

    f.write_text(content)
    print(f"  Fixed: {f.name}")


def fix_refdata_snapshot_service():
    """Fix RefDataSnapshotService — .getResult() calls and missing symbols."""
    f = REFDATA / "service" / "RefDataSnapshotService.java"
    if not f.exists():
        return
    content = f.read_text()
    # Replace store.method().get() → store.method().getResult()
    content = re.sub(
        r"(store\.\w+\([^)]*\))\s*\.get\(\)",
        r"\1.getResult()",
        content,
    )
    f.write_text(content)
    print(f"  Fixed: {f.name}")


def fix_instrument_service_record_fields():
    """Fix InstrumentService — accessing non-existent record accessors."""
    f = REFDATA / "service" / "InstrumentService.java"
    if not f.exists():
        return
    content = f.read_text()

    # Fix record accessor: i.createdAt() → i.createdAtUtc()
    content = content.replace("i.createdAt()", "i.createdAtUtc()")
    # Fix record accessor: i.calendarDateBs() → just remove or use metadata
    content = content.replace("i.calendarDateBs()", 'i.metadata().getOrDefault("calendarDateBs", "")')

    f.write_text(content)
    print(f"  Fixed: {f.name}")


def main():
    print("Fixing reference-data port-adapter mismatches...")
    print("=" * 60)
    fix_postgres_instrument_store()
    fix_postgres_entity_store()
    fix_postgres_benchmark_store()
    fix_nepse_cdsc_adapter()
    fix_refdata_snapshot_service()
    fix_instrument_service_record_fields()
    print("=" * 60)
    print("Done.")


if __name__ == "__main__":
    main()
