#!/usr/bin/env python3
"""
Add missing @doc.pattern tag to 10 files.
"""

import re
from pathlib import Path

FILES_TO_FIX = [
    ("products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/AnalyticsQuery.java", "ValueObject"),
    ("products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/QueryResult.java", "ValueObject"),
    ("products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/QueryPlan.java", "ValueObject"),
    ("products/data-cloud/platform-config/src/main/java/com/ghatana/datacloud/pattern/DefaultPatternMatcher.java", "Strategy"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/application/nlq/NLQService.java", "Service"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/quality/MLQualityScorer.java", "Service"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/backpressure/BackpressureConfig.java", "Configuration"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/importexport/ExcelExporter.java", "Exporter"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/importexport/CsvImporter.java", "Importer"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/importexport/ImportExportService.java", "Service"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/client/feedback/DefaultFeedbackCollector.java", "Service"),
    ("products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/client/autonomy/AutonomyLog.java", "ValueObject"),
]

def add_pattern_tag(filepath: str, pattern: str):
    """Add @doc.pattern tag to a file that's missing it."""
    path = Path(filepath)
    if not path.exists():
        print(f"⚠️  File not found: {filepath}")
        return False
    
    with open(path, 'r') as f:
        content = f.read()
    
    # Check if @doc.pattern already exists
    if "@doc.pattern" in content:
        print(f"✅ Already has @doc.pattern: {filepath}")
        return True
    
    # Find last @doc line and add @doc.pattern after it
    # Pattern: look for @doc.layer or similar and add @doc.pattern after
    pattern_to_find = r'(\s+\* @doc\..+\n)'
    last_match = None
    for match in re.finditer(pattern_to_find, content):
        last_match = match
    
    if not last_match:
        print(f"❌ Could not find @doc tags in {filepath}")
        return False
    
    # Insert @doc.pattern after the last @doc tag
    insert_pos = last_match.end()
    new_content = content[:insert_pos] + f" * @doc.pattern {pattern}\n" + content[insert_pos:]
    
    with open(path, 'w') as f:
        f.write(new_content)
    
    print(f"✅ Added @doc.pattern={pattern}: {filepath}")
    return True

if __name__ == "__main__":
    success = 0
    for filepath, pattern in FILES_TO_FIX:
        if add_pattern_tag(filepath, pattern):
            success += 1
    
    print(f"\n✅ Successfully added @doc.pattern to {success}/{len(FILES_TO_FIX)} files")
