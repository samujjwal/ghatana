#!/usr/bin/env python3
"""
Phase 6.9: Replace Consumer<Object> eventPublisher with EventBusPort eventBusPort.

Replaces:
  - Field: Consumer<Object> eventPublisher  → EventBusPort eventBusPort
  - Constructor param: Consumer<Object> eventPublisher → EventBusPort eventBusPort
  - Usage: eventPublisher.accept(...)  → eventBusPort.publish(...)
  - Import: java.util.function.Consumer → com.ghatana.platform.core.event.EventBusPort
    (only removes Consumer if no other Consumer<...> usage remains)

Targets: products/app-platform/domain-packs/*/src/main/java/**/*.java
"""
import os
import re

ROOT = "/home/samujjwal/Developments/ghatana"
DOMAIN_PACKS = os.path.join(ROOT, "products", "app-platform", "domain-packs")
EVENTBUS_IMPORT = "import com.ghatana.platform.core.event.EventBusPort;"
CONSUMER_IMPORT = "import java.util.function.Consumer;"

changed_files = []
skipped_files = []


def process_file(filepath):
    with open(filepath, "r") as f:
        content = f.read()

    # Only process files that have Consumer<Object> eventPublisher
    if "Consumer<Object>" not in content or "eventPublisher" not in content:
        return False

    original = content

    # 1. Replace field declarations
    content = re.sub(
        r'(private\s+(?:final\s+)?)Consumer<Object>(\s+)eventPublisher',
        r'\1EventBusPort\2eventBusPort',
        content
    )

    # 2. Replace constructor/method parameters
    content = re.sub(
        r'Consumer<Object>\s+eventPublisher',
        'EventBusPort eventBusPort',
        content
    )

    # 3. Replace field assignments in constructors: this.eventPublisher = eventPublisher
    content = content.replace(
        'this.eventPublisher = eventPublisher',
        'this.eventBusPort = eventBusPort'
    )

    # 4. Replace .accept( calls: eventPublisher.accept(  → eventBusPort.publish(
    content = content.replace('eventPublisher.accept(', 'eventBusPort.publish(')

    # 5. Replace other references to eventPublisher field/param
    # Be careful: only replace standalone references (not part of other words)
    content = re.sub(r'\beventPublisher\b', 'eventBusPort', content)

    # 6. Add EventBusPort import if not present
    if 'EventBusPort' in content and EVENTBUS_IMPORT not in content:
        # Insert after package declaration
        content = re.sub(
            r'(package\s+[^;]+;\s*\n)',
            r'\1\n' + EVENTBUS_IMPORT + '\n',
            content,
            count=1
        )

    # 7. Remove Consumer import if no Consumer<...> references remain (other than EventBusPort replacements)
    if CONSUMER_IMPORT in content:
        # Check if Consumer is still used for anything else
        remaining = content.replace(CONSUMER_IMPORT, '')
        if 'Consumer<' not in remaining and 'Consumer ' not in remaining:
            content = content.replace(CONSUMER_IMPORT + '\n', '')
            # Also remove if it's the last import before a blank line
            content = content.replace(CONSUMER_IMPORT, '')

    if content != original:
        with open(filepath, "w") as f:
            f.write(content)
        return True
    return False


def walk_java_files(base_dir):
    for root, dirs, files in os.walk(base_dir):
        for fname in files:
            if fname.endswith(".java"):
                yield os.path.join(root, fname)


def main():
    count = 0
    scanned = 0
    print(f"Scanning: {DOMAIN_PACKS}")
    print(f"Exists: {os.path.exists(DOMAIN_PACKS)}")
    for filepath in walk_java_files(DOMAIN_PACKS):
        scanned += 1
        if process_file(filepath):
            rel = os.path.relpath(filepath, ROOT)
            changed_files.append(rel)
            count += 1
            print(f"  ✓ {rel}")

    print(f"\n{'='*60}")
    print(f"Total files scanned: {scanned}")
    print(f"Total files modified: {count}")
    for f in changed_files:
        print(f"  {f}")


if __name__ == "__main__":
    main()
